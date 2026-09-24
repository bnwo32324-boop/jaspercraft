package chat.jaspr.revive;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * A port of CreativeMD's PlayerRevive to Bukkit. A fatal blow puts you on the
 * ground bleeding instead of killing you: the body lies where it fell, invulnerable
 * and frozen, and another player crouching over it brings you back. Run out of
 * time and the death happens for real, which is what raises your gravestone.
 *
 * Timings follow the mod (one helper per tick of progress, so helpers stack) with
 * this server's own bleed-out window and a full recovery on revive.
 */
public final class RevivePlugin extends JavaPlugin implements Listener {
    private final Map<UUID, Downed> downed = new HashMap<UUID, Downed>();
    private final Map<UUID, String> deathMessages = new HashMap<UUID, String>();
    private boolean shuttingDown;
    private OwedDeaths owed;
    private final java.util.Set<UUID> leaving = new java.util.HashSet<UUID>();
    private final java.util.Set<UUID> collecting = new java.util.HashSet<UUID>();
    private int clock;
    private final java.util.Set<UUID> synchronizedObservers = new java.util.HashSet<UUID>();

    @Override public void onEnable() {
        saveDefaultConfig();
        owed = new OwedDeaths(this);
        owed.load();
        getServer().getPluginManager().registerEvents(this, this);
        getServer().getPluginManager().registerEvents(new WorldContext(this), this);
        getServer().getScheduler().runTaskTimer(this, new Runnable() {
            @Override public void run() { tick(); }
        }, 1L, 1L);
        getLogger().info("REVIVE_READY bleedOut=" + bleedOutTicks() + "t reviveTicks=" + reviveTicks()
            + " radius=" + reviveRadius() + " pose=" + pose());
    }

    @Override public void onDisable() {
        shuttingDown = true;
        // A restart must not cost anyone their kit: stand everyone back up.
        for (Downed state : new ArrayList<Downed>(downed.values())) {
            Player player = Bukkit.getPlayer(state.id);
            if (player != null) restore(player, state);
        }
        downed.clear();
        sendSnapshots();
    }

    // -- settings --------------------------------------------------------------

    boolean enabled() { return getConfig().getBoolean("revive.enabled", true); }
    int bleedOutTicks() { return Math.max(20, getConfig().getInt("revive.bleed-out-ticks", 2400)); }
    int reviveTicks() { return Math.max(1, getConfig().getInt("revive.revive-ticks", 100)); }
    double reviveRadius() { return getConfig().getDouble("revive.revive-radius", 3.5); }
    boolean requireSneak() { return getConfig().getBoolean("revive.require-sneak", true); }
    double downedHealth() { return Math.max(1.0, getConfig().getDouble("revive.downed-health", 2.0)); }
    int downedFood() { return getConfig().getInt("revive.downed-food", 6); }
    float reviverExhaustion() { return (float) getConfig().getDouble("revive.reviver-exhaustion", 0.5); }
    boolean pose() { return getConfig().getBoolean("revive.lying-pose", true); }
    boolean needsWitness() { return getConfig().getBoolean("revive.require-another-player", true); }
    boolean killOnQuit() { return getConfig().getBoolean("revive.kill-on-disconnect", true); }

    boolean isDowned(Player player) { return player != null && downed.containsKey(player.getUniqueId()); }

    // -- going down ------------------------------------------------------------

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player)) return;
        Player player = (Player) event.getEntity();
        if (leaving.contains(player.getUniqueId()) || collecting.contains(player.getUniqueId())) {
            event.setCancelled(true); return;
        }
        if (shuttingDown || player.isDead() || !player.isOnline()) return;
        if (isDowned(player)) { event.setCancelled(true); return; }
        if (!enabled()) return;
        if (bypasses(event.getCause())) return;
        if (player.getGameMode() == org.bukkit.GameMode.CREATIVE
            || player.getGameMode() == org.bukkit.GameMode.SPECTATOR) return;
        if (player.getHealth() - event.getFinalDamage() > 0.0) return;
        if (needsWitness() && others(player).isEmpty()) return; // alone: nobody could ever reach you
        event.setCancelled(true);
        goDown(player);
    }

    /** Damage that kills outright in the mod too: nothing can revive you from the void. */
    private static boolean bypasses(EntityDamageEvent.DamageCause cause) {
        return cause == EntityDamageEvent.DamageCause.VOID
            || cause == EntityDamageEvent.DamageCause.SUICIDE;
    }

    private List<Player> others(Player player) {
        List<Player> out = new ArrayList<Player>();
        for (Player other : Bukkit.getOnlinePlayers()) {
            if (other.getUniqueId().equals(player.getUniqueId())) continue;
            if (other.getGameMode() == org.bukkit.GameMode.SPECTATOR) continue;
            if (!other.isOnline() || other.isDead() || isDowned(other)
                || leaving.contains(other.getUniqueId()) || other.getWorld() != player.getWorld()) continue;
            out.add(other);
        }
        return out;
    }

    private void goDown(Player player) {
        Location at = player.getLocation().clone();
        Downed state = new Downed(player.getUniqueId(), player.getName(), at, bleedOutTicks());
        state.wasInvulnerable = player.isInvulnerable();
        state.wasCollidable = player.isCollidable();
        state.wasAllowFlight = player.getAllowFlight();
        state.wasGravity = player.hasGravity();
        state.bar = Bukkit.createBossBar("Bleeding out", org.bukkit.boss.BarColor.RED, org.bukkit.boss.BarStyle.SOLID);
        state.bar.addPlayer(player);
        downed.put(player.getUniqueId(), state);

        player.setInvulnerable(true);
        player.setCollidable(false);
        player.setFireTicks(0);
        player.setSprinting(false);
        player.setGravity(false);
        player.setVelocity(new org.bukkit.util.Vector());
        try { player.closeInventory(); } catch (RuntimeException ignored) { }
        player.setHealth(Math.min(downedHealth(), maxHealth(player)));
        player.setFoodLevel(downedFood());
        player.setSaturation(0f);

        player.sendTitle(ChatColor.DARK_RED + "You are bleeding out",
            ChatColor.GRAY + "A friend can still reach you", 5, 60, 10);
        TextComponent giveUp = new TextComponent(ChatColor.RED + "[Give up and respawn] "
            + ChatColor.GRAY + "or wait for a friend to crouch beside you.");
        giveUp.setClickEvent(new net.md_5.bungee.api.chat.ClickEvent(
            net.md_5.bungee.api.chat.ClickEvent.Action.RUN_COMMAND, "/giveup"));
        player.spigot().sendMessage(giveUp);
        String message = ChatColor.RED + player.getName() + " is bleeding...";
        for (Player other : others(player)) other.sendMessage(message);
        player.getWorld().playSound(at, Sound.ENTITY_PLAYER_HURT, 1.2f, 0.6f);
        getLogger().info("REVIVE_DOWN player=" + player.getName()
            + " at=" + at.getBlockX() + "," + at.getBlockY() + "," + at.getBlockZ());
    }

    private static double maxHealth(Player player) {
        try { return player.getAttribute(Attribute.GENERIC_MAX_HEALTH).getValue(); }
        catch (RuntimeException e) { return 20.0; }
    }

    // -- the loop --------------------------------------------------------------

    private void tick() {
        clock++;
        if (downed.isEmpty()) {
            if (!synchronizedObservers.isEmpty()) sendSnapshots();
            return;
        }
        // Crouch is the complete interaction: no hidden right-click prerequisite.
        // Each helper can work on only the closest eligible body, not several at once.
        for (Player helper : Bukkit.getOnlinePlayers()) {
            Downed closest = closest(helper, true);
            for (Downed state : downed.values()) {
                if (state != closest && state.revivers.remove(helper.getUniqueId())) {
                    state.bar.removePlayer(helper);
                    helper.spigot().sendMessage(ChatMessageType.ACTION_BAR,
                        new TextComponent(ChatColor.RED + "Revive interrupted."));
                }
            }
            if (closest != null) begin(helper, closest);
        }
        for (Iterator<Map.Entry<UUID, Downed>> it = downed.entrySet().iterator(); it.hasNext(); ) {
            Downed state = it.next().getValue();
            Player player = Bukkit.getPlayer(state.id);
            if (player == null || !player.isOnline() || player.isDead()) {
                it.remove();
                if (player != null) restore(player, state); else state.bar.removeAll();
                continue;
            }
            state.ticksLeft--;

            List<Player> helpers = helpers(state, player);
            state.progress += helpers.size();
            for (Player helper : helpers) helper.setExhaustion(helper.getExhaustion() + reviverExhaustion());

            // Pinned the way the mod pins them, every tick, so nothing can nudge them.
            if (player.getHealth() > downedHealth()) player.setHealth(Math.min(downedHealth(), maxHealth(player)));
            if (player.getFoodLevel() != downedFood()) player.setFoodLevel(downedFood());
            if (!player.isInvulnerable()) player.setInvulnerable(true);

            // The browser applies an authoritative render-only pose. Do not fake a
            // bed then immediately delete it: vanilla wakes that entity back up.
            if (state.ticksLeft % 6 == 0) bleedParticles(player);
            if (state.ticksLeft % 4 == 0) readouts(state, player, helpers);

            if (state.progress >= reviveTicks()) { it.remove(); revive(state, player); continue; }
            if (state.ticksLeft <= 0) { it.remove(); bleedOut(state, player); }
        }
        if (clock % 5 == 0) sendSnapshots();
    }

    private boolean eligible(Player helper, Player body, boolean sneak) {
        return helper != null && body != null && helper != body && helper.isOnline() && body.isOnline()
            && !helper.isDead() && !body.isDead() && !isDowned(helper)
            && !leaving.contains(helper.getUniqueId()) && !collecting.contains(helper.getUniqueId())
            && helper.getGameMode() != org.bukkit.GameMode.SPECTATOR
            && helper.getWorld() == body.getWorld()
            && helper.getLocation().distanceSquared(body.getLocation()) <= reviveRadius() * reviveRadius()
            && (!sneak || !requireSneak() || helper.isSneaking()) && helper.hasLineOfSight(body);
    }

    private Downed closest(Player helper, boolean sneak) {
        Downed best = null;
        double distance = Double.MAX_VALUE;
        for (Downed state : downed.values()) {
            Player body = Bukkit.getPlayer(state.id);
            if (!eligible(helper, body, sneak)) continue;
            double d = helper.getLocation().distanceSquared(body.getLocation());
            if (d < distance) { best = state; distance = d; }
        }
        return best;
    }

    /** Helpers still meeting every condition this tick. Anyone who stops is dropped. */
    private List<Player> helpers(Downed state, Player player) {
        List<Player> valid = new ArrayList<Player>();
        for (Iterator<UUID> it = state.revivers.iterator(); it.hasNext(); ) {
            UUID id = it.next();
            Player helper = Bukkit.getPlayer(id);
            if (!eligible(helper, player, true)) {
                it.remove();
                if (helper != null) state.bar.removePlayer(helper);
                if (helper != null && helper.isOnline()) {
                    helper.spigot().sendMessage(ChatMessageType.ACTION_BAR,
                        new TextComponent(ChatColor.RED + "Revive interrupted."));
                }
                continue;
            }
            valid.add(helper);
        }
        return valid;
    }

    private void bleedParticles(Player player) {
        try {
            Location at = player.getLocation().add(0, 0.2, 0);
            for (Player observer : player.getWorld().getPlayers()) {
                if (observer != player && observer.getLocation().distanceSquared(at) < 1024)
                    observer.spawnParticle(Particle.REDSTONE, at, 3, 0.3, 0.1, 0.3, 0.0);
            }
        } catch (Throwable ignored) { }
    }

    private void readouts(Downed state, Player player, List<Player> helpers) {
        int percent = (int) Math.min(100f, state.progress * 100f / reviveTicks());
        String bar = bar(percent);
        // Keep the native title short so it does not cover the FPS readout.
        // Detailed instructions and the target's name are in the HUD/action bar.
        state.bar.setTitle(helpers.isEmpty() ? "Bleeding out - " + state.secondsLeft()
            + "s" : "Reviving - " + percent + "%");
        state.bar.setColor(helpers.isEmpty() ? org.bukkit.boss.BarColor.RED : org.bukkit.boss.BarColor.GREEN);
        state.bar.setProgress(helpers.isEmpty() ? Math.min(1.0, (double) state.ticksLeft / bleedOutTicks()) : percent / 100.0);
        if (helpers.isEmpty()) {
            player.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent(
                ChatColor.DARK_RED + "BLEEDING OUT " + ChatColor.RED + state.secondsLeft() + "s"));
        } else {
            player.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent(
                ChatColor.GREEN + "Being revived " + bar + ChatColor.GREEN + " " + percent + "%"));
        }
        for (Player helper : helpers) {
            helper.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent(
                ChatColor.GRAY + "Reviving " + ChatColor.WHITE + state.name + " " + bar
                    + ChatColor.GRAY + " " + percent + "%"));
        }
    }

    private static String bar(int percent) {
        int filled = Math.max(0, Math.min(20, percent / 5));
        StringBuilder out = new StringBuilder(ChatColor.DARK_GRAY + "[");
        out.append(ChatColor.GREEN);
        for (int i = 0; i < filled; i++) out.append('|');
        out.append(ChatColor.DARK_GRAY);
        for (int i = filled; i < 20; i++) out.append('|');
        out.append(ChatColor.DARK_GRAY).append(']');
        return out.toString();
    }

    // -- endings ---------------------------------------------------------------

    private void restore(Player player, Downed state) {
        player.setInvulnerable(state.wasInvulnerable);
        player.setCollidable(state.wasCollidable);
        player.setAllowFlight(state.wasAllowFlight);
        player.setGravity(state.wasGravity);
        player.setVelocity(new org.bukkit.util.Vector());
        state.bar.removeAll();
        state.revivers.clear();
    }

    private void revive(Downed state, Player player) {
        restore(player, state);
        player.setHealth(maxHealth(player));
        player.setFoodLevel(20);
        player.setSaturation(5f);
        player.setFireTicks(0);
        player.sendTitle(ChatColor.GREEN + "Revived", ChatColor.GRAY + "Back on your feet", 5, 40, 10);
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1.4f);
        player.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent(""));
        String message = ChatColor.GREEN + state.name + " was revived.";
        for (Player other : others(player)) other.sendMessage(message);
        getLogger().info("REVIVE_UP player=" + player.getName());
    }

    /** The real death, which is what JasprGraves turns into a headstone. */
    private void bleedOut(Downed state, Player player) {
        restore(player, state);
        deathMessages.put(player.getUniqueId(), ChatColor.GRAY + state.name + " bled out");
        player.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent(""));
        player.setHealth(0.0);
        getLogger().info("REVIVE_BLED_OUT player=" + player.getName());
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onDeath(PlayerDeathEvent event) {
        Downed stale = downed.remove(event.getEntity().getUniqueId());
        if (stale != null) restore(event.getEntity(), stale);
        collecting.remove(event.getEntity().getUniqueId());
        String message = deathMessages.remove(event.getEntity().getUniqueId());
        if (message != null) event.setDeathMessage(message);
    }

    // -- starting a revive ------------------------------------------------------

    @EventHandler(priority = EventPriority.LOW)
    public void onInteractEntity(PlayerInteractEntityEvent event) {
        if (isDowned(event.getPlayer())) { event.setCancelled(true); return; }
        if (!(event.getRightClicked() instanceof Player)) return;
        Downed state = downed.get(((Player) event.getRightClicked()).getUniqueId());
        if (state == null) return;
        event.setCancelled(true);
        begin(event.getPlayer(), state);
    }

    /** The lying body has a tiny client-side hitbox, so a right-click anywhere
     *  near it counts. Without this the revive is fiddly to even start. */
    @EventHandler(priority = EventPriority.LOW)
    public void onInteract(PlayerInteractEvent event) {
        if (isDowned(event.getPlayer())) { event.setCancelled(true); return; }
        if (event.getAction() != org.bukkit.event.block.Action.RIGHT_CLICK_AIR
            && event.getAction() != org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK) return;
        if (downed.isEmpty()) return;
        Player helper = event.getPlayer();
        Downed nearest = null;
        double best = reviveRadius() * reviveRadius();
        for (Downed state : downed.values()) {
            Player body = Bukkit.getPlayer(state.id);
            if (body == null || body.getWorld() != helper.getWorld()) continue;
            double distance = body.getLocation().distanceSquared(helper.getLocation());
            if (distance <= best) { best = distance; nearest = state; }
        }
        if (nearest != null) begin(helper, nearest);
    }

    private void begin(Player helper, Downed state) {
        if (!eligible(helper, Bukkit.getPlayer(state.id), true)) return;
        if (!state.revivers.add(helper.getUniqueId())) return;
        state.bar.addPlayer(helper);
        getLogger().info("REVIVE_BEGIN helper=" + helper.getName() + " target=" + state.name);
        helper.spigot().sendMessage(ChatMessageType.ACTION_BAR,
            new TextComponent(ChatColor.GRAY + "Reviving " + state.name + "..."));
        if (requireSneak() && !helper.isSneaking()) {
            helper.sendMessage(ChatColor.GRAY + "Hold sneak next to " + state.name + " to keep reviving.");
        }
        Player body = Bukkit.getPlayer(state.id);
        if (body != null) body.sendMessage(ChatColor.GREEN + helper.getName() + " is reviving you.");
    }

    // -- what a downed player may do -------------------------------------------

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        if (event instanceof PlayerTeleportEvent) return;
        Downed state = downed.get(event.getPlayer().getUniqueId());
        if (state == null || event.getTo() == null) return;
        Location to = event.getTo();
        if (to.getX() == state.where.getX() && to.getY() == state.where.getY() && to.getZ() == state.where.getZ()) return;
        Location pinned = state.where.clone();
        pinned.setYaw(to.getYaw());
        pinned.setPitch(to.getPitch());
        event.setTo(pinned);
    }

    @EventHandler(ignoreCancelled = true) public void onTeleport(PlayerTeleportEvent event) {
        Downed state = downed.get(event.getPlayer().getUniqueId());
        if (state == null || event.getTo() == null) return;
        if (event.getCause() == PlayerTeleportEvent.TeleportCause.UNKNOWN
            || event.getCause() == PlayerTeleportEvent.TeleportCause.PLUGIN
            || event.getCause() == PlayerTeleportEvent.TeleportCause.COMMAND) {
            // An operator moving the body is allowed; the pose has to follow it.
            state.where = event.getTo().clone();
            return;
        }
        event.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true) public void onDrop(PlayerDropItemEvent event) {
        if (isDowned(event.getPlayer())) event.setCancelled(true);
    }
    @EventHandler(ignoreCancelled = true) public void onBreak(BlockBreakEvent event) {
        if (isDowned(event.getPlayer())) event.setCancelled(true);
    }
    @EventHandler(ignoreCancelled = true) public void onPlace(BlockPlaceEvent event) {
        if (isDowned(event.getPlayer())) event.setCancelled(true);
    }
    @EventHandler(ignoreCancelled = true) public void onOpen(InventoryOpenEvent event) {
        if (event.getPlayer() instanceof Player && isDowned((Player) event.getPlayer())) event.setCancelled(true);
    }
    @EventHandler(ignoreCancelled = true) public void onFood(FoodLevelChangeEvent event) {
        if (event.getEntity() instanceof Player && isDowned((Player) event.getEntity())) event.setCancelled(true);
    }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onAttack(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Player && isDowned((Player) event.getDamager())) event.setCancelled(true);
    }
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        if (!isDowned(event.getPlayer())) return;
        String token = event.getMessage().substring(1).split(" ", 2)[0].toLowerCase(java.util.Locale.ROOT);
        if (token.equals("giveup") || token.endsWith(":giveup")) return;
        event.setCancelled(true);
        event.getPlayer().sendMessage(ChatColor.RED + "You are bleeding out. /giveup to stop holding on.");
    }

    @EventHandler public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        final UUID id = player.getUniqueId();
        leaving.add(id);
        synchronizedObservers.remove(id);
        if (!shuttingDown) Bukkit.getScheduler().runTaskLater(this, () -> leaving.remove(id), 200L);
        Downed state = downed.remove(player.getUniqueId());
        if (state == null) return;
        restore(player, state);
        if (shuttingDown || !killOnQuit()) return;
        // Logging out is not an escape, but setHealth(0) inside a quit event does not
        // reliably run the drop handlers, and an unreliable death here would cost the
        // player their inventory outright. The death is owed, and collected on return.
        owed.record(state);
        getLogger().info("REVIVE_QUIT_OWED player=" + player.getName());
    }

    @EventHandler public void onJoin(PlayerJoinEvent event) {
        final Player player = event.getPlayer();
        leaving.remove(player.getUniqueId());
        Downed stale = downed.remove(player.getUniqueId());
        if (stale != null) restore(player, stale);
        final OwedDeaths.Entry entry = owed.get(player.getUniqueId());
        if (entry == null) return;
        getServer().getScheduler().runTaskLater(this, new Runnable() {
            @Override public void run() {
                if (!player.isOnline() || shuttingDown || owed.get(player.getUniqueId()) != entry) return;
                collecting.add(player.getUniqueId());
                // Die where the body was lying, so the headstone lands where they fell.
                Location at = entry.location();
                if (at != null && at.getWorld() != null) {
                    try { player.teleport(at); } catch (RuntimeException ignored) { }
                }
                deathMessages.put(player.getUniqueId(), ChatColor.GRAY + entry.name + " bled out");
                player.sendMessage(ChatColor.DARK_RED + "You bled out while you were away.");
                try { player.setHealth(0.0); owed.take(player.getUniqueId()); }
                catch (RuntimeException error) { getLogger().warning("REVIVE_OWED_DEFERRED " + error.getClass().getSimpleName()); }
                collecting.remove(player.getUniqueId());
                getLogger().info("REVIVE_OWED_COLLECTED player=" + player.getName());
            }
        }, 20L);
    }

    // -- giving up --------------------------------------------------------------

    /** Full, bounded, per-observer snapshots: a late join never depends on an old
     * bed packet. No client-supplied progress or target selection is trusted. */
    private void sendSnapshots() {
        for (Player observer : Bukkit.getOnlinePlayers()) {
            com.google.gson.JsonObject root = new com.google.gson.JsonObject();
            root.addProperty("v", 1);
            root.addProperty("self", observer.getEntityId());
            com.google.gson.JsonArray bodies = new com.google.gson.JsonArray();
            for (Downed state : downed.values()) {
                Player body = Bukkit.getPlayer(state.id);
                if (body == null || body.getWorld() != observer.getWorld()
                    || observer.getLocation().distanceSquared(body.getLocation()) > 4096 || bodies.size() >= 32) continue;
                com.google.gson.JsonObject data = new com.google.gson.JsonObject();
                data.addProperty("id", body.getEntityId()); data.addProperty("name", state.name);
                data.addProperty("yaw", state.where.getYaw()); data.addProperty("pose", pose());
                data.addProperty("seconds", state.secondsLeft());
                data.addProperty("progress", Math.min(1f, state.progress / reviveTicks()));
                data.addProperty("helping", state.revivers.contains(observer.getUniqueId()));
                data.addProperty("near", eligible(observer, body, false));
                data.addProperty("helpers", state.revivers.size());
                bodies.add(data);
            }
            root.add("bodies", bodies);
            if (bodies.size() == 0 && !synchronizedObservers.remove(observer.getUniqueId())) continue;
            if (bodies.size() != 0) synchronizedObservers.add(observer.getUniqueId());
            try {
                net.minecraft.server.v1_12_R1.PacketDataSerializer buf =
                    new net.minecraft.server.v1_12_R1.PacketDataSerializer(io.netty.buffer.Unpooled.buffer());
                buf.a(root.toString());
                ((org.bukkit.craftbukkit.v1_12_R1.entity.CraftPlayer) observer).getHandle().playerConnection.sendPacket(
                    new net.minecraft.server.v1_12_R1.PacketPlayOutCustomPayload("JASPR|Revive", buf));
            } catch (RuntimeException error) {
                if (clock % 200 == 0) getLogger().warning("REVIVE_SYNC_FAILED " + error.getClass().getSimpleName());
            }
        }
    }

    @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) { sender.sendMessage("Only a bleeding player can give up."); return true; }
        Player player = (Player) sender;
        Downed state = downed.remove(player.getUniqueId());
        if (state == null) { player.sendMessage(ChatColor.GRAY + "You are not bleeding out."); return true; }
        bleedOut(state, player);
        return true;
    }
}
