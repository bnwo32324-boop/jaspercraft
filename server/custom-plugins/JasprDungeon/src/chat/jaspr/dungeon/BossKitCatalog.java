package chat.jaspr.dungeon;

import chat.jaspr.dungeon.EncounterCatalog.Species;
import java.util.*;

/**
 * Generation 7 (owner 2026-10-05): "I still think the bosses are lackluster ... bosses should very commonly summon mobs as
 * well. They should also have custom unique attacks"; "I want the bosses to feel more like bosses and not just enemies with
 * scaled-up health"; "I want some bosses to be legitimately incredibly powerful."
 *
 * Pure (no Bukkit, clocks or random state): the boss ability library, every theme's kit (0..101, keyed by the theme index
 * and flavoured by its name in Layout.THEMES, so Floor I's new themes and Floors II and III fit whatever bosses their own
 * catalogues name), the three floor guardians' kits, phases at 66% and 33% health, enrage timers, floor scaling and the rare
 * Dread variant. BossKits plays them; every boss also keeps its own telegraphed signature pattern (EncounterCatalog).
 */
public final class BossKitCatalog {
    private BossKitCatalog(){}

    // ---------------------------------------------------------------- the library
    /** heavy: a big hit, always marked at least HEAVY_TELEGRAPH ticks before it lands. The hint is the dodge, said with the cue. */
    public enum Ability {
        SUMMON("Call",false,"its servants erupt from the floor"),
        CHARGE("Charge",true,"step out of the marked lane"),
        LEAP_SLAM("Leap",true,"leave the marked circle, then jump the shockwave"),
        VOLLEY("Volley",false,"keep moving and put stone between you"),
        BEAM("Lance",true,"step out of the marked line"),
        METEOR_RAIN("Fall",true,"leave the marked circles before they land"),
        BLINK_STRIKE("Step",true,"it will appear in the marked ring - get out"),
        VORTEX("Maelstrom",true,"run outward against the pull before the well bursts"),
        SPIKES("Spikes",true,"step off the marked spots"),
        LIGHTNING("Storm",true,"leave the marked circles"),
        SNARE("Snare",false,"step off the gathering threads"),
        CLONES("Mirage",false,"find the real one - the copies vanish when struck"),
        SHIELD("Ward",false,"kill its wardens to break the ward"),
        LEECH("Feast",false,"kill its servants before it drinks them"),
        FIRE_RING("Ring",true,"stand between the marked bands"),
        POISON_CLOUD("Miasma",false,"stay out of the clouds"),
        SACRIFICE("Offering",true,"get away from the marked servants"),
        ROAR("Roar",true,"leave the marked circle or be thrown back"),
        QUAKE_LINES("Quake",true,"stand between the marked fault lines"),
        ENRAGE("Fury",false,"it grows faster and stronger"),
        ECLIPSE("Eclipse",true,"reach a lit circle before the dark falls");
        public final String noun,hint;public final boolean heavy;
        Ability(String noun,boolean heavy,String hint){this.noun=noun;this.heavy=heavy;this.hint=hint;}
        /** Moves that wound (by blow, projectile, fang or cloud); the others call, shield, heal or enrage. */
        public boolean damaging(){return this!=SUMMON&&this!=CLONES&&this!=SHIELD&&this!=LEECH&&this!=ENRAGE;}
        /** Moves that throw the boss's own body about (a stationary body cannot use them). */
        public boolean moves(){return this==CHARGE||this==LEAP_SLAM||this==BLINK_STRIKE;}
    }
    public enum Shot { SMALL_FIREBALL, ARROW, SHULKER_BULLET, WITHER_SKULL, SNOWBALL }
    /**
     * A kit's look and feel, from its theme's name: the particles that mark and burst, its cue colour and sound, what it
     * shoots, what falls from the roof (never placed), its cloud and whether its snares are webs (else a numbing frost).
     * Names are Bukkit enum constants (audited), so this class needs no server.
     */
    public enum Element {
        FIRE("Cinder",'6',"FLAME","LAVA","ENTITY_BLAZE_SHOOT",Shot.SMALL_FIREBALL,"MAGMA","WEAKNESS",0xE0601E,false),
        FROST("Rime",'b',"SNOW_SHOVEL","SNOWBALL","BLOCK_GLASS_BREAK",Shot.SNOWBALL,"PACKED_ICE","SLOW",0xA0D8F0,false),
        TIDE("Brine",'3',"WATER_SPLASH","WATER_WAKE","ENTITY_ELDER_GUARDIAN_CURSE",Shot.ARROW,"PRISMARINE","SLOW",0x3070B0,false),
        ROT("Blight",'a',"VILLAGER_HAPPY","SLIME","ENTITY_WITCH_AMBIENT",Shot.SHULKER_BULLET,"SLIME_BLOCK","POISON",0x50B428,true),
        BONE("Bone",'f',"CRIT","SMOKE_NORMAL","ENTITY_WITHER_SKELETON_AMBIENT",Shot.ARROW,"BONE_BLOCK","WITHER",0xD8D0B8,true),
        VOID("Starless",'d',"PORTAL","DRAGON_BREATH","ENTITY_ENDERMEN_SCREAM",Shot.SHULKER_BULLET,"OBSIDIAN","BLINDNESS",0x5A2878,true),
        LIGHT("Candle",'e',"END_ROD","FIREWORKS_SPARK","BLOCK_NOTE_BELL",Shot.SNOWBALL,"GLOWSTONE","WEAKNESS",0xF0E08C,false),
        BLOOD("Sanguine",'c',"REDSTONE","DAMAGE_INDICATOR","ENTITY_EVOCATION_ILLAGER_PREPARE_ATTACK",Shot.WITHER_SKULL,"NETHER_WART_BLOCK","WITHER",0x8C1414,true),
        IRON("Iron",'7',"CRIT","SMOKE_LARGE","BLOCK_ANVIL_LAND",Shot.ARROW,"ANVIL","SLOW",0x8C8C8C,true),
        STONE("Rock",'7',"CLOUD","EXPLOSION_NORMAL","ENTITY_IRONGOLEM_ATTACK",Shot.SNOWBALL,"COBBLESTONE","SLOW",0x787064,true),
        THORN("Briar",'2',"VILLAGER_HAPPY","CRIT","BLOCK_CHORUS_FLOWER_GROW",Shot.ARROW,"MOSSY_COBBLESTONE","POISON",0x3C7828,true),
        SHADOW("Mourning",'5',"SMOKE_LARGE","SPELL_WITCH","ENTITY_ILLUSION_ILLAGER_PREPARE_BLINDNESS",Shot.WITHER_SKULL,"OBSIDIAN","BLINDNESS",0x281E32,true);
        public final String adjective,mark,burst,sound,meteor,cloud;public final char colour;public final Shot shot;public final int rgb;public final boolean webs;
        Element(String adjective,char colour,String mark,String burst,String sound,Shot shot,String meteor,String cloud,int rgb,boolean webs){
            this.adjective=adjective;this.colour=colour;this.mark=mark;this.burst=burst;this.sound=sound;this.shot=shot;this.meteor=meteor;this.cloud=cloud;this.rgb=rgb;this.webs=webs;
        }
    }
    private static final Ability SUMMON=Ability.SUMMON,CHARGE=Ability.CHARGE,LEAP_SLAM=Ability.LEAP_SLAM,VOLLEY=Ability.VOLLEY,BEAM=Ability.BEAM,
        METEOR_RAIN=Ability.METEOR_RAIN,BLINK_STRIKE=Ability.BLINK_STRIKE,VORTEX=Ability.VORTEX,SPIKES=Ability.SPIKES,LIGHTNING=Ability.LIGHTNING,
        SNARE=Ability.SNARE,CLONES=Ability.CLONES,SHIELD=Ability.SHIELD,LEECH=Ability.LEECH,FIRE_RING=Ability.FIRE_RING,POISON_CLOUD=Ability.POISON_CLOUD,
        SACRIFICE=Ability.SACRIFICE,ROAR=Ability.ROAR,QUAKE_LINES=Ability.QUAKE_LINES,ENRAGE=Ability.ENRAGE,ECLIPSE=Ability.ECLIPSE;
    private static final Element FIRE=Element.FIRE,FROST=Element.FROST,TIDE=Element.TIDE,ROT=Element.ROT,BONE=Element.BONE,VOID=Element.VOID,
        LIGHT=Element.LIGHT,BLOOD=Element.BLOOD,IRON=Element.IRON,STONE=Element.STONE,THORN=Element.THORN,SHADOW=Element.SHADOW;

    /** One parameterised use of an ability. Numbers are Floor I's; damage(), cooldown(), telegraph() and count() scale them. */
    public static final class Move {
        public final Ability ability;public final String name;
        /** Half-hearts before floor and threat scaling (per projectile for a volley, per burst for a sacrifice). */
        public final double damage;
        /** Circle, band or lane half-width, pull or roar reach, summon spread, as the ability reads it (cells). */
        public final double radius;
        /** Projectiles per target, circles, beams, fangs, spokes, adds, wardens or safe circles. */
        public final int count;
        /** Floor I ticks before this move may come again, and ticks of warning before it lands. */
        public final int cooldown,telegraph;
        /** Repeats: volley salvos, ring bands, quake turns, spike patterns. */
        public final int pulses;
        /** Adds this move calls are elites (tougher, harder hitting). */
        public final boolean elite;
        /** The look of this move when it differs from its kit's (a stone tyrant's magma breath), else null. */
        public final Element look;
        private final Species[] species;
        Move(Ability ability,String name,double damage,double radius,int count,int cooldown,int telegraph,int pulses,Species[] species,boolean elite,Element look){
            this.ability=ability;this.name=name;this.damage=damage;this.radius=radius;this.count=count;this.cooldown=cooldown;this.telegraph=telegraph;this.pulses=pulses;
            this.species=species==null?null:species.clone();this.elite=elite;this.look=look;
        }
        /** Summons and wardens: the species this move calls instead of its theme's pool (empty: the pool). */
        public List<Species> species(){return species==null?Collections.<Species>emptyList():Collections.unmodifiableList(Arrays.asList(species));}
        Move look(Element e){return new Move(ability,name,damage,radius,count,cooldown,telegraph,pulses,species,elite,e);}
        public Element element(Kit k){return look!=null?look:k.element;}
        @Override public String toString(){return ability.name();}
    }
    /** Library defaults (a Floor I room boss); kits rename and tweak them, guardians set their own. */
    static Move standard(Ability a,String name){
        switch(a){
            case SUMMON:       return new Move(a,name,0,6,2,300,24,1,null,false,null);
            case CHARGE:       return new Move(a,name,6,1.6,1,260,26,1,null,false,null);
            case LEAP_SLAM:    return new Move(a,name,7,3.5,1,320,30,1,null,false,null);
            case VOLLEY:       return new Move(a,name,3.5,0,2,200,16,3,null,false,null);
            case BEAM:         return new Move(a,name,7,1.3,1,300,30,1,null,false,null);
            case METEOR_RAIN:  return new Move(a,name,6,2,5,340,32,1,null,false,null);
            case BLINK_STRIKE: return new Move(a,name,6,2.5,1,280,22,1,null,false,null);
            case VORTEX:       return new Move(a,name,5,9,1,360,30,1,null,false,null);
            case SPIKES:       return new Move(a,name,5,1,9,240,22,2,null,false,null);
            case LIGHTNING:    return new Move(a,name,6,1.6,3,300,26,1,null,false,null);
            case SNARE:        return new Move(a,name,2,1.5,3,320,18,1,null,false,null);
            case CLONES:       return new Move(a,name,0,6,3,420,20,1,null,false,null);
            case SHIELD:       return new Move(a,name,0,0,3,900,24,1,null,false,null);
            case LEECH:        return new Move(a,name,0,14,4,400,30,1,null,false,null);
            case FIRE_RING:    return new Move(a,name,6,4,1,300,28,2,null,false,null);
            case POISON_CLOUD: return new Move(a,name,0,2.5,3,320,18,1,null,false,null);
            case SACRIFICE:    return new Move(a,name,6,3,3,420,30,1,null,false,null);
            case ROAR:         return new Move(a,name,3,7,1,360,24,1,null,false,null);
            case QUAKE_LINES:  return new Move(a,name,6,1,6,280,26,2,null,false,null);
            case ENRAGE:       return new Move(a,name,0,0,0,6000,20,1,null,false,null);
            case ECLIPSE:      return new Move(a,name,14,3.5,3,900,70,1,null,false,null);
            default: throw new AssertionError(a);
        }
    }
    private static Move move(Ability a,String name,double damage,double radius,int count,int cooldown,int telegraph,int pulses){return new Move(a,name,damage,radius,count,cooldown,telegraph,pulses,null,false,null);}
    private static Move call(Ability a,String name,int count,int cooldown,boolean elite,Species... species){Move s=standard(a,name);return new Move(a,name,0,s.radius,count,cooldown,s.telegraph,1,species,elite,null);}

    // ---------------------------------------------------------------- kits
    public static final class Kit {
        public final String id,title;
        /** The theme of a room boss's kit, -1 for a guardian's. */
        public final int theme;
        public final Element element;
        private final Move[] base;private final Move[][] phases;
        /** The Dread variant's extra move (room bosses only), else null. */
        public final Move dread;
        /** Ticks to enrage, Floor I ticks between summons, the live-add cap, adds per phase burst and ticks between casts. */
        public final int enrage,summonEvery,addsCap,burst,gap;
        public final boolean guardian;
        /** A guardian whose last phase is desperate: it enrages at once and holds nothing back. */
        public final boolean desperate;
        private final String[] shouts;
        Kit(String id,String title,int theme,Element element,Move[] base,Move[][] phases,Move dread,int enrage,int summonEvery,int addsCap,int burst,int gap,boolean guardian,boolean desperate,String[] shouts){
            this.id=id;this.title=title;this.theme=theme;this.element=element;this.base=base.clone();this.phases=new Move[phases.length][];for(int i=0;i<phases.length;i++)this.phases[i]=phases[i].clone();
            this.dread=dread;this.enrage=enrage;this.summonEvery=summonEvery;this.addsCap=addsCap;this.burst=burst;this.gap=gap;this.guardian=guardian;this.desperate=desperate;this.shouts=shouts.clone();
        }
        /** The moves a fight starts with: the summon first, then the attacks. */
        public List<Move> base(){return Collections.unmodifiableList(Arrays.asList(base));}
        /** The moves unlocked on entering phase p (1: below 66% health, 2: below 33%). */
        public List<Move> unlocked(int p){return p<1||p>phases.length?Collections.<Move>emptyList():Collections.unmodifiableList(Arrays.asList(phases[p-1]));}
        /** Every move usable in phase p (0..2), with the Dread move when asked. */
        public List<Move> moves(int phase,boolean dread){
            List<Move> out=new ArrayList<>(base());for(int p=1;p<=Math.min(phase,phases.length);p++)out.addAll(unlocked(p));
            if(dread&&this.dread!=null)out.add(this.dread);return out;
        }
        public Move summon(){return base[0];}
        public int phaseCount(){return phases.length+1;}
        /** 0: entering phase II, 1: entering phase III, 2: the enrage. */
        public String shout(int i){return shouts[Math.max(0,Math.min(shouts.length-1,i))];}
        @Override public String toString(){return id;}
    }
    private static final Kit[] KITS=new Kit[Floors.THEME_TOTAL];
    /** The name without a leading "The ", for "Call of the ..." */
    static String bare(String theme){return theme.startsWith("The ")?theme.substring(4):theme;}
    private static final String[][] SHOUTS={
        {"The %s burns hotter!","Everything in the %s turns to cinder!","The furnace of the %s roars out of control!"},
        {"The %s freezes over!","Winter closes on the %s!","The cold of the %s reaches for your heart!"},
        {"The waters of the %s rise!","The %s floods with grief!","The tide of the %s will not ebb!"},
        {"The %s festers!","Blight spreads through the %s!","The %s rots around you!"},
        {"The bones of the %s stir!","The %s rattles with the dead!","Every bone in the %s rises at once!"},
        {"The %s goes dark between the stars!","Nothing in the %s is where it seems!","The %s unravels into the void!"},
        {"The candles of the %s flare!","The %s blazes with a false holiness!","The last light of the %s burns white!"},
        {"The %s drinks deep!","Blood runs through the %s!","The %s thirsts for more!"},
        {"The machinery of the %s grinds faster!","The chains of the %s pull tight!","Iron screams across the %s!"},
        {"The %s shakes!","The walls of the %s crack!","The %s begins to fall apart!"},
        {"The roots of the %s tighten!","Thorns burst through the %s!","The garden of the %s devours the living!"},
        {"The %s falls silent!","Shadows fill the %s!","The %s mourns its last guest!"}
    };
    /** Small per-theme differences (numbers stay inside the audited bounds): more of a count, a slower or quicker cooldown, a wider reach. */
    private static Move tweak(Move m,int theme,int salt){
        long h=Layout.mix(theme*0x632be59bd9b4e019L^salt*0x9e3779b97f4a7c15L^0x4b69745477L);
        int count=m.count>=3&&m.ability!=SHIELD&&m.ability!=LEECH&&m.ability!=ECLIPSE?m.count+(int)Math.floorMod(h,2):m.count;
        int cooldown=(int)Math.round(m.cooldown*(.9+.05*Math.floorMod(h>>>8,5)));
        int telegraph=m.telegraph+(int)Math.floorMod(h>>>16,4);
        double radius=m.radius*(1+.05*Math.floorMod(h>>>24,3));
        return new Move(m.ability,m.name,m.damage,radius,count,cooldown,telegraph,m.pulses,m.species,m.elite,m.look);
    }
    /**
     * A room boss's kit: its summon, the attacks (two on Floor I, three below; the first carries the kit's own name), then the
     * move unlocked at 66%, the move unlocked at 33% and the Dread variant's extra move.
     */
    private static void row(int theme,Element e,String unique,Ability... a){
        int n=a.length-3,floor=Floors.floorOfTheme(theme);String name=Layout.THEMES[theme];
        Move[] base=new Move[n+1];base[0]=tweak(standard(SUMMON,"Call of the "+bare(name)),theme,0);
        for(int i=0;i<n;i++)base[i+1]=tweak(standard(a[i],i==0?unique:e.adjective+" "+a[i].noun),theme,i+1);
        Move second=tweak(standard(a[n],e.adjective+" "+a[n].noun),theme,7),third=tweak(standard(a[n+1],e.adjective+" "+a[n+1].noun),theme,8),dread=tweak(standard(a[n+2],"Dread "+a[n+2].noun),theme,9);
        String[] s=SHOUTS[e.ordinal()];
        KITS[theme]=new Kit("T"+theme,name,theme,e,base,new Move[][]{{second},{third}},dread,3600,240+(int)Math.floorMod(Layout.mix(theme*0x9E3779B97F4A7C15L+0x53756d6dL),161),
            4+2*floor,1+floor,80,false,false,new String[]{String.format(s[0],name),String.format(s[1],name),String.format(s[2],name)});
    }
    static{
        // Floor I, generation 6 (the bosses EncounterCatalog names)
        row(0,TIDE,"Tearfall",METEOR_RAIN,CHARGE,VORTEX,ROAR,SNARE);
        row(1,BONE,"Ossuary Teeth",SPIKES,VOLLEY,SHIELD,QUAKE_LINES,LEECH);
        row(2,TIDE,"Undertow",VORTEX,POISON_CLOUD,LEECH,VOLLEY,SNARE);
        row(3,FIRE,"Furnace Drop",LEAP_SLAM,FIRE_RING,VOLLEY,METEOR_RAIN,ROAR);
        row(4,VOID,"Hollow Step",BLINK_STRIKE,ROAR,CLONES,BEAM,VORTEX);
        row(5,ROT,"Cradle Webs",SNARE,CHARGE,SACRIFICE,POISON_CLOUD,LEECH);
        row(6,FIRE,"Burning Verse",BEAM,VOLLEY,LIGHTNING,FIRE_RING,SNARE);
        row(7,BLOOD,"Crimson Verdict",CHARGE,QUAKE_LINES,BLINK_STRIKE,ROAR,LEECH);
        row(8,FROST,"Hailshards",VOLLEY,SNARE,METEOR_RAIN,ROAR,BEAM);
        row(9,THORN,"Rootspears",SPIKES,SNARE,VORTEX,LEECH,POISON_CLOUD);
        row(10,IRON,"Gear Teeth",QUAKE_LINES,CHARGE,BEAM,SHIELD,SPIKES);
        row(11,ROT,"Fever Ward",POISON_CLOUD,BLINK_STRIKE,SACRIFICE,LEECH,SNARE);
        row(12,LIGHT,"Divided Self",CLONES,BEAM,BLINK_STRIKE,LIGHTNING,SHIELD);
        row(13,STONE,"Salt Fault",QUAKE_LINES,VOLLEY,ROAR,METEOR_RAIN,SPIKES);
        row(14,TIDE,"Riptide",VORTEX,LIGHTNING,SHIELD,POISON_CLOUD,BEAM);
        row(15,SHADOW,"The Last Supper",CHARGE,SNARE,ROAR,SACRIFICE,CLONES);
        row(16,VOID,"Starfall",METEOR_RAIN,BLINK_STRIKE,BEAM,VORTEX,LIGHTNING);
        row(17,THORN,"Briar Crown",SPIKES,POISON_CLOUD,SHIELD,VOLLEY,SNARE);
        row(18,LIGHT,"Candle Ring",FIRE_RING,SNARE,LEECH,METEOR_RAIN,BEAM);
        row(19,BLOOD,"Blood Communion",LEECH,BEAM,SACRIFICE,CHARGE,VOLLEY);
        row(20,STONE,"Falling Nave",METEOR_RAIN,LEAP_SLAM,QUAKE_LINES,SHIELD,ROAR);
        row(21,ROT,"Spore Bloom",POISON_CLOUD,LEAP_SLAM,SACRIFICE,VORTEX,SNARE);
        row(22,IRON,"Manacles",SNARE,CHARGE,SPIKES,ROAR,QUAKE_LINES);
        row(23,SHADOW,"Black Thread",SNARE,BEAM,CLONES,POISON_CLOUD,BLINK_STRIKE);
        row(24,LIGHT,"Amber Shell",SHIELD,VOLLEY,QUAKE_LINES,LIGHTNING,METEOR_RAIN);
        row(25,SHADOW,"Dead Bell",ROAR,LIGHTNING,BLINK_STRIKE,QUAKE_LINES,CLONES);
        row(26,ROT,"Carrion Feast",SACRIFICE,VOLLEY,LEECH,METEOR_RAIN,POISON_CLOUD);
        row(27,VOID,"Many Faces",CLONES,BEAM,SPIKES,SHIELD,BLINK_STRIKE);
        row(28,FIRE,"Crucible Drop",LEAP_SLAM,VOLLEY,FIRE_RING,QUAKE_LINES,METEOR_RAIN);
        row(29,BONE,"Stampede",CHARGE,SNARE,SACRIFICE,ROAR,LEAP_SLAM);
        row(30,TIDE,"Ink Lance",BEAM,POISON_CLOUD,VORTEX,METEOR_RAIN,CLONES);
        row(31,SHADOW,"Black Step",BLINK_STRIKE,SPIKES,SHIELD,FIRE_RING,VORTEX);
        row(32,LIGHT,"Alms of Ruin",SACRIFICE,CHARGE,LEECH,METEOR_RAIN,ROAR);
        row(33,VOID,"Astral Bolt",LIGHTNING,METEOR_RAIN,BLINK_STRIKE,VORTEX,BEAM);
        row(34,SHADOW,"Procession",CLONES,QUAKE_LINES,SNARE,CHARGE,SPIKES);
        row(35,LIGHT,"Absolution",LIGHTNING,FIRE_RING,SHIELD,ROAR,BEAM);
        // Floor I, generation 7 (36..53)
        row(36,IRON,"Great Toll",ROAR,QUAKE_LINES,METEOR_RAIN,CHARGE,SPIKES);
        row(37,LIGHT,"Moth Swarm",CLONES,VOLLEY,VORTEX,POISON_CLOUD,LIGHTNING);
        row(38,FIRE,"Wick Ring",FIRE_RING,BEAM,SNARE,METEOR_RAIN,VOLLEY);
        row(39,IRON,"Chained Tomes",SNARE,BEAM,VORTEX,SPIKES,SHIELD);
        row(40,TIDE,"Scalding Drain",VORTEX,POISON_CLOUD,LEAP_SLAM,LIGHTNING,SNARE);
        row(41,STONE,"Effigies",CLONES,QUAKE_LINES,SHIELD,BLINK_STRIKE,METEOR_RAIN);
        row(42,BONE,"Grave Bargain",SACRIFICE,VOLLEY,CHARGE,LEECH,SPIKES);
        row(43,THORN,"Bitter Harvest",METEOR_RAIN,SNARE,SPIKES,POISON_CLOUD,VORTEX);
        row(44,IRON,"Rust Ward",SHIELD,SPIKES,CHARGE,METEOR_RAIN,QUAKE_LINES);
        row(45,LIGHT,"Lamp Flare",LIGHTNING,BLINK_STRIKE,FIRE_RING,BEAM,CLONES);
        row(46,FIRE,"Kitchen Fires",VOLLEY,POISON_CLOUD,CHARGE,FIRE_RING,LEAP_SLAM);
        row(47,SHADOW,"Masks",CLONES,BLINK_STRIKE,ROAR,SNARE,BEAM);
        row(48,ROT,"Gutter Drain",VORTEX,SACRIFICE,POISON_CLOUD,LEAP_SLAM,SPIKES);
        row(49,LIGHT,"Censer Smoke",POISON_CLOUD,BEAM,FIRE_RING,LEECH,SHIELD);
        row(50,THORN,"Hanging Vines",SPIKES,LEECH,METEOR_RAIN,SHIELD,SNARE);
        row(51,BONE,"Pilgrim's March",CHARGE,SNARE,QUAKE_LINES,SACRIFICE,VOLLEY);
        row(52,IRON,"Grave Spades",SPIKES,LEAP_SLAM,METEOR_RAIN,ROAR,CHARGE);
        row(53,SHADOW,"Unlight",BEAM,VORTEX,CLONES,LIGHTNING,BLINK_STRIKE);
        // Floor II, The Underworks (54..77): three attacks
        row(54,FIRE,"Magma Spill",METEOR_RAIN,FIRE_RING,CHARGE,LEAP_SLAM,VOLLEY,QUAKE_LINES);
        row(55,STONE,"Stalactite Fall",METEOR_RAIN,QUAKE_LINES,ROAR,BLINK_STRIKE,SHIELD,SPIKES);
        row(56,ROT,"Spore Burst",POISON_CLOUD,SACRIFICE,SNARE,LEECH,VORTEX,LEAP_SLAM);
        row(57,TIDE,"Flooding Shaft",VORTEX,VOLLEY,CHARGE,METEOR_RAIN,LIGHTNING,SNARE);
        row(58,LIGHT,"Prism Lance",BEAM,CLONES,SPIKES,SHIELD,LIGHTNING,METEOR_RAIN);
        row(59,SHADOW,"Web Lattice",SNARE,LEAP_SLAM,VOLLEY,SACRIFICE,POISON_CLOUD,CLONES);
        row(60,BONE,"Ribcage",SPIKES,CHARGE,ROAR,SACRIFICE,METEOR_RAIN,LEECH);
        row(61,STONE,"Runaway Cart",CHARGE,METEOR_RAIN,SNARE,QUAKE_LINES,VOLLEY,SHIELD);
        row(62,FIRE,"Lava Torrent",BEAM,FIRE_RING,VOLLEY,METEOR_RAIN,VORTEX,LEAP_SLAM);
        row(63,STONE,"Burrowing Swarm",SACRIFICE,QUAKE_LINES,CLONES,SNARE,BLINK_STRIKE,VORTEX);
        row(64,FIRE,"Powder Keg",SACRIFICE,METEOR_RAIN,CHARGE,FIRE_RING,ROAR,VOLLEY);
        row(65,IRON,"Express",CHARGE,QUAKE_LINES,BEAM,BLINK_STRIKE,METEOR_RAIN,SPIKES);
        row(66,ROT,"Sulfur Vent",POISON_CLOUD,VORTEX,FIRE_RING,LIGHTNING,LEECH,SNARE);
        row(67,SHADOW,"Quarry Blast",QUAKE_LINES,METEOR_RAIN,SHIELD,CHARGE,SPIKES,ROAR);
        row(68,LIGHT,"Glowspit",VOLLEY,CLONES,SNARE,LIGHTNING,LEECH,BEAM);
        row(69,IRON,"Anvil Drop",LEAP_SLAM,FIRE_RING,METEOR_RAIN,SHIELD,QUAKE_LINES,CHARGE);
        row(70,TIDE,"River Lance",BEAM,VORTEX,SNARE,CLONES,LEAP_SLAM,LIGHTNING);
        row(71,IRON,"Rust Bloom",POISON_CLOUD,SPIKES,CHARGE,SACRIFICE,SHIELD,VOLLEY);
        row(72,VOID,"Geode Shards",SPIKES,BEAM,BLINK_STRIKE,VORTEX,CLONES,METEOR_RAIN);
        row(73,THORN,"Root Grasp",SNARE,SPIKES,LEECH,VORTEX,POISON_CLOUD,QUAKE_LINES);
        row(74,SHADOW,"Cutpurse",BLINK_STRIKE,VOLLEY,CLONES,SACRIFICE,SNARE,CHARGE);
        row(75,STONE,"Pillar Crush",LEAP_SLAM,QUAKE_LINES,ROAR,METEOR_RAIN,SHIELD,BLINK_STRIKE);
        row(76,FIRE,"Ember Spit",VOLLEY,SACRIFICE,LEAP_SLAM,FIRE_RING,POISON_CLOUD,VORTEX);
        row(77,IRON,"Deep Charge",QUAKE_LINES,METEOR_RAIN,BEAM,CHARGE,ROAR,SHIELD);
        // Floor III, The Abyssal Citadel (78..101)
        row(78,FIRE,"Hellforge Lance",BEAM,METEOR_RAIN,CHARGE,FIRE_RING,QUAKE_LINES,LEAP_SLAM);
        row(79,BLOOD,"Royal Charge",CHARGE,SPIKES,ROAR,SHIELD,BLINK_STRIKE,LIGHTNING);
        row(80,BLOOD,"Rampart Barrage",VOLLEY,LEECH,METEOR_RAIN,SACRIFICE,QUAKE_LINES,CHARGE);
        row(81,VOID,"Void Bloom",VORTEX,BLINK_STRIKE,POISON_CLOUD,CLONES,BEAM,SPIKES);
        row(82,SHADOW,"Spire Strike",LIGHTNING,BEAM,SHIELD,METEOR_RAIN,VORTEX,QUAKE_LINES);
        row(83,FIRE,"Soulfire Halo",FIRE_RING,LEECH,VOLLEY,LIGHTNING,SACRIFICE,BEAM);
        row(84,IRON,"Chain Drag",VORTEX,SNARE,QUAKE_LINES,CHARGE,SPIKES,SHIELD);
        row(85,FIRE,"Soul Furnace",LEAP_SLAM,METEOR_RAIN,SACRIFICE,FIRE_RING,LEECH,VOLLEY);
        row(86,VOID,"Sky Shatter",LIGHTNING,METEOR_RAIN,BLINK_STRIKE,BEAM,ROAR,VORTEX);
        row(87,BONE,"Gladiator's Rush",CHARGE,LEAP_SLAM,SPIKES,ROAR,SACRIFICE,CLONES);
        row(88,SHADOW,"Noose Web",SNARE,QUAKE_LINES,BLINK_STRIKE,SHIELD,LIGHTNING,SACRIFICE);
        row(89,FIRE,"Cannonade",VOLLEY,VORTEX,METEOR_RAIN,CHARGE,FIRE_RING,SNARE);
        row(90,VOID,"Forbidden Text",BEAM,CLONES,SPIKES,VORTEX,BLINK_STRIKE,POISON_CLOUD);
        row(91,BLOOD,"Crimson Drill",CHARGE,VOLLEY,ROAR,SHIELD,QUAKE_LINES,LEECH);
        row(92,IRON,"Doom Ingots",METEOR_RAIN,LEAP_SLAM,FIRE_RING,QUAKE_LINES,SHIELD,BEAM);
        row(93,LIGHT,"Mirror Host",CLONES,BEAM,BLINK_STRIKE,LIGHTNING,VORTEX,SHIELD);
        row(94,FIRE,"Ashen Decree",ROAR,METEOR_RAIN,LEECH,BEAM,CHARGE,FIRE_RING);
        row(95,VOID,"Abyssal Pull",VORTEX,LIGHTNING,SACRIFICE,SHIELD,METEOR_RAIN,CLONES);
        row(96,FIRE,"Pyre Ring",FIRE_RING,BEAM,SHIELD,METEOR_RAIN,LEAP_SLAM,ROAR);
        row(97,VOID,"Falling Stars",METEOR_RAIN,LIGHTNING,BEAM,BLINK_STRIKE,VORTEX,QUAKE_LINES);
        row(98,SHADOW,"Wraith Step",BLINK_STRIKE,CLONES,POISON_CLOUD,LEECH,SPIKES,VORTEX);
        row(99,FIRE,"Molten Relic",LEAP_SLAM,VOLLEY,FIRE_RING,SACRIFICE,METEOR_RAIN,CHARGE);
        row(100,BLOOD,"Cursed Coin",SACRIFICE,SNARE,LEECH,CLONES,BLINK_STRIKE,METEOR_RAIN);
        row(101,IRON,"Bastion Wall",SHIELD,QUAKE_LINES,VOLLEY,CHARGE,METEOR_RAIN,ROAR);
    }
    // ---------------------------------------------------------------- the floor guardians
    /** Floor I's Descent: the Gaoler of Mercy, who keeps the stair to the Underworks. Chains, cells and keys. */
    private static final Kit GAOLER=new Kit("GAOLER",Guardians.NAMES[0],-1,IRON,
        new Move[]{call(SUMMON,"Call the Turnkeys",3,300,true,Species.VINDICATOR,Species.ZOMBIE_VILLAGER,Species.SKELETON),
            move(CHARGE,"Gaoler's Rush",8,1.8,1,220,26,1),move(SNARE,"Shackles of Mercy",3,1.6,4,280,18,1),move(QUAKE_LINES,"Iron Bars",8,1.1,8,260,26,2)},
        new Move[][]{{call(SHIELD,"Lockdown",3,900,true,Species.VINDICATOR),move(VORTEX,"Key of Mercy",7,11,1,320,30,1)},
            {move(METEOR_RAIN,"Falling Cages",8,2.2,7,300,30,1),move(ROAR,"Last Call",5,9,1,300,24,1)}},
        null,4800,260,8,3,60,true,false,
        new String[]{"Locks turn: the cells of mercy open!","No one leaves the House of Mercy!","The Gaoler has lost all patience!"});
    /** Floor II's Descent: the Deep Tyrant, who keeps the stair to the Abyssal Citadel. Rock, magma and blasting charges. */
    private static final Kit TYRANT=new Kit("DEEP_TYRANT",Guardians.NAMES[1],-1,STONE,
        new Move[]{call(SUMMON,"Call the Deep Crews",4,300,true,Species.DEEP_MINER,Species.MAGMA_LURKER,Species.CEILING_STALKER,Species.TUNNEL_GUNNER),
            move(LEAP_SLAM,"Tyrant's Fall",10,4.5,1,280,30,1),move(METEOR_RAIN,"Cave-in",9,2.4,8,300,30,1),move(BEAM,"Magma Breath",10,1.6,3,280,30,1).look(FIRE)},
        new Move[][]{{move(FIRE_RING,"Magma Moat",9,4,1,280,28,3).look(FIRE),move(SACRIFICE,"Blasting Charges",9,3.5,4,360,30,1).look(FIRE)},
            {move(ENRAGE,"Tyranny",0,0,0,6000,20,1),move(VOLLEY,"Rockslide",5,0,3,180,16,3)}},
        null,5400,260,10,4,55,true,false,
        new String[]{"The Deep Tyrant tears the mountain open!","The whole Underworks shakes with its fury!","The Deep Tyrant will bury you all!"});
    /**
     * Floor III's Throne: the Abyssal Sovereign. Owner 2026-10-05: "some bosses to be legitimately incredibly powerful."
     * Three phases: legions and judgement; the ward, the Eclipse (the whole arena but a few lit circles) and the maw; then a
     * desperate last stand that enrages at once and adds illusions, rings of ruin and sundering quakes. Every big hit is marked.
     */
    private static final Kit SOVEREIGN=new Kit("SOVEREIGN",Guardians.NAMES[2],-1,VOID,
        new Move[]{call(SUMMON,"Legions of the Throne",4,300,true,Species.HELLFORGED_SENTINEL,Species.BLOOD_TEMPLAR,Species.DOOM_HERALD,Species.ABYSSAL_GUNSLINGER,Species.WITHER_SKELETON,Species.BLAZE),
            move(BEAM,"Sovereign's Judgement",12,1.5,3,260,32,1),move(BLINK_STRIKE,"Abyssal Step",11,3,1,240,24,1),
            move(METEOR_RAIN,"Fall of Heaven",11,2.5,10,300,34,1),move(LIGHTNING,"Crown of Storms",10,2,6,260,28,1)},
        new Move[][]{{call(SHIELD,"Throne Ward",4,800,true,Species.HELLFORGED_SENTINEL),move(ECLIPSE,"Eclipse",16,3.5,3,700,70,1),move(VORTEX,"Abyssal Maw",10,13,1,320,32,1)},
            {move(ENRAGE,"The Abyss Unchained",0,0,0,6000,24,1),move(CLONES,"Thousand Thrones",0,7,5,420,20,1),
                move(FIRE_RING,"Ring of Ruin",12,4,1,260,30,3).look(FIRE),move(QUAKE_LINES,"Sundering",12,1.2,10,260,28,3)}},
        null,7200,300,12,5,45,true,true,
        new String[]{"Kneel before the Throne of the Abyss!","The Abyss itself answers its Sovereign!","Then let the whole Citadel fall with me!"});
    private static final Kit[] GUARDIAN_KITS={GAOLER,TYRANT,SOVEREIGN};

    // ---------------------------------------------------------------- lookups
    static int clampFloor(int floor){return Math.max(1,Math.min(3,floor));}
    /** A room boss's kit by theme (0..101): every theme has one. */
    public static Kit kit(int theme){if(theme<0||theme>=KITS.length||KITS[theme]==null)throw new IllegalArgumentException("theme "+theme);return KITS[theme];}
    /** A floor's guardian: 1 the Gaoler of Mercy, 2 the Deep Tyrant, 3 the Abyssal Sovereign. */
    public static Kit guardian(int floor){return GUARDIAN_KITS[clampFloor(floor)-1];}
    /** A boss room's kit: its floor's guardian in the Descent or the Throne, else its theme's. */
    public static Kit of(Layout.Room r){return r.finale()?guardian(r.floor):kit(r.theme);}
    /** Every room kit (by theme) and then the three guardians'. */
    public static List<Kit> all(){List<Kit> out=new ArrayList<>(Arrays.asList(KITS));Collections.addAll(out,GUARDIAN_KITS);return Collections.unmodifiableList(out);}
    /** The cue said to the room when a move begins: who, what, and how to live through it. */
    public static String cue(String boss,Move m){return boss+" - "+m.name+": "+m.ability.hint+(m.ability.heavy?"!":".");}

    // ---------------------------------------------------------------- numbers (BossKitAuditTest holds them to their bounds)
    public static final int MIN_COOLDOWN=80,MIN_GAP=30,HEAVY_TELEGRAPH=20,LIGHT_TELEGRAPH=15,ARENA_TELEGRAPH=50;
    /** Owner: bosses "very commonly summon": a room boss on Floor I calls every 12 to 20 seconds, faster below. */
    public static final int SUMMON_MIN=240,SUMMON_MAX=400;
    /** Adds never need more health than this (spigot.yml caps any max health, 2048 by default). */
    public static final double ADD_LIFE_LIMIT=1024;
    private static final double[] PACE={1,.85,.72};
    /** Each phase quickens the boss: cooldowns and the pause between casts shrink. */
    public static double pace(int phase){return PACE[Math.max(0,Math.min(2,phase))];}
    /** The hardest blow of any move: per floor, room bosses and guardians. */
    public static double damageCap(int floor,boolean guardian){int f=clampFloor(floor)-1;return guardian?new double[]{12,17,24}[f]:new double[]{9,13,18}[f];}
    public static double damage(Move m,int floor,int tier,boolean dread,boolean guardian){
        if(m.damage<=0)return 0;
        return Math.min(damageCap(floor,guardian),m.damage*Floors.damage(clampFloor(floor))*(.85+.05*EncounterCatalog.threat(tier))*(dread?1.2:1));
    }
    public static int cooldown(Move m,int floor,int phase,boolean enraged,boolean dread){
        return (int)Math.max(MIN_COOLDOWN,Math.round(m.cooldown*Floors.cadence(clampFloor(floor))*pace(phase)*(enraged?.7:1)*(dread?.9:1)));
    }
    /** Ticks between one cast and the next. */
    public static int gap(Kit k,int floor,int phase,boolean enraged){return (int)Math.max(MIN_GAP,Math.round(k.gap*Floors.cadence(clampFloor(floor))*pace(phase)*(enraged?.75:1)));}
    public static int minTelegraph(Ability a){return a==ECLIPSE?ARENA_TELEGRAPH:a.heavy?HEAVY_TELEGRAPH:LIGHT_TELEGRAPH;}
    /** Warnings shorten a little deeper down and later in a fight, never below the ability's minimum. */
    public static int telegraph(Move m,int floor,int phase,boolean enraged){
        double f=1-.06*(clampFloor(floor)-1)-.05*Math.max(0,Math.min(2,phase))-(enraged?.05:0);
        return Math.max(minTelegraph(m.ability),(int)Math.round(m.telegraph*f));
    }
    public static int summonEvery(Kit k,int floor,int phase,boolean enraged){return (int)Math.round(k.summonEvery*Floors.cadence(clampFloor(floor))*(phase>=2?.85:1)*(enraged?.85:1));}
    /** Live adds a boss may have at once (its summons, clones and wardens); owner: "very commonly", about six to ten. */
    public static int addsCap(Kit k,int floor,boolean dread){return k.guardian?k.addsCap:Math.min(10,4+2*clampFloor(floor)+(dread?1:0));}
    /** Adds per summon. */
    public static int summonCount(Kit k,int floor,int phase){return Math.min(6,k.summon().count+clampFloor(floor)-1+(phase>=1?1:0));}
    public static int enrage(Kit k,int floor){int f=clampFloor(floor);return k.guardian?k.enrage:(int)Math.round(k.enrage*(f==3?.8:f==2?.9:1));}
    static int maxCount(Ability a){
        switch(a){
            case VOLLEY: return 6; case METEOR_RAIN: return 14; case LIGHTNING: return 9; case SPIKES: return 14; case QUAKE_LINES: return 12;
            case CLONES: return 6; case SNARE: return 6; case BEAM: return 4; case SACRIFICE: return 6; case SHIELD: return 4; case LEECH: return 6;
            case SUMMON: return 6; case ECLIPSE: return 4; case ENRAGE: return 0; default: return 1;
        }
    }
    /** How many projectiles, circles, fangs, spokes or beams: more on deeper floors and in the last phase. */
    public static int count(Move m,int floor,int phase){
        int f=clampFloor(floor)-1,extra;
        switch(m.ability){
            case VOLLEY: case LIGHTNING: case SNARE: case CLONES: case SACRIFICE: extra=f>=2?1:0;break;
            case METEOR_RAIN: case SPIKES: case QUAKE_LINES: extra=f+(phase>=2?1:0);break;
            case BEAM: extra=f>=2&&phase>=1?1:0;break;
            default: extra=0;
        }
        return Math.max(m.ability==ENRAGE?0:1,Math.min(maxCount(m.ability),m.count+extra));
    }
    /** A summoned add's health and blows: the room's escorts' formula, lighter for chaff, much tougher for elites. */
    public static double addHealth(int floor,int tier,double depth,double danger,boolean elite){
        return Math.min(ADD_LIFE_LIMIT,(12+5*EncounterCatalog.threat(tier))*Floors.health(clampFloor(floor))*(1+.5*Math.max(0,Math.min(1,depth)))*danger*(elite?2.5:.8));
    }
    public static double addDamage(int floor,int tier,double danger,boolean elite){return (2+.65*EncounterCatalog.threat(tier))*danger*Floors.damage(clampFloor(floor))*(elite?1.3:1);}

    // ---------------------------------------------------------------- the Dread variant
    static final long DREAD_SALT=0x447265616442L;
    public static final int DREAD_IN_1000=100;
    /** About one ordinary boss room in ten on Floors II and III holds a Dread boss: from the room's hash alone. */
    public static boolean dread(Layout.Room r){return r!=null&&r.kind==Layout.Kind.BOSS&&r.floor>=2&&Math.floorMod(Layout.mix(r.hash^DREAD_SALT),1000)<DREAD_IN_1000;}

    // ---------------------------------------------------------------- what a boss may call
    /** Species a boss may summon as adds: none that fly through walls, sit still, explode, crush with fixed damage or are surprises. */
    public static boolean summonable(Species s){
        if(s==null||s.mutant())return false;
        switch(s){case GHAST: case SHULKER: case VEX: case CANDLE_WISP: case IRON_GOLEM: case RUST_GOLEM: case MIMIC: case GILDED_THIEF: return false;default: return true;}
    }
    private static final Species[][] FALLBACK={{Species.ZOMBIE,Species.SKELETON,Species.SPIDER},{Species.HUSK,Species.CAVE_SPIDER,Species.SKELETON},{Species.WITHER_SKELETON,Species.BLAZE,Species.STRAY}};
    /** What this move calls: its own species, else the theme's pool (a catalogue not yet written falls back to its floor's). */
    public static List<Species> summons(Move m,int theme,int floor){
        List<Species> out=new ArrayList<>();
        for(Species s:m.species())if(summonable(s)&&!out.contains(s))out.add(s);
        if(out.isEmpty())try{for(Species s:EncounterCatalog.entry(theme).pool())if(summonable(s)&&!out.contains(s))out.add(s);}catch(RuntimeException unwritten){/* falls back below */}
        if(out.isEmpty())Collections.addAll(out,FALLBACK[clampFloor(floor)-1]);
        return Collections.unmodifiableList(out);
    }
}
