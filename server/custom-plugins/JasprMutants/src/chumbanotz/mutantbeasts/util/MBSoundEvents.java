package chumbanotz.mutantbeasts.util;

import chumbanotz.mutantbeasts.MutantBeasts;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.SoundEvent;
import net.minecraftforge.fml.common.registry.GameRegistry;

@GameRegistry.ObjectHolder(MutantBeasts.MOD_ID)
public class MBSoundEvents {
    public static final SoundEvent ENTITY_CREEPER_MINION_AMBIENT = new SoundEvent(new ResourceLocation(MutantBeasts.MOD_ID, "entity.creeper_minion.ambient"));
    public static final SoundEvent ENTITY_CREEPER_MINION_DEATH = new SoundEvent(new ResourceLocation(MutantBeasts.MOD_ID, "entity.creeper_minion.death"));
    public static final SoundEvent ENTITY_CREEPER_MINION_HURT = new SoundEvent(new ResourceLocation(MutantBeasts.MOD_ID, "entity.creeper_minion.hurt"));
    public static final SoundEvent ENTITY_CREEPER_MINION_PRIMED = new SoundEvent(new ResourceLocation(MutantBeasts.MOD_ID, "entity.creeper_minion.primed"));
    public static final SoundEvent ENTITY_CREEPER_MINION_EGG_HATCH = new SoundEvent(new ResourceLocation(MutantBeasts.MOD_ID, "entity.creeper_minion_egg.hatch"));

    public static final SoundEvent ENTITY_ENDERSOUL_CLONE_DEATH = new SoundEvent(new ResourceLocation(MutantBeasts.MOD_ID, "entity.endersoul_clone.death"));
    public static final SoundEvent ENTITY_ENDERSOUL_CLONE_TELEPORT = new SoundEvent(new ResourceLocation(MutantBeasts.MOD_ID, "entity.endersoul_clone.teleport"));
    public static final SoundEvent ENTITY_ENDERSOUL_FRAGMENT_EXPLODE = new SoundEvent(new ResourceLocation(MutantBeasts.MOD_ID, "entity.endersoul_fragment.explode"));

    public static final SoundEvent ENTITY_MUTANT_CREEPER_AMBIENT = new SoundEvent(new ResourceLocation(MutantBeasts.MOD_ID, "entity.mutant_creeper.ambient"));
    public static final SoundEvent ENTITY_MUTANT_CREEPER_CHARGE = new SoundEvent(new ResourceLocation(MutantBeasts.MOD_ID, "entity.mutant_creeper.charge"));
    public static final SoundEvent ENTITY_MUTANT_CREEPER_DEATH = new SoundEvent(new ResourceLocation(MutantBeasts.MOD_ID, "entity.mutant_creeper.death"));
    public static final SoundEvent ENTITY_MUTANT_CREEPER_HURT = new SoundEvent(new ResourceLocation(MutantBeasts.MOD_ID, "entity.mutant_creeper.hurt"));

    public static final SoundEvent ENTITY_MUTANT_ENDERMAN_AMBIENT = new SoundEvent(new ResourceLocation(MutantBeasts.MOD_ID, "entity.mutant_enderman.ambient"));
    public static final SoundEvent ENTITY_MUTANT_ENDERMAN_DEATH = new SoundEvent(new ResourceLocation(MutantBeasts.MOD_ID, "entity.mutant_enderman.death"));
    public static final SoundEvent ENTITY_MUTANT_ENDERMAN_HURT = new SoundEvent(new ResourceLocation(MutantBeasts.MOD_ID, "entity.mutant_enderman.hurt"));
    public static final SoundEvent ENTITY_MUTANT_ENDERMAN_MORPH = new SoundEvent(new ResourceLocation(MutantBeasts.MOD_ID, "entity.mutant_enderman.morph"));
    public static final SoundEvent ENTITY_MUTANT_ENDERMAN_SCREAM = new SoundEvent(new ResourceLocation(MutantBeasts.MOD_ID, "entity.mutant_enderman.scream"));
    public static final SoundEvent ENTITY_MUTANT_ENDERMAN_STARE = new SoundEvent(new ResourceLocation(MutantBeasts.MOD_ID, "entity.mutant_enderman.stare"));
    public static final SoundEvent ENTITY_MUTANT_ENDERMAN_TELEPORT = new SoundEvent(new ResourceLocation(MutantBeasts.MOD_ID, "entity.mutant_enderman.teleport"));

    public static final SoundEvent ENTITY_MUTANT_SKELETON_AMBIENT = new SoundEvent(new ResourceLocation(MutantBeasts.MOD_ID, "entity.mutant_skeleton.ambient"));
    public static final SoundEvent ENTITY_MUTANT_SKELETON_AMBIENT_LEGACY = new SoundEvent(new ResourceLocation(MutantBeasts.MOD_ID, "entity.mutant_skeleton.ambient.legacy"));
    public static final SoundEvent ENTITY_MUTANT_SKELETON_BITE = new SoundEvent(new ResourceLocation(MutantBeasts.MOD_ID, "entity.mutant_skeleton.bite"));
    public static final SoundEvent ENTITY_MUTANT_SKELETON_BOW_DRAW = new SoundEvent(new ResourceLocation(MutantBeasts.MOD_ID, "entity.mutant_skeleton.bow_draw"));
    public static final SoundEvent ENTITY_MUTANT_SKELETON_BOW_SHOOT = new SoundEvent(new ResourceLocation(MutantBeasts.MOD_ID, "entity.mutant_skeleton.bow_shoot"));
    public static final SoundEvent ENTITY_MUTANT_SKELETON_DEATH = new SoundEvent(new ResourceLocation(MutantBeasts.MOD_ID, "entity.mutant_skeleton.death"));
    public static final SoundEvent ENTITY_MUTANT_SKELETON_DEATH_LEGACY = new SoundEvent(new ResourceLocation(MutantBeasts.MOD_ID, "entity.mutant_skeleton.death.legacy"));
    public static final SoundEvent ENTITY_MUTANT_SKELETON_HURT = new SoundEvent(new ResourceLocation(MutantBeasts.MOD_ID, "entity.mutant_skeleton.hurt"));
    public static final SoundEvent ENTITY_MUTANT_SKELETON_HURT_LEGACY = new SoundEvent(new ResourceLocation(MutantBeasts.MOD_ID, "entity.mutant_skeleton.hurt.legacy"));
    public static final SoundEvent ENTITY_MUTANT_SKELETON_JUMP = new SoundEvent(new ResourceLocation(MutantBeasts.MOD_ID, "entity.mutant_skeleton.jump"));
    public static final SoundEvent ENTITY_MUTANT_SKELETON_PUNCH = new SoundEvent(new ResourceLocation(MutantBeasts.MOD_ID, "entity.mutant_skeleton.punch"));
    public static final SoundEvent ENTITY_MUTANT_SKELETON_STEP = new SoundEvent(new ResourceLocation(MutantBeasts.MOD_ID, "entity.mutant_skeleton.step"));
    public static final SoundEvent ENTITY_MUTANT_SKELETON_STEP_LEGACY = new SoundEvent(new ResourceLocation(MutantBeasts.MOD_ID, "entity.mutant_skeleton.step.legacy"));

    public static final SoundEvent ENTITY_MUTANT_SNOW_GOLEM_DEATH = new SoundEvent(new ResourceLocation(MutantBeasts.MOD_ID, "entity.mutant_snow_golem.death"));
    public static final SoundEvent ENTITY_MUTANT_SNOW_GOLEM_HURT = new SoundEvent(new ResourceLocation(MutantBeasts.MOD_ID, "entity.mutant_snow_golem.hurt"));

    public static final SoundEvent ENTITY_MUTANT_ZOMBIE_AMBIENT = new SoundEvent(new ResourceLocation(MutantBeasts.MOD_ID, "entity.mutant_zombie.ambient"));
    public static final SoundEvent ENTITY_MUTANT_ZOMBIE_ATTACK = new SoundEvent(new ResourceLocation(MutantBeasts.MOD_ID, "entity.mutant_zombie.attack"));
    public static final SoundEvent ENTITY_MUTANT_ZOMBIE_DEATH = new SoundEvent(new ResourceLocation(MutantBeasts.MOD_ID, "entity.mutant_zombie.death"));
    public static final SoundEvent ENTITY_MUTANT_ZOMBIE_GRUNT = new SoundEvent(new ResourceLocation(MutantBeasts.MOD_ID, "entity.mutant_zombie.grunt"));
    public static final SoundEvent ENTITY_MUTANT_ZOMBIE_HURT = new SoundEvent(new ResourceLocation(MutantBeasts.MOD_ID, "entity.mutant_zombie.hurt"));
    public static final SoundEvent ENTITY_MUTANT_ZOMBIE_ROAR = new SoundEvent(new ResourceLocation(MutantBeasts.MOD_ID, "entity.mutant_zombie.roar"));

    public static final SoundEvent ENTITY_SPIDER_PIG_AMBIENT = new SoundEvent(new ResourceLocation(MutantBeasts.MOD_ID, "entity.spider_pig.ambient"));
    public static final SoundEvent ENTITY_SPIDER_PIG_DEATH = new SoundEvent(new ResourceLocation(MutantBeasts.MOD_ID, "entity.spider_pig.death"));
    public static final SoundEvent ENTITY_SPIDER_PIG_HURT = new SoundEvent(new ResourceLocation(MutantBeasts.MOD_ID, "entity.spider_pig.hurt"));
}
