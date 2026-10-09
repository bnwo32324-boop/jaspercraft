package chat.jaspr.dungeon;

/**
 * Pure, order-independent plan. 128-block macroparcels partition on a 32-block door grid.
 *
 * Generation 7: a layout belongs to a world slot (see Floors): 0 is Floor I, 1..3 its rift pockets, 4 Floor II and 5 Floor
 * III. Floor I and the rifts are planned here exactly as generation 6 planned them (Floor I now draws from more themes and
 * grows harder with distance); Floors II and III plan their own partitions and interiors (FloorTwo, FloorThree). On every
 * floor one whole parcel at distance 3 holds the floor's Descent (or, on Floor III, the Throne).
 */
public final class Layout {
    public static final int TILE=32, PARCEL=128, FLOOR=64, MOTIF_COUNT=48;
    /** DESCENT: a floor's guardian arena and its way down; THRONE: Floor III's final arena. */
    public enum Kind { REFUGE, BATTLE, TREASURE, SHRINE, GAUNTLET, BOSS, DESCENT, THRONE }
    public static final String[] THEMES={
        // Floor I, generation 6 (0..35)
        "Weeping Cellar","Ossuary","Drowned Confessional","Cinder Chapel","Hollow Choir","Rotten Nursery",
        "Ashen Archive","Vermilion Court","Frozen Sacristy","Rootbound Crypt","Clockwork Penance","Plague Infirmary",
        "Mirror Tribunal","Salt Cathedral","Sunken Reliquary","Gallows Refectory","Starless Observatory","Thorn Sanctuary",
        "Wax Sepulchre","Sanguine Cloister","Shattered Basilica","Fungal Hospice","Iron Inquisition","Velvet Catacomb",
        "Amber Baptistry","Silent Belfry","Carrion Conservatory","Opaline Sepulcher","Sunless Foundry","Pale Menagerie",
        "Flooded Scriptorium","Obsidian Vestry","Gilded Pauperhouse","Astral Chancel","Mourning Labyrinth","Last Absolution",
        // Floor I, generation 7 (36..53)
        "Bellfounder's Crypt","Moth Sanctum","Candlewright Hall","Chained Library","Penitent Bathhouse","Hall of Effigies",
        "Grave Market","Weeping Orchard","Rusted Reliquary","Lamplighter's Rest","Ashen Kitchens","Mute Theatre",
        "Gutter Abbey","Thurible Gallery","Hanging Gardens of Mercy","Pilgrim's Hostel","Sexton's Workshop","The Unlit Nave",
        // Floor II, The Underworks (54..77)
        "Magma Galleries","Stalactite Cathedral","Fungal Grotto","Drowned Mine","Crystal Hollow","Spider Warrens",
        "Bone Pit","Forgotten Mineshaft","Lava Falls","Silverfish Labyrinth","Gunpowder Depot","Rail Junction",
        "Sulfur Springs","Obsidian Quarry","Glowworm Caves","Collapsed Forge","Underground River","Rust Cavern",
        "Geode Chamber","Hollow Roots","Smugglers' Tunnels","Pillar Caves","Ember Burrows","The Deep Workings",
        // Floor III, The Abyssal Citadel (78..101)
        "Hellforge Bridges","Throne Approach","Bleeding Ramparts","Void Gardens","Obsidian Spire","Soulfire Chapel",
        "Chain Hall","Furnace of Souls","Shattered Sky Halls","Bone Colosseum","The Gallows Keep","Lava Sea Docks",
        "Ender Archive","Crimson Barracks","Doom Foundry","Hall of Mirrors","Ashen Throne Room","The Abyss Gate",
        "Pyre of Kings","Starfall Observatory","Wraith Catacombs","Molten Reliquary","Cursed Treasury","The Last Bastion"
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
    /** World slot (Floors): 0 Floor I, 1..3 Floor I's rifts, 4 Floor II, 5 Floor III. */
    public final int realm;
    /** 1, 2 or 3. */
    public final int floor;
    public Layout(long seed){this(seed,0);}
    public Layout(long seed,int realm){
        if(realm<0||realm>=Floors.SLOTS)throw new IllegalArgumentException("Dungeon world slot must be 0.."+(Floors.SLOTS-1));
        this.seed=seed;this.realm=realm;this.floor=Floors.floor(realm);
    }
    public static long mix(long n){n=(n^(n>>>30))*0xbf58476d1ce4e5b9L;n=(n^(n>>>27))*0x94d049bb133111ebL;return n^(n>>>31);}
    public long hash(int x,int z,long salt){return mix(seed ^ ((long)x*0x632be59bd9b4e019L) ^ ((long)z*0x9e3779b97f4a7c15L) ^ salt ^ ((long)realm*0xd6e8feb86659fd93L));}
    /** The parcel of this floor's Descent (Floors I and II) or Throne (Floor III); rifts have none (null). */
    public int[] finaleParcel(){return Floors.floorSlot(realm)?Floors.descentParcel(seed,floor):null;}
    public Room at(int x,int z){
        int tx=Math.floorDiv(x,TILE),tz=Math.floorDiv(z,TILE),px=Math.floorDiv(tx,4),pz=Math.floorDiv(tz,4);
        // Generation 7: the floor's Descent or Throne fills a whole parcel on the ring at distance 3.
        if(Floors.floorSlot(realm)&&Floors.descentParcel(seed,floor,px,pz))return finale(px,pz);
        if(floor==2)return FloorTwo.at(this,x,z);
        if(floor==3)return FloorThree.at(this,x,z);
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
        long h=hash(ax,az,19);int theme=realm==0?(int)Math.floorMod(h,Floors.floorOneThemes())
            :REALM_THEMES[realm][(int)Math.floorMod(h,REALM_THEMES[realm].length)];
        int roll=(int)Math.floorMod(h>>>8,100);
        Kind kind=roll<8?Kind.TREASURE:roll<15?Kind.SHRINE:roll<28?Kind.GAUNTLET:Kind.BATTLE;
        if(w*d>=4 && roll<65)kind=Kind.BOSS;
        // The four small rooms in the arrival parcel are a quiet buffer around the return gate.
        if(refuge)kind=Kind.REFUGE;
        // Larger rooms and long journeys are harder; threat and encounter slots stay bounded. Generation 7: the way to the
        // Descent climbs one threat step per parcel (Floor I) instead of one per eighteen tiles.
        int distance=realm==0?(int)Math.floor(3*Floors.depth(Math.floorDiv(ax+w/2,4),Math.floorDiv(az+d/2,4))):(int)Math.min(2,(Math.abs((long)ax)+Math.abs((long)az))/18);
        int tier=kind==Kind.REFUGE?0:Math.min(5,1+(w*d>=9?3:w*d>=4?2:w*d>=2?1:0)+distance
            +(kind==Kind.GAUNTLET?1:0)+Math.min(2,realm));
        return new Room(ax*TILE,az*TILE,w*TILE,d*TILE,theme,(int)Math.floorMod(h>>>16,MOTIF_COUNT),tier,kind,h,floor);
    }
    /** The Descent (or Throne): the whole parcel, its theme one of the floor's own, the highest threat. */
    Room finale(int px,int pz){
        long h=hash(px*4,pz*4,0x46696e616c65L);
        int theme=Floors.themeBase(floor)+(int)Math.floorMod(h,Floors.themeCount(floor));
        return new Room(px*PARCEL,pz*PARCEL,PARCEL,PARCEL,theme,(int)Math.floorMod(h>>>16,MOTIF_COUNT),5,floor==3?Kind.THRONE:Kind.DESCENT,h,floor);
    }
    public static final class Room {
        public final int x,z,w,d,theme,motif,tier; public final Kind kind;public final long hash;
        /** 1, 2 or 3 (Floors). */
        public final int floor;
        /** This room's own dangers (HazardCatalog): none in many rooms, one where a room is trapped, two where the gauntlet runs. */
        public final HazardCatalog.Type[] hazards;
        Room(int x,int z,int w,int d,int theme,int motif,int tier,Kind kind,long hash){this(x,z,w,d,theme,motif,tier,kind,hash,1);}
        Room(int x,int z,int w,int d,int theme,int motif,int tier,Kind kind,long hash,int floor){this.x=x;this.z=z;this.w=w;this.d=d;this.theme=theme;this.motif=motif;this.tier=tier;this.kind=kind;this.hash=hash;this.floor=floor;hazards=HazardCatalog.of(this);}
        public String id(){return x+"_"+z;}
        public int cx(){return x+w/2;} public int cz(){return z+d/2;}
        /** Floor I keeps generation 6's ceilings; Floors II and III have tall caverns and halls. */
        public int roof(){int area=w*d;if(floor>1)return area>=16384?95:area>=8192?92:area>=4096?88:area>=2048?84:79;return area>=8192?83:area>=4096?81:area>=2048?77:73;}
        public boolean contains(double bx,double bz){return bx>=x&&bx<x+w&&bz>=z&&bz<z+d;}
        public boolean inner(double bx,double bz){return bx>=x+2&&bx<x+w-2&&bz>=z+2&&bz<z+d-2;}
        public boolean door(int bx,int bz){return ((bx==x||bx==x+w-1)&&lane(bz))||((bz==z||bz==z+d-1)&&lane(bx));}
        public static boolean lane(int n){int v=Math.floorMod(n,32);return v>=15&&v<=17;}
        public boolean clearLane(int bx,int bz){int a=Math.floorMod(bx,32),b=Math.floorMod(bz,32);return (a>=13&&a<=19)||(b>=13&&b<=19)||Math.abs(bx-cx())<=3||Math.abs(bz-cz())<=3;}
        /** A room with a boss in slot 0: boss chambers, the Descent's guardian and the Throne. */
        public boolean bossRoom(){return kind==Kind.BOSS||kind==Kind.DESCENT||kind==Kind.THRONE;}
        /** The Descent or the Throne. */
        public boolean finale(){return kind==Kind.DESCENT||kind==Kind.THRONE;}
        // ---------------------------------------------------------------- population (owner 2026-10-05): the bigger the room, the more mobs
        /** Generation 7: up to 60 slots (the room journal's killed set is a long). */
        public static final int MIN_MOBS=3,MAX_MOBS=60;
        /** The room's area in 32 x 32 tiles: 1 to 16. */
        public int tiles(){return Math.max(1,w*d/(TILE*TILE));}
        /**
         * How many mobs live here. The count grows with the room's area, its threat and its floor, never falls below
         * {@value #MIN_MOBS} (a boss always has an escort; no room holds a lone mob) and stops at {@value #MAX_MOBS}, which
         * is as many as the room journal and the spawn stations hold. Refuges are empty. Treasure rooms and shrines count
         * their dormant guardians; a boss room counts the boss (slot 0) and its escorts.
         */
        public int mobCount(){
            if(kind==Kind.REFUGE)return 0;
            int t=tiles(),n;
            switch(kind){
                case TREASURE:n=3+tier+Math.round(1.0f*t);break;
                case SHRINE:n=3+tier/2+Math.round(.75f*t);break;
                case GAUNTLET:n=5+tier+Math.round(2.0f*t);break;
                case BOSS:n=3+tier/2+t;break;
                case DESCENT:case THRONE:n=10+2*floor+t/2;break;
                default:n=2+tier+Math.round(1.6f*t);
            }
            return Math.max(MIN_MOBS,Math.min(MAX_MOBS,n+Floors.extraMobs(floor)));
        }
        /** In a boss room slot 0 is the boss; every other slot is an escort from the theme's ordinary pool. */
        public boolean bossSlot(int slot){return bossRoom()&&slot==0;}
        /** The cell a slot's mob stands in (any slot number is valid: later slots are the fallbacks for an obstructed cell). */
        public int spawnX(int slot){return x+Stations.at(w,d,bossRoom(),slot)[0];}
        public int spawnZ(int slot){return z+Stations.at(w,d,bossRoom(),slot)[1];}

        /**
         * Spawn stations. Mobs stand on the room's clear lanes (the corridors the generator never furnishes), one cell in
         * from the lane edge, ten cells from the walls (a doorway is never an ambush), clear of the reliquary chest. Within a room the stations are ranked so
         * that every prefix is as spread out as it can be: the first n mobs of any room stand as far apart as the lanes allow,
         * so a bigger room's extra mobs fill the whole hall instead of crowding its middle. A boss room's boss stands on the
         * middle lane six cells north of the centre, and no escort stands within nine cells of it.
         */
        static final class Stations {
            static final int COUNT=MAX_MOBS+14,MARGIN=10;
            private static final java.util.Map<Integer,int[][]> CACHE=new java.util.concurrent.ConcurrentHashMap<Integer,int[][]>();
            static int[] boss(int w,int d){return new int[]{w/2,d/2-6};}
            /** Cell of the slot, relative to the room's corner. */
            static int[] at(int w,int d,boolean bossRoom,int slot){
                if(bossRoom&&slot==0)return boss(w,d);
                int[][] order=order(w,d,bossRoom);
                return order[Math.floorMod(bossRoom?slot-1:slot,order.length)];
            }
            static int[][] order(int w,int d,boolean bossRoom){
                return CACHE.computeIfAbsent(w*4099+d*2+(bossRoom?1:0),k->build(w,d,bossRoom));
            }
            /** A cell on a lane core: the middle five cells of a tile lane, or the middle five of the centre cross. */
            static boolean core(int w,int d,int x,int z){
                int a=x&31,b=z&31;
                return (a>=14&&a<=18)||Math.abs(x-w/2)<=2||(b>=14&&b<=18)||Math.abs(z-d/2)<=2;
            }
            private static int[][] build(int w,int d,boolean bossRoom){
                java.util.List<int[]> pool=new java.util.ArrayList<int[]>();
                int[] boss=boss(w,d);
                for(int x=MARGIN;x<=w-MARGIN-1;x+=2)for(int z=MARGIN;z<=d-MARGIN-1;z+=2){
                    if(!core(w,d,x,z))continue;
                    // The reliquary chest stands at (w/2, d/2+4); nothing spawns within two cells of it.
                    if(Math.abs(x-w/2)<=2&&Math.abs(z-(d/2+4))<=2)continue;
                    if(bossRoom){int dx=x-boss[0],dz=z-boss[1];if(dx*dx+dz*dz<81)continue;}
                    pool.add(new int[]{x,z});
                }
                int n=Math.min(COUNT,pool.size());
                int[][] out=new int[n][];
                long[] best=new long[pool.size()];
                // Greedy farthest-point order, seeded by the boss or by the cell nearest the old first spawn (centre, six north).
                int[] seed=bossRoom?boss:new int[]{w/2,d/2-6};
                for(int i=0;i<pool.size();i++){int[] p=pool.get(i);long dx=p[0]-seed[0],dz=p[1]-seed[1];best[i]=dx*dx+dz*dz;}
                boolean[] used=new boolean[pool.size()];
                for(int k=0;k<n;k++){
                    int pick=-1;
                    for(int i=0;i<pool.size();i++){
                        if(used[i])continue;
                        // Non-boss rooms start at the cell nearest the seed; later picks maximise the distance to every chosen cell.
                        if(pick<0||(k==0&&!bossRoom?best[i]<best[pick]:best[i]>best[pick]))pick=i;
                    }
                    used[pick]=true;out[k]=pool.get(pick);
                    for(int i=0;i<pool.size();i++){
                        if(used[i])continue;
                        long dx=pool.get(i)[0]-out[k][0],dz=pool.get(i)[1]-out[k][1],dd=dx*dx+dz*dz;
                        if(k==0&&!bossRoom)best[i]=dd;else if(dd<best[i])best[i]=dd;
                    }
                }
                return out;
            }
        }
        public boolean dormant(){return kind==Kind.TREASURE||kind==Kind.SHRINE;}
        public String title(){
            if(kind==Kind.REFUGE)return floor==1?"The Last Candle":"The Last Candle of Floor "+Floors.numeral(floor);
            if(kind==Kind.DESCENT)return "The Descent of "+Floors.title(floor)+" - "+THEMES[theme];
            if(kind==Kind.THRONE)return "The Throne of the Abyss - "+THEMES[theme];
            return THEMES[theme]+" - "+kind.name().toLowerCase(java.util.Locale.ROOT);
        }
    }
}
