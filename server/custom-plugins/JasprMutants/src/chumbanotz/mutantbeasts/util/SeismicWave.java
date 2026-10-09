package chumbanotz.mutantbeasts.util;

import chumbanotz.mutantbeasts.util.ZombieResurrection;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.block.Block;
import net.minecraft.block.BlockDirt;
import net.minecraft.block.BlockDoor;
import net.minecraft.block.BlockTNT;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

public class SeismicWave
extends BlockPos {
    private boolean first;
    private boolean spawnParticles = true;

    public SeismicWave(int x, int y, int z) {
        super(x, y, z);
    }

    public SeismicWave(int x, int y, int z, boolean spawnParticles) {
        this(x, y, z);
        this.spawnParticles = spawnParticles;
    }

    public boolean isFirst() {
        return this.first;
    }

    public static void createWaves(World world, List<SeismicWave> list, int x1, int z1, int x2, int z2, int y) {
        int deltaX = x2 - x1;
        int deltaZ = z2 - z1;
        int xStep = deltaX < 0 ? -1 : 1;
        int zStep = deltaZ < 0 ? -1 : 1;
        deltaX = Math.abs(deltaX);
        deltaZ = Math.abs(deltaZ);
        int x = x1;
        int z = z1;
        int deltaX2 = deltaX * 2;
        int deltaZ2 = deltaZ * 2;
        SeismicWave.addWave(world, list, x1, y, z1, true);
        if (deltaX2 >= deltaZ2) {
            int error = deltaX;
            for (int i = 0; i < deltaX; ++i) {
                x += xStep;
                if ((error += deltaZ2) > deltaX2) {
                    z += zStep;
                    error -= deltaX2;
                }
                SeismicWave.addWave(world, list, x, y, z, false);
            }
        } else {
            int error = deltaZ;
            for (int i = 0; i < deltaZ; ++i) {
                z += zStep;
                if ((error += deltaX2) > deltaZ2) {
                    x += xStep;
                    error -= deltaZ2;
                }
                SeismicWave.addWave(world, list, x, y, z, false);
            }
        }
    }

    @Nullable
    public static SeismicWave addWave(World world, List<SeismicWave> list, int x, int y, int z, boolean first) {
        y = ZombieResurrection.getSuitableGround(world, x, y, z, 3, false);
        SeismicWave wave = null;
        if (y != -1 || first) {
            wave = new SeismicWave(x, y, z);
            if (first) {
                wave.first = true;
            }
            list.add(wave);
        }
        if (world.rand.nextInt(2) == 0) {
            list.add(new SeismicWave(x, y + 1, z, false));
        }
        return wave;
    }

    public void affectBlocks(World world, EntityLivingBase entity) {
        EntityPlayer player;
        if (!this.spawnParticles) {
            return;
        }
        IBlockState blockState = world.getBlockState(this);
        Block block = blockState.getBlock();
        BlockPos posUp = new BlockPos(this.up());
        EntityPlayer entityPlayer = player = entity instanceof EntityPlayer ? (EntityPlayer)entity : null;
        if (player != null && player.isAllowEdit() || world.getGameRules().getBoolean("mobGriefing")) {
            if (block == Blocks.GRASS || block == Blocks.GRASS_PATH || block == Blocks.FARMLAND || block == Blocks.MYCELIUM || block instanceof BlockDirt && blockState.getValue(BlockDirt.VARIANT) == BlockDirt.DirtType.PODZOL) {
                world.setBlockState(this, Blocks.DIRT.getDefaultState(), 2);
            }
            IBlockState blockStateUp = world.getBlockState(posUp);
            float hardness = blockStateUp.getBlockHardness(world, posUp);
            if (!blockStateUp.isFullBlock() && hardness > -1.0f && hardness <= 1.0f) {
                world.destroyBlock(posUp, player != null);
            }
            if (blockStateUp.getBlock() instanceof BlockDoor) {
                if (blockStateUp.getMaterial() == Material.WOOD) {
                    world.playEvent(1019, posUp, 0);
                } else if (blockStateUp.getMaterial() == Material.IRON) {
                    world.playEvent(1020, posUp, 0);
                }
            }
            if (block instanceof BlockTNT) {
                ((BlockTNT)((Object)block)).explode(world, this, blockState.withProperty(BlockTNT.EXPLODE, Boolean.valueOf(true)), player);
                world.setBlockToAir(this);
            }
        }
        if (block == Blocks.REDSTONE_ORE) {
            block.onEntityWalk(world, this, entity);
        }
        world.playEvent(2001, posUp, Block.getStateId((IBlockState)blockState));
    }
}
