package ru.local.qcatalog;
import java.io.*;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
public final class DownloadFilesTest {
    static void require(boolean ok,String message) { if (!ok) throw new AssertionError(message); }
    public static void main(String[] args) throws Exception {
        Path workspace = Paths.get(args[0]).toAbsolutePath().normalize(); Files.createDirectories(workspace);
        Path test = Files.createTempDirectory(workspace,"cleanup-");
        if (!test.toRealPath().startsWith(workspace.toRealPath())) throw new AssertionError("Test must stay in workspace");
        String a = String.join("",Collections.nCopies(64,"a")), b = String.join("",Collections.nCopies(64,"b"));
        File base = test.resolve("games").toFile(); Files.createDirectories(base.toPath());
        Path other = test.resolve("installed-sentinel"); Files.write(other,new byte[]{7,8,9});
        try {
            File first = DownloadFiles.path(base,a), second = DownloadFiles.path(base,b);
            Files.createDirectories(first.toPath().resolve("nested")); Files.createDirectories(second.toPath());
            Files.write(first.toPath().resolve("nested/file.apk"),new byte[17]); Files.write(second.toPath().resolve("keep.apk"),new byte[11]);
            require(DownloadFiles.size(base,a) == 17,"Size counts only selected release");
            require(DownloadFiles.releases(base).size() == 2,"Release discovery");
            for (String unsafe : Arrays.asList("..", "../installed-sentinel", ".", "", a + "/../" + b)) {
                boolean rejected = false; try { DownloadFiles.delete(base,unsafe); } catch (IOException e) { rejected = true; }
                require(rejected,"Unsafe target rejected: " + unsafe);
            }
            boolean linkSupported = false;
            try { Files.createSymbolicLink(first.toPath().resolve("outside-link"),other); linkSupported = true; } catch (IOException | UnsupportedOperationException | SecurityException e) { }
            require(DownloadFiles.delete(base,a) == 17,"Only selected file bytes deleted");
            require(!first.exists() && Files.exists(second.toPath().resolve("keep.apk")),"Other download preserved");
            require(Arrays.equals(Files.readAllBytes(other),new byte[]{7,8,9}),"Outside files preserved");
            require(DownloadFiles.delete(base,a) == 0,"Repeated delete is safe");
            System.out.println("Download cleanup tests passed; symlink case " + (linkSupported ? "verified" : "not available on this host"));
        } finally {
            Files.walkFileTree(test,new SimpleFileVisitor<Path>() {
                @Override public FileVisitResult visitFile(Path path,BasicFileAttributes attrs) throws IOException { Files.delete(path); return FileVisitResult.CONTINUE; }
                @Override public FileVisitResult postVisitDirectory(Path path,IOException error) throws IOException { if (error != null) throw error; Files.delete(path); return FileVisitResult.CONTINUE; }
            });
        }
    }
}
