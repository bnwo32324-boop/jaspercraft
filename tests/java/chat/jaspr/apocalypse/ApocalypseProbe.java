package chat.jaspr.apocalypse;

import com.google.gson.Gson;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.server.v1_12_R1.NBTCompressedStreamTools;
import net.minecraft.server.v1_12_R1.NBTTagCompound;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Difficulty;
import org.bukkit.Keyed;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.Chest;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.craftbukkit.v1_12_R1.inventory.CraftItemStack;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Zombie;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.ChunkUnloadEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.ShapelessRecipe;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.RegisteredListener;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.permissions.PermissionDefault;
import org.bukkit.util.io.BukkitObjectInputStream;
import org.bukkit.util.io.BukkitObjectOutputStream;

/** Console-triggered, post-ready assertions inside a real, disposable Paper JVM. */
public final class ApocalypseProbe extends JavaPlugin implements Listener {
    private static final int GROUND = 63;
    private ApocalypsePlugin apocalypse;
    private Object siege, arsenal, ruins, wallJump, teleports;
    private World world;
    private final List<String> failures = new ArrayList<>();
    private final Map<String, Object> metrics = new LinkedHashMap<>();
    private final Set<String> newChunks = new HashSet<>();
    private int assertions;
    private boolean started, finished;
    private UUID actor;
    private boolean cancelChange, cancelExplosion, tamperExplosion;
    private int changes, explosions;
    private List<Block> explosionList = new ArrayList<>();
    private Location explosionCenter;
    private final List<Block> injected = new ArrayList<>();

    @Override public void onEnable() {
        try {
            File cwd = new File(".").getCanonicalFile();
            if (!Boolean.getBoolean("jaspr.apocalypse.smoke") || !"server".equals(cwd.getName())
                    || !cwd.getParentFile().getName().matches("apocalypse-smoke-[0-9a-f-]{36}")) {
                throw new IllegalStateException("Probe is permitted only in a harness-created fixture JVM");
            }
            Bukkit.getPluginManager().registerEvents(this, this);
            getLogger().info("APOCALYPSE_SMOKE_ARMED waiting for post-ready console command");
        } catch (Exception error) {
            getLogger().severe(error.toString());
            Bukkit.getPluginManager().disablePlugin(this);
        }
    }

    @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof ConsoleCommandSender) || started) return true;
        started = true;
        Bukkit.getScheduler().runTask(this, this::begin);
        return true;
    }

    private interface Checked { void run() throws Exception; }
    private void phase(String name, Checked body) {
        try {
            body.run();
            getLogger().info("APOCALYPSE_SMOKE_PHASE " + name + " PASS assertions=" + assertions);
        } catch (Throwable error) {
            Throwable cause = error;
            while (cause.getCause() != null) cause = cause.getCause();
            failures.add(name + ": " + cause);
            getLogger().severe("APOCALYPSE_SMOKE_PHASE " + name + " FAIL " + cause);
            error.printStackTrace();
        }
    }

    private void check(boolean condition, String message) {
        assertions++;
        if (!condition) throw new AssertionError(message);
    }

    private static Object field(Object instance, String name) throws Exception {
        Field field = instance.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(instance);
    }

    private static void setField(Object instance, String name, Object value) throws Exception {
        Field field = instance.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(instance, value);
    }

    private static Method method(Class<?> type, String name, Class<?>... arguments) throws Exception {
        Method method = type.getDeclaredMethod(name, arguments);
        method.setAccessible(true);
        return method;
    }

    private void begin() {
        phase("lifecycle", () -> {
            check(Bukkit.isPrimaryThread(), "Probe must execute on the Bukkit thread");
            check(Bukkit.getOnlinePlayers().isEmpty(), "Fixture must have no players");
            check("127.0.0.1".equals(Bukkit.getIp()), "Fixture must bind only loopback");
            Set<String> plugins = new HashSet<>();
            for (Plugin plugin : Bukkit.getPluginManager().getPlugins()) plugins.add(plugin.getName());
            check(plugins.equals(new HashSet<>(Arrays.asList("AuthMe", "JasprApocalypse", "ApocalypseProbe"))),
                    "Unexpected fixture plugin set: " + plugins);
            check(Bukkit.getPluginManager().isPluginEnabled("AuthMe"), "AuthMe must be enabled");
            Plugin main = Bukkit.getPluginManager().getPlugin("JasprApocalypse");
            check(main instanceof ApocalypsePlugin && main.isEnabled(), "Main apocalypse plugin must be enabled");
            apocalypse = (ApocalypsePlugin) main;
            PluginCommand tp = Bukkit.getPluginCommand("tp");
            check(tp != null && "jaspr.apocalypse.teleport".equals(tp.getPermission()),
                    "Compatibility teleport command must use the public request permission");
            check(tp != null && tp.getAliases().contains("teleport"), "Teleport command must expose /teleport as an alias");
            check(tp != null && Bukkit.getServer().getCommandMap().getCommand("tp") == tp,
                    "Bare /tp must resolve to the consent-based plugin command");
            for (String name : Arrays.asList("tp", "tpa", "tpahere", "tpaccept", "tpdeny", "tpcancel")) {
                PluginCommand request = Bukkit.getPluginCommand(name);
                check(request != null && "jaspr.apocalypse.teleport".equals(request.getPermission()),
                    name + " must be registered with the same public permission");
            }
            check(Bukkit.getPluginManager().getPermission("jaspr.apocalypse.teleport").getDefault() == PermissionDefault.TRUE,
                    "Teleport requests must be granted to non-ops and ops by default");
            check(TeleportRequests.targetSyntax(new String[] {"alice"}), "One player name must be accepted for a request");
            check(!TeleportRequests.targetSyntax(new String[] {"alice", "bob"}), "Multi-target request forms must be rejected");
            check(!TeleportRequests.targetSyntax(new String[0]), "Bare target request must be rejected");
            world = Bukkit.getWorld("world");
            check(world != null && apocalypse.enabledWorld(world), "Configured world must be enabled");
            check(world.getSeed() == 6840227782638526189L, "Fresh world seed must match harness");
            check(!apocalypse.enabledWorld(null) && !apocalypse.isSurvivor(null), "Nulls must not be eligible");
            check(Bukkit.getWorlds().size() == 1, "Fixture must contain only world");
            check(world.getDifficulty() == Difficulty.HARD, "Plugin must raise configured EASY difficulty to HARD");
            check("false".equals(world.getGameRuleValue("keepInventory")), "Death inventory loss must be enabled");
            siege = field(apocalypse, "siege");
            arsenal = field(apocalypse, "arsenal");
            ruins = field(apocalypse, "ruins");
            wallJump = field(apocalypse, "wallJump");
            teleports = field(apocalypse, "teleports");
            check(siege != null && arsenal != null && ruins != null && wallJump != null && teleports != null, "All module fields must be initialized");
            check(Boolean.TRUE.equals(field(arsenal, "started")), "Arsenal registry must be started");
            check(field(siege, "task") != null, "Siege scheduler must be registered");
            check(field(wallJump, "task") != null, "Wall-jump scheduler must be registered");
            check(field(teleports, "expiryTask") != null, "Teleport expiry scheduler must be registered");
            Set<Object> listeners = new HashSet<>();
            for (RegisteredListener listener : HandlerList.getRegisteredListeners(apocalypse)) listeners.add(listener.getListener());
            check(listeners.contains(siege) && listeners.contains(arsenal) && listeners.contains(ruins) && listeners.contains(wallJump) && listeners.contains(teleports),
                    "All modules must have live Bukkit event registrations");
            world.setGameRuleValue("doDaylightCycle", "false");
            world.setGameRuleValue("doMobSpawning", "false");
            world.setTime(18000);
            metrics.put("paper", Bukkit.getVersion());
        });
        if (apocalypse == null || siege == null || arsenal == null || ruins == null || wallJump == null || teleports == null || world == null) { finish(); return; }
        phase("wall-jump-rules", this::wallJumpRules);
        phase("items-and-arsenal", this::items);
        phase("registered-recipes-and-forgery", this::recipes);
        phase("zombie-decoration-and-breach", this::breaching);
        phase("tnt-cancelled-explosion", () -> blast(true, false, false));
        phase("tnt-cancelled-block-changes", () -> blast(false, true, false));
        phase("tnt-shared-six-block-budget", () -> blast(false, false, false));
        phase("tnt-radius-and-32-block-cap", () -> blast(false, false, false, 40));
        phase("tnt-mutated-event-list", () -> blast(false, false, true));
        phase("ruins-start", this::startRuins);
        if (!ruinsRunning) finish();
    }

    private static NBTTagCompound itemData(ItemStack item) {
        return CraftItemStack.asNMSCopy(item).getTag().getCompound("JasprApocalypse");
    }

    private void wallJumpRules() {
        check(WallJumpRules.slideVelocity(0.25, 1, 15) == 0.0, "Upward cling velocity must be stopped");
        check(Math.abs(WallJumpRules.slideVelocity(-0.8, 1, 15) + 0.6) < 1.0e-9,
                "Fast downward cling velocity must be softened");
        check(WallJumpRules.slideVelocity(-0.2, 15, 15) == 0.0, "Slide delay must include its final delayed tick");
        check(Math.abs(WallJumpRules.slideVelocity(-0.2, 16, 15) + 0.1) < 1.0e-9,
                "Cling must slowly slide after the configured delay");
        double[] south = WallJumpRules.jumpVelocity(WallJumpRules.SOUTH, 0.55, 0.0, 0.0, 1.0);
        check(Math.abs(south[1] - 0.55) < 1.0e-9 && south[2] > 0.0,
                "Forward movement intent must launch upward in the requested direction");
        check(Math.abs(Math.hypot(south[0], south[2]) - 0.2475) < 1.0e-9,
                "Wall-jump horizontal boost must match the researched default");
        double[] east = WallJumpRules.jumpVelocity(WallJumpRules.EAST, 0.55, 0.0, -1.0, 0.0);
        check(east[0] < 0.0 && Math.abs(east[1] - 0.55) < 1.0e-9,
                "Leftward movement intent must launch upward in the requested direction");
        double[] facing = WallJumpRules.jumpVelocity(WallJumpRules.SOUTH, 0.55, 0.0);
        check(facing[2] > 0.0, "Facing direction must be the fallback when no movement sample exists");
        check(!WallJumpRules.canRecling(0.0, 0.0, WallJumpRules.SOUTH, WallJumpRules.SOUTH, false),
                "The same wall must not be immediately re-clingable");
        check(WallJumpRules.canRecling(0.0, 0.0, WallJumpRules.SOUTH, WallJumpRules.NORTH, false),
                "A different wall must be re-clingable");
        check(WallJumpRules.canRecling(-1.01, 0.0, WallJumpRules.SOUTH, WallJumpRules.SOUTH, false),
                "The original wall must be re-clingable after a one-block drop");
        Map<String, Object> wallMetrics = new LinkedHashMap<>();
        wallMetrics.put("defaultJumpBoost", 0.55);
        wallMetrics.put("defaultSlideDelayTicks", 15);
        wallMetrics.put("horizontalJumpBoost", Math.hypot(south[0], south[2]));
        wallMetrics.put("directionSource", "recent movement intent, then facing direction");
        wallMetrics.put("defaultReclinging", false);
        metrics.put("wallJumpRules", wallMetrics);
    }

    private ItemStack roundTrip(ItemStack item) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (BukkitObjectOutputStream output = new BukkitObjectOutputStream(bytes)) { output.writeObject(item); }
        ItemStack bukkit;
        try (BukkitObjectInputStream input = new BukkitObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
            bukkit = (ItemStack) input.readObject();
        }
        check(item.equals(bukkit), "Bukkit serialization changed item " + ApocalypseItems.id(item));
        bytes = new ByteArrayOutputStream();
        NBTCompressedStreamTools.a(CraftItemStack.asNMSCopy(bukkit).save(new NBTTagCompound()), bytes);
        NBTTagCompound saved = NBTCompressedStreamTools.a(new ByteArrayInputStream(bytes.toByteArray()));
        ItemStack restored = CraftItemStack.asBukkitCopy(new net.minecraft.server.v1_12_R1.ItemStack(saved));
        check(item.equals(restored), "Compressed NBT serialization changed item " + ApocalypseItems.id(item));
        return restored;
    }

    private void items() throws Exception {
        ItemStack[] samples = { ApocalypseItems.relic(3), ApocalypseItems.scrap(5), ApocalypseItems.ammo(24) };
        String[] ids = { "relic", "scrap", "ammo" };
        for (int i = 0; i < samples.length; i++) {
            ItemStack copy = roundTrip(samples[i]);
            check(ids[i].equals(ApocalypseItems.id(copy)), "NBT identity must survive: " + ids[i]);
            check(copy.getAmount() == samples[i].getAmount(), "Stack size must survive: " + ids[i]);
            ItemStack renamed = new ItemStack(copy.getType(), 1, copy.getDurability());
            ItemMeta meta = renamed.getItemMeta();
            meta.setDisplayName(copy.getItemMeta().getDisplayName());
            meta.setLore(copy.getItemMeta().getLore());
            renamed.setItemMeta(meta);
            check(ApocalypseItems.id(renamed).isEmpty(), "Name/lore forgery must not obtain NBT identity: " + ids[i]);
        }
        String[] guns = { "rifle", "shotgun", "railgun" };
        int[] models = { 1560, 1550, 1540 }, capacities = { 18, 6, 3 };
        Set<String> serials = new HashSet<>();
        Method identify = method(Arsenal.class, "identify", ItemStack.class);
        for (int i = 0; i < guns.length; i++) {
            ItemStack weapon = Arsenal.weapon(guns[i]);
            check(weapon.getType() == Material.DIAMOND_HOE && weapon.getDurability() == models[i], "Gun model: " + guns[i]);
            check(weapon.getItemMeta().isUnbreakable(), "Model durability must not wear: " + guns[i]);
            check(weapon.getItemMeta().hasDisplayName() && weapon.getItemMeta().hasLore(), "Gun presentation: " + guns[i]);
            NBTTagCompound data = itemData(weapon);
            check(guns[i].equals(data.getString("id")) && "jaspr-arsenal-v1".equals(data.getString("arsenalMark")), "Gun mark: " + guns[i]);
            check(data.hasKeyOfType("rounds", 3) && data.getInt("rounds") == 0, "Crafted guns must start empty");
            UUID.fromString(data.getString("serial"));
            check(serials.add(data.getString("serial")), "Each gun must get a unique serial");
            check(!data.getString("serial").equals(itemData(Arsenal.weapon(guns[i])).getString("serial")), "Repeated factory call must issue a new serial");
            Object gun = identify.invoke(null, weapon);
            check(gun != null, "Factory gun must pass validation");
            Method rounds = method(Arsenal.class, "rounds", ItemStack.class, gun.getClass(), int.class);
            ItemStack loaded = (ItemStack) rounds.invoke(null, weapon, gun, capacities[i] - 1);
            ItemStack restored = roundTrip(loaded);
            check(itemData(restored).getInt("rounds") == capacities[i] - 1, "Persistent magazine: " + guns[i]);
            check(data.getString("serial").equals(itemData(restored).getString("serial")), "Reload serialization must retain serial");
            check(restored.getDurability() == models[i] && identify.invoke(null, restored) != null, "Restored weapon must remain valid");
            check(restored.getItemMeta().getLore().get(0).contains((capacities[i] - 1) + "/" + capacities[i]), "Magazine lore must agree with NBT");
            for (int badCount : new int[] { -1, capacities[i] + 1 }) {
                ItemStack invalid = (ItemStack) rounds.invoke(null, weapon, gun, badCount);
                check(identify.invoke(null, invalid) == null, "Out-of-range magazine must be rejected: " + badCount);
            }
            ItemStack fake = ApocalypseItems.mark(new ItemStack(Material.DIAMOND_HOE, 1, (short) models[i]), guns[i]);
            check(identify.invoke(null, fake) == null, "An id alone must not forge a gun");
        }
        ItemStack blade = ExpeditionEquipment.item("trench_blade");
        NBTTagCompound bladeEnhancement = CraftItemStack.asNMSCopy(blade).getTag().getCompound("EnhancedArmaments");
        check(bladeEnhancement.getBoolean("EA_ENABLED") && "MELEE".equals(bladeEnhancement.getString("EA_CATEGORY")), "Custom melee must be Enhanced Armaments eligible");
        UUID.fromString(bladeEnhancement.getString("EA_ID"));
        check(bladeEnhancement.getInt("RARITY") >= 1 && bladeEnhancement.getInt("RARITY") <= 6, "Custom melee must receive a valid rarity");
        ItemStack armor = ExpeditionEquipment.item("bulwark_chestplate");
        NBTTagCompound armorEnhancement = CraftItemStack.asNMSCopy(armor).getTag().getCompound("EnhancedArmaments");
        check(armorEnhancement.getBoolean("EA_ENABLED") && "ARMOR".equals(armorEnhancement.getString("EA_CATEGORY")), "Custom armor must be Enhanced Armaments eligible");
        ItemStack vanilla = EnhancedArmaments.prepare(new ItemStack(Material.DIAMOND_SWORD));
        NBTTagCompound vanillaEnhancement = CraftItemStack.asNMSCopy(vanilla).getTag().getCompound("EnhancedArmaments");
        check(vanillaEnhancement.getBoolean("EA_ENABLED") && "MELEE".equals(vanillaEnhancement.getString("EA_CATEGORY")), "Vanilla swords must be Enhanced Armaments eligible");
        metrics.put("serializedItemIds", Arrays.asList("relic", "scrap", "ammo", "rifle", "shotgun", "railgun"));
    }

    private void recipes() throws Exception {
        Map<?, ?> registry = (Map<?, ?>) field(arsenal, "recipeIds");
        Set<String> required = new HashSet<>(Arsenal.catalogue().keySet());
        required.add("ammo"); required.add("ammo_salvage");
        check(registry.values().containsAll(required), "Arsenal must register every firearm and both ammunition recipes");
        Map<String, Recipe> actual = new HashMap<>();
        Recipe guide = null;
        Iterator<Recipe> recipes = Bukkit.recipeIterator();
        while (recipes.hasNext()) {
            Recipe recipe = recipes.next();
            if (!(recipe instanceof Keyed)) continue;
            NamespacedKey key = ((Keyed) recipe).getKey();
            if (key.equals(new NamespacedKey(apocalypse, "field_guide"))) guide = recipe;
            if (registry.containsKey(key)) {
                String id = String.valueOf(registry.get(key));
                check(!actual.containsKey(id), "Duplicate live recipe: " + id);
                actual.put(id, recipe);
            }
        }
        check(actual.keySet().containsAll(required) && actual.size() == registry.size(), "All registered recipe keys must be live in Bukkit");
        check(guide instanceof ShapelessRecipe && "guide".equals(ApocalypseItems.id(guide.getResult())), "Marked field_guide recipe must be registered in Bukkit");
        List<Material> guideIngredients = new ArrayList<>();
        for (ItemStack item : ((ShapelessRecipe) guide).getIngredientList()) guideIngredients.add(item.getType());
        check(guideIngredients.size() == 2 && guideIngredients.contains(Material.BOOK) && guideIngredients.contains(Material.ROTTEN_FLESH), "Field guide recipe must consume book and rotten flesh");
        Method valid = method(Arsenal.class, "validIngredients", String.class, ItemStack[].class);
        for (String id : Arsenal.catalogue().keySet()) {
            check(actual.get(id) instanceof ShapedRecipe, "Gun must have a shaped recipe");
            ShapedRecipe recipe = (ShapedRecipe) actual.get(id);
            check(id.equals(ApocalypseItems.id(recipe.getResult())), "Recipe result must be the marked gun");
            String shape = String.join("", recipe.getShape());
            ItemStack[] matrix = new ItemStack[9];
            int ingredientSlot = -1;
            for (int slot = 0; slot < 9; slot++) {
                ItemStack ingredient = recipe.getIngredientMap().get(shape.charAt(slot));
                if (ingredient == null || ingredient.getType() == Material.AIR) continue;
                matrix[slot] = new ItemStack(ingredient.getType(), 1, ingredient.getDurability());
                check(ApocalypseItems.id(matrix[slot]).isEmpty(), "Gun recipe ingredient must be vanilla: " + id);
                if (ingredientSlot < 0) ingredientSlot = slot;
            }
            check(Boolean.TRUE.equals(valid.invoke(null, id, matrix)), "Vanilla-only gun recipe must validate: " + id);
            ItemStack[] mirrored = new ItemStack[9];
            for (int slot = 0; slot < 9; slot++) mirrored[slot] = matrix[(slot / 3) * 3 + 2 - slot % 3];
            check(Boolean.TRUE.equals(valid.invoke(null, id, mirrored)), "Mirrored gun recipe must validate: " + id);
            check(ingredientSlot >= 0, "Registered gun recipe must have ingredients");
            matrix[ingredientSlot] = ApocalypseItems.mark(matrix[ingredientSlot], "tagged_substitute");
            check(Boolean.FALSE.equals(valid.invoke(null, id, matrix)), "Tagged custom item must be rejected: " + id);
        }
        check(actual.get("ammo") instanceof ShapelessRecipe, "Ammo must be shapeless");
        ShapelessRecipe ammo = (ShapelessRecipe) actual.get("ammo");
        check("ammo".equals(ApocalypseItems.id(ammo.getResult())) && ammo.getResult().getAmount() == 24, "Ammo yield and NBT");
        ItemStack[] ingredients = ammo.getIngredientList().toArray(new ItemStack[0]);
        check(ingredients.length == 8 && Boolean.TRUE.equals(valid.invoke(null, "ammo", ingredients)), "Four iron + four powder must validate");
        ingredients[0] = ApocalypseItems.mark(ingredients[0], "scrap");
        check(Boolean.FALSE.equals(valid.invoke(null, "ammo", ingredients)), "Marked substitute in ammo recipe must fail");
        if (actual.containsKey("ammo_salvage")) {
            check(actual.get("ammo_salvage") instanceof ShapelessRecipe, "Salvage ammunition must be shapeless");
            ShapelessRecipe salvage = (ShapelessRecipe) actual.get("ammo_salvage");
            check("ammo".equals(ApocalypseItems.id(salvage.getResult())) && salvage.getResult().getAmount() == 24, "Salvage ammo must produce 24 marked cartridges");
            ItemStack[] matrix = salvage.getIngredientList().toArray(new ItemStack[0]);
            check(matrix.length == 8, "Salvage ammo must occupy eight ingredient slots");
            int scrapSlot = -1;
            for (int i = 0; i < matrix.length; i++) if (matrix[i].getType() == ApocalypseItems.scrap(1).getType()) {
                matrix[i] = ApocalypseItems.scrap(1); scrapSlot = i;
            }
            check(scrapSlot >= 0 && Boolean.TRUE.equals(valid.invoke(null, "ammo_salvage", matrix)), "Marked salvage ammunition ingredients must validate");
            matrix[scrapSlot] = new ItemStack(ApocalypseItems.scrap(1).getType());
            check(Boolean.FALSE.equals(valid.invoke(null, "ammo_salvage", matrix)), "Ordinary nuggets must not substitute for marked salvage");
            matrix[scrapSlot] = ApocalypseItems.ammo(1);
            check(Boolean.FALSE.equals(valid.invoke(null, "ammo_salvage", matrix)), "Marked ammunition must not substitute for salvage");
            matrix[scrapSlot] = new ItemStack(Material.IRON_INGOT);
            check(Boolean.FALSE.equals(valid.invoke(null, "ammo_salvage", matrix)), "Mixed iron/salvage recipe must be rejected");
        }
        metrics.put("salvageAmmoRecipe", actual.containsKey("ammo_salvage"));
        metrics.put("fieldGuideRecipe", true);
        metrics.put("liveRecipes", new ArrayList<>(actual.keySet()));
        metrics.put("craftingScope", "Live registry and production NBT ingredient validation; no player inventory/crafting event simulation");
    }

    @SuppressWarnings("unchecked")
    private Map<UUID, Object> hunters() throws Exception { return (Map<UUID, Object>) field(siege, "hunters"); }

    private Zombie zombie(Location at, String kind) throws Exception {
        Zombie zombie = (Zombie) world.spawnEntity(at, EntityType.ZOMBIE);
        zombie.setAI(false);
        zombie.setSilent(true);
        method(siege.getClass(), "decorate", Zombie.class, String.class).invoke(siege, zombie, kind);
        check(zombie.isValid() && hunters().containsKey(zombie.getUniqueId()), "Decorated zombie must be live and tracked");
        check(zombie.getScoreboardTags().contains("jaspr_undead_" + kind), "Persistent zombie identity: " + kind);
        return zombie;
    }

    private void remove(Zombie zombie) throws Exception {
        if (zombie != null) { hunters().remove(zombie.getUniqueId()); zombie.remove(); }
        actor = null;
        cancelChange = cancelExplosion = tamperExplosion = false;
    }

    private void loadArea(int x, int z) {
        for (int cx = (x - 16) >> 4; cx <= (x + 16) >> 4; cx++)
            for (int cz = (z - 16) >> 4; cz <= (z + 16) >> 4; cz++) world.getChunkAt(cx, cz).load(true);
    }

    private void breaching() throws Exception {
        int x = 4096, z = 4096;
        loadArea(x, z);
        for (int dx = -3; dx <= 8; dx++) for (int dz = -3; dz <= 3; dz++) {
            world.getBlockAt(x + dx, GROUND, z + dz).setType(Material.OBSIDIAN, false);
            for (int y = GROUND + 1; y < GROUND + 12; y++) world.getBlockAt(x + dx, y, z + dz).setType(Material.AIR, false);
        }
        String[] variants = { "walker", "runner", "brute", "tnt", "revenant", "warden" };
        double[] health = { 28, 24, 70, 36, 60, 180 };
        for (int i = 0; i < variants.length; i++) {
            Zombie variant = zombie(new Location(world, x + .5, GROUND + 1, z + .5), variants[i]);
            try {
                check(variant.getAttribute(Attribute.GENERIC_MAX_HEALTH).getBaseValue() == health[i], "Variant health: " + variants[i]);
                check(variant.getAttribute(Attribute.ZOMBIE_SPAWN_REINFORCEMENTS).getBaseValue() == 0, "Disable uncontrolled reinforcement spawning");
                check(!variant.isBaby() && !variant.getCanPickupItems(), "Variant must have adult/no-pickup flags");
                if ("tnt".equals(variants[i])) check(variant.getEquipment().getHelmet().getType() == Material.TNT, "TNT carrier helmet");
            } finally { remove(variant); }
        }
        Zombie zombie = zombie(new Location(world, x + .5, GROUND + 1, z + .5), "walker");
        try {
            actor = zombie.getUniqueId();
            Object hunter = hunters().get(actor);
            Method breach = method(siege.getClass(), "breach", hunter.getClass(), Location.class);
            Location target = new Location(world, x + 6, GROUND + 1, z + .5);
            Block wall = world.getBlockAt(x + 1, GROUND + 2, z);
            wall.setType(Material.WOOD, false);
            changes = 0;
            for (int pass = 1; pass <= 7; pass++) {
                setField(siege, "budget", 6);
                breach.invoke(siege, hunter, target);
                check(wall.getType() == Material.WOOD, "Wood must survive before its eighth walker strike");
            }
            setField(siege, "budget", 6);
            breach.invoke(siege, hunter, target);
            check(wall.getType() == Material.AIR && changes == 1, "Eighth strike must remove wood through Bukkit change event");
            check(((Number) field(siege, "budget")).intValue() == 5, "Successful breach must consume one budget unit");
            for (Material protectedType : new Material[] { Material.OBSIDIAN, Material.CHEST }) {
                wall.setType(protectedType, false);
                if (protectedType == Material.CHEST) ((Chest) wall.getState()).getBlockInventory().setItem(0, ApocalypseItems.relic(2));
                int before = changes;
                for (int pass = 0; pass < 40; pass++) {
                    setField(siege, "budget", 6);
                    breach.invoke(siege, hunter, target);
                }
                check(wall.getType() == protectedType && changes == before, "Protected block must never be breached: " + protectedType);
                if (protectedType == Material.CHEST) check("relic".equals(ApocalypseItems.id(((Chest) wall.getState()).getBlockInventory().getItem(0))), "Chest contents must survive siege");
            }
            wall.setType(Material.WOOD, false);
            cancelChange = true;
            int before = changes;
            for (int pass = 0; pass < 16; pass++) {
                setField(siege, "budget", 6);
                breach.invoke(siege, hunter, target);
            }
            check(changes == before + 2 && wall.getType() == Material.WOOD, "Cancelled EntityChangeBlockEvent must protect wood over repeated attempts");
            check(((Number) field(siege, "budget")).intValue() == 5, "Cancelled breach consumes bounded event work budget");
            setField(siege, "budget", 0);
            before=changes;
            for(int attempt=0;attempt<16;attempt++)breach.invoke(siege,hunter,target);
            check(changes==before&&wall.getType()==Material.WOOD,"exhausted shared budget dispatches no more breach events");
            cancelChange = false;
            for (int pass = 0; pass < 8; pass++) { setField(siege, "budget", 6); breach.invoke(siege, hunter, target); }
            check(wall.getType() == Material.AIR, "Breach must recover when protection stops cancelling");
            metrics.put("zombieVariants", Arrays.asList(variants));
            metrics.put("woodBreakPasses", 8);
        } finally { remove(zombie); }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void changing(EntityChangeBlockEvent event) {
        if (actor == null || !actor.equals(event.getEntity().getUniqueId())) return;
        changes++;
        if (cancelChange) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void exploding(EntityExplodeEvent event) {
        if (actor == null || !actor.equals(event.getEntity().getUniqueId())) return;
        explosions++;
        explosionList = new ArrayList<>(event.blockList());
        explosionCenter = event.getLocation().clone();
        if (tamperExplosion) event.blockList().addAll(0, injected);
        if (cancelExplosion) event.setCancelled(true);
    }

    private void blast(boolean cancelWhole, boolean cancelBlocks, boolean tamper) throws Exception {
        blast(cancelWhole,cancelBlocks,tamper,6);
    }
    private void blast(boolean cancelWhole, boolean cancelBlocks, boolean tamper, int availableBudget) throws Exception {
        int x = 4192, z = 4096;
        loadArea(x, z);
        Map<Block, Material> before = new LinkedHashMap<>();
        for (int dx = -6; dx <= 6; dx++) for (int dz = -6; dz <= 6; dz++) for (int y = GROUND + 5; y <= GROUND + 18; y++) {
            Block block = world.getBlockAt(x + dx, y, z + dz);
            block.setType(Material.WOOD, false);
            before.put(block, Material.WOOD);
        }
        Block chest = world.getBlockAt(x + 1, GROUND + 11, z), obsidian = world.getBlockAt(x - 1, GROUND + 11, z);
        chest.setType(Material.CHEST, false);
        ((Chest) chest.getState()).getBlockInventory().setItem(0, ApocalypseItems.relic(2));
        obsidian.setType(Material.OBSIDIAN, false);
        before.put(chest, Material.CHEST);
        before.put(obsidian, Material.OBSIDIAN);
        for (int y = GROUND + 10; y <= GROUND + 11; y++) {
            Block block = world.getBlockAt(x, y, z);
            block.setType(Material.AIR, false);
            before.put(block, Material.AIR);
        }
        Zombie zombie = zombie(new Location(world, x + .5, GROUND + 10, z + .5), "tnt");
        Object previousRadius = apocalypse.getConfig().get("siege.tnt-radius");
        Object previousMax = apocalypse.getConfig().get("siege.tnt-max-blocks");
        try {
            apocalypse.getConfig().set("siege.tnt-radius", 100.0); // Exercise the hard radius clamp, not only the default.
            apocalypse.getConfig().set("siege.tnt-max-blocks", 32);
            actor = zombie.getUniqueId();
            cancelChange = cancelBlocks;
            cancelExplosion = cancelWhole;
            tamperExplosion = tamper;
            changes = explosions = 0;
            injected.clear();
            injected.add(world.getBlockAt(x + 6, GROUND + 10, z)); // Out-of-radius wood must be rejected even if injected.
            injected.add(obsidian);
            injected.add(chest);
            for (Block block : before.keySet()) {
                if (block.getType() == Material.WOOD && block.getLocation().distanceSquared(zombie.getLocation()) < 12) injected.add(block);
            }
            long brokenBefore = ((Number) field(siege, "broken")).longValue();
            Object hunter = hunters().get(actor);
            setField(siege, "budget", 6);
            Object shared=field(siege,"work");setField(shared,"edits",availableBudget);
            method(siege.getClass(), "explode", hunter.getClass()).invoke(siege, hunter);
            int expected=Math.min(32,availableBudget);
            check(explosions == 1, "TNT must dispatch a cancellable Bukkit explosion event");
            check(explosionList.size() == expected, "Explosion list obeys both configured cap and remaining shared budget");
            check(!explosionList.contains(chest) && !explosionList.contains(obsidian), "Protected blocks must be excluded from explosion list");
            for (Block block : explosionList) {
                // Enumeration uses integer offsets then floor-to-block; compare block coordinates
                // to the center block to avoid a half-block corner rounding false positive.
                int dx = block.getX() - explosionCenter.getBlockX();
                int dy = block.getY() - explosionCenter.getBlockY();
                int dz = block.getZ() - explosionCenter.getBlockZ();
                check(dx * dx + dy * dy + dz * dz <= 3.5 * 3.5, "Configured radius=100 must still cap event selection at 3.5");
            }
            int removed = 0;
            for (Map.Entry<Block, Material> entry : before.entrySet()) {
                if (entry.getKey().getType() == entry.getValue()) continue;
                removed++;
                check(entry.getValue() == Material.WOOD && entry.getKey().getType() == Material.AIR, "Explosion must remove only eligible wood");
                check(entry.getKey().getLocation().distanceSquared(explosionCenter) <= 4.5 * 4.5, "Explosion removal must respect capped radius including block rounding");
            }
            check(removed == (cancelWhole || cancelBlocks ? 0 : expected), "TNT removal cap/cancellation: removed=" + removed);
            check(((Number)field(shared,"edits")).intValue()==availableBudget-(cancelWhole?0:expected),"TNT including cancelled block events consumes shared work budget");
            check(((Number) field(siege, "broken")).longValue() - brokenBefore == removed, "Breach metrics must match real removed blocks");
            check(chest.getType() == Material.CHEST && obsidian.getType() == Material.OBSIDIAN, "Chest and obsidian must survive TNT");
            check("relic".equals(ApocalypseItems.id(((Chest) chest.getState()).getBlockInventory().getItem(0))), "Chest loot must survive TNT");
            if (cancelWhole) check(changes == 0 && zombie.isValid(), "Cancelling explosion must leave carrier and blocks intact");
            else check(changes == expected && !zombie.isValid() && !hunters().containsKey(actor), "Explosion must dispatch budgeted block changes and retire carrier");
            metrics.put("tnt-" + (cancelWhole ? "cancel-event" : cancelBlocks ? "cancel-blocks" : tamper ? "mutated-list" : "normal"), removed);
        } finally {
            apocalypse.getConfig().set("siege.tnt-radius", previousRadius);
            apocalypse.getConfig().set("siege.tnt-max-blocks", previousMax);
            remove(zombie);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void loaded(ChunkLoadEvent event) {
        if (event.isNewChunk() && "world".equals(event.getWorld().getName()))
            newChunks.add(event.getChunk().getX() + "," + event.getChunk().getZ());
    }

    // Fixed seed/default-selection samples: three examples of every family, with
    // additional ordinary/relic SHRINE and BUNKER examples. Never force offer(),
    // call the builder, change selection odds, or place the ruins from the probe.
    private static final int[][] SITES = {
        {-160,142}, {-160,152}, {-160,156}, {-160,158}, {-158,130}, {-158,132},
        {-158,152}, {-158,154}, {-158,180}, {-158,200}, {-158,204}, {-156,126},
        {-156,202}, {-154,124}, {-154,132}, {-154,134}, {-154,204}, {-152,210},
        {-150,202}, {-146,126}, {-134,106}, {-132,102}, {-132,108}, {-132,208}
    };
    private boolean ruinsRunning, sawQueued;
    private int siteIndex, queuePeak, supplies, relics, scraps, ruinBlocks;
    private long ruinDeadline;
    private final Set<String> pinned = new HashSet<>();
    private final Set<String> families = new HashSet<>();
    private final List<String> inspectedSites = new ArrayList<>();
    private final Map<String, Object> lastWork = new HashMap<>();
    private final Map<String, Object> ruinDiagnostics = new LinkedHashMap<>();
    private Location persistedChest;

    @EventHandler(priority = EventPriority.HIGH)
    public void keepFixtureChunks(ChunkUnloadEvent event) {
        if (event.getWorld() == world && pinned.contains(event.getChunk().getX() + "," + event.getChunk().getZ()))
            event.setCancelled(true);
    }

    private int pendingPlans() throws Exception {
        return ((Number) method(ruins.getClass(), "pendingPlans").invoke(ruins)).intValue();
    }

    private void startRuins() throws Exception {
        check(Boolean.TRUE.equals(field(ruins, "running")) && Boolean.FALSE.equals(field(ruins, "failed")), "Ruins generator must be running");
        check(world.getPopulators().contains(field(ruins, "populator")), "Ruins populator must be attached to world");
        Object settings = field(ruins, "settings");
        Method selected = method(ruins.getClass(), "selected", long.class, int.class, int.class, settings.getClass());
        for (int[] site : SITES) {
            check(!world.isChunkLoaded(site[0], site[1]), "Predetermined site must initially be fresh: " + Arrays.toString(site));
            check(Boolean.TRUE.equals(selected.invoke(null, world.getSeed(), site[0], site[1], settings)),
                    "Fixed seed must select predetermined site: " + Arrays.toString(site));
        }
        ruinDeadline = System.currentTimeMillis() + 150000;
        ruinsRunning = true;
        nextSite();
    }

    private void nextSite() {
        try {
            if (siteIndex == SITES.length) { verifyAllRuins(); return; }
            int[] site = SITES[siteIndex];
            // Neighbors cause vanilla population of the center chunk. Hold only
            // fixture chunks loaded so Paper's empty-server unloading cannot abort work.
            for (int cx = site[0] - 1; cx <= site[0] + 1; cx++) for (int cz = site[1] - 1; cz <= site[1] + 1; cz++) {
                pinned.add(cx + "," + cz);
                world.getChunkAt(cx, cz).load(true);
            }
            pollRuins(0);
        } catch (Throwable error) { ruinFailure(error); }
    }

    private void pollRuins(int idleTicks) {
        Bukkit.getScheduler().runTaskLater(this, () -> {
            try {
                check(Boolean.FALSE.equals(field(ruins, "failed")), "Ruins worker must not enter failed state");
                int pending = pendingPlans();
                Object work = field(ruins, "active");
                if (work != null) {
                    Object key = field(field(work, "candidate"), "key");
                    lastWork.put(field(key, "x") + "," + field(key, "z"), work);
                }
                sawQueued |= pending > 0;
                queuePeak = Math.max(queuePeak, pending);
                if (System.currentTimeMillis() >= ruinDeadline) throw new AssertionError("Ruins queue deadline: pending=" + pending);
                if (pending != 0 || idleTicks < 2) { pollRuins(pending == 0 ? idleTicks + 1 : 0); return; }
                int[] site = SITES[siteIndex];
                String key = site[0] + "," + site[1];
                phase("ruin-" + key, () -> {
                    try { inspectRuin(world.getChunkAt(site[0], site[1])); }
                    finally { diagnoseRuin(key); }
                });
                siteIndex++;
                getLogger().info("APOCALYPSE_SMOKE_RUIN " + siteIndex + "/" + SITES.length + " chunk=" + site[0] + "," + site[1] + " queue=0");
                nextSite();
            } catch (Throwable error) { ruinFailure(error); }
        }, 2L);
    }

    private void ruinFailure(Throwable error) {
        phase("ruins-behavior", () -> { throw new IllegalStateException(error); });
        ruinsRunning = false;
        finish();
    }

    private Material virgin(int y) {
        return y > GROUND ? Material.AIR : y == GROUND ? Material.GRASS : y >= GROUND - 2 ? Material.DIRT : Material.STONE;
    }

    private void diagnoseRuin(String key) throws Exception {
        Object work = lastWork.get(key);
        if (work == null) { ruinDiagnostics.put(key, "No active work observed"); return; }
        Map<String, Object> report = new LinkedHashMap<>();
        for (String name : new String[] { "cursor", "verifyCursor", "aborted", "verified", "finishing", "cachePlaced" })
            report.put(name, field(work, name));
        List<?> edits = (List<?>) field(work, "edits");
        report.put("edits", edits.size());
        Chunk chunk = (Chunk) field(field(work, "candidate"), "chunk");
        List<String> mismatches = new ArrayList<>();
        for (int i = 0; i < Math.min(((Number) field(work, "cursor")).intValue(), edits.size()); i++) {
            Object edit = edits.get(i);
            int x = ((Number) field(edit, "x")).intValue(), y = ((Number) field(edit, "y")).intValue(), z = ((Number) field(edit, "z")).intValue();
            Block block = chunk.getBlock(x, y, z);
            Material type = (Material) field(edit, "type");
            byte data = ((Number) field(edit, "data")).byteValue();
            if ((block.getType() != type || block.getData() != data) && mismatches.size() < 8)
                mismatches.add("edit=" + i + " local=" + x + "," + y + "," + z + " expected=" + type + ":" + data + " actual=" + block.getType() + ":" + block.getData());
        }
        report.put("appliedEditMismatches", mismatches);
        ruinDiagnostics.put(key, report);
        if (!Boolean.TRUE.equals(report.get("finishing"))) getLogger().warning("APOCALYPSE_SMOKE_RUIN_DIAGNOSTIC " + key + " " + new Gson().toJson(report));
    }

    private void inspectRuin(Chunk chunk) throws Exception {
        String key = chunk.getX() + "," + chunk.getZ();
        inspectedSites.add(key);
        check(newChunks.contains(key), "Ruin must originate from a real new-chunk event: " + key);
        check(chunk.isLoaded(), "Ruin chunk must remain loaded while queued");
        int minY = GROUND - 10, maxY = GROUND + 11;
        int size = (maxY - minY + 1) * 256;
        boolean[] solid = new boolean[size], anchored = new boolean[size];
        ArrayDeque<Integer> frontier = new ArrayDeque<>();
        int built = 0, raised = 0;
        for (int y = minY; y <= maxY; y++) for (int x = 0; x < 16; x++) for (int z = 0; z < 16; z++) {
            Material type = chunk.getBlock(x, y, z).getType();
            if (x < 2 || x > 13 || z < 2 || z > 13) check(type == virgin(y), "Guard ring changed at " + key + " local=" + x + "," + y + "," + z);
            if (type != virgin(y) && type != Material.AIR) built++;
            if (type.isSolid()) {
                int p = ((y - minY) << 8) | (z << 4) | x;
                solid[p] = true;
                if (y == minY) { anchored[p] = true; frontier.add(p); }
                if (y > GROUND) raised++;
            }
        }
        check(built > 50 && raised > 15, "Actual ruin structure must be present at " + key + ": built=" + built);
        while (!frontier.isEmpty()) {
            int p = frontier.removeFirst(), x = p & 15, z = (p >> 4) & 15, y = p >> 8;
            int[] neighbors = { x > 0 ? p - 1 : -1, x < 15 ? p + 1 : -1,
                z > 0 ? p - 16 : -1, z < 15 ? p + 16 : -1, y > 0 ? p - 256 : -1, p + 256 < size ? p + 256 : -1 };
            for (int next : neighbors) if (next >= 0 && solid[next] && !anchored[next]) { anchored[next] = true; frontier.add(next); }
        }
        for (int p = 0; p < size; p++) if (solid[p] && (p >> 8) + minY > GROUND)
            check(anchored[p], "Floating solid in ruin " + key + " local=" + (p & 15) + "," + ((p >> 8) + minY) + "," + ((p >> 4) & 15));
        int chests = 0;
        for (BlockState tile : chunk.getTileEntities()) {
            check(tile instanceof Chest, "Unexpected ruin tile entity: " + tile.getType());
            Chest chest = (Chest) tile;
            chests++;
            check(chest.getBlock().getRelative(0, -1, 0).getType().isSolid(), "Loot cache must be grounded");
            check(chest.getBlock().getRelative(0, 1, 0).getType() == Material.AIR, "Loot cache lid must open");
            boolean sealed = "Sealed Relic Cache".equals(chest.getCustomName());
            check(sealed || "Weathered Supplies".equals(chest.getCustomName()), "Cache must have its generated name");
            int filled = 0, cacheRelics = 0;
            boolean bread = false;
            Set<Material> allowed = new HashSet<>(Arrays.asList(Material.BREAD, Material.COAL, Material.STRING, Material.STICK,
                    Material.PAPER, Material.TORCH, Material.CARROT_ITEM));
            for (ItemStack item : chest.getBlockInventory().getContents()) {
                if (item == null || item.getType() == Material.AIR) continue;
                filled++;
                String id = ApocalypseItems.id(item);
                if ("relic".equals(id)) { cacheRelics += item.getAmount(); relics += item.getAmount(); }
                else if ("scrap".equals(id)) scraps += item.getAmount();
                else check(id.isEmpty() && allowed.contains(item.getType()), "Ruin must contain starter supplies, never guns/ammo: " + item);
                if (item.getType() == Material.BREAD) bread = true;
            }
            check(filled >= 3 && bread, "Completed loot must be nonempty and contain food at " + key
                    + ": filled=" + filled + ", bread=" + bread + ", name=" + chest.getCustomName());
            check(cacheRelics == (sealed ? 1 : 0), "Sealed caches must contain exactly one marked relic");
            if (sealed && persistedChest == null) persistedChest = chest.getLocation();
            supplies++;
        }
        check(chests == 1, "Every completed ruin must have exactly one accessible cache: " + key + " chests=" + chests);
        Object ledger = field(ruins, "ledger");
        Map<?, ?> states = (Map<?, ?>) field(ledger, "states");
        boolean complete = false;
        for (Map.Entry<?, ?> entry : states.entrySet()) if (((Number) field(entry.getKey(), "x")).intValue() == chunk.getX()
                && ((Number) field(entry.getKey(), "z")).intValue() == chunk.getZ()) complete = Integer.valueOf(2).equals(entry.getValue());
        check(complete, "Structure and loot must have a completed durable journal entry: " + key);
        long seed = ((Number) method(ruins.getClass(), "chunkSeed", long.class, int.class, int.class)
                .invoke(null, world.getSeed(), chunk.getX(), chunk.getZ())).longValue();
        Object family = method(ruins.getClass(), "family", int.class).invoke(null, new java.util.Random(seed).nextInt(100));
        families.add(String.valueOf(family));
        ruinBlocks += built;
    }

    private void verifyAllRuins() throws Exception {
        phase("ruins-coverage", () -> {
            check(sawQueued && queuePeak > 0 && pendingPlans() == 0, "Real queued plans must drain completely");
            check(families.size() == 6, "Fixed samples must cover all six completed ruin families: " + families);
            check(supplies == SITES.length && relics > 0 && scraps > 0, "Actual world loot must include supplies, NBT relics and scrap; chests=" + supplies);
        });
        check(persistedChest != null, "A generated relic chest must be available for disk persistence test");
        Chest chest = (Chest) persistedChest.getBlock().getState();
        // Add one partially loaded gun to this fixture chest to cover chunk-disk
        // serialization of the magazine and serial as well as generated loot.
        ItemStack gun = Arsenal.weapon("rifle");
        Object kind = method(Arsenal.class, "identify", ItemStack.class).invoke(null, gun);
        gun = (ItemStack) method(Arsenal.class, "rounds", ItemStack.class, kind.getClass(), int.class).invoke(null, gun, kind, 7);
        final int gunSlot = chest.getBlockInventory().firstEmpty();
        check(gunSlot >= 0, "Generated cache must have a free slot for the persistence test gun");
        chest.getBlockInventory().setItem(gunSlot, gun);
        ItemStack[] expected = chest.getBlockInventory().getContents();
        for (int i = 0; i < expected.length; i++) if (expected[i] != null) expected[i] = expected[i].clone();
        String name = chest.getCustomName();
        int cx = persistedChest.getBlockX() >> 4, cz = persistedChest.getBlockZ() >> 4;
        String key = cx + "," + cz;
        pinned.remove(key);
        world.save(); // Explicitly only this fresh fixture world.
        check(world.unloadChunk(cx, cz, true), "Persistence test must really unload the generated chunk");
        check(!world.isChunkLoaded(cx, cz), "Chunk must be absent before disk reload");
        newChunks.remove(key);
        world.getChunkAt(cx, cz).load(false);
        pinned.add(key);
        check(!newChunks.contains(key), "Disk reload must not be reported as new terrain");
        final ItemStack[] persisted = expected;
        Bukkit.getScheduler().runTaskLater(this, () -> {
            phase("ruins-persistence-and-no-replay", () -> {
                Chest restored = (Chest) persistedChest.getBlock().getState();
                check(Arrays.equals(persisted, restored.getBlockInventory().getContents()), "Disk reload must preserve all loot, weapon NBT and magazine without refill");
                check(name.equals(restored.getCustomName()), "Disk reload must preserve cache name");
                check(itemData(restored.getBlockInventory().getItem(gunSlot)).getInt("rounds") == 7, "Magazine must persist through actual region-file save/reload");
                check(pendingPlans() == 0, "Reloading completed terrain must not queue another ruin");
            });
            metrics.put("ruinsQueueDrained", true);
            ruinsRunning = false;
            finish();
        }, 10L);
    }

    private void finish() {
        if (finished) return;
        finished = true;
        metrics.put("ruinChunks", inspectedSites);
        metrics.put("ruinFamilies", new ArrayList<>(families));
        metrics.put("ruinBlocks", ruinBlocks);
        metrics.put("ruinLootChests", supplies);
        metrics.put("ruinRelics", relics);
        metrics.put("ruinScrap", scraps);
        metrics.put("ruinsQueuePeak", queuePeak);
        metrics.put("ruinDiagnostics", ruinDiagnostics);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("success", failures.isEmpty());
        result.put("assertions", assertions);
        result.put("failures", failures);
        result.put("metrics", metrics);
        getLogger().info("APOCALYPSE_SMOKE_RESULT " + new Gson().toJson(result));
    }
}
