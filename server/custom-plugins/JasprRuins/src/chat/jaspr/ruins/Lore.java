package chat.jaspr.ruins;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BookMeta;

/**
 * The lore of the drowned city Drownhollow: the Choir of the Drowned Star, who raised it to wake Ythaqqua, the Dreamer in
 * the Deep; their five Wardens and the Seals; the Great Door and the Herald behind it. Lore books are found in cult
 * chests and on slain horrors; the Pilgrim's Primer (the step-by-step guide) is handed to everyone who arrives.
 */
final class Lore {
    private Lore() {}

    static final String CITY = "Drownhollow", CULT = "the Choir of the Drowned Star", GOD = "Ythaqqua";

    /** Carved chants for signs (four lines of at most 15 characters). */
    static final String[] CHANTS = {
        "IA! IA!\nYTHAQQUA\nDREAMS BELOW\nTHE DOOR",
        "THE STAR\nIS DROWNED\nTHE STAR\nIS WAITING",
        "DO NOT COUNT\nTHE ANGLES.\nTHEY COUNT\nYOU.",
        "FIVE SEALS\nFIVE WARDENS\nONE DOOR\nONE DREAMER",
        "HE SLEEPS\nNOT DEAD\nHE DREAMS\nOF YOU",
        "THE CHOIR\nSINGS UNDER\nTHE WATER\nFOREVER",
        "WHEN THE\nDOOR OPENS\nTHE HERALD\nWALKS",
        "LEAVE NO\nLIGHT BURN\nLET HIM\nSEE",
        "DROWNHOLLOW\nRISES WHEN\nTHE STARS\nARE RIGHT",
        "WE GAVE OUR\nFACES TO\nTHE DARK.\nIT KEPT THEM.",
    };

    static String chant(long h) { return CHANTS[(int) ((h >>> 7) % CHANTS.length)]; }

    // ------------------------------------------------------------------ lore books

    private static final String[][] BOOKS = {
        {"Survey of the Pillars", "Surveyor Maren Holt",
            "Day one. The mossy gate opened on a city of stone that should not stand. The pillars are too tall for their width, and they lean, though every plumb line I drop swears they are true.",
            "I measured one pillar four times and got four heights. The angles of the plazas add up wrong; my compass spins near the cult altars. I have stopped drawing maps. The maps were drawing me.",
            "Things walk here at night, and it is always night. Tall shapes with wet green scales that the others call Deep Ones. A slick black mass that flowed through a doorway like tar. I keep my torches lit."},
        {"Hymn of the Drowned Star", "The Choir",
            "Ia! Ia! Ythaqqua sleeps\nbeneath the Door of green\nwhere star-stone weeps.\n\nWe raised the city\nwhere the star fell burning,\nwe raised the pillars\nfor his returning.",
            "Five we chose to keep the Seals,\nfive we gave the dark.\nWhen three return unto the Door\nthe Herald leaves his mark.\n\nSing, and do not count the angles."},
        {"The Five Wardens", "Brother Aldous, scribe",
            "The high priests did not die. They were given to the Door as its wardens, each holding a Seal:\n\nThe Hierophant keeps the Seal of Tides in the Sanctum of the Drowned Star.\n\nThe Pillar Warden keeps the Seal of Stone in the Circle of the Watchers.",
            "The Brood Mother keeps the Seal of Hunger in the Pit of Offerings.\n\nThe Spawn of the Deep keeps the Seal of the Deep in the Spawning Pool.\n\nThe Faceless Priest keeps the Seal of Silence in the Chapel of the Faceless.",
            "Any three Seals, set into the Great Door, will open it. Behind it the Herald of Ythaqqua stands guard over the Dreamer. May no one ever gather three."},
        {"On the Geometry of Drownhollow", "Unsigned",
            "The builders were not men, or were no longer men when they built. Their stones are cut to a geometry of other spheres. A corner that looks convex is concave. A stair that rises may descend.",
            "Do not rest your eyes on the leaning pillars for long. The mind tries to make them straight, and in the trying something else comes in. That is the Dread. Light keeps it out. Light, and the Choir's wardstones."},
        {"Last Testament of Brother Aldous", "Brother Aldous",
            "I brought the offerings to the Pit for thirty years. I watched the Brood Mother grow fat on them. I watched the Faceless Priest take the faces of my brothers, one by one, until the chapel was full of blank men singing.",
            "Now the Hierophant says the stars are nearly right. I have hidden this testament in the stones. If you read it: do not open the Door. And if you must open it, open it to kill what waits inside."},
        {"Tablet of the Deep Ones", "Copied from a wall",
            "They came up from the drowned places when the star fell. They taught the Choir to breathe the water and to sing beneath it. In return the Choir fed them.\n\nThe Deep Ones do not die of age. They do not forget a face. They swim faster than any man can run."},
        {"A Letter Never Sent", "Ines, to her sister",
            "Dear Tova, I followed the lights into the mossy arch and now I cannot find the arch again. There are dogs here that come out of corners - the sharp corners, where two walls meet. They are not dogs. They smell of dust from before the world.",
            "If you come after me, bring torches and bring a friend. And if you meet something with no face, do not look where its face should be."},
        {"The Mi-Go Harvest", "Surveyor Maren Holt",
            "The pale winged things with fungal heads are not of the Choir. They came from beyond the stars to harvest the city, and they carry brains in shining cylinders. One of those cylinders, taken from a dead Mi-Go, lets a man step through space. I have used it. I will not use it again."},
        {"What Waits Behind the Door", "The Hierophant",
            "The Herald is the size of a tower. It is the first thing the Dreamer made when he dreamt. It walks the hall behind the Door and it will walk out of it when the stars are right.\n\nIts gaze blinds. Its stamp breaks bones. The floor sprouts mouths where it points.",
            "When it is wounded it calls the Deep. When it is near death the sky falls on its killers. The Choir wrote no instruction for killing it, because none of us believed it could be killed."},
        {"After the Waking", "The last of the Choir",
            "It is done. The Herald is fallen and the Dreamer sleeps deeper than before. Drownhollow will wait another thousand years for its stars.\n\nWear the Crown. It was made for the one who would end the Dream. The city will remember your face - it remembers every face - but it will fear yours."},
    };

    static int bookCount() { return BOOKS.length; }

    static final int AFTER_THE_WAKING = BOOKS.length - 1, WARDENS_BOOK = 2, HERALD_BOOK = 8;

    static ItemStack book(int id) {
        String[] b = BOOKS[Math.floorMod(id, BOOKS.length)];
        return written(b[0], b[1], Arrays.copyOfRange(b, 2, b.length));
    }

    static ItemStack written(String title, String author, String... pages) {
        ItemStack item = new ItemStack(Material.WRITTEN_BOOK);
        BookMeta meta = (BookMeta) item.getItemMeta();
        meta.setTitle(title);
        meta.setAuthor(author);
        meta.setPages(Arrays.asList(pages));
        item.setItemMeta(meta);
        return item;
    }

    static final String GUIDE_TITLE = "Pilgrim's Primer";

    /** The step-by-step guide every arriving player receives. */
    static ItemStack guide(int doorX, int doorZ) {
        List<String> p = new ArrayList<>();
        p.add(ChatColor.DARK_GREEN + "" + ChatColor.BOLD + "PILGRIM'S PRIMER\n" + ChatColor.RESET
            + "How to end the Dream of " + CITY + "\n\nYou stand in the drowned city of " + CULT + ". Night never ends here.\n\n"
            + "Five steps beat this dimension. Read on.");
        p.add(ChatColor.BOLD + "STEP 1: SURVIVE\n" + ChatColor.RESET + "Horrors spawn everywhere in the dark.\n\n"
            + "Carry torches: standing in darkness fills your " + ChatColor.DARK_PURPLE + "Dread" + ChatColor.RESET
            + ", which brings nausea, weakness and blindness. Light clears it.\n\nBeds do not work here.");
        p.add(ChatColor.BOLD + "STEP 2: FOLLOW THE COMPASS\n" + ChatColor.RESET + "You were given a "
            + ChatColor.DARK_AQUA + "Drowned Star Compass" + ChatColor.RESET + ". Hold it: it points to the nearest Warden whose Seal you do not "
            + "carry. With three Seals it points to the Great Door.");
        p.add(ChatColor.BOLD + "STEP 3: SLAY THREE WARDENS\n" + ChatColor.RESET + "Each drops its Seal:\n\n"
            + "Hierophant - Sanctum of the Drowned Star\nPillar Warden - Circle of the Watchers\nBrood Mother - Pit of Offerings\n"
            + "Spawn of the Deep - Spawning Pool\nFaceless Priest - Chapel of the Faceless");
        p.add(ChatColor.BOLD + "STEP 4: OPEN THE DOOR\n" + ChatColor.RESET + "The Great Door stands at\n\n"
            + ChatColor.DARK_RED + "   X " + doorX + "   Z " + doorZ + ChatColor.RESET
            + "\n\nRight-click the Door (the obsidian) with each Seal. Three " + ChatColor.BOLD + "different" + ChatColor.RESET + " Seals open it.");
        p.add(ChatColor.BOLD + "STEP 5: SLAY THE HERALD\n" + ChatColor.RESET + "The Dreamer's Herald, a giant, waits behind the Door. "
            + "It blinds, stomps, raises mouths from the floor, calls the Deep, and brings down the sky when nearly dead.\n\n"
            + "Kill it to end the Dream. Fight it together.");
        p.add(ChatColor.BOLD + "THE REWARD\n" + ChatColor.RESET + "The Herald leaves the Crown of the Drowned Star, the Herald's "
            + "Cleaver, the Wings of the Nightgaunt, the Idol of the Dreamer, the Faceless Mask, the Dreamer's Heart and more.\n\n"
            + "Wardens drop a Seal and a trinket each.");
        p.add(ChatColor.BOLD + "HORRORS\n" + ChatColor.RESET + "Deep Ones - fast in water\nGhouls - they starve you\nShoggoths - they split\n"
            + "Nightgaunts - they fly through walls\nMi-Go - they blink\nHounds of Tindalos - out of corners\nStar-Spawn Thralls - wither\n"
            + "Cult Zealots and Adepts\nTomb Crawlers - poison");
        p.add(ChatColor.BOLD + "TRINKETS\n" + ChatColor.RESET + "Found only here, on horrors, Wardens and in cult chests. "
            + "Keep them in your inventory or off hand: wardstones, pearls, charms, shards, pinions, teeth, cylinders.\n\n"
            + "Lore books tell the city's story.");
        p.add(ChatColor.BOLD + "GOING HOME\n" + ChatColor.RESET + "Stand in any mossy cobblestone portal for three seconds. "
            + "The one you arrived through is right beside you.\n\nLost this book? Leave and come back: the city gives it again.");
        return written(GUIDE_TITLE, "The last of the Choir", p.toArray(new String[0]));
    }
}
