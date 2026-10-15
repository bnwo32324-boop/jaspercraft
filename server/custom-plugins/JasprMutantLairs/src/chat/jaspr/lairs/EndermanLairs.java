package chat.jaspr.lairs;

import java.util.Random;

/** The Mutant Enderman's three lairs. */
final class EndermanLairs {
    private EndermanLairs() {}

    // ================================================================ 10. Void Obelisk
    /** A ring of black obelisks round a sunken arena of end stone, shards of land floating over it, a vault under the altar. */
    static final class VoidObelisk extends Design {
        VoidObelisk() {
            super("void_obelisk", "The Void Obelisk", "mutant_enderman", "The Hollow Seer", 46, 46, 10,
                "Eight stones to|hold the door.|Seven still|stand.~The Seer moves|between them.|Under the altar|lies its hoard.");
        }
        @Override void draw(Canvas c, Random r) {
            int cu = 23, cv = 23;
            c.padCircle(cu, cv, 22, 30);
            Canvas.Mat endFloor = Canvas.mix(M.END_STONE, 5, M.END_BRICKS, 3, M.concrete(M.BLACK), 1);
            c.disc(cu, cv, 19.5, 0, Canvas.mix(M.END_STONE, 4, M.GRASS, 2, M.COARSE, 2, M.MYCELIUM, 1));
            c.ring(cu, cv, 16, 0, Canvas.of(M.concrete(M.BLACK)));
            c.ring(cu, cv, 15, 0, Canvas.of(M.PURPUR));
            // the sunken arena (floor y -2) with a step ring
            c.disc(cu, cv, 12.5, -2, endFloor);
            c.disc(cu, cv, 12.5, -1, Canvas.of(M.AIR));
            c.disc(cu, cv, 12.5, 0, Canvas.of(M.AIR));
            c.ring(cu, cv, 12.5, -1, Canvas.of(M.PURPUR_SLAB_TOP));
            c.ring(cu, cv, 9, -2, Canvas.of(M.concrete(M.PURPLE)));
            c.ring(cu, cv, 5, -2, Canvas.of(M.OBSIDIAN));
            // eight obelisks, one fallen
            for (int k = 0; k < 8; k++) {
                double a = Math.toRadians(k * 45 + 22.5);
                int ou = (int) Math.round(cu + Math.cos(a) * 17), ov = (int) Math.round(cv + Math.sin(a) * 17);
                int h = 13 + r.nextInt(9);
                if (k == 5) {
                    // the fallen one lies outward
                    c.beam(ou, 1, ov, cu + Math.cos(a) * 24, 1, cv + Math.sin(a) * 24, 0.9, Canvas.mix(M.OBSIDIAN, 4, M.concrete(M.BLACK), 1));
                    c.fill(ou - 1, 1, ov - 1, ou + 1, 2, ov + 1, Canvas.mix(M.OBSIDIAN, 2, M.COBBLE, 1));
                    continue;
                }
                c.fill(ou - 1, 1, ov - 1, ou + 1, 3, ov + 1, M.OBSIDIAN);
                c.fill(ou - 1, 4, ov - 1, ou + 1, 4, ov + 1, M.PURPUR);
                for (int y = 5; y <= h; y++) {
                    int s = y < h - 4 ? 1 : 0;
                    c.fill(ou - s, y, ov - s, ou + s, y, ov + s, y % 5 == 0 ? M.PURPUR : M.OBSIDIAN);
                }
                c.set(ou, h + 1, ov, M.END_ROD_UP);
            }
            // shards of land drifting over the arena
            for (int i = 0; i < 6; i++) {
                double a = r.nextDouble() * Math.PI * 2, rad = 3 + r.nextDouble() * 11;
                double fu = cu + Math.cos(a) * rad, fv = cv + Math.sin(a) * rad, fy = 16 + r.nextInt(10);
                c.ellipsoid(fu, fy, fv, 2 + r.nextDouble() * 1.5, 1.4, 2 + r.nextDouble() * 1.5, Canvas.mix(M.END_STONE, 4, M.OBSIDIAN, 1), false, -99);
                c.ellipsoid(fu, fy - 1.5, fv, 1.2, 1.5, 1.2, Canvas.of(M.END_STONE), false, -99);
                c.set((int) Math.round(fu), (int) fy + 2, (int) Math.round(fv), M.END_ROD_UP);
            }
            // the altar
            c.fill(cu - 1, -1, cv - 1, cu + 1, 0, cv + 1, M.PURPUR_PILLAR);
            c.fill(cu - 1, 1, cv - 1, cu + 1, 1, cv + 1, M.PURPUR_SLAB);
            c.set(cu, 1, cv, M.END_ROD_UP);
            c.set(cu - 2, -1, cv, M.END_ROD_UP); c.set(cu + 2, -1, cv, M.END_ROD_UP); c.set(cu, -1, cv - 2, M.END_ROD_UP);
            // the offering chests by two obelisks
            c.chest(cu + 13, 1, cv + 7, M.W, "lair_enderman_cache");
            c.chest(cu - 13, 1, cv - 7, M.E, "lair_enderman_cache");
            c.spawner(cu - 17, 0, cv + 4, "endermite");
            // the vault under the altar: a hatch south of it, sealed with bars, a ladder down
            c.room(cu - 5, -10, cv - 4, cu + 5, -3, cv + 5, Canvas.mix(M.OBSIDIAN, 3, M.END_BRICKS, 2), Canvas.of(M.END_BRICKS), Canvas.of(M.OBSIDIAN));
            c.set(cu, -2, cv + 4, M.AIR);
            c.seal(cu, -2, cv + 4, M.IRON_BARS);
            c.set(cu, -3, cv + 4, M.AIR);
            ladder(c, cu, cv + 4, -9, -3, M.N);
            c.set(cu, -9, cv + 5, M.END_BRICKS);
            c.vaultChest(cu - 4, -9, cv - 2, M.E, "lair_enderman_vault");
            c.vaultChest(cu + 4, -9, cv - 2, M.W, "lair_enderman_vault");
            c.set(cu, -9, cv - 3, M.END_ROD_UP); c.set(cu - 4, -9, cv + 3, M.BREWING);
            loreSign(c, cu + 1, -7, cv - 3, M.S, 1);
            c.vault(cu - 5, -10, cv - 4, cu + 5, -2, cv + 5);
            c.set(cu, 1, 44, M.OBSIDIAN); c.set(cu, 2, 44, M.OBSIDIAN);
            loreSign(c, cu, 2, 45, M.S, 0);
            c.boss(cu, -1, cv - 8, 22);
            c.garrison(cu + 8, -1, cv, "enderman+endermite+endermite");
            c.garrison(cu - 8, -1, cv + 3, "enderman+endermite");
            c.garrison(cu, 1, cv + 18, "enderman+enderman");
            c.garrison(cu - 18, 1, cv - 4, "enderman+endermite");
            c.garrison(cu + 16, 1, cv - 10, "enderman");
            c.garrison(cu + 4, -1, cv + 9, "endermite+endermite+endermite");
        }
    }

    // ================================================================ 11. Shattered Observatory
    /** A domed observatory with its glass dome split open, a great telescope, a gallery of books and a sealed archive below. */
    static final class Observatory extends Design {
        Observatory() {
            super("shattered_observatory", "The Shattered Observatory", "mutant_enderman", "The Stargazer", 38, 38, 9,
                "OBSERVATORY|Night watch|Keep the dome|closed~It looked back|through the|lens. Archive|sealed for good");
        }
        @Override void draw(Canvas c, Random r) {
            int cu = 19, cv = 19;
            c.padCircle(cu, cv, 18.5, 26);
            Canvas.Mat wall = Canvas.mix(M.SB, 5, M.CRACKED_SB, 2, M.MOSSY_SB, 1, M.POL_ANDESITE, 1);
            c.disc(cu, cv, 17.5, 0, Canvas.mix(M.GRASS, 4, M.GRAVEL, 2, M.POL_ANDESITE, 2, M.COARSE, 1));
            // walls, a quartz band, the floor with a star map
            c.cylinder(cu, cv, 12, 1, 10, wall, true);
            c.cylinder(cu, cv, 11, 1, 10, wall, true);
            c.ring(cu, cv, 12, 10, Canvas.of(M.QUARTZ));
            c.disc(cu, cv, 10.5, 0, Canvas.mix(M.POL_ANDESITE, 5, M.ANDESITE, 2));
            c.ring(cu, cv, 7, 0, Canvas.of(M.PURPUR));
            c.ring(cu, cv, 4, 0, Canvas.of(M.QUARTZ));
            for (int i = 0; i < 22; i++) {
                double a = r.nextDouble() * Math.PI * 2, rad = 1.5 + r.nextDouble() * 9;
                c.set((int) Math.round(cu + Math.cos(a) * rad), 0, (int) Math.round(cv + Math.sin(a) * rad), i % 3 == 0 ? M.concrete(M.YELLOW) : i % 3 == 1 ? M.concrete(M.LIGHT_BLUE) : M.concrete(M.PURPLE));
            }
            // the dome: dark glass between quartz ribs, a great crack open to the sky
            for (int v = cv - 13; v <= cv + 13; v++) for (int u = cu - 13; u <= cu + 13; u++) {
                double dist = Math.hypot(u - cu, v - cv);
                if (dist > 12.4) continue;
                double hy = Math.sqrt(Math.max(0, 144 - dist * dist));
                int y = 10 + (int) Math.round(hy);
                double ang = Math.toDegrees(Math.atan2(v - cv, u - cu));
                if (ang < 0) ang += 360;
                boolean crack = ang > 25 && ang < 70 && dist > 3;
                boolean rib = Math.abs(((ang + 22.5) % 45) - 22.5) < 3.2;
                if (crack) { if (c.chance(u, y, v, 0.15, 121)) c.set(u, y, v, M.glass(M.BLACK)); continue; }
                c.set(u, y, v, rib ? M.QUARTZ : M.glass(c.chance(u, y, v, 0.1, 122) ? M.PURPLE : M.BLACK));
                // fill the shell gaps of a steep dome edge
                int below = 10 + (int) Math.round(Math.sqrt(Math.max(0, 144 - Canvas.sq(dist + 1))));
                for (int yy = below + 1; yy < y; yy++) c.set(u, yy, v, rib ? M.QUARTZ : M.glass(M.BLACK));
            }
            // shattered glass on the floor under the crack
            for (int i = 0; i < 14; i++) { double a = Math.toRadians(30 + r.nextDouble() * 35), rad = 4 + r.nextDouble() * 6; c.soft((int) Math.round(cu + Math.cos(a) * rad), 1, (int) Math.round(cv + Math.sin(a) * rad), M.pane(M.BLACK)); }
            // the door at the south
            c.air(cu - 1, 1, cv + 10, cu + 1, 3, cv + 12);
            c.fill(cu - 1, 0, cv + 10, cu + 1, 0, cv + 14, M.POL_ANDESITE);
            // the telescope on its pedestal
            c.fill(cu - 1, 1, cv - 1, cu + 1, 2, cv + 1, M.axis(M.QUARTZ, 0));
            c.set(cu, 3, cv, M.CHISELED_QUARTZ);
            c.beam(cu - 3, 2.5, cv - 3, cu + 6, 16, cv + 6, 1.25, Canvas.mix(M.concrete(M.BLACK), 3, M.concrete(M.GRAY), 1));
            c.beam(cu + 5.5, 15.2, cv + 5.5, cu + 6.5, 16.6, cv + 6.5, 1.0, Canvas.of(M.QUARTZ));
            c.set(cu - 3, 2, cv - 3, M.SEA_LANTERN);
            // gallery of books round the wall at y 6, stairs up to it
            for (int deg = 0; deg < 360; deg += 4) {
                double a = Math.toRadians(deg);
                for (double rad = 8.6; rad <= 10.6; rad += 0.5) c.set((int) Math.round(cu + Math.cos(a) * rad), 6, (int) Math.round(cv + Math.sin(a) * rad), M.SPRUCE_SLAB_TOP);
                c.set((int) Math.round(cu + Math.cos(a) * 8.6), 7, (int) Math.round(cv + Math.sin(a) * 8.6), M.SPRUCE_FENCE);
                if (deg % 12 == 0) { int bu = (int) Math.round(cu + Math.cos(a) * 10.6), bv = (int) Math.round(cv + Math.sin(a) * 10.6); c.set(bu, 7, bv, M.BOOKSHELF); c.set(bu, 8, bv, M.BOOKSHELF); c.set(bu, 1, bv, M.BOOKSHELF); c.set(bu, 2, bv, M.BOOKSHELF); }
            }
            stairway(c, cu - 8, 1, cv + 4, M.N, 6, 2, M.SPRUCE_STAIRS, null);
            c.air(cu - 9, 7, cv - 2, cu - 9, 8, cv);
            for (int deg = 30; deg < 360; deg += 60) { double a = Math.toRadians(deg); c.set((int) Math.round(cu + Math.cos(a) * 9.4), 1, (int) Math.round(cv + Math.sin(a) * 9.4), M.TORCH); c.set((int) Math.round(cu + Math.cos(a) * 9.6), 7, (int) Math.round(cv + Math.sin(a) * 9.6), M.TORCH); }
            c.chest(cu + 9, 7, cv, M.W, "lair_enderman_cache");
            c.chest(cu - 8, 1, cv + 5, M.E, "lair_enderman_cache");
            // the sealed archive below: a stair from the hall's north side
            c.room(cu - 7, -8, cv - 7, cu + 7, -1, cv + 5, wall, Canvas.of(M.POL_ANDESITE), Canvas.of(M.SB));
            stairway(c, cu + 2, -7, cv - 1, M.N, 7, 2, M.SB_STAIRS, Canvas.of(M.SB));
            for (int u = cu + 2; u <= cu + 3; u++) for (int v = cv - 7; v <= cv - 5; v++) c.seal(u, 0, v, M.IRON_BARS);
            c.air(cu + 2, 1, cv - 7, cu + 3, 3, cv - 5);
            for (int u = cu - 6; u <= cu + 6; u += 2) { c.set(u, -7, cv + 4, M.BOOKSHELF); c.set(u, -6, cv + 4, M.BOOKSHELF); }
            c.vaultChest(cu - 5, -7, cv - 5, M.E, "lair_enderman_vault");
            c.vaultChest(cu - 5, -7, cv - 2, M.E, "lair_enderman_vault");
            c.set(cu - 6, -7, cv + 1, M.BREWING); c.set(cu - 3, -7, cv - 6, M.END_ROD_UP);
            c.set(cu - 2, -4, cv - 6, M.wallTorch(M.S));
            loreSign(c, cu - 4, -6, cv - 6, M.S, 1);
            c.vault(cu - 7, -8, cv - 7, cu + 7, 0, cv + 5);
            c.set(cu + 3, 1, cv + 15, M.COBBLE_WALL); c.set(cu + 3, 2, cv + 15, M.COBBLE_WALL);
            loreSign(c, cu + 3, 2, cv + 16, M.S, 0);
            c.boss(cu - 5, 1, cv + 5, 16);
            c.garrison(cu + 6, 1, cv + 5, "enderman+endermite");
            c.garrison(cu - 6, 1, cv - 5, "enderman");
            c.garrison(cu, 7, cv + 9, "endermite+endermite");
            c.garrison(cu + 14, 1, cv + 8, "enderman+enderman");
            c.garrison(cu - 14, 1, cv - 8, "enderman+endermite");
        }
    }

    // ================================================================ 12. Ender Grove
    /** A dark grove of warped trees round a sunken amphitheatre; the vault is the hollow of the greatest trunk. */
    static final class EnderGrove extends Design {
        EnderGrove() {
            super("ender_grove", "The Ender Grove", "mutant_enderman", "The Grove Shepherd", 48, 48, 10,
                "Do not look|at the trees.~The Shepherd|tends the rift.|The old trunk|keeps its seed.");
        }
        @Override void draw(Canvas c, Random r) {
            int cu = 22, cv = 24;
            c.padCircle(cu + 2, cv, 23, 28);
            c.pad(32, 2, 46, 16, 30);
            c.disc(cu + 2, cv, 22.5, 0, Canvas.mix(M.PODZOL, 4, M.MYCELIUM, 3, M.END_STONE, 2, M.COARSE, 2, M.GRASS, 2));
            // the amphitheatre: tiers of end brick down to a floor at y -3
            for (int t = 0; t < 3; t++) {
                double rr = 12.5 - t * 1.6;
                c.disc(cu, cv, rr, -t, Canvas.mix(M.END_BRICKS, 3, M.END_STONE, 2, M.PURPUR, 0));
                c.disc(cu, cv, rr - 1.6, -t, Canvas.of(M.AIR));
                c.disc(cu, cv, rr, -t + 1, Canvas.of(M.AIR));
            }
            c.disc(cu, cv, 7.7, -3, Canvas.mix(M.END_STONE, 4, M.OBSIDIAN, 1, M.concrete(M.BLACK), 1));
            c.ring(cu, cv, 3, -3, Canvas.of(M.PURPUR));
            c.set(cu, -2, cv, M.END_ROD_UP);
            // warped trees round the clearing: bent dark trunks, dark crowns, rods hanging like seed pods
            for (int k = 0; k < 9; k++) {
                double a = Math.toRadians(k * 40 + r.nextInt(16));
                double tu = cu + Math.cos(a) * (16 + r.nextDouble() * 4), tv = cv + Math.sin(a) * (16 + r.nextDouble() * 4);
                if (tu > 31 && tv < 16) continue;
                int h = 9 + r.nextInt(6);
                double lean = (r.nextDouble() - 0.5) * 6, lean2 = (r.nextDouble() - 0.5) * 6;
                c.beam(tu, 1, tv, tu + lean, h, tv + lean2, 0.8, Canvas.of(M.BARK_DARK));
                c.ellipsoid(tu + lean, h + 1.5, tv + lean2, 3.5, 2.2, 3.5, Canvas.mix(M.LEAVES_DARK, 6, M.wool(M.PURPLE), 1, M.wool(M.BLACK), 1), false, -99);
                for (int j = 0; j < 3; j++) c.set((int) Math.round(tu + lean + (j - 1) * 2), h - 1, (int) Math.round(tv + lean2 + (j == 1 ? 2 : -1)), M.END_ROD_DOWN);
            }
            // pillars of purpur like petrified chorus
            for (int k = 0; k < 6; k++) {
                double a = Math.toRadians(k * 60 + 20);
                int pu = (int) Math.round(cu + Math.cos(a) * 13.5), pv = (int) Math.round(cv + Math.sin(a) * 13.5);
                int h = 3 + r.nextInt(5);
                post(c, pu, pv, 1, h, Canvas.of(M.PURPUR_PILLAR));
                c.set(pu, h + 1, pv, M.glass(M.MAGENTA));
                if (k % 2 == 0) { c.set(pu + 1, h - 1, pv, M.PURPUR_PILLAR); c.set(pu + 2, h, pv, M.glass(M.PURPLE)); }
            }
            // floating stones over the clearing
            for (int i = 0; i < 4; i++) {
                double fu = cu - 8 + r.nextDouble() * 16, fv = cv - 8 + r.nextDouble() * 16;
                c.ellipsoid(fu, 14 + r.nextInt(6), fv, 1.8, 1.2, 1.8, Canvas.mix(M.END_STONE, 3, M.OBSIDIAN, 1), false, -99);
            }
            c.scatter(2, 2, 46, 46, 1, 0.05, Canvas.mix(M.DEAD_BUSH, 2, M.FERN, 2, M.MUSHROOM_BROWN, 1, M.MUSHROOM_RED, 1), 131);
            c.chest(cu - 15, 1, cv + 9, M.E, "lair_enderman_cache");
            c.spawner(cu + 10, 0, cv + 14, "endermite");
            // the great hollow trunk (vault) at the north-east, roots spreading, door sealed with bars
            int gu = 39, gv = 9;
            c.cylinder(gu, gv, 5.5, 0, 22, Canvas.mix(M.BARK_DARK, 5, M.LOG_DARK, 1), true);
            c.cylinder(gu, gv, 4.5, 1, 20, Canvas.of(M.AIR), false);
            c.disc(gu, gv, 4.5, 0, Canvas.of(M.DARK_OAK));
            c.disc(gu, gv, 5.5, 21, Canvas.of(M.BARK_DARK));
            for (int k = 0; k < 6; k++) {
                double a = Math.toRadians(k * 60 + 10);
                c.beam(gu + Math.cos(a) * 5, 1, gv + Math.sin(a) * 5, gu + Math.cos(a) * 9, -1, gv + Math.sin(a) * 9, 0.7, Canvas.of(M.BARK_DARK));
                c.beam(gu + Math.cos(a) * 4, 20, gv + Math.sin(a) * 4, gu + Math.cos(a) * 9, 25, gv + Math.sin(a) * 9, 0.7, Canvas.of(M.BARK_DARK));
            }
            c.ellipsoid(gu, 27, gv, 8, 3.5, 8, Canvas.mix(M.LEAVES_DARK, 6, M.wool(M.PURPLE), 1), false, 24);
            c.air(gu - 6, 1, gv - 1, gu - 4, 3, gv + 1);
            for (int v = gv - 1; v <= gv + 1; v++) for (int y = 1; y <= 3; y++) c.seal(gu - 5, y, v, M.IRON_BARS);
            c.vaultChest(gu + 3, 1, gv - 1, M.W, "lair_enderman_vault");
            c.vaultChest(gu + 3, 1, gv + 1, M.W, "lair_enderman_vault");
            c.set(gu, 1, gv + 3, M.END_ROD_UP); c.set(gu - 1, 1, gv - 3, M.MUSHROOM_RED);
            for (int y = 2; y <= 14; y += 4) c.set(gu + 4, y, gv, M.endRod(M.W));
            loreSign(c, gu + 1, 2, gv - 4, M.S, 1);
            c.vault(gu - 6, 0, gv - 6, gu + 6, 23, gv + 6);
            c.set(cu + 2, 1, 46, M.PURPUR_PILLAR); c.set(cu + 2, 2, 46, M.PURPUR_PILLAR);
            loreSign(c, cu + 2, 2, 47, M.S, 0);
            c.boss(cu, -2, cv + 4, 22);
            c.garrison(cu + 5, -2, cv - 3, "enderman+endermite");
            c.garrison(cu - 5, -2, cv + 2, "enderman+enderman");
            c.garrison(cu - 16, 1, cv - 6, "enderman+endermite+endermite");
            c.garrison(cu + 14, 1, cv + 12, "enderman");
            c.garrison(cu, 1, cv - 17, "enderman+endermite");
            c.garrison(cu + 8, 1, cv - 13, "endermite+endermite");
        }
    }
}
