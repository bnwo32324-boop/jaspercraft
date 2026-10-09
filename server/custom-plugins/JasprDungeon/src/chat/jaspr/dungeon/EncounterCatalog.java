package chat.jaspr.dungeon;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Pure encounter data and geometry. No Bukkit, clocks, random state, or world writes. */
public final class EncounterCatalog {
    private EncounterCatalog() {}
    public static final int COUNT = 36, MAX_MARKERS = 192;
    public static final double EXIT_MARGIN = 4, REACH = 12;

    // Conservative adult bounds, including a fully open shulker and sized slimes.
    /**
     * Generation 7: a species is a vanilla mob (its own base), a custom mob drawn on a vanilla base (Bestiary dresses and
     * drives it), or a ported Mutant Creatures mob ("@" base: spawned through JasprMutants, with the named vanilla fallback
     * when that plugin is missing). Bounds are the body before scaling.
     */
    public enum Species {
        ZOMBIE(.7,2), SPIDER(1.5,1), SILVERFISH(.5,.4), SKELETON(.7,2.1),
        HUSK(.7,2), WITHER_SKELETON(.8,2.5), WITCH(.7,2), SLIME(1.1,1.1),
        CAVE_SPIDER(.8,.6), BLAZE(.7,2), MAGMA_CUBE(1.1,1.1),
        ENDERMAN(.7,3), SHULKER(2,2), ENDERMITE(.5,.4), VINDICATOR(.7,2), STRAY(.7,2.1),
        // Generation 7, vanilla bases the dungeon did not use before
        EVOKER(.7,2), VEX(.5,.9), ZOMBIE_VILLAGER(.7,2), POLAR_BEAR(1.4,1.5), IRON_GOLEM(1.5,2.8), GHAST(4.1,4.1), PIG_ZOMBIE(.7,2),
        // Generation 7 custom mobs (Bestiary dresses and drives them on their vanilla base). Floor I:
        FLAGELLANT(.7,2,"ZOMBIE"), CANDLE_WISP(.5,.9,"VEX"), OSSUARY_CRAWLER(.5,.4,"SILVERFISH"), CHOIR_BANSHEE(.7,2,"WITCH"),
        MIRE_LEECH(.8,.6,"CAVE_SPIDER"), GRAVEBOUND_KNIGHT(.8,2.5,"WITHER_SKELETON"),
        // Floor II (TUNNEL_GUNNER carries a gun):
        MAGMA_LURKER(1.1,1.1,"MAGMA_CUBE"), TUNNEL_GUNNER(.7,2.1,"SKELETON"), CEILING_STALKER(1.5,1,"SPIDER"), DEEP_MINER(.7,2,"HUSK"),
        RUST_GOLEM(1.5,2.8,"IRON_GOLEM"), GNAWER_SWARM(.5,.4,"ENDERMITE"),
        // Floor III (ABYSSAL_GUNSLINGER and SQUAD_CAPTAIN carry guns):
        ABYSSAL_GUNSLINGER(.7,2.1,"STRAY"), HELLFORGED_SENTINEL(.8,2.5,"WITHER_SKELETON"), VOID_WRAITH(.7,3,"ENDERMAN"),
        BLOOD_TEMPLAR(.7,2,"VINDICATOR"), DOOM_HERALD(.7,2,"EVOKER"), SQUAD_CAPTAIN(.7,2,"PIG_ZOMBIE"),
        // Surprises (Secrets): a reliquary that bites, and a thief who runs with the loot
        MIMIC(.7,2,"ZOMBIE"), GILDED_THIEF(.7,2,"ZOMBIE"),
        // Mutant Creatures (JasprMutants); sizes are the mod's own (setSize)
        MUTANT_ZOMBIE(1.8,3.2,"@mutant_zombie","ZOMBIE"), MUTANT_SKELETON(1.2,3.6,"@mutant_skeleton","SKELETON"),
        MUTANT_CREEPER(1.98,2.8,"@mutant_creeper","CREEPER"), MUTANT_ENDERMAN(1.2,4.2,"@mutant_enderman","ENDERMAN"),
        CREEPER_MINION(.3,.84,"@creeper_minion","CREEPER"), SPIDER_PIG(1.4,.9,"@spider_pig","SPIDER"),
        MUTANT_SNOW_GOLEM(1.1,2.2,"@mutant_snow_golem","SNOWMAN");
        public final double width, height;
        /** The entity this species is: its own name for vanilla mobs, a vanilla base for custom ones, "@kind" for mutants. */
        public final String base;
        /** For a mutant: the vanilla entity used when JasprMutants is not installed. Otherwise the base. */
        public final String fallback;
        Species(double width,double height){this(width,height,null,null);}
        Species(double width,double height,String base){this(width,height,base,base);}
        Species(double width,double height,String base,String fallback){this.width=width;this.height=height;this.base=base==null?name():base;this.fallback=fallback==null?this.base:fallback;}
        public boolean mutant(){return base.startsWith("@");}
        public boolean vanilla(){return base.equals(name());}
        /** The mutant kind without its "@" (JasprMutants' id), or null. */
        public String mutantKind(){return mutant()?base.substring(1):null;}
        public double width(boolean boss){return boss&&(this==SLIME||this==MAGMA_CUBE)?2.1:width;}
        public double height(boolean boss){return boss&&(this==SLIME||this==MAGMA_CUBE)?2.1:height;}
    }
    public enum Shape {
        TEAR_DROP, BONE_CROSS, DROWNING_RING, FURNACE_FAN, HOLLOW_HALO, CRADLE_PAIR,
        ARCHIVE_LINES, COURT_LUNGE, FROZEN_STAR, ROOT_FORK, CLOCK_HANDS, PLAGUE_PATCHES,
        MIRROR_FLANKS, SALT_SWEEP, RELIQUARY_TIDES, GALLOWS_BAR, STARLESS_FALL, THORN_CROWN,
        WAX_SPIRAL, SANGUINE_HOURGLASS, BASILICA_CHECKER, FUNGAL_BLOOM, IRON_JAWS, VELVET_SEAM,
        AMBER_LATTICE, SILENT_ECHO, CARRION_ORBIT, OPAL_PRISM, FOUNDRY_PISTONS, PALE_HOOFPRINTS,
        SCRIPTORIUM_GLYPHS, OBSIDIAN_SCISSORS, PAUPER_SCALES, ASTRAL_COMET, MOURNING_MAZE, LAST_ABSOLUTION
    }
    // No lingering fire/poison/wither or forced movement: a doorway remains a reliable escape.
    public enum Status { NONE, SLOW, WEAKNESS, BLINDNESS, SLOW_DIGGING, HUNGER }
    public static final class Entry {
        public final int theme;
        public final String themeName, bossName, cue;
        public final Species boss;
        private final Species[] pool;
        public final Shape shape;
        public final Status status;
        public final int statusTicks, windupTicks, cooldownTicks, pulses, pulseTicks;
        public final boolean bossCentered;
        Entry(int theme,String themeName,String bossName,Species boss,Species[] pool,
                      Shape shape,Status status,int statusTicks,boolean bossCentered,
                      int windupTicks,int cooldownTicks,int pulses,int pulseTicks,String cue){
            this.theme=theme;this.themeName=themeName;this.bossName=bossName;this.boss=boss;this.pool=pool.clone();
            this.shape=shape;this.status=status;this.statusTicks=statusTicks;this.bossCentered=bossCentered;
            this.windupTicks=windupTicks;this.cooldownTicks=cooldownTicks;this.pulses=pulses;this.pulseTicks=pulseTicks;this.cue=cue;
        }
        public String id(){return shape.name();}
        public Species species(int slot,int motif,boolean isBoss){return isBoss?boss:pool[Math.floorMod(slot+motif,pool.length)];}
        public List<Species> pool(){List<Species> out=new ArrayList<>();Collections.addAll(out,pool);return Collections.unmodifiableList(out);}
        // Preserve the original eighteen timings; new sequences scale without removing dodge windows.
        public int windup(int tier){return theme<18?windupTicks:Math.max(36,windupTicks-2*threat(tier));}
        public int pulseDelay(int tier){return theme<18?pulseTicks:Math.max(28,pulseTicks-threat(tier));}
        public int cooldown(int tier,boolean enraged){int base=theme<18?cooldownTicks:Math.max(140,cooldownTicks-6*threat(tier));return enraged?Math.max(100,base*2/3):base;}
        public double damage(int tier){return (4+threat(tier))/(theme<18?1:pulses==3?1.8:1.35);}
        public int statusDuration(int tier){return theme<18?statusTicks:Math.min(statusTicks,20+4*threat(tier));}
        public String phaseCue(int phase){
            if(phase<0||phase>=pulses)throw new IllegalArgumentException("phase "+phase);
            switch(shape){
                case WAX_SPIRAL:return "Wax coil "+(phase+1)+"/3: step across the curved ribbon into an unmarked gap.";
                case SANGUINE_HOURGLASS:return phase==0?"Hourglass I: the forward and rear wedges fill; move to either side.":"Hourglass II: the side wedges fill; move forward or back.";
                case BASILICA_CHECKER:return phase==0?"Broken tiles I: stand on an unmarked checker square.":"Broken tiles II: squares invert; move onto a previously marked square.";
                case FUNGAL_BLOOM:return "Bloom "+(phase+1)+"/3: four spore caps expand outward; use the diagonal gaps.";
                case IRON_JAWS:return "Press "+(phase+1)+"/3: jaws close inward; follow the shrinking center gap or leave the press.";
                case VELVET_SEAM:return phase==0?"Hem I: leave the winding velvet seam.":"Hem II: the seam reverses its bends; follow the fresh marks.";
                case AMBER_LATTICE:return phase==0?"Amber I: stand inside a clear diamond cell.":"Amber II: the lattice shifts; move to a fresh clear cell.";
                case SILENT_ECHO:return "Echo "+(phase+1)+"/3: an oval wave expands; step inside the spent wave.";
                case CARRION_ORBIT:return "Carrion "+(phase+1)+"/3: three curved wings orbit; use the center or gaps between wings.";
                case OPAL_PRISM:return "Prism "+(phase+1)+"/3: the triangular frame contracts; cross its marked edges.";
                case FOUNDRY_PISTONS:return "Pistons "+(phase+1)+"/3: staggered hammer blocks switch lanes; use the clear lane.";
                case PALE_HOOFPRINTS:return "Hooves "+(phase+1)+"/3: paired prints march forward; step beside the next pair.";
                case SCRIPTORIUM_GLYPHS:return "Script "+(phase+1)+"/3: corner brackets turn; use the open center or unmarked corners.";
                case OBSIDIAN_SCISSORS:return "Shears "+(phase+1)+"/3: two blades close toward the forward axis; move behind their hinge.";
                case PAUPER_SCALES:return phase==0?"Scales I: the right diamond is heavy; leave both marked pans.":"Scales II: the left diamond grows; cross through the center gap.";
                case ASTRAL_COMET:return "Comet "+(phase+1)+"/3: a bowed tail advances; cross behind the marked curve.";
                case MOURNING_MAZE:return "Labyrinth "+(phase+1)+"/3: square walls contract; follow the turning gate or leave the square.";
                case LAST_ABSOLUTION:return "Absolution "+(phase+1)+"/3: enter the moving clear island or leave the marked disc.";
                default:return cue;
            }
        }
    }
    private static Species[] pool(Species... values){return values;}
    private static final Entry[] ENTRIES={
        new Entry(0,"Weeping Cellar","The Unforgiven",Species.ZOMBIE,pool(Species.ZOMBIE,Species.SPIDER,Species.SILVERFISH),Shape.TEAR_DROP,Status.NONE,0,false,36,150,1,0,"Tears gather: leave the marked disc!"),
        new Entry(1,"Ossuary","Prior of Bones",Species.WITHER_SKELETON,pool(Species.SKELETON,Species.HUSK,Species.WITHER_SKELETON),Shape.BONE_CROSS,Status.SLOW,40,false,40,160,1,0,"Bone cross: step diagonally out of its arms!"),
        new Entry(2,"Drowned Confessional","Mother of Tears",Species.WITCH,pool(Species.WITCH,Species.SLIME,Species.CAVE_SPIDER),Shape.DROWNING_RING,Status.HUNGER,60,false,40,160,1,0,"Drowning ring: its center and outer shore are safe!"),
        new Entry(3,"Cinder Chapel","The Furnace Heart",Species.MAGMA_CUBE,pool(Species.BLAZE,Species.MAGMA_CUBE,Species.WITHER_SKELETON),Shape.FURNACE_FAN,Status.NONE,0,true,44,160,1,0,"Furnace breath: get beside or behind the marked fan!"),
        new Entry(4,"Hollow Choir","The Hollow Witness",Species.ENDERMAN,pool(Species.ENDERMAN,Species.SHULKER,Species.ENDERMITE),Shape.HOLLOW_HALO,Status.BLINDNESS,20,true,44,170,1,0,"Hollow halo: move close to the witness or beyond the ring!"),
        new Entry(5,"Rotten Nursery","Keeper of the Cradle",Species.VINDICATOR,pool(Species.HUSK,Species.CAVE_SPIDER,Species.VINDICATOR),Shape.CRADLE_PAIR,Status.WEAKNESS,60,false,36,150,1,0,"Twin cradles: keep between the two marked discs!"),
        new Entry(6,"Ashen Archive","The Cindered Scribe",Species.SKELETON,pool(Species.SKELETON,Species.WITCH,Species.ENDERMITE),Shape.ARCHIVE_LINES,Status.SLOW_DIGGING,60,true,44,170,1,0,"Burning verses: step between the three parallel lines!"),
        new Entry(7,"Vermilion Court","The Red Magistrate",Species.VINDICATOR,pool(Species.VINDICATOR,Species.HUSK,Species.WITCH),Shape.COURT_LUNGE,Status.WEAKNESS,40,true,32,130,1,0,"Crimson verdict: sidestep the long narrow strike!"),
        new Entry(8,"Frozen Sacristy","The Rime Cantor",Species.STRAY,pool(Species.STRAY,Species.SKELETON,Species.SILVERFISH),Shape.FROZEN_STAR,Status.SLOW,50,false,44,170,1,0,"Ice star: stand between the eight marked spokes!"),
        new Entry(9,"Rootbound Crypt","The Buried Gardener",Species.HUSK,pool(Species.HUSK,Species.SPIDER,Species.ENDERMITE),Shape.ROOT_FORK,Status.SLOW,60,true,42,160,1,0,"Forking roots: keep between or outside the two branches!"),
        new Entry(10,"Clockwork Penance","The Brass Confessor",Species.WITHER_SKELETON,pool(Species.WITHER_SKELETON,Species.SKELETON,Species.SILVERFISH),Shape.CLOCK_HANDS,Status.NONE,0,true,44,190,3,28,"Clock hands: three quarter-turn strikes; follow the new marks!"),
        new Entry(11,"Plague Infirmary","The Pallid Surgeon",Species.ZOMBIE,pool(Species.ZOMBIE,Species.WITCH,Species.SILVERFISH),Shape.PLAGUE_PATCHES,Status.HUNGER,60,false,42,160,1,0,"Sickbeds: leave the three marked patches!"),
        new Entry(12,"Mirror Tribunal","The Divided Judge",Species.VINDICATOR,pool(Species.VINDICATOR,Species.SKELETON,Species.SHULKER),Shape.MIRROR_FLANKS,Status.WEAKNESS,50,false,40,155,1,0,"Mirror sentence: the corridor between the two bands is safe!"),
        new Entry(13,"Salt Cathedral","The Salt Prelate",Species.HUSK,pool(Species.HUSK,Species.STRAY,Species.SILVERFISH),Shape.SALT_SWEEP,Status.SLOW,30,true,44,190,3,28,"Salt breakers: three bands advance away from the prelate!"),
        new Entry(14,"Sunken Reliquary","The Drowned Custodian",Species.WITCH,pool(Species.WITCH,Species.SLIME,Species.WITHER_SKELETON),Shape.RELIQUARY_TIDES,Status.SLOW,30,false,44,180,2,32,"Receding tide: the outer ring strikes, then the inner ring!"),
        new Entry(15,"Gallows Refectory","The Last Host",Species.VINDICATOR,pool(Species.VINDICATOR,Species.ZOMBIE,Species.SPIDER),Shape.GALLOWS_BAR,Status.BLINDNESS,20,false,36,145,1,0,"Gallows beam: move forward or back out of the crosswise band!"),
        new Entry(16,"Starless Observatory","The Unseeing Astronomer",Species.ENDERMAN,pool(Species.SHULKER,Species.STRAY,Species.ENDERMITE),Shape.STARLESS_FALL,Status.BLINDNESS,20,false,44,175,1,0,"Falling stars: leave the six small marked impact discs!"),
        new Entry(17,"Thorn Sanctuary","The Briar Abbess",Species.WITCH,pool(Species.WITCH,Species.CAVE_SPIDER,Species.HUSK),Shape.THORN_CROWN,Status.SLOW,40,false,42,165,1,0,"Thorn crown: use the four gaps or stay inside the ring!"),
        new Entry(18,"Wax Sepulchre","The Tallow Saint",Species.HUSK,pool(Species.HUSK,Species.SLIME,Species.SILVERFISH,Species.SKELETON),Shape.WAX_SPIRAL,Status.SLOW,36,false,52,210,3,38,"Wax spiral: three curved ribbons turn; cross into the unmarked gaps!"),
        new Entry(19,"Sanguine Cloister","The Vein Cantor",Species.VINDICATOR,pool(Species.VINDICATOR,Species.WITCH,Species.CAVE_SPIDER,Species.ZOMBIE),Shape.SANGUINE_HOURGLASS,Status.WEAKNESS,40,false,50,195,2,38,"Blood hourglass: opposite wedges fill, then the side wedges; switch axes!"),
        new Entry(20,"Shattered Basilica","The Broken Pontiff",Species.WITHER_SKELETON,pool(Species.WITHER_SKELETON,Species.SKELETON,Species.SILVERFISH,Species.SHULKER),Shape.BASILICA_CHECKER,Status.NONE,0,false,54,210,2,40,"Fractured floor: alternating checker squares strike; switch to a spent square!"),
        new Entry(21,"Fungal Hospice","The Spore Matron",Species.SLIME,pool(Species.SLIME,Species.ZOMBIE,Species.CAVE_SPIDER,Species.ENDERMITE),Shape.FUNGAL_BLOOM,Status.HUNGER,40,false,52,215,3,38,"Spore bloom: four caps grow outward in three steps; keep to diagonal gaps!"),
        new Entry(22,"Iron Inquisition","The Rack Warden",Species.VINDICATOR,pool(Species.VINDICATOR,Species.WITHER_SKELETON,Species.HUSK,Species.SILVERFISH),Shape.IRON_JAWS,Status.SLOW_DIGGING,40,true,50,200,3,38,"Iron jaws: two bars close in three steps; follow the gap or step past the ends!"),
        new Entry(23,"Velvet Catacomb","The Seamstress of Mourning",Species.WITCH,pool(Species.WITCH,Species.SPIDER,Species.ENDERMAN,Species.ZOMBIE),Shape.VELVET_SEAM,Status.WEAKNESS,36,false,52,195,2,38,"Velvet seam: a winding stitch reverses its bends; follow each fresh warning!"),
        new Entry(24,"Amber Baptistry","The Preserved Deacon",Species.HUSK,pool(Species.SHULKER,Species.SILVERFISH,Species.SLIME,Species.HUSK),Shape.AMBER_LATTICE,Status.SLOW,32,false,54,215,2,40,"Amber lattice: diagonal lines shift half a cell; move between clear diamonds!"),
        new Entry(25,"Silent Belfry","The Voiceless Ringer",Species.STRAY,pool(Species.STRAY,Species.ENDERMAN,Species.SKELETON,Species.ENDERMITE),Shape.SILENT_ECHO,Status.NONE,0,true,52,210,3,38,"Silent echoes: three oval waves expand; step behind each spent wave!"),
        new Entry(26,"Carrion Conservatory","The Carrion Curator",Species.HUSK,pool(Species.HUSK,Species.SPIDER,Species.WITHER_SKELETON,Species.ENDERMITE),Shape.CARRION_ORBIT,Status.HUNGER,40,false,54,215,3,40,"Carrion orbit: three curved wings turn around a safe center; follow the gaps!"),
        new Entry(27,"Opaline Sepulcher","The Many-Faced Mourner",Species.WITCH,pool(Species.SHULKER,Species.STRAY,Species.WITCH,Species.SILVERFISH),Shape.OPAL_PRISM,Status.NONE,0,false,54,220,3,40,"Opal prism: a triangular frame contracts in three steps; cross a spent edge!"),
        new Entry(28,"Sunless Foundry","The Cold Crucible",Species.MAGMA_CUBE,pool(Species.MAGMA_CUBE,Species.BLAZE,Species.VINDICATOR,Species.SKELETON),Shape.FOUNDRY_PISTONS,Status.SLOW_DIGGING,40,true,50,205,3,36,"Cold pistons: staggered hammer blocks switch lanes; leave the next marked block!"),
        new Entry(29,"Pale Menagerie","The Ivory Beastkeeper",Species.SPIDER,pool(Species.SPIDER,Species.CAVE_SPIDER,Species.SLIME,Species.ENDERMITE),Shape.PALE_HOOFPRINTS,Status.NONE,0,true,50,195,3,36,"Pale stampede: paired hoofprints march forward; dodge sideways out of their path!"),
        new Entry(30,"Flooded Scriptorium","The Drowned Illuminator",Species.WITCH,pool(Species.WITCH,Species.SLIME,Species.STRAY,Species.SKELETON),Shape.SCRIPTORIUM_GLYPHS,Status.SLOW,32,false,54,215,3,40,"Drowned script: corner brackets turn around an open center; leave the marked corners!"),
        new Entry(31,"Obsidian Vestry","The Black Sacristan",Species.WITHER_SKELETON,pool(Species.WITHER_SKELETON,Species.ENDERMAN,Species.VINDICATOR,Species.BLAZE),Shape.OBSIDIAN_SCISSORS,Status.WEAKNESS,36,true,52,205,3,38,"Obsidian shears: two blades close toward the forward axis; retreat behind the hinge!"),
        new Entry(32,"Gilded Pauperhouse","The Beggar Sovereign",Species.ZOMBIE,pool(Species.ZOMBIE,Species.HUSK,Species.VINDICATOR,Species.SKELETON),Shape.PAUPER_SCALES,Status.HUNGER,40,false,52,195,2,38,"Beggar scales: two diamond pans trade weight; cross the clear center to escape!"),
        new Entry(33,"Astral Chancel","The Falling Celebrant",Species.ENDERMAN,pool(Species.ENDERMAN,Species.SHULKER,Species.BLAZE,Species.STRAY),Shape.ASTRAL_COMET,Status.NONE,0,true,54,215,3,40,"Astral comet: a bowed tail advances three times; cross behind its marked curve!"),
        new Entry(34,"Mourning Labyrinth","The Lost Procession",Species.VINDICATOR,pool(Species.VINDICATOR,Species.ENDERMITE,Species.SPIDER,Species.STRAY),Shape.MOURNING_MAZE,Status.SLOW,32,false,56,220,3,42,"Mourning maze: square walls contract as their gate turns; follow the gap or escape outside!"),
        new Entry(35,"Last Absolution","The Final Penitent",Species.WITHER_SKELETON,pool(Species.WITHER_SKELETON,Species.WITCH,Species.HUSK,Species.SHULKER),Shape.LAST_ABSOLUTION,Status.NONE,0,false,56,220,3,42,"Last absolution: a clear island circles inside the marked disc; follow it or leave the disc!")
    };
    /** Generation 7: themes 36..53 (Floor I's new ones), 54..77 (Floor II) and 78..101 (Floor III) have their own catalogues. */
    public static Entry entry(int theme){
        if(theme>=0&&theme<COUNT)return ENTRIES[theme];
        Entry e=theme<Floors.FLOOR_TWO_BASE?FloorOneExtra.entry(theme):theme<Floors.FLOOR_THREE_BASE?FloorTwo.entry(theme):theme<Floors.THEME_TOTAL?FloorThree.entry(theme):null;
        if(e==null)throw new IllegalArgumentException("theme "+theme);return e;
    }
    /** Generation 6's 36 entries (themes 0..35). */
    public static List<Entry> entries(){List<Entry> out=new ArrayList<>();Collections.addAll(out,ENTRIES);return Collections.unmodifiableList(out);}
    /** Every entry that exists, all floors. */
    public static List<Entry> allEntries(){
        List<Entry> out=new ArrayList<>(entries());
        for(int t=COUNT;t<Floors.THEME_TOTAL;t++){if(t>=Floors.floorOneThemes()&&t<Floors.FLOOR_TWO_BASE)continue;out.add(entry(t));}
        return Collections.unmodifiableList(out);
    }
    static Species[] pool(Entry e){return e.pool.clone();}

    /** A frozen warning: moving a boss or target never moves an already advertised impact. */
    public static final class Pattern {
        public final Entry entry;
        public final double x,z,angle;
        public final int phase;
        private Pattern(Entry entry,double x,double z,double angle,int phase){this.entry=entry;this.x=x;this.z=z;this.angle=angle;this.phase=phase;}
        public Pattern next(){if(phase+1>=entry.pulses)throw new IllegalStateException("Last pulse");return new Pattern(entry,x,z,angle,phase+1);}
        public boolean hits(Layout.Room room,double px,double py,double pz){
            if(!hazardAllowed(room,px,py,pz))return false;
            double a=angle+(entry.shape==Shape.CLOCK_HANDS?phase*Math.PI/2:0);
            double dx=px-x,dz=pz-z,u=dx*Math.cos(a)+dz*Math.sin(a),v=-dx*Math.sin(a)+dz*Math.cos(a);
            double radius=Math.hypot(u,v);
            switch(entry.shape){
                case TEAR_DROP:return radius<=3;
                case BONE_CROSS:return (Math.abs(u)<=.85&&Math.abs(v)<=7)||(Math.abs(v)<=.85&&Math.abs(u)<=7);
                case DROWNING_RING:return radius>=2.5&&radius<=5;
                case FURNACE_FAN:return radius<=10&&u>=Math.abs(v);
                case HOLLOW_HALO:return radius>=4&&radius<=8;
                case CRADLE_PAIR:return disc(u-3,v,2)||disc(u+3,v,2);
                case ARCHIVE_LINES:return u>=0&&u<=12&&(Math.abs(v)<=.65||Math.abs(v-3)<=.65||Math.abs(v+3)<=.65);
                case COURT_LUNGE:return u>=0&&u<=12&&Math.abs(v)<=1.2;
                case FROZEN_STAR:return radius<=6&&(Math.abs(u)<=.55||Math.abs(v)<=.55||Math.abs(u-v)<=.78||Math.abs(u+v)<=.78);
                case ROOT_FORK:return u>=2&&u<=10&&(Math.abs(v-u*.5)<=.8||Math.abs(v+u*.5)<=.8);
                case CLOCK_HANDS:return (u>=0&&u<=9&&Math.abs(v)<=.75)||(v>=0&&v<=9&&Math.abs(u)<=.75);
                case PLAGUE_PATCHES:return disc(u,v,1.8)||disc(u-3,v-2.5,1.8)||disc(u+3,v-2.5,1.8);
                case MIRROR_FLANKS:return Math.abs(u)<=6&&Math.abs(v)>=2&&Math.abs(v)<=4;
                case SALT_SWEEP:return u>=2+phase*3&&u<=4+phase*3&&Math.abs(v)<=6;
                case RELIQUARY_TIDES:return radius>=(phase==0?5:2)&&radius<=(phase==0?7:4);
                case GALLOWS_BAR:return Math.abs(u)<=1&&Math.abs(v)<=8;
                case STARLESS_FALL:
                    if(disc(u,v,1.3))return true;
                    for(int i=0;i<5;i++)if(disc(u-5*Math.cos(i*2*Math.PI/5),v-5*Math.sin(i*2*Math.PI/5),1.3))return true;
                    return false;
                case THORN_CROWN:return radius>=4&&radius<=6&&Math.abs(u)>=1.2&&Math.abs(v)>=1.2;
                case WAX_SPIRAL:return radius>=1.5&&radius<=8&&Math.abs(Math.sin(Math.atan2(v,u)-radius*.6-phase*1.1))<.22;
                case SANGUINE_HOURGLASS:return radius>=1&&radius<=8&&(phase==0?Math.abs(v)<Math.abs(u)*.65:Math.abs(u)<Math.abs(v)*.65);
                case BASILICA_CHECKER:return Math.abs(u)<8&&Math.abs(v)<8&&Math.floorMod((int)Math.floor(u/3)+(int)Math.floor(v/3)+phase,2)==0;
                case FUNGAL_BLOOM:
                    double bloom=2+phase*2;
                    return disc(u-bloom,v,1.4)||disc(u+bloom,v,1.4)||disc(u,v-bloom,1.4)||disc(u,v+bloom,1.4);
                case IRON_JAWS:return Math.abs(u)<=7&&Math.abs(Math.abs(v)-(6-phase*2))<=.85;
                case VELVET_SEAM:return Math.abs(u)<=8&&Math.abs(v-(phase==0?1:-1)*3*Math.sin(u*.6))<=.8;
                case AMBER_LATTICE:return Math.abs(u)<=7&&Math.abs(v)<=7&&(gridLine(u+v+phase*2,4,.65)||gridLine(u-v+phase*2,4,.65));
                case SILENT_ECHO:
                    double oval=Math.hypot(u/1.4,v);
                    return oval>=2+phase*1.6&&oval<=3+phase*1.6;
                case CARRION_ORBIT:return radius>=3.5&&radius<=7&&Math.cos(3*(Math.atan2(v,u)-phase*.65))>.45;
                case OPAL_PRISM:
                    double triangle=Math.max(-u*.5+v*Math.sqrt(3)/2,Math.max(-u*.5-v*Math.sqrt(3)/2,u));
                    return triangle>=4.5-phase&&triangle<=5.3-phase;
                case FOUNDRY_PISTONS:return u>=0&&u<=10&&Math.abs(v)<=5&&(Math.floorMod((int)Math.floor((u-phase*.75)/2.5)+phase,2)==0?v>=1:v<=-1);
                case PALE_HOOFPRINTS:return disc(u-(1+phase*3),v-2,1.5)||disc(u-(2+phase*3),v+2,1.5);
                case SCRIPTORIUM_GLYPHS:
                    double turn=phase*2*Math.PI/3,gu=u*Math.cos(turn)+v*Math.sin(turn),gv=-u*Math.sin(turn)+v*Math.cos(turn);
                    return bracket(gu,gv)||bracket(-gu,-gv);
                case OBSIDIAN_SCISSORS:
                    double blade=(3-phase)*.24;
                    return u>=0&&u<=10&&(Math.abs(v-u*blade)<=.65||Math.abs(v+u*blade)<=.65);
                case PAUPER_SCALES:return Math.abs(u)+Math.abs(v-4)<=(phase==0?3:1.5)||Math.abs(u)+Math.abs(v+4)<=(phase==0?1.5:3);
                case ASTRAL_COMET:return Math.abs(v)<=6&&Math.abs(u-(-2+phase*3+v*v*.13))<=.8;
                case MOURNING_MAZE:
                    double square=Math.max(Math.abs(u),Math.abs(v));
                    boolean gate=phase==0?u>0&&Math.abs(v)<2:phase==1?v>0&&Math.abs(u)<2:u<0&&Math.abs(v)<2;
                    return square>=6.5-phase*1.6&&square<=7.5-phase*1.6&&!gate;
                case LAST_ABSOLUTION:
                    double island=phase*2*Math.PI/3;
                    return radius<=7&&!disc(u-3.5*Math.cos(island),v-3.5*Math.sin(island),2.5);
                default:throw new AssertionError(entry.shape);
            }
        }
        /** Bounded filled marks, sampled from the exact damage predicate and clipped to this room. */
        public List<double[]> markers(Layout.Room room){
            List<double[]> all=new ArrayList<>();
            // Diagonal components of the longest rectangular pattern stay inside this square.
            for(int ix=-18;ix<=18;ix++)for(int iz=-18;iz<=18;iz++){
                double px=x+ix*.75,pz=z+iz*.75;
                if(hits(room,px,Layout.FLOOR+1,pz))all.add(new double[]{px,pz});
            }
            if(all.size()<=MAX_MARKERS)return Collections.unmodifiableList(all);
            List<double[]> limited=new ArrayList<>();for(int i=0;i<MAX_MARKERS;i++)limited.add(all.get(i*all.size()/MAX_MARKERS));
            return Collections.unmodifiableList(limited);
        }
    }
    private static boolean disc(double x,double z,double radius){return x*x+z*z<=radius*radius;}
    private static boolean gridLine(double value,double period,double width){return Math.abs(value-period*Math.rint(value/period))<=width;}
    private static boolean bracket(double u,double v){return u>=2&&u<=6.8&&v>=2&&v<=6.8&&(u>=5.5||v>=5.5);}
    public static int threat(int tier){return Math.max(0,Math.min(5,tier));}
    public static Pattern pattern(int theme,double bossX,double bossZ,double targetX,double targetZ){
        Entry e=entry(theme);return new Pattern(e,e.bossCentered?bossX:targetX,e.bossCentered?bossZ:targetZ,Math.atan2(targetZ-bossZ,targetX-bossX),0);
    }
    public static boolean hazardAllowed(Layout.Room r,double x,double y,double z){
        return r.kind!=Layout.Kind.REFUGE&&x>=r.x+EXIT_MARGIN&&x<r.x+r.w-EXIT_MARGIN&&z>=r.z+EXIT_MARGIN&&z<r.z+r.d-EXIT_MARGIN&&y>=Layout.FLOOR+1&&y<Math.min(r.roof()-1,Layout.FLOOR+5);
    }
    public static boolean bodyInside(Layout.Room r,Species species,boolean boss,double x,double y,double z){
        return bodyInside(r,species.width(boss),species.height(boss),x,y,z);
    }
    /** A body of any (scaled) width and height: clear of the walls by two cells and under the roof. */
    public static boolean bodyInside(Layout.Room r,double width,double height,double x,double y,double z){
        double half=width/2+.05;
        return x-half>=r.x+2&&x+half<r.x+r.w-2&&z-half>=r.z+2&&z+half<r.z+r.d-2&&y>=Layout.FLOOR+1&&y+height<r.roof();
    }
    public interface Blocks {boolean air(int x,int y,int z);boolean floor(int x,int y,int z);}
    public static boolean fits(Layout.Room r,Species species,boolean boss,double x,double y,double z,Blocks blocks){
        return fits(r,species.width(boss),species.height(boss),x,y,z,blocks);
    }
    /** Floor under the whole footprint and air through the whole body, for a body of any (scaled) width and height. */
    public static boolean fits(Layout.Room r,double width,double height,double x,double y,double z,Blocks blocks){
        if(!bodyInside(r,width,height,x,y,z))return false;
        double half=width/2+.05;
        int lowY=(int)Math.floor(y),highY=(int)Math.ceil(y+height)-1;
        for(int bx=(int)Math.floor(x-half);bx<=(int)Math.floor(x+half);bx++)for(int bz=(int)Math.floor(z-half);bz<=(int)Math.floor(z+half);bz++){
            if(!blocks.floor(bx,lowY-1,bz))return false;
            for(int by=lowY;by<=highY;by++)if(!blocks.air(bx,by,bz))return false;
        }
        return true;
    }
    /** The cell of a slot and its fallbacks: see Layout.Room.Stations. */
    public static double[] spawnPoint(Layout.Room r,int slot){return new double[]{r.spawnX(slot)+.5,Layout.FLOOR+1,r.spawnZ(slot)+.5};}
}
