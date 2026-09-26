package chat.jaspr.lostcities;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;

/**
 * Port of lost.BuildingInfo (and LostChunkCharacteristics): everything the mod decides about one chunk -- city or
 * not, building or street, 2x2 multibuilding section, floors and cellars and their parts, doors, park/fountain/stair/
 * front/bridge/rail-dungeon parts, corridors, ruins, palette. Neighbours are looked up through the bounded cache
 * instead of being held in fields, so evicted infos never pin the rest of the world in memory.
 */
final class BuildingInfo {
    enum StreetType { NORMAL, FULL, PARK }

    static final class Characteristics {
        boolean isCity;
        boolean couldHaveBuilding;
        int section;
        int cityLevel;
        Assets.CityStyle cityStyle;
        Assets.MultiBuilding multiBuilding;
        Assets.Building buildingType;
    }

    final CityWorld w;
    final int chunkX, chunkZ;
    final int groundLevel = Profile.GROUNDLEVEL;
    final int waterLevel = Profile.WATERLEVEL;

    final boolean isCity;
    final boolean hasBuilding;
    final int building2x2Section;
    final Assets.MultiBuilding multiBuilding;
    final Assets.Building buildingType;
    final BuildingPart fountainType, parkType, bridgeType, stairType, frontType, railDungeon;
    private final float stairPriority;
    final StreetType streetType;
    private final int floors;
    final int floorsBelowGround;
    final BuildingPart[] floorTypes, floorTypes2;
    final boolean[] connectionAtX, connectionAtZ;
    final boolean noLoot;
    final float ruinHeight;
    final int cityLevel;
    final boolean xBridge, zBridge;
    final boolean xRailCorridor, zRailCorridor;
    final int doorBlock;
    private final Assets.CityStyle cityStyle;

    private DamageArea damageArea;
    private Palette palette;
    private CompiledPalette compiledPalette;

    private boolean xBridgeTypeCalculated, zBridgeTypeCalculated;
    private BuildingPart xBridgeType, zBridgeType;
    private boolean stairsCalculated, actualStairsCalculated;
    private Direction stairDirection, actualStairDirection;
    private Boolean elevatedPark;
    private Boolean xCorridor, zCorridor;

    // ------------------------------------------------------------------ cache access

    static BuildingInfo get(CityWorld w, int cx, int cz) {
        long k = CityWorld.key(cx, cz);
        BuildingInfo info = w.buildingInfos.get(k);
        if (info == null) {
            info = new BuildingInfo(w, cx, cz);
            w.buildingInfos.put(k, info);
        }
        return info;
    }

    BuildingInfo getXmin() { return get(w, chunkX - 1, chunkZ); }
    BuildingInfo getXmax() { return get(w, chunkX + 1, chunkZ); }
    BuildingInfo getZmin() { return get(w, chunkX, chunkZ - 1); }
    BuildingInfo getZmax() { return get(w, chunkX, chunkZ + 1); }

    boolean same(BuildingInfo o) { return o.chunkX == chunkX && o.chunkZ == chunkZ; }

    static Random getBuildingRandom(int chunkX, int chunkZ, long seed) {
        Random rand = new QualityRandom(seed + chunkZ * 341873128712L + chunkX * 132897987541L);
        rand.nextFloat();
        rand.nextFloat();
        return rand;
    }

    // ------------------------------------------------------------------ characteristics

    static Characteristics characteristics(CityWorld w, int chunkX, int chunkZ) {
        long key = CityWorld.key(chunkX, chunkZ);
        Characteristics c = w.characteristics.get(key);
        if (c != null) return c;
        c = new Characteristics();
        c.isCity = w.isCityRaw(chunkX, chunkZ);
        c.section = getMultiBuildingSection(w, chunkX, chunkZ);
        if (c.section > 0) c.cityLevel = topLeft(w, c, chunkX, chunkZ).cityLevel;
        else c.cityLevel = w.cityLevel(chunkX, chunkZ);
        Random rand = getBuildingRandom(chunkX, chunkZ, w.seed);
        c.couldHaveBuilding = c.isCity && checkBuildingPossibility(w, chunkX, chunkZ, c.section, c.cityLevel, rand);

        Assets.CityStyle cityStyle;
        if (c.isCity && !c.couldHaveBuilding) {
            // Streets take the majority style of the 3x3 around them (this chunk counted twice).
            Map<String, Integer> counter = new HashMap<>();
            String best = null;
            int bestCount = -1;
            for (int cx = -1; cx <= 1; cx++)
                for (int cz = -1; cz <= 1; cz++) {
                    String n = getCityStyle(w, chunkX + cx, chunkZ + cz).name;
                    counter.merge(n, (cx == 0 && cz == 0) ? 2 : 1, Integer::sum);
                }
            for (Map.Entry<String, Integer> e : counter.entrySet()) if (e.getValue() > bestCount) { bestCount = e.getValue(); best = e.getKey(); }
            cityStyle = w.assets.cityStyles.get(best);
        } else {
            cityStyle = getCityStyle(w, chunkX, chunkZ);
        }
        c.cityStyle = cityStyle;

        if (c.section >= 1) {
            Characteristics topleft = topLeft(w, c, chunkX, chunkZ);
            c.multiBuilding = topleft.multiBuilding;
            if (c.multiBuilding != null) {
                switch (c.section) {
                    case 1: c.buildingType = w.assets.buildings.get(c.multiBuilding.getBuilding(1, 0)); break;
                    case 2: c.buildingType = w.assets.buildings.get(c.multiBuilding.getBuilding(0, 1)); break;
                    default: c.buildingType = w.assets.buildings.get(c.multiBuilding.getBuilding(1, 1)); break;
                }
            } else {
                c.buildingType = topleft.buildingType;
            }
        } else if (c.section == 0) {
            String name = cityStyle.getRandomMultiBuilding(rand);
            c.multiBuilding = w.assets.multiBuildings.get(name);
            c.buildingType = w.assets.buildings.get(c.multiBuilding.getBuilding(0, 0));
        } else {
            c.multiBuilding = null;
            c.buildingType = w.assets.buildings.get(cityStyle.getRandomBuilding(rand));
        }
        w.characteristics.put(key, c);
        return c;
    }

    /** City.getCityStyle as it resolves for a chunk inside a city: a per-chunk pick from the world style. */
    static Assets.CityStyle getCityStyle(CityWorld w, int chunkX, int chunkZ) {
        Random rand = new Random(w.seed + chunkZ * 899809363L + chunkX * 256203221L);
        rand.nextFloat();
        rand.nextFloat();
        String name = w.worldStyle.getRandomCityStyle(w.biomes(chunkX, chunkZ), rand);
        return w.assets.cityStyles.get(name);
    }

    private static boolean checkBuildingPossibility(CityWorld w, int chunkX, int chunkZ, int section, int cityLevel, Random rand) {
        float bc = rand.nextFloat();
        if (section >= 0) return true;
        if (bc >= Profile.BUILDING_CHANCE) return false;
        if (hasHighway(w, chunkX, chunkZ)) {
            int maxh = Math.max(w.highway.getXHighwayLevel(chunkX, chunkZ), w.highway.getZHighwayLevel(chunkX, chunkZ));
            return cityLevel > maxh + 1;
        }
        if (hasRailway(w, chunkX, chunkZ)) {
            Railway.RailChunkInfo info = w.railway.getRailChunkType(chunkX, chunkZ);
            if (info.type == RailChunkType.STATION_UNDERGROUND) return false;
            return cityLevel > info.level + 1;
        }
        return true;
    }

    private static int getMultiBuildingSection(CityWorld w, int chunkX, int chunkZ) {
        if (isTopLeftOf2x2Building(w, chunkX, chunkZ)) return 0;
        if (isTopLeftOf2x2Building(w, chunkX - 1, chunkZ)) return 1;
        if (isTopLeftOf2x2Building(w, chunkX, chunkZ - 1)) return 2;
        if (isTopLeftOf2x2Building(w, chunkX - 1, chunkZ - 1)) return 3;
        return -1;
    }

    private static Characteristics topLeft(CityWorld w, Characteristics c, int chunkX, int chunkZ) {
        switch (c.section) {
            case 0: return c;
            case 1: return characteristics(w, chunkX - 1, chunkZ);
            case 2: return characteristics(w, chunkX, chunkZ - 1);
            case 3: return characteristics(w, chunkX - 1, chunkZ - 1);
            default: throw new IllegalStateException("What!");
        }
    }

    private static boolean isCandidateForTopLeftOf2x2Building(CityWorld w, int chunkX, int chunkZ) {
        if (isMultiBuildingCandidate(w, chunkX, chunkZ)) {
            Random rand = getBuildingRandom(chunkX, chunkZ, w.seed);
            return rand.nextFloat() < Profile.BUILDING2X2_CHANCE;
        }
        return false;
    }

    private static boolean isMultiBuildingCandidate(CityWorld w, int chunkX, int chunkZ) {
        return w.isCityRaw(chunkX, chunkZ) && !hasHighway(w, chunkX, chunkZ) && !hasRailway(w, chunkX, chunkZ);
    }

    static boolean hasHighway(CityWorld w, int chunkX, int chunkZ) {
        return w.highway.getXHighwayLevel(chunkX, chunkZ) >= 0 || w.highway.getZHighwayLevel(chunkX, chunkZ) >= 0;
    }

    static boolean hasRailway(CityWorld w, int chunkX, int chunkZ) {
        return w.railway.getRailChunkType(chunkX, chunkZ).type != RailChunkType.NONE;
    }

    private static boolean isTopLeftOf2x2Building(CityWorld w, int chunkX, int chunkZ) {
        if (isCandidateForTopLeftOf2x2Building(w, chunkX, chunkZ) &&
            !isCandidateForTopLeftOf2x2Building(w, chunkX - 1, chunkZ) &&
            !isCandidateForTopLeftOf2x2Building(w, chunkX - 1, chunkZ - 1) &&
            !isCandidateForTopLeftOf2x2Building(w, chunkX, chunkZ - 1) &&
            !isCandidateForTopLeftOf2x2Building(w, chunkX + 1, chunkZ - 1) &&
            !isCandidateForTopLeftOf2x2Building(w, chunkX + 1, chunkZ) &&
            !isCandidateForTopLeftOf2x2Building(w, chunkX + 1, chunkZ + 1) &&
            !isCandidateForTopLeftOf2x2Building(w, chunkX, chunkZ + 1) &&
            !isCandidateForTopLeftOf2x2Building(w, chunkX - 1, chunkZ + 1)) {
            return isMultiBuildingCandidate(w, chunkX + 1, chunkZ) &&
                isMultiBuildingCandidate(w, chunkX + 1, chunkZ + 1) &&
                isMultiBuildingCandidate(w, chunkX, chunkZ + 1);
        }
        return false;
    }

    // ------------------------------------------------------------------ construction

    private BuildingInfo(CityWorld w, int chunkX, int chunkZ) {
        this.w = w;
        this.chunkX = chunkX;
        this.chunkZ = chunkZ;
        Characteristics characteristics = characteristics(w, chunkX, chunkZ);
        isCity = characteristics.isCity;
        building2x2Section = characteristics.section;
        cityLevel = characteristics.cityLevel;
        buildingType = characteristics.buildingType;
        multiBuilding = characteristics.multiBuilding;
        cityStyle = characteristics.cityStyle;

        Random rand = getBuildingRandom(chunkX, chunkZ, w.seed);
        rand.nextFloat();       // Compatibility?

        boolean b = characteristics.couldHaveBuilding;
        if (b && building2x2Section < 0) {
            if (rand.nextFloat() < characteristics(w, chunkX - 1, chunkZ).buildingType.prefersLonely) b = false;
            else if (rand.nextFloat() < characteristics(w, chunkX + 1, chunkZ).buildingType.prefersLonely) b = false;
            else if (rand.nextFloat() < characteristics(w, chunkX, chunkZ - 1).buildingType.prefersLonely) b = false;
            else if (rand.nextFloat() < characteristics(w, chunkX, chunkZ + 1).buildingType.prefersLonely) b = false;
        }
        hasBuilding = b;

        Assets.CityStyle cs = characteristics.cityStyle;
        if (building2x2Section >= 1) {
            BuildingInfo topleft = calculateTopLeft();
            streetType = topleft.streetType;
            fountainType = topleft.fountainType;
            parkType = topleft.parkType;
            floors = topleft.floors;
            floorsBelowGround = topleft.floorsBelowGround;
            doorBlock = topleft.doorBlock;
            bridgeType = topleft.bridgeType;
            stairType = topleft.stairType;
            stairPriority = topleft.stairPriority;
            palette = topleft.palette;
            compiledPalette = topleft.getCompiledPalette();
            noLoot = topleft.noLoot;
            ruinHeight = topleft.ruinHeight;
        } else {
            if (rand.nextDouble() < Profile.PARK_CHANCE) streetType = StreetType.values()[rand.nextInt(StreetType.values().length)];
            else streetType = StreetType.NORMAL;
            if (rand.nextFloat() < Profile.FOUNTAIN_CHANCE) fountainType = w.assets.part(cs.getRandomFountain(rand));
            else fountainType = null;
            parkType = w.assets.part(cs.getRandomPark(rand));
            float cityFactor = Profile.CITY_FACTOR;

            int maxfloors = getMaxfloors(cs);
            int f = Profile.BUILDING_MINFLOORS + rand.nextInt((int) (Profile.BUILDING_MINFLOORS_CHANCE
                + (cityFactor + .1f) * (Profile.BUILDING_MAXFLOORS_CHANCE - Profile.BUILDING_MINFLOORS_CHANCE)));
            f++;
            if (f > maxfloors) f = maxfloors;
            int minfloors = getMinfloors(cs);
            if (f < minfloors) f = minfloors;
            floors = f;

            int maxcellars = getMaxcellars(cs);
            int fb = Profile.BUILDING_MINCELLARS + ((maxcellars <= 0) ? 0 : rand.nextInt(maxcellars));
            if (getMaxHighwayLevel() >= 0) {
                fb = Math.min(cityLevel - getMaxHighwayLevel() - 1, fb);
                if (fb < 0) fb = 0;
            }
            floorsBelowGround = fb;

            doorBlock = getRandomDoor(rand);
            bridgeType = w.assets.part(cs.getRandomBridge(rand));
            stairType = w.assets.part(cs.getRandomStair(rand));
            stairPriority = rand.nextFloat();
            createPalette(rand);
            float r = rand.nextFloat();
            noLoot = building2x2Section == -1 && r < Profile.BUILDING_WITHOUT_LOOT_CHANCE;
            r = rand.nextFloat();
            if (rand.nextFloat() < Profile.RUIN_CHANCE) {
                ruinHeight = Profile.RUIN_MINLEVEL_PERCENT + (Profile.RUIN_MAXLEVEL_PERCENT - Profile.RUIN_MINLEVEL_PERCENT) * r;
            } else {
                ruinHeight = -1;
            }
        }

        floorTypes = new BuildingPart[floors + floorsBelowGround + 1];
        floorTypes2 = new BuildingPart[floors + floorsBelowGround + 1];
        connectionAtX = new boolean[floors + floorsBelowGround + 1];
        connectionAtZ = new boolean[floors + floorsBelowGround + 1];
        Assets.Building building = buildingType;
        String biome = w.biomes(chunkX, chunkZ)[0];
        for (int i = 0; i <= floors + floorsBelowGround; i++) {
            ConditionContext ctx = new ConditionContext(cityLevel + i - floorsBelowGround, i - floorsBelowGround, floorsBelowGround, floors,
                "<none>", building.name, chunkX, chunkZ, biome, true);
            String randomPart = building.getRandomPart(rand, ctx);
            floorTypes[i] = w.assets.part(randomPart);
            if (floorTypes[i] == null) throw new IllegalStateException("Null part for building " + building.name + " floor " + (i - floorsBelowGround));
            randomPart = building.getRandomPart2(rand, ctx);
            floorTypes2[i] = w.assets.part(randomPart);
            connectionAtX[i] = w.isCityRaw(chunkX - 1, chunkZ) && (rand.nextFloat() < Profile.BUILDING_DOORWAYCHANCE);
            connectionAtZ[i] = w.isCityRaw(chunkX, chunkZ - 1) && (rand.nextFloat() < Profile.BUILDING_DOORWAYCHANCE);
        }

        if (hasBuilding && floorsBelowGround > 0) {
            xRailCorridor = false;
            zRailCorridor = false;
        } else {
            xRailCorridor = rand.nextFloat() < Profile.CORRIDOR_CHANCE;
            zRailCorridor = rand.nextFloat() < Profile.CORRIDOR_CHANCE;
        }

        if (isCity) {
            xBridge = false;
            zBridge = false;
        } else {
            xBridge = rand.nextFloat() < Profile.BRIDGE_CHANCE;
            zBridge = rand.nextFloat() < Profile.BRIDGE_CHANCE;
        }

        if (rand.nextFloat() < Profile.RAILWAY_DUNGEON_CHANCE) {
            if (!hasBuilding || (Railway.RAILWAY_LEVEL_OFFSET < (cityLevel - floorsBelowGround))) {
                railDungeon = w.assets.part(cityStyle.getRandomRailDungeon(rand));
            } else {
                railDungeon = null;
            }
        } else {
            railDungeon = null;
        }

        if (rand.nextFloat() < Profile.BUILDING_FRONTCHANCE) frontType = w.assets.part(cityStyle.getRandomFront(rand));
        else frontType = null;
    }

    private BuildingInfo calculateTopLeft() {
        switch (building2x2Section) {
            case 0: return this;
            case 1: return getXmin();
            case 2: return getZmin();
            case 3: return get(w, chunkX - 1, chunkZ - 1);
            default: throw new IllegalStateException("What!");
        }
    }

    private void createPalette(Random rand) {
        Assets.Style style;
        if (!isCity) {
            style = w.assets.styles.get(w.worldStyle.outsideStyle);
        } else {
            style = w.assets.styles.get(cityStyle.style);
            if (style == null) throw new IllegalStateException("Cannot find style '" + cityStyle.style + "'!");
        }
        palette = style.getRandomPalette(w.assets, rand);
    }

    private int getMaxcellars(Assets.CityStyle cs) {
        int maxcellars = Profile.BUILDING_MAXCELLARS + cityLevel;
        if (buildingType.maxCellars != -1) maxcellars = Math.min(maxcellars, buildingType.maxCellars);
        if (buildingType.minCellars != -1) maxcellars = Math.max(maxcellars, buildingType.minCellars);
        if (cs.maxCellarCount != null) maxcellars = Math.min(maxcellars, cs.maxCellarCount);
        if (cs.minCellarCount != null) maxcellars = Math.max(maxcellars, cs.minCellarCount);
        return maxcellars;
    }

    private int getMinfloors(Assets.CityStyle cs) {
        int minfloors = Profile.BUILDING_MINFLOORS + 1;
        if (buildingType.minFloors != -1) minfloors = Math.max(minfloors, buildingType.minFloors);
        if (cs.minFloorCount != null) minfloors = Math.max(minfloors, cs.minFloorCount);
        return minfloors;
    }

    private int getMaxfloors(Assets.CityStyle cs) {
        int maxfloors = Profile.BUILDING_MAXFLOORS;
        if (buildingType.maxFloors != -1) maxfloors = Math.min(maxfloors, buildingType.maxFloors);
        if (cs.maxFloorCount != null) maxfloors = Math.min(maxfloors, cs.maxFloorCount);
        return maxfloors;
    }

    private static int getRandomDoor(Random rand) {
        switch (rand.nextInt(7)) {
            case 0: return 194;   // birch_door
            case 1: return 196;   // acacia_door
            case 2: return 197;   // dark_oak_door
            case 3: return 193;   // spruce_door
            case 4: return 64;    // wooden_door (oak)
            case 5: return 195;   // jungle_door
            case 6: return 71;    // iron_door
            default: return 64;
        }
    }

    // ------------------------------------------------------------------ accessors

    Assets.CityStyle getCityStyle() { return cityStyle; }

    CompiledPalette getCompiledPalette() {
        if (compiledPalette == null) compiledPalette = new CompiledPalette(palette);
        return compiledPalette;
    }

    DamageArea getDamageArea() {
        if (damageArea == null) damageArea = new DamageArea(w, this);
        return damageArea;
    }

    int getHighwayXLevel() { return w.highway.getXHighwayLevel(chunkX, chunkZ); }
    int getHighwayZLevel() { return w.highway.getZHighwayLevel(chunkX, chunkZ); }
    int getMaxHighwayLevel() { return Math.max(getHighwayXLevel(), getHighwayZLevel()); }

    Railway.RailChunkInfo getRailInfo() { return w.railway.getRailChunkType(chunkX, chunkZ); }

    int getNumFloors() { return floors; }

    int getMaxHeight() {
        if (hasBuilding) return getCityGroundLevel() + floors * 6;
        int m = getMaxHighwayLevel();
        if (m >= 0) return groundLevel + m * 6;
        return getCityGroundLevel();
    }

    int getCityGroundLevel() { return groundLevel + cityLevel * 6; }

    int getCityGroundLevelOutsideLower() { return isCity ? groundLevel + cityLevel * 6 : groundLevel + cityLevel * 6 - 1; }

    boolean isValidFloor(int l) { return (l + floorsBelowGround) >= 0 && (l + floorsBelowGround) < floorTypes.length; }

    BuildingPart getFloor(int l) { return floorTypes[l + floorsBelowGround]; }

    BuildingPart getFloorPart2(int l) { return floorTypes2[l + floorsBelowGround]; }

    String getBuildingType() { return hasBuilding ? buildingType.name : null; }

    boolean isOcean() { return w.isOcean(chunkX, chunkZ); }

    /** True if a highway at this level would be a tunnel. */
    boolean isTunnel(int level) {
        if (isCity) return cityLevel > level;
        int highwayHeight = groundLevel + level * 6 + 3;
        int cnt = 0;
        for (int h : w.tunnelHeights(chunkX, chunkZ)) if (h > highwayHeight) cnt++;
        return cnt > 12;
    }

    boolean isStreetSection() { return isCity && !hasBuilding; }

    boolean isElevatedParkSection() {
        if (elevatedPark != null) return elevatedPark;
        boolean r = computeElevated();
        elevatedPark = r;
        return r;
    }

    private boolean computeElevated() {
        if (!isStreetSection()) return false;
        if (!getXmin().isStreetSection()) return false;
        if (!getXmax().isStreetSection()) return false;
        if (!getZmin().isStreetSection()) return false;
        if (!getZmax().isStreetSection()) return false;
        int cnt = 0;
        cnt += get(w, chunkX - 1, chunkZ - 1).isStreetSection() ? 1 : 0;
        cnt += get(w, chunkX - 1, chunkZ + 1).isStreetSection() ? 1 : 0;
        cnt += get(w, chunkX + 1, chunkZ - 1).isStreetSection() ? 1 : 0;
        cnt += get(w, chunkX + 1, chunkZ + 1).isStreetSection() ? 1 : 0;
        return cnt >= 3;
    }

    private Direction getStairDirection() {
        if (!stairsCalculated) {
            stairsCalculated = true;
            if (streetType != StreetType.PARK && !hasBuilding && isCity) {
                BuildingInfo xmin = getXmin(), xmax = getXmax(), zmin = getZmin(), zmax = getZmax();
                if (cityLevel == xmin.cityLevel - 1 && !xmin.hasBuilding && xmin.isCity) stairDirection = Direction.XMIN;
                else if (cityLevel == xmax.cityLevel - 1 && !xmax.hasBuilding && xmax.isCity) stairDirection = Direction.XMAX;
                else if (cityLevel == zmin.cityLevel - 1 && !zmin.hasBuilding && zmin.isCity) stairDirection = Direction.ZMIN;
                else if (cityLevel == zmax.cityLevel - 1 && !zmax.hasBuilding && zmax.isCity) stairDirection = Direction.ZMAX;
                else stairDirection = null;
            } else {
                stairDirection = null;
            }
        }
        return stairDirection;
    }

    /** The stair direction after competing stairs around with a higher priority have been taken into account. */
    Direction getActualStairDirection() {
        if (!actualStairsCalculated) {
            actualStairsCalculated = true;
            actualStairDirection = getStairDirection();
            if (actualStairDirection != null) {
                outer:
                for (int cx = -1; cx <= 1; cx++) {
                    for (int cz = -1; cz <= 1; cz++) {
                        if (cx != 0 || cz != 0) {
                            BuildingInfo adjacent = get(w, chunkX + cx, chunkZ + cz);
                            if (adjacent.getStairDirection() != null && adjacent.stairPriority > stairPriority) {
                                actualStairDirection = null;
                                break outer;
                            }
                        }
                    }
                }
            }
        }
        return actualStairDirection;
    }

    BuildingPart hasBridge(Orientation orientation) {
        return orientation == Orientation.X ? hasXBridge() : hasZBridge();
    }

    private boolean suitableForBridge(BuildingInfo i) {
        return i.cityLevel < cityLevel || w.isWaterBiome(i.chunkX, i.chunkZ);
    }

    /** Adjacent X bridges are prevented by giving bridges at even chunk Z the priority. */
    BuildingPart hasXBridge() {
        if (xBridgeTypeCalculated) return xBridgeType;
        xBridgeTypeCalculated = true;
        xBridgeType = null;
        if (!xBridge) return null;
        if (!suitableForBridge(this)) return null;
        if (chunkZ % 2 != 0 && (getZmin().hasXBridge() != null || getZmax().hasXBridge() != null)) return null;
        BuildingPart bt = bridgeType;
        BuildingInfo i = getXmin();
        int guard = 0;
        while ((!i.isCity) && i.xBridge && suitableForBridge(i) && guard++ < 512) {
            if (chunkZ % 2 != 0 && (i.getZmin().hasXBridge() != null || i.getZmax().hasXBridge() != null)) return null;
            bt = i.bridgeType;
            i = i.getXmin();
        }
        if ((!i.isCity) || i.hasBuilding || i.cityLevel > 0) return null;
        BuildingInfo minimum = i;
        i = getXmax();
        guard = 0;
        while ((!i.isCity) && i.xBridge && suitableForBridge(i) && guard++ < 512) {
            if (chunkZ % 2 != 0 && (i.getZmin().hasXBridge() != null || i.getZmax().hasXBridge() != null)) return null;
            i = i.getXmax();
        }
        if ((!i.isCity) || i.hasBuilding || i.cityLevel > 0) return null;
        xBridgeType = bt;
        i = i.getXmin();
        guard = 0;
        while (!i.same(minimum) && guard++ < 512) {
            i.xBridgeType = bt;
            i.xBridgeTypeCalculated = true;
            i.zBridgeType = null;
            i.zBridgeTypeCalculated = true;
            i = i.getXmin();
        }
        return bt;
    }

    /** Adjacent Z bridges are prevented by giving bridges at even chunk X the priority. */
    BuildingPart hasZBridge() {
        if (zBridgeTypeCalculated) return zBridgeType;
        zBridgeTypeCalculated = true;
        zBridgeType = null;
        if (!zBridge) return null;
        if (!suitableForBridge(this)) return null;
        if (hasXBridge() != null) return null;
        if (chunkX % 2 != 0 && (getXmin().hasZBridge() != null || getXmax().hasZBridge() != null)) return null;
        BuildingPart bt = bridgeType;
        BuildingInfo i = getZmin();
        int guard = 0;
        while ((!i.isCity) && i.zBridge && suitableForBridge(i) && guard++ < 512) {
            if (i.hasXBridge() != null) return null;
            if (chunkX % 2 != 0 && (i.getXmin().hasZBridge() != null || i.getXmax().hasZBridge() != null)) return null;
            bt = i.bridgeType;
            i = i.getZmin();
        }
        BuildingInfo minimum = i;
        if ((!i.isCity) || i.hasBuilding || i.cityLevel > 0) return null;
        i = getZmax();
        guard = 0;
        while ((!i.isCity) && i.zBridge && suitableForBridge(i) && guard++ < 512) {
            if (i.hasXBridge() != null) return null;
            if (chunkX % 2 != 0 && (i.getXmin().hasZBridge() != null || i.getXmax().hasZBridge() != null)) return null;
            i = i.getZmax();
        }
        if ((!i.isCity) || i.hasBuilding || i.cityLevel > 0) return null;
        zBridgeType = bt;
        i = i.getZmin();
        guard = 0;
        while (!i.same(minimum) && guard++ < 512) {
            i.zBridgeType = bt;
            i.zBridgeTypeCalculated = true;
            i.xBridgeType = null;
            i.xBridgeTypeCalculated = true;
            i = i.getZmin();
        }
        return bt;
    }

    boolean hasXCorridor() {
        if (xCorridor != null) return xCorridor;
        boolean r = computeXCorridor();
        xCorridor = r;
        return r;
    }

    private boolean computeXCorridor() {
        if (!xRailCorridor) return false;
        BuildingInfo i = getXmin();
        int guard = 0;
        while (i.canRailGoThrough() && i.xRailCorridor && guard++ < 512) i = i.getXmin();
        if ((!i.hasBuilding) || i.floorsBelowGround == 0) return false;
        i = getXmax();
        guard = 0;
        while (i.canRailGoThrough() && i.xRailCorridor && guard++ < 512) i = i.getXmax();
        return !((!i.hasBuilding) || i.floorsBelowGround == 0);
    }

    boolean hasZCorridor() {
        if (zCorridor != null) return zCorridor;
        boolean r = computeZCorridor();
        zCorridor = r;
        return r;
    }

    private boolean computeZCorridor() {
        if (!zRailCorridor) return false;
        BuildingInfo i = getZmin();
        int guard = 0;
        while (i.canRailGoThrough() && i.zRailCorridor && guard++ < 512) i = i.getZmin();
        if ((!i.hasBuilding) || i.floorsBelowGround == 0) return false;
        i = getZmax();
        guard = 0;
        while (i.canRailGoThrough() && i.zRailCorridor && guard++ < 512) i = i.getZmax();
        return !((!i.hasBuilding) || i.floorsBelowGround == 0);
    }

    boolean canRailGoThrough() {
        if (!isCity) return false;
        if (!hasBuilding) return true;
        return floorsBelowGround == 0;
    }

    boolean doesRoadExtendTo() {
        boolean b = isCity && !hasBuilding;
        if (b) return !isElevatedParkSection();
        return false;
    }

    static boolean hasRoadConnection(BuildingInfo i1, BuildingInfo i2) {
        if (!i1.doesRoadExtendTo()) return false;
        if (!i2.doesRoadExtendTo()) return false;
        return Math.abs(i1.cityLevel - i2.cityLevel) <= 0;
    }

    int localToGlobal(int l) { return l + cityLevel; }
    int globalToLocal(int l) { return l - cityLevel; }

    boolean hasConnectionAt(int level, Orientation orientation) {
        return orientation == Orientation.X ? hasConnectionAtX(level) : hasConnectionAtZ(level);
    }

    /** Call from the street chunk with the (potential) building as 'adj'. */
    boolean hasFrontPartFrom(BuildingInfo adj) {
        StreetType st = streetType;
        if (isElevatedParkSection()) st = StreetType.PARK;
        if (adj.hasBuilding && adj.frontType != null && st == StreetType.NORMAL && cityLevel < adj.cityLevel + adj.getNumFloors()) {
            RailChunkType type = getRailInfo().type;
            if (type == RailChunkType.STATION_UNDERGROUND) return false;
            if (type == RailChunkType.GOING_DOWN_ONE_FROM_SURFACE) return false;
            if (getMaxHighwayLevel() >= 0) return false;
            int local = adj.globalToLocal(cityLevel);
            if (adj.isValidFloor(local) && adj.getFloor(local).getMetaBoolean("dontconnect")) return false;
        } else {
            return false;
        }
        return true;
    }

    boolean hasConnectionAtX(int level) {
        if (!isCity) return false;
        if (building2x2Section == 1 || building2x2Section == 3) return false;
        if (level < 0 || level >= connectionAtX.length) return false;
        if (level < floorTypes.length && floorTypes[level].getMetaBoolean("dontconnect")) return false;
        if (getXmin().hasFrontPartFrom(this)) return true;
        return connectionAtX[level];
    }

    boolean hasConnectionAtXFromStreet(int level) {
        if (!isCity) return false;
        if (building2x2Section == 1 || building2x2Section == 3) return false;
        if (level < 0 || level >= connectionAtX.length) return false;
        if (hasFrontPartFrom(getXmin())) return true;
        return connectionAtX[level];
    }

    boolean hasConnectionAtZ(int level) {
        if (!isCity) return false;
        if (building2x2Section == 2 || building2x2Section == 3) return false;
        if (level < 0 || level >= connectionAtZ.length) return false;
        if (level < floorTypes.length && floorTypes[level].getMetaBoolean("dontconnect")) return false;
        if (getZmin().hasFrontPartFrom(this)) return true;
        return connectionAtZ[level];
    }

    boolean hasConnectionAtZFromStreet(int level) {
        if (!isCity) return false;
        if (building2x2Section == 2 || building2x2Section == 3) return false;
        if (level < 0 || level >= connectionAtZ.length) return false;
        if (hasFrontPartFrom(getZmin())) return true;
        return connectionAtZ[level];
    }
}
