package chat.jaspr.graves;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.ExperienceOrb;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockFadeEvent;
import org.bukkit.event.block.BlockFormEvent;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.block.BlockIgniteEvent;
import org.bukkit.event.block.BlockPhysicsEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Gravestones. A player's belongings never scatter on death: they are sealed in
 * a headstone raised where they fell, which survives explosions, fire, lava and
 * restarts, and opens for anyone who walks up to it. Only a pickaxe takes it
 * down, and taking it down spills the contents like a broken chest.
 */
public final class GravesPlugin extends JavaPlugin implements Listener {
    private GraveStore store;
    private GraveMenu menu;

    @Override public void onEnable() {
        saveDefaultConfig();
        store = new GraveStore(this);
        menu = new GraveMenu(this);
        store.load();
        getServer().getPluginManager().registerEvents(this, this);
        // Rebuild anything already loaded; the rest is repaired as chunks come back.
        for (World world : Bukkit.getWorlds()) {
            for (Grave grave : store.loaded(world)) Headstone.raise(grave);
        }
        getServer().getScheduler().runTaskTimer(this, new Runnable() {
            @Override public void run() { store.save(false); }
        }, 600L, 600L);
        getLogger().info("GRAVES_READY graves=" + store.size() + " experience=" + keepExperience());
    }

    @Override public void onDisable() {
        if (menu != null) menu.closeAll();
        if (store != null) store.save(true);
    }

    GraveStore store() { return store; }

    boolean enabled() { return getConfig().getBoolean("graves.enabled", true); }
    boolean keepExperience() { return getConfig().getBoolean("graves.store-experience", true); }
    boolean announce() { return getConfig().getBoolean("graves.announce", true); }

    // -- death ----------------------------------------------------------------

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDeath(PlayerDeathEvent event) {
        if (!enabled()) return;
        Player player = event.getEntity();
        try {
            if (event.getKeepInventory()) return;
            List<ItemStack> carried = new ArrayList<ItemStack>();
            for (ItemStack item : event.getDrops()) {
                if (item != null && item.getType() != Material.AIR && item.getAmount() > 0) carried.add(item.clone());
            }
            int experience = keepExperience() ? totalExperience(player) : 0;
            if (carried.isEmpty() && experience <= 0) return;
            Location spot = findSpot(player.getLocation());
            if (spot == null) return; // nowhere to stand it up: let vanilla scatter, never eat the items
            Grave grave = new Grave(UUID.randomUUID(), player.getUniqueId(), player.getName(),
                spot.getWorld().getName(), spot.getBlockX(), spot.getBlockY(), spot.getBlockZ(),
                System.currentTimeMillis(), player.getLocation().getYaw(), experience);
            grave.contents.addAll(carried);
            store.add(grave);
            if (!Headstone.raise(grave)) { store.remove(grave); return; }
            event.getDrops().clear();
            if (keepExperience()) event.setDroppedExp(0);
            store.save(false);
            if (announce()) {
                player.sendMessage(ChatColor.GRAY + "Your belongings rest in a grave at "
                    + ChatColor.WHITE + grave.x + ", " + grave.y + ", " + grave.z
                    + ChatColor.GRAY + " in " + grave.world + ".");
            }
            getLogger().info("GRAVE_RAISED owner=" + player.getName() + " at=" + grave.baseKey()
                + " stacks=" + grave.contents.size() + " xp=" + experience);
        } catch (RuntimeException e) {
            // A thrown handler here would drop the player's inventory on the floor.
            getLogger().severe("GRAVE_FAILED owner=" + player.getName() + " " + e);
        }
    }

    /** Every point the player had, not the capped orb vanilla would scatter. */
    static int totalExperience(Player player) {
        int level = player.getLevel();
        int total = experienceToReach(level);
        total += Math.round(player.getExp() * experienceForNext(level));
        return Math.max(0, total);
    }

    private static int experienceForNext(int level) {
        if (level >= 31) return 9 * level - 158;
        if (level >= 16) return 5 * level - 38;
        return 2 * level + 7;
    }

    private static int experienceToReach(int level) {
        if (level >= 32) return (int) (4.5 * level * level - 162.5 * level + 2220.0);
        if (level >= 17) return (int) (2.5 * level * level - 40.5 * level + 360.0);
        return level * level + 6 * level;
    }

    /** Prefer standing on solid ground near where they fell; float only as a last resort. */
    private Location findSpot(Location death) {
        World world = death.getWorld();
        if (world == null) return null;
        int x = death.getBlockX(), z = death.getBlockZ();
        int ceiling = world.getMaxHeight() - 3;
        int start = Math.max(1, Math.min(ceiling, death.getBlockY()));
        for (int radius = 0; radius <= 2; radius++) {
            List<int[]> ring = ring(x, z, radius);
            // settle onto the first footing at or below the death height
            for (int[] column : ring) {
                for (int y = start; y >= 1; y--) {
                    if (!free(world, column[0], y, column[1])) continue;
                    if (Headstone.solidFooting(world.getBlockAt(column[0], y - 1, column[1])))
                        return new Location(world, column[0], y, column[1]);
                }
            }
            // then anywhere clear, floating if it must
            for (int[] column : ring) {
                for (int y = start; y <= ceiling; y++)
                    if (free(world, column[0], y, column[1])) return new Location(world, column[0], y, column[1]);
                for (int y = start; y >= 1; y--)
                    if (free(world, column[0], y, column[1])) return new Location(world, column[0], y, column[1]);
            }
        }
        return null;
    }

    private static List<int[]> ring(int x, int z, int radius) {
        List<int[]> out = new ArrayList<int[]>();
        if (radius == 0) { out.add(new int[] {x, z}); return out; }
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                if (Math.max(Math.abs(dx), Math.abs(dz)) != radius) continue;
                out.add(new int[] {x + dx, z + dz});
            }
        }
        return out;
    }

    private boolean free(World world, int x, int y, int z) {
        if (!Headstone.spaceFree(world, x, y, z)) return false;
        return store.at(world.getName(), x, y, z) == null && store.at(world.getName(), x, y + 1, z) == null;
    }

    // -- looking up a grave from a block ---------------------------------------

    /** By position, whatever the block currently is: a block being placed into the
     *  headstone's cell already reports the new material by the time the event fires. */
    private Grave graveAt(Block block) {
        if (block == null || store == null || store.size() == 0) return null;
        return store.at(block.getWorld().getName(), block.getX(), block.getY(), block.getZ());
    }

    /** Physics is the one genuinely hot event: rule it out on the material first. */
    private Grave graveAtStanding(Block block) {
        if (block == null || store == null || store.size() == 0) return null;
        Material type = block.getType();
        if (type != Material.COBBLE_WALL && type != Material.SKULL) return null;
        return store.at(block.getWorld().getName(), block.getX(), block.getY(), block.getZ());
    }

    private boolean isGrave(Block block) { return graveAt(block) != null; }

    // -- opening ---------------------------------------------------------------

    @EventHandler(priority = EventPriority.LOW)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        Grave grave = graveAt(event.getClickedBlock());
        if (grave == null) return;
        event.setCancelled(true);
        if (event.getHand() != EquipmentSlot.HAND) return;
        Player player = event.getPlayer();
        if (menu.busy(grave)) {
            player.sendMessage(ChatColor.RED + menu.viewerName(grave) + " is already going through this grave.");
            return;
        }
        menu.open(player, grave);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onGraveClick(org.bukkit.event.inventory.InventoryClickEvent event) {
        syncLater(event.getInventory());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onGraveDrag(org.bukkit.event.inventory.InventoryDragEvent event) {
        syncLater(event.getInventory());
    }

    /** One tick later the window has settled; copy it back over the grave's record. */
    private void syncLater(org.bukkit.inventory.Inventory inventory) {
        if (inventory == null || !(inventory.getHolder() instanceof GraveMenu.Holder)) return;
        final GraveMenu.Holder holder = (GraveMenu.Holder) inventory.getHolder();
        getServer().getScheduler().runTaskLater(this, new Runnable() {
            @Override public void run() { menu.sync(holder); }
        }, 1L);
    }

    @EventHandler public void onClose(InventoryCloseEvent event) {
        if (!(event.getInventory().getHolder() instanceof GraveMenu.Holder)) return;
        GraveMenu.Holder holder = (GraveMenu.Holder) event.getInventory().getHolder();
        Player player = event.getPlayer() instanceof Player ? (Player) event.getPlayer() : null;
        menu.close(player, holder);
    }

    // -- breaking --------------------------------------------------------------

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        Grave grave = graveAt(event.getBlock());
        if (grave == null) return;
        event.setCancelled(true);
        Player player = event.getPlayer();
        if (menu.busy(grave)) {
            player.sendMessage(ChatColor.RED + menu.viewerName(grave) + " is going through this grave.");
            return;
        }
        // Any tool, or an empty hand in Creative -- the owner asked for no pickaxe requirement.
        spill(grave, player);
    }

    /** Break behaviour: everything inside is thrown out where a chest would throw it. */
    private void spill(Grave grave, Player breaker) {
        Location at = grave.centre();
        List<ItemStack> contents = new ArrayList<ItemStack>(grave.contents);
        int experience = grave.experience;
        retire(grave);
        if (at != null) {
            for (ItemStack item : contents) {
                if (item == null || item.getType() == Material.AIR) continue;
                try { at.getWorld().dropItemNaturally(at, item); } catch (RuntimeException ignored) { }
            }
            if (experience > 0) {
                try {
                    ExperienceOrb orb = at.getWorld().spawn(at, ExperienceOrb.class);
                    orb.setExperience(experience);
                } catch (RuntimeException ignored) { }
            }
        }
        store.save(false);
        if (breaker != null) {
            breaker.sendMessage(ChatColor.GRAY + "You break open " + grave.ownerName + "'s grave.");
        }
        getLogger().info("GRAVE_BROKEN owner=" + grave.ownerName + " at=" + grave.baseKey()
            + " by=" + (breaker == null ? "server" : breaker.getName()));
    }

    /** Takes a grave off the map and out of the record. Does not touch its contents. */
    void retire(Grave grave) {
        menu.forget(grave);
        Headstone.lower(grave);
        store.remove(grave);
    }

    // -- protection ------------------------------------------------------------

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) { shield(event.blockList()); }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) { shield(event.blockList()); }

    private void shield(List<Block> blocks) {
        for (Iterator<Block> it = blocks.iterator(); it.hasNext(); ) if (isGrave(it.next())) it.remove();
    }

    @EventHandler(ignoreCancelled = true) public void onBurn(BlockBurnEvent event) {
        if (isGrave(event.getBlock())) event.setCancelled(true);
    }
    @EventHandler(ignoreCancelled = true) public void onIgnite(BlockIgniteEvent event) {
        if (isGrave(event.getBlock())) event.setCancelled(true);
    }
    @EventHandler(ignoreCancelled = true) public void onFade(BlockFadeEvent event) {
        if (isGrave(event.getBlock())) event.setCancelled(true);
    }
    @EventHandler(ignoreCancelled = true) public void onForm(BlockFormEvent event) {
        if (isGrave(event.getBlock())) event.setCancelled(true);
    }
    @EventHandler(ignoreCancelled = true) public void onFromTo(BlockFromToEvent event) {
        if (isGrave(event.getToBlock())) event.setCancelled(true);
    }
    @EventHandler(ignoreCancelled = true) public void onPhysics(BlockPhysicsEvent event) {
        if (graveAtStanding(event.getBlock()) != null) event.setCancelled(true);
    }
    @EventHandler(ignoreCancelled = true) public void onEntityChange(EntityChangeBlockEvent event) {
        if (isGrave(event.getBlock())) event.setCancelled(true);
    }
    @EventHandler(ignoreCancelled = true) public void onPlace(BlockPlaceEvent event) {
        if (isGrave(event.getBlock())) event.setCancelled(true);
    }
    @EventHandler(ignoreCancelled = true) public void onPistonExtend(BlockPistonExtendEvent event) {
        if (pushes(event.getBlocks())) event.setCancelled(true);
    }
    @EventHandler(ignoreCancelled = true) public void onPistonRetract(BlockPistonRetractEvent event) {
        if (pushes(event.getBlocks())) event.setCancelled(true);
    }
    private boolean pushes(List<Block> blocks) {
        for (Block block : blocks) if (isGrave(block)) return true;
        return false;
    }

    /** A grave whose chunk was unloaded when something removed its blocks stands back up. */
    @EventHandler public void onChunkLoad(ChunkLoadEvent event) {
        List<Grave> graves = store.inChunk(event.getWorld().getName(), event.getChunk().getX(), event.getChunk().getZ());
        if (graves.isEmpty()) return;
        for (Grave grave : new ArrayList<Grave>(graves)) Headstone.raise(grave);
    }

    // -- command ---------------------------------------------------------------

    @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) { sender.sendMessage("Graves: " + store.size() + " standing."); return true; }
        Player player = (Player) sender;
        List<Grave> mine = store.ownedBy(player.getUniqueId());
        if (mine.isEmpty()) {
            player.sendMessage(ChatColor.GRAY + "You have no graves. Keep it that way.");
            return true;
        }
        player.sendMessage(ChatColor.DARK_GRAY + "--- " + ChatColor.GRAY + "Your graves" + ChatColor.DARK_GRAY + " ---");
        for (Grave grave : mine) {
            String where = grave.x + ", " + grave.y + ", " + grave.z;
            String distance = "";
            if (player.getWorld().getName().equals(grave.world)) {
                Location at = grave.centre();
                if (at != null) distance = ChatColor.DARK_GRAY + " (" + (int) player.getLocation().distance(at) + "m)";
            } else {
                distance = ChatColor.DARK_GRAY + " (" + grave.world + ")";
            }
            player.sendMessage(ChatColor.GRAY + "  " + where + ChatColor.DARK_GRAY + " - "
                + ChatColor.YELLOW + grave.contents.size() + ChatColor.GRAY + " stacks, "
                + ChatColor.YELLOW + grave.itemCount() + ChatColor.GRAY + " items"
                + (grave.experience > 0 ? ChatColor.GRAY + ", " + ChatColor.YELLOW + grave.experience + ChatColor.GRAY + " xp" : "")
                + distance);
        }
        return true;
    }
}
