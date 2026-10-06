package chumbanotz.mutantbeasts.item;

import chumbanotz.mutantbeasts.util.MutatedExplosion;
import com.google.common.collect.Multimap;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.ai.attributes.AttributeModifier;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.MobEffects;
import net.minecraft.init.SoundEvents;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.item.EnumRarity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.potion.PotionEffect;
import net.minecraft.stats.StatList;
import net.minecraft.util.ActionResult;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumHand;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

public class CreeperShardItem extends Item {
    // JasperCraft port: client-only (Item.hasEffect is not in the server jar; its vanilla body, isItemEnchanted, inlined)
    public boolean hasEffect(ItemStack stack) {
        return stack.isItemEnchanted() || stack.getItemDamage() <= 0;
    }

    public EnumRarity getRarity(ItemStack stack) {
        return EnumRarity.UNCOMMON;
    }

    public boolean canDestroyBlockInCreative(World world, BlockPos pos, ItemStack stack, EntityPlayer player) {
        return false;
    }

    public boolean hitEntity(ItemStack stack, EntityLivingBase target, EntityLivingBase attacker) {
        EntityPlayer player = (EntityPlayer) attacker;
        int damage = stack.getItemDamage();
        if (damage > 0) {
            stack.setItemDamage(damage - 1);
            if (!player.isCreative() && player.getRNG().nextInt(4) == 0) {
                player.addPotionEffect(new PotionEffect(MobEffects.POISON, 80 + player.getRNG().nextInt(40)));
            }
        }
        target.knockBack(player, 0.9f, player.posX - target.posX, player.posZ - target.posZ);
        player.world.playSound(null, player.posX, player.posY, player.posZ, SoundEvents.ENTITY_GENERIC_EXPLODE, SoundCategory.NEUTRAL, 0.3f, 0.8f + player.getRNG().nextFloat() * 0.4f);
        return true;
    }

    public ActionResult<ItemStack> onItemRightClick(World worldIn, EntityPlayer playerIn, EnumHand handIn) {
        ItemStack stack = playerIn.getHeldItem(handIn);
        int maxDmg = stack.getMaxDamage();
        int dmg = stack.getItemDamage();
        if (!worldIn.isRemote) {
            float damage = 5.0f * (float) (maxDmg - dmg) / 32.0f;
            if (dmg == 0) {
                damage += 2.0f;
            }
            MutatedExplosion.create(worldIn, playerIn, playerIn.posX, playerIn.posY + 1.0, playerIn.posZ, damage, false, playerIn.isAllowEdit());
        }
        if (!playerIn.capabilities.isCreativeMode) {
            stack.setItemDamage(maxDmg);
        }
        playerIn.swingArm(handIn);
        playerIn.getCooldownTracker().setCooldown(this, (maxDmg - dmg) * 2);
        playerIn.addStat(StatList.getObjectUseStats((Item) this));
        return new ActionResult(EnumActionResult.SUCCESS, stack);
    }

    // JasperCraft port: Forge's stack-aware Item.getAttributeModifiers(slot, stack) does not exist on Paper (no @Override);
    // its Forge default is the vanilla getItemAttributeModifiers(slot), which Paper's ItemStack calls, so that one
    // (below) delegates here.
    public Multimap<String, AttributeModifier> getAttributeModifiers(EntityEquipmentSlot slot, ItemStack stack) {
        Multimap multimap = super.getItemAttributeModifiers(slot);
        if (slot == EntityEquipmentSlot.MAINHAND) {
            multimap.put(SharedMonsterAttributes.ATTACK_DAMAGE.getName(), new AttributeModifier(ATTACK_DAMAGE_MODIFIER, "Weapon modifier", 2.0, 0));
            multimap.put(SharedMonsterAttributes.ATTACK_SPEED.getName(), new AttributeModifier(ATTACK_SPEED_MODIFIER, "Weapon modifier", -2.0, 0));
        }
        return multimap;
    }

    // JasperCraft port: the vanilla entry point of the attribute modifiers above (see getAttributeModifiers(slot, stack)).
    @Override
    public Multimap<String, AttributeModifier> getItemAttributeModifiers(EntityEquipmentSlot slot) {
        return this.getAttributeModifiers(slot, ItemStack.EMPTY);
    }
}
