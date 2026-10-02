package chat.jaspr.bounties;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.Sign;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.SignChangeEvent;
import org.bukkit.event.enchantment.EnchantItemEvent;
import org.bukkit.event.entity.EntityBreedEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityTameEvent;
import org.bukkit.event.inventory.FurnaceExtractEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.metadata.MetadataValue;

/**
 * Counts what players do towards their bounties: kills (their own, a turret's they own, a boss fight they were in),
 * mining, woodcutting, harvests, fishing, smelting, breeding, taming and enchanting; remembers which realms they have
 * been to; and turns "[Bounties]" signs into bounty boards. Blocks a player placed and silk-touch mining never count.
 */
final class Tracker implements Listener {
    /** Metadata a sentry turret leaves on what it shoots: the owner's UUID (JasprApocalypse). */
    static final String TURRET_OWNER = "jaspr_sentry_owner";
    static final String SIGN_LINE = "[Bounties]";
    private static final int PLACED_LIMIT = 50_000;

    private final BountiesPlugin plugin;
    private final Map<String, Boolean> placed = new LinkedHashMap<String, Boolean>(1024, 0.75f, false) {
        @Override protected boolean removeEldestEntry(Map.Entry<String, Boolean> eldest) { return size() > PLACED_LIMIT; }
    };
    long placedSkips, silkSkips;

    Tracker(BountiesPlugin plugin) { this.plugin = plugin; }

    private static boolean playing(Player p) { return p != null && p.isOnline() && (p.getGameMode() == GameMode.SURVIVAL || p.getGameMode() == GameMode.ADVENTURE); }

    // ---- kills -------------------------------------------------------------------------------------------------------

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(EntityDeathEvent e) {
        LivingEntity dead = e.getEntity();
        if (dead instanceof Player) return;
        Player killer = dead.getKiller();
        if (killer == null) killer = turretOwner(dead);
        // Bosses: everyone fighting nearby shares the credit.
        boolean boss = false;
        for (Template t : Template.ALL.values()) if (t.goal == Template.Goal.BOSS && t.mob.test(dead)) { boss = true; break; }
        if (boss) {
            for (Player p : dead.getWorld().getPlayers()) {
                if (!playing(p) || p.getLocation().distanceSquared(dead.getLocation()) > 64 * 64) continue;
                plugin.progress(p, Template.Goal.BOSS, t -> t.mob.test(dead), 1);
            }
        }
        if (!playing(killer)) return;
        plugin.progress(killer, Template.Goal.KILL, t -> t.mob.test(dead), 1);
    }

    private Player turretOwner(LivingEntity dead) {
        for (MetadataValue v : dead.getMetadata(TURRET_OWNER)) {
            try {
                Player p = plugin.getServer().getPlayer(UUID.fromString(v.asString()));
                if (p != null) return p;
            } catch (IllegalArgumentException ignored) { }
        }
        return null;
    }

    // ---- blocks ------------------------------------------------------------------------------------------------------

    private static String key(Block b) { return b.getWorld().getName() + ':' + b.getX() + ':' + b.getY() + ':' + b.getZ(); }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent e) {
        if (Template.watchedPlacement(e.getBlockPlaced().getType())) placed.put(key(e.getBlockPlaced()), Boolean.TRUE);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent e) {
        Block b = e.getBlock();
        boolean wasPlaced = Template.watchedPlacement(b.getType()) && placed.remove(key(b)) != null;
        Player p = e.getPlayer();
        if (!playing(p)) return;
        if (wasPlaced) { placedSkips++; return; }
        Material type = b.getType();
        if (Template.ORES.contains(type)) {
            ItemStack tool = p.getInventory().getItemInMainHand();
            if (tool != null && tool.containsEnchantment(Enchantment.SILK_TOUCH)) { silkSkips++; return; }
            plugin.progress(p, Template.Goal.MINE, t -> t.block.test(b), 1);
        } else if (Template.LOGS.contains(type)) {
            plugin.progress(p, Template.Goal.CHOP, t -> t.block.test(b), 1);
        } else if (Template.grown(b)) {
            plugin.progress(p, Template.Goal.HARVEST, t -> t.block.test(b), 1);
        }
    }

    // ---- everything else ---------------------------------------------------------------------------------------------

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onFish(PlayerFishEvent e) {
        if (e.getState() == PlayerFishEvent.State.CAUGHT_FISH && playing(e.getPlayer())) plugin.progress(e.getPlayer(), Template.Goal.FISH, t -> true, 1);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onSmelt(FurnaceExtractEvent e) {
        Player p = e.getPlayer();
        Material m = e.getItemType();
        if (playing(p)) plugin.progress(p, Template.Goal.SMELT, t -> t.items != null && t.items.contains(m), e.getItemAmount());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreed(EntityBreedEvent e) {
        if (e.getBreeder() instanceof Player && playing((Player) e.getBreeder())) plugin.progress((Player) e.getBreeder(), Template.Goal.BREED, t -> true, 1);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTame(EntityTameEvent e) {
        if (e.getOwner() instanceof Player && playing((Player) e.getOwner())) plugin.progress((Player) e.getOwner(), Template.Goal.TAME, t -> true, 1);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEnchant(EnchantItemEvent e) {
        if (playing(e.getEnchanter())) plugin.progress(e.getEnchanter(), Template.Goal.ENCHANT, t -> true, 1);
    }

    // ---- realms, joining, leaving ------------------------------------------------------------------------------------

    @EventHandler(priority = EventPriority.MONITOR)
    public void onWorld(PlayerChangedWorldEvent e) {
        Player p = e.getPlayer();
        Template.Realm r = Template.Realm.ofWorld(p.getWorld().getName());
        if (r == null) return;
        PlayerBoard b = plugin.board(p);
        if (!b.realms.add(r)) return;
        b.dirty = true;
        BountiesPlugin.link(p, ChatColor.GOLD + "[Bounties] " + ChatColor.GRAY + "First time in " + ChatColor.WHITE + r.title + ChatColor.GRAY
            + ": its bounties can now appear on your board. " + ChatColor.YELLOW + "/bounty");
        plugin.getLogger().info("BOUNTY_REALM player=" + p.getName() + " realm=" + r);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent e) { plugin.joined(e.getPlayer()); }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent e) { plugin.quit(e.getPlayer()); }

    // ---- sign boards -------------------------------------------------------------------------------------------------

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onSign(SignChangeEvent e) {
        String first = ChatColor.stripColor(e.getLine(0) == null ? "" : e.getLine(0)).trim();
        if (!first.equalsIgnoreCase(SIGN_LINE) && !first.equalsIgnoreCase("[bounty]") && !first.equalsIgnoreCase("[bounty board]")) return;
        e.setLine(0, ChatColor.DARK_GREEN + SIGN_LINE);
        e.setLine(1, "Right-click to");
        e.setLine(2, "see your");
        e.setLine(3, "bounties");
        e.getPlayer().sendMessage(ChatColor.GOLD + "[Bounties] " + ChatColor.GRAY + "A bounty board: anyone can right-click it.");
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onBoardClick(PlayerInteractEvent e) {
        if (e.getAction() != Action.RIGHT_CLICK_BLOCK || e.getClickedBlock() == null) return;
        Material m = e.getClickedBlock().getType();
        if (m != Material.WALL_SIGN && m != Material.SIGN_POST) return;
        if (!(e.getClickedBlock().getState() instanceof Sign)) return;
        String first = ChatColor.stripColor(((Sign) e.getClickedBlock().getState()).getLine(0)).trim();
        if (!first.equalsIgnoreCase(SIGN_LINE)) return;
        e.setCancelled(true);
        plugin.openBoard(e.getPlayer());
    }
}
