package org.entermedia.youtube;

import java.util.List;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.openedit.Data;
import org.openedit.data.Searcher;
import org.openedit.entermedia.Asset;
import org.openedit.entermedia.MediaArchive;
import org.openedit.entermedia.publishing.BasePublisher;
import org.openedit.entermedia.publishing.PublishResult;
import org.openedit.entermedia.publishing.Publisher;

import com.openedit.page.Page;

public class YoutubePublisher extends BasePublisher implements Publisher
{
	private static final Log log = LogFactory.getLog(YoutubePublisher.class);
	
	public PublishResult publish(MediaArchive mediaArchive, Asset inAsset, Data inPublishRequest, Data inDestination, Data inPreset)
	{
		
		//https://github.com/youtube/api-samples/blob/master/java/src/main/java/com/google/api/services/samples/youtube/cmdline/data/UploadVideo.java
		//Could be a better way - see above, would support resuming?
		
		//setup result object
		PublishResult result = new PublishResult();
		
		//get publishing service
		YouTubeModule module = (YouTubeModule) mediaArchive.getModuleManager().getBean("YouTubeModule");
		
		YouTubePublishingService service = module.getYouTubePublishingService();
		//get tokens and validate
		String access_token = inDestination.get("access_token");
		if (access_token == null){
			result.setComplete(true);
			result.setErrorMessage("Access Token is not valid; configure OAuth");
			return result;
		}
		if (!service.isAccessTokenValid(access_token)){
			String client_id = inDestination.get("accesskey");
			String client_secret = inDestination.get("secretkey");
			String refresh_token = inDestination.get("refresh_token");
			String token = service.refreshAccessToken(client_id, client_secret, refresh_token);
			if (service.isAccessTokenValid(token)){
				Searcher searcher = mediaArchive.getSearcher("publishdestination");
				Data obj = (Data) searcher.searchById(inDestination.getId());
				obj.setProperty("access_token",token);
				searcher.saveData(obj, null);
				access_token = token;
			}
			else {
				result.setComplete(true);
				result.setErrorMessage("Access Token is not valid; reconfigure OAuth");
				return result;
			}
		}
		String pubstatus = inPublishRequest.get("status");
		if (pubstatus == null){
			result.setComplete(true);
			result.setErrorMessage("Status is not defined");
			return result;
		}
		if ("new".equals(pubstatus) || "retry".equals(pubstatus)) {
			String title = inAsset.get("assettitle");
			String description = inAsset.get("longcaption");
			List<String> keywords = inAsset.getKeywords();
			if (title == null || description == null|| keywords.isEmpty()){
				result.setComplete(true);
				result.setErrorMessage("Title, Long Caption, and Keywords of an asset need to be defined before publishing to YouTube");
				return result;
			}
			Page inputpage = findInputPage(mediaArchive,inAsset,inPreset);
			if (inputpage == null){
				result.setComplete(true);
				result.setErrorMessage("Video file cannot be found");
				return result;
			}
			String videoPath = inputpage.getContentItem().getAbsolutePath();
			//prevent others from trying to publish
			Searcher queuesearcher = mediaArchive.getSearcherManager().getSearcher(mediaArchive.getCatalogId(), "publishqueue");
			inPublishRequest.setProperty("status","pending");
			inPublishRequest.setProperty("errordetails", " ");
			queuesearcher.saveData(inPublishRequest, null);
			//publish video and get tracking id
			log.info("starting to publish asset ID" + inAsset.getId() + " to YouTube");
			long ms = System.currentTimeMillis();
			String videoId = service.publish(access_token, videoPath, title, description, keywords);
			ms = System.currentTimeMillis() - ms;
			log.info("finished publishing asset ID " + inAsset.getId() + " to YouTube, took " + ms + "  ms, tracking id=" + videoId);
			inPublishRequest.setProperty("trackingnumber",videoId);
			

			queuesearcher.saveData(inPublishRequest, null);//save again
			//update result
			result.setPending(true);
			//set asset metadata field video id if available
			String assetVideoIdField = inDestination.get("assetvideoidfield");
			if(assetVideoIdField != null){
				inAsset.setProperty(assetVideoIdField, videoId);
				mediaArchive.getAssetSearcher().saveData(inAsset, null);
			}
		}
		else if (pubstatus.equals("pending") && inPublishRequest.get("trackingnumber") != null){
			String videoId = inPublishRequest.get("trackingnumber");
			String videostatus = service.getVideoUploadStatus(access_token, videoId);
			log.info("video status of " + videoId + ", asset ID " + inAsset.getId() +"is  " +  videostatus);
	
			if (videostatus != null){
				if (videostatus == "uploaded"){
					result.setPending(true);
				} else if (videostatus == "processed"){
					result.setComplete(true);
				} else if (videostatus == "rejected"){
					result.setComplete(true);
					result.setErrorMessage("The video was rejected");
				} else if (videostatus == "not found"){
					result.setComplete(true);
					result.setErrorMessage("The video was not found");
				} else {
					result.setComplete(true);
					result.setErrorMessage("Unknown upload status: $videostatus");
				}
			} else {
				result.setPending(true);//try again
			}
		}
		return result;
	}
}