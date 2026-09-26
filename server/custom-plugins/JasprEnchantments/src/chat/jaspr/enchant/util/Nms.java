package chat.jaspr.enchant.util;

import com.google.common.base.Function;
import net.minecraft.server.v1_12_R1.DamageSource;
import net.minecraft.server.v1_12_R1.DataWatcherObject;
import net.minecraft.server.v1_12_R1.Enchantment;
import net.minecraft.server.v1_12_R1.EnchantmentManager;
import net.minecraft.server.v1_12_R1.Entity;
import net.minecraft.server.v1_12_R1.EntityCreeper;
import net.minecraft.server.v1_12_R1.EntityFishingHook;
import net.minecraft.server.v1_12_R1.EntityHuman;
import net.minecraft.server.v1_12_R1.EntityItem;
import net.minecraft.server.v1_12_R1.EntityLiving;
import net.minecraft.server.v1_12_R1.ItemStack;
import net.minecraft.server.v1_12_R1.PathfinderGoalSelector;
import org.bukkit.craftbukkit.v1_12_R1.entity.CraftEntity;
import org.bukkit.craftbukkit.v1_12_R1.entity.CraftLivingEntity;
import org.bukkit.craftbukkit.v1_12_R1.inventory.CraftItemStack;
import org.bukkit.event.entity.EntityDamageEvent;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * NMS/CraftBukkit access for Paper 1.12.2 (v1_12_R1). Private fields used, with their MCP meaning:
 * EntityLiving.bp = activeItemStackUseCount, EntityLiving.aE = ticksSinceLastSwing,
 * EntityHuman.bS = xpSeed, EntityItem.age, EntityCreeper.fuseTicks = timeSinceIgnited,
 * EntityCreeper.c = IGNITED watcher, PathfinderGoalSelector.b = taskEntries,
 * EntityFishingHook.aw = luck / ax = lureSpeed, CraftItemStack.handle,
 * EntityDamageEvent.modifierFunctions (captures the NMS DamageSource as val$damagesource).
 */
public final class Nms {
    private Nms() {}

    private static Field f(Class<?> c, String name) {
        try {
            Field fl = c.getDeclaredField(name);
            fl.setAccessible(true);
            return fl;
        } catch (Throwable t) {
            Log.warn("SME_REFLECT_MISSING field=" + c.getSimpleName() + "." + name);
            return null;
        }
    }

    private static final Field USE_COUNT = f(EntityLiving.class, "bp");
    private static final Field SWING_TICKS = f(EntityLiving.class, "aE");
    private static final Field JUMP_TICKS = f(EntityLiving.class, "bD");
    private static final Field XP_SEED = f(EntityHuman.class, "bS");
    private static final Field ITEM_AGE = f(EntityItem.class, "age");
    private static final Field CREEPER_FUSE = f(EntityCreeper.class, "fuseTicks");
    private static final Field CREEPER_IGNITED = f(EntityCreeper.class, "c");
    private static final Field GOAL_ENTRIES = f(PathfinderGoalSelector.class, "b");
    private static final Field HOOK_LUCK = f(EntityFishingHook.class, "aw");
    private static final Field HOOK_LURE = f(EntityFishingHook.class, "ax");
    private static final Field CRAFT_HANDLE = f(CraftItemStack.class, "handle");
    private static final Field MOD_FUNCTIONS = f(EntityDamageEvent.class, "modifierFunctions");

    public static boolean reflectionOk() {
        return USE_COUNT != null && SWING_TICKS != null && XP_SEED != null && ITEM_AGE != null && CREEPER_FUSE != null
                && CREEPER_IGNITED != null && GOAL_ENTRIES != null && HOOK_LUCK != null && HOOK_LURE != null
                && CRAFT_HANDLE != null && MOD_FUNCTIONS != null;
    }

    // ------------------------------------------------------------------ entities

    public static EntityLiving living(org.bukkit.entity.Entity e) {
        return e instanceof CraftLivingEntity ? ((CraftLivingEntity) e).getHandle() : null;
    }

    public static Entity entity(org.bukkit.entity.Entity e) {
        return e instanceof CraftEntity ? ((CraftEntity) e).getHandle() : null;
    }

    public static int useCount(EntityLiving e) {
        try {
            return USE_COUNT.getInt(e);
        } catch (Throwable t) {
            return 0;
        }
    }

    public static void setUseCount(EntityLiving e, int v) {
        try {
            USE_COUNT.setInt(e, v);
        } catch (Throwable ignored) {
        }
    }

    public static int swingTicks(EntityLiving e) {
        try {
            return SWING_TICKS.getInt(e);
        } catch (Throwable t) {
            return 0;
        }
    }

    public static int jumpTicks(EntityLiving e) {
        try {
            return JUMP_TICKS.getInt(e);
        } catch (Throwable t) {
            return 0;
        }
    }

    public static int xpSeed(EntityHuman h) {
        try {
            return XP_SEED.getInt(h);
        } catch (Throwable t) {
            return 0;
        }
    }

    public static int itemAge(EntityItem it) {
        try {
            return ITEM_AGE.getInt(it);
        } catch (Throwable t) {
            return 0;
        }
    }

    public static void setItemAge(EntityItem it, int age) {
        try {
            ITEM_AGE.setInt(it, age);
        } catch (Throwable ignored) {
        }
    }

    @SuppressWarnings("unchecked")
    public static void defuseCreeper(EntityCreeper c) throws Exception {
        DataWatcherObject<Boolean> ignited = (DataWatcherObject<Boolean>) CREEPER_IGNITED.get(null);
        c.getDataWatcher().set(ignited, Boolean.FALSE);
        c.a(0); // setCreeperState(0)
        CREEPER_FUSE.setInt(c, 0);
        c.explosionRadius = 1;
    }

    public static int goalCount(PathfinderGoalSelector sel) {
        try {
            return ((Set<?>) GOAL_ENTRIES.get(sel)).size();
        } catch (Throwable t) {
            return 0;
        }
    }

    public static void addHookLuckLure(EntityFishingHook hook, int luck, int lure) {
        try {
            if (luck != 0) HOOK_LUCK.setInt(hook, HOOK_LUCK.getInt(hook) + luck);
            if (lure != 0) HOOK_LURE.setInt(hook, HOOK_LURE.getInt(hook) + lure);
        } catch (Throwable ignored) {
        }
    }

    public static int hookLuck(EntityFishingHook hook) {
        try {
            return HOOK_LUCK.getInt(hook);
        } catch (Throwable t) {
            return -1;
        }
    }

    public static int hookLure(EntityFishingHook hook) {
        try {
            return HOOK_LURE.getInt(hook);
        } catch (Throwable t) {
            return -1;
        }
    }

    // ------------------------------------------------------------------ items

    /** live NMS stack behind a CraftItemStack (or a copy for plain Bukkit stacks) */
    public static ItemStack nms(org.bukkit.inventory.ItemStack s) {
        if (s == null) return ItemStack.a;
        if (s instanceof CraftItemStack) {
            try {
                ItemStack h = (ItemStack) CRAFT_HANDLE.get(s);
                return h == null ? ItemStack.a : h;
            } catch (Throwable ignored) {
            }
        }
        return CraftItemStack.asNMSCopy(s);
    }

    public static int level(Enchantment e, ItemStack s) {
        if (e == null || s == null || s.isEmpty()) return 0;
        return EnchantmentManager.getEnchantmentLevel(e, s);
    }

    /** EnchantmentHelper.getMaxEnchantmentLevel over the enchantment's slots */
    public static int maxLevel(Enchantment e, EntityLiving ent) {
        if (e == null || ent == null) return 0;
        return EnchantmentManager.a(e, ent);
    }

    /** EnchantUtil.getTotalArmorEnchantmentLevel */
    public static int totalLevel(Enchantment e, EntityLiving ent) {
        if (e == null || ent == null) return 0;
        List<ItemStack> list = e.a(ent);
        int sum = 0;
        for (ItemStack s : list) {
            if (s.isEmpty()) continue;
            sum += EnchantmentManager.getEnchantmentLevel(e, s);
        }
        return sum;
    }

    public static int mainLevel(Enchantment e, EntityLiving ent) {
        return ent == null ? 0 : level(e, ent.getItemInMainHand());
    }

    // ------------------------------------------------------------------ damage events

    @SuppressWarnings("unchecked")
    public static Map<EntityDamageEvent.DamageModifier, ? extends Function<? super Double, Double>> functions(EntityDamageEvent e) {
        try {
            return (Map<EntityDamageEvent.DamageModifier, ? extends Function<? super Double, Double>>) MOD_FUNCTIONS.get(e);
        } catch (Throwable t) {
            return null;
        }
    }

    /** The exact NMS DamageSource of a living-entity damage event (captured by CraftBukkit's modifier lambdas). */
    public static DamageSource sourceOf(EntityDamageEvent e) {
        Map<EntityDamageEvent.DamageModifier, ? extends Function<? super Double, Double>> map = functions(e);
        if (map == null) return null;
        for (Object fn : map.values()) {
            if (fn == null) continue;
            for (Field fl : fn.getClass().getDeclaredFields()) {
                if (DamageSource.class.isAssignableFrom(fl.getType())) {
                    try {
                        fl.setAccessible(true);
                        Object v = fl.get(fn);
                        if (v instanceof DamageSource) return (DamageSource) v;
                    } catch (Throwable ignored) {
                    }
                }
            }
        }
        return null;
    }

    public static double apply(EntityDamageEvent e, EntityDamageEvent.DamageModifier mod, double remaining) {
        Map<EntityDamageEvent.DamageModifier, ? extends Function<? super Double, Double>> map = functions(e);
        if (map == null) return 0;
        Function<? super Double, Double> fn = map.get(mod);
        if (fn == null) return 0;
        Double d = fn.apply(remaining);
        return d == null ? 0 : d;
    }

    // ------------------------------------------------------------------ misc reflection

    public static Object invoke(Object target, Class<?> owner, String name, Class<?>[] types, Object... args) throws Exception {
        Method m = owner.getDeclaredMethod(name, types);
        m.setAccessible(true);
        return m.invoke(target, args);
    }

    public static Object get(Object target, Class<?> owner, String name) throws Exception {
        Field fl = owner.getDeclaredField(name);
        fl.setAccessible(true);
        return fl.get(target);
    }

    public static void set(Object target, Class<?> owner, String name, Object value) throws Exception {
        Field fl = owner.getDeclaredField(name);
        fl.setAccessible(true);
        fl.set(target, value);
    }
}
