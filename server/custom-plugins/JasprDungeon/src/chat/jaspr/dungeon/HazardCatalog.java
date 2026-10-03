package chat.jaspr.dungeon;

/**
 * Owner 2026-10-03: every room has its own dangers, without exception, treasure rooms most of all.
 *
 * Pure (no Bukkit): which dangers a room has, and where their marks sit in the stone. The generator draws the
 * marks and Hazards strikes from the same cells, so what a player sees is where the danger comes from. Ordinary
 * rooms carry one danger; treasure rooms, shrines and gauntlets carry two. Each theme favours three dangers that
 * suit it and the room's hash picks among them, so neighbouring rooms rarely share the same set. Nothing is ever
 * marked or aimed inside the arrival circle of the origin refuge.
 */
public final class HazardCatalog {
    public enum Type {
        FLAME_VENTS("Flame Vents"), DART_SLITS("Dart Slits"), SPIKE_RUNES("Spike Runes"), FALLING_MASONRY("Falling Masonry"),
        MIASMA("Poison Miasma"), FROST_GUSTS("Frost Gusts"), SMITE("Smiting Bolts"), SHOCKWAVE("Shockwave"),
        PHANTOM_BLADES("Phantom Blades"), POTION_RAIN("Potion Rain"), GRAVITY_WELL("Gravity Well"), CREEPING_DARK("Creeping Dark"),
        BLAST_SPORES("Blast Spores"), EMBER_BOLTS("Ember Bolts");
        public final String title;
        Type(String title){this.title=title;}
    }
    private static final Type VENTS=Type.FLAME_VENTS, DARTS=Type.DART_SLITS, RUNES=Type.SPIKE_RUNES, MASONRY=Type.FALLING_MASONRY,
        MIASMA=Type.MIASMA, FROST=Type.FROST_GUSTS, SMITE=Type.SMITE, SHOCK=Type.SHOCKWAVE, BLADES=Type.PHANTOM_BLADES,
        RAIN=Type.POTION_RAIN, WELL=Type.GRAVITY_WELL, DARK=Type.CREEPING_DARK, SPORES=Type.BLAST_SPORES, EMBERS=Type.EMBER_BOLTS;
    /** Three dangers that suit each theme, in Layout.THEMES order. */
    static final Type[][] FAVOURED={
        {MIASMA,MASONRY,DARK},{RUNES,MASONRY,SHOCK},{FROST,WELL,RAIN},{VENTS,EMBERS,SPORES},{SHOCK,BLADES,DARK},{MIASMA,SPORES,RUNES},
        {VENTS,DARTS,BLADES},{BLADES,DARTS,EMBERS},{FROST,MASONRY,SMITE},{RUNES,MIASMA,WELL},{DARTS,BLADES,SHOCK},{MIASMA,RAIN,SPORES},
        {BLADES,SMITE,DARK},{SMITE,FROST,SHOCK},{WELL,FROST,RAIN},{DARTS,MASONRY,RUNES},{SMITE,WELL,DARK},{RUNES,DARTS,MIASMA},
        {VENTS,EMBERS,DARK},{RUNES,RAIN,BLADES},{MASONRY,SHOCK,SMITE},{SPORES,MIASMA,RAIN},{RUNES,DARTS,BLADES},{DARK,MIASMA,MASONRY},
        {VENTS,WELL,RAIN},{SHOCK,MASONRY,DARK},{MIASMA,SPORES,DARTS},{SMITE,BLADES,FROST},{VENTS,EMBERS,SHOCK},{RUNES,SPORES,FROST},
        {FROST,WELL,DARTS},{EMBERS,DARK,RUNES},{DARTS,MASONRY,RAIN},{SMITE,WELL,BLADES},{DARK,BLADES,RUNES},{SMITE,VENTS,SHOCK}
    };

    private HazardCatalog(){}
    private static int data(int id,int value){return id|(value<<12);}

    public static Type[] of(Layout.Room r){
        long h=Layout.mix(r.hash^0x48415a4152445300L);
        Type[] favoured=FAVOURED[r.theme];int pick=(int)Math.floorMod(h,favoured.length);Type first=favoured[pick];
        // The arrival room's only well sigil would lie inside the arrival circle: it takes the theme's next danger instead.
        if(first==WELL&&r.kind==Layout.Kind.REFUGE&&r.x==0&&r.z==0)first=favoured[(pick+1)%favoured.length];
        boolean two=r.kind==Layout.Kind.TREASURE||r.kind==Layout.Kind.SHRINE||r.kind==Layout.Kind.GAUNTLET;
        if(!two)return new Type[]{first};
        Type[] all=Type.values();int i=(int)Math.floorMod(h>>>17,all.length-1);if(i>=first.ordinal())i++;
        return new Type[]{first,all[i]};
    }
    public static boolean has(Layout.Room r,Type t){for(Type x:r.hazards)if(x==t)return true;return false;}
    public static String names(Layout.Room r){
        StringBuilder b=new StringBuilder();for(Type t:r.hazards){if(b.length()>0)b.append(", ");b.append(t.title);}return b.toString();
    }

    /** The arrival circle in the origin refuge: return portal, landing squares and rift cracks. */
    public static boolean safe(Layout.Room r,double x,double z){return r.kind==Layout.Kind.REFUGE&&r.x==0&&r.z==0&&x>=6&&x<27&&z>=5&&z<27;}
    /** The four cells in from each doorway, where a traveller lands. */
    static boolean landing(Layout.Room r,int x,int z){
        int rx=x-r.x,rz=z-r.z;
        return Layout.Room.lane(z)&&(rx<6||rx>r.w-7)||Layout.Room.lane(x)&&(rz<6||rz>r.d-7);
    }
    /** Interior floor a mark may take: off the light lattice, away from the chest, doorways and the arrival circle. */
    public static boolean open(Layout.Room r,int x,int z){
        int rx=x-r.x,rz=z-r.z;
        if(rx<3||rz<3||rx>r.w-4||rz>r.d-4)return false;
        if(Math.floorMod(x,8)==4&&Math.floorMod(z,8)==4)return false;
        if(Math.abs(x-r.cx())<=1&&Math.abs(z-r.cz()-4)<=1)return false;
        return !safe(r,x+.5,z+.5)&&!landing(r,x,z);
    }

    // ---------------------------------------------------------------- where each danger lives
    public static boolean vent(int x,int z){return Math.floorMod(x,6)==3&&Math.floorMod(z,6)==3;}
    public static boolean seep(int x,int z){return Math.floorMod(x,7)==3&&Math.floorMod(z,7)==3;}
    public static boolean rune(int x,int z){return Math.floorMod(x,16)==8||Math.floorMod(z,16)==8;}
    public static boolean drift(int z){return Math.floorMod(z,10)==5;}
    public static boolean blade(int x,int z){return Math.floorMod(x,8)==0||Math.floorMod(z,8)==0;}
    public static boolean ring(Layout.Room r,int x,int z){
        double d=Math.hypot(x-r.cx(),z-r.cz());int k=(int)Math.round(d);return k>0&&k%6==0&&Math.abs(d-k)<.5;
    }
    /** A sigil around every lane crossing: where a gravity well opens. */
    public static boolean sigil(int x,int z){int dx=Math.floorMod(x,32)-16,dz=Math.floorMod(z,32)-16,c=Math.max(Math.abs(dx),Math.abs(dz));return c==0||c==2;}
    public static boolean stain(Layout.Room r,int x,int z){return Math.floorMod(Layout.mix(r.hash^(x*0x632be59bd9b4e019L)^(z*0x9e3779b97f4a7c15L)),11)==0;}
    public static boolean crack(int x,int z){int a=Math.floorMod(x,5),b=Math.floorMod(z,5);return a>=2&&a<=3&&b>=2&&b<=3;}
    public static boolean lens(int x,int z){return Math.floorMod(x,9)==4&&Math.floorMod(z,9)==4;}
    public static boolean spout(int x,int z){return Math.floorMod(x,6)==3&&Math.floorMod(z,6)==0;}

    /** Position along the inner face of a wall for inner-layer wall cells that are not corners or doorways, else -1. */
    public static int along(Layout.Room r,int x,int z){
        int rx=x-r.x,rz=z-r.z;boolean we=rx==1||rx==r.w-2,ns=rz==1||rz==r.d-2;
        if(we==ns)return -1;
        int along=we?rz:rx,span=we?r.d:r.w;
        if(along<3||along>span-4||Layout.Room.lane(we?z:x))return -1;
        return along;
    }
    public static boolean slit(Layout.Room r,int x,int z){int a=along(r,x,z),m=Math.floorMod(a,32);return a>=0&&(m==2||m==14||m==18||m==30);}
    public static boolean socket(Layout.Room r,int x,int z){int a=along(r,x,z),m=Math.floorMod(a,32);return a>=0&&(m==13||m==19);}
    /** The interior point in front of an inner-wall cell, and the direction into the room: {x, z, dx, dz}. */
    public static double[] front(Layout.Room r,int x,int z){
        int rx=x-r.x,rz=z-r.z;
        if(rx==1)return new double[]{x+1.5,z+.5,1,0};
        if(rx==r.w-2)return new double[]{x-.5,z+.5,-1,0};
        if(rz==1)return new double[]{x+.5,z+1.5,0,1};
        return new double[]{x+.5,z-.5,0,-1};
    }
    /**
     * The first interior ring against the walls. On the west and north walls a free column separates the ring from the
     * bays, so it holds pillars at slots 4, 10, 22, 28 and props at 7 and 25 of every 32 along the wall. On the east and
     * south walls the ring touches the bays, so only two pillars flank each doorway (slots 12 and 20), where no bay is
     * beside them. Corners and lanes stay clear and no two pieces touch, so the floor behind them stays connected.
     */
    public static int strip(Layout.Room r,int x,int z){
        int rx=x-r.x,rz=z-r.z;boolean we=rx==2||rx==r.w-3,ns=rz==2||rz==r.d-3;
        if(we==ns||r.clearLane(x,z))return -1;
        int along=we?rz:rx,span=we?r.d:r.w;
        if(along<4||along>span-5)return -1;
        return Math.floorMod(along,32);
    }
    /** East or south side of the room, where the first interior ring touches the bays. */
    public static boolean far(Layout.Room r,int x,int z){return x-r.x>=r.w-3||z-r.z>=r.d-3;}
    public static boolean pillarSlot(boolean far,int m){return far?m==12||m==20:m==4||m==10||m==22||m==28;}
    public static boolean pillar(Layout.Room r,int x,int z){int m=strip(r,x,z);return m>=0&&pillarSlot(far(r,x,z),m);}
    public static boolean prop(Layout.Room r,int x,int z){int m=strip(r,x,z);return (m==7||m==25)&&!far(r,x,z);}
    public static boolean pod(Layout.Room r,int x,int z){return has(r,SPORES)&&prop(r,x,z)&&!safe(r,x+.5,z+.5);}

    // ---------------------------------------------------------------- the marks themselves
    /** A floor mark or 0. Every mark is a full, opaque block, so footing and spawning are unchanged. */
    public static int floor(Layout.Room r,int x,int z){
        if(!open(r,x,z))return 0;
        // Rune channels lie on blade tracks: drawn first, so a room with both still shows its runes.
        if(has(r,RUNES)&&rune(x,z))return data(159,15);
        for(Type t:r.hazards)switch(t){
            case FLAME_VENTS: if(vent(x,z))return 87; break;                  // netherrack grates
            case MIASMA: if(seep(x,z))return data(159,5); break;              // lime seep stones
            case SPIKE_RUNES: if(rune(x,z))return data(159,15); break;        // black rune channels
            case FROST_GUSTS: if(drift(z))return 174; break;                  // packed-ice drifts
            case PHANTOM_BLADES: if(blade(x,z))return data(159,0); break;     // pale blade tracks
            case SHOCKWAVE: if(ring(r,x,z))return data(159,9); break;         // cyan tremor rings
            case GRAVITY_WELL: if(sigil(x,z))return data(159,11); break;      // blue well sigils
            case CREEPING_DARK: if(stain(r,x,z))return data(159,15); break;   // black stains
            default: break;
        }
        return 0;
    }
    /** A ceiling mark or 0, all full blocks. */
    public static int roof(Layout.Room r,int x,int z){
        int rx=x-r.x,rz=z-r.z;if(rx<2||rz<2||rx>r.w-3||rz>r.d-3||safe(r,x+.5,z+.5))return 0;
        for(Type t:r.hazards)switch(t){
            case FALLING_MASONRY: if(crack(x,z))return data(98,2); break;     // cracked bricks overhead
            case SMITE: if(lens(x,z))return 95; break;                         // white storm lenses
            case POTION_RAIN: if(spout(x,z))return data(95,10); break;        // purple drip panes
            case CREEPING_DARK: if(Math.floorMod(x,5)==0&&Math.floorMod(z,5)==2)return data(159,15); break;
            default: break;
        }
        return 0;
    }
    /** A mark on the inner wall face or 0: dart slits at eye level, ember sockets just above. */
    public static int wall(Layout.Room r,int x,int y,int z){
        if(y==66&&has(r,DARTS)&&slit(r,x,z))return data(98,3);
        if(y==67&&has(r,EMBERS)&&socket(r,x,z))return 215;
        return 0;
    }
}
