package chat.jaspr.gear;

import java.util.Locale;
import org.bukkit.ChatColor;
import org.bukkit.Material;

/**
 * The six realm armouries (owner, 2026-10-02: "every dimension should have an armor set that's unique to that dimension
 * and that is stronger than diamond ... The armor should have accompanying tools and weapons as well"). Each set is four
 * armour pieces, a sword, an axe, a pickaxe, a shovel and a hoe (ArmoryPiece), all equally strong; what differs is the
 * set's own powers and where it comes from. A set is at home in its realm: there its weapons hit 15% harder and each of
 * its armour pieces turns aside 3% of every blow.
 *
 * Emerald pieces are made entirely of emerald blocks, in the vanilla shapes. Every other piece is its diamond counterpart
 * forged with eight of the realm's own materials (corners and edges of the grid, the diamond piece in the middle), so a
 * realm piece always costs at least as much as diamond. Abyssal Pearls, Titan Shards and Liminal Fragments exist only in
 * their realm; blaze rods and magma cream come only from the Nether, shulker shells and chorus only from the End.
 */
enum ArmorySet {
    EMERALD("emerald", "Emerald", ChatColor.GREEN, "world", "the Overworld",
        null, null, null,
        "Full set - Prosperity: +2 hearts and +2 luck",
        "Bounty: kills give 30% more experience and may drop an emerald",
        "Prospector: 20% chance of an extra drop from ores"),
    BLAZEFORGED("blazeforged", "Blazeforged", ChatColor.GOLD, "world_nether", "the Nether",
        Material.BLAZE_ROD, Material.MAGMA_CREAM, null,
        "Full set - Fireproof: no harm from fire, lava or magma",
        "Searing: sets foes alight, +20% against burning foes",
        "Smelting: iron and gold ore drop ingots, sand drops glass"),
    ABYSSAL("abyssal", "Abyssal", ChatColor.DARK_AQUA, "jaspr_ruins", "Drownhollow",
        Material.PRISMARINE_CRYSTALS, Material.PRISMARINE_CRYSTALS, "abyssal_pearl",
        "Full set - Deepbreath: you never drown, the Dread cannot sicken you",
        "Undertow: hits slow foes, +25% against foes in water",
        "Tidal: digging underwater refills your air"),
    TITAN("titan", "Titan", ChatColor.YELLOW, "jaspr_atlas", "Atlas",
        Material.QUARTZ, Material.QUARTZ, "titan_shard",
        "Full set - Colossus: +50% knockback resistance, half fall damage",
        "Sunder: +25% against armoured foes (10+ armour); hits stagger",
        "Titan's Strength: veins of ore, whole trees, 3x3 digging while sneaking"),
    LIMINAL("liminal", "Liminal", ChatColor.WHITE, "jaspr_levels", "the Backrooms",
        Material.GLOWSTONE_DUST, Material.GLOWSTONE_DUST, "liminal_fragment",
        "Full set - Lucid: no blindness or nausea, +10% speed",
        "Disorient: may blind foes, +25% against foes in the dark",
        "Pathfinder: mining in the dark sets a torch from your pack"),
    VOID("void", "Void", ChatColor.DARK_PURPLE, "world_the_end", "the End",
        Material.SHULKER_SHELL, Material.CHORUS_FRUIT_POPPED, null,
        "Full set - Ender Step: no levitation, no pearl harm, saved once from the void",
        "Voidstrike: 15% chance of a strike for +60%",
        "Void Pull: drops and experience go straight to you");

    final String id, title, world, realmTitle, materialId, setBonus, weaponPower, toolPower;
    final ChatColor color;
    /** Forge recipe: corner and edge ingredients around the diamond piece (null for the emerald set). */
    final Material corner, edge;

    ArmorySet(String id, String title, ChatColor color, String world, String realmTitle, Material corner, Material edge,
              String materialId, String setBonus, String weaponPower, String toolPower) {
        this.id = id; this.title = title; this.color = color; this.world = world; this.realmTitle = realmTitle;
        this.corner = corner; this.edge = edge; this.materialId = materialId;
        this.setBonus = setBonus; this.weaponPower = weaponPower; this.toolPower = toolPower;
    }

    /** Model (damage) value of this set's pieces on every diamond base item; one free value above each as the fallback. */
    int model() { return 100 + 2 * ordinal(); }

    /** True when the forge recipe asks for this realm's own crafting material (an NBT-marked stand-in). */
    boolean customMaterial() { return materialId != null; }

    /** The realm this world belongs to; the old liminal world counts as the Backrooms. Null outside every realm. */
    static ArmorySet ofWorld(String world) {
        if (world == null) return null;
        if (world.equals("jaspr_backrooms")) return LIMINAL;
        for (ArmorySet s : values()) if (s.world.equals(world)) return s;
        return null;
    }

    static ArmorySet byId(String id) {
        if (id == null) return null;
        String key = id.toLowerCase(Locale.ROOT);
        for (ArmorySet s : values()) if (s.id.equals(key)) return s;
        return null;
    }
}
