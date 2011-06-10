package org.entermedia.fatwire;

import java.io.IOException;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
import java.util.List;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.dom4j.Element;
import org.openedit.entermedia.Asset;
import org.openedit.entermedia.MediaArchive;
import org.openedit.xml.XmlArchive;
import org.openedit.xml.XmlFile;

import com.fatwire.rest.beans.AssetBean;
import com.fatwire.rest.beans.AssetInfo;
import com.fatwire.rest.beans.AssetsBean;
import com.fatwire.rest.beans.Attribute;
import com.fatwire.rest.beans.Attribute.Data;
import com.fatwire.rest.beans.Site;
import com.fatwire.rest.beans.SitesBean;
import com.fatwire.wem.sso.SSO;
import com.fatwire.wem.sso.SSOException;
import com.openedit.hittracker.HitTracker;
import com.openedit.hittracker.SearchQuery;
import com.openedit.page.manage.PageManager;
import com.openedit.users.User;
import com.openedit.users.UserManager;
import com.openedit.util.PathUtilities;
import com.sun.jersey.api.client.Client;
import com.sun.jersey.api.client.WebResource;
import com.sun.jersey.api.client.WebResource.Builder;

public class FatwireManager {
	private static final Log log = LogFactory.getLog(FatwireManager.class);
	protected Client fieldClient;
	protected String fieldSSOConfig = "SSOConfig.xml";
	protected MediaArchive fieldMediaArchive;
	protected XmlArchive fieldXmlArchive;
	protected UserManager fieldUserManager;
	
	public PageManager getPageManager()
	{
		return getMediaArchive().getPageManager();
	}
	
	public UserManager getUserManager()
	{
		return fieldUserManager;
	}
	
	public void setUserManager(UserManager inUserManager)
	{
		fieldUserManager = inUserManager;
	}
  
	public XmlArchive getXmlArchive() {
		return fieldXmlArchive;
	}

	public void setXmlArchive(XmlArchive inXmlArchive) {
		fieldXmlArchive = inXmlArchive;
	}

	public MediaArchive getMediaArchive()
	{
		return fieldMediaArchive;
	}

	public void setMediaArchive(MediaArchive inMediaArchive)
	{
		fieldMediaArchive = inMediaArchive;
	}
	
	public String getSSOConfig()
	{
		return fieldSSOConfig;
	}
	
	public void setSSOConfig(String inSSOConfig)
	{
		fieldSSOConfig = inSSOConfig;
	}
	
	public Client getClient()
	{
		if(fieldClient == null)
		{
			fieldClient = Client.create();
		}
		return fieldClient;
	}
	
	public String getAttribute(AssetBean inAsset, String inName)
	{
		List<Attribute> attributes = inAsset.getAttributes();
		for(Iterator<Attribute> iterator = attributes.iterator(); iterator.hasNext();)
		{
			Attribute attr = iterator.next();
			if(attr.getName().equalsIgnoreCase(inName))
			{
				return attr.getData().getStringValue();
			}
		}
		return null;
	}
	
	public List<String> getAttributeList(AssetBean inAsset, String inName)
	{
		ArrayList<String> attrs = new ArrayList<String>();
		List<Attribute> attributes = inAsset.getAttributes();
		for(Iterator<Attribute> iterator = attributes.iterator(); iterator.hasNext();)
		{
			Attribute attr = iterator.next();
			if(attr.getName().equalsIgnoreCase(inName))
			{
				attrs.add(attr.getData().getStringValue());
			}
		}
		return attrs;
	}
	
	public String getSite()
	{
		String catalogid = getMediaArchive().getCatalogId();
		org.openedit.Data catalog = (org.openedit.Data)getMediaArchive().getSearcherManager().getData("media", "catalogs", catalogid);
		return catalog.getName();
	}
	
	public AssetsBean search(String inSite, String inType, BigInteger inStartIndex)
	{
		Client client = getClient();
		String baseurl = getUrlBase();
		String url = baseurl + "/sites/" + inSite + "/types/" + inType + "/search?field:name:wildcard=*";
		if(inStartIndex != null)
		{
			url = url + "&startindex=" + inStartIndex;
		}
		WebResource wr = client.resource(url);
		String ticket = getTicket(url, getSSOConfig());
		wr = wr.queryParam("ticket", ticket);
		Builder builder = wr.header("Pragma", "auth-redirect=false");
		AssetsBean fwassets = builder.get(AssetsBean.class);
		return fwassets;
	}
	
	public void pullAssets()
	{
		pullImageAssets(getSite(), BigInteger.ZERO, null);
	}
	
	public void pullImageAssets(String inSite, BigInteger inStartIndex, BigInteger inTotal)
	{
		log.info("Called pullImageAssets with StartIndex, Total: " + inStartIndex + ", " + inTotal);
		if(inTotal != null && inTotal.compareTo(inStartIndex) <= 0)
		{
			//we hit the end of the list
			return;
		}
		
		//do a wildcard search and process each bean
		AssetsBean fwassets = search(inSite, "Image_C", inStartIndex);
		
		inTotal = fwassets.getTotal();
		
		//loop through count returned
		//search again with start index - 1
		for (AssetInfo assetinfo : fwassets.getAssetinfos()) {
			String id = assetinfo.getId();
			pullAsset(inSite, "Image_C", id.split(":")[1]);
		}
		pullImageAssets(inSite, inStartIndex.add(fwassets.getCount()), inTotal);
	}
	
	public void pullAsset(String inSite, String inType, String inId)
	{
		//first search for the asset
		Client client = getClient();
		String baseurl = getUrlBase();
		String url = baseurl + "/sites/" + inSite + "/types/" + inType + "/assets/" + inId;
		WebResource wr = client.resource(url);
		String ticket = getTicket(url, getSSOConfig());
		wr = wr.queryParam("ticket", ticket);
		Builder builder = wr.header("Pragma", "auth-redirect=false");
		AssetBean fwasset = builder.get(AssetBean.class);
		
		if(fwasset == null || fwasset.getId() == null)
		{
			//asset doesn't exist
			return;
		}
		
		//see if we have an asset with this id
		SearchQuery q = getMediaArchive().getAssetSearcher().createSearchQuery();
		q.addExact("fatwireid", fwasset.getId());
		HitTracker hits = getMediaArchive().getAssetSearcher().search(q);
		
		if(hits.size() > 0)
		{
			//don't import asset is already here, in future merge properties
			log.info("Not importing asset, asset already exists in EnterMedia");
			return;
		}
		
		//lets go ahead and import it
		String imageurl = getAttribute(fwasset, "imageurl");
		if(imageurl == null)
		{
			return;
		}
		String pageName = PathUtilities.extractPageName(imageurl);
		Asset asset = getMediaArchive().createAsset("fatwire/" + inSite + "/" + inType + "/" + pageName);
		asset.setFolder(true);
		
		//lets go through and set some properties
		asset.setName(fwasset.getName());
		asset.setProperty("assettitle", fwasset.getDescription());
		
		//set up categories
		List<String> categories = getAttributeList(fwasset, "cat");
		for (String category : categories) {
			asset.addKeyword(category);
		}
		asset.setProperty("fatwireid", fwasset.getId());
		
		//save the asset
		getMediaArchive().getAssetSearcher().saveData(asset, null);
	}
	
	public void addCategory(AssetBean inFwAsset, String inKeyword)
	{
		Attribute catattr = new Attribute();
        Data catdata = new Data();
        catdata.setStringValue(inKeyword);
        catattr.setData(catdata);
        catattr.setName("cat");
        inFwAsset.getAttributes().add(catattr);
	}
	
	public AssetBean pushAsset(Asset inAsset, String inType, String inSubtype, User inUser, String inUrlHome, String inUsage) throws IOException
	{
		return pushAsset(inAsset, getSite(), inType, inSubtype, inUser, inUrlHome, inUsage);
	}
	
	public AssetBean pushAsset(Asset inAsset, String inSite, String inType, String inSubtype, User inUser, String inUrlHome, String inUsage) throws IOException
	{
		if(inAsset.get("fatwireid") != null)
		{
			//don't send the asset to fatwire again, maybe in future we can can sync fields
			log.info("Not exporting asset, already exists on Fatwire");
			return null;
		}
		
		//convert our asset in to an asset bean
		AssetBean fwasset = new AssetBean();
		fwasset.setId("entermedia" + ":" + inAsset.getId());
		fwasset.setName(inAsset.getName());
		fwasset.setDescription(inAsset.get("assettitle"));
		fwasset.setSubtype(inSubtype);
		
		//add usagerights attribute
		Attribute urattr = new Attribute();
		Data urdata = new Data();
		if(inUsage == null)
		{
			urdata.setStringValue("Free Reuse");
		}
		else
		{
			urdata.setStringValue(inUsage);
		}
		urattr.setData(urdata);
		urattr.setName("usagerights");
		fwasset.getAttributes().add(urattr);
		
		List<String> cats = inAsset.getKeywords();
		for (String category : cats) {
			addCategory(fwasset, category);
		}
		
		//url for thumbnail
		String thumbpath = inUrlHome + getMediaArchive().getCatalogHome() + "/downloads/preview/thumb/" + inAsset.getSourcePath() + "/preview.jpg";
        Attribute thumb_attr = new Attribute();
        Data thumb_data = new Data();
        thumb_data.setStringValue(thumbpath);
        thumb_attr.setData(thumb_data);
        thumb_attr.setName("thumbnailurl");
        fwasset.getAttributes().add(thumb_attr);
        
        //url for original
        String originalpath = inUrlHome + getMediaArchive().getCatalogHome() + "/downloads/preview/cache/" + inAsset.getSourcePath() + "/preview.jpg";
        Attribute image_attr = new Attribute();
        Data image_data = new Data();
        image_data.setStringValue(originalpath);
        image_attr.setData(image_data);
        image_attr.setName("imageurl");
        fwasset.getAttributes().add(image_attr);
        
		//add to this site
		fwasset.getPublists().add(inSite);
		
		//prepare rest request
		Client client = getClient();
		String baseurl = getUrlBase();
		String url = baseurl + "/sites/" + inSite + "/types/" + inType + "/assets/" + fwasset.getId();
		WebResource wr = client.resource(url);
		String ticket = getTicket(url, getSSOConfig());
		wr = wr.queryParam("ticket", ticket);
		Builder builder = wr.header("Pragma", "auth-redirect=false");
		
		AssetBean ab = builder.put(AssetBean.class, fwasset);
		
		inAsset.setProperty("fatwireid", ab.getId());
		inAsset.setProperty("fatwireexportedby", inUser.getUserName());
		inAsset.setProperty("fatwireexportdate", new Date().toString());
		getMediaArchive().saveAsset(inAsset, inUser);
		
		return ab;
	}
	
	public List<Site> getSites()
	{
		String baseurl = getUrlBase();
		String url = baseurl + "/sites";
		
		Client client = getClient();
		WebResource wr = client.resource(url);
		//we need to set the ticket for authentication
		String ticket = getTicket(url, getSSOConfig());
		wr = wr.queryParam("ticket", ticket);
		//make sure we don't redirect
		Builder builder = wr.header("Pragma", "auth-redirect=false");
		//make the call
		SitesBean sb = builder.get(SitesBean.class);
		return sb.getSites();
	}
	
	public String getUrlBase()
	{
		String catalogid = getMediaArchive().getCatalogId();
		XmlFile settings = getXmlArchive().getXml("/"+ catalogid + "/configuration/fatwire.xml");
		Element config = settings.getElement("fatwire");
		String serverurl = config.element("server").getText();
		return serverurl;
	}
	
	public String getUserName()
	{
		String catalogid = getMediaArchive().getCatalogId();
		XmlFile settings = getXmlArchive().getXml("/"+ catalogid + "/configuration/fatwire.xml");
		Element config = settings.getElement("fatwire");
		String user = config.element("username").getText();
		return user;
	}
	
	public String getTicket(String inUrl, String inConfig)
	{
		String username = getUserName();
		User user = getUserManager().getUser(username);
		String password = getUserManager().decryptPassword(user);
		try {
			String ticket = SSO.getSSOSession(inConfig).getTicket(inUrl, username, password);
			return ticket;
		} catch (SSOException e) {
			System.out.println("Couldn't Get Ticket");
			e.printStackTrace();
		}
		return "";
	}
}
