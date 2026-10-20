package ru.local.qcatalog;

import org.json.*;
import java.net.*;
import java.util.*;
import java.util.regex.*;

/** Public gallery references only. No HTML execution, cookies or local cache. */
final class MediaInfo {
    final List<String> images=new ArrayList<>();
    String trailer="",cover="",storeId="",note="QLoader";long trailerSize=-1;
    static String https(String value) {
        try{URI uri=Engine.validateUrl(value);String host=uri.getHost().toLowerCase(Locale.ROOT);
            if(host.equals("oculuscdn.com")||host.endsWith(".oculuscdn.com")||host.equals("fbcdn.net")||host.endsWith(".fbcdn.net")||host.equals("meta.com")||host.endsWith(".meta.com")||host.equals("cloud.dipvr.ru"))return uri.toString();
        }catch(Exception ignored){}return "";
    }
    static String reference(Object value){if(value instanceof String)return (String)value;if(value instanceof JSONObject){JSONObject j=(JSONObject)value;return j.optString("contentUrl",j.optString("url",j.optString("@id","")));}return "";}
    void addImage(String url){url=https(url);if(!url.isEmpty()&&!images.contains(url)&&images.size()<16)images.add(url);}
    static MediaInfo provider(JSONObject json){MediaInfo result=new MediaInfo();result.storeId=json.optString("id","");if(!result.storeId.matches("[0-9]{5,30}"))result.storeId="";
        result.cover=https(json.optString("cover_url"));result.addImage(result.cover);for(String field:Arrays.asList("screenshots","screenshot_urls")){JSONArray shots=json.optJSONArray(field);if(shots!=null)for(int i=0;i<shots.length();i++)result.addImage(reference(shots.opt(i)));}
        result.trailer=https(json.optString("trailer_url"));return result;
    }
    void metaPage(String html) throws Exception {
        if(storeId.isEmpty())return;
        Matcher tags=Pattern.compile("(?is)<meta\\b[^>]*>").matcher(html);
        while(tags.find()){Map<String,String> attributes=new HashMap<>();Matcher attr=Pattern.compile("(?is)([\\w:]+)\\s*=\\s*([\"'])(.*?)\\2").matcher(tags.group());while(attr.find())attributes.put(attr.group(1).toLowerCase(Locale.ROOT),attr.group(3));String property=attributes.containsKey("property")?attributes.get("property"):attributes.get("name");if("og:image".equalsIgnoreCase(property)){String refreshedCover=https(DescriptionText.plain(attributes.getOrDefault("content","")));if(!refreshedCover.isEmpty())cover=refreshedCover;}}
        Matcher scripts=Pattern.compile("(?is)<script\\b[^>]*type=[\"']application/ld\\+json[\"'][^>]*>(.*?)</script>").matcher(html);
        List<String> refreshed=new ArrayList<>();String video="";
        while(scripts.find()) {
            JSONObject data;try{data=new JSONObject(scripts.group(1));}catch(JSONException ignored){continue;}
            JSONArray graph=data.optJSONArray("@graph");if(graph==null)graph=new JSONArray().put(data);Map<String,JSONObject> nodes=new HashMap<>();
            for(int i=0;i<graph.length();i++){JSONObject node=graph.optJSONObject(i);if(node!=null)nodes.put(node.optString("@id"),node);}
            for(JSONObject node:nodes.values()) {
                String id=node.optString("@id");if(!id.endsWith("/SoftwareApplication/"+storeId)&&!id.endsWith("/SoftwareApplication/"+storeId+"/"))continue;
                Object image=node.opt("image");JSONArray images=image instanceof JSONArray?(JSONArray)image:new JSONArray().put(image);
                for(int i=0;i<images.length();i++){String ref=reference(images.opt(i));JSONObject resolved=nodes.get(ref);String url=https(resolved==null?ref:reference(resolved));if(!url.isEmpty()&&!refreshed.contains(url)&&refreshed.size()<16)refreshed.add(url);}
                for(JSONObject candidate:nodes.values())if(candidate.optString("@type").equals("VideoObject")&&reference(candidate.opt("about")).equals(id))video=https(candidate.optString("contentUrl"));
            }
        }
        if(!refreshed.isEmpty()){images.clear();images.addAll(refreshed);note="QLoader → галерея Meta";}
        if(!video.isEmpty())trailer=video;
    }
}
