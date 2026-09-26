package chat.jaspr.enchant.nms;

import chat.jaspr.enchant.data.Incompat;
import chat.jaspr.enchant.data.SmeDef;
import chat.jaspr.enchant.data.SmeTable;
import net.minecraft.server.v1_12_R1.Enchantment;
import net.minecraft.server.v1_12_R1.EnchantmentSlotType;
import net.minecraft.server.v1_12_R1.EnumItemSlot;
import net.minecraft.server.v1_12_R1.MinecraftKey;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

/**
 * Inserts the 130 SME enchantments into Enchantment.enchantments (RegistryMaterials, fixed ids
 * 72..201 in SME registration order, keys somanyenchantments:&lt;regname&gt;) and registers Bukkit
 * wrappers. Must run in onLoad so worlds/players load with the ids known.
 */
public final class Registrar {
    private Registrar() {}

    public static final int FIRST_ID = 72;
    public static final SmeEnchantment[] BY_INDEX = new SmeEnchantment[SmeTable.ALL.length];
    public static final List<String> problems = new ArrayList<>();
    public static boolean registered = false;
    public static int bukkitRegistered = 0;

    public static EnumItemSlot[] slots(String name) {
        switch (name) {
            case "NONE": return new EnumItemSlot[0];
            case "ALL": return EnumItemSlot.values();
            case "BODY": return new EnumItemSlot[]{EnumItemSlot.HEAD, EnumItemSlot.CHEST, EnumItemSlot.LEGS, EnumItemSlot.FEET};
            case "HEAD": return new EnumItemSlot[]{EnumItemSlot.HEAD};
            case "CHEST": return new EnumItemSlot[]{EnumItemSlot.CHEST};
            case "LEGS": return new EnumItemSlot[]{EnumItemSlot.LEGS};
            case "FEET": return new EnumItemSlot[]{EnumItemSlot.FEET};
            case "HAND": return new EnumItemSlot[]{EnumItemSlot.MAINHAND, EnumItemSlot.OFFHAND};
            case "MAINHAND": return new EnumItemSlot[]{EnumItemSlot.MAINHAND};
            case "OFFHAND": return new EnumItemSlot[]{EnumItemSlot.OFFHAND};
            default: throw new IllegalArgumentException("slots " + name);
        }
    }

    public static synchronized void registerAll() throws Exception {
        if (registered) return;
        // refuse double registration (plugin reload): ids/keys must be free or already ours
        for (SmeDef def : SmeTable.ALL) {
            Enchantment existing = Enchantment.c(def.id);
            if (existing != null) {
                throw new IllegalStateException("enchantment id " + def.id + " already taken by " + existing.getClass().getName()
                        + " (plugin reload is not supported; restart the server)");
            }
            if (Enchantment.enchantments.get(new MinecraftKey("somanyenchantments", def.regName)) != null) {
                throw new IllegalStateException("key somanyenchantments:" + def.regName + " already registered");
            }
        }
        for (SmeDef def : SmeTable.ALL) {
            if (def.id != FIRST_ID + def.index) throw new IllegalStateException("id order broken at " + def.regName);
            EnchantmentSlotType target = SlotTypes.create(def);
            boolean exact = target instanceof SmeSlotType;
            SmeEnchantment e = new SmeEnchantment(def, Enchantment.Rarity.valueOf(def.rarity), target, slots(def.slots), exact);
            Enchantment.enchantments.a(def.id, new MinecraftKey("somanyenchantments", def.regName), e);
            BY_INDEX[def.index] = e;
        }
        for (SmeEnchantment e : BY_INDEX) {
            e.setIncompatible(Incompat.compute(e.def, e));
        }
        // Bukkit wrappers
        Field accepting = org.bukkit.enchantments.Enchantment.class.getDeclaredField("acceptingNew");
        accepting.setAccessible(true);
        boolean before = accepting.getBoolean(null);
        accepting.setBoolean(null, true);
        try {
            for (SmeEnchantment e : BY_INDEX) {
                if (org.bukkit.enchantments.Enchantment.getById(e.def.id) != null) {
                    problems.add("bukkit id " + e.def.id + " already present");
                    continue;
                }
                org.bukkit.enchantments.Enchantment.registerEnchantment(new SmeCraftEnchantment(e));
                bukkitRegistered++;
            }
        } finally {
            if (!before) org.bukkit.enchantments.Enchantment.stopAcceptingRegistrations();
        }
        registered = true;
    }

    public static SmeEnchantment byReg(String reg) {
        for (SmeEnchantment e : BY_INDEX) if (e != null && e.def.regName.equals(reg)) return e;
        return null;
    }

    public static SmeEnchantment of(Enchantment e) {
        return e instanceof SmeEnchantment ? (SmeEnchantment) e : null;
    }
}
