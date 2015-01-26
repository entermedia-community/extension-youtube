package org.entermedia.youtube;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.methods.GetMethod;
import org.apache.commons.httpclient.methods.PostMethod;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.google.api.client.googleapis.auth.oauth2.GoogleCredential;
import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import com.google.api.client.googleapis.media.MediaHttpUploader;
import com.google.api.client.googleapis.media.MediaHttpUploaderProgressListener;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.http.InputStreamContent;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.services.youtube.YouTube;
import com.google.api.services.youtube.model.Video;
import com.google.api.services.youtube.model.VideoListResponse;
import com.google.api.services.youtube.model.VideoSnippet;
import com.google.api.services.youtube.model.VideoStatus;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;



public class YouTubePublishingService {
	
	private static final Log log = LogFactory.getLog(YouTubePublishingService.class);
	
	public static final String APPLICATION_NAME = "YouTubePublishingService";
	public static final String VIDEO_FILE_FORMAT = "video/*";
	public static final HttpTransport HTTP_TRANSPORT = new NetHttpTransport();
    public static final JsonFactory JSON_FACTORY = new JacksonFactory();
	
	protected HttpClient fieldHttpClient = null;
	
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
	 * @param inAccessToken
	 * @param inVideoId
	 * @return
	 */
	public String getVideoUploadStatus(String inAccessToken, String inVideoId){
		String videoStatus = null;
		try{
			GoogleCredential credentials = new GoogleCredential().setAccessToken(inAccessToken);
			YouTube youtube = new YouTube.Builder(HTTP_TRANSPORT,JSON_FACTORY, credentials)
				.setApplicationName(APPLICATION_NAME).build();
			YouTube.Videos.List listVideosRequest = youtube.videos().list("status").setId(inVideoId);
			VideoListResponse listResponse = listVideosRequest.execute();
			List<Video> videoList = listResponse.getItems();
			if (videoList!=null && videoList.isEmpty() == false){
				Video first = videoList.get(0);
				if (first != null && first.getStatus()!=null){
					videoStatus = first.getStatus().getUploadStatus();
				}
			} else {
				log.info("video status of "+inVideoId+" cannot be found");
				videoStatus = "not found";
			}
		}
		catch (Exception e){
			log.error(e.getMessage(),e);
		}
		return videoStatus;
	}
	
	/**
	 * 
	 * @param inAccessToken
	 * @return
	 */
	public boolean isAccessTokenValid(String inAccessToken){
		boolean isValid = false;
		GetMethod method = null;
		try{
			method = new GetMethod("https://www.googleapis.com/oauth2/v1/tokeninfo?access_token="+inAccessToken);
			int status = getHttpClient().executeMethod(method);
			String body = method.getResponseBodyAsString();
			log.info("tokeninfo status: "+status+", response: "+body);
			//400 - access token is not valid; 200 - access token valid
			if (status == 200){
				Type stringStringMap = new TypeToken<Map<String, String>>(){}.getType();
				Map<String,String> map = new Gson().fromJson(body, stringStringMap);
				log.info("tokeninfo map: "+map);
				isValid = !map.containsKey("error") && map.containsKey("issued_to") && map.containsKey("audience") && map.containsKey("scope");
			}
		}
		catch (Exception e){
			log.error(e.getMessage(),e);
		}
		finally{
			try{
				method.releaseConnection();
			}catch(Exception e){}
		}
		return isValid;
	}
	
	/**
	 * 
	 * @param inClientId
	 * @param inClientSecret
	 * @param inRedirectUri
	 * @param inCode
	 * @return
	 */
	public Map<String,String> exchangeCodeForTokens(String inClientId, String inClientSecret, String inRedirectUri, String inCode){
		Map<String,String> tokens = new HashMap<String,String>();
		PostMethod method = null;
		try{
			method = new PostMethod("https://www.googleapis.com/oauth2/v3/token");
			method.addParameter("client_id", inClientId);
			method.addParameter("client_secret", inClientSecret);
			method.addParameter("redirect_uri",inRedirectUri);
			method.addParameter("code", inCode);
			method.addParameter("grant_type", "authorization_code");
			int status = getHttpClient().executeMethod(method);
			String body = method.getResponseBodyAsString();
			log.info("exchange code status: "+status+", response: "+body);
			if (status == 200){
				Type stringStringMap = new TypeToken<Map<String, String>>(){}.getType();
				Map<String,String> map = new Gson().fromJson(body, stringStringMap);
				log.info("exchange code map: "+map);
				tokens.put("access_token",map.get("access_token"));
				if (map.containsKey("refresh_token")){
					tokens.put("refresh_token",map.get("refresh_token"));
				}
			}
		}
		catch (Exception e){
			log.error(e.getMessage(),e);
		}
		finally{
			try{
				method.releaseConnection();
			}catch(Exception e){}
		}
		return tokens;
	}
	
	
	/**
	 * 
	 * @param inClientId
	 * @param inClientSecret
	 * @param inRefreshToken
	 * @return
	 */
	public String refreshAccessToken(String inClientId, String inClientSecret, String inRefreshToken){
		String token = null;
		PostMethod method = null;
		try{
			method = new PostMethod("https://accounts.google.com/o/oauth2/token");
			method.addParameter("client_id", inClientId);
			method.addParameter("client_secret", inClientSecret);
			method.addParameter("refresh_token", inRefreshToken);
			method.addParameter("grant_type", "refresh_token");
			int status = getHttpClient().executeMethod(method);
			String body = method.getResponseBodyAsString();
			log.info("refreshaccesstoken status: "+status+", response: "+body);
			if (status == 200){
				Type stringStringMap = new TypeToken<Map<String, String>>(){}.getType();
				Map<String,String> map = new Gson().fromJson(body, stringStringMap);
				log.info("refreshaccesstoken map: "+map);
				token = map.get("access_token");
			}
		}
		catch (Exception e){
			log.error(e.getMessage(),e);
		}
		finally{
			try{
				method.releaseConnection();
			}catch(Exception e){}
		}
		return token;
	}
	
	/**
	 * Publish a video file to YouTube using v3 API
	 * @param inAccessToken
	 * @param inFile
	 * @param inTitle
	 * @param inDescription
	 * @param tags
	 * @return
	 */
	public String publish(String inAccessToken, String inFile, String inTitle, String inDescription, List<String> inTags){
		String id = null;
		try{
			GoogleCredential credentials = new GoogleCredential().setAccessToken(inAccessToken);
			YouTube youtube = new YouTube.Builder(HTTP_TRANSPORT,JSON_FACTORY,credentials)
				.setApplicationName(APPLICATION_NAME).build();
			Video videoObjectDefiningMetadata = new Video();
			VideoStatus status = new VideoStatus();
			status.setPrivacyStatus("public");
			videoObjectDefiningMetadata.setStatus(status);
			VideoSnippet snippet = new VideoSnippet();
			snippet.setTitle(inTitle);
            snippet.setDescription(inDescription);
            snippet.setTags(inTags);
            videoObjectDefiningMetadata.setSnippet(snippet);
            InputStreamContent mediaContent = new InputStreamContent(VIDEO_FILE_FORMAT,
            		new FileInputStream(new File(inFile)));
            YouTube.Videos.Insert videoInsert = youtube.videos()
            		.insert("snippet,statistics,status", videoObjectDefiningMetadata, mediaContent);
            MediaHttpUploader uploader = videoInsert.getMediaHttpUploader();
            uploader.setDirectUploadEnabled(false);
            MediaHttpUploaderProgressListener progressListener = new MediaHttpUploaderProgressListener(){
            	public void progressChanged(MediaHttpUploader uploader) throws IOException{
                    switch (uploader.getUploadState()){
                        case INITIATION_STARTED:
                            log.info("progressListener: initiation started");
                            break;
                        case INITIATION_COMPLETE:
                        	log.info("progressListener: initiation completed");
                            break;
                        case MEDIA_IN_PROGRESS:
                        	log.info("progressListener: upload progress, percentage complete " + uploader.getProgress());
                            break;
                        case MEDIA_COMPLETE:
                        	log.info("progressListener: upload finished");
                            break;
                        case NOT_STARTED:
                        	log.info("progressListener: upload not started");
                            break;
                    }
                }
            };
            uploader.setProgressListener(progressListener);
            Video publishedvideo = videoInsert.execute();
            id = publishedvideo.getId();
            log.info("published video: id="+publishedvideo.getId()+", title="+publishedvideo.getSnippet().getTitle());
		}
		catch (GoogleJsonResponseException e)
		{
			log.error(e.getMessage(),e);
        }
		catch (IOException e)
		{
			log.error(e.getMessage(),e);
        } 
		catch (Exception e)
		{
			log.error(e.getMessage(),e);
        }
		return id;
	}
	
	/**
	 * 
	 * @param inAccessToken
	 */
	public void revokeAccessToken(String inAccessToken){
		//https://accounts.google.com/o/oauth2/revoke?token={token}
	}
}
