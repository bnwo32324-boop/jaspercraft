package chumbanotz.mutantbeasts.item;

import chumbanotz.mutantbeasts.entity.projectile.ChemicalXEntity;
import net.minecraft.block.BlockDispenser;
import net.minecraft.dispenser.BehaviorProjectileDispense;
import net.minecraft.dispenser.IPosition;
import net.minecraft.entity.IProjectile;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.SoundEvents;
import net.minecraft.item.EnumRarity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.stats.StatList;
import net.minecraft.util.ActionResult;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumHand;
import net.minecraft.util.SoundCategory;
import net.minecraft.world.World;

public class ChemicalXItem extends Item {
    public ChemicalXItem() {
        BlockDispenser.DISPENSE_BEHAVIOR_REGISTRY.putObject(this, (source, stack) -> new BehaviorProjectileDispense() {

            protected IProjectile getProjectileEntity(World worldIn, IPosition position, ItemStack stackIn) {
                return new ChemicalXEntity(worldIn, position.getX(), position.getY(), position.getZ());
            }

            protected float getProjectileInaccuracy() {
                return super.getProjectileInaccuracy() * 0.5f;
            }

            protected float getProjectileVelocity() {
                return super.getProjectileVelocity() * 1.25f;
            }
        }.dispense(source, stack));
    }

    public EnumRarity getRarity(ItemStack stack) {
        return EnumRarity.EPIC;
    }

    public ActionResult<ItemStack> onItemRightClick(World worldIn, EntityPlayer playerIn, EnumHand handIn) {
        ItemStack itemstack = playerIn.getHeldItem(handIn);
        worldIn.playSound(null, playerIn.posX, playerIn.posY, playerIn.posZ, SoundEvents.ENTITY_SPLASH_POTION_THROW, SoundCategory.PLAYERS, 0.5f, 0.4f / (itemRand.nextFloat() * 0.4f + 0.8f));
        if (!worldIn.isRemote) {
            ChemicalXEntity chemicalXEntity = new ChemicalXEntity(worldIn, playerIn);
            chemicalXEntity.shoot(playerIn, playerIn.rotationPitch, playerIn.rotationYaw, -20.0f, 0.5f, 1.0f);
            worldIn.spawnEntity(chemicalXEntity);
        }
        if (!playerIn.capabilities.isCreativeMode) {
            itemstack.shrink(1);
        }
        playerIn.addStat(StatList.getObjectUseStats((Item) this));
        return new ActionResult(EnumActionResult.SUCCESS, itemstack);
    }
}
