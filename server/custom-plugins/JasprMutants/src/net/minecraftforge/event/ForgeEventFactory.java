package net.minecraftforge.event;

import chat.jaspr.mutants.hooks.BukkitBridge;
import java.util.List;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.passive.EntityAnimal;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.Explosion;
import net.minecraft.world.World;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.world.ExplosionEvent;

/**
 * JasperCraft port shim of the ForgeEventFactory methods the mod calls. Each one keeps Forge 1.12.2's result when
 * nothing listens, and is wired to the closest Bukkit event so other plugins can veto as they do for vanilla:
 * explosion start = ExplosionPrimeEvent, animal tame = EntityTameEvent (see chat.jaspr.mutants.hooks.BukkitBridge).
 */
public class ForgeEventFactory {
    /** Forge: EntityMobGriefingEvent, DEFAULT = the mobGriefing game rule. */
    public static boolean getMobGriefingEvent(World world, Entity entity) {
        return world.getGameRules().getBoolean("mobGriefing");
    }

    /** Forge: posts ExplosionEvent.Start and returns true when cancelled. Bukkit: ExplosionPrimeEvent. */
    public static boolean onExplosionStart(World world, Explosion explosion) {
        if (MinecraftForge.EVENT_BUS.post(new ExplosionEvent.Start(world, explosion))) return true;
        return BukkitBridge.explosionPrimeCancelled(world, explosion);
    }

    /** Forge: posts ExplosionEvent.Detonate with the list the explosion will damage (the mod removes Creeper Shards). */
    public static void onExplosionDetonate(World world, Explosion explosion, List<Entity> list, double diameter) {
        MinecraftForge.EVENT_BUS.post(new ExplosionEvent.Detonate(world, explosion, list));
    }

    /** Forge: LivingExperienceDropEvent; no listeners on this server, so the original amount. */
    public static int getExperienceDrop(EntityLivingBase entity, EntityPlayer attackingPlayer, int originalExperience) {
        return originalExperience;
    }

    /** Forge: posts AnimalTameEvent and returns true when cancelled. Bukkit: EntityTameEvent. */
    public static boolean onAnimalTame(EntityAnimal animal, EntityPlayer tamer) {
        return BukkitBridge.tameCancelled(animal, tamer);
    }

    /** Forge: LivingDestroyBlockEvent, not cancelled when nothing listens. */
    public static boolean onEntityDestroyBlock(EntityLivingBase entity, BlockPos pos, IBlockState state) {
        return true;
    }
}
