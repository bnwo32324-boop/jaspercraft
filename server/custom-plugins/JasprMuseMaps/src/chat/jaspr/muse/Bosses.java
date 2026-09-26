package chat.jaspr.muse;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.block.Block;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

/**
 * The ten Muse+GLM_Maps bosses (catalogue "boss"), one per chosen design. A boss rises when a player reaches its
 * arena, fights with a boss bar and telegraphed moves (every move is announced and shown before it lands), grows
 * faster at 66% and 33% health, stays tethered to its arena, and resets to full health if everyone leaves. Its
 * site's vault chest stays sealed until it falls; the kill is remembered per site (the ledger) and pays out the
 * boss's signature weapon, Essences and So Many Enchantments books. Tagged jaspr_boss so JasprGear adds a trinket.
 */
final class Bosses implements Listener {
    static final String BOSS_TAG = "jaspr_muse_boss";
    private static final class Fight {
        final Ledger.Record record; final LivingEntity boss; final BossBar bar; final Location arena;
        int cooldown = 60, phase = 1, next; long lastSeen = System.currentTimeMillis(), born = System.currentTimeMillis();
        Fight(Ledger.Record r, LivingEntity b, BossBar bar, Location arena) { record = r; boss = b; this.bar = bar; this.arena = arena; }
    }
    private final MusePlugin plugin;
    private final Abilities abilities;
    private final Map<String, Fight> fights = new HashMap<>();
    private final Map<UUID, Fight> byEntity = new HashMap<>();
    private final Random random = new Random();
    private final Map<String, Long> backoff = new HashMap<>();

    Bosses(MusePlugin plugin, Abilities abilities) { this.plugin = plugin; this.abilities = abilities; }

    static Location arena(World w, Planner.Plan plan) {
        int[] a = plan.design.bossArena;
        if (a == null) return new Location(w, plan.x + plan.design.width() / 2.0, plan.y + plan.design.surfaceAnchor + 1, plan.z + plan.design.depth() / 2.0);
        return new Location(w, plan.x + a[0] + 0.5, plan.y + a[1], plan.z + a[2] + 0.5);
    }

    boolean active(String siteKey) { return fights.containsKey(siteKey); }

    /** Called by the encounter tick for a player inside a boss site. */
    void offer(Ledger.Record record, Player p) {
        Planner.Plan plan = record.plan;
        if (plan == null || plan.design.boss == null || record.bossDefeated) return;
        String key = plan.key();
        Fight f = fights.get(key);
        Location arena = arena(p.getWorld(), plan);
        if (f != null) { if (p.getLocation().distanceSquared(f.arena) < 48 * 48) f.lastSeen = System.currentTimeMillis(); return; }
        if (p.getLocation().distanceSquared(arena) > 14 * 14) return;
        if (p.getWorld().getDifficulty() == org.bukkit.Difficulty.PEACEFUL) return;
        Long wait = backoff.get(key);
        if (wait != null && System.currentTimeMillis() < wait) return;
        Location spawn = Abilities.floor(arena);
        if (spawn == null) spawn = arena;
        else spawn.add(0.5, 0, 0.5);
        Catalog.Boss def = plan.design.boss;
        EntityType type = EntityType.valueOf(def.type);
        LivingEntity boss = (LivingEntity) p.getWorld().spawnEntity(spawn, type);
        if (!boss.isValid()) {                       // another plugin refused the spawn: do not retry every second
            backoff.put(key, System.currentTimeMillis() + 60000);
            plugin.getLogger().warning("MUSE_BOSS_SPAWN_REFUSED boss=" + def.key + " site=" + key);
            return;
        }
        Mobs.tag(boss, key);
        boss.addScoreboardTag(BOSS_TAG);
        boss.addScoreboardTag("jaspr_boss");
        boss.setCustomName(ChatColor.DARK_RED + "" + ChatColor.BOLD + def.name);
        boss.setCustomNameVisible(true);
        set(boss, Attribute.GENERIC_MAX_HEALTH, Math.min(1024, def.health));
        boss.setHealth(Math.min(1024, def.health));
        set(boss, Attribute.GENERIC_ATTACK_DAMAGE, def.damage);
        set(boss, Attribute.GENERIC_ARMOR, 10);
        set(boss, Attribute.GENERIC_KNOCKBACK_RESISTANCE, 0.8);
        set(boss, Attribute.GENERIC_FOLLOW_RANGE, 48);
        equip(boss, def);
        Mobs.aggravate(boss, p);
        BossBar bar = Bukkit.createBossBar(ChatColor.DARK_RED + def.name, BarColor.RED, BarStyle.SEGMENTED_10);
        f = new Fight(record, boss, bar, arena);
        fights.put(key, f);
        byEntity.put(boss.getUniqueId(), f);
        for (Player q : abilities.near(arena, 32)) {
            q.sendTitle(ChatColor.DARK_RED + def.name, ChatColor.GRAY + plan.design.name + " - Muse+GLM_Maps", 10, 60, 20);
            q.playSound(q.getLocation(), Sound.ENTITY_WITHER_SPAWN, 0.8f, 1.2f);
        }
        plugin.getLogger().info("MUSE_BOSS_SPAWN boss=" + def.key + " site=" + key);
    }

    private static void set(LivingEntity e, Attribute a, double v) {
        AttributeInstance i = e.getAttribute(a);
        if (i != null) i.setBaseValue(v);
    }

    private static void equip(LivingEntity boss, Catalog.Boss def) {
        EntityEquipment eq = boss.getEquipment();
        if (eq == null) return;
        switch (boss.getType()) {
            case WITHER_SKELETON: { ItemStack s = new ItemStack(Material.DIAMOND_SWORD); s.addUnsafeEnchantment(Enchantment.DAMAGE_ALL, 3); eq.setItemInMainHand(s); break; }
            case STRAY: case SKELETON: {
                ItemStack bow = new ItemStack(Material.BOW); bow.addUnsafeEnchantment(Enchantment.ARROW_DAMAGE, 4);
                eq.setItemInMainHand(bow); eq.setHelmet(new ItemStack(Material.DIAMOND_HELMET));
                boss.addPotionEffect(new PotionEffect(PotionEffectType.FIRE_RESISTANCE, Integer.MAX_VALUE, 0, true, false));
                break;
            }
            case VINDICATOR: { ItemStack axe = new ItemStack(Material.DIAMOND_AXE); axe.addUnsafeEnchantment(Enchantment.DAMAGE_ALL, 2); eq.setItemInMainHand(axe); break; }
            default: break;
        }
        eq.setItemInMainHandDropChance(0f); eq.setHelmetDropChance(0f);
    }

    /** Every 10 ticks. */
    void tick() {
        long now = System.currentTimeMillis();
        for (Fight f : new ArrayList<>(fights.values())) {
            LivingEntity b = f.boss;
            if (!b.isValid() || b.isDead()) {
                if (now - f.born < 5000) { backoff.put(f.record.plan.key(), now + 60000); plugin.getLogger().warning("MUSE_BOSS_LOST site=" + f.record.plan.key()); }
                end(f, false); continue;
            }
            double max = b.getAttribute(Attribute.GENERIC_MAX_HEALTH).getValue();
            f.bar.setProgress(Math.max(0, Math.min(1, b.getHealth() / max)));
            List<Player> near = abilities.near(f.arena, 48);
            for (Player p : new ArrayList<>(f.bar.getPlayers())) if (!near.contains(p)) f.bar.removePlayer(p);
            for (Player p : near) if (!f.bar.getPlayers().contains(p)) f.bar.addPlayer(p);
            if (!near.isEmpty()) f.lastSeen = now;
            if (now - f.lastSeen > 60000) { b.remove(); end(f, false); continue; }
            if (b.getLocation().distanceSquared(f.arena) > 28 * 28) {
                Location back = Abilities.floor(f.arena);
                b.teleport(back != null ? back.add(0.5, 0, 0.5) : f.arena);
                b.getWorld().spawnParticle(Particle.PORTAL, b.getLocation(), 40, 0.5, 1, 0.5, 0.4);
            }
            int phase = b.getHealth() <= max * 0.33 ? 3 : b.getHealth() <= max * 0.66 ? 2 : 1;
            if (phase > f.phase) {
                f.phase = phase;
                for (Player p : near) p.sendMessage(ChatColor.DARK_RED + f.record.plan.design.boss.name + ChatColor.RED + (phase == 2 ? " grows furious!" : " is desperate!"));
                abilities.cast(summonOf(f.record.plan.design.boss), b, target(b, near), 5);
                f.bar.setColor(phase == 2 ? BarColor.PINK : BarColor.PURPLE);
            }
            if ((f.cooldown -= 10) > 0 || near.isEmpty()) continue;
            Player t = target(b, near);
            List<String> moves = f.record.plan.design.boss.abilities;
            String move = moves.get(f.next++ % moves.size());
            if (random.nextInt(3) == 0) move = moves.get(random.nextInt(moves.size()));
            final String cast = move;
            telegraph(b, near, cast);
            if (Boolean.getBoolean("jaspr.muse.fixture")) plugin.getLogger().info("MUSE_BOSS_MOVE boss=" + f.record.plan.design.boss.key + " move=" + cast + " phase=" + phase);
            plugin.later(16, () -> { if (b.isValid() && t.isOnline()) abilities.cast(cast, b, t, 5); });
            f.cooldown = phase == 3 ? 30 : phase == 2 ? 45 : 60;
        }
    }

    private static String summonOf(Catalog.Boss def) {
        for (String a : def.abilities) if (a.startsWith("SUMMON_") || a.equals("BROOD") || a.equals("MAGMA_SPAWN")) return a;
        return "WARCRY";
    }

    private Player target(LivingEntity b, List<Player> near) {
        Player best = null; double bd = Double.MAX_VALUE;
        for (Player p : near) { double d = p.getLocation().distanceSquared(b.getLocation()); if (d < bd) { bd = d; best = p; } }
        return best;
    }

    private void telegraph(LivingEntity b, List<Player> near, String move) {
        String label = move.charAt(0) + move.substring(1).toLowerCase().replace('_', ' ');
        for (Player p : near) p.sendActionBar(ChatColor.RED + "⚠ " + ChatColor.stripColor(b.getCustomName()) + " prepares " + label + "!");
        b.getWorld().spawnParticle(Particle.SPELL_WITCH, b.getLocation().add(0, 1.2, 0), 40, 0.5, 0.8, 0.5, 0.05);
        b.getWorld().playSound(b.getLocation(), Sound.ENTITY_EVOCATION_ILLAGER_PREPARE_ATTACK, 1, 0.6f);
    }

    private void end(Fight f, boolean defeated) {
        f.bar.removeAll();
        fights.values().remove(f);
        byEntity.remove(f.boss.getUniqueId());
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void death(EntityDeathEvent e) {
        Fight f = byEntity.get(e.getEntity().getUniqueId());
        if (f == null) return;
        Catalog.Boss def = f.record.plan.design.boss;
        Player killer = e.getEntity().getKiller();
        e.getDrops().clear();
        e.setDroppedExp(600);
        String weapon = Weapons.BOSS_WEAPON.get(def.key);
        if (weapon != null) e.getDrops().add(Loot.expedition(weapon, 5));
        Essences.Kind a = Essences.random(random), b;
        do { b = Essences.random(random); } while (b == a);
        e.getDrops().add(Essences.item(a, 1));
        e.getDrops().add(Essences.item(b, 1));
        e.getDrops().add(Loot.smeBook(random, 5, true));
        e.getDrops().add(Loot.smeBook(random, 5, true));
        e.getDrops().add(new ItemStack(Material.DIAMOND, 2 + random.nextInt(3)));
        f.record.bossDefeated = true;
        f.record.bossDefeatedAt = System.currentTimeMillis();
        try { plugin.ledger(e.getEntity().getWorld()).save(f.record); }
        catch (Exception ex) { plugin.getLogger().warning("MUSE_BOSS_SAVE_FAILED " + ex.getClass().getSimpleName()); }
        String who = killer != null ? killer.getName() : "Someone";
        Bukkit.broadcastMessage(ChatColor.GOLD + who + " defeated " + ChatColor.DARK_RED + def.name + ChatColor.GOLD + " at the "
            + f.record.plan.design.name + ChatColor.GRAY + " (Muse+GLM_Maps). The vault is unsealed.");
        plugin.getLogger().info("MUSE_BOSS_DEFEATED boss=" + def.key + " site=" + f.record.plan.key());
        end(f, true);
    }

    /** The vault chest of a boss site stays sealed until its boss is defeated. */
    boolean sealed(Block chest, Ledger.Record r) {
        if (r == null || r.plan == null || r.plan.design.boss == null || r.bossDefeated) return false;
        int[] v = Stamper.vaultSpot(r.plan.design);
        return v != null && chest.getX() == r.plan.x + v[0] && chest.getY() == r.plan.y + v[1] && chest.getZ() == r.plan.z + v[2];
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void open(PlayerInteractEvent e) {
        if (e.getAction() != Action.RIGHT_CLICK_BLOCK || e.getClickedBlock() == null || e.getClickedBlock().getType() != Material.CHEST) return;
        Ledger.Record r = plugin.siteAt(e.getClickedBlock().getLocation());
        if (!sealed(e.getClickedBlock(), r)) return;
        e.setCancelled(true);
        e.getPlayer().sendMessage(ChatColor.DARK_RED + "Sealed. " + ChatColor.GRAY + r.plan.design.boss.name + " still holds this vault.");
        e.getPlayer().playSound(e.getClickedBlock().getLocation(), Sound.BLOCK_CHEST_LOCKED, 1, 1);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void breakVault(BlockBreakEvent e) {
        if (e.getBlock().getType() != Material.CHEST) return;
        Ledger.Record r = plugin.siteAt(e.getBlock().getLocation());
        if (sealed(e.getBlock(), r)) { e.setCancelled(true); e.getPlayer().sendMessage(ChatColor.DARK_RED + "The vault will not yield while its keeper lives."); }
    }

    void shutdown() {
        for (Fight f : new ArrayList<>(fights.values())) { f.bar.removeAll(); if (f.boss.isValid()) f.boss.remove(); }
        fights.clear(); byEntity.clear();
    }

    int active() { return fights.size(); }
    boolean isBoss(Entity e) { return byEntity.containsKey(e.getUniqueId()); }
}
