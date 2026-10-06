package chumbanotz.mutantbeasts.pathfinding;

import java.util.EnumSet;
import net.minecraft.block.Block;
import net.minecraft.block.BlockBasePressurePlate;
import net.minecraft.block.BlockCactus;
import net.minecraft.block.BlockDoor;
import net.minecraft.block.BlockEndPortal;
import net.minecraft.block.BlockFence;
import net.minecraft.block.BlockFenceGate;
import net.minecraft.block.BlockFire;
import net.minecraft.block.BlockLilyPad;
import net.minecraft.block.BlockMagma;
import net.minecraft.block.BlockPortal;
import net.minecraft.block.BlockRailBase;
import net.minecraft.block.BlockSoulSand;
import net.minecraft.block.BlockTrapDoor;
import net.minecraft.block.BlockTripWire;
import net.minecraft.block.BlockWall;
import net.minecraft.block.BlockWeb;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.pathfinding.PathNodeType;
import net.minecraft.pathfinding.WalkNodeProcessor;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;

public class MBWalkNodeProcessor
extends WalkNodeProcessor {
    public PathNodeType getPathNodeType(IBlockAccess blockAccess, int x, int y, int z, int xSize, int ySize, int zSize, boolean canOpenDoorsIn, boolean canEnterDoorsIn, EnumSet<PathNodeType> pathNodeTypes, PathNodeType pathNodeType, BlockPos blockPos) {
        for (int i = 0; i < xSize; ++i) {
            for (int j = 0; j < ySize; ++j) {
                for (int k = 0; k < zSize; ++k) {
                    int l = i + x;
                    int i1 = j + y;
                    int j1 = k + z;
                    PathNodeType pathnodetype = this.getPathNodeType(blockAccess, l, i1, j1);
                    if (pathnodetype == PathNodeType.DOOR_WOOD_CLOSED && canOpenDoorsIn && canEnterDoorsIn) {
                        pathnodetype = PathNodeType.WALKABLE;
                    }
                    if (pathnodetype == PathNodeType.DOOR_OPEN && !canEnterDoorsIn) {
                        pathnodetype = PathNodeType.BLOCKED;
                    }
                    if (i == 0 && j == 0 && k == 0) {
                        pathNodeType = pathnodetype;
                    }
                    pathNodeTypes.add(pathnodetype);
                }
            }
        }
        return pathNodeType;
    }

    public PathNodeType getPathNodeType(IBlockAccess blockaccessIn, int x, int y, int z) {
        PathNodeType pathNodeAbove = this.getPathNodeTypeRaw(blockaccessIn, x, y, z);
        if (pathNodeAbove == PathNodeType.OPEN && y >= 1) {
            PathNodeType pathNodeBelow = this.getPathNodeTypeRaw(blockaccessIn, x, y - 1, z);
            pathNodeAbove = pathNodeBelow != PathNodeType.WALKABLE && pathNodeBelow != PathNodeType.OPEN && pathNodeBelow != PathNodeType.WATER && pathNodeBelow != PathNodeType.LAVA ? PathNodeType.WALKABLE : PathNodeType.OPEN;
            switch (pathNodeBelow) {
                case DAMAGE_FIRE: 
                case DAMAGE_CACTUS: 
                case DAMAGE_OTHER: 
                case DANGER_OTHER: {
                    pathNodeAbove = pathNodeBelow;
                    break;
                }
            }
        }
        pathNodeAbove = this.checkNeighborBlocks(blockaccessIn, x, y, z, pathNodeAbove);
        return pathNodeAbove;
    }

    public PathNodeType checkNeighborBlocks(IBlockAccess blockaccessIn, int x, int y, int z, PathNodeType pathNode) {
        BlockPos.PooledMutableBlockPos blockpos$pooledmutableblockpos = BlockPos.PooledMutableBlockPos.retain();
        if (pathNode == PathNodeType.WALKABLE) {
            for (int i = -1; i <= 1; ++i) {
                block6: for (int j = -1; j <= 1; ++j) {
                    if (i == 0 && j == 0) continue;
                    BlockPos.PooledMutableBlockPos pos = blockpos$pooledmutableblockpos.setPos(i + x, y, j + z);
                    PathNodeType rawType = this.getPathNodeTypeRaw(blockaccessIn, pos.getX(), pos.getY(), pos.getZ());
                    switch (rawType) {
                        case DAMAGE_CACTUS: {
                            pathNode = PathNodeType.DANGER_CACTUS;
                            continue block6;
                        }
                        case DAMAGE_FIRE: {
                            pathNode = PathNodeType.DANGER_FIRE;
                            continue block6;
                        }
                        case DANGER_OTHER: 
                        case LAVA: {
                            pathNode = rawType;
                            continue block6;
                        }
                    }
                }
            }
        }
        blockpos$pooledmutableblockpos.release();
        return pathNode;
    }

    protected PathNodeType getPathNodeTypeRaw(IBlockAccess blockaccessIn, int x, int y, int z) {
        BlockPos blockpos = new BlockPos(x, y, z);
        IBlockState iblockstate = blockaccessIn.getBlockState(blockpos);
        Block block = iblockstate.getBlock();
        Material material = iblockstate.getMaterial();
        PathNodeType forgeType = block.getAiPathNodeType(iblockstate, blockaccessIn, blockpos);
        if (forgeType != null) {
            return forgeType;
        }
        if (blockaccessIn.isAirBlock(blockpos)) {
            return PathNodeType.OPEN;
        }
        if (!(block instanceof BlockTrapDoor) && !(block instanceof BlockLilyPad)) {
            if (block instanceof BlockFire || block instanceof BlockMagma) {
                return PathNodeType.DAMAGE_FIRE;
            }
            if (block instanceof BlockCactus) {
                return PathNodeType.DAMAGE_CACTUS;
            }
            if (block instanceof BlockEndPortal || block instanceof BlockPortal) {
                return PathNodeType.DAMAGE_OTHER;
            }
            if (block instanceof BlockWeb || block instanceof BlockSoulSand || block instanceof BlockBasePressurePlate || block instanceof BlockTripWire) {
                return PathNodeType.DANGER_OTHER;
            }
            if (block instanceof BlockDoor && material == Material.WOOD && !((Boolean)iblockstate.getValue(BlockDoor.OPEN)).booleanValue()) {
                return PathNodeType.DOOR_WOOD_CLOSED;
            }
            if (block instanceof BlockDoor && material == Material.IRON && !((Boolean)iblockstate.getValue(BlockDoor.OPEN)).booleanValue()) {
                return PathNodeType.DOOR_IRON_CLOSED;
            }
            if (block instanceof BlockDoor && ((Boolean)iblockstate.getValue(BlockDoor.OPEN)).booleanValue()) {
                return PathNodeType.DOOR_OPEN;
            }
            if (block instanceof BlockRailBase) {
                return PathNodeType.RAIL;
            }
            if (!(block instanceof BlockFence || block instanceof BlockWall || block instanceof BlockFenceGate && !((Boolean)iblockstate.getValue(BlockFenceGate.OPEN)).booleanValue())) {
                if (material == Material.WATER) {
                    return PathNodeType.WATER;
                }
                if (material == Material.LAVA) {
                    return PathNodeType.LAVA;
                }
                return block.isPassable(blockaccessIn, blockpos) ? PathNodeType.OPEN : PathNodeType.BLOCKED;
            }
            return PathNodeType.FENCE;
        }
        return PathNodeType.TRAPDOOR;
    }
}
