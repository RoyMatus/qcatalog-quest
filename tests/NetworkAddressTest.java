package ru.local.qcatalog;
public final class NetworkAddressTest {
 static int checks;
 static void require(boolean ok){checks++;if(!ok)throw new AssertionError("Network address validation failed");}
 public static void main(String[] args)throws Exception{
  NetworkAddress a=NetworkAddress.parse("\\\\PC\\Games\\Folder");require(a.host.equals("PC")&&a.port==445&&a.share.equals("Games")&&a.path.equals("Folder"));
  a=NetworkAddress.parse("smb://pc.local:1445/APK");require(a.port==1445&&a.share.equals("APK"));require(NetworkAddress.parent("one\\two").equals("one"));require(NetworkAddress.child("one","two.apk").equals("one\\two.apk"));
  for(String value:new String[]{"user:password@host/Share","https://host/Share","host:0/Share","host:65536/Share","host/../file.apk","host/Share/../file.apk","host/Share/a\nb\nc.apk"}){boolean rejected=false;try{NetworkAddress.parse(value);}catch(java.io.IOException expected){rejected=true;}require(rejected);}
  for(String value:new String[]{"..","a\\b.apk","a/b.apk","bad\u0000.apk","a\nb\nc.apk"}){boolean rejected=false;try{NetworkAddress.component(value);}catch(java.io.IOException expected){rejected=true;}require(rejected);}
  System.out.println("Network address tests passed ("+checks+" checks)");
 }
}
