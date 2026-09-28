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
 */
final class Bosses implements Listener {
    static final String TAG = "jaspr_boss", TYPE_TAG = "jaspr_boss:", ARENA_TAG = "jaspr_boss_arena:", STONE_TAG = "jaspr_boss_stone";

    enum Boss {
        HIEROPHANT(EntityType.EVOKER, "Hierophant of the Drowned Star", 320, 0, BarColor.BLUE, Trinkets.Seal.TIDES, "He sings the Door's name."),
        PILLAR_WARDEN(EntityType.WITHER_SKELETON, "The Pillar Warden", 420, 14, BarColor.WHITE, Trinkets.Seal.STONE, "The watchers lean closer."),
        BROOD_MOTHER(EntityType.SPIDER, "The Brood Mother", 300, 10, BarColor.RED, Trinkets.Seal.HUNGER, "Something vast stirs in the bones."),
        SPAWN_OF_THE_DEEP(EntityType.SLIME, "Spawn of the Deep", 520, 12, BarColor.GREEN, Trinkets.Seal.DEEP, "The pool rises to meet you."),
        FACELESS_PRIEST(EntityType.ILLUSIONER, "The Faceless Priest", 280, 0, BarColor.PURPLE, Trinkets.Seal.SILENCE, "It has no face. It wants yours."),
        HERALD(EntityType.GIANT, "The Dreamer's Herald", 1600, 16, BarColor.PINK, null, "IT WALKS.");
        final EntityType type;
        final String title, intro;
        final int health, damage;
        final BarColor color;
        final Trinkets.Seal seal;
        Boss(EntityType type, String title, int health, int damage, BarColor color, Trinkets.Seal seal, String intro) {
            this.type = type; this.title = title; this.health = health; this.damage = damage; this.color = color; this.seal = seal; this.intro = intro;
        }
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

    static boolean isBoss(Entity e) { return e != null && e.getScoreboardTags().contains(TAG); }

    static final long WARDEN_COOLDOWN = 30L * 60_000L, HERALD_COOLDOWN = 45L * 60_000L;
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
    long wardensSlain, heraldsSlain;

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
        Horrors.set(e, Attribute.GENERIC_MAX_HEALTH, boss.health);
        e.setHealth(boss.health);
        if (boss.damage > 0) Horrors.set(e, Attribute.GENERIC_ATTACK_DAMAGE, boss.damage);
        Horrors.set(e, Attribute.GENERIC_ARMOR, boss == Boss.HERALD ? 12 : 8);
        Horrors.set(e, Attribute.GENERIC_KNOCKBACK_RESISTANCE, boss == Boss.HERALD ? 1.0 : 0.6);
        Horrors.set(e, Attribute.GENERIC_FOLLOW_RANGE, 48);
        if (boss == Boss.BROOD_MOTHER) Horrors.set(e, Attribute.GENERIC_MOVEMENT_SPEED, 0.4);
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

    private void call(LivingEntity boss, Horrors.Kind kind, int count, int cap) {
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
                p.sendTitle(ChatColor.GOLD + "THE DREAM IS ENDED", ChatColor.GRAY + "You have conquered Ul'Nhaar.", 10, 100, 30);
                slayers.add(p.getUniqueId().toString());
            }
            Bukkit.broadcastMessage(ChatColor.GOLD + (killer != null ? killer.getName() : "Pilgrims") + " ended the Dream of Ul'Nhaar: the Dreamer's Herald is slain!");
            cooldown.put("door", now + HERALD_COOLDOWN);
            doorClosesAt = now + 3L * 60_000L;
            plugin.getLogger().info("RUINS_HERALD_SLAIN by=" + (killer == null ? "unknown" : killer.getName()) + " rewarded=" + Horrors.playersNear(at, 64).size());
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

    // ------------------------------------------------------------------ the Great Door

    @EventHandler(priority = EventPriority.HIGH)
    public void door(PlayerInteractEvent e) {
        if (e.getAction() != Action.RIGHT_CLICK_BLOCK || e.getClickedBlock() == null || !plugin.isRuins(e.getClickedBlock().getWorld())) return;
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
        return "Wardens slain " + wardensSlain + ", Heralds slain " + heraldsSlain + ", active " + active.keySet() + ", Door "
            + (doorOpen ? "open" : "sealed (" + doorSeals.size() + "/" + SEALS_NEEDED + ")") + ", Dreamslayers " + slayers.size();
    }

    static Boss parse(String s) {
        try { return Boss.valueOf(s.toUpperCase(Locale.ROOT)); } catch (IllegalArgumentException e) { return null; }
    }
}
