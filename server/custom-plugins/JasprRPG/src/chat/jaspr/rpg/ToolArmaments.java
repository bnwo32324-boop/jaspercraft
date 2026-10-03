package chat.jaspr.rpg;

import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.craftbukkit.v1_12_R1.entity.CraftPlayer;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

/**
 * Tools as armaments (2026-10-02, with the realm armouries: their pickaxes, shovels and hoes "should also work with the
 * upgrade system"). An enhanced tool levels by digging (1), ores (4), harvesting grown crops (2) and tilling (1); blocks a
 * player put down never pay. Experience is written to the item in small batches (8 at a time, and whenever the player
 * switches away) so the held tool is not rewritten on every single block.
 *
 * The roster (AbilityType.Kind.TOOL): Excavation digs a matching neighbour too (10% a level, through the player's own
 * vanilla break so every protection still applies), Prospecting adds an extra ore drop (5% a level, on top of the
 * rarity's own +ore yield), Experienced adds 30% a level to the experience a block drops, Replanting plants harvested crops
 * again, Treasure Hunter turns up gold, emeralds or diamonds from natural ground (0.5% a level).
 */
final class ToolArmaments implements Listener {
    private static final int FLUSH = 8, PLACED_LIMIT = 20_000;
    private final ArmamentListener armaments;
    private final Random random = new Random();
    private final Map<String, Boolean> placed = new LinkedHashMap<String, Boolean>(1024, 0.75f, false) {
        @Override protected boolean removeEldestEntry(Map.Entry<String, Boolean> eldest) { return size() > PLACED_LIMIT; }
    };
    /** Per player: experience not yet written, and the hotbar slot it belongs to. */
    private final Map<UUID, int[]> pending = new HashMap<UUID, int[]>();
    private boolean digging;
    long dug, prospected, excavated, treasures, replanted, tilled, flushed;

    ToolArmaments(ArmamentListener armaments) { this.armaments = armaments; }

    private static String key(Block b) { return b.getWorld().getName() + ':' + b.getX() + ':' + b.getY() + ':' + b.getZ(); }

    private static boolean enhancedTool(ItemStack item) { return Armament.isTool(item) && Armament.isEnhanced(item); }

    static boolean grownCrop(Block b) {
        switch (b.getType()) {
            case CROPS: case CARROT: case POTATO: return b.getData() >= 7;
            case BEETROOT_BLOCK: case NETHER_WARTS: return b.getData() >= 3;
            default: return false;
        }
    }

    static boolean natural(Material m) {
        switch (m) {
            case STONE: case DIRT: case GRASS: case GRAVEL: case SAND: case NETHERRACK: case CLAY: case SANDSTONE: case ENDER_STONE:
            case SOUL_SAND: case MYCEL: case RED_SANDSTONE: case HARD_CLAY: case STAINED_CLAY: return true;
            default: return m.name().endsWith("_ORE");
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent e) { placed.put(key(e.getBlockPlaced()), Boolean.TRUE); }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDigExp(BlockBreakEvent e) {
        ItemStack held = e.getPlayer().getInventory().getItemInMainHand();
        if (!enhancedTool(held) || e.getExpToDrop() <= 0) return;
        int level = Armament.abilityLevel(held, AbilityType.EXPERIENCED);
        if (level > 0) e.setExpToDrop((int) Math.round(e.getExpToDrop() * (1.0d + 0.30d * level)));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDig(BlockBreakEvent e) {
        Player p = e.getPlayer();
        ItemStack held = p.getInventory().getItemInMainHand();
        if (!enhancedTool(held)) return;
        Block b = e.getBlock();
        Material type = b.getType();
        byte data = b.getData();
        boolean wasPlaced = placed.remove(key(b)) != null;
        boolean ore = type.name().endsWith("_ORE");
        boolean crop = grownCrop(b);
        dug++;
        if (!wasPlaced) {
            Rarity rarity = Armament.rarity(held);
            if (ore && e.isDropItems() && !held.containsEnchantment(Enchantment.SILK_TOUCH)) {
                double chance = rarity.bonus * 0.5d + 0.05d * Armament.abilityLevel(held, AbilityType.PROSPECTING);
                if (chance > 0 && random.nextDouble() < chance) {
                    Collection<ItemStack> drops = b.getDrops(held);
                    if (!drops.isEmpty()) {
                        ItemStack extra = drops.iterator().next().clone();
                        extra.setAmount(1);
                        b.getWorld().dropItemNaturally(b.getLocation().add(0.5, 0.5, 0.5), extra);
                        prospected++;
                    }
                }
            }
            int treasure = Armament.abilityLevel(held, AbilityType.TREASURE_HUNTER);
            if (treasure > 0 && natural(type) && random.nextDouble() < 0.005d * treasure) {
                double roll = random.nextDouble();
                ItemStack find = roll < 0.70 ? new ItemStack(Material.GOLD_NUGGET, 1 + random.nextInt(3))
                    : roll < 0.90 ? new ItemStack(Material.EMERALD) : new ItemStack(Material.DIAMOND);
                b.getWorld().dropItemNaturally(b.getLocation().add(0.5, 0.5, 0.5), find);
                treasures++;
            }
            int excavation = Armament.abilityLevel(held, AbilityType.EXCAVATION);
            if (excavation > 0 && !digging && random.nextDouble() < 0.10d * excavation) excavate(p, b, type);
            if (crop && Armament.abilityLevel(held, AbilityType.REPLANTING) > 0) replantLater(p, b, type);
        }
        int xp = wasPlaced ? 0 : ore ? 4 : crop ? 2 : 1;
        if (xp > 0) addXp(p, xp);
    }

    /** One matching neighbour (the six faces), broken as the player would break it. */
    private void excavate(Player p, Block origin, Material type) {
        BlockFace[] faces = {BlockFace.UP, BlockFace.DOWN, BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST};
        int start = random.nextInt(faces.length);
        for (int i = 0; i < faces.length; i++) {
            Block n = origin.getRelative(faces[(start + i) % faces.length]);
            if (n.getType() != type || n.getState() instanceof InventoryHolder || placed.containsKey(key(n))) continue;
            digging = true;
            try {
                ((CraftPlayer) p).getHandle().playerInteractManager.breakBlock(new net.minecraft.server.v1_12_R1.BlockPosition(n.getX(), n.getY(), n.getZ()));
                excavated++;
            } finally {
                digging = false;
            }
            return;
        }
    }

    private void replantLater(Player p, Block b, Material type) {
        RpgPlugin plugin = armaments.plugin();
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (b.getType() != Material.AIR) return;
            Material soil = b.getRelative(BlockFace.DOWN).getType();
            if (type == Material.NETHER_WARTS ? soil != Material.SOUL_SAND : soil != Material.SOIL) return;
            b.setType(type);
            replanted++;
        });
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onTill(PlayerInteractEvent e) {
        if (e.getAction() != Action.RIGHT_CLICK_BLOCK || e.getClickedBlock() == null || e.useItemInHand() == org.bukkit.event.Event.Result.DENY) return;
        ItemStack held = e.getItem();
        if (held == null || !held.getType().name().endsWith("_HOE") || !enhancedTool(held)) return;
        Block b = e.getClickedBlock();
        if ((b.getType() != Material.GRASS && b.getType() != Material.DIRT) || b.getRelative(BlockFace.UP).getType() != Material.AIR) return;
        tilled++;
        addXp(e.getPlayer(), 1);
    }

    private void addXp(Player p, int xp) {
        int slot = p.getInventory().getHeldItemSlot();
        int[] state = pending.get(p.getUniqueId());
        if (state == null) { state = new int[]{0, slot}; pending.put(p.getUniqueId(), state); }
        if (state[1] != slot) { state[0] = 0; state[1] = slot; }
        state[0] += xp;
        if (state[0] >= FLUSH) flush(p, state);
    }

    private void flush(Player p, int[] state) {
        if (state[0] <= 0) return;
        ItemStack tool = p.getInventory().getItem(state[1]);
        if (enhancedTool(tool)) {
            ItemStack updated = armaments.award(p, tool, state[0]);
            if (updated != null) p.getInventory().setItem(state[1], updated);
            flushed++;
        }
        state[0] = 0;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSwitch(PlayerItemHeldEvent e) {
        int[] state = pending.get(e.getPlayer().getUniqueId());
        if (state != null && state[1] == e.getPreviousSlot()) flush(e.getPlayer(), state);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent e) {
        int[] state = pending.remove(e.getPlayer().getUniqueId());
        if (state != null) flush(e.getPlayer(), state);
    }

    String metrics() {
        return "toolsDug=" + dug + " toolXpWrites=" + flushed + " prospected=" + prospected + " excavated=" + excavated + " treasures=" + treasures
            + " replanted=" + replanted + " tilled=" + tilled;
    }
}
