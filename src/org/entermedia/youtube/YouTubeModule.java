package org.entermedia.youtube;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.openedit.Data;
import org.openedit.data.Searcher;
import org.openedit.entermedia.MediaArchive;

import com.openedit.WebPageRequest;
import com.openedit.hittracker.HitTracker;
import com.openedit.hittracker.SearchQuery;
import com.openedit.modules.BaseModule;

public class YouTubeModule extends BaseModule {
	
	private static final Log log = LogFactory.getLog(YouTubeModule.class);
	
	protected HttpClient fieldHttpClient = null;
	protected YouTubePublishingService fieldYouTubePublishingService = null;
	
	/**
	 * 
	 * @return
	 */
	public HttpClient getHttpClient() {
		if (fieldHttpClient == null){
			fieldHttpClient = new HttpClient();
		}
		return fieldHttpClient;
	}
	
	/**
	 * 
	 * @param inHttpClient
	 */
	public void setHttpClient(HttpClient inHttpClient) {
		fieldHttpClient = inHttpClient;
	}
	
	/**
	 * 
	 * @return
	 */
	public YouTubePublishingService getYouTubePublishingService() {
		if (fieldYouTubePublishingService == null){
			fieldYouTubePublishingService = new YouTubePublishingService();
		}
		return fieldYouTubePublishingService;
	}
	
	/**
	 * 
	 * @param inYouTubePublishingService
	 */
	public void setYouTubePublishingService(
			YouTubePublishingService inYouTubePublishingService) {
		fieldYouTubePublishingService = inYouTubePublishingService;
	}
	
	/**
	 * 
	 * @param inReq
	 */
	public void initOAuthConfiguration(WebPageRequest inReq){
		log.info("starting OAuth configuration, resetting oauth flag");
		MediaArchive archive = (MediaArchive) inReq.getPageValue("mediaarchive");
		Searcher searcher = archive.getSearcher("publishdestination");
		SearchQuery query = searcher.createSearchQuery();
		query.addMatches("publishtype","youtube");
		HitTracker hits = searcher.search(query);
		List<Data> list = new ArrayList<Data>();
		for(int i=0; i < hits.size(); i++){
			Data hit = hits.get(i);
			Data data = (Data) searcher.searchById(hit.getId());
			data.setProperty("oauth", "false");
			list.add(data);
		}
		searcher.saveAllData(list, null);
	}
	
	/**
	 * 
	 * @param inReq
	 */
	public void startOAuthConfiguration(WebPageRequest inReq){
		log.info("starting OAuth configuration");
		String id = inReq.getRequestParameter("id");
		if (id==null){
			log.info("id has not been provided, aborting");
			inReq.putPageValue("result","error");
			return;
		}
		MediaArchive archive = (MediaArchive) inReq.getPageValue("mediaarchive");
		Searcher searcher = archive.getSearcher("publishdestination");
		SearchQuery query = searcher.createSearchQuery();
		query.addMatches("publishtype","youtube");
		HitTracker hits = searcher.search(query);
		if (hits.isEmpty()){
			log.info("unable to find any youtube publishers, aborting");
			inReq.putPageValue("result","error");
			return;
		}
		List<Data> list = new ArrayList<Data>();
		for(int i=0; i < hits.size(); i++){
			Data hit = hits.get(i);
			Data data = (Data) searcher.searchById(hit.getId());
			data.setProperty("oauth", id.equals(data.getId()) ? "true" : "false");
			list.add(data);
		}
		searcher.saveAllData(list, null);
		inReq.putPageValue("result","ok");
		log.info("finished enabling OAuth for publishdestination #"+id);
	}
	
	/**
	 * 
	 * @param inReq
	 */
	public void handleOAuthConfiguration(WebPageRequest inReq){
		log.info("processing configuration callback");
		String code = inReq.getRequestParameter("code");
		log.info("found the following code: "+code);
		if (code == null){
			log.info("code not provided, aborting");
			inReq.putPageValue("result","error");
			inReq.putPageValue("reason","Parameter error: code was not provided");
			return;
		}
		MediaArchive archive = (MediaArchive) inReq.getPageValue("mediaarchive");
		Searcher searcher = archive.getSearcher("publishdestination");
		SearchQuery query = searcher.createSearchQuery();
		query.addMatches("publishtype","youtube");
		query.addMatches("oauth","true");
		HitTracker hits = searcher.search(query);
		if (hits.isEmpty()){
			log.info("unable to find youtube publisher with oauth flag enabled, aborting");
			inReq.putPageValue("result","error");
			inReq.putPageValue("reason","Configuration error: cannot find the right YouTube publisher to configure");
			return;
		}
		Data first = (Data) hits.first();
		String client_id = first.get("accesskey");
		String client_secret = first.get("secretkey");
		String redirect_uri = first.get("url");
		Map<String,String> tokens = getYouTubePublishingService().exchangeCodeForTokens(client_id,client_secret,redirect_uri,code);
		Data entry = (Data) searcher.searchById(first.getId());
		if (!tokens.containsKey("access_token") && !tokens.containsKey("refresh_token")){
			log.info("unable to find access or refresh token from exchange request, aborting");
			inReq.putPageValue("result","error");
			inReq.putPageValue("reason","Communication error: cannot exchange code for access tokens");
			return;
		}
		if (tokens.containsKey("access_token")){
			entry.setProperty("access_token",tokens.get("access_token"));
		}
		if (tokens.containsKey("refresh_token")) {
			entry.setProperty("refresh_token",tokens.get("refresh_token"));
		}
		searcher.saveData(entry,null);
		inReq.putPageValue("result","ok");
		inReq.putPageValue("data",entry);
		log.info("finished configuring youtube publisher "+first.getId());
	}
	
	/**
	 * 
	 * @param inReq
	 */
	public void setRefreshToken(WebPageRequest inReq){
		
	}
	
	/**
	 * 
	 * @param inReq
	 */
	public void revokeAccessToken(WebPageRequest inReq){
		
	}
	
	/**
	 * 
	 * @param inReq
	 */
	public void publishVideo(WebPageRequest inReq){
		
	}
	
	/**
	 * 
	 * @param inReq
	 */
	public void setVideoStatus(WebPageRequest inReq){
		
	}
}
