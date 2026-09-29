package chat.jaspr.nether;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Color;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.craftbukkit.v1_12_R1.entity.CraftEntity;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Bat;
import org.bukkit.entity.Creature;
import org.bukkit.entity.Enderman;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Fireball;
import org.bukkit.entity.IronGolem;
import org.bukkit.entity.LargeFireball;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.MagmaCube;
import org.bukkit.entity.PigZombie;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.SmallFireball;
import org.bukkit.entity.WitherSkull;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.EntityTargetLivingEntityEvent;
import org.bukkit.event.entity.ExplosionPrimeEvent;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

/**
 * The ten Nether Lords (owner, 2026-09-29: "I want bosses that you have to conquer ... you have to conquer some of
 * these builds to beat the Nether especially in regard to the new bosses"). Each rules one of the GLM strongholds; it
 * rises when a player comes within 32 blocks of its arena (a registry point written by generation), fights with its
 * own abilities in three phases (minions at 66 % and 33 %), keeps to its arena, and rests fifteen minutes after it
 * falls. Everyone who hurt it, or stood within 64 blocks when it fell, conquers it: the tag jn_lord_&lt;id&gt; (three
 * different Lords open the Urn of Sorrow, see {@link Boss}) and the Lord's sigil. The Lord drops its hoard.
 * <ul>
 *   <li>Deathwing (ender dragon) and Ignareth the Magma Wyrm (ender dragon): kept in the hover phase and flown by the
 *       plugin around their lairs; fire volleys, dives and burning rain;</li>
 *   <li>the Pit Lord (giant): walked by the plugin; blows, ground slams and fireballs;</li>
 *   <li>the Ashen Wither (wither): its own skulls, withering ash, ashen knights;</li>
 *   <li>the Cursed King (wither skeleton): blinks behind his foes, curses, calls soul wraiths;</li>
 *   <li>the Dread Sorcerer (evoker): fangs and vexes, fire volleys, blinks away from blades;</li>
 *   <li>the Voidborn (enderman): pulls its foes in, strikes from behind, lifts them off the ground;</li>
 *   <li>the Bone Colossus (golem): bone storms, slams, ashbone archers;</li>
 *   <li>the Crimson Tyrant (giant magma cube): crushing landings, rings of fire, magma hulks;</li>
 *   <li>the Blood Count (zombie pigman lord): drinks the life he takes, blood bats, mist form, ghoul thralls.</li>
 * </ul>
 * No Lord damages blocks: their explosions, skulls, fireballs and bodies leave the strongholds standing.
 */
final class Lords implements Listener {
    static final class Def {
        final String id, name; final EntityType base; final double hp, dmg, speed, armor; final boolean flies; final String minion;
        final BarColor colour; final String relic;
        Def(String id, String name, EntityType base, double hp, double dmg, double speed, double armor, boolean flies, String minion, BarColor colour, String relic) {
            this.id = id; this.name = name; this.base = base; this.hp = hp; this.dmg = dmg; this.speed = speed; this.armor = armor; this.flies = flies;
            this.minion = minion; this.colour = colour; this.relic = relic;
        }
    }

    static final Map<String, Def> DEFS = new LinkedHashMap<>();
    private static void def(Def d) { DEFS.put(d.id, d); }
    static {
        def(new Def("deathwing", "Deathwing", EntityType.ENDER_DRAGON, 260, 12, 0, 0, true, "hellhound", BarColor.RED, "deathwing_talon"));
        def(new Def("ignareth", "Ignareth the Magma Wyrm", EntityType.ENDER_DRAGON, 240, 12, 0, 0, true, "magma_hulk", BarColor.YELLOW, "wyrmfire_bow"));
        def(new Def("pit_lord", "The Pit Lord", EntityType.GIANT, 300, 14, 0.25, 8, false, "cinder_imp", BarColor.RED, "pit_lord_cleaver"));
        def(new Def("ashen_wither", "The Ashen Wither", EntityType.WITHER, 340, 10, 0, 4, true, "infernal_knight", BarColor.WHITE, "ashen_crown"));
        def(new Def("cursed_king", "The Cursed King", EntityType.WITHER_SKELETON, 240, 12, 0.3, 10, false, "soul_wraith", BarColor.PURPLE, "cursed_katana"));
        def(new Def("dread_sorcerer", "The Dread Sorcerer", EntityType.EVOKER, 210, 6, 0.5, 6, false, "pyre_warden", BarColor.PURPLE, "dread_staff"));
        def(new Def("voidborn", "The Voidborn", EntityType.ENDERMAN, 240, 12, 0.32, 6, false, "shade", BarColor.PURPLE, "voidstep_boots"));
        def(new Def("bone_colossus", "The Bone Colossus", EntityType.IRON_GOLEM, 320, 16, 0.25, 10, false, "ashbone_archer", BarColor.WHITE, "colossus_maul"));
        def(new Def("crimson_tyrant", "The Crimson Tyrant", EntityType.MAGMA_CUBE, 340, 14, 0.3, 6, false, "magma_hulk", BarColor.RED, "tyrant_heart"));
        def(new Def("blood_count", "The Blood Count", EntityType.PIG_ZOMBIE, 240, 11, 0.32, 8, false, "charred_ghoul", BarColor.RED, "bloodfang_dagger"));
    }

    static boolean isLord(String kind) { return kind != null && DEFS.containsKey(kind); }

    static void specs(java.util.function.Consumer<Mobs.Spec> spec) {
        for (Def d : DEFS.values())
            spec.accept(new Mobs.Spec(d.id, d.name, d.base, d.hp, d.dmg, d.speed, 64, d.armor, 1.0, true, true, true, true, false, null));
    }

    private static ItemStack dyed(Material m, Color c) {
        ItemStack s = new ItemStack(m);
        LeatherArmorMeta meta = (LeatherArmorMeta) s.getItemMeta();
        meta.setColor(c);
        s.setItemMeta(meta);
        return s;
    }

    /** Looks, once when a Lord is spawned (before its attributes are set). */
    static void configure(Mobs m, LivingEntity e, Mobs.Spec s) {
        e.addScoreboardTag("jn_lord");
        e.addScoreboardTag("jaspr_boss");
        e.setRemoveWhenFarAway(false);
        EntityEquipment q = e.getEquipment();
        switch (s.kind) {
            case "pit_lord":
                q.setItemInMainHand(new ItemStack(Material.GOLD_AXE));
                q.setHelmet(dyed(Material.LEATHER_HELMET, Color.fromRGB(0x5A0A06)));
                q.setChestplate(dyed(Material.LEATHER_CHESTPLATE, Color.fromRGB(0x2A0804)));
                q.setLeggings(dyed(Material.LEATHER_LEGGINGS, Color.fromRGB(0x5A0A06)));
                q.setBoots(dyed(Material.LEATHER_BOOTS, Color.fromRGB(0x1A1A1A)));
                break;
            case "cursed_king":
                q.setItemInMainHand(new ItemStack(Material.DIAMOND_SWORD));
                q.setHelmet(new ItemStack(Material.GOLD_HELMET));
                q.setChestplate(dyed(Material.LEATHER_CHESTPLATE, Color.fromRGB(0x1B0F2A)));
                q.setLeggings(dyed(Material.LEATHER_LEGGINGS, Color.fromRGB(0x1B0F2A)));
                break;
            case "blood_count": {
                PigZombie p = (PigZombie) e;
                p.setBaby(false); p.setAngry(true); p.setAnger(Integer.MAX_VALUE / 2);
                q.setItemInMainHand(new ItemStack(Material.GOLD_SWORD));
                q.setHelmet(dyed(Material.LEATHER_HELMET, Color.fromRGB(0x16060A)));
                q.setChestplate(dyed(Material.LEATHER_CHESTPLATE, Color.fromRGB(0x7A0A14)));
                q.setLeggings(dyed(Material.LEATHER_LEGGINGS, Color.fromRGB(0x16060A)));
                q.setBoots(dyed(Material.LEATHER_BOOTS, Color.fromRGB(0x16060A)));
                break;
            }
            case "crimson_tyrant": ((MagmaCube) e).setSize(10); break;
            case "bone_colossus": ((IronGolem) e).setPlayerCreated(false); break;
            case "voidborn": ((Enderman) e).setCarriedMaterial(new org.bukkit.material.MaterialData(Material.OBSIDIAN)); break;
            default:
        }
        if (q != null) {
            q.setItemInMainHandDropChance(0f); q.setItemInOffHandDropChance(0f); q.setHelmetDropChance(0f);
            q.setChestplateDropChance(0f); q.setLeggingsDropChance(0f); q.setBootsDropChance(0f);
        }
    }

    // ---- fights ---------------------------------------------------------------------------------------------------------
    static final class Fight {
        final LivingEntity e; final Def d; final BossBar bar; final int hx, hy, hz; final String point;
        final Set<UUID> fought = new HashSet<>();
        final Map<String, Long> cd = new HashMap<>();
        long lastSeen, swoopUntil; int phase; boolean airborne; double orbit, lastX, lastZ;
        Location goal;          // where a dragon is flying to (the plugin flies it: the hover phase never moves by itself)
        final List<Entity> bats = new ArrayList<>();
        Fight(LivingEntity e, Def d, BossBar bar, int hx, int hy, int hz) {
            this.e = e; this.d = d; this.bar = bar; this.hx = hx; this.hy = hy; this.hz = hz; this.point = hx + "_" + hy + "_" + hz;
        }
        Location home(World w) { return new Location(w, hx + 0.5, hy, hz + 0.5); }
    }

    static final int NEAR = 32, REST_TICKS = 20 * 60 * 15, NEEDED = 3;
    static final long ALONE_TICKS = 20 * 60;

    private final NetherPlugin plugin;
    private final Map<UUID, Fight> fights = new LinkedHashMap<>();
    private final Map<String, Long> rest = new HashMap<>();
    private final Random random = new Random();
    private long now;
    int risen, slain, abilities;
    final Map<String, Integer> slainBy = new java.util.TreeMap<>();

    Lords(NetherPlugin plugin) { this.plugin = plugin; }

    int active() { return fights.size(); }
    String describe() { return "lords=" + fights.size() + " risen=" + risen + " slain=" + slain + " abilities=" + abilities; }
    Iterable<Fight> all() { return fights.values(); }

    /** The Lords a player has conquered (from the jn_lord_* tags). */
    static Set<String> conquered(Player p) {
        Set<String> out = new TreeSet<>();
        for (String t : p.getScoreboardTags()) if (t.startsWith("jn_lord_") && DEFS.containsKey(t.substring(8))) out.add(t.substring(8));
        return out;
    }

    void tick(long ticks) {
        now = ticks;
        if (plugin.nether == null) return;
        if ((ticks % 20) == 0) rouse();
        if (fights.isEmpty()) return;
        steer();
        if ((ticks % 2) != 0) return;
        Iterator<Fight> it = fights.values().iterator();
        while (it.hasNext()) {
            Fight f = it.next();
            if (!f.e.isValid() || f.e.isDead()) {
                f.bar.removeAll();
                for (Entity b : f.bats) b.remove();
                it.remove();
                continue;
            }
            try { fight(f); } catch (RuntimeException ex) {
                if (plugin.warnings++ < 40) plugin.getLogger().warning("NETHER_LORD_AI_FAILED lord=" + f.d.id + " reason=" + ex.getClass().getSimpleName() + " at=" + NetherPlugin.where(ex));
            }
        }
    }

    /** A Lord rises when a player comes near its arena and it is not resting. */
    private void rouse() {
        World w = plugin.nether;
        if (plugin.registry == null) return;
        for (Player p : w.getPlayers()) {
            if (p.isDead() || p.getGameMode() == GameMode.SPECTATOR || p.getGameMode() == GameMode.CREATIVE) continue;
            Location l = p.getLocation();
            for (Registry.Entry e : plugin.registry.near(l.getBlockX(), l.getBlockZ(), NEAR, "lord")) {
                Def d = DEFS.get(e.name);
                if (d == null || Math.abs(e.y1 - l.getBlockY()) > 28) continue;
                double dx = e.x1 + 0.5 - l.getX(), dz = e.z1 + 0.5 - l.getZ();
                if (dx * dx + dz * dz > NEAR * NEAR) continue;
                String key = e.x1 + "_" + e.y1 + "_" + e.z1;
                Long until = rest.get(key);
                if (until != null && now < until) continue;
                boolean up = false;
                for (Fight f : fights.values()) if (f.point.equals(key)) up = true;
                if (up) continue;
                if (!w.isChunkLoaded(e.x1 >> 4, e.z1 >> 4)) continue;
                rise(w, d, e.x1, e.y1, e.z1);
            }
        }
    }

    LivingEntity rise(World w, Def d, int x, int y, int z) {
        Location at = new Location(w, x + 0.5, y, z + 0.5);
        LivingEntity e = plugin.mobs.spawn(d.id, at, false);
        if (e == null) return null;
        e.addScoreboardTag("jn_lordpt_" + x + "_" + y + "_" + z);
        e.setCustomName(ChatColor.DARK_RED + d.name);
        e.setCustomNameVisible(true);
        Fight f = register(e, d, x, y, z);
        risen++;
        rest.put(f.point, now + 200);        // no second rising while this one stands
        w.playSound(at, d.base == EntityType.ENDER_DRAGON ? Sound.ENTITY_ENDERDRAGON_GROWL : Sound.ENTITY_WITHER_SPAWN, 3f, 0.7f);
        w.spawnParticle(Particle.LAVA, at, 60, 2, 2, 2, 0);
        for (Player p : w.getPlayers()) if (p.getLocation().distanceSquared(at) < 64 * 64)
            p.sendTitle(ChatColor.DARK_RED + d.name, ChatColor.GOLD + "a Nether Lord rises", 10, 60, 20);
        plugin.getLogger().info("NETHER_LORD_RISEN lord=" + d.id + " at=" + x + "," + y + "," + z + " hp=" + (int) e.getMaxHealth());
        return e;
    }

    private Fight register(LivingEntity e, Def d, int x, int y, int z) {
        BossBar bar = Bukkit.createBossBar(d.name, d.colour, BarStyle.SEGMENTED_10);
        Fight f = new Fight(e, d, bar, x, y, z);
        f.lastSeen = now;
        for (String t : e.getScoreboardTags()) if (t.startsWith("jn_lphase_")) f.phase = Integer.parseInt(t.substring(10));
        fights.put(e.getUniqueId(), f);
        if (d.base == EntityType.ENDER_DRAGON) hover(f, e.getLocation());
        return f;
    }

    /** A Lord found again after a restart or a chunk load. */
    void adopt(LivingEntity e) {
        if (fights.containsKey(e.getUniqueId())) return;
        String kind = plugin.mobs.kind(e);
        Def d = DEFS.get(kind);
        if (d == null) return;
        int x = e.getLocation().getBlockX(), y = e.getLocation().getBlockY(), z = e.getLocation().getBlockZ();
        for (String t : e.getScoreboardTags()) if (t.startsWith("jn_lordpt_")) {
            String[] p = t.substring(10).split("_");
            try { x = Integer.parseInt(p[0]); y = Integer.parseInt(p[1]); z = Integer.parseInt(p[2]); } catch (RuntimeException ignored) { }
        }
        register(e, d, x, y, z);
    }

    private void fight(Fight f) {
        LivingEntity e = f.e;
        World w = e.getWorld();
        Location home = f.home(w);
        Player target = null;
        double best = 56 * 56;
        boolean anyone = false;
        for (Player p : w.getPlayers()) {
            if (p.isDead() || p.getGameMode() == GameMode.SPECTATOR || p.getGameMode() == GameMode.CREATIVE) continue;
            double d = p.getLocation().distanceSquared(e.getLocation());
            if (d < 96 * 96) anyone = true;
            if (d < best) { best = d; target = p; }
        }
        if (anyone) f.lastSeen = now;
        else if (now - f.lastSeen > ALONE_TICKS) {    // nobody left to fight: it sinks back into its hall, whole again
            plugin.getLogger().info("NETHER_LORD_RESET lord=" + f.d.id + " at=" + f.point);
            e.remove();
            rest.remove(f.point);
            return;
        }
        if ((now % 10) == 0) {
            f.bar.setProgress(Math.max(0, Math.min(1, e.getHealth() / e.getMaxHealth())));
            if (f.d.base != EntityType.WITHER)
                for (Player p : w.getPlayers()) {
                    boolean near = p.getLocation().distanceSquared(e.getLocation()) < 80 * 80;
                    if (near && !f.bar.getPlayers().contains(p)) f.bar.addPlayer(p);
                    else if (!near && f.bar.getPlayers().contains(p)) f.bar.removePlayer(p);
                }
        }
        // keep to the arena
        double leash = f.d.flies ? 70 : 40;
        if (f.d.base != EntityType.ENDER_DRAGON && (Math.abs(e.getLocation().getX() - home.getX()) > leash || Math.abs(e.getLocation().getZ() - home.getZ()) > leash
            || Math.abs(e.getLocation().getY() - home.getY()) > 30)) {
            e.teleport(home);
            abilities++;
        }
        if (target != null && e instanceof Creature && f.d.base != EntityType.WITHER) {
            LivingEntity cur = ((Creature) e).getTarget();
            if (cur == null || cur.isDead() || !(cur instanceof Player)) ((Creature) e).setTarget(target);
        }
        // phases: minions at two thirds and one third
        double frac = e.getHealth() / e.getMaxHealth();
        int phase = frac < 1 / 3.0 ? 2 : frac < 2 / 3.0 ? 1 : 0;
        if (phase > f.phase) {
            e.removeScoreboardTag("jn_lphase_" + f.phase);
            f.phase = phase;
            e.addScoreboardTag("jn_lphase_" + phase);
            summon(f, f.d.minion, f.d.base == EntityType.ENDER_DRAGON ? 3 : 2, target);
            w.playSound(e.getLocation(), f.d.base == EntityType.ENDER_DRAGON ? Sound.ENTITY_ENDERDRAGON_GROWL : Sound.ENTITY_WITHER_AMBIENT, 3f, 0.6f);
            plugin.getLogger().info("NETHER_LORD_PHASE lord=" + f.d.id + " phase=" + phase + " hp=" + (int) e.getHealth());
            if (f.d.id.equals("blood_count")) mist(f, target);
        }
        double speedUp = 1 - 0.2 * f.phase;
        switch (f.d.id) {
            case "deathwing": dragon(f, target, speedUp, false); break;
            case "ignareth": dragon(f, target, speedUp, true); break;
            case "pit_lord": giant(f, target, speedUp); break;
            case "ashen_wither":
                if (target != null && ready(f, "ash", (int) (200 * speedUp))) curse(f, 10, new PotionEffect(PotionEffectType.WITHER, 80, 0, false, true), Particle.SMOKE_LARGE);
                break;
            case "cursed_king":
                if (target != null && best < 24 * 24 && ready(f, "blink", (int) (140 * speedUp))) blink(f, target);
                if (target != null && ready(f, "curse", (int) (110 * speedUp)))
                    curse(f, 8, new PotionEffect(PotionEffectType.WITHER, 60, 0, false, true), Particle.SPELL_WITCH);
                if (f.phase >= 2 && ready(f, "nova", 160)) nova(f, 6, 8, 100, Particle.FLAME);
                if (ready(f, "wraiths", 400) && count(f, "soul_wraith") < 4) summon(f, "soul_wraith", 2, target);
                break;
            case "dread_sorcerer":
                if (target != null && best < 30 * 30 && ready(f, "volley", (int) (80 * speedUp))) volley(f, target, 3, 0.12, false);
                if (target != null && best < 9 && ready(f, "blink", 60)) blinkAway(f, target);
                if (target != null && ready(f, "nova", (int) (180 * speedUp))) nova(f, 6, 6, 80, Particle.FLAME);
                break;
            case "voidborn":
                if (target != null && best < 16 * 16 && ready(f, "pull", (int) (150 * speedUp))) pull(f, 14, 0.9);
                if (target != null && best > 9 && best < 24 * 24 && ready(f, "blink", (int) (100 * speedUp))) { blink(f, target); plugin.mobs.hurt(target, e, 8 * plugin.mobs.dmgMult); }
                if (target != null && best < 36 && ready(f, "lift", (int) (220 * speedUp))) curse(f, 6, new PotionEffect(PotionEffectType.LEVITATION, 40, 0, false, true), Particle.PORTAL);
                break;
            case "bone_colossus":
                if (target != null && best < 24 * 24 && ready(f, "storm", (int) (150 * speedUp))) boneStorm(f);
                if (target != null && best < 36 && ready(f, "slam", (int) (180 * speedUp))) slam(f, 6, 10, 0.7);
                break;
            case "crimson_tyrant": {
                boolean ground = e.isOnGround();
                if (f.airborne && ground && target != null) slam(f, 7, 10, 0.6);
                f.airborne = !ground;
                if (target != null && ready(f, "ring", (int) (160 * speedUp))) nova(f, 8, 6, 120, Particle.LAVA);
                break;
            }
            case "blood_count":
                if (target != null && ready(f, "bats", (int) (200 * speedUp))) bats(f, target);
                biteBats(f);
                break;
            default:
        }
    }

    private boolean ready(Fight f, String key, int cooldown) {
        Long t = f.cd.get(key);
        if (t != null && now < t) return false;
        f.cd.put(key, now + Math.max(20, cooldown));
        if (t == null) return false;        // first sighting: wait one cooldown before the first use
        abilities++;
        plugin.mobs.ability(f.d.id + "_" + key);
        return true;
    }

    private static boolean victim(Entity n) {
        return n instanceof Player && !n.isDead() && ((Player) n).getGameMode() != GameMode.CREATIVE && ((Player) n).getGameMode() != GameMode.SPECTATOR;
    }

    private int count(Fight f, String kind) {
        int n = 0;
        for (Entity o : f.e.getNearbyEntities(24, 12, 24)) { Mobs.T t = plugin.mobs.track(o); if (t != null && t.spec.kind.equals(kind)) n++; }
        return n;
    }

    // ---- abilities ------------------------------------------------------------------------------------------------------
    private void summon(Fight f, String kind, int n, Player target) {
        if (kind == null) return;
        int minions = 0;
        for (Entity o : f.e.getNearbyEntities(32, 16, 32)) if (o.getScoreboardTags().contains("jn_minion")) minions++;
        Location c = f.d.flies ? f.home(f.e.getWorld()) : f.e.getLocation();
        for (int i = 0; i < n && minions < 8; i++) {
            Location at = free(c, 5);
            if (at == null) continue;
            LivingEntity m = plugin.mobs.spawn(kind, at, i == 0 && f.phase >= 2);
            if (m == null) continue;
            m.addScoreboardTag("jn_minion");
            if (m instanceof Creature && target != null) ((Creature) m).setTarget(target);
            at.getWorld().spawnParticle(Particle.FLAME, at.clone().add(0, 1, 0), 20, 0.3, 0.8, 0.3, 0.02);
            minions++;
        }
    }

    /** Two blocks of air on something solid near c, or null. */
    private Location free(Location c, int r) {
        World w = c.getWorld();
        for (int k = 0; k < 24; k++) {
            int x = c.getBlockX() + random.nextInt(2 * r + 1) - r, z = c.getBlockZ() + random.nextInt(2 * r + 1) - r;
            for (int dy = 3; dy >= -4; dy--) {
                int y = c.getBlockY() + dy;
                if (w.getBlockAt(x, y, z).getType() == Material.AIR && w.getBlockAt(x, y + 1, z).getType() == Material.AIR
                    && w.getBlockAt(x, y - 1, z).getType().isSolid()) return new Location(w, x + 0.5, y, z + 0.5);
            }
        }
        return null;
    }

    private void tag(Projectile p) { p.setMetadata("jn_lord", new FixedMetadataValue(plugin, Boolean.TRUE)); }

    private void volley(Fight f, Player target, int n, double spread, boolean large) {
        Location from = f.e.getEyeLocation();
        if (f.d.base == EntityType.ENDER_DRAGON) from = f.e.getLocation().add(0, 2, 0);
        Vector dir = target.getEyeLocation().toVector().subtract(from.toVector()).normalize();
        for (int i = 0; i < n; i++) {
            Vector d = Mobs.rotY(dir, (i - (n - 1) / 2.0) * spread);
            Class<? extends Fireball> type = large ? LargeFireball.class : SmallFireball.class;
            Fireball b = from.getWorld().spawn(from.clone().add(d.clone().multiply(3)), type);
            b.setShooter(f.e);
            b.setDirection(d);
            if (large) { ((LargeFireball) b).setYield(1.5f); b.setIsIncendiary(false); }
            tag(b);
        }
        from.getWorld().playSound(from, Sound.ENTITY_GHAST_SHOOT, 2f, 0.6f);
    }

    /** Burning rain: small fireballs falling around the target. */
    private void rain(Fight f, Player target, int n) {
        World w = target.getWorld();
        for (int i = 0; i < n; i++) {
            Location at = target.getLocation().add(random.nextInt(11) - 5, 14, random.nextInt(11) - 5);
            while (at.getBlockY() > target.getLocation().getBlockY() + 3 && at.getBlock().getType() != Material.AIR) at.add(0, -1, 0);
            SmallFireball b = w.spawn(at, SmallFireball.class);
            b.setShooter(f.e);
            b.setDirection(new Vector(0, -1, 0));
            tag(b);
        }
        w.playSound(target.getLocation(), Sound.ENTITY_BLAZE_SHOOT, 1.5f, 0.5f);
    }

    private void nova(Fight f, double r, double dmg, int fire, Particle particle) {
        Location c = f.e.getLocation();
        for (Entity n : f.e.getNearbyEntities(r, r / 2 + 2, r)) {
            if (!victim(n)) continue;
            plugin.mobs.hurt((Player) n, f.e, dmg * plugin.mobs.dmgMult);
            if (fire > 0) ((Player) n).setFireTicks(Math.max(((Player) n).getFireTicks(), fire));
        }
        for (int i = 0; i < 36; i++) {
            double a = i * Math.PI / 18;
            c.getWorld().spawnParticle(particle, c.getX() + Math.cos(a) * r * 0.8, c.getY() + 0.5, c.getZ() + Math.sin(a) * r * 0.8, 2, 0.2, 0.2, 0.2, 0.01);
        }
        c.getWorld().playSound(c, Sound.ENTITY_BLAZE_SHOOT, 2f, 0.4f);
    }

    private void slam(Fight f, double r, double dmg, double up) {
        Location c = f.e.getLocation();
        for (Entity n : f.e.getNearbyEntities(r, 4, r)) {
            if (!victim(n)) continue;
            plugin.mobs.hurt((Player) n, f.e, dmg * plugin.mobs.dmgMult);
            Vector v = n.getLocation().toVector().subtract(c.toVector()).setY(0);
            if (v.lengthSquared() < 1e-4) v = new Vector(1, 0, 0);
            n.setVelocity(v.normalize().multiply(1.2).setY(up));
        }
        c.getWorld().spawnParticle(Particle.EXPLOSION_LARGE, c, 6, r / 2, 0.3, r / 2, 0);
        c.getWorld().playSound(c, Sound.ENTITY_GENERIC_EXPLODE, 2f, 0.5f);
    }

    private void curse(Fight f, double r, PotionEffect effect, Particle particle) {
        for (Entity n : f.e.getNearbyEntities(r, r, r)) if (victim(n)) ((Player) n).addPotionEffect(effect, true);
        f.e.getWorld().spawnParticle(particle, f.e.getLocation().add(0, 1, 0), 80, r / 2, 1, r / 2, 0.05);
        f.e.getWorld().playSound(f.e.getLocation(), Sound.ENTITY_WITHER_AMBIENT, 1.5f, 1.4f);
    }

    private void pull(Fight f, double r, double strength) {
        Location c = f.e.getLocation();
        for (Entity n : f.e.getNearbyEntities(r, r / 2, r)) {
            if (!victim(n)) continue;
            Vector v = c.toVector().subtract(n.getLocation().toVector());
            double d = v.length();
            if (d < 2) continue;
            n.setVelocity(v.normalize().multiply(strength).setY(0.3));
        }
        c.getWorld().spawnParticle(Particle.PORTAL, c.add(0, 1.5, 0), 120, r / 3, 1.5, r / 3, 1.2);
        c.getWorld().playSound(c, Sound.ENTITY_ENDERMEN_SCREAM, 2f, 0.5f);
    }

    private void blink(Fight f, Player target) {
        Location t = target.getLocation();
        Vector back = t.getDirection().setY(0);
        if (back.lengthSquared() < 1e-4) back = new Vector(1, 0, 0);
        Location at = t.clone().add(back.normalize().multiply(-2));
        if (!at.getBlock().getType().isSolid() && !at.clone().add(0, 1, 0).getBlock().getType().isSolid()) {
            f.e.getWorld().spawnParticle(Particle.PORTAL, f.e.getLocation().add(0, 1, 0), 40, 0.5, 1, 0.5, 0.6);
            at.setDirection(t.toVector().subtract(at.toVector()));
            f.e.teleport(at);
            f.e.getWorld().playSound(at, Sound.ENTITY_ENDERMEN_TELEPORT, 1.5f, 0.5f);
        }
    }

    private void blinkAway(Fight f, Player from) {
        Location c = f.home(f.e.getWorld());
        Location at = free(c, 10);
        if (at == null) return;
        f.e.getWorld().spawnParticle(Particle.SPELL_WITCH, f.e.getLocation().add(0, 1, 0), 40, 0.5, 1, 0.5, 0.2);
        f.e.teleport(at);
    }

    private void boneStorm(Fight f) {
        Location c = f.e.getLocation().add(0, 1.6, 0);
        for (int i = 0; i < 16; i++) {
            double a = i * Math.PI / 8;
            Vector d = new Vector(Math.cos(a), 0.05, Math.sin(a));
            Arrow ar = c.getWorld().spawnArrow(c.clone().add(d.clone().multiply(1.8)), d, 1.4f, 4f);
            ar.setShooter(f.e);
            ar.setPickupStatus(Arrow.PickupStatus.DISALLOWED);
            tag(ar);
        }
        c.getWorld().playSound(c, Sound.ENTITY_SKELETON_SHOOT, 2f, 0.5f);
    }

    private void bats(Fight f, Player target) {
        World w = target.getWorld();
        f.bats.removeIf(b -> !b.isValid());
        for (int i = 0; i < 4 && f.bats.size() < 8; i++) {
            Location at = target.getLocation().add(random.nextInt(5) - 2, 2, random.nextInt(5) - 2);
            if (at.getBlock().getType() != Material.AIR) at = target.getLocation().add(0, 1.5, 0);
            Bat b = w.spawn(at, Bat.class);
            b.addScoreboardTag("jn_bloodbat");
            b.setCustomName(ChatColor.DARK_RED + "Blood Bat");
            f.bats.add(b);
        }
        w.playSound(target.getLocation(), Sound.ENTITY_BAT_TAKEOFF, 2f, 0.6f);
    }

    /** Blood bats bite whoever they flutter against, and fade after half a minute. */
    private void biteBats(Fight f) {
        if ((now % 20) != 0) return;
        Iterator<Entity> it = f.bats.iterator();
        while (it.hasNext()) {
            Entity b = it.next();
            if (!b.isValid() || b.getTicksLived() > 600) { b.remove(); it.remove(); continue; }
            for (Entity n : b.getNearbyEntities(1.2, 1.2, 1.2)) if (victim(n)) {
                plugin.mobs.hurt((Player) n, f.e, 2 * plugin.mobs.dmgMult);
                if (f.e.isValid()) f.e.setHealth(Math.min(f.e.getMaxHealth(), f.e.getHealth() + 2));
            }
        }
    }

    private void mist(Fight f, Player target) {
        f.e.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, 80, 0, false, false), true);
        f.e.getWorld().spawnParticle(Particle.REDSTONE, f.e.getLocation().add(0, 1, 0), 80, 1, 1, 1, 0);
        if (target != null) blink(f, target);
    }

    // ---- the Pit Lord walks, the dragons fly ------------------------------------------------------------------------------
    private void giant(Fight f, Player target, double speedUp) {
        LivingEntity e = f.e;
        if (target == null) return;
        Location l = e.getLocation(), t = target.getLocation();
        Vector to = t.toVector().subtract(l.toVector()).setY(0);
        double d = to.length();
        if (d > 0.1) {
            float yaw = (float) Math.toDegrees(Math.atan2(-to.getX(), to.getZ()));
            net.minecraft.server.v1_12_R1.Entity h = ((CraftEntity) e).getHandle();
            h.yaw = yaw; h.setHeadRotation(yaw);
            if (h instanceof net.minecraft.server.v1_12_R1.EntityLiving) ((net.minecraft.server.v1_12_R1.EntityLiving) h).aN = yaw;
        }
        if (d > 4.5 && d < 48 && e.isOnGround()) {
            boolean stuck = Math.abs(l.getX() - f.lastX) < 0.03 && Math.abs(l.getZ() - f.lastZ) < 0.03;
            Vector v = to.normalize().multiply(0.16);
            v.setY(stuck ? 0.55 : e.getVelocity().getY());      // a one-block step: hop it
            e.setVelocity(v);
        }
        f.lastX = l.getX(); f.lastZ = l.getZ();
        if (d <= 5.5 && ready(f, "blow", 26)) {
            plugin.mobs.hurt(target, e, f.d.dmg * plugin.mobs.dmgMult);
            target.setVelocity(to.normalize().multiply(0.9).setY(0.5));
            e.getWorld().playSound(l, Sound.ENTITY_IRONGOLEM_ATTACK, 2f, 0.5f);
        }
        if (d < 9 && ready(f, "slam", (int) (140 * speedUp))) slam(f, 8, 10, 0.8);
        if (d > 6 && d < 32 && ready(f, "hurl", (int) (100 * speedUp))) volley(f, target, 1, 0, true);
    }

    /** Where a dragon flies next. */
    private void hover(Fight f, Location to) { f.goal = to; }

    private static boolean steerBroken;

    /**
     * Flies the dragons, every tick. They are kept in the hover phase, which on this server never moves by itself (and
     * never follows the End's paths towards the world's origin, as the other phases would outside the End); the plugin
     * moves them smoothly towards their goal and turns them to face where they go. Their wings still knock back and
     * their heads still bite whoever they pass.
     */
    private void steer() {
        if (steerBroken) return;
        for (Fight f : fights.values()) {
            if (f.d.base != EntityType.ENDER_DRAGON || f.goal == null || !f.e.isValid()) continue;
            try {
                net.minecraft.server.v1_12_R1.Entity raw = ((CraftEntity) f.e).getHandle();
                if (!(raw instanceof net.minecraft.server.v1_12_R1.EntityEnderDragon)) continue;
                net.minecraft.server.v1_12_R1.EntityEnderDragon h = (net.minecraft.server.v1_12_R1.EntityEnderDragon) raw;
                net.minecraft.server.v1_12_R1.DragonControllerManager m = h.getDragonControllerManager();
                if (m.a().getControllerPhase() == net.minecraft.server.v1_12_R1.DragonControllerPhase.j || h.getHealth() <= 0) continue;   // dying
                if (m.a().getControllerPhase() != net.minecraft.server.v1_12_R1.DragonControllerPhase.k) m.setControllerPhase(net.minecraft.server.v1_12_R1.DragonControllerPhase.k);
                double dx = f.goal.getX() - h.locX, dy = f.goal.getY() - h.locY, dz = f.goal.getZ() - h.locZ;
                double d = Math.sqrt(dx * dx + dy * dy + dz * dz);
                double speed = Math.min(d, Math.min(1.1, 0.3 + 0.035 * d)) * (f.swoopUntil > now ? 1.5 : 1);
                double nx = h.locX, ny = h.locY, nz = h.locZ;
                if (d > 0.05) { nx += dx / d * speed; ny += dy / d * speed; nz += dz / d * speed; }
                float yaw = h.yaw;
                if (dx * dx + dz * dz > 0.25) {
                    float want = (float) (180 - Math.toDegrees(Math.atan2(dx, dz)));
                    float turn = want - yaw;
                    while (turn > 180) turn -= 360;
                    while (turn < -180) turn += 360;
                    yaw += Math.max(-7f, Math.min(7f, turn));
                }
                h.setPositionRotation(nx, ny, nz, yaw, 0);
                h.aN = yaw;
            } catch (RuntimeException ex) {
                steerBroken = true;
                plugin.getLogger().warning("NETHER_LORD_STEER_FAILED reason=" + ex.getClass().getSimpleName() + " at=" + NetherPlugin.where(ex));
            }
        }
    }

    private void dragon(Fight f, Player target, double speedUp, boolean magma) {
        World w = f.e.getWorld();
        Location home = f.home(w);
        if (f.swoopUntil > now && target != null) {
            hover(f, target.getLocation().add(0, 2, 0));
        } else if ((now % 20) == 0) {
            // circling its lair: the next open point along the circle (radius and height vary; the lair itself is skipped)
            Location p = null;
            for (int k = 0; k < 8 && p == null; k++) {
                f.orbit += 0.35 * (1 + f.phase * 0.3);
                double r = 8 + 14 * (0.5 + 0.5 * Math.sin(f.orbit * 0.37 + k));
                Location c = home.clone().add(Math.cos(f.orbit) * r, -3 + 8 * (0.5 + 0.5 * Math.sin(f.orbit * 0.5 + k * 0.7)), Math.sin(f.orbit) * r);
                if (c.getY() < 8 || c.getY() > 118) continue;
                if (open(c)) p = c;
            }
            if (p == null) p = home.clone().add(random.nextInt(7) - 3, random.nextInt(5) - 2, random.nextInt(7) - 3);
            hover(f, p);
        }
        if (target == null) return;
        double d = target.getLocation().distance(f.e.getLocation());
        if (d < 60 && ready(f, "volley", (int) (50 * speedUp))) volley(f, target, 3, 0.15, false);
        if (d < 60 && ready(f, "blast", (int) (160 * speedUp))) volley(f, target, 1, 0, true);
        if (d < 50 && ready(f, "swoop", (int) (240 * speedUp))) f.swoopUntil = now + 60;
        if (magma && d < 50 && ready(f, "rain", (int) (140 * speedUp))) rain(f, target, 8);
    }

    /** The dragon's controller phase and hover point (for /jnether lords). */
    String debug(Fight f) {
        try {
            net.minecraft.server.v1_12_R1.Entity h = ((CraftEntity) f.e).getHandle();
            if (!(h instanceof net.minecraft.server.v1_12_R1.EntityEnderDragon)) return "";
            net.minecraft.server.v1_12_R1.IDragonController c = ((net.minecraft.server.v1_12_R1.EntityEnderDragon) h).getDragonControllerManager().a();
            net.minecraft.server.v1_12_R1.Vec3D t = c.g();
            return "dragonPhase=" + c.getControllerPhase() + " target=" + (t == null ? "-" : (int) t.x + "," + (int) t.y + "," + (int) t.z)
                + " mot=" + String.format(java.util.Locale.ROOT, "%.2f,%.2f,%.2f", h.motX, h.motY, h.motZ) + " noclip=" + h.noclip + " lived=" + h.ticksLived + " valid=" + h.valid + " dead=" + h.dead;
        } catch (RuntimeException ex) { return "debug=" + ex.getClass().getSimpleName(); }
    }

    private static boolean open(Location p) {
        for (int dx = -3; dx <= 3; dx += 3) for (int dz = -3; dz <= 3; dz += 3) for (int dy = -2; dy <= 3; dy += 5)
            if (p.clone().add(dx, dy, dz).getBlock().getType().isSolid()) return false;
        return true;
    }

    // ---- hits, projectiles, loot ----------------------------------------------------------------------------------------
    void onHit(Mobs.T s, LivingEntity v) {
        switch (s.spec.kind) {
            case "blood_count": {
                double heal = s.spec.damage * plugin.mobs.dmgMult * 0.5;
                s.e.setHealth(Math.min(s.e.getMaxHealth(), s.e.getHealth() + heal));
                s.e.getWorld().spawnParticle(Particle.DAMAGE_INDICATOR, v.getLocation().add(0, 1, 0), 6, 0.3, 0.4, 0.3, 0.05);
                plugin.mobs.ability("blood_count_drink");
                break;
            }
            case "cursed_king": v.addPotionEffect(new PotionEffect(PotionEffectType.WITHER, 60, 1, false, true), true); break;
            case "pit_lord": case "crimson_tyrant": v.setFireTicks(Math.max(v.getFireTicks(), 100)); break;
            default:
        }
    }

    /** Lord projectiles never break blocks. True when handled. */
    boolean launch(Mobs.T t, Projectile p) {
        tag(p);
        if (p instanceof LargeFireball) { ((LargeFireball) p).setYield(1.5f); ((LargeFireball) p).setIsIncendiary(false); }
        return false;
    }

    /** The hoard, the credit and the sigils. */
    void loot(Mobs.T t, List<ItemStack> drops, EntityDeathEvent event) {
        Def d = DEFS.get(t.spec.kind);
        if (d == null) return;
        Fight f = fights.remove(t.e.getUniqueId());
        if (f != null) { f.bar.removeAll(); for (Entity b : f.bats) b.remove(); }
        slain++;
        slainBy.merge(d.id, 1, Integer::sum);
        String theme = "fortress";
        if (plugin.gen != null) { GlmSites.Entry en = plugin.gen.glm.lordEntry(d.id); if (en != null) theme = en.theme; }
        Random r = random;
        drops.addAll(Loot.roll("glm:vault:" + theme, r));
        drops.addAll(Loot.roll("glm:rich:" + theme, r));
        drops.add(Items.create("hellforged_shard", 4 + r.nextInt(5)));
        drops.add(Items.create("amethyst_crystal", 6 + r.nextInt(7)));
        drops.add(new ItemStack(Material.GOLD_INGOT, 3 + r.nextInt(5)));
        if (r.nextInt(100) < 50) drops.add(Items.create(d.relic, 1));
        event.setDroppedExp(d.base == EntityType.ENDER_DRAGON ? 0 : 250);
        credit(t.e, d, f);
        String key = f != null ? f.point : pointOf(t.e);
        if (key != null) rest.put(key, now + REST_TICKS);
    }

    private static String pointOf(LivingEntity e) {
        for (String tag : e.getScoreboardTags()) if (tag.startsWith("jn_lordpt_")) return tag.substring(10);
        return null;
    }

    private void credit(LivingEntity e, Def d, Fight f) {
        Location at = e.getLocation();
        int credited = 0;
        for (Player p : e.getWorld().getPlayers()) {
            boolean near = p.getGameMode() != GameMode.SPECTATOR && p.getLocation().distanceSquared(at) < 64 * 64;
            if (!near && (f == null || !f.fought.contains(p.getUniqueId()))) continue;
            boolean first = p.addScoreboardTag("jn_lord_" + d.id);
            int n = conquered(p).size();
            ItemStack sigil = Items.create("sigil_" + d.id, 1);
            if (!p.getInventory().addItem(sigil).isEmpty()) p.getWorld().dropItemNaturally(p.getLocation(), sigil);
            p.sendTitle(ChatColor.GOLD + "LORD CONQUERED", ChatColor.YELLOW + d.name, 10, 70, 20);
            p.sendMessage(ChatColor.GOLD + "You conquered " + d.name + "! " + ChatColor.YELLOW + "Nether Lords conquered: " + n + " of " + DEFS.size()
                + (n < NEEDED ? ChatColor.GRAY + " (" + (NEEDED - n) + " more before the Urn of Sorrow answers)" : ChatColor.GREEN + " -- the Urn of Sorrow will answer you"));
            p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f);
            credited++;
            plugin.getLogger().info("NETHER_LORD_CREDIT lord=" + d.id + " player=" + p.getUniqueId() + " first=" + first + " conquered=" + n);
        }
        plugin.getLogger().info("NETHER_LORD_DEFEATED lord=" + d.id + " at=" + at.getBlockX() + "," + at.getBlockY() + "," + at.getBlockZ() + " credited=" + credited);
    }

    // ---- no block damage, no silly deaths, no infighting ---------------------------------------------------------------
    private boolean ours(Entity e) {
        if (e == null) return false;
        if (e.hasMetadata("jn_lord")) return true;
        if (e instanceof Projectile && ((Projectile) e).getShooter() instanceof Entity) {
            Entity s = (Entity) ((Projectile) e).getShooter();
            return s.getScoreboardTags().contains("jn_lord");
        }
        return e.getScoreboardTags().contains("jn_lord");
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onExplode(EntityExplodeEvent e) {
        if (!ours(e.getEntity())) return;
        if (e.getEntity() instanceof org.bukkit.entity.EnderDragon) { e.setCancelled(true); return; }
        e.blockList().clear();
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPrime(ExplosionPrimeEvent e) {
        if (e.getEntity() instanceof WitherSkull && ours(e.getEntity())) e.setFire(false);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onChangeBlock(EntityChangeBlockEvent e) {
        if (e.getEntity().getScoreboardTags().contains("jn_lord") || (e.getEntity() instanceof Enderman && plugin.mobs.track(e.getEntity()) != null))
            e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onHurt(EntityDamageEvent e) {
        if (!e.getEntity().getScoreboardTags().contains("jn_lord")) return;
        switch (e.getCause()) {
            case SUFFOCATION: case FALL: case DROWNING: case FIRE: case FIRE_TICK: case LAVA: case HOT_FLOOR: case CRAMMING: case FLY_INTO_WALL:
                e.setCancelled(true); break;
            default:
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onFought(EntityDamageByEntityEvent e) {
        Fight f = fights.get(e.getEntity().getUniqueId());
        if (f == null) {
            // the dragon's body parts pass the blow to the dragon
            if (e.getEntity() instanceof org.bukkit.entity.ComplexEntityPart) f = fights.get(((org.bukkit.entity.ComplexEntityPart) e.getEntity()).getParent().getUniqueId());
            if (f == null) return;
        }
        Entity d = e.getDamager();
        if (d instanceof Projectile && ((Projectile) d).getShooter() instanceof Entity) d = (Entity) ((Projectile) d).getShooter();
        if (d instanceof Player) f.fought.add(d.getUniqueId());
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onTarget(EntityTargetLivingEntityEvent e) {
        // a Lord's minions never turn on their Lord, and the Lord keeps to players
        LivingEntity t = e.getTarget();
        if (t == null) return;
        boolean minion = e.getEntity().getScoreboardTags().contains("jn_minion") || e.getEntity().getScoreboardTags().contains("jn_lord");
        if (minion && (t.getScoreboardTags().contains("jn_lord") || t.getScoreboardTags().contains("jn_minion"))) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onMinionHurt(EntityDamageByEntityEvent e) {
        Entity v = e.getEntity(), d = e.getDamager();
        if (d instanceof Projectile && ((Projectile) d).getShooter() instanceof Entity) d = (Entity) ((Projectile) d).getShooter();
        if (d == null || d == v) return;
        boolean vs = v.getScoreboardTags().contains("jn_lord") || v.getScoreboardTags().contains("jn_minion");
        boolean ds = d.getScoreboardTags().contains("jn_lord") || d.getScoreboardTags().contains("jn_minion");
        if (vs && ds) e.setCancelled(true);
    }

    void shutdown() { for (Fight f : fights.values()) { f.bar.removeAll(); for (Entity b : f.bats) b.remove(); } }
}
