package ru.local.qcatalog;

import java.io.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import org.apache.commons.compress.archivers.sevenz.*;

/** 7z is expanded into release-local staging, with CRC checks and no shell commands. */
final class ArchiveFiles {
    interface Progress {void update(long done,long total) throws IOException;}
    static String hash(File file) throws Exception {
        MessageDigest sha=MessageDigest.getInstance("SHA-256");try(InputStream in=new FileInputStream(file)){byte[] b=new byte[1024*1024];int n;while((n=in.read(b))!=-1)sha.update(b,0,n);}
        StringBuilder text=new StringBuilder();for(byte b:sha.digest())text.append(String.format(Locale.ROOT,"%02x",b));return text.toString();
    }
    static void remove(File root) throws IOException {
        if(Files.isSymbolicLink(root.toPath()))throw new IOException("Ссылка вместо папки распаковки.");
        if(!root.exists())return;
        Files.walkFileTree(root.toPath(),new SimpleFileVisitor<Path>(){
            @Override public FileVisitResult visitFile(Path p,java.nio.file.attribute.BasicFileAttributes a)throws IOException{Files.delete(p);return FileVisitResult.CONTINUE;}
            @Override public FileVisitResult postVisitDirectory(Path p,IOException e)throws IOException{if(e!=null)throw e;Files.delete(p);return FileVisitResult.CONTINUE;}
        });
    }
    static File expand(File archive,File release,Progress progress) throws Exception {
        String key=hash(archive);File base=new File(release,".qcatalog-expanded");
        if(Files.isSymbolicLink(base.toPath()))throw new IOException("Ссылка вместо папки распаковки.");
        File complete=new File(base,key),marker=new File(base,key+".complete");
        if(Files.isSymbolicLink(complete.toPath())||Files.isSymbolicLink(marker.toPath()))throw new IOException("Ссылки в папке распаковки.");
        if(marker.isFile()&&complete.isDirectory())return complete;
        File partial=new File(base,key+".partial");remove(partial);remove(complete);if(marker.exists())Files.delete(marker.toPath());
        if(!partial.mkdirs())throw new IOException("Не удалось создать папку распаковки.");
        try(SevenZFile seven=SevenZFile.builder().setFile(archive).setMaxMemoryLimitKiB(262144).get()) {
            long total=0;int count=0;Set<String> names=new HashSet<>();
            for(SevenZArchiveEntry e:seven.getEntries()) {
                String name=InstallPlan.relative(e.getName());
                if(++count>100000||!names.add(name.toLowerCase(Locale.ROOT))||e.isAntiItem()||(e.getHasWindowsAttributes()&&(e.getWindowsAttributes()&0x400)!=0))throw new IOException("Неподдерживаемая запись в архиве 7z.");
                if(e.getHasWindowsAttributes()&&(e.getWindowsAttributes()&0x8000)!=0&&((e.getWindowsAttributes()>>>16)&0170000)==0120000)throw new IOException("Ссылки в архиве не поддерживаются.");
                if(e.getSize()<0||e.getSize()>32L*1024*1024*1024)throw new IOException("Слишком большой файл в архиве.");
                total=Math.addExact(total,e.getSize());if(total>64L*1024*1024*1024)throw new IOException("Слишком большой архив.");
            }
            if(release.getUsableSpace()<total+64L*1024*1024)throw new IOException("Недостаточно места для распаковки 7z: нужно "+DownloadFiles.format(total)+".");
            long done=0,last=0;progress.update(0,total);SevenZArchiveEntry entry;
            while((entry=seven.getNextEntry())!=null) {
                String name=InstallPlan.relative(entry.getName());File target=new File(partial,name);
                if(!target.getCanonicalPath().startsWith(partial.getCanonicalPath()+File.separator))throw new IOException("Архив выходит за папку релиза.");
                if(entry.isDirectory()){if(!target.isDirectory()&&!target.mkdirs())throw new IOException("Не удалось создать папку архива.");continue;}
                if(!target.getParentFile().isDirectory()&&!target.getParentFile().mkdirs())throw new IOException("Не удалось создать папку архива.");
                long written=0;try(FileOutputStream out=new FileOutputStream(target)){byte[] b=new byte[1024*1024];int n;while((n=seven.read(b))!=-1){out.write(b,0,n);written+=n;done+=n;long now=System.nanoTime();if(now-last>250000000L){last=now;progress.update(done,total);}}out.getFD().sync();}
                if(written!=entry.getSize())throw new IOException("Неполный файл в архиве 7z.");
            }
            progress.update(done,total);
        }catch(Exception e){remove(partial);throw e;}
        Files.move(partial.toPath(),complete.toPath());Files.write(marker.toPath(),new byte[]{1});return complete;
    }
}
