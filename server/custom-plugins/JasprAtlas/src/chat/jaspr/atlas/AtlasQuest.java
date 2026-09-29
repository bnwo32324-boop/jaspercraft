package chat.jaspr.atlas;

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
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.map.MapCursor;
import org.bukkit.map.MapPalette;

/**
 * How Atlas is beaten, for the gate guide kit (compass, checklist, map): the Codex's road, one task at a time. Meet
 * Kleio; take each Ash-Crowned's weakness from the Concord (the Oath, the Hymn, the Counterpoint, the Charter) and break
 * their crowns (Kallias first: his Veil bars the Teeth); take the Light of Theano and end the Pyrarch. The four crowns,
 * the mechanisms and the Pyrarch are shared (whoever breaks one breaks it for everyone); what a player has learned is
 * their own. Someone who comes after Atlas is free faces the Echo of the Pyrarch to conquer it themselves.
 */
final class AtlasQuest implements GuideKit.Realm {
    private final AtlasPlugin plugin;
    private final Map<UUID, Location> gates = new HashMap<>();

    AtlasQuest(AtlasPlugin plugin) { this.plugin = plugin; }

    @Override public String key() { return "atlas"; }
    @Override public String title() { return "Atlas"; }
    @Override public String goal() { return "End the Pyrarch and free Atlas"; }
    @Override public String guideName() { return "Atlas Guide"; }
    @Override public String gate() { return "a quartz gate (kindled with lapis and coal)"; }
    @Override public ChatColor colour() { return ChatColor.AQUA; }
    @Override public Villager.Profession profession() { return Villager.Profession.LIBRARIAN; }
    @Override public int order() { return 0; }
    @Override public boolean inRealm(World w) { return plugin.isAtlas(w); }
    @Override public boolean needle() { return true; }
    @Override public Particle trail() { return Particle.END_ROD; }
    @Override public int mapScale() { return 16; }   // the whole realm, Rim to Rim, on one map

    void gate(Player p, Location at) { gates.put(p.getUniqueId(), at.clone()); }
    Location takeGate(Player p) { return gates.remove(p.getUniqueId()); }

    // ---- the tasks --------------------------------------------------------------------------------------------------
    private Location spot(String kind) {
        World w = plugin.atlas();
        if (w == null || !plugin.registry().ready()) return null;
        Registry.Spot s = plugin.registry().spot(kind);
        return s == null ? null : new Location(w, s.x + 0.5, s.y, s.z + 0.5);
    }

    private Location place(Realm.Place p) {
        World w = plugin.atlas();
        return w == null ? null : new Location(w, p.x + 0.5, 70, p.z + 0.5);
    }

    private Location or(Location a, Location b) { return a != null ? a : b; }

    /** The first of a boss's three mechanisms still working (lowest first: the Governors must go in order), else null. */
    private Location nextMechanism(String kind) {
        State s = plugin.state();
        for (int k = 0; k < 3; k++) if (!s.mechanisms.contains(kind + ":" + k)) return spot("mech:" + kind + ":" + k);
        return null;
    }

    private int count(String prefix) { int n = 0; for (String m : plugin.state().mechanisms) if (m.startsWith(prefix)) n++; return n; }

    /** The Pyrarch (or his Echo) where he fights; the Heart's root when he hides behind the Cinder Heart. */
    private Location pyrarchTarget() {
        Bosses.Fight f = plugin.bosses().fight(Bosses.Boss.PYRARCH);
        World w = plugin.atlas();
        if (f != null && w != null) {
            if (f.phase == 3 && !f.heartBroken) return new Location(w, Realm.TX + 0.5, Mechanisms.heartRootY(), Realm.TZ + 0.5);
            LivingEntity e = f.entity();
            if (e != null) return e.getLocation();
        }
        return or(spot("boss:pyrarch"), place(Realm.Place.ANTHRAKION));
    }

    @Override public List<GuideKit.Task> tasks(Player p) {
        State s = plugin.state();
        State.Player rec = s.player(p.getUniqueId(), p.getName());
        boolean won = plugin.guide() != null && plugin.guide().beaten(p);
        boolean kallias = s.bossesFallen.containsKey("kallias"), melaina = s.bossesFallen.containsKey("melaina"),
            daidaros = s.bossesFallen.containsKey("daidaros"), keleos = s.bossesFallen.containsKey("keleos");
        List<GuideKit.Task> t = new ArrayList<>();
        t.add(new GuideKit.Task("Meet Archon Kleio",
            "She waits in the Synedrion, the great hall at the heart of Astreion (east of the Gate). Right-click her to talk.",
            won || rec.met.contains("kleio"), or(spot("key:kleio"), place(Realm.Place.ASTREION)), "Kleio in the Synedrion, Astreion"));
        t.add(new GuideKit.Task("Take the Oath from Lysandra",
            "Lysandra keeps Kallias's boyhood Oath at the Stoa of Shields in Astreion. Talk to her; she hands it over.",
            won || kallias || rec.knows.contains("oath"), or(spot("key:lysandra"), place(Realm.Place.ASTREION)), "Lysandra at the Stoa of Shields"));
        t.add(new GuideKit.Task("Break Kallias at the Pylon of Teeth",
            "Hold the Oath in your hand within 24 blocks of Kallias: only then can he be hurt. His Veil bars the Teeth until he falls.",
            won || kallias, or(spot("boss:kallias"), place(Realm.Place.PYLON)), "Kallias at the Pylon of Teeth"));
        t.add(new GuideKit.Task("Learn the Hymn from Iaso",
            "Iaso teaches the Hymn of Passage at the Asklepieion in Hieranthe of the Gardens (south-west).",
            won || melaina || rec.knows.contains("hymn"), or(spot("key:iaso"), place(Realm.Place.HIERANTHE)), "Iaso at the Asklepieion, Hieranthe"));
        Location font = melaina ? null : nextMechanism("font");
        t.add(new GuideKit.Task("Break Melaina, the Stiller",
            "Hold the Hymn and right-click each of her three Stilling Fonts (" + count("font:") + " of 3 quiet); then she can be hurt.",
            won || melaina, or(font, or(spot("boss:melaina"), place(Realm.Place.STILLED_GARDEN))), font != null ? "a Stilling Font, the Stilled Garden" : "Melaina in the Stilled Garden"));
        t.add(new GuideKit.Task("Get the Counterpoint from Perdix",
            "Perdix keeps Daidaros's old notebook, the Counterpoint, at the Mechaneion in Lampsa of the Lamps (north-west).",
            won || daidaros || rec.knows.contains("counterpoint"), or(spot("key:perdix"), place(Realm.Place.LAMPSA)), "Perdix at the Mechaneion, Lampsa"));
        Location governor = daidaros ? null : nextMechanism("governor");
        t.add(new GuideKit.Task("Break Daidaros, the Forgemaster",
            "Carry the Counterpoint and strike the Great Engine's three Governors in its order: deep, middle, high (" + count("governor:") + " of 3). Then he can be hurt.",
            won || daidaros, or(governor, or(spot("boss:daidaros"), place(Realm.Place.GREAT_ENGINE))), governor != null ? "the next Governor, the Great Engine" : "Daidaros in the Great Engine"));
        t.add(new GuideKit.Task("Get the Charter from Hesper",
            "Hesper keeps the Founding Charter in the Archive of Mnemeia, the City of Memory (far north-west).",
            won || keleos || rec.knows.contains("charter"), or(spot("key:hesper"), place(Realm.Place.MNEMEIA)), "Hesper in the Archive, Mnemeia"));
        Location edict = keleos ? null : nextMechanism("edict");
        t.add(new GuideKit.Task("Break Keleos, the Silent Magistrate",
            "Hold the Charter and right-click each of his three Edict Stones in Pellene (" + count("edict:") + " of 3 silent). Then he can be hurt.",
            won || keleos, or(edict, or(spot("boss:keleos"), place(Realm.Place.PELLENE))), edict != null ? "an Edict Stone in Pellene" : "Keleos in Pellene"));
        t.add(new GuideKit.Task("Take the Light of Theano from Kleio",
            "When all four Wards around Anthrakion are dark (" + s.wardsDark() + " of 4), Archon Kleio lends the Light. Ask her for it.",
            won || rec.knows.contains("light"), or(spot("key:kleio"), place(Realm.Place.ASTREION)), "Kleio in the Synedrion, Astreion"));
        boolean echo = s.victory && !won;
        t.add(new GuideKit.Task(echo ? "Defeat the Echo of the Pyrarch" : "End the Pyrarch in Anthrakion",
            (echo ? "Atlas is free, but an echo of the Pyrarch rises in the Cinder Throne for anyone who did not see him fall. " : "Climb Anthrakion to the Cinder Throne. ")
                + "When he hides behind the Cinder Heart, hold the Light at the Heart's root (the black column above the throne) until it breaks.",
            won, pyrarchTarget(), echo ? "the Echo in the Cinder Throne, Anthrakion" : "the Pyrarch in the Cinder Throne, Anthrakion"));
        return t;
    }

    @Override public List<String> tips() {
        return Arrays.asList(
            ChatColor.BOLD + "TIPS" + ChatColor.RESET + "\n\nRight-click anyone in Atlas to talk.\n\nLost the Oath, Hymn, Counterpoint, Charter or Light? The one who gave it gives it again.\n\n"
                + "The Wayfarer's Codex (from Philon at the Gate) keeps the whole story.",
            ChatColor.BOLD + "GETTING ABOUT" + ChatColor.RESET + "\n\nHeliodromes (rings of quartz and light) carry you between the cities you have touched.\n\n"
                + "The ash of the Dominion is dangerous: bring armour and food.\n\nGoing home: walk into any quartz gate.");
    }

    // ---- the map: the whole realm, its lands and places, the next task ---------------------------------------------------
    private static final byte CONCORD = MapPalette.matchColor(118, 152, 84), LINE = MapPalette.matchColor(210, 200, 170),
        WOUND = MapPalette.matchColor(110, 100, 86), ASH = MapPalette.matchColor(72, 64, 60), TEETH = MapPalette.matchColor(40, 36, 36),
        PLATEAU = MapPalette.matchColor(96, 44, 34), RIM = MapPalette.matchColor(24, 24, 30), FREED = MapPalette.matchColor(126, 160, 96),
        CITY = MapPalette.matchColor(236, 232, 220), HOLD = MapPalette.matchColor(34, 30, 30);

    @Override public int[] mapCentre(Player p) { return new int[]{0, 0}; }

    /** Liberation and victory recolour the land: the map is painted again when either changes. */
    @Override public int paintVersion() { return plugin.state().liberated + (plugin.state().victory ? 64 : 0); }

    @Override public byte paint(int x, int z) {
        Realm.Place pl = Realm.placeAt(x, z);
        if (pl != null && pl.radius >= 20) return pl.province == null ? CITY : HOLD;
        Realm.Zone zone = Realm.zone(x, z);
        switch (zone) {
            case CONCORD: return CONCORD;
            case LINE: return LINE;
            case WOUND: return WOUND;
            case TEETH: return TEETH;
            case RIM: return RIM;
            case PLATEAU: return plugin.state().victory ? FREED : PLATEAU;
            default: return zone.province != null && plugin.state().liberated(zone.province) ? FREED : ASH;
        }
    }

    private static final Realm.Place[] CITIES = {Realm.Place.GATE_OF_STRANGERS, Realm.Place.ASTREION, Realm.Place.LAMPSA, Realm.Place.HIERANTHE, Realm.Place.MNEMEIA};
    private static final Object[][] CROWNS = {{Realm.Place.PYLON, "kallias"}, {Realm.Place.STILLED_GARDEN, "melaina"}, {Realm.Place.GREAT_ENGINE, "daidaros"},
        {Realm.Place.PELLENE, "keleos"}, {Realm.Place.ANTHRAKION, "pyrarch"}};

    @Override public List<GuideKit.Marker> markers(Player p) {
        List<GuideKit.Marker> out = new ArrayList<>();
        for (Realm.Place c : CITIES) out.add(new GuideKit.Marker(c.x, c.z, c == Realm.Place.GATE_OF_STRANGERS ? MapCursor.Type.SMALL_WHITE_CIRCLE : MapCursor.Type.WHITE_CIRCLE));
        for (Object[] c : CROWNS) {
            Realm.Place pl = (Realm.Place) c[0];
            boolean fallen = plugin.state().bossesFallen.containsKey((String) c[1]);
            out.add(new GuideKit.Marker(pl.x, pl.z, fallen ? MapCursor.Type.WHITE_CROSS : pl == Realm.Place.ANTHRAKION ? MapCursor.Type.TEMPLE : MapCursor.Type.MANSION));
        }
        return out;
    }

    @Override public List<GuideKit.Label> labels(Player p) {
        List<GuideKit.Label> out = new ArrayList<>();
        String[][] names = {{"ASTREION", "Astreion"}, {"LAMPSA", "Lampsa"}, {"HIERANTHE", "Hieranthe"}, {"MNEMEIA", "Mnemeia"}, {"PYLON", "Pylon"},
            {"STILLED_GARDEN", "Garden"}, {"GREAT_ENGINE", "Engine"}, {"PELLENE", "Pellene"}, {"ANTHRAKION", "Anthrakion"}};
        for (String[] n : names) { Realm.Place pl = Realm.Place.valueOf(n[0]); out.add(new GuideKit.Label(pl.x, pl.z, n[1])); }
        return out;
    }
}
