package ru.local.qcatalog;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** Declarative translation of known QLoader commands. Never launches a shell or deletes installed data. */
final class InstallPlan {
    final LinkedHashMap<File,String> files=new LinkedHashMap<>();
    File apk; boolean preservesOldData;
    static String relative(String value) throws IOException {
        String path=value.replace('\\','/');
        while(path.endsWith("/"))path=path.substring(0,path.length()-1);
        if(path.isEmpty()||path.startsWith("/")||path.contains(":")||path.contains("\0"))throw new IOException("Недопустимый путь в install.txt.");
        for(String part:path.split("/",-1))if(part.isEmpty()||part.equals(".")||part.equals("..")||part.startsWith(".qcatalog"))throw new IOException("Недопустимый путь в install.txt.");
        return path;
    }
    private static String storageRelative(String value) throws IOException {
        String path=value.replace('\\','/');
        if(path.startsWith("/sdcard/"))path=path.substring(8);
        else if(path.startsWith("/storage/emulated/0/"))path=path.substring(20);
        else throw new IOException("install.txt: поддерживаются пути общего накопителя /sdcard/.");
        return path.isEmpty()?"":relative(path);
    }
    static String destination(String value,String pkg) throws IOException {
        String path=storageRelative(value);if(path.isEmpty())return "";
        String top=path.split("/")[0].toLowerCase(Locale.ROOT);
        if(top.equals("android")) {
            if(!path.equals("Android/obb/"+pkg)&&!path.startsWith("Android/obb/"+pkg+"/"))throw new IOException("install.txt: доступ к чужой папке Android запрещён.");
        }else if(Arrays.asList("dcim","pictures","movies","music","download","downloads","documents","alarms","notifications","ringtones","qcatalog backups").contains(top))throw new IOException("install.txt: общая пользовательская папка не является папкой игры.");
        return path;
    }
    static List<String> words(String line) throws IOException {
        List<String> result=new ArrayList<>();StringBuilder word=new StringBuilder();boolean quoted=false;
        for(char ch:line.toCharArray()) {
            if(ch=='"'){quoted=!quoted;continue;}
            if(";&|><`$\0".indexOf(ch)>=0)throw new IOException("install.txt: составные команды не поддерживаются.");
            if(Character.isWhitespace(ch)&&!quoted){if(word.length()>0){result.add(word.toString());word.setLength(0);}}else word.append(ch);
        }
        if(quoted)throw new IOException("install.txt: незакрытая кавычка.");
        if(word.length()>0)result.add(word.toString());return result;
    }
    static File lookup(File root,String path) throws IOException {
        File current=root;
        for(String part:relative(path).split("/")) {
            File match=null;File[] entries=current.listFiles();if(entries==null)return null;
            for(File f:entries)if(f.getName().equalsIgnoreCase(part)){if(match!=null)throw new IOException("Неоднозначный регистр имени файла в релизе.");match=f;}
            if(match==null)return null;if(Files.isSymbolicLink(match.toPath()))throw new IOException("Ссылки в релизах не поддерживаются.");current=match;
        }
        return current;
    }
    static File source(List<File> roots,String path) throws IOException {
        File result=null;for(File root:roots){File f=lookup(root,path);if(f!=null){if(result!=null)throw new IOException("Неоднозначный источник в install.txt.");result=f;}}
        if(result==null)throw new IOException("install.txt: не найден источник «"+path+"» после распаковки.");return result;
    }
    private void add(File source,String destination,Set<String> targets) throws IOException {
        if(Files.isSymbolicLink(source.toPath()))throw new IOException("Ссылки в данных игры не поддерживаются.");
        if(source.isDirectory()) {File[] children=source.listFiles();if(children==null)throw new IOException("Не удалось прочитать данные игры.");for(File child:children)add(child,destination+"/"+child.getName(),targets);}
        else {String safe=relative(destination);if(!targets.add(safe.toLowerCase(Locale.ROOT)))throw new IOException("install.txt: два файла записываются по одному пути.");files.put(source,safe);}
    }
    static InstallPlan read(File script,List<File> roots,String pkg) throws Exception {
        if(script.length()>65536)throw new IOException("install.txt слишком велик.");
        InstallPlan plan=new InstallPlan();Set<String> targets=new HashSet<>();int lineNumber=0;
        for(String raw:new String(Files.readAllBytes(script.toPath()),StandardCharsets.UTF_8).replace("\ufeff","").split("[\r\n]+")) {
            lineNumber++;String line=raw.trim();if(line.isEmpty()||line.startsWith("#")||line.equalsIgnoreCase("@echo off")||line.toUpperCase(Locale.ROOT).startsWith("REM "))continue;
            List<String> w=words(line);
            if(w.size()>=3&&w.get(0).equalsIgnoreCase("adb")&&w.get(1).equalsIgnoreCase("install")) {
                for(int i=2;i<w.size()-1;i++)if(!Arrays.asList("-g","-r","-d").contains(w.get(i)))throw new IOException("install.txt: неподдерживаемый параметр установки APK.");
                File apk=source(roots,w.get(w.size()-1));if(!apk.isFile()||!apk.getName().toLowerCase(Locale.ROOT).endsWith(".apk")||plan.apk!=null)throw new IOException("install.txt: требуется один APK.");plan.apk=apk;
            }else if(w.size()==4&&w.get(0).equalsIgnoreCase("adb")&&w.get(1).equalsIgnoreCase("push")) {
                File source=source(roots,w.get(2));String dest=storageRelative(w.get(3));
                // adb push DIR EXISTING_DIR copies DIR itself; a non-root destination names the directory.
                if(dest.isEmpty()||w.get(3).endsWith("/"))dest=dest.isEmpty()?source.getName():dest+"/"+source.getName();
                destination("/sdcard/"+dest,pkg);plan.add(source,dest,targets);
            }else if(w.size()==5&&w.get(0).equalsIgnoreCase("adb")&&w.get(1).equals("shell")&&w.get(2).equals("pm")&&w.get(3).equals("uninstall")&&w.get(4).equals(pkg)) {
                plan.preservesOldData=true;
            }else if(w.size()==5&&w.get(0).equalsIgnoreCase("adb")&&w.get(1).equals("shell")&&w.get(2).equals("rm")&&w.get(3).equals("-rf")&&!destination(w.get(4),pkg).isEmpty()) {
                plan.preservesOldData=true;
            }else throw new IOException("install.txt: команда в строке "+lineNumber+" пока не поддерживается. Файлы сохранены.");
        }
        if(plan.apk==null)throw new IOException("install.txt не указал APK для установки.");return plan;
    }
}
