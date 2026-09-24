package chat.jaspr.apocalypse;

import fr.xephi.authme.api.v3.AuthMeApi;
import fr.xephi.authme.events.LoginEvent;
import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Difficulty;
import org.bukkit.GameMode;
import org.bukkit.World;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerResourcePackStatusEvent;
import org.bukkit.event.world.WorldLoadEvent;
import org.bukkit.inventory.FurnaceRecipe;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.ShapelessRecipe;
import org.bukkit.plugin.java.JavaPlugin;

public final class ApocalypsePlugin extends JavaPlugin implements Listener {
    private SiegeDirector siege;
    private Arsenal arsenal;
    private ExpeditionEquipment equipment;
    private Ruins ruins;
    private CreativeCatalogue creativeCatalogue;
    private SentryTurret sentry;
    private Waypoints waypoints;
    private DynamicLights dynamicLights;
    private WallJumpController wallJump;
    private TeleportRequests teleports;
    private PortalGun portalGun;
    private YamlConfiguration survivors;
    private File survivorFile;
    private final Map<UUID, Long> grace = new HashMap<>();
    @Override public void onEnable() {
        saveDefaultConfig();
        survivorFile = new File(getDataFolder(), "survivors.yml");
        survivors = YamlConfiguration.loadConfiguration(survivorFile);
        Bukkit.getPluginManager().registerEvents(this, this);
        for (World world : Bukkit.getWorlds()) configureWorld(world);
        siege = new SiegeDirector(this); siege.start();
        arsenal = new Arsenal(this); arsenal.start();
        equipment = new ExpeditionEquipment(this); equipment.start();
        ruins = new Ruins(this); ruins.start();
        creativeCatalogue = new CreativeCatalogue(this); creativeCatalogue.start();
        sentry = new SentryTurret(this); sentry.start();
        waypoints = new Waypoints(this); waypoints.start();
        dynamicLights = new DynamicLights(this); dynamicLights.start();
        wallJump = new WallJumpController(this); wallJump.start();
        teleports = new TeleportRequests(this); teleports.start();
        portalGun = new PortalGun(this); portalGun.start();
        ShapelessRecipe guide = new ShapelessRecipe(new NamespacedKey(this, "field_guide"), ApocalypseItems.guide());
        guide.addIngredient(Material.BOOK).addIngredient(Material.ROTTEN_FLESH);
        Bukkit.addRecipe(guide);
        FurnaceRecipe sanitized = new FurnaceRecipe(ExpeditionEquipment.item("sanitized_flesh"),
            new org.bukkit.material.MaterialData(Material.ROTTEN_FLESH), 0.35f);
        Bukkit.addRecipe(sanitized);
        for (Player p : Bukkit.getOnlinePlayers()) welcomeLater(p);
        getLogger().info("APOCALYPSE_READY version=" + getDescription().getVersion() + " world=" + getConfig().getString("world", "world")
            + " siege=true legacyRuins=" + getConfig().getBoolean("ruins.enabled", false) + " arsenal=late-game");
    }
    @Override public void onDisable() {
        // Sequential best-effort stops; the nested finally-chain exceeded the
        // JVM per-try code limit once dynamic lights joined, and this keeps the
        // same guarantee (every feature gets its stop even if one throws).
        try { if (siege != null) siege.stop(); } catch (Throwable ignored) { }
        try { if (arsenal != null) arsenal.stop(); } catch (Throwable ignored) { }
        try { if (ruins != null) ruins.stop(); } catch (Throwable ignored) { }
        try { if (teleports != null) teleports.stop(); } catch (Throwable ignored) { }
        try { if (wallJump != null) wallJump.stop(); } catch (Throwable ignored) { }
        try { if (equipment != null) equipment.stop(); } catch (Throwable ignored) { }
        try { if (creativeCatalogue != null) creativeCatalogue.stop(); } catch (Throwable ignored) { }
        try { if (sentry != null) sentry.stop(); } catch (Throwable ignored) { }
        try { if (waypoints != null) waypoints.stop(); } catch (Throwable ignored) { }
        try { if (dynamicLights != null) dynamicLights.stop(); } catch (Throwable ignored) { }
        try { if (portalGun != null) portalGun.stop(); } catch (Throwable ignored) { }
        saveSurvivors();
    }
    public boolean enabledWorld(World world) {
        return world != null && world.getEnvironment() == World.Environment.NORMAL
            && world.getName().equals(getConfig().getString("world", "world"));
    }
    public DynamicLights dynamicLights() { return dynamicLights; }
    public boolean authenticated(Player p) {
        return p != null && p.isOnline() && AuthMeApi.getInstance() != null && AuthMeApi.getInstance().isAuthenticated(p);
    }
    public boolean isSurvivor(Player p) {
        return authenticated(p) && !p.isDead() && (p.getGameMode() == GameMode.SURVIVAL || p.getGameMode() == GameMode.ADVENTURE);
    }
    public boolean huntable(Player p) {
        return isSurvivor(p) && grace.getOrDefault(p.getUniqueId(), 0L) < System.currentTimeMillis();
    }
    /** Successful gunshots join the same bounded sound memory as other player activity. */
    public void noise(Player player,org.bukkit.Location position,double radius) {
        if(siege!=null)siege.noise(player,position,radius);
    }
    private void configureWorld(World world) {
        if (enabledWorld(world)) {
            Difficulty configured;
            try { configured = Difficulty.valueOf(getConfig().getString("difficulty", "HARD").trim().toUpperCase(java.util.Locale.ROOT)); }
            catch (IllegalArgumentException | NullPointerException unknown) { configured = Difficulty.HARD; }
            world.setDifficulty(configured);
            // Breaching is dispatched through cancellable entity events by SiegeDirector.
        }
    }
    @EventHandler public void onWorld(WorldLoadEvent event) { configureWorld(event.getWorld()); }
    @EventHandler public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        if (!survivors.getBoolean("introduced." + player.getUniqueId(), false))
            grace.put(player.getUniqueId(), System.currentTimeMillis() + 90000L);
        welcomeLater(player);
    }
    // A manual/slow login may finish after the initial five-second join task.
    @EventHandler public void onAuthenticated(LoginEvent event) { if (event.isLogin()) welcomeLater(event.getPlayer()); }
    @EventHandler public void onQuit(PlayerQuitEvent event) { grace.remove(event.getPlayer().getUniqueId()); }
    @EventHandler public void onRespawn(PlayerRespawnEvent event) {
        grace.put(event.getPlayer().getUniqueId(), System.currentTimeMillis() + 45000);
    }
    private void welcomeLater(Player player) {
        Bukkit.getScheduler().runTaskLater(this, () -> {
            if (!authenticated(player)) return;
            String key = "introduced." + player.getUniqueId();
            if (!survivors.getBoolean(key, false)) {
                grace.put(player.getUniqueId(), System.currentTimeMillis() + Math.max(30, getConfig().getInt("siege.newcomer-grace-seconds", 90)) * 1000L);
                Map<Integer, ItemStack> left = player.getInventory().addItem(ApocalypseItems.guide());
                survivors.set(key, true); saveSurvivors();
                player.sendMessage(ChatColor.DARK_RED + "The Last Broadcast: " + ChatColor.GRAY + "The dead break walls. Find ruins, recover relics, forge your arsenal.");
                player.sendMessage(ChatColor.GOLD + (left.isEmpty()
                    ? "You have a short grace period. Read The Last Broadcast in your inventory."
                    : "You have a short grace period. Make room, then craft a book + rotten flesh for your field guide."));
            }
            String url = getConfig().getString("resource-pack.url", "");
            String sha = getConfig().getString("resource-pack.sha1", "");
            if (!url.isEmpty() && sha.matches("[0-9a-fA-F]{40}")) {
                byte[] hash = new byte[20]; for (int i=0;i<20;i++) hash[i]=(byte)Integer.parseInt(sha.substring(i*2,i*2+2),16);
                player.setResourcePack(url, hash);
            }
        }, 100L);
    }
    private void saveSurvivors() {
        if (survivors == null) return;
        try { survivors.save(survivorFile); } catch (IOException e) { getLogger().warning("Could not save survivor introductions: " + e.getMessage()); }
    }
    @EventHandler public void onPack(PlayerResourcePackStatusEvent event) {
        getLogger().info("APOCALYPSE_PACK status=" + event.getStatus());
    }
    @Override public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        String command = cmd.getName();
        if ("tp".equalsIgnoreCase(command) || "teleport".equalsIgnoreCase(command)
                || "tpa".equalsIgnoreCase(command) || "tpahere".equalsIgnoreCase(command)
                || "tpaccept".equalsIgnoreCase(command) || "tpdeny".equalsIgnoreCase(command)
                || "tpcancel".equalsIgnoreCase(command))
            return teleports != null && teleports.command(sender, command, args);
        if (sender instanceof Player && !authenticated((Player)sender)) return true;
        if ("portalgun".equalsIgnoreCase(command) || "portal".equalsIgnoreCase(command)) {
            if (!(sender instanceof Player)) { sender.sendMessage("Players only."); return true; }
            Player pg = (Player) sender;
            if (pg.getGameMode() != org.bukkit.GameMode.CREATIVE) {
                pg.sendMessage(ChatColor.RED + "The Portal Gun is creative-mode only for now.");
                return true;
            }
            pg.getInventory().addItem(ApocalypseItems.expedition("portal_gun", 5));
            pg.sendMessage(ChatColor.AQUA + "Portal Gun " + ChatColor.GRAY
                    + "added. Right-click: cyan portal. Sneak + right-click: amber. Anyone can walk through.");
            return true;
        }
        if ("waypoints".equalsIgnoreCase(cmd.getName()) || "wp".equalsIgnoreCase(cmd.getName())) {
            if (waypoints != null) return waypoints.command(sender, args);
            return true;
        }
        if ("dl".equalsIgnoreCase(cmd.getName()) || "dynamiclights".equalsIgnoreCase(cmd.getName())) {
            if (dynamicLights != null) return dynamicLights.command(sender, args);
            return true;
        }
        if (args.length > 0 && "status".equalsIgnoreCase(args[0]) && (!(sender instanceof Player) || sender.hasPermission("jaspr.apocalypse.admin"))) {
            sender.sendMessage("Apocalypse " + getDescription().getVersion() + " | undead=" + siege.activeCount() + " | ruinsQueued=" + ruins.pendingPlans() + " | " + siege.metrics()+" | "+creativeCatalogue.metrics()+" | "+wallJump.metrics()+" | "+sentry.metrics()+" | "+teleports.metrics()+" | "+waypoints.metrics()+" | "+dynamicLights.metrics()+" | "+portalGun.metrics());
        } else if (sender instanceof Player) {
            Player p = (Player)sender;
            for (ItemStack item : p.getInventory().getContents()) if ("guide".equals(ApocalypseItems.id(item))) {
                p.sendMessage(ChatColor.GOLD + "Read The Last Broadcast in your inventory."); return true;
            }
            p.getInventory().addItem(ApocalypseItems.guide());
        } else sender.sendMessage("Use apocalypse status for server diagnostics.");
        return true;
    }
}
