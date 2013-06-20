package org.entermedia.fatwire;

import java.io.File;
import java.io.IOException;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
import java.util.List;

import javax.ws.rs.core.MediaType;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.dom4j.Element;
import org.openedit.data.Searcher;
import org.openedit.data.SearcherManager;
import org.openedit.entermedia.Asset;
import org.openedit.entermedia.MediaArchive;
import org.openedit.xml.XmlArchive;
import org.openedit.xml.XmlFile;

import com.fatwire.rest.beans.AssetBean;
import com.fatwire.rest.beans.AssetInfo;
import com.fatwire.rest.beans.AssetsBean;
import com.fatwire.rest.beans.Association;
import com.fatwire.rest.beans.Associations;
import com.fatwire.rest.beans.Attribute;
import com.fatwire.rest.beans.Attribute.Data;
import com.fatwire.rest.beans.Blob;
import com.fatwire.rest.beans.Site;
import com.fatwire.rest.beans.SitesBean;
import com.fatwire.wem.sso.SSO;
import com.fatwire.wem.sso.SSOException;
import com.fatwire.wem.sso.SSOSession;
import com.openedit.hittracker.HitTracker;
import com.openedit.hittracker.SearchQuery;
import com.openedit.page.manage.PageManager;
import com.openedit.users.User;
import com.openedit.users.UserManager;
import com.openedit.util.PathUtilities;
import com.sun.jersey.api.client.Client;
import com.sun.jersey.api.client.UniformInterfaceException;
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
		String ticket = getTicket();
		//@todo change to multiticket
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
		String ticket = getTicket();
		
		//@todo - change to multiticket
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
	
	public AssetBean pushAsset(Asset inAsset, User inUser, String inUrlHome, String inUsage, String exportName, String outputFile) throws IOException
	{
		return pushAsset(inAsset, "CA", "Image_C", "Image", inUser, inUrlHome, inUsage, exportName, outputFile);
	}
	
	public AssetBean pushAsset(Asset inAsset, String inType, String inSubtype, User inUser, String inUrlHome, String inUsage) throws IOException
	{
		return pushAsset(inAsset, getSite(), inType, inSubtype, inUser, inUrlHome, inUsage, null, null);
	}
	
	public AssetBean pushAsset(Asset inAsset, String inSite, String inType, String inSubtype, User inUser, String inUrlHome, String inUsage, String inExportName, String inOutputFile) throws IOException
	{
		log.info("\npushAsset ("+(inAsset!=null ? inAsset.getId() : "null")+","+
				(inSite)+","+(inType)+","+(inSubtype)+","+(inUser!=null ? inUser.getName() : "null")+","+
				(inUrlHome)+","+(inUsage)+","+(inExportName)+","+(inOutputFile)+")\n");
		
		//no longer necessary
//		if(inAsset.get("fatwireid") != null)
//		{
//			//don't send the asset to fatwire again, maybe in future we can can sync fields
//			log.info("Not exporting asset, already exists on Fatwire");
//			return null;
//		}
		
		//convert our asset in to an asset bean
		AssetBean fwasset = new AssetBean();
		
		//id is overwritten when we publish to fatwire
//		fwasset.setId("entermedia:" + inAsset.getId());
		if (inExportName!=null && !inExportName.isEmpty())
			fwasset.setName(inExportName);
		else fwasset.setName(inAsset.getName());
		
		fwasset.setDescription(inAsset.get("assettitle"));
		fwasset.setSubtype(inSubtype);
		fwasset.getPublists().add(inSite);
		
		//required fields: source, thumbnailurl, imageurl, width, height, alttext, usagerights,sendtolexis
		//other fields: keywords, artist, caption, shootdate, startdate, endate
		
		String thumbpath = inUrlHome + getMediaArchive().getCatalogHome() + "/downloads/preview/thumb/" + inAsset.getSourcePath() + "/preview.jpg";
		
		//http://localhost:8080/emshare/views/modules/asset/downloads/preview/thumb/users/admin/2013/05/Power-Macintosh-G4-Cube.jpg/thumb.jpg
		thumbpath = inUrlHome + getMediaArchive().getCatalogHome() + "/views/modules/asset/downloads/preview/thumb/" + inAsset.getSourcePath() + "/thumb.jpg";
		System.out.println("thumb:\n"+thumbpath);
		
		String originalpath = inUrlHome + getMediaArchive().getCatalogHome() + "/downloads/preview/cache/" + inAsset.getSourcePath() + "/preview.jpg";
		
		//$home/${applicationid}/views/modules/asset/downloads/generated/${asset.sourcepath}/${convertpreset.outputfile}/${publishqueue.exportname}
		originalpath = inUrlHome + getMediaArchive().getCatalogHome() + "/views/modules/asset/downloads/generated/"
			+inAsset.getSourcePath() +"/"+inOutputFile+"/"+inExportName;
		System.out.println("originalpath:\n"+originalpath);
		
		String width = inAsset.get("width");
		String height = inAsset.get("height");
		
		System.out.println(width+","+height);
		
		String alttext = inAsset.get("headline");
		String usagerights = (inUsage == null || inUsage.isEmpty() ? "Free Reuse" : inUsage);
		String artist = inAsset.get("artist");
		StringBuilder buf = new StringBuilder();
		List<String> keywords = inAsset.getKeywords();
		for (String keyword : keywords) {
			buf.append(keyword).append(",");
		}
		String keywordlist = "0";
		if (!keywords.isEmpty())
		{
			keywordlist = buf.toString().substring(0, buf.toString().length()-1);
		}
		
		//list of attribute name:value pairs
        String[][] attributes = {
        		{"source","0"},//0 by default
        		{"thumbnailurl",thumbpath},
        		{"imageurl",originalpath},
        		{"width","int:"+width},
        		{"height","int:"+height},
        		{"alttext",alttext},
        		{"usagerights",usagerights},//default: Free Reuse
        		{"sendtolexis","0"},//0 default, otherwise y/n
        		{"keywords",keywordlist},
        		{"artist",artist}
//        		{"caption","0"},
//        		{"shootdate","0"},
//        		{"startdate","0"},
//        		{"endate","0"}
        		
        };
        for (int i=0; i<attributes.length;i++)
        {
        	String name = attributes[i][0];
        	String stringvalue = attributes[i][1];
        	boolean useInt = false;
        	if (stringvalue == null || stringvalue.isEmpty())
        		stringvalue = "0";
        	else if (stringvalue.startsWith("int:"))
        	{
        		String str = stringvalue.substring("int:".length());
        		try{
        			stringvalue = String.valueOf(Integer.parseInt(str));
        			useInt = true;
        		}
        		catch (Exception e){}
        	}
        	log.info("adding "+name+":"+stringvalue+" to fatwire assetbean");
        	Attribute sourceAssetAttribute = new Attribute();
            Data sourceAssetAttributeData = new Data();
            sourceAssetAttribute.setName(name);
            if (useInt)
            {
            	sourceAssetAttributeData.setIntegerValue(new Integer(Integer.parseInt(stringvalue)));
            }
            else 
            {
            	sourceAssetAttributeData.setStringValue(stringvalue);
            }
            sourceAssetAttribute.setData(sourceAssetAttributeData);
            fwasset.getAttributes().add(sourceAssetAttribute);
        }
		
		String multiticket = getTicket();
		log.info("generated ticket from sso: "+multiticket);
		String urlbase = getUrlBase();
		log.info("urlbase: "+urlbase);
		
		Client client = Client.create();
		WebResource webResource = client.resource(urlbase);
		webResource = webResource.queryParam("multiticket", multiticket);
        webResource = webResource.path("sites").path(inSite).path("types").path(inType).path("assets").path("0");
        Builder builder = webResource.accept(MediaType.APPLICATION_JSON);//APPLICATION_XML throws a security exception, use JSON
        builder = builder.header("X-CSRF-Token", multiticket);//this is required
        
		AssetBean ab = null;
		try
		{
			ab = builder.put(AssetBean.class, fwasset);
			printAssetBean(ab);
		}
		catch (UniformInterfaceException e)
		{
			log.error("UniformInterfaceException caught while issuing put(), "+e.getMessage(), e);
			//trim message
			String errorMessage = e.getMessage();
			if (errorMessage.contains("returned a response status of"))
			{
				errorMessage = "UniformInterfaceException "+errorMessage.substring(errorMessage.indexOf("returned a response status of"));
			}
			throw new IOException(errorMessage,e);
		}
		
		//no longer works - asset can be published as different presets
//		inAsset.setProperty("fatwireid", ab.getId());
//		inAsset.setProperty("fatwireexportedby", inUser.getUserName());
//		inAsset.setProperty("fatwireexportdate", new Date().toString());
//		getMediaArchive().saveAsset(inAsset, inUser);
		
		return ab;
	}
	
	public List<Site> getSites()
	{
		String baseurl = getUrlBase();
		String url = baseurl + "/sites";
		
		Client client = getClient();
		WebResource wr = client.resource(url);
		//we need to set the ticket for authentication
		String ticket = getTicket();//url, getSSOConfig());
		//@todo change to multiticket
		wr = wr.queryParam("ticket", ticket);
		//make sure we don't redirect
		Builder builder = wr.header("Pragma", "auth-redirect=false");
		//make the call
		SitesBean sb = builder.get(SitesBean.class);
		return sb.getSites();
	}
	
	public String getUrlBase()
	{
		SearcherManager sm = getMediaArchive().getSearcherManager();
		Searcher searcher = sm.getSearcher(getMediaArchive().getCatalogId(), "publishdestination");
		org.openedit.Data data = (org.openedit.Data) searcher.searchByField("publishtype", "fatwire");
		if (data == null) return null;
		return data.get("server");
	}
	
	public String getUserName()
	{
		SearcherManager sm = getMediaArchive().getSearcherManager();
		Searcher searcher = sm.getSearcher(getMediaArchive().getCatalogId(), "publishdestination");
		org.openedit.Data data = (org.openedit.Data) searcher.searchByField("publishtype", "fatwire");
		if (data == null) return null;
		return data.get("username");
	}
	
	public String getTicket()
	{
		String username = getUserName();
		log.info("fatwire username "+username);
		User user = getUserManager().getUser(username);
		String password = getUserManager().decryptPassword(user);
		String config = getSSOConfig();
//		log.info("Getting SSO Session ticket ("+config+", "+username+", "+password+")");
		try {
//			String ticket = SSO.getSSOSession(inConfig).getTicket(inUrl, username, password);
			String ticket = SSO.getSSOSession(config).getMultiTicket(username, password);//use multiticket instead
			return ticket;
		} catch (SSOException e) {
			log.error("unable to generate SSO Session Ticket, "+e.getMessage(),e);
		}
		return null;
	}
	
	
	
	
	
	
	
	//testing
	
	
	
	
	
	
	public static void testRead(String ticket)
	{
		Client client = Client.create();
		WebResource webResource = client.resource("http://dev11g.app.www.law.com:8080/cs/REST");
		webResource = webResource.queryParam("multiticket", ticket);
		String basicAssetSiteName = "CA";
        String basicAssetTypeName = "Image_C";
        String basicAssetId = "1196293542243";//1196293542243
        webResource = webResource.path("sites").path(basicAssetSiteName).path("types").path(basicAssetTypeName).path("assets").path(basicAssetId);
        Builder builder = webResource.accept(MediaType.APPLICATION_JSON);
        builder.header("X-CSRF-Token", ticket);
        try
        {
        	AssetBean resultAsset = builder.get(AssetBean.class);
        	printAssetBean(resultAsset);
        }
        catch (Exception e)
        {
        	e.printStackTrace();
        }
        
	}
	
	public static void printAssetBean(AssetBean bean)
	{
		StringBuilder buf = new StringBuilder();
		buf.append("id:\t").append(bean.getId()).append("\n");
		buf.append("name:\t").append(bean.getName()).append("\n");
		buf.append("createdby:\t").append(bean.getCreatedby()).append("\n");
		buf.append("description:\t").append(bean.getDescription()).append("\n");
		buf.append("status:\t").append(bean.getStatus()).append("\n");
		buf.append("subtype:\t").append(bean.getSubtype()).append("\n");
		buf.append("updatedby:\t").append(bean.getUpdatedby()).append("\n");
		buf.append(getAssociationsStr(bean.getAssociations()));
		buf.append(getAttributesStr(bean.getAttributes()));
		buf.append(getPublistsStr(bean.getPublists()));
		System.out.println(buf.toString().trim());
	}
	
	public static String getAssociationsStr(Associations associations)
	{
		StringBuilder buf = new StringBuilder();
		Iterator<Association> itr = associations.getAssociations().iterator();
		while(itr.hasNext())
		{
			buf.append("association:\t"+itr.next().getName()).append("\n");
		}
		return buf.toString();
	}
	
	public static String getAttributesStr(List<Attribute> list)
	{
		StringBuilder buf = new StringBuilder();
		Iterator<Attribute> itr = list.iterator();
		while(itr.hasNext())
		{
			Attribute attr = itr.next();
			Data data = attr.getData();
			String val = (data!=null ? data.getStringValue() : "[null]");
			if (val == null || val.equals("null"))
			{
				val = data.getIntegerValue()!=null ? data.getIntegerValue().toString() : "null";
			}
			buf.append("attribute:\t"+attr.getName()+"\t"+val).append("\n");
		}
		return buf.toString();
	}
	
	public static String getPublistsStr(List<String> list)
	{
		StringBuilder buf = new StringBuilder();
		Iterator<String> itr = list.iterator();
		while(itr.hasNext())
		{
			buf.append("publist:\t"+itr.next()).append("\n");
		}
		return buf.toString();
	}
	
	public static void testWrite(String ticket)
	{
		Client client = Client.create();
		WebResource webResource = client.resource("http://dev11g.app.www.law.com:8080/cs/REST");
		webResource = webResource.queryParam("multiticket", ticket);
		String basicAssetSiteName = "CA";
        String basicAssetTypeName = "Image_C";
        webResource = webResource.path("sites").path(basicAssetSiteName).path("types").path(basicAssetTypeName).path("assets").path("0");
        Builder builder = webResource.accept(MediaType.APPLICATION_JSON);
        
        //Add the CSRF header
        builder = builder.header("X-CSRF-Token", ticket);

        // Step 7: Instantiate and define the AssetBean for the asset
        AssetBean sourceAsset = new AssetBean();

        // Name - mandatory field
        sourceAsset.setName("Test REST PUT");

        // Description - optional field
        sourceAsset.setDescription("Test REST PUT description");
        
        //add subtype
        sourceAsset.setSubtype("Image");
        
        
        
        //attribute:source
        String[][] strs = {
        		{"source","0"},//?
        		{"thumbnailurl","http://www.law.com/images/128_pics/banks_lisa.jpg"},
        		{"imageurl","http://www.law.com/images/128_pics/banks_lisa.jpg"},
        		{"width","200"},
        		{"height","200"},
        		{"alttext","test alt text"},
        		{"usagerights","Free Reuse"},//? default: Free Reuse
        		{"sendtolexis","0"}//0 default, otherwise y/n
        };
        for (int i=0; i<strs.length;i++)
        {
        	String [] str = strs[i];
        	String key = str[0];
        	String val = str[1];
        	Attribute sourceAssetAttribute = new Attribute();
            Data sourceAssetAttributeData = new Data();
            sourceAssetAttribute.setName(key);
            sourceAssetAttributeData.setStringValue(val);
            sourceAssetAttribute.setData(sourceAssetAttributeData);
            sourceAsset.getAttributes().add(sourceAssetAttribute);
        }

        // Required: Must specify the site(s) for the asset
        sourceAsset.getPublists().add(basicAssetSiteName);

        // Step 8: Invoke the REST request to create the asset
        try
        {
        	AssetBean resultAsset = builder.put(AssetBean.class, sourceAsset);
        	printAssetBean(resultAsset);
        }
        catch (Exception e)
        {
        	e.printStackTrace();
        }
        

       
	}
	
	public static void main (String args[])
	{
		String config = "SSOConfig.xml";
		String username = "entermedia";
		String password = "dam";
		try {
			String ticket = SSO.getSSOSession(config).getMultiTicket(username, password);
			System.out.println(ticket);
			testRead(ticket);
//			testWrite(ticket);
		}catch (Exception e)
		{
			e.printStackTrace();
		}
	}
}
