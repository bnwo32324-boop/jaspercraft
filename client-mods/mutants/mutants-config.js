/* MBConfig.java (client copy). The client must use the values the JasperCraft server runs with; these are the mod's
 * defaults except mutantSkeletonLegacy{Ambient,Death,Hurt,Step}Sound = true (MUTANTS_PROTOCOL.md 1.4: the new skeleton
 * sounds are All Rights Reserved and are never shipped). Only the options read by client code paths are listed. */
(function (M) {
  "use strict";
  M.CFG = {
    GENERAL: { endersoulParticleID: 100, skullSpiritParticleID: 101 },
    ENTITIES: {
      creeperMinionOnShoulder: true,
      mutantCreeperArmor: 10.0, mutantCreeperAttackDamage: 5.0, mutantCreeperDeathStrength: 8.0,
      mutantCreeperDeathStrengthCharged: 12.0, mutantCreeperFollowRange: 35.0, mutantCreeperKnockbackResistance: 1.0,
      mutantCreeperMaxHealth: 150.0, mutantCreeperMovementSpeed: 0.26, mutantCreeperSwimSpeed: 4.5,
      mutantEndermanArmor: 10.0, mutantEndermanAttackDamage: 7.0, mutantEndermanFollowRange: 96.0,
      mutantEndermanKnockbackResistance: 1.0, mutantEndermanMaxHealth: 200.0, mutantEndermanMovementSpeed: 0.3,
      mutantEndermanSwimSpeed: 1.0, mutantEndermanRendersTeleport: true,
      mutantSkeletonArmor: 10.0, mutantSkeletonAttackDamage: 4.0, mutantSkeletonFollowRange: 50.0,
      mutantSkeletonKnockbackResistance: 1.0, mutantSkeletonMaxHealth: 150.0, mutantSkeletonMovementSpeed: 0.27,
      mutantSkeletonSwimSpeed: 5.0, mutantSkeletonBoneDrops: true, mutantSkeletonArrowDamage: 12.0,
      mutantSkeletonLegacyAmbientSound: true, mutantSkeletonLegacyDeathSound: true, mutantSkeletonLegacyHurtSound: true,
      mutantSkeletonLegacyStepSound: true,
      mutantSnowGolemArmor: 0.0, mutantSnowGolemKnockbackResistance: 1.0, mutantSnowGolemMaxHealth: 80.0,
      mutantSnowGolemMovementSpeed: 0.26, mutantSnowGolemSwimSpeed: 1.0, mutantSnowGolemNetherWeakness: false,
      mutantSnowGolemWaterWeakness: false,
      mutantZombieArmor: 12.0, mutantZombieAttackDamage: 12.0, mutantZombieFollowRange: 35.0,
      mutantZombieKnockbackResistance: 1.0, mutantZombieLives: 3, mutantZombieMaxHealth: 150.0,
      mutantZombieMovementSpeed: 0.26, mutantZombieSwimSpeed: 4.0,
      spiderPigArmor: 0.0, spiderPigAttackDamage: 3.0, spiderPigKnockbackResistance: 0.0, spiderPigMaxHealth: 40.0,
      spiderPigMovementSpeed: 0.25, spiderPigSwimSpeed: 1.0
    },
    ITEMS: {
      creeperShardCharges: 32, endersoulHandAttackSpeed: 1.6, endersoulHandCooldown: 40, endersoulHandDamage: 6.0,
      endersoulHandDurability: 240, endersoulHandEnchantability: 20, endersoulHandTeleports: true,
      endersoulHandTeleportationCost: 4, endersoulHandTeleportationRadius: 128.0, hulkHammerAttackSpeed: 1.0,
      hulkHammerCooldown: 25, hulkHammerDamage: 9.0, hulkHammerEnchantability: 10, hulkHammerDurability: 64,
      hulkHammerDisablesShields: true, mutantSkeletonArmorDurability: 15, mutantSkeletonArmorEnchantability: 9,
      mutantSkeletonArmorProtectionBoots: 2, mutantSkeletonArmorProtectionChestplate: 6,
      mutantSkeletonArmorProtectionHelmet: 2, mutantSkeletonArmorProtectionLeggings: 5,
      mutantSkeletonArmorToughness: 0.0, mutantSkeletonBootsJumpBoost: true, mutantSkeletonChestplateBowCharging: true,
      mutantSkeletonLeggingsSpeed: true, mutantSkeletonHelmetArrow: true
    }
  };
})(JasprMutants);
