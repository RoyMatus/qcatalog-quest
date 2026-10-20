package ru.local.qcatalog;
import java.io.IOException;

/** UNC / SMB addresses; credentials are never embedded in URLs. */
final class NetworkAddress {
    final String host,share,path;final int port;
    private NetworkAddress(String host,int port,String share,String path){this.host=host;this.port=port;this.share=share;this.path=path;}
    static NetworkAddress parse(String value)throws IOException{
        String clean=value.trim().replace('\\','/').replaceFirst("(?i)^smb://","").replaceFirst("^/+","");String[] parts=clean.split("/",3);String server=parts[0];int port=445;int colon=server.lastIndexOf(':');
        if(colon>=0){try{port=Integer.parseInt(server.substring(colon+1));}catch(Exception e){throw new IOException("Некорректный порт компьютера.");}server=server.substring(0,colon);}
        if(!server.matches("[A-Za-z0-9][A-Za-z0-9._-]{0,252}")||port<1||port>65535)throw new IOException("Укажи имя компьютера или локальный IP, например 192.168.1.10.");
        String share=parts.length>1?parts[1]:"",path=parts.length>2?parts[2]:"";if(!share.isEmpty())component(share);if(!path.isEmpty())for(String part:path.split("/",-1))component(part);
        return new NetworkAddress(server,port,share,path.replace('/','\\'));
    }
    static void component(String value)throws IOException{if(value.isEmpty()||value.equals(".")||value.equals("..")||value.matches("(?s).*[\\\\/:\u0000-\u001f].*"))throw new IOException("Некорректное имя сетевой папки или файла.");}
    static String child(String parent,String name)throws IOException{component(name);return parent.isEmpty()?name:parent+"\\"+name;}
    static String parent(String path){int at=path.lastIndexOf('\\');return at<0?"":path.substring(0,at);}
}
