importPackage( Packages.com.openedit.util );
importPackage( Packages.java.util );
importPackage( Packages.java.lang );
importPackage( Packages.com.openedit.modules.update );

var war = "http://dev.entermediasoftware.com/jenkins/job/extension-fatwire/lastSuccessfulBuild/artifact/deploy/extension-fatwire.zip";

var root = moduleManager.getBean("root").getAbsolutePath();
var web = root + "/WEB-INF";
var tmp = web + "/tmp";

log.add("1. GET THE LATEST WAR FILE");
var downloader = new Downloader();
downloader.download( war, tmp + "/extension-fatwire.zip");

log.add("2. UNZIP WAR FILE");
var unziper = new ZipUtil();
unziper.unzip(  tmp + "/extension-fatwire.zip",  tmp );

log.add("3. REPLACE LIBS");
var files = new FileUtils();
files.deleteMatch( web + "/lib/extension-fatwire*.jar");
files.deleteMatch( web + "/lib/wem*.jar");
files.deleteMatch( web + "/lib/spring*.jar");
files.deleteMatch( web + "/lib/unoil*.jar");
files.deleteMatch( web + "/lib/ridl*.jar");
files.deleteMatch( web + "/lib/rest-api*.jar");
files.deleteMatch( web + "/lib/jurt*.jar");
files.deleteMatch( web + "/lib/juh*.jar");
files.deleteMatch( web + "/lib/jsr311-api*.jar");
files.deleteMatch( web + "/lib/jodconverter-core*.jar");
files.deleteMatch( web + "/lib/jersey-core*.jar");
files.deleteMatch( web + "/lib/jersey-client*.jar");
files.deleteMatch( web + "/lib/cas-client-core*.jar");

files.copyFileByMatch( tmp + "/lib/wem*.jar", web + "/lib/");
files.copyFileByMatch( tmp + "/lib/extension-fatwire*.jar", web + "/lib/");
files.copyFileByMatch( tmp + "/lib/spring*.jar", web + "/lib/");
files.copyFileByMatch( tmp + "/lib/unoil*.jar", web + "/lib/");
files.copyFileByMatch( tmp + "/lib/ridl*.jar", web + "/lib/");
files.copyFileByMatch( tmp + "/lib/rest-api*.jar", web + "/lib/");
files.copyFileByMatch( tmp + "/lib/jurt*.jar", web + "/lib/");
files.copyFileByMatch( tmp + "/lib/juh*.jar", web + "/lib/");
files.copyFileByMatch( tmp + "/lib/jsr311-api*.jar", web + "/lib/");
files.copyFileByMatch( tmp + "/lib/jodconverter-core*.jar", web + "/lib/");
files.copyFileByMatch( tmp + "/lib/jersey-core*.jar", web + "/lib/");
files.copyFileByMatch( tmp + "/lib/jersey-client*.jar", web + "/lib/");
files.copyFileByMatch( tmp + "/lib/cas-client-core*.jar", web + "/lib/");

log.add("5. CLEAN UP");
files.deleteAll(tmp);

log.add("6. UPGRADE COMPLETED");