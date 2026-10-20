package ru.local.qcatalog;

import java.io.*;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.apache.commons.compress.archivers.sevenz.*;

/** Runs unchanged on the host and in the isolated Quest test app. */
public final class SpecialInstallTest {
    static int checks;
    static void require(boolean ok,String message){checks++;if(!ok)throw new AssertionError(message);}
    interface Checked {void run() throws Exception;}
    static void rejects(Checked action,String message)throws Exception{boolean rejected=false;try{action.run();}catch(IOException expected){rejected=true;}require(rejected,message);}
    static void archive(File file,String path,byte[] bytes)throws Exception {
        try(SevenZOutputFile out=new SevenZOutputFile(file)){SevenZArchiveEntry entry=new SevenZArchiveEntry();entry.setName(path);entry.setSize(bytes.length);out.putArchiveEntry(entry);out.write(bytes);out.closeArchiveEntry();}
    }
    public static int run(File root)throws Exception {
        checks=0;if(!root.isDirectory()&&!root.mkdirs())throw new IOException("Fixture directory unavailable");
        File release=new File(root,"release");release.mkdirs();File apk=new File(release,"game.apk");Files.write(apk.toPath(),new byte[]{1});
        byte[] data="fixture game content".getBytes(StandardCharsets.UTF_8);File archive=new File(release,"xash.7z");archive(archive,"XASH/valve/pak0.pak",data);
        final long[] progress={0,0};File expanded=ArchiveFiles.expand(archive,release,(done,total)->{progress[0]=done;progress[1]=total;});
        require(Arrays.equals(data,Files.readAllBytes(new File(expanded,"XASH/valve/pak0.pak").toPath())),"7z extracts complete content");
        require(progress[0]==data.length&&progress[1]==data.length,"7z exposes exact progress");
        File reused=ArchiveFiles.expand(archive,release,(done,total)->{throw new IOException("Cache not reused");});require(reused.equals(expanded),"Complete verified extraction reused");
        File script=new File(release,"Install.txt");String commands="adb shell pm uninstall org.qcatalog.fixture\r\r\nadb shell rm -rf /sdcard/XASH/\r\r\nadb install -g -r \"game.apk\"\r\r\nadb push xash /sdcard/";
        Files.write(script.toPath(),commands.getBytes(StandardCharsets.UTF_8));List<File> roots=Arrays.asList(release,expanded);
        InstallPlan plan=InstallPlan.read(script,roots,"org.qcatalog.fixture");
        require(plan.apk.equals(apk),"Declared APK selected");require(plan.preservesOldData,"Legacy uninstall/remove translated without deletion");
        require(plan.files.size()==1&&plan.files.values().contains("XASH/valve/pak0.pak"),"Case-insensitive source maps XASH to shared storage");
        File sdcard=new File(root,"sdcard"),save=new File(sdcard,"XASH/save/slot1.sav");save.getParentFile().mkdirs();Files.write(save.toPath(),new byte[]{9});
        DataFiles.copy(plan.files,sdcard,(verify,done,total)->{});
        require(save.exists()&&Files.readAllBytes(save.toPath())[0]==9,"Existing saves preserved");require(Arrays.equals(data,Files.readAllBytes(new File(sdcard,"XASH/valve/pak0.pak").toPath())),"Game data copied and verified");
        require(InstallPlan.destination("/storage/emulated/0/XASH/valve","org.qcatalog.fixture").equals("XASH/valve"),"Shared-storage alias accepted");
        File obb=new File(release,"org.qcatalog.fixture/main.1.org.qcatalog.fixture.obb");obb.getParentFile().mkdirs();Files.write(obb.toPath(),data);
        Files.write(script.toPath(),"adb install game.apk\nadb push org.qcatalog.fixture /sdcard/Android/obb/".getBytes(StandardCharsets.UTF_8));
        InstallPlan obbPlan=InstallPlan.read(script,roots,"org.qcatalog.fixture");require(obbPlan.files.values().contains("Android/obb/org.qcatalog.fixture/"+obb.getName()),"Own OBB parent destination resolves before validation");
        for(String bad:Arrays.asList("../outside","/absolute","C:/outside","folder/../outside",".qcatalog-expanded/fake","folder//file"))rejects(()->InstallPlan.relative(bad),"Path traversal/absolute/reserved path rejected");
        for(String bad:Arrays.asList("/data/local/tmp/file","/sdcard/Android/data/org.other/file","/sdcard/DCIM/file","/sdcard/../escape"))rejects(()->InstallPlan.destination(bad,"org.qcatalog.fixture"),"Unsafe destination rejected");
        rejects(()->InstallPlan.words("adb push XASH /sdcard/; rm -rf /"),"Shell chaining rejected");
        Files.write(script.toPath(),"adb shell pm grant org.qcatalog.fixture android.permission.CAMERA".getBytes(StandardCharsets.UTF_8));rejects(()->InstallPlan.read(script,roots,"org.qcatalog.fixture"),"Unknown instructions fail explicitly");
        Files.write(script.toPath(),"adb install missing.apk".getBytes(StandardCharsets.UTF_8));rejects(()->InstallPlan.read(script,roots,"org.qcatalog.fixture"),"Missing APK fails explicitly");
        File evil=new File(release,"evil.7z");archive(evil,"../outside",data);rejects(()->ArchiveFiles.expand(evil,release,(d,t)->{}),"7z traversal rejected before writes");require(!new File(release,"outside").exists(),"No escaped archive file");
        File interrupted=new File(release,"interrupted.7z");archive(interrupted,"XASH/retry.dat",new byte[]{4,5,6});
        rejects(()->ArchiveFiles.expand(interrupted,release,(d,t)->{throw new IOException("Interrupted fixture");}),"Interrupted extraction fails");
        require(!new File(release,".qcatalog-expanded/"+ArchiveFiles.hash(interrupted)+".complete").exists(),"No completion marker after interruption");
        File resumed=ArchiveFiles.expand(interrupted,release,(d,t)->{});require(new File(resumed,"XASH/retry.dat").length()==3,"Interrupted extraction can retry from saved archive");
        File broken=new File(release,"broken.7z");Files.write(broken.toPath(),new byte[]{55,122,0,0});rejects(()->ArchiveFiles.expand(broken,release,(d,t)->{}),"Broken 7z rejected");
        return checks;
    }
    public static void main(String[] args)throws Exception{System.out.println("PASS "+run(Files.createTempDirectory("qcatalog-special-").toFile())+" special-install/archive checks");}
}
