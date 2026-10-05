package chat.jaspr.ruins;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

/**
 * One of the twenty great structures of epoch 6 (see {@link Plans.Great}, {@link Greats}). A design plans its layout once
 * per site (from the site's own Random) and draws the part of it that falls in the canvas' chunk. Every decision is a
 * pure function of the site, so a structure spanning many chunks comes out whole, and the populator (which draws again
 * without a block target, see {@link RuinsPopulator#tilesOf}) finds exactly the same chests, spawners and signs.
 * <p>
 * Never read the canvas: {@code Canvas.get} answers -1 when the populator recomputes tiles and {@code Canvas.ground} knows
 * only the chunk's own columns. Use {@link Plans.GreatSite#surface} for the land's height anywhere.
 */
abstract class GreatDesign {
    /** What the runtime needs from a layout: where the boss wakes and the garrison points (local u, y above base, v). */
    static class Layout {
        int[] boss;
        final List<Garrison> garrisons = new ArrayList<>();
    }

    /** The layout, made once per site; must set {@code boss} and add the garrisons. */
    abstract Layout plan(Plans.GreatSite s, Random r);

    /** Draws the part of the structure inside the canvas' chunk (after the terrain, the field and the ruins). */
    abstract void draw(Plans.GreatSite s, Layout plan, Canvas c);

    // ---- every kind of monster (owner, 2026-10-04: "they should include all types of mobs and custom mobs") -----------------
    /** The ten horrors of Drownhollow (see {@link Horrors.Kind}). */
    static final String[] HORRORS = {"deep_one", "ghoul", "cult_zealot", "tomb_crawler", "nightgaunt", "shoggoth", "mi_go", "star_spawn", "hound", "cult_adept"};
    /** Every hostile monster of the overworld. */
    static final String[] MONSTERS = {"zombie", "husk", "zombie_villager", "skeleton", "stray", "spider", "cave_spider", "creeper", "witch", "enderman",
        "slime", "silverfish", "endermite", "vindicator", "evoker", "illusioner", "wither_skeleton"};
    /** Each great structure holds all of these in its garrisons (tests/ruins.test.cjs checks every kind). */
    static final String[] ROSTER;
    static {
        ROSTER = Arrays.copyOf(HORRORS, HORRORS.length + MONSTERS.length);
        System.arraycopy(MONSTERS, 0, ROSTER, HORRORS.length, MONSTERS.length);
    }
    /** Water dwellers: only in flooded rooms of the drowned structures, never added by {@link #complete}. */
    static final Set<String> WATER = new LinkedHashSet<>(Arrays.asList("guardian"));

    /** A garrison point in the local frame (u, y above the base, v) with its pack ("kind+kind", '!' first for an elder leader). */
    static final class Garrison {
        final int u, y, v; String pack;
        Garrison(int u, int y, int v, String pack) { this.u = u; this.y = y; this.v = v; this.pack = pack; }
    }

    static Garrison g(int u, int y, int v, String pack) { return new Garrison(u, y, v, pack); }

    /** Completes the garrisons so that together they hold the whole {@link #ROSTER}: each missing kind joins the smallest pack (at most five a pack). */
    static void complete(List<Garrison> gs) {
        Set<String> have = kinds(gs);
        for (String k : ROSTER) {
            if (have.contains(k)) continue;
            Garrison best = null;
            for (Garrison x : gs) if (WATER.stream().noneMatch(x.pack::contains) && size(x.pack) < 5 && (best == null || size(x.pack) < size(best.pack))) best = x;
            if (best == null) break;
            best.pack = best.pack + "+" + k;
        }
    }

    static Set<String> kinds(List<Garrison> gs) {
        Set<String> out = new LinkedHashSet<>();
        for (Garrison x : gs) for (String k : (x.pack.startsWith("!") ? x.pack.substring(1) : x.pack).split("\\+")) out.add(k);
        return out;
    }

    private static int size(String pack) { return pack.split("\\+").length; }
}
