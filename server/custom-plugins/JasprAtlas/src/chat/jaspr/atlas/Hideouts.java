package chat.jaspr.atlas;

import static chat.jaspr.atlas.Canvas.*;

/**
 * Where the Dominion's talkers keep themselves: Uzgar the Ashborn deserter in a gutted Concord watch post in the Wound,
 * and Vesk the Gnawling informer in a burrow off the Ash Road North, among everything he has stolen.
 */
final class Hideouts {
    private Hideouts() {}

    /** A Concord watch post the Wound took: two broken walls, a lean-to, a bedroll, a cold hearth, a stash. */
    static void uzgar(Frame f) {
        Places.level(f, 9, 4);
        for (int x = -6; x <= 6; x++)
            for (int z = -6; z <= 6; z++) {
                if (Math.max(Math.abs(x), Math.abs(z)) != 5) continue;
                int top = x < 0 ? 5 - Math.abs(z) / 2 : z < 0 ? 3 : 1;   // the west wall stands; the rest is rubble
                if (f.roll(x, 0, z, 301) < 0.2) top = 1;
                for (int y = 1; y <= top; y++) if (f.roll(x, y, z, 302) < 0.85) f.set(x, y, z, y == 1 ? COBBLE : 98, f.roll(x, y, z, 303) < 0.4 ? 2 : 0);
            }
        for (int z = -1; z <= 1; z++) for (int y = 1; y <= 3; y++) f.set(5, y, z, AIR);   // the way in, east, facing the Dominion he left
        // A lean-to against the standing west wall: fence posts and a slab roof.
        for (int z = -3; z <= 3; z += 3) { f.set(-1, 1, z, SPRUCE_FENCE); f.set(-1, 2, z, SPRUCE_FENCE); }
        for (int x = -4; x <= -1; x++) for (int z = -4; z <= 4; z++) f.set(x, 3, z, WOOD_SLAB, 1);
        f.set(-4, 1, -2, CARPET, BROWN); f.set(-4, 1, -1, CARPET, BROWN); f.set(-3, 1, -2, CARPET, GRAY);   // the bedroll
        f.set(-3, 1, 2, COBBLE); f.set(-3, 1, 3, COBBLE); f.set(-2, 1, 3, COBBLE);                       // a cold hearth
        f.set(-2, 1, 2, FURNACE, f.facing(1, 0));
        f.chest(-4, 1, 3, 1, 0, "atlas:orc_stash", "book:orders");
        f.set(2, 1, -3, BONE, 0); f.set(3, 1, 2, SKULL, 1);
        f.set(1, 1, 3, CAULDRON, 1);
        f.sign(-4, 2, 0, 1, 0, "LEAVE BREAD.\nTAKE NOTHING.\nI DO NOT\nFIGHT NOW. -U");
        f.npc(-2, 1, 0, 1, 0, "talker:uzgar", null);
    }

    /** A Gnawling's burrow: a pit in the ash, a lidded tunnel mouth, tally sticks, bones and a hoard of stolen things. */
    static void vesk(Frame f, boolean freed) {
        Places.level(f, 9, 1);
        for (int x = -4; x <= 4; x++)
            for (int z = -4; z <= 4; z++) {
                double d = Math.sqrt(x * x + z * z);
                if (d > 4.4) continue;
                int depth = d < 2.6 ? 3 : d < 3.6 ? 2 : 1;
                for (int y = 0; y > -depth; y--) f.set(x, y, z, AIR);
                f.set(x, -depth, z, DIRT, 1);
            }
        // Steps down on the south side, and the tunnel mouth north under a trapdoor lid.
        f.set(0, 0, 4, DIRT, 1); f.set(0, -1, 3, DIRT, 1); f.set(0, -2, 2, DIRT, 1);
        for (int y = -2; y <= -1; y++) f.set(0, y, -5, AIR);
        f.set(0, 0, -5, TRAPDOOR, 8);
        // The hoard: stolen Concord things, tally sticks, a jack-o'-lantern.
        f.chest(-2, -2, -1, 1, 0, "atlas:orc_stash", "book:requisitions");
        f.set(2, -2, -1, 91, 0);
        for (int k = -1; k <= 1; k++) f.set(3, -1, k, FENCE);
        f.set(-2, -1, 2, BONE, 0);
        f.set(2, -1, 2, FLOWER_POT);
        f.post(0, 1, 6, 0, 1, freed ? "VESK IS\nSTILL HERE.\nVESK KNOWS\nNEW THINGS." : "VESK KNOWS.\nVESK SELLS.\nVESK DOES\nNOT BITE.");
        f.npc(0, -2, 0, 0, 1, "talker:vesk", null);
    }
}
