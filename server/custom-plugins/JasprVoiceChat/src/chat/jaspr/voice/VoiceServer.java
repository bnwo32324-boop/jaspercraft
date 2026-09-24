package chat.jaspr.voice;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.Base64;

/**
 * Loopback-only WebSocket listener. It never faces the public internet: the Jaspr gateway
 * terminates TLS, authenticates the browser session, and forwards the upgrade here.
 */
final class VoiceServer {
    private static final String GUID = "258EAFA5-E914-47DA-95CA-C5AB0DC85B11";
    private static final int MAX_HEADER_LINES = 48;
    private static final int MAX_HEADER_LENGTH = 2048;

    interface Acceptor {
        /** Called on the connection thread once the handshake and identity check both pass. */
        void accepted(WebSocketConnection connection, String playerName);
    }

    private final int port;
    private final EdgeAuth auth;
    private final VoiceLog log;
    private final Acceptor acceptor;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicInteger connectionSequence = new AtomicInteger();
    private ServerSocket serverSocket;
    private Thread acceptThread;

    VoiceServer(int port, EdgeAuth auth, VoiceLog log, Acceptor acceptor) {
        this.port = port;
        this.auth = auth;
        this.log = log;
        this.acceptor = acceptor;
    }

    void start() throws IOException {
        if (!running.compareAndSet(false, true)) return;
        serverSocket = new ServerSocket();
        serverSocket.setReuseAddress(true);
        serverSocket.bind(new InetSocketAddress(InetAddress.getByName("127.0.0.1"), port), 32);
        acceptThread = new Thread(new Runnable() {
            @Override public void run() { acceptLoop(); }
        }, "jaspr-voice-accept");
        acceptThread.setDaemon(true);
        acceptThread.start();
    }

    void stop() {
        if (!running.compareAndSet(true, false)) return;
        try { if (serverSocket != null) serverSocket.close(); } catch (IOException ignored) { /* shutting down */ }
        if (acceptThread != null) acceptThread.interrupt();
    }

    private void acceptLoop() {
        while (running.get()) {
            final Socket socket;
            try {
                socket = serverSocket.accept();
            } catch (IOException stopped) {
                if (running.get()) log.warn("voice.accept.failed");
                return;
            }
            final String name = "jaspr-voice-" + connectionSequence.incrementAndGet();
            Thread worker = new Thread(new Runnable() {
                @Override public void run() { serve(socket, name); }
            }, name);
            worker.setDaemon(true);
            worker.start();
        }
    }

    private void serve(Socket socket, String threadName) {
        try {
            socket.setTcpNoDelay(true);
            socket.setSoTimeout(45_000);
            boolean loopbackPeer = socket.getInetAddress() != null && socket.getInetAddress().isLoopbackAddress();
            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(socket.getInputStream(), StandardCharsets.US_ASCII), 1);
            String requestLine = readLine(reader);
            if (requestLine == null || !requestLine.startsWith("GET ")) { reject(socket, 400, "Bad Request"); return; }
            Map<String, String> headers = readHeaders(reader);
            if (headers == null) { reject(socket, 431, "Request Header Fields Too Large"); return; }

            String upgrade = headers.get("upgrade");
            String key = headers.get("sec-websocket-key");
            if (upgrade == null || !upgrade.toLowerCase(Locale.ROOT).contains("websocket") || key == null) {
                reject(socket, 400, "Bad Request");
                return;
            }
            String player = auth.verify(headers, loopbackPeer);
            if (player == null) {
                log.info("voice.handshake.rejected reason=identity");
                reject(socket, 403, "Forbidden");
                return;
            }
            writeHandshake(socket.getOutputStream(), key);
            socket.setSoTimeout(0);
            WebSocketConnection connection = new WebSocketConnection(socket, threadName);
            acceptor.accepted(connection, player);
        } catch (Exception failed) {
            try { socket.close(); } catch (IOException ignored) { /* nothing left to do */ }
        }
    }

    private static String readLine(BufferedReader reader) throws IOException {
        StringBuilder line = new StringBuilder();
        while (line.length() <= MAX_HEADER_LENGTH) {
            int ch = reader.read();
            if (ch < 0) return line.length() == 0 ? null : line.toString();
            if (ch == '\n') return line.toString().trim();
            if (ch != '\r') line.append((char) ch);
        }
        return null;
    }

    private static Map<String, String> readHeaders(BufferedReader reader) throws IOException {
        Map<String, String> headers = new HashMap<String, String>();
        for (int i = 0; i < MAX_HEADER_LINES; i++) {
            String line = readLine(reader);
            if (line == null) return null;
            if (line.isEmpty()) return headers;
            int colon = line.indexOf(':');
            if (colon <= 0) continue;
            headers.put(line.substring(0, colon).trim().toLowerCase(Locale.ROOT), line.substring(colon + 1).trim());
        }
        return null;
    }

    private static void writeHandshake(OutputStream out, String key) throws Exception {
        MessageDigest sha1 = MessageDigest.getInstance("SHA-1");
        byte[] accept = sha1.digest((key + GUID).getBytes(StandardCharsets.US_ASCII));
        String response = "HTTP/1.1 101 Switching Protocols\r\n"
                + "Upgrade: websocket\r\n"
                + "Connection: Upgrade\r\n"
                + "Sec-WebSocket-Accept: " + Base64.getEncoder().encodeToString(accept) + "\r\n\r\n";
        out.write(response.getBytes(StandardCharsets.US_ASCII));
        out.flush();
    }

    private static void reject(Socket socket, int status, String message) {
        try {
            String response = "HTTP/1.1 " + status + " " + message + "\r\n"
                    + "Connection: close\r\n"
                    + "Content-Length: 0\r\n\r\n";
            socket.getOutputStream().write(response.getBytes(StandardCharsets.US_ASCII));
            socket.getOutputStream().flush();
        } catch (IOException ignored) {
            // The peer already went away.
        } finally {
            try { socket.close(); } catch (IOException ignored) { /* nothing left to do */ }
        }
    }
}
