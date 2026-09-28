package chat.jaspr.atlas;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Chunk;
import org.bukkit.ChunkSnapshot;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Biome;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.Sign;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.inventory.InventoryHolder;

/**
 * Liberation and victory, and the healing of the land. When an Ash-Crowned falls, their province is liberated: the
 * realm's liberation mask gains its bit, its Ward at Anthrakion goes dark, the Dominion's folk there are gone and the
 * Bound walk free. The land then heals chunk by chunk, as it is loaded: each chunk is drawn twice in memory, as it was
 * generated and as it is now, and every block still exactly as generated becomes its liberated form (ash to soil and
 * grass, fires out, pens open, banners changed). Anything a player built or changed is left alone, as are containers
 * that hold anything. The biomes change too, and the chunk is sent again so everyone sees it.
 */
final class Liberation implements Listener {
    private final AtlasPlugin plugin;
    private final ArrayDeque<Long> queue = new ArrayDeque<>();
    private final Set<Long> queued = new HashSet<>();
    long chunksHealed, blocksHealed, containersKept, healNanos;
    private long batchChunks, batchBlocks, batchStart;

    Liberation(AtlasPlugin plugin) { this.plugin = plugin; }

    // ------------------------------------------------------------------ liberation and victory

    void liberate(Realm.Province p, List<Player> by, Bosses.Boss boss) {
        State s = plugin.state();
        int before = s.liberated;
        s.liberated |= p.bit;
        plugin.saveStateSoon();
        World w = plugin.atlas();
        String names = names(by);
        Bukkit.broadcastMessage(ChatColor.GOLD + boss.title.split(",")[0] + " has fallen" + (names.isEmpty() ? "" : " to " + names) + ". " + ChatColor.YELLOW + cap(p.title) + " are free.");
        if (w != null) {
            for (Player pl : w.getPlayers()) {
                pl.sendTitle(ChatColor.GOLD + cap(p.title), ChatColor.YELLOW + "is free. The Ward of " + Anthrakion.wardName(p).charAt(0) + Anthrakion.wardName(p).substring(1).toLowerCase() + " goes dark.", 10, 80, 30);
                pl.playSound(pl.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.8f, 1f);
            }
            int cleared = plugin.dominion().clearProvince(w, p);
            plugin.getLogger().info("ATLAS_PROVINCE_CLEARED province=" + p.name() + " removed=" + cleared);
            healLoaded(w);
        }
        if (s.wardsDark() == 4) {
            Bukkit.broadcastMessage(ChatColor.GOLD + "All four Wards around Anthrakion are dark. " + ChatColor.YELLOW + "Its gate stands open. Archon Kleio will lend the Light of Theano.");
        }
        plugin.getLogger().info("ATLAS_LIBERATED province=" + p.name() + " mask=" + before + "->" + s.liberated + " by=" + names.replace(", ", ","));
    }

    void victory(List<Player> by) {
        State s = plugin.state();
        if (s.victory) return;
        s.victory = true;
        s.victoryAt = System.currentTimeMillis();
        s.liberated = Realm.ALL_LIBERATED;
        for (Player p : by) if (!s.rekindlers.contains(p.getName())) s.rekindlers.add(p.getName());
        plugin.captives().freeAll(s.rekindlers.isEmpty() ? "the Rekindlers" : s.rekindlers.get(0));
        plugin.saveStateSoon();
        String names = names(by);
        Bukkit.broadcastMessage(ChatColor.AQUA + "" + ChatColor.BOLD + "ATLAS IS FREE. " + ChatColor.RESET + ChatColor.GRAY + "The Pyrarch has fallen" + (names.isEmpty() ? "" : " to " + names)
            + ". The Cinder Heart is broken, the ash has stopped, and the Star is whole.");
        World w = plugin.atlas();
        if (w != null) {
            for (Player pl : w.getPlayers()) {
                pl.sendTitle(ChatColor.AQUA + "Atlas is free", ChatColor.GRAY + "The ash has stopped. The Star is whole.", 20, 120, 40);
                pl.playSound(pl.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 0.8f);
                pl.playSound(pl.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 0.8f);
            }
            for (Entity e : w.getEntities()) {
                if (Npcs.has(e, Npcs.CHOIR)) freeSinger(e);
                else if (Dominion.isDominion(e) && Npcs.tagValue(e, Bosses.TAG) == null && !Npcs.has(e, "atlas_remnant")) e.remove();
            }
            carveMonument(w);
            healLoaded(w);
        }
        plugin.getLogger().info("ATLAS_VICTORY rekindlers=" + String.join(",", s.rekindlers));
    }

    /** One of the Chained Choir, unchained. */
    static void freeSinger(Entity e) {
        e.removeScoreboardTag(Npcs.CHOIR);
        e.addScoreboardTag(Npcs.CITIZEN);
        e.addScoreboardTag("atlas_role:freed");
        String n = e.getCustomName() == null ? "Someone" : ChatColor.stripColor(e.getCustomName());
        int comma = n.indexOf(',');
        e.setCustomName(ChatColor.WHITE + (comma > 0 ? n.substring(0, comma) : n) + ChatColor.GRAY + ", who sang in the Choir");
    }

    /** The Rekindlers' names on the plinth at the Gate of Strangers. */
    @SuppressWarnings("deprecation")
    void carveMonument(World w) {
        Registry.Spot m = plugin.registry().spot("monument");
        if (m == null || !w.isChunkLoaded(m.x >> 4, m.z >> 4)) return;
        List<String> lines = new ArrayList<>();
        lines.add("THE");
        lines.add("REKINDLERS");
        lines.add("OF ATLAS");
        lines.add("~");
        for (String n : plugin.state().rekindlers) lines.add(n.length() > 15 ? n.substring(0, 15) : n);
        int signs = Math.min(5, (lines.size() + 3) / 4);
        for (int k = 0; k < signs; k++) {
            Block b = w.getBlockAt(m.x - 2 + k, m.y, m.z);
            if (!b.isEmpty() && b.getType() != Material.SIGN_POST) continue;
            b.setTypeIdAndData(63, (byte) 8, false);   // facing north, toward the court
            BlockState st = b.getState();
            if (!(st instanceof Sign)) continue;
            Sign sg = (Sign) st;
            for (int i = 0; i < 4; i++) { int at = k * 4 + i; sg.setLine(i, at < lines.size() ? lines.get(at) : ""); }
            sg.update(true, false);
        }
        plugin.getLogger().info("ATLAS_MONUMENT_CARVED names=" + plugin.state().rekindlers.size());
    }

    private static String names(List<Player> by) { List<String> n = new ArrayList<>(); for (Player p : by) n.add(p.getName()); return String.join(", ", n); }

    private static String cap(String t) { return t.isEmpty() ? t : Character.toUpperCase(t.charAt(0)) + t.substring(1); }

    // ------------------------------------------------------------------ healing

    void healLoaded(World w) { for (Chunk c : w.getLoadedChunks()) enqueue(c.getX(), c.getZ()); }

    private void enqueue(int cx, int cz) {
        long k = MaskStore.key(cx, cz);
        if (queued.add(k)) queue.add(k);
    }

    int pending() { return queue.size(); }

    @EventHandler
    public void loaded(ChunkLoadEvent e) {
        if (!plugin.isAtlas(e.getWorld()) || e.isNewChunk()) return;
        AtlasGenerator gen = plugin.generator();
        int cx = e.getChunk().getX(), cz = e.getChunk().getZ();
        if (gen.masks.known(cx, cz) && gen.masks.get(cx, cz, 0) != plugin.state().liberated) enqueue(cx, cz);
    }

    /** Every tick: heal queued chunks for up to 8 ms. */
    void tick() {
        if (queue.isEmpty()) {
            if (batchChunks > 0) {
                plugin.getLogger().info("ATLAS_HEALED chunks=" + batchChunks + " blocks=" + batchBlocks + " ms=" + (System.nanoTime() - batchStart) / 1_000_000L + " total=" + chunksHealed);
                batchChunks = 0; batchBlocks = 0;
            }
            return;
        }
        World w = plugin.atlas();
        if (w == null) { queue.clear(); queued.clear(); return; }
        long t0 = System.nanoTime();
        if (batchChunks == 0) batchStart = t0;
        while (!queue.isEmpty() && System.nanoTime() - t0 < 8_000_000L) {
            long k = queue.poll();
            queued.remove(k);
            int cx = (int) (k >> 32), cz = (int) k;
            if (!w.isChunkLoaded(cx, cz)) continue;   // healed when it is next loaded
            try { heal(w, w.getChunkAt(cx, cz)); }
            catch (RuntimeException ex) { plugin.getLogger().warning("ATLAS_HEAL_FAILED chunk=" + cx + "," + cz + " " + ex.getClass().getSimpleName() + ": " + String.valueOf(ex.getMessage()).replaceAll("[\\r\\n]", " ")); }
        }
        healNanos += System.nanoTime() - t0;
    }

    /** Heals one chunk from the mask it was drawn with to the realm's current mask. */
    @SuppressWarnings("deprecation")
    void heal(World w, Chunk chunk) {
        AtlasGenerator gen = plugin.generator();
        int cx = chunk.getX(), cz = chunk.getZ(), now = plugin.state().liberated;
        int was = gen.masks.get(cx, cz, now);
        if (was == now) return;
        Drawing before = new Drawing(), after = new Drawing();
        gen.fill(before, before, cx, cz, was);
        gen.fill(after, after, cx, cz, now);
        ChunkSnapshot snap = chunk.getChunkSnapshot(false, false, false);
        int changed = 0;
        for (int y = 0; y < 256; y++)
            for (int z = 0; z < 16; z++)
                for (int x = 0; x < 16; x++) {
                    int oldId = before.id(x, y, z), newId = after.id(x, y, z), oldData = before.meta(x, y, z), newData = after.meta(x, y, z);
                    if (oldId == newId && oldData == newData) continue;
                    if (snap.getBlockTypeId(x, y, z) != oldId || snap.getBlockData(x, y, z) != oldData) continue;   // changed since: a player's
                    Block b = chunk.getBlock(x, y, z);
                    BlockState st = b.getState();
                    if (st instanceof InventoryHolder && !empty((InventoryHolder) st)) { containersKept++; continue; }
                    b.setTypeIdAndData(newId, (byte) newData, false);
                    changed++;
                }
        for (int x = 0; x < 16; x++)
            for (int z = 0; z < 16; z++) {
                Biome nb = after.getBiome(x, z);
                if (nb != null && nb != before.getBiome(x, z)) w.setBiome(cx * 16 + x, cz * 16 + z, nb);
            }
        // What the new drawing adds (settlers, heliodromes, signs, banners, chests) is finished as population would.
        Map<String, Canvas.Tile> old = new HashMap<>();
        for (Canvas.Tile t : AtlasPopulator.tilesOf(gen, cx, cz, was)) old.put(t.x + "," + t.y + "," + t.z + ":" + t.kind + ":" + t.what, t);
        java.util.Random r = new java.util.Random(Hash.of(plugin.seed() ^ 0x4EA1L, cx, 0, cz));
        for (Canvas.Tile t : AtlasPopulator.tilesOf(gen, cx, cz, now)) {
            if (old.containsKey(t.x + "," + t.y + "," + t.z + ":" + t.kind + ":" + t.what)) continue;
            try { plugin.npcs().tile(w, chunk, t, r); } catch (RuntimeException ex) { plugin.getLogger().warning("ATLAS_HEAL_TILE_FAILED " + t.what); }
        }
        // The Dominion's garrison here is gone; the Bound are released by the Captives' pass.
        for (Entity e : chunk.getEntities()) {
            if (!Dominion.isDominion(e) || Npcs.tagValue(e, Bosses.TAG) != null || Npcs.has(e, "atlas_remnant")) continue;
            Realm.Zone z = Realm.zone(e.getLocation().getBlockX(), e.getLocation().getBlockZ());
            if (z.province != null && (now & z.province.bit) != 0) e.remove();
        }
        gen.masks.put(cx, cz, now);
        w.refreshChunk(cx, cz);
        chunksHealed++;
        blocksHealed += changed;
        batchChunks++;
        batchBlocks += changed;
    }

    private static boolean empty(InventoryHolder h) {
        for (org.bukkit.inventory.ItemStack i : h.getInventory().getContents()) if (i != null && i.getType() != Material.AIR) return false;
        return true;
    }

    /** Where the player stands, is the land around them healed yet (for status)? */
    String status() { return "healing: queued " + queue.size() + ", healed " + chunksHealed + " chunks / " + blocksHealed + " blocks, kept " + containersKept + " full containers"; }

    static Location centre(World w, int cx, int cz) { return new Location(w, cx * 16 + 8, 64, cz * 16 + 8); }
}
