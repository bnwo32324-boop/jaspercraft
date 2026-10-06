package chumbanotz.mutantbeasts.item;

import chumbanotz.mutantbeasts.MBConfig;
import chumbanotz.mutantbeasts.MutantBeasts;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.MobEffects;
import net.minecraft.init.SoundEvents;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.item.EnumRarity;
import net.minecraft.item.ItemArmor;
import net.minecraft.item.ItemStack;
import net.minecraft.potion.PotionEffect;
import net.minecraft.world.World;
import net.minecraftforge.common.util.EnumHelper;

public class MutantSkeletonArmorItem extends ItemArmor {
    private static final ItemArmor.ArmorMaterial MUTANT_SKELETON = EnumHelper.addArmorMaterial("mutant_skeleton", "mutantbeasts:mutant_skeleton", MBConfig.ITEMS.mutantSkeletonArmorDurability, new int[]{MBConfig.ITEMS.mutantSkeletonArmorProtectionBoots, MBConfig.ITEMS.mutantSkeletonArmorProtectionLeggings, MBConfig.ITEMS.mutantSkeletonArmorProtectionChestplate, MBConfig.ITEMS.mutantSkeletonArmorProtectionHelmet}, MBConfig.ITEMS.mutantSkeletonArmorEnchantability, SoundEvents.ENTITY_SKELETON_STEP, (float) MBConfig.ITEMS.mutantSkeletonArmorToughness);

    public MutantSkeletonArmorItem(EntityEquipmentSlot equipmentSlotIn) {
        super(MUTANT_SKELETON, 0, equipmentSlotIn);
    }

    @Override
    public EnumRarity getRarity(ItemStack stack) {
        return EnumRarity.UNCOMMON;
    }

    public void onArmorTick(World world, EntityPlayer player, ItemStack itemStack) {
        if (this.armorType == EntityEquipmentSlot.LEGS && !player.isPotionActive(MobEffects.SPEED) && MBConfig.ITEMS.mutantSkeletonLeggingsSpeed) {
            player.addPotionEffect(new PotionEffect(MobEffects.SPEED, 1, 1, false, false));
        }
        if (this.armorType == EntityEquipmentSlot.FEET && !player.isPotionActive(MobEffects.JUMP_BOOST) && MBConfig.ITEMS.mutantSkeletonBootsJumpBoost) {
            player.addPotionEffect(new PotionEffect(MobEffects.JUMP_BOOST, 1, player.isSprinting() ? 1 : 0, false, false));
        }
    }

    // JasperCraft port: Forge calls Item.onArmorTick for each worn armour stack from InventoryPlayer.decrementAnimations,
    // in the player's tick right after its potion effects were updated (so these 1-tick effects are re-added straight
    // after they expire, and players always move with them). Paper has no onArmorTick; the same loop calls Item.onUpdate
    // for every stack of every inventory list, armour included, so the armour pass of that loop calls onArmorTick.
    @Override
    public void onUpdate(ItemStack stack, World worldIn, Entity entityIn, int itemSlot, boolean isSelected) {
        if (entityIn instanceof EntityPlayer) {
            EntityPlayer player = (EntityPlayer) entityIn;
            if (itemSlot >= 0 && itemSlot < player.inventory.armorInventory.size() && player.inventory.armorInventory.get(itemSlot) == stack) {
                this.onArmorTick(worldIn, player, stack);
            }
        }
    }

    // JasperCraft port: getArmorModel (the skull's armour model) is client-only Forge rendering (ModelBiped is a client
    // class); the client stage renders it. Original:
    // public ModelBiped getArmorModel(EntityLivingBase entityLiving, ItemStack itemStack, EntityEquipmentSlot armorSlot, ModelBiped _default) {
    //     return armorSlot == EntityEquipmentSlot.HEAD ? (ModelBiped) MutantBeasts.PROXY.getMutantSkeletonArmorModel() : _default;
    // }
}
