package ru.local.qcatalog;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
public class CatalogTest {
    static void require(boolean b, String message) { if (!b) throw new AssertionError(message); }
    public static void main(String[] args) throws Exception {
        String header = "Game Name;Release Name;Package Name;Version Code;Last Updated;Size (MB)\r\n";
        List<Catalog.Game> games = Catalog.parse(new StringReader("\uFEFF" + header + "\"Тест; игра\";Release v1;org.example.game;123;2026-10-19;500\r\n"));
        require(games.size() == 1 && games.get(0).name.equals("Тест; игра"), "quoted Unicode fields");
        require(games.get(0).version == 123 && games.get(0).megabytes == 500, "numeric fields");
        require(!Catalog.safeRelease("../escape") && !Catalog.safeRelease("x\\y") && !Catalog.safeRelease(".."), "release path traversal");
        require(!Catalog.safePackage("../other") && Catalog.safePackage("com.Example.Game2"), "package validation");
        require(Catalog.digest("release").matches("[a-f0-9]{64}") && Catalog.digest("release").equals(Catalog.digest("release")), "stable safe folder ID");
        games = Catalog.parse(new StringReader(header + "Good;release;org.example.game;1;date;2\nBad;../escape;org.bad.game;1;date;2\nGood;release;org.example.game;1;date;2\n"));
        require(games.size() == 1, "reject unsafe releases and deduplicate");
        boolean rejected = false;
        try { Catalog.parse(new StringReader(header + "\"unterminated")); } catch (IOException expected) { rejected = true; }
        require(rejected, "reject malformed CSV");
        if (args.length == 1) try (Reader in = new InputStreamReader(new FileInputStream(args[0]), StandardCharsets.UTF_8)) {
            System.out.println("Local QLoader catalogue parsed: " + Catalog.parse(in).size() + " entries");
        }
        System.out.println("Catalog tests passed (7 checks)");
    }
}
