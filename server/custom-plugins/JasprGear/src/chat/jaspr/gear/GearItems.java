package chat.jaspr.gear;

import net.minecraft.server.v1_12_R1.MojangsonParser;
import net.minecraft.server.v1_12_R1.NBTTagCompound;
import net.minecraft.server.v1_12_R1.NBTTagList;
import net.minecraft.server.v1_12_R1.NBTTagString;
import org.bukkit.Material;
import org.bukkit.craftbukkit.v1_12_R1.inventory.CraftItemStack;
import org.bukkit.inventory.ItemStack;

/**
 * Canonical gear items. Identity is ONLY the JasprGear:{id} NBT compound on an unbreakable
 * stone hoe; names and lore are display. The canonical compound is built from plain NBT so the
 * standalone exporter (GearExport) can emit the exact same SNBT for the browser catalogue.
 */
public final class GearItems {
    public static final String TAG = "JasprGear";
    public static final String ICON_TAG = "JasprGearIcon";
    /** Phase 2 consumables: JasprGearUse:{id, doses}. Never carries the JasprGear compound. */
    public static final String USE_TAG = "JasprGearUse";
    /** Backpacks: JasprGearPack:{id[, uuid]}. The uuid names the contents file, stamped on first open. */
    public static final String PACK_TAG = "JasprGearPack";
    public static final String CARRIER = "minecraft:stone_hoe";
    public static final int ICON_BASE_MODEL = 40;
    private static final char S = '§';

    private GearItems() {}

    public static NBTTagCompound canonicalTag(GearItem item) {
        NBTTagCompound tag = new NBTTagCompound();
        tag.setBoolean("Unbreakable", true);
        tag.setInt("HideFlags", 63);
        NBTTagCompound display = new NBTTagCompound();
        display.setString("Name", S + String.valueOf(item.color()) + item.title);
        NBTTagList lore = new NBTTagList();
        lore.add(new NBTTagString(S + "8" + (item.type == GearType.ANY ? "Any-slot" : item.type.label) + " gear"));
        for (String line : item.effects) lore.add(new NBTTagString(S + "7" + line));
        display.set("Lore", lore);
        tag.set("display", display);
        NBTTagCompound gear = new NBTTagCompound();
        gear.setString("id", item.id);
        tag.set(TAG, gear);
        return wrap(item.model, tag);
    }

    /** Canonical consumable with its full dose count. */
    public static NBTTagCompound canonicalTag(GearConsumable item) {
        return consumableTag(item, item.doses);
    }

    static NBTTagCompound consumableTag(GearConsumable item, int doses) {
        NBTTagCompound tag = new NBTTagCompound();
        tag.setBoolean("Unbreakable", true);
        tag.setInt("HideFlags", 63);
        NBTTagCompound display = new NBTTagCompound();
        display.setString("Name", S + String.valueOf(item.color()) + item.title);
        NBTTagList lore = new NBTTagList();
        lore.add(new NBTTagString(S + "8Consumable - right-click to use"));
        for (String line : item.effects) lore.add(new NBTTagString(S + "7" + line));
        if (item.doses > 1) lore.add(new NBTTagString(S + "6Doses: " + doses + "/" + item.doses));
        display.set("Lore", lore);
        tag.set("display", display);
        NBTTagCompound use = new NBTTagCompound();
        use.setString("id", item.id);
        if (item.doses > 1) use.setInt("doses", doses);
        tag.set(USE_TAG, use);
        return wrap(item.model, tag);
    }

    public static ItemStack create(GearConsumable item) { return fromTag(canonicalTag(item)); }

    /** Canonical (never opened, so no uuid yet) backpack. */
    public static NBTTagCompound canonicalTag(GearBackpack item) {
        NBTTagCompound tag = new NBTTagCompound();
        tag.setBoolean("Unbreakable", true);
        tag.setInt("HideFlags", 63);
        NBTTagCompound display = new NBTTagCompound();
        display.setString("Name", S + String.valueOf(item.color()) + item.title);
        NBTTagList lore = new NBTTagList();
        lore.add(new NBTTagString(S + "8Backpack - right-click to open"));
        lore.add(new NBTTagString(S + "7" + item.slots() + " slots (tier " + item.tier + ")"));
        lore.add(new NBTTagString(S + "7Right-click it in your inventory too"));
        display.set("Lore", lore);
        tag.set("display", display);
        NBTTagCompound pack = new NBTTagCompound();
        pack.setString("id", item.id);
        tag.set(PACK_TAG, pack);
        return wrap(item.model, tag);
    }

    public static ItemStack create(GearBackpack item) { return fromTag(canonicalTag(item)); }

    /** The backpack carried by this stack, or null. Never trusts names or lore. */
    public static GearBackpack backpack(ItemStack stack) {
        if (empty(stack) || stack.getType() != Material.STONE_HOE) return null;
        try {
            net.minecraft.server.v1_12_R1.ItemStack nms = CraftItemStack.asNMSCopy(stack);
            if (nms == null || !nms.hasTag()) return null;
            NBTTagCompound tag = nms.getTag();
            if (!tag.hasKeyOfType(PACK_TAG, 10) || tag.hasKey(TAG) || tag.hasKey(USE_TAG)) return null;
            return GearBackpack.byId(tag.getCompound(PACK_TAG).getString("id"));
        } catch (RuntimeException error) {
            return null;
        }
    }

    /** The contents uuid of a backpack stack, or null when it was never opened (or is no valid uuid). */
    public static java.util.UUID packUuid(ItemStack stack) {
        if (backpack(stack) == null) return null;
        String raw = CraftItemStack.asNMSCopy(stack).getTag().getCompound(PACK_TAG).getString("uuid");
        try { return raw.isEmpty() ? null : java.util.UUID.fromString(raw); }
        catch (IllegalArgumentException bad) { return null; }
    }

    /** Copy of a backpack stack carrying this contents uuid (display, anvil names etc. kept). */
    public static ItemStack withPackUuid(ItemStack stack, java.util.UUID uuid) {
        net.minecraft.server.v1_12_R1.ItemStack nms = CraftItemStack.asNMSCopy(stack);
        NBTTagCompound tag = nms.getTag();
        NBTTagCompound pack = tag.getCompound(PACK_TAG);
        pack.setString("uuid", uuid.toString());
        tag.set(PACK_TAG, pack);
        nms.setTag(tag);
        return CraftItemStack.asBukkitCopy(nms);
    }

    /** The consumable carried by this stack, or null. Never trusts names or lore. */
    public static GearConsumable consumable(ItemStack stack) {
        if (empty(stack) || stack.getType() != Material.STONE_HOE) return null;
        try {
            net.minecraft.server.v1_12_R1.ItemStack nms = CraftItemStack.asNMSCopy(stack);
            if (nms == null || !nms.hasTag()) return null;
            NBTTagCompound tag = nms.getTag();
            if (!tag.hasKeyOfType(USE_TAG, 10) || tag.hasKey(TAG)) return null;
            return GearConsumable.byId(tag.getCompound(USE_TAG).getString("id"));
        } catch (RuntimeException error) {
            return null;
        }
    }

    /** Doses left in a consumable stack (1 for single-use items, 0 for anything else). */
    public static int doses(ItemStack stack) {
        GearConsumable item = consumable(stack);
        if (item == null) return 0;
        if (item.doses <= 1) return 1;
        int left = CraftItemStack.asNMSCopy(stack).getTag().getCompound(USE_TAG).getInt("doses");
        return Math.max(1, Math.min(item.doses, left <= 0 ? item.doses : left));
    }

    /** The stack after one dose: a copy with fewer doses (custom name kept), or null when used up. */
    public static ItemStack afterDose(ItemStack stack) {
        GearConsumable item = consumable(stack);
        if (item == null) return stack;
        int left = doses(stack) - 1;
        if (left <= 0) return null;
        net.minecraft.server.v1_12_R1.ItemStack nms = CraftItemStack.asNMSCopy(stack);
        NBTTagCompound tag = nms.getTag();
        NBTTagCompound use = tag.getCompound(USE_TAG);
        use.setInt("doses", left);
        tag.set(USE_TAG, use);
        NBTTagCompound display = tag.getCompound("display");
        NBTTagList old = display.getList("Lore", 8), lore = new NBTTagList();
        String marker = S + "6Doses: ";
        for (int i = 0; i < old.size(); i++) if (!old.getString(i).startsWith(marker)) lore.add(new NBTTagString(old.getString(i)));
        lore.add(new NBTTagString(marker + left + "/" + item.doses));
        display.set("Lore", lore);
        tag.set("display", display);
        nms.setTag(tag);
        return CraftItemStack.asBukkitCopy(nms);
    }

    /** Empty-slot hint icon. Never issued to inventories; shown in the /gear menu and panel. */
    public static NBTTagCompound iconTag(int slot) {
        GearType type = GearType.SLOTS[slot];
        NBTTagCompound tag = new NBTTagCompound();
        tag.setBoolean("Unbreakable", true);
        tag.setInt("HideFlags", 63);
        NBTTagCompound display = new NBTTagCompound();
        display.setString("Name", S + "7" + type.label + " slot");
        NBTTagList lore = new NBTTagList();
        lore.add(new NBTTagString(S + "8" + type.hint));
        lore.add(new NBTTagString(S + "8Any-slot gear also fits"));
        display.set("Lore", lore);
        tag.set("display", display);
        tag.setBoolean(ICON_TAG, true);
        return wrap(iconModel(slot), tag);
    }

    public static int iconModel(int slot) { return ICON_BASE_MODEL + GearType.SLOTS[slot].ordinal(); }

    private static NBTTagCompound wrap(int damage, NBTTagCompound tag) {
        NBTTagCompound root = new NBTTagCompound();
        root.setString("id", CARRIER);
        root.setByte("Count", (byte) 1);
        root.setShort("Damage", (short) damage);
        root.set("tag", tag);
        return root;
    }

    public static ItemStack create(GearItem item) { return fromTag(canonicalTag(item)); }

    public static ItemStack icon(int slot) { return fromTag(iconTag(slot)); }

    static ItemStack fromTag(NBTTagCompound compound) {
        return CraftItemStack.asBukkitCopy(new net.minecraft.server.v1_12_R1.ItemStack(compound));
    }

    public static boolean empty(ItemStack stack) {
        return stack == null || stack.getType() == Material.AIR || stack.getAmount() <= 0;
    }

    /** The gear definition carried by this stack, or null. Never trusts names or lore. */
    public static GearItem identify(ItemStack stack) {
        if (empty(stack) || stack.getType() != Material.STONE_HOE) return null;
        try {
            net.minecraft.server.v1_12_R1.ItemStack nms = CraftItemStack.asNMSCopy(stack);
            if (nms == null || !nms.hasTag()) return null;
            NBTTagCompound tag = nms.getTag();
            if (!tag.hasKeyOfType(TAG, 10)) return null;
            return GearItem.byId(tag.getCompound(TAG).getString("id"));
        } catch (RuntimeException error) {
            return null;
        }
    }

    public static boolean isIcon(ItemStack stack) {
        if (empty(stack) || stack.getType() != Material.STONE_HOE) return false;
        net.minecraft.server.v1_12_R1.ItemStack nms = CraftItemStack.asNMSCopy(stack);
        return nms != null && nms.hasTag() && nms.getTag().getBoolean(ICON_TAG);
    }

    /** Experience points banked inside a Field Journal (0 for anything else). */
    public static int storedXp(ItemStack stack) {
        if (identify(stack) == null) return 0;
        net.minecraft.server.v1_12_R1.ItemStack nms = CraftItemStack.asNMSCopy(stack);
        return Math.max(0, nms.getTag().getCompound(TAG).getInt("xp"));
    }

    /** Copy of the stack with its bank set to xp; the lore shows the balance. */
    public static ItemStack withStoredXp(ItemStack stack, int xp) {
        net.minecraft.server.v1_12_R1.ItemStack nms = CraftItemStack.asNMSCopy(stack);
        NBTTagCompound tag = nms.getTag();
        NBTTagCompound gear = tag.getCompound(TAG);
        if (xp > 0) gear.setInt("xp", xp); else gear.remove("xp");
        tag.set(TAG, gear);
        NBTTagCompound display = tag.getCompound("display");
        NBTTagList old = display.getList("Lore", 8), lore = new NBTTagList();
        String marker = S + "6Stored: ";
        for (int i = 0; i < old.size(); i++) if (!old.getString(i).startsWith(marker)) lore.add(new NBTTagString(old.getString(i)));
        if (xp > 0) lore.add(new NBTTagString(marker + xp + " XP"));
        display.set("Lore", lore);
        tag.set("display", display);
        nms.setTag(tag);
        return CraftItemStack.asBukkitCopy(nms);
    }

    /** True when the stack carries any NBT at all (used to refuse tagged recipe substitutes). */
    public static boolean tagged(ItemStack stack) {
        if (empty(stack)) return false;
        net.minecraft.server.v1_12_R1.ItemStack nms = CraftItemStack.asNMSCopy(stack);
        return nms != null && nms.hasTag() && !nms.getTag().isEmpty();
    }

    /** Full-fidelity SNBT for storage and for the client panel (keeps anvil names etc.). */
    public static String toSnbt(ItemStack stack) {
        if (empty(stack)) return "";
        net.minecraft.server.v1_12_R1.ItemStack nms = CraftItemStack.asNMSCopy(stack);
        return nms.save(new NBTTagCompound()).toString();
    }

    public static ItemStack fromSnbt(String snbt) throws Exception {
        if (snbt == null || snbt.isEmpty()) return null;
        NBTTagCompound compound = MojangsonParser.parse(snbt);
        ItemStack stack = fromTag(compound);
        return empty(stack) ? null : stack;
    }
}
