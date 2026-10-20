package ru.local.qcatalog;

import com.hierynomus.smbj.*;
import com.hierynomus.smbj.auth.AuthenticationContext;
import com.hierynomus.smbj.connection.Connection;
import com.hierynomus.smbj.session.Session;
import com.hierynomus.smbj.share.DiskShare;
import com.hierynomus.msdtyp.AccessMask;
import com.hierynomus.msfscc.FileAttributes;
import com.hierynomus.msfscc.fileinformation.FileIdBothDirectoryInformation;
import com.hierynomus.mssmb2.*;
import com.hierynomus.security.bc.BCSecurityProvider;
import com.rapid7.client.dcerpc.transport.*;
import com.rapid7.client.dcerpc.mssrvs.ServerService;
import com.rapid7.client.dcerpc.mssrvs.dto.NetShareInfo0;
import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/** Read-only SMB2/3 browser. Credentials live only in this connection's RAM. */
final class NetworkShare implements AutoCloseable {
    static final class Entry {final String name;final boolean directory;final long bytes;Entry(String name,boolean directory,long bytes){this.name=name;this.directory=directory;this.bytes=bytes;}}
    interface Progress {void update(long done,long total)throws IOException;}
    private final SMBClient client;private Connection connection;private Session session;private DiskShare disk;private char[] secret;private List<String> listedShares;private volatile boolean closed;
    NetworkShare(){client=new SMBClient(SmbConfig.builder().withSecurityProvider(new BCSecurityProvider()).withDfsEnabled(false).withTimeout(15,TimeUnit.SECONDS).withSoTimeout(15,TimeUnit.SECONDS).build());}
    void connect(NetworkAddress address,String user,char[] password)throws Exception{
        InetAddress selected=null;for(InetAddress resolved:InetAddress.getAllByName(address.host))if(resolved.isSiteLocalAddress()||resolved.isLoopbackAddress()||resolved.isLinkLocalAddress()){selected=resolved;break;}
        if(selected==null)throw new IOException("Нужен компьютер в локальной сети.");if(closed)throw new InterruptedIOException();connection=client.connect(selected.getHostAddress(),address.port);if(closed){connection.close();throw new InterruptedIOException();}
        String domain="";int split=user.indexOf('\\');if(split>=0){domain=user.substring(0,split);user=user.substring(split+1);}secret=password.clone();session=connection.authenticate(user.trim().isEmpty()?AuthenticationContext.anonymous():new AuthenticationContext(user,secret,domain));if(closed){session.close();throw new InterruptedIOException();}
    }
    List<String> shares()throws Exception{if(listedShares!=null)return new ArrayList<>(listedShares);List<String> names=new ArrayList<>();{RPCTransport transport=SMBTransportFactories.SRVSVC.getTransport(session);for(NetShareInfo0 row:new ServerService(transport).getShares0()){String name=row.getNetName();if(!name.endsWith("$")){NetworkAddress.component(name);names.add(name);}}}names.sort(String.CASE_INSENSITIVE_ORDER);listedShares=new ArrayList<>(names);return names;}
    void open(String name)throws Exception{NetworkAddress.component(name);if(disk!=null)disk.close();disk=(DiskShare)session.connectShare(name);}
    List<Entry> list(String path)throws Exception{List<Entry> result=new ArrayList<>();for(FileIdBothDirectoryInformation row:disk.list(path)){String name=row.getFileName();if(name.equals(".")||name.equals(".."))continue;NetworkAddress.component(name);boolean directory=(row.getFileAttributes()&FileAttributes.FILE_ATTRIBUTE_DIRECTORY.getValue())!=0;if(directory||name.toLowerCase(Locale.ROOT).endsWith(".apk"))result.add(new Entry(name,directory,row.getEndOfFile()));}result.sort(Comparator.comparing((Entry e)->!e.directory).thenComparing(e->e.name.toLowerCase(Locale.ROOT)));return result;}
    void download(String path,long size,java.io.File destination,AtomicBoolean cancelled,Progress progress)throws Exception{
        if(size<=0||destination.getParentFile().getUsableSpace()<size+64L*1024*1024)throw new IOException("Недостаточно места для APK или файл пуст.");boolean success=false;
        try(com.hierynomus.smbj.share.File remote=disk.openFile(path,EnumSet.of(AccessMask.GENERIC_READ),EnumSet.of(FileAttributes.FILE_ATTRIBUTE_NORMAL),EnumSet.of(SMB2ShareAccess.FILE_SHARE_READ),SMB2CreateDisposition.FILE_OPEN,EnumSet.of(SMB2CreateOptions.FILE_NON_DIRECTORY_FILE));InputStream input=remote.getInputStream();FileOutputStream output=new FileOutputStream(destination)){
            byte[] buffer=new byte[256*1024];long done=0,last=0;int read;while((read=input.read(buffer))!=-1){if(closed||cancelled.get()||Thread.currentThread().isInterrupted())throw new InterruptedIOException("Передача отменена.");done+=read;if(done>size)throw new IOException("Размер сетевого файла изменился. Выбери его снова.");output.write(buffer,0,read);long now=System.nanoTime();if(now-last>250000000||done==size){last=now;progress.update(done,size);}}if(done!=size)throw new IOException("APK получен не полностью. Выбери его снова.");output.getFD().sync();success=true;
        }finally{if(!success&&destination.exists()&&!destination.delete())destination.deleteOnExit();}
    }
    @Override public void close(){closed=true;try{if(connection!=null)connection.close(true);}catch(Exception ignored){}try{client.close();}catch(Exception ignored){}if(secret!=null)Arrays.fill(secret,'\0');}
    static String error(Exception e){String type=e.toString();if(type.contains("LOGON_FAILURE")||type.contains("ACCESS_DENIED")||type.contains("ACCOUNT_"))return "Нет доступа. Введи логин и пароль Windows; PIN-код не подходит. Проверь разрешение чтения общей папки.";if(e instanceof IOException&&e.getMessage()!=null&&!type.contains("com.hierynomus"))return e.getMessage();return "Не удалось открыть сетевую папку. Проверь адрес, общий доступ, логин и соединение Wi-Fi.";}
}
