package chat.jaspr.mutants;

import chat.jaspr.mutants.brewing.Brewing;
import chat.jaspr.mutants.config.MutantsConfig;
import chat.jaspr.mutants.hooks.EnchantingHooks;
import chat.jaspr.mutants.hooks.EntityLifecycle;
import chat.jaspr.mutants.hooks.ForgeEventBridge;
import chat.jaspr.mutants.net.ClientFilter;
import chat.jaspr.mutants.net.ModChannel;
import chat.jaspr.mutants.net.MutantEntityTracker;
import chat.jaspr.mutants.net.MutantTrackerEntry;
import chat.jaspr.mutants.registry.BukkitMaterials;
import chat.jaspr.mutants.registry.BukkitSounds;
import chat.jaspr.mutants.registry.DataKeyCheck;
import chat.jaspr.mutants.registry.DataResources;
import chat.jaspr.mutants.registry.EntityRegistry;
import chat.jaspr.mutants.registry.EnumInjector;
import chat.jaspr.mutants.registry.ItemRegistry;
import chat.jaspr.mutants.registry.Language;
import chat.jaspr.mutants.registry.SoundRegistry;
import chumbanotz.mutantbeasts.MBConfig;
import chumbanotz.mutantbeasts.entity.CreeperMinionEntity;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.logging.Logger;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.world.WorldServer;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.craftbukkit.v1_12_R1.CraftWorld;
import org.bukkit.craftbukkit.v1_12_R1.entity.CraftEntity;
import org.bukkit.craftbukkit.v1_12_R1.entity.CraftPlayer;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.world.WorldInitEvent;
import org.bukkit.event.world.WorldLoadEvent;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * JasprMutants: Mutant Creatures Legacy (Mutant Beasts, AGPL-3.0, by the Elite Modding Team and its original authors)
 * on Paper. The mod's own classes run on a Forge API shim; this class plays FML's part on a dedicated server (config,
 * mod construction, preInit, registry events, object holders, init, recipe registry) and connects the shim to Paper:
 * entity trackers, the mod's network channel, the jaspr:mutants client channel, Bukkit events for the Forge events the
 * mod listens to, JSON data, natural spawning in the main overworld only. See PORT_NOTES.md and MUTANTS_PROTOCOL.md.
 */
public final class JasprMutants extends JavaPlugin implements Listener {
    private static JasprMutants instance;
    private volatile boolean ready;
    private final ForgeEventBridge bridge = new ForgeEventBridge();
    private final EnchantingHooks enchanting = new EnchantingHooks(this);
    private final ClientFilter clients = new ClientFilter();
    private final Map<String, Integer> spawned = new TreeMap<String, Integer>();
    private final Map<String, Integer> refused = new TreeMap<String, Integer>();
    private int naturalSpawns;
    private int naturalBlocked;
    private int trackers;
    private int holders;
    private int languageKeys;
    private int switchMaps;
    private List<String> dataKeyProblems = new ArrayList<String>();
    private String failure;

    public static JasprMutants instance() {
        return instance;
    }

    public boolean isReady() {
        return this.ready;
    }

    public ClientFilter clients() {
        return this.clients;
    }

    void spawned(String kind) {
        this.spawned.merge(kind, 1, Integer::sum);
    }

    void spawnRefused(String kind) {
        this.refused.merge(kind, 1, Integer::sum);
    }

    @Override
    public void onEnable() {
        instance = this;
        Logger log = this.getLogger();
        ModChannel.setLogger(log);
        MutantEntityTracker.setLogger(log);
        MutantTrackerEntry.setLogger(log);
        EntityLifecycle.setLogger(log);
        ForgeEventBridge.setLogger(log);
        EnchantingHooks.setLogger(log);
        ClientFilter.setLogger(log);
        Brewing.setLogger(log);
        DataResources.setLogger(log);
        String stage = "config";
        try {
            // Forge's ConfigManager loads the @Config class before the mod is constructed
            MutantsConfig.init(this.getDataFolder(), log);
            MutantsConfig.sync();
            stage = "construct";
            ModLoader.construct();
            stage = "preInit";
            ModLoader.preInit();
            stage = "registries";
            ModLoader.registries();
            stage = "objectHolders";
            this.holders = ModLoader.objectHolders();
            stage = "init";
            ModLoader.init();
            stage = "recipes";
            ModLoader.recipeRegistry();
            stage = "materials";
            BukkitMaterials.register(ItemRegistry.INSTANCE.ordered());
            BukkitSounds.register(SoundRegistry.INSTANCE.ordered());
            this.switchMaps = BukkitMaterials.widenSwitchTables();
            for (String line : EnumInjector.report()) log.info("MUTANTS_ENUM " + line);
            stage = "language";
            try (InputStream in = this.getResource("assets/mutantbeasts/lang/en_us.lang")) {
                if (in != null) this.languageKeys = Language.inject(in);
            }
            stage = "data";
            DataResources.load(this.getFile());
            stage = "channels";
            ModChannel mod = ModChannel.get("mutantbeasts");
            mod.setValidator(0, (player, payload) -> validateTracker(player, payload));
            mod.listen(this);
            Bukkit.getMessenger().registerIncomingPluginChannel(this, ClientFilter.CHANNEL, this.clients);
            stage = "listeners";
            Bukkit.getPluginManager().registerEvents(this, this);
            Bukkit.getPluginManager().registerEvents(new EntityLifecycle(), this);
            Bukkit.getPluginManager().registerEvents(new Brewing(), this);
            Bukkit.getPluginManager().registerEvents(this.bridge, this);
            Bukkit.getPluginManager().registerEvents(this.enchanting, this);
            Bukkit.getPluginManager().registerEvents(this.clients, this);
            for (World w : Bukkit.getWorlds()) this.installTracker(w);
            for (Player p : Bukkit.getOnlinePlayers()) this.clients.inject(p);
            Bukkit.getScheduler().runTaskTimer(this, () -> this.bridge.tick(Bukkit.getOnlinePlayers()), 1L, 1L);
            Bukkit.getScheduler().runTaskTimer(this, this::keepData, 1200L, 1200L);
            Bukkit.getScheduler().runTask(this, this::afterStartup);
        } catch (Throwable t) {
            this.failure = stage + ":" + t.getClass().getSimpleName();
            for (Throwable c = t; c != null; c = c.getCause() == c ? null : c.getCause()) {
                log.severe((c == t ? "MUTANTS_FAILED stage=" + stage + " reason=" : "  caused by ") + c.getClass().getSimpleName()
                        + (c.getMessage() == null ? "" : " " + c.getMessage().replaceAll("[\\r\\n]", " ")));
                for (StackTraceElement el : c.getStackTrace()) {
                    if (el.getClassName().startsWith("chat.jaspr") || el.getClassName().startsWith("chumbanotz") || el.getClassName().startsWith("net.minecraftforge")) {
                        log.severe("  at " + el);
                    }
                }
            }
        }
    }

    /** Runs once the server has finished starting (main world loaded, its advancement list built). */
    private void afterStartup() {
        if (this.failure != null) return;
        World main = Bukkit.getWorlds().isEmpty() ? null : Bukkit.getWorlds().get(0);
        if (main != null) {
            this.dataKeyProblems = DataKeyCheck.run(((CraftWorld) main).getHandle());
            for (String p : this.dataKeyProblems) this.getLogger().warning("MUTANTS_DATAKEYS_MISMATCH " + p);
        }
        DataResources.loadAdvancements(onlineHandles());
        for (World w : Bukkit.getWorlds()) DataResources.injectLootTables(((CraftWorld) w).getHandle());
        this.ready = true;
        this.getLogger().info(this.statusLine());
    }

    /** /reload of loot tables or advancements drops the mod's; they are put back within a minute. */
    private void keepData() {
        if (!this.ready) return;
        DataResources.loadAdvancements(onlineHandles());
        for (World w : Bukkit.getWorlds()) DataResources.injectLootTables(((CraftWorld) w).getHandle());
    }

    private static List<EntityPlayerMP> onlineHandles() {
        List<EntityPlayerMP> out = new ArrayList<EntityPlayerMP>();
        for (Player p : Bukkit.getOnlinePlayers()) out.add(((CraftPlayer) p).getHandle());
        return out;
    }

    String statusLine() {
        return "MUTANTS_READY entities=" + EntityRegistry.INSTANCE.ordered().size() + " items=" + ItemRegistry.INSTANCE.ordered().size()
                + " sounds=" + SoundRegistry.INSTANCE.ordered().size() + " particles=2 materials=" + BukkitMaterials.added().size() + " bukkitSounds=" + BukkitSounds.added().size()
                + " switchMaps=" + this.switchMaps + " holders=" + this.holders + " lang=" + this.languageKeys
                + " recipes=" + DataResources.recipeCount() + " brewing=" + Brewing.modRecipeCount() + " lootTables=" + DataResources.lootTableCount()
                + " advancements=" + DataResources.advancementCount() + " config=" + MutantsConfig.optionsFromFile() + "/" + MutantsConfig.optionCount()
                + " trackers=" + this.trackers + " dataKeys=" + (this.dataKeyProblems.isEmpty() ? "ok" : "mismatch:" + this.dataKeyProblems.size())
                + " spawns=" + (MBConfig.ENTITIES.mutantZombieSpawnRate + MBConfig.ENTITIES.mutantSkeletonSpawnRate + MBConfig.ENTITIES.mutantCreeperSpawnRate + MBConfig.ENTITIES.mutantEndermanSpawnRate > 0 ? "overworld" : "off")
                + " protocol=" + ClientFilter.PROTOCOL;
    }

    private String countersLine() {
        ModChannel mod = ModChannel.get("mutantbeasts");
        return "MUTANTS_STATUS ready=" + this.ready + (this.failure == null ? "" : " failure=" + this.failure)
                + " tracked=" + MutantEntityTracker.trackedCount() + " spawnMessages=" + MutantTrackerEntry.spawnsSent()
                + " natural=" + this.naturalSpawns + " naturalBlocked=" + this.naturalBlocked + " apiSpawned=" + this.spawned + " apiRefused=" + this.refused
                + " hellos=" + this.clients.helloCount() + " soundsFiltered=" + this.clients.filteredSoundCount() + " particlesFiltered=" + this.clients.filteredParticleCount()
                + " modPackets=" + (mod == null ? "-" : mod.sentCount() + "/" + mod.receivedCount() + "/" + mod.rejectedCount())
                + " forgeEvents=" + this.bridge.postedCount() + " joinEvents=" + EntityLifecycle.joinEventCount() + " brewed=" + Brewing.brewedCount()
                + " enchanted=" + this.enchanting.enchantedCount() + " lootInjections=" + DataResources.lootInjectionCount()
                + " advancementLoads=" + DataResources.advancementLoadCount() + " enumReport=" + EnumInjector.report().size();
    }

    @Override
    public void onDisable() {
        if (this.ready) this.getLogger().info(this.countersLine());
        this.ready = false;
    }

    // ---- worlds: Forge's tracker step, loot tables ----

    private void installTracker(World world) {
        try {
            MutantEntityTracker.install(((CraftWorld) world).getHandle());
            this.trackers++;
        } catch (ReflectiveOperationException | RuntimeException e) {
            this.getLogger().warning("MUTANTS_TRACKER_INSTALL_FAILED world=" + world.getName() + " reason=" + e.getClass().getSimpleName());
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onWorldInit(WorldInitEvent event) {
        if (this.failure == null) this.installTracker(event.getWorld());
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onWorldLoad(WorldLoadEvent event) {
        if (this.failure == null) DataResources.injectLootTables(((CraftWorld) event.getWorld()).getHandle());
    }

    // ---- natural spawning: the mod's biome spawn entries, used only in the main overworld ----

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onSpawn(CreatureSpawnEvent event) {
        CreatureSpawnEvent.SpawnReason reason = event.getSpawnReason();
        if (reason != CreatureSpawnEvent.SpawnReason.NATURAL && reason != CreatureSpawnEvent.SpawnReason.CHUNK_GEN) return;
        Entity handle = ((CraftEntity) event.getEntity()).getHandle();
        if (!EntityRegistry.isModEntity(handle)) return;
        World main = Bukkit.getWorlds().get(0);
        if (event.getEntity().getWorld() != main) {
            event.setCancelled(true);
            this.naturalBlocked++;
        } else {
            this.naturalSpawns++;
        }
    }

    // ---- the mod's incoming packet (CreeperMinionTrackerPacket): exact size, own minion nearby ----

    private static String validateTracker(EntityPlayerMP player, byte[] payload) {
        if (payload.length != 7) return "size";
        int id = ((payload[1] & 0xFF) << 24) | ((payload[2] & 0xFF) << 16) | ((payload[3] & 0xFF) << 8) | (payload[4] & 0xFF);
        int option = payload[5];
        if (option < 0 || option > 2) return "option";
        Entity entity = player.world.getEntityByID(id);
        if (!(entity instanceof CreeperMinionEntity)) return "not_a_minion";
        CreeperMinionEntity minion = (CreeperMinionEntity) entity;
        if (minion.getOwner() != player) return "not_owner";
        if (minion.getDistanceSq(player) > 128.0D * 128.0D) return "too_far";
        return null;
    }

    // ---- /mutants ----

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String sub = args.length == 0 ? "status" : args[0].toLowerCase(Locale.ROOT);
        if ("status".equals(sub)) {
            sender.sendMessage(this.statusLine());
            sender.sendMessage(this.countersLine());
            return true;
        }
        if ("reload".equals(sub)) {
            MutantsConfig.sync();
            sender.sendMessage("MUTANTS_CONFIG_RELOADED options=" + MutantsConfig.optionsFromFile() + "/" + MutantsConfig.optionCount() + " (options marked [restart required] apply after a restart)");
            return true;
        }
        if ("spawn".equals(sub) && args.length >= 2) {
            if (!(sender instanceof Player)) {
                sender.sendMessage("Only a player can spawn at their position.");
                return true;
            }
            LivingEntity e = MutantsApi.spawn(args[1].toLowerCase(Locale.ROOT), ((Player) sender).getLocation());
            sender.sendMessage(e == null ? ChatColor.RED + "Not spawned (unknown kind, not ready or refused)." : "Spawned " + MutantsApi.kindOf(e) + " id=" + e.getEntityId());
            return true;
        }
        return false;
    }

    // static helper for WorldServer handles used by tests
    static WorldServer handle(World w) {
        return ((CraftWorld) w).getHandle();
    }
}
