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
files.deleteMatch( web + "/lib/commons-logging*.jar");
files.deleteMatch( web + "/lib/gson-*.jar");
//files.deleteMatch( web + "/lib/httpclient-*.jar");
//files.deleteMatch( web + "/lib/httpcore-*.jar");
files.deleteMatch( web + "/lib/jackson-core-*.jar");
files.deleteMatch( web + "/lib/jdo2-api-*.jar");
files.deleteMatch( web + "/lib/jetty-*.jar");
files.deleteMatch( web + "/lib/jsr*.jar");
files.deleteMatch( web + "/lib/transaction-api-*.jar");
files.deleteMatch( web + "/lib/extension-youtube*.jar");
files.deleteMatch( web + "/base/youtube/");


files.copyFileByMatch( tmp + "/lib/google-*.jar", web + "/lib/");
files.copyFileByMatch( tmp + "/lib/guava-*.jar", web + "/lib/");
files.copyFileByMatch( tmp + "/lib/tools.jar", web + "/lib/");
files.copyFileByMatch( tmp + "/lib/commons-logging*.jar", web + "/lib/");
files.copyFileByMatch( tmp + "/lib/gson-*.jar", web + "/lib/");
files.copyFileByMatch( tmp + "/lib/httpclient-*.jar", web + "/lib/");
files.copyFileByMatch( tmp + "/lib/httpcore-*.jar", web + "/lib/");
files.copyFileByMatch( tmp + "/lib/jackson-core-*.jar", web + "/lib/");
files.copyFileByMatch( tmp + "/lib/jdo2-api-*.jar", web + "/lib/");
files.copyFileByMatch( tmp + "/lib/jetty-*.jar", web + "/lib/");
files.copyFileByMatch( tmp + "/lib/jsr*.jar", web + "/lib/");
files.copyFileByMatch( tmp + "/lib/transaction-api-*.jar", web + "/lib/");
files.copyFileByMatch( tmp + "/lib/extension-youtube*.jar", web + "/lib/");
files.copyFileByMatch( tmp + "/youtube", web + "/base/youtube");

log.add("4. UPGRADE CART FILES");
files.deleteAll( root + "/WEB-INF/base/emfrontend/views/google");
files.copyFiles( tmp + "/youtube/views/google", root + "/WEB-INF/base/emfrontend/views/google");

log.add("5. CLEAN UP");
files.deleteAll(tmp);

log.add("6. UPGRADE COMPLETED");