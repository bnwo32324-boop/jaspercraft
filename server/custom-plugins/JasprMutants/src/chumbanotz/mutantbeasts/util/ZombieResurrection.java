package chumbanotz.mutantbeasts.util;

import chumbanotz.mutantbeasts.entity.ai.EntityAICopySummonerTarget;
import chumbanotz.mutantbeasts.entity.mutant.MutantZombieEntity;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityCreature;
import net.minecraft.entity.EntityList;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.EnumCreatureType;
import net.minecraft.entity.IEntityLivingData;
import net.minecraft.entity.monster.EntityHusk;
import net.minecraft.entity.monster.EntityZombie;
import net.minecraft.entity.monster.EntityZombieVillager;
import net.minecraft.init.Blocks;
import net.minecraft.util.WeightedRandom;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.biome.Biome;

public class ZombieResurrection
extends BlockPos {
    private int tick;
    private final World world;

    public ZombieResurrection(World world, int x, int y, int z) {
        super(x, y, z);
        this.world = world;
        this.tick = 100 + world.rand.nextInt(40);
    }

    public ZombieResurrection(World world, BlockPos pos, int tick) {
        super(pos);
        this.world = world;
        this.tick = tick;
    }

    public int getTick() {
        return this.tick;
    }

    public boolean update(MutantZombieEntity mutantZombie) {
        if (this.world.isAirBlock(this) || !this.world.getBlockState(this).getMaterial().blocksMovement()) {
            return false;
        }
        if (mutantZombie.getRNG().nextInt(15) == 0) {
            this.world.playEvent(2001, this.up(), Block.getStateId((IBlockState)this.world.getBlockState(this)));
        }
        if (--this.tick <= 0) {
            BlockPos posUp = this.up();
            EntityZombie zombie = (EntityZombie)EntityList.newEntity(ZombieResurrection.getZombieByLocation(this.world, posUp), (World)this.world);
            IEntityLivingData entityLivingData = null;
            entityLivingData = zombie.onInitialSpawn(this.world.getDifficultyForLocation(this), entityLivingData);
            zombie.setHealth(zombie.getMaxHealth() * (0.6f + 0.4f * zombie.getRNG().nextFloat()));
            zombie.playLivingSound();
            zombie.dismountRidingEntity();
            this.world.playEvent(2001, posUp, Block.getStateId((IBlockState)this.world.getBlockState(this)));
            if (!this.world.isRemote) {
                zombie.targetTasks.addTask(0, new EntityAICopySummonerTarget((EntityCreature)zombie, mutantZombie));
                zombie.moveToBlockPosAndAngles(posUp, mutantZombie.rotationYaw, 0.0f);
                this.world.spawnEntity(zombie);
            }
            return false;
        }
        return true;
    }

    public static int getSuitableGround(World world, int x, int y, int z) {
        return ZombieResurrection.getSuitableGround(world, x, y, z, 4, true);
    }

    public static int getSuitableGround(World world, int x, int y, int z, int range, boolean checkDay) {
        int i = y;
        BlockPos.MutableBlockPos checkPos = new BlockPos.MutableBlockPos(x, y, z);
        BlockPos.MutableBlockPos abovePos = new BlockPos.MutableBlockPos(checkPos);
        while (Math.abs(y - i) <= range) {
            checkPos.setY(i);
            abovePos.setY(i + 1);
            IBlockState blockState = world.getBlockState(checkPos);
            IBlockState blockStateUp = world.getBlockState(abovePos);
            if (blockState.getBlock() != Blocks.FIRE) {
                BlockPos pos1;
                float f;
                if (blockState.getBlock() != Blocks.LAVA) {
                    if (world.isAirBlock(checkPos)) {
                        --i;
                        continue;
                    }
                    if (!world.isAirBlock(checkPos) && world.isAirBlock(abovePos) && blockState.getBlock().isPassable(world, checkPos)) {
                        --i;
                    } else if (!(world.isAirBlock(checkPos) || world.isAirBlock(abovePos) || blockStateUp.getBlock().isPassable(world, abovePos))) {
                        ++i;
                        continue;
                    }
                }
                if (checkDay && world.isDaytime() && (f = world.getLightBrightness(pos1 = new BlockPos(x, y + 1, z))) > 0.5f && world.canSeeSky(pos1) && world.rand.nextInt(3) != 0) {
                    return -1;
                }
                return i;
            }
            return -1;
        }
        return -1;
    }

    public static boolean canBeResurrected(Class<? extends Entity> entityClass) {
        return entityClass == EntityZombie.class || entityClass == EntityZombieVillager.class || entityClass == EntityHusk.class;
    }

    public static Class<? extends EntityLiving> getZombieByLocation(World world, BlockPos pos) {
        ArrayList<Biome.SpawnListEntry> zombieEntries = null;
        for (Biome.SpawnListEntry entry : world.getBiome(pos).getSpawnableList(EnumCreatureType.MONSTER)) {
            if (!ZombieResurrection.canBeResurrected(entry.entityClass)) continue;
            if (zombieEntries == null) {
                zombieEntries = new ArrayList<Biome.SpawnListEntry>();
            }
            zombieEntries.add(entry);
        }
        return zombieEntries == null || zombieEntries.isEmpty() ? EntityZombie.class : ((Biome.SpawnListEntry)((Object)WeightedRandom.getRandomItem((Random)world.rand, (List)zombieEntries))).entityClass;
    }
}
