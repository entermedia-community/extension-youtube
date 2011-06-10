package org.entermedia.fatwire;

import java.io.IOException;

import org.openedit.entermedia.Asset;
import org.openedit.entermedia.MediaArchive;

import com.openedit.WebPageRequest;
import com.openedit.modules.BaseModule;

public class FatwireModule extends BaseModule
{
	protected FatwireManager fieldFatwireManager;
	
	public FatwireManager getFatwireManager(WebPageRequest inReq)
	{
		if(fieldFatwireManager.getMediaArchive() == null)
		{
			String catalogid = inReq.getRequestParameter("catalogid");
			fieldFatwireManager.setMediaArchive(getMediaArchive(catalogid));
		}
		return fieldFatwireManager;
	}
	
	public void setFatwireManager(FatwireManager inFatwireManager)
	{
		fieldFatwireManager = inFatwireManager;
	}
	
	public MediaArchive getMediaArchive(String inCatalogid)
	{
		if (inCatalogid == null)
		{
			return null;
		}
		MediaArchive archive = (MediaArchive) getModuleManager().getBean(inCatalogid, "mediaArchive");
		return archive;
	}
	
	public Asset getAsset(WebPageRequest inReq){
		Asset asset = (Asset)inReq.getPageValue("asset");
		if(asset == null)
		{
			String sourcepath = inReq.getRequestParameter("sourcepath");
			String catalogid = inReq.getRequestParameter("catalogid");
			asset = getMediaArchive(catalogid).getAssetBySourcePath(sourcepath);
		}
		return asset;
	}
	
	public void exportAsset(WebPageRequest inReq)
	{
		if(inReq.getRequestParameter("export") == null)
		{
			return;
		}
		
		FatwireManager manager = getFatwireManager(inReq);
		String urlHome = inReq.getSiteRoot() + inReq.getPageValue("home");
		String usage = inReq.getRequestParameter("usage");
		try {
			manager.pushAsset(getAsset(inReq), "Image_C", "Image", inReq.getUser(), urlHome, usage);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
}