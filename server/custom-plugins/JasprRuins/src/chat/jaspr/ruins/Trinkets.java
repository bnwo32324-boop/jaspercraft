package chat.jaspr.ruins;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityTargetLivingEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

/**
 * Relics found only in Drownhollow (on horrors, Wardens, the Herald and in cult chests), the five Seals of the Great Door,
 * and the Herald's rewards. Relics work wherever they are carried; they are recognised by a lore line vanilla survival
 * cannot forge ("Relic of Drownhollow - <key>", "Seal of Drownhollow - <key>").
 */
final class Trinkets implements Listener {
    static final String RELIC = "Relic of Drownhollow - ", SEAL = "Seal of Drownhollow - ";
    /** The marks items carried before the city was renamed (2026-09-28): still recognised. */
    static final String OLD_RELIC = "Relic of Ul'Nhaar - ", OLD_SEAL = "Seal of Ul'Nhaar - ";

    enum Trinket {
        WARDSTONE("Choir Wardstone", Material.QUARTZ, 0, "Carried: your Dread rises at half speed", "and never sickens you."),
        TIDE_PEARL("Pearl of the Drowned", Material.PRISMARINE_CRYSTALS, 0, "Carried: you breathe under water."),
        TENTACLE_CHARM("Tentacle Charm", Material.RABBIT_FOOT, 0, "Off hand: you regenerate while", "below half health."),
        STAR_SHARD("Star-Metal Shard", Material.IRON_NUGGET, 0, "Off hand: Strength."),
        NIGHTGAUNT_PINION("Nightgaunt Pinion", Material.FEATHER, 0, "Carried: you take no fall damage."),
        GHOUL_TOOTH("Ghoul's Tooth", Material.BONE, 0, "Off hand: your blows heal you by", "a fifth of the harm they deal."),
        MIGO_CYLINDER("Mi-Go Brain Cylinder", Material.FIREWORK_CHARGE, 0, "Right-click: blink eight blocks ahead.", "Recharges in 12 seconds."),
        FACELESS_MASK("Faceless Mask", Material.SKULL_ITEM, 1, "Worn: horrors do not see you", "until you strike one."),
        IDOL_OF_THE_DREAMER("Idol of the Dreamer", Material.PRISMARINE, 2, "Carried: Resistance, and the Dread", "cannot touch you."),
        CROWN("Crown of the Drowned Star", Material.DIAMOND_HELMET, 0, "Worn: you breathe under water and", "the Dread cannot touch you.");
        final String title;
        final Material material;
        final short data;
        final String[] text;
        Trinket(String title, Material material, int data, String... text) { this.title = title; this.material = material; this.data = (short) data; this.text = text; }
        String key() { return name().toLowerCase(Locale.ROOT); }
    }

    /** Relics that drop from horrors and chests (the mask, idol and crown come only from the Herald). */
    static final Trinket[] COMMON = {Trinket.WARDSTONE, Trinket.TIDE_PEARL, Trinket.TENTACLE_CHARM, Trinket.STAR_SHARD,
        Trinket.NIGHTGAUNT_PINION, Trinket.GHOUL_TOOTH, Trinket.MIGO_CYLINDER};

    enum Seal {
        TIDES("Seal of Tides", Material.PRISMARINE_SHARD, "the Hierophant"), STONE("Seal of Stone", Material.FLINT, "the Pillar Warden"),
        HUNGER("Seal of Hunger", Material.SPIDER_EYE, "the Brood Mother"), DEEP("Seal of the Deep", Material.SLIME_BALL, "the Spawn of the Deep"),
        SILENCE("Seal of Silence", Material.GHAST_TEAR, "the Faceless Priest");
        final String title, keeper;
        final Material material;
        Seal(String title, Material material, String keeper) { this.title = title; this.material = material; this.keeper = keeper; }
        String key() { return name().toLowerCase(Locale.ROOT); }
    }

    // ------------------------------------------------------------------ items

    static ItemStack item(Trinket t) {
        ItemStack item = new ItemStack(t.material, 1, t.data);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(ChatColor.LIGHT_PURPLE + t.title);
        List<String> lore = new ArrayList<>();
        for (String line : t.text) lore.add(ChatColor.GRAY + line);
        lore.add(ChatColor.DARK_GRAY + RELIC + t.key());
        meta.setLore(lore);
        if (t == Trinket.CROWN) {
            meta.addEnchant(Enchantment.PROTECTION_ENVIRONMENTAL, 4, true);
            meta.addEnchant(Enchantment.OXYGEN, 3, true);
            meta.addEnchant(Enchantment.WATER_WORKER, 1, true);
            meta.addEnchant(Enchantment.DURABILITY, 3, true);
        } else {
            meta.addEnchant(Enchantment.DURABILITY, 1, true);
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        }
        item.setItemMeta(meta);
        return item;
    }

    static ItemStack seal(Seal s) {
        ItemStack item = new ItemStack(s.material);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(ChatColor.DARK_AQUA + s.title);
        meta.setLore(Arrays.asList(ChatColor.GRAY + "Taken from " + s.keeper + ".", ChatColor.GRAY + "Right-click the Great Door with it.",
            ChatColor.DARK_GRAY + SEAL + s.key()));
        meta.addEnchant(Enchantment.DURABILITY, 1, true);
        meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        item.setItemMeta(meta);
        return item;
    }

    static ItemStack random(Random r) { return item(COMMON[r.nextInt(COMMON.length)]); }

    private static String marker(ItemStack item, String prefix) {
        if (item == null || !item.hasItemMeta()) return null;
        ItemMeta meta = item.getItemMeta();
        if (!meta.hasLore()) return null;
        for (String line : meta.getLore()) {
            String plain = ChatColor.stripColor(line);
            if (plain.startsWith(prefix)) return plain.substring(prefix.length());
        }
        return null;
    }

    static Trinket trinketOf(ItemStack item) {
        String k = marker(item, RELIC);
        if (k == null) k = marker(item, OLD_RELIC);
        if (k == null) return null;
        try { return Trinket.valueOf(k.toUpperCase(Locale.ROOT)); } catch (IllegalArgumentException e) { return null; }
    }

    static Seal sealOf(ItemStack item) {
        String k = marker(item, SEAL);
        if (k == null) k = marker(item, OLD_SEAL);
        if (k == null) return null;
        try { return Seal.valueOf(k.toUpperCase(Locale.ROOT)); } catch (IllegalArgumentException e) { return null; }
    }

    static boolean carries(Player p, Trinket t) {
        PlayerInventory inv = p.getInventory();
        for (ItemStack item : inv.getContents()) if (trinketOf(item) == t) return true;
        for (ItemStack item : inv.getArmorContents()) if (trinketOf(item) == t) return true;
        return false;
    }

    static boolean offhand(Player p, Trinket t) { return trinketOf(p.getInventory().getItemInOffHand()) == t; }
    static boolean wearing(Player p, Trinket t) { return trinketOf(p.getInventory().getHelmet()) == t; }

    /** The distinct Seals a player carries. */
    static java.util.EnumSet<Seal> seals(Player p) {
        java.util.EnumSet<Seal> out = java.util.EnumSet.noneOf(Seal.class);
        for (ItemStack item : p.getInventory().getContents()) { Seal s = sealOf(item); if (s != null) out.add(s); }
        return out;
    }

    /** The Herald's hoard: what ending the Dream is worth. */
    static List<ItemStack> heraldRewards() {
        List<ItemStack> out = new ArrayList<>();
        out.add(item(Trinket.CROWN));
        ItemStack cleaver = new ItemStack(Material.DIAMOND_SWORD);
        ItemMeta m = cleaver.getItemMeta();
        m.setDisplayName(ChatColor.DARK_PURPLE + "Herald's Cleaver");
        m.setLore(Arrays.asList(ChatColor.GRAY + "Cut from the Herald's own claw.", ChatColor.DARK_GRAY + "Relic of Drownhollow"));
        m.addEnchant(Enchantment.DAMAGE_ALL, 5, true);
        m.addEnchant(Enchantment.LOOT_BONUS_MOBS, 3, true);
        m.addEnchant(Enchantment.FIRE_ASPECT, 2, true);
        m.addEnchant(Enchantment.DURABILITY, 3, true);
        cleaver.setItemMeta(m);
        out.add(cleaver);
        ItemStack wings = new ItemStack(Material.ELYTRA);
        m = wings.getItemMeta();
        m.setDisplayName(ChatColor.DARK_PURPLE + "Wings of the Nightgaunt");
        m.setLore(Arrays.asList(ChatColor.GRAY + "Leathery, silent, and faintly warm.", ChatColor.DARK_GRAY + "Relic of Drownhollow"));
        m.addEnchant(Enchantment.DURABILITY, 3, true);
        m.addEnchant(Enchantment.MENDING, 1, true);
        wings.setItemMeta(m);
        out.add(wings);
        ItemStack heart = new ItemStack(Material.NETHER_STAR);
        m = heart.getItemMeta();
        m.setDisplayName(ChatColor.GOLD + "Dreamer's Heart");
        m.setLore(Arrays.asList(ChatColor.GRAY + "It still beats, very slowly.", ChatColor.DARK_GRAY + "Relic of Drownhollow"));
        heart.setItemMeta(m);
        out.add(heart);
        out.add(item(Trinket.IDOL_OF_THE_DREAMER));
        out.add(item(Trinket.FACELESS_MASK));
        out.add(new ItemStack(Material.TOTEM, 2));
        out.add(new ItemStack(Material.GOLDEN_APPLE, 3, (short) 1));
        out.add(new ItemStack(Material.DIAMOND, 12));
        out.add(Lore.book(Lore.AFTER_THE_WAKING));
        return out;
    }

    // ------------------------------------------------------------------ effects

    private final RuinsPlugin plugin;
    private final Map<UUID, Long> blinkReady = new HashMap<>();
    private final Map<UUID, Long> provoked = new HashMap<>();

    Trinkets(RuinsPlugin plugin) { this.plugin = plugin; }

    private static void effect(Player p, PotionEffectType type, int amplifier) {
        p.addPotionEffect(new PotionEffect(type, 70, amplifier, true, false), true);
    }

    /** Every second: the potion-like powers of carried and held relics. */
    void tick() {
        for (Player p : plugin.getServer().getOnlinePlayers()) {
            if (carries(p, Trinket.TIDE_PEARL) || wearing(p, Trinket.CROWN)) effect(p, PotionEffectType.WATER_BREATHING, 0);
            if (carries(p, Trinket.IDOL_OF_THE_DREAMER)) effect(p, PotionEffectType.DAMAGE_RESISTANCE, 0);
            if (offhand(p, Trinket.STAR_SHARD)) effect(p, PotionEffectType.INCREASE_DAMAGE, 0);
            if (offhand(p, Trinket.TENTACLE_CHARM) && p.getHealth() < p.getMaxHealth() / 2) effect(p, PotionEffectType.REGENERATION, 0);
        }
    }

    /** Whether the Dread cannot touch this player, or only rises at half speed. */
    static boolean dreadImmune(Player p) { return carries(p, Trinket.IDOL_OF_THE_DREAMER) || wearing(p, Trinket.CROWN); }
    static boolean dreadWarded(Player p) { return carries(p, Trinket.WARDSTONE); }

    @EventHandler(ignoreCancelled = true)
    public void fall(EntityDamageEvent e) {
        if (e.getCause() == EntityDamageEvent.DamageCause.FALL && e.getEntity() instanceof Player && carries((Player) e.getEntity(), Trinket.NIGHTGAUNT_PINION))
            e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void strike(EntityDamageByEntityEvent e) {
        if (!(e.getDamager() instanceof Player)) return;
        Player p = (Player) e.getDamager();
        if (Horrors.isHorror(e.getEntity())) provoked.put(p.getUniqueId(), System.currentTimeMillis());
        if (offhand(p, Trinket.GHOUL_TOOTH) && e.getEntity() instanceof LivingEntity)
            p.setHealth(Math.min(p.getMaxHealth(), p.getHealth() + e.getFinalDamage() * 0.2));
    }

    @EventHandler(ignoreCancelled = true)
    public void unseen(EntityTargetLivingEntityEvent e) {
        if (!(e.getTarget() instanceof Player) || !Horrors.isHorror(e.getEntity()) || Bosses.isBoss(e.getEntity())) return;
        Player p = (Player) e.getTarget();
        if (!wearing(p, Trinket.FACELESS_MASK)) return;
        Long last = provoked.get(p.getUniqueId());
        if (last == null || System.currentTimeMillis() - last > 10_000L) e.setCancelled(true);
    }

    @EventHandler
    public void blink(PlayerInteractEvent e) {
        if (e.getAction() != Action.RIGHT_CLICK_AIR && e.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        Player p = e.getPlayer();
        if (trinketOf(e.getItem()) != Trinket.MIGO_CYLINDER) return;
        e.setCancelled(true);
        long now = System.currentTimeMillis();
        Long ready = blinkReady.get(p.getUniqueId());
        if (ready != null && now < ready) { p.sendMessage(ChatColor.DARK_GRAY + "The cylinder is still humming."); return; }
        Location eye = p.getEyeLocation();
        Vector dir = eye.getDirection().normalize();
        Location best = null;
        for (double d = 1; d <= 8; d += 0.5) {
            Location at = eye.clone().add(dir.clone().multiply(d));
            Block feet = at.getBlock().getRelative(0, -1, 0), head = at.getBlock();
            if (!passable(head) ) break;
            if (passable(feet)) best = feet.getLocation().add(0.5, 0, 0.5);
        }
        if (best == null) return;
        best.setYaw(eye.getYaw());
        best.setPitch(eye.getPitch());
        p.getWorld().playSound(p.getLocation(), Sound.ENTITY_ENDERMEN_TELEPORT, 0.8f, 0.6f);
        p.setFallDistance(0f);
        p.teleport(best, PlayerTeleportEvent.TeleportCause.PLUGIN);
        blinkReady.put(p.getUniqueId(), now + 12_000L);
    }

    private static boolean passable(Block b) { return !b.getType().isSolid() && !b.isLiquid(); }

}
