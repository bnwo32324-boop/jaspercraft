package chat.jaspr.dungeon;

import chat.jaspr.dungeon.EncounterCatalog.Species;

/**
 * Generation 7 (owner 2026-10-05): the ported Mutant Creatures mobs should be "prevalent throughout the entire dungeon
 * experience, and ... progressively harder as well", some of them as bosses. Pure: which slots of a room become mutants
 * (deterministic from the room's hash and the slot), how strong they are, and which boss rooms crown a mutant.
 *
 * Floor I keeps them rare and small near the gate: creeper minions from the first parcel out, the odd mutant zombie deeper.
 * Floor II brings mutant skeletons and creepers, Floor III the mutant endermen. A room holds at most a few, more below; the
 * chance and their health grow with the floor and with the way to its Descent (Floors.depth). The mod's own attributes
 * (MBConfig defaults) are the base: zombie 150, skeleton 150, creeper 150, enderman 200, spider pig 40, minion 4 health.
 */
public final class MutantPresence {
    private MutantPresence() {}
    static long roll(Layout.Room r, long salt) { return Layout.mix(r.hash ^ salt); }
    /** At most this many mutants among a room's ordinary slots. */
    public static int cap(int floor) { return floor == 3 ? 5 : floor == 2 ? 3 : 1; }
    /** In a thousand: the chance that an ordinary slot is a mutant. */
    public static int chanceIn1000(Layout.Room r) {
        double d = Floors.depth(r);
        if (r.floor == 3) return (int) Math.round(120 + 120 * d);
        if (r.floor == 2) return (int) Math.round(80 + 100 * d);
        return d < 1 / 3.0 ? 0 : (int) Math.round(30 + 60 * d);
    }
    /** The mutant an ordinary slot becomes, or null. The first slots decide first, so the cap keeps the count bounded. */
    public static Species ordinary(Layout.Room r, int slot) {
        if (r.kind == Layout.Kind.REFUGE || r.finale() || r.bossSlot(slot)) return null;
        int n = 0;
        for (int s = r.bossRoom() ? 1 : 0; s <= slot; s++) {
            if (Math.floorMod(roll(r, 0x4d7574616e74L + 31L * s), 1000) >= chanceIn1000(r)) continue;
            if (++n > cap(r.floor)) return null;
            if (s == slot) return kind(r, s);
        }
        return null;
    }
    private static Species kind(Layout.Room r, int slot) {
        int p = (int) Math.floorMod(roll(r, 0x4b696e64L + 131L * slot), 100);
        if (r.floor == 3) return p < 30 ? Species.MUTANT_ENDERMAN : p < 55 ? Species.MUTANT_ZOMBIE : p < 75 ? Species.MUTANT_CREEPER : Species.MUTANT_SKELETON;
        if (r.floor == 2) return p < 35 ? Species.MUTANT_SKELETON : p < 60 ? Species.MUTANT_CREEPER : p < 90 ? Species.CREEPER_MINION : Species.SPIDER_PIG;
        return p < 75 || Floors.depth(r) < 2 / 3.0 ? Species.CREEPER_MINION : Species.MUTANT_ZOMBIE;
    }
    /** In a thousand: the chance that a boss room's boss is a mutant whatever its theme (the floors also name mutant bosses). */
    public static int bossChanceIn1000(int floor) { return floor == 3 ? 250 : floor == 2 ? 150 : 50; }
    /** The mutant boss of a boss room, or null. Never for the Descent or the Throne (their guardians are the floors' own). */
    public static Species boss(Layout.Room r) {
        if (r.kind != Layout.Kind.BOSS) return null;
        if (Math.floorMod(roll(r, 0x426f73734dL), 1000) >= bossChanceIn1000(r.floor)) return null;
        int p = (int) Math.floorMod(roll(r, 0x426f73734bL), 100);
        if (r.floor == 3) return p < 45 ? Species.MUTANT_ENDERMAN : p < 75 ? Species.MUTANT_ZOMBIE : Species.MUTANT_CREEPER;
        if (r.floor == 2) return p < 45 ? Species.MUTANT_SKELETON : p < 80 ? Species.MUTANT_CREEPER : Species.MUTANT_ZOMBIE;
        return Species.MUTANT_ZOMBIE;
    }
    /** The mod's own maximum health (MBConfig defaults). */
    public static double baseHealth(Species s) {
        switch (s) {
            case MUTANT_ENDERMAN: return 200;
            case MUTANT_ZOMBIE: case MUTANT_SKELETON: case MUTANT_CREEPER: return 150;
            case MUTANT_SNOW_GOLEM: return 80;
            case SPIDER_PIG: return 40;
            case CREEPER_MINION: return 4;
            default: return 20;
        }
    }
    /** A mutant's health in this room: the mod's own, gentler on Floor I, stronger below and toward the Descent; bosses far more. */
    public static double health(Species s, Layout.Room r, boolean boss) {
        double floor = r.floor == 3 ? 2.0 : r.floor == 2 ? 1.3 : .7;
        double h = baseHealth(s) * floor * (1 + .3 * Floors.depth(r));
        if (boss) h *= 2.5 + .5 * r.floor;
        return Math.max(4, Math.round(h));
    }
}
