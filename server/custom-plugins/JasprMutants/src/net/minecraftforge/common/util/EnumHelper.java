package net.minecraftforge.common.util;

import chat.jaspr.mutants.registry.EnumInjector;
import net.minecraft.item.ItemArmor;
import net.minecraft.util.SoundEvent;

/**
 * JasperCraft port shim of Forge's EnumHelper. Forge creates enum constants through ReflectionFactory (Java 8); the
 * server runs Java 17, so chat.jaspr.mutants.registry.EnumInjector calls the enum's real constructor through a method
 * handle and appends the constant to values() exactly as Forge does.
 */
public class EnumHelper {
    public static ItemArmor.ArmorMaterial addArmorMaterial(String name, String textureName, int durability, int[] reductionAmounts, int enchantability, SoundEvent soundOnEquip, float toughness) {
        return addEnum(ItemArmor.ArmorMaterial.class, name, new Class<?>[]{String.class, int.class, int[].class, int.class, SoundEvent.class, float.class},
                new Object[]{textureName, durability, reductionAmounts, enchantability, soundOnEquip, toughness});
    }

    public static <T extends Enum<?>> T addEnum(Class<T> enumType, String enumName, Class<?>[] paramTypes, Object[] paramValues) {
        return EnumInjector.addEnum(enumType, enumName, paramTypes, paramValues);
    }
}
