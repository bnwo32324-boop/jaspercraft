package chumbanotz.mutantbeasts.item;

import chumbanotz.mutantbeasts.MBConfig;
import chumbanotz.mutantbeasts.entity.mutant.MutantEndermanEntity;
import chumbanotz.mutantbeasts.entity.projectile.ThrowableBlockEntity;
import chumbanotz.mutantbeasts.util.EntityUtil;
import chumbanotz.mutantbeasts.util.MBParticles;
import com.google.common.collect.Multimap;
import net.minecraft.block.state.IBlockState;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnumEnchantmentType;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.ai.attributes.AttributeModifier;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Enchantments;
import net.minecraft.init.SoundEvents;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.item.EnumRarity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.stats.StatList;
import net.minecraft.util.ActionResult;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.world.World;

public class EndersoulHandItem extends Item {
    public EnumRarity getRarity(ItemStack stack) {
        return EnumRarity.EPIC;
    }

    public EntityEquipmentSlot getEquipmentSlot(ItemStack stack) {
        return EntityEquipmentSlot.MAINHAND;
    }

    public boolean canDestroyBlockInCreative(World world, BlockPos pos, ItemStack stack, EntityPlayer player) {
        return false;
    }

    public boolean hitEntity(ItemStack stack, EntityLivingBase target, EntityLivingBase attacker) {
        stack.damageItem(1, attacker);
        return true;
    }

    public int getItemEnchantability() {
        return MBConfig.ITEMS.endersoulHandEnchantability;
    }

    // JasperCraft port: Forge's Item.canApplyAtEnchantingTable (no @Override on Paper; super is Forge's default, in
    // ForgeAccess); chat.jaspr.mutants.hooks.EnchantingHooks makes the enchanting table use it, as Forge's does.
    public boolean canApplyAtEnchantingTable(ItemStack stack, Enchantment enchantment) {
        return chat.jaspr.mutants.compat.ForgeAccess.canApplyAtEnchantingTable(stack, enchantment) || enchantment.type == EnumEnchantmentType.WEAPON && enchantment != Enchantments.SWEEPING;
    }

    public EnumActionResult onItemUse(EntityPlayer player, World worldIn, BlockPos pos, EnumHand hand, EnumFacing facing, float hitX, float hitY, float hitZ) {
        IBlockState blockState = worldIn.getBlockState(pos);
        if (player.isSneaking()) {
            return EnumActionResult.PASS;
        }
        if (!EndersoulHandItem.canCarry(worldIn, pos, blockState)) {
            return EnumActionResult.FAIL;
        }
        if (!worldIn.isBlockModifiable(player, pos)) { // JasperCraft port: Forge's World.canMineBlockBody; on WorldServer it is vanilla isBlockModifiable (spawn protection, world border)
            return EnumActionResult.FAIL;
        }
        if (!player.canPlayerEdit(pos, facing, player.getHeldItem(hand))) {
            return EnumActionResult.FAIL;
        }
        if (worldIn.getTileEntity(pos) != null) {
            return EnumActionResult.FAIL;
        }
        if (!worldIn.isRemote) {
            worldIn.spawnEntity(new ThrowableBlockEntity(worldIn, player, blockState, pos));
            worldIn.setBlockToAir(pos);
        }
        return EnumActionResult.SUCCESS;
    }

    public ActionResult<ItemStack> onItemRightClick(World worldIn, EntityPlayer playerIn, EnumHand handIn) {
        ItemStack stack = playerIn.getHeldItem(handIn);
        if (!playerIn.isSneaking() || !MBConfig.ITEMS.endersoulHandTeleports) {
            return new ActionResult(EnumActionResult.PASS, stack);
        }
        RayTraceResult result = EndersoulHandItem.rayTrace(playerIn, MBConfig.ITEMS.endersoulHandTeleportationRadius);
        if (result == null || result.typeOfHit != RayTraceResult.Type.BLOCK) {
            playerIn.sendStatusMessage(new TextComponentTranslation("Unable to teleport to location", new Object[0]), true);
            return new ActionResult(EnumActionResult.FAIL, stack);
        }
        if (!worldIn.isRemote) {
            BlockPos startPos = result.getBlockPos();
            BlockPos endPos = startPos.offset(result.sideHit);
            BlockPos posDown = startPos.down();
            if (!worldIn.isAirBlock(posDown) || !worldIn.getBlockState(posDown).getMaterial().blocksMovement()) {
                for (int tries = 0; tries < 3; ++tries) {
                    BlockPos checkPos = startPos.up(tries + 1);
                    if (!worldIn.isAirBlock(checkPos)) continue;
                    endPos = checkPos;
                    break;
                }
            }
            worldIn.playSound(null, playerIn.prevPosX, playerIn.prevPosY, playerIn.prevPosZ, SoundEvents.ITEM_CHORUS_FRUIT_TELEPORT, playerIn.getSoundCategory(), 1.0f, 1.0f);
            playerIn.setPositionAndUpdate((double) endPos.getX() + 0.5, endPos.getY(), (double) endPos.getZ() + 0.5);
            worldIn.playSound(null, playerIn.posX, playerIn.posY, playerIn.posZ, SoundEvents.ITEM_CHORUS_FRUIT_TELEPORT, playerIn.getSoundCategory(), 1.0f, 1.0f);
            MutantEndermanEntity.teleportAttack(playerIn);
            EntityUtil.sendParticlePacket(playerIn, MBParticles.ENDERSOUL, 256);
            if (MBConfig.ITEMS.endersoulHandCooldown > 0)
                playerIn.getCooldownTracker().setCooldown(this, MBConfig.ITEMS.endersoulHandCooldown);
            stack.damageItem(MBConfig.ITEMS.endersoulHandTeleportationCost, playerIn);
        }
        playerIn.fallDistance = 0.0f;
        playerIn.swingArm(handIn);
        playerIn.addStat(StatList.getObjectUseStats((Item) this));
        return new ActionResult(EnumActionResult.SUCCESS, stack);
    }

    // JasperCraft port: Forge's stack-aware Item.getAttributeModifiers(slot, stack) does not exist on Paper (no @Override);
    // its Forge default is the vanilla getItemAttributeModifiers(slot), which Paper's ItemStack calls, so that one
    // (below) delegates here.
    public Multimap<String, AttributeModifier> getAttributeModifiers(EntityEquipmentSlot slot, ItemStack stack) {
        Multimap multimap = super.getItemAttributeModifiers(slot);
        if (slot == EntityEquipmentSlot.MAINHAND) {
            multimap.put(SharedMonsterAttributes.ATTACK_DAMAGE.getName(), new AttributeModifier(ATTACK_DAMAGE_MODIFIER, "Weapon modifier", MBConfig.ITEMS.endersoulHandDamage - 1.0D, 0));
            multimap.put(SharedMonsterAttributes.ATTACK_SPEED.getName(), new AttributeModifier(ATTACK_SPEED_MODIFIER, "Weapon modifier", MBConfig.ITEMS.endersoulHandAttackSpeed - 4.0D, 0));
        }
        return multimap;
    }

    public static RayTraceResult rayTrace(EntityPlayer player, double blockReachDistance) {
        Vec3d vec3d = player.getPositionEyes(1.0f);
        Vec3d vec3d1 = player.getLook(1.0f);
        Vec3d vec3d2 = vec3d.add(vec3d1.x * blockReachDistance, vec3d1.y * blockReachDistance, vec3d1.z * blockReachDistance);
        return player.world.rayTraceBlocks(vec3d, vec3d2, false, true, false);
    }

    public static boolean canCarry(World world, BlockPos pos, IBlockState blockState) {
        return blockState.isOpaqueCube() && blockState.getBlockHardness(world, pos) > -1.0f;
    }

    // JasperCraft port: the vanilla entry point of the attribute modifiers above (see getAttributeModifiers(slot, stack)).
    @Override
    public Multimap<String, AttributeModifier> getItemAttributeModifiers(EntityEquipmentSlot slot) {
        return this.getAttributeModifiers(slot, ItemStack.EMPTY);
    }
}
