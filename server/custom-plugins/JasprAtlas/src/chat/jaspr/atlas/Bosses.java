package chat.jaspr.atlas;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Blaze;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Evoker;
import org.bukkit.entity.Illusioner;
import org.bukkit.entity.IronGolem;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Creature;
import org.bukkit.entity.Player;
import org.bukkit.entity.Vindicator;
import org.bukkit.entity.Witch;
import org.bukkit.entity.WitherSkeleton;
import org.bukkit.entity.Zombie;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityTargetLivingEntityEvent;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

/**
 * The Dominion's rulers and captains. Each waits at its place (found by the {@link Registry}) and rises when a player
 * comes near; it goes back to waiting, healed, if everyone leaves. The four Ash-Crowned cannot be harmed without the
 * Concord's knowledge: the Oath held near Kallias, the Fonts sung quiet around Melaina, the Governors stilled in order
 * around Daidaros, the Edict Stones read silent around Keleos. The Pyrarch fights in three phases and, at the last,
 * hides behind the Cinder Heart until the Light of Theano is carried to it. A fallen boss never rises again, and its
 * reward goes once to each player who fought it.
 */
final class Bosses implements Listener {
    static final String TAG = "atlas_boss:";
    static final String ECHO = "Echo of the Pyrarch";

    /** The captains, for the Codex: id, name, where. */
    static final String[][] LESSER = {
        {"gorvash", "Gorvash the Drummer", "his war-camp, north Marches"}, {"crownless", "the Crownless Rider", "the Rider's Tower, south Marches"},
        {"sallow", "Mother Sallow", "the House of Stilling, Weald"}, {"grunnak", "Grunnak the Slag Troll", "the Slag Quarry, Forges"},
        {"fiend", "the Hollow Fiend", "the Pit, Forges"}, {"kalchas", "Kalchas the Informer", "Aigai"}, {"choirmaster", "the Choirmaster", "Anthrakion"},
    };

    enum Boss {
        KALLIAS("kallias", "Kallias, the Marshal of the Teeth", 320, 12, 8, BarColor.RED, Realm.Province.MARCHES),
        MELAINA("melaina", "Melaina, the Stiller", 280, 0, 6, BarColor.WHITE, Realm.Province.WEALD),
        DAIDAROS("daidaros", "Daidaros, the Forgemaster", 300, 11, 12, BarColor.YELLOW, Realm.Province.FORGES),
        KELEOS("keleos", "Keleos, the Silent Magistrate", 280, 0, 8, BarColor.PURPLE, Realm.Province.FALLEN),
        PYRARCH("pyrarch", "The Pyrarch, Phosphoros the Lampbearer", 700, 15, 14, BarColor.RED, Realm.Province.PLATEAU),
        GORVASH("gorvash", "Gorvash the Drummer", 150, 10, 8, BarColor.RED, null),
        CROWNLESS("crownless", "the Crownless Rider", 170, 11, 8, BarColor.PURPLE, null),
        SALLOW("sallow", "Mother Sallow", 130, 0, 4, BarColor.WHITE, null),
        GRUNNAK("grunnak", "Grunnak the Slag Troll", 240, 17, 10, BarColor.YELLOW, null),
        FIEND("fiend", "the Hollow Fiend", 200, 9, 6, BarColor.RED, null),
        KALCHAS("kalchas", "Kalchas the Informer", 130, 0, 6, BarColor.PURPLE, null),
        CHOIRMASTER("choirmaster", "the Choirmaster", 180, 0, 6, BarColor.PURPLE, null);
        final String id, title;
        final double health, damage, armor;
        final BarColor color;
        final Realm.Province province;
        Boss(String id, String title, double health, double damage, double armor, BarColor color, Realm.Province province) {
            this.id = id; this.title = title; this.health = health; this.damage = damage; this.armor = armor; this.color = color; this.province = province;
        }
        boolean crowned() { return province != null && province != Realm.Province.PLATEAU; }
        static Boss of(String id) { for (Boss b : values()) if (b.id.equals(id)) return b; return null; }
    }

    /** One risen boss. */
    final class Fight {
        final Boss boss;
        final UUID entity;
        final BossBar bar;
        final Location home;
        final Set<UUID> fought = new HashSet<>();
        long ticks, alone, lastWarn;
        int phase = 1;
        boolean heartBroken, oathSeen, echo;   // echo: the Pyrarch again, after victory, for those who did not see him fall
        double channel;
        Fight(Boss boss, LivingEntity e, Location home) {
            this.boss = boss; this.entity = e.getUniqueId(); this.home = home;
            this.bar = Bukkit.createBossBar(ChatColor.DARK_RED + boss.title, boss.color, BarStyle.SEGMENTED_10);
        }
        LivingEntity entity() { Entity e = Bukkit.getEntity(entity); return e instanceof LivingEntity && !e.isDead() ? (LivingEntity) e : null; }
    }

    private final AtlasPlugin plugin;
    private final Map<Boss, Fight> fights = new HashMap<>();
    private final java.util.Random random = new java.util.Random();
    private long tick;
    long risen, fallen, turned;

    Bosses(AtlasPlugin plugin) { this.plugin = plugin; }

    boolean fallen(Boss b) { return plugin.state().bossesFallen.containsKey(b.id); }

    Fight fight(Boss b) { return fights.get(b); }

    /**
     * Whether a boss may rise at all: not fallen, and (for the Pyrarch) the four Wards dark; Grunnak turns to stone with
     * his master. After victory the Pyrarch's Echo rises for anyone who has not conquered Atlas (see {@link #echoWanted}).
     */
    boolean mayRise(Boss b) {
        if (fallen(b)) return b == Boss.PYRARCH && plugin.state().victory;
        if (b == Boss.PYRARCH) return plugin.state().wardsDark() == 4;
        if (b == Boss.GRUNNAK) return !fallen(Boss.DAIDAROS);
        return true;
    }

    // ------------------------------------------------------------------ rising and waiting

    /** Twice a second: bosses rise near players, fight, and go back to waiting when left alone. */
    void tick() {
        tick++;
        World w = plugin.atlas();
        if (w == null) { clear(); return; }
        if (!plugin.registry().ready()) return;
        for (Boss b : Boss.values()) {
            Fight f = fights.get(b);
            if (f != null) { run(w, f); continue; }
            if (!mayRise(b) || tick % 4 != 0) continue;
            Registry.Spot spot = plugin.registry().spot("boss:" + b.id);
            if (spot == null || !w.isChunkLoaded(spot.x >> 4, spot.z >> 4)) continue;
            Location at = spot.at(w);
            Player near = nearest(w, at, 30);
            if (near == null) continue;
            if (b == Boss.PYRARCH && fallen(b) && !echoWanted(w, at)) continue;
            rise(b, standable(at));
        }
        if (tick % 10 == 0) orphans(w);
    }

    private Player nearest(World w, Location at, double r) {
        Player best = null;
        double bd = r * r;
        for (Player p : w.getPlayers()) {
            if (p.getGameMode() == GameMode.SPECTATOR || p.isDead()) continue;
            double d = p.getLocation().distanceSquared(at);
            if (d < bd) { bd = d; best = p; }
        }
        return best;
    }

    /** A free spot to stand near a marker (two clear blocks over something solid). */
    static Location standable(Location at) {
        World w = at.getWorld();
        for (int r = 0; r <= 4; r++)
            for (int dy = 0; dy <= 3; dy++)
                for (int sy = 1; sy >= -1; sy -= 2)
                    for (int dx = -r; dx <= r; dx++)
                        for (int dz = -r; dz <= r; dz++) {
                            if (Math.max(Math.abs(dx), Math.abs(dz)) != r) continue;
                            int x = at.getBlockX() + dx, y = at.getBlockY() + dy * sy, z = at.getBlockZ() + dz;
                            Block feet = w.getBlockAt(x, y, z);
                            if (!feet.isEmpty() && feet.getType().isSolid() || feet.getRelative(0, 1, 0).getType().isSolid() || !feet.getRelative(0, -1, 0).getType().isSolid()) continue;
                            if (feet.isLiquid()) continue;
                            return new Location(w, x + 0.5, y, z + 0.5, at.getYaw(), 0);
                        }
        return at;
    }

    LivingEntity rise(Boss b, Location at) {
        boolean echo = b == Boss.PYRARCH && fallen(b);
        LivingEntity e = spawn(b, at);
        Fight f = new Fight(b, e, at.clone());
        f.echo = echo;
        if (echo) { e.setCustomName(ChatColor.DARK_RED + ECHO); f.bar.setTitle(ChatColor.DARK_RED + ECHO); }
        fights.put(b, f);
        risen++;
        for (Player p : at.getWorld().getPlayers()) if (p.getLocation().distanceSquared(at) < 48 * 48) {
            p.sendTitle(ChatColor.DARK_RED + (echo ? ECHO : b.title), ChatColor.GRAY + (echo ? "For those who did not see him fall." : intro(b)), 10, 70, 20);
            p.playSound(p.getLocation(), Sound.ENTITY_WITHER_SPAWN, 0.6f, 0.6f);
        }
        plugin.getLogger().info("ATLAS_BOSS_RISEN id=" + b.id + (echo ? " echo=true" : "") + " at=" + at.getBlockX() + "," + at.getBlockY() + "," + at.getBlockZ());
        return e;
    }

    /** After victory: whether someone near the Cinder Throne has not conquered Atlas yet (the Echo rises only for them). */
    private boolean echoWanted(World w, Location at) {
        for (Player p : w.getPlayers())
            if (p.getGameMode() != GameMode.SPECTATOR && !p.isDead() && p.getLocation().distanceSquared(at) < 30 * 30
                && plugin.guide() != null && !plugin.guide().beaten(p)) return true;
        return false;
    }

    private static String intro(Boss b) {
        switch (b) {
            case KALLIAS: return "He does not tire. He does not fall. Unless...";
            case MELAINA: return "Nothing dies near her Fonts.";
            case DAIDAROS: return "The Engine shields its master.";
            case KELEOS: return "His Stones speak for him.";
            case PYRARCH: return "The Keeper of the broken Star.";
            case GORVASH: return "The drum of the war-camp.";
            case CROWNLESS: return "He keeps the list of the taken.";
            case SALLOW: return "The Stiller's high nurse.";
            case GRUNNAK: return "Troll-boss of the quarry.";
            case FIEND: return "It burns in the pit.";
            case KALCHAS: return "He knows who said what.";
            default: return "The Choir's master.";
        }
    }

    @SuppressWarnings("deprecation")
    private LivingEntity spawn(Boss b, Location at) {
        World w = at.getWorld();
        LivingEntity e;
        switch (b) {
            case KALLIAS: case PYRARCH: case CROWNLESS:
                e = w.spawn(at, WitherSkeleton.class, s -> {
                    EntityEquipment q = s.getEquipment();
                    q.setHelmet(new ItemStack(b == Boss.PYRARCH ? Material.GOLD_HELMET : Material.IRON_HELMET));
                    q.setChestplate(new ItemStack(b == Boss.PYRARCH ? Material.GOLD_CHESTPLATE : Material.IRON_CHESTPLATE));
                    q.setItemInMainHand(new ItemStack(b == Boss.CROWNLESS ? Material.STONE_SWORD : Material.DIAMOND_SWORD));
                    if (b == Boss.KALLIAS) q.setItemInOffHand(new ItemStack(Material.SHIELD));
                    if (b == Boss.PYRARCH) { ItemStack sword = q.getItemInMainHand(); sword.addUnsafeEnchantment(org.bukkit.enchantments.Enchantment.FIRE_ASPECT, 2); q.setItemInMainHand(sword); }
                });
                break;
            case MELAINA: case SALLOW: e = w.spawn(at, Witch.class, x -> { }); break;
            case DAIDAROS: e = w.spawn(at, Vindicator.class, v -> {
                v.getEquipment().setItemInMainHand(new ItemStack(Material.IRON_AXE));
                v.getEquipment().setHelmet(new ItemStack(Material.IRON_HELMET));
                v.getEquipment().setChestplate(new ItemStack(Material.IRON_CHESTPLATE));
            }); break;
            case KELEOS: case KALCHAS: e = w.spawn(at, Evoker.class, x -> { }); break;
            case GORVASH: e = w.spawn(at, Zombie.class, z -> {
                z.getEquipment().setHelmet(new ItemStack(Material.IRON_HELMET));
                z.getEquipment().setChestplate(new ItemStack(Material.IRON_CHESTPLATE));
                z.getEquipment().setItemInMainHand(new ItemStack(Material.IRON_AXE));
            }); break;
            case GRUNNAK: e = w.spawn(at, IronGolem.class, g -> g.setPlayerCreated(false)); break;
            case FIEND: e = w.spawn(at, Blaze.class, x -> { }); break;
            default: e = w.spawn(at, Illusioner.class, x -> { }); break;
        }
        e.setCustomName(ChatColor.DARK_RED + b.title);
        e.setCustomNameVisible(true);
        e.setRemoveWhenFarAway(false);
        e.addScoreboardTag(Npcs.TAG);
        e.addScoreboardTag(Npcs.DOMINION);
        e.addScoreboardTag(TAG + b.id);
        e.addScoreboardTag(Npcs.EXEMPT);
        set(e, Attribute.GENERIC_MAX_HEALTH, b.health);
        e.setHealth(b.health);
        if (b.damage > 0) set(e, Attribute.GENERIC_ATTACK_DAMAGE, b.damage);
        set(e, Attribute.GENERIC_ARMOR, b.armor);
        set(e, Attribute.GENERIC_KNOCKBACK_RESISTANCE, b == Boss.PYRARCH || b == Boss.GRUNNAK || b == Boss.KALLIAS ? 1.0 : 0.6);
        set(e, Attribute.GENERIC_FOLLOW_RANGE, 40);
        EntityEquipment q = e.getEquipment();
        if (q != null) { q.setHelmetDropChance(0f); q.setChestplateDropChance(0f); q.setItemInMainHandDropChance(0f); q.setItemInOffHandDropChance(0f); }
        e.addPotionEffect(new PotionEffect(PotionEffectType.FIRE_RESISTANCE, Integer.MAX_VALUE, 0, true, false));
        return e;
    }

    private static void set(LivingEntity e, Attribute a, double v) { AttributeInstance i = e.getAttribute(a); if (i != null) i.setBaseValue(v); }

    /** Removes boss entities nobody is fighting (saved into a chunk, or left over from before a restart). */
    private void orphans(World w) {
        Set<UUID> live = new HashSet<>();
        for (Fight f : fights.values()) live.add(f.entity);
        for (Entity e : w.getEntities()) {
            String id = Npcs.tagValue(e, TAG);
            if (id != null && !live.contains(e.getUniqueId())) e.remove();
        }
    }

    /** Clears every fight (the world is unloading or the plugin stopping). */
    void clear() {
        for (Fight f : fights.values()) { f.bar.removeAll(); LivingEntity e = f.entity(); if (e != null) e.remove(); }
        fights.clear();
    }

    // ------------------------------------------------------------------ fighting

    private void run(World w, Fight f) {
        LivingEntity e = f.entity();
        if (e == null || e.getWorld() != w) { f.bar.removeAll(); fights.remove(f.boss); return; }
        f.ticks++;
        // Who sees the bar; who is near; whether everyone has left.
        List<Player> near = new ArrayList<>();
        for (Player p : w.getPlayers()) {
            boolean in = p.getGameMode() != GameMode.SPECTATOR && !p.isDead() && p.getLocation().distanceSquared(e.getLocation()) < 48 * 48;
            if (in) { near.add(p); if (!f.bar.getPlayers().contains(p)) f.bar.addPlayer(p); } else if (f.bar.getPlayers().contains(p)) f.bar.removePlayer(p);
        }
        double max = e.getAttribute(Attribute.GENERIC_MAX_HEALTH).getValue();
        f.bar.setProgress(Math.max(0, Math.min(1, e.getHealth() / max)));
        if (near.isEmpty()) {
            if (++f.alone > 80) {   // 40 seconds alone: back to waiting, healed
                f.bar.removeAll();
                e.remove();
                fights.remove(f.boss);
                plugin.getLogger().info("ATLAS_BOSS_RESET id=" + f.boss.id + " reason=alone");
            }
            return;
        }
        f.alone = 0;
        // Leashed to its place: dragged back if lured away.
        if (e.getLocation().distanceSquared(f.home) > 40 * 40 || e.getLocation().getY() < f.home.getY() - 20) { e.teleport(f.home); e.setFallDistance(0); }
        if (e instanceof Creature) {
            Creature m = (Creature) e;
            if (m.getTarget() == null || !(m.getTarget() instanceof Player) || m.getTarget().getLocation().distanceSquared(e.getLocation()) > 30 * 30) {
                Player t = nearest(w, e.getLocation(), 28);
                if (t != null && t.getGameMode() != GameMode.CREATIVE) m.setTarget(t);
            }
        }
        plugin.mechanisms().during(f, e, near);
        behave(w, f, e, near);
    }

    /** Each boss's own manner of fighting. */
    private void behave(World w, Fight f, LivingEntity e, List<Player> near) {
        Location at = e.getLocation();
        switch (f.boss) {
            case KALLIAS: {
                boolean oath = plugin.mechanisms().oathNear(e);
                if (!oath && e.getHealth() < f.boss.health) e.setHealth(Math.min(f.boss.health, e.getHealth() + 2));
                f.bar.setTitle(ChatColor.DARK_RED + f.boss.title + (oath ? ChatColor.GOLD + "  (the Oath is heard: he can be hurt)" : ChatColor.GRAY + "  (tireless)"));
                if (f.ticks % 24 == 0) summon(e, Dominion.Kind.BLACKSHIELD, 2, 5);
                break;
            }
            case MELAINA:
                f.bar.setTitle(ChatColor.DARK_RED + f.boss.title + ChatColor.GRAY + "  (Fonts singing: " + (3 - plugin.mechanisms().count("font:")) + ")");
                if (f.ticks % 30 == 0) summon(e, Dominion.Kind.ASHBORN, 2, 5);
                break;
            case DAIDAROS:
                f.bar.setTitle(ChatColor.DARK_RED + f.boss.title + ChatColor.GRAY + "  (Governors singing: " + (3 - plugin.mechanisms().count("governor:")) + ")");
                if (f.ticks % 30 == 0) summon(e, Dominion.Kind.EMBERKIN, 2, 4);
                if (f.ticks % 16 == 0) for (Player p : near) if (p.getLocation().distanceSquared(at) < 5 * 5) { p.setFireTicks(60); p.sendMessage(ChatColor.GOLD + "A vent of engine steam scalds you."); }
                break;
            case KELEOS:
                f.bar.setTitle(ChatColor.DARK_RED + f.boss.title + ChatColor.GRAY + "  (Stones speaking: " + (3 - plugin.mechanisms().count("edict:")) + ")");
                if (f.ticks % 30 == 0) summon(e, Dominion.Kind.BLACKSHIELD, 2, 5);
                break;
            case PYRARCH: pyrarch(w, f, e, near); break;
            case GORVASH:
                if (f.ticks % 20 == 0) {
                    w.playSound(at, Sound.BLOCK_NOTE_BASEDRUM, 2f, 0.5f);
                    for (Entity x : e.getNearbyEntities(16, 6, 16)) if (Dominion.isDominion(x) && x instanceof LivingEntity) {
                        ((LivingEntity) x).addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 120, 0, true, false));
                        ((LivingEntity) x).addPotionEffect(new PotionEffect(PotionEffectType.INCREASE_DAMAGE, 120, 0, true, false));
                    }
                    summon(e, Dominion.Kind.ASHBORN, 1, 4);
                }
                break;
            case SALLOW:
                if (f.ticks % 16 == 0) for (Entity x : e.getNearbyEntities(12, 6, 12)) if (Dominion.isDominion(x) && x instanceof LivingEntity) ((LivingEntity) x).addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 100, 1, true, false));
                break;
            case FIEND:
                if (f.ticks % 30 == 0) summon(e, Dominion.Kind.EMBERKIN, 2, 3);
                break;
            case KALCHAS:
                if (f.ticks % 36 == 0) summon(e, Dominion.Kind.BLACKSHIELD, 2, 3);
                break;
            case GRUNNAK:
                if (f.ticks % 20 == 0) for (Player p : near) if (p.getLocation().distanceSquared(at) < 4 * 4 && p.getGameMode() == GameMode.SURVIVAL) {
                    p.setVelocity(p.getLocation().toVector().subtract(at.toVector()).setY(0).normalize().multiply(1.2).setY(0.5));
                    w.playSound(at, Sound.ENTITY_IRONGOLEM_ATTACK, 1f, 0.6f);
                }
                break;
            default:
        }
    }

    /** The Pyrarch: fire, then his guard and the ash-shock, then the Heart; the Light must be carried to the Heart's root. */
    private void pyrarch(World w, Fight f, LivingEntity e, List<Player> near) {
        double frac = e.getHealth() / f.boss.health;
        if (f.phase == 1 && frac < 0.6) { f.phase = 2; say(near, ChatColor.DARK_RED + "The Pyrarch: " + ChatColor.GRAY + "\"You play well, stranger. You play the wrong song.\""); }
        if (f.phase == 2 && frac < 0.25 && !f.heartBroken) {
            f.phase = 3;
            say(near, ChatColor.DARK_RED + "The Pyrarch: " + ChatColor.GRAY + "\"Enough.\" " + ChatColor.RED + "The Cinder Heart roars above the throne and wraps him in black fire. "
                + ChatColor.GOLD + "Carry the Light of Theano to the Heart's root, the black column above the throne, and hold it there.");
            plugin.getLogger().info("ATLAS_PYRARCH_PHASE phase=3");
        }
        Location at = e.getLocation();
        String status = f.heartBroken ? ChatColor.GOLD + "  (the Heart is broken: he is mortal)" : f.phase == 3 ? ChatColor.GRAY + "  (shielded by the Cinder Heart)" : ChatColor.GRAY + "  (phase " + f.phase + ")";
        f.bar.setTitle(ChatColor.DARK_RED + (f.echo ? ECHO : f.boss.title) + status);
        if (f.ticks % 24 == 0) {   // ember rain: small fireballs at the nearest players
            for (Player p : near) {
                if (p.getGameMode() != GameMode.SURVIVAL || p.getLocation().distanceSquared(at) > 24 * 24) continue;
                Vector dir = p.getEyeLocation().toVector().subtract(e.getEyeLocation().toVector()).normalize();
                org.bukkit.entity.SmallFireball fb = w.spawn(e.getEyeLocation().add(dir.clone().multiply(1.5)), org.bukkit.entity.SmallFireball.class);
                fb.setShooter(e);
                fb.setDirection(dir);
                fb.setIsIncendiary(false);
            }
        }
        if (f.phase >= 2 && f.ticks % 20 == 0) {   // the ash-shock
            w.spawnParticle(Particle.SMOKE_LARGE, at, 60, 3, 0.5, 3, 0.05);
            w.playSound(at, Sound.ENTITY_GENERIC_EXPLODE, 1f, 0.6f);
            for (Player p : near) if (p.getLocation().distanceSquared(at) < 6 * 6 && p.getGameMode() == GameMode.SURVIVAL) {
                p.damage(4, e);
                p.setVelocity(p.getLocation().toVector().subtract(at.toVector()).setY(0).normalize().multiply(1.1).setY(0.45));
            }
        }
        if (f.phase >= 2 && f.ticks % 40 == 0 && !f.heartBroken) { summon(e, Dominion.Kind.EMBERKIN, 2, 5); summon(e, Dominion.Kind.BLACKSHIELD, 1, 5); }
        if (f.phase == 3 && !f.heartBroken && f.ticks % 12 == 0)
            for (Player p : near) if (!Items.holding(p, "light")) p.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 50, 0, true, false));
        if (f.phase == 3 && !f.heartBroken) plugin.mechanisms().channelHeart(f, e, near);
    }

    /** Raises up to n of a kind round a boss, keeping at most cap of its raised near it. */
    private void summon(LivingEntity boss, Dominion.Kind k, int n, int cap) {
        int have = 0;
        for (Entity x : boss.getNearbyEntities(20, 8, 20)) if (Npcs.has(x, "atlas_minion")) have++;
        for (int i = 0; i < n && have < cap; i++, have++) {
            Location at = boss.getLocation().add(random.nextInt(7) - 3, 0, random.nextInt(7) - 3);
            LivingEntity m = plugin.dominion().raise(standable(at), k, false);
            m.addScoreboardTag("atlas_minion");
        }
    }

    static void say(List<Player> to, String msg) { for (Player p : to) p.sendMessage(msg); }

    // ------------------------------------------------------------------ damage, immunity and death

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void damaged(EntityDamageEvent e) {
        String id = Npcs.tagValue(e.getEntity(), TAG);
        if (id == null) return;
        Boss b = Boss.of(id);
        Fight f = b == null ? null : fights.get(b);
        switch (e.getCause()) {
            case FALL: case SUFFOCATION: case DROWNING: case FIRE: case FIRE_TICK: case LAVA: case HOT_FLOOR: case CRAMMING:
                e.setCancelled(true);
                return;
            default:
        }
        if (f == null) return;
        String why = plugin.mechanisms().shield(f, (LivingEntity) e.getEntity());
        if (why == null) return;
        e.setCancelled(true);
        Player p = e instanceof EntityDamageByEntityEvent ? Reputation.attacker(((EntityDamageByEntityEvent) e).getDamager()) : null;
        if (p != null && System.currentTimeMillis() - f.lastWarn > 4000) {
            f.lastWarn = System.currentTimeMillis();
            e.getEntity().getWorld().playSound(e.getEntity().getLocation(), Sound.ITEM_SHIELD_BLOCK, 1f, 0.6f);
            for (Player q : e.getEntity().getWorld().getPlayers()) if (q.getLocation().distanceSquared(e.getEntity().getLocation()) < 32 * 32) q.sendMessage(why);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void hit(EntityDamageByEntityEvent e) {
        String id = Npcs.tagValue(e.getEntity(), TAG);
        if (id == null) return;
        Fight f = fights.get(Boss.of(id));
        Player p = Reputation.attacker(e.getDamager());
        if (f != null && p != null) f.fought.add(p.getUniqueId());
    }

    /** Bosses never fight other Dominion folk or the Bound. */
    @EventHandler(ignoreCancelled = true)
    public void target(EntityTargetLivingEntityEvent e) {
        if (Npcs.tagValue(e.getEntity(), TAG) != null && e.getTarget() != null && !(e.getTarget() instanceof Player)) e.setCancelled(true);
    }

    @EventHandler
    public void death(EntityDeathEvent e) {
        String id = Npcs.tagValue(e.getEntity(), TAG);
        if (id == null) return;
        Boss b = Boss.of(id);
        e.getDrops().clear();
        e.setDroppedExp(b != null && (b.crowned() || b == Boss.PYRARCH) ? 400 : 120);
        if (b == null) return;
        Fight f = fights.remove(b);
        if (f != null) f.bar.removeAll();
        if (f != null && f.echo) { echoFallen(e, f); return; }
        if (fallen(b)) return;
        World w = e.getEntity().getWorld();
        Location at = e.getEntity().getLocation();
        List<Player> by = new ArrayList<>();
        for (Player p : w.getPlayers()) {
            boolean fought = f != null && f.fought.contains(p.getUniqueId());
            if (fought || p.getGameMode() != GameMode.SPECTATOR && p.getLocation().distanceSquared(at) < 40 * 40) by.add(p);
        }
        plugin.state().bossesFallen.put(b.id, System.currentTimeMillis());
        fallen++;
        say(by, ChatColor.DARK_RED + b.title + ": " + ChatColor.GRAY + lastWords(b));
        w.playSound(at, Sound.ENTITY_WITHER_DEATH, 1f, b == Boss.PYRARCH ? 0.5f : 0.8f);
        w.spawnParticle(Particle.SMOKE_LARGE, at.clone().add(0, 1, 0), 80, 1, 1.5, 1, 0.05);
        // The captain's papers and trophies.
        if (b == Boss.CROWNLESS) e.getDrops().add(LoreBooks.BOOKS.get("rider_list").item());
        if (b == Boss.SALLOW) e.getDrops().add(LoreBooks.BOOKS.get("stilling_protocol").item());
        if (b == Boss.KALCHAS) e.getDrops().add(LoreBooks.BOOKS.get("ledger").item());
        for (Player p : by) reward(p, b);
        String names = by.isEmpty() ? "nobody" : String.join(",", names(by));
        plugin.getLogger().info("ATLAS_BOSS_FALLEN id=" + b.id + " by=" + names + " participants=" + by.size());
        if (b.crowned()) plugin.liberation().liberate(b.province, by, b);
        else if (b == Boss.PYRARCH) {
            plugin.liberation().victory(by);
            if (plugin.guide() != null) for (Player p : by) plugin.guide().victory(p);   // each of them has conquered Atlas
        }
        else Bukkit.broadcastMessage(ChatColor.DARK_RED + b.title + ChatColor.GRAY + " has fallen in Atlas (" + (by.isEmpty() ? "unseen" : "by " + String.join(", ", names(by))) + ").");
        if (b == Boss.DAIDAROS) stoneTheTrolls(w);
        plugin.saveStateSoon();
    }

    private static List<String> names(List<Player> ps) { List<String> n = new ArrayList<>(); for (Player p : ps) n.add(p.getName()); return n; }

    /** The Echo falls: everyone who fought it or stood near has conquered Atlas (the realm itself was freed already). */
    private void echoFallen(EntityDeathEvent e, Fight f) {
        World w = e.getEntity().getWorld();
        Location at = e.getEntity().getLocation();
        List<Player> by = new ArrayList<>();
        for (Player p : w.getPlayers())
            if (f.fought.contains(p.getUniqueId()) || p.getGameMode() != GameMode.SPECTATOR && p.getLocation().distanceSquared(at) < 40 * 40) by.add(p);
        say(by, ChatColor.DARK_RED + ECHO + ": " + ChatColor.GRAY + lastWords(Boss.PYRARCH));
        w.playSound(at, Sound.ENTITY_WITHER_DEATH, 1f, 0.5f);
        w.spawnParticle(Particle.END_ROD, at.clone().add(0, 1, 0), 120, 1, 1.5, 1, 0.05);
        for (Player p : by) {
            reward(p, Boss.PYRARCH);   // once per player, like the Pyrarch's own
            if (plugin.guide() != null) plugin.guide().victory(p);
        }
        fallen++;
        plugin.getLogger().info("ATLAS_ECHO_FALLEN by=" + (by.isEmpty() ? "nobody" : String.join(",", names(by))) + " participants=" + by.size());
    }

    /** With the Forgemaster gone, his trolls stop and crust over into slag-stone; Grunnak with them. */
    private void stoneTheTrolls(World w) {
        int n = 0;
        for (Entity e : w.getEntities()) if ("troll".equals(Npcs.tagValue(e, Npcs.DOM_KIND))) {
            Location l = e.getLocation();
            e.remove();
            Block b = l.getBlock();
            if (b.isEmpty()) { b.setType(Material.MAGMA, false); if (b.getRelative(0, 1, 0).isEmpty()) b.getRelative(0, 1, 0).setTypeIdAndData(1, (byte) 5, false); }
            n++;
        }
        plugin.state().bossesFallen.putIfAbsent("grunnak", System.currentTimeMillis());
        turned += n;
        plugin.getLogger().info("ATLAS_TROLLS_STONED count=" + n);
    }

    /** A boss's reward, given once to each player (the claim is remembered). */
    private void reward(Player p, Boss b) {
        State.Player rec = plugin.state().player(p.getUniqueId(), p.getName());
        if (!rec.claimed.add("boss_" + b.id)) return;
        if (b.crowned()) {
            Talk.give(p, Items.reward(b.id));
            Talk.give(p, Items.crownShard(b.id.substring(0, 1).toUpperCase() + b.id.substring(1)));
            Talk.give(p, new ItemStack(Material.EMERALD, 10));
            plugin.reputation().add(p, 25, "you broke the crown of " + b.title.split(",")[0]);
        } else if (b == Boss.PYRARCH) {
            Talk.give(p, Items.reward("pyrarch"));
            Talk.give(p, new ItemStack(Material.EMERALD, 20));
            plugin.reputation().add(p, 40, "you rekindled the Star");
        } else {
            Talk.give(p, new ItemStack(Material.EMERALD, 6));
            plugin.reputation().add(p, 8, "you felled " + b.title);
        }
        plugin.getLogger().info("ATLAS_REWARD player=" + p.getName() + " reward=boss_" + b.id);
    }

    static String lastWords(Boss b) {
        switch (b) {
            case KALLIAS: return "(He lowers his spear.) \"I will not lift my spear against the unarmed...\" (He looks west.) \"Is the shield still on the wall?\"";
            case MELAINA: return "\"Let the lamp go out... gently. Yes. Gently.\" (She closes her eyes.) \"Ianthe. I'm sorry I kept you waiting.\"";
            case DAIDAROS: return "\"Machines are for sparing hands. I spared none.\" (The Engine's hum dies with him.) \"Tell Ktesias... no. Let him build something.\"";
            case KELEOS: return "\"No law shall bind the tongue... I wrote that. I believed it.\" (He looks up at last.) \"Speak, then. All of you. Speak.\"";
            case PYRARCH: return "\"It was only ever... one note...\" (The Cinder Heart falls silent. For the first time in three hundred years you hear the plain: wind, and far off, birds.)";
            case GORVASH: return "(The drum stops.)";
            case CROWNLESS: return "\"Every name... I kept every name...\"";
            case SALLOW: return "\"Who will keep them now? Who will keep them?\"";
            case GRUNNAK: return "(The troll crashes down and does not rise.)";
            case FIEND: return "(The fire in the pit goes out with a sound like a sigh.)";
            case KALCHAS: return "\"I only wrote down what they said. I only wrote it down.\"";
            default: return "(The one note the Choir was singing falters.)";
        }
    }

    /** For status: which bosses are risen now. */
    String status() {
        StringBuilder b = new StringBuilder("bosses: fallen " + plugin.state().bossesFallen.keySet() + ", risen " );
        List<String> r = new ArrayList<>();
        for (Fight f : fights.values()) r.add(f.boss.id + (f.boss == Boss.PYRARCH ? "(phase " + f.phase + (f.heartBroken ? ", heart broken" : "") + ")" : ""));
        return b.append(r).toString();
    }
}
