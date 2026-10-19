package ru.local.qcatalog;

import java.io.*;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;

/** Only operates inside one validated release directory; never follows symlinks. */
final class DownloadFiles {
    static File path(File base, String id) throws IOException {
        if (base == null || id == null || !id.matches("[a-f0-9]{64}")) throw new IOException("Некорректная папка загрузки.");
        File result = new File(base, id);
        if (Files.isSymbolicLink(result.toPath()) || !result.getCanonicalFile().getParentFile().equals(base.getCanonicalFile()))
            throw new IOException("Папка загрузки выходит за пределы хранилища QCatalog.");
        return result;
    }
    static long size(File base, String id) throws IOException {
        File dir = path(base, id); if (!dir.exists()) return 0;
        final long[] bytes = {0};
        Files.walkFileTree(dir.toPath(), new SimpleFileVisitor<Path>() {
            @Override public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                if (attrs.isRegularFile()) bytes[0] = Math.addExact(bytes[0], attrs.size());
                return FileVisitResult.CONTINUE;
            }
        });
        return bytes[0];
    }
    static long delete(File base, String id) throws IOException {
        File dir = path(base, id); if (!dir.exists()) return 0;
        final long[] bytes = {0};
        Files.walkFileTree(dir.toPath(), new SimpleFileVisitor<Path>() {
            @Override public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Files.delete(file); if (attrs.isRegularFile()) bytes[0] = Math.addExact(bytes[0], attrs.size());
                return FileVisitResult.CONTINUE;
            }
            @Override public FileVisitResult postVisitDirectory(Path dir, IOException error) throws IOException {
                if (error != null) throw error; Files.delete(dir); return FileVisitResult.CONTINUE;
            }
        });
        return bytes[0];
    }
    static List<String> releases(File base) throws IOException {
        List<String> result = new ArrayList<>(); if (base == null || !base.exists()) return result;
        File[] entries = base.listFiles(); if (entries == null) throw new IOException("Не удалось прочитать загрузки.");
        for (File file : entries) if (file.getName().matches("[a-f0-9]{64}") && !Files.isSymbolicLink(file.toPath()) && file.isDirectory()) result.add(file.getName());
        return result;
    }
    static String format(long bytes) {
        return bytes >= 1024L * 1024 * 1024 ? String.format(Locale.ROOT, "%.2f ГБ", bytes / (1024.0 * 1024 * 1024)) :
            String.format(Locale.ROOT, "%.1f МБ", bytes / (1024.0 * 1024));
    }
}
