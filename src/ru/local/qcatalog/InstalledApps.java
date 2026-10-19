package ru.local.qcatalog;

import android.content.Context;
import android.content.pm.PackageInfo;
import org.json.JSONObject;
import java.util.*;

final class InstalledApps {
    static final class Entry {
        final String pkg, name; final long version;
        Entry(PackageInfo p) { pkg = p.packageName; version = p.getLongVersionCode(); name = p.versionName == null ? "" : p.versionName; }
        String display() { return name.isEmpty() ? "код " + version : name + " (код " + version + ")"; }
    }
    private final Map<String,Entry> apps = new HashMap<>();
    private final Map<String,String> aliases = new HashMap<>();
    static InstalledApps read(Context c) {
        InstalledApps result = new InstalledApps();
        for (PackageInfo p : c.getPackageManager().getInstalledPackages(0)) result.apps.put(p.packageName, new Entry(p));
        for (Map.Entry<String,?> alias : c.getSharedPreferences("package-aliases",0).getAll().entrySet())
            if (alias.getValue() instanceof String) result.aliases.put(alias.getKey(), (String)alias.getValue());
        // Keep identities confirmed in earlier preview builds, even after history is cleared.
        for (JSONObject job : QueueStore.all(c)) {
            String original = job.optString("pkg"), actual = job.optString("approvedPackage");
            if (Catalog.safePackage(original) && Catalog.safePackage(actual)) {
                result.aliases.put(original, actual);
                c.getSharedPreferences("package-aliases",0).edit().putString(original,actual).apply();
            }
        }
        return result;
    }
    Entry get(String pkg) { return apps.get(aliases.containsKey(pkg) ? aliases.get(pkg) : pkg); }
    String metadataPackage(String pkg) { return aliases.containsKey(pkg) ? aliases.get(pkg) : pkg; }
    boolean newer(Catalog.Game game) { Entry current = get(game.pkg); return current != null && isNewer(game.version,current.version); }
    static boolean isNewer(long catalog, long installed) { return catalog > installed; }
}
