package chumbanotz.mutantbeasts.entity.ai;

import javax.annotation.Nullable;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityCreature;
import net.minecraft.entity.ai.EntityAIPanic;
import net.minecraft.entity.ai.RandomPositionGenerator;
import net.minecraft.util.DamageSource;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

public class EntityAIAvoidDamage
extends EntityAIPanic {
    public EntityAIAvoidDamage(EntityCreature creature, double speed) {
        super(creature, speed);
    }

    public boolean shouldExecute() {
        if (this.creature.isBurning()) {
            BlockPos blockpos = this.getRandPos(this.creature.world, this.creature, (int)this.creature.getNavigator().getPathSearchRange(), 4);
            if (blockpos != null && this.creature.getNavigator().getPathToPos(blockpos) != null) {
                return this.hasPosition(new Vec3d(blockpos));
            }
            return this.findRandomPosition();
        }
        if (this.creature.isChild() && this.creature.getRevengeTarget() != null) {
            return this.hasPosition(RandomPositionGenerator.findRandomTargetBlockAwayFrom((EntityCreature)this.creature, (int)16, (int)7, (Vec3d)this.creature.getRevengeTarget().getPositionVector()));
        }
        if (this.creature.getLastDamageSource() != null && this.shouldAvoidDamage(this.creature.getLastDamageSource())) {
            Vec3d damageLocation = this.creature.getLastDamageSource().getDamageLocation();
            if (damageLocation != null) {
                return this.hasPosition(RandomPositionGenerator.findRandomTargetBlockAwayFrom((EntityCreature)this.creature, (int)16, (int)7, (Vec3d)damageLocation));
            }
            return this.findRandomPosition();
        }
        return false;
    }

    private boolean hasPosition(Vec3d vec3d) {
        if (vec3d == null) {
            return false;
        }
        this.randPosX = vec3d.x;
        this.randPosY = vec3d.y;
        this.randPosZ = vec3d.z;
        return true;
    }

    private boolean shouldAvoidDamage(DamageSource source) {
        if (source.isCreativePlayer()) {
            return false;
        }
        if (source.getTrueSource() == this.creature.getAttackTarget()) {
            return false;
        }
        if (source.isMagicDamage() && source.getImmediateSource() == null) {
            return false;
        }
        return source != DamageSource.DROWN && source != DamageSource.FALL && source != DamageSource.STARVE && source != DamageSource.OUT_OF_WORLD;
    }

    @Nullable
    private BlockPos getRandPos(World worldIn, Entity entityIn, int horizontalRange, int verticalRange) {
        BlockPos blockpos = new BlockPos(entityIn);
        int i = blockpos.getX();
        int j = blockpos.getY();
        int k = blockpos.getZ();
        float f = horizontalRange * horizontalRange * verticalRange * 2;
        BlockPos blockpos1 = null;
        BlockPos.MutableBlockPos blockpos$mutableblockpos = new BlockPos.MutableBlockPos();
        for (int l = i - horizontalRange; l <= i + horizontalRange; ++l) {
            for (int i1 = j - verticalRange; i1 <= j + verticalRange; ++i1) {
                for (int j1 = k - horizontalRange; j1 <= k + horizontalRange; ++j1) {
                    float f1;
                    blockpos$mutableblockpos.setPos(l, i1, j1);
                    IBlockState iblockstate = worldIn.getBlockState(blockpos$mutableblockpos);
                    if (iblockstate.getMaterial() != Material.WATER || !((f1 = (float)((l - i) * (l - i) + (i1 - j) * (i1 - j) + (j1 - k) * (j1 - k))) < f)) continue;
                    f = f1;
                    blockpos1 = new BlockPos(blockpos$mutableblockpos);
                }
            }
        }
        return blockpos1;
    }
}
