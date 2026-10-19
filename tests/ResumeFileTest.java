package ru.local.qcatalog;

import java.io.*;
import java.nio.file.*;
import java.util.Arrays;

public final class ResumeFileTest {
    static int checks;
    static void require(boolean value) { checks++; if (!value) throw new AssertionError("Resume check " + checks); }
    public static void main(String[] args) throws Exception {
        File root=Files.createTempDirectory("qcatalog-resume-").toFile();
        try {
            File target=ResumeFile.resolve(root,"nested/test.bin"); byte[] data=new byte[1200000];
            for (int i=0;i<data.length;i++) data[i]=(byte)(i*31);
            try (ResumeFile file=new ResumeFile(target,data.length,"version-one")) { require(file.position()==0); file.write(data,0,500000); }
            require(!target.exists());
            try (FileOutputStream out=new FileOutputStream(target.getPath()+".qcatalog-part",true)) { out.write(new byte[123]); }
            try (ResumeFile file=new ResumeFile(target,data.length,"version-one")) {
                require(file.position()==500000); require(new File(target.getPath()+".qcatalog-part").length()==500000);
                file.write(data,500000,data.length-500000); file.finish();
            }
            require(Arrays.equals(data,Files.readAllBytes(target.toPath())));
            require(!new File(target.getPath()+".qcatalog-resume").exists());
            try (ResumeFile file=new ResumeFile(target,data.length,"version-one")) { require(file.isComplete()); require(file.position()==data.length); }
            File changed=ResumeFile.resolve(root,"changed.bin");
            try (ResumeFile file=new ResumeFile(changed,20,"old")) { file.write(new byte[10]); }
            try (ResumeFile file=new ResumeFile(changed,20,"new")) { require(file.position()==0); }
            boolean escaped=false; try { ResumeFile.resolve(root,"../outside"); } catch (IOException expected) { escaped=true; } require(escaped);
            boolean tooLarge=false; try (ResumeFile file=new ResumeFile(ResumeFile.resolve(root,"limit.bin"),1,"one")) { try { file.write(new byte[2]); } catch (IOException expected) { tooLarge=true; } } require(tooLarge);
            boolean reserved=false; try { ResumeFile.resolve(root,"file.qcatalog-part"); } catch (IOException expected) { reserved=true; } require(reserved);
            System.out.println("Resume tests passed ("+checks+" checks; interrupted tail, exact continuation, changed source and path containment)");
        } finally { try (java.util.stream.Stream<Path> files=Files.walk(root.toPath())) { for (Path path : (Iterable<Path>)files.sorted(java.util.Comparator.reverseOrder())::iterator) Files.delete(path); } }
    }
}
