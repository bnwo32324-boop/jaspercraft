package chat.jaspr.dungeon;

import java.util.*;
import org.bukkit.*;
import org.bukkit.entity.Player;

/**
 * Generation 7 (owner 2026-10-05): "you should be able to go to different tiers, just like in The Binding of Isaac ...
 * after you progress enough." Each of Floors I and II has one Descent, Floor III the Throne: a whole parcel at distance 3
 * (Floors.descentParcel). Its doorways stay sealed until the floor world has Floors.clearsRequired(floor) absolved rooms.
 * Inside waits the floor's guardian; when the arena is absolved its Descent opens at the centre (a lit well of nine cells):
 * stand in it for a moment and the run's next floor takes you, at its own Last Candle. The Throne's fall wins the run:
 * standing in its well sends the winner home with the victory. Floors are one-way.
 *
 * Logs DUNGEON_DESCENT_SEALED (once per player and arena), DUNGEON_DESCENT_OPENED, DUNGEON_DESCENT_TAKEN, DUNGEON_RUN_WON.
 */
public final class Descents {
    private final DungeonPlugin plugin;
    /** Opened finale rooms: world/room keys. */
    private final Set<String> open = new HashSet<>();
    private final Map<UUID, Integer> dwell = new HashMap<>();
    private final Map<UUID, Long> refused = new HashMap<>();
    private final Set<String> sealedLogged = new HashSet<>();
    private long ticks;
    public static final int DWELL_TICKS = 30;

    Descents(DungeonPlugin plugin) { this.plugin = plugin; }

    /** The finale room of this world's floor (the Descent or the Throne), or null in a rift. */
    public Layout.Room finale(World w) {
        DungeonGenerator g = plugin.generator(w);
        if (g == null) return null;
        int[] p = g.layout.finaleParcel();
        return p == null ? null : g.layout.at(p[0] * Layout.PARCEL + 1, p[1] * Layout.PARCEL + 1);
    }
    public boolean opened(World w, Layout.Room r) { return w != null && r != null && open.contains(plugin.roomKey(w, r)); }
    /** True while this finale room still turns players away: the floor has not absolved enough rooms. */
    public boolean sealed(World w, Layout.Room r) {
        return w != null && r != null && r.finale() && plugin.encounters != null && plugin.encounters.clears(w) < Floors.clearsRequired(r.floor);
    }
    public void refuse(Player p, Layout.Room r) {
        long now = System.currentTimeMillis();
        if (now < refused.getOrDefault(p.getUniqueId(), 0L)) return;
        refused.put(p.getUniqueId(), now + 2500);
        int have = plugin.encounters.clears(p.getWorld()), need = Floors.clearsRequired(r.floor);
        p.sendTitle(ChatColor.DARK_PURPLE + (r.kind == Layout.Kind.THRONE ? "The Throne is sealed" : "The Descent is sealed"),
            ChatColor.GRAY + "Absolve " + (need - have) + " more room" + (need - have == 1 ? "" : "s") + " of this floor (" + have + "/" + need + ")", 5, 40, 10);
        p.playSound(p.getLocation(), Sound.BLOCK_IRON_DOOR_CLOSE, .7f, .6f);
        if (sealedLogged.add(p.getUniqueId() + "/" + plugin.roomKey(p.getWorld(), r)))
            plugin.getLogger().info("DUNGEON_DESCENT_SEALED player=" + p.getName() + " floor=" + r.floor + " clears=" + have + "/" + need);
    }
    /** A room of this world was absolved: perhaps the seal breaks (said once), perhaps the finale itself fell. */
    void cleared(Encounters.Run a) {
        if (a.world == null) return;
        Layout.Room f = finale(a.world);
        int have = plugin.encounters.clears(a.world), need = Floors.clearsRequired(a.room.floor);
        if (a.room.finale()) { open(a); return; }
        if (f != null && have == need) for (Player p : a.world.getPlayers())
            p.sendMessage(ChatColor.DARK_PURPLE + (f.kind == Layout.Kind.THRONE ? "The Throne's seal breaks. " : "The Descent's seal breaks. ")
                + ChatColor.GRAY + Guardians.name(f) + " waits " + Floors.direction(p.getLocation().getX(), p.getLocation().getZ(), f.cx(), f.cz()) + ".");
    }
    private void open(Encounters.Run a) {
        String key = plugin.roomKey(a.world, a.room);
        if (!open.add(key)) return;
        Location c = new Location(a.world, a.room.cx() + .5, 65, a.room.cz() + .5);
        // The well: a lit ring around three by three cells at the arena's centre (the run's world is deleted with the run).
        for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++) {
            boolean ring = Math.max(Math.abs(dx), Math.abs(dz)) == 2;
            a.world.getBlockAt(a.room.cx() + dx, 64, a.room.cz() + dz).setType(ring ? Material.SEA_LANTERN : Material.OBSIDIAN, false);
        }
        boolean throne = a.room.kind == Layout.Kind.THRONE;
        for (Player p : a.world.getPlayers()) {
            if (!plugin.roomKey(p.getLocation()).equals(key)) continue;
            p.sendTitle(ChatColor.GOLD + (throne ? "The Abyss is conquered" : "The Descent opens"),
                ChatColor.GRAY + (throne ? "Stand in the well to return home victorious" : "Stand in the well to descend to Floor " + Floors.numeral(a.room.floor + 1)), 10, 70, 20);
            p.playSound(c, throne ? Sound.UI_TOAST_CHALLENGE_COMPLETE : Sound.ENTITY_ENDERDRAGON_GROWL, 1f, throne ? 1f : .7f);
        }
        plugin.getLogger().info("DUNGEON_DESCENT_OPENED room=" + key + " floor=" + a.room.floor + " throne=" + throne);
        if (plugin.trophies != null) plugin.trophies.award(a);
    }
    public void tick() {
        ticks++;
        for (World w : plugin.dungeonWorlds()) {
            if (plugin.rifts != null && plugin.rifts.contains(w)) continue;
            Layout.Room f = finale(w);
            if (f == null || !opened(w, f)) continue;
            if (ticks % 5 == 0) for (Player p : w.getPlayers()) {
                if (p.getLocation().distanceSquared(new Location(w, f.cx() + .5, 65, f.cz() + .5)) > 40 * 40) continue;
                for (int n = 0; n < 6; n++) {
                    double a = ticks * .08 + n * Math.PI / 3;
                    p.spawnParticle(f.kind == Layout.Kind.THRONE ? Particle.TOTEM : Particle.PORTAL, f.cx() + .5 + 1.4 * Math.cos(a), 65.2 + (n % 3) * .4, f.cz() + .5 + 1.4 * Math.sin(a), 2, .05, .1, .05, 0);
                }
            }
            for (Player p : new ArrayList<>(w.getPlayers())) {
                if (p.isDead() || p.getGameMode() == GameMode.SPECTATOR) continue;
                Location l = p.getLocation();
                boolean in = Math.abs(l.getX() - (f.cx() + .5)) <= 1.5 && Math.abs(l.getZ() - (f.cz() + .5)) <= 1.5 && l.getY() >= 64.5 && l.getY() < 67;
                if (!in) { dwell.remove(p.getUniqueId()); continue; }
                int n = dwell.merge(p.getUniqueId(), 1, Integer::sum);
                if (n % 10 == 0) p.playSound(l, Sound.BLOCK_PORTAL_TRIGGER, .25f, 1.6f);
                if (n < DWELL_TICKS) continue;
                dwell.remove(p.getUniqueId());
                if (f.kind == Layout.Kind.THRONE) win(p, w); else descend(p, w, f.floor + 1);
            }
        }
    }
    private void descend(Player p, World from, int floor) {
        Sessions.Session run = plugin.sessions.of(from);
        if (run == null) return;
        try {
            World next = plugin.sessions.floorWorld(run, floor);
            Location arrival = plugin.sanctuary.arrival(next);
            if (arrival == null) { p.sendMessage(ChatColor.YELLOW + "The way down is obstructed. The well will not send you into danger."); return; }
            if (!plugin.move(p, arrival)) return;
            p.sendTitle(ChatColor.DARK_RED + "Floor " + Floors.numeral(floor), ChatColor.GRAY + Floors.title(floor), 10, 60, 20);
            p.sendMessage(ChatColor.DARK_PURPLE + "You descend to " + Floors.title(floor) + ". " + ChatColor.GRAY + "Harder rooms, stronger foes, richer reliquaries. There is no way back up; the gate here leads home.");
            plugin.getLogger().info("DUNGEON_DESCENT_TAKEN run=" + run.id + " player=" + p.getName() + " floor=" + floor);
        } catch (RuntimeException ex) {
            p.sendMessage(ChatColor.YELLOW + "The way down cannot open safely right now.");
            plugin.getLogger().warning("DUNGEON_DESCENT_FAILED floor=" + floor + " " + ex.getMessage());
        }
    }
    private void win(Player p, World from) {
        Sessions.Session run = plugin.sessions.of(from);
        p.sendTitle(ChatColor.GOLD + "Victory", ChatColor.GRAY + "The Abyssal Citadel has fallen", 10, 80, 20);
        p.sendMessage(ChatColor.GOLD + "You have conquered all three floors of the Dungeon Dimension." + ChatColor.GRAY + " The Last Candle carries you home.");
        plugin.getLogger().info("DUNGEON_RUN_WON run=" + (run == null ? "-" : String.valueOf(run.id)) + " player=" + p.getName());
        plugin.gates.leave(p, "victory");
        if (plugin.trophies != null) plugin.trophies.victory(p, run);
    }
    /** For room titles: where this floor's Descent or Throne lies from here, or that it is open. */
    public String hint(World w, Layout.Room here) {
        Layout.Room f = finale(w);
        if (f == null) return "";
        String what = f.kind == Layout.Kind.THRONE ? "The Throne" : "The Descent";
        if (f.id().equals(here.id())) return opened(w, f) ? what + " is open" : Guardians.name(f) + " " + Guardians.epithet(f);
        int have = plugin.encounters.clears(w), need = Floors.clearsRequired(f.floor);
        int rooms = Math.max(1, (int) Math.round(Math.max(Math.abs(f.cx() - here.cx()), Math.abs(f.cz() - here.cz())) / 64.0));
        return what + ": " + Floors.direction(here.cx(), here.cz(), f.cx(), f.cz()) + ", ~" + rooms + " room" + (rooms == 1 ? "" : "s") + (have < need ? " | sealed " + have + "/" + need : " | unsealed");
    }
    public void forget(String world) { open.removeIf(k -> k.startsWith(world + "/")); }
}
