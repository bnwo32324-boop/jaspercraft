package chat.jaspr.backrooms;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.FallingBlock;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockFadeEvent;
import org.bukkit.event.block.BlockFormEvent;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.block.BlockIgniteEvent;
import org.bukkit.event.block.BlockMultiPlaceEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.BlockSpreadEvent;
import org.bukkit.event.block.LeavesDecayEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.hanging.HangingBreakEvent;
import org.bukkit.event.player.PlayerBedEnterEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.event.player.PlayerInteractEvent;

/**
 * The Backrooms cannot be broken (owner, 2026-09-30: "You can only break the blocks that you have placed down, or maybe a
 * few exceptions like chests, but the terrain itself ... you should not be able to break out of"). Every block a player
 * places is remembered (persisted, bounded); only those, chests and cobwebs can be broken. Explosions, fire, pistons,
 * falling blocks, mobs, water and buckets leave the terrain alone. An operator in creative mode can still edit.
 */
final class Protect implements Listener {
    static final int MAX_PLACED = 250_000;
    private static final EnumSet<Material> BREAKABLE = EnumSet.of(Material.CHEST, Material.TRAPPED_CHEST, Material.WEB);

    private final BackroomsPlugin plugin;
    private final File file;
    private final Set<Long> placed = new HashSet<>();
    private final Map<UUID, Long> lastNotice = new HashMap<>();
    private boolean dirty;
    long refusedBreaks, refusedPlaces, shieldedExplosions, blockedFlows;

    Protect(BackroomsPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "placed-blocks.bin");
        load();
    }

    static long key(int x, int y, int z) { return ((long) x & 0x3FFFFFFL) << 38 | ((long) z & 0x3FFFFFFL) << 12 | (y & 0xFFFL); }
    static long key(Block b) { return key(b.getX(), b.getY(), b.getZ()); }

    boolean placed(Block b) { return placed.contains(key(b)); }
    int count() { return placed.size(); }

    private boolean ours(World w) { return plugin.isBackrooms(w); }
    private static boolean editor(Player p) { return p != null && p.isOp() && p.getGameMode() == GameMode.CREATIVE; }

    /** Whether a player may break this block here: their own, a chest or a cobweb (or an editing operator). */
    boolean mayBreak(Player p, Block b) { return editor(p) || placed(b) || BREAKABLE.contains(b.getType()); }

    private void notice(Player p, String text) {
        long now = System.currentTimeMillis();
        Long last = lastNotice.get(p.getUniqueId());
        if (last != null && now - last < 2500) return;
        lastNotice.put(p.getUniqueId(), now);
        p.sendMessage(ChatColor.YELLOW + text);
    }

    // ---- players --------------------------------------------------------------------------------------------------------
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent e) {
        if (!ours(e.getBlock().getWorld())) return;
        Block b = e.getBlock();
        if (!mayBreak(e.getPlayer(), b)) {
            e.setCancelled(true);
            refusedBreaks++;
            notice(e.getPlayer(), "The Backrooms do not break. Only what you placed yourself comes away.");
            return;
        }
        forget(b);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent e) {
        if (!ours(e.getBlock().getWorld()) || e instanceof BlockMultiPlaceEvent) return;
        if (placed.size() >= MAX_PLACED && !editor(e.getPlayer())) {
            e.setCancelled(true);
            refusedPlaces++;
            notice(e.getPlayer(), "The Backrooms will not hold any more of your blocks.");
            return;
        }
        remember(e.getBlock());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMultiPlace(BlockMultiPlaceEvent e) {
        if (!ours(e.getBlock().getWorld())) return;
        for (org.bukkit.block.BlockState s : e.getReplacedBlockStates()) remember(s.getBlock());
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onBucketEmpty(PlayerBucketEmptyEvent e) {
        if (!ours(e.getBlockClicked().getWorld())) return;
        remember(e.getBlockClicked().getRelative(e.getBlockFace()));
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onBucketFill(PlayerBucketFillEvent e) {
        if (!ours(e.getBlockClicked().getWorld())) return;
        Block b = e.getBlockClicked().getRelative(e.getBlockFace());
        Block water = b.isLiquid() ? b : e.getBlockClicked();
        if (editor(e.getPlayer()) || placed(water)) { forget(water); return; }
        e.setCancelled(true);
        notice(e.getPlayer(), "This water belongs to the Backrooms.");
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onSleep(PlayerBedEnterEvent e) {
        if (!ours(e.getPlayer().getWorld())) return;
        e.setCancelled(true);
        e.getPlayer().sendMessage(ChatColor.YELLOW + "You cannot sleep in the Backrooms. Something is always awake.");
    }

    /** No trampling or tilling of the terrain (farmland, the city's grass). */
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onPhysical(PlayerInteractEvent e) {
        if (e.getClickedBlock() == null || !ours(e.getClickedBlock().getWorld())) return;
        if (e.getAction() == org.bukkit.event.block.Action.PHYSICAL && e.getClickedBlock().getType() == Material.SOIL) e.setCancelled(true);
        if (e.getAction() == org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK && e.getItem() != null && e.getItem().getType().name().endsWith("_HOE")
            && !placed(e.getClickedBlock()) && !editor(e.getPlayer())) e.setCancelled(true);
    }

    // ---- everything else ------------------------------------------------------------------------------------------------
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent e) {
        if (!ours(e.getLocation().getWorld())) return;
        shield(e.blockList());
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent e) {
        if (!ours(e.getBlock().getWorld())) return;
        shield(e.blockList());
    }

    /** Explosions only take blocks players placed. */
    private void shield(List<Block> blocks) {
        int before = blocks.size();
        blocks.removeIf(b -> !placed(b));
        for (Block b : blocks) forget(b);
        if (blocks.size() < before) shieldedExplosions++;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPistonExtend(BlockPistonExtendEvent e) {
        if (!ours(e.getBlock().getWorld())) return;
        if (!moveTracked(e.getBlocks(), e.getDirection())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPistonRetract(BlockPistonRetractEvent e) {
        if (!ours(e.getBlock().getWorld())) return;
        if (!moveTracked(e.getBlocks(), e.getDirection())) e.setCancelled(true);
    }

    /** A piston may move only placed blocks; their records move with them. */
    private boolean moveTracked(List<Block> blocks, org.bukkit.block.BlockFace dir) {
        for (Block b : blocks) if (!placed(b)) return false;
        List<Block> moved = new ArrayList<>(blocks);
        for (Block b : moved) forget(b);
        for (Block b : moved) remember(b.getRelative(dir));
        return true;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBurn(BlockBurnEvent e) { if (ours(e.getBlock().getWorld())) e.setCancelled(true); }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onIgnite(BlockIgniteEvent e) {
        if (!ours(e.getBlock().getWorld())) return;
        if (e.getCause() == BlockIgniteEvent.IgniteCause.FLINT_AND_STEEL && e.getPlayer() != null) return;   // a player's own fire, harmless (no fire spread here)
        e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onFade(BlockFadeEvent e) { if (ours(e.getBlock().getWorld()) && !placed(e.getBlock())) e.setCancelled(true); }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onForm(BlockFormEvent e) { if (ours(e.getBlock().getWorld()) && !(e instanceof BlockSpreadEvent)) e.setCancelled(true); }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onSpread(BlockSpreadEvent e) { if (ours(e.getBlock().getWorld())) e.setCancelled(true); }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDecay(LeavesDecayEvent e) { if (ours(e.getBlock().getWorld())) e.setCancelled(true); }

    /** Water and lava never wash anything away here (torches, signs, carpets, the terrain's decorations). */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onFlow(BlockFromToEvent e) {
        Block to = e.getToBlock();
        if (!ours(to.getWorld())) return;
        Material m = to.getType();
        if (m != Material.AIR && !to.isLiquid() && !placed(to)) { e.setCancelled(true); blockedFlows++; }
    }

    /** Mobs, withers, silverfish and falling blocks change nothing (a falling block players dropped lands normally). */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onChange(EntityChangeBlockEvent e) {
        if (!ours(e.getBlock().getWorld())) return;
        if (e.getEntity() instanceof FallingBlock) {
            if (e.getEntity().getScoreboardTags().contains(Bosses.CRATE_TAG)) { e.setCancelled(true); return; }
            if (e.getTo() == Material.AIR) { if (!placed(e.getBlock())) e.setCancelled(true); else forget(e.getBlock()); }
            else remember(e.getBlock());
            return;
        }
        if (e.getEntityType() == EntityType.PLAYER) return;
        e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onHangingBreak(HangingBreakEvent e) {
        if (!ours(e.getEntity().getWorld())) return;
        if (e.getCause() == HangingBreakEvent.RemoveCause.EXPLOSION || e.getCause() == HangingBreakEvent.RemoveCause.PHYSICS) e.setCancelled(true);
    }

    // ---- the record -----------------------------------------------------------------------------------------------------
    void remember(Block b) {
        if (placed.size() >= MAX_PLACED) return;
        if (placed.add(key(b))) dirty = true;
    }

    void forget(Block b) { if (placed.remove(key(b))) dirty = true; }

    private void load() {
        if (!file.isFile()) return;
        try (DataInputStream in = new DataInputStream(new java.io.BufferedInputStream(new FileInputStream(file)))) {
            int n = Math.min(in.readInt(), MAX_PLACED);
            for (int i = 0; i < n; i++) placed.add(in.readLong());
        } catch (IOException e) {
            plugin.getLogger().warning("BACKROOMS_PLACED_UNREADABLE " + e.getClass().getSimpleName() + " kept=" + placed.size());
        }
    }

    /** Saves when something changed (called every half minute and on shutdown); written aside, then moved in place. */
    void save() {
        if (!dirty) return;
        File tmp = new File(file.getPath() + ".tmp");
        try {
            plugin.getDataFolder().mkdirs();
            try (DataOutputStream out = new DataOutputStream(new java.io.BufferedOutputStream(new FileOutputStream(tmp)))) {
                out.writeInt(placed.size());
                for (long k : placed) out.writeLong(k);
            }
            java.nio.file.Files.move(tmp.toPath(), file.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            dirty = false;
        } catch (IOException e) {
            plugin.getLogger().warning("BACKROOMS_PLACED_UNSAVED " + e.getClass().getSimpleName());
        }
    }

    /** A new world starts with no placed blocks. */
    void reset() { placed.clear(); dirty = true; save(); }

    String describe() {
        return "placed=" + placed.size() + " refusedBreaks=" + refusedBreaks + " refusedPlaces=" + refusedPlaces + " shieldedExplosions=" + shieldedExplosions + " blockedFlows=" + blockedFlows;
    }
}
