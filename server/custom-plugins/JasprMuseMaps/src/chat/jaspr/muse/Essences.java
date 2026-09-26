package chat.jaspr.muse;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import net.minecraft.server.v1_12_R1.NBTTagCompound;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.craftbukkit.v1_12_R1.inventory.CraftItemStack;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

/**
 * Essences: permanent character upgrades (owner brief: "more ways to upgrade your character"). Six kinds, five ranks
 * each, dropped by the ten Muse+GLM_Maps bosses and found (rarely) in Muse+GLM_Maps vaults. Right-click to absorb.
 * Ranks are kept per player in plugins/JasprMuseMaps/essences/<uuid>.yml and survive death; they are applied as
 * attribute modifiers with fixed UUIDs, so they never stack with themselves and never touch other plugins' modifiers.
 */
public final class Essences implements Listener {
    public enum Kind {
        VITALITY("Essence of Vitality", Material.SPECKLED_MELON, ChatColor.RED, "+2 max health per rank",
            Attribute.GENERIC_MAX_HEALTH, 0, 2.0, "4d7a1e6e-8f2d-4c1e-9b1a-7a1e6e8f2d01"),
        MIGHT("Essence of Might", Material.BLAZE_POWDER, ChatColor.GOLD, "+5% attack damage per rank",
            Attribute.GENERIC_ATTACK_DAMAGE, 2, 0.05, "4d7a1e6e-8f2d-4c1e-9b1a-7a1e6e8f2d02"),
        CELERITY("Essence of Celerity", Material.SUGAR, ChatColor.AQUA, "+3% movement speed per rank",
            Attribute.GENERIC_MOVEMENT_SPEED, 2, 0.03, "4d7a1e6e-8f2d-4c1e-9b1a-7a1e6e8f2d03"),
        BULWARK("Essence of the Bulwark", Material.SHULKER_SHELL, ChatColor.GRAY, "+1 armor per rank",
            Attribute.GENERIC_ARMOR, 0, 1.0, "4d7a1e6e-8f2d-4c1e-9b1a-7a1e6e8f2d04"),
        RESOLVE("Essence of Resolve", Material.GHAST_TEAR, ChatColor.WHITE, "+1 armor toughness and +4% knockback resistance per rank",
            Attribute.GENERIC_ARMOR_TOUGHNESS, 0, 1.0, "4d7a1e6e-8f2d-4c1e-9b1a-7a1e6e8f2d05"),
        FORTUNE("Essence of Fortune", Material.RABBIT_FOOT, ChatColor.GREEN, "+1 luck per rank (better loot and fishing)",
            Attribute.GENERIC_LUCK, 0, 1.0, "4d7a1e6e-8f2d-4c1e-9b1a-7a1e6e8f2d06");

        final String title, text; final Material material; final ChatColor color; final Attribute attribute; final int op; final double per; final UUID uuid;
        Kind(String title, Material material, ChatColor color, String text, Attribute attribute, int op, double per, String uuid) {
            this.title = title; this.material = material; this.color = color; this.text = text; this.attribute = attribute;
            this.op = op; this.per = per; this.uuid = UUID.fromString(uuid);
        }
    }
    public static final int MAX_RANK = 5;
    private static final UUID RESOLVE_KB = UUID.fromString("4d7a1e6e-8f2d-4c1e-9b1a-7a1e6e8f2d07");

    private final MusePlugin plugin;
    private final File folder;

    Essences(MusePlugin plugin) { this.plugin = plugin; this.folder = new File(plugin.getDataFolder(), "essences"); folder.mkdirs(); }

    static Kind random(Random r) { return Kind.values()[r.nextInt(Kind.values().length)]; }

    public static ItemStack item(Kind kind, int amount) {
        ItemStack item = new ItemStack(kind.material, amount);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(kind.color + kind.title);
        List<String> lore = new ArrayList<>();
        lore.add(ChatColor.GRAY + kind.text);
        lore.add(ChatColor.DARK_GRAY + "Right-click to absorb (permanent, up to rank " + MAX_RANK + ")");
        lore.add(ChatColor.DARK_PURPLE + "Muse+GLM_Maps essence");
        meta.setLore(lore);
        meta.addEnchant(Enchantment.DURABILITY, 1, true);
        meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        item.setItemMeta(meta);
        net.minecraft.server.v1_12_R1.ItemStack nms = CraftItemStack.asNMSCopy(item);
        NBTTagCompound root = nms.getTag();
        NBTTagCompound tag = new NBTTagCompound();
        tag.setString("essence", kind.name());
        root.set("JasprMuse", tag);
        nms.setTag(root);
        return CraftItemStack.asBukkitCopy(nms);
    }

    static Kind kindOf(ItemStack item) {
        if (item == null || item.getType() == Material.AIR) return null;
        net.minecraft.server.v1_12_R1.ItemStack nms = CraftItemStack.asNMSCopy(item);
        if (nms == null || !nms.hasTag() || !nms.getTag().hasKeyOfType("JasprMuse", 10)) return null;
        String k = nms.getTag().getCompound("JasprMuse").getString("essence");
        try { return k.isEmpty() ? null : Kind.valueOf(k); } catch (IllegalArgumentException e) { return null; }
    }

    private File file(UUID id) { return new File(folder, id + ".yml"); }

    int rank(UUID id, Kind k) { return YamlConfiguration.loadConfiguration(file(id)).getInt(k.name(), 0); }

    private void setRank(UUID id, Kind k, int rank) throws IOException {
        YamlConfiguration y = YamlConfiguration.loadConfiguration(file(id));
        y.set(k.name(), rank);
        y.save(file(id));
    }

    void apply(Player p) {
        YamlConfiguration y = YamlConfiguration.loadConfiguration(file(p.getUniqueId()));
        for (Kind k : Kind.values()) {
            AttributeInstance a = p.getAttribute(k.attribute);
            if (a == null) continue;
            for (AttributeModifier m : new ArrayList<>(a.getModifiers())) if (m.getUniqueId().equals(k.uuid)) a.removeModifier(m);
            int rank = Math.max(0, Math.min(MAX_RANK, y.getInt(k.name(), 0)));
            if (rank > 0) a.addModifier(new AttributeModifier(k.uuid, "jaspr_muse_" + k.name().toLowerCase(), k.per * rank, AttributeModifier.Operation.values()[k.op]));
            if (k == Kind.RESOLVE) {
                AttributeInstance kb = p.getAttribute(Attribute.GENERIC_KNOCKBACK_RESISTANCE);
                if (kb == null) continue;
                for (AttributeModifier m : new ArrayList<>(kb.getModifiers())) if (m.getUniqueId().equals(RESOLVE_KB)) kb.removeModifier(m);
                if (rank > 0) kb.addModifier(new AttributeModifier(RESOLVE_KB, "jaspr_muse_resolve_kb", 0.04 * rank, AttributeModifier.Operation.ADD_NUMBER));
            }
        }
    }

    String summary(UUID id) {
        StringBuilder s = new StringBuilder();
        for (Kind k : Kind.values()) s.append(k.color).append(k.title.replace("Essence of ", "").replace("the ", "")).append(ChatColor.GRAY).append(' ').append(rank(id, k)).append('/').append(MAX_RANK).append("  ");
        return s.toString().trim();
    }

    @EventHandler public void join(PlayerJoinEvent e) { plugin.later(20, () -> { if (e.getPlayer().isOnline()) apply(e.getPlayer()); }); }
    @EventHandler public void respawn(PlayerRespawnEvent e) { plugin.later(2, () -> { if (e.getPlayer().isOnline()) apply(e.getPlayer()); }); }

    @EventHandler(priority = EventPriority.HIGH)
    public void absorb(PlayerInteractEvent e) {
        if (e.getAction() != Action.RIGHT_CLICK_AIR && e.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (e.getHand() != EquipmentSlot.HAND) return;
        ItemStack held = e.getPlayer().getInventory().getItemInMainHand();
        Kind kind = kindOf(held);
        if (kind == null) return;
        e.setCancelled(true);
        Player p = e.getPlayer();
        int rank = rank(p.getUniqueId(), kind);
        if (rank >= MAX_RANK) { p.sendMessage(ChatColor.GRAY + kind.title + " is already at rank " + MAX_RANK + "."); return; }
        try { setRank(p.getUniqueId(), kind, rank + 1); }
        catch (IOException ex) { plugin.getLogger().warning("MUSE_ESSENCE_SAVE_FAILED " + ex.getClass().getSimpleName()); return; }
        held.setAmount(held.getAmount() - 1);
        p.getInventory().setItemInMainHand(held.getAmount() > 0 ? held : null);
        apply(p);
        p.getWorld().spawnParticle(Particle.TOTEM, p.getLocation().add(0, 1, 0), 40, 0.4, 0.8, 0.4, 0.2);
        p.getWorld().playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1, 1.4f);
        p.sendMessage(kind.color + kind.title + ChatColor.GRAY + " absorbed: rank " + (rank + 1) + "/" + MAX_RANK + " (" + kind.text + ").");
        plugin.getLogger().info("MUSE_ESSENCE kind=" + kind.name() + " rank=" + (rank + 1));
    }

    void applyAll() { for (Player p : Bukkit.getOnlinePlayers()) apply(p); }
}
