package chat.jaspr.dungeon;

import java.util.*;

/** Server-free reward contract. Bauble names are persisted IDs; preserve the first 36. */
public final class LootCatalog {
    private LootCatalog() {}
    public enum Trigger { HURT, ATTACK, KILL, ENTER, CLEAR, EXPERIENCE, DURABILITY, REGAIN, FOOD, TICK, REACT }
    public enum Gear {
        SWORD("SWORD","DAMAGE_ALL"), AXE("AXE","DAMAGE_ALL"), CHESTPLATE("CHESTPLATE","PROTECTION_ENVIRONMENTAL"), BOW("BOW","ARROW_DAMAGE"),
        HELMET("HELMET","OXYGEN"), LEGGINGS("LEGGINGS","PROTECTION_ENVIRONMENTAL"), BOOTS("BOOTS","PROTECTION_FALL"), PICKAXE("PICKAXE","DIG_SPEED"),
        SHOVEL("SPADE","DIG_SPEED"), HOE("HOE","DURABILITY"), SHIELD("SHIELD","DURABILITY"), FISHING_ROD("FISHING_ROD","LURE"),
        SMITE_SWORD("SWORD","DAMAGE_UNDEAD"), CLEAVING_AXE("AXE","DIG_SPEED"), THORNS_CHESTPLATE("CHESTPLATE","THORNS"), PUNCH_BOW("BOW","ARROW_KNOCKBACK"),
        AQUA_HELMET("HELMET","WATER_WORKER"), BLAST_LEGGINGS("LEGGINGS","PROTECTION_EXPLOSIONS"), DEPTH_BOOTS("BOOTS","DEPTH_STRIDER"), SILK_PICKAXE("PICKAXE","SILK_TOUCH"),
        FORTUNE_SHOVEL("SPADE","LOOT_BONUS_BLOCKS"), MENDING_HOE("HOE","MENDING"), MENDING_SHIELD("SHIELD","MENDING"), LUCK_ROD("FISHING_ROD","LUCK");
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
        LAST_CANDLE("Last Candle","BLAZE_POWDER",5,35,Trigger.CLEAR,0,"Clearing combat removes poison and wither.");
        public final String title,material;
        public final int rank,theme;
        public final Trigger trigger;
        public final long cooldownMillis;
        public final List<String> description;
        Bauble(String title,String material,int rank,int theme,Trigger trigger,long cooldown,String...description){this.title=title;this.material=material;this.rank=rank;this.theme=theme;this.trigger=trigger;this.cooldownMillis=cooldown;this.description=Collections.unmodifiableList(Arrays.asList(description));}
        public String cooldownKey(){switch(this){case MARROW_BEAD:return "marrowAt";case LAMB_BELL:return "bellAt";case WARDEN_EYE:return "wardAt";case CROWN_OF_MERCY:return "mercyAt";default:return "relic_"+name()+"At";}}
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
        new Profile("Last Absolution","Candle-Kept","BLAZE_POWDER","BREAD","At the last threshold no judge remained. A candle waited beside an unlocked door. The house could offer no final permission to live; that had always belonged to those within it.",Gear.THORNS_CHESTPLATE,Gear.BLAST_LEGGINGS,Gear.MENDING_HOE)
    ));
    public static Profile profile(int theme){return PROFILES.get(Math.floorMod(theme,PROFILES.size()));}
    public static int budget(String kind,int tier,int mobs){tier=Math.max(0,Math.min(5,tier));if("REFUGE".equals(kind))return 0;if("TREASURE".equals(kind))return 6+tier*4+Math.max(0,Math.min(14,mobs))/2;if("SHRINE".equals(kind))return 4+tier*3+Math.max(0,Math.min(14,mobs))/2;return 3+tier*3+Math.max(0,Math.min(14,mobs))/2+("BOSS".equals(kind)?12:"GAUNTLET".equals(kind)?4:0);}
    public static int scaledBudget(int base,double multiplier){if(Double.isNaN(multiplier))multiplier=1;return base<=0?0:Math.max(1,(int)Math.round(base*Math.max(.1,Math.min(3,multiplier))));}
    /** Exact theme gets 6x weight; same narrative family (index modulo 6) gets 2x. */
    public static List<Bauble> pool(int theme,int tier,boolean boss){
        List<Bauble> pool=new ArrayList<>();tier=Math.max(0,Math.min(5,tier));
        for(Bauble b:Bauble.values()){if(boss?b.rank<3||b.rank>Math.min(5,tier+1):b.rank>Math.min(3,tier))continue;int weight=b.theme==theme?6:b.theme%6==Math.floorMod(theme,6)?2:1;for(int i=0;i<weight;i++)pool.add(b);}
        return Collections.unmodifiableList(pool);
    }
    public static Bauble roll(long hash,int theme,int tier,String kind){
        if("REFUGE".equals(kind))return null;Random rng=new Random(hash^0x426175626c65L);boolean boss="BOSS".equals(kind);
        double chance=boss?1:"TREASURE".equals(kind)?.30:"SHRINE".equals(kind)?.15:.06+Math.max(0,Math.min(5,tier))*.03;
        if(rng.nextDouble()>=chance)return null;List<Bauble> choices=pool(theme,tier,boss);return choices.isEmpty()?null:choices.get(rng.nextInt(choices.size()));
    }
    public static List<String> audit(){
        List<String> errors=new ArrayList<>();Set<String> names=new HashSet<>(),passages=new HashSet<>(),keys=new HashSet<>(),effects=new HashSet<>();Set<Gear> gear=EnumSet.noneOf(Gear.class);int[] themes=new int[36];
        if(PROFILES.size()!=36)errors.add("Expected 36 profiles");if(Bauble.values().length!=72)errors.add("Expected 72 baubles");
        for(Profile p:PROFILES){if(!names.add(p.theme)||!passages.add(p.passage))errors.add("Duplicate theme/chapter: "+p.theme);gear.addAll(p.gear);if(p.gear.size()!=3)errors.add("Gear choices: "+p.theme);}
        if(gear.size()!=24)errors.add("Expected 24 reachable gear families/variants");
        for(Bauble b:Bauble.values()){if(b.theme<0||b.theme>=36)errors.add("Bad theme: "+b);else themes[b.theme]++;if(b.rank<1||b.rank>5||b.description.isEmpty()||b.cooldownMillis<0||!keys.add(b.cooldownKey()))errors.add("Bad metadata: "+b);if(!effects.add(b.trigger+":"+b.description))errors.add("Duplicate effect: "+b);if(b.rank>=4&&pool(b.theme,5,false).contains(b))errors.add("Boss-only violation: "+b);}
        Set<String> variants=new HashSet<>();for(Gear g:Gear.values())if(!variants.add(g.suffix+":"+g.enchantment))errors.add("Duplicate gear function: "+g);
        for(int i=0;i<36;i++)if(themes[i]!=2)errors.add("Theme needs two baubles: "+i);return Collections.unmodifiableList(errors);
    }
}
