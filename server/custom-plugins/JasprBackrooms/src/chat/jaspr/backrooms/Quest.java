package chat.jaspr.backrooms;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.map.MapCursor;

/**
 * How the Backrooms are beaten, for the guide kit (compass, checklist, map): beat each level's boss in turn, walking
 * through each exit into the next level, until the Lifeguard at the bottom of the Poolrooms falls. The compass points to
 * whatever is next from wherever the player stands: the boss's arena in the level they are in, the exit when that
 * boss is beaten, or the Threshold's door to a level they reached before.
 */
final class Quest implements GuideKit.Realm {
    static final String[] ARENAS = {"the Dark Room", "the Loading Bay", "the Boiler Room", "the Substation", "the Boardroom", "City Hall plaza", "the Deep End"};
    static final String[] HINTS = {
        "Head east through the yellow rooms. The Smiler waits in the Dark Room at the far end: do not let it stare at you for long.",
        "Cross the Warehouse eastwards through the hall gates. The Foreman guards the Loading Bay; it throws crates.",
        "Find your way east through the hot tunnels. Magma vents burn; water cools you. The Stoker keeps the Boiler Room.",
        "East through the Electrical Corridors: red floor tiles are live. The Live Wire hums in the Substation.",
        "East through the office, hallway by hallway. The Manager holds a meeting in the Boardroom.",
        "Follow the avenue east through the city. Not everyone on the streets is what they seem. The Stranger waits at City Hall.",
        "Wade east through the Poolrooms. Deep pools pull you down. The Lifeguard waits in the Deep End. Beat it to get out."};

    private final BackroomsPlugin plugin;
    private final Map<Long, Byte> colours = new HashMap<>();
    private final Map<UUID, Location> gates = new HashMap<>();

    Quest(BackroomsPlugin plugin) { this.plugin = plugin; }

    @Override public String key() { return "backrooms"; }
    @Override public String title() { return "The Backrooms"; }
    @Override public String goal() { return "Find the way out: beat the Lifeguard"; }
    @Override public String guideName() { return "Backrooms Guide"; }
    @Override public String gate() { return "a yellow glazed terracotta portal (only a conqueror can light it)"; }
    @Override public ChatColor colour() { return ChatColor.YELLOW; }
    @Override public Villager.Profession profession() { return Villager.Profession.BUTCHER; }
    @Override public int order() { return 3; }
    @Override public boolean inRealm(World w) { return plugin.isBackrooms(w); }
    @Override public boolean needle() { return true; }
    @Override public Particle trail() { return Particle.END_ROD; }
    @Override public int mapScale() { return 2; }

    void gate(Player p, Location at) { gates.put(p.getUniqueId(), at.clone()); }
    Location takeGate(Player p) { return gates.remove(p.getUniqueId()); }

    private Location at(double[] xyz) {
        World w = plugin.world();
        return w == null ? null : new Location(w, xyz[0], xyz[1], xyz[2]);
    }

    private Location at(int x, int y, int z) {
        World w = plugin.world();
        return w == null ? null : new Location(w, x + 0.5, y, z + 0.5);
    }

    /** The level a player is in (the Threshold counts as level 1), or null outside the Backrooms. */
    Level here(Player p) {
        if (!plugin.isBackrooms(p.getWorld())) return null;
        return Level.at(p.getLocation().getX(), p.getLocation().getZ());
    }

    @Override public List<GuideKit.Task> tasks(Player p) {
        boolean won = plugin.guide() != null && plugin.guide().beaten(p);
        List<GuideKit.Task> t = new ArrayList<>();
        int current = -1;
        for (Level lv : Level.ALL) if (current < 0 && !won && !Travel.cleared(p, lv.number)) current = lv.index();
        Level here = here(p);
        for (Level lv : Level.ALL) {
            boolean done = won || Travel.cleared(p, lv.number);
            String title = "Level " + lv.number + ": beat " + lv.boss.replace("The ", "the ");
            if (lv.index() != current) { t.add(new GuideKit.Task(title, HINTS[lv.index()], done, null, ARENAS[lv.index()])); continue; }
            Location target = null;
            String where = ARENAS[lv.index()] + " (" + lv.title + ")", hint = HINTS[lv.index()];
            if (here == lv) {
                Location boss = plugin.bosses().bossAt(lv);
                int[] b = Styles.of(lv).bossSpot(lv);
                target = boss != null ? boss : at(b[0], b[1], b[2]);
                where = ARENAS[lv.index()] + ", at the far east end of " + lv.title;
            } else if (here != null && here.number < lv.number) {
                if (here.number == 1 && Travel.reached(p, lv.number)) {
                    int[] a = Rooms.doorAlcove(lv.number);
                    target = at(a[0], Level.WALK, a[1]);
                    where = "the Threshold's door to Level " + lv.number;
                    hint = "Walk into the door marked Level " + lv.number + " in the Threshold. " + hint;
                } else {
                    target = at(here.xEnd() - Level.WALL - 3, Level.WALK, 0);
                    where = "the exit of " + here.title + " (on to " + here.next().title + ")";
                    hint = "You beat this level's boss: walk east to the glowing exit behind its arena. " + hint;
                }
            } else if (here != null) {
                target = here.number == 1 ? at(Rooms.threshold()) : at(here.x0() + 3, Level.WALK, 0);
                where = here.number == 1 ? "the Threshold" : "the way back to the Threshold, at the west end of this landing";
                hint = "Go back to the Threshold and take the door to Level " + lv.number + ". " + hint;
            }
            t.add(new GuideKit.Task(title, hint, done, target, where));
        }
        return t;
    }

    @Override public List<String> tips() {
        StringBuilder levels = new StringBuilder(ChatColor.BOLD + "THE LEVELS" + ChatColor.RESET + "\n");
        for (Level lv : Level.ALL) levels.append('\n').append(lv.number).append(". ").append(lv.title.replace("The ", "")).append(" - ").append(lv.boss.replace("The ", ""));
        return Arrays.asList(
            ChatColor.BOLD + "SURVIVE" + ChatColor.RESET + "\n\nThe Threshold is safe. Every level is more dangerous than the last, and each grows worse the further east you go."
                + " Nothing here can be broken, only what you place yourself.\n\nDrink Almond Water.",
            levels.toString(),
            ChatColor.BOLD + "GEAR" + ChatColor.RESET + "\n\nEach level hides its own armour set (wear all four for a bonus), a weapon, a tool and trinkets."
                + " Chests and bosses deeper in hold better things.",
            ChatColor.BOLD + "GOING HOME" + ChatColor.RESET + "\n\nStand in the Threshold's gate for three seconds.\n\nEvery landing has a way back to the Threshold,"
                + " and the Threshold has a door to every level you have reached.");
    }

    @Override public String hud(Player p) {
        Level lv = here(p);
        if (lv == null) return null;
        Location l = p.getLocation();
        String line = lv.colour + "Lv " + lv.number + ChatColor.GRAY + " danger " + Math.round(100 * lv.danger(l.getX(), l.getZ())) + "%";
        String hazard = plugin.hazards() == null ? null : plugin.hazards().hud(p);
        return hazard == null ? line : line + " " + hazard;
    }

    // ---- the map: the level around the player, its arena, exit and landing ------------------------------------------------
    @Override public int[] mapCentre(Player p) {
        int span = 128 * mapScale() / 4;
        Location l = p.getLocation();
        return new int[] {Math.floorDiv(l.getBlockX() + span / 2, span) * span, Math.floorDiv(l.getBlockZ() + span / 2, span) * span};
    }

    @Override public byte paint(int x, int z) {
        long k = (long) x << 32 ^ (z & 0xffffffffL);
        Byte c = colours.get(k);
        if (c != null) return c;
        byte b = Styles.ink(plugin.seed(), x, z);
        if (colours.size() > 200_000) colours.clear();
        colours.put(k, b);
        return b;
    }

    @Override public List<GuideKit.Marker> markers(Player p) {
        List<GuideKit.Marker> out = new ArrayList<>();
        Level lv = here(p);
        if (lv == null) return out;
        out.add(new GuideKit.Marker(lv.arenaCentreX(), 0, MapCursor.Type.TEMPLE));
        out.add(new GuideKit.Marker(lv.xEnd() - Level.WALL - 3, 0, MapCursor.Type.MANSION));
        double[] a = Rooms.arrival(lv);
        out.add(new GuideKit.Marker((int) a[0], (int) a[2], MapCursor.Type.WHITE_CROSS));
        return out;
    }

    @Override public List<GuideKit.Label> labels(Player p) {
        List<GuideKit.Label> out = new ArrayList<>();
        Level lv = here(p);
        if (lv == null) return out;
        out.add(new GuideKit.Label(lv.arenaCentreX(), 0, lv.boss.replace("The ", "")));
        out.add(new GuideKit.Label(lv.xEnd() - Level.WALL - 3, 0, "EXIT"));
        double[] a = Rooms.arrival(lv);
        out.add(new GuideKit.Label((int) a[0], (int) a[2], lv.number == 1 ? "Threshold" : "Landing"));
        return out;
    }
}
