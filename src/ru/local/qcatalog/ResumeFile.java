package ru.local.qcatalog;

import java.io.*;
import java.nio.file.*;

/** Durable byte checkpoint; incomplete files never use their final filename. */
final class ResumeFile extends OutputStream {
    static final long CHECKPOINT_BYTES = 4L * 1024 * 1024;
    final File target, partial, metadata;
    final long expected;
    final String identity;
    private RandomAccessFile file;
    private long position, committed;
    private boolean complete;
    static File resolve(File base, String relative) throws IOException {
        if (relative == null || relative.isEmpty() || relative.length() > 2048 || relative.startsWith("/") || relative.contains("\\") || relative.indexOf('\0') >= 0)
            throw new IOException("Некорректный путь файла релиза.");
        File value = base;
        for (String part : relative.split("/",-1)) {
            if (part.isEmpty() || part.equals(".") || part.equals("..") || part.endsWith(".qcatalog-part") || part.endsWith(".qcatalog-resume") || part.endsWith(".qcatalog-resume.tmp")) throw new IOException("Некорректный путь файла релиза.");
            value = new File(value,part); if (Files.isSymbolicLink(value.toPath())) throw new IOException("Ссылки в загрузках не поддерживаются.");
        }
        if (!value.getCanonicalPath().startsWith(base.getCanonicalPath() + File.separator)) throw new IOException("Файл выходит за пределы загрузки.");
        return value;
    }
    ResumeFile(File target, long expected, String identity) throws IOException {
        if (expected < 0) throw new IOException("Некорректный размер файла.");
        this.target=target; this.expected=expected; this.identity=identity;
        partial=new File(target.getPath()+".qcatalog-part"); metadata=new File(target.getPath()+".qcatalog-resume");
        for (File path : new File[]{target,partial,metadata,new File(metadata.getPath()+".tmp")}) if (Files.isSymbolicLink(path.toPath())) throw new IOException("Ссылки в загрузках не поддерживаются.");
        if (target.isFile() && target.length() == expected) {
            Files.deleteIfExists(partial.toPath()); Files.deleteIfExists(metadata.toPath()); Files.deleteIfExists(new File(metadata.getPath()+".tmp").toPath());
            complete=true; position=committed=expected; return;
        }
        if (!target.getParentFile().isDirectory() && !target.getParentFile().mkdirs()) throw new IOException("Не удалось создать папку загрузки.");
        long saved=0;
        if (partial.isFile() && metadata.isFile()) {
            try (DataInputStream in=new DataInputStream(new FileInputStream(metadata))) {
                if (in.readUTF().equals(identity)) { long count=in.readLong(); if (count >= 0 && count <= expected && count <= partial.length()) saved=count; }
            } catch (IOException ignored) { }
        }
        file=new RandomAccessFile(partial,"rw"); file.setLength(saved); file.seek(saved); position=committed=saved;
        try { checkpoint(); } catch (IOException e) { file.close(); file=null; throw e; }
    }
    long position() { return position; }
    boolean isComplete() { return complete; }
    @Override public void write(int value) throws IOException { write(new byte[]{(byte)value},0,1); }
    @Override public void write(byte[] buffer,int offset,int length) throws IOException {
        if (file == null || position > expected - length) throw new IOException("Сервер вернул лишние данные файла.");
        file.write(buffer,offset,length); position+=length;
        if (position-committed >= CHECKPOINT_BYTES) checkpoint();
    }
    private void checkpoint() throws IOException {
        file.getFD().sync();
        File next=new File(metadata.getPath()+".tmp");
        try (FileOutputStream raw=new FileOutputStream(next); DataOutputStream out=new DataOutputStream(raw)) {
            out.writeUTF(identity); out.writeLong(position); out.flush(); raw.getFD().sync();
        }
        Files.move(next.toPath(),metadata.toPath(),StandardCopyOption.REPLACE_EXISTING); committed=position;
    }
    void finish() throws IOException {
        if (complete) return;
        if (position != expected) throw new IOException("Файл скачан не полностью; сохранена позиция для продолжения.");
        checkpoint(); file.close(); file=null;
        Files.move(partial.toPath(),target.toPath(),StandardCopyOption.REPLACE_EXISTING);
        Files.deleteIfExists(metadata.toPath()); complete=true;
    }
    @Override public void close() throws IOException {
        if (file != null) { try { checkpoint(); } finally { file.close(); file=null; } }
    }
}
