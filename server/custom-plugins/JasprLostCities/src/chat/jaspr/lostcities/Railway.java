package chat.jaspr.lostcities;

import java.util.Random;

import static chat.jaspr.lostcities.RailChunkType.*;

/** Port of lost.Railway: the subway grid (stations every 10 chunks on rows, links on columns 5 and 15). */
final class Railway {
    static final int RAILWAY_LEVEL_OFFSET = -3;

    enum RailDirection { BI, WEST, EAST }

    static final class RailChunkInfo {
        final RailChunkType type;
        final RailDirection direction;
        final int level;
        final int rails;
        final String part;
        static final RailChunkInfo NOTHING = new RailChunkInfo(NONE, RailDirection.BI, 0, 0, null);

        RailChunkInfo(RailChunkType type, RailDirection direction, int level, int rails) { this(type, direction, level, rails, null); }
        RailChunkInfo(RailChunkType type, RailDirection direction, int level, int rails, String part) {
            this.type = type; this.direction = direction; this.level = level; this.rails = rails; this.part = part;
        }
    }

    private final CityWorld w;
    private final Lru<Long, RailChunkInfo> cache = new Lru<>(65536);

    Railway(CityWorld w) { this.w = w; }

    RailChunkInfo getRailChunkType(int chunkX, int chunkZ) {
        long key = CityWorld.key(chunkX, chunkZ);
        RailChunkInfo info = cache.get(key);
        if (info != null) return info;
        info = internal(chunkX, chunkZ);
        if (info.type.isStation()) {
            if (!Profile.RAILWAY_STATIONS_ENABLED) info = RailChunkInfo.NOTHING;
        } else if (!Profile.RAILWAYS_ENABLED) {
            info = RailChunkInfo.NOTHING;
        }
        cache.put(key, info);
        return info;
    }

    private boolean city(int x, int z) { return w.isCityRaw(x, z); }

    private RailChunkInfo internal(int chunkX, int chunkZ) {
        Random rand = new QualityRandom(w.seed + chunkZ * 2600003897L + chunkX * 43600002517L);
        rand.nextFloat();
        rand.nextFloat();
        float r = rand.nextFloat();

        int mx = Math.floorMod(chunkX + 1, 20);
        int mz = Math.floorMod(chunkZ + 1, 20);
        if (mx == 0 && mz == 10) {
            if (!city(chunkX, chunkZ)) {
                if (Profile.RAILWAYS_CAN_END) {
                    boolean cityEast = city(chunkX + 10, chunkZ) || city(chunkX + 10, chunkZ - 10) || city(chunkX + 10, chunkZ + 10);
                    boolean cityWest = city(chunkX - 10, chunkZ) || city(chunkX - 10, chunkZ - 10) || city(chunkX - 10, chunkZ + 10);
                    if (!cityEast && !cityWest) return RailChunkInfo.NOTHING;
                    if (!cityEast) return new RailChunkInfo(RAILS_END_HERE, RailDirection.WEST, -3, 3);
                    if (!cityWest) return new RailChunkInfo(RAILS_END_HERE, RailDirection.EAST, -3, 3);
                }
                return new RailChunkInfo(HORIZONTAL, RailDirection.BI, RAILWAY_LEVEL_OFFSET, 3);
            }
            return getStationType(chunkX, chunkZ, r, 3, rand.nextFloat() < .5f ? "station_open" : "station_openroof");
        }
        if (mx == 10 && mz == 0) {
            if (!city(chunkX, chunkZ)) {
                if (Profile.RAILWAYS_CAN_END) {
                    boolean cityEast = city(chunkX + 10, chunkZ - 10) || city(chunkX + 10, chunkZ + 10);
                    boolean cityWest = city(chunkX - 10, chunkZ - 10) || city(chunkX - 10, chunkZ + 10);
                    if (!cityEast && !cityWest) return RailChunkInfo.NOTHING;
                    if (!cityEast) return new RailChunkInfo(RAILS_END_HERE, RailDirection.WEST, -3, 2);
                    if (!cityWest) return new RailChunkInfo(RAILS_END_HERE, RailDirection.EAST, -3, 2);
                }
                return new RailChunkInfo(HORIZONTAL, RailDirection.BI, RAILWAY_LEVEL_OFFSET, 2);
            }
            return getStationType(chunkX, chunkZ, r, 2, rand.nextFloat() < .5f ? "station_open" : "station_openroof");
        }
        if (mx == 10 && mz == 10) {
            if (!city(chunkX, chunkZ)) {
                if (Profile.RAILWAYS_CAN_END) {
                    boolean cityEast = city(chunkX + 10, chunkZ);
                    boolean cityWest = city(chunkX - 10, chunkZ);
                    if (!cityEast && !cityWest) return RailChunkInfo.NOTHING;
                    if (!cityEast) return new RailChunkInfo(RAILS_END_HERE, RailDirection.WEST, -3, 1);
                    if (!cityWest) return new RailChunkInfo(RAILS_END_HERE, RailDirection.EAST, -3, 1);
                }
                return new RailChunkInfo(HORIZONTAL, RailDirection.BI, RAILWAY_LEVEL_OFFSET, 1);
            }
            return getStationType(chunkX, chunkZ, r, 1, rand.nextFloat() < .5f ? "station_open" : "station_openroof");
        }
        if (mx == 0 && mz == 0) return RailChunkInfo.NOTHING;

        if (mz == 0 || mz == 10) {
            if ((mx >= 16 && mz != 0) || (mx >= 6 && mx <= 9)) {
                RailChunkInfo adjacent = getRailChunkType(chunkX + 1, chunkZ);
                RailDirection direction = adjacent.direction;
                if (direction == RailDirection.BI || adjacent.type == RAILS_END_HERE) direction = RailDirection.WEST;
                return testAdjacentRailChunk(r, adjacent, direction, chunkX - 1, chunkZ);
            }
            if ((mx >= 1 && mx <= 4 && mz != 0) || (mx >= 11 && mx <= 14)) {
                RailChunkInfo adjacent = getRailChunkType(chunkX - 1, chunkZ);
                RailDirection direction = adjacent.direction;
                if (direction == RailDirection.BI || adjacent.type == RAILS_END_HERE) direction = RailDirection.EAST;
                return testAdjacentRailChunk(r, adjacent, direction, chunkX + 1, chunkZ);
            }
            if (mz == 0 && mx == 5) {
                if (Profile.RAILWAYS_CAN_END) {
                    boolean cityWest = city(chunkX - 5, chunkZ - 10) || city(chunkX - 5, chunkZ + 10);
                    boolean cityEast = city(chunkX + 5, chunkZ);
                    if (!cityEast && !cityWest) return RailChunkInfo.NOTHING;
                }
                return new RailChunkInfo(DOUBLE_BEND, RailDirection.EAST, RAILWAY_LEVEL_OFFSET, 1);
            }
            if (mz == 0 && mx == 15) {
                if (Profile.RAILWAYS_CAN_END) {
                    boolean cityEast = city(chunkX + 5, chunkZ - 10) || city(chunkX + 5, chunkZ + 10);
                    boolean cityWest = city(chunkX - 5, chunkZ);
                    if (!cityEast && !cityWest) return RailChunkInfo.NOTHING;
                }
                return new RailChunkInfo(DOUBLE_BEND, RailDirection.WEST, RAILWAY_LEVEL_OFFSET, 1);
            }
            if (mz == 10 && (mx == 5 || mx == 15)) {
                if (Profile.RAILWAYS_CAN_END) {
                    boolean cityEast = city(chunkX + 5, chunkZ);
                    boolean cityWest = city(chunkX - 5, chunkZ);
                    if (!cityEast && !cityWest) {
                        // The mod checks the double bend to the north twice (its "south" check repeats chunkZ - 10).
                        RailChunkInfo typeNorth = getRailChunkType(chunkX, chunkZ - 10);
                        if (typeNorth.type == NONE) {
                            RailChunkInfo typeSouth = getRailChunkType(chunkX, chunkZ - 10);
                            if (typeSouth.type == NONE) return RailChunkInfo.NOTHING;
                        }
                    }
                }
                return new RailChunkInfo(THREE_SPLIT, mx == 5 ? RailDirection.EAST : RailDirection.WEST, RAILWAY_LEVEL_OFFSET, 3);
            }
            return RailChunkInfo.NOTHING;
        }
        if (mx == 5 || mx == 15) {
            if (Profile.RAILWAYS_CAN_END) {
                RailChunkInfo typeNorth = getRailChunkType(chunkX, chunkZ - (mz % 10));
                RailChunkInfo typeSouth = getRailChunkType(chunkX, chunkZ - (mz % 10) + 10);
                if (typeNorth.type == NONE || typeSouth.type == NONE) return RailChunkInfo.NOTHING;
            }
            return new RailChunkInfo(VERTICAL, mx == 5 ? RailDirection.EAST : RailDirection.WEST, RAILWAY_LEVEL_OFFSET, 1);
        }
        return RailChunkInfo.NOTHING;
    }

    private RailChunkInfo getStationType(int chunkX, int chunkZ, float r, int rails, String part) {
        int cityLevel = w.cityLevel(chunkX, chunkZ);
        if (cityLevel > 2) return new RailChunkInfo(STATION_UNDERGROUND, RailDirection.BI, RAILWAY_LEVEL_OFFSET, rails);
        int highwayX = w.highway.getXHighwayLevel(chunkX, chunkZ);
        int highwayZ = w.highway.getZHighwayLevel(chunkX, chunkZ);
        if ((highwayX != -1 && cityLevel >= highwayX) || (highwayZ != -1 && cityLevel >= highwayZ)) {
            return new RailChunkInfo(STATION_UNDERGROUND, RailDirection.BI, RAILWAY_LEVEL_OFFSET, rails);
        } else {
            highwayZ = w.highway.getZHighwayLevel(chunkX - 1, chunkZ);
            if (highwayZ != -1 && cityLevel >= highwayZ) return new RailChunkInfo(STATION_UNDERGROUND, RailDirection.BI, RAILWAY_LEVEL_OFFSET, rails);
            highwayZ = w.highway.getZHighwayLevel(chunkX + 1, chunkZ);
            if (highwayZ != -1 && cityLevel >= highwayZ) return new RailChunkInfo(STATION_UNDERGROUND, RailDirection.BI, RAILWAY_LEVEL_OFFSET, rails);
        }
        return r < .5f ? new RailChunkInfo(STATION_SURFACE, RailDirection.BI, cityLevel, rails, part)
            : new RailChunkInfo(STATION_UNDERGROUND, RailDirection.BI, RAILWAY_LEVEL_OFFSET, rails);
    }

    private RailChunkInfo testAdjacentRailChunk(float r, RailChunkInfo adjacent, RailDirection direction, int chunkX, int chunkZ) {
        switch (adjacent.type) {
            case NONE:
                return RailChunkInfo.NOTHING;
            case STATION_SURFACE: {
                int highwayX = w.highway.getXHighwayLevel(chunkX, chunkZ);
                int highwayZ = w.highway.getZHighwayLevel(chunkX, chunkZ);
                if ((highwayX != -1 && adjacent.level == highwayX) || (highwayZ != -1 && adjacent.level == highwayZ)) r = 1;
                if (r < .4f) return new RailChunkInfo(STATION_EXTENSION_SURFACE, direction, adjacent.level, adjacent.rails);
                else if ((adjacent.level & 1) == 0) return new RailChunkInfo(GOING_DOWN_ONE_FROM_SURFACE, direction, adjacent.level - 1, adjacent.rails);
                else return new RailChunkInfo(GOING_DOWN_TWO_FROM_SURFACE, direction, adjacent.level - 2, adjacent.rails);
            }
            case STATION_UNDERGROUND:
                return r < .4f ? new RailChunkInfo(STATION_EXTENSION_UNDERGROUND, direction, adjacent.level, adjacent.rails)
                    : new RailChunkInfo(HORIZONTAL, direction, adjacent.level, adjacent.rails);
            case STATION_EXTENSION_SURFACE:
                if ((adjacent.level & 1) == 0) return new RailChunkInfo(GOING_DOWN_ONE_FROM_SURFACE, direction, adjacent.level - 1, adjacent.rails);
                else return new RailChunkInfo(GOING_DOWN_TWO_FROM_SURFACE, direction, adjacent.level - 2, adjacent.rails);
            case STATION_EXTENSION_UNDERGROUND:
                return new RailChunkInfo(HORIZONTAL, direction, adjacent.level, adjacent.rails);
            case GOING_DOWN_FURTHER:
            case GOING_DOWN_ONE_FROM_SURFACE:
            case GOING_DOWN_TWO_FROM_SURFACE:
                if (adjacent.level == RAILWAY_LEVEL_OFFSET) return new RailChunkInfo(HORIZONTAL, direction, adjacent.level, adjacent.rails);
                else return new RailChunkInfo(GOING_DOWN_FURTHER, direction, adjacent.level - 2, adjacent.rails);
            case RAILS_END_HERE:
                if (direction == adjacent.direction) return new RailChunkInfo(HORIZONTAL, direction, adjacent.level, adjacent.rails);
                else return RailChunkInfo.NOTHING;
            case HORIZONTAL:
                return adjacent;
            default:
                break;
        }
        throw new IllegalStateException("This is really impossible!");
    }

    void clear() { cache.clear(); }

    String stats() { return "rail=" + cache.stats(); }
}
