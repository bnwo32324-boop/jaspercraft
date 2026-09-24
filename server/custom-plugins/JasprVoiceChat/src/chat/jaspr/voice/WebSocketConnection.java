package chat.jaspr.voice;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * One RFC 6455 connection. Reading happens on the accept-spawned thread; writing happens on a
 * dedicated thread behind a bounded queue so that a stalled listener can never block the relay.
 * Voice is realtime: when the queue is full the oldest audio is dropped rather than buffered.
 */
final class WebSocketConnection {
    private static final int MAX_MESSAGE_BYTES = 8192;
    private static final int SEND_QUEUE_DEPTH = 96;

    interface Handler {
        void onMessage(WebSocketConnection connection, byte[] payload) throws IOException;
        void onClosed(WebSocketConnection connection);
    }

    private final Socket socket;
    private final InputStream in;
    private final OutputStream out;
    private final ArrayBlockingQueue<byte[]> outbound = new ArrayBlockingQueue<byte[]>(SEND_QUEUE_DEPTH);
    private final AtomicBoolean closed = new AtomicBoolean(false);
    private final Thread writer;
    private volatile long droppedFrames;

    WebSocketConnection(Socket socket, String threadName) throws IOException {
        this.socket = socket;
        this.in = socket.getInputStream();
        this.out = socket.getOutputStream();
        this.writer = new Thread(new Runnable() {
            @Override public void run() { pumpOutbound(); }
        }, threadName + "-tx");
        this.writer.setDaemon(true);
        this.writer.start();
    }

    long droppedFrames() { return droppedFrames; }

    boolean isClosed() { return closed.get(); }

    /** Queues one binary frame. Returns false when the frame was dropped or the peer is gone. */
    boolean send(byte[] payload) {
        if (closed.get()) return false;
        if (outbound.offer(payload)) return true;
        // Make room by discarding the oldest queued frame: stale audio is worse than no audio.
        outbound.poll();
        droppedFrames++;
        return outbound.offer(payload);
    }

    private void pumpOutbound() {
        try {
            while (!closed.get()) {
                byte[] payload = outbound.poll(1, TimeUnit.SECONDS);
                if (payload == null) continue;
                writeFrame(0x2, payload);
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        } catch (IOException gone) {
            // Peer disappeared; the read loop performs teardown.
        } finally {
            close();
        }
    }

    private synchronized void writeFrame(int opcode, byte[] payload) throws IOException {
        int length = payload.length;
        byte[] header;
        if (length < 126) {
            header = new byte[] { (byte) (0x80 | opcode), (byte) length };
        } else if (length < 65536) {
            header = new byte[] { (byte) (0x80 | opcode), 126, (byte) (length >>> 8), (byte) length };
        } else {
            throw new IOException("Frame too large for this relay.");
        }
        out.write(header);
        out.write(payload);
        out.flush();
    }

    private synchronized void writeControl(int opcode, byte[] payload) throws IOException {
        out.write(new byte[] { (byte) (0x80 | opcode), (byte) payload.length });
        out.write(payload);
        out.flush();
    }

    /** Blocking read loop. Returns when the peer closes or misbehaves. */
    void readLoop(Handler handler) {
        try {
            byte[] assembled = null;
            int assembledOpcode = 0;
            while (!closed.get()) {
                int b0 = readByte();
                int b1 = readByte();
                boolean fin = (b0 & 0x80) != 0;
                int opcode = b0 & 0x0F;
                boolean masked = (b1 & 0x80) != 0;
                int length = b1 & 0x7F;
                if (length == 126) {
                    length = (readByte() << 8) | readByte();
                } else if (length == 127) {
                    long extended = 0;
                    for (int i = 0; i < 8; i++) extended = (extended << 8) | readByte();
                    if (extended > MAX_MESSAGE_BYTES) throw new IOException("Oversized frame.");
                    length = (int) extended;
                }
                if (!masked) throw new IOException("Client frames must be masked.");
                if (length > MAX_MESSAGE_BYTES) throw new IOException("Oversized frame.");
                byte[] mask = new byte[4];
                readFully(mask);
                byte[] payload = new byte[length];
                readFully(payload);
                for (int i = 0; i < length; i++) payload[i] = (byte) (payload[i] ^ mask[i & 3]);

                if (opcode == 0x8) { return; }
                if (opcode == 0x9) { writeControl(0xA, payload); continue; }
                if (opcode == 0xA) { continue; }

                if (opcode == 0x0) {
                    if (assembled == null) throw new IOException("Unexpected continuation.");
                    assembled = concat(assembled, payload);
                    if (assembled.length > MAX_MESSAGE_BYTES) throw new IOException("Oversized message.");
                } else if (opcode == 0x1 || opcode == 0x2) {
                    if (assembled != null) throw new IOException("Interleaved message.");
                    assembled = payload;
                    assembledOpcode = opcode;
                } else {
                    throw new IOException("Unsupported opcode.");
                }
                if (fin) {
                    byte[] message = assembled;
                    assembled = null;
                    if (assembledOpcode == 0x2) handler.onMessage(this, message);
                }
            }
        } catch (IOException expected) {
            // Normal disconnect path.
        } finally {
            try { writeControl(0x8, new byte[0]); } catch (IOException ignored) { /* already gone */ }
            close();
            handler.onClosed(this);
        }
    }

    private static byte[] concat(byte[] left, byte[] right) {
        byte[] result = new byte[left.length + right.length];
        System.arraycopy(left, 0, result, 0, left.length);
        System.arraycopy(right, 0, result, left.length, right.length);
        return result;
    }

    private int readByte() throws IOException {
        int value = in.read();
        if (value < 0) throw new EOFException();
        return value;
    }

    private void readFully(byte[] buffer) throws IOException {
        int offset = 0;
        while (offset < buffer.length) {
            int read = in.read(buffer, offset, buffer.length - offset);
            if (read < 0) throw new EOFException();
            offset += read;
        }
    }

    void close() {
        if (!closed.compareAndSet(false, true)) return;
        outbound.clear();
        try { socket.close(); } catch (IOException ignored) { /* nothing left to do */ }
    }
}
