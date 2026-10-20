package ru.local.qcatalog;

import android.app.*;
import android.content.*;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.*;
import android.provider.Settings;
import android.text.*;
import android.view.*;
import android.widget.*;
import org.json.JSONObject;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;

public final class MainActivity extends Activity {
    private int BG,PANEL,MINT,MUTED,INK;
    private final ExecutorService io=Executors.newSingleThreadExecutor(), detailIo=Executors.newFixedThreadPool(3);
    private final Set<GameDetails> openDetails=new HashSet<>();
    private final Handler handler=new Handler(Looper.getMainLooper());
    private final List<Catalog.Game> games=new ArrayList<>(),filtered=new ArrayList<>();
    private List<JSONObject> jobs=new ArrayList<>();
    private boolean downloads,resumed,checkingInstalled,loadingPopularity;
    private volatile boolean busy;
    private int section,columns=1;
    private long lastInstalledRead,seenCompletion;
    private TextView status,empty,versions,heading,notice;
    private EditText search;
    private Button cleanup,sort;
    private final List<Button> navigation=new ArrayList<>();
    private LinearLayout rail,body,tools;
    private Button sections,refreshControl; private int layoutMode=-1;
    private GridView grid;
    private InstalledApps installed;
    private Rows adapter;
    private CoverLoader covers;
    private String queueSignature="",sortMode="name";
    private Popularity popularity;
    private final Runnable hideNotice=()->{if(notice!=null)notice.setVisibility(View.GONE);};
    private final Runnable tick=new Runnable(){public void run(){if(resumed){updateQueue();if(SystemClock.elapsedRealtime()-lastInstalledRead>15000)refreshInstalled();handler.postDelayed(this,500);}}};

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        BG=getColor(R.color.qc_background);PANEL=getColor(R.color.qc_surface);MINT=getColor(R.color.qc_primary);MUTED=getColor(R.color.qc_text_secondary);INK=getColor(R.color.qc_text);
        getWindow().setStatusBarColor(BG);getWindow().setNavigationBarColor(BG);boolean night=(getResources().getConfiguration().uiMode & android.content.res.Configuration.UI_MODE_NIGHT_MASK)==android.content.res.Configuration.UI_MODE_NIGHT_YES;getWindow().getDecorView().setSystemUiVisibility(night?0:View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR|View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.MATCH_PARENT);
        if(!DownloadService.RUNNING.get())QueueStore.recover(this);
        covers=new CoverLoader(this);
        section=state==null?0:state.getInt("section",0);sortMode=getSharedPreferences("ui",0).getString("sort","name");
        LinearLayout root=row();root.setBackgroundColor(BG);setContentView(root);
        rail=column();rail.setVisibility(getResources().getConfiguration().screenWidthDp>=880?View.VISIBLE:View.GONE);rail.setPadding(dp(16),dp(24),dp(16),dp(16));rail.setBackgroundColor(PANEL);root.addView(rail,new LinearLayout.LayoutParams(dp(248),-1));
        TextView logo=text("QCatalog",32,INK);logo.setTypeface(Typeface.DEFAULT,Typeface.BOLD);logo.setPadding(dp(16),0,0,dp(32));rail.addView(logo);
        String[] labels={"Каталог","Библиотека","Загрузки","Обновления"};int[] icons={R.drawable.qc_ic_catalog,R.drawable.qc_ic_library,R.drawable.qc_ic_download,R.drawable.qc_ic_update};
        for(int i=0;i<labels.length;i++){final int target=i;Button b=button(labels[i]);b.setGravity(Gravity.START|Gravity.CENTER_VERTICAL);b.setCompoundDrawablesWithIntrinsicBounds(icons[i],0,0,0);b.setCompoundDrawablePadding(dp(12));LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.bottomMargin=dp(16);rail.addView(b,lp);navigation.add(b);b.setOnClickListener(v->selectSection(target));}
        rail.addView(new View(this),new LinearLayout.LayoutParams(1,0,1));Button settings=button("Настройки");rail.addView(settings);settings.setOnClickListener(v->settings());
        body=column();body.setPadding(dp(32),dp(20),dp(32),dp(12));root.addView(body,new LinearLayout.LayoutParams(0,-1,1));
        LinearLayout top=row();body.addView(top);sections=button("Разделы");sections.setVisibility(getResources().getConfiguration().screenWidthDp<880?View.VISIBLE:View.GONE);top.addView(sections);sections.setOnClickListener(v->new AlertDialog.Builder(this).setTitle("Разделы").setItems(new String[]{"Каталог","Библиотека","Загрузки","Обновления","Настройки"},(d,w)->{if(w==4)settings();else selectSection(w);}).show());
        heading=text("Каталог",32,INK);heading.setTypeface(Typeface.DEFAULT,Typeface.BOLD);top.addView(heading,new LinearLayout.LayoutParams(0,-2,1));
        refreshControl=button("Обновить каталог");if(getResources().getConfiguration().screenWidthDp<880)refreshControl.setVisibility(View.GONE);top.addView(refreshControl);refreshControl.setOnClickListener(v->refresh());
        status=text("Подключи QLoader в настройках.",20,MUTED);status.setPadding(0,dp(10),0,dp(8));body.addView(status);
        tools=row();body.addView(tools);search=new EditText(this);search.setSingleLine(true);search.setTextSize(22);search.setTextColor(INK);search.setHintTextColor(MUTED);search.setHint("Найти игру");search.setPadding(dp(16),dp(12),dp(16),dp(12));search.setBackground(Cards.background(this,R.drawable.qc_bg_surface));tools.addView(search,new LinearLayout.LayoutParams(0,dp(64),1));
        sort=button(sortLabel());LinearLayout.LayoutParams sl=new LinearLayout.LayoutParams(-2,-2);sl.leftMargin=dp(16);tools.addView(sort,sl);if(getResources().getConfiguration().screenWidthDp<880){tools.setOrientation(LinearLayout.VERTICAL);search.setLayoutParams(new LinearLayout.LayoutParams(-1,dp(64)));LinearLayout.LayoutParams narrow=new LinearLayout.LayoutParams(-1,-2);narrow.topMargin=dp(8);sort.setLayoutParams(narrow);}sort.setOnClickListener(v->sorting());
        search.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence s,int st,int c,int a){}public void onTextChanged(CharSequence s,int st,int b,int c){filter();}public void afterTextChanged(Editable e){}});
        versions=text("Проверяю установленные версии…",20,MUTED);versions.setPadding(0,dp(12),0,dp(12));body.addView(versions);
        cleanup=button("Очистка файлов и истории");body.addView(cleanup);cleanup.setOnClickListener(v->cleanupMenu(null));
        FrameLayout content=new FrameLayout(this);body.addView(content,new LinearLayout.LayoutParams(-1,0,1));
        grid=new GridView(this);grid.setVerticalSpacing(dp(16));grid.setHorizontalSpacing(dp(16));grid.setStretchMode(GridView.STRETCH_COLUMN_WIDTH);grid.setClipToPadding(false);grid.setPadding(0,dp(8),0,dp(12));content.addView(grid);
        empty=text("Подключи QLoader в настройках.",26,MUTED);empty.setGravity(Gravity.CENTER);content.addView(empty,new FrameLayout.LayoutParams(-1,-1));grid.setEmptyView(empty);
        adapter=new Rows();grid.setAdapter(adapter);grid.setOnItemClickListener((p,v,position,id)->{if(downloads)jobDetails(jobs.get(position));else gameDetails(filtered.get(position));});
        notice=text("",22,getColor(R.color.qc_success));notice.setPadding(dp(24),dp(16),dp(24),dp(16));notice.setBackgroundColor(getColor(R.color.qc_success_surface));notice.setVisibility(View.GONE);body.addView(notice);
                root.addOnLayoutChangeListener((v,l,t,r,b,ol,ot,or,ob)->configureWidth(Math.round((r-l)/getResources().getDisplayMetrics().density)));
        configureWidth(getResources().getConfiguration().screenWidthDp);
        try{games.addAll(new Engine(this).cached());if(!games.isEmpty())status.setText("Каталог · "+games.size()+" записей");}catch(Exception e){showStatus(DownloadService.safeMessage(e));}
        selectSection(section);if(state!=null)search.setText(state.getString("query",""));
        File bootstrap=new File(getFilesDir(),"bootstrap.json");if(bootstrap.exists())try{JSONObject data=new JSONObject(new String(PrivateStore.read(bootstrap,32768),StandardCharsets.UTF_8));String address=data.getString("configUrl");Engine.validateUrl(address);PrivateStore.saveUrl(this,address);if(!bootstrap.delete())throw new IOException("Не удалось удалить временный файл подключения.");refresh();}catch(Exception e){showStatus(DownloadService.safeMessage(e));}
        if(!sortMode.equals("name"))loadPopularity();
    }
    @Override protected void onSaveInstanceState(Bundle out){out.putInt("section",section);out.putString("query",search==null?"":search.getText().toString());super.onSaveInstanceState(out);}
    @Override protected void onResume(){super.onResume();resumed=true;handler.post(tick);refreshInstalled();if(!DownloadService.RUNNING.get())for(JSONObject job:QueueStore.all(this))if(QueueStore.pending(job)){DownloadService.start(this);break;}}
    @Override protected void onPause(){resumed=false;handler.removeCallbacks(tick);super.onPause();}
    @Override protected void onDestroy(){handler.removeCallbacks(hideNotice);for(GameDetails d:openDetails)d.close();openDetails.clear();if(covers!=null)covers.close();detailIo.shutdownNow();io.shutdown();super.onDestroy();}
    private int dp(int n){return Cards.dp(this,n);}
    private LinearLayout column(){LinearLayout v=new LinearLayout(this);v.setOrientation(LinearLayout.VERTICAL);return v;}
    private LinearLayout row(){LinearLayout v=new LinearLayout(this);v.setOrientation(LinearLayout.HORIZONTAL);v.setGravity(Gravity.CENTER_VERTICAL);return v;}
    private TextView text(String value,int size,int color){return Cards.text(this,value,Math.max(20,size),color==Color.WHITE?INK:color);}
    private Button button(String value){return Cards.button(this,value,false);}
    private void showStatus(String value){runOnUiThread(()->{if(!isDestroyed())status.setText(value);});}
    private void settings(){new AlertDialog.Builder(this).setTitle("Настройки QCatalog").setItems(new String[]{"Подключение QLoader","Разрешения установки","Обновить каталог","Очистка файлов и истории"},(d,w)->{if(w==0)connection();else if(w==1)permissions();else if(w==2)refresh();else cleanupMenu(null);}).setNegativeButton("Закрыть",null).show();}
    private void selectTab(boolean value){selectSection(value?2:0);}
    private void selectSection(int value){section=value;downloads=value==2;if(grid==null)return;int width=Math.round(getWindow().getDecorView().getWidth()/getResources().getDisplayMetrics().density);columns=downloads?1:cardColumns(width);grid.setNumColumns(columns);search.setVisibility(downloads?View.GONE:View.VISIBLE);sort.setVisibility(downloads?View.GONE:View.VISIBLE);cleanup.setVisibility(downloads?View.VISIBLE:View.GONE);for(int i=0;i<navigation.size();i++)navigation.get(i).setBackground(Cards.background(this,i==section?R.drawable.qc_bg_selected:R.drawable.qc_bg_surface));heading.setText(new String[]{"Каталог","Библиотека","Загрузки","Обновления"}[section]);queueSignature="";updateQueue();filter();grid.smoothScrollToPosition(0);}
    private void updateQueue(){try{
        List<JSONObject> all=QueueStore.all(this);jobs=new ArrayList<>();for(JSONObject job:all)if(!Arrays.asList("installed","removed").contains(job.optString("state")))jobs.add(job);
        String signature=jobs.toString();navigation.get(2).setText("Загрузки"+(jobs.isEmpty()?"":" · "+jobs.size()));
        if(!signature.equals(queueSignature)){queueSignature=signature;adapter.notifyDataSetChanged();refreshInstalled();}
        long at=getSharedPreferences("state",0).getLong("completionAt",0);if(at>seenCompletion){seenCompletion=at;if(System.currentTimeMillis()-at<6000){notice.setText(getSharedPreferences("state",0).getString("completion",""));notice.setVisibility(View.VISIBLE);handler.removeCallbacks(hideNotice);handler.postDelayed(hideNotice,Math.max(1,6000-(System.currentTimeMillis()-at)));}}
    }catch(Exception e){showStatus(DownloadService.safeMessage(e));}}
    private String sortLabel(){return sortMode.equals("name")?"Сортировка: А–Я":"Популярные · "+(sortMode.equals("1D")?"сутки":sortMode.equals("7D")?"7 дней":"30 дней");}
    private void sorting(){new AlertDialog.Builder(this).setTitle("Сортировка игр").setItems(new String[]{"По названию · А–Я","По скачиваниям · 30 дней","По скачиваниям · 7 дней","По скачиваниям · сутки"},(d,w)->{sortMode=new String[]{"name","30D","7D","1D"}[w];getSharedPreferences("ui",0).edit().putString("sort",sortMode).apply();sort.setText(sortLabel());if(!sortMode.equals("name"))loadPopularity();filter();grid.smoothScrollToPosition(0);}).setNegativeButton("Закрыть",null).show();}
    private void loadPopularity(){if(loadingPopularity || popularity!=null)return;loadingPopularity=true;showStatus("Загружаю рейтинг скачиваний QLoader…");detailIo.execute(()->{try{Popularity result=Popularity.fetch();runOnUiThread(()->{if(!isDestroyed()){popularity=result;filter();showStatus("Популярность скачиваний · QLoader · без личной статистики");}});}catch(Exception e){showStatus("Рейтинг скачиваний недоступен. Показан порядок А–Я; выбери сортировку снова для повтора.");}finally{loadingPopularity=false;}});}
    private void filter(){if(search==null||adapter==null)return;String query=search.getText().toString().toLowerCase(Locale.ROOT).trim();filtered.clear();Map<String,Long> latest=new HashMap<>();Set<String> present=new HashSet<>(),updates=new HashSet<>();for(Catalog.Game g:games)latest.put(g.pkg,Math.max(latest.containsKey(g.pkg)?latest.get(g.pkg):0,g.version));
        for(Catalog.Game game:games){InstalledApps.Entry current=installed==null?null:installed.get(game.pkg);if(current!=null)present.add(current.pkg);if(current!=null&&installed.newer(game))updates.add(current.pkg);if((section==1||section==3)&&(current==null||game.version<latest.get(game.pkg)))continue;if(section==3&&!installed.newer(game))continue;if(query.isEmpty()||(game.name+" "+game.release+" "+game.pkg).toLowerCase(Locale.ROOT).contains(query))filtered.add(game);}
        if(popularity!=null&&!sortMode.equals("name"))filtered.sort(popularity.order(sortMode,installed));else filtered.sort(Comparator.comparing(g->g.name.toLowerCase(Locale.ROOT)));
        versions.setText(downloads?"Задачи и их текущие этапы":"На шлеме: "+present.size()+" · обновления: "+updates.size()+(sortMode.equals("name")?"":" · "+(popularity==null?"рейтинг недоступен, А–Я":"по скачиваниям QLoader")));
        if(downloads)empty.setText("Активных загрузок нет");else empty.setText(section==3?"Все игры обновлены":section==1?"Установленные игры не найдены в каталоге":games.isEmpty()?"Подключи QLoader в настройках":"Ничего не найдено");
        navigation.get(3).setText("Обновления"+(updates.isEmpty()?"":" · "+updates.size()));adapter.notifyDataSetChanged();}
    private void refreshInstalled(){if(checkingInstalled||isDestroyed())return;checkingInstalled=true;lastInstalledRead=SystemClock.elapsedRealtime();io.execute(()->{try{InstalledApps value=InstalledApps.read(this);runOnUiThread(()->{if(!isDestroyed()){boolean changed=installed==null||!installed.sameVersions(value);installed=value;if(changed)filter();}});}catch(Exception e){showStatus("Не удалось прочитать установленные версии.");}finally{runOnUiThread(()->checkingInstalled=false);}});}
    private final class Tile {
        final LinearLayout card=column(),actions=new ActionRow(MainActivity.this);final ImageView cover=new ImageView(MainActivity.this);final TextView title=text("",22,INK),detail=text("",20,MUTED),amount=text("",20,INK);final ProgressBar bar=new ProgressBar(MainActivity.this,null,0,R.style.QCatalogProgress);
        Tile(){card.setPadding(dp(12),dp(12),dp(12),dp(12));Cards.target(MainActivity.this,card,R.drawable.qc_bg_surface);title.setMaxLines(2);title.setEllipsize(TextUtils.TruncateAt.END);title.setTypeface(Typeface.DEFAULT,Typeface.BOLD);detail.setMaxLines(4);detail.setEllipsize(TextUtils.TruncateAt.END);cover.setScaleType(ImageView.ScaleType.CENTER_CROP);cover.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);cover.setImageResource(R.drawable.qc_cover_placeholder);card.addView(cover,new LinearLayout.LayoutParams(-1,dp(100)));card.addView(title);card.addView(detail);LinearLayout.LayoutParams bp=new LinearLayout.LayoutParams(-1,dp(12));bp.topMargin=dp(16);bp.bottomMargin=dp(12);card.addView(bar,bp);bar.setProgressTintList(android.content.res.ColorStateList.valueOf(MINT));bar.setProgressBackgroundTintList(android.content.res.ColorStateList.valueOf(getColor(R.color.qc_surface_alt)));bar.setIndeterminateTintList(android.content.res.ColorStateList.valueOf(MINT));card.addView(amount);card.addView(actions);card.setTag(this);cover.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener(){public void onViewAttachedToWindow(View v){}public void onViewDetachedFromWindow(View v){covers.release(cover);}});}
    }
    private final class Rows extends BaseAdapter {
        public int getCount(){return downloads?jobs.size():filtered.size();}public Object getItem(int p){return downloads?jobs.get(p):filtered.get(p);}public long getItemId(int p){return p;}
        public View getView(int p,View recycled,ViewGroup parent){Tile tile=recycled==null?new Tile():(Tile)recycled.getTag();tile.actions.removeAllViews();tile.actions.setVisibility(downloads?View.VISIBLE:View.GONE);tile.bar.setVisibility(View.GONE);tile.amount.setVisibility(View.GONE);tile.card.setOnClickListener(null);tile.cover.setVisibility(downloads?View.GONE:View.VISIBLE);tile.detail.setTextColor(MUTED);tile.title.setTextSize(downloads?26:22);int inset=dp(downloads?16:12);tile.card.setPadding(inset,inset,inset,inset);
            if(downloads){covers.release(tile.cover);JSONObject job=jobs.get(p);String state=job.optString("state");tile.title.setText(job.optString("name"));tile.detail.setText(QueueStore.label(state)+"\n"+job.optString("message"));tile.card.setOnClickListener(v->jobDetails(job));
                if(QueueStore.active(job)&&!state.equals("queued")){tile.bar.setVisibility(View.VISIBLE);long total=job.optLong("total"),done=job.optLong("done");tile.bar.setIndeterminate(total<=0);if(total>0){int percent=(int)Math.min(100,100.0*done/total);tile.bar.setMax(100);tile.bar.setProgress(percent);tile.amount.setText(percent+"% · "+DownloadFiles.format(done)+" / "+DownloadFiles.format(total));tile.amount.setVisibility(View.VISIBLE);}}
                if(state.equals("queued")||state.equals("downloading"))action(tile,"Пауза",()->DownloadService.pause(MainActivity.this,job.optString("id")));
                else if(state.equals("paused")||state.equals("error"))action(tile,"Продолжить",()->{QueueStore.update(MainActivity.this,job.optString("id"),"queued","В очереди");DownloadService.start(MainActivity.this);});
                else if(state.equals("downloaded")||state.equals("installError"))action(tile,state.equals("installError")?"Повторить":"Установить",()->install(job));
                else if(state.equals("dataError"))action(tile,"Повторить данные",()->{QueueStore.update(MainActivity.this,job.optString("id"),"needsData","Повторная установка данных");DownloadService.start(MainActivity.this);});
                else if(state.equals("cleanupError"))action(tile,"Повторить очистку",()->retryCleanup(job));
                if(state.endsWith("Error")||state.equals("error"))tile.detail.setTextColor(getColor(R.color.qc_error));
                Button more=button("Подробнее");tile.actions.addView(more);more.setOnClickListener(v->jobDetails(job));
            }else{Catalog.Game game=filtered.get(p);InstalledApps.Entry current=installed==null?null:installed.get(game.pkg);tile.title.setText(game.name);tile.detail.setText(game.megabytes+" МБ"+(current==null?"":installed.newer(game)?(section==1?" · ✓ Есть обновление":" · Есть обновление"):" · Установлено")+(popularity!=null&&!sortMode.equals("name")?"\n"+popularity.label(game,sortMode,installed):""));if(section==1&&current!=null&&installed.newer(game)){android.text.SpannableString marked=new android.text.SpannableString(tile.detail.getText());int start=marked.toString().indexOf("✓");marked.setSpan(new android.text.style.ForegroundColorSpan(getColor(R.color.qc_success)),start,start+"✓ Есть обновление".length(),android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);tile.detail.setText(marked);}covers.bind(tile.cover,installed==null?game.pkg:installed.metadataPackage(game.pkg));tile.card.setOnClickListener(v->gameDetails(game));}
            return tile.card;}
        private void action(Tile tile,String label,Runnable run){Button b=Cards.button(MainActivity.this,label,true);tile.actions.addView(b);b.setOnClickListener(v->{run.run();updateQueue();});}
    }
    private int cardColumns(int width){int available=width-64-(width>=880?248:0);return Math.max(1,Math.min(5,(available+16)/236));}
    private void configureWidth(int width){int mode=width>=1180?3:width>=880?2:1;
        if(mode!=layoutMode){layoutMode=mode;boolean wide=mode>1;rail.setVisibility(wide?View.VISIBLE:View.GONE);sections.setVisibility(wide?View.GONE:View.VISIBLE);refreshControl.setVisibility(wide?View.VISIBLE:View.GONE);heading.setTextSize(wide?32:26);tools.setOrientation(wide?LinearLayout.HORIZONTAL:LinearLayout.VERTICAL);search.setLayoutParams(wide?new LinearLayout.LayoutParams(0,dp(64),1):new LinearLayout.LayoutParams(-1,dp(64)));LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(wide?-2:-1,-2);lp.leftMargin=wide?dp(16):0;lp.topMargin=wide?0:dp(8);sort.setLayoutParams(lp);}
        int n=downloads?1:cardColumns(width);if(n!=columns){columns=n;grid.setNumColumns(n);if(adapter!=null)adapter.notifyDataSetChanged();}
    }
    private void retryCleanup(JSONObject job){QueueStore.update(this,job.optString("id"),"cleanup","Повторная очистка установочных файлов");DownloadService.start(this);}

    private void refresh() {
        if (busy) return;
        if (DownloadService.RUNNING.get()) { showStatus("Каталог можно обновить после завершения текущей загрузки."); return; }
        busy = true; showStatus("Подключаюсь к каталогу…");
        io.execute(() -> {
            try { List<Catalog.Game> result = new Engine(this).refresh(this::showStatus);
                getSharedPreferences("state",0).edit().putInt("catalogCount",result.size()).remove("lastError").apply();
                runOnUiThread(() -> { if (!isDestroyed()) { games.clear(); games.addAll(result); filter(); refreshInstalled(); status.setText("Подключено · " + result.size() + " записей"); } });
            } catch (Exception e) { String msg = DownloadService.safeMessage(e); getSharedPreferences("state",0).edit().putString("lastError",msg).apply(); showStatus(msg); }
            finally { busy = false; }
        });
    }
    private void connection() {
        if (busy || DownloadService.RUNNING.get()) { showStatus("Дождись завершения текущей операции."); return; }
        LinearLayout layout = column(); layout.setPadding(dp(24),dp(8),dp(24),dp(8));
        layout.addView(text("Вставь ссылку подключения из настроек QLoader. Доступ хранится только на этом устройстве.",17,MUTED));
        EditText field = new EditText(this); field.setSingleLine(true); field.setTextSize(16); field.setHint("https://…/rclone_config?…");
        field.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD); layout.addView(field);
        Button permissions = button("Разрешения для установки"); layout.addView(permissions); permissions.setOnClickListener(v -> permissions());
        new AlertDialog.Builder(this).setTitle("Подключить QLoader").setView(layout)
            .setPositiveButton("Подключить",(d,w) -> connectUrl(field.getText().toString().trim()))
            .setNeutralButton("Импорт файла",(d,w) -> { Intent choose = new Intent(Intent.ACTION_OPEN_DOCUMENT).setType("*/*").addCategory(Intent.CATEGORY_OPENABLE); startActivityForResult(choose,41); })
            .setNegativeButton("Закрыть",null).show();
    }
    private void connectUrl(String value) {
        try { Engine.validateUrl(value); } catch (Exception e) { showStatus("Вставь полную HTTPS-ссылку конфигурации QLoader."); return; }
        if (busy) return; busy = true; showStatus("Проверяю подключение…");
        io.execute(() -> {
            String previousUrl = ""; byte[] previousConfig = null; Engine engine = new Engine(this);
            try {
                previousUrl = PrivateStore.loadUrl(this); if (engine.config().exists()) previousConfig = PrivateStore.read(engine.config(),2*1024*1024);
                PrivateStore.saveUrl(this,value); List<Catalog.Game> result = engine.refresh(this::showStatus);
                getSharedPreferences("state",0).edit().putInt("catalogCount",result.size()).remove("lastError").apply();
                runOnUiThread(() -> { games.clear(); games.addAll(result); filter(); status.setText("Подключено · " + games.size() + " записей"); });
            } catch (Exception e) {
                try { PrivateStore.saveUrl(this,previousUrl); if (previousConfig != null) PrivateStore.atomic(engine.config(),previousConfig); } catch (Exception ignored) {}
                showStatus(DownloadService.safeMessage(e));
            } finally { busy = false; }
        });
    }
    @Override protected void onActivityResult(int request,int result,Intent data) {
        super.onActivityResult(request,result,data);
        if (request != 41 || result != RESULT_OK || data == null || data.getData() == null) return;
        Uri uri = data.getData(); busy = true; showStatus("Импортирую конфигурацию…");
        io.execute(() -> {
            try (InputStream in = getContentResolver().openInputStream(uri)) {
                if (in == null) throw new IOException("Не удалось открыть файл.");
                byte[] contents = PrivateStore.read(in,2*1024*1024); Engine.validateConfig(contents);
                PrivateStore.atomic(new Engine(this).config(),contents); PrivateStore.saveUrl(this,"");
                runOnUiThread(() -> { busy = false; refresh(); });
            } catch (Exception e) { busy = false; showStatus(DownloadService.safeMessage(e)); }
        });
    }
    private void gameDetails(Catalog.Game game) {
        InstalledApps.Entry current = installed == null ? null : installed.get(game.pkg);
        boolean update = current != null && installed.newer(game);
        String versionText = game.megabytes + " МБ · версия каталога: " + game.version;
        if (current != null) versionText += "\nНа шлеме: " + current.display() + "\n" + (update ? "Доступно обновление." : current.version == game.version ? "Эта версия уже установлена." : "На шлеме версия новее этого релиза.");
        LinearLayout layout = column(); layout.setPadding(dp(20),dp(8),dp(20),dp(12));
        layout.addView(text(versionText,16,MINT));
        ImageView cover = new ImageView(this); cover.setAdjustViewBounds(true); cover.setScaleType(ImageView.ScaleType.FIT_CENTER); cover.setImageResource(R.drawable.qc_cover_placeholder); layout.addView(cover,new LinearLayout.LayoutParams(-1,dp(160)));
        TextView description = text("Загружаю описание…",19,Color.WHITE); description.setPadding(0,dp(12),0,dp(8)); layout.addView(description);
        TextView attribution = text("Описание и обложка загружаются при открытии карточки.",13,MUTED); layout.addView(attribution);
        Button original = button("Оригинал описания"); original.setVisibility(View.GONE); layout.addView(original);
        TextView release = text(game.release,13,MUTED); release.setPadding(0,dp(12),0,0); layout.addView(release);
        ScrollView scroll = new ScrollView(this); scroll.addView(layout);
        AlertDialog.Builder builder = new AlertDialog.Builder(this).setTitle(game.name).setView(scroll).setNegativeButton("Закрыть",null);
        if (current == null || update) builder.setPositiveButton(update ? "Обновить" : "Установить",(d,w) -> enqueue(game));
        else { builder.setPositiveButton("Открыть",(d,w) -> launch(current.pkg)); builder.setNeutralButton("Скачать APK",(d,w) -> enqueue(game)); }
        AlertDialog dialog = builder.create(); Cards.dialog(dialog);
        GameDetails session = new GameDetails(this); openDetails.add(session);
        dialog.setOnDismissListener(d -> { session.close(); openDetails.remove(session); cover.setImageDrawable(null); description.setText(""); original.setOnClickListener(null); }); dialog.show();
        String pkg = installed == null ? game.pkg : installed.metadataPackage(game.pkg);
        detailIo.execute(() -> {
            try {
                GameDetails.Info info = session.description(pkg);
                runOnUiThread(() -> {
                    if (!dialog.isShowing() || isDestroyed()) return;
                    description.setText(info.russian.isEmpty() ? info.note : info.russian);
                    attribution.setText((info.russian.isEmpty() ? "Источник: QLoader" : info.note) + (info.developer.isEmpty() ? "" : "\nРазработчик: " + info.developer));
                    if (!info.original.isEmpty()) { original.setVisibility(View.VISIBLE); original.setOnClickListener(v -> new AlertDialog.Builder(this).setTitle("Оригинал из QLoader").setMessage(info.original).setPositiveButton("Закрыть",null).show()); }
                });
            } catch (Exception e) { runOnUiThread(() -> { if (dialog.isShowing() && !isDestroyed()) description.setText("Не удалось загрузить описание. Открой карточку позже."); }); }
        });
        detailIo.execute(() -> {
            try { android.graphics.Bitmap bitmap = session.cover(pkg);
                runOnUiThread(() -> { if (dialog.isShowing() && !isDestroyed() && bitmap != null) { cover.setImageBitmap(bitmap); cover.setVisibility(View.VISIBLE); } });
            } catch (Exception ignored) { }
        });
    }
    private void enqueue(Catalog.Game game) {
        if (busy) { showStatus("Сначала дождись завершения текущей операции."); return; }
        try { QueueStore.add(this,game); DownloadService.start(this); selectTab(true); } catch (Exception e) { showStatus(DownloadService.safeMessage(e)); }
    }
    private void launch(String pkg) { Intent intent = getPackageManager().getLaunchIntentForPackage(pkg); if (intent != null) startActivity(intent); else showStatus("Открой игру из библиотеки Quest."); }
    private void jobDetails(JSONObject job) {
        String state = job.optString("state"), id = job.optString("id");
        AlertDialog.Builder dialog = new AlertDialog.Builder(this).setTitle(job.optString("name")).setMessage(QueueStore.label(state) + "\n\n" + job.optString("message")).setNegativeButton("Закрыть",null);
        if (state.equals("queued") || state.equals("downloading")) dialog.setPositiveButton("Пауза",(d,w) -> { DownloadService.pause(this,id); updateQueue(); });
        else if (state.equals("downloaded") || state.equals("installError")) dialog.setPositiveButton("Установить",(d,w) -> install(job));
        else if (state.equals("cleanupError")) dialog.setPositiveButton("Повторить очистку",(d,w) -> retryCleanup(job));
        else if (state.equals("installed")) dialog.setPositiveButton("Открыть",(d,w) -> launch(QueueStore.installedPackage(job)));
        else if (state.equals("dataError")) {
            dialog.setPositiveButton("Повторить данные",(d,w) -> { QueueStore.update(this,id,"needsData","Повторная установка данных"); DownloadService.start(this); });
        } else if (!QueueStore.active(job)) {
            dialog.setPositiveButton("Продолжить",(d,w) -> { QueueStore.update(this,id,"queued","В очереди"); DownloadService.start(this); updateQueue(); });
        }
        dialog.setNeutralButton("Ещё",(d,w) -> jobMenu(job));
        dialog.show();
    }
    private void jobMenu(JSONObject job) {
        String[] options = {"Описание и версии", "Удалить скачанные файлы", "Убрать из истории", "Проверить скачанные файлы", "Разрешения установки"};
        new AlertDialog.Builder(this).setTitle(job.optString("name")).setItems(options,(d,w) -> {
            if (w == 0) {
                for (Catalog.Game game : games) if (game.id.equals(job.optString("id"))) { gameDetails(game); return; }
                showStatus("Обнови каталог, чтобы открыть сведения об этом релизе.");
            } else if (w == 1) confirmCleanup(job.optString("id"),false);
            else if (w == 2) confirmCleanup(job.optString("id"),true);
            else if (w == 3) verifyDownloaded(job);
            else permissions();
        }).setNegativeButton("Закрыть",null).show();
    }
    private void cleanupMenu(String id) {
        new AlertDialog.Builder(this).setTitle("Очистка QCatalog").setItems(new String[]{"Удалить скачанные файлы", "Очистить историю загрузок"},(d,w) -> confirmCleanup(id,w == 1)).setNegativeButton("Закрыть",null).show();
    }
    private void confirmCleanup(String id, boolean history) {
        if (busy) { showStatus("Дождись завершения текущей операции."); return; }
        try { QueueStore.ensureIdle(this); } catch (Exception e) { showStatus(DownloadService.safeMessage(e)); return; }
        String title = history ? id == null ? "Очистить историю?" : "Убрать запись из истории?" : id == null ? "Удалить все скачанные файлы?" : "Удалить скачанные файлы?";
        String message = history ? "Записи загрузок будут убраны. Скачанные файлы останутся; их можно удалить отдельно через «Очистку». Установленные приложения останутся на шлеме." : "Будут удалены скачанные APK и файлы в папке загрузок QCatalog. Установленные приложения и их сохранения останутся на шлеме. Для повторной установки понадобится новая загрузка.";
        new AlertDialog.Builder(this).setTitle(title).setMessage(message).setNegativeButton("Отмена",null).setPositiveButton(history ? "Очистить" : "Удалить",(d,w) -> {
            if (busy) return; busy = true;
            io.execute(() -> {
                try {
                    String result = history ? "Убрано записей: " + QueueStore.clearHistory(this,id) : "Освобождено: " + DownloadFiles.format(QueueStore.deleteFiles(this,id));
                    showStatus(result);
                } catch (Exception e) { showStatus(DownloadService.safeMessage(e)); }
                finally { busy = false; runOnUiThread(() -> { if (!isDestroyed()) { updateQueue(); refreshInstalled(); } }); }
            });
        }).show();
    }
    private void verifyDownloaded(JSONObject job) {
        if (busy || DownloadService.RUNNING.get()) return;
        busy = true; showStatus("Проверяю скачанный APK…");
        io.execute(() -> {
            try {
                Installer.Bundle bundle = Installer.inspect(this, job);
                QueueStore.update(this, job.getString("id"), "downloaded", bundle.needsApproval(job) ? "Скачано. Имя пакета отличается от каталога — при установке покажу подробности." : "Файлы проверены. Можно установить.");
                showStatus("Скачанный APK проверен. Нажми «Установить».");
            } catch (Exception e) { showStatus(DownloadService.safeMessage(e)); }
            finally { busy = false; }
        });
    }
    private void install(JSONObject job) {
        if (busy) return;
        if (!getPackageManager().canRequestPackageInstalls()) { permissions(); return; }
        busy = true; showStatus("Проверяю APK перед установкой…");
        io.execute(() -> {
            try {
                Installer.Bundle bundle = Installer.inspect(this,job);
                Installer.checkUpdateSignature(this,bundle);
                if (bundle.needsApproval(job)) {
                    runOnUiThread(() -> {
                        if (isDestroyed()) return;
                        new AlertDialog.Builder(this).setTitle("Сведения APK отличаются от каталога")
                            .setMessage("Приложение внутри файла: " + bundle.label + "\n\nВ каталоге:\n" + job.optString("pkg") + "\n\nВ APK:\n" + bundle.packageName + "\n\nВерсия совпадает. Установить этот скачанный APK?")
                            .setPositiveButton("Установить APK", (dialog, which) -> {
                                try { QueueStore.approvePackage(this, job.optString("id"), bundle.packageName, bundle.sha256); install(QueueStore.get(this, job.optString("id"))); }
                                catch (Exception e) { showStatus(DownloadService.safeMessage(e)); }
                            }).setNegativeButton("Отмена", null).show();
                    });
                    return;
                }
                if (!bundle.data.isEmpty() && Build.VERSION.SDK_INT >= 30 && !Environment.isExternalStorageManager()) { runOnUiThread(this::permissions); return; }
                Installer.install(this,job); showStatus("Подтверди установку в системном окне.");
            } catch (Exception e) { QueueStore.update(this,job.optString("id"),"installError",DownloadService.safeMessage(e)); showStatus(DownloadService.safeMessage(e)); }
            finally { busy = false; }
        });
    }
    private void permissions() {
        String[] options = {"Разрешить установку приложений", "Разрешить доступ к файлам для OBB"};
        new AlertDialog.Builder(this).setTitle("Разрешения QCatalog").setItems(options,(d,w) -> {
            String action = w == 0 ? Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES : Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION;
            try { startActivity(new Intent(action,Uri.parse("package:" + getPackageName()))); }
            catch (Exception e) { showStatus("Эта прошивка не открыла настройки разрешения. Нужна ручная настройка на Quest."); }
        }).setNegativeButton("Закрыть",null).show();
    }
}
