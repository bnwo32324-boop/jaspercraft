package chat.jaspr.gear;

import net.minecraft.server.v1_12_R1.NBTTagCompound;
import net.minecraft.server.v1_12_R1.NBTTagList;
import net.minecraft.server.v1_12_R1.NBTTagString;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.craftbukkit.v1_12_R1.inventory.CraftItemStack;
import org.bukkit.inventory.ItemStack;

/**
 * Canonical realm armoury items and their forging materials. Identity is ONLY the JasprArmory compound on an unbreakable
 * diamond item whose damage value is the set's model number; names and lore are display. Built from plain NBT so the
 * standalone exporter (GearExport) emits the exact SNBT the browser catalogue shows.
 *
 * Lore contract with JasprRPG's Armaments: an armoury item's own lines come first; when the item becomes an enhanced
 * armament its block is written underneath ARMAMENT_HEADER, and both plugins keep the other's lines.
 */
public final class ArmoryItems {
    public static final String TAG = "JasprArmory";
    /** First line of the Armaments block under an armoury item's own lore (the same string JasprRPG writes). */
    public static final String ARMAMENT_HEADER = ChatColor.DARK_GRAY + "- Armament -";
    /**
     * Worn-skin hint for the browser client (armour only): the set's number 1..6. The client's armour layer reads this
     * one integer to draw textures/models/armor/jaspr_<set>_layer_<n>.png; it is never used for identity.
     */
    public static final String SKIN_TAG = "JasprArmorySkin";
    private static final char S = '§';

    private ArmoryItems() {}

    /** An identified armoury item. */
    static final class Id {
        final ArmorySet set; final ArmoryPiece piece;
        Id(ArmorySet set, ArmoryPiece piece) { this.set = set; this.piece = piece; }
        String key() { return set.id + "_" + piece.id; }
    }

    // ------------------------------------------------------------------ gear

    static String name(ArmorySet set, ArmoryPiece piece) { return set.title + " " + piece.title; }

    /** The lore lines the armoury owns (everything above the Armaments header). */
    static java.util.List<String> ownLore(ArmorySet set, ArmoryPiece piece) {
        java.util.List<String> lines = new java.util.ArrayList<String>();
        lines.add(S + "8" + set.title + " armoury of " + set.realmTitle);
        if (piece.armour()) lines.add(S + "7" + set.setBonus);
        if (piece.weapon()) lines.add(S + "7" + set.weaponPower);
        if (piece.tool()) lines.add(S + "7" + set.toolPower);
        lines.add(S + "5At home in " + set.realmTitle + ": " + (piece.armour() ? "turns aside 3% of every blow"
            : piece.weapon() ? "hits 15% harder" : "digs as it was forged to"));
        return lines;
    }

    public static NBTTagCompound canonicalTag(ArmorySet set, ArmoryPiece piece) {
        NBTTagCompound tag = new NBTTagCompound();
        tag.setBoolean("Unbreakable", true);
        NBTTagCompound display = new NBTTagCompound();
        display.setString("Name", S + String.valueOf(set.color.getChar()) + name(set, piece));
        NBTTagList lore = new NBTTagList();
        for (String line : ownLore(set, piece)) lore.add(new NBTTagString(line));
        display.set("Lore", lore);
        tag.set("display", display);
        tag.set("AttributeModifiers", modifiers(piece));
        if (piece.armour()) tag.setInt(SKIN_TAG, set.ordinal() + 1);
        NBTTagCompound mine = new NBTTagCompound();
        mine.setString("set", set.id);
        mine.setString("piece", piece.id);
        tag.set(TAG, mine);
        NBTTagCompound root = new NBTTagCompound();
        root.setString("id", "minecraft:" + itemKey(piece.base));
        root.setByte("Count", (byte) 1);
        root.setShort("Damage", (short) set.model());
        root.set("tag", tag);
        return root;
    }

    /** Registry name of a base item (diamond_spade is "diamond_shovel" in 1.12). */
    static String itemKey(Material m) {
        switch (m) {
            case DIAMOND_SPADE: return "diamond_shovel";
            default: return m.name().toLowerCase(java.util.Locale.ROOT);
        }
    }

    static NBTTagList modifiers(ArmoryPiece piece) {
        NBTTagList list = new NBTTagList();
        if (piece.armour()) {
            String uuid = ArmoryPiece.ARMOR_UUIDS[piece.armorIndex()];
            list.add(modifier("generic.armor", "Armor modifier", piece.armor, uuid, piece.slot));
            list.add(modifier("generic.armorToughness", "Armor toughness", ArmoryPiece.TOUGHNESS, uuid, piece.slot));
            list.add(modifier("generic.knockbackResistance", "Armor knockback resistance", ArmoryPiece.KNOCKBACK, uuid, piece.slot));
        } else {
            list.add(modifier("generic.attackDamage", "Weapon modifier", piece.damage, ArmoryPiece.DAMAGE_UUID, "mainhand"));
            list.add(modifier("generic.attackSpeed", "Weapon modifier", piece.speed, ArmoryPiece.SPEED_UUID, "mainhand"));
        }
        return list;
    }

    private static NBTTagCompound modifier(String attribute, String name, double amount, String uuid, String slot) {
        java.util.UUID id = java.util.UUID.fromString(uuid);
        NBTTagCompound m = new NBTTagCompound();
        m.setString("AttributeName", attribute);
        m.setString("Name", name);
        m.setDouble("Amount", amount);
        m.setInt("Operation", 0);
        m.setLong("UUIDMost", id.getMostSignificantBits());
        m.setLong("UUIDLeast", id.getLeastSignificantBits());
        m.setString("Slot", slot);
        return m;
    }

    public static ItemStack create(ArmorySet set, ArmoryPiece piece) { return GearItems.fromTag(canonicalTag(set, piece)); }

    /** The armoury identity of a stack, or null. Never trusts names or lore. */
    static Id identify(ItemStack stack) {
        if (GearItems.empty(stack)) return null;
        ArmoryPiece piece = ArmoryPiece.ofBase(stack.getType());
        if (piece == null) return null;
        try {
            net.minecraft.server.v1_12_R1.ItemStack nms = CraftItemStack.asNMSCopy(stack);
            if (nms == null || !nms.hasTag()) return null;
            NBTTagCompound tag = nms.getTag();
            if (!tag.hasKeyOfType(TAG, 10) || !tag.getBoolean("Unbreakable")) return null;
            NBTTagCompound mine = tag.getCompound(TAG);
            ArmorySet set = ArmorySet.byId(mine.getString("set"));
            if (set == null || ArmoryPiece.byId(mine.getString("piece")) != piece || nms.getData() != set.model()) return null;
            return new Id(set, piece);
        } catch (RuntimeException error) {
            return null;
        }
    }

    /**
     * The up-to-date copy of an armoury armour piece made before its stats last changed (owner 2026-10-05: the helmet and
     * boots were 3 armour, exactly diamond's, until they became 4), or null when there is nothing to do: not an armoury
     * armour piece, or its armour, toughness and knockback resistance already read as ArmoryPiece says. Only the amounts of
     * those three modifiers are rewritten; enchantments, anvil cost, the Armaments record, lore and every other tag stay.
     */
    static ItemStack upgraded(ItemStack stack) {
        Id id = identify(stack);
        if (id == null || !id.piece.armour()) return null;
        try {
            net.minecraft.server.v1_12_R1.ItemStack nms = CraftItemStack.asNMSCopy(stack);
            NBTTagList mods = nms.getTag().getList("AttributeModifiers", 10);
            NBTTagList want = modifiers(id.piece);
            boolean changed = mods.size() != want.size();
            for (int i = 0; i < want.size() && !changed; i++) {
                NBTTagCompound have = mods.get(i), canon = want.get(i);
                changed = !have.getString("AttributeName").equals(canon.getString("AttributeName")) || have.getDouble("Amount") != canon.getDouble("Amount");
            }
            if (!changed) return null;
            nms.getTag().set("AttributeModifiers", want);
            return CraftItemStack.asBukkitCopy(nms);
        } catch (RuntimeException error) {
            return null;
        }
    }

    /**
     * The forged result for a diamond piece: the canonical armoury piece carrying over the diamond piece's enchantments,
     * anvil cost and Armaments record (level, rarity, abilities and the lines that show them), so nothing earned is lost.
     */
    static ItemStack forge(ArmorySet set, ArmoryPiece piece, ItemStack diamond) {
        NBTTagCompound root = canonicalTag(set, piece);
        NBTTagCompound tag = root.getCompound("tag");
        try {
            net.minecraft.server.v1_12_R1.ItemStack nms = CraftItemStack.asNMSCopy(diamond);
            NBTTagCompound old = nms != null && nms.hasTag() ? nms.getTag() : null;
            if (old != null) {
                if (old.hasKeyOfType("ench", 9)) tag.set("ench", old.getList("ench", 10).d());
                if (old.hasKeyOfType("RepairCost", 3)) tag.setInt("RepairCost", old.getInt("RepairCost"));
                if (old.hasKeyOfType("JasprArmament", 10)) {
                    tag.set("JasprArmament", old.getCompound("JasprArmament").g());
                    NBTTagList armamentLines = old.getCompound("display").getList("Lore", 8);
                    NBTTagList lore = tag.getCompound("display").getList("Lore", 8);
                    lore.add(new NBTTagString(ARMAMENT_HEADER));
                    for (int i = 0; i < armamentLines.size(); i++) lore.add(new NBTTagString(armamentLines.getString(i)));
                    tag.getCompound("display").set("Lore", lore);
                }
            }
        } catch (RuntimeException ignored) {
            // a diamond piece we cannot read forges into a fresh one
        }
        return GearItems.fromTag(root);
    }

    // ------------------------------------------------------------------ forging materials

    /** The three realm-only materials (the Nether and the End forge with their own vanilla goods). */
    static final String[] MATERIALS = {"abyssal_pearl", "titan_shard", "liminal_fragment"};

    static ArmorySet setOfMaterial(String id) {
        for (ArmorySet s : ArmorySet.values()) if (id != null && id.equals(s.materialId)) return s;
        return null;
    }

    static String materialTitle(String id) {
        switch (id) {
            case "abyssal_pearl": return "Abyssal Pearl";
            case "titan_shard": return "Titan Shard";
            case "liminal_fragment": return "Liminal Fragment";
            default: return id;
        }
    }

    private static String materialFlavour(String id) {
        switch (id) {
            case "abyssal_pearl": return "Grown in the drowned dark.";
            case "titan_shard": return "Splintered from a fallen titan.";
            case "liminal_fragment": return "It hums like a dying light.";
            default: return "";
        }
    }

    public static NBTTagCompound materialTag(String id, int count) {
        ArmorySet set = setOfMaterial(id);
        if (set == null) throw new IllegalArgumentException("Unknown armoury material: " + id);
        NBTTagCompound tag = new NBTTagCompound();
        NBTTagCompound display = new NBTTagCompound();
        display.setString("Name", S + String.valueOf(set.color.getChar()) + materialTitle(id));
        NBTTagList lore = new NBTTagList();
        lore.add(new NBTTagString(S + "7" + materialFlavour(id)));
        lore.add(new NBTTagString(S + "5Found only in " + set.realmTitle));
        lore.add(new NBTTagString(S + "8Forge: eight around a diamond piece"));
        display.set("Lore", lore);
        tag.set("display", display);
        NBTTagList ench = new NBTTagList();             // the glint marks it at a glance; hidden, and useless on a material
        NBTTagCompound e = new NBTTagCompound();
        e.setShort("id", (short) 34);
        e.setShort("lvl", (short) 1);
        ench.add(e);
        tag.set("ench", ench);
        tag.setInt("HideFlags", 1);
        NBTTagCompound mine = new NBTTagCompound();
        mine.setString("material", id);
        tag.set(TAG, mine);
        NBTTagCompound root = new NBTTagCompound();
        root.setString("id", "minecraft:" + materialItemKey(set.corner));
        root.setByte("Count", (byte) Math.max(1, Math.min(64, count)));
        root.setShort("Damage", (short) 0);
        root.set("tag", tag);
        return root;
    }

    private static String materialItemKey(Material m) {
        switch (m) {
            case PRISMARINE_CRYSTALS: return "prismarine_crystals";
            case QUARTZ: return "quartz";
            case GLOWSTONE_DUST: return "glowstone_dust";
            default: return m.name().toLowerCase(java.util.Locale.ROOT);
        }
    }

    public static ItemStack material(String id, int count) { return GearItems.fromTag(materialTag(id, count)); }

    /** The forging material id carried by this stack, or null. */
    static String materialOf(ItemStack stack) {
        if (GearItems.empty(stack)) return null;
        try {
            net.minecraft.server.v1_12_R1.ItemStack nms = CraftItemStack.asNMSCopy(stack);
            if (nms == null || !nms.hasTag()) return null;
            NBTTagCompound tag = nms.getTag();
            if (!tag.hasKeyOfType(TAG, 10)) return null;
            String id = tag.getCompound(TAG).getString("material");
            ArmorySet set = setOfMaterial(id);
            return set != null && stack.getType() == set.corner ? id : null;
        } catch (RuntimeException error) {
            return null;
        }
    }

    /** True for anything the armoury marked (gear or material). */
    static boolean marked(ItemStack stack) {
        if (GearItems.empty(stack)) return false;
        try {
            net.minecraft.server.v1_12_R1.ItemStack nms = CraftItemStack.asNMSCopy(stack);
            return nms != null && nms.hasTag() && nms.getTag().hasKey(TAG);
        } catch (RuntimeException error) {
            return false;
        }
    }
}
