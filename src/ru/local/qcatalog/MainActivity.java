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
    private static final int BG = Color.rgb(16, 24, 35), PANEL = Color.rgb(25, 38, 52), MINT = Color.rgb(107, 225, 189), MUTED = Color.rgb(167, 185, 200);
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final ExecutorService detailIo = Executors.newFixedThreadPool(3);
    private final Set<GameDetails> openDetails = new HashSet<>();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final List<Catalog.Game> games = new ArrayList<>(), filtered = new ArrayList<>();
    private List<JSONObject> jobs = new ArrayList<>();
    private boolean downloads, resumed;
    private volatile boolean busy;
    private TextView status, empty, versions;
    private EditText search;
    private Button catalogTab, downloadsTab, cleanup;
    private Spinner catalogFilter;
    private InstalledApps installed;
    private boolean checkingInstalled;
    private long lastInstalledRead;
    private Rows adapter;
    private String queueSignature = "";
    private final Runnable tick = new Runnable() { public void run() { if (resumed) { updateQueue(); if (SystemClock.elapsedRealtime() - lastInstalledRead > 15000) refreshInstalled(); handler.postDelayed(this, 1000); } } };

    @Override public void onCreate(Bundle state) {
        super.onCreate(state); getWindow().setStatusBarColor(BG); getWindow().setNavigationBarColor(BG);
        if (!DownloadService.RUNNING.get()) QueueStore.recover(this);
        LinearLayout root = column(); root.setPadding(dp(24), dp(20), dp(24), dp(10)); root.setBackgroundColor(BG); setContentView(root);
        LinearLayout branding = column(); root.addView(branding);
        TextView title = text("QCatalog", 30, Color.WHITE); title.setTypeface(Typeface.DEFAULT, Typeface.BOLD); branding.addView(title);
        branding.addView(text("Твой каталог QLoader · пробная версия", 14, MUTED));
        LinearLayout header = row(); root.addView(header);
        Button connect = button("Подключение"); header.addView(connect); connect.setOnClickListener(v -> connection());
        Button refresh = button("Обновить"); header.addView(refresh); refresh.setOnClickListener(v -> refresh());
        status = text("Подключи QLoader, чтобы открыть каталог.", 16, MINT); status.setPadding(0, dp(16), 0, dp(12)); root.addView(status);
        LinearLayout tabs = row(); root.addView(tabs);
        catalogTab = button("Каталог"); downloadsTab = button("Загрузки"); tabs.addView(catalogTab); tabs.addView(downloadsTab);
        catalogTab.setOnClickListener(v -> selectTab(false)); downloadsTab.setOnClickListener(v -> selectTab(true));
        search = new EditText(this); search.setSingleLine(true); search.setTextSize(19); search.setTextColor(Color.WHITE); search.setHintTextColor(MUTED);
        search.setHint("Найти игру или приложение"); search.setPadding(dp(14), dp(10), dp(14), dp(10)); root.addView(search, new LinearLayout.LayoutParams(-1, dp(64)));
        search.addTextChangedListener(new TextWatcher() { public void beforeTextChanged(CharSequence s,int st,int c,int a) {} public void onTextChanged(CharSequence s,int st,int b,int c) { filter(); } public void afterTextChanged(Editable e) {} });
        catalogFilter = new Spinner(this);
        ArrayAdapter<String> choices = new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,new String[]{"Все игры", "Установленные", "Есть обновления"});
        catalogFilter.setAdapter(choices); root.addView(catalogFilter,new LinearLayout.LayoutParams(-1,dp(48)));
        catalogFilter.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() { public void onItemSelected(AdapterView<?> p,View v,int position,long id) { filter(); } public void onNothingSelected(AdapterView<?> p) {} });
        versions = text("Проверяю установленные версии…",14,MUTED); root.addView(versions);
        cleanup = button("Очистка файлов и истории"); root.addView(cleanup); cleanup.setOnClickListener(v -> cleanupMenu(null));
        FrameLayout content = new FrameLayout(this); root.addView(content, new LinearLayout.LayoutParams(-1, 0, 1));
        ListView list = new ListView(this); list.setDividerHeight(dp(7)); list.setDivider(new android.graphics.drawable.ColorDrawable(BG)); content.addView(list);
        empty = text("Здесь появится каталог после подключения.", 20, MUTED); empty.setGravity(Gravity.CENTER); content.addView(empty, new FrameLayout.LayoutParams(-1,-1)); list.setEmptyView(empty);
        adapter = new Rows(); list.setAdapter(adapter); list.setOnItemClickListener((parent, view, position, id) -> { if (downloads) jobDetails(jobs.get(position)); else gameDetails(filtered.get(position)); });
        selectTab(false);
        try { games.addAll(new Engine(this).cached()); if (!games.isEmpty()) status.setText("Сохранённый каталог · " + games.size() + " записей"); filter(); }
        catch (Exception e) { showStatus(DownloadService.safeMessage(e)); }
        File bootstrap = new File(getFilesDir(), "bootstrap.json");
        if (bootstrap.exists()) {
            try {
                JSONObject data = new JSONObject(new String(PrivateStore.read(bootstrap, 32768), StandardCharsets.UTF_8));
                String address = data.getString("configUrl"); Engine.validateUrl(address); PrivateStore.saveUrl(this, address);
                if (!bootstrap.delete()) throw new IOException("Не удалось удалить временный файл подключения.");
                refresh();
            } catch (Exception e) { showStatus(DownloadService.safeMessage(e)); }
        }
    }
    @Override protected void onResume() {
        super.onResume(); resumed = true; handler.post(tick);
        refreshInstalled();
        if (!DownloadService.RUNNING.get()) for (JSONObject job : QueueStore.all(this)) if (Arrays.asList("queued","needsData").contains(job.optString("state"))) { DownloadService.start(this); break; }
    }
    @Override protected void onPause() { resumed = false; handler.removeCallbacks(tick); super.onPause(); }
    @Override protected void onDestroy() { for (GameDetails details : openDetails) details.close(); openDetails.clear(); detailIo.shutdownNow(); io.shutdown(); super.onDestroy(); }
    private int dp(int n) { return Math.round(n * getResources().getDisplayMetrics().density); }
    private LinearLayout column() { LinearLayout v = new LinearLayout(this); v.setOrientation(LinearLayout.VERTICAL); return v; }
    private LinearLayout row() { LinearLayout v = new LinearLayout(this); v.setOrientation(LinearLayout.HORIZONTAL); v.setGravity(Gravity.CENTER_VERTICAL); return v; }
    private TextView text(String value, int size, int color) { TextView v = new TextView(this); v.setText(value); v.setTextSize(size); v.setTextColor(color); return v; }
    private Button button(String value) { Button b = new Button(this); b.setText(value); b.setTextSize(16); b.setAllCaps(false); b.setMinHeight(dp(52)); return b; }
    private void showStatus(String value) { runOnUiThread(() -> { if (!isDestroyed()) status.setText(value); }); }
    private void selectTab(boolean value) {
        downloads = value; search.setVisibility(value ? View.GONE : View.VISIBLE);
        catalogFilter.setVisibility(value ? View.GONE : View.VISIBLE); versions.setVisibility(value ? View.GONE : View.VISIBLE); cleanup.setVisibility(value ? View.VISIBLE : View.GONE);
        catalogTab.setTextColor(value ? MUTED : MINT); downloadsTab.setTextColor(value ? MINT : MUTED);
        empty.setText(value ? "Пока нет загрузок. Выбери игру в каталоге." : "Подключи QLoader или обнови каталог.");
        updateQueue(); filter();
    }
    private void updateQueue() {
        try {
            jobs = QueueStore.all(this); String signature = jobs.toString();
            downloadsTab.setText("Загрузки · " + jobs.size());
            if (!signature.equals(queueSignature)) { queueSignature = signature; adapter.notifyDataSetChanged(); }
        } catch (Exception e) { showStatus(DownloadService.safeMessage(e)); }
    }
    private void filter() {
        if (search == null || adapter == null) return;
        String query = search.getText().toString().toLowerCase(Locale.ROOT).trim(); filtered.clear();
        int mode = catalogFilter == null ? 0 : catalogFilter.getSelectedItemPosition();
        Map<String,Long> latest = new HashMap<>(); Set<String> present = new HashSet<>(), updates = new HashSet<>();
        for (Catalog.Game game : games) latest.put(game.pkg,Math.max(latest.containsKey(game.pkg) ? latest.get(game.pkg) : 0,game.version));
        for (Catalog.Game game : games) {
            InstalledApps.Entry current = installed == null ? null : installed.get(game.pkg);
            if (current != null) present.add(current.pkg);
            if (current != null && installed.newer(game)) updates.add(current.pkg);
            if (mode > 0 && (current == null || game.version < latest.get(game.pkg))) continue;
            if (mode == 2 && !installed.newer(game)) continue;
            if (query.isEmpty() || (game.name + " " + game.release + " " + game.pkg).toLowerCase(Locale.ROOT).contains(query)) filtered.add(game);
        }
        if (installed != null) versions.setText("На шлеме: " + present.size() + " · обновления: " + updates.size());
        if (!downloads) empty.setText(installed == null && mode > 0 ? "Проверяю установленные версии…" : mode == 2 ? "Обновлений по текущему каталогу нет." : mode == 1 ? "Установленные игры не найдены в каталоге." : query.isEmpty() ? "Подключи QLoader или обнови каталог." : "Ничего не найдено.");
        catalogTab.setText("Каталог · " + filtered.size()); adapter.notifyDataSetChanged();
    }
    private void refreshInstalled() {
        if (checkingInstalled || isDestroyed()) return; checkingInstalled = true; lastInstalledRead = SystemClock.elapsedRealtime();
        io.execute(() -> {
            try { InstalledApps value = InstalledApps.read(this); runOnUiThread(() -> { if (!isDestroyed()) { installed = value; filter(); } }); }
            catch (Exception e) { showStatus("Не удалось прочитать установленные версии."); }
            finally { runOnUiThread(() -> checkingInstalled = false); }
        });
    }
    private final class Rows extends BaseAdapter {
        public int getCount() { return downloads ? jobs.size() : filtered.size(); }
        public Object getItem(int p) { return downloads ? jobs.get(p) : filtered.get(p); }
        public long getItemId(int p) { return p; }
        public View getView(int p, View recycled, android.view.ViewGroup parent) {
            LinearLayout card; TextView title, detail;
            if (recycled == null) {
                card = column(); card.setPadding(dp(18), dp(14), dp(18), dp(14)); card.setMinimumHeight(dp(90));
                GradientDrawable shape = new GradientDrawable(); shape.setColor(PANEL); shape.setCornerRadius(dp(12)); card.setBackground(shape);
                title = text("", 22, Color.WHITE); title.setMaxLines(2); card.addView(title);
                detail = text("", 15, MUTED); detail.setPadding(0,dp(5),0,0); detail.setMaxLines(3); card.addView(detail);
            } else { card = (LinearLayout)recycled; title = (TextView)card.getChildAt(0); detail = (TextView)card.getChildAt(1); }
            detail.setTextColor(MUTED);
            if (downloads) { JSONObject job = jobs.get(p); title.setText(job.optString("name")); detail.setText(QueueStore.label(job.optString("state")) + " · " + job.optString("message")); }
            else {
                Catalog.Game game = filtered.get(p); title.setText(game.name); String label = game.megabytes + " МБ · в каталоге: " + game.version;
                InstalledApps.Entry current = installed == null ? null : installed.get(game.pkg);
                if (current != null) { label += "\nНа шлеме: " + current.display(); if (installed.newer(game)) { label += " · есть обновление"; detail.setTextColor(MINT); } }
                detail.setText(label);
            }
            return card;
        }
    }
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
        ImageView cover = new ImageView(this); cover.setAdjustViewBounds(true); cover.setScaleType(ImageView.ScaleType.FIT_CENTER); cover.setVisibility(View.GONE); layout.addView(cover,new LinearLayout.LayoutParams(-1,dp(160)));
        TextView description = text("Загружаю описание…",19,Color.WHITE); description.setPadding(0,dp(12),0,dp(8)); layout.addView(description);
        TextView attribution = text("Описание и обложка загружаются при открытии карточки.",13,MUTED); layout.addView(attribution);
        Button original = button("Оригинал описания"); original.setVisibility(View.GONE); layout.addView(original);
        TextView release = text(game.release,13,MUTED); release.setPadding(0,dp(12),0,0); layout.addView(release);
        ScrollView scroll = new ScrollView(this); scroll.addView(layout);
        AlertDialog.Builder builder = new AlertDialog.Builder(this).setTitle(game.name).setView(scroll).setNegativeButton("Закрыть",null);
        if (current == null || update) builder.setPositiveButton(update ? "Обновить" : "Скачать",(d,w) -> enqueue(game));
        else { builder.setPositiveButton("Открыть",(d,w) -> launch(current.pkg)); builder.setNeutralButton("Скачать APK",(d,w) -> enqueue(game)); }
        AlertDialog dialog = builder.create();
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
        else if (state.equals("installed")) dialog.setPositiveButton("Открыть",(d,w) -> launch(QueueStore.installedPackage(job)));
        else if (state.equals("dataError")) {
            dialog.setPositiveButton("Повторить данные",(d,w) -> { QueueStore.update(this,id,"needsData","Повторная установка данных"); DownloadService.start(this); });
        } else if (!state.equals("installing") && !state.equals("needsData")) {
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
