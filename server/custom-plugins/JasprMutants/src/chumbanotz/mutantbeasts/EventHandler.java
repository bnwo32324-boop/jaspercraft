package chumbanotz.mutantbeasts;

import chumbanotz.mutantbeasts.entity.EndersoulFragmentEntity;
import chumbanotz.mutantbeasts.entity.mutant.MutantCreeperEntity;
import chumbanotz.mutantbeasts.entity.mutant.MutantZombieEntity;
import chumbanotz.mutantbeasts.entity.mutant.SpiderPigEntity;
import chumbanotz.mutantbeasts.item.HulkHammerItem;
import chumbanotz.mutantbeasts.item.MBItems;
import chumbanotz.mutantbeasts.util.EntityUtil;
import chumbanotz.mutantbeasts.util.MBParticles;
import chumbanotz.mutantbeasts.util.SeismicWave;

import java.util.List;

import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityCreature;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.ai.EntityAIAvoidEntity;
import net.minecraft.entity.ai.EntityAITempt;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.passive.EntityOcelot;
import net.minecraft.entity.passive.EntityPig;
import net.minecraft.entity.passive.EntityVillager;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.projectile.EntityArrow;
import net.minecraft.init.Enchantments;
import net.minecraft.init.Items;
import net.minecraft.init.MobEffects;
import net.minecraft.init.SoundEvents;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.item.EnumAction;
import net.minecraft.item.Item;
import net.minecraft.item.ItemArrow;
import net.minecraft.item.ItemBow;
import net.minecraft.item.ItemStack;
import net.minecraft.potion.PotionEffect;
import net.minecraft.stats.StatList;
import net.minecraft.util.DamageSource;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumHand;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.world.World;
import net.minecraftforge.common.config.Config;
import net.minecraftforge.common.config.ConfigManager;
import net.minecraftforge.event.entity.EntityJoinWorldEvent;
import net.minecraftforge.event.entity.item.ItemTossEvent;
import net.minecraftforge.event.entity.living.LivingDropsEvent;
import net.minecraftforge.event.entity.living.LivingEntityUseItemEvent;
import net.minecraftforge.event.entity.player.ArrowLooseEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.world.ExplosionEvent;
import net.minecraftforge.fml.client.event.ConfigChangedEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

@Mod.EventBusSubscriber(modid = "mutantbeasts")
public class EventHandler {
    @SubscribeEvent
    public static void onEntityJoinWorld(EntityJoinWorldEvent event) {
        if (!event.getWorld().isRemote && event.getEntity() instanceof EntityCreature) {
            EntityCreature creature = (EntityCreature) event.getEntity();
            if (creature instanceof EntityOcelot) {
                creature.tasks.addTask(2, new EntityAIAvoidEntity(creature, MutantCreeperEntity.class, 16.0f, 0.8, 1.33));
            }
            if (creature instanceof EntityVillager) {
                creature.tasks.addTask(1, new EntityAIAvoidEntity(creature, MutantZombieEntity.class, 12.0f, 0.8, 0.8));
            }
            if (creature.getClass() == EntityPig.class) {
                creature.tasks.addTask(2, new EntityAITempt(creature, 1.0, Items.FERMENTED_SPIDER_EYE, false));
            }
        }
    }

    @SubscribeEvent
    public static void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        ItemStack stack = event.getEntityPlayer().getHeldItem(event.getHand());
        if (event.getTarget().getClass() == EntityPig.class && !((EntityPig) event.getTarget()).isPotionActive(MobEffects.UNLUCK) && stack.getItem() == Items.FERMENTED_SPIDER_EYE) {
            if (!event.getEntityPlayer().isCreative()) {
                stack.shrink(1);
            }
            ((EntityPig) event.getTarget()).addPotionEffect(new PotionEffect(MobEffects.UNLUCK, 600, 13));
            event.setCancellationResult(EnumActionResult.SUCCESS);
        }
    }

    @SubscribeEvent
    public static void onLivingDrops(LivingDropsEvent event) {
        if (SpiderPigEntity.isPigOrSpider(event.getEntityLiving()) && event.getSource().getTrueSource() instanceof SpiderPigEntity) {
            event.setCanceled(true);
        }
    }

    // TODO: Less 'invasive' way to do this
    @SubscribeEvent
    public static void onLivingUseItem(LivingEntityUseItemEvent.Tick event) {
        if (event.getEntityLiving().getItemStackFromSlot(EntityEquipmentSlot.CHEST).getItem() == MBItems.MUTANT_SKELETON_CHESTPLATE && MBConfig.ITEMS.mutantSkeletonChestplateBowCharging && event.getItem().getItemUseAction() == EnumAction.BOW && event.getDuration() > 4) {
            event.setDuration(event.getDuration() - 3);
        }
    }

    @SubscribeEvent
    public static void onPlayerUpdate(TickEvent.PlayerTickEvent event) {
        if (!event.player.world.isRemote && !HulkHammerItem.WAVES.isEmpty() && HulkHammerItem.WAVES.containsKey(event.player.getUniqueID())) {
            EntityPlayer player = event.player;
            List<SeismicWave> waveList = HulkHammerItem.WAVES.get(player.getUniqueID());
            while (waveList.size() > 16) {
                waveList.remove(0);
            }
            SeismicWave wave = waveList.remove(0);
            wave.affectBlocks(player.world, player);
            AxisAlignedBB box = new AxisAlignedBB(wave.getX(), wave.getY() + 1, wave.getZ(), wave.getX() + 1, wave.getY() + 2, wave.getZ() + 1);
            for (Entity entity : player.world.getEntitiesWithinAABBExcludingEntity(player, box)) {
                if (!entity.canBeCollidedWith() || player.getRidingEntity() == entity || !entity.attackEntityFrom(DamageSource.causePlayerDamage(player), 6 + player.getRNG().nextInt(3)))
                    continue;
                if (entity instanceof EntityLivingBase) {
                    EnchantmentHelper.applyThornEnchantments((EntityLivingBase) entity, player);
                }
                EnchantmentHelper.applyArthropodEnchantments(player, entity);
            }
            if (waveList.isEmpty()) {
                HulkHammerItem.WAVES.remove(player.getUniqueID());
            }
        }
    }

    // TODO: See how this interacts with bows from third party mods
    @SubscribeEvent
    public static void onPlayerShootArrow(ArrowLooseEvent event) {
        if (!event.getWorld().isRemote && event.getEntityPlayer().getItemStackFromSlot(EntityEquipmentSlot.HEAD).getItem() == MBItems.MUTANT_SKELETON_SKULL && MBConfig.ITEMS.mutantSkeletonHelmetArrow && event.hasAmmo()) {
            int k;
            int j;
            boolean inAir;
            event.setCanceled(true);
            EntityPlayer player = event.getEntityPlayer();
            World world = event.getWorld();
            ItemStack bow = event.getBow();
            ItemStack arrowStack = EventHandler.findAmmo(player);
            boolean bl = inAir = !player.onGround && !player.isInWater() && !player.isInLava();
            if (arrowStack.isEmpty()) {
                arrowStack = new ItemStack(Items.ARROW);
            }
            float velocity = ItemBow.getArrowVelocity(event.getCharge());
            // JasperCraft port: ItemArrow.isInfinite is a Forge addition (ForgeAccess has its Forge logic)
            boolean infiniteArrow = player.capabilities.isCreativeMode || arrowStack.getItem() instanceof ItemArrow && chat.jaspr.mutants.compat.ForgeAccess.isInfinite((ItemArrow) arrowStack.getItem(), arrowStack, bow, player);
            ItemArrow itemarrow = (ItemArrow) (arrowStack.getItem() instanceof ItemArrow ? arrowStack.getItem() : Items.ARROW);
            EntityArrow entityarrow = itemarrow.createArrow(world, arrowStack, player);
            entityarrow.shoot(player, player.rotationPitch, player.rotationYaw, 0.0f, velocity * 2.0f, 1.0f);
            if (velocity == 1.0f && inAir) {
                entityarrow.setIsCritical(true);
            }
            if ((j = EnchantmentHelper.getEnchantmentLevel(Enchantments.POWER, bow)) > 0) {
                entityarrow.setDamage(entityarrow.getDamage() + (double) j * 0.5 + 0.5);
            }
            if ((k = EnchantmentHelper.getEnchantmentLevel(Enchantments.PUNCH, bow)) > 0) {
                entityarrow.setKnockbackStrength(k);
            }
            if (EnchantmentHelper.getEnchantmentLevel(Enchantments.FLAME, bow) > 0) {
                entityarrow.setFire(100);
            }
            entityarrow.setDamage(entityarrow.getDamage() * (inAir ? 2.0 : 0.5));
            bow.damageItem(1, player);
            if (infiniteArrow || player.capabilities.isCreativeMode && (arrowStack.getItem() == Items.SPECTRAL_ARROW || arrowStack.getItem() == Items.TIPPED_ARROW)) {
                entityarrow.pickupStatus = EntityArrow.PickupStatus.CREATIVE_ONLY;
            }
            world.spawnEntity(entityarrow);
            world.playSound(null, player.posX, player.posY, player.posZ, SoundEvents.ENTITY_ARROW_SHOOT, SoundCategory.PLAYERS, 1.0f, 1.0f / (player.getRNG().nextFloat() * 0.4f + 1.2f) + velocity * 0.5f);
            if (!infiniteArrow && !player.capabilities.isCreativeMode) {
                arrowStack.shrink(1);
                if (arrowStack.isEmpty()) {
                    player.inventory.deleteStack(arrowStack);
                }
            }
            player.addStat(StatList.getObjectUseStats(bow.getItem()));
        }
    }

    private static ItemStack findAmmo(EntityPlayer player) {
        if (player.getHeldItem(EnumHand.OFF_HAND).getItem() instanceof ItemArrow) {
            return player.getHeldItem(EnumHand.OFF_HAND);
        }
        if (player.getHeldItem(EnumHand.MAIN_HAND).getItem() instanceof ItemArrow) {
            return player.getHeldItem(EnumHand.MAIN_HAND);
        }
        for (int i = 0; i < player.inventory.getSizeInventory(); ++i) {
            ItemStack itemstack = player.inventory.getStackInSlot(i);
            if (!(itemstack.getItem() instanceof ItemArrow)) continue;
            return itemstack;
        }
        return ItemStack.EMPTY;
    }

    @SubscribeEvent
    public static void onPlayerToss(ItemTossEvent event) {
        World world = event.getPlayer().world;
        EntityPlayer player = event.getPlayer();
        if (!world.isRemote) {
            boolean isHand;
            ItemStack stack = event.getEntityItem().getItem();
            boolean bl = isHand = stack.getItem() == MBItems.ENDERSOUL_HAND && stack.isItemDamaged();
            if (stack.getItem() == Items.ENDER_EYE || isHand) {
                int count = 0;
                for (EndersoulFragmentEntity orb : world.getEntitiesWithinAABB(EndersoulFragmentEntity.class, player.getEntityBoundingBox().grow(8.0))) {
                    if (!orb.isEntityAlive() || orb.getOwner() != player) continue;
                    ++count;
                    orb.setDead();
                }
                if (count > 0) {
                    EntityUtil.sendParticlePacket(player, MBParticles.ENDERSOUL, 256);
                    int addDmg = count * 60;
                    if (isHand) {
                        int dmg = stack.getItemDamage() - addDmg;
                        stack.setItemDamage(Math.max(dmg, 0));
                    } else {
                        ItemStack newStack = new ItemStack(MBItems.ENDERSOUL_HAND);
                        // JasperCraft port: Forge's stack-aware Item.getMaxDamage(stack) defaults to the vanilla getMaxDamage()
                        newStack.setItemDamage(MBItems.ENDERSOUL_HAND.getMaxDamage() - addDmg);
                        event.getEntityItem().setItem(newStack);
                    }
                }
            }
        }
    }

    @SubscribeEvent
    public static void onExplosionDetonate(ExplosionEvent.Detonate event) {
        event.getAffectedEntities().removeIf(entity -> entity instanceof EntityItem && ((EntityItem) entity).getItem().getItem() == MBItems.CREEPER_SHARD);
    }

    @SubscribeEvent
    public static void onConfigChanged(ConfigChangedEvent.OnConfigChangedEvent event) {
        if (event.getModID().equals("mutantbeasts")) {
            ConfigManager.sync("mutantbeasts", Config.Type.INSTANCE);
        }
    }
}
