package chumbanotz.mutantbeasts.entity.ai;

import net.minecraft.entity.EntityCreature;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.ai.EntityAIAttackMelee;
import net.minecraft.entity.ai.RandomPositionGenerator;
import net.minecraft.util.math.Vec3d;

public class MBEntityAIAttackMelee
extends EntityAIAttackMelee {
    private int maxAttackTick = 20;
    private final double moveSpeed;
    private int delayCounter;

    public MBEntityAIAttackMelee(EntityCreature entityCreature, double moveSpeed) {
        super(entityCreature, moveSpeed, true);
        this.moveSpeed = moveSpeed;
    }

    public boolean shouldExecute() {
        this.attackTick = Math.max(this.attackTick - 1, 0);
        if (this.attacker.getAttackTarget() == null) {
            return false;
        }
        if (!this.attacker.getAttackTarget().isEntityAlive()) {
            this.attacker.setAttackTarget(null);
            return false;
        }
        return true;
    }

    public void startExecuting() {
        this.delayCounter = 0;
    }

    public void updateTask() {
        EntityLivingBase target = this.attacker.getAttackTarget();
        if (target == null) {
            return;
        }
        this.attacker.getLookHelper().setLookPositionWithEntity(target, 30.0f, 30.0f);
        double distSq = this.attacker.getDistanceSq(target.posX, target.getEntityBoundingBox().minY, target.posZ);
        if (--this.delayCounter <= 0) {
            this.delayCounter = 4 + this.attacker.getRNG().nextInt(7);
            float followRange = this.attacker.getNavigator().getPathSearchRange();
            if (distSq > (double)(followRange * followRange)) {
                Vec3d vec3d;
                if (!(this.attacker.hasPath() || (vec3d = RandomPositionGenerator.findRandomTargetBlockTowards((EntityCreature)this.attacker, (int)16, (int)7, (Vec3d)target.getPositionVector())) != null && this.attacker.getNavigator().tryMoveToXYZ(vec3d.x, vec3d.y, vec3d.z, this.moveSpeed))) {
                    this.delayCounter += 5;
                }
            } else {
                this.attacker.getNavigator().tryMoveToEntityLiving(target, this.moveSpeed);
            }
        }
        this.attackTick = Math.max(this.attackTick - 1, 0);
        this.checkAndPerformAttack(target, distSq);
    }

    protected void checkAndPerformAttack(EntityLivingBase enemy, double distToEnemySqr) {
        if ((distToEnemySqr <= this.getAttackReachSqr(enemy) || this.attacker.getEntityBoundingBox().intersects(enemy.getEntityBoundingBox())) && this.attackTick <= 0) {
            this.attackTick = this.maxAttackTick;
            this.attacker.attackEntityAsMob(enemy);
        }
    }

    public void resetTask() {
        this.attacker.getNavigator().clearPath();
    }

    public MBEntityAIAttackMelee setMaxAttackTick(int max) {
        this.maxAttackTick = max;
        return this;
    }
}
