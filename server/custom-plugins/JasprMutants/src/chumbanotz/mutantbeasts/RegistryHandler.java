package chumbanotz.mutantbeasts;

import chumbanotz.mutantbeasts.entity.*;
import chumbanotz.mutantbeasts.entity.mutant.MutantCreeperEntity;
import chumbanotz.mutantbeasts.entity.mutant.MutantEndermanEntity;
import chumbanotz.mutantbeasts.entity.mutant.MutantSkeletonEntity;
import chumbanotz.mutantbeasts.entity.mutant.MutantSnowGolemEntity;
import chumbanotz.mutantbeasts.entity.mutant.MutantZombieEntity;
import chumbanotz.mutantbeasts.entity.mutant.SpiderPigEntity;
import chumbanotz.mutantbeasts.entity.projectile.ChemicalXEntity;
import chumbanotz.mutantbeasts.entity.projectile.MutantArrowEntity;
import chumbanotz.mutantbeasts.entity.projectile.ThrowableBlockEntity;
import chumbanotz.mutantbeasts.item.ChemicalXItem;
import chumbanotz.mutantbeasts.item.CreeperShardItem;
import chumbanotz.mutantbeasts.item.EndersoulHandItem;
import chumbanotz.mutantbeasts.item.HulkHammerItem;
import chumbanotz.mutantbeasts.item.MBItems;
import chumbanotz.mutantbeasts.item.MutantSkeletonArmorItem;
import chumbanotz.mutantbeasts.util.SpecialBrewingRecipe;
import chumbanotz.mutantbeasts.util.MBSoundEvents; // JasperCraft port: the sound event fields are registered
import chat.jaspr.mutants.compat.ForgeAccess; // JasperCraft port: Forge-only members (see uses)

import java.util.Iterator;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.EnumCreatureType;
import net.minecraft.entity.monster.EntityCreeper;
import net.minecraft.entity.monster.EntityEnderman;
import net.minecraft.entity.monster.EntitySkeleton;
import net.minecraft.entity.monster.EntityZombie;
import net.minecraft.entity.passive.EntityParrot;
import net.minecraft.init.Items;
import net.minecraft.init.PotionTypes;
import net.minecraft.init.SoundEvents;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.IRecipe;
import net.minecraft.potion.PotionUtils;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.SoundEvent;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.storage.loot.LootTableList;
import net.minecraftforge.common.brewing.BrewingRecipeRegistry;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.registry.EntityEntry;
import net.minecraftforge.fml.common.registry.EntityEntryBuilder;
import net.minecraftforge.fml.common.registry.ForgeRegistries;
import net.minecraftforge.registries.IForgeRegistryEntry;

@Mod.EventBusSubscriber(modid = "mutantbeasts")
public class RegistryHandler {
    private static int entityId;

    @SubscribeEvent
    public static void registerItems(RegistryEvent.Register<Item> event) {
        event.getRegistry().registerAll(
                RegistryHandler.setRegistryName("chemical_x", new ChemicalXItem().setMaxStackSize(1)),
                RegistryHandler.setRegistryName("creeper_minion_tracker", new Item().setMaxStackSize(1)),
                // JasperCraft port: Forge's access transformer makes Item.setMaxDamage public; Paper keeps it protected.
                RegistryHandler.setRegistryName("creeper_shard", ForgeAccess.setMaxDamage(new CreeperShardItem().setMaxStackSize(1), MBConfig.ITEMS.creeperShardCharges)),
                RegistryHandler.setRegistryName("endersoul_hand", ForgeAccess.setMaxDamage(new EndersoulHandItem().setMaxStackSize(1), MBConfig.ITEMS.endersoulHandDurability)),
                RegistryHandler.setRegistryName("hulk_hammer", ForgeAccess.setMaxDamage(new HulkHammerItem().setMaxStackSize(1), MBConfig.ITEMS.hulkHammerDurability)),
                RegistryHandler.setRegistryName("mutant_skeleton_arms", new Item()),
                RegistryHandler.setRegistryName("mutant_skeleton_limb", new Item()),
                RegistryHandler.setRegistryName("mutant_skeleton_shoulder_pad", new Item()),
                RegistryHandler.setRegistryName("mutant_skeleton_rib", new Item()),
                RegistryHandler.setRegistryName("mutant_skeleton_rib_cage", new Item()),
                RegistryHandler.setRegistryName("mutant_skeleton_pelvis", new Item()),
                RegistryHandler.setRegistryName("mutant_skeleton_skull", new MutantSkeletonArmorItem(EntityEquipmentSlot.HEAD)),
                RegistryHandler.setRegistryName("mutant_skeleton_chestplate", new MutantSkeletonArmorItem(EntityEquipmentSlot.CHEST)),
                RegistryHandler.setRegistryName("mutant_skeleton_leggings", new MutantSkeletonArmorItem(EntityEquipmentSlot.LEGS)),
                RegistryHandler.setRegistryName("mutant_skeleton_boots", new MutantSkeletonArmorItem(EntityEquipmentSlot.FEET)));
    }

    @SubscribeEvent
    public static void registerSoundEvents(RegistryEvent.Register<SoundEvent> event) {
        // JasperCraft port: registers the MBSoundEvents field instances themselves, all 43 in declaration order
        // (MUTANTS_PROTOCOL.md 1.4, ids 1000-1042), instead of 34 new copies. The mod plays the field instances; Forge
        // only finds them in singleplayer (packets are not serialised there), on a dedicated server an unregistered
        // instance is written as sound id -1.
        event.getRegistry().registerAll(
                MBSoundEvents.ENTITY_CREEPER_MINION_AMBIENT,
                MBSoundEvents.ENTITY_CREEPER_MINION_DEATH,
                MBSoundEvents.ENTITY_CREEPER_MINION_HURT,
                MBSoundEvents.ENTITY_CREEPER_MINION_PRIMED,
                MBSoundEvents.ENTITY_CREEPER_MINION_EGG_HATCH,
                MBSoundEvents.ENTITY_ENDERSOUL_CLONE_DEATH,
                MBSoundEvents.ENTITY_ENDERSOUL_CLONE_TELEPORT,
                MBSoundEvents.ENTITY_ENDERSOUL_FRAGMENT_EXPLODE,
                MBSoundEvents.ENTITY_MUTANT_CREEPER_AMBIENT,
                MBSoundEvents.ENTITY_MUTANT_CREEPER_CHARGE,
                MBSoundEvents.ENTITY_MUTANT_CREEPER_DEATH,
                MBSoundEvents.ENTITY_MUTANT_CREEPER_HURT,
                MBSoundEvents.ENTITY_MUTANT_ENDERMAN_AMBIENT,
                MBSoundEvents.ENTITY_MUTANT_ENDERMAN_DEATH,
                MBSoundEvents.ENTITY_MUTANT_ENDERMAN_HURT,
                MBSoundEvents.ENTITY_MUTANT_ENDERMAN_MORPH,
                MBSoundEvents.ENTITY_MUTANT_ENDERMAN_SCREAM,
                MBSoundEvents.ENTITY_MUTANT_ENDERMAN_STARE,
                MBSoundEvents.ENTITY_MUTANT_ENDERMAN_TELEPORT,
                MBSoundEvents.ENTITY_MUTANT_SKELETON_AMBIENT,
                MBSoundEvents.ENTITY_MUTANT_SKELETON_AMBIENT_LEGACY,
                MBSoundEvents.ENTITY_MUTANT_SKELETON_BITE,
                MBSoundEvents.ENTITY_MUTANT_SKELETON_BOW_DRAW,
                MBSoundEvents.ENTITY_MUTANT_SKELETON_BOW_SHOOT,
                MBSoundEvents.ENTITY_MUTANT_SKELETON_DEATH,
                MBSoundEvents.ENTITY_MUTANT_SKELETON_DEATH_LEGACY,
                MBSoundEvents.ENTITY_MUTANT_SKELETON_HURT,
                MBSoundEvents.ENTITY_MUTANT_SKELETON_HURT_LEGACY,
                MBSoundEvents.ENTITY_MUTANT_SKELETON_JUMP,
                MBSoundEvents.ENTITY_MUTANT_SKELETON_PUNCH,
                MBSoundEvents.ENTITY_MUTANT_SKELETON_STEP,
                MBSoundEvents.ENTITY_MUTANT_SKELETON_STEP_LEGACY,
                MBSoundEvents.ENTITY_MUTANT_SNOW_GOLEM_DEATH,
                MBSoundEvents.ENTITY_MUTANT_SNOW_GOLEM_HURT,
                MBSoundEvents.ENTITY_MUTANT_ZOMBIE_AMBIENT,
                MBSoundEvents.ENTITY_MUTANT_ZOMBIE_ATTACK,
                MBSoundEvents.ENTITY_MUTANT_ZOMBIE_DEATH,
                MBSoundEvents.ENTITY_MUTANT_ZOMBIE_GRUNT,
                MBSoundEvents.ENTITY_MUTANT_ZOMBIE_HURT,
                MBSoundEvents.ENTITY_MUTANT_ZOMBIE_ROAR,
                MBSoundEvents.ENTITY_SPIDER_PIG_AMBIENT,
                MBSoundEvents.ENTITY_SPIDER_PIG_DEATH,
                MBSoundEvents.ENTITY_SPIDER_PIG_HURT);

        // JasperCraft port: EntityParrot.registerMimicSound is a Forge addition; the helper fills Paper's map the same way.
        ForgeAccess.registerMimicSound(MutantCreeperEntity.class, SoundEvents.E_PARROT_IM_CREEPER);
        ForgeAccess.registerMimicSound(MutantSkeletonEntity.class, SoundEvents.E_PARROT_IM_SKELETON);
        ForgeAccess.registerMimicSound(MutantZombieEntity.class, SoundEvents.E_PARROT_IM_ZOMBIE);
    }

    @SubscribeEvent
    public static void registerRecipes(RegistryEvent.Register<IRecipe> event) {
        // Chemical X recipe
        BrewingRecipeRegistry.addRecipe(new SpecialBrewingRecipe(PotionUtils.addPotionToItemStack(new ItemStack(Items.POTIONITEM), PotionTypes.THICK), new ItemStack(Items.END_CRYSTAL), new ItemStack(MBItems.CHEMICAL_X)));

        // Brewing recipes to convert thick potions + boss drops to Chemical X
        BrewingRecipeRegistry.addRecipe(new SpecialBrewingRecipe(PotionUtils.addPotionToItemStack(new ItemStack(Items.POTIONITEM), PotionTypes.THICK), new ItemStack(MBItems.CREEPER_SHARD), new ItemStack(MBItems.CHEMICAL_X)));
        BrewingRecipeRegistry.addRecipe(new SpecialBrewingRecipe(PotionUtils.addPotionToItemStack(new ItemStack(Items.POTIONITEM), PotionTypes.THICK), new ItemStack(MBItems.ENDERSOUL_HAND), new ItemStack(MBItems.CHEMICAL_X)));
        BrewingRecipeRegistry.addRecipe(new SpecialBrewingRecipe(PotionUtils.addPotionToItemStack(new ItemStack(Items.POTIONITEM), PotionTypes.THICK), new ItemStack(MBItems.HULK_HAMMER), new ItemStack(MBItems.CHEMICAL_X)));
        BrewingRecipeRegistry.addRecipe(new SpecialBrewingRecipe(PotionUtils.addPotionToItemStack(new ItemStack(Items.POTIONITEM), PotionTypes.THICK), new ItemStack(MBItems.MUTANT_SKELETON_BOOTS), new ItemStack(MBItems.CHEMICAL_X)));
        BrewingRecipeRegistry.addRecipe(new SpecialBrewingRecipe(PotionUtils.addPotionToItemStack(new ItemStack(Items.POTIONITEM), PotionTypes.THICK), new ItemStack(MBItems.MUTANT_SKELETON_CHESTPLATE), new ItemStack(MBItems.CHEMICAL_X)));
        BrewingRecipeRegistry.addRecipe(new SpecialBrewingRecipe(PotionUtils.addPotionToItemStack(new ItemStack(Items.POTIONITEM), PotionTypes.THICK), new ItemStack(MBItems.MUTANT_SKELETON_LEGGINGS), new ItemStack(MBItems.CHEMICAL_X)));
        BrewingRecipeRegistry.addRecipe(new SpecialBrewingRecipe(PotionUtils.addPotionToItemStack(new ItemStack(Items.POTIONITEM), PotionTypes.THICK), new ItemStack(MBItems.MUTANT_SKELETON_SKULL), new ItemStack(MBItems.CHEMICAL_X)));
    }

    @SubscribeEvent
    public static void fixMissingMappings(RegistryEvent.MissingMappings<SoundEvent> event) {
        for (RegistryEvent.MissingMappings.Mapping mapping : event.getMappings()) {
            if (!mapping.key.getPath().startsWith("entity.mutant_husk")) continue;
            mapping.ignore();
        }
    }

    private static <T extends EntityLiving> EntityEntryBuilder<Entity> createEntityEntry(String name, Class<T> entityClass, int eggPrimary, int eggSecondary) {
        return RegistryHandler.createEntityEntry(name, entityClass).egg(eggPrimary, eggSecondary).tracker(80, 3, true);
    }

    private static <T extends Entity> EntityEntryBuilder<Entity> createEntityEntry(String name, Class<T> entityClass) {
        if (EntityLiving.class.isAssignableFrom(entityClass))
            LootTableList.register(MutantBeasts.prefix("entities/" + name));
        return EntityEntryBuilder.create().entity(entityClass).id(MutantBeasts.prefix(name), entityId++).name("mutantbeasts." + name);
    }

    private static EntityEntryBuilder<?> createEntry(String name, Class<? extends EntityLiving> entityClass, int eggPrimary, int eggSecondary) {
        return RegistryHandler.createEntry(name, entityClass).egg(eggPrimary, eggSecondary).tracker(80, 3, true);
    }

    private static EntityEntryBuilder<?> createEntry(String name, Class<? extends Entity> entityClass) {
        if (EntityLiving.class.isAssignableFrom(entityClass)) {
            LootTableList.register(MutantBeasts.prefix("entities/" + name));
        }
        return EntityEntryBuilder.create().entity(entityClass).id(MutantBeasts.prefix(name), entityId++).name("mutantbeasts." + name);
    }

    private static void copySpawnsForMutant(Class<? extends EntityLiving> classToAdd, Class<? extends EntityLiving> classToCopy, EnumCreatureType creatureType, int weight) {
        Iterator iterator = ForgeRegistries.BIOMES.iterator();
        while (iterator.hasNext()) {
            Biome biome = (Biome) iterator.next();
            // JasperCraft port: Biome.getRegistryName() is Forge's; Paper's biome registry gives the same key.
            if (!ForgeRegistries.BIOMES.getKey(biome).getNamespace().equals("minecraft")) continue;
            biome.getSpawnableList(creatureType).stream().filter(entry -> entry.entityClass == classToCopy).findFirst().ifPresent(
                    spawnListEntry -> biome.getSpawnableList(creatureType).add(new Biome.SpawnListEntry(classToAdd, weight, 1, 1)));
        }
    }

    @SubscribeEvent
    public static void onEntityEntryRegistry(RegistryEvent.Register<EntityEntry> event) {
        event.getRegistry().registerAll(
                RegistryHandler.createEntry("body_part", BodyPartEntity.class).tracker(64, 10, true).build(),
                RegistryHandler.createEntry("chemical_x", ChemicalXEntity.class).tracker(160, 10, true).build(),
                RegistryHandler.createEntityEntry("endersoul_clone", EndersoulCloneEntity.class, 15027455, 15027455).build(),
                RegistryHandler.createEntry("creeper_minion", CreeperMinionEntity.class, 894731, 0xB7B7B7).build(),
                RegistryHandler.createEntry("creeper_minion_egg", CreeperMinionEggEntity.class).tracker(160, 20, true).build(),
                RegistryHandler.createEntry("endersoul_fragment", EndersoulFragmentEntity.class).tracker(64, 10, true).build(),
                RegistryHandler.createEntry("mutant_arrow", MutantArrowEntity.class).tracker(80, 3, true).build(),
                RegistryHandler.createEntry("mutant_creeper", MutantCreeperEntity.class, 5349438, 11013646).build(),
                RegistryHandler.createEntry("mutant_enderman", MutantEndermanEntity.class, 0x161616, 8860812).build(),
                RegistryHandler.createEntry("mutant_skeleton", MutantSkeletonEntity.class, 0xC1C1C1, 6310217).build(),
                RegistryHandler.createEntry("mutant_snow_golem", MutantSnowGolemEntity.class, 0xE5FFFF, 16753434).build(),
                RegistryHandler.createEntry("mutant_zombie", MutantZombieEntity.class, 7969893, 44975).build(),
                RegistryHandler.createEntry("skull_spirit", SkullSpiritEntity.class).tracker(160, 20, false).build(),
                RegistryHandler.createEntry("spider_pig", SpiderPigEntity.class, 3419431, 15771042).build(),
                RegistryHandler.createEntry("throwable_block", ThrowableBlockEntity.class).tracker(64, 100, true).build());

        if (MBConfig.ENTITIES.mutantCreeperSpawnRate > 0) {
            RegistryHandler.copySpawnsForMutant(MutantCreeperEntity.class, EntityCreeper.class, EnumCreatureType.MONSTER, MBConfig.ENTITIES.mutantCreeperSpawnRate);
        }

        if (MBConfig.ENTITIES.mutantEndermanSpawnRate > 0) {
            RegistryHandler.copySpawnsForMutant(MutantEndermanEntity.class, EntityEnderman.class, EnumCreatureType.MONSTER, MBConfig.ENTITIES.mutantEndermanSpawnRate);
        }

        if (MBConfig.ENTITIES.mutantSkeletonSpawnRate > 0) {
            RegistryHandler.copySpawnsForMutant(MutantSkeletonEntity.class, EntitySkeleton.class, EnumCreatureType.MONSTER, MBConfig.ENTITIES.mutantSkeletonSpawnRate);
        }

        if (MBConfig.ENTITIES.mutantZombieSpawnRate > 0) {
            RegistryHandler.copySpawnsForMutant(MutantZombieEntity.class, EntityZombie.class, EnumCreatureType.MONSTER, MBConfig.ENTITIES.mutantZombieSpawnRate);
        }
    }

    private static SoundEvent createSoundEvent(String name) {
        ResourceLocation registryName = MutantBeasts.prefix(name);
        // JasperCraft port: Paper's SoundEvent has no Forge registry name; RegistryNames keeps it (unused, see above).
        return chat.jaspr.mutants.registry.RegistryNames.set(new SoundEvent(registryName), registryName);
    }

    // JasperCraft port: Paper's Item/SoundEvent do not implement Forge's IForgeRegistryEntry, so the type bound is gone
    // and the registry name is kept by RegistryNames until the object is registered.
    private static <T> T setRegistryName(String name, T entry) {
        ResourceLocation registryName = MutantBeasts.prefix(name);

        if (entry instanceof Item) {
            ((Item) entry).setTranslationKey("mutantbeasts." + registryName.getPath());
            ((Item) entry).setCreativeTab(MutantBeasts.CREATIVE_TAB);
        }

        return chat.jaspr.mutants.registry.RegistryNames.set(entry, registryName);
    }
}
