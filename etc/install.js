importPackage( Packages.com.openedit.util );
importPackage( Packages.java.util );
importPackage( Packages.java.lang );
importPackage( Packages.com.openedit.modules.update );

var war = "http://dev.entermediasoftware.com/jenkins/job/extension-youtube/lastSuccessfulBuild/artifact/deploy/extension-youtube.zip";

var root = moduleManager.getBean("root").getAbsolutePath();
var web = root + "/WEB-INF";
var tmp = web + "/tmp";

log.add("1. GET THE LATEST WAR FILE");
var downloader = new Downloader();
downloader.download( war, tmp + "/extension-youtube.zip");

log.add("2. UNZIP WAR FILE");
var unziper = new ZipUtil();
unziper.unzip(  tmp + "/extension-youtube.zip",  tmp );

log.add("3. REPLACE LIBS");
var files = new FileUtils();
files.deleteMatch( web + "/lib/gdata-*.jar");
files.deleteMatch( web + "/lib/google-*.jar");
files.deleteMatch( web + "/lib/guava-*.jar");
files.deleteMatch( web + "/lib/tools.jar");
files.deleteMatch( web + "/WEB-INF/base/youtube/");

files.copyFileByMatch( tmp + "/lib/gdata-*.jar", web + "/lib/");
files.copyFileByMatch( tmp + "/lib/google-*.jar", web + "/lib/");
files.copyFileByMatch( tmp + "/lib/guava-*.jar", web + "/lib/");
files.copyFileByMatch( tmp + "/lib/tools.jar", web + "/lib/");
files.copyFileByMatch( tmp + "/youtube", web + "/WEB-INF/base/youtube");

log.add("5. CLEAN UP");
files.deleteAll(tmp);

log.add("6. UPGRADE COMPLETED");