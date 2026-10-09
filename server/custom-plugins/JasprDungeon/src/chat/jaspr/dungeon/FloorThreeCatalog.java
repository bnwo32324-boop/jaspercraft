package chat.jaspr.dungeon;

import chat.jaspr.dungeon.EncounterCatalog.Shape;
import chat.jaspr.dungeon.EncounterCatalog.Species;
import chat.jaspr.dungeon.EncounterCatalog.Status;
import chat.jaspr.dungeon.HazardCatalog.Type;

/**
 * Generation 7, Floor III (The Abyssal Citadel): its 24 encounters and favoured dangers. Pure.
 *
 * Owner 2026-10-05: "I want some bosses to be legitimately incredibly powerful"; "some enemies to be equipped with guns that
 * actively shoot at you" (the gunners lead the barracks, the docks, the bastion and the throne approach); mutants
 * "prevalent throughout the entire dungeon experience ... progressively harder" (seven of the 24 bosses are mutants and
 * mutants walk in eight pools). Each boss keeps one of the 36 telegraphed signature patterns (no two neighbouring themes
 * alike, all 24 different) with its pulse count, but it winds up faster, rests less and leaves longer, harder statuses than
 * on Floor I, within the bounds EncounterAuditTest holds every entry to.
 */
final class FloorThreeCatalog {
    private FloorThreeCatalog() {}
    static final int BASE = Floors.FLOOR_THREE_BASE, COUNT = Floors.FLOOR_THREE_THEMES;
    private static final Type VENTS = Type.FLAME_VENTS, DARTS = Type.DART_SLITS, RUNES = Type.SPIKE_RUNES, MASONRY = Type.FALLING_MASONRY,
        MIASMA = Type.MIASMA, FROST = Type.FROST_GUSTS, SMITE = Type.SMITE, SHOCK = Type.SHOCKWAVE, BLADES = Type.PHANTOM_BLADES,
        RAIN = Type.POTION_RAIN, WELL = Type.GRAVITY_WELL, DARK = Type.CREEPING_DARK, SPORES = Type.BLAST_SPORES, EMBERS = Type.EMBER_BOLTS;
    /** Three dangers per theme (theme order 78..101), at most one of them seizing. */
    static final Type[][] FAVOURED = {
        {VENTS, EMBERS, MASONRY}, {DARTS, BLADES, SMITE}, {DARTS, RUNES, SHOCK}, {WELL, RAIN, SPORES}, {SMITE, MASONRY, DARK},
        {EMBERS, SMITE, MIASMA}, {BLADES, DARTS, SHOCK}, {VENTS, MIASMA, EMBERS}, {MASONRY, SMITE, FROST}, {RUNES, BLADES, SHOCK},
        {DARTS, MASONRY, DARK}, {EMBERS, VENTS, SPORES}, {BLADES, RAIN, WELL}, {DARTS, RUNES, EMBERS}, {VENTS, SPORES, SHOCK},
        {BLADES, SMITE, DARK}, {MIASMA, MASONRY, DARK}, {WELL, SMITE, RUNES}, {VENTS, EMBERS, SMITE}, {SMITE, MASONRY, RAIN},
        {MIASMA, BLADES, DARK}, {VENTS, RUNES, EMBERS}, {DARTS, RAIN, SPORES}, {DARTS, EMBERS, SHOCK}
    };
    static Type[] favoured(int theme) {
        int i = theme - BASE;
        return i < 0 || i >= COUNT ? null : FAVOURED[i].clone();
    }

    private static Species[] pool(Species... s) { return s; }
    /** Built on first use (after EncounterCatalog's own 36, whose pulse counts each reused pattern keeps). */
    private static final class Holder {
        static final EncounterCatalog.Entry[] ENTRIES = {
            make(78, "Vulkhar, Bridgewright of Ruin", Species.HELLFORGED_SENTINEL,
                pool(Species.HELLFORGED_SENTINEL, Species.BLAZE, Species.WITHER_SKELETON, Species.MAGMA_CUBE),
                Shape.FOUNDRY_PISTONS, Status.SLOW_DIGGING, 60, "Bridgeforge hammers: the anvils fall lane by lane; step to the cold lane!"),
            make(79, "Marshal Kethrian, Warden of the Last Stair", Species.SQUAD_CAPTAIN,
                pool(Species.ABYSSAL_GUNSLINGER, Species.BLOOD_TEMPLAR, Species.HELLFORGED_SENTINEL, Species.WITHER_SKELETON),
                Shape.COURT_LUNGE, Status.WEAKNESS, 60, "The Marshal's lance: one long straight thrust down the approach; sidestep the marked line!"),
            make(80, "Sanguis, the Wound That Never Closes", Species.BLOOD_TEMPLAR,
                pool(Species.BLOOD_TEMPLAR, Species.ABYSSAL_GUNSLINGER, Species.VINDICATOR, Species.MUTANT_ZOMBIE),
                Shape.SANGUINE_HOURGLASS, Status.WEAKNESS, 60, "The wound opens: opposite wedges fill with blood, then the sides; switch axes!"),
            make(81, "The Gardener Who Eats the Stars", Species.MUTANT_ENDERMAN,
                pool(Species.VOID_WRAITH, Species.ENDERMITE, Species.SHULKER, Species.ENDERMAN),
                Shape.FUNGAL_BLOOM, Status.BLINDNESS, 30, "Starflowers bloom outward in three steps; keep to the diagonal gaps!"),
            make(82, "Ozrath, Saint of the Black Spire", Species.WITHER_SKELETON,
                pool(Species.WITHER_SKELETON, Species.VOID_WRAITH, Species.EVOKER, Species.STRAY),
                Shape.OBSIDIAN_SCISSORS, Status.SLOW, 60, "The black shears of the spire close on the forward axis; get behind the hinge!"),
            make(83, "Prelate Ashvane, Who Sings the Dead Awake", Species.DOOM_HERALD,
                pool(Species.VEX, Species.WITCH, Species.STRAY, Species.BLOOD_TEMPLAR, Species.MUTANT_SKELETON),
                Shape.SILENT_ECHO, Status.BLINDNESS, 40, "Soulfire hymn: three waves of pale flame expand; step behind each spent wave!"),
            make(84, "The Fettered Colossus", Species.MUTANT_ZOMBIE,
                pool(Species.PIG_ZOMBIE, Species.HUSK, Species.VINDICATOR, Species.MUTANT_ZOMBIE),
                Shape.IRON_JAWS, Status.SLOW, 60, "The Colossus hauls its chains: two bars close in three steps; slip through the gap!"),
            make(85, "Infernax, the Furnace That Hungers", Species.BLAZE,
                pool(Species.BLAZE, Species.MAGMA_CUBE, Species.HELLFORGED_SENTINEL, Species.MUTANT_CREEPER),
                Shape.FURNACE_FAN, Status.HUNGER, 60, "The furnace door swings open: a fan of soulfire; get beside or behind it!"),
            make(86, "Caelum, the Sky That Fell", Species.VOID_WRAITH,
                pool(Species.STRAY, Species.VEX, Species.SHULKER, Species.ABYSSAL_GUNSLINGER),
                Shape.STARLESS_FALL, Status.SLOW, 50, "The sky breaks apart: six shards fall on the marked discs; leave them now!"),
            make(87, "Ossuarch, Champion of Ten Thousand Deaths", Species.MUTANT_SKELETON,
                pool(Species.SKELETON, Species.WITHER_SKELETON, Species.HUSK, Species.MUTANT_SKELETON, Species.BLOOD_TEMPLAR),
                Shape.BASILICA_CHECKER, Status.SLOW_DIGGING, 60, "The arena floor turns: alternating squares strike; move to a spent square!"),
            make(88, "Mother Noose, Hangwoman of the Keep", Species.WITCH,
                pool(Species.VINDICATOR, Species.ZOMBIE_VILLAGER, Species.STRAY, Species.ABYSSAL_GUNSLINGER),
                Shape.GALLOWS_BAR, Status.BLINDNESS, 40, "The trapdoor beam swings across: step forward or back out of the band!"),
            make(89, "Varrow Blackwake, Corsair of the Burning Tide", Species.ABYSSAL_GUNSLINGER,
                pool(Species.ABYSSAL_GUNSLINGER, Species.SQUAD_CAPTAIN, Species.PIG_ZOMBIE, Species.MAGMA_CUBE),
                Shape.RELIQUARY_TIDES, Status.SLOW, 50, "The burning tide: the outer ring breaks first, then the inner; time your step!"),
            make(90, "The Librarian of Unwritten Ends", Species.EVOKER,
                pool(Species.VOID_WRAITH, Species.ENDERMITE, Species.SHULKER, Species.DOOM_HERALD),
                Shape.SCRIPTORIUM_GLYPHS, Status.WEAKNESS, 60, "Unwritten glyphs turn around the open centre; leave the marked corners!"),
            make(91, "Drillmaster Vorne of the Crimson Hundred", Species.SQUAD_CAPTAIN,
                pool(Species.ABYSSAL_GUNSLINGER, Species.BLOOD_TEMPLAR, Species.VINDICATOR, Species.PIG_ZOMBIE, Species.MUTANT_ZOMBIE),
                Shape.PALE_HOOFPRINTS, Status.WEAKNESS, 50, "The Crimson Hundred march: paired boots stamp forward; step aside!"),
            make(92, "The Doomforge Engine", Species.MUTANT_CREEPER,
                pool(Species.HELLFORGED_SENTINEL, Species.BLAZE, Species.MUTANT_CREEPER, Species.WITHER_SKELETON),
                Shape.SALT_SWEEP, Status.SLOW_DIGGING, 60, "Doomforge slag: three molten bands roll away from the engine; get out sideways!"),
            make(93, "Thousandface, Who Wears Your Reflection", Species.VINDICATOR,
                pool(Species.VINDICATOR, Species.VOID_WRAITH, Species.EVOKER, Species.ENDERMAN),
                Shape.OPAL_PRISM, Status.BLINDNESS, 30, "A thousand reflections: the mirrored triangle contracts; cross a spent edge!"),
            make(94, "Cinerex, the Ash-Crowned King", Species.WITHER_SKELETON,
                pool(Species.WITHER_SKELETON, Species.HUSK, Species.DOOM_HERALD, Species.MUTANT_SKELETON),
                Shape.THORN_CROWN, Status.WEAKNESS, 60, "The crown of ash ignites: use the four gaps or stand inside the ring!"),
            make(95, "Ammun-Vael, Mouth of the Abyss", Species.MUTANT_ENDERMAN,
                pool(Species.VOID_WRAITH, Species.MUTANT_ENDERMAN, Species.ENDERMITE, Species.DOOM_HERALD),
                Shape.MOURNING_MAZE, Status.SLOW, 60, "The Abyss Gate grinds shut: square walls close as the gate turns; follow the gap!"),
            make(96, "Regent Pyrrhos, the King Who Will Not Stop Burning", Species.MAGMA_CUBE,
                pool(Species.BLAZE, Species.WITHER_SKELETON, Species.HUSK, Species.MAGMA_CUBE),
                Shape.WAX_SPIRAL, Status.HUNGER, 60, "The pyre spirals: three curved ribbons of flame turn; cross into the unburnt gaps!"),
            make(97, "Astrologer Vey, Who Counts the Falling Stars", Species.ENDERMAN,
                pool(Species.SHULKER, Species.STRAY, Species.VOID_WRAITH, Species.ENDERMITE),
                Shape.ASTRAL_COMET, Status.BLINDNESS, 30, "A falling star sweeps its bowed tail three times; cross behind the curve!"),
            make(98, "Morrowgast, the Grave-Titan", Species.MUTANT_ZOMBIE,
                pool(Species.HUSK, Species.ZOMBIE_VILLAGER, Species.VOID_WRAITH, Species.MUTANT_SKELETON),
                Shape.CARRION_ORBIT, Status.SLOW, 60, "Wraith wings circle the grave-titan; keep to the centre or the gaps between them!"),
            make(99, "Saal, the Molten Reliquant", Species.HELLFORGED_SENTINEL,
                pool(Species.HELLFORGED_SENTINEL, Species.MAGMA_CUBE, Species.BLAZE, Species.PIG_ZOMBIE),
                Shape.AMBER_LATTICE, Status.SLOW_DIGGING, 50, "Molten lattice: the glowing seams shift half a cell; move between the clear diamonds!"),
            make(100, "Avarix, the Hoard That Bursts", Species.MUTANT_CREEPER,
                pool(Species.PIG_ZOMBIE, Species.VINDICATOR, Species.ZOMBIE_VILLAGER, Species.ABYSSAL_GUNSLINGER),
                Shape.PAUPER_SCALES, Status.HUNGER, 60, "The hoard's scales tip: two pans trade their weight; cross the clear centre!"),
            make(101, "Castellan Graveheart, Last Gun of the Bastion", Species.ABYSSAL_GUNSLINGER,
                pool(Species.ABYSSAL_GUNSLINGER, Species.SQUAD_CAPTAIN, Species.HELLFORGED_SENTINEL, Species.MUTANT_SKELETON, Species.BLOOD_TEMPLAR),
                Shape.LAST_ABSOLUTION, Status.WEAKNESS, 60, "Last stand: a clear island circles inside the killing ground; follow it or leave!")
        };
    }
    /**
     * A Floor III entry on one of the 36 patterns: the pattern keeps its pulse count and whether it centres on the boss (its
     * geometry depends on both); the boss winds up 8 ticks sooner (never under 36), rests 30 ticks less (never under 140)
     * and pauses 6 ticks less between pulses (never under 28).
     */
    private static EncounterCatalog.Entry make(int theme, String boss, Species species, Species[] pool, Shape shape, Status status, int statusTicks, String cue) {
        EncounterCatalog.Entry like = EncounterCatalog.entry(shape.ordinal());
        int pulse = like.pulses > 1 ? Math.max(28, like.pulseTicks - 6) : like.pulseTicks;
        return new EncounterCatalog.Entry(theme, Layout.THEMES[theme], boss, species, pool, shape, status, statusTicks, like.bossCentered,
            Math.max(36, like.windupTicks - 8), Math.max(140, like.cooldownTicks - 30), like.pulses, pulse, cue);
    }
    static EncounterCatalog.Entry entry(int theme) {
        int i = theme - BASE;
        return i < 0 || i >= COUNT ? null : Holder.ENTRIES[i];
    }
}
