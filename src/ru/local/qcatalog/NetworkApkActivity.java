package ru.local.qcatalog;

import android.app.*;
import android.content.*;
import android.content.pm.PackageInfo;
import android.net.*;
import android.os.Bundle;
import android.text.InputType;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import org.json.JSONObject;

/** Local-network file picker. Passwords and directory listings never persist. */
public final class NetworkApkActivity extends Activity {
    private final ExecutorService worker=Executors.newSingleThreadExecutor(),scanner=Executors.newFixedThreadPool(16);
    private final AtomicBoolean cancelled=new AtomicBoolean();private volatile NetworkShare remote;private volatile File partial;
    private LinearLayout rows;private TextView status,location;private String share="",path="";private volatile boolean busy;private int generation;private AlertDialog credentialsDialog;
    private Button button(String text){return Cards.button(this,text,false);}
    private void say(String text){runOnUiThread(()->{if(!isDestroyed())status.setText(text);});}
    @Override public void onCreate(Bundle saved){super.onCreate(saved);getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setPadding(Cards.dp(this,24),Cards.dp(this,16),Cards.dp(this,24),Cards.dp(this,16));root.setBackgroundColor(getColor(R.color.qc_background));setContentView(root);
        root.addView(Cards.text(this,"Установить APK с компьютера",28,getColor(R.color.qc_text)));
        ActionRow tools=new ActionRow(this);root.addView(tools);Button back=button("Назад"),search=button("Найти компьютеры"),address=button("Адрес компьютера");tools.addView(back);tools.addView(search);tools.addView(address);back.setOnClickListener(v->onBackPressed());search.setOnClickListener(v->discover());address.setOnClickListener(v->connectDialog(""));
        status=Cards.text(this,"Компьютер и шлем должны быть в одной локальной сети.",20,getColor(R.color.qc_text_secondary));root.addView(status);location=Cards.text(this,"",22,getColor(R.color.qc_text));root.addView(location);
        ScrollView scroll=new ScrollView(this);root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));rows=new LinearLayout(this);rows.setOrientation(LinearLayout.VERTICAL);scroll.addView(rows);discover();
    }
    private void row(String label,Runnable action){Button b=button(label);b.setGravity(Gravity.START|Gravity.CENTER_VERTICAL);rows.addView(b,new LinearLayout.LayoutParams(-1,-2));b.setOnClickListener(v->{if(!busy)action.run();});}
    private void discover(){if(busy)return;int token=++generation;if(remote!=null){remote.close();remote=null;}share=path="";rows.removeAllViews();location.setText("Компьютеры в сети");say("Ищу компьютеры с общими папками… Можно также указать адрес вручную.");
        Set<String> targets=new LinkedHashSet<>();ConnectivityManager manager=(ConnectivityManager)getSystemService(CONNECTIVITY_SERVICE);LinkProperties links=manager.getLinkProperties(manager.getActiveNetwork());
        if(links!=null)for(LinkAddress link:links.getLinkAddresses()){InetAddress ip=link.getAddress();if(ip instanceof Inet4Address&&ip.isSiteLocalAddress()){byte[] bytes=ip.getAddress();String prefix=(bytes[0]&255)+"."+(bytes[1]&255)+"."+(bytes[2]&255)+".";for(int i=1;i<255;i++)if(i!=(bytes[3]&255))targets.add(prefix+i);}}
        if(targets.isEmpty()){say("Нет подключения к локальной сети. Подключи Wi-Fi или введи адрес компьютера.");return;}java.util.concurrent.atomic.AtomicInteger left=new java.util.concurrent.atomic.AtomicInteger(targets.size()),found=new java.util.concurrent.atomic.AtomicInteger();
        for(String host:targets)scanner.execute(()->{try(java.net.Socket socket=new java.net.Socket()){socket.connect(new InetSocketAddress(host,445),350);found.incrementAndGet();runOnUiThread(()->{if(!isDestroyed()&&token==generation)row("Компьютер · "+host,()->connectDialog(host));});}catch(Exception ignored){}finally{if(left.decrementAndGet()==0)runOnUiThread(()->{if(!isDestroyed()&&token==generation)status.setText(found.get()==0?"Компьютеры не найдены. Проверь общий доступ или введи адрес вручную.":"Выбери компьютер. Для защищённых папок понадобится логин Windows.");});}});
    }
    private EditText field(LinearLayout layout,String hint,String value,boolean password){EditText input=new EditText(this);input.setSingleLine(true);input.setTextSize(22);input.setMinHeight(Cards.dp(this,64));input.setHint(hint);input.setText(value);input.setTextColor(getColor(R.color.qc_text));input.setHintTextColor(getColor(R.color.qc_text_secondary));input.setSaveEnabled(false);if(password)input.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PASSWORD);layout.addView(input);return input;}
    private void connectDialog(String preset){if(busy)return;LinearLayout layout=new LinearLayout(this);layout.setOrientation(LinearLayout.VERTICAL);layout.setPadding(Cards.dp(this,20),0,Cards.dp(this,20),0);EditText address=field(layout,"Имя / IP или \\\\компьютер\\папка",preset,false),user=field(layout,"Логин Windows (пусто — гостевой доступ)","",false),password=field(layout,"Пароль Windows, не PIN","",true);
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle("Общие папки компьютера").setView(layout).setPositiveButton("Подключиться",null).setNegativeButton("Отмена",null).create();credentialsDialog=dialog;dialog.setOnDismissListener(d->{password.setText("");credentialsDialog=null;});dialog.show();dialog.getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);Cards.sizeGameDialog(this,dialog);dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{
            NetworkAddress target;try{target=NetworkAddress.parse(address.getText().toString());}catch(Exception e){address.setError(e.getMessage());return;}
            String login=user.getText().toString().trim();char[] secret=new char[password.length()];password.getText().getChars(0,password.length(),secret,0);password.setText("");dialog.dismiss();busy=true;generation++;say("Подключаюсь к компьютеру…");NetworkShare client=new NetworkShare();if(remote!=null)remote.close();remote=client;worker.execute(()->{try{client.connect(target,login,secret);if(isDestroyed())return;share=target.share;path=target.path;if(share.isEmpty())showShares(client);else{client.open(share);showFiles(client);}}catch(Exception e){client.close();say(NetworkShare.error(e));runOnUiThread(()->{if(!isDestroyed()){share=path="";rows.removeAllViews();row("Ввести адрес и логин снова",()->connectDialog(preset));}});}finally{Arrays.fill(secret,'\0');busy=false;}});
        });
    }
    private void showShares(NetworkShare client)throws Exception{List<String> names=client.shares();runOnUiThread(()->{if(isDestroyed()||client!=remote)return;share=path="";rows.removeAllViews();location.setText("Общие папки");status.setText(names.isEmpty()?"Нет доступных общих папок. Можно ввести имя папки вручную.":"Выбери общую папку.");for(String name:names)row("Папка · "+name,()->openShare(name));row("Открыть папку по имени",this::manualShare);});}
    private void manualShare(){LinearLayout layout=new LinearLayout(this);layout.setOrientation(LinearLayout.VERTICAL);EditText name=field(layout,"Имя общей папки","",false);new AlertDialog.Builder(this).setTitle("Имя общей папки").setView(layout).setPositiveButton("Открыть",(d,w)->openShare(name.getText().toString().trim())).setNegativeButton("Отмена",null).show();}
    private void openShare(String name){if(busy||remote==null)return;NetworkShare client=remote;busy=true;say("Открываю папку…");worker.execute(()->{try{client.open(name);share=name;path="";showFiles(client);}catch(Exception e){say(NetworkShare.error(e));}finally{busy=false;}});}
    private void folder(String value){if(busy||remote==null)return;busy=true;path=value;NetworkShare client=remote;worker.execute(()->{try{showFiles(client);}catch(Exception e){say(NetworkShare.error(e));}finally{busy=false;}});}
    private void showFiles(NetworkShare client)throws Exception{List<NetworkShare.Entry> entries=client.list(path);String at=path;runOnUiThread(()->{if(isDestroyed()||client!=remote)return;rows.removeAllViews();location.setText(share+(at.isEmpty()?"":"\\"+at));status.setText("Выбери APK. Файл будет скопирован на шлем для установки.");row("← "+(at.isEmpty()?"Общие папки":"На папку выше"),()->{if(at.isEmpty())sharesAgain();else folder(NetworkAddress.parent(at));});for(NetworkShare.Entry entry:entries)row(entry.directory?"Папка · "+entry.name:entry.name+" · "+DownloadFiles.format(entry.bytes),()->{try{String next=NetworkAddress.child(at,entry.name);if(entry.directory)folder(next);else receive(next,entry);}catch(Exception e){say(e.getMessage());}});if(entries.isEmpty())rows.addView(Cards.text(this,"Здесь нет APK или вложенных папок.",22,getColor(R.color.qc_text_secondary)));});}
    private void sharesAgain(){if(busy)return;busy=true;NetworkShare client=remote;worker.execute(()->{try{showShares(client);}catch(Exception e){say(NetworkShare.error(e));}finally{busy=false;}});}
    private void receive(String filename,NetworkShare.Entry entry){if(busy||remote==null)return;busy=true;cancelled.set(false);NetworkShare client=remote;
        ProgressBar bar=new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);bar.setMax(1000);TextView amount=Cards.text(this,"Получаю APK…",22,getColor(R.color.qc_text));LinearLayout layout=new LinearLayout(this);layout.setOrientation(LinearLayout.VERTICAL);layout.addView(bar);layout.addView(amount);AlertDialog progress=new AlertDialog.Builder(this).setTitle(entry.name).setView(layout).setCancelable(false).setNegativeButton("Отмена",(d,w)->{cancelled.set(true);new Thread(client::close,"qcatalog-smb-cancel").start();}).create();progress.show();Cards.sizeGameDialog(this,progress);
        worker.execute(()->{try{
            File directory=getExternalFilesDir("network-imports");if(directory==null)throw new IOException("Хранилище шлема недоступно.");if(!directory.isDirectory()&&!directory.mkdirs())throw new IOException("Не удалось подготовить место для APK.");File target=new File(directory,UUID.randomUUID()+".apk");partial=target;client.download(filename,entry.bytes,target,cancelled,(done,total)->runOnUiThread(()->{if(!isDestroyed()&&progress.isShowing()){bar.setProgress((int)(1000*done/Math.max(1,total)));amount.setText(DownloadFiles.format(done)+" / "+DownloadFiles.format(total));}}));
            if(cancelled.get()||isDestroyed())throw new InterruptedIOException("Передача отменена.");PackageInfo info=getPackageManager().getPackageArchiveInfo(target.getAbsolutePath(),0);if(info==null||!Catalog.safePackage(info.packageName))throw new IOException("Файл не является корректным APK.");JSONObject job=QueueStore.addLocal(this,target,info);partial=null;runOnUiThread(()->{if(!isDestroyed()){progress.dismiss();setResult(RESULT_OK,new Intent().putExtra("jobId",job.optString("id")));finish();}});
        }catch(Exception e){say(cancelled.get()?"Передача отменена. Подключись к компьютеру снова.":NetworkShare.error(e));runOnUiThread(()->{if(!isDestroyed())progress.dismiss();});}finally{File temporary=partial;partial=null;if(temporary!=null&&temporary.exists())temporary.delete();busy=false;}});
    }
    @Override public void onBackPressed(){if(busy){cancelled.set(true);super.onBackPressed();return;}if(!share.isEmpty()){if(path.isEmpty())sharesAgain();else folder(NetworkAddress.parent(path));}else super.onBackPressed();}
    @Override public void onDestroy(){cancelled.set(true);generation++;NetworkShare current=remote;if(current!=null)current.close();worker.shutdownNow();scanner.shutdownNow();File temporary=partial;if(temporary!=null&&temporary.exists())temporary.delete();super.onDestroy();}
}
