package ru.local.qcatalog;

import android.app.*;
import android.content.Context;
import android.content.pm.*;
import android.os.Bundle;
import java.io.*;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.*;

/** Runs in a separate app/UID; never starts, stops, reads, or changes the real QCatalog app. */
public final class StorageChecks extends Instrumentation {
    private int checks;
    private Bundle arguments;
    private void require(boolean ok,String message) { checks++; if (!ok) throw new AssertionError(message); }
    @Override public void onCreate(Bundle args) { super.onCreate(args); arguments=args; start(); }
    private String digest(byte[] bytes) throws Exception {
        StringBuilder text=new StringBuilder(); for (byte b : MessageDigest.getInstance("SHA-256").digest(bytes)) text.append(String.format(Locale.ROOT,"%02x",b)); return text.toString();
    }
    @Override public void onStart() {
        Bundle result = new Bundle(); int outcome = Activity.RESULT_OK;
        try {
            Context isolated = getTargetContext();
            require(PrivateStore.loadUrl(isolated).isEmpty(),"Fresh storage has no URL");
            PrivateStore.saveUrl(isolated,"");
            require(PrivateStore.loadUrl(isolated).isEmpty(),"File import accepts an empty encrypted URL");
            File file = new File(isolated.getFilesDir(),"access.bin");
            require(file.length() == 29,"Empty URL has an IV and authentication tag");
            String example = "https://example.invalid/config?value=example";
            PrivateStore.saveUrl(isolated,example);
            require(PrivateStore.loadUrl(isolated).equals(example),"Non-empty URL survives encryption");
            byte[] tampered = Files.readAllBytes(file.toPath()); tampered[tampered.length-1] ^= 1;
            Files.write(file.toPath(),tampered);
            boolean rejected = false; try { PrivateStore.loadUrl(isolated); } catch (Exception expected) { rejected = true; }
            require(rejected,"Modified ciphertext is rejected");
            Files.write(file.toPath(),new byte[0]);
            boolean truncated = false; try { PrivateStore.loadUrl(isolated); } catch (IOException expected) { truncated = true; }
            require(truncated,"Truncated ciphertext returns a controlled error");
            File previousApk = new File(isolated.getFilesDir(),"previous.apk"), incomingApk = new File(isolated.getFilesDir(),"incoming.apk");
            if (previousApk.exists() && incomingApk.exists()) {
                PackageManager pm = isolated.getPackageManager();
                PackageInfo previous = pm.getPackageArchiveInfo(previousApk.getAbsolutePath(),PackageManager.GET_SIGNING_CERTIFICATES);
                PackageInfo incoming = pm.getPackageArchiveInfo(incomingApk.getAbsolutePath(),PackageManager.GET_SIGNING_CERTIFICATES);
                if (previous == null || incoming == null) throw new AssertionError("Unreadable APK fixtures");
                boolean conflict = false; try { SigningPreflight.check(isolated,previous); } catch (IOException expected) { conflict = true; }
                require(conflict,"Different certificate is rejected before installation");
                SigningPreflight.check(isolated,incoming);
                require(true,"Matching installed certificate passes preflight");
            }
            Catalog.Game fixture=new Catalog.Game("Resume fixture","Resume fixture v1","org.qcatalog.fixture",1,"test",1);
            QueueStore.add(isolated,fixture); QueueStore.update(isolated,fixture.id,"downloading","test"); QueueStore.interrupted(isolated);
            require(QueueStore.get(isolated,fixture.id).optString("state").equals("queued"),"Interrupted download is automatically queued");
            QueueStore.update(isolated,fixture.id,"paused","manual pause"); QueueStore.progress(isolated,fixture.id,"late progress"); QueueStore.interrupted(isolated);
            require(QueueStore.get(isolated,fixture.id).optString("state").equals("paused"),"Manual pause survives late progress and recovery");
            final int[] starts={0};
            android.content.ContextWrapper fake=new android.content.ContextWrapper(isolated) {
                @Override public android.content.ComponentName startForegroundService(android.content.Intent intent) { starts[0]++; return new android.content.ComponentName(getPackageName(),DownloadService.class.getName()); }
            };
            QueueStore.update(isolated,fixture.id,"queued","test"); new BootReceiver().onReceive(fake,new android.content.Intent(android.content.Intent.ACTION_BOOT_COMPLETED));
            require(starts[0]==1,"Boot completion restarts pending work");
            QueueStore.update(isolated,fixture.id,"paused","manual pause"); new BootReceiver().onReceive(fake,new android.content.Intent(android.content.Intent.ACTION_BOOT_COMPLETED));
            require(starts[0]==1,"Boot completion respects manual pause");
            if (arguments != null && arguments.containsKey("remote64")) {
                String remote=new String(android.util.Base64.decode(arguments.getString("remote64"),android.util.Base64.DEFAULT),java.nio.charset.StandardCharsets.UTF_8);
                long expected=Long.parseLong(arguments.getString("size")); Engine engine=new Engine(isolated);
                byte[] prefix=engine.runBytes(Arrays.asList("cat",remote,"--offset","0","--count","65536"),null,65536);
                require(digest(prefix).equals(arguments.getString("prefixSha")),"Remote prefix matches the existing verified APK");
                File probe=new File(isolated.getExternalFilesDir("resume-test"),"resume-probe.bin");
                try (ResumeFile resumed=new ResumeFile(probe,expected,"probe")) { resumed.write(prefix); }
                try (ResumeFile resumed=new ResumeFile(probe,expected,"probe")) {
                    require(resumed.position()==prefix.length,"Closing and reopening retains the exact saved position");
                    long position=resumed.position(); new Engine(isolated).runTo(Arrays.asList("cat",remote,"--offset",Long.toString(position),"--count",Long.toString(expected-position)),null,resumed,expected-position);
                    resumed.finish();
                }
                MessageDigest sha=MessageDigest.getInstance("SHA-256"); try (InputStream input=new FileInputStream(probe)) { byte[] data=new byte[65536]; int n; while((n=input.read(data))!=-1) sha.update(data,0,n); }
                StringBuilder sum=new StringBuilder(); for(byte b:sha.digest()) sum.append(String.format(Locale.ROOT,"%02x",b));
                require(sum.toString().equals(arguments.getString("sha")),"Resumed native range transfer matches the verified APK SHA-256");
                require(!new File(probe.getPath()+".qcatalog-part").exists(),"No partial file remains after success");
                File sourceDir=new File(isolated.getCacheDir(),"source"); sourceDir.mkdirs();
                byte[] block=new byte[65536]; for(int i=0;i<block.length;i++) block[i]=(byte)(i*31);
                File source=new File(sourceDir,"fixture.bin");
                try(FileOutputStream out=new FileOutputStream(source)) { for(int i=0;i<128;i++) out.write(block); }
                Engine localEngine=new Engine(isolated);
                org.json.JSONArray listing=Engine.fileListing(localEngine.run(Arrays.asList("lsjson",sourceDir.getAbsolutePath(),"--recursive","--files-only"),null));
                require(listing.length()==1,"Production file listing parses correctly");
                File destinationDir=isolated.getExternalFilesDir("resume-integration");
                File destination=new File(destinationDir,"fixture.bin");
                try(ResumeFile saved=new ResumeFile(destination,source.length(),Engine.downloadIdentity(listing.getJSONObject(0)))) { saved.write(block); }
                localEngine.downloadRelease(sourceDir.getAbsolutePath(),destinationDir,null);
                require(destination.length()==source.length(),"Production release downloader completes the partial file");
                require(Arrays.equals(Files.readAllBytes(source.toPath()),Files.readAllBytes(destination.toPath())),"Production resumed bytes exactly match the source");
                require(!new File(destination.getPath()+".qcatalog-part").exists(),"Production completion removes partial state");
            }
            result.putString("result","PASS " + checks + " storage/signing/queue/resume checks in a separate app; QCatalog and its downloads were not touched.");
        } catch (Throwable e) { result.putString("result","FAIL: " + e.getClass().getSimpleName() + ": " + e.getMessage()); outcome = Activity.RESULT_CANCELED; }
        finish(outcome,result);
    }
}
