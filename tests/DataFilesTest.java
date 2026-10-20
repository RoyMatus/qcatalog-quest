package ru.local.qcatalog;
import java.io.*;
import java.nio.file.*;
import java.util.*;

public final class DataFilesTest {
    static int checks;
    static void require(boolean ok,String message){checks++;if(!ok)throw new AssertionError(message);}
    public static void main(String[] args)throws Exception {
        File fixture=Files.createTempDirectory("qc-data-test").toFile();
        try {
            File source=new File(fixture,"source.bin");byte[] payload=new byte[2097152];new Random(7).nextBytes(payload);Files.write(source.toPath(),payload);
            Map<File,String> files=new LinkedHashMap<>();files.put(source,"sub/target.bin");File root=new File(fixture,"installed");List<String> stages=new ArrayList<>();
            DataFiles.copy(files,root,(verify,done,total)->{require(done>=0&&done<=total,"Real byte progress is bounded");stages.add(verify+":"+done+":"+total);});
            File destination=new File(root,"sub/target.bin");require(Arrays.equals(payload,Files.readAllBytes(destination.toPath())),"Installed bytes match source");require(source.exists(),"Source survives copy and verification");require(stages.get(stages.size()-1).equals("true:"+payload.length+":"+payload.length),"Verification finishes at exact total");require(!new File(destination.getPath()+".qcatalog-part").exists(),"Verified files leave no staging file");
            byte[] old={1,2,3};Files.write(destination.toPath(),old);final boolean[] changed={false};boolean bad=false;
            try { DataFiles.copy(files,root,(verify,done,total)->{if(verify&&!changed[0]){changed[0]=true;try{Files.write(new File(destination.getPath()+".qcatalog-part").toPath(),new byte[]{9});}catch(Exception e){throw new RuntimeException(e);}}}); }catch(IOException expected){bad=true;}
            require(bad,"Corrupted pending data is rejected");require(Arrays.equals(old,Files.readAllBytes(destination.toPath())),"Failed verification preserves previous installed data");require(Arrays.equals(payload,Files.readAllBytes(source.toPath())),"Failure retains downloaded source for retry");
            files.put(source,"../escape");boolean traversal=false;try{DataFiles.copy(files,root,(v,d,t)->{});}catch(IOException expected){traversal=true;}require(traversal&&!new File(fixture,"escape").exists(),"Data path traversal is rejected");
            System.out.println("PASS "+checks+" data copying/progress/verification checks");
        }finally{Files.walk(fixture.toPath()).sorted(Comparator.reverseOrder()).forEach(p->{try{Files.delete(p);}catch(IOException e){throw new RuntimeException(e);}});}
    }
}
