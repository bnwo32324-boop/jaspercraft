package chat.jaspr.dungeon;

import io.netty.buffer.Unpooled;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;
import net.minecraft.server.v1_12_R1.PacketDataSerializer;
import net.minecraft.server.v1_12_R1.PacketPlayOutCustomPayload;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.craftbukkit.v1_12_R1.entity.CraftEntity;
import org.bukkit.craftbukkit.v1_12_R1.entity.CraftPlayer;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

/**
 * Bodies that are not vanilla size (owner 2026-10-05: scale mobs up "literally ... a larger size and ... a larger hitbox").
 * The server keeps the mob's real body: width, height, the bounding box rebuilt around its feet, a step height its legs
 * reach, and knockback resistance a towering body would have. The client draws the same factor and keeps the same hitbox
 * (client-mods/big-mobs-teavm.js): once a second every player in a run's world is sent the table of scaled mobs near it
 * ("id:hundredths,id:hundredths") on plugin channel jaspr:scale, and at once when a mob has just grown. Old clients ignore
 * the channel and draw vanilla bodies; the server's bodies are unchanged for them.
 */
public final class Bodies {
    public static final String CHANNEL = "jaspr:scale";
    /** One message holds at most this many entries (a world's active rooms: up to 60 mobs a room plus boss adds). */
    static final int MAX_ENTRIES = 128;
    final DungeonPlugin plugin;
    /** Scaled mobs: entity UUID to {scale, vanilla width, vanilla height}. */
    private final Map<UUID, double[]> grown = new HashMap<>();
    private final Set<String> dirty = new HashSet<>();
    private final Set<String> told = new HashSet<>();
    private long ticks;
    private boolean warned;
    public long sent;

    Bodies(DungeonPlugin plugin) { this.plugin = plugin; }

    /** The scale a mob was given (1 for a vanilla body). */
    public double scaleOf(LivingEntity e) { double[] g = grown.get(e.getUniqueId()); return g == null ? 1 : g[0]; }
    public int scaledMobs() { return grown.size(); }

    /**
     * Gives the spawned mob its body. A slime or magma cube already carries its size (set by the caller); every other
     * mob is resized here: width and height times the scale, the box rebuilt around the feet so it is centred where it
     * stands. The mob's bounding box is what players' arrows and the server's reach rule measure.
     */
    void grow(LivingEntity e, Scaling.Body body) {
        if (body.scale <= 1.0001) return;
        net.minecraft.server.v1_12_R1.Entity n = ((CraftEntity) e).getHandle();
        double w0 = n.width, h0 = n.length;
        grown.put(e.getUniqueId(), new double[]{body.scale, w0, h0});
        size(n, body.scale, w0, h0);
        // A body this tall steps over what its legs reach, and is not thrown about like a vanilla mob.
        n.P = (float) Math.max(n.P, .6 * body.scale);
        AttributeInstance resist = e.getAttribute(Attribute.GENERIC_KNOCKBACK_RESISTANCE);
        if (resist != null) resist.setBaseValue(Math.max(resist.getBaseValue(), Math.min(.8, .12 * (body.scale - 1))));
        dirty.add(e.getWorld().getName());
    }
    private static void size(net.minecraft.server.v1_12_R1.Entity n, double scale, double w0, double h0) {
        n.width = (float) (w0 * scale);
        n.length = (float) (h0 * scale);
        n.setPosition(n.locX, n.locY, n.locZ);
    }

    /** Once a second (and at once after a mob grew): keep every scaled body at its size and tell the world's players. */
    void tick() {
        ticks++;
        boolean second = ticks % 20 == 0;
        if (!second && dirty.isEmpty()) return;
        Map<String, List<String>> tables = new HashMap<>();
        Set<UUID> live = new HashSet<>();
        for (Encounters.Run run : plugin.encounters.active.values()) {
            if (run.world == null) continue;
            // Generation 7: a boss kit's summoned adds grow with the room too (Encounters.summon), so they are drawn grown as well.
            List<LivingEntity> bodies = new ArrayList<>(run.mobs.values());bodies.addAll(run.adds);
            for (LivingEntity e : bodies) {
                if (e == null || !e.isValid() || e.isDead()) continue;
                double[] g = grown.get(e.getUniqueId());
                if (g == null) continue;
                live.add(e.getUniqueId());
                // Something may have reset the body (a baby toggle, a plugin): put it back.
                if (second) {
                    net.minecraft.server.v1_12_R1.Entity n = ((CraftEntity) e).getHandle();
                    if (Math.abs(n.width - g[1] * g[0]) > .01 || Math.abs(n.length - g[2] * g[0]) > .01) size(n, g[0], g[1], g[2]);
                }
                List<String> list = tables.computeIfAbsent(run.world.getName(), k -> new ArrayList<>());
                if (list.size() < MAX_ENTRIES) list.add(e.getEntityId() + ":" + Math.round(g[0] * 100));
            }
        }
        if (second) grown.keySet().removeIf(id -> !live.contains(id));
        for (World world : plugin.dungeonWorlds()) {
            String name = world.getName();
            if (!second && !dirty.contains(name)) continue;
            List<String> list = tables.get(name);
            String text = list == null ? "" : String.join(",", list);
            // Nothing to say and nothing said last time: stay quiet. An emptied table is sent once, so clients clear theirs.
            if (text.isEmpty()) { if (!told.remove(name)) continue; }
            else told.add(name);
            for (Player p : world.getPlayers()) send(p, text);
        }
        dirty.clear();
    }

    private void send(Player p, String text) {
        try {
            net.minecraft.server.v1_12_R1.PlayerConnection connection = ((CraftPlayer) p).getHandle().playerConnection;
            if (connection == null) return;
            connection.sendPacket(new PacketPlayOutCustomPayload(CHANNEL, new PacketDataSerializer(Unpooled.wrappedBuffer(encode(text)))));
            sent++;
        } catch (RuntimeException ex) {
            if (!warned) { warned = true; plugin.getLogger().warning("DUNGEON_SCALE_SEND_FAILED " + ex.getClass().getSimpleName()); }
        }
    }
    /** A Minecraft PacketBuffer string: a VarInt byte length, then UTF-8. */
    static byte[] encode(String s) {
        byte[] text = s.getBytes(StandardCharsets.UTF_8);
        ByteArrayOutputStream out = new ByteArrayOutputStream(text.length + 3);
        int n = text.length;
        do { int b = n & 127; n >>>= 7; out.write(n == 0 ? b : b | 128); } while (n != 0);
        out.write(text, 0, text.length);
        return out.toByteArray();
    }
}
