package chat.jaspr.gear;

import java.util.Locale;
import org.bukkit.Material;

/**
 * The nine pieces of every realm armoury, each on its diamond counterpart (so enchanting, the Armaments upgrade system
 * and every other diamond-aware rule treat it as the diamond item it outranks). Stats are the same for all six sets:
 *
 *   armour  helmet 3, chestplate 9, leggings 7, boots 3 (diamond: 3/8/6/3) with toughness 3 each (diamond 2) and
 *           +5% knockback resistance each: 22 armour, 12 toughness, 20% knockback resistance for a full set;
 *   weapons sword 8 damage at 1.6 (diamond 7), axe 10 at 1.0 (diamond 9);
 *   tools   pickaxe 6 at 1.2, shovel 6.5 at 1.0, hoe 2 at 4.0 (diamond 5, 5.5, 1), mining at diamond speed.
 *
 * Every piece is unbreakable: the item's damage value only picks its model (ArmorySet.model()).
 */
enum ArmoryPiece {
    HELMET("helmet", "Helmet", Material.DIAMOND_HELMET, "head", 3, new String[]{"XXX", "X X"}),
    CHESTPLATE("chestplate", "Chestplate", Material.DIAMOND_CHESTPLATE, "chest", 9, new String[]{"X X", "XXX", "XXX"}),
    LEGGINGS("leggings", "Leggings", Material.DIAMOND_LEGGINGS, "legs", 7, new String[]{"XXX", "X X", "X X"}),
    BOOTS("boots", "Boots", Material.DIAMOND_BOOTS, "feet", 3, new String[]{"X X", "X X"}),
    SWORD("sword", "Sword", Material.DIAMOND_SWORD, 7.0, -2.4, new String[]{"X", "X", "S"}),
    AXE("axe", "Axe", Material.DIAMOND_AXE, 9.0, -3.0, new String[]{"XX", "XS", " S"}),
    PICKAXE("pickaxe", "Pickaxe", Material.DIAMOND_PICKAXE, 5.0, -2.8, new String[]{"XXX", " S ", " S "}),
    SHOVEL("shovel", "Shovel", Material.DIAMOND_SPADE, 5.5, -3.0, new String[]{"X", "S", "S"}),
    HOE("hoe", "Hoe", Material.DIAMOND_HOE, 1.0, 0.0, new String[]{"XX", " S", " S"});

    /** Vanilla's per-slot armour modifier ids, so the tooltip and stacking rules read exactly like diamond's. */
    static final String[] ARMOR_UUIDS = {"2AD3F246-FEE1-4E67-B886-69FD380BB150", "9F3D476D-C118-4544-8365-64846904B48E",
        "D8499B04-0E66-4726-AB29-64469D734E0D", "845DB27C-C624-495F-8C9F-6020A9A58B6B"};
    /** Vanilla's main-hand attack damage and attack speed modifier ids (the tooltip shows them as base values). */
    static final String DAMAGE_UUID = "CB3F55D3-645C-4F38-A497-9C13A33DB5CF", SPEED_UUID = "FA233E1C-4180-4865-B01B-BCCE9785ACA3";
    static final double TOUGHNESS = 3.0, KNOCKBACK = 0.05;

    final String id, title;
    final Material base;
    /** Armour: the attribute slot name ("head" ...). Tools and weapons: "mainhand". */
    final String slot;
    final int armor;
    /** Attack damage and speed modifier amounts (vanilla base values: damage 1, speed 4). */
    final double damage, speed;
    /** The vanilla shape the emerald version is crafted in (X emerald block, S stick). */
    final String[] shape;

    ArmoryPiece(String id, String title, Material base, String slot, int armor, String[] shape) {
        this.id = id; this.title = title; this.base = base; this.slot = slot; this.armor = armor; this.damage = 0; this.speed = 0; this.shape = shape;
    }

    ArmoryPiece(String id, String title, Material base, double damage, double speed, String[] shape) {
        this.id = id; this.title = title; this.base = base; this.slot = "mainhand"; this.armor = 0; this.damage = damage; this.speed = speed; this.shape = shape;
    }

    boolean armour() { return ordinal() <= BOOTS.ordinal(); }
    boolean weapon() { return this == SWORD || this == AXE; }
    /** Tools that dig (the axe both fights and fells). */
    boolean tool() { return this == AXE || this == PICKAXE || this == SHOVEL || this == HOE; }

    /** Max durability of the diamond base item (the model predicate is damage / this). */
    int baseDurability() { return base.getMaxDurability(); }

    /** The armour-slot index (0 head .. 3 feet) or -1 for hand items. */
    int armorIndex() { return armour() ? ordinal() : -1; }

    static ArmoryPiece byId(String id) {
        if (id == null) return null;
        String key = id.toLowerCase(Locale.ROOT);
        for (ArmoryPiece p : values()) if (p.id.equals(key)) return p;
        return null;
    }

    static ArmoryPiece ofBase(Material m) {
        for (ArmoryPiece p : values()) if (p.base == m) return p;
        return null;
    }
}
