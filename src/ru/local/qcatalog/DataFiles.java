package ru.local.qcatalog;

import java.io.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;

/** Copy into per-file staging, verify every byte, then replace destination files. Sources remain until completion cleanup. */
final class DataFiles {
    interface Progress { void update(boolean verifying,long done,long total); }
    static void copy(Map<File,String> files,File root,Progress progress) throws Exception {
        if(Files.isSymbolicLink(root.toPath())) throw new IOException("Ссылки вместо папки данных не поддерживаются.");
        if(!root.isDirectory() && !root.mkdirs()) throw new IOException("Не удалось создать папку данных.");
        long total=0; for(File source:files.keySet()) total=Math.addExact(total,source.length());
        Map<File,byte[]> sums=new LinkedHashMap<>(); Map<File,File> destinations=new LinkedHashMap<>();
        long done=0,last=0; progress.update(false,0,total);
        for(Map.Entry<File,String> entry:files.entrySet()) {
            String path=entry.getValue();
            if(path.isEmpty() || path.startsWith("/") || path.contains("\\") || Arrays.asList(path.split("/")).contains("..")) throw new IOException("Недопустимый путь данных.");
            File target=new File(root,path);
            if(!target.getCanonicalPath().startsWith(root.getCanonicalPath()+File.separator)) throw new IOException("Недопустимый путь данных.");
            if(!target.getParentFile().isDirectory() && !target.getParentFile().mkdirs()) throw new IOException("Не удалось создать папку данных.");
            File pending=new File(target.getParentFile(),target.getName()+".qcatalog-part");
            if(Files.isSymbolicLink(pending.toPath()) || Files.isSymbolicLink(target.toPath())) throw new IOException("Ссылки в файлах данных не поддерживаются.");
            MessageDigest sha=MessageDigest.getInstance("SHA-256");
            try(InputStream in=new FileInputStream(entry.getKey()); FileOutputStream out=new FileOutputStream(pending)) {
                byte[] buf=new byte[1024*1024]; int n;
                while((n=in.read(buf))!=-1) { out.write(buf,0,n); sha.update(buf,0,n); done+=n; long now=System.nanoTime(); if(now-last>250000000L || done==total) { last=now; progress.update(false,done,total); } }
                out.getFD().sync();
            }
            sums.put(pending,sha.digest()); destinations.put(pending,target);
        }
        done=0; last=0; progress.update(true,0,total);
        for(Map.Entry<File,byte[]> entry:sums.entrySet()) {
            MessageDigest sha=MessageDigest.getInstance("SHA-256");
            try(InputStream in=new FileInputStream(entry.getKey())) {
                byte[] buf=new byte[1024*1024]; int n;
                while((n=in.read(buf))!=-1) { sha.update(buf,0,n); done+=n; long now=System.nanoTime(); if(now-last>250000000L || done==total) { last=now; progress.update(true,done,total); } }
            }
            if(!MessageDigest.isEqual(entry.getValue(),sha.digest())) throw new IOException("Проверка записанных данных не прошла. Скачанные файлы сохранены.");
        }
        for(Map.Entry<File,File> entry:destinations.entrySet()) Files.move(entry.getKey().toPath(),entry.getValue().toPath(),StandardCopyOption.REPLACE_EXISTING);
    }
}
