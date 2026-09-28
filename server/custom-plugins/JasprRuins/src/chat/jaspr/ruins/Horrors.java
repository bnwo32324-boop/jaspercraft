package chat.jaspr.ruins;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.ChatColor;
import org.bukkit.Color;
import org.bukkit.Effect;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.block.Block;
import org.bukkit.entity.Ambient;
import org.bukkit.entity.Animals;
import org.bukkit.entity.Enderman;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.entity.Slime;
import org.bukkit.entity.Wolf;
import org.bukkit.entity.Zombie;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.entity.SlimeSplitEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.material.MaterialData;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

/**
 * The horrors of Drownhollow. In the ruins every monster that spawns naturally or from a spawner becomes one of ten
 * Lovecraftian horrors (there are no animals), each with its own strength and trick; and the Dread rises in anyone who
 * stands in darkness without a light, bringing whispers, nausea, weakness, blindness and finally harm.
 */
final class Horrors implements Listener {
    static final String TAG = "jaspr_horror", KIND_TAG = "jaspr_horror:", DAYLIGHT_EXEMPT = "jaspr_daylight_exempt", ELITE = "jaspr_horror_elite";

    enum Kind {
        DEEP_ONE(EntityType.ZOMBIE, 22, "Deep One", 40, 7, 0.27),
        GHOUL(EntityType.HUSK, 16, "Ghoul", 34, 6, 0.32),
        CULT_ZEALOT(EntityType.VINDICATOR, 10, "Cult Zealot", 40, 11, 0.33),
        TOMB_CRAWLER(EntityType.CAVE_SPIDER, 10, "Tomb Crawler", 18, 4, 0.34),
        NIGHTGAUNT(EntityType.VEX, 9, "Nightgaunt", 24, 6, 0.0),
        SHOGGOTH(EntityType.SLIME, 8, "Shoggoth", 60, 7, 0.0),
        MI_GO(EntityType.ENDERMAN, 8, "Mi-Go", 60, 9, 0.32),
        STAR_SPAWN(EntityType.WITHER_SKELETON, 8, "Star-Spawn Thrall", 50, 10, 0.27),
        HOUND(EntityType.WOLF, 6, "Hound of Tindalos", 30, 8, 0.42),
        CULT_ADEPT(EntityType.EVOKER, 3, "Cult Adept", 44, 0, 0.0);
        final EntityType type;
        final int weight, health, damage;
        final double speed;
        final String title;
        Kind(EntityType type, int weight, String title, int health, int damage, double speed) {
            this.type = type; this.weight = weight; this.title = title; this.health = health; this.damage = damage; this.speed = speed;
        }
    }

    static boolean isHorror(Entity e) { return e != null && e.getScoreboardTags().contains(TAG); }

    static Kind kindOf(Entity e) {
        if (e == null) return null;
        for (String t : e.getScoreboardTags())
            if (t.startsWith(KIND_TAG)) try { return Kind.valueOf(t.substring(KIND_TAG.length())); } catch (IllegalArgumentException ignored) { }
        return null;
    }

    static Kind pick(Random r) {
        int total = 0;
        for (Kind k : Kind.values()) total += k.weight;
        int roll = r.nextInt(total);
        for (Kind k : Kind.values()) { roll -= k.weight; if (roll < 0) return k; }
        return Kind.DEEP_ONE;
    }

    private final RuinsPlugin plugin;
    private final Random random = new Random();
    private final Map<UUID, Integer> dread = new HashMap<>();
    private final Map<UUID, Long> lastWhisper = new HashMap<>();
    private boolean spawning;
    private final java.util.Set<Long> ambushed = new java.util.HashSet<>();
    private final Map<UUID, Long> lastShadow = new HashMap<>();
    private int dreadTicks;
    long transformed, slain, elites, ambushes, shadows, crumbles, eased;

    Horrors(RuinsPlugin plugin) { this.plugin = plugin; }

    // ------------------------------------------------------------------ spawning

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void spawn(CreatureSpawnEvent e) {
        if (spawning || !plugin.isRuins(e.getLocation().getWorld())) return;
        LivingEntity entity = e.getEntity();
        CreatureSpawnEvent.SpawnReason reason = e.getSpawnReason();
        boolean wild = reason == CreatureSpawnEvent.SpawnReason.NATURAL || reason == CreatureSpawnEvent.SpawnReason.CHUNK_GEN
            || reason == CreatureSpawnEvent.SpawnReason.SPAWNER || reason == CreatureSpawnEvent.SpawnReason.DEFAULT;
        if (!wild) return;
        if (entity instanceof Animals || entity instanceof Ambient) { e.setCancelled(true); return; }   // nothing lives here
        if (!(entity instanceof Monster) && !(entity instanceof Slime)) return;
        e.setCancelled(true);
        // Half the spawns: the monster cap is halved in RuinsPlugin; spawner cages (which ignore it) fire at half rate.
        if (reason == CreatureSpawnEvent.SpawnReason.SPAWNER && random.nextDouble() >= RuinsPlugin.EASE) return;
        Location at = e.getLocation();
        Kind kind = at.getBlock().isLiquid() ? Kind.DEEP_ONE
            : reason == CreatureSpawnEvent.SpawnReason.SPAWNER && entity.getType() == EntityType.CAVE_SPIDER ? Kind.TOMB_CRAWLER : pick(random);
        int group = kind == Kind.TOMB_CRAWLER ? 1 + random.nextInt(2) : 1;
        for (int i = 0; i < group; i++) {
            LivingEntity h = spawn(kind, i == 0 ? at : at.clone().add(random.nextDouble() * 2 - 1, 0, random.nextDouble() * 2 - 1));
            if (h != null && random.nextDouble() < 0.12 * RuinsPlugin.EASE) elder(h, kind);
        }
        transformed++;
    }

    LivingEntity spawn(Kind kind, Location at) {
        spawning = true;
        try {
            LivingEntity e = (LivingEntity) at.getWorld().spawnEntity(at, kind.type);
            dress(e, kind);
            return e;
        } finally {
            spawning = false;
        }
    }

    /** An Elder: the same horror, older and worse, wreathed in a faint purple haze. */
    void elder(LivingEntity e, Kind kind) {
        e.addScoreboardTag(ELITE);
        e.setCustomName(ChatColor.DARK_PURPLE + "Elder " + kind.title);
        set(e, Attribute.GENERIC_MAX_HEALTH, kind.health * 1.8 * RuinsPlugin.EASE);
        e.setHealth(kind.health * 1.8 * RuinsPlugin.EASE);
        if (kind.damage > 0) set(e, Attribute.GENERIC_ATTACK_DAMAGE, kind.damage * 1.4);
        if (kind.speed > 0) set(e, Attribute.GENERIC_MOVEMENT_SPEED, kind.speed * 1.08);
        set(e, Attribute.GENERIC_ARMOR, 6);
        elites++;
    }

    @SuppressWarnings("deprecation")
    static void dress(LivingEntity e, Kind kind) {
        e.addScoreboardTag(TAG);
        e.addScoreboardTag(KIND_TAG + kind.name());
        e.addScoreboardTag(DAYLIGHT_EXEMPT);
        e.setCustomName(ChatColor.DARK_GREEN + kind.title);
        e.setCustomNameVisible(false);
        if (e instanceof Slime) ((Slime) e).setSize(kind == Kind.SHOGGOTH ? 4 : 2);
        if (e instanceof Zombie) ((Zombie) e).setBaby(false);
        set(e, Attribute.GENERIC_MAX_HEALTH, kind.health * RuinsPlugin.EASE);
        e.setHealth(kind.health * RuinsPlugin.EASE);
        if (kind.damage > 0) set(e, Attribute.GENERIC_ATTACK_DAMAGE, kind.damage);
        if (kind.speed > 0) set(e, Attribute.GENERIC_MOVEMENT_SPEED, kind.speed);
        set(e, Attribute.GENERIC_FOLLOW_RANGE, 40);
        EntityEquipment eq = e.getEquipment();
        if (kind == Kind.DEEP_ONE && eq != null) {
            eq.setHelmet(dyed(Material.LEATHER_HELMET, 28, 86, 74));
            eq.setChestplate(dyed(Material.LEATHER_CHESTPLATE, 22, 70, 60));
            eq.setLeggings(dyed(Material.LEATHER_LEGGINGS, 22, 70, 60));
            eq.setBoots(dyed(Material.LEATHER_BOOTS, 28, 86, 74));
            eq.setHelmetDropChance(0f); eq.setChestplateDropChance(0f); eq.setLeggingsDropChance(0f); eq.setBootsDropChance(0f);
        } else if (kind == Kind.CULT_ZEALOT && eq != null) {
            eq.setChestplate(dyed(Material.LEATHER_CHESTPLATE, 25, 20, 30));
            eq.setChestplateDropChance(0f);
        } else if (kind == Kind.MI_GO) {
            ((Enderman) e).setCarriedMaterial(new MaterialData(Material.SEA_LANTERN));
        } else if (kind == Kind.HOUND) {
            ((Wolf) e).setAngry(true);
        }
    }

    private static ItemStack dyed(Material m, int r, int g, int b) {
        ItemStack item = new ItemStack(m);
        LeatherArmorMeta meta = (LeatherArmorMeta) item.getItemMeta();
        meta.setColor(Color.fromRGB(r, g, b));
        item.setItemMeta(meta);
        return item;
    }

    static void set(LivingEntity e, Attribute a, double v) {
        AttributeInstance i = e.getAttribute(a);
        if (i != null) i.setBaseValue(v);
    }

    @EventHandler(ignoreCancelled = true)
    public void split(SlimeSplitEvent e) {
        if (!isHorror(e.getEntity()) || Bosses.isBoss(e.getEntity())) return;
        e.setCount(Math.min(e.getCount(), 2));
    }

    // ------------------------------------------------------------------ their tricks

    /** Every half second: hounds out of corners, Mi-Go on the hunt, Deep Ones fast in water, shoggoths closing wounds. */
    void tick() {
        World w = plugin.ruins();
        if (w == null || w.getPlayers().isEmpty()) return;
        for (LivingEntity e : w.getLivingEntities()) {
            if (!isHorror(e) || Bosses.isBoss(e)) continue;
            Kind k = kindOf(e);
            if (k == null) continue;
            if (e.getScoreboardTags().contains(ELITE)) w.spawnParticle(org.bukkit.Particle.SPELL_WITCH, e.getLocation().add(0, 1, 0), 3, 0.3, 0.6, 0.3, 0.0);
            switch (k) {
                case HOUND: {
                    Player t = nearest(e, 28);
                    if (t == null) break;
                    ((Wolf) e).setTarget(t);
                    if (e.getLocation().distance(t.getLocation()) > 7 && random.nextInt(8) == 0) {
                        Location behind = t.getLocation().clone().subtract(t.getLocation().getDirection().setY(0).normalize().multiply(2));
                        if (!behind.getBlock().getType().isSolid() && !behind.getBlock().getRelative(0, 1, 0).getType().isSolid()) {
                            w.playEffect(e.getLocation(), Effect.ENDER_SIGNAL, 0);
                            e.teleport(behind);
                            w.playSound(behind, Sound.ENTITY_WOLF_GROWL, 1f, 0.5f);
                        }
                    }
                    break;
                }
                case MI_GO: {
                    Player t = nearest(e, 20);
                    if (t != null) ((Enderman) e).setTarget(t);
                    break;
                }
                case DEEP_ONE:
                    if (e.getLocation().getBlock().isLiquid()) e.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 30, 2, true, false), true);
                    break;
                case SHOGGOTH:
                    if (e.getHealth() < e.getMaxHealth()) e.setHealth(Math.min(e.getMaxHealth(), e.getHealth() + 1));
                    break;
                default: break;
            }
        }
    }

    static Player nearest(Entity from, double range) {
        Player best = null;
        double bestD = range * range;
        for (Player p : from.getWorld().getPlayers()) {
            if (p.getGameMode() == GameMode.CREATIVE || p.getGameMode() == GameMode.SPECTATOR || p.isDead()) continue;
            double d = p.getLocation().distanceSquared(from.getLocation());
            if (d < bestD) { bestD = d; best = p; }
        }
        return best;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void bite(EntityDamageByEntityEvent e) {
        if (!(e.getEntity() instanceof Player) || !isHorror(e.getDamager())) return;
        Player p = (Player) e.getEntity();
        Kind k = kindOf(e.getDamager());
        if (k == null) return;
        switch (k) {
            case GHOUL: p.addPotionEffect(new PotionEffect(PotionEffectType.HUNGER, 100, 1), true); p.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, 50, 0), true); break;
            case MI_GO: p.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 20, 0), true); break;
            case DEEP_ONE: p.addPotionEffect(new PotionEffect(PotionEffectType.SLOW, 20, 1), true); break;
            case NIGHTGAUNT: p.addPotionEffect(new PotionEffect(PotionEffectType.LEVITATION, 10, 1), true); break;
            case HOUND: p.addPotionEffect(new PotionEffect(PotionEffectType.WITHER, 30, 0), true); break;
            default: break;
        }
    }

    @EventHandler
    public void death(EntityDeathEvent e) {
        if (!isHorror(e.getEntity()) || Bosses.isBoss(e.getEntity())) return;
        slain++;
        boolean elder = e.getEntity().getScoreboardTags().contains(ELITE);
        e.setDroppedExp(e.getDroppedExp() * (elder ? 4 : 2) + 5);
        double r = random.nextDouble();
        if (r < (elder ? 0.08 : 0.015)) e.getDrops().add(Trinkets.random(random));
        else if (r < 0.045) e.getDrops().add(Lore.book(random.nextInt(Lore.bookCount() - 1)));
    }

    // ------------------------------------------------------------------ the Dread

    private static final String[] WHISPERS = {"The angles are watching you.", "Something wet moves behind you.", "Ythaqqua dreams of your name.",
        "Count the pillars. Count them again.", "The Choir is singing under the stones.", "Your light is so small.", "Do not look at the corners.",
        "The Door remembers every face."};

    static boolean lit(Player p) {
        Block b = p.getLocation().getBlock();
        if (b.getLightFromBlocks() >= 8) return true;
        for (ItemStack item : new ItemStack[] {p.getInventory().getItemInMainHand(), p.getInventory().getItemInOffHand()}) {
            if (item == null) continue;
            switch (item.getType()) {
                case TORCH: case GLOWSTONE: case SEA_LANTERN: case JACK_O_LANTERN: case LAVA_BUCKET: case REDSTONE_TORCH_ON: case BEACON: return true;
                default: break;
            }
        }
        return false;
    }

    /** Every two seconds: Dread rises in the dark and falls in the light; its stages bite harder as it grows. */
    void dreadTick() {
        World w = plugin.ruins();
        if (w == null) return;
        long now = System.currentTimeMillis();
        for (Player p : w.getPlayers()) {
            if (p.getGameMode() == GameMode.CREATIVE || p.getGameMode() == GameMode.SPECTATOR || p.isDead()) continue;
            UUID id = p.getUniqueId();
            int d = dread.getOrDefault(id, 0);
            boolean immune = Trinkets.dreadImmune(p), warded = Trinkets.dreadWarded(p);
            d = immune ? 0 : lit(p) ? Math.max(0, d - 4) : Math.min(100, d + (warded ? (dreadTicks % 2 == 0 ? 1 : 0) : 1));   // half the old rise
            dread.put(id, d);
            if (d >= 30) {
                int bars = d / 10;
                StringBuilder s = new StringBuilder(ChatColor.DARK_PURPLE + "Dread ");
                for (int i = 0; i < 10; i++) s.append(i < bars ? ChatColor.LIGHT_PURPLE + "|" : ChatColor.DARK_GRAY + "|");
                s.append(ChatColor.GRAY).append(d >= 100 ? "  The dark is inside you. Find light!" : "  find light");
                p.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent(s.toString()));
                Long last = lastWhisper.get(id);
                if ((last == null || now - last > 20_000L) && random.nextInt(4) == 0) {
                    lastWhisper.put(id, now);
                    p.sendMessage(ChatColor.DARK_PURPLE + "" + ChatColor.ITALIC + WHISPERS[random.nextInt(WHISPERS.length)]);
                    p.playSound(p.getLocation(), Sound.AMBIENT_CAVE, 0.7f, 0.6f);
                }
            }
            if (d >= 55 && !warded) p.addPotionEffect(new PotionEffect(PotionEffectType.CONFUSION, 120, 0, true, false), true);
            if (d >= 75) {
                p.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, 60, 0, true, false), true);
                p.addPotionEffect(new PotionEffect(PotionEffectType.SLOW, 60, 0, true, false), true);
            }
            if (d >= 100) {
                p.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 70, 0, true, false), true);
                p.damage(2.0 * RuinsPlugin.EASE);
            }
            // Something answers great fear.
            Long last = lastShadow.get(id);
            if (d >= 90 && (last == null || now - last > 20_000L) && random.nextInt(6) == 0) {
                lastShadow.put(id, now);
                Location behind = p.getLocation().clone().subtract(p.getLocation().getDirection().setY(0).normalize().multiply(3)).add(0, 1, 0);
                if (!behind.getBlock().getType().isSolid()) {
                    spawn(Kind.NIGHTGAUNT, behind);
                    p.sendMessage(ChatColor.DARK_PURPLE + "" + ChatColor.ITALIC + "Something answers your fear.");
                    shadows++;
                }
            }
        }
        if (++dreadTicks % 30 == 0) crumble(w);
    }

    /** About once a minute, loose masonry may come down on someone in the ruins; the crack comes a moment first. */
    @SuppressWarnings("deprecation")
    private void crumble(World w) {
        for (Player p : w.getPlayers()) {
            if (p.getGameMode() == GameMode.CREATIVE || p.getGameMode() == GameMode.SPECTATOR || random.nextInt(8) != 0) continue;
            Location at = p.getLocation();
            p.playSound(at, Sound.BLOCK_STONE_BREAK, 1.2f, 0.5f);
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                if (!p.isOnline() || p.getWorld() != w) return;
                for (int k = 0; k < 4; k++) {
                    Location drop = p.getLocation().clone().add(random.nextInt(5) - 2, 9 + random.nextInt(4), random.nextInt(5) - 2);
                    if (drop.getBlock().getType() != Material.AIR) continue;
                    org.bukkit.entity.FallingBlock b = w.spawnFallingBlock(drop, new MaterialData(Material.SMOOTH_BRICK, (byte) 2));
                    b.setDropItem(false);
                    b.setHurtEntities(true);
                    b.addScoreboardTag(Bosses.STONE_TAG);
                }
                crumbles++;
            }, 20L);
        }
    }

    /** Opening an offering chest can wake its defenders (once per chest). */
    @EventHandler(ignoreCancelled = true)
    public void ambush(org.bukkit.event.inventory.InventoryOpenEvent e) {
        if (!(e.getPlayer() instanceof Player) || !(e.getInventory().getHolder() instanceof org.bukkit.block.Chest)) return;
        Player p = (Player) e.getPlayer();
        Block b = ((org.bukkit.block.Chest) e.getInventory().getHolder()).getBlock();
        if (!plugin.isRuins(b.getWorld()) || p.getGameMode() == GameMode.CREATIVE) return;
        long key = (long) b.getX() << 38 ^ (long) (b.getZ() & 0x3FFFFFF) << 12 ^ b.getY();
        if (!ambushed.add(key) || random.nextDouble() >= 0.3 * RuinsPlugin.EASE) return;
        if (ambushed.size() > 50_000) ambushed.clear();
        int n = 1 + random.nextInt(2);
        for (int i = 0; i < n; i++) {
            double t = random.nextDouble() * Math.PI * 2;
            Location at = b.getLocation().add(0.5 + Math.cos(t) * 4, 1, 0.5 + Math.sin(t) * 4);
            if (at.getBlock().getType().isSolid()) at = b.getLocation().add(0.5, 1, 0.5);
            spawn(pick(random), at);
        }
        ambushes++;
        p.sendTitle("", ChatColor.DARK_RED + "The Choir defends its offerings!", 5, 40, 10);
        p.playSound(b.getLocation(), Sound.ENTITY_ZOMBIE_VILLAGER_CONVERTED, 1f, 0.5f);
    }

    int dreadOf(Player p) { return dread.getOrDefault(p.getUniqueId(), 0); }

    /** Everything hostile in the city hurts half as much (horrors, Wardens, the Herald, falling masonry, dread, wither). */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void eased(EntityDamageEvent e) {
        if (!(e.getEntity() instanceof Player) || !plugin.isRuins(e.getEntity().getWorld())) return;
        switch (e.getCause()) {
            case ENTITY_ATTACK: case ENTITY_SWEEP_ATTACK: case PROJECTILE: case MAGIC: case WITHER: case POISON: case FALLING_BLOCK:
            case ENTITY_EXPLOSION: case BLOCK_EXPLOSION: case CUSTOM: case THORNS: case LIGHTNING: case DRAGON_BREATH:
                e.setDamage(e.getDamage() * RuinsPlugin.EASE);
                eased++;
                break;
            default:
        }
    }

    @EventHandler
    public void left(PlayerChangedWorldEvent e) { dread.remove(e.getPlayer().getUniqueId()); }

    @EventHandler
    public void died(PlayerDeathEvent e) { dread.remove(e.getEntity().getUniqueId()); }

    static Vector away(Location from, Location to, double strength, double up) {
        Vector v = to.toVector().subtract(from.toVector()).setY(0);
        if (v.lengthSquared() < 0.01) v = new Vector(1, 0, 0);
        return v.normalize().multiply(strength).setY(up);
    }

    static Collection<Player> playersNear(Location at, double range) {
        java.util.List<Player> out = new java.util.ArrayList<>();
        for (Player p : at.getWorld().getPlayers()) {
            if (p.getGameMode() == GameMode.CREATIVE || p.getGameMode() == GameMode.SPECTATOR || p.isDead()) continue;
            if (p.getLocation().distanceSquared(at) <= range * range) out.add(p);
        }
        return out;
    }
}
