package ru.local.qcatalog;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.*;
import java.util.concurrent.*;

/** CONNECT-only loopback tunnel. Android resolves hostnames; rclone retains end-to-end TLS. */
final class DnsTunnel implements AutoCloseable {
    private final ServerSocket server;
    private final Set<Socket> sockets = Collections.newSetFromMap(new ConcurrentHashMap<Socket,Boolean>());
    private final ThreadPoolExecutor workers = new ThreadPoolExecutor(0,16,30,TimeUnit.SECONDS,new SynchronousQueue<Runnable>());
    private final String password, authorization;
    private volatile boolean closed;
    DnsTunnel() throws IOException {
        byte[] random = new byte[24]; new SecureRandom().nextBytes(random);
        StringBuilder value = new StringBuilder(); for (byte b : random) value.append(String.format(Locale.ROOT,"%02x",b)); password = value.toString();
        authorization = "Basic " + Base64.getEncoder().encodeToString(("q:" + password).getBytes(StandardCharsets.US_ASCII));
        server = new ServerSocket(); server.bind(new InetSocketAddress(InetAddress.getByName("127.0.0.1"),0));
        Thread accept = new Thread(() -> {
            while (!closed) try {
                Socket socket = server.accept(); sockets.add(socket);
                try { workers.execute(() -> connect(socket)); } catch (RejectedExecutionException e) { discard(socket); }
            } catch (IOException e) { if (!closed) close(); }
        },"qcatalog-network-accept"); accept.setDaemon(true); accept.start();
    }
    String proxyUrl() { return "http://q:" + password + "@127.0.0.1:" + server.getLocalPort(); }
    private static String line(InputStream in) throws IOException {
        ByteArrayOutputStream data = new ByteArrayOutputStream(); int b;
        while ((b = in.read()) != -1) { if (b == '\n') return new String(data.toByteArray(),StandardCharsets.US_ASCII).replace("\r",""); if (data.size() >= 8192) throw new IOException("Header too long"); data.write(b); }
        throw new EOFException();
    }
    private static void respond(Socket socket, String status) throws IOException {
        socket.getOutputStream().write(("HTTP/1.1 " + status + "\r\nContent-Length: 0\r\n\r\n").getBytes(StandardCharsets.US_ASCII)); socket.getOutputStream().flush();
    }
    private static void copy(InputStream in, OutputStream out) throws IOException {
        byte[] buffer = new byte[65536]; int n; while ((n = in.read(buffer)) != -1) { out.write(buffer,0,n); out.flush(); }
    }
    private void connect(Socket client) {
        Socket remote = null;
        try {
            client.setSoTimeout(15000); BufferedInputStream input = new BufferedInputStream(client.getInputStream());
            String[] request = line(input).split(" "); String auth = ""; int length = 0;
            while (true) {
                String header = line(input); if (header.isEmpty()) break;
                length += header.length(); if (length > 16384) throw new IOException("Too many headers");
                int colon = header.indexOf(':'); if (colon > 0 && header.substring(0,colon).equalsIgnoreCase("Proxy-Authorization")) auth = header.substring(colon+1).trim();
            }
            if (!MessageDigest.isEqual(auth.getBytes(StandardCharsets.US_ASCII),authorization.getBytes(StandardCharsets.US_ASCII))) { respond(client,"407 Proxy Authentication Required"); return; }
            if (request.length != 3 || !request[0].equals("CONNECT")) { respond(client,"405 Method Not Allowed"); return; }
            URI target = new URI("https://" + request[1]);
            int port = target.getPort() < 0 ? 443 : target.getPort();
            if (target.getHost() == null || target.getUserInfo() != null || !target.getPath().isEmpty() || target.getQuery() != null || target.getFragment() != null || port < 1 || port > 65535)
                throw new IOException("Invalid destination");
            for (InetAddress address : InetAddress.getAllByName(target.getHost())) {
                if (address.isAnyLocalAddress() || address.isLoopbackAddress() || address.isLinkLocalAddress()) continue;
                Socket candidate = new Socket();
                try { candidate.connect(new InetSocketAddress(address,port),15000); remote = candidate; break; }
                catch (IOException e) { try { candidate.close(); } catch (IOException ignored) {} }
            }
            if (remote == null) throw new IOException("Server unavailable");
            sockets.add(remote); final Socket upstream = remote;
            client.setSoTimeout(60000); remote.setSoTimeout(60000);
            client.getOutputStream().write("HTTP/1.1 200 Connection Established\r\n\r\n".getBytes(StandardCharsets.US_ASCII)); client.getOutputStream().flush();
            Thread upload = new Thread(() -> { try { copy(input,upstream.getOutputStream()); upstream.shutdownOutput(); } catch (IOException ignored) {} },"qcatalog-network-upload");
            upload.setDaemon(true); upload.start();
            copy(remote.getInputStream(),client.getOutputStream());
        } catch (Exception ignored) { try { respond(client,"502 Bad Gateway"); } catch (IOException ignored2) {} }
        finally { discard(client); if (remote != null) discard(remote); }
    }
    private void discard(Socket socket) { sockets.remove(socket); try { socket.close(); } catch (IOException ignored) {} }
    @Override public void close() {
        closed = true; try { server.close(); } catch (IOException ignored) {}
        for (Socket socket : sockets) discard(socket); workers.shutdownNow();
    }
}
