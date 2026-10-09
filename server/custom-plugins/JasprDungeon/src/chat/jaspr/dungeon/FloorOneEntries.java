package chat.jaspr.dungeon;

import chat.jaspr.dungeon.EncounterCatalog.Shape;
import chat.jaspr.dungeon.EncounterCatalog.Species;
import chat.jaspr.dungeon.EncounterCatalog.Status;
import chat.jaspr.dungeon.HazardCatalog.Type;

/**
 * Generation 7: the encounter entries and favoured dangers of Floor I's eighteen new themes (36..53). Pure data.
 *
 * Every entry has its own boss (original names and identities), a pool of four species that mixes the vanilla mobs with Floor I's
 * custom species (Bestiary dresses them), the evokers, vexes and zombie villagers the framework added, and, in three late-feeling
 * themes, the mildest of the ported mutants. Each signature pattern is one of the House's existing shapes, so the telegraph a
 * player already knows still reads; no two neighbouring themes use the same one and the eighteen are all different. Windups,
 * cooldowns and statuses stay inside Floor I's range (windup 36-60 ticks, cooldown 140 up, status at most three seconds, a dodge
 * window of at least one second between pulses), which EncounterCatalog's tier scaling then tightens.
 */
final class FloorOneEntries {
    private FloorOneEntries() {}
    private static final Type VENTS = Type.FLAME_VENTS, DARTS = Type.DART_SLITS, RUNES = Type.SPIKE_RUNES, MASONRY = Type.FALLING_MASONRY,
        MIASMA = Type.MIASMA, FROST = Type.FROST_GUSTS, SMITE = Type.SMITE, SHOCK = Type.SHOCKWAVE, BLADES = Type.PHANTOM_BLADES,
        RAIN = Type.POTION_RAIN, WELL = Type.GRAVITY_WELL, DARK = Type.CREEPING_DARK, SPORES = Type.BLAST_SPORES, EMBERS = Type.EMBER_BOLTS;

    /** Three dangers that suit each new theme (at least one ordinary, at most one seizing), in theme order. */
    private static final Type[][] FAVOURED = {
        {EMBERS, MASONRY, SHOCK},      // 36 Bellfounder's Crypt: sparks from the casting, falling lintels, a bell's shockwave
        {MIASMA, RAIN, DARK},          // 37 Moth Sanctum: wing dust, scale rain, the dark that draws them
        {EMBERS, DARTS, SPORES},       // 38 Candlewright Hall: guttering sparks, wick darts, wax-pod bursts
        {RUNES, DARTS, WELL},          // 39 Chained Library: warding runes, trap darts, the chains' pull
        {MIASMA, RAIN, FROST},         // 40 Penitent Bathhouse: steam, scalding rain, the cold plunge
        {MASONRY, SMITE, BLADES},      // 41 Hall of Effigies: toppling stone, judgment bolts, sculptors' blades
        {DARTS, BLADES, MIASMA},       // 42 Grave Market: stall traps, cut-throat blades, grave air
        {RAIN, SPORES, MASONRY},       // 43 Weeping Orchard: dripping sap, rot pods, falling boughs
        {RUNES, MASONRY, EMBERS},      // 44 Rusted Reliquary: seals, rotten stone, sparks off the rust
        {SMITE, EMBERS, DARK},         // 45 Lamplighter's Rest: lamp bolts, spilt oil, the lamps put out
        {VENTS, MIASMA, DARTS},        // 46 Ashen Kitchens: oven flame, smoke, cleaver slits
        {BLADES, DARTS, DARK},         // 47 Mute Theatre: prop swords, rigging darts, the house lights out
        {RAIN, MIASMA, MASONRY},       // 48 Gutter Abbey: sewage drip, foul air, loose coping
        {MIASMA, EMBERS, SHOCK},       // 49 Thurible Gallery: incense, coals, a swung censer's shock
        {SPORES, MASONRY, FROST},      // 50 Hanging Gardens of Mercy: seed pods, falling planters, frost on the leaves
        {DARTS, MIASMA, DARK},         // 51 Pilgrim's Hostel: bed-frame darts, damp air, the lamp blown out
        {BLADES, MASONRY, RUNES},      // 52 Sexton's Workshop: shovels, coffin lids, grave-sigils
        {DARK, SMITE, VENTS}           // 53 The Unlit Nave: the dark, a lone bolt of light, cold braziers
    };
    static Type[] favoured(int theme) { int i = theme - FloorOneExtra.BASE; return i >= 0 && i < FloorOneExtra.COUNT ? FAVOURED[i] : null; }

    private static Species[] pool(Species... s) { return s; }
    private static EncounterCatalog.Entry e(int theme, String boss, Species bossSpecies, Species[] pool, Shape shape, Status status, int statusTicks,
                                            boolean centred, int windup, int cooldown, int pulses, int pulseTicks, String cue) {
        return new EncounterCatalog.Entry(theme, Layout.THEMES[theme], boss, bossSpecies, pool, shape, status, statusTicks, centred, windup, cooldown, pulses, pulseTicks, cue);
    }
    private static EncounterCatalog.Entry[] build() {
        int b = FloorOneExtra.BASE;
        return new EncounterCatalog.Entry[]{
            e(b, "The Bellwright", Species.VINDICATOR, pool(Species.VINDICATOR, Species.MAGMA_CUBE, Species.GRAVEBOUND_KNIGHT, Species.CHOIR_BANSHEE),
                Shape.FOUNDRY_PISTONS, Status.SLOW_DIGGING, 40, true, 50, 205, 3, 38, "Bell hammers: staggered strikes swing side to side down the aisle; stand in the unmarked lane!"),
            e(b + 1, "Mother of Moths", Species.CHOIR_BANSHEE, pool(Species.CHOIR_BANSHEE, Species.CANDLE_WISP, Species.VEX, Species.SILVERFISH),
                Shape.CARRION_ORBIT, Status.BLINDNESS, 20, false, 54, 215, 3, 40, "Moth wings: three curved wings circle a calm centre; hold the centre or slip between the wings!"),
            e(b + 2, "The Wick Warden", Species.STRAY, pool(Species.STRAY, Species.CANDLE_WISP, Species.SLIME, Species.HUSK),
                Shape.PLAGUE_PATCHES, Status.SLOW, 40, false, 44, 175, 1, 0, "Wax pools: three molten puddles gather around you; step out of every marked pool!"),
            e(b + 3, "The Chained Archivist", Species.GRAVEBOUND_KNIGHT, pool(Species.GRAVEBOUND_KNIGHT, Species.SKELETON, Species.EVOKER, Species.SILVERFISH),
                Shape.IRON_JAWS, Status.SLOW, 40, true, 52, 200, 3, 38, "Chain jaws: two chained bars close inward in three steps; follow the narrowing gap or step past the ends!"),
            e(b + 4, "The Scalding Penitent", Species.FLAGELLANT, pool(Species.FLAGELLANT, Species.MIRE_LEECH, Species.SLIME, Species.ZOMBIE_VILLAGER),
                Shape.DROWNING_RING, Status.WEAKNESS, 40, false, 42, 165, 1, 0, "Scalding steam: a ring of steam bursts around you; the centre and the outer shore are safe!"),
            e(b + 5, "The Stonewright", Species.EVOKER, pool(Species.EVOKER, Species.GRAVEBOUND_KNIGHT, Species.VEX, Species.WITHER_SKELETON),
                Shape.THORN_CROWN, Status.SLOW, 50, false, 46, 175, 1, 0, "Stone crown: a ring of carved spikes rises with four gaps; use a gap or stay inside the ring!"),
            e(b + 6, "Pedlar of Last Things", Species.WITCH, pool(Species.WITCH, Species.CREEPER_MINION, Species.ZOMBIE_VILLAGER, Species.SPIDER),
                Shape.PAUPER_SCALES, Status.HUNGER, 40, false, 52, 200, 2, 38, "Pedlar's scales: two pans trade weight; leave both marked pans and cross the clear centre!"),
            e(b + 7, "The Withered Orchardist", Species.ZOMBIE, pool(Species.ZOMBIE, Species.HUSK, Species.CAVE_SPIDER, Species.CHOIR_BANSHEE),
                Shape.ROOT_FORK, Status.SLOW, 50, true, 44, 170, 1, 0, "Dead roots: two branches fork from the orchardist; keep between or outside the branches!"),
            e(b + 8, "The Rust Reliquarian", Species.WITHER_SKELETON, pool(Species.WITHER_SKELETON, Species.OSSUARY_CRAWLER, Species.SHULKER, Species.VINDICATOR),
                Shape.BASILICA_CHECKER, Status.WEAKNESS, 36, false, 54, 210, 2, 40, "Rotten flagstones: alternating squares collapse; stand on an unmarked square, then switch to a spent one!"),
            e(b + 9, "The Last Lamplighter", Species.SKELETON, pool(Species.SKELETON, Species.CANDLE_WISP, Species.ZOMBIE, Species.SPIDER),
                Shape.STARLESS_FALL, Status.BLINDNESS, 20, false, 44, 170, 1, 0, "Falling lamps: six small discs mark where lamps will drop; leave every marked disc!"),
            e(b + 10, "The Cinder Cook", Species.PIG_ZOMBIE, pool(Species.PIG_ZOMBIE, Species.CREEPER_MINION, Species.BLAZE, Species.ZOMBIE),
                Shape.FURNACE_FAN, Status.HUNGER, 40, true, 46, 165, 1, 0, "Oven blast: a wide fan of flame bursts from the cook; get beside or behind the marked fan!"),
            e(b + 11, "The Silent Prompter", Species.ENDERMAN, pool(Species.ENDERMAN, Species.ENDERMITE, Species.SKELETON, Species.CANDLE_WISP),
                Shape.VELVET_SEAM, Status.WEAKNESS, 36, false, 52, 195, 2, 36, "Curtain seam: a stitched curtain edge sweeps the stage and reverses its bends; follow each fresh warning!"),
            e(b + 12, "The Gutter Abbot", Species.SLIME, pool(Species.SLIME, Species.MIRE_LEECH, Species.CAVE_SPIDER, Species.ZOMBIE),
                Shape.SALT_SWEEP, Status.HUNGER, 40, true, 50, 200, 3, 38, "Gutter flood: three bands of filth sweep away from the abbot; step back into each spent gap!"),
            e(b + 13, "The Censer Bearer", Species.BLAZE, pool(Species.BLAZE, Species.CANDLE_WISP, Species.WITCH, Species.WITHER_SKELETON),
                Shape.HOLLOW_HALO, Status.BLINDNESS, 20, true, 46, 175, 1, 0, "Swinging censer: a ring of smoke circles the bearer; close in tight or stay beyond the ring!"),
            e(b + 14, "The Hanging Gardener", Species.SPIDER, pool(Species.SPIDER, Species.CAVE_SPIDER, Species.MIRE_LEECH, Species.VEX),
                Shape.FUNGAL_BLOOM, Status.HUNGER, 32, false, 52, 215, 3, 38, "Garden bloom: four blossoms swell outward in three steps; keep to the diagonal gaps!"),
            e(b + 15, "The Sleepless Innkeeper", Species.ZOMBIE_VILLAGER, pool(Species.ZOMBIE_VILLAGER, Species.ZOMBIE, Species.FLAGELLANT, Species.STRAY),
                Shape.CRADLE_PAIR, Status.WEAKNESS, 50, false, 40, 155, 1, 0, "Twin bunks: two marked beds slam down either side of you; keep between the two discs!"),
            e(b + 16, "The Sexton", Species.HUSK, pool(Species.HUSK, Species.SKELETON, Species.OSSUARY_CRAWLER, Species.ZOMBIE_VILLAGER),
                Shape.TEAR_DROP, Status.SLOW, 40, false, 38, 150, 1, 0, "Open grave: a marked disc caves in under the sexton's spade; leave the marked disc!"),
            e(b + 17, "The Unlit Shepherd", Species.MUTANT_ZOMBIE, pool(Species.MUTANT_ZOMBIE, Species.FLAGELLANT, Species.CHOIR_BANSHEE, Species.WITHER_SKELETON),
                Shape.MOURNING_MAZE, Status.SLOW, 32, false, 56, 220, 3, 42, "Dark procession: square walls contract in the dark as their gate turns; follow the gap or escape outside!")
        };
    }
    /** Built on first use (not while the catalogues initialise each other). */
    private static final class Holder { static final EncounterCatalog.Entry[] ENTRIES = build(); }
    static EncounterCatalog.Entry entry(int theme) {
        int i = theme - FloorOneExtra.BASE;
        return i >= 0 && i < FloorOneExtra.COUNT ? Holder.ENTRIES[i] : null;
    }
}
