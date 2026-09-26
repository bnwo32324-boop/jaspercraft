package chat.jaspr.enchant.nms;

import chat.jaspr.enchant.data.ItemTypes;
import chat.jaspr.enchant.data.SmeDef;
import net.minecraft.server.v1_12_R1.EnchantmentSlotType;
import net.minecraft.server.v1_12_R1.Item;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.function.Predicate;

/**
 * Builds one extra EnchantmentSlotType per SME enchantment so that every vanilla code path that asks
 * {@code enchantment.itemTarget.canEnchant(item)} (enchanting table candidates, enchant_with_levels loot,
 * mob equipment) sees SME's exact "Can apply on enchantment table" item set. Falls back to the closest
 * vanilla constant if the JVM refuses the allocation.
 */
public final class SlotTypes {
    private SlotTypes() {}

    public static boolean customTargetsWorking = true;
    public static String failure = null;

    private static Object unsafe;
    private static Method allocateInstance, objectFieldOffset, putObject, putInt;

    private static void initUnsafe() throws Exception {
        if (unsafe != null) return;
        Class<?> uc = Class.forName("sun.misc.Unsafe");
        Field f = uc.getDeclaredField("theUnsafe");
        f.setAccessible(true);
        unsafe = f.get(null);
        allocateInstance = uc.getMethod("allocateInstance", Class.class);
        objectFieldOffset = uc.getMethod("objectFieldOffset", Field.class);
        putObject = uc.getMethod("putObject", Object.class, long.class, Object.class);
        putInt = uc.getMethod("putInt", Object.class, long.class, int.class);
    }

    /** closest vanilla target (used for the ordinal, Bukkit getItemTarget and as a fallback) */
    public static EnchantmentSlotType closestVanilla(SmeDef def) {
        String first = def.tableTypes.length == 0 ? "NONE" : def.tableTypes[0];
        if (def.regName.equals("supremeprotection")) first = "ARMOR";
        switch (first) {
            case "ARMOR": return EnchantmentSlotType.ARMOR;
            case "ARMOR_HEAD": return EnchantmentSlotType.ARMOR_HEAD;
            case "ARMOR_CHEST": return EnchantmentSlotType.ARMOR_CHEST;
            case "ARMOR_LEGS": return EnchantmentSlotType.ARMOR_LEGS;
            case "ARMOR_FEET": return EnchantmentSlotType.ARMOR_FEET;
            case "SWORD": return EnchantmentSlotType.WEAPON;
            case "AXE":
            case "PICKAXE":
            case "SHOVEL":
            case "TOOL": return EnchantmentSlotType.DIGGER;
            case "FISHING_ROD": return EnchantmentSlotType.FISHING_ROD;
            case "BOW": return EnchantmentSlotType.BOW;
            case "WEARABLE": return EnchantmentSlotType.WEARABLE;
            case "ALL_ITEMS":
            case "ALL_TYPES": return EnchantmentSlotType.ALL;
            default: return EnchantmentSlotType.BREAKABLE; // HOE, SHIELD, BREAKABLE, NONE
        }
    }

    /** SME table check for one definition (supreme protection uses EnumEnchantmentType.ARMOR). */
    public static boolean tableAllows(SmeDef def, Item item) {
        return ItemTypes.canItemApply(def.tableTypes, item);
    }

    public static EnchantmentSlotType create(final SmeDef def) {
        EnchantmentSlotType vanilla = closestVanilla(def);
        if (!customTargetsWorking) return vanilla;
        try {
            initUnsafe();
            Object o = allocateInstance.invoke(unsafe, SmeSlotType.class);
            SmeSlotType t = (SmeSlotType) o;
            long nameOff = (Long) objectFieldOffset.invoke(unsafe, Enum.class.getDeclaredField("name"));
            long ordOff = (Long) objectFieldOffset.invoke(unsafe, Enum.class.getDeclaredField("ordinal"));
            putObject.invoke(unsafe, t, nameOff, "SME_" + def.regName.toUpperCase());
            putInt.invoke(unsafe, t, ordOff, vanilla.ordinal());
            t.test = new Predicate<Object>() {
                @Override
                public boolean test(Object item) {
                    return item instanceof Item && tableAllows(def, (Item) item);
                }
            };
            EnchantmentSlotType asTarget = t;
            // sanity: must behave like an enum constant for switch maps and identity
            if (asTarget.ordinal() != vanilla.ordinal()) throw new IllegalStateException("ordinal not applied");
            return asTarget;
        } catch (Throwable ex) {
            customTargetsWorking = false;
            failure = ex.getClass().getSimpleName() + ": " + ex.getMessage();
            return vanilla;
        }
    }
}
