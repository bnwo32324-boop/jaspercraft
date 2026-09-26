package chat.jaspr.lostcities;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/** Port of lost.DamageArea and lost.Explosion: seeded explosions around cities and their per-block damage. */
final class DamageArea {
    static final float BLOCK_DAMAGE_CHANCE = .7f;

    static final class Explosion {
        final int radius, sqradius, x, y, z;
        Explosion(int radius, int x, int y, int z) { this.radius = radius; this.sqradius = radius * radius; this.x = x; this.y = y; this.z = z; }
        double distanceSq(double px, double py, double pz) { double dx = x - px, dy = y - py, dz = z - pz; return dx * dx + dy * dy + dz * dz; }
    }

    private final long seed;
    private final int chunkX, chunkZ;
    final List<Explosion> explosions = new ArrayList<>();

    DamageArea(CityWorld w, BuildingInfo info) {
        this.seed = w.seed;
        this.chunkX = info.chunkX;
        this.chunkZ = info.chunkZ;
        Random rand = new Random(seed + chunkZ * 295075153L + chunkX * 899826547L);
        rand.nextFloat();
        rand.nextFloat();
        int offset = (Math.max(Profile.EXPLOSION_MAXRADIUS, Profile.MINI_EXPLOSION_MAXRADIUS) + 15) / 16;
        for (int cx = chunkX - offset; cx <= chunkX + offset; cx++) {
            for (int cz = chunkZ - offset; cz <= chunkZ + offset; cz++) {
                if (!Profile.EXPLOSIONS_IN_CITIES_ONLY || w.isCityRaw(cx, cz)) {
                    Explosion explosion = getExplosionAt(w, cx, cz);
                    if (explosion != null && intersects(explosion)) {
                        Float chance = w.explosionChances ? BuildingInfo.characteristics(w, cx, cz).cityStyle.explosionChance : null;
                        if (chance == null || rand.nextFloat() < chance) explosions.add(explosion);
                    }
                    explosion = getMiniExplosionAt(w, cx, cz);
                    if (explosion != null && intersects(explosion)) {
                        Float chance = w.explosionChances ? BuildingInfo.characteristics(w, cx, cz).cityStyle.explosionChance : null;
                        if (chance == null || rand.nextFloat() < chance) explosions.add(explosion);
                    }
                }
            }
        }
    }

    /** DamageArea.damageBlock: glass takes 2.5x damage; lightly damaged blocks may become their 'damaged' form. */
    char damageBlock(char b, Random rand, int y, float damage, CompiledPalette palette, char liquidChar) {
        if (b == B.BEDROCK || (b >> 4) == 119 || (b >> 4) == 120) return b;
        if (B.isGlass(b)) damage *= 2.5f;
        if (rand.nextFloat() <= damage) {
            Character damaged = palette.canBeDamagedToIronBars(b);
            int waterlevel = Profile.WATERLEVEL;
            if (damage < BLOCK_DAMAGE_CHANCE && damaged != null) {
                if (rand.nextFloat() < .7f) b = damaged;
                else b = y <= waterlevel ? liquidChar : B.AIR;
            } else {
                b = y <= waterlevel ? liquidChar : B.AIR;
            }
        }
        return b;
    }

    private static double sqDistBox(int minX, int minY, int minZ, int maxX, int maxY, int maxZ, int px, int py, int pz) {
        double d = 0;
        if (px < minX) d += Math.pow(px - minX, 2); else if (px > maxX) d += Math.pow(px - maxX, 2);
        if (py < minY) d += Math.pow(py - minY, 2); else if (py > maxY) d += Math.pow(py - maxY, 2);
        if (pz < minZ) d += Math.pow(pz - minZ, 2); else if (pz > maxZ) d += Math.pow(pz - maxZ, 2);
        return d;
    }

    private static double maxSqDistBox(int minX, int minY, int minZ, int maxX, int maxY, int maxZ, int px, int py, int pz) {
        double d = 0;
        d += px < (minX + maxX) / 2.0 ? Math.pow(px - maxX, 2) : Math.pow(px - minX, 2);
        d += py < (minY + maxY) / 2.0 ? Math.pow(py - maxY, 2) : Math.pow(py - minY, 2);
        d += pz < (minZ + maxZ) / 2.0 ? Math.pow(pz - maxZ, 2) : Math.pow(pz - minZ, 2);
        return d;
    }

    private boolean intersects(Explosion e) {
        return sqDistBox(chunkX * 16, 0, chunkZ * 16, chunkX * 16 + 15, 256, chunkZ * 16 + 15, e.x, e.y, e.z) <= e.radius * e.radius;
    }

    private Explosion getExplosionAt(CityWorld w, int cx, int cz) {
        Random rand = new Random(seed + cz * 295075153L + cx * 797003437L);
        rand.nextFloat();
        rand.nextFloat();
        if (rand.nextFloat() < Profile.EXPLOSION_CHANCE) {
            int r = Profile.EXPLOSION_MINRADIUS + rand.nextInt(Profile.EXPLOSION_MAXRADIUS - Profile.EXPLOSION_MINRADIUS);
            int x = cx * 16 + rand.nextInt(16);
            int y = w.cityLevel(cx, cz) * 6 + Profile.EXPLOSION_MINHEIGHT + rand.nextInt(Profile.EXPLOSION_MAXHEIGHT - Profile.EXPLOSION_MINHEIGHT);
            int z = cz * 16 + rand.nextInt(16);
            return new Explosion(r, x, y, z);
        }
        return null;
    }

    private Explosion getMiniExplosionAt(CityWorld w, int cx, int cz) {
        Random rand = new Random(seed + cz * 1400305337L + cx * 573259391L);
        rand.nextFloat();
        rand.nextFloat();
        if (rand.nextFloat() < Profile.MINI_EXPLOSION_CHANCE) {
            int r = Profile.MINI_EXPLOSION_MINRADIUS + rand.nextInt(Profile.MINI_EXPLOSION_MAXRADIUS - Profile.MINI_EXPLOSION_MINRADIUS);
            int x = cx * 16 + rand.nextInt(16);
            int y = w.cityLevel(cx, cz) * 6 + Profile.MINI_EXPLOSION_MINHEIGHT + rand.nextInt(Profile.MINI_EXPLOSION_MAXHEIGHT - Profile.MINI_EXPLOSION_MINHEIGHT);
            int z = cz * 16 + rand.nextInt(16);
            return new Explosion(r, x, y, z);
        }
        return null;
    }

    boolean hasExplosions() { return !explosions.isEmpty(); }

    boolean hasExplosions(int y) {
        for (Explosion e : explosions) {
            double dmin = sqDistBox(chunkX * 16, y * 16, chunkZ * 16, chunkX * 16 + 15, y * 16 + 15, chunkZ * 16 + 15, e.x, e.y, e.z);
            if (dmin <= e.radius * e.radius) return true;
        }
        return false;
    }

    boolean isCompletelyDestroyed(int y) {
        for (Explosion e : explosions) {
            double dmax = maxSqDistBox(chunkX * 16, y * 16, chunkZ * 16, chunkX * 16 + 15, y * 16 + 15, chunkZ * 16 + 15, e.x, e.y, e.z);
            int sqdist = e.radius * e.radius;
            if (dmax <= sqdist) {
                double dist = (e.radius - 3.0 * e.radius) / -3.0;
                dist *= dist;
                if (dmax <= dist) return true;
            }
        }
        return false;
    }

    int getLowestExplosionHeight() {
        for (int yy = 0; yy < 16; yy++) if (hasExplosions(yy)) return yy * 16;
        return -1;
    }

    int getHighestExplosionHeight() {
        for (int yy = 15; yy >= 0; yy--) if (hasExplosions(yy)) return yy * 16 + 15;
        return -1;
    }

    float getDamageFactor() {
        float damage = 0.0f;
        for (Explosion e : explosions) {
            double sq = e.distanceSq(chunkX * 16, e.y, chunkZ * 16);
            if (sq < e.sqradius) {
                double d = Math.sqrt(sq);
                damage += 3.0f * (e.radius - d) / e.radius;
            }
        }
        return damage;
    }

    float getDamage(int x, int y, int z) {
        float damage = 0.0f;
        for (Explosion e : explosions) {
            double sq = e.distanceSq(x, y, z);
            if (sq < e.sqradius) {
                double d = Math.sqrt(sq);
                damage += 3.0f * (e.radius - d) / e.radius;
            }
        }
        return damage;
    }
}
