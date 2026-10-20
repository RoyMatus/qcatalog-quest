package ru.local.qcatalog;

import java.net.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.json.*;

/** QLoader ApiClient.GetPopularityAsync source and live response verified; read-only public statistics. */
final class Popularity {
    static final String URL="https://qloader.5698452.xyz/api/v1/popularity";
    final Map<String,long[]> values=new HashMap<>();
    static Popularity parse(String json) throws Exception {
        Popularity result=new Popularity();JSONArray rows=new JSONArray(json);
        for(int i=0;i<rows.length();i++) { JSONObject row=rows.getJSONObject(i);String pkg=row.optString("package_name");if(!Catalog.safePackage(pkg))continue;
            long[] scores=new long[3];String[] periods={"1D","7D","30D"};for(int j=0;j<3;j++){Object value=row.opt(periods[j]);scores[j]=value instanceof Number && ((Number)value).doubleValue()>=0 ? ((Number)value).longValue():-1;}result.values.put(pkg,scores);
        }
        if(result.values.isEmpty())throw new IOException("Статистика QLoader пока пуста.");return result;
    }
    static Popularity fetch() throws Exception {
        HttpURLConnection http=(HttpURLConnection)Engine.validateUrl(URL).toURL().openConnection();http.setConnectTimeout(10000);http.setReadTimeout(12000);http.setUseCaches(false);http.setInstanceFollowRedirects(false);http.setRequestProperty("Cache-Control","no-store");http.setRequestProperty("User-Agent","QLoader/1.2.0");
        try { if(http.getResponseCode()!=200)throw new IOException("Рейтинг временно недоступен.");try(InputStream in=http.getInputStream()){return parse(new String(PrivateStore.read(in,2*1024*1024),StandardCharsets.UTF_8));} } finally{http.disconnect();}
    }
    long score(Catalog.Game game,String period,InstalledApps installed){String pkg=installed==null?game.pkg:installed.metadataPackage(game.pkg);long[] scores=values.get(GameDetails.originalPackage(pkg));return scores==null?-1:scores[period.equals("1D")?0:period.equals("7D")?1:2];}
    Comparator<Catalog.Game> order(String period,InstalledApps installed){return (a,b)->{int n=Long.compare(score(b,period,installed),score(a,period,installed));if(n==0)n=a.name.compareToIgnoreCase(b.name);if(n==0)n=a.release.compareTo(b.release);return n;};}
    String label(Catalog.Game game,String period,InstalledApps installed){long score=score(game,period,installed);return score<0?"Нет статистики скачиваний":"Индекс скачиваний: "+score;}
}
