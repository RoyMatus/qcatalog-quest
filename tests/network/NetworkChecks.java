package ru.local.qcatalog;
import android.app.*;
import android.content.*;
import android.os.Bundle;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.atomic.*;
import org.json.JSONObject;

/** Isolated SMB fixture and picker checks. Credentials are read privately, never reported. */
public final class NetworkChecks extends Instrumentation {
 private int checks;private NetworkApkActivity activity;
 private void require(boolean ok,String text){checks++;if(!ok)throw new AssertionError(text);}
 private void ui(Runnable action){AtomicReference<Throwable> error=new AtomicReference<>();runOnMainSync(()->{try{action.run();}catch(Throwable t){error.set(t);}});if(error.get()!=null)throw new AssertionError(error.get().getMessage());}
 private Object field(Object target,String name){try{java.lang.reflect.Field f=target.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(target);}catch(Exception e){throw new IllegalStateException(e);}}
 private View find(View root,String label){if(root instanceof TextView&&((TextView)root).getText().toString().equals(label))return root;if(root instanceof ViewGroup){ViewGroup parent=(ViewGroup)root;for(int i=0;i<parent.getChildCount();i++){View found=find(parent.getChildAt(i),label);if(found!=null)return found;}}return null;}
 private void edits(View root,List<EditText> result){if(root instanceof EditText)result.add((EditText)root);if(root instanceof ViewGroup){ViewGroup group=(ViewGroup)root;for(int i=0;i<group.getChildCount();i++)edits(group.getChildAt(i),result);}}
 private void click(String label)throws Exception{long deadline=System.currentTimeMillis()+25000;while(System.currentTimeMillis()<deadline){AtomicBoolean done=new AtomicBoolean();ui(()->{View view=find(activity.getWindow().getDecorView(),label);if(view!=null&&!(Boolean)field(activity,"busy")){view.performClick();done.set(true);}});if(done.get())return;Thread.sleep(100);}throw new AssertionError("Picker action not available: "+label);}
 private String digest(File file)throws Exception{StringBuilder result=new StringBuilder();for(byte b:MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(file.toPath())))result.append(String.format(Locale.ROOT,"%02x",b));return result.toString();}
 @Override public void onCreate(Bundle args){super.onCreate(args);start();}
 @Override public void onStart(){Bundle result=new Bundle();int outcome=Activity.RESULT_OK;Context c=getTargetContext();File settings=new File(c.getFilesDir(),"connection.json");try{
  JSONObject config=new JSONObject(new String(PrivateStore.read(settings,32768),"UTF-8"));NetworkAddress address=NetworkAddress.parse(config.getString("host"));String user=config.getString("user");char[] password=config.getString("password").toCharArray();File downloaded=new File(c.getCacheDir(),"smb-fixture.apk");
  NetworkShare client=new NetworkShare();try{client.connect(address,user,password);require(client.shares().contains(config.getString("share")),"Fixture share is enumerated over SMB2");client.open(config.getString("share"));require(client.list("").stream().anyMatch(e->e.directory&&e.name.equals("APK")),"Fixture folder appears");List<NetworkShare.Entry> entries=client.list("APK");require(entries.size()==1&&!entries.get(0).directory,"Only APK files appear alongside folders");NetworkShare.Entry apk=entries.get(0);client.download("APK\\"+apk.name,apk.bytes,downloaded,new AtomicBoolean(),(done,total)->{});require(downloaded.length()==apk.bytes&&digest(downloaded).equals(config.getString("sha256")),"Device receives byte-identical APK from the computer");
   android.content.pm.PackageInfo info=c.getPackageManager().getPackageArchiveInfo(downloaded.getAbsolutePath(),0);require(info!=null&&info.packageName.equals("ru.local.qcatalog.smbfixture"),"Downloaded fixture is a valid Android APK");File cancelled=new File(c.getCacheDir(),"cancelled.apk");boolean stopped=false;try{client.download("APK\\"+apk.name,apk.bytes,cancelled,new AtomicBoolean(true),(done,total)->{});}catch(InterruptedIOException expected){stopped=true;}require(stopped&&!cancelled.exists(),"Cancelled transfer removes its partial APK");
  }finally{client.close();Arrays.fill(password,'\0');}
  require(Arrays.equals((char[])field(client,"secret"),new char[((char[])field(client,"secret")).length]),"Connection close erases its password buffer");
  activity=(NetworkApkActivity)startActivitySync(new Intent(c,NetworkApkActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));click("Адрес компьютера");
  ui(()->{AlertDialog dialog=(AlertDialog)field(activity,"credentialsDialog");List<EditText> inputs=new ArrayList<>();edits(dialog.getWindow().getDecorView(),inputs);require(inputs.size()==3,"Picker presents computer, user and password fields");inputs.get(0).setText(config.optString("host"));inputs.get(1).setText(config.optString("user"));inputs.get(2).setText(config.optString("password"));require(!inputs.get(2).isSaveEnabled(),"Password field is excluded from saved UI state");dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick();require(inputs.get(2).length()==0,"Closing authentication dialog clears visible password");});
  click("Папка · "+config.getString("share"));click("Папка · APK");click("Network-Test.apk · "+DownloadFiles.format(config.getLong("bytes")));
  long deadline=System.currentTimeMillis()+30000;while(System.currentTimeMillis()<deadline&&QueueStore.all(c).isEmpty())Thread.sleep(100);List<JSONObject> jobs=QueueStore.all(c);require(jobs.size()==1,"File-picker selection creates one installation task");JSONObject job=jobs.get(0);require(job.getString("source").equals("network")&&job.getString("state").equals("downloaded"),"Imported APK uses normal downloaded-install flow");Installer.Bundle bundle=Installer.inspect(c,job);require(bundle.packageName.equals("ru.local.qcatalog.smbfixture")&&bundle.apkInfo.getLongVersionCode()==1,"Installer verifies actual package and version, independently of catalogue");require(digest(bundle.apk).equals(config.getString("sha256")),"Imported staging APK retains original digest");require(!job.toString().contains(config.getString("password")),"Installation history contains no SMB password");
  android.content.pm.PackageInstaller installer=c.getPackageManager().getPackageInstaller();int session=installer.createSession(new android.content.pm.PackageInstaller.SessionParams(android.content.pm.PackageInstaller.SessionParams.MODE_FULL_INSTALL));try(android.content.pm.PackageInstaller.Session stage=installer.openSession(session);OutputStream out=stage.openWrite("base.apk",0,bundle.apk.length());InputStream in=new FileInputStream(bundle.apk)){byte[] buffer=new byte[32768];int n;while((n=in.read(buffer))!=-1)out.write(buffer,0,n);stage.fsync(out);require(true,"Transferred APK is accepted into a real Android installer session");}finally{installer.abandonSession(session);}
  result.putString("result","PASS "+checks+" isolated SMB2/picker/APK checks; installer staging verified, no game installed or changed");
 }catch(Throwable e){result.putString("result","FAIL "+e.getClass().getSimpleName()+": "+(e instanceof AssertionError?e.getMessage():e instanceof Exception?NetworkShare.error((Exception)e):"network runtime failure"));outcome=Activity.RESULT_CANCELED;}finally{if(activity!=null)ui(()->activity.finish());settings.delete();}finish(outcome,result);}
}
