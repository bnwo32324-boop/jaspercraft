package chat.jaspr.dungeon;

/** Pure, order-independent plan. 128-block macroparcels partition on a 32-block door grid. */
public final class Layout {
    public static final int TILE=32, PARCEL=128, FLOOR=64, MOTIF_COUNT=48;
    public enum Kind { REFUGE, BATTLE, TREASURE, SHRINE, GAUNTLET, BOSS }
    public static final String[] THEMES={
        "Weeping Cellar","Ossuary","Drowned Confessional","Cinder Chapel","Hollow Choir","Rotten Nursery",
        "Ashen Archive","Vermilion Court","Frozen Sacristy","Rootbound Crypt","Clockwork Penance","Plague Infirmary",
        "Mirror Tribunal","Salt Cathedral","Sunken Reliquary","Gallows Refectory","Starless Observatory","Thorn Sanctuary",
        "Wax Sepulchre","Sanguine Cloister","Shattered Basilica","Fungal Hospice","Iron Inquisition","Velvet Catacomb",
        "Amber Baptistry","Silent Belfry","Carrion Conservatory","Opaline Sepulcher","Sunless Foundry","Pale Menagerie",
        "Flooded Scriptorium","Obsidian Vestry","Gilded Pauperhouse","Astral Chancel","Mourning Labyrinth","Last Absolution"
    };
    public static final String[] MOTIFS={
        "Vaulted Piers","Paired Sarcophagi","Confession Archive","Sealed Cistern","Broken Pews","Open Penitent Cage",
        "Webbed Cradles","Bone Votive Altar","Raised Aqueduct","Timber Confessional","Processional Arch","Hanging Bell",
        "Mirror Screens","Salt Font","Root Effigy","Pendulum Dial","Infirmary Cots","Judgment Dais",
        "Glass Reliquary","Gallows Frame","Refectory Benches","Stone Orrery","Thorn Bower","Ossuary Spiral",
        "Wax Candle Grove","Cloister Arcade","Broken Buttress","Fungal Umbrella","Inquisitor Rack","Velvet Canopy",
        "Twin Baptism Wells","Silent Organ","Carrion Perches","Opaline Triptych","Foundry Crucible","Menagerie Pens",
        "Scribe Desks","Vestry Wardrobes","Pauper Stair","Astral Crosswheel","Mourning Switchback","Absolution Crown",
        "Ribbed Tunnel","Split Obelisk","Suspended Lantern","Sunken Mosaic","Forked Root Shrine","Fourfold Tribunal"
    };
    // Realm 1: fire/inquisition; 2: flooded growth; 3: pale/astral. Indices remain canonical.
    private static final int[][] REALM_THEMES={
        {},{3,7,10,19,22,28,31,32},{2,9,11,14,21,24,26,30},{4,8,12,16,18,20,23,25,27,29,33,34,35}
    };
    public final long seed;
    public final int realm;
    public Layout(long seed){this(seed,0);}
    public Layout(long seed,int realm){
        if(realm<0||realm>3)throw new IllegalArgumentException("Dungeon realm must be 0..3");
        this.seed=seed;this.realm=realm;
    }
    public static long mix(long n){n=(n^(n>>>30))*0xbf58476d1ce4e5b9L;n=(n^(n>>>27))*0x94d049bb133111ebL;return n^(n>>>31);}
    public long hash(int x,int z,long salt){return mix(seed ^ ((long)x*0x632be59bd9b4e019L) ^ ((long)z*0x9e3779b97f4a7c15L) ^ salt ^ ((long)realm*0xd6e8feb86659fd93L));}
    public Room at(int x,int z){
        int tx=Math.floorDiv(x,TILE),tz=Math.floorDiv(z,TILE),px=Math.floorDiv(tx,4),pz=Math.floorDiv(tz,4);
        int lx=Math.floorMod(tx,4),lz=Math.floorMod(tz,4),ax=tx,az=tz,w=1,d=1;
        boolean refuge=px==0&&pz==0&&lx<2&&lz<2;
        if(px==0&&pz==0){
            // Only [0,64) x [0,64) is sanctuary; the other quadrants are ordinary rooms.
            if(!refuge){ax=px*4+(lx/2)*2;az=pz*4+(lz/2)*2;w=d=2;}
        }else{
            int mode=realm==0?(int)Math.floorMod(hash(px,pz,7),10)
                :realm==1?1+(int)Math.floorMod(hash(px,pz,7),9):2+(int)Math.floorMod(hash(px,pz,7),8);
            if(mode==1){ax=px*4+(lx/2)*2;az=pz*4+(lz/2)*2;w=d=2;}
            else if(mode==2){ax=px*4;az=pz*4;w=d=4;}
            else if(mode>=3){
                // Cartesian partitions: full-span strips, asymmetric quadrants, and half parcels.
                int cutX=mode==3||mode==5?1:mode==6||mode==8?2:mode==7?3:4;
                int cutZ=mode==4||mode==5?1:mode==6?3:mode==7||mode==9?2:4;
                ax=px*4+(lx<cutX?0:cutX);w=lx<cutX?cutX:4-cutX;
                az=pz*4+(lz<cutZ?0:cutZ);d=lz<cutZ?cutZ:4-cutZ;
            }
        }
        long h=hash(ax,az,19);int theme=realm==0?(int)Math.floorMod(h,THEMES.length)
            :REALM_THEMES[realm][(int)Math.floorMod(h,REALM_THEMES[realm].length)];
        int roll=(int)Math.floorMod(h>>>8,100);
        Kind kind=roll<8?Kind.TREASURE:roll<15?Kind.SHRINE:roll<28?Kind.GAUNTLET:Kind.BATTLE;
        if(w*d>=4 && roll<65)kind=Kind.BOSS;
        // The four small rooms in the arrival parcel are a quiet buffer around the return gate.
        if(refuge)kind=Kind.REFUGE;
        // Larger rooms and long journeys are harder; threat and encounter slots stay bounded.
        int distance=(int)Math.min(2,(Math.abs((long)ax)+Math.abs((long)az))/18);
        int tier=kind==Kind.REFUGE?0:Math.min(5,1+(w*d>=9?3:w*d>=4?2:w*d>=2?1:0)+distance
            +(kind==Kind.GAUNTLET?1:0)+Math.min(2,realm));
        return new Room(ax*TILE,az*TILE,w*TILE,d*TILE,theme,(int)Math.floorMod(h>>>16,MOTIF_COUNT),tier,kind,h);
    }
    public static final class Room {
        public final int x,z,w,d,theme,motif,tier; public final Kind kind;public final long hash;
        /** This room's own dangers (HazardCatalog): none in many rooms, one where a room is trapped, two where the gauntlet runs. */
        public final HazardCatalog.Type[] hazards;
        Room(int x,int z,int w,int d,int theme,int motif,int tier,Kind kind,long hash){this.x=x;this.z=z;this.w=w;this.d=d;this.theme=theme;this.motif=motif;this.tier=tier;this.kind=kind;this.hash=hash;hazards=HazardCatalog.of(this);}
        public String id(){return x+"_"+z;}
        public int cx(){return x+w/2;} public int cz(){return z+d/2;}
        public int roof(){int area=w*d;return area>=8192?83:area>=4096?81:area>=2048?77:73;}
        public boolean contains(double bx,double bz){return bx>=x&&bx<x+w&&bz>=z&&bz<z+d;}
        public boolean inner(double bx,double bz){return bx>=x+2&&bx<x+w-2&&bz>=z+2&&bz<z+d-2;}
        public boolean door(int bx,int bz){return ((bx==x||bx==x+w-1)&&lane(bz))||((bz==z||bz==z+d-1)&&lane(bx));}
        public static boolean lane(int n){int v=Math.floorMod(n,32);return v>=15&&v<=17;}
        public boolean clearLane(int bx,int bz){int a=Math.floorMod(bx,32),b=Math.floorMod(bz,32);return (a>=13&&a<=19)||(b>=13&&b<=19)||Math.abs(bx-cx())<=3||Math.abs(bz-cz())<=3;}
        // Keep the reward at (cx,cz+4); the former spawn at that point intersected its chest.
        private static final int[][] SPAWNS={{0,-6},{0,6},{-6,0},{6,0},{0,-10},{0,10},{-10,0},{10,0},{-4,0},{4,0},{0,-4},{0,8},{-8,0},{8,0}};
        public int spawnX(int slot){return cx()+SPAWNS[Math.floorMod(slot,SPAWNS.length)][0];}
        public int spawnZ(int slot){return cz()+SPAWNS[Math.floorMod(slot,SPAWNS.length)][1];}
        /** Treasure rooms and shrines are guarded too: their guardians wake when someone opens the chest. */
        public int mobCount(){
            if(kind==Kind.REFUGE)return 0;
            if(kind==Kind.TREASURE)return Math.min(14,3+tier+(w*d>=2048?1:0));
            if(kind==Kind.SHRINE)return Math.min(14,2+tier/2);
            return kind==Kind.BOSS?1:Math.min(14,2+tier+w*d/1024+(kind==Kind.GAUNTLET?3:0));
        }
        public boolean dormant(){return kind==Kind.TREASURE||kind==Kind.SHRINE;}
        public String title(){return kind==Kind.REFUGE?"The Last Candle":THEMES[theme]+" - "+kind.name().toLowerCase(java.util.Locale.ROOT);}
    }
}
