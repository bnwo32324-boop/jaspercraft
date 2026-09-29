package chat.jaspr.ruins;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.map.MapCursor;
import org.bukkit.map.MapPalette;

/**
 * How Drownhollow is beaten, for the gate guide kit (compass, checklist, map): win three different Warden Seals, set
 * them into the Great Door, slay the Dreamer's Herald behind it. The Door is shared: Seals someone already set count
 * for everyone, and after each Herald it closes and wants three new Seals. Tasks are pure reads of the plan (Plans),
 * the Door's state (Bosses) and the player's inventory.
 */
final class RuinsQuest implements GuideKit.Realm {
    private final RuinsPlugin plugin;
    /** Where a player last came through a gate (the guide stands there), until they are welcomed. */
    private final Map<UUID, Location> gates = new HashMap<>();
    private final Map<Long, Byte> colours = new HashMap<>();

    RuinsQuest(RuinsPlugin plugin) { this.plugin = plugin; }

    @Override public String key() { return "ruins"; }
    @Override public String title() { return "Drownhollow"; }
    @Override public String goal() { return "Slay the Dreamer's Herald"; }
    @Override public String guideName() { return "Drownhollow Guide"; }
    @Override public String gate() { return "a mossy cobblestone portal"; }
    @Override public ChatColor colour() { return ChatColor.DARK_GREEN; }
    @Override public Villager.Profession profession() { return Villager.Profession.PRIEST; }
    @Override public int order() { return 1; }
    @Override public boolean inRealm(World w) { return plugin.isRuins(w); }
    @Override public boolean needle() { return true; }
    @Override public Particle trail() { return Particle.VILLAGER_HAPPY; }
    @Override public int mapScale() { return 8; }

    void gate(Player p, Location at) { gates.put(p.getUniqueId(), at.clone()); }
    Location takeGate(Player p) { return gates.remove(p.getUniqueId()); }

    // ---- the tasks --------------------------------------------------------------------------------------------------
    /** The Seals that count towards opening the Door for this player: those already in it and those they carry. */
    EnumSet<Trinkets.Seal> counted(Player p) {
        EnumSet<Trinkets.Seal> s = plugin.bosses().doorSeals();
        s.addAll(Trinkets.seals(p));
        return s;
    }

    /** The nearest cult arena whose Seal still counts for nothing, preferring Wardens awake to be fought. */
    Plans.Site nextWarden(Player p, EnumSet<Trinkets.Seal> have) {
        Location at = p.getLocation();
        boolean here = plugin.isRuins(p.getWorld());
        int x0 = here ? at.getBlockX() : 0, z0 = here ? at.getBlockZ() : 0;
        int i0 = Math.floorDiv(x0, Plans.SITE_GRID), j0 = Math.floorDiv(z0, Plans.SITE_GRID);
        Plans.Site best = null, sleeping = null;
        double bestD = Double.MAX_VALUE, sleepD = Double.MAX_VALUE;
        for (int a = i0 - 14; a <= i0 + 14; a++)
            for (int b = j0 - 14; b <= j0 + 14; b++) {
                Plans.Site s = plugin.plans().site(a, b);
                if (s == null || !s.kind.cult) continue;
                Bosses.Boss boss = Bosses.Boss.of(s.kind);
                if (boss == null || have.contains(boss.seal)) continue;
                double dx = s.x - x0, dz = s.z - z0, d = dx * dx + dz * dz;
                if (plugin.bosses().waitLeft(Bosses.arena(s)) > 0) { if (d < sleepD) { sleepD = d; sleeping = s; } }
                else if (d < bestD) { bestD = d; best = s; }
            }
        return best != null ? best : sleeping;
    }

    private Location loc(int x, int y, int z) {
        World w = plugin.ruins();
        return w == null ? null : new Location(w, x + 0.5, y, z + 0.5);
    }

    @Override public List<GuideKit.Task> tasks(Player p) {
        boolean won = plugin.guide() != null && plugin.guide().beaten(p);
        Bosses bosses = plugin.bosses();
        boolean open = bosses.doorOpen();
        EnumSet<Trinkets.Seal> have = counted(p);
        int count = open ? Bosses.SEALS_NEEDED : Math.min(Bosses.SEALS_NEEDED, have.size());
        Plans.Door d = plugin.plans().door();
        List<GuideKit.Task> t = new ArrayList<>();
        Plans.Site s = won || open || count >= Bosses.SEALS_NEEDED ? null : nextWarden(p, have);
        String sealHint, sealWhere;
        Location sealAt = null;
        if (s != null) {
            Bosses.Boss boss = Bosses.Boss.of(s.kind);
            int[] sp = Cult.spawnPoint(s);
            sealAt = loc(sp[0], sp[1], sp[2]);
            long wait = bosses.waitLeft(Bosses.arena(s));
            sealWhere = s.name + " (" + boss.title.replace("The ", "the ") + ")";
            sealHint = "Slay the Warden of a cult site: it wakes when you come close and drops its Seal. The compass leads to the nearest one"
                + " whose Seal you lack." + (wait > 0 ? " This one sleeps " + (wait / 60_000 + 1) + " more minutes." : "");
        } else {
            sealWhere = "a cult site";
            sealHint = "Five kinds of Warden hold the Seals: Sanctum, Circle of the Watchers, Pit, Spawning Pool, Chapel. You need three different ones.";
        }
        String[] nth = {"Win a Warden's Seal", "Win a second, different Seal", "Win a third, different Seal"};
        for (int i = 0; i < Bosses.SEALS_NEEDED; i++) {
            boolean done = won || open || count > i;
            t.add(new GuideKit.Task(nth[i], sealHint, done, done ? null : sealAt, sealWhere));
        }
        long doorWait = bosses.waitLeft("door");
        t.add(new GuideKit.Task("Set the Seals into the Great Door",
            "Walk to the Great Door (an obsidian wall with a great eye) and right-click it with each Seal. It holds " + bosses.doorSeals().size() + " of "
                + Bosses.SEALS_NEEDED + "." + (doorWait > 0 && !open ? " It is settling after the last Herald: " + (doorWait / 60_000 + 1) + " minutes." : ""),
            won || open, loc(d.x, d.base + 1, d.z - 3), "the Great Door"));
        LivingEntity herald = bosses.herald();
        int[] hs = Cult.heraldSpawn(d);
        t.add(new GuideKit.Task("Slay the Dreamer's Herald",
            "The Herald, a giant, wakes in the round hall behind the open Door. Fight it together; bring food, armour and torches.",
            won, herald != null && herald.isValid() ? herald.getLocation() : loc(hs[0], hs[1], hs[2]), herald != null ? "the Herald" : "the Herald's hall"));
        return t;
    }

    @Override public List<String> tips() {
        return Arrays.asList(
            ChatColor.BOLD + "SURVIVE" + ChatColor.RESET + "\n\nThe gates are safe. Danger grows the farther you go; horrors lurk in ruined "
                + "buildings, crypts and catacombs. Carry torches: darkness there fills your " + ChatColor.DARK_PURPLE + "Dread" + ChatColor.BLACK
                + ".\n\nBeds do not work here.",
            ChatColor.BOLD + "THE WARDENS" + ChatColor.RESET + "\n\nHierophant - Sanctum\nPillar Warden - Circle of the Watchers\nBrood Mother - Pit of Offerings\n"
                + "Spawn of the Deep - Spawning Pool\nFaceless Priest - Chapel\n\nEach drops its own Seal.",
            ChatColor.BOLD + "THE REWARD" + ChatColor.RESET + "\n\nThe Herald's hoard: the Crown of the Drowned Star, the Herald's Cleaver, the Wings of the "
                + "Nightgaunt and more.\n\nGoing home: stand in a mossy portal for three seconds.\n\nLost an item? The guide at any gate has more.");
    }

    @Override public String hud(Player p) {
        int d = plugin.horrors().dreadOf(p);
        return d >= 30 ? ChatColor.DARK_PURPLE + "Dread " + d + "%" + (d >= 75 ? ChatColor.LIGHT_PURPLE + " find light!" : "") : null;
    }

    // ---- the map: land and sea, the cult sites, the Door, the next task ---------------------------------------------------
    private static final byte DEEP = MapPalette.matchColor(38, 58, 96), SHALLOW = MapPalette.matchColor(52, 84, 116),
        LOW = MapPalette.matchColor(58, 84, 50), MID = MapPalette.matchColor(74, 96, 58), HIGH = MapPalette.matchColor(96, 104, 82),
        PEAK = MapPalette.matchColor(126, 126, 116), CITY = MapPalette.matchColor(104, 100, 96), CITADEL = MapPalette.matchColor(64, 30, 60);

    @Override public int[] mapCentre(Player p) {
        int span = 128 * mapScale() / 4;   // re-centre whenever the player leaves the middle half
        Location l = p.getLocation();
        return new int[]{Math.floorDiv(l.getBlockX() + span / 2, span) * span, Math.floorDiv(l.getBlockZ() + span / 2, span) * span};
    }

    @Override public byte paint(int x, int z) {
        long k = (long) Math.floorDiv(x, 8) << 32 ^ (Math.floorDiv(z, 8) & 0xffffffffL);
        Byte c = colours.get(k);
        if (c != null) return c;
        byte b = colour(x, z);
        if (colours.size() > 60_000) colours.clear();
        colours.put(k, b);
        return b;
    }

    private byte colour(int x, int z) {
        Plans plans = plugin.plans();
        Plans.Door d = plans.door();
        if (Math.abs(x - d.x) <= 44 && Math.abs(z - d.z) <= 48) return CITADEL;
        Plans.City c = plans.cityNear(x, z);
        if (c != null && c.outside(x, z) <= 0) return CITY;
        int y = plans.terrain.sample(x, z).y;
        if (y <= Plans.SEA - 6) return DEEP;
        if (y <= Plans.SEA) return SHALLOW;
        return y < 72 ? LOW : y < 88 ? MID : y < 108 ? HIGH : PEAK;
    }

    private List<Plans.Site> cultSitesNear(Player p) {
        List<Plans.Site> out = new ArrayList<>();
        int[] c = mapCentre(p);
        int half = 64 * mapScale();
        int i0 = Math.floorDiv(c[0] - half, Plans.SITE_GRID), i1 = Math.floorDiv(c[0] + half, Plans.SITE_GRID);
        int j0 = Math.floorDiv(c[1] - half, Plans.SITE_GRID), j1 = Math.floorDiv(c[1] + half, Plans.SITE_GRID);
        for (int a = i0; a <= i1; a++) for (int b = j0; b <= j1; b++) {
            Plans.Site s = plugin.plans().site(a, b);
            if (s != null && s.kind.cult) out.add(s);
        }
        return out;
    }

    @Override public List<GuideKit.Marker> markers(Player p) {
        List<GuideKit.Marker> out = new ArrayList<>();
        EnumSet<Trinkets.Seal> have = counted(p);
        for (Plans.Site s : cultSitesNear(p)) {
            Bosses.Boss boss = Bosses.Boss.of(s.kind);
            out.add(new GuideKit.Marker(s.x, s.z, boss != null && have.contains(boss.seal) ? MapCursor.Type.WHITE_CIRCLE : MapCursor.Type.TEMPLE));
        }
        Plans.Door d = plugin.plans().door();
        out.add(new GuideKit.Marker(d.x, d.z, MapCursor.Type.MANSION));
        return out;
    }

    @Override public List<GuideKit.Label> labels(Player p) {
        List<GuideKit.Label> out = new ArrayList<>();
        Plans.Door d = plugin.plans().door();
        out.add(new GuideKit.Label(d.x, d.z, "Great Door"));
        // Named: the four nearest arenas whose Seal still counts (every arena is marked; names only where they help).
        EnumSet<Trinkets.Seal> have = counted(p);
        List<Plans.Site> wanted = new ArrayList<>();
        for (Plans.Site s : cultSitesNear(p)) { Bosses.Boss b = Bosses.Boss.of(s.kind); if (b != null && !have.contains(b.seal)) wanted.add(s); }
        Location l = p.getLocation();
        wanted.sort((a, b) -> Double.compare(Math.hypot(a.x - l.getX(), a.z - l.getZ()), Math.hypot(b.x - l.getX(), b.z - l.getZ())));
        for (int i = 0; i < Math.min(4, wanted.size()); i++) out.add(new GuideKit.Label(wanted.get(i).x, wanted.get(i).z, shortName(wanted.get(i).kind)));
        return out;
    }

    static String shortName(Plans.Kind k) {
        switch (k) {
            case SANCTUM: return "Sanctum";
            case MONOLITHS: return "Watchers";
            case PIT: return "Pit";
            case POOL: return "Pool";
            case CHAPEL: return "Chapel";
            default: return k.noun;
        }
    }
}
