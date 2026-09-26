package chat.jaspr.enchant.data;

import net.minecraft.server.v1_12_R1.BlockPumpkin;
import net.minecraft.server.v1_12_R1.EnumItemSlot;
import net.minecraft.server.v1_12_R1.Item;
import net.minecraft.server.v1_12_R1.ItemArmor;
import net.minecraft.server.v1_12_R1.ItemAxe;
import net.minecraft.server.v1_12_R1.ItemBlock;
import net.minecraft.server.v1_12_R1.ItemBow;
import net.minecraft.server.v1_12_R1.ItemElytra;
import net.minecraft.server.v1_12_R1.ItemFishingRod;
import net.minecraft.server.v1_12_R1.ItemHoe;
import net.minecraft.server.v1_12_R1.ItemPickaxe;
import net.minecraft.server.v1_12_R1.ItemShield;
import net.minecraft.server.v1_12_R1.ItemSkull;
import net.minecraft.server.v1_12_R1.ItemSpade;
import net.minecraft.server.v1_12_R1.ItemSword;
import net.minecraft.server.v1_12_R1.ItemTool;
import net.minecraft.server.v1_12_R1.MinecraftKey;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Port of SME ConfigProvider.canItemApply + ITypeMatcher. With allowCustomItems=true SME asks
 * item.canApplyAtEnchantingTable(stack, fakeEnchant) which for every vanilla item is exactly the
 * EnumEnchantmentType predicate, reproduced here on NMS item classes.
 */
public final class ItemTypes {
    private ItemTypes() {}

    /** CanApplyConfig.customTypes defaults (they never match vanilla items, kept for fidelity). */
    private static final Map<String, Pattern> CUSTOM = new HashMap<>();
    static {
        CUSTOM.put("BATTLEAXE", Pattern.compile("(mujmajnkraftsbettersurvival\\:item.*battleaxe)|(spartan(defiled|fire|weaponry)\\:battleaxe.*)"));
        CUSTOM.put("LYCANITES_EQUIPMENT", Pattern.compile("lycanitesmobs:equipment"));
        CUSTOM.put("SW_CROSSBOW", Pattern.compile("spartan(defiled|fire|weaponry)\\:crossbow.*"));
    }

    private static boolean armor(Item item, EnumItemSlot slot) {
        return item instanceof ItemArmor && ((ItemArmor) item).c == slot;
    }

    /** vanilla EnumEnchantmentType.WEARABLE */
    private static boolean wearable(Item item) {
        boolean pumpkin = item instanceof ItemBlock && ((ItemBlock) item).getBlock() instanceof BlockPumpkin;
        return item instanceof ItemArmor || item instanceof ItemElytra || item instanceof ItemSkull || pumpkin;
    }

    /** One named type from the SME config. Unknown names never match (SME logs and skips them). */
    public static boolean matches(String type, Item item) {
        if (item == null) return false;
        switch (type) {
            // ALL_TYPES = Forge EnumEnchantmentType.ALL, which with SME's registered "All" helper type matches every item.
            case "ALL_TYPES": return true;
            case "ARMOR": return item instanceof ItemArmor;
            case "ARMOR_HEAD": return armor(item, EnumItemSlot.HEAD);
            case "ARMOR_CHEST": return armor(item, EnumItemSlot.CHEST);
            case "ARMOR_LEGS": return armor(item, EnumItemSlot.LEGS);
            case "ARMOR_FEET": return armor(item, EnumItemSlot.FEET);
            case "SWORD": return item instanceof ItemSword;
            case "TOOL": return item instanceof ItemTool;
            case "FISHING_ROD": return item instanceof ItemFishingRod;
            case "BREAKABLE": return item.usesDurability();
            case "BOW": return item instanceof ItemBow;
            case "WEARABLE": return wearable(item);
            case "ALL_ITEMS": return true;
            case "AXE": return item instanceof ItemAxe;
            case "PICKAXE": return item instanceof ItemPickaxe;
            case "HOE": return item instanceof ItemHoe;
            case "SHOVEL": return item instanceof ItemSpade;
            case "SHIELD": return item instanceof ItemShield;
            case "NONE": return false;
            default:
                Pattern p = CUSTOM.get(type);
                if (p == null) return false;
                MinecraftKey key = Item.REGISTRY.b(item);
                return key != null && p.matcher(key.toString()).matches();
        }
    }

    /** ConfigProvider.canItemApply: any non-inverted type matches and no "!" type matches. */
    public static boolean canItemApply(String[] config, Item item) {
        boolean valid = false;
        boolean inverted = false;
        for (String s : config) {
            boolean inv = false;
            if (s.startsWith("!")) {
                inv = true;
                s = s.substring(1);
            }
            boolean m = matches(s, item);
            if (!inv) valid = valid || m;
            else inverted = inverted || m;
        }
        return valid && !inverted;
    }
}
