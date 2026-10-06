package chat.jaspr.mutants.compat;

import chat.jaspr.mutants.hooks.EntityLifecycle;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import net.minecraft.block.Block;
import net.minecraft.block.SoundType;
import net.minecraft.block.state.IBlockState;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityList;
import net.minecraft.entity.passive.EntityParrot;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Enchantments;
import net.minecraft.item.Item;
import net.minecraft.item.ItemArrow;
import net.minecraft.item.ItemStack;
import net.minecraft.util.DamageSource;
import net.minecraft.util.SoundEvent;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.Explosion;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.network.NetworkRegistry;

/**
 * Members Forge adds to (or widens in) vanilla classes, for the call sites in the mod that Paper cannot compile. Each
 * method reproduces Forge 1.12.2's behaviour; the mod's call sites are marked "JasperCraft port" and listed in
 * PORT_NOTES.md. Reflection uses Paper's Spigot member names (names-1.12.2.tsv).
 */
public final class ForgeAccess {
    private static Method setMaxDurability;
    private static Method setIgnoreArmor;
    private static Method setAbsolute;
    private static Int2ObjectMap<SoundEvent> mimicSounds;

    private ForgeAccess() {
    }

    /** Item.setMaxDamage(int): protected on Paper (Item.setMaxDurability), public through Forge's access transformer. */
    public static Item setMaxDamage(Item item, int maxDamage) {
        try {
            if (setMaxDurability == null) {
                setMaxDurability = Item.class.getDeclaredMethod("setMaxDurability", int.class);
                setMaxDurability.setAccessible(true);
            }
            return (Item) setMaxDurability.invoke(item, maxDamage);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Item.setMaxDamage", e);
        }
    }

    /** DamageSource.setDamageBypassesArmor(): protected on Paper (setIgnoreArmor), public through Forge's AT. */
    public static DamageSource setDamageBypassesArmor(DamageSource source) {
        try {
            if (setIgnoreArmor == null) {
                setIgnoreArmor = DamageSource.class.getDeclaredMethod("setIgnoreArmor");
                setIgnoreArmor.setAccessible(true);
            }
            return (DamageSource) setIgnoreArmor.invoke(source);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("DamageSource.setDamageBypassesArmor", e);
        }
    }

    /** DamageSource.setDamageIsAbsolute(): protected on Paper (DamageSource.m), public through Forge's AT. */
    public static DamageSource setDamageIsAbsolute(DamageSource source) {
        try {
            if (setAbsolute == null) {
                setAbsolute = DamageSource.class.getDeclaredMethod("m");
                setAbsolute.setAccessible(true);
            }
            return (DamageSource) setAbsolute.invoke(source);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("DamageSource.setDamageIsAbsolute", e);
        }
    }

    private static Field forceDrops;

    /**
     * CraftBukkit's EntityLiving.forceDrops (package-private): while false, a living entity's dropped items are held
     * back for its death event. Forge drops them at once outside onDeath; CraftBukkit sets this flag around its own
     * non-death drops (eggs, shearing, saddles). Returns the previous value.
     */
    public static boolean setForceDrops(net.minecraft.entity.EntityLivingBase entity, boolean value) {
        try {
            if (forceDrops == null) {
                Field f = net.minecraft.entity.EntityLivingBase.class.getDeclaredField("forceDrops");
                f.setAccessible(true);
                forceDrops = f;
            }
            boolean old = forceDrops.getBoolean(entity);
            forceDrops.setBoolean(entity, value);
            return old;
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("EntityLiving.forceDrops", e);
        }
    }

    /** Forge's EntityParrot.registerMimicSound: parrots imitate this entity with the given sound. */
    @SuppressWarnings("unchecked")
    public static void registerMimicSound(Class<? extends Entity> cls, SoundEvent sound) {
        try {
            if (mimicSounds == null) {
                Field f = EntityParrot.class.getDeclaredField("bK"); // IMITATION_SOUND_EVENTS
                f.setAccessible(true);
                mimicSounds = (Int2ObjectMap<SoundEvent>) f.get(null);
            }
            mimicSounds.put(EntityList.REGISTRY.getIDForObject(cls), sound);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("EntityParrot.registerMimicSound", e);
        }
    }

    /** Forge's Entity.isAddedToWorld(): true between World.onEntityAdded and onEntityRemoved (CraftBukkit: Entity.valid). */
    public static boolean isAddedToWorld(Entity entity) {
        return EntityLifecycle.isAddedToWorld(entity);
    }

    /** Forge's ItemArrow.isInfinite(stack, bow, player). */
    public static boolean isInfinite(ItemArrow arrow, ItemStack stack, ItemStack bow, EntityPlayer player) {
        int enchant = EnchantmentHelper.getEnchantmentLevel(Enchantments.INFINITY, bow);
        return enchant > 0 && arrow.getClass() == ItemArrow.class;
    }

    /** Forge's Block.getSoundType(state, world, pos, entity): the block's sound type unless a block overrides it. */
    public static SoundType getSoundType(Block block, IBlockState state, World world, BlockPos pos, Entity entity) {
        return block.getSoundType();
    }

    /** Forge's Block.getExplosionResistance(world, pos, exploder, explosion): defaults to the vanilla getExplosionResistance(exploder). */
    public static float getExplosionResistance(Block block, World world, BlockPos pos, Entity exploder, Explosion explosion) {
        return block.getExplosionResistance(exploder);
    }

    /** Forge's Item.canApplyAtEnchantingTable default: the enchantment's type accepts the item. */
    public static boolean canApplyAtEnchantingTable(ItemStack stack, Enchantment enchantment) {
        return enchantment.type.canEnchantItem(stack.getItem());
    }

    /**
     * Forge's EntityPlayer.openGui on the server: FML asks the mod's IGuiHandler for a server container; Mutant Creatures'
     * ServerProxy returns null for its only GUI (the client-side minion tracker), so the server opens nothing.
     */
    public static void openGui(EntityPlayer player, Object mod, int modGuiId, World world, int x, int y, int z) {
        NetworkRegistry.INSTANCE.getRemoteGuiContainer(mod, modGuiId, player, world, x, y, z);
    }

    /** Vec3d.fromPitchYaw(pitch, yaw): client-only in the 1.12.2 server jar; the vanilla client's formula. */
    public static Vec3d fromPitchYaw(float pitch, float yaw) {
        float f = MathHelper.cos(-yaw * 0.017453292F - (float) Math.PI);
        float f1 = MathHelper.sin(-yaw * 0.017453292F - (float) Math.PI);
        float f2 = -MathHelper.cos(-pitch * 0.017453292F);
        float f3 = MathHelper.sin(-pitch * 0.017453292F);
        return new Vec3d((double) (f1 * f2), (double) f3, (double) (f * f2));
    }
}
