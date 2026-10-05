package chat.jaspr.nether;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerPortalEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.map.MapCursor;
import org.bukkit.map.MapPalette;

/**
 * How to beat the Nether (owner, 2026-09-29: every realm beatable, obviously so): slay the Ghast Queen.
 * <ol>
 *   <li>Conquer three different Nether Lords (owner, 2026-09-29: "you have to conquer some of these builds to beat the
 *       Nether especially in regard to the new bosses"): each rules one of the GLM strongholds, the compass points to
 *       the nearest one not yet conquered; the Urn of Sorrow answers only after the third.</li>
 *   <li>Reach the Spore Cathedral (the Fungi Forest's mega structure; the nearest one is chosen).</li>
 *   <li>Take a Potion of Sorrow from the Font of Sorrow in its crypt (or carry one already: brewed or looted).</li>
 *   <li>Pour it into the Urn of Sorrow on the Cathedral's crown.</li>
 *   <li>Slay the Ghast Queen: everyone who hurt her, or stood within 64 blocks when she fell, has beaten the Nether.</li>
 * </ol>
 * The Nether Guide stands beside every portal a player arrives through and hands out the compass, the checklist and
 * the map (see {@link GuideKit}). The vanilla compass needle spins in the Nether, so the compass also shows the way
 * above the hotbar and draws a trail of flame.
 */
final class NetherQuest implements GuideKit.Realm, Listener {
    private final NetherPlugin plugin;
    private final java.util.Set<java.util.UUID> portalling = new java.util.HashSet<>();
    private final java.util.Map<java.util.UUID, Long> fontUsed = new java.util.HashMap<>();
    int fonts;

    NetherQuest(NetherPlugin plugin) {
        this.plugin = plugin;
        org.bukkit.Bukkit.getScheduler().runTaskTimer(plugin, this::touchFonts, 40L, 20L);
    }

    @Override public String key() { return "nether"; }
    @Override public String title() { return "The Nether"; }
    @Override public String goal() { return "Slay the Ghast Queen"; }
    @Override public String guideName() { return "Nether Guide"; }
    @Override public String gate() { return "an obsidian portal"; }
    @Override public ChatColor colour() { return ChatColor.RED; }
    @Override public Villager.Profession profession() { return Villager.Profession.BLACKSMITH; }
    @Override public int order() { return 2; }
    @Override public boolean inRealm(World w) { return plugin.isNether(w); }
    @Override public boolean needle() { return false; }
    @Override public Particle trail() { return Particle.FLAME; }

    /** The nearest Spore Cathedral that stands (or will stand, in land not yet generated). */
    Mega.Site cathedral(Player p) {
        if (plugin.gen == null) return null;
        boolean here = plugin.isNether(p.getWorld());
        int x = here ? p.getLocation().getBlockX() : 0, z = here ? p.getLocation().getBlockZ() : 0;
        Mega.Site best = null;
        double bd = Double.MAX_VALUE;
        int cx = Math.floorDiv(x, Mega.CELL), cz = Math.floorDiv(z, Mega.CELL);
        for (int dx = -8; dx <= 8; dx++) for (int dz = -8; dz <= 8; dz++) {
            Mega.Site s = plugin.gen.mega.site(cx + dx, cz + dz);
            if (s == null || s.kind != Mega.Kind.CATHEDRAL || Boolean.FALSE.equals(plugin.registry.megaDecision(s.cellX, s.cellZ))) continue;
            double d = (double) (s.x - x) * (s.x - x) + (double) (s.z - z) * (s.z - z);
            if (d < bd) { bd = d; best = s; }
        }
        return best;
    }

    private Location at(int[] c) { return plugin.nether == null ? null : new Location(plugin.nether, c[0] + 0.5, c[1], c[2] + 0.5); }

    /** Lord strongholds that stand (or will, in land not yet generated) within {@code cells} lord cells. */
    List<GlmSites.Site> lordSites(int x, int z, int cells) {
        List<GlmSites.Site> out = new ArrayList<>();
        if (plugin.gen == null) return out;
        for (GlmSites g : new GlmSites[]{plugin.gen.glm, plugin.gen.legacyGlm}) {
        int cx = Math.floorDiv(x, g.cell(GlmSites.Tier.LORD)), cz = Math.floorDiv(z, g.cell(GlmSites.Tier.LORD));
        for (int dx = -cells; dx <= cells; dx++) for (int dz = -cells; dz <= cells; dz++) {
            GlmSites.Site s = g.site(GlmSites.Tier.LORD, cx + dx, cz + dz);
            if (s != null && s.e.lord != null && (g.legacy
                ? Boolean.TRUE.equals(plugin.gen.legacyRegistry.glmDecision(s.decisionTier(), s.cellX, s.cellZ))
                : !Boolean.FALSE.equals(plugin.registry.glmDecision(s.decisionTier(), s.cellX, s.cellZ)))) out.add(s);
        }
        }
        return out;
    }

    /** The nearest stronghold whose Lord the player has not conquered. */
    GlmSites.Site nextLord(Player p) {
        boolean here = plugin.isNether(p.getWorld());
        int x = here ? p.getLocation().getBlockX() : 0, z = here ? p.getLocation().getBlockZ() : 0;
        java.util.Set<String> done = Lords.conquered(p);
        GlmSites.Site best = null;
        double bd = Double.MAX_VALUE;
        for (GlmSites.Site s : lordSites(x, z, 4)) {
            if (done.contains(s.e.lord)) continue;
            double d = s.dist(x, z);
            if (d < bd) { bd = d; best = s; }
        }
        return best;
    }

    /** Where a Lord rises: its arena (known from the build, generated or not), else the stronghold's middle. */
    Location lordTarget(GlmSites.Site s) {
        if (plugin.nether == null) return null;
        int[] a = plugin.gen.glm.arena(s);
        if (a != null) return new Location(plugin.nether, a[0] + 0.5, a[1], a[2] + 0.5);
        return new Location(plugin.nether, s.x + 0.5, s.floor + 1, s.z + 0.5);
    }

    static String regionName(Biomes.Nex n) { return n == null ? "Nether" : n.display; }

    static boolean hasSorrow(Player p) {
        for (ItemStack s : p.getInventory().getContents()) if (Items.is(s, "potion_sorrow")) return true;
        return false;
    }

    @Override public List<GuideKit.Task> tasks(Player p) {
        boolean won = plugin.guide != null && plugin.guide.beaten(p);
        boolean here = plugin.isNether(p.getWorld());
        Mega.Site c = cathedral(p);
        Location centre = c == null ? null : at(new int[]{c.x, c.y + 1, c.z});
        Location font = c == null ? null : at(MegaCathedral.font(c)), urn = c == null ? null : at(MegaCathedral.urn(c));
        Location crypt = c == null ? null : at(MegaCathedral.cryptDoor(c)), door = c == null ? null : at(MegaCathedral.stemDoor(c));
        if (here && c != null && Math.hypot(p.getLocation().getX() - c.x, p.getLocation().getZ() - c.z) < 64) p.addScoreboardTag("jn_q_cathedral");
        LivingEntity queen = here ? plugin.boss.queenNear(p.getLocation(), 160) : null;
        boolean summoning = here && plugin.boss.summoningNear(p.getLocation(), 160);
        boolean fighting = queen != null || summoning;
        boolean potion = won || fighting || hasSorrow(p);
        boolean reached = won || potion || p.getScoreboardTags().contains("jn_q_cathedral");
        String side = c == null ? "" : " (" + GuideKit.side(c.x, c.z, MegaCathedral.cryptDoor(c)) + " side)";
        // the font sits in the crypt: from above ground, point at the crypt stair first
        Location fontTarget = font;
        if (here && font != null && crypt != null && p.getLocation().getY() > c.y - 3
            && p.getLocation().distanceSquared(crypt) > 16) fontTarget = crypt;
        // the urn is on the crown: from the ground outside the stem, point at the stem's door first
        Location urnTarget = urn;
        if (here && urn != null && door != null && p.getLocation().getY() < c.y + 6
            && Math.hypot(p.getLocation().getX() - c.x, p.getLocation().getZ() - c.z) > MegaCathedral.STEM) urnTarget = door;
        List<GuideKit.Task> t = new ArrayList<>();
        java.util.Set<String> lords = Lords.conquered(p);
        GlmSites.Site next = won || lords.size() >= Lords.NEEDED ? null : nextLord(p);
        String[] ordinal = {"a", "a second", "a third"};
        for (int i = 0; i < Lords.NEEDED; i++) {
            boolean done = won || lords.size() > i;
            boolean current = !done && lords.size() == i;
            Lords.Def d = current && next != null ? Lords.DEFS.get(next.e.lord) : null;
            String hint = d == null ? "Ten Nether Lords rule great strongholds across the Nether, and three more keep the Great Pyramid, the Caldera Citadel and the"
                + " Endless Catacombs. Everyone who hurts a Lord, or stands near when it falls, conquers it."
                : d.name + " rules " + next.e.title + " in the " + regionName(next.region) + ". Follow the compass or the red mark on the map. Go in"
                + " armed and armoured: the Lord rises when you come near its hall. Everyone who hurts it, or stands near when it falls, conquers it.";
            t.add(new GuideKit.Task("Conquer " + ordinal[i] + " Nether Lord" + (current ? " (" + lords.size() + "/" + Lords.NEEDED + ")" : ""), hint,
                done, current && next != null ? lordTarget(next) : null, d == null ? "a Nether Lord's stronghold" : d.name + " at " + next.e.title));
        }
        t.add(new GuideKit.Task("Reach the Spore Cathedral",
            "It stands in a Fungi Forest: a red-capped mushroom as tall as a mountain. Follow the compass or the red mark on the map.",
            reached, centre, "the Spore Cathedral"));
        t.add(new GuideKit.Task("Take a Potion of Sorrow",
            "Go down the stair beside the stem" + side + " into the crypt and walk up to the Font of Sorrow (a cauldron on a pillar): it fills a bottle for you. (Or brew one: an awkward potion and raw ghast meat.)",
            potion, fontTarget, fontTarget == crypt ? "the crypt stair" : "the Font of Sorrow in the crypt"));
        t.add(new GuideKit.Task("Pour it into the Urn of Sorrow",
            "Enter the stem, climb the spiral stair to the top of the cap and right-click the Urn of Sorrow on the Weeping Balcony with the potion."
                + " The urn answers only one who has conquered three Nether Lords.",
            won || fighting, urnTarget, urnTarget == door ? "the door into the stem" : "the Urn of Sorrow on the crown"));
        t.add(new GuideKit.Task("Slay the Ghast Queen",
            "She rises above the urn. Punch her fireballs back at her or shoot her with a bow; bring armour and fire resistance.",
            won, queen != null ? queen.getLocation() : urn, queen != null ? "the Ghast Queen" : "the Urn of Sorrow"));
        return t;
    }

    @Override public List<String> tips() {
        return Arrays.asList(
            ChatColor.BOLD + "NETHER LORDS" + ChatColor.RESET + "\n\nDeathwing, Ignareth, the Pit Lord, the Ashen Wither, the Cursed King, the Dread Sorcerer,"
                + " the Voidborn, the Bone Colossus, the Crimson Tyrant and the Blood Count. Each holds a hoard and a relic; conquer any three.",
            ChatColor.BOLD + "COLOSSI" + ChatColor.RESET + "\n\nThe Great Pyramid, the Caldera Citadel and the Endless Catacombs: champions keep"
                + " the keys to their seals, puzzles and traps guard the way, and a Lord waits at the heart of each.",
            ChatColor.BOLD + "TEN MORE COLOSSI" + ChatColor.RESET + "\n\nMaw of the Abyss, Ashen Spire, Leviathan's Bones, Infernal Colosseum,"
                + " Hanging Citadel, Sporefather's Hive, Rime Bastion, Burning Palace, Amethyst Sanctum, World Serpent.\n\nA Lord in each; its fall opens"
                + " the vault.",
            ChatColor.BOLD + "STRONGHOLDS" + ChatColor.RESET + "\n\nCastles, temples and crypts stand in their own caverns, full of loot, spawners and"
                + " guards. Break a spawner to stop it. Trapped chests spring ambushes. Forge Hellforged and Soulweave gear from what the guards drop.",
            ChatColor.BOLD + "TIPS" + ChatColor.RESET + "\n\nThe Ghast Queen hits hard. Get ready first in the Nether's other great places:\n\n"
                + ChatColor.DARK_RED + "Soul Pyramid" + ChatColor.BLACK + ": Wither Bone armour.\n" + ChatColor.DARK_RED + "Cinder Forge" + ChatColor.BLACK
                + ": Salamander Hide armour (fire and lava immunity).",
            ChatColor.BOLD + "MORE" + ChatColor.RESET + "\n\n" + ChatColor.DARK_RED + "Golden Bazaar" + ChatColor.BLACK
                + ": Pigtificate traders, a Respawner Statue (glowstone dust), no monsters.\n\n" + ChatColor.DARK_RED + "Frozen Citadel" + ChatColor.BLACK
                + ": the richest vault.\n\nLost an item? The Nether Guide at any portal gives new ones.");
    }

    // ---- the map: the Nether's regions, its five great places, the next task ------------------------------------------
    private static final byte[] REGION = {MapPalette.matchColor(118, 32, 24), MapPalette.matchColor(96, 74, 52), MapPalette.matchColor(168, 84, 28),
        MapPalette.matchColor(118, 62, 124), MapPalette.matchColor(126, 162, 196)};
    private static final byte CLEARING = MapPalette.matchColor(60, 40, 36);

    @Override public int mapScale() { return 8; }

    @Override public int[] mapCentre(Player p) {
        int span = 128 * mapScale() / 4;   // re-centre whenever the player leaves the middle half
        Location l = p.getLocation();
        return new int[]{Math.floorDiv(l.getBlockX() + span / 2, span) * span, Math.floorDiv(l.getBlockZ() + span / 2, span) * span};
    }

    @Override public byte paint(int x, int z) {
        if (plugin.gen == null) return CLEARING;
        Mega.Site s = plugin.gen.mega.at(x, z);
        if (s != null && s.f(x, z) < 1) return CLEARING;
        return REGION[plugin.gen.biomes.nex(x, z).ordinal()];
    }

    private List<Mega.Site> sitesNear(Player p) {
        List<Mega.Site> out = new ArrayList<>();
        if (plugin.gen == null) return out;
        Location l = p.getLocation();
        int cx = Math.floorDiv(l.getBlockX(), Mega.CELL), cz = Math.floorDiv(l.getBlockZ(), Mega.CELL);
        for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++) {
            Mega.Site s = plugin.gen.mega.site(cx + dx, cz + dz);
            if (s != null && !Boolean.FALSE.equals(plugin.registry.megaDecision(s.cellX, s.cellZ))) out.add(s);
        }
        return out;
    }

    @Override public List<GuideKit.Marker> markers(Player p) {
        List<GuideKit.Marker> out = new ArrayList<>();
        for (Mega.Site s : sitesNear(p))
            out.add(new GuideKit.Marker(s.x, s.z, s.kind == Mega.Kind.CATHEDRAL ? MapCursor.Type.TEMPLE : MapCursor.Type.MANSION));
        java.util.Set<String> done = Lords.conquered(p);
        Location l = p.getLocation();
        for (GlmSites.Site s : lordSites(l.getBlockX(), l.getBlockZ(), 1))
            out.add(new GuideKit.Marker(s.x, s.z, done.contains(s.e.lord) ? MapCursor.Type.WHITE_CROSS : MapCursor.Type.RED_MARKER));
        for (Colossi.Site s : colossiNear(p))
            out.add(new GuideKit.Marker(s.x, s.z, done.contains(s.kind.lord) ? MapCursor.Type.WHITE_CROSS : MapCursor.Type.TEMPLE));
        return out;
    }

    /** The colossal structures standing within a cell of a player (for the map: each crossed once its Lord is conquered). */
    private List<Colossi.Site> colossiNear(Player p) {
        List<Colossi.Site> out = new ArrayList<>();
        if (plugin.gen == null || plugin.registry == null) return out;
        Location l = p.getLocation();
        for (Colossi.Site s : plugin.gen.colossi.near(null, l.getBlockX(), l.getBlockZ(), 1))
            if (!Boolean.FALSE.equals(plugin.registry.colossusDecision(s.cellX, s.cellZ))) out.add(s);
        return out;
    }

    @Override public List<GuideKit.Label> labels(Player p) {
        List<GuideKit.Label> out = new ArrayList<>();
        for (Mega.Site s : sitesNear(p)) out.add(new GuideKit.Label(s.x, s.z, s.kind.display.replace("The ", "")));
        java.util.Set<String> done = Lords.conquered(p);
        Location l = p.getLocation();
        for (GlmSites.Site s : lordSites(l.getBlockX(), l.getBlockZ(), 1))
            if (!done.contains(s.e.lord)) out.add(new GuideKit.Label(s.x, s.z, Lords.DEFS.get(s.e.lord).name.replace("The ", "")));
        for (Colossi.Site s : colossiNear(p)) out.add(new GuideKit.Label(s.x, s.z, s.kind.display.replace("The ", "")));
        return out;
    }

    // ---- the Font of Sorrow ------------------------------------------------------------------------------------------
    // The font fills a bottle when a player clicks it (either button) or simply steps up to it: the browser client does
    // not send an empty-handed right-click on a plain block, so a click alone would leave empty-handed players stuck.
    @EventHandler(priority = EventPriority.HIGH)
    public void font(PlayerInteractEvent e) {
        if ((e.getAction() != Action.RIGHT_CLICK_BLOCK && e.getAction() != Action.LEFT_CLICK_BLOCK) || e.getClickedBlock() == null) return;
        Block b = e.getClickedBlock();
        if (e.getHand() == org.bukkit.inventory.EquipmentSlot.OFF_HAND) {   // the client repeats a refused click with the off hand
            if (b.getType() == Material.CAULDRON && plugin.registry != null && plugin.registry.at(b.getX(), b.getY(), b.getZ(), "font") != null) e.setCancelled(true);
            return;
        }
        if (b.getType() != Material.CAULDRON || plugin.registry == null || !plugin.isNether(b.getWorld())) return;
        if (plugin.registry.at(b.getX(), b.getY(), b.getZ(), "font") == null) return;
        e.setCancelled(true);   // no filling or emptying, and a punch never starts breaking it
        offerSorrow(e.getPlayer(), b, e.getAction() == Action.RIGHT_CLICK_BLOCK ? "right-click" : "left-click");
    }

    /** Every second: a player standing at a font (within two blocks) is handed a potion if they have none. */
    void touchFonts() {
        World w = plugin.nether;
        if (w == null || plugin.registry == null) return;
        for (Player p : w.getPlayers()) {
            if (p.isDead() || p.getGameMode() == org.bukkit.GameMode.SPECTATOR) continue;
            Location l = p.getLocation();
            for (Registry.Entry f : plugin.registry.near(l.getBlockX(), l.getBlockZ(), 3, "font")) {
                double dx = f.x1 + 0.5 - l.getX(), dz = f.z1 + 0.5 - l.getZ(), dy = f.y1 - l.getY();
                if (dx * dx + dz * dz > 2.2 * 2.2 || dy < -1 || dy > 2.5) continue;
                Block b = w.getBlockAt(f.x1, f.y1, f.z1);
                if (b.getType() == Material.CAULDRON) offerSorrow(p, b, "touch");
                break;
            }
        }
    }

    private void offerSorrow(Player p, Block b, String how) {
        boolean clicked = !how.equals("touch");
        if (hasSorrow(p)) {
            if (clicked) Effects.bar(p, ChatColor.DARK_PURPLE + "You already carry a Potion of Sorrow. Take it to the Urn on the Cathedral's crown.");
            return;
        }
        long now = System.currentTimeMillis();
        Long last = fontUsed.get(p.getUniqueId());
        if (last != null && now - last < 10_000) { if (clicked) Effects.bar(p, ChatColor.DARK_PURPLE + "The font is still filling..."); return; }
        fontUsed.put(p.getUniqueId(), now);
        if (fontUsed.size() > 512) fontUsed.clear();
        for (ItemStack left : p.getInventory().addItem(Items.create("potion_sorrow", 1)).values()) p.getWorld().dropItemNaturally(p.getLocation(), left);
        p.getWorld().spawnParticle(Particle.SPELL_WITCH, b.getLocation().add(0.5, 1, 0.5), 20, 0.3, 0.3, 0.3, 0.02);
        p.playSound(b.getLocation(), org.bukkit.Sound.ITEM_BOTTLE_FILL, 1f, 0.7f);
        p.sendMessage(ChatColor.DARK_PURPLE + "The Font of Sorrow weeps into a bottle: " + ChatColor.WHITE + "Potion of Sorrow" + ChatColor.DARK_PURPLE
            + ". Pour it into the Urn on the Cathedral's crown.");
        fonts++;
        plugin.getLogger().info("NETHER_FONT_GIVEN at=" + b.getX() + "," + b.getY() + "," + b.getZ() + " how=" + how);
    }

    /** The font (and the pillar under it) cannot be broken, except in creative. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void keepFont(org.bukkit.event.block.BlockBreakEvent e) {
        Block b = e.getBlock();
        if (plugin.registry == null || !plugin.isNether(b.getWorld()) || e.getPlayer().getGameMode() == org.bukkit.GameMode.CREATIVE) return;
        if (plugin.registry.at(b.getX(), b.getY(), b.getZ(), "font") != null || plugin.registry.at(b.getX(), b.getY() + 1, b.getZ(), "font") != null) {
            e.setCancelled(true);
            Effects.bar(e.getPlayer(), ChatColor.DARK_PURPLE + "The Font of Sorrow will not break.");
        }
    }

    /** Ghast fireballs may scar the Cathedral but never break an urn or a font (or the block it stands on). */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void keepAltars(EntityExplodeEvent e) {
        if (plugin.registry == null || !plugin.isNether(e.getLocation().getWorld())) return;
        Location l = e.getLocation();
        if (plugin.registry.near(l.getBlockX(), l.getBlockZ(), 12, "urn").isEmpty() && plugin.registry.near(l.getBlockX(), l.getBlockZ(), 12, "font").isEmpty()) return;
        e.blockList().removeIf(b -> altar(b.getX(), b.getY(), b.getZ()) || altar(b.getX(), b.getY() + 1, b.getZ()));
    }

    private boolean altar(int x, int y, int z) {
        return plugin.registry.at(x, y, z, "urn") != null || plugin.registry.at(x, y, z, "font") != null;
    }

    // ---- guides at every portal a player comes through ------------------------------------------------------------------
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void portal(PlayerPortalEvent e) {
        if (e.getCause() == PlayerTeleportEvent.TeleportCause.NETHER_PORTAL && e.getTo() != null && plugin.isNether(e.getTo().getWorld()))
            portalling.add(e.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void arrived(PlayerChangedWorldEvent e) {
        Player p = e.getPlayer();
        boolean byPortal = portalling.remove(p.getUniqueId());
        if (!plugin.isNether(p.getWorld()) || plugin.guide == null) return;
        org.bukkit.Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (p.isOnline() && plugin.isNether(p.getWorld())) plugin.guide.arrive(p, byPortal ? p.getLocation() : null);
        }, 20L);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void joined(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        if (plugin.isNether(p.getWorld()) && plugin.guide != null)
            org.bukkit.Bukkit.getScheduler().runTaskLater(plugin, () -> { if (p.isOnline() && plugin.isNether(p.getWorld())) plugin.guide.arrive(p, null); }, 60L);
    }
}
