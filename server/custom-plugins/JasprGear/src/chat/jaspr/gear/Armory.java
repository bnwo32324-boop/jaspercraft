package chat.jaspr.gear;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.block.Chest;
import org.bukkit.block.DoubleChest;
import org.bukkit.craftbukkit.v1_12_R1.entity.CraftPlayer;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.EnderDragon;
import org.bukkit.entity.Item;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.ItemSpawnEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

/**
 * The realm armouries in play (JasprGear 5.0.0): forging, set powers, weapon and tool powers, and the loot.
 *
 * Forging: emerald pieces in the vanilla shapes from emerald blocks; every other set as its diamond piece (any wear, any
 * enchantment, enhanced or not) surrounded by the realm's materials. The forged piece keeps the diamond piece's
 * enchantments and Armaments record. Nothing that is already special (armoury, expedition gear, a gun, a trinket) is
 * ever accepted as the diamond piece.
 *
 * Loot: realm chests through GearApi.rollRealmLoot (called by each realm plugin's chest fill), vanilla loot-table chests
 * (dungeons, temples, strongholds, fortresses, End cities ...) on their first opening, overworld structure chests through
 * GearApi.rollLoot's armoury band, and creatures slain in each realm (the Ender Dragon always leaves a Void piece).
 *
 * Logs ARMORY_FORGE, ARMORY_DROP, ARMORY_CHEST, ARMORY_RESCUE, ARMORY_UPGRADE (armour made before its stats changed, brought up
 * to date in place: counts only) and ARMORY_METRICS (on disable).
 */
final class Armory implements Listener {
    private final GearPlugin plugin;
    private final Random random = new Random();

    int recipes, forged, pieceDrops, materialDrops, chestPieces, chestMaterials, vanillaChests, veins, trees, areas, tilled,
        smelted, prospected, pulled, torches, rescues, guarded, cleansed, weaponProcs, upgraded;

    /** Full-set bonus attribute modifiers, one fixed id each so they are found, kept and removed exactly. */
    private static final UUID HEALTH_ID = UUID.fromString("6a5e3c41-7d2b-4b1a-9f31-5e0c7a1d2001");
    private static final UUID LUCK_ID = UUID.fromString("6a5e3c41-7d2b-4b1a-9f31-5e0c7a1d2002");
    private static final UUID KNOCK_ID = UUID.fromString("6a5e3c41-7d2b-4b1a-9f31-5e0c7a1d2003");
    private static final UUID SPEED_ID = UUID.fromString("6a5e3c41-7d2b-4b1a-9f31-5e0c7a1d2004");

    static final double HOME_WEAPON = 1.15, HOME_ARMOUR_PER_PIECE = 0.03;
    static final long RESCUE_COOLDOWN_MS = 300_000L;

    private final Map<UUID, Location> safe = new HashMap<UUID, Location>();
    private final Map<UUID, Long> rescuedAt = new HashMap<UUID, Long>();
    private final Map<UUID, Long> pearlAt = new HashMap<UUID, Long>();
    private final Map<UUID, Long> torchAt = new HashMap<UUID, Long>();
    /** Fresh vanilla loot-table chests a player just clicked, by block key, until their inventory opens. */
    private final Map<String, String> freshLoot = new HashMap<String, String>();

    /** Blocks broken this tick by an armoury tool, for the drops they are about to spawn. */
    private static final class Break {
        final Player player; final ArmorySet set; final Block block; final Material type; final boolean silk; boolean prospectDone;
        Break(Player player, ArmorySet set, Block block, Material type, boolean silk) {
            this.player = player; this.set = set; this.block = block; this.type = type; this.silk = silk;
        }
    }
    private final List<Break> breaks = new ArrayList<Break>();
    private boolean clearQueued, breaking, pulling;

    Armory(GearPlugin plugin) { this.plugin = plugin; }

    // ================================================================== recipes

    void registerRecipes() {
        for (ArmorySet set : ArmorySet.values()) for (ArmoryPiece piece : ArmoryPiece.values()) {
            try {
                ShapedRecipe recipe = new ShapedRecipe(new NamespacedKey(plugin, "armory_" + set.id + "_" + piece.id), ArmoryItems.create(set, piece));
                if (set == ArmorySet.EMERALD) {
                    recipe.shape(piece.shape);
                    recipe.setIngredient('X', Material.EMERALD_BLOCK);
                    if (String.join("", piece.shape).indexOf('S') >= 0) recipe.setIngredient('S', Material.STICK);
                } else {
                    recipe.shape("CEC", "EDE", "CEC");
                    recipe.setIngredient('C', set.corner);
                    recipe.setIngredient('E', set.edge);
                    recipe.setIngredient('D', piece.base, Short.MAX_VALUE);   // any wear: the forge keeps what it can
                }
                if (plugin.getServer().addRecipe(recipe)) recipes++;
            } catch (RuntimeException error) {
                plugin.getLogger().warning("ARMORY_RECIPE_FAILED item=" + set.id + "_" + piece.id + " error=" + error);
            }
        }
    }

    /** Human recipe line for /gear and the catalogue. */
    static String recipeText(ArmorySet set, ArmoryPiece piece) {
        if (set == ArmorySet.EMERALD) {
            StringBuilder out = new StringBuilder();
            for (String row : piece.shape) out.append('[').append(row.replace(' ', '.')).append("] ");
            out.append("X=Emerald Block");
            if (String.join("", piece.shape).indexOf('S') >= 0) out.append(" S=Stick");
            return out.toString();
        }
        String corner = set.customMaterial() ? ArmoryItems.materialTitle(set.materialId) : GearAbilities.pretty(set.corner.name());
        String edge = set.customMaterial() ? ArmoryItems.materialTitle(set.materialId) : GearAbilities.pretty(set.edge.name());
        return "[CEC] [EDE] [CEC] C=" + corner + " E=" + edge + " D=Diamond " + piece.title + " (keeps its enchantments)";
    }

    /** Identifies what an armoury recipe in this grid would make, or null when the grid is not one. */
    private static ArmoryItems.Id target(ItemStack result) {
        return result == null ? null : ArmoryItems.identify(result);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPrepare(PrepareItemCraftEvent e) {
        ItemStack[] matrix = e.getInventory().getMatrix();
        ArmoryItems.Id want = e.getRecipe() == null ? null : target(e.getRecipe().getResult());
        if (want == null) {
            // Armoury gear and materials never stand in for their plain base items in any other recipe.
            for (ItemStack in : matrix) if (ArmoryItems.marked(in)) { e.getInventory().setResult(null); return; }
            return;
        }
        ItemStack diamond = null;
        for (ItemStack in : matrix) {
            if (GearItems.empty(in)) continue;
            Material m = in.getType();
            if (want.set == ArmorySet.EMERALD) {
                if (GearItems.tagged(in)) { e.getInventory().setResult(null); return; }   // plain emerald blocks and sticks only
                continue;
            }
            if (m == want.piece.base) {
                if (!plainDiamond(in)) { e.getInventory().setResult(null); return; }
                diamond = in;
            } else if (want.set.customMaterial()) {
                if (!want.set.materialId.equals(ArmoryItems.materialOf(in))) { e.getInventory().setResult(null); return; }
            } else if (GearItems.tagged(in)) {
                e.getInventory().setResult(null); return;   // the Nether's and the End's goods, plain
            }
        }
        if (want.set != ArmorySet.EMERALD) {
            if (diamond == null) { e.getInventory().setResult(null); return; }
            e.getInventory().setResult(ArmoryItems.forge(want.set, want.piece, diamond));
        }
    }

    /**
     * A diamond piece the forge accepts: vanilla diamond gear, worn or not, enchanted or enhanced or renamed, but never an
     * item another system owns (armoury, expedition equipment, guns, the portal gun, Atlas rewards, Nether stand-ins).
     */
    static boolean plainDiamond(ItemStack in) {
        if (GearItems.empty(in) || in.getAmount() != 1 || ArmoryPiece.ofBase(in.getType()) == null) return false;
        try {
            net.minecraft.server.v1_12_R1.ItemStack nms = org.bukkit.craftbukkit.v1_12_R1.inventory.CraftItemStack.asNMSCopy(in);
            if (nms == null || !nms.hasTag()) return true;
            net.minecraft.server.v1_12_R1.NBTTagCompound tag = nms.getTag();
            if (tag.getBoolean("Unbreakable")) return false;
            for (String key : tag.c()) {
                if (key.equals("ench") || key.equals("RepairCost") || key.equals("display") || key.equals("JasprArmament")) continue;
                return false;   // any other plugin's mark
            }
            return true;
        } catch (RuntimeException error) {
            return false;
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onCraft(org.bukkit.event.inventory.CraftItemEvent e) {
        ArmoryItems.Id id = ArmoryItems.identify(e.getCurrentItem());
        if (id == null || !(e.getWhoClicked() instanceof Player)) return;
        forged++;
        plugin.getLogger().info("ARMORY_FORGE item=" + id.key() + " player=" + e.getWhoClicked().getUniqueId());
    }

    // ================================================================== wearing and wielding

    /** The set all four worn armour pieces belong to, or null. */
    static ArmorySet fullSet(Player p) {
        ItemStack[] armour = p.getInventory().getArmorContents();
        ArmorySet found = null;
        for (ItemStack piece : armour) {
            ArmoryItems.Id id = ArmoryItems.identify(piece);
            if (id == null || !id.piece.armour()) return null;
            if (found == null) found = id.set; else if (found != id.set) return null;
        }
        return found;
    }

    /** True when this world is the set's home (the old liminal world counts as the Backrooms). */
    static boolean home(ArmorySet set, World world) { return world != null && ArmorySet.ofWorld(world.getName()) == set; }

    /**
     * Armoury armour in the pack or on the body that was made before the stats last changed (the helmet and boots went from 3
     * to 4 armour on 2026-10-05) is rewritten in place: same item, enchantments, lore and Armaments, new armour amounts.
     */
    private void carried(Player p) {
        PlayerInventory inv = p.getInventory();
        int n = 0;
        for (int i = 0, storage = inv.getStorageContents().length; i < storage; i++) {
            ItemStack up = ArmoryItems.upgraded(inv.getItem(i));
            if (up != null) { inv.setItem(i, up); n++; }
        }
        ItemStack up = ArmoryItems.upgraded(inv.getHelmet());
        if (up != null) { inv.setHelmet(up); n++; }
        up = ArmoryItems.upgraded(inv.getChestplate());
        if (up != null) { inv.setChestplate(up); n++; }
        up = ArmoryItems.upgraded(inv.getLeggings());
        if (up != null) { inv.setLeggings(up); n++; }
        up = ArmoryItems.upgraded(inv.getBoots());
        if (up != null) { inv.setBoots(up); n++; }
        if (n > 0) { upgraded += n; plugin.getLogger().info("ARMORY_UPGRADE player=" + p.getUniqueId() + " pieces=" + n); }
    }

    /** Every second: the full-set attribute bonuses (idempotent), remembered safe ground for Ender Step. */
    void second(Player p) {
        if (!p.isDead()) carried(p);
        ArmorySet set = p.isDead() ? null : fullSet(p);
        keep(p, Attribute.GENERIC_MAX_HEALTH, HEALTH_ID, "jaspr_armory_prosperity_health", set == ArmorySet.EMERALD ? 4.0 : 0, AttributeModifier.Operation.ADD_NUMBER);
        keep(p, Attribute.GENERIC_LUCK, LUCK_ID, "jaspr_armory_prosperity_luck", set == ArmorySet.EMERALD ? 2.0 : 0, AttributeModifier.Operation.ADD_NUMBER);
        keep(p, Attribute.GENERIC_KNOCKBACK_RESISTANCE, KNOCK_ID, "jaspr_armory_colossus", set == ArmorySet.TITAN ? 0.5 : 0, AttributeModifier.Operation.ADD_NUMBER);
        keep(p, Attribute.GENERIC_MOVEMENT_SPEED, SPEED_ID, "jaspr_armory_lucid", set == ArmorySet.LIMINAL ? 0.10 : 0, AttributeModifier.Operation.ADD_SCALAR);
        if (set != ArmorySet.EMERALD) {
            AttributeInstance max = p.getAttribute(Attribute.GENERIC_MAX_HEALTH);
            if (max != null && p.getHealth() > max.getValue()) p.setHealth(max.getValue());
        }
        if (set == ArmorySet.VOID && p.isOnGround() && p.getLocation().getY() > 1 && p.getWorld().getName().equals(ArmorySet.VOID.world))
            safe.put(p.getUniqueId(), p.getLocation().clone());
    }

    private static void keep(Player p, Attribute attribute, UUID id, String name, double amount, AttributeModifier.Operation op) {
        AttributeInstance inst = p.getAttribute(attribute);
        if (inst == null) return;
        AttributeModifier current = null;
        for (AttributeModifier m : inst.getModifiers()) if (m.getUniqueId().equals(id)) { current = m; break; }
        if (amount == 0) { if (current != null) inst.removeModifier(current); return; }
        if (current != null && current.getAmount() == amount && current.getOperation() == op) return;
        if (current != null) inst.removeModifier(current);
        inst.addModifier(new AttributeModifier(id, name, amount, op));
    }

    /** Every fifth tick for each player: cleanse what the full set forbids, keep Abyssal lungs full, catch Void falls. */
    void fast(Player p, long now) {
        ArmorySet set = fullSet(p);
        if (set == null) return;
        switch (set) {
            case ABYSSAL:
                if (p.getRemainingAir() < p.getMaximumAir()) p.setRemainingAir(p.getMaximumAir());
                if (p.hasPotionEffect(PotionEffectType.CONFUSION)) { p.removePotionEffect(PotionEffectType.CONFUSION); cleansed++; }
                break;
            case LIMINAL:
                if (p.hasPotionEffect(PotionEffectType.BLINDNESS)) { p.removePotionEffect(PotionEffectType.BLINDNESS); cleansed++; }
                if (p.hasPotionEffect(PotionEffectType.CONFUSION)) { p.removePotionEffect(PotionEffectType.CONFUSION); cleansed++; }
                break;
            case VOID:
                if (p.hasPotionEffect(PotionEffectType.LEVITATION)) { p.removePotionEffect(PotionEffectType.LEVITATION); cleansed++; }
                if (p.getLocation().getY() < -6 && p.getWorld().getName().equals(ArmorySet.VOID.world)) rescue(p, now);
                break;
            case BLAZEFORGED:
                if (p.getFireTicks() > 0) p.setFireTicks(0);
                break;
            default:
        }
    }

    private void rescue(Player p, long now) {
        Location back = safe.get(p.getUniqueId());
        Long last = rescuedAt.get(p.getUniqueId());
        if (back == null || (last != null && now - last < RESCUE_COOLDOWN_MS) || p.getGameMode() == GameMode.SPECTATOR) return;
        rescuedAt.put(p.getUniqueId(), now);
        p.setFallDistance(0);
        p.teleport(back);
        p.setFallDistance(0);
        p.getWorld().playSound(back, Sound.ENTITY_ENDERMEN_TELEPORT, 1.0f, 0.8f);
        p.getWorld().spawnParticle(Particle.PORTAL, back.clone().add(0, 1, 0), 60, 0.4, 0.8, 0.4, 0.4);
        p.sendMessage(ChatColor.DARK_PURPLE + "Ender Step pulled you back from the void. " + ChatColor.GRAY + "(again in 5 minutes)");
        rescues++;
        plugin.getLogger().info("ARMORY_RESCUE player=" + p.getUniqueId());
    }

    void forget(Player p) {
        safe.remove(p.getUniqueId()); rescuedAt.remove(p.getUniqueId()); pearlAt.remove(p.getUniqueId()); torchAt.remove(p.getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent e) { forget(e.getPlayer()); }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPearl(PlayerTeleportEvent e) {
        if (e.getCause() == PlayerTeleportEvent.TeleportCause.ENDER_PEARL && fullSet(e.getPlayer()) == ArmorySet.VOID)
            pearlAt.put(e.getPlayer().getUniqueId(), System.currentTimeMillis());
    }

    /** Wearer side: set immunities and the home realm's turned-aside share. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onHurt(EntityDamageEvent e) {
        if (!(e.getEntity() instanceof Player)) return;
        Player p = (Player) e.getEntity();
        ArmorySet set = fullSet(p);
        EntityDamageEvent.DamageCause cause = e.getCause();
        if (set == ArmorySet.BLAZEFORGED && (cause == EntityDamageEvent.DamageCause.FIRE || cause == EntityDamageEvent.DamageCause.FIRE_TICK
            || cause == EntityDamageEvent.DamageCause.LAVA || cause == EntityDamageEvent.DamageCause.HOT_FLOOR)) {
            e.setCancelled(true); p.setFireTicks(0); guarded++; return;
        }
        if (set == ArmorySet.ABYSSAL && cause == EntityDamageEvent.DamageCause.DROWNING) { e.setCancelled(true); guarded++; return; }
        if (cause == EntityDamageEvent.DamageCause.FALL) {
            if (set == ArmorySet.VOID) {
                Long at = pearlAt.remove(p.getUniqueId());
                if (at != null && System.currentTimeMillis() - at < 2000) { e.setCancelled(true); guarded++; return; }
            }
            if (set == ArmorySet.TITAN) { e.setDamage(e.getDamage() * 0.5); guarded++; }
        }
        int home = 0;
        for (ItemStack piece : p.getInventory().getArmorContents()) {
            ArmoryItems.Id id = ArmoryItems.identify(piece);
            if (id != null && id.piece.armour() && home(id.set, p.getWorld())) home++;
        }
        if (home > 0) e.setDamage(e.getDamage() * (1.0 - HOME_ARMOUR_PER_PIECE * home));
    }

    /** Wielder side: the weapon powers and the home realm's edge. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onStrike(EntityDamageByEntityEvent e) {
        if (!(e.getDamager() instanceof Player) || !(e.getEntity() instanceof LivingEntity)) return;
        Player p = (Player) e.getDamager();
        ArmoryItems.Id id = ArmoryItems.identify(p.getInventory().getItemInMainHand());
        if (id == null || !id.piece.weapon()) return;
        LivingEntity target = (LivingEntity) e.getEntity();
        double multiplier = home(id.set, p.getWorld()) ? HOME_WEAPON : 1.0;
        switch (id.set) {
            case BLAZEFORGED:
                if (target.getFireTicks() > 0) multiplier *= 1.20;
                target.setFireTicks(Math.max(target.getFireTicks(), 80));
                break;
            case ABYSSAL: {
                Material at = target.getLocation().getBlock().getType();
                if (at == Material.WATER || at == Material.STATIONARY_WATER) multiplier *= 1.25;
                target.addPotionEffect(new PotionEffect(PotionEffectType.SLOW, 40, 1), true);
                break;
            }
            case TITAN: {
                AttributeInstance armour = target.getAttribute(Attribute.GENERIC_ARMOR);
                if (armour != null && armour.getValue() >= 10) multiplier *= 1.25;
                target.addPotionEffect(new PotionEffect(PotionEffectType.SLOW, 20, 0), true);
                break;
            }
            case LIMINAL:
                if (target.getLocation().getBlock().getLightLevel() < 5) multiplier *= 1.25;
                if (random.nextDouble() < 0.20) { target.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 40, 0), true); weaponProcs++; }
                break;
            case VOID:
                if (random.nextDouble() < 0.15) {
                    multiplier *= 1.60; weaponProcs++;
                    target.getWorld().spawnParticle(Particle.PORTAL, target.getLocation().add(0, 1, 0), 30, 0.3, 0.6, 0.3, 0.6);
                    target.getWorld().playSound(target.getLocation(), Sound.ENTITY_ENDERMEN_HURT, 0.6f, 1.4f);
                }
                break;
            default:
        }
        if (multiplier != 1.0) e.setDamage(e.getDamage() * multiplier);
    }

    // ================================================================== drops from creatures

    @EventHandler(priority = EventPriority.NORMAL)
    public void onDeath(EntityDeathEvent e) {
        LivingEntity dead = e.getEntity();
        if (dead instanceof Player) return;
        Player killer = dead.getKiller();
        if (killer != null) {
            ArmoryItems.Id weapon = ArmoryItems.identify(killer.getInventory().getItemInMainHand());
            if (weapon != null && weapon.set == ArmorySet.EMERALD && weapon.piece.weapon()) {
                e.setDroppedExp((int) Math.round(e.getDroppedExp() * 1.3));
                if (GearExtras.hostileAnywhere(dead) && random.nextDouble() < 0.12) e.getDrops().add(new ItemStack(Material.EMERALD));
            }
        }
        ArmorySet set = ArmorySet.ofWorld(dead.getWorld().getName());
        if (set == null) return;
        if (dead instanceof EnderDragon) {           // the dragon always leaves one Void piece
            dropAt(dead, set, randomPiece(random), "dragon");
            return;
        }
        if (killer == null) return;
        boolean boss = GearPlugin.boss(dead) || dead.getScoreboardTags().contains("br_boss") || dead.getScoreboardTags().contains("jn_lord")
            || hasTagPrefix(dead, "atlas_boss:");
        boolean elite = dead.getScoreboardTags().contains("jn_elite") || dead.getScoreboardTags().contains("jaspr_horror_elite")
            || dead.getScoreboardTags().contains("br_elite");
        if (!boss && (!GearExtras.hostileAnywhere(dead) || plugin.spawned(dead))) return;
        String world = set == ArmorySet.EMERALD ? "overworld" : "realm";
        double pieceChance = boss ? cfg("armory.drops.boss-piece", 0.20) : elite ? cfg("armory.drops.elite-piece", 0.015)
            : cfg("armory.drops." + world + "-piece", set == ArmorySet.EMERALD ? 0.0005 : 0.0012);
        if (random.nextDouble() < pieceChance) {
            ItemStack piece = ArmoryItems.create(set, randomPiece(random));
            e.getDrops().add(piece);
            pieceDrops++;
            plugin.getLogger().info("ARMORY_DROP item=" + set.id + "_" + ArmoryItems.identify(piece).piece.id + " world=" + dead.getWorld().getName()
                + " mob=" + dead.getType().name() + " boss=" + boss + " elite=" + elite + " killer=" + killer.getUniqueId());
        }
        if (set.customMaterial()) {
            double chance = boss ? 1.0 : elite ? cfg("armory.drops.elite-material", 0.35) : cfg("armory.drops.material", 0.05);
            if (random.nextDouble() < chance) {
                int count = boss ? 3 + random.nextInt(4) : elite ? 1 + random.nextInt(2) : 1;
                e.getDrops().add(ArmoryItems.material(set.materialId, count));
                materialDrops += count;
            }
        }
    }

    private void dropAt(LivingEntity dead, ArmorySet set, ArmoryPiece piece, String why) {
        Location at = dead.getLocation().add(0, 0.5, 0);
        if (dead.getWorld().getHighestBlockYAt(at) <= 0) {   // over the End's void: the killer gets it
            Player killer = dead.getKiller();
            at = killer != null && killer.getWorld() == dead.getWorld() ? killer.getLocation() : dead.getWorld().getSpawnLocation();
        } else if (at.getY() < 1) at.setY(dead.getWorld().getHighestBlockYAt(at) + 1);
        dead.getWorld().dropItemNaturally(at, ArmoryItems.create(set, piece));
        pieceDrops++;
        plugin.getLogger().info("ARMORY_DROP item=" + set.id + "_" + piece.id + " world=" + dead.getWorld().getName() + " mob=" + dead.getType().name()
            + " reason=" + why);
    }

    private static boolean hasTagPrefix(LivingEntity e, String prefix) {
        for (String t : e.getScoreboardTags()) if (t.startsWith(prefix)) return true;
        return false;
    }

    private double cfg(String path, double fallback) {
        return Math.max(0.0, Math.min(1.0, plugin.getConfig().getDouble(path, fallback)));
    }

    /** Armour and weapons a little likelier than the simplest tools. */
    static ArmoryPiece randomPiece(Random random) {
        int[] weights = {2, 2, 2, 2, 2, 2, 2, 1, 1};
        int total = 0;
        for (int w : weights) total += w;
        int roll = random.nextInt(total);
        for (int i = 0; i < weights.length; i++) { roll -= weights[i]; if (roll < 0) return ArmoryPiece.values()[i]; }
        return ArmoryPiece.HELMET;
    }

    // ================================================================== chest loot

    /**
     * One roll for a realm chest of this tier (0..5): a piece 1.5-7.5% of the time; in Drownhollow, Atlas and the
     * Backrooms, forging material 12-32% of the time. Uses ONLY the passed Random, in a fixed order (nextDouble, nextInt
     * only on a hit, nextDouble, nextInt only on a hit), so a seeded chest always rolls the same.
     */
    static List<ItemStack> roll(Random r, String world, int tier) {
        List<ItemStack> out = new ArrayList<ItemStack>(2);
        ArmorySet set = ArmorySet.ofWorld(world);
        if (r == null || set == null) return out;
        int t = Math.max(0, Math.min(5, tier));
        if (r.nextDouble() < 0.015 + 0.012 * t) out.add(ArmoryItems.create(set, randomPiece(r)));
        if (set.customMaterial() && r.nextDouble() < 0.12 + 0.04 * t) out.add(ArmoryItems.material(set.materialId, 1 + r.nextInt(1 + (t + 1) / 2)));
        return out;
    }

    /** Places loot into random empty slots of an inventory; whatever does not fit is dropped nowhere (counted). */
    static int place(Inventory inv, List<ItemStack> items, Random r) {
        int placed = 0;
        for (ItemStack item : items) {
            List<Integer> empty = new ArrayList<Integer>();
            for (int i = 0; i < inv.getSize(); i++) if (GearItems.empty(inv.getItem(i))) empty.add(i);
            if (empty.isEmpty()) break;
            inv.setItem(empty.get(r.nextInt(empty.size())), item);
            placed++;
        }
        return placed;
    }

    /** Chance that a vanilla loot table's chest also holds the realm's piece. */
    static double vanillaChance(String table) {
        if (table == null) return 0;
        String t = table.toLowerCase(java.util.Locale.ROOT);
        if (t.contains("spawn_bonus")) return 0;
        if (t.contains("end_city")) return 0.12;
        if (t.contains("woodland_mansion")) return 0.06;
        if (t.contains("stronghold")) return 0.05;
        if (t.contains("nether_bridge")) return 0.05;
        if (t.contains("desert_pyramid") || t.contains("jungle_temple")) return 0.04;
        if (t.contains("abandoned_mineshaft")) return 0.02;
        return 0.03;
    }

    private static String key(Block b) { return b.getWorld().getName() + ':' + b.getX() + ':' + b.getY() + ':' + b.getZ(); }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onClickChest(PlayerInteractEvent e) {
        if (e.getAction() != Action.RIGHT_CLICK_BLOCK || e.getClickedBlock() == null || e.useInteractedBlock() == org.bukkit.event.Event.Result.DENY) return;
        Block b = e.getClickedBlock();
        if (b.getType() != Material.CHEST && b.getType() != Material.TRAPPED_CHEST) return;
        if (ArmorySet.ofWorld(b.getWorld().getName()) == null) return;
        try {
            BlockState state = b.getState();
            if (!(state instanceof Chest)) return;
            Chest chest = (Chest) state;
            if (!chest.hasLootTable() || chest.hasBeenFilled()) return;
            if (freshLoot.size() > 256) freshLoot.clear();
            freshLoot.put(key(b), chest.getLootTableName());
        } catch (RuntimeException | LinkageError ignored) {
            // a server without Paper's lootable API simply never rolls here
        }
    }

    /** HIGHEST, not MONITOR: JasprRPG enhances realm pieces in a container at MONITOR, so ours must be in by then. */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onOpen(InventoryOpenEvent e) {
        if (freshLoot.isEmpty()) return;
        InventoryHolder holder = e.getInventory().getHolder();
        Block block = holder instanceof Chest ? ((Chest) holder).getBlock()
            : holder instanceof DoubleChest && ((DoubleChest) holder).getLeftSide() instanceof Chest ? ((Chest) ((DoubleChest) holder).getLeftSide()).getBlock() : null;
        String table = null;
        if (block != null) table = freshLoot.remove(key(block));
        if (table == null && holder instanceof DoubleChest && ((DoubleChest) holder).getRightSide() instanceof Chest)
            table = freshLoot.remove(key(((Chest) ((DoubleChest) holder).getRightSide()).getBlock()));
        if (table == null) return;
        ArmorySet set = ArmorySet.ofWorld(e.getPlayer().getWorld().getName());
        if (set == null) return;
        e.getInventory().getContents();   // the loot table fills on first access; ours goes into what is left empty
        vanillaChests++;
        List<ItemStack> loot = new ArrayList<ItemStack>();
        if (random.nextDouble() < vanillaChance(table)) loot.add(ArmoryItems.create(set, randomPiece(random)));
        // Drownhollow's chests use vanilla tables: its pearls come this way (the other realms add theirs as they fill)
        int mats = 0;
        if (set.customMaterial() && random.nextDouble() < 0.18) { mats = 1 + random.nextInt(3); loot.add(ArmoryItems.material(set.materialId, mats)); }
        if (loot.isEmpty() || place(e.getInventory(), loot, random) == 0) return;
        ArmoryItems.Id piece = ArmoryItems.identify(loot.get(0));
        if (piece != null) chestPieces++;
        chestMaterials += mats;
        plugin.getLogger().info("ARMORY_CHEST source=vanilla table=" + table + " world=" + e.getPlayer().getWorld().getName()
            + " item=" + (piece == null ? "-" : piece.key()) + " materials=" + mats + " player=" + e.getPlayer().getUniqueId());
    }

    // ================================================================== tools

    private static final Set<Material> ORES = EnumSet.of(Material.COAL_ORE, Material.IRON_ORE, Material.GOLD_ORE, Material.DIAMOND_ORE,
        Material.EMERALD_ORE, Material.LAPIS_ORE, Material.REDSTONE_ORE, Material.GLOWING_REDSTONE_ORE, Material.QUARTZ_ORE);
    private static final Set<Material> PICK_AREA = EnumSet.of(Material.STONE, Material.COBBLESTONE, Material.MOSSY_COBBLESTONE,
        Material.NETHERRACK, Material.SANDSTONE, Material.RED_SANDSTONE, Material.ENDER_STONE, Material.HARD_CLAY, Material.STAINED_CLAY,
        Material.PRISMARINE, Material.SMOOTH_BRICK, Material.NETHER_BRICK, Material.PACKED_ICE, Material.ICE, Material.MAGMA,
        Material.COAL_ORE, Material.IRON_ORE, Material.GOLD_ORE, Material.DIAMOND_ORE, Material.EMERALD_ORE, Material.LAPIS_ORE,
        Material.REDSTONE_ORE, Material.GLOWING_REDSTONE_ORE, Material.QUARTZ_ORE);
    private static final Set<Material> SHOVEL_AREA = EnumSet.of(Material.DIRT, Material.GRASS, Material.SAND, Material.GRAVEL, Material.CLAY,
        Material.SOUL_SAND, Material.MYCEL, Material.SNOW, Material.SNOW_BLOCK, Material.SOIL, Material.GRASS_PATH, Material.CONCRETE_POWDER);
    private static final int MAX_VEIN = 16, MAX_TREE = 64;

    static boolean ore(Material m) { return ORES.contains(m); }
    private static boolean sameOre(Material a, Material b) {
        boolean ra = a == Material.REDSTONE_ORE || a == Material.GLOWING_REDSTONE_ORE, rb = b == Material.REDSTONE_ORE || b == Material.GLOWING_REDSTONE_ORE;
        return ra ? rb : a == b;
    }
    private static boolean log(Material m) { return m == Material.LOG || m == Material.LOG_2; }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBreakExp(BlockBreakEvent e) {
        ArmoryItems.Id id = ArmoryItems.identify(e.getPlayer().getInventory().getItemInMainHand());
        if (id == null || !id.piece.tool()) return;
        Material m = e.getBlock().getType();
        if (id.set == ArmorySet.BLAZEFORGED && (m == Material.IRON_ORE || m == Material.GOLD_ORE) && e.isDropItems())
            e.setExpToDrop(e.getExpToDrop() + 1);
        if (id.set == ArmorySet.VOID && e.getExpToDrop() > 0) {
            e.getPlayer().giveExp(e.getExpToDrop());
            e.setExpToDrop(0);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent e) {
        Player p = e.getPlayer();
        ItemStack tool = p.getInventory().getItemInMainHand();
        ArmoryItems.Id id = ArmoryItems.identify(tool);
        if (id == null || !id.piece.tool()) return;
        Block block = e.getBlock();
        Material type = block.getType();
        if (e.isDropItems() && (id.set == ArmorySet.EMERALD || id.set == ArmorySet.BLAZEFORGED || id.set == ArmorySet.VOID)) {
            breaks.add(new Break(p, id.set, block, type, tool.containsEnchantment(Enchantment.SILK_TOUCH)));
            if (!clearQueued) { clearQueued = true; plugin.getServer().getScheduler().runTask(plugin, () -> { breaks.clear(); clearQueued = false; }); }
        }
        if (id.set == ArmorySet.ABYSSAL) {
            Material eye = p.getEyeLocation().getBlock().getType();
            if (eye == Material.WATER || eye == Material.STATIONARY_WATER) p.setRemainingAir(Math.min(p.getMaximumAir(), p.getRemainingAir() + 60));
        }
        if (id.set == ArmorySet.LIMINAL) torchLater(p, block);
        if (id.set == ArmorySet.TITAN && !breaking) titan(p, id.piece, block, type);
    }

    /** Titan's Strength: ore veins and whole trees standing, a 3x3 face while sneaking. */
    private void titan(Player p, ArmoryPiece piece, Block origin, Material type) {
        List<Block> more = new ArrayList<Block>();
        if (piece == ArmoryPiece.PICKAXE && !p.isSneaking() && ore(type)) {
            more = flood(origin, b -> sameOre(b.getType(), type), MAX_VEIN, 32);
            if (!more.isEmpty()) veins++;
        } else if (piece == ArmoryPiece.AXE && !p.isSneaking() && log(type)) {
            List<Block> logs = flood(origin, b -> b.getType() == type, MAX_TREE, 6);
            if (!logs.isEmpty() && leavesAround(logs) >= 4) { more = logs; trees++; }
        } else if (p.isSneaking() && (piece == ArmoryPiece.PICKAXE && PICK_AREA.contains(type) || piece == ArmoryPiece.SHOVEL && SHOVEL_AREA.contains(type))) {
            Set<Material> allowed = piece == ArmoryPiece.PICKAXE ? PICK_AREA : SHOVEL_AREA;
            for (Block b : face(p, origin)) if (allowed.contains(b.getType())) more.add(b);
            if (!more.isEmpty()) areas++;
        }
        if (more.isEmpty()) return;
        breaking = true;
        try {
            net.minecraft.server.v1_12_R1.EntityPlayer handle = ((CraftPlayer) p).getHandle();
            for (Block b : more) {
                if (b.getState() instanceof InventoryHolder) continue;   // never a chest, a turret or any container
                handle.playerInteractManager.breakBlock(new net.minecraft.server.v1_12_R1.BlockPosition(b.getX(), b.getY(), b.getZ()));
            }
        } finally {
            breaking = false;
        }
    }

    interface Match { boolean test(Block b); }

    /** Connected blocks (26-neighbourhood) matching, origin excluded, within a horizontal radius. */
    private static List<Block> flood(Block origin, Match match, int max, int radius) {
        List<Block> found = new ArrayList<Block>();
        Set<String> seen = new HashSet<String>();
        ArrayDeque<Block> queue = new ArrayDeque<Block>();
        queue.add(origin);
        seen.add(key(origin));
        while (!queue.isEmpty() && found.size() < max) {
            Block at = queue.poll();
            for (int dx = -1; dx <= 1; dx++) for (int dy = -1; dy <= 1; dy++) for (int dz = -1; dz <= 1; dz++) {
                if (dx == 0 && dy == 0 && dz == 0) continue;
                Block n = at.getRelative(dx, dy, dz);
                if (Math.abs(n.getX() - origin.getX()) > radius || Math.abs(n.getZ() - origin.getZ()) > radius || n.getY() < 1 || n.getY() > 254) continue;
                if (!seen.add(key(n)) || !match.test(n)) continue;
                found.add(n);
                queue.add(n);
                if (found.size() >= max) break;
            }
        }
        return found;
    }

    private static int leavesAround(List<Block> logs) {
        int leaves = 0;
        for (Block log : logs) for (BlockFace f : new BlockFace[]{BlockFace.UP, BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST}) {
            Material m = log.getRelative(f).getType();
            if (m == Material.LEAVES || m == Material.LEAVES_2) leaves++;
        }
        return leaves;
    }

    /** The eight blocks around the origin in the plane the player is digging into. */
    private static List<Block> face(Player p, Block origin) {
        List<Block> out = new ArrayList<Block>(8);
        float pitch = p.getLocation().getPitch();
        org.bukkit.util.Vector dir = p.getLocation().getDirection();
        for (int a = -1; a <= 1; a++) for (int b = -1; b <= 1; b++) {
            if (a == 0 && b == 0) continue;
            if (Math.abs(pitch) > 50) out.add(origin.getRelative(a, 0, b));
            else if (Math.abs(dir.getX()) > Math.abs(dir.getZ())) out.add(origin.getRelative(0, a, b));
            else out.add(origin.getRelative(a, b, 0));
        }
        return out;
    }

    /** Titan hoes till the 3x3 around the clicked earth while sneaking. */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onTill(PlayerInteractEvent e) {
        if (e.getAction() != Action.RIGHT_CLICK_BLOCK || e.getClickedBlock() == null || !e.getPlayer().isSneaking()) return;
        if (e.useItemInHand() == org.bukkit.event.Event.Result.DENY) return;
        ArmoryItems.Id id = ArmoryItems.identify(e.getItem());
        if (id == null || id.set != ArmorySet.TITAN || id.piece != ArmoryPiece.HOE) return;
        Block center = e.getClickedBlock();
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            int n = 0;
            for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) {
                Block b = center.getRelative(dx, 0, dz);
                Material m = b.getType();
                if ((m == Material.GRASS || m == Material.DIRT && b.getData() == 0 || m == Material.GRASS_PATH) && b.getRelative(BlockFace.UP).getType() == Material.AIR) {
                    b.setType(Material.SOIL);
                    n++;
                }
            }
            if (n > 0) tilled += n;
        });
    }

    /** Pathfinder: a torch from the player's pack where they just dug in the dark (one per two seconds at most). */
    private void torchLater(Player p, Block block) {
        long now = System.currentTimeMillis();
        Long last = torchAt.get(p.getUniqueId());
        if (last != null && now - last < 2000) return;
        if (!p.getInventory().contains(Material.TORCH)) return;
        torchAt.put(p.getUniqueId(), now);
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (!p.isOnline() || block.getType() != Material.AIR || block.getLightLevel() >= 5) return;
            Block below = block.getRelative(BlockFace.DOWN);
            if (!below.getType().isSolid() || !below.getType().isOccluding()) return;
            int slot = p.getInventory().first(Material.TORCH);
            if (slot < 0) return;
            ItemStack stack = p.getInventory().getItem(slot);
            if (stack.getAmount() <= 1) p.getInventory().setItem(slot, null); else stack.setAmount(stack.getAmount() - 1);
            block.setType(Material.TORCH);
            torches++;
        });
    }

    /** Smelting and Prospector reshape the drops of a broken block. */
    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onDropShape(ItemSpawnEvent e) {
        if (breaks.isEmpty() || pulling) return;
        Break b = breakAt(e.getLocation());
        if (b == null) return;
        Item item = e.getEntity();
        ItemStack stack = item.getItemStack();
        if (b.set == ArmorySet.BLAZEFORGED) {
            Material to = stack.getType() == Material.IRON_ORE ? Material.IRON_INGOT : stack.getType() == Material.GOLD_ORE ? Material.GOLD_INGOT
                : stack.getType() == Material.SAND ? Material.GLASS : null;
            if (to != null) { item.setItemStack(new ItemStack(to, stack.getAmount())); smelted++; }
        } else if (b.set == ArmorySet.EMERALD && !b.silk && !b.prospectDone && ore(b.type)) {
            b.prospectDone = true;
            if (random.nextDouble() < 0.20 && stack.getAmount() < stack.getMaxStackSize()) {
                stack.setAmount(stack.getAmount() + 1);
                item.setItemStack(stack);
                prospected++;
            }
        }
    }

    /** Void Pull: the drops go straight into the miner's pack. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDropPull(ItemSpawnEvent e) {
        if (breaks.isEmpty() || pulling) return;
        Break b = breakAt(e.getLocation());
        if (b == null || b.set != ArmorySet.VOID || !b.player.isOnline()) return;
        e.setCancelled(true);
        pulling = true;
        try {
            for (ItemStack left : b.player.getInventory().addItem(e.getEntity().getItemStack()).values())
                b.player.getWorld().dropItem(b.player.getLocation(), left);
        } finally {
            pulling = false;
        }
        pulled++;
    }

    private Break breakAt(Location l) {
        for (Break b : breaks) {
            if (b.block.getWorld() != l.getWorld()) continue;
            double dx = l.getX() - (b.block.getX() + 0.5), dy = l.getY() - (b.block.getY() + 0.5), dz = l.getZ() - (b.block.getZ() + 0.5);
            if (dx * dx + dy * dy + dz * dz <= 1.44) return b;
        }
        return null;
    }

    // ================================================================== status

    String metrics() {
        return "armoryRecipes=" + recipes + " forged=" + forged + " pieceDrops=" + pieceDrops + " materialDrops=" + materialDrops
            + " chestPieces=" + chestPieces + " chestMaterials=" + chestMaterials + " vanillaChests=" + vanillaChests + " veins=" + veins
            + " trees=" + trees + " areas=" + areas + " tilled=" + tilled + " smelted=" + smelted + " prospected=" + prospected + " pulled=" + pulled
            + " torches=" + torches + " rescues=" + rescues + " guarded=" + guarded + " cleansed=" + cleansed + " weaponProcs=" + weaponProcs + " upgraded=" + upgraded;
    }

    /** /gear armory give: every piece of a set (or one piece), or a stack of a realm material. */
    static List<ItemStack> kit(String what, String piece) {
        List<ItemStack> out = new ArrayList<ItemStack>();
        if (ArmoryItems.setOfMaterial(what) != null) { out.add(ArmoryItems.material(what, 64)); return out; }
        for (ArmorySet s : ArmorySet.values()) {
            if (!what.equalsIgnoreCase("all") && !s.id.equalsIgnoreCase(what)) continue;
            for (ArmoryPiece p : ArmoryPiece.values()) if (piece == null || piece.equalsIgnoreCase("all") || p.id.equalsIgnoreCase(piece)) out.add(ArmoryItems.create(s, p));
        }
        return out;
    }

}
