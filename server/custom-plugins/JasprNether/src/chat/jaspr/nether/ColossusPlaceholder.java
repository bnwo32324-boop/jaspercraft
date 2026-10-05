package chat.jaspr.nether;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static chat.jaspr.nether.Draw.b;

/**
 * A plain stand-in for a colossus of 2026-10-04 while its own design is being drawn up: a walled hall on a plinth with its
 * Lord's throne, side rooms, a vault behind the Lord's seal, every hostile creature in its garrisons and the great fliers
 * in the open cavern. It exercises the whole contract (plan, garrisons, the Lord's point, chests, spawners, signs, the
 * vault's seal) so the framework and the offline check can be tried before the real structure exists.
 */
abstract class ColossusPlaceholder extends ColossusDesign {
    static final class Plan {
        int rot;
        List<Garrison> garrisons;
        List<Ordeals.Ordeal> ordeals;
    }

    private static final int BRICK = b(112), RED = b(215), OBSIDIAN = b(49), GLOW = b(89), MAGMA = b(213), QUARTZ = b(155, 0);

    @Override Object plan(Colossi.Site s, Random r) {
        Plan p = new Plan();
        p.rot = r.nextInt(4);
        int y = s.y + 1;
        List<Garrison> gs = new ArrayList<>();
        String[] packs = {"!infernal_knight+wither_skeleton", "pigman_berserker+zombie_pigman", "mummy+tomb_guardian", "ember_legionnaire+flame_adept",
            "crypt_guard+skeleton", "charred_ghoul+cinder_witch", "hellhound+brute", "royal_guard+dread_rider", "salamander+magma_cube",
            "spinout+wight", "deep_crawler+coolmar_spider", "brimstone_spider+asp"};
        int k = 0;
        for (int u = -16; u <= 16; u += 8) for (int v = -16; v <= 16; v += 32) if (k < packs.length) gs.add(g(u, y, v, packs[k++]));
        for (int v = -8; v <= 8; v += 8) for (int u = -16; u <= 16; u += 32) if (k < packs.length) gs.add(g(u, y, v, packs[k++]));
        gs.add(g(-60, s.y + 30, 0, "ghast+ghastling"));
        gs.add(g(60, s.y + 30, 0, "ghastling+blaze"));
        p.garrisons = complete(gs);
        return p;
    }

    @Override void draw(Colossi.Site s, Draw d) {
        Plan p = (Plan) s.plan;
        Draw.Frame f = new Draw.Frame(d, s.x, s.z, p.rot);
        int y0 = s.y;
        String loot = "jaspr:colossus/" + s.kind.name().toLowerCase(java.util.Locale.ROOT);
        f.box(-24, y0 - 4, -24, 24, y0, 34, Draw.of(BRICK));                                    // the plinth
        f.room(-20, y0, -20, 20, y0 + 14, 20, Draw.of(BRICK));                                  // the hall
        f.box(-19, y0, -19, 19, y0, 19, Draw.mix(BRICK, RED, 0.3, s.salt));
        f.box(-3, y0 + 1, -20, 3, y0 + 6, -20, Draw.AIR);                                       // the door
        for (int u = -12; u <= 12; u += 8) for (int v = -12; v <= 12; v += 24) { f.box(u, y0 + 1, v, u, y0 + 13, v, Draw.of(QUARTZ)); f.set(u, y0 + 9, v, GLOW); }
        f.box(-3, y0 + 1, 8, 3, y0 + 1, 12, Draw.of(OBSIDIAN));                                 // the throne's dais
        f.point(0, y0 + 2, 10, "lord:" + s.kind.lord);
        for (int i = 0; i < 8; i++) f.chest(-18 + i * 5, y0 + 1, -18, Draw.SOUTH, loot);
        for (int i = 0; i < 5; i++) f.chest(-18, y0 + 1, -12 + i * 6, Draw.EAST, loot + "_rich");
        f.spawner(15, y0 + 1, -15, "infernal_knight");
        f.spawner(-15, y0 + 1, 15, "magma_hulk");
        f.wallSign(0, y0 + 5, -19, Draw.SOUTH, s.kind.display.length() > 15 ? s.kind.display.substring(4) : s.kind.display, "", "", "");
        f.wallSign(-2, y0 + 3, 19, Draw.NORTH, "The vault", "opens when", "its Lord falls", "");
        f.wallSign(19, y0 + 3, 0, Draw.WEST, "Every creature", "of the Nether", "keeps this hall", "");
        f.wallSign(-19, y0 + 3, 0, Draw.EAST, "Beware", "", "", "");
        // the vault behind the throne, sealed by red brick until the Lord falls
        f.room(-6, y0, 20, 6, y0 + 7, 30, Draw.of(BRICK));
        f.box(-1, y0 + 1, 20, 1, y0 + 3, 20, Draw.of(RED));
        for (int u = -4; u <= 4; u += 4) f.chest(u, y0 + 1, 29, Draw.NORTH, loot + "_vault");
        f.set(0, y0 + 5, 25, GLOW);
        f.set(0, y0, 25, MAGMA);
        garrisons(f, p.garrisons);
    }

    @Override List<Ordeals.Ordeal> ordeals(Colossi.Site s) {
        Plan p = (Plan) s.plan;
        if (p.ordeals != null) return p.ordeals;
        List<Ordeals.Ordeal> out = new ArrayList<>();
        Ordeals.Frame fr = new Ordeals.Frame(s.x, s.z, p.rot);
        out.add(Ordeals.bossSeal(s.kind.id + "_vault", fr.box(-1, s.y + 1, 20, 1, s.y + 3, 20), RED, s.kind.lord));
        p.ordeals = out;
        return out;
    }

    @Override int ground(Colossi.Site s, int x, int y, int z) { return Draw.rnd(x, 0, z, s.salt + 61) < 0.3 ? b(88) : b(87); }

    @Override Draw.Mat under() { return Draw.of(b(87)); }

    @Override boolean dry(Colossi.Site s, int x, int z) { return s.dist(x, z) < 60; }
}
