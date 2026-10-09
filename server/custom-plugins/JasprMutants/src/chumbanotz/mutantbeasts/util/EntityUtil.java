package chumbanotz.mutantbeasts.util;

import chumbanotz.mutantbeasts.MBConfig;
import chumbanotz.mutantbeasts.MutantBeasts;
import chumbanotz.mutantbeasts.packet.MBPacketHandler;
import chumbanotz.mutantbeasts.packet.SpawnParticlePacket;
import chumbanotz.mutantbeasts.util.MBParticles;
import java.lang.reflect.Field;
import java.util.Collection;
import java.util.Random;
import java.util.function.Function;
import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityAreaEffectCloud;
import net.minecraft.entity.EntityList;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.ai.attributes.AbstractAttributeMap;
import net.minecraft.entity.item.EntityXPOrb;
import net.minecraft.entity.monster.EntityCreeper;
import net.minecraft.entity.monster.EntityEnderman;
import net.minecraft.entity.monster.EntityGolem;
import net.minecraft.entity.monster.IMob;
import net.minecraft.entity.passive.AbstractHorse;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.network.play.server.SPacketEntityVelocity;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraftforge.event.ForgeEventFactory;
import net.minecraftforge.fml.common.ObfuscationReflectionHelper;
import net.minecraftforge.fml.common.network.NetworkRegistry;

public class EntityUtil {
    private static final Field FIRE = ObfuscationReflectionHelper.findField(Entity.class, "field_190534_ay");

    public static int getFire(Entity entity) {
        try {
            return FIRE.getInt(entity);
        }
        catch (IllegalAccessException | IllegalArgumentException e) {
            throw new RuntimeException("Failed to access fire timer for " + entity, e);
        }
    }

    public static float getHeadAngle(EntityLivingBase livingEntity, double x, double z) {
        return Math.abs(MathHelper.wrapDegrees(livingEntity.rotationYawHead - ((float)(Math.atan2(z, x) * 180.0 / Math.PI) + 90.0f)));
    }

    public static boolean getRandomSpawnChance(Random rand) {
        int i = Math.max(1, MBConfig.ENTITIES.globalSpawnRate);
        i = Math.min(20, i);
        return (rand.nextInt(50 / i) == 0);
    }

    public static void spawnLingeringCloud(EntityLivingBase entityLivingBase) {
        Collection<PotionEffect> collection = entityLivingBase.getActivePotionEffects();
        if (!collection.isEmpty()) {
            EntityAreaEffectCloud entityareaeffectcloud = new EntityAreaEffectCloud(entityLivingBase.world, entityLivingBase.posX, entityLivingBase.posY, entityLivingBase.posZ);
            entityareaeffectcloud.setRadius(2.5f);
            entityareaeffectcloud.setRadiusOnUse(-0.5f);
            entityareaeffectcloud.setWaitTime(10);
            entityareaeffectcloud.setDuration(entityareaeffectcloud.getDuration() / 2);
            entityareaeffectcloud.setRadiusPerTick(-entityareaeffectcloud.getRadius() / (float)entityareaeffectcloud.getDuration());
            for (PotionEffect potioneffect : collection) {
                entityareaeffectcloud.addEffect(new PotionEffect(potioneffect));
            }
            entityLivingBase.world.spawnEntity(entityareaeffectcloud);
        }
    }

    public static void disableShield(EntityLivingBase entityLivingBase, int ticks) {
        if (entityLivingBase instanceof EntityPlayer && entityLivingBase.isActiveItemStackBlocking()) {
            ((EntityPlayer)entityLivingBase).getCooldownTracker().setCooldown(entityLivingBase.getActiveItemStack().getItem(), ticks);
            entityLivingBase.resetActiveHand();
            entityLivingBase.world.setEntityState(entityLivingBase, (byte)30);
        }
    }

    public static void knockBackBlockingPlayer(Entity target) {
        if (target instanceof EntityPlayer && ((EntityPlayer)target).isActiveItemStackBlocking()) {
            target.velocityChanged = true;
        }
    }

    public static void sendPlayerVelocityPacket(Entity entity) {
        if (entity instanceof EntityPlayerMP) {
            ((EntityPlayerMP) entity).connection.sendPacket(new SPacketEntityVelocity(entity));
        }
    }

    public static boolean shouldAttackEntity(EntityLivingBase target, EntityLivingBase owner, boolean canTargetCreepers) {
        if (owner instanceof EntityPlayer) {
            if (target instanceof EntityCreeper) {
                return canTargetCreepers;
            }
            if (target instanceof EntityPlayer && !((EntityPlayer)owner).canAttackPlayer((EntityPlayer)target)) {
                return false;
            }
            if (target instanceof EntityGolem & !(target instanceof IMob)) {
                return false;
            }
            return !(target instanceof AbstractHorse) || !((AbstractHorse) target).isTame();
        }
        return true;
    }

    public static void dropExperience(EntityLiving mob, int recentlyHit, Function<EntityPlayer, Integer> experiencePoints, EntityPlayer attackingPlayer) {
        if (!mob.world.isRemote && recentlyHit > 0 && mob.world.getGameRules().getBoolean("doMobLoot")) {
            int j;
            int i = experiencePoints.apply(attackingPlayer);
            for (i = ForgeEventFactory.getExperienceDrop(mob, attackingPlayer, i); i > 0; i -= j) {
                j = EntityXPOrb.getXPSplit(i);
                // JasperCraft port: CraftBukkit's EntityXPOrb constructor also takes the spawn reason and the entities
                // involved (Paper's own death XP passes ENTITY_DEATH, the killer and the dying mob).
                mob.world.spawnEntity(new EntityXPOrb(mob.world, mob.posX, mob.posY, mob.posZ, j, org.bukkit.entity.ExperienceOrb.SpawnReason.ENTITY_DEATH, attackingPlayer, mob));
            }
        }
    }

    public static EntityLiving convertMobWithNBT(EntityLivingBase oldEntity, EntityLiving newEntity, boolean dropInventory) {
        if (oldEntity.isDead) {
            return null;
        }
        NBTTagCompound copiedNBT = oldEntity.writeToNBT(new NBTTagCompound());
        copiedNBT.setUniqueId("UUID", newEntity.getUniqueID());
        copiedNBT.setTag("Attributes", SharedMonsterAttributes.writeBaseAttributeMapToNBT(newEntity.getAttributeMap()));
        copiedNBT.setFloat("Health", newEntity.getHealth());
        if (oldEntity.getTeam() != null) {
            copiedNBT.setString("Team", oldEntity.getTeam().getName());
        }
        if (copiedNBT.hasKey("ActiveEffects", 9)) {
            NBTTagList activeEffects = copiedNBT.getTagList("ActiveEffects", 10);
            for (int i = 0; i < activeEffects.tagCount(); ++i) {
                NBTTagCompound compound = activeEffects.getCompoundTagAt(i);
                PotionEffect potionEffect = PotionEffect.readCustomPotionEffectFromNBT(compound);
                if (potionEffect == null || newEntity.isPotionApplicable(potionEffect)) continue;
                activeEffects.removeTag(i);
                --i;
            }
        }
        // JasperCraft port: CraftBukkit holds back every item a living entity drops outside its death (they would only
        // be released by a later death event, which never comes: the old mob is removed below), so the drops are made
        // immediate here, as Forge drops them (the same flag CraftBukkit sets around its own non-death drops).
        boolean immediateDrops = chat.jaspr.mutants.compat.ForgeAccess.setForceDrops(oldEntity, true);
        if (dropInventory && oldEntity.world.getGameRules().getBoolean("doMobLoot")) {
            IBlockState iblockstate;
            ItemStack itemStack;
            copiedNBT.setBoolean("CanPickUpLoot", false);
            if (copiedNBT.hasKey("ArmorItems", 9)) {
                NBTTagList armorItems = copiedNBT.getTagList("ArmorItems", 10);
                NBTTagList armorDropChances = copiedNBT.getTagList("ArmorDropChances", 5);
                for (int i = 0; i < armorItems.tagCount(); ++i) {
                    itemStack = new ItemStack(armorItems.getCompoundTagAt(i));
                    if (itemStack.isEmpty() || EnchantmentHelper.hasVanishingCurse(itemStack) || !(armorDropChances.getFloatAt(i) > 1.0f)) continue;
                    oldEntity.entityDropItem(itemStack, 0.0f);
                }
                copiedNBT.setTag("ArmorItems", new NBTTagList());
                copiedNBT.setTag("ArmorDropChances", new NBTTagList());
            }
            if (copiedNBT.hasKey("HandItems", 9)) {
                NBTTagList handItems = copiedNBT.getTagList("HandItems", 10);
                NBTTagList handDropChances = copiedNBT.getTagList("HandDropChances", 5);
                for (int i = 0; i < handItems.tagCount(); ++i) {
                    itemStack = new ItemStack(handItems.getCompoundTagAt(i));
                    if (!itemStack.isEmpty() && !EnchantmentHelper.hasVanishingCurse(itemStack) && handDropChances.getFloatAt(i) > 1.0f) {
                        oldEntity.entityDropItem(itemStack, 0.0f);
                    }
                    copiedNBT.setTag("HandItems", new NBTTagList());
                    copiedNBT.setTag("HandDropChances", new NBTTagList());
                }
            }
            if (oldEntity instanceof EntityEnderman && (iblockstate = Block.getBlockById(copiedNBT.getShort("carried")).getStateFromMeta(copiedNBT.getShort("carriedData") & 0xFFFF)) != null && iblockstate.getBlock() != null && iblockstate.getMaterial() != Material.AIR) {
                Item item = Item.getItemFromBlock(iblockstate.getBlock());
                int i = item.getHasSubtypes() ? iblockstate.getBlock().getMetaFromState(iblockstate) : 0;
                oldEntity.entityDropItem(new ItemStack(item, 1, i), 0.0f);
            }
        }
        chat.jaspr.mutants.compat.ForgeAccess.setForceDrops(oldEntity, immediateDrops); // JasperCraft port: see above
        newEntity.readFromNBT(copiedNBT);
        oldEntity.world.spawnEntity(newEntity);
        oldEntity.setDead();
        return newEntity;
    }

    public static ResourceLocation getLootTable(Entity entity) {
        return MutantBeasts.prefix("entities/" + EntityList.getKey(entity).getPath());
    }

    public static void spawnParticleAtEntity(EntityLivingBase entity, EnumParticleTypes particleType, int amount, int ... parameters) {
        if (entity.world.isRemote) {
            for (int i = 0; i < amount; ++i) {
                double posX = entity.posX + (double)(entity.getRNG().nextFloat() * entity.width * 2.0f) - (double)entity.width;
                double posY = entity.posY + 0.5 + (double)(entity.getRNG().nextFloat() * entity.height);
                double posZ = entity.posZ + (double)(entity.getRNG().nextFloat() * entity.width * 2.0f) - (double)entity.width;
                double x = entity.getRNG().nextGaussian() * 0.02;
                double y = entity.getRNG().nextGaussian() * 0.02;
                double z = entity.getRNG().nextGaussian() * 0.02;
                entity.world.spawnParticle(particleType, posX, posY, posZ, x, y, z, parameters);
            }
        }
    }

    public static void spawnEndersoulParticles(Entity entity, int amount, float speed) {
        for (int i = 0; i < amount; ++i) {
            float f = (entity.world.rand.nextFloat() - 0.5f) * speed;
            float f1 = (entity.world.rand.nextFloat() - 0.5f) * speed;
            float f2 = (entity.world.rand.nextFloat() - 0.5f) * speed;
            double tempX = entity.posX + (double)((entity.world.rand.nextFloat() - 0.5f) * entity.width);
            double tempY = entity.posY + (double)((entity.world.rand.nextFloat() - 0.5f) * entity.height) + 0.5;
            double tempZ = entity.posZ + (double)((entity.world.rand.nextFloat() - 0.5f) * entity.width);
            entity.world.spawnParticle(MBParticles.ENDERSOUL, tempX, tempY, tempZ, f, f1, f2);
        }
    }

    public static void sendParticlePacket(Entity entity, EnumParticleTypes particleType, int amount) {
        // JasperCraft port: Paper runs several worlds under one dimension id, so the target point names the entity's world.
        MBPacketHandler.INSTANCE.sendToAllAround(new SpawnParticlePacket(particleType, entity.posX, entity.posY, entity.posZ, entity.width, entity.height, entity.width, amount), new NetworkRegistry.TargetPoint(entity.world, entity.posX, entity.posY, entity.posZ, 1024.0));
    }

    public static Vec3d getDirVector(float rotation, float scale) {
        float rad = rotation * ((float)Math.PI / 180);
        return new Vec3d(-MathHelper.sin(rad) * scale, 0.0, MathHelper.cos(rad) * scale);
    }

    public static boolean teleportTo(EntityLiving mob, double x, double y, double z) {
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos().setPos(x, y, z);
        if (mob.world.isBlockLoaded(pos)) {
            do {
                pos.move(EnumFacing.DOWN);
            } while (pos.getY() > 0 && !mob.world.getBlockState(pos).getMaterial().blocksMovement());
            pos.move(EnumFacing.UP);
        }
        if (!mob.isOffsetPositionInLiquid(pos.getX() - MathHelper.floor(mob.posX), pos.getY() - MathHelper.floor(mob.posY), pos.getZ() - MathHelper.floor(mob.posZ))) {
            return false;
        }
        mob.moveToBlockPosAndAngles(pos, mob.rotationYaw, mob.rotationPitch);
        mob.getNavigator().clearPath();
        return true;
    }

    public static void divertAttackers(EntityLiving targetedMob, EntityLivingBase newTarget) {
        for (EntityLiving mob : targetedMob.world.getEntitiesWithinAABB(EntityLiving.class, targetedMob.getEntityBoundingBox().grow(16.0, 10.0, 16.0))) {
            if (mob == targetedMob || mob == newTarget || mob.getAttackTarget() != targetedMob) continue;
            mob.setAttackTarget(newTarget);
        }
    }
}
