package chat.jaspr.nether;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LargeFireball;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

/**
 * The Ghast Queen (NetherEx EntityGhastQueen + Urn of Sorrow). A Potion of Sorrow poured into a shrine's urn (a
 * registered cauldron) summons her 7 blocks above it after 6.8 s. 140 HP (+25%), 24-damage power-3 fireballs, every
 * third shot a three-fireball barrage, four Ghastling waves at the first hit, 75 %, 50 % and 25 % with a 10 s pause in
 * her fire after each, purple boss bar, tagged "jaspr_boss" (JasprGear trinket). Drops Ghast Queen Tears.
 */
final class Boss implements Listener {
    static final class Queen {
        final LivingEntity e; final BossBar bar; int stage; long cooldownUntil; int shots; int[] urn;
        final java.util.Set<UUID> fought = new java.util.HashSet<>();   // players who hurt her (they beat the Nether with her)
        Queen(LivingEntity e, BossBar bar) { this.e = e; this.bar = bar; }
    }

    private final NetherPlugin plugin;
    private final Map<UUID, Queen> queens = new HashMap<>();
    private final Map<String, Long> summoning = new HashMap<>();
    private long now;
    private boolean barrage;
    int summoned, defeated, waves, barrages;

    Boss(NetherPlugin plugin) { this.plugin = plugin; }

    String describe() { return "queens=" + queens.size() + " summoned=" + summoned + " defeated=" + defeated + " waves=" + waves; }

    private static String key(int x, int y, int z) { return x + "," + y + "," + z; }

    private boolean isUrn(Block b) {
        return plugin.registry != null && b.getType() == Material.CAULDRON && plugin.registry.at(b.getX(), b.getY(), b.getZ(), "urn") != null;
    }

    private boolean urnBusy(Block b) {
        String k = key(b.getX(), b.getY(), b.getZ());
        if (summoning.containsKey(k)) return true;
        for (Queen q : queens.values()) if (q.urn != null && key(q.urn[0], q.urn[1], q.urn[2]).equals(k)) return true;
        return false;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onUrn(PlayerInteractEvent e) {
        if (e.getAction() != Action.RIGHT_CLICK_BLOCK || e.getClickedBlock() == null || !isUrn(e.getClickedBlock())) return;
        ItemStack hand = e.getItem();
        if (!Items.is(hand, "potion_sorrow") || hand.getType() != Material.POTION) {
            if (e.getPlayer().isSneaking() || hand == null) return;
            Effects.bar(e.getPlayer(), ChatColor.DARK_PURPLE + "The Urn of Sorrow wants a Potion of Sorrow");
            e.setCancelled(true);
            return;
        }
        e.setCancelled(true);
        Block b = e.getClickedBlock();
        if (urnBusy(b)) { Effects.bar(e.getPlayer(), ChatColor.DARK_PURPLE + "The urn is already weeping"); return; }
        int lords = Lords.conquered(e.getPlayer()).size();
        if (lords < Lords.NEEDED && (plugin.guide == null || !plugin.guide.beaten(e.getPlayer()))) {
            // the Queen answers only one who has conquered three Nether Lords (the checklist and compass show the way)
            Effects.bar(e.getPlayer(), ChatColor.DARK_PURPLE + "The Urn of Sorrow stays silent: conquer " + (Lords.NEEDED - lords) + " more Nether Lord"
                + (Lords.NEEDED - lords == 1 ? "" : "s") + " (" + lords + "/" + Lords.NEEDED + ")");
            plugin.getLogger().info("NETHER_URN_REFUSED player=" + e.getPlayer().getUniqueId() + " lords=" + lords);
            return;
        }
        if (e.getPlayer().getGameMode() != org.bukkit.GameMode.CREATIVE) {
            hand.setAmount(hand.getAmount() - 1);
            e.getPlayer().getInventory().setItemInMainHand(hand.getAmount() <= 0 ? null : hand);
        }
        startSummon(b);
    }

    void startSummon(Block b) {
        b.setData((byte) 3);
        summoning.put(key(b.getX(), b.getY(), b.getZ()), now);
        b.getWorld().playSound(b.getLocation(), Sound.ENTITY_GHAST_SCREAM, 1.5f, 0.5f);
        plugin.getLogger().info("NETHER_QUEEN_SUMMONING urn=" + key(b.getX(), b.getY(), b.getZ()));
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent e) {
        if (isUrn(e.getBlock()) && urnBusy(e.getBlock())) e.setCancelled(true);
    }

    LivingEntity summon(Location at, int[] urn) {
        LivingEntity q = plugin.mobs.spawn("ghast_queen", at, false);
        if (q == null) return null;
        q.setRemoveWhenFarAway(false);
        q.addScoreboardTag("jaspr_boss");
        q.setCustomNameVisible(true);
        q.setCustomName(ChatColor.DARK_PURPLE + "Ghast Queen");
        if (urn != null) q.addScoreboardTag("jn_urn_" + urn[0] + "_" + urn[1] + "_" + urn[2]);
        Queen queen = register(q);
        queen.urn = urn;
        summoned++;
        at.getWorld().playSound(at, Sound.ENTITY_GENERIC_EXPLODE, 4f, 0.7f);
        plugin.getLogger().info("NETHER_QUEEN_SUMMONED at=" + at.getBlockX() + "," + at.getBlockY() + "," + at.getBlockZ() + " hp=" + (int) q.getMaxHealth());
        return q;
    }

    private Queen register(LivingEntity q) {
        BossBar bar = Bukkit.createBossBar("Ghast Queen", BarColor.PURPLE, BarStyle.SEGMENTED_10);
        Queen queen = new Queen(q, bar);
        for (String tag : q.getScoreboardTags()) {
            if (tag.startsWith("jn_urn_")) { String[] p = tag.substring(7).split("_"); queen.urn = new int[]{Integer.parseInt(p[0]), Integer.parseInt(p[1]), Integer.parseInt(p[2])}; }
            if (tag.startsWith("jn_qstage_")) queen.stage = Integer.parseInt(tag.substring(10));
        }
        queens.put(q.getUniqueId(), queen);
        return queen;
    }

    void adopt(LivingEntity q) { if (!queens.containsKey(q.getUniqueId())) register(q); }

    /** A living Ghast Queen within r blocks, or null. */
    LivingEntity queenNear(Location l, double r) {
        for (Queen q : queens.values())
            if (q.e.isValid() && q.e.getWorld() == l.getWorld() && q.e.getLocation().distanceSquared(l) < r * r) return q.e;
        return null;
    }

    /** Whether an urn within r blocks is weeping (a Queen is on her way). */
    boolean summoningNear(Location l, double r) {
        for (String k : summoning.keySet()) {
            String[] p = k.split(",");
            double dx = Integer.parseInt(p[0]) - l.getX(), dz = Integer.parseInt(p[2]) - l.getZ();
            if (dx * dx + dz * dz < r * r) return true;
        }
        return false;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void hurt(org.bukkit.event.entity.EntityDamageByEntityEvent e) {
        Queen q = queens.get(e.getEntity().getUniqueId());
        if (q == null) return;
        Entity d = e.getDamager();
        if (d instanceof org.bukkit.entity.Projectile && ((org.bukkit.entity.Projectile) d).getShooter() instanceof Entity) d = (Entity) ((org.bukkit.entity.Projectile) d).getShooter();
        if (d instanceof Player) q.fought.add(d.getUniqueId());
    }

    void tick(long ticks) {
        now = ticks;
        if (!summoning.isEmpty()) {
            Iterator<Map.Entry<String, Long>> it = summoning.entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry<String, Long> s = it.next();
                if (now - s.getValue() < 136) {
                    if ((now - s.getValue()) % 20 == 0 && plugin.nether != null) {
                        String[] p = s.getKey().split(",");
                        Location l = new Location(plugin.nether, Integer.parseInt(p[0]) + 0.5, Integer.parseInt(p[1]) + 1, Integer.parseInt(p[2]) + 0.5);
                        l.getWorld().spawnParticle(org.bukkit.Particle.SPELL_WITCH, l, 20, 0.4, 0.6, 0.4, 0.05);
                    }
                    continue;
                }
                it.remove();
                if (plugin.nether == null) continue;
                String[] p = s.getKey().split(",");
                int[] urn = {Integer.parseInt(p[0]), Integer.parseInt(p[1]), Integer.parseInt(p[2])};
                Block b = plugin.nether.getBlockAt(urn[0], urn[1], urn[2]);
                if (b.getType() == Material.CAULDRON) b.setData((byte) 0);
                summon(new Location(plugin.nether, urn[0] + 0.5, urn[1] + 7, urn[2] + 0.5), urn);
            }
        }
        if (queens.isEmpty() || (ticks % 5) != 0) return;
        Iterator<Queen> it = queens.values().iterator();
        while (it.hasNext()) {
            Queen q = it.next();
            if (!q.e.isValid() || q.e.isDead()) {
                q.bar.removeAll();
                if (q.e.isDead()) finish(q);
                it.remove();
                continue;
            }
            double max = q.e.getMaxHealth(), hp = q.e.getHealth();
            q.bar.setProgress(Math.max(0, Math.min(1, hp / max)));
            for (Player p : q.e.getWorld().getPlayers()) {
                boolean near = p.getLocation().distanceSquared(q.e.getLocation()) < 96 * 96;
                if (near && !q.bar.getPlayers().contains(p)) q.bar.addPlayer(p);
                else if (!near && q.bar.getPlayers().contains(p)) q.bar.removePlayer(p);
            }
            if (now >= q.cooldownUntil && q.stage < 4 && hp < max - q.stage * max / 4.0) wave(q);
        }
    }

    private void wave(Queen q) {
        Location l = q.e.getLocation().add(0, -1, 0);
        int spawned = 0;
        for (int i = 0; i < 4; i++) {
            Location at = l.clone().add((i - 1.5) * 3, 0, (i % 2) * 3 - 1.5);
            if (plugin.mobs.spawn("ghastling", at, false) != null) spawned++;
        }
        q.e.removeScoreboardTag("jn_qstage_" + q.stage);
        q.stage++;
        q.e.addScoreboardTag("jn_qstage_" + q.stage);
        q.cooldownUntil = now + 200;
        waves++;
        plugin.mobs.ability("queen_ghastling_wave");
        q.e.getWorld().playSound(l, Sound.ENTITY_GHAST_WARN, 3f, 0.6f);
        plugin.getLogger().info("NETHER_QUEEN_WAVE stage=" + q.stage + " ghastlings=" + spawned + " hp=" + (int) q.e.getHealth());
    }

    /** Called for each queen fireball; paused after a wave, and every third shot becomes a barrage. */
    void onShot(Mobs.T t, LargeFireball f) {
        Queen q = queens.get(t.e.getUniqueId());
        if (q == null) return;
        if (now < q.cooldownUntil) { f.remove(); return; }
        if (barrage) return;
        q.shots++;
        if (q.shots % 3 != 0) return;
        barrage = true;
        try {
            for (int i = -1; i <= 1; i += 2) {
                Vector dir = Mobs.rotY(f.getDirection(), 0.22 * i);
                LargeFireball extra = t.e.launchProjectile(LargeFireball.class, dir);
                extra.setDirection(dir);
                extra.setYield(3f);
                extra.setMetadata("jn_queen", new org.bukkit.metadata.FixedMetadataValue(plugin, Boolean.TRUE));
            }
        } finally { barrage = false; }
        barrages++;
        plugin.mobs.ability("queen_barrage");
    }

    void loot(Mobs.T t, List<ItemStack> drops, int looting) {
        drops.add(Items.create("ghast_queen_tear", 1));
        plugin.mobs.ghastLoot(drops, looting);
        plugin.mobs.ghastLoot(drops, looting);
        drops.add(Items.create("amethyst_crystal", 4 + (int) (Math.random() * 5)));
    }

    private void finish(Queen q) {
        defeated++;
        if (q.urn != null && plugin.nether != null) {
            Block b = plugin.nether.getBlockAt(q.urn[0], q.urn[1], q.urn[2]);
            if (b.getType() == Material.CAULDRON) b.setData((byte) 0);
        }
        Player killer = q.e.getKiller();
        plugin.getLogger().info("NETHER_QUEEN_DEFEATED waves=" + q.stage + " killer=" + (killer == null ? "-" : killer.getUniqueId().toString()));
        // Beating the Nether: everyone who hurt her, and everyone standing within 64 blocks when she fell.
        if (plugin.guide != null && plugin.nether != null) {
            Location at = q.e.getLocation();
            int credited = 0;
            for (Player p : plugin.nether.getPlayers()) {
                boolean near = p.getGameMode() != org.bukkit.GameMode.SPECTATOR && p.getWorld() == at.getWorld() && p.getLocation().distanceSquared(at) < 64 * 64;
                if (near || q.fought.contains(p.getUniqueId())) { plugin.guide.victory(p); credited++; }
            }
            plugin.getLogger().info("NETHER_QUEEN_CREDIT players=" + credited);
        }
    }

    void shutdown() { for (Queen q : queens.values()) q.bar.removeAll(); }

    int active() { return queens.size(); }
    Queen any() { return queens.isEmpty() ? null : queens.values().iterator().next(); }
    boolean hasQueen(Entity e) { return queens.containsKey(e.getUniqueId()); }
}
