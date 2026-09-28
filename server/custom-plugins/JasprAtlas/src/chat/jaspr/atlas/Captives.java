package chat.jaspr.atlas;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockDamageEvent;

/**
 * Rescue. The twelve named captives are freed by talking to them once their guards are dealt with: a thread of the
 * Hearthstar's light carries them west to a berth in the House of Return, where they can be visited, and where they
 * give their rescuer their testimony. Labour camps are freed by killing the camp's Taskmaster and breaking its shackle
 * post: every chain in the camp goes slack and the Bound become free people. Each rescue and each camp happens once,
 * for everyone, and is never undone; its reward goes to whoever did it.
 */
final class Captives implements Listener {
    private final AtlasPlugin plugin;
    long rescues, campsFreed, boundFreed;

    Captives(AtlasPlugin plugin) { this.plugin = plugin; }

    // ------------------------------------------------------------------ named captives

    /** Why a captive cannot be freed yet (Dominion guards within 10 blocks), or null. */
    String blocker(Entity captive) {
        int guards = 0;
        String kind = null;
        for (Entity e : captive.getNearbyEntities(10, 5, 10)) {
            if (!Dominion.isDominion(e) || e.isDead() || Npcs.has(e, "atlas_remnant")) continue;
            guards++;
            if (kind == null) kind = ChatColor.stripColor(e.getCustomName() == null ? "a guard" : e.getCustomName());
        }
        if (guards == 0) return null;
        return "(They shake their head and look past you.) " + (guards == 1 ? kind + " is watching. " : guards + " of them are watching, " + kind + " among them. ")
            + "If you take me now they will cut us both down. Deal with the guards first.";
    }

    void rescue(Player p, Entity captive, String id) {
        State s = plugin.state();
        Lore.Captive c = Lore.captive(id);
        if (c == null) return;
        if (s.captivesRescued.containsKey(id)) { captive.remove(); return; }
        s.captivesRescued.put(id, p.getName());
        State.Player rec = s.player(p.getUniqueId(), p.getName());
        rec.rescued++;
        rescues++;
        Location at = captive.getLocation();
        World w = at.getWorld();
        w.spawnParticle(Particle.SPELL_INSTANT, at.clone().add(0, 1, 0), 60, 0.4, 1.0, 0.4, 0.2);
        w.playSound(at, Sound.BLOCK_NOTE_CHIME, 1f, 1.2f);
        w.playSound(at, Sound.ENTITY_ILLUSION_ILLAGER_MIRROR_MOVE, 0.8f, 1.4f);
        captive.remove();
        p.sendTitle(ChatColor.YELLOW + c.name, ChatColor.GRAY + "is free, and on the way home", 10, 60, 20);
        p.sendMessage(ChatColor.YELLOW + c.name + ChatColor.GRAY + " takes your hand. A thread of blue light pulls them west, to the House of Return in Astreion. Visit them there: they have things to tell you.");
        Talk.give(p, new org.bukkit.inventory.ItemStack(Material.EMERALD, 4));
        plugin.reputation().add(p, 10, "you freed " + c.name);
        broadcast(ChatColor.YELLOW + p.getName() + ChatColor.GRAY + " freed " + ChatColor.YELLOW + c.name + ChatColor.GRAY + ", " + c.title + " (" + s.captivesRescued.size() + " of " + Lore.CAPTIVES.size() + " home).");
        plugin.saveStateSoon();
        plugin.getLogger().info("ATLAS_CAPTIVE_RESCUED id=" + id + " by=" + p.getName() + " total=" + s.captivesRescued.size());
    }

    /** At victory: every captive still held goes free (the Rekindlers are credited). */
    void freeAll(String by) {
        State s = plugin.state();
        for (Lore.Captive c : Lore.CAPTIVES.values()) if (!s.captivesRescued.containsKey(c.id)) s.captivesRescued.put(c.id, by);
        plugin.saveStateSoon();
    }

    private void broadcast(String msg) { for (Player p : Bukkit.getOnlinePlayers()) if (plugin.isAtlas(p.getWorld())) p.sendMessage(msg); }

    /** Berth index of a captive in the House of Return (their order in the roll). */
    static int berth(String id) {
        int i = 0;
        for (String k : Lore.CAPTIVES.keySet()) { if (k.equals(id)) return i; i++; }
        return -1;
    }

    // ------------------------------------------------------------------ labour camps

    /** The nearest shackle post within r blocks of a location, or null (for owner tests). */
    static Block nearestPost(Location at, int r) {
        Block best = null;
        double bd = Double.MAX_VALUE;
        for (int x = -r; x <= r; x++) for (int z = -r; z <= r; z++) for (int y = -6; y <= 6; y++) {
            Block b = at.getWorld().getBlockAt(at.getBlockX() + x, at.getBlockY() + y, at.getBlockZ() + z);
            if (b.getType() != Material.OBSIDIAN || !isPost(b)) continue;
            double d = b.getLocation().distanceSquared(at);
            if (d < bd) { bd = d; best = b; }
        }
        return best;
    }

    static boolean isPost(Block b) {
        if (b.getType() != Material.OBSIDIAN) return false;
        Block up = b.getRelative(BlockFace.UP), down = b.getRelative(BlockFace.DOWN);
        return up.getType() == Material.OBSIDIAN && up.getRelative(BlockFace.UP).getType() == Material.IRON_TRAPDOOR
            || up.getType() == Material.IRON_TRAPDOOR && down.getType() == Material.OBSIDIAN;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void strike(BlockDamageEvent e) {
        if (!plugin.isAtlas(e.getBlock().getWorld()) || !isPost(e.getBlock())) return;
        e.setCancelled(true);
        breakPost(e.getPlayer(), e.getBlock());
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void broken(BlockBreakEvent e) {
        if (!plugin.isAtlas(e.getBlock().getWorld()) || !isPost(e.getBlock())) return;
        e.setCancelled(true);
        breakPost(e.getPlayer(), e.getBlock());
    }

    private final Map<java.util.UUID, Long> told = new HashMap<>();

    void breakPost(Player p, Block b) {
        Plans.Site site = plugin.plans().siteAt(b.getX(), b.getZ(), 6);
        String camp = site != null ? site.i + ":" + site.j : "post:" + b.getX() + ":" + b.getZ();
        State s = plugin.state();
        if (s.campsFreed.contains(camp)) { p.sendMessage(ChatColor.GRAY + "The chains here are already slack. This camp is free."); return; }
        int cx = site != null ? site.x : b.getX(), cz = site != null ? site.z : b.getZ(), r = site != null ? site.kind.radius + 8 : 24;
        for (Entity e : b.getWorld().getNearbyEntities(new Location(b.getWorld(), cx, b.getY(), cz), r, 24, r)) {
            if (!"taskmaster".equals(Npcs.tagValue(e, Npcs.DOM_KIND)) || e.isDead()) continue;
            Long last = told.get(p.getUniqueId());
            if (last == null || System.currentTimeMillis() - last > 3000) {
                told.put(p.getUniqueId(), System.currentTimeMillis());
                p.sendMessage(ChatColor.DARK_RED + "The chains hold. " + ChatColor.GRAY + "The camp's Taskmaster is alive and keeps the chains' note. Kill the Taskmaster, then break the post.");
            }
            return;
        }
        freeCamp(p, b, camp);
    }

    private void freeCamp(Player p, Block post, String camp) {
        State s = plugin.state();
        s.campsFreed.add(camp);
        campsFreed++;
        // The post comes down: what is left is what a freed camp keeps (a fence post with flowers), bars gone.
        Block base = post.getRelative(BlockFace.DOWN).getType() == Material.OBSIDIAN ? post.getRelative(BlockFace.DOWN) : post;
        Block top = base.getRelative(BlockFace.UP);
        for (BlockFace f : new BlockFace[] {BlockFace.EAST, BlockFace.WEST, BlockFace.NORTH, BlockFace.SOUTH})
            if (top.getRelative(f).getType() == Material.IRON_FENCE) top.getRelative(f).setType(Material.AIR, false);
        top.getRelative(BlockFace.UP).setType(Material.AIR, false);
        base.setType(Material.FENCE, false);
        top.setType(Material.FLOWER_POT, false);
        World w = post.getWorld();
        w.playSound(base.getLocation(), Sound.BLOCK_ANVIL_LAND, 0.8f, 1.4f);
        w.spawnParticle(Particle.CLOUD, base.getLocation().add(0.5, 1, 0.5), 20, 0.3, 0.6, 0.3, 0.02);
        int n = 0;
        for (Entity e : w.getEntities()) if (camp.equals(Npcs.tagValue(e, Npcs.CAMP)) && Npcs.has(e, Npcs.BOUND)) { if (release(e)) n++; }
        // Bound near the post who were drawn without a camp tag (older chunks) go free too.
        for (Entity e : w.getNearbyEntities(base.getLocation(), 40, 16, 40)) if (Npcs.has(e, Npcs.BOUND) && Npcs.tagValue(e, Npcs.CAMP) == null) { if (release(e)) n++; }
        s.boundFreed += n;
        boundFreed += n;
        State.Player rec = s.player(p.getUniqueId(), p.getName());
        rec.rescued += n;
        Talk.give(p, new org.bukkit.inventory.ItemStack(Material.EMERALD, 3));
        plugin.reputation().add(p, 5 + Math.min(10, n * 2), "you broke a shackle post");
        p.sendTitle("", ChatColor.YELLOW + "The camp is free", 5, 50, 15);
        broadcast(ChatColor.YELLOW + p.getName() + ChatColor.GRAY + " broke a shackle post: " + n + " of the Bound walk free (" + s.campsFreed.size() + " camps freed).");
        plugin.saveStateSoon();
        plugin.getLogger().info("ATLAS_CAMP_FREED camp=" + camp + " bound=" + n + " by=" + p.getName() + " total=" + s.campsFreed.size());
    }

    /** One of the Bound becomes free: a stilled one is let rest, anyone else becomes a free person where they stood. */
    boolean release(Entity e) {
        if (!Npcs.has(e, Npcs.BOUND)) return false;
        String kind = Npcs.tagValue(e, "atlas_bound_kind:");
        Location at = e.getLocation();
        if ("stilled".equals(kind)) {
            at.getWorld().spawnParticle(Particle.SPELL_INSTANT, at.clone().add(0, 1, 0), 12, 0.2, 0.5, 0.2, 0.01);
            e.remove();
            return true;
        }
        e.removeScoreboardTag(Npcs.BOUND);
        for (String t : new ArrayList<>(e.getScoreboardTags())) if (t.startsWith("atlas_bound_kind:") || t.startsWith(Npcs.CAMP)) e.removeScoreboardTag(t);
        e.addScoreboardTag(Npcs.CITIZEN);
        e.addScoreboardTag("atlas_role:freed");
        String name = e.getCustomName() == null ? "Someone" : ChatColor.stripColor(e.getCustomName());
        int comma = name.indexOf(',');
        e.setCustomName(ChatColor.WHITE + (comma > 0 ? name.substring(0, comma) : name) + ChatColor.GRAY + ", freed");
        at.getWorld().spawnParticle(Particle.VILLAGER_HAPPY, at.clone().add(0, 1.6, 0), 6, 0.3, 0.3, 0.3, 0);
        return true;
    }

    // ------------------------------------------------------------------ the House of Return, and keeping things consistent

    /** Every 10 seconds: rescued captives wait at their berths; freed camps' and liberated provinces' Bound are free. */
    void maintain(World w) {
        if (w == null) return;
        State s = plugin.state();
        Map<String, Entity> atBerth = new HashMap<>();
        List<Entity> stale = new ArrayList<>();
        for (Entity e : w.getEntities()) {
            String cap = Npcs.tagValue(e, Npcs.CAPTIVE);
            if (cap != null && s.captivesRescued.containsKey(cap)) { stale.add(e); continue; }
            String home = Npcs.tagValue(e, "atlas_rescued:");
            if (home != null) { if (atBerth.containsKey(home)) stale.add(e); else atBerth.put(home, e); continue; }
            if (s.victory && Npcs.has(e, Npcs.CHOIR)) { Liberation.freeSinger(e); continue; }
            if (Npcs.has(e, Npcs.BOUND)) {
                String camp = Npcs.tagValue(e, Npcs.CAMP);
                Realm.Zone z = Realm.zone(e.getLocation().getBlockX(), e.getLocation().getBlockZ());
                if (camp != null && s.campsFreed.contains(camp) || z.province != null && s.liberated(z.province) || s.victory) { if (release(e)) { s.boundFreed++; boundFreed++; } }
            }
        }
        for (Entity e : stale) e.remove();
        plugin.liberation().carveIfNeeded(w);
        if (!plugin.registry().ready()) return;
        for (String id : s.captivesRescued.keySet()) {
            if (atBerth.containsKey(id)) continue;
            Registry.Spot spot = plugin.registry().spot("berth:" + berth(id));
            if (spot == null || !w.isChunkLoaded(spot.x >> 4, spot.z >> 4)) continue;
            boolean near = false;
            for (Player p : w.getPlayers()) if (p.getLocation().distanceSquared(spot.at(w)) < 80 * 80) near = true;
            if (!near) continue;
            Lore.Captive c = Lore.captive(id);
            if (c == null) continue;
            w.spawn(spot.at(w), Villager.class, v -> {
                v.setProfession(c.profession);
                v.setCustomName(ChatColor.WHITE + c.name + ChatColor.GRAY + ", " + c.title);
                v.setCustomNameVisible(false);
                v.setRemoveWhenFarAway(false);
                v.setAI(false);
                v.setInvulnerable(true);
                v.setCollidable(false);
                v.addScoreboardTag(Npcs.TAG);
                v.addScoreboardTag("atlas_rescued:" + id);
            });
            plugin.getLogger().info("ATLAS_CAPTIVE_HOME id=" + id + " berth=" + berth(id));
        }
    }

}
