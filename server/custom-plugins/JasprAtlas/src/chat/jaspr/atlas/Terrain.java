package chat.jaspr.atlas;

/**
 * Atlas is a realm of broad, low plains: long sightlines, gentle swells in the Concord, flat ash under the Dominion with
 * low dunes, and a raised, cracked Plateau of Cinders around Anthrakion. Cities and strongholds stand on levelled ground
 * that blends into the land around them.
 */
final class Terrain {
    private final long seed;

    Terrain(long seed) { this.seed = seed; }

    /** The level a fixed place is built on. */
    static int base(Realm.Place p) {
        switch (p) {
            case GATE_OF_STRANGERS: return 67;
            case ASTREION: return 68;
            case LAMPSA: case HIERANTHE: return 66;
            case MNEMEIA: return 67;
            case LAST_WATCH: return 65;
            case ANTHRAKION: return 73;
            default: return 64;
        }
    }

    /** Natural height before places are levelled. */
    double natural(int x, int z) {
        Realm.Zone zone = Realm.zone(x, z);
        double n1 = Hash.noise(seed ^ 0xA7L, x, z, 190), n2 = Hash.noise(seed ^ 0xA8L, x, z, 64), n3 = Hash.noise(seed ^ 0xA9L, x, z, 23);
        switch (zone) {
            case CONCORD: case LINE: return 66 + (n1 - 0.5) * 9 + (n2 - 0.5) * 4 + (n3 - 0.5) * 1.2;
            case WOUND: return 64.5 + (n1 - 0.5) * 6 + (n2 - 0.5) * 3;
            case PLATEAU: {
                double r = Realm.teethDistance(x, z);
                return 73 + (n2 - 0.5) * 2.5 - Math.max(0, r - (Realm.PLATEAU_R - 14)) * 0.5;
            }
            case RIM: return 64;
            default: {
                double dunes = n3 > 0.68 ? (n3 - 0.68) * 7 : 0;
                double h = 64 + (n1 - 0.5) * 6 + (n2 - 0.5) * 3 + dunes;
                // The ground rises toward the Plateau's rim.
                double r = Realm.teethDistance(x, z);
                if (r < Realm.PLATEAU_R + 30) h += (Realm.PLATEAU_R + 30 - r) / 30.0 * 6;
                return h;
            }
        }
    }

    /** Surface height: natural land, levelled under places (blending over 16 blocks). */
    int height(int x, int z) {
        double h = natural(x, z);
        for (Realm.Place p : Realm.Place.values()) {
            double dx = Math.max(0, Math.abs(x - p.x) - p.radius), dz = Math.max(0, Math.abs(z - p.z) - p.radius);
            double d = Math.sqrt(dx * dx + dz * dz);
            if (d >= 16) continue;
            double t = d / 16.0;
            t = t * t * (3 - 2 * t);
            h = base(p) + (h - base(p)) * t;
        }
        return (int) Math.round(h);
    }
}
