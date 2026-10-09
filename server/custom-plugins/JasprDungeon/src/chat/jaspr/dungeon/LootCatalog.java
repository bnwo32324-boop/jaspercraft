package chat.jaspr.dungeon;

import java.util.*;

/**
 * Server-free reward contract. Bauble names are persisted IDs; preserve every one and its order (the first 72 also fix their
 * icon bands). Generation 7 adds a profile for each of its 66 themes, their baubles (each floor's reliquaries hold that floor's
 * own), twelve gear variants, the victor's laurel, and boss-chamber loot for a floor's Descent and the Throne.
 */
public final class LootCatalog {
    private LootCatalog() {}
    public enum Trigger { HURT, ATTACK, KILL, ENTER, CLEAR, EXPERIENCE, DURABILITY, REGAIN, FOOD, TICK, REACT }
    public enum Gear {
        SWORD("SWORD","DAMAGE_ALL"), AXE("AXE","DAMAGE_ALL"), CHESTPLATE("CHESTPLATE","PROTECTION_ENVIRONMENTAL"), BOW("BOW","ARROW_DAMAGE"),
        HELMET("HELMET","OXYGEN"), LEGGINGS("LEGGINGS","PROTECTION_ENVIRONMENTAL"), BOOTS("BOOTS","PROTECTION_FALL"), PICKAXE("PICKAXE","DIG_SPEED"),
        SHOVEL("SPADE","DIG_SPEED"), HOE("HOE","DURABILITY"), SHIELD("SHIELD","DURABILITY"), FISHING_ROD("FISHING_ROD","LURE"),
        SMITE_SWORD("SWORD","DAMAGE_UNDEAD"), CLEAVING_AXE("AXE","DIG_SPEED"), THORNS_CHESTPLATE("CHESTPLATE","THORNS"), PUNCH_BOW("BOW","ARROW_KNOCKBACK"),
        AQUA_HELMET("HELMET","WATER_WORKER"), BLAST_LEGGINGS("LEGGINGS","PROTECTION_EXPLOSIONS"), DEPTH_BOOTS("BOOTS","DEPTH_STRIDER"), SILK_PICKAXE("PICKAXE","SILK_TOUCH"),
        FORTUNE_SHOVEL("SPADE","LOOT_BONUS_BLOCKS"), MENDING_HOE("HOE","MENDING"), MENDING_SHIELD("SHIELD","MENDING"), LUCK_ROD("FISHING_ROD","LUCK"),
        // Generation 7: twelve more variants for the new themes and the deeper floors (fire, crowds, gunners, mines).
        BRAND_SWORD("SWORD","FIRE_ASPECT"), SWEEP_SWORD("SWORD","SWEEPING_EDGE"), BANE_SWORD("SWORD","DAMAGE_ARTHROPODS"), SMITE_AXE("AXE","DAMAGE_UNDEAD"),
        EMBER_CHESTPLATE("CHESTPLATE","PROTECTION_FIRE"), WARD_HELMET("HELMET","PROTECTION_PROJECTILE"), SHOT_LEGGINGS("LEGGINGS","PROTECTION_PROJECTILE"), CINDER_BOOTS("BOOTS","PROTECTION_FIRE"),
        FLAME_BOW("BOW","ARROW_FIRE"), ENDLESS_BOW("BOW","ARROW_INFINITE"), FORTUNE_PICKAXE("PICKAXE","LOOT_BONUS_BLOCKS"), BLAST_BOOTS("BOOTS","PROTECTION_EXPLOSIONS");
        public final String suffix,enchantment;
        Gear(String suffix,String enchantment){this.suffix=suffix;this.enchantment=enchantment;}
        public String material(boolean diamond){return suffix.equals("BOW")||suffix.equals("SHIELD")||suffix.equals("FISHING_ROD")?suffix:(diamond?"DIAMOND_":"IRON_")+suffix;}
        public boolean variant(){return ordinal()>=12;}
    }
    public enum Bauble {
        SALT_TEAR("Salt Tear","GHAST_TEAR",1,0,Trigger.HURT,0,"Take 15% less projectile damage."),
        MARROW_BEAD("Marrow Bead","BONE",1,1,Trigger.KILL,2000,"Kills restore half a heart; 2-second cooldown."),
        PILGRIM_KNOT("Pilgrim's Knot","STRING",1,0,Trigger.HURT,0,"Take 50% less fall damage."),
        CINDER_HEART("Cinder Heart","MAGMA_CREAM",2,3,Trigger.HURT,0,"Take 40% less fire and lava damage."),
        HOLLOW_LENS("Hollow Lens","PRISMARINE_CRYSTALS",2,4,Trigger.ATTACK,0,"Your projectiles deal 12% more damage."),
        RUSTED_HALO("Rusted Halo","IRON_NUGGET",2,1,Trigger.ATTACK,0,"Deal 15% more melee damage to undead."),
        BLOOD_THREAD("Blood Thread","REDSTONE",3,7,Trigger.ATTACK,0,"Deal 18% more damage below 35% health."),
        LAMB_BELL("Bell of the Lost Lamb","GOLD_NUGGET",3,5,Trigger.ENTER,45000,"Entering danger grants 2 absorption hearts for 6s.","45-second cooldown; stronger absorption is preserved."),
        MOURNING_PEARL("Mourning Pearl","ENDER_PEARL",3,2,Trigger.HURT,0,"Take 30% less magic, poison and wither damage."),
        CONFESSOR_SEAL("Confessor's Seal","CLAY_BALL",4,2,Trigger.CLEAR,0,"Clearing a dungeon combat room restores 2 hearts."),
        WARDEN_EYE("The Warden's Eye","EYE_OF_ENDER",4,4,Trigger.HURT,60000,"The first hit after 60 seconds deals 50% less damage."),
        CROWN_OF_MERCY("Crown of Mercy","NETHER_STAR",5,5,Trigger.HURT,600000,"Survive a lethal hit with half a heart.","10-minute cooldown, saved in the pouch."),
        EMBER_VIAL("Ember Vial","BLAZE_POWDER",2,3,Trigger.REACT,12000,"A fire hit caps remaining burning at 2 seconds.","12-second cooldown; does not extinguish lava."),
        ASHEN_BOOKMARK("Ashen Bookmark","PAPER",1,6,Trigger.EXPERIENCE,0,"Gain 10% more picked-up experience, at most 3 per orb."),
        SCRIBE_QUILL("Scribe's Quill","FEATHER",2,6,Trigger.DURABILITY,10000,"Prevent 1 point of ordinary equipment wear every 10s."),
        RUBY_BROOCH("Ruby Brooch","REDSTONE",2,7,Trigger.ATTACK,0,"Deal 12% more melee damage to full-health monsters."),
        RIME_NEEDLE("Rime Needle","QUARTZ",2,8,Trigger.REACT,8000,"Your projectile slows a monster for 2 seconds.","8-second cooldown; preserves existing slowness."),
        THAWED_LOCKET("Thawed Locket","GOLD_NUGGET",3,8,Trigger.TICK,30000,"Remove slowness once every 30 seconds."),
        ROOT_HEART("Root Heart","SEEDS",3,9,Trigger.TICK,12000,"Stand still on ground for 8s to restore half a heart.","Requires 9 hunger icons; 12-second cooldown."),
        GRAVE_SEED("Grave Seed","MELON_SEEDS",1,9,Trigger.KILL,8000,"Monster kills restore 1 hunger icon every 8 seconds."),
        BRASS_ESCAPEMENT("Brass Escapement","GOLD_NUGGET",2,10,Trigger.REACT,12000,"A melee hit grants Speed I for 3 seconds.","12-second cooldown; preserves existing speed."),
        PENANCE_COG("Penance Cog","IRON_NUGGET",3,10,Trigger.ATTACK,0,"Deal 18% more melee damage to monsters while weakened."),
        SURGEON_THIMBLE("Surgeon's Thimble","IRON_NUGGET",2,11,Trigger.REGAIN,0,"Natural food healing restores 25% more, at most 1/4 heart."),
        QUARANTINE_MASK("Quarantine Mask","LEATHER",3,11,Trigger.TICK,45000,"Remove poison once every 45 seconds."),
        SILVER_VERDICT("Silver Verdict","IRON_NUGGET",2,12,Trigger.ATTACK,0,"Deal 15% more melee damage to witches and illagers."),
        MIRROR_SHARD("Mirror Shard","PRISMARINE_CRYSTALS",1,12,Trigger.REACT,10000,"After a monster shoots you, its next hit within 3s", "deals 20% less damage to you; 10-second cooldown."),
        SALT_CENSER("Salt Censer","SUGAR",3,13,Trigger.HURT,0,"Take 25% less explosion damage while blocking."),
        CHOIR_SHELL("Choir Shell","PRISMARINE_SHARD",3,13,Trigger.CLEAR,0,"Clearing a combat room removes weakness and slowness."),
        DIVER_SEAL("Diver's Seal","CLAY_BALL",1,14,Trigger.HURT,0,"Take 50% less drowning damage."),
        RELIQUARY_KEY("Reliquary Key","TRIPWIRE_HOOK",4,14,Trigger.CLEAR,0,"Clearing combat repairs 12 main-hand durability.","Only ordinary, breakable equipment is repaired."),
        HANGMAN_LOOP("Hangman's Loop","STRING",1,15,Trigger.HURT,0,"Take 40% less suffocation damage."),
        FASTING_SPOON("Fasting Spoon","STICK",2,15,Trigger.FOOD,10000,"A meal restores an extra half hunger icon every 10s."),
        STAR_CHART("Star Chart","PAPER",2,16,Trigger.HURT,0,"Take 20% less damage from Endermen, Endermites", "and Shulkers, including their projectiles."),
        NIGHT_TALLOW("Night Tallow","GHAST_TEAR",3,16,Trigger.TICK,30000,"Remove blindness once every 30 seconds."),
        THORN_BROOCH("Thorn Brooch","FLINT",3,17,Trigger.REACT,3000,"A melee monster hitting you takes 1 damage every 3s.","No recursive retaliation or damage multipliers."),
        SANCTUARY_ACORN("Sanctuary Acorn","INK_SACK",4,17,Trigger.CLEAR,30000,"Clearing combat grants Resistance I for 4 seconds.","30-second cooldown; preserves existing resistance."),
        WAX_SEAL("Wax Seal","CLAY_BALL",1,18,Trigger.HURT,0,"Take 45% less cactus contact damage."),
        VIGIL_WICK("Vigil Wick","STRING",3,18,Trigger.TICK,40000,"Remove wither once every 40 seconds."),
        CRIMSON_SUTURE("Crimson Suture","REDSTONE",3,19,Trigger.REGAIN,0,"Instant magical healing restores 25% more below", "half health, capped at an extra quarter heart."),
        CHALICE_CHAIN("Chalice Chain","GOLD_NUGGET",2,19,Trigger.KILL,8000,"Monster kills restore 1 saturation every 8 seconds.","Cannot exceed your current food level."),
        BASILICA_CHIP("Basilica Chip","BRICK",1,20,Trigger.HURT,0,"Take 40% less falling-block and anvil damage."),
        FRACTURED_ICON("Fractured Icon","QUARTZ",3,20,Trigger.HURT,0,"Take 20% less melee damage while at full health."),
        SPORE_PENDANT("Spore Pendant","SLIME_BALL",2,21,Trigger.REACT,12000,"Your melee hit weakens a monster for 3 seconds.","12-second cooldown; preserves existing weakness."),
        MYCELIAL_PAD("Mycelial Pad","PAPER",2,21,Trigger.FOOD,20000,"Prevent 1 point of food loss every 20 seconds."),
        IRON_WRIT("Iron Writ","IRON_NUGGET",2,22,Trigger.ATTACK,0,"Deal 15% more melee damage to armored monsters."),
        CUSTODIAN_RIVET("Custodian Rivet","FLINT",2,22,Trigger.DURABILITY,15000,"Prevent up to 2 shield wear every 15 seconds.","Only ordinary, breakable shields qualify."),
        VELVET_RIBBON("Velvet Ribbon","STRING",2,23,Trigger.HURT,0,"Take 15% less melee damage while sneaking."),
        FUNERAL_BUTTON("Funeral Button","STONE_BUTTON",2,23,Trigger.KILL,15000,"Monster kills remove blindness every 15 seconds."),
        AMBER_PRISM("Amber Prism","GOLD_NUGGET",1,24,Trigger.HURT,0,"Take 50% less lightning damage."),
        BAPTISM_DROP("Baptism Drop","GHAST_TEAR",3,24,Trigger.REGAIN,0,"Regeneration-potion healing restores 20% more,", "capped at an extra quarter heart per pulse."),
        MUFFLED_CLAPPER("Muffled Clapper","IRON_NUGGET",3,25,Trigger.REACT,25000,"After a monster shoots you, gain Resistance I for 2s.","25-second cooldown; preserves existing resistance."),
        BELL_COUNTERWEIGHT("Bell Counterweight","CLAY_BALL",2,25,Trigger.ATTACK,0,"Deal 15% more melee damage to monsters while slowed."),
        CARRION_TOKEN("Carrion Token","BONE",2,26,Trigger.ATTACK,0,"Deal 16% more melee damage to monsters", "at or below one quarter of their maximum health."),
        KEEPER_WHISTLE("Keeper's Whistle","FEATHER",2,26,Trigger.KILL,20000,"Monster kills grant Jump Boost I for 4 seconds.","20-second cooldown; preserves existing jump boost."),
        OPAL_CABOCHON("Opal Cabochon","PRISMARINE_CRYSTALS",2,27,Trigger.HURT,0,"Take 40% less dragon-breath damage."),
        PRISMATIC_CLASP("Prismatic Clasp","PRISMARINE_SHARD",2,27,Trigger.TICK,30000,"Remove nausea once every 30 seconds."),
        FOUNDRY_SLAG("Foundry Slag","COAL",2,28,Trigger.ATTACK,0,"Deal 12% more melee damage to burning monsters."),
        TEMPERED_RIVET("Tempered Rivet","IRON_NUGGET",3,28,Trigger.DURABILITY,20000,"Prevent up to 2 armor wear below 25% durability.","20-second cooldown; ordinary breakable armor only."),
        MENAGERIE_TAG("Menagerie Tag","NAME_TAG",2,29,Trigger.ATTACK,0,"Deal 15% more melee damage to spiders", "and silverfish."),
        PALE_FEATHER("Pale Feather","FEATHER",1,29,Trigger.HURT,0,"Take 45% less flying-into-wall damage."),
        SODDEN_BOOKMARK("Sodden Bookmark","PAPER",2,30,Trigger.ATTACK,0,"Your projectiles deal 15% more damage to guardians."),
        SCRIBE_REED("Scribe's Reed","SUGAR_CANE",2,30,Trigger.TICK,10000,"When air falls below 100 ticks, restore 40 air.","10-second cooldown; never exceeds maximum air."),
        OBSIDIAN_CLASP("Obsidian Clasp","FLINT",3,31,Trigger.HURT,0,"Take 15% less explosion damage while on the ground."),
        VESTRY_PIN("Vestry Pin","IRON_NUGGET",2,31,Trigger.REACT,25000,"A monster's melee hit removes weakness from you.","25-second cooldown."),
        PAUPER_COIN("Pauper Coin","GOLD_NUGGET",1,32,Trigger.EXPERIENCE,8000,"Picking up 1-5 experience grants 2 extra experience.","8-second cooldown."),
        GILDED_CRUMB("Gilded Crumb","WHEAT",2,32,Trigger.FOOD,8000,"A meal removes up to 1 exhaustion every 8 seconds."),
        ASTRAL_COMPASS("Astral Compass","COMPASS",3,33,Trigger.ATTACK,0,"Your projectiles deal 12% more damage to monsters", "at least 8 blocks away from you when hit."),
        ORBIT_BEAD("Orbit Bead","ENDER_PEARL",2,33,Trigger.HURT,0,"Take 15% less projectile damage while airborne.","Does not activate in vehicles."),
        LABYRINTH_THREAD("Labyrinth Thread","STRING",2,34,Trigger.TICK,30000,"Remove hunger once every 30 seconds."),
        MOURNER_TREAD("Mourner's Tread","LEATHER",3,34,Trigger.HURT,0,"Sneaking reduces fall damage by 1 heart.","Combined baubles never remove over 75% of a hit."),
        ABSOLUTION_MEDAL("Absolution Medal","GOLD_NUGGET",4,35,Trigger.CLEAR,30000,"Clearing combat restores up to 2 hunger icons.","30-second cooldown."),
        LAST_CANDLE("Last Candle","BLAZE_POWDER",5,35,Trigger.CLEAR,0,"Clearing combat removes poison and wither."),
        // ---------------------------------------------------------------- generation 7, Floor I's new themes (36..53)
        FOUNDERS_FORK("Founder's Tuning Fork","GOLD_NUGGET",2,36,Trigger.ATTACK,0,"Deal 18% more melee damage to a monster","that struck you in the last 3 seconds."),
        BRONZE_TOLL("Bronze Toll","CLAY_BRICK",3,36,Trigger.REACT,20000,"A blow that leaves you below half health slows","monsters within 4 blocks for 3s; 20-second cooldown."),
        MOTH_COCOON("Moth Cocoon","STRING",3,37,Trigger.REACT,60000,"Falling below 4 hearts wraps you in 4 absorption","hearts for 6s; 60-second cooldown."),
        WINGDUST_PHIAL("Wing-Dust Phial","SUGAR",1,37,Trigger.HURT,0,"Take 20% less damage from vexes, blazes and ghasts,","including their projectiles."),
        VOTIVE_STUB("Votive Stub","CLAY_BALL",2,38,Trigger.CLEAR,30000,"Clearing a combat room grants Fire Resistance for 20s.","30-second cooldown; preserves existing fire resistance."),
        WRIGHTS_TAPER("Candlewright's Taper","STICK",1,38,Trigger.REACT,8000,"Your melee hit sets a monster alight for 2 seconds.","8-second cooldown."),
        LIBRARIANS_CHAIN("Librarian's Chain","IRON_NUGGET",2,39,Trigger.ATTACK,0,"Each monster you kill in a room adds 3% melee damage","there, up to 12%; leaving the room resets it."),
        MARGIN_NOTE("Margin Note","PAPER",3,39,Trigger.HURT,0,"Take 25% less damage once you have stood still","for a second."),
        BATH_SPONGE("Bathhouse Sponge","SPONGE",2,40,Trigger.TICK,5000,"While standing in water, restore half a heart","every 5 seconds."),
        PENITENT_PUMICE("Penitent's Pumice","FLINT",1,40,Trigger.HURT,0,"Take 50% less hot-floor (magma block) damage."),
        STONE_LIKENESS("Stone Likeness","CLAY_BRICK",5,41,Trigger.HURT,90000,"A blow of more than 6 hearts is cut to 4 hearts.","90-second cooldown."),
        EFFIGY_WAX("Effigy Wax","CLAY_BALL",2,41,Trigger.HURT,0,"Take 30% less projectile damage while sneaking."),
        MARKET_LEDGER("Grave Market Ledger","BOOK",1,42,Trigger.CLEAR,0,"Clearing a combat room grants 2 extra experience","per threat level."),
        MOURNERS_OBOL("Mourner's Obol","GOLD_NUGGET",3,42,Trigger.KILL,6000,"Killing a monster that hurt you in the last 5s","restores 1 heart; 6-second cooldown."),
        WEEPING_BOUGH("Weeping Bough","SAPLING",2,43,Trigger.TICK,40000,"Below half health, gain Regeneration I for 4s.","40-second cooldown; preserves existing regeneration."),
        BITTER_FRUIT("Bitter Fruit","APPLE",1,43,Trigger.FOOD,15000,"A meal eaten below half health also heals 1 heart.","15-second cooldown."),
        CASKET_NAIL("Casket Nail","IRON_NUGGET",2,44,Trigger.CLEAR,0,"Clearing combat repairs 4 durability on each","ordinary, breakable armour piece you wear."),
        CORRODED_SIGIL("Corroded Sigil","FLINT",2,44,Trigger.ATTACK,0,"Deal 15% more melee damage to wither skeletons."),
        LANTERN_GLASS("Lantern Glass","GLASS_BOTTLE",2,45,Trigger.REACT,10000,"A monster striking you in melee is set alight","for 2 seconds; 10-second cooldown."),
        LAMPLIGHTERS_HOOK("Lamplighter's Hook","TRIPWIRE_HOOK",1,45,Trigger.ENTER,20000,"Entering a room with monsters grants Speed I for 4s.","20-second cooldown; preserves existing speed."),
        SALT_CELLAR("Kitchen Salt-Cellar","SUGAR",2,46,Trigger.FOOD,0,"Your hunger never falls below 3 icons."),
        COOKS_LADLE("Cook's Ladle","BOWL",1,46,Trigger.FOOD,10000,"A meal also restores 2 saturation; 10-second","cooldown. Cannot exceed your food level."),
        MIMES_GLOVE("Mime's Glove","LEATHER",2,47,Trigger.ATTACK,0,"Deal 20% more melee damage to a monster","struck from behind."),
        CURTAIN_CORD("Curtain Cord","STRING",2,47,Trigger.KILL,15000,"Monster kills grant Speed I for 3 seconds.","15-second cooldown; preserves existing speed."),
        GUTTER_RAG("Gutter Saint's Rag","LEATHER",1,48,Trigger.TICK,30000,"Remove mining fatigue once every 30 seconds."),
        RAT_KING_KNOT("Rat-King's Knot","STRING",1,48,Trigger.ATTACK,0,"Deal 18% more melee damage to monsters","shorter than one block."),
        INCENSE_CONE("Incense Cone","SULPHUR",3,49,Trigger.REACT,15000,"A monster striking you weakens monsters within","3 blocks for 3s; 15-second cooldown."),
        SWINGING_THURIBLE("Swinging Thurible","IRON_NUGGET",2,49,Trigger.ATTACK,0,"Your sweep attacks deal 25% more damage."),
        GARDEN_BLOOM("Hanging Bloom","RED_ROSE",2,50,Trigger.CLEAR,30000,"Clearing a combat room grants Regeneration I for 6s.","30-second cooldown; preserves existing regeneration."),
        GARDENERS_TWINE("Gardener's Twine","STRING",3,50,Trigger.REACT,20000,"A monster striking you is rooted (Slowness III)","for 1.5 seconds; 20-second cooldown."),
        HOSTEL_BLANKET("Hostel Blanket","WOOL",1,51,Trigger.TICK,5000,"After 10 seconds unhurt, restore half a heart","every 5 seconds."),
        PILGRIMS_TOKEN("Pilgrim's Token","GOLD_NUGGET",2,51,Trigger.ENTER,30000,"Entering a room with monsters grants Resistance I","for 3s; 30-second cooldown."),
        SEXTONS_MEASURE("Sexton's Measure","STICK",2,52,Trigger.KILL,8000,"Monster kills repair 3 durability on your held","ordinary weapon or tool; 8-second cooldown."),
        BURIAL_SHROUD("Burial Shroud","PAPER",3,52,Trigger.HURT,0,"Take 15% less damage from undead monsters."),
        UNLIT_WICK("Unlit Wick","STRING",2,53,Trigger.ATTACK,0,"Deal 15% more melee damage in darkness","(light level 4 or less)."),
        NAVE_VEIL("Nave Veil","INK_SACK",4,53,Trigger.HURT,0,"Take 20% less damage from monsters in darkness","(light level 4 or less)."),
        // ---------------------------------------------------------------- generation 7, Floor II: The Underworks (54..77)
        MAGMA_GIZZARD("Magma Gizzard","MAGMA_CREAM",2,54,Trigger.HURT,0,"Take 35% less damage from magma cubes."),
        BASALT_HEART("Basalt Heart","COAL",4,54,Trigger.REACT,45000,"When fire or lava hurts you, gain Fire Resistance","for 6 seconds; 45-second cooldown."),
        STALACTITE_TOOTH("Stalactite Tooth","FLINT",2,55,Trigger.REACT,20000,"A falling block's hit grants Resistance I for 4s.","20-second cooldown; preserves existing resistance."),
        CAVERN_ECHO("Cavern Echo","PRISMARINE_SHARD",3,55,Trigger.ATTACK,0,"Your projectiles deal 15% more damage to monsters","at least 3 blocks above you."),
        GROTTO_CAP("Grotto Cap","BROWN_MUSHROOM",2,56,Trigger.KILL,12000,"Monster kills grant Regeneration I for 3 seconds.","12-second cooldown; preserves existing regeneration."),
        SPOREBURST_SAC("Sporeburst Sac","RED_MUSHROOM",4,56,Trigger.KILL,10000,"A monster you kill bursts, poisoning living monsters","within 3 blocks for 3s; 10-second cooldown."),
        PUMP_VALVE("Pump Valve","IRON_NUGGET",2,57,Trigger.ATTACK,0,"Deal 20% more damage to monsters standing in water."),
        FLOODED_LANTERN("Flooded Lantern","GLASS_BOTTLE",3,57,Trigger.HURT,0,"Take 25% less damage while standing in water."),
        RESONANT_CRYSTAL("Resonant Crystal","QUARTZ",4,58,Trigger.ATTACK,0,"Every third melee hit on the same monster deals","30% more damage."),
        CRYSTAL_LATTICE("Crystal Lattice","PRISMARINE_CRYSTALS",4,58,Trigger.HURT,30000,"Every 30 seconds, the next monster blow is","reduced by 3 hearts."),
        CHITIN_PLATE("Chitin Plate","SPIDER_EYE",2,59,Trigger.HURT,0,"Take 20% less damage from spiders, cave spiders,","silverfish and endermites."),
        VENOM_GLAND("Venom Gland","FERMENTED_SPIDER_EYE",3,59,Trigger.REACT,10000,"Your melee hit poisons a living (not undead)","monster for 3s; 10-second cooldown."),
        MARROW_FLUTE("Marrow Flute","BONE",2,60,Trigger.ATTACK,0,"Your projectiles deal 20% more damage to skeletons,","strays and wither skeletons."),
        BONE_DICE("Bone Dice","BONE",2,60,Trigger.KILL,8000,"Monster kills restore half a heart, or 2 hearts on","a lucky throw (1 in 6); 8-second cooldown."),
        SLING_STONE("Shaft Sling-Stone","FLINT",2,61,Trigger.REACT,10000,"Your projectile hit weakens a monster for 3s.","10-second cooldown; preserves existing weakness."),
        QUENCH_STONE("Quench Stone","SNOW_BALL",2,62,Trigger.ATTACK,0,"Deal 20% more damage to blazes and magma cubes."),
        SPRAY_VEIL("Falls-Spray Veil","STRING",3,62,Trigger.TICK,30000,"Standing within 2 blocks of lava grants Fire","Resistance for 3s; 30-second cooldown."),
        LABYRINTH_CHALK("Labyrinth Chalk","CLAY_BALL",3,63,Trigger.ENTER,20000,"Entering a room with monsters halves the first","monster blow you take there; 20-second cooldown."),
        BLAST_DAMPER("Blast Damper","WOOL",2,64,Trigger.HURT,0,"Take 40% less damage from creeper explosions,","mutant creepers and their minions included."),
        FUSE_SNIPS("Fuse Snips","SHEARS",3,64,Trigger.ATTACK,0,"Deal 25% more melee damage to creepers, mutant","creepers and their minions."),
        SWITCHMANS_FLAG("Switchman's Flag","PAPER",2,65,Trigger.HURT,0,"Take 25% less projectile damage while sprinting."),
        BRAKE_LEVER("Brake Lever","LEVER",3,65,Trigger.REACT,45000,"A blow that leaves you below a third of your health","grants Speed II for 3s; 45-second cooldown."),
        SULFUR_SALVE("Sulfur Salve","SLIME_BALL",2,66,Trigger.TICK,30000,"Remove weakness once every 30 seconds."),
        SPRING_FLASK("Spring Flask","GLASS_BOTTLE",3,66,Trigger.REGAIN,0,"Healing while poisoned or withered is 30% stronger,","at most half a heart extra."),
        QUARRY_WEDGE("Quarry Wedge","IRON_NUGGET",5,67,Trigger.ATTACK,0,"Deal 25% more melee damage to monsters","taller than three blocks."),
        WORM_LURE("Cave-Worm Lure","STRING",2,68,Trigger.KILL,15000,"Monster kills grant Absorption I for 6 seconds.","15-second cooldown; stronger absorption is preserved."),
        FORGE_TEMPER("Forge Temper","IRON_NUGGET",4,69,Trigger.DURABILITY,0,"Below half health, your ordinary equipment","does not wear."),
        RIVERBED_PEBBLE("Riverbed Pebble","CLAY_BALL",2,70,Trigger.CLEAR,20000,"Clearing a combat room restores 4 saturation.","20-second cooldown; cannot exceed your food level."),
        RUST_EATER_TOOTH("Rust-Eater's Tooth","IRON_NUGGET",2,71,Trigger.ATTACK,0,"Deal 25% more melee damage to iron golems,","Rust Golems and snow golems."),
        GEODE_HEART("Geode Heart","QUARTZ",3,72,Trigger.ATTACK,0,"Deal 15% more damage while you have","absorption hearts."),
        ROOTDRINKER("Rootdrinker","BEETROOT_SEEDS",3,73,Trigger.CLEAR,0,"Clearing combat restores 1 heart for every 4 monsters","the room held, up to 4 hearts."),
        SMUGGLERS_SHIV("Smuggler's Shiv","FLINT",4,74,Trigger.ATTACK,0,"Your first melee hit on each monster deals","35% more damage."),
        CONTRABAND_PLATE("Contraband Plate","IRON_NUGGET",3,74,Trigger.HURT,0,"Take 25% less damage from gunners."),
        COLUMN_CAPITAL("Column Capital","CLAY_BALL",2,75,Trigger.REACT,8000,"A monster striking you in melee is knocked back;","bosses stand firm. 8-second cooldown."),
        BURROW_EMBER("Burrow Ember","BLAZE_POWDER",2,76,Trigger.KILL,20000,"Monster kills put out your flames and grant Fire","Resistance for 3s; 20-second cooldown."),
        SMOLDERING_ZEAL("Smoldering Zeal","MAGMA_CREAM",3,76,Trigger.ATTACK,0,"Deal 20% more damage while you are burning."),
        TYRANTS_TALLY("Tyrant's Tally","PAPER",4,77,Trigger.ATTACK,0,"Deal 15% more damage to bosses."),
        FOREMANS_BELL("Foreman's Bell","GOLD_NUGGET",5,77,Trigger.REACT,60000,"A blow that leaves you below a third of your health","throws back and slows monsters within 5 blocks","for 3s; 60-second cooldown."),
        // ---------------------------------------------------------------- generation 7, Floor III: The Abyssal Citadel (78..101)
        SENTINEL_RIVET("Sentinel Rivet","IRON_NUGGET",3,78,Trigger.HURT,0,"Take 25% less damage from wither skeletons."),
        COURTIERS_CLOAK("Courtier's Cloak","LEATHER",4,79,Trigger.HURT,0,"Take 15% less damage from bosses."),
        KNEELERS_CUSHION("Kneeler's Cushion","WOOL",3,79,Trigger.REACT,25000,"A boss's blow grants Resistance I for 3 seconds.","25-second cooldown; preserves existing resistance."),
        SANGUINE_MERLON("Sanguine Merlon","REDSTONE",5,80,Trigger.REACT,0,"Your melee hits heal you for 10% of the damage","dealt, at most half a heart per second."),
        RAMPART_STONE("Rampart Stone","CLAY_BRICK",4,80,Trigger.HURT,0,"Below a quarter of your health, take 25% less","damage from monsters."),
        VOID_THORN("Void Thorn","CHORUS_FRUIT_POPPED",3,81,Trigger.ATTACK,0,"Deal 20% more damage to endermen."),
        GRAVITY_SEED("Gravity Seed","PUMPKIN_SEEDS",3,81,Trigger.TICK,20000,"Remove levitation once every 20 seconds."),
        OBSIDIAN_SPLINTER("Obsidian Splinter","FLINT",5,82,Trigger.HURT,45000,"Every 45 seconds, the next monster blow","is turned aside entirely."),
        SOULFIRE_WICK("Soulfire Wick","STRING",3,83,Trigger.REACT,10000,"Your melee hit withers a monster for 3 seconds.","10-second cooldown; preserves existing wither."),
        SOULFIRE_CENSER("Soulfire Censer","BLAZE_POWDER",4,83,Trigger.CLEAR,0,"Clearing a combat room removes every harmful effect."),
        PENANCE_CHAIN("Chain of Penance","IRON_NUGGET",3,84,Trigger.KILL,6000,"A kill lashes the nearest other monster within","4 blocks for 3 damage; 6-second cooldown."),
        SHACKLE_LINK("Shackle Link","IRON_NUGGET",3,84,Trigger.ATTACK,0,"Deal 18% more damage to slowed monsters."),
        SOUL_EMBER("Soul Ember","BLAZE_POWDER",5,85,Trigger.KILL,0,"Monster kills add 1 absorption heart, up to 4."),
        SKYFALL_TALON("Skyfall Talon","FEATHER",3,86,Trigger.ATTACK,0,"Deal 20% more melee damage while falling."),
        CROWDS_ROAR("Crowd's Roar","BONE",3,87,Trigger.KILL,30000,"Three kills within 10 seconds grant Strength I","for 5s; 30-second cooldown."),
        GLADIATORS_TORC("Gladiator's Torc","GOLD_NUGGET",4,87,Trigger.REACT,20000,"Your melee hit on a boss grants Strength I for 3s.","20-second cooldown; preserves existing strength."),
        HEADSMANS_LEDGER("Headsman's Ledger","BOOK",4,88,Trigger.KILL,60000,"Slaying a boss restores all your health and removes","harmful effects; 60-second cooldown."),
        FERRYMANS_LANTERN("Ferryman's Lantern","GLASS_BOTTLE",3,89,Trigger.ENTER,40000,"Entering a room with monsters grants Fire","Resistance for 6s; 40-second cooldown."),
        PEARL_INDEX("Pearl Index","ENDER_PEARL",3,90,Trigger.REACT,15000,"A monster's projectile hit slows its shooter","for 3 seconds; 15-second cooldown."),
        SERGEANTS_WHISTLE("Sergeant's Whistle","STICK",3,91,Trigger.ATTACK,0,"Deal 25% more damage to monsters a boss summoned."),
        MUSTER_ROLL("Muster Roll","PAPER",3,91,Trigger.HURT,0,"Take 25% less damage from monsters a boss summoned."),
        FOUNDRY_SIGHTS("Foundry Sights","IRON_NUGGET",3,92,Trigger.ATTACK,0,"Your projectiles deal 15% more damage to monsters","within 4 blocks of you."),
        DOOM_RIVET("Doom Rivet","IRON_NUGGET",4,92,Trigger.ATTACK,0,"Deal 20% more damage to mutants."),
        SILVERED_RETORT("Silvered Retort","GLASS_BOTTLE",4,93,Trigger.REACT,4000,"A monster striking you in melee takes back 25% of","the blow, at most 2 hearts; 4-second cooldown."),
        UNMARRED_REFLECTION("Unmarred Reflection","QUARTZ",3,93,Trigger.ATTACK,0,"Deal 15% more melee damage while at full health."),
        ASHEN_CROWN_SHARD("Ashen Crown-Shard","GOLD_NUGGET",4,94,Trigger.ENTER,60000,"Entering a boss's room grants Strength I for 6s.","60-second cooldown; preserves existing strength."),
        THRONE_ASH("Throne Ash","SULPHUR",3,94,Trigger.REGAIN,0,"Healing in a boss's room is 20% stronger,","at most half a heart extra."),
        GATEBREAKER_SIGIL("Gatebreaker Sigil","NETHER_BRICK_ITEM",5,95,Trigger.ATTACK,0,"Deal 25% more damage to the floor guardians","and the Throne's sovereign."),
        ABYSSAL_KEYSTONE("Abyssal Keystone","OBSIDIAN",5,95,Trigger.ENTER,300000,"Entering a Descent or the Throne grants 8 absorption","hearts for 30s; 5-minute cooldown."),
        PYRE_URN("Pyre Urn","FLOWER_POT_ITEM",4,96,Trigger.CLEAR,60000,"Clearing a boss's room grants Health Boost I for","2 minutes and heals 2 hearts; 60-second cooldown."),
        STAR_IRON_LENS("Star-Iron Lens","QUARTZ",3,97,Trigger.HURT,0,"Take 30% less projectile damage from monsters","more than 10 blocks away."),
        STARFALL_SHARD("Starfall Shard","FIREWORK_CHARGE",4,97,Trigger.REACT,10000,"Fall damage strikes monsters within 3 blocks of","your landing for as much, at most 3 hearts.","10-second cooldown."),
        GRAVE_WIND_SHROUD("Grave-Wind Shroud","PAPER",4,98,Trigger.TICK,0,"Monsters within 3 blocks of you are slowed","every 2 seconds."),
        MOLTEN_CORE("Molten Core","MAGMA_CREAM",3,99,Trigger.HURT,0,"Take 20% less damage from monsters while burning."),
        CURSED_COIN("Cursed Coin","GOLD_NUGGET",3,100,Trigger.ATTACK,0,"Deal 25% more damage to monsters, but take 10%","more damage from them."),
        BASTION_STANDARD("Bastion Standard","STICK",4,101,Trigger.TICK,20000,"With 3 or more monsters within 6 blocks, gain","Resistance I for 2s; 20-second cooldown."),
        BASTION_HORN("Bastion Horn","BONE",3,101,Trigger.ATTACK,0,"Deal 15% more damage with 3 or more monsters","within 5 blocks of you."),
        // ---------------------------------------------------------------- generation 7 trophy: the victor's reward (never in a chest; Trophies awards it)
        VICTORS_LAUREL("Laurel of the Unbowed","GOLD_NUGGET",5,101,Trigger.HURT,180000,"Deal 15% more damage to bosses. Once every 3 minutes,","a blow that would leave you below 3 hearts leaves","you at 3 hearts instead.");
        public final String title,material;
        public final int rank,theme;
        public final Trigger trigger;
        public final long cooldownMillis;
        public final List<String> description;
        Bauble(String title,String material,int rank,int theme,Trigger trigger,long cooldown,String...description){this.title=title;this.material=material;this.rank=rank;this.theme=theme;this.trigger=trigger;this.cooldownMillis=cooldown;this.description=Collections.unmodifiableList(Arrays.asList(description));}
        public String cooldownKey(){switch(this){case MARROW_BEAD:return "marrowAt";case LAMB_BELL:return "bellAt";case WARDEN_EYE:return "wardAt";case CROWN_OF_MERCY:return "mercyAt";default:return "relic_"+name()+"At";}}
        /** Generation 7: the floor whose reliquaries hold this bauble (its theme's floor). */
        public int floor(){return Floors.floorOfTheme(theme);}
        /** A trophy is awarded (Trophies), never found in a chest. */
        public boolean trophy(){return this==VICTORS_LAUREL;}
    }
    public static final class Profile {
        public final String theme,prefix,essence,provision,passage;
        public final List<Gear> gear;
        Profile(String theme,String prefix,String essence,String provision,String passage,Gear...gear){this.theme=theme;this.prefix=prefix;this.essence=essence;this.provision=provision;this.passage=passage;this.gear=Collections.unmodifiableList(Arrays.asList(gear));}
    }
    public static final List<Profile> PROFILES=Collections.unmodifiableList(Arrays.asList(
        new Profile("Weeping Cellar","Tear-Stained","REDSTONE","COOKED_BEEF","They built a refuge for frightened children. Then the keepers decided that fear was proof of sin. Every locked door became another confession.",Gear.SWORD,Gear.BOOTS,Gear.SHIELD),
        new Profile("Ossuary","Ossuary","BONE","BREAD","The bones remember names the keepers erased. The catacombs were not dug beneath the house: they grew wherever mercy was withheld.",Gear.AXE,Gear.CHESTPLATE,Gear.HELMET),
        new Profile("Drowned Confessional","Confessor's","PRISMARINE_SHARD","COOKED_FISH","The wells contain no rain. Listen beside them and hear the promises made to children who never came home.",Gear.HELMET,Gear.FISHING_ROD,Gear.BOOTS),
        new Profile("Cinder Chapel","Ash-Bound","BLAZE_POWDER","BAKED_POTATO","The chapel burns because someone still tends its furnace. Its congregation traded forgiveness for the comforting certainty of punishment.",Gear.CHESTPLATE,Gear.PICKAXE,Gear.SHIELD),
        new Profile("Hollow Choir","Hollow","ENDER_PEARL","BREAD","Behind the mirrors, the choir sings your doubts in your own voice. Nothing here can follow you through a threshold unless you invite it.",Gear.BOW,Gear.HELMET,Gear.SWORD),
        new Profile("Rotten Nursery","Mourner's","SLIME_BALL","CARROT_ITEM","There is no final cellar. The house grows whenever its keeper confuses suffering with goodness. Carry the candle outward. Leave a door open.",Gear.HOE,Gear.SHOVEL,Gear.BOOTS),
        new Profile("Ashen Archive","Scribe's","PAPER","BREAD","The books burned before the witnesses could read them. A young scribe copied every name into the margins. Ash still gathers around the words that survived.",Gear.PICKAXE,Gear.BOW,Gear.HELMET),
        new Profile("Vermilion Court","Vermilion","GOLD_NUGGET","COOKED_BEEF","The court weighed the ink, never the plea. One empty chair was reserved for mercy; no one dared to sit.",Gear.SWORD,Gear.LEGGINGS,Gear.CHESTPLATE),
        new Profile("Frozen Sacristy","Rime-Bound","SNOW_BALL","BAKED_POTATO","Winter sealed the cupboards with the bread inside. A keeper called hunger a lesson. Beneath the ice, the smallest candle continues to warm a key.",Gear.BOOTS,Gear.BOW,Gear.SHIELD),
        new Profile("Rootbound Crypt","Rootbound","SEEDS","CARROT_ITEM","Roots found the graves before the mourners did. They broke each stone until a name could breathe. The house calls this decay; the garden calls it returning.",Gear.HOE,Gear.SHOVEL,Gear.AXE),
        new Profile("Clockwork Penance","Clockwork","IRON_NUGGET","COOKED_CHICKEN","Every confession advanced the clock one tooth. Every pardon stopped its hands. The maker dismantled the bell rather than admit that the hours belonged to everyone.",Gear.PICKAXE,Gear.CHESTPLATE,Gear.SWORD),
        new Profile("Plague Infirmary","Physician's","STRING","BAKED_POTATO","The infirmary counted fevers as failings. One nurse hid clean linen beneath the ledger and taught the patients to open windows. Her thimble outlasted every lock.",Gear.HELMET,Gear.LEGGINGS,Gear.SHIELD),
        new Profile("Mirror Tribunal","Silvered","QUARTZ","BREAD","Each judge faced a covered mirror. A child pulled the cloth aside, and the tribunal saw who had written its laws. The verdict has been pending ever since.",Gear.SWORD,Gear.SHIELD,Gear.BOW),
        new Profile("Salt Cathedral","Salt-Worn","SUGAR","COOKED_FISH","The cathedral kept its tears in salt jars. When the choir spoke instead of singing, the jars cracked. No voice was pure; all of them were needed.",Gear.CHESTPLATE,Gear.HELMET,Gear.FISHING_ROD),
        new Profile("Sunken Reliquary","Tide-Kept","PRISMARINE_CRYSTALS","COOKED_FISH","The reliquary sank under its locks. Its diver returned with a clay seal and no gold. Some doors are opened by bringing someone home.",Gear.FISHING_ROD,Gear.HELMET,Gear.SHOVEL),
        new Profile("Gallows Refectory","Last-Supper","LEATHER","COOKED_MUTTON","At supper the keepers left a rope beside each plate. A cook cut the ropes into handles for spoons. The first meal without a sentence tasted only of bread.",Gear.AXE,Gear.LEGGINGS,Gear.SHOVEL),
        new Profile("Starless Observatory","Starless","GLOWSTONE_DUST","GOLDEN_CARROT","The astronomer painted the dome black to make the house the center of the sky. An apprentice scraped one pinhole clean. Through it, the world remained impossibly large.",Gear.BOW,Gear.PICKAXE,Gear.BOOTS),
        new Profile("Thorn Sanctuary","Briar-Kept","VINE","APPLE","Thorns guarded the last garden from its gardeners. A traveler laid down the shears and waited. By morning a narrow path led outward, wide enough for two.",Gear.HOE,Gear.AXE,Gear.LEGGINGS),
        new Profile("Wax Sepulchre","Vigil-Kept","CLAY_BALL","BREAD","The keepers sealed each candle in wax so that no flame would change. A mourner warmed the seals with her hands. By dawn the graves had windows.",Gear.SMITE_SWORD,Gear.THORNS_CHESTPLATE,Gear.MENDING_SHIELD),
        new Profile("Sanguine Cloister","Suture-Bound","REDSTONE","COOKED_BEEF","The cloister measured devotion in drops. Its youngest physician tore the measuring cloth into bandages. The first vow she broke was the one that forbade relief.",Gear.SMITE_SWORD,Gear.BLAST_LEGGINGS,Gear.AQUA_HELMET),
        new Profile("Shattered Basilica","Fractured","BRICK","BAKED_POTATO","When the basilica cracked, the congregation called it judgment. A mason found room for a door in every fracture. The stones had been carrying too much.",Gear.CLEAVING_AXE,Gear.THORNS_CHESTPLATE,Gear.PUNCH_BOW),
        new Profile("Fungal Hospice","Mycelial","SLIME_BALL","CARROT_ITEM","The hospice grew soft where its rules had been hardest. Mushrooms lifted the floorboards over a hidden pantry. Someone had been feeding the forgotten all along.",Gear.MENDING_HOE,Gear.FORTUNE_SHOVEL,Gear.DEPTH_BOOTS),
        new Profile("Iron Inquisition","Iron-Writ","IRON_NUGGET","COOKED_CHICKEN","The interrogators trusted iron because it could not answer. Their smith forged a shield from the last set of shackles. At last the iron had something to say.",Gear.CLEAVING_AXE,Gear.BLAST_LEGGINGS,Gear.MENDING_SHIELD),
        new Profile("Velvet Catacomb","Velvet-Lined","STRING","COOKED_MUTTON","Velvet lined the corridors so no one would hear the grieving. A seamstress stitched every muffled name into its hem. The cloth became too heavy to hang.",Gear.DEPTH_BOOTS,Gear.PUNCH_BOW,Gear.SMITE_SWORD),
        new Profile("Amber Baptistry","Amber-Kept","GOLD_NUGGET","GOLDEN_CARROT","The amber held a single drop from every baptism. When the basin broke, its water remembered the river. Nothing born in motion had consented to remain still.",Gear.AQUA_HELMET,Gear.LUCK_ROD,Gear.DEPTH_BOOTS),
        new Profile("Silent Belfry","Hushed","FLINT","BREAD","The bell was silent because its tongue had been locked away. The ringer tapped a spoon against the rail. The city answered with a thousand ordinary sounds.",Gear.PUNCH_BOW,Gear.SILK_PICKAXE,Gear.BLAST_LEGGINGS),
        new Profile("Carrion Conservatory","Carrion-Kept","BONE","COOKED_CHICKEN","They called the birds unclean for tending what the house abandoned. The conservator unlatched their cages. In their absence the keepers learned what care had looked like.",Gear.CLEAVING_AXE,Gear.MENDING_HOE,Gear.FORTUNE_SHOVEL),
        new Profile("Opaline Sepulcher","Opaline","PRISMARINE_CRYSTALS","APPLE","The opal changed color whenever a visitor told the truth. The keepers shuttered every lamp. A child carried the stone outside and watched it become a hundred skies.",Gear.THORNS_CHESTPLATE,Gear.AQUA_HELMET,Gear.SMITE_SWORD),
        new Profile("Sunless Foundry","Tempered","COAL","BAKED_POTATO","The foundry made locks around the clock and named the smoke a sunrise. A worker cast a hinge instead. Light entered through the thing they had never learned to make.",Gear.SILK_PICKAXE,Gear.CLEAVING_AXE,Gear.THORNS_CHESTPLATE),
        new Profile("Pale Menagerie","Pale-Kept","FEATHER","COOKED_MUTTON","Every creature was catalogued by the fear it inspired. The keeper's daughter wrote what each needed on the other side. The catalogue became a set of instructions for leaving.",Gear.PUNCH_BOW,Gear.DEPTH_BOOTS,Gear.MENDING_HOE),
        new Profile("Flooded Scriptorium","Reed-Bound","SUGAR_CANE","COOKED_FISH","Water washed the verdicts from the desks but left the names. The scribes began again with names alone. This time they asked the living how their stories ended.",Gear.LUCK_ROD,Gear.AQUA_HELMET,Gear.FORTUNE_SHOVEL),
        new Profile("Obsidian Vestry","Obsidian","QUARTZ","BREAD","The vestments hung behind black glass, untouched by any weather. A novice broke the pane to mend a traveler's coat. The cold outside had more claim on the cloth.",Gear.BLAST_LEGGINGS,Gear.MENDING_SHIELD,Gear.SILK_PICKAXE),
        new Profile("Gilded Pauperhouse","Crumb-Kept","WHEAT","BAKED_POTATO","Gold covered the pauperhouse door while hunger sat behind it. A cook sold one letter from the sign each morning. By winter the house had no name, and everyone had soup.",Gear.FORTUNE_SHOVEL,Gear.MENDING_HOE,Gear.LUCK_ROD),
        new Profile("Astral Chancel","Astral","ENDER_PEARL","GOLDEN_CARROT","The chancel charted a heaven with the house at its center. A sailor turned the chart until its roads met the horizon. There were more ways home than the keepers allowed.",Gear.PUNCH_BOW,Gear.AQUA_HELMET,Gear.SILK_PICKAXE),
        new Profile("Mourning Labyrinth","Thread-Kept","STRING","APPLE","Each turn promised that grief ended at the next. A traveler tied a thread to the hand beside hers. Together they found that mourning needed company, not an exit.",Gear.DEPTH_BOOTS,Gear.SMITE_SWORD,Gear.MENDING_SHIELD),
        new Profile("Last Absolution","Candle-Kept","BLAZE_POWDER","BREAD","At the last threshold no judge remained. A candle waited beside an unlocked door. The house could offer no final permission to live; that had always belonged to those within it.",Gear.THORNS_CHESTPLATE,Gear.BLAST_LEGGINGS,Gear.MENDING_HOE),
        // ---------------------------------------------------------------- generation 7, Floor I: The House of Mercy (36..53)
        new Profile("Bellfounder's Crypt","Bell-Cast","GOLD_NUGGET","BREAD","The founder cast a bell for every sentence the house passed and buried each one unrung. His apprentice struck the smallest with a spoon. Only the condemned heard it, and knew they were remembered.",Gear.SHIELD,Gear.HELMET,Gear.SMITE_AXE),
        new Profile("Moth Sanctum","Moth-Winged","STRING","COOKIE","The moths came for the candles, and the keepers called it a judgment on the light. A novice opened the shutters instead of closing them. Every moth flew out toward the moon, and the dark that remained was only dark.",Gear.BOW,Gear.BOOTS,Gear.WARD_HELMET),
        new Profile("Candlewright Hall","Wick-Wrought","CLAY_BALL","COOKED_RABBIT","Each candle was dipped a hundred times, one dip for every hour of penance. The candlewright's daughter cut her wicks short. Her candles died by supper, and the penitents went home in the dark, free.",Gear.EMBER_CHESTPLATE,Gear.SHOVEL,Gear.BRAND_SWORD),
        new Profile("Chained Library","Chain-Read","PAPER","BAKED_POTATO","The books were chained so that no one could carry an idea out of the room. The librarian read them aloud at the open window. By spring the street outside could recite what the shelves were forbidden to lend.",Gear.SILK_PICKAXE,Gear.WARD_HELMET,Gear.SWORD),
        new Profile("Penitent Bathhouse","Steam-Scoured","SLIME_BALL","COOKED_FISH","The keepers scrubbed the penitents raw and called the pain a cleaning. An attendant warmed the water in secret. The steam that rose from the baths was the only kindness the house never saw.",Gear.AQUA_HELMET,Gear.DEPTH_BOOTS,Gear.CHESTPLATE),
        new Profile("Hall of Effigies","Effigy-Carved","BRICK","GRILLED_PORK","Every sinner was carved in stone so their shame would outlast them. A sculptor gave each effigy open hands. Visitors began to leave bread in those hands, and no one could say whom they fed.",Gear.THORNS_CHESTPLATE,Gear.LEGGINGS,Gear.SMITE_AXE),
        new Profile("Grave Market","Market-Struck","GOLD_NUGGET","PUMPKIN_PIE","Mourners paid in coin for every prayer said over their dead. One stallholder sold his prayers for a song, then for a story, then for nothing at all. His stall had the longest line in the market.",Gear.FORTUNE_SHOVEL,Gear.LUCK_ROD,Gear.AXE),
        new Profile("Weeping Orchard","Sap-Wept","SAPLING","APPLE","The orchard wept sap because the keepers grafted thorns onto every branch. A gardener cut the thorns away and was punished for each one. The trees bore fruit that winter, and it tasted of salt and of forgiveness.",Gear.HOE,Gear.BOW,Gear.MENDING_SHIELD),
        new Profile("Rusted Reliquary","Rust-Bitten","IRON_NUGGET","BREAD","The relics rusted in their caskets because no one was allowed to touch them. A pilgrim polished one bone with her sleeve. It was only a bone, she said, and it had belonged to someone who would have liked to be held.",Gear.SHIELD,Gear.BLAST_LEGGINGS,Gear.PICKAXE),
        new Profile("Lamplighter's Rest","Lamp-Kept","FLINT","COOKED_MUTTON","The lamplighters walked the halls all night so the keepers could watch everything. The oldest began to skip the last cells. In those dark doorways people slept unseen, which was nearly rest.",Gear.BOOTS,Gear.FISHING_ROD,Gear.FLAME_BOW),
        new Profile("Ashen Kitchens","Hearth-Scorched","COAL","COOKED_BEEF","The kitchens burned the bread on purpose, for the keepers said hunger sharpened repentance. The cook baked a second loaf every night and hid it in the ash. The penitents learned to sift the cold hearth before dawn.",Gear.AXE,Gear.EMBER_CHESTPLATE,Gear.SHOVEL),
        new Profile("Mute Theatre","Mute-Masked","INK_SACK","COOKIE","Actors were forbidden to speak, so the plays told only the keepers' stories. One player turned his back on the stage. Without a word he showed the audience their own faces, and the theatre emptied.",Gear.PUNCH_BOW,Gear.LEGGINGS,Gear.SWEEP_SWORD),
        new Profile("Gutter Abbey","Gutter-Washed","LEATHER","BREAD","The abbey poured its waste into the gutters and its poor into the same streets. A lay sister swept the gutters every morning and fed whoever she found there. The abbey's bells fell silent, but its gutters ran clean.",Gear.BANE_SWORD,Gear.BOOTS,Gear.HOE),
        new Profile("Thurible Gallery","Incense-Wreathed","SULPHUR","COOKED_CHICKEN","The censers swung so the smoke would hide what happened in the gallery. An altar boy let his thurible go cold. In the clear air the congregation saw one another for the first time, and they stood up together.",Gear.SWEEP_SWORD,Gear.AQUA_HELMET,Gear.MENDING_SHIELD),
        new Profile("Hanging Gardens of Mercy","Vine-Hung","VINE","MELON","The gardens hung over the pit so that every flower grew above someone falling. A gardener tied ropes to the vines and lowered them. The keepers counted their blooms; she counted the people who climbed.",Gear.MENDING_HOE,Gear.DEPTH_BOOTS,Gear.PUNCH_BOW),
        new Profile("Pilgrim's Hostel","Road-Worn","WHEAT","BREAD","The hostel charged each pilgrim a confession for a bed. The innkeeper's son wrote them himself, all false and gentle. Everyone who slept there had committed the same small sin: being tired.",Gear.BOOTS,Gear.LEGGINGS,Gear.FISHING_ROD),
        new Profile("Sexton's Workshop","Grave-Dug","BONE","COOKED_RABBIT","The sexton dug graves for people who were still alive, so that they would know their place. He dug them shallow and wide. When the keepers came to inspect them, they found gardens.",Gear.SHOVEL,Gear.SMITE_AXE,Gear.MENDING_HOE),
        new Profile("The Unlit Nave","Unlit","STRING","GOLDEN_CARROT","No candle was lit in the nave, so that no worshipper could read its walls. Someone carved the words deeper, deep enough to read by touch. Hands found them in the dark, and the dark became a library.",Gear.SMITE_SWORD,Gear.WARD_HELMET,Gear.ENDLESS_BOW),
        // ---------------------------------------------------------------- generation 7, Floor II: The Underworks (54..77)
        new Profile("Magma Galleries","Magma-Veined","MAGMA_CREAM","COOKED_BEEF","The Deep Tyrant opened galleries into the molten rock and called the heat a furnace of virtue. The miners learned which walls sweated before they broke, and carved warnings he never bothered to read.",Gear.EMBER_CHESTPLATE,Gear.CINDER_BOOTS,Gear.PICKAXE),
        new Profile("Stalactite Cathedral","Drip-Carved","QUARTZ","COOKED_RABBIT","Water took ten thousand years to hang one spear from the cathedral roof. The Tyrant called it patience and demanded the same of his diggers. They answered slowly, and their slowness held the roof up.",Gear.WARD_HELMET,Gear.SHIELD,Gear.SILK_PICKAXE),
        new Profile("Fungal Grotto","Spore-Grown","BROWN_MUSHROOM","BREAD","Nothing was planted in the grotto, yet it fed half the Underworks. Diggers told to starve learned which caps were bread and which were poison, and kept it in songs too dull for any overseer.",Gear.MENDING_HOE,Gear.BANE_SWORD,Gear.DEPTH_BOOTS),
        new Profile("Drowned Mine","Flood-Silted","CLAY_BALL","COOKED_FISH","The mine flooded the night the pumps were stopped to save oil. One pumpman worked the handle alone until morning. Every miner climbed out on a rope he had tied, and the water kept only the ledger.",Gear.AQUA_HELMET,Gear.LUCK_ROD,Gear.FORTUNE_PICKAXE),
        new Profile("Crystal Hollow","Crystal-Struck","QUARTZ","GOLDEN_CARROT","The crystals rang whenever anyone in the hollow spoke the truth, so the Tyrant forbade speech. The miners hummed instead. The whole hollow sang, and not one of them could be accused of saying anything.",Gear.PUNCH_BOW,Gear.SILK_PICKAXE,Gear.CHESTPLATE),
        new Profile("Spider Warrens","Web-Spun","STRING","COOKED_CHICKEN","Spiders guarded the deep stores, and their webs caught more diggers than thieves. A child learned to walk the threads without waking them. She carried bread across the warrens for a whole year.",Gear.BANE_SWORD,Gear.FLAME_BOW,Gear.LEGGINGS),
        new Profile("Bone Pit","Pit-Bleached","BONE","GRILLED_PORK","The Tyrant threw broken diggers into the pit and called their bones debts repaid. A foreman climbed down each evening to lay them straight, and gave each a shift, as if they were only resting.",Gear.SMITE_AXE,Gear.SHOT_LEGGINGS,Gear.SHIELD),
        new Profile("Forgotten Mineshaft","Shaft-Timbered","COAL","BREAD","No map shows this shaft: its crew refused to dig further. They put down their picks, lit their lamps and sat. The Tyrant sealed them in, and they walked out the far side, which they had dug instead.",Gear.FORTUNE_PICKAXE,Gear.CLEAVING_AXE,Gear.HELMET),
        new Profile("Lava Falls","Fall-Scorched","BLAZE_POWDER","COOKED_BEEF","The falls were the Tyrant's clock: when the lava rose, the shift was over, and not before. A smelter turned one thin stream into a channel of her own, too small to tell time by but enough to cook by.",Gear.CINDER_BOOTS,Gear.BRAND_SWORD,Gear.EMBER_CHESTPLATE),
        new Profile("Silverfish Labyrinth","Maze-Bored","FLINT","PUMPKIN_PIE","The silverfish bored a labyrinth through the old prison walls, and the Tyrant called it a fortress. The prisoners followed the tunnels, and left by a door no keeper had ever built.",Gear.BANE_SWORD,Gear.BLAST_BOOTS,Gear.SILK_PICKAXE),
        new Profile("Gunpowder Depot","Powder-Black","SULPHUR","COOKED_RABBIT","Powder was stacked to the ceiling to remind the diggers what the Tyrant could do. The quartermaster dampened it barrel by barrel. When the order came to blow the lower galleries, nothing happened.",Gear.BLAST_LEGGINGS,Gear.BLAST_BOOTS,Gear.PUNCH_BOW),
        new Profile("Rail Junction","Rail-Worn","IRON_NUGGET","COOKED_MUTTON","Every cart from the junction carried ore up and diggers down. A switchman threw one lever wrong every night. One cart a day went up with people in it, and the ledgers always showed the proper weight.",Gear.BOOTS,Gear.CLEAVING_AXE,Gear.SHOT_LEGGINGS),
        new Profile("Sulfur Springs","Sulfur-Stained","GLOWSTONE_DUST","COOKED_FISH","The springs stank, so no overseer stayed long beside them. The sick were sent there to suffer quietly. In the warm water they recovered instead, and told no one: the smell was the best guard they had.",Gear.AQUA_HELMET,Gear.MENDING_SHIELD,Gear.SHOT_LEGGINGS),
        new Profile("Obsidian Quarry","Quarry-Cut","FLINT","COOKED_BEEF","Obsidian takes longest to cut, and the Tyrant wanted his throne room walled in nothing else. The cutters measured twice and set once. Their wall still stands; his throne room is where they eat lunch.",Gear.PICKAXE,Gear.THORNS_CHESTPLATE,Gear.CLEAVING_AXE),
        new Profile("Glowworm Caves","Worm-Threaded","SLIME_BALL","MELON","Ten thousand worms hung their threads from the ceiling and made a sky beneath the stone. The diggers were forbidden to look up at work. At rest they did, and named the brightest threads for their lost.",Gear.DEPTH_BOOTS,Gear.WARD_HELMET,Gear.ENDLESS_BOW),
        new Profile("Collapsed Forge","Forge-Fallen","IRON_NUGGET","BREAD","The forge collapsed under the weight of the chains the Tyrant ordered cast. The smiths had made every link a little thinner than the last. The roof came down on the chains alone; every smith was at supper.",Gear.MENDING_SHIELD,Gear.BRAND_SWORD,Gear.CHESTPLATE),
        new Profile("Underground River","River-Smoothed","SUGAR_CANE","COOKED_FISH","The river runs beneath the whole Underworks, and the Tyrant never found its end. The ferrywoman did. She took one passenger a night downstream and came back alone, and the river kept her secret.",Gear.LUCK_ROD,Gear.DEPTH_BOOTS,Gear.SHOT_LEGGINGS),
        new Profile("Rust Cavern","Rust-Caked","IRON_NUGGET","GRILLED_PORK","The cavern's golems were built to carry ore and never stop. Rust took their joints one by one. An old engineer oiled only their hands, so when they could no longer walk they could still wave goodbye.",Gear.SMITE_AXE,Gear.BLAST_LEGGINGS,Gear.MENDING_HOE),
        new Profile("Geode Chamber","Geode-Split","QUARTZ","PUMPKIN_PIE","Every geode was split in search of a stone the Tyrant had dreamed of. None held it. A digger kept the halves and gave them to children, who held them to their eyes and saw rooms full of small purple stars.",Gear.SILK_PICKAXE,Gear.WARD_HELMET,Gear.SWEEP_SWORD),
        new Profile("Hollow Roots","Root-Hollowed","BEETROOT_SEEDS","BEETROOT","Roots from the world above grew down through the Underworks looking for water. The Tyrant had them cut. A digger split one lengthwise, found it hollow, and climbed it all the way to the light.",Gear.HOE,Gear.CLEAVING_AXE,Gear.BOOTS),
        new Profile("Smugglers' Tunnels","Contraband","GOLD_NUGGET","COOKED_CHICKEN","The smugglers moved what the Tyrant forbade: medicine, letters, seeds and now and then a person. They charged the rich double and the poor nothing. The tunnels were narrow on purpose, because greed is wide.",Gear.PUNCH_BOW,Gear.SWEEP_SWORD,Gear.BOOTS),
        new Profile("Pillar Caves","Pillar-Borne","CLAY_BALL","BAKED_POTATO","The caves stand because the first diggers left every tenth pillar uncut, against orders. The Tyrant wanted the hall open from wall to wall. Their disobedience holds up the floor he walked on.",Gear.CHESTPLATE,Gear.HELMET,Gear.BLAST_BOOTS),
        new Profile("Ember Burrows","Ember-Burrowed","COAL","GRILLED_PORK","Small creatures burrowed toward the deep heat, and the diggers followed them for warmth. The overseers called it desertion. The deserters kept a fire no one ordered, and let in anyone who came cold.",Gear.FLAME_BOW,Gear.CINDER_BOOTS,Gear.BRAND_SWORD),
        new Profile("The Deep Workings","Deepwork","IRON_NUGGET","GOLDEN_CARROT","At the bottom of everything the Tyrant built the Workings, engines that ran on fear. Their stair goes further down. Whoever reaches it learns his last secret: the engines stopped long ago.",Gear.FORTUNE_PICKAXE,Gear.THORNS_CHESTPLATE,Gear.SWORD),
        // ---------------------------------------------------------------- generation 7, Floor III: The Abyssal Citadel (78..101)
        new Profile("Hellforge Bridges","Bridge-Forged","BLAZE_POWDER","GRILLED_PORK","The Sovereign's bridges were forged hot and laid over the lava sea while still red. The smiths knew how much each span could bear. They told the soldiers, quietly, which planks to step around.",Gear.BRAND_SWORD,Gear.CINDER_BOOTS,Gear.SHIELD),
        new Profile("Throne Approach","Processional","GOLD_NUGGET","BREAD","Every petitioner crawled the length of the approach, and most were refused at its end. A guard began telling them the answer at the gate. Many turned back on their feet instead of their knees.",Gear.SMITE_SWORD,Gear.WARD_HELMET,Gear.LEGGINGS),
        new Profile("Bleeding Ramparts","Blood-Mortared","REDSTONE","GRILLED_PORK","The ramparts were mortared with what the Sovereign took from his own soldiers. A mason mixed in river clay when no one was watching. The walls held anyway, and the soldiers bled a little less.",Gear.THORNS_CHESTPLATE,Gear.SHOT_LEGGINGS,Gear.SWEEP_SWORD),
        new Profile("Void Gardens","Void-Grown","CHORUS_FRUIT_POPPED","COOKIE","Nothing grows in the abyss, so the Sovereign ordered gardens of nothing: pale stalks that drank the dark. A gardener planted one living seed. It grew toward the only warmth it found, her.",Gear.WARD_HELMET,Gear.ENDLESS_BOW,Gear.DEPTH_BOOTS),
        new Profile("Obsidian Spire","Spire-Hewn","FLINT","COOKED_CHICKEN","The spire was raised so the Sovereign could see every corner of his citadel. Its builders left one window facing the sky. The guards posted there watched the stars, which needed no keepers.",Gear.BLAST_LEGGINGS,Gear.PUNCH_BOW,Gear.SILK_PICKAXE),
        new Profile("Soulfire Chapel","Soulfire","BONE","BAKED_POTATO","The chapel burned souls as candles so that the Sovereign's prayers would be bright. A priest prayed in the dark instead. Her prayers were short, and they were all the same: let them go.",Gear.SMITE_SWORD,Gear.EMBER_CHESTPLATE,Gear.MENDING_SHIELD),
        new Profile("Chain Hall","Chain-Hung","IRON_NUGGET","COOKED_BEEF","Every chain in the hall once held someone who displeased the Sovereign. The smith who forged them left one link in each a hair's width open. It took the prisoners years to find it, and seconds to slip free.",Gear.CLEAVING_AXE,Gear.SHOT_LEGGINGS,Gear.MENDING_SHIELD),
        new Profile("Furnace of Souls","Furnace-Fed","COAL","COOKED_CHICKEN","The citadel ran on a furnace fed with the fears of the conquered. Its stoker was afraid of nothing but the dark. One night he opened the furnace door to see by, and every fear inside flew out toward someone braver.",Gear.BRAND_SWORD,Gear.BLAST_BOOTS,Gear.CHESTPLATE),
        new Profile("Shattered Sky Halls","Sky-Shattered","FEATHER","APPLE","The Sovereign roofed his halls with painted skies and broke the real one out of spite. The painters left the smallest cracks unmended. Through them, on clear nights, the soldiers saw a sky that no one owned.",Gear.BOOTS,Gear.PUNCH_BOW,Gear.WARD_HELMET),
        new Profile("Bone Colosseum","Arena-Blooded","BONE","COOKED_BEEF","Captives fought in the colosseum for the Sovereign's amusement. Two champions once laid their weapons down in the sand and sat. The crowd fell silent, then cheered, and the stands never filled again.",Gear.SWEEP_SWORD,Gear.THORNS_CHESTPLATE,Gear.SHIELD),
        new Profile("The Gallows Keep","Gallows-Kept","STRING","BREAD","The keep hanged its prisoners at dawn so the whole citadel would wake to it. The executioner tied every knot to slip. He was hanged himself in the end, and the knot he had taught them slipped for him too.",Gear.SMITE_AXE,Gear.HELMET,Gear.ENDLESS_BOW),
        new Profile("Lava Sea Docks","Dock-Tarred","COAL","COOKED_FISH","Iron ships crossed the lava sea carrying the Sovereign's armies. The dockhands loaded them heavy at the bow. Not one ship ever sank, but every one arrived late, and the wars were over before they began.",Gear.LUCK_ROD,Gear.CINDER_BOOTS,Gear.CLEAVING_AXE),
        new Profile("Ender Archive","Archive-Sealed","PAPER","GOLDEN_CARROT","The archive held a record of every rebellion, so that none would ever be tried twice. The archivist filed them all under hope. The Sovereign never once looked there.",Gear.SILK_PICKAXE,Gear.WARD_HELMET,Gear.PUNCH_BOW),
        new Profile("Crimson Barracks","Crimson-Drilled","REDSTONE","BREAD","The soldiers drilled until they could march without thinking. One sergeant drilled them to halt without thinking too. Ordered to march on the last free town, the regiment stopped at its gate, in step.",Gear.SWEEP_SWORD,Gear.CHESTPLATE,Gear.SHOT_LEGGINGS),
        new Profile("Doom Foundry","Doom-Cast","IRON_NUGGET","COOKED_RABBIT","The foundry cast weapons for wars the Sovereign had not yet declared. Its founders cast every blade a little soft. They broke on the first stroke, and their soldiers lived to complain about it.",Gear.CLEAVING_AXE,Gear.BLAST_LEGGINGS,Gear.FLAME_BOW),
        new Profile("Hall of Mirrors","Mirror-Silvered","QUARTZ","BREAD","The Sovereign's mirrors showed every visitor a lesser version of themselves. A glazier ground one mirror true. Those who looked into it saw only who they were, which was more than the Sovereign could bear to see.",Gear.SMITE_SWORD,Gear.WARD_HELMET,Gear.MENDING_SHIELD),
        new Profile("Ashen Throne Room","Throne-Ashed","COAL","GOLDEN_CARROT","An older king sat on this throne before the Sovereign burned him and it together. The ash was swept into the corners and forgotten. It remembered, and it settles on whoever sits there, a little heavier every year.",Gear.SMITE_AXE,Gear.THORNS_CHESTPLATE,Gear.ENDLESS_BOW),
        new Profile("The Abyss Gate","Gate-Bound","FLINT","GOLDEN_CARROT","The gate opens onto the abyss, and the Sovereign claimed he alone could hold it shut. It was never open. He stood before it every day pretending, so no one would notice the wall behind it.",Gear.BRAND_SWORD,Gear.THORNS_CHESTPLATE,Gear.WARD_HELMET),
        new Profile("Pyre of Kings","Pyre-Crowned","BLAZE_POWDER","GOLDEN_CARROT","Every king the Sovereign defeated burned on the same pyre, crown and all. A servant raked the gold from the ashes and sent it home to their towns. He ruled the kings; the gold ruled nothing.",Gear.EMBER_CHESTPLATE,Gear.SMITE_SWORD,Gear.MENDING_HOE),
        new Profile("Starfall Observatory","Star-Struck","GLOWSTONE_DUST","COOKED_MUTTON","The astronomers charted falling stars as omens of the Sovereign's victories. One kept a second chart of where they truly fell: everywhere his armies had never reached, which was nearly everywhere.",Gear.FLAME_BOW,Gear.WARD_HELMET,Gear.BOOTS),
        new Profile("Wraith Catacombs","Wraith-Haunted","BONE","COOKIE","The wraiths of the catacombs were soldiers who died following orders. They still patrol, because no one ever relieved them. Say dismissed clearly enough, and some of them will finally sit down.",Gear.SMITE_SWORD,Gear.SHOT_LEGGINGS,Gear.MENDING_HOE),
        new Profile("Molten Reliquary","Molten-Sealed","MAGMA_CREAM","GOLDEN_CARROT","The Sovereign melted the relics of every faith he conquered into one reliquary of his own, and made the faithful watch. It came out of the mould too heavy to lift, so he worships it on his knees.",Gear.BRAND_SWORD,Gear.EMBER_CHESTPLATE,Gear.FORTUNE_PICKAXE),
        new Profile("Cursed Treasury","Hoard-Cursed","GOLD_NUGGET","BAKED_POTATO","The treasury holds everything the Sovereign took and nothing he gave. Each coin is cursed to weigh what it cost someone else. The treasurers carry them out a handful at a time; no one can lift more.",Gear.FORTUNE_PICKAXE,Gear.LUCK_ROD,Gear.SHIELD),
        new Profile("The Last Bastion","Last-Standing","IRON_NUGGET","APPLE","When the Sovereign falls, the citadel's last soldiers will hold this bastion out of habit. Tell them the war is over. Most of them have waited years to hear it from someone they could believe.",Gear.SHIELD,Gear.CHESTPLATE,Gear.SWEEP_SWORD)
    ));
    public static Profile profile(int theme){return PROFILES.get(Math.floorMod(theme,PROFILES.size()));}
    /** The 72 baubles of generations 4 to 6: persisted IDs whose order fixes their icon bands (1..72; the pouch is 73). */
    public static final int CLASSIC_BAUBLES=72;
    /** Generation 7: a floor's Descent and the Throne are boss chambers for their loot (the guardian or the sovereign held them). */
    public static boolean boss(String kind){return "BOSS".equals(kind)||"DESCENT".equals(kind)||"THRONE".equals(kind);}
    /** The kind the armory's gates see: a finale room counts as a boss chamber. */
    public static String lootKind(String kind){return boss(kind)?"BOSS":kind;}
    /** Generation 7: the Descent pays 20 over an ordinary room and the Throne 28 (a boss chamber 12). */
    public static int budget(String kind,int tier,int mobs){tier=Math.max(0,Math.min(5,tier));if("REFUGE".equals(kind))return 0;if("TREASURE".equals(kind))return 6+tier*4+Math.max(0,Math.min(14,mobs))/2;if("SHRINE".equals(kind))return 4+tier*3+Math.max(0,Math.min(14,mobs))/2;return 3+tier*3+Math.max(0,Math.min(14,mobs))/2+("BOSS".equals(kind)?12:"GAUNTLET".equals(kind)?4:"DESCENT".equals(kind)?20:"THRONE".equals(kind)?28:0);}
    public static int scaledBudget(int base,double multiplier){if(Double.isNaN(multiplier))multiplier=1;return base<=0?0:Math.max(1,(int)Math.round(base*Math.max(.1,Math.min(3,multiplier))));}
    /** Generation 7: a deeper floor pays like a higher threat (+1 on Floor II, +2 on Floor III), capped at 5. */
    public static int effectiveTier(int tier,int floor){return Math.max(0,Math.min(5,tier+Math.max(1,Math.min(3,floor))-1));}
    /** Exact theme gets 6x weight; same narrative family (index modulo 6) gets 2x. The theme's own floor. */
    public static List<Bauble> pool(int theme,int tier,boolean boss){return pool(theme,tier,boss,Floors.floorOfTheme(theme));}
    /**
     * Generation 7 (owner 2026-10-05: "2x the amount of content in the early and late game"): each floor's reliquaries hold that
     * floor's own relics, so the Underworks and the Citadel pay in stronger ones, and the threat gates use the effective threat.
     * Rank 4 and 5 stay boss-only on every floor; trophies are awarded, never found.
     */
    public static List<Bauble> pool(int theme,int tier,boolean boss,int floor){
        List<Bauble> pool=new ArrayList<>();floor=Math.max(1,Math.min(3,floor));tier=effectiveTier(tier,floor);
        for(Bauble b:Bauble.values()){if(b.trophy()||b.floor()!=floor)continue;if(boss?b.rank<3||b.rank>Math.min(5,tier+1):b.rank>Math.min(3,tier))continue;int weight=b.theme==theme?6:b.theme%6==Math.floorMod(theme,6)?2:1;for(int i=0;i<weight;i++)pool.add(b);}
        return Collections.unmodifiableList(pool);
    }
    public static Bauble roll(long hash,int theme,int tier,String kind){return roll(hash,theme,tier,kind,Floors.floorOfTheme(theme));}
    /** Generation 7: deeper floors find relics more often (chance x Floors.loot); boss chambers, Descents and the Throne always give one. */
    public static Bauble roll(long hash,int theme,int tier,String kind,int floor){
        if("REFUGE".equals(kind))return null;Random rng=new Random(hash^0x426175626c65L);boolean boss=boss(kind);
        double chance=boss?1:Math.min(1,("TREASURE".equals(kind)?.30:"SHRINE".equals(kind)?.15:.06+Math.max(0,Math.min(5,tier))*.03)*Floors.loot(floor));
        if(rng.nextDouble()>=chance)return null;List<Bauble> choices=pool(theme,tier,boss,floor);return choices.isEmpty()?null:choices.get(rng.nextInt(choices.size()));
    }
    /** Generation 7: the second relic in a Descent's or the Throne's reliquary, an independent boss-pool draw. */
    public static Bauble finaleRelic(long hash,int theme,int tier,int floor){
        // A finale is the floor's highest threat (Layout.finale: tier 5), so its relic is drawn at that threat whatever the room says.
        List<Bauble> choices=pool(theme,Math.max(5,tier),true,floor);return choices.isEmpty()?null:choices.get(new Random(hash^0x46696e616c65L).nextInt(choices.size()));
    }
    public static List<String> audit(){
        List<String> errors=new ArrayList<>();Set<String> names=new HashSet<>(),passages=new HashSet<>(),keys=new HashSet<>(),effects=new HashSet<>(),titles=new HashSet<>(),signatures=new HashSet<>(),prefixes=new HashSet<>();
        Set<Gear> gear=EnumSet.noneOf(Gear.class);int[] themes=new int[Floors.THEME_TOTAL];
        if(PROFILES.size()!=Floors.THEME_TOTAL)errors.add("Expected "+Floors.THEME_TOTAL+" profiles");if(Bauble.values().length<CLASSIC_BAUBLES)errors.add("Lost classic baubles");
        for(int i=0;i<PROFILES.size();i++){Profile p=PROFILES.get(i);
            if(!names.add(p.theme)||!passages.add(p.passage))errors.add("Duplicate theme/chapter: "+p.theme);if(i>=Layout.THEMES.length||!Layout.THEMES[i].equals(p.theme))errors.add("Theme order: "+i);
            if(!prefixes.add(p.prefix))errors.add("Duplicate prefix: "+p.prefix);if(!signatures.add(p.essence+":"+p.provision+":"+p.gear))errors.add("Duplicate loot profile: "+p.theme);
            gear.addAll(p.gear);if(p.gear.size()!=3||new HashSet<>(p.gear).size()!=3)errors.add("Gear choices: "+p.theme);if(p.passage.contains("\""))errors.add("Quote in chapter: "+p.theme);
            // A chapter is the book's second page: it must fit one page of a 1.12 book (generation 6's longest is 177 characters).
            if(p.passage.length()>215||p.prefix.length()>18)errors.add("Chapter or prefix too long: "+p.theme);}
        if(gear.size()!=Gear.values().length)errors.add("Expected all "+Gear.values().length+" gear families/variants reachable");
        for(Bauble b:Bauble.values()){if(b.theme<0||b.theme>=Floors.THEME_TOTAL)errors.add("Bad theme: "+b);else if(!b.trophy())themes[b.theme]++;if(b.rank<1||b.rank>5||b.description.isEmpty()||b.cooldownMillis<0||b.cooldownMillis>600000||b.cooldownMillis>0&&b.cooldownMillis<1000||!keys.add(b.cooldownKey()))errors.add("Bad metadata: "+b);if(!effects.add(b.trigger+":"+b.description))errors.add("Duplicate effect: "+b);if(!titles.add(b.title))errors.add("Duplicate title: "+b);
            if(b.rank>=4)for(int t=0;t<=5;t++)if(pool(b.theme,t,false,b.floor()).contains(b))errors.add("Boss-only violation: "+b);
            // Owner 2026-10-05: the Underworks and the Citadel are much harder, so their relics are stronger.
            if(!b.trophy()&&b.ordinal()>=CLASSIC_BAUBLES&&b.rank<b.floor())errors.add("Too weak for its floor: "+b);
            for(int t=0;t<=5;t++)for(int f=1;f<=3;f++)if(b.trophy()&&(pool(b.theme,t,true,f).contains(b)||pool(b.theme,t,false,f).contains(b)))errors.add("Trophy in a reliquary: "+b);
            if(b.description.toString().toLowerCase(Locale.ROOT).matches(".*(night vision|glow|flight|levitat(e|es|ing) you).*"))errors.add("Darkness/flight contract: "+b);}
        Set<String> variants=new HashSet<>();for(Gear g:Gear.values())if(!variants.add(g.suffix+":"+g.enchantment))errors.add("Duplicate gear function: "+g);
        for(int i=0;i<36;i++)if(themes[i]!=2)errors.add("Theme needs two baubles: "+i);
        for(int i=36;i<Floors.THEME_TOTAL;i++)if(themes[i]<1||themes[i]>2)errors.add("New theme needs one or two baubles: "+i);
        return Collections.unmodifiableList(errors);
    }
}
