package ru.local.qcatalog;
import android.app.*;
import android.os.Bundle;
import android.content.*;
import android.view.*;
import android.widget.*;
import android.graphics.*;
import java.io.*;
import java.nio.file.*;
import org.json.JSONObject;

/** Synthetic catalogue, no network permission, isolated UID; never reads personal configuration. */
public final class UiChecks extends Instrumentation {
    int checks;MainActivity activity;
    void require(boolean ok,String message){checks++;if(!ok)throw new AssertionError(message);}
    @Override public void onCreate(Bundle args){super.onCreate(args);start();}
    View find(View view,String text){if(!view.isShown())return null;if(view instanceof TextView && ((TextView)view).getText().toString().equals(text))return view;if(view instanceof ViewGroup){ViewGroup g=(ViewGroup)view;for(int i=0;i<g.getChildCount();i++){View found=find(g.getChildAt(i),text);if(found!=null)return found;}}return null;}
    void click(String label){runOnMainSync(()->{View v=find(activity.getWindow().getDecorView(),label);require(v!=null,"Visible action: "+label);v.performClick();});waitForIdleSync();}
    void capture(String name){settle();runOnMainSync(()->{try{View view=activity.getWindow().getDecorView();Bitmap b=Bitmap.createBitmap(view.getWidth(),view.getHeight(),Bitmap.Config.ARGB_8888);view.draw(new Canvas(b));try(FileOutputStream out=new FileOutputStream(new File(getTargetContext().getFilesDir(),name+".png"))){b.compress(Bitmap.CompressFormat.PNG,100,out);}b.recycle();}catch(Exception e){throw new RuntimeException(e);}});}
    void settle(){java.util.concurrent.CountDownLatch ready=new java.util.concurrent.CountDownLatch(1);runOnMainSync(()->activity.getWindow().getDecorView().postOnAnimation(()->activity.getWindow().getDecorView().postOnAnimation(ready::countDown)));try{if(!ready.await(5,java.util.concurrent.TimeUnit.SECONDS))throw new AssertionError("No UI frame");}catch(InterruptedException e){throw new RuntimeException(e);}}
    @Override public void onStart(){Bundle result=new Bundle();int outcome=Activity.RESULT_OK;try{
        Context c=getTargetContext();String header="Game Name;Release Name;Package Name;Version Code;Last Updated;Size (MB)\n";String[] names={"Beat Saber","Maestro","Metro Awakening","Walkabout Mini Golf","Puzzling Places","Moss"};StringBuilder catalogue=new StringBuilder(header);for(int i=0;i<names.length;i++)catalogue.append(names[i]+";"+names[i]+" v1;org.qcatalog.fixture"+i+";1;2026-10-20;512\n");Files.write(new File(c.getFilesDir(),"catalog.txt").toPath(),catalogue.toString().getBytes("UTF-8"));
        Catalog.Game job=new Catalog.Game("Beat Saber","Fixture download","org.qcatalog.fixture0",1,"test",512);QueueStore.add(c,job);QueueStore.update(c,job.id,"downloading","fixture");QueueStore.stage(c,job.id,"downloading","Скачивание",68*1024*1024,100*1024*1024,"Тестовый прогресс");
        // Do not start background service during this fixture run.
        DownloadService.RUNNING.set(true);
        Intent intent=new Intent(c,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);activity=(MainActivity)startActivitySync(intent);waitForIdleSync();
        require(activity.getResources().getColor(R.color.qc_background,null)==Color.parseColor("#F5F3EF"),"Exact approved background token");
        require(activity.getResources().getDimension(R.dimen.qc_touch_min)==Cards.dp(activity,64),"Approved minimum target token");
        capture("catalog");
        runOnMainSync(()->{try{java.lang.reflect.Method select=MainActivity.class.getDeclaredMethod("selectSection",int.class);select.setAccessible(true);select.invoke(activity,2);}catch(Exception e){throw new RuntimeException(e);}});waitForIdleSync();capture("downloads");
        require(find(activity.getWindow().getDecorView(),"Пауза")!=null,"Download exposes labelled pause");
        require(find(activity.getWindow().getDecorView(),"68% · 68.0 МБ / 100.0 МБ")!=null,"Visible exact byte progress");
        QueueStore.stage(c,job.id,"installing","Установка APK",0,0,"Ожидаем результат Android");runOnMainSync(()->{try{java.lang.reflect.Method update=MainActivity.class.getDeclaredMethod("updateQueue");update.setAccessible(true);update.invoke(activity);}catch(Exception e){throw new RuntimeException(e);}});waitForIdleSync();capture("installing");
        require(find(activity.getWindow().getDecorView(),"Пауза")==null,"Android installation has no false pause action");
        require(find(activity.getWindow().getDecorView(),"68% · 68.0 МБ / 100.0 МБ")==null,"Android installation has no stale download percent");
        QueueStore.update(c,job.id,"installError","Файлы сохранены");runOnMainSync(()->{try{java.lang.reflect.Method update=MainActivity.class.getDeclaredMethod("updateQueue");update.setAccessible(true);update.invoke(activity);}catch(Exception e){throw new RuntimeException(e);}});waitForIdleSync();capture("error");
        require(find(activity.getWindow().getDecorView(),"Повторить")!=null,"Failure has direct retry action");
        runOnMainSync(()->{try{java.lang.reflect.Method select=MainActivity.class.getDeclaredMethod("selectSection",int.class);select.setAccessible(true);select.invoke(activity,0);java.lang.reflect.Method responsive=MainActivity.class.getDeclaredMethod("configureWidth",int.class);responsive.setAccessible(true);responsive.invoke(activity,1280);View root=((android.view.ViewGroup)activity.findViewById(android.R.id.content)).getChildAt(0);int w=Cards.dp(activity,1280),h=Cards.dp(activity,800);root.measure(View.MeasureSpec.makeMeasureSpec(w,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(h,View.MeasureSpec.EXACTLY));root.layout(0,0,w,h);root.measure(View.MeasureSpec.makeMeasureSpec(w,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(h,View.MeasureSpec.EXACTLY));root.layout(0,0,w,h);Bitmap image=Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888);root.draw(new Canvas(image));java.lang.reflect.Field gridField=MainActivity.class.getDeclaredField("grid");gridField.setAccessible(true);require(((GridView)gridField.get(activity)).getNumColumns()==3,"Wide grid uses three columns");require(find(root,"Библиотека")!=null,"Wide sidebar contains labelled library");try(FileOutputStream out=new FileOutputStream(new File(c.getFilesDir(),"catalog-wide.png"))){image.compress(Bitmap.CompressFormat.PNG,100,out);}image.recycle();}catch(Exception e){throw new RuntimeException(e);}});
        require(true,"Wide layout rendered at the approved baseline");
        result.putString("result","PASS "+checks+" isolated UI checks; synthetic data only, no network permission");
    }catch(Throwable e){result.putString("result","FAIL "+e.getClass().getSimpleName()+": "+e.getMessage());outcome=Activity.RESULT_CANCELED;}finally{if(activity!=null)runOnMainSync(()->activity.finish());DownloadService.RUNNING.set(false);}finish(outcome,result);}
}
