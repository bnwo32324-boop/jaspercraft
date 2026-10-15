package chat.jaspr.lairs;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.Chest;
import org.bukkit.block.DoubleChest;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.plugin.Plugin;

/**
 * The lairs at runtime. A player who comes near a built lair learns its name; one who walks into its arena wakes its boss
 * (the mod's own mutant through JasprMutants' API, with its own AI, attacks and boss bar, given the lair's name, more
 * health, and the "jaspr_boss" tag so JasprGear drops a trinket and the bounty board counts it) and its garrison. The boss
 * is held to its arena, goes back to sleep after a minute alone, and once killed rests for a while. Its first death opens
 * the vault for good; until then the vault room cannot be broken, blown up or opened.
 */
final class Bosses implements Listener {
    private final LairsPlugin plugin;
    private final Map<String, List<Ledger.Lair>> byCell = new HashMap<>();
    private final Map<Long, List<Ledger.Lair>> byVaultChunk = new HashMap<>();
    private Method spawn;
    private boolean looked;
    final double healthMultiplier;
    final long restMs;
    int wakes, deaths, resets, fallbacks, discoveries;

    Bosses(LairsPlugin plugin) {
        this.plugin = plugin;
        this.healthMultiplier = Math.max(1.0, plugin.getConfig().getDouble("boss-health-multiplier", 2.0));
        this.restMs = Math.max(1, plugin.getConfig().getLong("boss-rest-minutes", 30)) * 60_000L;
    }

    void index(Ledger.Lair l) {
        if (!l.built()) return;
        byCell.computeIfAbsent(l.ci + "_" + l.cj, k -> new ArrayList<>()).add(l);
        if (l.vault != null && !l.vaultOpen)
            for (int cx = l.vault[0] >> 4; cx <= l.vault[3] >> 4; cx++) for (int cz = l.vault[2] >> 4; cz <= l.vault[5] >> 4; cz++)
                byVaultChunk.computeIfAbsent(((long) cx << 32) ^ (cz & 0xffffffffL), k -> new ArrayList<>()).add(l);
    }

    private List<Ledger.Lair> near(Location at) {
        List<Ledger.Lair> out = new ArrayList<>();
        int i = Math.floorDiv(at.getBlockX(), Planner.CELL), j = Math.floorDiv(at.getBlockZ(), Planner.CELL);
        for (int di = -1; di <= 1; di++) for (int dj = -1; dj <= 1; dj++) {
            List<Ledger.Lair> l = byCell.get((i + di) + "_" + (j + dj));
            if (l != null) out.addAll(l);
        }
        return out;
    }

    private Ledger.Lair sealedVaultAt(Block b) {
        List<Ledger.Lair> l = byVaultChunk.get(((long) (b.getX() >> 4) << 32) ^ ((b.getZ() >> 4) & 0xffffffffL));
        if (l == null) return null;
        for (Ledger.Lair x : l) if (!x.vaultOpen && x.inVault(b.getX(), b.getY(), b.getZ())) return x;
        return null;
    }

    // ---------------------------------------------------------------- the loop (every 10 ticks)

    void tick(World world) {
        long now = System.currentTimeMillis();
        for (Player p : world.getPlayers()) {
            if (p.isDead()) continue;
            Location at = p.getLocation();
            boolean fighter = p.getGameMode() == GameMode.SURVIVAL || p.getGameMode() == GameMode.ADVENTURE;
            for (Ledger.Lair l : near(at)) {
                double dx = at.getX() - l.centerX(), dz = at.getZ() - l.centerZ();
                double reach = Math.max(l.w, l.d) / 2.0 + 14;
                if (dx * dx + dz * dz < reach * reach && p.getGameMode() != GameMode.SPECTATOR && l.seen.add(p.getUniqueId().toString())) {
                    if (l.seen.size() > 400) { Iterator<String> it = l.seen.iterator(); it.next(); it.remove(); }
                    plugin.ledger().touch();
                    discoveries++;
                    p.sendTitle(ChatColor.DARK_RED + l.design.title, ChatColor.GRAY + "Lair of the " + l.design.mutantName(), 10, 70, 20);
                    p.playSound(at, Sound.AMBIENT_CAVE, 1f, 0.6f);
                }
                if (!fighter || l.bossId != null || now < l.restUntil || l.boss == null) continue;
                double bx = at.getX() - (l.boss[0] + 0.5), by = at.getY() - l.boss[1], bz = at.getZ() - (l.boss[2] + 0.5);
                if (bx * bx + bz * bz <= l.wake * l.wake && Math.abs(by) <= 10) wake(world, l, p);
            }
        }
        // the awake bosses: held to their arena, back to sleep after a minute alone
        for (List<Ledger.Lair> cell : byCell.values()) for (Ledger.Lair l : cell) {
            if (l.bossId == null) continue;
            Entity e = Bukkit.getEntity(l.bossId);
            Location home = new Location(world, l.boss[0] + 0.5, l.boss[1], l.boss[2] + 0.5);
            if (e == null || e.isDead()) {
                if (!world.isChunkLoaded(l.boss[0] >> 4, l.boss[2] >> 4)) continue;     // asleep with its chunk; found again on load
                l.bossId = null; l.minions.clear();
                continue;
            }
            if (e.getLocation().distanceSquared(home) > 38 * 38) e.teleport(home);
            boolean company = false;
            for (Player p : world.getPlayers())
                if ((p.getGameMode() == GameMode.SURVIVAL || p.getGameMode() == GameMode.ADVENTURE) && !p.isDead() && p.getLocation().distanceSquared(home) < 72 * 72) { company = true; break; }
            if (company) l.lonelySince = 0;
            else if (l.lonelySince == 0) l.lonelySince = now;
            else if (now - l.lonelySince > 60_000L) sleep(l, e);
        }
    }

    private void wake(World world, Ledger.Lair l, Player who) {
        Location at = new Location(world, l.boss[0] + 0.5, l.boss[1], l.boss[2] + 0.5);
        // a boss already standing here (saved with its chunk over a restart) is this lair's, never a second one
        for (Entity e : world.getNearbyEntities(at, 48, 32, 48))
            if (!e.isDead() && e.getScoreboardTags().contains("jaspr_lair:" + l.key) && e.getScoreboardTags().contains("jaspr_lair_boss")) { l.bossId = e.getUniqueId(); return; }
        LivingEntity boss = spawnMutant(l.design.mutant, at);
        boolean fallback = false;
        if (boss == null) { boss = fallback(l.design.mutant, at); fallback = true; fallbacks++; }
        if (boss == null) { l.restUntil = System.currentTimeMillis() + 60_000L; plugin.getLogger().warning("LAIR_BOSS_UNAVAILABLE key=" + l.key); return; }
        boss.setCustomName(ChatColor.DARK_RED + l.design.boss);
        boss.setRemoveWhenFarAway(false);
        AttributeInstance hp = boss.getAttribute(Attribute.GENERIC_MAX_HEALTH);
        if (hp != null) {
            double base = fallback ? 150.0 : hp.getBaseValue();
            hp.setBaseValue(Math.min(1024.0, base * healthMultiplier));
            boss.setHealth(hp.getValue());
        }
        boss.addScoreboardTag("jaspr_boss");
        boss.addScoreboardTag("jaspr_lair_boss");
        boss.addScoreboardTag("jaspr_lair:" + l.key);
        l.bossId = boss.getUniqueId();
        l.lonelySince = 0;
        wakes++;
        for (Ledger.Garrison g : l.garrisons) for (String kind : g.pack.split("\\+")) {
            EntityType t = EntityType.fromName(kind);
            if (t == null || !t.isAlive()) continue;
            Location spot = open(world, g.x, g.y, g.z);
            if (spot == null) continue;
            Entity m = world.spawnEntity(spot, t);
            if (m instanceof LivingEntity) { m.addScoreboardTag("jaspr_lair_minion"); m.addScoreboardTag("jaspr_lair:" + l.key); ((LivingEntity) m).setRemoveWhenFarAway(true); l.minions.add(m.getUniqueId()); }
        }
        for (Player p : world.getPlayers()) if (p.getLocation().distanceSquared(at) < 64 * 64) {
            p.sendTitle("", ChatColor.RED + l.design.boss + " stirs", 5, 50, 15);
            p.playSound(p.getLocation(), Sound.ENTITY_ENDERDRAGON_GROWL, 0.6f, 0.7f);
        }
        plugin.getLogger().info("LAIR_BOSS_WAKE key=" + l.key + " design=" + l.design.id + " mutant=" + l.design.mutant + " fallback=" + fallback
            + " health=" + Math.round(boss.getHealth()) + " minions=" + l.minions.size());
    }

    /** The first standing room (two air blocks over something solid) from y upward, within four blocks. */
    private static Location open(World w, int x, int y, int z) {
        for (int k = 0; k <= 4; k++) {
            Block feet = w.getBlockAt(x, y + k, z);
            if (!feet.getType().isSolid() && !feet.getRelative(0, 1, 0).getType().isSolid() && !feet.isLiquid()) return new Location(w, x + 0.5, y + k, z + 0.5);
        }
        return null;
    }

    private void sleep(Ledger.Lair l, Entity boss) {
        boss.remove();
        for (UUID id : l.minions) { Entity m = Bukkit.getEntity(id); if (m != null && !m.isDead()) m.remove(); }
        l.minions.clear();
        l.bossId = null;
        l.lonelySince = 0;
        resets++;
        plugin.getLogger().info("LAIR_BOSS_SLEEPS key=" + l.key + " reason=alone");
    }

    private LivingEntity spawnMutant(String kind, Location at) {
        Plugin p = Bukkit.getPluginManager().getPlugin("JasprMutants");
        if (p == null || !p.isEnabled()) return null;
        if (!looked || spawn == null) {
            looked = true;
            try { spawn = Class.forName("chat.jaspr.mutants.MutantsApi", true, p.getClass().getClassLoader()).getMethod("spawn", String.class, Location.class); }
            catch (ReflectiveOperationException e) { spawn = null; plugin.getLogger().warning("LAIR_MUTANTS_API_MISSING " + e.getClass().getSimpleName()); }
        }
        if (spawn == null) return null;
        try {
            Object e = spawn.invoke(null, kind, at);
            return e instanceof LivingEntity ? (LivingEntity) e : null;
        } catch (ReflectiveOperationException | RuntimeException e) {
            plugin.getLogger().warning("LAIR_MUTANT_SPAWN_FAILED kind=" + kind + " " + e.getClass().getSimpleName());
            return null;
        }
    }

    /** Without JasprMutants a lair keeps a boss: the vanilla kin of the mutant, far stronger. */
    private static LivingEntity fallback(String kind, Location at) {
        EntityType t = kind.contains("zombie") ? EntityType.ZOMBIE : kind.contains("skeleton") ? EntityType.SKELETON : kind.contains("creeper") ? EntityType.CREEPER : EntityType.ENDERMAN;
        Entity e = at.getWorld().spawnEntity(at, t);
        return e instanceof LivingEntity ? (LivingEntity) e : null;
    }

    /** Bosses saved with their chunks come back with them: re-adopt them by their tags. */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onChunkLoad(ChunkLoadEvent e) {
        for (Entity x : e.getChunk().getEntities()) {
            if (!x.getScoreboardTags().contains("jaspr_lair_boss")) continue;
            for (String t : x.getScoreboardTags()) if (t.startsWith("jaspr_lair:")) {
                Ledger.Lair l = plugin.ledger().get(t.substring("jaspr_lair:".length()));
                if (l != null && l.bossId == null) l.bossId = x.getUniqueId();
            }
        }
    }

    // ---------------------------------------------------------------- deaths

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(EntityDeathEvent e) {
        LivingEntity dead = e.getEntity();
        if (!dead.getScoreboardTags().contains("jaspr_lair_boss")) return;
        String key = null;
        for (String t : dead.getScoreboardTags()) if (t.startsWith("jaspr_lair:")) key = t.substring("jaspr_lair:".length());
        Ledger.Lair l = key == null ? null : plugin.ledger().get(key);
        if (l == null) return;
        e.setDroppedExp(e.getDroppedExp() + 250);
        l.bossId = null;
        l.kills++;
        l.restUntil = System.currentTimeMillis() + restMs;
        deaths++;
        Player killer = dead.getKiller();
        boolean opened = false;
        if (!l.vaultOpen) { openVault(dead.getWorld(), l); opened = true; }
        plugin.ledger().touch();
        for (Player p : dead.getWorld().getPlayers()) if (p.getLocation().distanceSquared(dead.getLocation()) < 96 * 96) {
            p.sendTitle(ChatColor.GOLD + l.design.boss + " falls", opened ? ChatColor.YELLOW + "The vault of " + l.design.title + " is open" : "", 10, 70, 20);
            p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.8f, 1f);
        }
        plugin.getLogger().info("LAIR_BOSS_DEFEATED key=" + l.key + " design=" + l.design.id + " kills=" + l.kills + " vaultOpened=" + opened + " byPlayer=" + (killer != null));
    }

    private void openVault(World w, Ledger.Lair l) {
        l.vaultOpen = true;
        for (int[] s : l.seals) {
            Block b = w.getBlockAt(s[0], s[1], s[2]);
            if (b.getType() == Material.IRON_FENCE || b.getType() == Material.IRON_TRAPDOOR) {
                b.setType(Material.AIR, false);
                w.spawnParticle(Particle.CLOUD, b.getLocation().add(0.5, 0.5, 0.5), 4, 0.2, 0.2, 0.2, 0.01);
            }
        }
        if (!l.seals.isEmpty()) { int[] s = l.seals.get(0); w.playSound(new Location(w, s[0], s[1], s[2]), Sound.BLOCK_IRON_DOOR_OPEN, 1f, 0.6f); }
        for (List<Ledger.Lair> list : byVaultChunk.values()) list.remove(l);
        plugin.getLogger().info("LAIR_VAULT_OPENED key=" + l.key + " seals=" + l.seals.size());
    }

    // ---------------------------------------------------------------- the sealed vault

    private static void tell(Player p, Ledger.Lair l) {
        p.sendMessage(ChatColor.DARK_RED + "Sealed. " + ChatColor.GRAY + "Defeat " + l.design.boss + " to open the vault of " + l.design.title + ".");
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent e) {
        Ledger.Lair l = sealedVaultAt(e.getBlock());
        if (l == null || e.getPlayer().getGameMode() == GameMode.CREATIVE && e.getPlayer().isOp()) return;
        e.setCancelled(true);
        tell(e.getPlayer(), l);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onExplode(EntityExplodeEvent e) { e.blockList().removeIf(b -> sealedVaultAt(b) != null); }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent e) { e.blockList().removeIf(b -> sealedVaultAt(b) != null); }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onChange(EntityChangeBlockEvent e) { if (sealedVaultAt(e.getBlock()) != null) e.setCancelled(true); }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onOpen(InventoryOpenEvent e) {
        InventoryHolder h = e.getInventory().getHolder();
        Block b = null;
        if (h instanceof Chest) b = ((Chest) h).getBlock();
        else if (h instanceof DoubleChest) { InventoryHolder left = ((DoubleChest) h).getLeftSide(); if (left instanceof BlockState) b = ((BlockState) left).getBlock(); }
        if (b == null) return;
        Ledger.Lair l = sealedVaultAt(b);
        if (l == null || !(e.getPlayer() instanceof Player)) return;
        Player p = (Player) e.getPlayer();
        if (p.getGameMode() == GameMode.CREATIVE && p.isOp()) return;
        e.setCancelled(true);
        tell(p, l);
    }

    String status() { return "wakes=" + wakes + " deaths=" + deaths + " sleeps=" + resets + " fallbacks=" + fallbacks + " discoveries=" + discoveries; }
}
