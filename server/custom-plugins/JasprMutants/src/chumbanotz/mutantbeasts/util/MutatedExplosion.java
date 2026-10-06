package chumbanotz.mutantbeasts.util;

import chumbanotz.mutantbeasts.entity.SkullSpiritEntity;
import chumbanotz.mutantbeasts.entity.mutant.MutantCreeperEntity;
import chumbanotz.mutantbeasts.entity.projectile.ChemicalXEntity;
import chumbanotz.mutantbeasts.util.EntityUtil;
import java.util.HashSet;
import java.util.List;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.enchantment.EnchantmentProtection;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.network.play.server.SPacketExplosion;
import net.minecraft.util.DamageSource;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.Explosion;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraftforge.event.ForgeEventFactory;

public class MutatedExplosion
extends Explosion {
    private final Entity exploder;
    private final World world;
    private final float size;

    // JasperCraft port: Forge adds Explosion.getPosition(); Paper keeps the explosion's x/y/z private.
    private final Vec3d position;

    private MutatedExplosion(World world, Entity exploder, double x, double y, double z, float size, boolean causesFire, boolean damagesTerrain) {
        super(world, exploder, x, y, z, size, causesFire, damagesTerrain);
        this.exploder = exploder;
        this.world = world;
        this.size = size;
        this.position = new Vec3d(x, y, z); // JasperCraft port: see getPosition()
    }

    // JasperCraft port: Forge's Explosion.getPosition() (the explosion centre).
    public Vec3d getPosition() {
        return this.position;
    }

    public void doExplosionA() {
        if (this.size <= 0.0f) {
            return;
        }
        HashSet<BlockPos> set = new HashSet<BlockPos>();
        for (int j = 0; j < 16; ++j) {
            for (int k = 0; k < 16; ++k) {
                for (int l = 0; l < 16; ++l) {
                    if (j != 0 && j != 15 && k != 0 && k != 15 && l != 0 && l != 15) continue;
                    double d0 = (float)j / 15.0f * 2.0f - 1.0f;
                    double d1 = (float)k / 15.0f * 2.0f - 1.0f;
                    double d2 = (float)l / 15.0f * 2.0f - 1.0f;
                    double d3 = Math.sqrt(d0 * d0 + d1 * d1 + d2 * d2);
                    d0 /= d3;
                    d1 /= d3;
                    d2 /= d3;
                    double x = this.getPosition().x;
                    double y = this.getPosition().y;
                    double z = this.getPosition().z;
                    float attenuation = 0.3f;
                    for (float intensity = this.size * (0.7f + this.world.rand.nextFloat() * 0.6f); intensity > 0.0f; intensity -= 0.22500001f) {
                        BlockPos blockpos = new BlockPos(x, y, z);
                        IBlockState iblockstate = this.world.getBlockState(blockpos);
                        if (!this.world.isAirBlock(blockpos) && !iblockstate.getMaterial().isLiquid()) {
                            // JasperCraft port: Block.getExplosionResistance(world, pos, exploder, explosion) is Forge's; its default is the vanilla one-argument method
                            float resistance = this.exploder != null ? this.exploder.getExplosionResistance(this, this.world, blockpos, iblockstate) : chat.jaspr.mutants.compat.ForgeAccess.getExplosionResistance(iblockstate.getBlock(), this.world, blockpos, this.exploder, this);
                            intensity -= (resistance + attenuation) * attenuation;
                        }
                        if (intensity > 0.0f && !iblockstate.getMaterial().isLiquid() && (this.exploder == null || this.exploder.canExplosionDestroyBlock(this, this.world, blockpos, iblockstate, intensity))) {
                            set.add(blockpos);
                        }
                        x += d0 * (double)0.3f;
                        y += d1 * (double)0.3f;
                        z += d2 * (double)0.3f;
                    }
                }
            }
        }
        this.getAffectedBlockPositions().addAll(set);
        float diameter = this.size * 2.0f;
        int minX = MathHelper.floor(this.getPosition().x - (double)diameter - 1.0);
        int maxX = MathHelper.floor(this.getPosition().x + (double)diameter + 1.0);
        int minY = MathHelper.floor(this.getPosition().y - (double)diameter - 1.0);
        int maxY = MathHelper.floor(this.getPosition().y + (double)diameter + 1.0);
        int minZ = MathHelper.floor(this.getPosition().z - (double)diameter - 1.0);
        int maxZ = MathHelper.floor(this.getPosition().z + (double)diameter + 1.0);
        List<Entity> list = this.world.getEntitiesInAABBexcluding(this.exploder, new AxisAlignedBB(minX, minY, minZ, maxX, maxY, maxZ), entity -> {
            if (entity.isImmuneToExplosions()) {
                return false;
            }
            if (this.exploder instanceof SkullSpiritEntity) {
                if (entity == ((SkullSpiritEntity)this.exploder).getTarget()) {
                    return !((SkullSpiritEntity)this.exploder).isAttached();
                }
                return ChemicalXEntity.IS_APPLICABLE.test(entity);
            }
            return true;
        });
        ForgeEventFactory.onExplosionDetonate(this.world, this, list, diameter);
        DamageSource damageSource = DamageSource.causeExplosionDamage(this);
        for (Entity entity2 : list) {
            EntityPlayer entityplayer;
            double z;
            double y;
            double x;
            double sqrt;
            double distance = entity2.getDistance(this.getPosition().x, this.getPosition().y, this.getPosition().z) / (double)diameter;
            if (!(distance <= 1.0) || (sqrt = MathHelper.sqrt((x = entity2.posX - this.getPosition().x) * x + (y = entity2.posY + (double)entity2.getEyeHeight() - this.getPosition().y) * y + (z = entity2.posZ - this.getPosition().z) * z)) == 0.0) continue;
            x /= sqrt;
            y /= sqrt;
            z /= sqrt;
            double intensity = (1.0 - distance) * (double)MutatedExplosion.getBlockDensity(this.getPosition(), entity2);
            float damage = (int)((intensity * intensity + intensity) / 2.0 * 6.0 * (double)diameter + 1.0);
            if (!entity2.attackEntityFrom(damageSource, damage) && this.exploder instanceof MutantCreeperEntity && entity2 instanceof EntityPlayer && ((EntityPlayer)entity2).isActiveItemStackBlocking()) {
                MutantCreeperEntity mutantCreeper = (MutantCreeperEntity)this.exploder;
                EntityPlayer player = (EntityPlayer)entity2;
                player.velocityChanged = true;
                if (mutantCreeper.isJumpAttacking()) {
                    EntityUtil.disableShield(player, mutantCreeper.getPowered() ? 200 : 100);
                    player.attackEntityFrom(damageSource, damage * 0.5f);
                } else {
                    player.getActiveItemStack().damageItem((int)damage * 2, player);
                    player.attackEntityFrom(damageSource, damage * 0.5f);
                }
            }
            double scale = intensity;
            if (entity2 instanceof EntityLivingBase) {
                scale = EnchantmentProtection.getBlastDamageReduction((EntityLivingBase) entity2, intensity);
            }
            if (!(entity2 instanceof MutantCreeperEntity)) {
                entity2.motionX += x * scale;
                entity2.motionY += y * scale;
                entity2.motionZ += z * scale;
            }
            if (!(entity2 instanceof EntityPlayer) || (entityplayer = (EntityPlayer)entity2).isSpectator() || entityplayer.isCreative() && entityplayer.capabilities.isFlying) continue;
            this.getPlayerKnockbackMap().put(entityplayer, new Vec3d(x * intensity, y * intensity, z * intensity));
        }
    }

    public static MutatedExplosion create(@Nonnull Entity exploder, float size, boolean causesFire, boolean damagesTerrain) {
        return MutatedExplosion.create(exploder.world, exploder, exploder.posX, exploder.posY, exploder.posZ, size, causesFire, damagesTerrain);
    }

    public static MutatedExplosion create(World world, @Nullable Entity exploder, double x, double y, double z, float size, boolean causesFire, boolean damagesTerrain) {
        MutatedExplosion explosion;
        if (damagesTerrain && exploder instanceof EntityLiving && !ForgeEventFactory.getMobGriefingEvent(world, exploder)) {
            damagesTerrain = false;
        }
        if (ForgeEventFactory.onExplosionStart(world, explosion = new MutatedExplosion(world, exploder, x, y, z, size, causesFire, damagesTerrain))) {
            return explosion;
        }
        if (world instanceof WorldServer) {
            explosion.doExplosionA();
            explosion.doExplosionB(false);
            // JasperCraft port: CraftBukkit's doExplosionB fires EntityExplodeEvent; when a plugin cancels it, no block
            // breaks and, as in CraftBukkit's own explosions, no explosion packet is sent (clients would remove blocks).
            if (explosion.wasCanceled) {
                return explosion;
            }
            if (!damagesTerrain) {
                explosion.clearAffectedBlockPositions();
            }
            for (EntityPlayer entityplayer : world.playerEntities) {
                if (!(entityplayer.getDistanceSq(x, y, z) < 4096.0)) continue;
                ((EntityPlayerMP) entityplayer).connection.sendPacket(new SPacketExplosion(x, y, z, size, explosion.getAffectedBlockPositions(), explosion.getPlayerKnockbackMap().get(entityplayer)));
            }
        }
        return explosion;
    }

    private static float getBlockDensity(Vec3d vec, Entity entity) {
        AxisAlignedBB bb = entity.getEntityBoundingBox();
        double d0 = 1.0 / ((bb.maxX - bb.minX) * 2.0 + 1.0);
        double d1 = 1.0 / ((bb.maxY - bb.minY) * 2.0 + 1.0);
        double d2 = 1.0 / ((bb.maxZ - bb.minZ) * 2.0 + 1.0);
        double d3 = (1.0 - Math.floor(1.0 / d0) * d0) / 2.0;
        double d4 = (1.0 - Math.floor(1.0 / d2) * d2) / 2.0;
        if (d0 >= 0.0 && d1 >= 0.0 && d2 >= 0.0) {
            int j2 = 0;
            int k2 = 0;
            float f = 0.0f;
            while (f <= 1.0f) {
                float f1 = 0.0f;
                while (f1 <= 1.0f) {
                    float f2 = 0.0f;
                    while (f2 <= 1.0f) {
                        double d5 = bb.minX + (bb.maxX - bb.minX) * (double)f;
                        double d6 = bb.minY + (bb.maxY - bb.minY) * (double)f1;
                        double d7 = bb.minZ + (bb.maxZ - bb.minZ) * (double)f2;
                        RayTraceResult result = entity.world.rayTraceBlocks(new Vec3d(d5 + d3, d6, d7 + d4), vec, false, true, false);
                        if (result == null || result.typeOfHit == RayTraceResult.Type.MISS || result.typeOfHit == RayTraceResult.Type.BLOCK && entity.world.getBlockState(result.getBlockPos()).getBoundingBox(entity.world, result.getBlockPos()) != Block.FULL_BLOCK_AABB) {
                            ++j2;
                        }
                        ++k2;
                        f2 = (float)((double)f2 + d2);
                    }
                    f1 = (float)((double)f1 + d1);
                }
                f = (float)((double)f + d0);
            }
            return (float)j2 / (float)k2;
        }
        return 0.0f;
    }
}
