package chumbanotz.mutantbeasts.entity.ai;

import java.util.Random;
import javax.annotation.Nullable;
import net.minecraft.entity.EntityCreature;
import net.minecraft.entity.ai.EntityAIBase;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

public class EntityAIFleeRain
extends EntityAIBase {
    private final EntityCreature creature;
    private double shelterX;
    private double shelterY;
    private double shelterZ;
    private final double movementSpeed;

    public EntityAIFleeRain(EntityCreature creature, double movementSpeedIn) {
        this.creature = creature;
        this.movementSpeed = movementSpeedIn;
        this.setMutexBits(1);
    }

    public boolean shouldExecute() {
        if (this.creature.getAttackTarget() != null) {
            return false;
        }
        if (!this.creature.world.isRainingAt(new BlockPos(this.creature.posX, this.creature.getEntityBoundingBox().minY, this.creature.posZ))) {
            return false;
        }
        Vec3d vec3d = this.findPossibleShelter();
        if (vec3d == null) {
            return false;
        }
        this.shelterX = vec3d.x;
        this.shelterY = vec3d.y;
        this.shelterZ = vec3d.z;
        return true;
    }

    public boolean shouldContinueExecuting() {
        return !this.creature.getNavigator().noPath();
    }

    public void startExecuting() {
        this.creature.getNavigator().tryMoveToXYZ(this.shelterX, this.shelterY, this.shelterZ, this.movementSpeed);
    }

    @Nullable
    private Vec3d findPossibleShelter() {
        Random random = this.creature.getRNG();
        BlockPos blockpos = new BlockPos(this.creature.posX, this.creature.getEntityBoundingBox().minY, this.creature.posZ);
        for (int i = 0; i < 10; ++i) {
            BlockPos blockpos1 = blockpos.add(random.nextInt(20) - 10, random.nextInt(6) - 3, random.nextInt(20) - 10);
            if (this.creature.world.isRainingAt(blockpos1) || this.creature.world.getBlockState(blockpos1).getMaterial().isLiquid()) continue;
            return new Vec3d(blockpos1.getX(), blockpos1.getY(), blockpos1.getZ());
        }
        return null;
    }
}
