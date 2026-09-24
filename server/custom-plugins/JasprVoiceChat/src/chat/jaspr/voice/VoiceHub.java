package chat.jaspr.voice;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Proximity routing. Audio payloads are relayed verbatim; this class only decides who may hear
 * whom and attaches the geometry each listener needs to place the sound.
 *
 * Culling is server-side and authoritative: a client never learns that a distant player is
 * speaking, so range cannot be widened by tampering with the browser overlay.
 */
final class VoiceHub implements WebSocketConnection.Handler {
    private final VoiceConfig config;
    private final SnapshotSource positions;
    private final VoiceLog log;
    private final Map<WebSocketConnection, VoiceSession> byConnection =
            new ConcurrentHashMap<WebSocketConnection, VoiceSession>();
    private final Map<String, VoiceSession> byPlayer = new ConcurrentHashMap<String, VoiceSession>();
    private final AtomicInteger idSequence = new AtomicInteger();

    VoiceHub(VoiceConfig config, SnapshotSource positions, VoiceLog log) {
        this.config = config;
        this.positions = positions;
        this.log = log;
    }

    int sessionCount() { return byConnection.size(); }

    VoiceSession sessionFor(String playerName) {
        return playerName == null ? null : byPlayer.get(PositionIndex.key(playerName));
    }

    void attach(WebSocketConnection connection, String playerName) {
        if (byConnection.size() >= config.maxClients) {
            sendNotice(connection, 2, "Voice chat is full right now.");
            connection.close();
            return;
        }
        VoiceSession previous = byPlayer.get(PositionIndex.key(playerName));
        if (previous != null) {
            // A reload leaves the old socket briefly alive. The newest session wins.
            sendNotice(previous.connection, 1, "Voice moved to a newer tab.");
            previous.connection.close();
            byConnection.remove(previous.connection);
        }
        int id = nextId();
        VoiceSession session = new VoiceSession(id, playerName, connection);
        byConnection.put(connection, session);
        byPlayer.put(PositionIndex.key(playerName), session);
        sendWelcome(session);
        log.info("voice.session.opened player=" + playerName + " sessions=" + byConnection.size());
        broadcastRoster();
    }

    private int nextId() {
        int id = idSequence.incrementAndGet() & 0xFFFF;
        return id == 0 ? idSequence.incrementAndGet() & 0xFFFF : id;
    }

    @Override public void onClosed(WebSocketConnection connection) {
        VoiceSession session = byConnection.remove(connection);
        if (session == null) return;
        byPlayer.remove(PositionIndex.key(session.playerName), session);
        log.info("voice.session.closed player=" + session.playerName + " sessions=" + byConnection.size());
        broadcastRoster();
    }

    @Override public void onMessage(WebSocketConnection connection, byte[] payload) throws IOException {
        VoiceSession session = byConnection.get(connection);
        if (session == null || payload.length < 1) return;
        int type = payload[0] & 0xFF;
        switch (type) {
            case VoiceProtocol.C_HELLO:
                session.helloReceived = true;
                if (payload.length >= 2) {
                    int announced = payload[1] & 0xFF;
                    session.protocolVersion = announced < 1 ? 1 : Math.min(announced, VoiceProtocol.VERSION);
                }
                break;
            case VoiceProtocol.C_STATE:
                if (payload.length >= 2) {
                    int flags = payload[1] & 0xFF;
                    session.micMuted = (flags & VoiceProtocol.STATE_MIC_MUTED) != 0;
                    session.deafened = (flags & VoiceProtocol.STATE_DEAFENED) != 0;
                    broadcastRoster();
                }
                break;
            case VoiceProtocol.C_PING:
                if (payload.length >= 5) {
                    byte[] pong = new byte[] { (byte) VoiceProtocol.S_PONG, payload[1], payload[2], payload[3], payload[4] };
                    connection.send(pong);
                }
                break;
            case VoiceProtocol.C_AUDIO:
                relayAudio(session, payload);
                break;
            default:
                break;
        }
    }

    /** payload: [type][seq hi][seq lo][codec][len hi][len lo][opus/adpcm bytes] */
    /**
     * Sends each listener what the world is doing to the voices they can hear.
     *
     * This is deliberately its own message rather than extra bytes on the audio frame. It runs at
     * a few times a second instead of per packet, and an overlay that predates it ignores an
     * unknown type and keeps working with plain, unfiltered voice.
     */
    void broadcastEnvironment() {
        if (!config.environmentEnabled) return;
        List<VoiceSession> sessions = new ArrayList<VoiceSession>(byConnection.values());
        for (VoiceSession listener : sessions) {
            if (listener.protocolVersion < 3) continue;
            PlayerSnapshot to = positions.get(listener.playerName);
            if (to == null) continue;

            byte[] body = new byte[3 + sessions.size() * 5];
            body[0] = (byte) VoiceProtocol.S_ENV;
            body[1] = (byte) (to.underwater ? VoiceProtocol.ENV_LISTENER_UNDERWATER : 0);
            int offset = 3;
            int count = 0;

            for (VoiceSession speaker : sessions) {
                if (speaker == listener) continue;
                PlayerSnapshot from = positions.get(speaker.playerName);
                if (from == null) continue;
                if (!config.crossWorld && !from.worldId.equals(to.worldId)) continue;
                double range = from.sneaking ? config.whisperDistance : config.maxDistance;
                double dx = from.x - to.x, dy = from.y - to.y, dz = from.z - to.z;
                if (dx * dx + dy * dy + dz * dz > range * range) continue;

                int env = 0;
                if (from.underwater) env |= VoiceProtocol.ENV_SPEAKER_UNDERWATER;
                if (from.enclosed) env |= VoiceProtocol.ENV_SPEAKER_ENCLOSED;

                body[offset] = (byte) (speaker.id >>> 8);
                body[offset + 1] = (byte) speaker.id;
                body[offset + 2] = (byte) env;
                body[offset + 3] = VoiceProtocol.toAmount(from.reverb);
                body[offset + 4] = VoiceProtocol.toAmount(positions.occlusion(speaker.playerName, listener.playerName));
                offset += 5;
                count++;
            }

            body[2] = (byte) count;
            listener.connection.send(offset == body.length ? body : Arrays.copyOf(body, offset));
        }
    }

    private void relayAudio(VoiceSession speaker, byte[] payload) {
        if (payload.length < 6) return;
        if (speaker.micMuted) return;
        if (!speaker.allowPacket(config.maxPacketsPerSecond)) return;

        int seq = ((payload[1] & 0xFF) << 8) | (payload[2] & 0xFF);
        int codec = payload[3] & 0xFF;
        int length = ((payload[4] & 0xFF) << 8) | (payload[5] & 0xFF);
        if (length <= 0 || length > config.maxAudioBytes || payload.length < 6 + length) return;
        if (codec != VoiceProtocol.CODEC_OPUS && codec != VoiceProtocol.CODEC_ADPCM) return;

        PlayerSnapshot from = positions.get(speaker.playerName);
        if (from == null) return;
        if (from.spectator && !config.spectatorsCanTalk) return;
        if (from.dead && !config.deadCanTalk) return;

        speaker.lastAudioAt = System.currentTimeMillis();

        boolean whisper = from.sneaking;
        double range = whisper ? config.whisperDistance : config.maxDistance;
        double rangeSquared = range * range;

        for (VoiceSession listener : byConnection.values()) {
            if (listener == speaker || listener.deafened) continue;
            PlayerSnapshot to = positions.get(listener.playerName);
            if (to == null) continue;
            if (!config.crossWorld && !from.worldId.equals(to.worldId)) continue;

            double dx = from.x - to.x;
            double dy = from.y - to.y;
            double dz = from.z - to.z;
            double distanceSquared = dx * dx + dy * dy + dz * dz;
            if (distanceSquared > rangeSquared) continue;

            double distance = Math.sqrt(distanceSquared);
            double yaw = Math.toRadians(to.yaw);
            double sin = Math.sin(yaw);
            double cos = Math.cos(yaw);
            // Minecraft: yaw 0 faces +Z. forward = (-sin, 0, cos); right = forward x up = (-cos, 0, -sin).
            double right = (dx * -cos) + (dz * -sin);
            double forward = (dx * -sin) + (dz * cos);

            byte[] frame = new byte[VoiceProtocol.AUDIO_HEADER + length];
            frame[0] = (byte) VoiceProtocol.S_AUDIO;
            putShort(frame, 1, speaker.id);
            putShort(frame, 3, seq);
            frame[5] = (byte) codec;
            frame[6] = (byte) (whisper ? VoiceProtocol.FLAG_WHISPER : 0);
            putShort(frame, 7, VoiceProtocol.toFixed(right));
            putShort(frame, 9, VoiceProtocol.toFixed(dy));
            putShort(frame, 11, VoiceProtocol.toFixed(forward));
            putShort(frame, 13, (int) Math.round(Math.min(distance, range) * VoiceProtocol.POSITION_SCALE));
            frame[15] = (byte) Math.round(Math.max(0.0d, Math.min(255.0d, range)));
            System.arraycopy(payload, 6, frame, VoiceProtocol.AUDIO_HEADER, length);
            listener.connection.send(frame);
        }
    }

    private void sendWelcome(VoiceSession session) {
        byte[] frame = new byte[10];
        frame[0] = (byte) VoiceProtocol.S_WELCOME;
        frame[1] = (byte) VoiceProtocol.VERSION;
        putShort(frame, 2, session.id);
        putShort(frame, 4, (int) Math.round(config.maxDistance));
        putShort(frame, 6, (int) Math.round(config.whisperDistance));
        frame[8] = (byte) 20; // frame duration in milliseconds
        frame[9] = 0;
        session.connection.send(frame);
    }

    void broadcastRoster() {
        long now = System.currentTimeMillis();
        Collection<VoiceSession> sessions = byConnection.values();
        for (VoiceSession listener : sessions) {
            PlayerSnapshot to = positions.get(listener.playerName);
            List<VoiceSession> visible = new ArrayList<VoiceSession>();
            for (VoiceSession peer : sessions) {
                if (peer == listener) continue;
                if (to == null) continue;
                PlayerSnapshot from = positions.get(peer.playerName);
                if (from == null) continue;
                if (!config.crossWorld && !from.worldId.equals(to.worldId)) continue;
                double dx = from.x - to.x, dy = from.y - to.y, dz = from.z - to.z;
                if (dx * dx + dy * dy + dz * dz > config.maxDistance * config.maxDistance) continue;
                visible.add(peer);
                if (visible.size() >= 32) break;
            }
            listener.connection.send(rosterFrame(visible, now));
        }
    }

    private byte[] rosterFrame(List<VoiceSession> peers, long now) {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream(64);
        DataOutputStream out = new DataOutputStream(buffer);
        try {
            out.writeByte(VoiceProtocol.S_ROSTER);
            out.writeByte(peers.size());
            for (VoiceSession peer : peers) {
                byte[] name = peer.playerName.getBytes(StandardCharsets.UTF_8);
                int flags = VoiceProtocol.PEER_IN_RANGE;
                if (peer.micMuted) flags |= VoiceProtocol.PEER_MUTED;
                if (peer.deafened) flags |= VoiceProtocol.PEER_DEAFENED;
                out.writeShort(peer.id);
                out.writeByte(flags);
                out.writeByte(Math.min(name.length, 64));
                out.write(name, 0, Math.min(name.length, 64));
            }
        } catch (IOException impossible) {
            return new byte[] { (byte) VoiceProtocol.S_ROSTER, 0 };
        }
        return buffer.toByteArray();
    }

    void sendNotice(WebSocketConnection connection, int severity, String text) {
        byte[] body = text.getBytes(StandardCharsets.UTF_8);
        int length = Math.min(body.length, 240);
        byte[] frame = new byte[4 + length];
        frame[0] = (byte) VoiceProtocol.S_NOTICE;
        frame[1] = (byte) severity;
        putShort(frame, 2, length);
        System.arraycopy(body, 0, frame, 4, length);
        connection.send(frame);
    }

    void disconnectPlayer(String playerName) {
        VoiceSession session = byPlayer.get(PositionIndex.key(playerName));
        if (session != null) session.connection.close();
    }

    void closeAll() {
        for (VoiceSession session : byConnection.values()) session.connection.close();
        byConnection.clear();
        byPlayer.clear();
    }

    List<VoiceSession> sessions() { return new ArrayList<VoiceSession>(byConnection.values()); }

    private static void putShort(byte[] target, int offset, int value) {
        target[offset] = (byte) (value >>> 8);
        target[offset + 1] = (byte) value;
    }

}
