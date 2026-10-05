package chat.jaspr.ruins;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.block.Block;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.EvokerFangs;
import org.bukkit.entity.FallingBlock;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Creature;
import org.bukkit.entity.Player;
import org.bukkit.entity.Slime;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.SlimeSplitEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.material.MaterialData;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

/**
 * The Wardens and the Herald. A Warden wakes when a player comes within 18 blocks of its arena (and its cooldown has
 * passed), fights with a boss bar and three tricks, and drops its Seal, a relic, a lore book and treasure. Three different
 * Seals set into the Great Door open it and wake the Dreamer's Herald, a giant in three phases whose death ends the Dream:
 * everyone near receives the Herald's hoard. Arena cooldowns, the Door's Seals and the list of Dreamslayers persist.
 * <p>
 * Epoch 6 (owner, 2026-10-04: "make 20 new big structures ... They all should be unique and have bosses"): each great
 * structure ({@link Greats}) has its own keeper, which wakes when a player comes within 20 blocks of its hall (and its
 * thirty-minute rest has passed), fights with its own three powers (faster once below half health) and its own
 * horrors, and leaves relics, a lore book and treasure. They hold no Seal of the Door.
 */
final class Bosses implements Listener {
    static final String TAG = "jaspr_boss", TYPE_TAG = "jaspr_boss:", ARENA_TAG = "jaspr_boss_arena:", STONE_TAG = "jaspr_boss_stone";

    enum Boss {
        HIEROPHANT(EntityType.EVOKER, "Hierophant of the Drowned Star", 320, 0, BarColor.BLUE, Trinkets.Seal.TIDES, "He sings the Door's name."),
        PILLAR_WARDEN(EntityType.WITHER_SKELETON, "The Pillar Warden", 420, 14, BarColor.WHITE, Trinkets.Seal.STONE, "The watchers lean closer."),
        BROOD_MOTHER(EntityType.SPIDER, "The Brood Mother", 300, 10, BarColor.RED, Trinkets.Seal.HUNGER, "Something vast stirs in the bones."),
        SPAWN_OF_THE_DEEP(EntityType.SLIME, "Spawn of the Deep", 520, 12, BarColor.GREEN, Trinkets.Seal.DEEP, "The pool rises to meet you."),
        FACELESS_PRIEST(EntityType.ILLUSIONER, "The Faceless Priest", 280, 0, BarColor.PURPLE, Trinkets.Seal.SILENCE, "It has no face. It wants yours."),
        HERALD(EntityType.GIANT, "The Dreamer's Herald", 1600, 16, BarColor.PINK, null, "IT WALKS."),
        // the keepers of the great structures (epoch 6): body, title, health, damage, bar, intro, the horror it calls, its powers
        GHOUL_KING(EntityType.HUSK, "The Ghoul-King", 360, 11, BarColor.YELLOW, "The dead kings hunger.", Horrors.Kind.GHOUL, Move.CALL, Move.QUAKE, Move.HUNGER),
        DROWNED_BISHOP(EntityType.ZOMBIE, "The Drowned Bishop", 380, 12, BarColor.BLUE, "The bells toll under the water.", Horrors.Kind.DEEP_ONE, Move.CONFUSE, Move.PULL, Move.CALL),
        SLEEPERS_AVATAR(EntityType.GIANT, "The Avatar of the Sleeper", 900, 14, BarColor.PURPLE, "The Sleeper turns in its dream.", Horrors.Kind.STAR_SPAWN, Move.QUAKE, Move.STONES, Move.CALL),
        STAR_PRIEST(EntityType.EVOKER, "The Star Priest", 320, 0, BarColor.WHITE, "The stars are watching.", Horrors.Kind.NIGHTGAUNT, Move.FANGS, Move.LEVITATE, Move.CALL),
        ELDER_SHOGGOTH(EntityType.SLIME, "The Elder Shoggoth", 560, 12, BarColor.GREEN, "Tekeli-li! Tekeli-li!", Horrors.Kind.SHOGGOTH, Move.PUSH, Move.CALL, Move.LEAP),
        DROWNED_ADMIRAL(EntityType.STRAY, "The Drowned Admiral", 360, 11, BarColor.BLUE, "All hands to the deep.", Horrors.Kind.DEEP_ONE, Move.LEAP, Move.SLOW, Move.CALL),
        MIGO_OVERSEER(EntityType.ENDERMAN, "The Mi-Go Overseer", 380, 12, BarColor.PURPLE, "It wants your mind in a jar.", Horrors.Kind.MI_GO, Move.SWAP, Move.BLIND, Move.CALL),
        TINDALOS_ALPHA(EntityType.WOLF, "The Alpha of Tindalos", 320, 12, BarColor.RED, "It comes through the angles.", Horrors.Kind.HOUND, Move.LEAP, Move.WITHER, Move.CALL),
        DARK_YOUNG(EntityType.IRON_GOLEM, "The Dark Young", 480, 16, BarColor.GREEN, "The Black Goat's child stirs.", Horrors.Kind.GHOUL, Move.QUAKE, Move.POISON, Move.CALL),
        LAMPLIGHTER(EntityType.PIG_ZOMBIE, "The Lamplighter of R'lyeh", 340, 12, BarColor.YELLOW, "The lamp burns green.", Horrors.Kind.DEEP_ONE, Move.LIGHTNING, Move.BLIND, Move.CALL),
        TOLL_KEEPER(EntityType.VINDICATOR, "The Toll Keeper", 360, 13, BarColor.RED, "None cross without paying.", Horrors.Kind.CULT_ZEALOT, Move.LEAP, Move.SLOW, Move.CALL),
        LIBRARIAN(EntityType.ILLUSIONER, "The Librarian of Celaeno", 320, 0, BarColor.PURPLE, "Some books read you.", Horrors.Kind.NIGHTGAUNT, Move.BLIND, Move.CONFUSE, Move.CALL),
        DROWNED_QUEEN(EntityType.WITCH, "The Drowned Queen", 340, 0, BarColor.BLUE, "Kneel, and drown.", Horrors.Kind.DEEP_ONE, Move.POISON, Move.PULL, Move.CALL),
        DEEP_WARLORD(EntityType.ZOMBIE, "The Warlord of Y'ha-nthlei", 420, 14, BarColor.GREEN, "The Deep Ones march.", Horrors.Kind.DEEP_ONE, Move.QUAKE, Move.PULL, Move.CALL),
        GATE_GUARDIAN(EntityType.WITHER_SKELETON, "The Guardian of the Silver Gate", 400, 14, BarColor.WHITE, "Only the Key may pass.", Horrors.Kind.STAR_SPAWN, Move.FANG_LINE, Move.LIGHTNING, Move.CALL),
        HIGH_PRIEST(EntityType.STRAY, "The High Priest of Leng", 340, 10, BarColor.YELLOW, "He wears a yellow silken mask.", Horrors.Kind.CULT_ADEPT, Move.SLOW, Move.FANGS, Move.CALL),
        ELDER_THING(EntityType.SPIDER, "The Elder Thing", 360, 11, BarColor.GREEN, "It was here before the stars.", Horrors.Kind.TOMB_CRAWLER, Move.WEBS, Move.POISON, Move.CALL),
        CISTERN_GORGON(EntityType.ELDER_GUARDIAN, "The Gorgon of the Cistern", 380, 10, BarColor.BLUE, "Do not meet her eyes.", Horrors.Kind.DEEP_ONE, Move.SLOW, Move.WEAKEN, Move.CALL),
        KEEPER_OF_AEONS(EntityType.BLAZE, "The Keeper of Aeons", 320, 8, BarColor.YELLOW, "Time runs backwards here.", Horrors.Kind.NIGHTGAUNT, Move.LIGHTNING, Move.LEVITATE, Move.CALL),
        BONE_TYRANT(EntityType.SKELETON, "The Bone Tyrant", 360, 10, BarColor.WHITE, "Golgotha remembers every skull.", Horrors.Kind.STAR_SPAWN, Move.STONES, Move.WITHER, Move.CALL);
        final EntityType type;
        final String title, intro;
        final int health, damage;
        final BarColor color;
        final Trinkets.Seal seal;
        /** A great structure's keeper: the horror it calls and its three powers (null for the Wardens and the Herald). */
        final Horrors.Kind minion;
        final Move[] moves;
        Boss(EntityType type, String title, int health, int damage, BarColor color, Trinkets.Seal seal, String intro) {
            this.type = type; this.title = title; this.health = health; this.damage = damage; this.color = color; this.seal = seal; this.intro = intro;
            this.minion = null; this.moves = null;
        }
        Boss(EntityType type, String title, int health, int damage, BarColor color, String intro, Horrors.Kind minion, Move... moves) {
            this.type = type; this.title = title; this.health = health; this.damage = damage; this.color = color; this.seal = null; this.intro = intro;
            this.minion = minion; this.moves = moves;
        }
        boolean keeper() { return moves != null; }
        static Boss of(Plans.Kind k) {
            switch (k) {
                case SANCTUM: return HIEROPHANT;
                case MONOLITHS: return PILLAR_WARDEN;
                case PIT: return BROOD_MOTHER;
                case POOL: return SPAWN_OF_THE_DEEP;
                case CHAPEL: return FACELESS_PRIEST;
                default: return null;
            }
        }
    }

    /** The powers of the great structures' keepers. */
    enum Move { FANGS, FANG_LINE, QUAKE, STONES, WEBS, CALL, CONFUSE, BLIND, WITHER, POISON, SLOW, LEVITATE, SWAP, LEAP, PUSH, PULL, LIGHTNING, HUNGER, WEAKEN }

    /** The keeper of a great structure. */
    static Boss keeperOf(Plans.Great g) { return Boss.valueOf(g.boss); }

    static boolean isBoss(Entity e) { return e != null && e.getScoreboardTags().contains(TAG); }

    static final long WARDEN_COOLDOWN = 30L * 60_000L, HERALD_COOLDOWN = 45L * 60_000L, KEEPER_COOLDOWN = 30L * 60_000L;
    /** A keeper wakes for a player this close to its hall (and this far up or down). */
    static final int KEEPER_WAKE = 20, KEEPER_WAKE_DY = 14;
    static final int SEALS_NEEDED = 3;

    private static final class Active {
        final Boss boss;
        final LivingEntity entity;
        final String arena;
        final Location home;
        final BossBar bar;
        int ticks, phase = 1;
        long lonelySince;
        Active(Boss boss, LivingEntity entity, String arena, Location home) {
            this.boss = boss; this.entity = entity; this.arena = arena; this.home = home;
            this.bar = Bukkit.createBossBar(ChatColor.BOLD + boss.title, boss.color, BarStyle.SEGMENTED_10);
        }
    }

    private final RuinsPlugin plugin;
    private final File file;
    private final Random random = new Random();
    private final Map<String, Active> active = new HashMap<>();
    private final Map<String, Long> cooldown = new HashMap<>();
    private final EnumSet<Trinkets.Seal> doorSeals = EnumSet.noneOf(Trinkets.Seal.class);
    private final Set<String> slayers = new HashSet<>();
    private boolean doorOpen;
    private long doorClosesAt;
    long wardensSlain, heraldsSlain, keepersSlain;

    Bosses(RuinsPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "bosses.yml");
        load();
    }

    // ------------------------------------------------------------------ persistence

    private void load() {
        if (!file.isFile()) return;
        YamlConfiguration y = YamlConfiguration.loadConfiguration(file);
        if (y.isConfigurationSection("cooldowns"))
            for (String k : y.getConfigurationSection("cooldowns").getKeys(false)) cooldown.put(k, y.getLong("cooldowns." + k));
        for (String s : y.getStringList("door.seals")) try { doorSeals.add(Trinkets.Seal.valueOf(s)); } catch (IllegalArgumentException ignored) { }
        slayers.addAll(y.getStringList("slayers"));
        // A Door holding three Seals was open when the server stopped (its obsidian is still gone): it stays open, and its
        // Herald wakes when someone walks into the hall.
        doorOpen = doorSeals.size() >= SEALS_NEEDED;
    }

    void save() {
        YamlConfiguration y = new YamlConfiguration();
        long now = System.currentTimeMillis();
        for (Map.Entry<String, Long> e : cooldown.entrySet()) if (e.getValue() > now) y.set("cooldowns." + e.getKey().replace('.', '_'), e.getValue());
        List<String> seals = new ArrayList<>();
        for (Trinkets.Seal s : doorSeals) seals.add(s.name());
        y.set("door.seals", seals);
        y.set("slayers", new ArrayList<>(slayers));
        try { plugin.getDataFolder().mkdirs(); y.save(file); } catch (IOException e) { plugin.getLogger().warning("RUINS_BOSSES_SAVE_FAILED " + e.getClass().getSimpleName()); }
    }

    /** A new world starts with a sealed Door and no cooldowns (the Dreamslayers are remembered). */
    void resetWorldState() {
        cooldown.clear();
        doorSeals.clear();
        doorOpen = false;
        save();
    }

    // ------------------------------------------------------------------ waking

    /** Every half second: wake Wardens near players, drive every active boss, close the Door when its time comes. */
    void tick() {
        World w = plugin.ruins();
        if (w == null) { dropAll(); return; }
        long now = System.currentTimeMillis();
        for (Player p : w.getPlayers()) {
            if (p.getGameMode() == org.bukkit.GameMode.SPECTATOR) continue;
            Location l = p.getLocation();
            int i = Math.floorDiv(l.getBlockX(), Plans.SITE_GRID), j = Math.floorDiv(l.getBlockZ(), Plans.SITE_GRID);
            for (int a = i - 1; a <= i + 1; a++)
                for (int b = j - 1; b <= j + 1; b++) {
                    Plans.Site s = plugin.plans().site(a, b);
                    if (s == null || !s.kind.cult) continue;
                    int[] sp = Cult.spawnPoint(s);
                    if (Math.abs(l.getX() - sp[0]) > 18 || Math.abs(l.getZ() - sp[2]) > 18 || Math.abs(l.getY() - sp[1]) > 18) continue;
                    String arena = "site_" + a + "_" + b;
                    if (active.containsKey(arena) || cooldown.getOrDefault(arena, 0L) > now) continue;
                    spawn(Boss.of(s.kind), arena, new Location(w, sp[0] + 0.5, sp[1], sp[2] + 0.5));
                }
        }
        // The keepers of the great structures (epoch 6).
        for (Player p : w.getPlayers()) {
            if (p.getGameMode() == org.bukkit.GameMode.SPECTATOR || p.getGameMode() == org.bukkit.GameMode.CREATIVE || p.isDead()) continue;
            Location l = p.getLocation();
            for (Plans.GreatSite g : plugin.plans().greatsNear(l.getBlockX(), l.getBlockZ(), 1)) {
                if (!g.covers(l.getBlockX(), l.getBlockZ(), KEEPER_WAKE + 2)) continue;
                int[] b = Greats.boss(g);
                if (Math.abs(l.getX() - (b[0] + 0.5)) > KEEPER_WAKE || Math.abs(l.getZ() - (b[2] + 0.5)) > KEEPER_WAKE || Math.abs(l.getY() - b[1]) > KEEPER_WAKE_DY) continue;
                String arena = Greats.arena(g);
                if (active.containsKey(arena) || cooldown.getOrDefault(arena, 0L) > now) continue;
                if (!w.isChunkLoaded(b[0] >> 4, b[2] >> 4)) continue;
                spawn(keeperOf(g.kind), arena, new Location(w, b[0] + 0.5, b[1], b[2] + 0.5));
            }
        }
        // An open Door whose Herald went back to sleep (nobody near for a minute) wakes it when someone walks into the hall.
        if (doorOpen && doorClosesAt == 0 && !active.containsKey("door")) {
            int[] hs = Cult.heraldSpawn(plugin.plans().door());
            Location hall = new Location(w, hs[0] + 0.5, hs[1], hs[2] + 0.5);
            if (!Horrors.playersNear(hall, 20).isEmpty()) spawn(Boss.HERALD, "door", hall);
        }
        for (Iterator<Active> it = active.values().iterator(); it.hasNext(); ) {
            Active a = it.next();
            if (!a.entity.isValid()) { a.bar.removeAll(); it.remove(); continue; }   // died (handled below) or unloaded with its chunk
            drive(a, now);
        }
        if (doorOpen && doorClosesAt > 0 && now >= doorClosesAt) closeDoor(w);
    }

    LivingEntity spawn(Boss boss, String arena, Location at) {
        LivingEntity e = (LivingEntity) at.getWorld().spawnEntity(at, boss.type);
        e.addScoreboardTag(TAG);
        e.addScoreboardTag(TYPE_TAG + boss.name());
        e.addScoreboardTag(ARENA_TAG + arena);
        e.addScoreboardTag(Horrors.TAG);
        e.addScoreboardTag(Horrors.DAYLIGHT_EXEMPT);
        e.setCustomName(ChatColor.DARK_RED + boss.title);
        e.setCustomNameVisible(true);
        e.setRemoveWhenFarAway(false);
        if (e instanceof Slime) ((Slime) e).setSize(8);
        Horrors.set(e, Attribute.GENERIC_MAX_HEALTH, boss.health * RuinsPlugin.EASE);
        e.setHealth(boss.health * RuinsPlugin.EASE);
        if (boss.damage > 0) Horrors.set(e, Attribute.GENERIC_ATTACK_DAMAGE, boss.damage);
        Horrors.set(e, Attribute.GENERIC_ARMOR, boss == Boss.HERALD ? 12 : 8);
        Horrors.set(e, Attribute.GENERIC_KNOCKBACK_RESISTANCE, boss == Boss.HERALD ? 1.0 : 0.6);
        Horrors.set(e, Attribute.GENERIC_FOLLOW_RANGE, 48);
        if (boss == Boss.BROOD_MOTHER) Horrors.set(e, Attribute.GENERIC_MOVEMENT_SPEED, 0.4);
        if (boss == Boss.ELDER_THING || boss == Boss.TINDALOS_ALPHA) Horrors.set(e, Attribute.GENERIC_MOVEMENT_SPEED, 0.42);
        if (e instanceof org.bukkit.entity.IronGolem) ((org.bukkit.entity.IronGolem) e).setPlayerCreated(false);
        if (e instanceof org.bukkit.entity.Wolf) ((org.bukkit.entity.Wolf) e).setAngry(true);
        if (e instanceof org.bukkit.entity.PigZombie) { ((org.bukkit.entity.PigZombie) e).setAngry(true); ((org.bukkit.entity.PigZombie) e).setAnger(Integer.MAX_VALUE / 2); }
        if (e instanceof org.bukkit.entity.Zombie) ((org.bukkit.entity.Zombie) e).setBaby(false);
        if (boss.keeper()) dressKeeper(e, boss);
        Active a = new Active(boss, e, arena, at.clone());
        active.put(arena, a);
        for (Player p : Horrors.playersNear(at, 48)) p.sendTitle(ChatColor.DARK_RED + boss.title, ChatColor.GRAY + boss.intro, 10, 60, 20);
        at.getWorld().playSound(at, boss == Boss.HERALD ? Sound.ENTITY_ENDERDRAGON_GROWL : Sound.ENTITY_WITHER_SPAWN, 2f, 0.6f);
        plugin.getLogger().info("RUINS_BOSS_WOKE boss=" + boss.name() + " arena=" + arena);
        return e;
    }

    /** Bosses whose chunks come back into memory resume where they left off. */
    @EventHandler
    public void reattach(ChunkLoadEvent e) {
        if (!plugin.isRuins(e.getWorld())) return;
        for (Entity en : e.getChunk().getEntities()) {
            if (!isBoss(en) || !(en instanceof LivingEntity)) continue;
            Boss boss = null;
            String arena = null;
            for (String t : en.getScoreboardTags()) {
                if (t.startsWith(TYPE_TAG)) try { boss = Boss.valueOf(t.substring(TYPE_TAG.length())); } catch (IllegalArgumentException ignored) { }
                if (t.startsWith(ARENA_TAG)) arena = t.substring(ARENA_TAG.length());
            }
            if (boss == null || arena == null || active.containsKey(arena)) continue;
            active.put(arena, new Active(boss, (LivingEntity) en, arena, en.getLocation()));
        }
    }

    private void dropAll() {
        for (Active a : active.values()) a.bar.removeAll();
        active.clear();
    }

    // ------------------------------------------------------------------ fighting

    private void drive(Active a, long now) {
        LivingEntity e = a.entity;
        a.ticks++;
        List<Player> near = new ArrayList<>(Horrors.playersNear(e.getLocation(), 48));
        a.bar.setProgress(Math.max(0, Math.min(1, e.getHealth() / e.getMaxHealth())));
        for (Player p : e.getWorld().getPlayers()) if (near.contains(p)) a.bar.addPlayer(p); else a.bar.removePlayer(p);
        if (near.isEmpty()) {
            if (a.lonelySince == 0) a.lonelySince = now;
            else if (now - a.lonelySince > 60_000L) {   // nobody to fight: it goes back to sleep, no cooldown
                a.bar.removeAll();
                e.remove();
                active.remove(a.arena);
                plugin.getLogger().info("RUINS_BOSS_SLEPT boss=" + a.boss.name() + " arena=" + a.arena);
            }
            return;
        }
        a.lonelySince = 0;
        if (e.getLocation().distanceSquared(a.home) > 40 * 40) e.teleport(a.home);
        Player target = Horrors.nearest(e, 48);
        if (e instanceof Creature && target != null) ((Creature) e).setTarget(target);
        if (a.boss == Boss.HERALD) { herald(a, target, near); return; }
        if (a.boss.keeper()) { keeper(a, target, near); return; }
        if (a.ticks % 8 != 0) return;   // a Warden acts every four seconds
        int move = (a.ticks / 8) % 3;
        Location at = e.getLocation();
        switch (a.boss) {
            case HIEROPHANT:
                if (move == 0) fangRing(e, 2, 5);
                else if (move == 1) call(e, Horrors.Kind.CULT_ZEALOT, 2, 7);
                else for (Player p : Horrors.playersNear(at, 14)) { p.addPotionEffect(new PotionEffect(PotionEffectType.CONFUSION, 120, 0)); p.addPotionEffect(new PotionEffect(PotionEffectType.SLOW, 60, 1)); }
                break;
            case PILLAR_WARDEN:
                if (move == 0) quake(e, 7, 8, 0.9);
                else if (move == 1) for (Player p : Horrors.playersNear(at, 22)) fallingStone(p.getLocation().add(0, 10, 0));
                else call(e, Horrors.Kind.STAR_SPAWN, 2, 5);
                break;
            case BROOD_MOTHER:
                if (move == 0) call(e, Horrors.Kind.TOMB_CRAWLER, 3, 9);
                else if (move == 1) for (Player p : Horrors.playersNear(at, 14)) web(p.getLocation().getBlock());
                else if (target != null) e.setVelocity(Horrors.away(at, target.getLocation(), 1.4, 0.5));
                break;
            case SPAWN_OF_THE_DEEP:
                if (move == 0) for (Player p : Horrors.playersNear(at, 14)) p.setVelocity(Horrors.away(p.getLocation(), at, 1.1, 0.3));
                else if (move == 1) call(e, Horrors.Kind.SHOGGOTH, 2, 6);
                else { e.setVelocity(e.getVelocity().setY(1.3)); Bukkit.getScheduler().runTaskLater(plugin, () -> { if (e.isValid()) quake(e, 6, 10, 0.6); }, 28L); }
                break;
            case FACELESS_PRIEST:
                if (move == 0) for (Player p : Horrors.playersNear(at, 16)) p.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 80, 0));
                else if (move == 1 && !near.isEmpty()) {
                    Player p = near.get(random.nextInt(near.size()));
                    Location mine = at.clone(), theirs = p.getLocation().clone();
                    e.teleport(theirs);
                    p.teleport(mine);
                    p.playSound(mine, Sound.ENTITY_ILLUSION_ILLAGER_MIRROR_MOVE, 1f, 0.7f);
                } else call(e, Horrors.Kind.NIGHTGAUNT, 2, 6);
                break;
            default: break;
        }
    }

    /** The Herald: walks at its target, strikes what is close, and cycles through its dream-powers by phase. */
    private void herald(Active a, Player target, List<Player> near) {
        LivingEntity e = a.entity;
        double hp = e.getHealth() / e.getMaxHealth();
        int phase = hp > 0.66 ? 1 : hp > 0.33 ? 2 : 3;
        if (phase > a.phase) {
            a.phase = phase;
            for (Player p : near) p.sendTitle(ChatColor.DARK_RED + (phase == 2 ? "THE DREAMER STIRS" : "THE STARS ARE RIGHT"), ChatColor.GRAY + (phase == 2 ? "The Herald calls the Deep." : "The sky is falling."), 5, 50, 15);
            e.getWorld().playSound(e.getLocation(), Sound.ENTITY_ENDERDRAGON_GROWL, 3f, 0.5f);
            call(e, Horrors.Kind.DEEP_ONE, 4, 12);
        }
        if (target == null) return;
        Location at = e.getLocation(), to = target.getLocation();
        double dx = to.getX() - at.getX(), dz = to.getZ() - at.getZ(), dist = Math.sqrt(dx * dx + dz * dz);
        if (dist > 4 && e.isOnGround()) e.setVelocity(new org.bukkit.util.Vector(dx / dist * 0.23, e.getVelocity().getY(), dz / dist * 0.23));
        if (dist <= 5.5 && a.ticks % 3 == 0) {
            target.damage(a.boss.damage, e);
            target.setVelocity(Horrors.away(at, to, 1.2, 0.5));
        }
        int every = phase == 3 ? 7 : 10;
        if (a.ticks % every != 0) return;
        int move = (a.ticks / every) % (phase == 1 ? 2 : phase == 2 ? 4 : 6);
        switch (move) {
            case 0: for (Player p : near) fangLine(e, p.getLocation(), 16); break;
            case 1: quake(e, 9, 10, 1.1); break;
            case 2: for (Player p : Horrors.playersNear(at, 32)) { p.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 60, 0)); p.addPotionEffect(new PotionEffect(PotionEffectType.WITHER, 80, 0)); } break;
            case 3: call(e, random.nextBoolean() ? Horrors.Kind.DEEP_ONE : Horrors.Kind.GHOUL, 3, 10); break;
            case 4:
                for (Player p : near) for (int k = 0; k < 2; k++) {
                    Location strike = p.getLocation().add(random.nextInt(5) - 2, 0, random.nextInt(5) - 2);
                    e.getWorld().strikeLightningEffect(strike);
                    for (Player hit : Horrors.playersNear(strike, 2.5)) hit.damage(8.0, e);
                }
                break;
            default:
                if (!near.isEmpty()) near.get(random.nextInt(near.size())).addPotionEffect(new PotionEffect(PotionEffectType.LEVITATION, 40, 1));
                break;
        }
    }

    /** A keeper: walks if it is a giant, and every four seconds (three below half health) uses the next of its three powers. */
    private void keeper(Active a, Player target, List<Player> near) {
        LivingEntity e = a.entity;
        if (e instanceof org.bukkit.entity.Wolf) ((org.bukkit.entity.Wolf) e).setAngry(true);
        if (a.boss.type == EntityType.GIANT && target != null) {      // giants have no wits of their own: it walks and strikes like the Herald
            Location at = e.getLocation(), to = target.getLocation();
            double dx = to.getX() - at.getX(), dz = to.getZ() - at.getZ(), dist = Math.sqrt(dx * dx + dz * dz);
            if (dist > 4 && e.isOnGround()) e.setVelocity(new org.bukkit.util.Vector(dx / dist * 0.22, e.getVelocity().getY(), dz / dist * 0.22));
            if (dist <= 5.5 && a.ticks % 3 == 0) {
                target.damage(a.boss.damage, e);
                target.setVelocity(Horrors.away(at, to, 1.1, 0.5));
            }
        }
        int every = e.getHealth() < e.getMaxHealth() / 2 ? 6 : 8;
        if (a.ticks % every != 0) return;
        Move m = a.boss.moves[(a.ticks / every) % a.boss.moves.length];
        power(a, m, target, near);
    }

    private void power(Active a, Move m, Player target, List<Player> near) {
        LivingEntity e = a.entity;
        Location at = e.getLocation();
        switch (m) {
            case FANGS: fangRing(e, 2, 6); break;
            case FANG_LINE: for (Player p : near) fangLine(e, p.getLocation(), 16); break;
            case QUAKE: quake(e, 7, 8, 0.9); break;
            case STONES: for (Player p : Horrors.playersNear(at, 22)) fallingStone(p.getLocation().add(0, 10, 0)); break;
            case WEBS: for (Player p : Horrors.playersNear(at, 14)) web(p.getLocation().getBlock()); break;
            case CALL: if (a.boss.minion != null) call(e, a.boss.minion, 2, 7); break;
            case CONFUSE: for (Player p : Horrors.playersNear(at, 14)) { p.addPotionEffect(new PotionEffect(PotionEffectType.CONFUSION, 120, 0)); p.addPotionEffect(new PotionEffect(PotionEffectType.SLOW, 60, 1)); } break;
            case BLIND: for (Player p : Horrors.playersNear(at, 16)) p.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 80, 0)); break;
            case WITHER: for (Player p : Horrors.playersNear(at, 12)) p.addPotionEffect(new PotionEffect(PotionEffectType.WITHER, 80, 0)); break;
            case POISON: for (Player p : Horrors.playersNear(at, 12)) p.addPotionEffect(new PotionEffect(PotionEffectType.POISON, 100, 0)); break;
            case SLOW: for (Player p : Horrors.playersNear(at, 16)) p.addPotionEffect(new PotionEffect(PotionEffectType.SLOW, 80, 1)); break;
            case LEVITATE: for (Player p : Horrors.playersNear(at, 12)) p.addPotionEffect(new PotionEffect(PotionEffectType.LEVITATION, 40, 0)); break;
            case SWAP:
                if (!near.isEmpty()) {
                    Player p = near.get(random.nextInt(near.size()));
                    Location mine = at.clone(), theirs = p.getLocation().clone();
                    e.teleport(theirs);
                    p.teleport(mine);
                    p.playSound(mine, Sound.ENTITY_ENDERMEN_TELEPORT, 1f, 0.6f);
                }
                break;
            case LEAP:
                if (target != null) {
                    e.setVelocity(Horrors.away(at, target.getLocation(), 1.2, 0.6));
                    Bukkit.getScheduler().runTaskLater(plugin, () -> { if (e.isValid()) quake(e, 5, 7, 0.6); }, 24L);
                }
                break;
            case PUSH: for (Player p : Horrors.playersNear(at, 14)) p.setVelocity(Horrors.away(at, p.getLocation(), 1.2, 0.4)); break;
            case PULL: for (Player p : Horrors.playersNear(at, 18)) if (p.getLocation().distanceSquared(at) > 9) p.setVelocity(Horrors.away(p.getLocation(), at, 1.0, 0.3)); break;
            case LIGHTNING:
                for (Player p : near) {
                    Location strike = p.getLocation().add(random.nextInt(5) - 2, 0, random.nextInt(5) - 2);
                    e.getWorld().strikeLightningEffect(strike);
                    for (Player hit : Horrors.playersNear(strike, 2.5)) hit.damage(7.0, e);
                }
                break;
            case HUNGER: for (Player p : Horrors.playersNear(at, 14)) { p.addPotionEffect(new PotionEffect(PotionEffectType.HUNGER, 200, 1)); p.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, 100, 0)); } break;
            case WEAKEN: for (Player p : Horrors.playersNear(at, 16)) { p.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, 120, 0)); p.addPotionEffect(new PotionEffect(PotionEffectType.SLOW, 60, 0)); } break;
            default: break;
        }
    }

    /** A keeper's look: what it holds and wears (nothing of it drops). */
    private static void dressKeeper(LivingEntity e, Boss boss) {
        org.bukkit.inventory.EntityEquipment q = e.getEquipment();
        if (q == null) return;
        switch (boss) {
            case GHOUL_KING: q.setHelmet(new ItemStack(Material.GOLD_HELMET)); q.setItemInMainHand(new ItemStack(Material.BONE)); break;
            case DROWNED_BISHOP: q.setHelmet(new ItemStack(Material.PRISMARINE)); q.setItemInMainHand(new ItemStack(Material.STICK)); break;
            case DROWNED_ADMIRAL: q.setHelmet(new ItemStack(Material.CHAINMAIL_HELMET)); q.setItemInMainHand(new ItemStack(Material.IRON_SWORD)); break;
            case LAMPLIGHTER: q.setItemInMainHand(new ItemStack(Material.SEA_LANTERN)); break;
            case TOLL_KEEPER: q.setItemInMainHand(new ItemStack(Material.IRON_AXE)); break;
            case DEEP_WARLORD: q.setHelmet(new ItemStack(Material.IRON_HELMET)); q.setChestplate(new ItemStack(Material.CHAINMAIL_CHESTPLATE)); q.setItemInMainHand(new ItemStack(Material.IRON_SWORD)); break;
            case GATE_GUARDIAN: q.setHelmet(new ItemStack(Material.IRON_HELMET)); q.setItemInMainHand(new ItemStack(Material.STONE_SWORD)); break;
            case HIGH_PRIEST: q.setHelmet(new ItemStack(Material.SPONGE)); break;
            case BONE_TYRANT: q.setHelmet(new ItemStack(Material.GOLD_HELMET)); q.setItemInMainHand(new ItemStack(Material.BOW)); break;
            default: break;
        }
        q.setItemInMainHandDropChance(0f); q.setItemInOffHandDropChance(0f); q.setHelmetDropChance(0f);
        q.setChestplateDropChance(0f); q.setLeggingsDropChance(0f); q.setBootsDropChance(0f);
    }

    /** A keeper's fireballs never set the ruins alight. */
    @EventHandler(ignoreCancelled = true)
    public void fireballs(org.bukkit.event.entity.ProjectileLaunchEvent e) {
        if (e.getEntity() instanceof org.bukkit.entity.SmallFireball && e.getEntity().getShooter() instanceof Entity && isBoss((Entity) e.getEntity().getShooter()))
            ((org.bukkit.entity.SmallFireball) e.getEntity()).setIsIncendiary(false);
    }

    private void call(LivingEntity boss, Horrors.Kind kind, int count, int cap) {
        count = (int) Math.ceil(count * RuinsPlugin.EASE);   // half the summons (owner, 2026-09-28)
        cap = (int) Math.ceil(cap * RuinsPlugin.EASE);
        int around = 0;
        for (Entity n : boss.getNearbyEntities(24, 12, 24)) if (Horrors.isHorror(n) && !isBoss(n)) around++;
        for (int i = 0; i < count && around < cap; i++, around++) {
            Location l = boss.getLocation().add(random.nextInt(9) - 4, 0.5, random.nextInt(9) - 4);
            if (l.getBlock().getType().isSolid()) l = boss.getLocation();
            plugin.horrors().spawn(kind, l);
        }
    }

    private void fangRing(LivingEntity boss, int from, int to) {
        for (int r = from; r <= to; r += 2)
            for (int k = 0; k < 8 + r * 2; k++) {
                double t = 2 * Math.PI * k / (8 + r * 2);
                fang(boss, boss.getLocation().add(Math.cos(t) * r, 0, Math.sin(t) * r), (r - from) * 3);
            }
    }

    private void fangLine(LivingEntity boss, Location to, int length) {
        Location from = boss.getLocation();
        org.bukkit.util.Vector dir = to.toVector().subtract(from.toVector()).setY(0);
        if (dir.lengthSquared() < 0.01) return;
        dir.normalize();
        for (int k = 2; k < length + 2; k++) fang(boss, from.clone().add(dir.clone().multiply(k)), k);
    }

    private void fang(LivingEntity owner, Location at, int delay) {
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!owner.isValid()) return;
            Location l = at.clone();
            for (int k = 0; k < 4 && l.getBlock().getType().isSolid(); k++) l.add(0, 1, 0);
            for (int k = 0; k < 4 && !l.getBlock().getRelative(0, -1, 0).getType().isSolid(); k++) l.add(0, -1, 0);
            EvokerFangs f = (EvokerFangs) l.getWorld().spawnEntity(l, EntityType.EVOKER_FANGS);
            f.setOwner(owner);
        }, Math.max(0, delay));
    }

    private void quake(LivingEntity boss, double range, double damage, double up) {
        Location at = boss.getLocation();
        at.getWorld().playSound(at, Sound.ENTITY_GENERIC_EXPLODE, 1.5f, 0.5f);
        for (Player p : Horrors.playersNear(at, range)) {
            p.damage(damage, boss);
            p.setVelocity(Horrors.away(at, p.getLocation(), 0.8, up));
        }
    }

    @SuppressWarnings("deprecation")
    private void fallingStone(Location at) {
        FallingBlock b = at.getWorld().spawnFallingBlock(at, new MaterialData(Material.ANVIL));
        b.setDropItem(false);
        b.setHurtEntities(true);
        b.addScoreboardTag(STONE_TAG);
    }

    private void web(Block b) {
        if (b.getType() != Material.AIR) return;
        b.setType(Material.WEB);
        Bukkit.getScheduler().runTaskLater(plugin, () -> { if (b.getType() == Material.WEB) b.setType(Material.AIR); }, 100L);
    }

    @EventHandler(ignoreCancelled = true)
    public void stoneLands(EntityChangeBlockEvent e) {
        if (e.getEntity().getScoreboardTags().contains(STONE_TAG)) { e.setCancelled(true); e.getEntity().remove(); }
    }

    @EventHandler(ignoreCancelled = true)
    public void unhurt(EntityDamageEvent e) {
        if (!isBoss(e.getEntity())) return;
        switch (e.getCause()) {
            case SUFFOCATION: case FALL: case DROWNING: case CRAMMING: case LIGHTNING: e.setCancelled(true); break;
            default: break;
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void split(SlimeSplitEvent e) {
        if (isBoss(e.getEntity())) e.setCount(3);
    }

    // ------------------------------------------------------------------ victory

    @EventHandler
    public void slain(EntityDeathEvent e) {
        if (!isBoss(e.getEntity())) return;
        Boss boss = null;
        String arena = null;
        for (String t : e.getEntity().getScoreboardTags()) {
            if (t.startsWith(TYPE_TAG)) try { boss = Boss.valueOf(t.substring(TYPE_TAG.length())); } catch (IllegalArgumentException ignored) { }
            if (t.startsWith(ARENA_TAG)) arena = t.substring(ARENA_TAG.length());
        }
        if (boss == null) return;
        Active a = arena == null ? null : active.remove(arena);
        if (a != null) a.bar.removeAll();
        e.getDrops().clear();
        Location at = e.getEntity().getLocation();
        Player killer = e.getEntity().getKiller();
        long now = System.currentTimeMillis();
        if (boss == Boss.HERALD) {
            heraldsSlain++;
            e.setDroppedExp(3000);
            for (Player p : Horrors.playersNear(at, 64)) {
                for (ItemStack item : Trinkets.heraldRewards())
                    for (ItemStack left : p.getInventory().addItem(item).values()) p.getWorld().dropItemNaturally(p.getLocation(), left);
                p.sendTitle(ChatColor.GOLD + "THE DREAM IS ENDED", ChatColor.GRAY + "You have conquered Drownhollow.", 10, 100, 30);
                slayers.add(p.getUniqueId().toString());
                if (plugin.guide() != null) plugin.guide().victory(p);
            }
            Bukkit.broadcastMessage(ChatColor.GOLD + (killer != null ? killer.getName() : "Pilgrims") + " ended the Dream of Drownhollow: the Dreamer's Herald is slain!");
            cooldown.put("door", now + HERALD_COOLDOWN);
            doorClosesAt = now + 3L * 60_000L;
            plugin.getLogger().info("RUINS_HERALD_SLAIN by=" + (killer == null ? "unknown" : killer.getName()) + " rewarded=" + Horrors.playersNear(at, 64).size());
        } else if (boss.keeper()) {
            keepersSlain++;
            e.setDroppedExp(700);
            e.getDrops().add(Trinkets.random(random));
            e.getDrops().add(Trinkets.random(random));
            e.getDrops().add(Lore.book(random.nextInt(Lore.bookCount() - 1)));
            e.getDrops().add(new ItemStack(Material.DIAMOND, 2 + random.nextInt(3)));
            e.getDrops().add(new ItemStack(Material.GOLDEN_APPLE, 2));
            e.getDrops().add(new ItemStack(Material.EXP_BOTTLE, 4 + random.nextInt(5)));
            if (arena != null) cooldown.put(arena, now + KEEPER_COOLDOWN);
            for (Player p : Horrors.playersNear(at, 48)) p.sendTitle(ChatColor.GOLD + "KEEPER SLAIN", ChatColor.GRAY + boss.title, 10, 60, 20);
            plugin.getLogger().info("RUINS_KEEPER_SLAIN boss=" + boss.name() + " arena=" + arena);
        } else {
            wardensSlain++;
            e.setDroppedExp(600);
            e.getDrops().add(Trinkets.seal(boss.seal));
            e.getDrops().add(Trinkets.random(random));
            e.getDrops().add(Lore.book(random.nextInt(Lore.bookCount() - 1)));
            e.getDrops().add(new ItemStack(Material.DIAMOND, 3 + random.nextInt(3)));
            e.getDrops().add(new ItemStack(Material.GOLDEN_APPLE, 2));
            if (boss == Boss.HIEROPHANT) e.getDrops().add(new ItemStack(Material.TOTEM));
            if (arena != null) cooldown.put(arena, now + WARDEN_COOLDOWN);
            for (Player p : Horrors.playersNear(at, 48)) p.sendTitle(ChatColor.DARK_AQUA + boss.seal.title, ChatColor.GRAY + "Three different Seals open the Great Door.", 10, 60, 20);
            plugin.getLogger().info("RUINS_WARDEN_SLAIN boss=" + boss.name() + " by=" + (killer == null ? "unknown" : killer.getName()));
        }
        save();
    }

    boolean slayer(Player p) { return slayers.contains(p.getUniqueId().toString()); }

    // ------------------------------------------------------------------ what the guide kit reads

    /** The arena key of a cult site (its site cell). */
    static String arena(Plans.Site s) { return "site_" + Math.floorDiv(s.x, Plans.SITE_GRID) + "_" + Math.floorDiv(s.z, Plans.SITE_GRID); }

    /** Milliseconds until an arena's Warden (or, for "door", the Door) can wake again; 0 when it can. */
    long waitLeft(String arena) { return Math.max(0, cooldown.getOrDefault(arena, 0L) - System.currentTimeMillis()); }

    EnumSet<Trinkets.Seal> doorSeals() { return EnumSet.copyOf(doorSeals.isEmpty() ? EnumSet.noneOf(Trinkets.Seal.class) : doorSeals); }

    boolean doorOpen() { return doorOpen; }

    /** The Herald while it is awake, else null. */
    LivingEntity herald() { Active a = active.get("door"); return a == null ? null : a.entity; }

    // ------------------------------------------------------------------ the Great Door

    @EventHandler(priority = EventPriority.HIGH)
    public void door(PlayerInteractEvent e) {
        if (e.getAction() != Action.RIGHT_CLICK_BLOCK || e.getClickedBlock() == null || !plugin.isRuins(e.getClickedBlock().getWorld())) return;
        // One answer per click: the client repeats a refused click with the off hand (empty), which read as "no Seal".
        if (e.getHand() == org.bukkit.inventory.EquipmentSlot.OFF_HAND) {
            if (Cult.doorBlock(plugin.plans().door(), e.getClickedBlock().getX(), e.getClickedBlock().getY(), e.getClickedBlock().getZ())) e.setCancelled(true);
            return;
        }
        Block b = e.getClickedBlock();
        Plans.Door d = plugin.plans().door();
        if (!Cult.doorBlock(d, b.getX(), b.getY(), b.getZ())) return;
        e.setCancelled(true);
        Player p = e.getPlayer();
        if (doorOpen) { p.sendMessage(ChatColor.GRAY + "The Great Door stands open."); return; }
        long wait = cooldown.getOrDefault("door", 0L) - System.currentTimeMillis();
        if (wait > 0) { p.sendMessage(ChatColor.GRAY + "The Door is still settling after the last waking. Return in " + (wait / 60000 + 1) + " minutes."); return; }
        ItemStack hand = e.getItem();
        Trinkets.Seal seal = Trinkets.sealOf(hand);
        if (seal == null) {
            p.sendMessage(ChatColor.DARK_AQUA + "The Great Door is sealed. " + ChatColor.GRAY + "It takes three different Seals (" + doorSeals.size() + "/" + SEALS_NEEDED + " given). Right-click it with a Seal.");
            return;
        }
        if (doorSeals.contains(seal)) { p.sendMessage(ChatColor.GRAY + "The Door already holds the " + seal.title + ". It wants a different Seal."); return; }
        hand.setAmount(hand.getAmount() - 1);
        doorSeals.add(seal);
        b.getWorld().playSound(b.getLocation(), Sound.BLOCK_END_PORTAL_FRAME_FILL, 2f, 0.6f);
        for (Player near : Horrors.playersNear(b.getLocation(), 48))
            near.sendMessage(ChatColor.DARK_AQUA + p.getName() + " set the " + seal.title + " into the Great Door (" + doorSeals.size() + "/" + SEALS_NEEDED + ").");
        plugin.getLogger().info("RUINS_DOOR_SEAL seal=" + seal.name() + " count=" + doorSeals.size() + " by=" + p.getName());
        if (doorSeals.size() >= SEALS_NEEDED) openDoor(b.getWorld());
        save();
    }

    @EventHandler(ignoreCancelled = true)
    public void unbreakable(BlockBreakEvent e) {
        Block b = e.getBlock();
        if (plugin.isRuins(b.getWorld()) && Cult.doorBlock(plugin.plans().door(), b.getX(), b.getY(), b.getZ())) {
            e.setCancelled(true);
            e.getPlayer().sendMessage(ChatColor.GRAY + "The Door does not break. Bring it three different Seals.");
        }
    }

    void openDoor(World w) {
        Plans.Door d = plugin.plans().door();
        for (int x = d.x - 5; x <= d.x + 5; x++)
            for (int y = d.base + 1; y <= d.base + 18; y++)
                for (int z = d.z; z <= d.z + 1; z++) { Block b = w.getBlockAt(x, y, z); if (b.getType() == Material.OBSIDIAN) b.setType(Material.AIR, false); }
        doorOpen = true;
        doorClosesAt = 0;
        Location at = new Location(w, d.x + 0.5, d.base + 10, d.z + 0.5);
        w.playSound(at, Sound.BLOCK_PORTAL_TRAVEL, 2f, 0.5f);
        for (Player p : Horrors.playersNear(at, 96)) p.sendTitle(ChatColor.DARK_RED + "THE GREAT DOOR OPENS", ChatColor.GRAY + "The Herald wakes.", 10, 70, 20);
        int[] hs = Cult.heraldSpawn(d);
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (plugin.ruins() == w && !active.containsKey("door")) spawn(Boss.HERALD, "door", new Location(w, hs[0] + 0.5, hs[1], hs[2] + 0.5));
        }, 80L);
        plugin.getLogger().info("RUINS_DOOR_OPENED");
    }

    void closeDoor(World w) {
        Plans.Door d = plugin.plans().door();
        for (int x = d.x - 5; x <= d.x + 5; x++)
            for (int y = d.base + 1; y <= d.base + 18; y++)
                for (int z = d.z; z <= d.z + 1; z++) { Block b = w.getBlockAt(x, y, z); if (b.getType() == Material.AIR) b.setType(Material.OBSIDIAN, false); }
        doorOpen = false;
        doorClosesAt = 0;
        doorSeals.clear();
        Active h = active.remove("door");
        if (h != null) { h.bar.removeAll(); h.entity.remove(); }
        save();
        plugin.getLogger().info("RUINS_DOOR_SEALED");
    }

    String status() {
        return "Wardens slain " + wardensSlain + ", Keepers slain " + keepersSlain + ", Heralds slain " + heraldsSlain + ", active " + active.keySet() + ", Door "
            + (doorOpen ? "open" : "sealed (" + doorSeals.size() + "/" + SEALS_NEEDED + ")") + ", Dreamslayers " + slayers.size();
    }

    static Boss parse(String s) {
        try { return Boss.valueOf(s.toUpperCase(Locale.ROOT)); } catch (IllegalArgumentException e) { return null; }
    }
}
