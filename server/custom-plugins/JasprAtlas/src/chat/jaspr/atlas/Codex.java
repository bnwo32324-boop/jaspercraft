package chat.jaspr.atlas;

import java.util.ArrayList;
import java.util.List;
import net.md_5.bungee.api.chat.BaseComponent;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;

/**
 * The Wayfarer's Codex: a book written fresh each time it is opened, from what Atlas remembers. It shows the road to
 * victory as a checklist (who teaches what, where, what has fallen), the named captives, the mechanisms silenced, where
 * the great places are, and what the player has learned, including what they were told that may not be true.
 */
final class Codex {
    private Codex() {}

    private static final ChatColor H = ChatColor.DARK_BLUE, T = ChatColor.BLACK, OK = ChatColor.DARK_GREEN, NO = ChatColor.DARK_RED, DIM = ChatColor.DARK_GRAY;

    private static String box(boolean done) { return done ? OK + "[x] " + T : NO + "[ ] " + T; }

    static List<BaseComponent[]> pages(AtlasPlugin plugin, Player p) {
        State s = plugin.state();
        State.Player rec = s.player(p.getUniqueId(), p.getName());
        List<List<String>> sections = new ArrayList<>();

        List<String> title = new ArrayList<>();
        title.add(H + "" + ChatColor.BOLD + "WAYFARER'S CODEX");
        title.add(T + "of " + p.getName());
        title.add("");
        title.add(T + "Standing: " + ChatColor.stripColor(Reputation.title(rec.standing)) + " (" + rec.standing + ")");
        title.add(T + "Rescued: " + rec.rescued + "  Slain: " + rec.slain);
        title.add("");
        title.add(DIM + Voices.progress(s));
        if (s.victory) { title.add(""); title.add(OK + "ATLAS IS FREE."); title.add(T + "The Rekindlers:"); for (String n : s.rekindlers) title.add(T + "- " + n); }
        sections.add(title);

        List<String> road = new ArrayList<>();
        road.add(H + "THE ROAD");
        road.add(box(rec.met.contains("kleio")) + "Meet Archon Kleio, Synedrion, Astreion");
        road.add(box(rec.knows.contains("oath")) + "The Oath: Lysandra, Stoa of Shields, Astreion");
        road.add(box(s.bossesFallen.containsKey("kallias")) + "Break Kallias at the Pylon of Teeth (hold the Oath near him)");
        road.add(box(rec.knows.contains("hymn")) + "The Hymn: Iaso, Asklepieion, Hieranthe");
        road.add(box(s.bossesFallen.containsKey("melaina")) + "Break Melaina: sing to her 3 Fonts (" + count(s, "font:") + "/3)");
        road.add(box(rec.knows.contains("counterpoint")) + "The Counterpoint: Perdix, Mechaneion, Lampsa");
        road.add(box(s.bossesFallen.containsKey("daidaros")) + "Break Daidaros: still the 3 Governors in order (" + count(s, "governor:") + "/3)");
        road.add(box(rec.knows.contains("charter")) + "The Charter: Hesper, Archive, Mnemeia");
        road.add(box(s.bossesFallen.containsKey("keleos")) + "Break Keleos: read the Charter to 3 Edict Stones (" + count(s, "edict:") + "/3)");
        road.add(box(rec.knows.contains("light") || s.victory) + "The Light of Theano: Kleio, when all 4 Wards are dark (" + s.wardsDark() + "/4)");
        road.add(box(s.victory) + "Carry the Light to the Cinder Heart; end the Pyrarch");
        sections.add(road);

        List<String> where = new ArrayList<>();
        where.add(H + "WHERE (x, z)");
        for (Realm.Place pl : new Realm.Place[] {Realm.Place.GATE_OF_STRANGERS, Realm.Place.ASTREION, Realm.Place.LAMPSA, Realm.Place.HIERANTHE, Realm.Place.MNEMEIA,
                Realm.Place.LAST_WATCH, Realm.Place.PYLON, Realm.Place.STILLED_GARDEN, Realm.Place.GREAT_ENGINE, Realm.Place.PELLENE, Realm.Place.ANTHRAKION})
            where.add(T + short_(pl) + DIM + " " + pl.x + ", " + pl.z);
        sections.add(where);

        List<String> caps = new ArrayList<>();
        caps.add(H + "THE NAMED CAPTIVES");
        caps.add(T + "" + s.captivesRescued.size() + " of " + Lore.CAPTIVES.size() + " home");
        for (Lore.Captive c : Lore.CAPTIVES.values()) caps.add(box(s.captivesRescued.containsKey(c.id)) + c.name + DIM + " (" + c.province.title.replace("the ", "") + ")");
        caps.add(T + "Camps freed: " + s.campsFreed.size());
        sections.add(caps);

        List<String> lesser = new ArrayList<>();
        lesser.add(H + "THE DOMINION'S CAPTAINS");
        for (String[] b : Bosses.LESSER) lesser.add(box(s.bossesFallen.containsKey(b[0])) + b[1] + DIM + " (" + b[2] + ")");
        sections.add(lesser);

        List<String> learned = new ArrayList<>();
        learned.add(H + "WHAT YOU HAVE LEARNED");
        if (rec.knows.contains("withdrawal")) learned.add(T + "The Synedrion voted 7 to 5 in YL 1127 to abandon the eastern cities.");
        if (rec.knows.contains("kleio_admitted")) learned.add(T + "Kleio admits it, and promises to read the vote aloud.");
        if (rec.knows.contains("guild")) learned.add(T + "Lampsa's Guild of the Anvil bought slave-dug ash-iron for sixty years.");
        if (rec.knows.contains("lyceum_weapons")) learned.add(T + "The Lyceum designed the harmonic pikes the Ashborn carry.");
        if (rec.knows.contains("uzgar")) learned.add(T + "Uzgar: the Veil is Kallias's own; it falls with him.");
        if (rec.knows.contains("vesk_governors")) learned.add(T + "Vesk claims the Engine's Governors break highest first." + DIM + " (Unverified.)");
        if (rec.claimed.contains("vesk_3")) learned.add(T + "Vesk claims a gap in the Teeth by the old aqueduct." + DIM + " (Unverified.)");
        if (learned.size() == 1) learned.add(DIM + "Nothing yet. Talk to people. Read.");
        sections.add(learned);

        List<String> helio = new ArrayList<>();
        helio.add(H + "HELIODROMES");
        helio.add(DIM + "Touch a ring's light to travel to one you have found.");
        for (Registry.Spot h : plugin.registry().all("heliodrome:")) {
            String id = h.kind.substring(11);
            helio.add(box(rec.heliodromes.contains(id)) + (h.extra == null ? id : h.extra));
        }
        sections.add(helio);

        List<BaseComponent[]> out = new ArrayList<>();
        for (List<String> sec : sections) out.addAll(Talk.paginate(sec));
        return out;
    }

    private static int count(State s, String prefix) { int n = 0; for (String m : s.mechanisms) if (m.startsWith(prefix)) n++; return n; }

    private static String short_(Realm.Place p) {
        String t = p.title;
        int comma = t.indexOf(',');
        if (comma > 0) t = t.substring(0, comma);
        return t.replace("the ", "").replace("The ", "");
    }
}
