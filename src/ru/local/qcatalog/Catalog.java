package ru.local.qcatalog;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

/** QLoader's documented six-column, semicolon-delimited FFA.txt format. */
public final class Catalog {
    public static final class Game {
        public final String name, release, pkg, updated, id;
        public final long version, megabytes;
        public Game(String name, String release, String pkg, long version, String updated, long megabytes) {
            this.name = name; this.release = release; this.pkg = pkg;
            this.version = version; this.updated = updated; this.megabytes = megabytes;
            this.id = digest(release);
        }
    }
    public static String digest(String input) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder out = new StringBuilder();
            for (byte b : hash) out.append(String.format(Locale.ROOT, "%02x", b));
            return out.toString();
        } catch (Exception e) { throw new IllegalStateException(e); }
    }
    public static boolean safeRelease(String s) {
        return s != null && !s.isEmpty() && !s.equals(".") && !s.equals("..") &&
            s.indexOf('/') < 0 && s.indexOf('\\') < 0 && s.indexOf('\0') < 0 && s.indexOf('\r') < 0 && s.indexOf('\n') < 0;
    }
    public static boolean safePackage(String s) {
        return s != null && s.matches("[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z0-9_]+)+");
    }
    public static List<Game> parse(Reader reader) throws IOException {
        List<List<String>> rows = rows(reader);
        if (rows.isEmpty() || rows.get(0).size() != 6 || !rows.get(0).get(0).replace("\uFEFF", "").trim().equalsIgnoreCase("Game Name"))
            throw new IOException("Неизвестный формат каталога: нужен FFA.txt QLoader.");
        List<Game> games = new ArrayList<>(); Set<String> seen = new HashSet<>();
        for (int i = 1; i < rows.size(); i++) {
            List<String> r = rows.get(i);
            if (r.size() == 1 && r.get(0).trim().isEmpty()) continue;
            if (r.size() != 6) throw new IOException("Повреждён каталог: строка " + (i + 1));
            String release = r.get(1).trim(), pkg = r.get(2).trim();
            if (!safeRelease(release) || !safePackage(pkg)) continue;
            try {
                long version = Long.parseLong(r.get(3).trim()), mb = Long.parseLong(r.get(5).trim());
                if (version < 0 || mb < 0 || mb > 10_000_000) continue;
                if (seen.add(release)) games.add(new Game(r.get(0).trim(), release, pkg, version, r.get(4).trim(), mb));
            } catch (NumberFormatException ignored) { }
        }
        if (games.isEmpty()) throw new IOException("В каталоге нет подходящих записей.");
        games.sort(Comparator.comparing(g -> g.name.toLowerCase(Locale.ROOT)));
        return games;
    }
    static List<List<String>> rows(Reader source) throws IOException {
        PushbackReader in = new PushbackReader(source, 1);
        List<List<String>> out = new ArrayList<>(); List<String> row = new ArrayList<>();
        StringBuilder cell = new StringBuilder(); boolean quoted = false; int c;
        while ((c = in.read()) != -1) {
            if (c == '"') {
                if (quoted) { int next = in.read(); if (next == '"') cell.append('"'); else { quoted = false; if (next != -1) in.unread(next); } }
                else if (cell.length() == 0) quoted = true;
                else cell.append((char)c);
            } else if (c == ';' && !quoted) { row.add(cell.toString()); cell.setLength(0); }
            else if ((c == '\n' || c == '\r') && !quoted) {
                if (c == '\r') { int next = in.read(); if (next != '\n' && next != -1) in.unread(next); }
                row.add(cell.toString()); cell.setLength(0); out.add(row); row = new ArrayList<>();
            } else cell.append((char)c);
            if (cell.length() > 16384 || out.size() > 100000) throw new IOException("Каталог слишком большой.");
        }
        if (quoted) throw new IOException("Незакрытая кавычка в каталоге.");
        if (cell.length() > 0 || !row.isEmpty()) { row.add(cell.toString()); out.add(row); }
        return out;
    }
    private Catalog() { }
}
