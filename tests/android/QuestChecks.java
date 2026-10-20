package ru.local.qcatalog;

import android.app.*;
import android.content.*;
import android.os.Bundle;
import org.json.JSONObject;
import java.io.*;
import java.nio.file.*;
import java.util.*;

/** Isolated fixtures by default; optional special-release validation expands an existing download. Never installs or deletes games. */
public final class QuestChecks extends Instrumentation {
    private int checks;
    private Bundle arguments;
    private void require(boolean ok,String message) { checks++; if (!ok) throw new AssertionError(message); }
    @Override public void onCreate(Bundle args) { super.onCreate(args);arguments=args;start(); }
    @Override public void onStart() {
        Bundle result = new Bundle(); Context target = getTargetContext();
        String fixtureId = Catalog.digest("qcatalog-tests-" + System.nanoTime());
        Set<String> preferences = new HashSet<>(); int outcome = Activity.RESULT_OK;
        try {
            File realQueue = new File(target.getFilesDir(),"queue.json");
            byte[] before = realQueue.exists() ? Files.readAllBytes(realQueue.toPath()) : null;
            File fixture = DownloadFiles.path(target.getCacheDir(),fixtureId); fixture.mkdirs();
            Context isolated = new ContextWrapper(target) {
                @Override public File getFilesDir() { File f = new File(fixture,"files"); f.mkdirs(); return f; }
                @Override public File getExternalFilesDir(String type) { File f = new File(fixture,"external/" + type); f.mkdirs(); return f; }
                @Override public SharedPreferences getSharedPreferences(String name,int mode) { String pref = fixtureId + "-" + name; preferences.add(pref); return target.getSharedPreferences(pref,mode); }
            };
            PrivateStore.saveUrl(isolated,"");
            require(PrivateStore.loadUrl(isolated).isEmpty(),"File imports can save an empty configuration URL");
            String example = "https://example.invalid/config";
            PrivateStore.saveUrl(isolated,example);
            require(PrivateStore.loadUrl(isolated).equals(example),"Encrypted URL survives a round trip");
            Files.write(new File(isolated.getFilesDir(),"access.bin").toPath(),new byte[0]);
            boolean malformed = false; try { PrivateStore.loadUrl(isolated); } catch (IOException e) { malformed = true; }
            require(malformed,"A truncated access file is rejected cleanly");
            Catalog.Game game = new Catalog.Game("Fixture","Fixture v1","org.qcatalog.fixture",1,"test",1);
            QueueStore.add(isolated,game);
            boolean blocked = false; try { QueueStore.clearHistory(isolated,null); } catch (IOException e) { blocked = true; }
            require(blocked,"Cleanup must reject queued tasks");
            QueueStore.update(isolated,game.id,"downloaded","Fixture");
            File dir = QueueStore.directory(isolated,QueueStore.get(isolated,game.id));
            Files.write(new File(dir,"fixture.apk").toPath(),new byte[31]);
            File sentinel = new File(fixture,"outside-sentinel"); Files.write(sentinel.toPath(),new byte[]{1,2,3});
            Files.createSymbolicLink(new File(dir,"outside-link").toPath(),sentinel.toPath());
            require(QueueStore.clearHistory(isolated,null) == 1,"One history row removed");
            require(new File(dir,"fixture.apk").exists(),"Clearing history preserves files");
            require(QueueStore.deleteFiles(isolated,null) == 31,"Orphaned download removed");
            require(sentinel.length() == 3,"Cleanup never follows symlinks outside the download");
            require(!dir.exists(),"Release directory removed");
            InstalledApps local = InstalledApps.read(target);
            InstalledApps.Entry self = local.get(target.getPackageName());
            require(self != null && self.version == 21,"Installed app version comes from PackageManager");
            require(InstalledApps.isNewer(self.version + 1,self.version),"Newer code is an update");
            require(!InstalledApps.isNewer(self.version,self.version),"Equal code is current");
            require(!InstalledApps.isNewer(self.version - 1,self.version),"Older code is not an update");
            require(InstalledApps.isNewer(4294967297L,4294967296L),"Long version codes are compared without truncation");
            List<Catalog.Game> catalogue = new Engine(target).cached();
            Set<String> installed = new HashSet<>(), updates = new HashSet<>();
            for (Catalog.Game item : catalogue) { InstalledApps.Entry present = local.get(item.pkg); if (present != null) { installed.add(present.pkg); if (local.newer(item)) updates.add(present.pkg); } }
            require(catalogue.size() > 0,"Existing catalogue is readable");
            boolean liveMedia=arguments!=null&&"true".equals(arguments.getString("liveMedia"));if(liveMedia)checks+=MediaChecks.run(target,true);
            String special=arguments.getString("specialReleaseId","");
            if(!special.isEmpty()) {
                if(!special.matches("[a-f0-9]{64}"))throw new IOException("Invalid special-release identifier");
                JSONObject saved=QueueStore.get(target,special);require(saved!=null,"Downloaded special release exists");
                // Use the existing download as input, but keep queue/progress fixtures isolated.
                Context inspectContext=new ContextWrapper(isolated){
                    @Override public File getExternalFilesDir(String type){return target.getExternalFilesDir(type);}
                };
                Installer.Bundle bundle=Installer.inspect(inspectContext,saved);
                require(bundle.sharedData&&!bundle.data.isEmpty(),"Actual special release produces a shared-storage data plan");
                require(bundle.apkInfo.getLongVersionCode()==saved.getLong("version"),"Actual APK version matches catalogue");
                long bytes=0;for(Map.Entry<File,String> file:bundle.data.entrySet()){require(file.getKey().isFile(),"Extracted data file exists");InstallPlan.destination("/sdcard/"+file.getValue(),saved.getString("pkg"));bytes+=file.getKey().length();}
                result.putString("specialRelease","PASS; data files="+bundle.data.size()+"; extracted bytes="+bytes+"; no game installed or deleted");
            }
            require(Arrays.equals(before,realQueue.exists() ? Files.readAllBytes(realQueue.toPath()) : null),"Real download history is unchanged");
            result.putString("result","PASS " + checks + " checks; catalogue=" + catalogue.size() + "; installed=" + installed.size() + "; updates=" + updates.size());
            result.putString("network",liveMedia?"One game media checked on request; no translation requests.":"No description, translation, or image request was made.");
        } catch (Throwable e) { result.putString("result","FAIL: " + e.getClass().getSimpleName() + ": " + e.getMessage()); outcome = Activity.RESULT_CANCELED; }
        finally {
            try { DownloadFiles.delete(target.getCacheDir(),fixtureId); } catch (Exception ignored) { }
            for (String pref : preferences) target.deleteSharedPreferences(pref);
        }
        finish(outcome,result);
    }
}
