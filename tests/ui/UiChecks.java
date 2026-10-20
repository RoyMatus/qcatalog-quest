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
        boolean night=(activity.getResources().getConfiguration().uiMode & android.content.res.Configuration.UI_MODE_NIGHT_MASK)==android.content.res.Configuration.UI_MODE_NIGHT_YES;
        require(activity.getColor(R.color.qc_background)==Color.parseColor(night?"#101823":"#F5F3EF"),"Background follows actual system night mode");
        result.putString("window",activity.getResources().getConfiguration().screenWidthDp+"x"+activity.getResources().getConfiguration().screenHeightDp+" dp; night="+night);
        require(activity.getResources().getDimension(R.dimen.qc_touch_min)==Cards.dp(activity,64),"Approved minimum target token");
        capture("catalog");
        runOnMainSync(()->{try{
            java.lang.reflect.Field gridField=MainActivity.class.getDeclaredField("grid");gridField.setAccessible(true);GridView grid=(GridView)gridField.get(activity);
            require(grid.getNumColumns()==5,"Expanded compact catalogue uses five columns");
            InstalledApps fixture=new InstalledApps();java.lang.reflect.Field appsField=InstalledApps.class.getDeclaredField("apps");appsField.setAccessible(true);
            @SuppressWarnings("unchecked") java.util.Map<String,InstalledApps.Entry> apps=(java.util.Map<String,InstalledApps.Entry>)appsField.get(fixture);
            for(int i=0;i<3;i++){android.content.pm.PackageInfo info=new android.content.pm.PackageInfo();info.packageName="org.qcatalog.fixture"+i;info.setLongVersionCode(i);apps.put(info.packageName,new InstalledApps.Entry(info));}
            java.lang.reflect.Field installedField=MainActivity.class.getDeclaredField("installed");installedField.setAccessible(true);installedField.set(activity,fixture);
            java.lang.reflect.Method select=MainActivity.class.getDeclaredMethod("selectSection",int.class);select.setAccessible(true);select.invoke(activity,1);
            require(grid.getAdapter().getCount()==3,"Library shows only installed fixture packages");
            int badges=0;View recycled=null;
            for(int i=0;i<3;i++){recycled=grid.getAdapter().getView(i,recycled,grid);LinearLayout card=(LinearLayout)recycled;TextView detail=(TextView)card.getChildAt(2);String label=detail.getText().toString();
                if(label.contains("✓")){badges++;android.text.Spanned marked=(android.text.Spanned)detail.getText();android.text.style.ForegroundColorSpan[] spans=marked.getSpans(0,marked.length(),android.text.style.ForegroundColorSpan.class);require(spans.length==1&&spans[0].getForegroundColor()==activity.getColor(R.color.qc_success),"Update check and label use theme green");}
                require(card.getMinimumHeight()>=Cards.dp(activity,64),"Compact game retains VR touch target");
            }
            require(badges==1,"Only newer catalogue version gets a check; recycled current/newer installs have none");
        }catch(Exception e){throw new RuntimeException(e);}});capture("library");
        runOnMainSync(()->{try{java.lang.reflect.Method select=MainActivity.class.getDeclaredMethod("selectSection",int.class);select.setAccessible(true);select.invoke(activity,2);}catch(Exception e){throw new RuntimeException(e);}});waitForIdleSync();capture("downloads");
        require(find(activity.getWindow().getDecorView(),"Пауза")!=null,"Download exposes labelled pause");
        runOnMainSync(()->{View pause=find(activity.getWindow().getDecorView(),"Пауза"),more=find(activity.getWindow().getDecorView(),"Подробнее");require(pause.getTop()==more.getTop()&&pause.getBottom()==more.getBottom(),"Pause and Details share exact top/bottom bounds");});
        require(find(activity.getWindow().getDecorView(),"68% · 68.0 МБ / 100.0 МБ")!=null,"Visible exact byte progress");
        QueueStore.update(c,job.id,"downloaded","Файлы готовы");runOnMainSync(()->{try{java.lang.reflect.Method update=MainActivity.class.getDeclaredMethod("updateQueue");update.setAccessible(true);update.invoke(activity);}catch(Exception e){throw new RuntimeException(e);}});settle();capture("ready");
        runOnMainSync(()->{View install=find(activity.getWindow().getDecorView(),"Установить"),more=find(activity.getWindow().getDecorView(),"Подробнее");require(install!=null&&install.getTop()==more.getTop()&&install.getBottom()==more.getBottom(),"Install and Details share exact top/bottom bounds");});
        QueueStore.stage(c,job.id,"installing","Установка APK",0,0,"Ожидаем результат Android");runOnMainSync(()->{try{java.lang.reflect.Method update=MainActivity.class.getDeclaredMethod("updateQueue");update.setAccessible(true);update.invoke(activity);}catch(Exception e){throw new RuntimeException(e);}});waitForIdleSync();capture("installing");
        require(find(activity.getWindow().getDecorView(),"Пауза")==null,"Android installation has no false pause action");
        require(find(activity.getWindow().getDecorView(),"68% · 68.0 МБ / 100.0 МБ")==null,"Android installation has no stale download percent");
        QueueStore.update(c,job.id,"installError","Файлы сохранены");runOnMainSync(()->{try{java.lang.reflect.Method update=MainActivity.class.getDeclaredMethod("updateQueue");update.setAccessible(true);update.invoke(activity);}catch(Exception e){throw new RuntimeException(e);}});waitForIdleSync();capture("error");
        require(find(activity.getWindow().getDecorView(),"Повторить")!=null,"Failure has direct retry action");
        runOnMainSync(()->{try{java.lang.reflect.Method select=MainActivity.class.getDeclaredMethod("selectSection",int.class);select.setAccessible(true);select.invoke(activity,0);java.lang.reflect.Method responsive=MainActivity.class.getDeclaredMethod("configureWidth",int.class);responsive.setAccessible(true);responsive.invoke(activity,1280);View root=((android.view.ViewGroup)activity.findViewById(android.R.id.content)).getChildAt(0);int w=Cards.dp(activity,1280),h=Cards.dp(activity,800);root.measure(View.MeasureSpec.makeMeasureSpec(w,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(h,View.MeasureSpec.EXACTLY));root.layout(0,0,w,h);root.measure(View.MeasureSpec.makeMeasureSpec(w,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(h,View.MeasureSpec.EXACTLY));root.layout(0,0,w,h);Bitmap image=Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888);root.draw(new Canvas(image));java.lang.reflect.Field gridField=MainActivity.class.getDeclaredField("grid");gridField.setAccessible(true);require(((GridView)gridField.get(activity)).getNumColumns()==4,"Compact grid uses four columns at 1280 dp");require(find(root,"Библиотека")!=null,"Wide sidebar contains labelled library");try(FileOutputStream out=new FileOutputStream(new File(c.getFilesDir(),"catalog-wide.png"))){image.compress(Bitmap.CompressFormat.PNG,100,out);}image.recycle();}catch(Exception e){throw new RuntimeException(e);}});
        require(true,"Wide layout rendered at the approved baseline");
        runOnMainSync(()->{
            for(int mode:new int[]{android.content.res.Configuration.UI_MODE_NIGHT_NO,android.content.res.Configuration.UI_MODE_NIGHT_YES}) {
                android.content.res.Configuration config=new android.content.res.Configuration(c.getResources().getConfiguration());config.uiMode=(config.uiMode&~android.content.res.Configuration.UI_MODE_NIGHT_MASK)|mode;config.fontScale=1.6f;
                Context themed=new android.view.ContextThemeWrapper(c.createConfigurationContext(config),R.style.QCatalogTheme);boolean dark=mode==android.content.res.Configuration.UI_MODE_NIGHT_YES;
                require(themed.getColor(R.color.qc_background)==Color.parseColor(dark?"#101823":"#F5F3EF"),"System light/dark background resolves correctly");
                ActionRow row=new ActionRow(themed);Button primary=Cards.button(themed,"Установить",true),secondary=Cards.button(themed,"Подробнее",false);row.addView(primary);row.addView(secondary);
                int width=Cards.dp(themed,1200);row.measure(View.MeasureSpec.makeMeasureSpec(width,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(Cards.dp(themed,400),View.MeasureSpec.AT_MOST));row.layout(0,0,row.getMeasuredWidth(),row.getMeasuredHeight());
                require(primary.getTop()==secondary.getTop()&&primary.getBottom()==secondary.getBottom(),"Large-font actions align in both themes");
                require(primary.getCurrentTextColor()==themed.getColor(R.color.qc_on_primary)&&secondary.getCurrentTextColor()==themed.getColor(R.color.qc_text),"Buttons have theme-specific text contrast");
                Bitmap bitmap=Bitmap.createBitmap(row.getMeasuredWidth(),row.getMeasuredHeight(),Bitmap.Config.ARGB_8888);Canvas canvas=new Canvas(bitmap);canvas.drawColor(themed.getColor(R.color.qc_background));row.draw(canvas);try(FileOutputStream out=new FileOutputStream(new File(c.getFilesDir(),dark?"theme-dark.png":"theme-light.png"))){bitmap.compress(Bitmap.CompressFormat.PNG,100,out);}catch(Exception e){throw new RuntimeException(e);}bitmap.recycle();
                row.measure(View.MeasureSpec.makeMeasureSpec(Cards.dp(themed,280),View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(Cards.dp(themed,600),View.MeasureSpec.AT_MOST));row.layout(0,0,row.getMeasuredWidth(),row.getMeasuredHeight());
                require(row.getOrientation()==LinearLayout.VERTICAL&&secondary.getTop()>=primary.getBottom(),"Narrow large-font actions stack without clipping");
            }
        });
        android.content.pm.ActivityInfo info=c.getPackageManager().getActivityInfo(new ComponentName(c,MainActivity.class),0);
        require(info.screenOrientation==android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE,"Panel launches in landscape");
        require(activity.getResources().getConfiguration().screenWidthDp>=1920,"Panel opens at full declared width on Quest");
        result.putString("result","PASS "+checks+" isolated UI checks; synthetic data only, no network permission");
    }catch(Throwable e){result.putString("result","FAIL "+e.getClass().getSimpleName()+": "+e.getMessage());outcome=Activity.RESULT_CANCELED;}finally{if(activity!=null)runOnMainSync(()->activity.finish());DownloadService.RUNNING.set(false);}finish(outcome,result);}
}
