package chat.jaspr.enchant.fx;

import chat.jaspr.enchant.nms.SmeEnchantment;
import chat.jaspr.enchant.util.Guard;
import chat.jaspr.enchant.util.Log;
import chat.jaspr.enchant.util.Nms;
import net.minecraft.server.v1_12_R1.BlockPosition;
import net.minecraft.server.v1_12_R1.Blocks;
import net.minecraft.server.v1_12_R1.Enchantment;
import net.minecraft.server.v1_12_R1.EnchantmentManager;
import net.minecraft.server.v1_12_R1.EntityHuman;
import net.minecraft.server.v1_12_R1.ItemStack;
import net.minecraft.server.v1_12_R1.Items;
import net.minecraft.server.v1_12_R1.NBTTagList;
import net.minecraft.server.v1_12_R1.World;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.craftbukkit.v1_12_R1.CraftWorld;
import org.bukkit.craftbukkit.v1_12_R1.inventory.CraftItemStack;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.Plugin;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * SME's enchanting-table upgrading as a chest menu (sneak + right-click an enchanting table): the vanilla
 * client cannot put prismarine shards into the lapis slot, so the rules of ContainerEnchantmentMixin run
 * here: same recipes, xp-seeded option picks, XP cost (level x rarity x 2), 1/8 prismarine shard tokens,
 * 10% tier failure into the curse equivalent, 30 bookshelf power, +10 anvil repair cost.
 */
public final class UpgradeGui implements Listener {
    static final int TARGET = 10, TOKEN = 12, INFO = 4;
    static final int[] OPTIONS = {14, 15, 16};
    private static Plugin plugin;
    private static final Map<String, String> LANG = new HashMap<>();
    public static long upgrades = 0, failures = 0;

    static final class Menu implements InventoryHolder {
        final Location table;
        Inventory inv;
        Upgrading.Option[] options = new Upgrading.Option[3];
        int power;

        Menu(Location table) {
            this.table = table;
        }

        @Override
        public Inventory getInventory() {
            return inv;
        }
    }

    public static void init(Plugin pl) {
        plugin = pl;
        try (InputStream in = pl.getResource("client-assets/en_us.lang")) {
            if (in != null) {
                BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
                String line;
                while ((line = r.readLine()) != null) {
                    int i = line.indexOf('=');
                    if (i > 0) LANG.put(line.substring(0, i).trim(), line.substring(i + 1).trim());
                }
            }
        } catch (Exception ex) {
            Log.error("upgrade.lang", ex);
        }
    }

    static String name(Enchantment e, int level) {
        String base;
        if (e instanceof SmeEnchantment) base = LANG.getOrDefault("enchantment." + ((SmeEnchantment) e).def.regName, ((SmeEnchantment) e).def.regName);
        else {
            String d = e.d(1);
            base = d == null ? "?" : d.replaceAll(" I$", "");
        }
        if (e.getMaxLevel() == 1 && level == 1) return base;
        return base + " " + roman(level);
    }

    static String roman(int n) {
        String[] r = {"", "I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X"};
        return n >= 0 && n < r.length ? r[n] : String.valueOf(n);
    }

    /** ContainerEnchantment bookshelf power (float sum, uncapped: up to 32) */
    static int power(World w, BlockPosition pos) {
        float power = 0;
        for (int j = -1; j <= 1; ++j) {
            for (int k = -1; k <= 1; ++k) {
                if ((j != 0 || k != 0) && w.isEmpty(pos.a(k, 0, j)) && w.isEmpty(pos.a(k, 1, j))) {
                    power += shelf(w, pos.a(k * 2, 0, j * 2));
                    power += shelf(w, pos.a(k * 2, 1, j * 2));
                    if (k != 0 && j != 0) {
                        power += shelf(w, pos.a(k * 2, 0, j));
                        power += shelf(w, pos.a(k * 2, 1, j));
                        power += shelf(w, pos.a(k, 0, j * 2));
                        power += shelf(w, pos.a(k, 1, j * 2));
                    }
                }
            }
        }
        return (int) power;
    }

    private static float shelf(World w, BlockPosition p) {
        return w.getType(p).getBlock() == Blocks.BOOKSHELF ? 1.0F : 0.0F;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEvent e) {
        if (e.getAction() != Action.RIGHT_CLICK_BLOCK || e.getClickedBlock() == null) return;
        if (e.getClickedBlock().getType() != Material.ENCHANTMENT_TABLE || !e.getPlayer().isSneaking()) return;
        if (e.getHand() != org.bukkit.inventory.EquipmentSlot.HAND) return;
        e.setCancelled(true);
        Guard.run("upgrade.open", () -> open(e.getPlayer(), e.getClickedBlock().getLocation()));
    }

    static void open(Player p, Location table) {
        Menu m = new Menu(table);
        m.inv = Bukkit.createInventory(m, 27, ChatColor.DARK_PURPLE + "Enchantment Upgrading");
        org.bukkit.inventory.ItemStack pane = icon(Material.STAINED_GLASS_PANE, (short) 15, " ", null);
        for (int i = 0; i < 27; i++) if (i != TARGET && i != TOKEN) m.inv.setItem(i, pane);
        refresh(m, p);
        p.openInventory(m.inv);
    }

    static org.bukkit.inventory.ItemStack icon(Material mat, short data, String name, List<String> lore) {
        org.bukkit.inventory.ItemStack s = new org.bukkit.inventory.ItemStack(mat, 1, data);
        ItemMeta meta = s.getItemMeta();
        meta.setDisplayName(name);
        if (lore != null) meta.setLore(lore);
        s.setItemMeta(meta);
        return s;
    }

    static void refresh(Menu m, Player p) {
        EntityHuman h = (EntityHuman) Nms.living(p);
        World w = ((CraftWorld) m.table.getWorld()).getHandle();
        BlockPosition pos = new BlockPosition(m.table.getBlockX(), m.table.getBlockY(), m.table.getBlockZ());
        m.power = power(w, pos);
        ItemStack target = CraftItemStack.asNMSCopy(m.inv.getItem(TARGET));
        ItemStack token = CraftItemStack.asNMSCopy(m.inv.getItem(TOKEN));
        m.options = Upgrading.options(target, token, Nms.xpSeed(h));
        List<String> info = new ArrayList<>();
        info.add(ChatColor.GRAY + "Bookshelf power: " + (m.power >= Upgrading.BOOKSHELVES_NEEDED ? ChatColor.GREEN : ChatColor.RED)
                + m.power + "/" + Upgrading.BOOKSHELVES_NEEDED);
        info.add(ChatColor.GRAY + "Put an enchanted item or book on the left");
        info.add(ChatColor.GRAY + "and Prismarine Shards next to it.");
        m.inv.setItem(INFO, icon(Material.BOOKSHELF, (short) 0, ChatColor.LIGHT_PURPLE + "Upgrade enchantments", info));
        for (int i = 0; i < 3; i++) {
            Upgrading.Option o = m.options[i];
            if (o == null) {
                m.inv.setItem(OPTIONS[i], icon(Material.STAINED_GLASS_PANE, (short) 7, ChatColor.DARK_GRAY + "No upgrade", null));
                continue;
            }
            List<String> lore = new ArrayList<>();
            lore.add(ChatColor.GRAY + "From: " + name(o.recipe.in, levelOf(target, o.recipe.in)));
            boolean xpOk = p.getGameMode() == GameMode.CREATIVE || h.expLevel >= o.cost;
            lore.add((xpOk ? ChatColor.GREEN : ChatColor.RED) + "Cost: " + o.cost + " levels");
            lore.add((o.tokenOk ? ChatColor.GREEN : ChatColor.RED) + "Tokens: " + o.recipe.tokenCount + "x Prismarine Shard");
            if (o.recipe.cursing != null && o.recipe.curseChance > 0) {
                lore.add(ChatColor.DARK_RED + "Failure chance " + Math.round(o.recipe.curseChance * 100) + "%: "
                        + name(o.recipe.cursing.out, o.recipe.cursing.out.getStartLevel()));
            }
            String title = Upgrading.CLUES ? ChatColor.AQUA + name(o.recipe.out, o.level) + " . . . ?" : ChatColor.AQUA + "Upgrade";
            m.inv.setItem(OPTIONS[i], icon(Material.ENCHANTED_BOOK, (short) 0, title, lore));
        }
    }

    static int levelOf(ItemStack s, Enchantment e) {
        Integer l = EnchantmentManager.a(s).get(e);
        return l == null ? 0 : l;
    }

    private static void later(Menu m, Player p) {
        Bukkit.getScheduler().runTask(plugin, () -> Guard.run("upgrade.refresh", () -> {
            if (p.getOpenInventory().getTopInventory().getHolder() != m) return;
            normalize(m, p);
            refresh(m, p);
        }));
    }

    /** the vanilla enchanting slot holds one item; tokens slot only prismarine shards */
    private static void normalize(Menu m, Player p) {
        org.bukkit.inventory.ItemStack t = m.inv.getItem(TARGET);
        if (t != null && t.getAmount() > 1) {
            org.bukkit.inventory.ItemStack extra = t.clone();
            extra.setAmount(t.getAmount() - 1);
            t.setAmount(1);
            m.inv.setItem(TARGET, t);
            give(p, extra);
        }
        org.bukkit.inventory.ItemStack k = m.inv.getItem(TOKEN);
        if (k != null && k.getType() != Material.AIR && k.getType() != Material.PRISMARINE_SHARD) {
            m.inv.setItem(TOKEN, null);
            give(p, k);
        }
    }

    private static void give(Player p, org.bukkit.inventory.ItemStack s) {
        for (org.bukkit.inventory.ItemStack left : p.getInventory().addItem(s).values()) {
            p.getWorld().dropItemNaturally(p.getLocation(), left);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onClick(InventoryClickEvent e) {
        if (!(e.getView().getTopInventory().getHolder() instanceof Menu)) return;
        Menu m = (Menu) e.getView().getTopInventory().getHolder();
        Player p = (Player) e.getWhoClicked();
        int raw = e.getRawSlot();
        InventoryAction a = e.getAction();
        if (raw >= 27) {
            if (a == InventoryAction.COLLECT_TO_CURSOR) {
                e.setCancelled(true);
                return;
            }
            if (a == InventoryAction.MOVE_TO_OTHER_INVENTORY) {
                e.setCancelled(true);
                org.bukkit.inventory.ItemStack cur = e.getCurrentItem();
                if (cur == null || cur.getType() == Material.AIR) return;
                if (cur.getType() == Material.PRISMARINE_SHARD) {
                    org.bukkit.inventory.ItemStack tok = m.inv.getItem(TOKEN);
                    if (tok == null || tok.getType() == Material.AIR) {
                        m.inv.setItem(TOKEN, cur.clone());
                        e.setCurrentItem(null);
                    } else if (tok.isSimilar(cur)) {
                        int room = tok.getMaxStackSize() - tok.getAmount();
                        int n = Math.min(room, cur.getAmount());
                        tok.setAmount(tok.getAmount() + n);
                        m.inv.setItem(TOKEN, tok);
                        cur.setAmount(cur.getAmount() - n);
                        e.setCurrentItem(cur.getAmount() <= 0 ? null : cur);
                    }
                } else {
                    org.bukkit.inventory.ItemStack tg = m.inv.getItem(TARGET);
                    if (tg == null || tg.getType() == Material.AIR) {
                        org.bukkit.inventory.ItemStack one = cur.clone();
                        one.setAmount(1);
                        m.inv.setItem(TARGET, one);
                        cur.setAmount(cur.getAmount() - 1);
                        e.setCurrentItem(cur.getAmount() <= 0 ? null : cur);
                    }
                }
            }
            later(m, p);
            return;
        }
        if (raw == TARGET || raw == TOKEN) {
            if (a == InventoryAction.COLLECT_TO_CURSOR || a == InventoryAction.HOTBAR_MOVE_AND_READD) {
                e.setCancelled(true);
                return;
            }
            later(m, p);
            return;
        }
        e.setCancelled(true);
        for (int i = 0; i < 3; i++) {
            if (raw == OPTIONS[i]) {
                final int id = i;
                Guard.run("upgrade.apply", () -> apply(m, p, id));
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onDrag(InventoryDragEvent e) {
        if (!(e.getView().getTopInventory().getHolder() instanceof Menu)) return;
        for (int raw : e.getRawSlots()) {
            if (raw < 27 && raw != TARGET && raw != TOKEN) {
                e.setCancelled(true);
                return;
            }
        }
        later((Menu) e.getView().getTopInventory().getHolder(), (Player) e.getWhoClicked());
    }

    @EventHandler
    public void onClose(InventoryCloseEvent e) {
        if (!(e.getInventory().getHolder() instanceof Menu)) return;
        Menu m = (Menu) e.getInventory().getHolder();
        Player p = (Player) e.getPlayer();
        for (int slot : new int[]{TARGET, TOKEN}) {
            org.bukkit.inventory.ItemStack s = m.inv.getItem(slot);
            m.inv.setItem(slot, null);
            if (s != null && s.getType() != Material.AIR) give(p, s);
        }
    }

    /** ContainerEnchantmentMixin enchantItem upgrading branch */
    static void apply(Menu m, Player p, int id) {
        Upgrading.Option o = m.options[id];
        if (o == null) return;
        EntityHuman h = (EntityHuman) Nms.living(p);
        boolean creative = h.abilities.canInstantlyBuild;
        // ContainerEnchantment.canInteractWith: table still there and within 8 blocks
        if (m.table.getBlock().getType() != Material.ENCHANTMENT_TABLE || !p.getWorld().equals(m.table.getWorld())
                || p.getLocation().distanceSquared(m.table.clone().add(0.5, 0.5, 0.5)) > 64.0D) {
            p.closeInventory();
            return;
        }
        m.power = power(((CraftWorld) m.table.getWorld()).getHandle(),
                new BlockPosition(m.table.getBlockX(), m.table.getBlockY(), m.table.getBlockZ()));
        if (!creative && m.power < Upgrading.BOOKSHELVES_NEEDED) {
            p.sendMessage(ChatColor.RED + "Upgrading needs " + Upgrading.BOOKSHELVES_NEEDED + " bookshelf power (you have " + m.power + ").");
            return;
        }
        int xpCost = o.cost;
        if (xpCost <= 0 || (!creative && h.expLevel < xpCost)) return;
        World w = ((CraftWorld) m.table.getWorld()).getHandle();
        Upgrading.Recipe used = o.recipe.used(w.random);
        org.bukkit.inventory.ItemStack tb = m.inv.getItem(TOKEN);
        ItemStack token = CraftItemStack.asNMSCopy(tb);
        if (!creative && (token.isEmpty() || !used.tokenValid(token))) return;
        org.bukkit.inventory.ItemStack targetB = m.inv.getItem(TARGET);
        ItemStack target = CraftItemStack.asNMSCopy(targetB);
        if (target.isEmpty()) return;
        Map<Enchantment, Integer> result = used.output(target);
        if (target.getItem() == Items.ENCHANTED_BOOK && target.getTag() != null) target.getTag().set("StoredEnchantments", new NBTTagList());
        EnchantmentManager.a(result, target);
        h.enchantDone(target, xpCost);
        if (!creative) {
            tb.setAmount(tb.getAmount() - used.tokenCount);
            m.inv.setItem(TOKEN, tb.getAmount() <= 0 ? null : tb);
        }
        target.setRepairCost(Upgrading.repairCostAfter(target.getRepairCost()));
        m.inv.setItem(TARGET, CraftItemStack.asBukkitCopy(target));
        m.table.getWorld().playSound(m.table.clone().add(0.5, 0.5, 0.5), used.isCursing ? Sound.BLOCK_FIRE_EXTINGUISH : Sound.BLOCK_ENCHANTMENT_TABLE_USE,
                SoundCategory.BLOCKS, 1.0F, w.random.nextFloat() * 0.1F + 0.9F);
        upgrades++;
        if (used.isCursing) failures++;
        refresh(m, p);
    }

    public static void closeAll() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p.getOpenInventory().getTopInventory().getHolder() instanceof Menu) p.closeInventory();
        }
    }
}
