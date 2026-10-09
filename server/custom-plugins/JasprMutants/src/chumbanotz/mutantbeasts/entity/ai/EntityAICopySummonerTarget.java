package chumbanotz.mutantbeasts.entity.ai;

import chumbanotz.mutantbeasts.entity.mutant.MutantZombieEntity;
import net.minecraft.entity.EntityCreature;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.ai.EntityAITarget;
import net.minecraft.util.math.BlockPos;

public class EntityAICopySummonerTarget
extends EntityAITarget {
    private MutantZombieEntity summoner;

    public EntityAICopySummonerTarget(EntityCreature creature, MutantZombieEntity summoner) {
        super(creature, false);
        this.summoner = summoner;
        this.setMutexBits(1);
    }

    public boolean shouldExecute() {
        if (this.summoner == null) {
            return false;
        }
        this.taskOwner.setHomePosAndDistance(new BlockPos(this.summoner), 8);
        return this.summoner.getAttackTarget() != null;
    }

    public void startExecuting() {
        super.startExecuting();
        this.target = this.summoner.getAttackTarget();
        this.taskOwner.setAttackTarget(this.target);
    }

    public boolean shouldContinueExecuting() {
        if (!super.shouldContinueExecuting()) {
            return false;
        }
        EntityLivingBase attackTarget = this.summoner.getAttackTarget();
        return attackTarget == null || attackTarget == this.target || !(this.taskOwner.getDistanceSq(attackTarget) < this.taskOwner.getDistanceSq(this.target));
    }

    public void updateTask() {
        if (this.taskOwner.ticksExisted % 3 == 0) {
            this.taskOwner.setHomePosAndDistance(new BlockPos(this.summoner), 16);
        }
    }

    public void resetTask() {
        super.resetTask();
        if (!chat.jaspr.mutants.compat.ForgeAccess.isAddedToWorld(this.summoner)) { // JasperCraft port: Forge's Entity.isAddedToWorld()
            this.summoner = null;
            this.taskOwner.detachHome();
        }
    }
}
