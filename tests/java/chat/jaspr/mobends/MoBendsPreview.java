package chat.jaspr.mobends;

import org.bukkit.*;
import org.bukkit.command.*;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.entity.EntityTargetEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Vector;
import java.util.*;

/** Isolated loopback fixture for the Mo' Bends client stage. Refuses to run without its private JVM flag. */
public final class MoBendsPreview extends JavaPlugin implements Listener {
    private final List<LivingEntity> walkers = new ArrayList<>();
    private final Map<UUID, double[]> anchors = new HashMap<>();
    private boolean walking = true;
    private String pacer = null;   // a player paced like the mobs, watched from a second tab
    private long tick;

    @Override public void onEnable() {
        if (!Boolean.getBoolean("jaspr.mobends.preview")) throw new IllegalStateException("Fixture guard missing");
        getServer().getPluginManager().registerEvents(this, this);
        World w = getServer().getWorlds().get(0);
        w.setGameRuleValue("doMobSpawning", "false");
        w.setGameRuleValue("doDaylightCycle", "false");
        w.setGameRuleValue("doWeatherCycle", "false");
        w.setTime(6000); w.setStorm(false); w.setSpawnLocation(0, 64, 0);
        // a ladder, water and stairs for the climbing, swimming and step states
        for (int y = 64; y < 70; y++) { w.getBlockAt(6, y, 0).setType(Material.STONE); w.getBlockAt(5, y, 0).setType(Material.LADDER); }
        for (int x = -8; x <= -4; x++) for (int z = -2; z <= 2; z++) { w.getBlockAt(x, 63, z).setType(Material.WATER); w.getBlockAt(x, 62, z).setType(Material.WATER); }
        getServer().getScheduler().runTaskTimer(this, this::step, 1, 1);
        getLogger().info("MOBENDS_PREVIEW_READY (no production worlds/accounts loaded)");
    }

    @EventHandler public void join(PlayerJoinEvent e) {
        getServer().getScheduler().runTaskLater(this, () -> {
            Player p = e.getPlayer(); p.setGameMode(GameMode.CREATIVE); p.setOp(true); // loopback fixture account only
            p.teleport(new Location(p.getWorld(), .5, 64, -6.5, 0, 10));
            p.getInventory().setItem(0, new ItemStack(Material.DIAMOND_SWORD));
            p.getInventory().setItem(1, new ItemStack(Material.BOW));
            p.getInventory().setItem(2, new ItemStack(Material.TORCH));
            p.getInventory().setItem(3, new ItemStack(Material.SHIELD));
            p.getInventory().setItem(4, new ItemStack(Material.APPLE, 16));
            p.getInventory().setItem(5, new ItemStack(Material.ARROW, 64));
            p.getInventory().setChestplate(new ItemStack(Material.IRON_CHESTPLATE));
            p.getInventory().setBoots(new ItemStack(Material.GOLD_BOOTS));
            if (walkers.isEmpty()) lineup(p.getWorld());
            getLogger().info("MOBENDS_JOIN " + p.getName());
        }, 40);
    }

    @EventHandler public void target(EntityTargetEvent e) { if (e.getTarget() instanceof Player) e.setCancelled(true); }

    private LivingEntity spawn(World w, EntityType type, double x, double z) {
        LivingEntity e = (LivingEntity) w.spawnEntity(new Location(w, x, 64, z, 180, 0), type);
        e.setAI(false); e.setRemoveWhenFarAway(false); e.setCustomName(type.name()); e.setCustomNameVisible(true);
        walkers.add(e); anchors.put(e.getUniqueId(), new double[] {x, z, walkers.size()});
        return e;
    }

    private void lineup(World w) {
        for (Entity e : w.getEntities()) if (!(e instanceof Player)) e.remove();
        walkers.clear(); anchors.clear();
        LivingEntity armored = spawn(w, EntityType.ZOMBIE, -6, 6);
        armored.getEquipment().setHelmet(new ItemStack(Material.IRON_HELMET));
        armored.getEquipment().setChestplate(new ItemStack(Material.DIAMOND_CHESTPLATE));
        armored.getEquipment().setLeggings(new ItemStack(Material.GOLD_LEGGINGS));
        armored.getEquipment().setBoots(new ItemStack(Material.LEATHER_BOOTS));
        armored.getEquipment().setItemInMainHand(new ItemStack(Material.IRON_SWORD));
        ((Zombie) armored).setBaby(false);
        spawn(w, EntityType.HUSK, -3, 6);
        LivingEntity skeleton = spawn(w, EntityType.SKELETON, 0, 6);
        skeleton.getEquipment().setItemInMainHand(new ItemStack(Material.BOW));
        skeleton.getEquipment().setHelmet(new ItemStack(Material.CHAINMAIL_HELMET));
        LivingEntity pig = spawn(w, EntityType.PIG_ZOMBIE, 3, 6);
        pig.getEquipment().setItemInMainHand(new ItemStack(Material.GOLD_SWORD));
        spawn(w, EntityType.SPIDER, 6, 6);
        spawn(w, EntityType.CAVE_SPIDER, 9, 6);
        Wolf wolf = (Wolf) spawn(w, EntityType.WOLF, -6, 11);
        Wolf pup = (Wolf) spawn(w, EntityType.WOLF, -3, 11); pup.setBaby();
        spawn(w, EntityType.SQUID, -6, 0).teleport(new Location(w, -6, 62.5, 0));
        Zombie baby = (Zombie) spawn(w, EntityType.ZOMBIE, 0, 11); baby.setBaby(true);
        getLogger().info("MOBENDS_LINEUP " + walkers.size());
    }

    // Walkers pace back and forth so the client sees real motion (Mo' Bends animates from position deltas).
    private void step() {
        tick++;
        if (pacer != null) { Player p = getServer().getPlayerExact(pacer); if (p != null) { double ph = tick / 30.0; p.teleport(new Location(p.getWorld(), 0.5 + Math.sin(ph) * 3.0, 64, -2.5, Math.cos(ph) > 0 ? 90 : 270, 0)); } }
        for (LivingEntity e : walkers) {
            if (e.isDead()) continue;
            double[] a = anchors.get(e.getUniqueId());
            if (a == null || e instanceof Squid) continue;
            if (!walking) continue;
            double phase = (tick + a[2] * 13) / 40.0, dz = Math.sin(phase) * 2.0;
            Location to = new Location(e.getWorld(), a[0] + .5, 64, a[1] + .5 + dz, Math.cos(phase) > 0 ? 0 : 180, 0);
            e.teleport(to);
        }
    }

    @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof ConsoleCommandSender)) return true;
        try {
            World w = getServer().getWorlds().get(0);
            String a = args.length == 0 ? "lineup" : args[0];
            switch (a) {
                case "lineup": lineup(w); break;
                case "walk": walking = !walking; getLogger().info("MOBENDS_WALKING " + walking); break;
                case "sit": for (LivingEntity e : walkers) if (e instanceof Wolf) { Wolf wolf = (Wolf) e; if (!wolf.isTamed()) { wolf.setTamed(true); wolf.setOwner(getServer().getOnlinePlayers().iterator().next()); } wolf.setSitting(!wolf.isSitting()); } break;
                case "hurt": for (LivingEntity e : walkers) if (!e.isDead()) { e.damage(Double.parseDouble(args.length > 1 ? args[1] : "4")); getLogger().info("MOBENDS_HURT " + e.getType() + " health=" + e.getHealth()); } break;
                case "kill": for (LivingEntity e : walkers) if (e instanceof Spider) e.setHealth(0); break;
                case "tp": for (Player p : getServer().getOnlinePlayers()) p.teleport(new Location(w, Double.parseDouble(args[1]), Double.parseDouble(args[2]), Double.parseDouble(args[3]), Float.parseFloat(args[4]), Float.parseFloat(args[5]))); break;
                case "pace": pacer = args.length > 1 ? args[1] : null; getLogger().info("MOBENDS_PACE " + pacer); break;
                case "sneak": for (Player p : getServer().getOnlinePlayers()) if (args.length < 2 || p.getName().equals(args[1])) p.setSneaking(!p.isSneaking()); break;
                case "sprint": for (Player p : getServer().getOnlinePlayers()) if (args.length < 2 || p.getName().equals(args[1])) p.setSprinting(!p.isSprinting()); break;
                case "who": for (Player p : getServer().getOnlinePlayers()) getLogger().info("MOBENDS_PLAYER " + p.getName()); break;
                case "elytra": for (Player p : getServer().getOnlinePlayers()) if (args.length < 2 || p.getName().equals(args[1])) { ItemStack c = p.getInventory().getChestplate(); p.getInventory().setChestplate(new ItemStack(c != null && c.getType() == Material.ELYTRA ? Material.IRON_CHESTPLATE : Material.ELYTRA)); } break;
                case "arrow": for (int i = 0; i < 6; i++) { Arrow arrow = w.spawnArrow(new Location(w, -8 + i * 3, 66, 14), new Vector(0, 0.35, -1), 1.2f, 0f); arrow.setPickupStatus(Arrow.PickupStatus.DISALLOWED); } getLogger().info("MOBENDS_ARROWS 6"); break;
                case "night": w.setTime(18000); break;
                case "day": w.setTime(6000); break;
                default: getLogger().info("MOBENDS_COMMANDS lineup walk sit hurt [n] kill night day");
            }
        } catch (Exception e) { getLogger().warning(e.toString()); }
        return true;
    }
}
