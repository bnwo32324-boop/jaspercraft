package chat.jaspr.biomes;

import java.util.*;
import org.bukkit.generator.ChunkGenerator;

/** Pure regional planning. No Bukkit World, chunk reads, population, or mutable site state. */
public final class StructurePlanner {
    public static final int REGION=1024;
    public static final int MIN_ANCHOR_SPACING=768;
    public static final int CACHE_LIMIT=192;
    /** Additional discoveries use their own grid; the v1 anchors and journal keys stay unchanged. */
    public static final int EXPANSION_REGION=384;
    public static final int EXPANSION_MIN_SPACING=352;
    /** Retain 46% of each previous generator's candidate cells. 0.10 from sparse-v5 until 3.21.0, then
     * raised 1.5x on request, then 0.15 -> 0.20 (3.23 structure audit, owner decision) to compensate for the
     * catalogue sites that now yield to register set pieces, then 0.20 -> 0.46 (3.25.0, owner: 1.5x). It is a
     * threshold on a fixed per-cell random, so raising it only adds cells: nothing that was already placed
     * moves. A cell under the previous threshold (StructureRates.PREVIOUS_DENSITY) is tier 0 and is admitted by
     * exactly the 3.24 rules; a tier-1 cell also yields to everything that was placed without knowing about it
     * (older lattice rooms above all: a site's envelope spans dozens of chunks), so only ~38% of tier-1 cells are
     * built and 0.30 gave 1.19x; 0.46 gives the 1.5x per site that was asked for (rates probe, SA/work/r-rates). */
    public static final double RELATIVE_STRUCTURE_DENSITY=0.46;
    /** Keep expedition architecture out of the opening survival area. */
    public static final int SPAWN_EXCLUSION_RADIUS=3072;
    /** Three quarters of expansion cells are reserved for unmistakable above-ground landmarks. */
    public static final int SURFACE_LANDMARK_TIER=3;
    static final int MARGIN=4, APPROACH=96;
    private static final Map<RegionKey,Optional<Site>> CACHE=new LinkedHashMap<RegionKey,Optional<Site>>(256,.75f,true) {
        protected boolean removeEldestEntry(Map.Entry<RegionKey,Optional<Site>> e) { return size()>CACHE_LIMIT; }
    };
    private static final Map<RegionKey,Optional<Site>> EXPANSION_CACHE=new LinkedHashMap<RegionKey,Optional<Site>>(256,.75f,true) {
        protected boolean removeEldestEntry(Map.Entry<RegionKey,Optional<Site>> e) { return size()>CACHE_LIMIT; }
    };
    /** Identification results (admit=false) are cached apart from placement results (3.20.0). */
    private static final Map<RegionKey,Optional<Site>> IDENTIFY_CACHE=new LinkedHashMap<RegionKey,Optional<Site>>(256,.75f,true) {
        protected boolean removeEldestEntry(Map.Entry<RegionKey,Optional<Site>> e) { return size()>CACHE_LIMIT; }
    };
    private static final Map<RegionKey,Optional<Site>> IDENTIFY_EXPANSION_CACHE=new LinkedHashMap<RegionKey,Optional<Site>>(256,.75f,true) {
        protected boolean removeEldestEntry(Map.Entry<RegionKey,Optional<Site>> e) { return size()>CACHE_LIMIT; }
    };
    private StructurePlanner() {}

    public static final class Marker {
        public final int x,y,z,ordinal;
        public final String kind;
        private Marker(int x,int y,int z,String kind,int ordinal) {
            this.x=x; this.y=y; this.z=z; this.kind=kind; this.ordinal=ordinal;
        }
        /** A marker inferred from a block that no planned marker matches (StructureLoot's pedestal rule).
         * The ordinal is derived from the position, so the same block always yields the same marker. */
        public static Marker reconstructed(int x,int y,int z,String kind) {
            return new Marker(x,y,z,kind,Math.abs((int)Terrain.mix(x*341873128712L+y*132897987541L+z))%4096);
        }
    }
    static final class Room {
        final int col,row,floor; final char type;
        Room(int col,int row,int floor,char type) { this.col=col;this.row=row;this.floor=floor;this.type=type; }
    }
    public static final class Site {
        public final String key;
        public final StructureCatalog.Design design;
        /** Reserved bounds, including approach. y is the entrance's walking-floor block. */
        public final int x,y,z,width,depth;
        public final int anchorX,anchorZ;
        final long seed;
        final List<Room> rooms;
        final int[] floors;
        final int entranceCol,entranceRow;
        final int[] approach;
        private final List<Marker> markers;
        /** 0: a cell under the 3.24 density (placed by the 3.24 rules); 1: added by 3.25.0 (StructureRates). */
        int tier;

        private Site(long seed,StructureCatalog.Design design,int x,int z,String key,int anchorX,int anchorZ) {
            this.seed=seed;this.design=design;this.x=x;this.z=z;this.key=key;this.anchorX=anchorX;this.anchorZ=anchorZ;
            width=design.columns*12+MARGIN*2;depth=design.rows*12+MARGIN*2+APPROACH;
            Terrain terrain=new Terrain(seed); int cols=design.columns,rows=design.rows;
            floors=new int[cols*rows]; Arrays.fill(floors,Integer.MIN_VALUE);
            int ec=0,er=rows-1;
            while(er>0) { boolean any=false;for(int c=0;c<cols;c++) any|=design.room(c,er)!='.';if(any)break;er--; }
            int best=Integer.MAX_VALUE;
            for(int c=0;c<cols;c++) if(design.room(c,er)!='.'&&Math.abs(c-cols/2)<best) {ec=c;best=Math.abs(c-cols/2);}
            entranceCol=ec;entranceRow=er;
            for(int rz=0;rz<rows;rz++) for(int rx=0;rx<cols;rx++) if(design.room(rx,rz)!='.') {
                int h=terrain.sample(x+MARGIN+rx*12+6,z+MARGIN+rz*12+6).y;
                if(design.mode.equals("buried")) h-=12;
                if(design.mode.equals("underwater")) h=Math.min(49,h-6);
                if(design.mode.equals("surface")) h=Math.max(63,h);
                floors[rz*cols+rx]=Math.max(20,Math.min(146,h));
            }
            // A terrain-derived lower envelope: neighboring terraces differ by at most four blocks.
            // Relaxation is over the authored graph, independent of the order chunks are requested.
            boolean changed;
            do { changed=false;
                for(int rz=0;rz<rows;rz++) for(int rx=0;rx<cols;rx++) if(design.room(rx,rz)!='.') {
                    int at=rz*cols+rx;
                    for(int[] d:StructureCatalog.DIRS) if(design.room(rx+d[0],rz+d[1])!='.') {
                        int n=(rz+d[1])*cols+rx+d[0];
                        if(floors[at]>floors[n]+4) {floors[at]=floors[n]+4;changed=true;}
                    }
                }
            } while(changed);
            List<Room> built=new ArrayList<>();
            for(int rz=0;rz<rows;rz++) for(int rx=0;rx<cols;rx++) if(design.room(rx,rz)!='.')
                built.add(new Room(rx,rz,floors[rz*cols+rx],design.room(rx,rz)));
            rooms=Collections.unmodifiableList(built);y=floors[er*cols+ec];
            int[] route=new int[APPROACH];int length=APPROACH,previous=y;
            for(int i=0;i<APPROACH;i++) {
                int wx=entryX(),wz=entryZ()+i+1,ground=terrain.sample(wx,wz).y;
                int target=Math.max(ground,63);
                route[i]=previous+Integer.signum(target-previous);previous=route[i];
                // Keep the final landing away from the facade; stop only at dry natural ground.
                if(i>=8&&previous==ground&&ground>=63&&Math.abs(terrain.sample(wx,wz+1).y-ground)<=1) {length=i+1;break;}
            }
            // The terrain bounds and 96-block reserve guarantee a surface landing, even for buried sites.
            if(length==APPROACH&&route[length-1]!=Math.max(63,terrain.sample(entryX(),entryZ()+length).y))
                throw new IllegalStateException("Approach cannot reach surface: "+key);
            approach=Arrays.copyOf(route,length);
            markers=makeMarkers();
        }
        public List<Marker> markers() {return markers;}
        public int tier() {return tier;}
        public boolean expansion() {return key.startsWith("structures:v3:")||key.startsWith("structures:v4:")||key.startsWith("structures:v5:")||key.startsWith("structures:v6:")||key.startsWith("structures:v7:");}
        public void stamp(ChunkGenerator.ChunkData data,int cx,int cz) {
            if(intersects(cx,cz)) StructureArchitecture.stamp(this,data,cx,cz);
        }
        public boolean intersects(int cx,int cz) {
            long bx=(long)cx*16,bz=(long)cz*16;
            return bx<x+(long)width&&bx+16>x&&bz<z+(long)depth&&bz+16>z;
        }
        int entryX(){return x+MARGIN+entranceCol*12+6;}
        int entryZ(){return z+MARGIN+entranceRow*12+11;}
        /** The ground this site actually builds on, as {x,z,sizeX,sizeZ} boxes (3.23 structure audit): the rooms
         * with their margin, and the five-wide approach as far as it runs. The rest of the 96-block approach
         * reserve is never written, so another structure there is no collision. */
        int[][] envelope(){return new int[][]{{x,z,width,design.rows*12+MARGIN*2},{entryX()-2,entryZ()+1,5,approach.length}};}
        boolean connected(Room room,int dx,int dz) {return design.room(room.col+dx,room.row+dz)!='.';}
        boolean doorway(Room room,int lx,int lz) {
            return lx>=5&&lx<=7&&((lz==0&&connected(room,0,-1))||(lz==11&&(connected(room,0,1)||room.col==entranceCol&&room.row==entranceRow)))
                ||lz>=5&&lz<=7&&((lx==0&&connected(room,-1,0))||(lx==11&&connected(room,1,0)));
        }
        int floor(Room room,int lx,int lz) {
            int f=room.floor;
            if(lz>=5&&lz<=7) {
                if(lx<=2&&connected(room,-1,0)) return terrace(f,floors[room.row*design.columns+room.col-1],lx);
                if(lx>=9&&connected(room,1,0)) return terrace(f,floors[room.row*design.columns+room.col+1],11-lx);
            }
            if(lx>=5&&lx<=7) {
                if(lz<=2&&connected(room,0,-1)) return terrace(f,floors[(room.row-1)*design.columns+room.col],lz);
                if(lz>=9&&connected(room,0,1)) return terrace(f,floors[(room.row+1)*design.columns+room.col],11-lz);
            }
            return f;
        }
        private static int terrace(int a,int b,int distance) {
            int d=Math.floorDiv(a+b,2)-a;
            return a+Integer.signum(d)*Math.max(0,Math.abs(d)-distance);
        }
        private List<Marker> makeMarkers() {
            int cols=design.columns;
            Map<Integer,Integer> distance=new HashMap<>();ArrayDeque<Integer> queue=new ArrayDeque<>();
            int start=entranceRow*cols+entranceCol;distance.put(start,0);queue.add(start);
            while(!queue.isEmpty()) {int n=queue.remove();for(int[] d:StructureCatalog.DIRS) {
                int nx=n%cols+d[0],nz=n/cols+d[1],p=nz*cols+nx;
                if(design.room(nx,nz)!='.'&&!distance.containsKey(p)) {distance.put(p,distance.get(n)+1);queue.add(p);}
            }}
            List<Room> ordered=new ArrayList<>(rooms);
            ordered.sort(Comparator.<Room>comparingInt(r->distance.get(r.row*cols+r.col)).thenComparingInt(r->r.row*cols+r.col));
            // Indoor rooms own containers/encounters; streets, aircraft wings and masts are transit.
            List<Room> indoor=new ArrayList<>();for(Room r:ordered) if(!StructureCatalog.open(r.type)) indoor.add(r);
            if(indoor.isEmpty()) indoor.add(ordered.get(0));
            List<Marker> result=new ArrayList<>();Set<String> occupied=new HashSet<>();
            int count=Math.min(indoor.size(),Math.min(12,2+design.tier*2));
            for(int i=0;i<count;i++) {
                Room r=indoor.get(count==1?0:i*(indoor.size()-1)/(count-1));
                String kind=i==0?"supply":i==1?"medical":i==2?"armory":i==count-1&&design.tier>=3?"vault":i==count-2&&design.tier>=2?"relic":"supply";
                add(result,occupied,r,8,8,StructureCatalog.floors(r.type)-1,kind,false);
            }
            Room deepest=indoor.get(indoor.size()-1);
            if(design.tier>=3) add(result,occupied,deepest,6,6,StructureCatalog.floors(deepest.type)-1,"boss",false);
            int mobs=Math.min(7,design.tier+1),stride=Math.max(1,indoor.size()/mobs);
            for(int i=0,n=0;i<indoor.size()&&n<mobs;i+=stride) {
                Room r=indoor.get(i);if(r==deepest&&design.tier>=3)continue;
                add(result,occupied,r,6,6,0,"mob",false);n++;
            }
            for(Room r:ordered) if(r.type=='P') {add(result,occupied,r,8,5,0,"door",true);break;}
            return Collections.unmodifiableList(result);
        }
        private void add(List<Marker> result,Set<String> occupied,Room room,int lx,int lz,int storey,String kind,boolean floorBlock) {
            int wx=x+MARGIN+room.col*12+lx,wz=z+MARGIN+room.row*12+lz,wy=room.floor+storey*6+(floorBlock?0:1);
            if(occupied.add(wx+":"+wy+":"+wz)) result.add(new Marker(wx,wy,wz,kind,result.size()));
        }
    }
    public static List<Site> sites(long seed,int cx,int cz) {
        int rx=(int)Math.floorDiv((long)cx*16,REGION),rz=(int)Math.floorDiv((long)cz*16,REGION);
        List<Site> result=new ArrayList<>(1);
        for(int a=rx-1;a<=rx+1;a++) for(int b=rz-1;b<=rz+1;b++) {
            Site site=region(seed,a,b);if(site!=null&&site.intersects(cx,cz)) result.add(site);
        }
        int ex=(int)Math.floorDiv((long)cx*16,EXPANSION_REGION),ez=(int)Math.floorDiv((long)cz*16,EXPANSION_REGION);
        for(int a=ex-1;a<=ex+1;a++)for(int b=ez-1;b<=ez+1;b++) {
            Site site=expansionRegion(seed,a,b);if(site!=null&&site.intersects(cx,cz))result.add(site);
        }
        return Collections.unmodifiableList(result);
    }
    public static Site region(long seed,int rx,int rz) {
        RegionKey key=new RegionKey(seed,rx,rz);Optional<Site> known;
        synchronized(CACHE) {known=CACHE.get(key);}if(known!=null)return known.orElse(null);
        Site made=plan(seed,rx,rz);
        synchronized(CACHE) {
            known=CACHE.get(key);if(known!=null)return known.orElse(null);
            CACHE.put(key,Optional.ofNullable(made));
        }
        return made;
    }
    /** Explicit QA placement. x,z are reserved northwest bounds; it does not enter the regional cache. */
    public static Site preview(long seed,StructureCatalog.Design design,int x,int z) {
        Objects.requireNonNull(design,"design");
        return new Site(seed,design,x,z,"preview:v1:"+seed+":"+x+":"+z+":"+design.id,
            x+(design.columns*12+MARGIN*2)/2,z+(design.rows*12+MARGIN*2+APPROACH)/2);
    }
    public static int cachedRegions() { synchronized(CACHE) {return CACHE.size();} }
    public static int cachedExpansionRegions() { synchronized(EXPANSION_CACHE) {return EXPANSION_CACHE.size();} }
    public static Site expansionRegion(long seed,int rx,int rz) {
        RegionKey key=new RegionKey(seed,rx,rz);Optional<Site> known;
        synchronized(EXPANSION_CACHE) {known=EXPANSION_CACHE.get(key);}if(known!=null)return known.orElse(null);
        Site made=planExpansion(seed,rx,rz);
        synchronized(EXPANSION_CACHE) {
            known=EXPANSION_CACHE.get(key);if(known!=null)return known.orElse(null);
            EXPANSION_CACHE.put(key,Optional.ofNullable(made));
        }
        return made;
    }
    private static Site planExpansion(long seed,int rx,int rz) {
        return planExpansion(seed,rx,rz,true);
    }
    /** admit=true plans for generation: every gate applies. admit=false identifies (3.20.0): it skips the
     * gates that decide WHETHER to build here now -- density, the disabled list, spawn exclusion, portal
     * reserve, legacy overlap, register set pieces (3.23 audit) -- and keeps everything that decides WHAT
     * and WHERE, so a site placed under older, denser rules is still recognised. Anything that changes which
     * structures get placed must not change which structures can be recognised. */
    private static Site planExpansion(long seed,int rx,int rz,boolean admit) {
        Terrain terrain=new Terrain(seed);
        // Independent deterministic admission preserves the complete v4 design mix while
        // removing 80% of its candidate frequency in expectation.
        if(admit&&!densityAdmitted(terrain,rx,rz,1909))return null;
        int tier=terrain.random(rx,rz,1909)<StructureRates.PREVIOUS_DENSITY?0:1;
        // Three out of every four expansion cells are reserved for unmistakable surface landmarks.
        // The fourth retains the mixed expedition pool, so underground discovery remains meaningful.
        boolean surfaceLandmark=Math.floorMod(rx+2*rz,4)!=0;
        if(!surfaceLandmark&&terrain.random(rx,rz,1910)>=.94)return null;
        long ax=(long)rx*EXPANSION_REGION+176+(int)(terrain.random(rx,rz,1911)*33);
        long az=(long)rz*EXPANSION_REGION+176+(int)(terrain.random(rx,rz,1912)*33);
        if(ax<Integer.MIN_VALUE+512L||ax>Integer.MAX_VALUE-512L||az<Integer.MIN_VALUE+512L||az>Integer.MAX_VALUE-512L)return null;
        int biome=terrain.sample((int)ax,(int)az).profile.index;
        List<StructureCatalog.Design> choices=StructureCatalog.expansionChoices(biome),pool=new ArrayList<>();
        if(surfaceLandmark) {
            for(StructureCatalog.Design d:choices)if(d.mode.equals("surface")&&d.tier>=SURFACE_LANDMARK_TIER)pool.add(d);
            // Every biome is authored with a major surface entry; retain a defensive themed fallback.
            if(pool.isEmpty())for(StructureCatalog.Design d:choices)if(d.mode.equals("surface"))pool.add(d);
        } else {
            boolean exclusive=terrain.random(rx,rz,1913)<.60;
            for(StructureCatalog.Design d:choices)if(d.exclusive==exclusive)addWeighted(pool,d);
            if(pool.isEmpty())for(StructureCatalog.Design d:choices)addWeighted(pool,d);
        }
        if(pool.isEmpty())return null;
        // Try alternatives when a large site straddles a biome edge. Each retry is deterministic.
        int attempts=surfaceLandmark?Math.min(12,pool.size()):4;
        int first=surfaceLandmark?(int)(terrain.random(rx,rz,1914)*pool.size()):0;
        for(int attempt=0;attempt<attempts;attempt++) {
            StructureCatalog.Design design=surfaceLandmark?pool.get((first+attempt)%pool.size()):pool.get((int)(terrain.random(rx,rz,1914+attempt)*pool.size()));
            // A disabled design leaves the whole cell empty (3.23.0); it is not replaced by another design.
            if(admit&&DisabledStructures.any(design.name,design.id))return null;
            int width=design.columns*12+MARGIN*2,depth=design.rows*12+MARGIN*2+APPROACH;
            if(width>EXPANSION_MIN_SPACING-8||depth>EXPANSION_MIN_SPACING-8)continue;
            int x=(int)ax-width/2,z=(int)az-depth/2;
            if(admit&&intersectsSpawnExclusion(x,z,width,depth))continue;
            boolean reserved=false;
            if(admit)for(int cx=Math.floorDiv(x,16)-2;cx<=Math.floorDiv(x+width-1,16)+2&&!reserved;cx++)
                for(int cz=Math.floorDiv(z,16)-2;cz<=Math.floorDiv(z+depth-1,16)+2;cz++)
                    if(HorrorGenerator.portalChunk(cx,cz)){reserved=true;break;}
            if(admit&&tier==1&&!reserved&&StructureRates.nearSanctuary(terrain,x,z,width,depth))reserved=true;
            int legacyX=(int)Math.floorDiv(ax,REGION),legacyZ=(int)Math.floorDiv(az,REGION);
            if(admit)for(int lx=legacyX-1;lx<=legacyX+1&&!reserved;lx++)for(int lz=legacyZ-1;lz<=legacyZ+1;lz++) {
                // A tier-0 cell asks only tier-0 legacy sites: exactly what region() admitted under 3.24.
                Site old=tier==0?regionTier0(seed,lx,lz):region(seed,lx,lz);
                if(old!=null&&x<old.x+(long)old.width+16&&x+(long)width+16>old.x&&z<old.z+(long)old.depth+16&&z+(long)depth+16>old.z){reserved=true;break;}
            }
            if(reserved)continue;
            int samples=0,suitable=0,wet=0;
            for(int row=0;row<design.rows;row++)for(int col=0;col<design.columns;col++)if(design.room(col,row)!='.')
                for(int dx:new int[]{1,6,10})for(int dz:new int[]{1,6,10}) {
                    Terrain.Sample sample=terrain.sample(x+MARGIN+col*12+dx,z+MARGIN+row*12+dz);
                    samples++;if(design.accepts(sample.profile.index))suitable++;if(sample.y<62)wet++;
                }
            if(suitable*3<samples*2||design.mode.equals("underwater")&&wet*10<samples*9)continue;
            try{Site site=new Site(seed,design,x,z,"structures:v7:"+seed+":"+rx+":"+rz+":"+design.id,(int)ax,(int)az);
                site.tier=tier;
                // Admission only (3.23 structure audit): a register set piece already owns ground this site builds
                // on, and the populator would build it straight through the site. The cell stays empty, as for a
                // disabled design, rather than trying a smaller design round it; identify() skips this.
                if(!admit)return site;
                if(tier==0)return setPiece(terrain,site)?null:site;
                return setPieceAll(terrain,site)||!newGround(terrain,site,false)?null:site;}
            catch(IllegalStateException ex){if(ex.getMessage()==null||!ex.getMessage().startsWith("Approach cannot reach surface"))throw ex;}
        }
        return null;
    }
    /** Every site whose footprint touches this chunk, by identification (3.20.0): what sites() returns now,
     * plus anything placed under older, denser admission rules. For recognition only -- loot, the Fold
     * door, /where, relighting. Generation must keep using sites(). */
    public static List<Site> identify(long seed,int cx,int cz) {
        List<Site> result=new ArrayList<>(sites(seed,cx,cz));
        Set<String> keys=new HashSet<>();for(Site site:result)keys.add(site.key);
        int rx=(int)Math.floorDiv((long)cx*16,REGION),rz=(int)Math.floorDiv((long)cz*16,REGION);
        for(int a=rx-1;a<=rx+1;a++) for(int b=rz-1;b<=rz+1;b++) {
            Site site=identifyRegion(seed,a,b);if(site!=null&&site.intersects(cx,cz)&&keys.add(site.key)) result.add(site);
        }
        int ex=(int)Math.floorDiv((long)cx*16,EXPANSION_REGION),ez=(int)Math.floorDiv((long)cz*16,EXPANSION_REGION);
        for(int a=ex-1;a<=ex+1;a++)for(int b=ez-1;b<=ez+1;b++) {
            Site site=identifyExpansion(seed,a,b);if(site!=null&&site.intersects(cx,cz)&&keys.add(site.key))result.add(site);
        }
        return Collections.unmodifiableList(result);
    }
    public static Site identifyRegion(long seed,int rx,int rz) {
        RegionKey key=new RegionKey(seed,rx,rz);Optional<Site> known;
        synchronized(IDENTIFY_CACHE) {known=IDENTIFY_CACHE.get(key);}if(known!=null)return known.orElse(null);
        Site made;
        try{made=plan(seed,rx,rz,false);}catch(RuntimeException ex){made=null;}
        synchronized(IDENTIFY_CACHE) {IDENTIFY_CACHE.put(key,Optional.ofNullable(made));}
        return made;
    }
    public static Site identifyExpansion(long seed,int rx,int rz) {
        RegionKey key=new RegionKey(seed,rx,rz);Optional<Site> known;
        synchronized(IDENTIFY_EXPANSION_CACHE) {known=IDENTIFY_EXPANSION_CACHE.get(key);}if(known!=null)return known.orElse(null);
        Site made;
        try{made=planExpansion(seed,rx,rz,false);}catch(RuntimeException ex){made=null;}
        synchronized(IDENTIFY_EXPANSION_CACHE) {IDENTIFY_EXPANSION_CACHE.put(key,Optional.ofNullable(made));}
        return made;
    }
    private static void addWeighted(List<StructureCatalog.Design> pool,StructureCatalog.Design design) {
        int weight=design.tier<=2?5:design.tier==3?3:design.tier==4?2:1;
        for(int i=0;i<weight;i++)pool.add(design);
    }
    private static Site plan(long seed,int rx,int rz) {
        return plan(seed,rx,rz,true);
    }
    /** admit as in planExpansion: false identifies without the placement gates (3.20.0). */
    private static Site plan(long seed,int rx,int rz,boolean admit) {
        Terrain terrain=new Terrain(seed);
        // The same relative gate applies to the original 1,024-block structure tier.
        if(admit&&!densityAdmitted(terrain,rx,rz,909))return null;
        int tier=terrain.random(rx,rz,909)<StructureRates.PREVIOUS_DENSITY?0:1;
        if(terrain.random(rx,rz,910)>.72) return null;
        long ax=(long)rx*REGION+384+(int)(terrain.random(rx,rz,911)*257);
        long az=(long)rz*REGION+384+(int)(terrain.random(rx,rz,912)*257);
        if(ax<Integer.MIN_VALUE+512L||ax>Integer.MAX_VALUE-512L||az<Integer.MIN_VALUE+512L||az>Integer.MAX_VALUE-512L)return null;
        int biome=terrain.sample((int)ax,(int)az).profile.index;
        List<StructureCatalog.Design> choices=StructureCatalog.legacyChoices(biome),pool=new ArrayList<>();
        boolean exclusive=terrain.random(rx,rz,913)<.55;
        for(StructureCatalog.Design d:choices) if(d.exclusive==exclusive) pool.add(d);
        StructureCatalog.Design design=pool.get((int)(terrain.random(rx,rz,914)*pool.size()));
        // Submerged habitats must have an actual wet regional anchor; surface alternatives remain themed.
        if(design.mode.equals("underwater")&&terrain.sample((int)ax,(int)az).y>=62) {
            pool.clear();for(StructureCatalog.Design d:choices) if(!d.mode.equals("underwater")) pool.add(d);
            if(pool.isEmpty())return null;
            design=pool.get((int)(terrain.random(rx,rz,915)*pool.size()));
        }
        if(admit&&DisabledStructures.any(design.name,design.id))return null;
        int width=design.columns*12+MARGIN*2,depth=design.rows*12+MARGIN*2+APPROACH;
        int x=(int)ax-width/2,z=(int)az-depth/2;
        int suitable=0,wet=0,samples=0;
        for(int row=0;row<design.rows;row++)for(int col=0;col<design.columns;col++)if(design.room(col,row)!='.') {
            for(int dx:new int[]{1,6,10})for(int dz:new int[]{1,6,10}) {
                Terrain.Sample sample=terrain.sample(x+MARGIN+col*12+dx,z+MARGIN+row*12+dz);
                samples++;if(design.accepts(sample.profile.index))suitable++;if(sample.y<62)wet++;
            }
        }
        // The entrance reserve shifts the building north of the anchor. Check the actual room area.
        if(suitable*3<samples*2||design.mode.equals("underwater")&&wet*10<samples*9)return null;
        // Reserve the whole envelope, including access. Never intersect spawn or portal sanctuary halos.
        if(admit&&intersectsSpawnExclusion(x,z,width,depth))return null;
        if(admit)for(int cx=Math.floorDiv(x,16)-2;cx<=Math.floorDiv(x+width-1,16)+2;cx++)
            for(int cz=Math.floorDiv(z,16)-2;cz<=Math.floorDiv(z+depth-1,16)+2;cz++)
                if(HorrorGenerator.portalChunk(cx,cz))return null;
        if(admit&&tier==1&&StructureRates.nearSanctuary(terrain,x,z,width,depth))return null;
        Site site;
        // 3.25.0 (census): a legacy site whose approach cannot reach the surface (e.g. region 229,242) threw out of
        // region() and so out of chunk generation and every lookup near it. It is simply not admitted now -- the
        // expansion planner has always done this -- and identify() no longer throws either.
        try{site=new Site(seed,design,x,z,"structures:v1:"+seed+":"+rx+":"+rz+":"+design.id,(int)ax,(int)az);}
        catch(IllegalStateException ex){if(ex.getMessage()==null||!ex.getMessage().startsWith("Approach cannot reach surface"))throw ex;return null;}
        site.tier=tier;
        // Admission only (3.23 structure audit): the populator builds register set pieces after this site is
        // stamped, straight through it, so a site whose ground a set piece already owns is left empty.
        if(!admit)return site;
        if(tier==0)return setPiece(terrain,site)?null:site;
        return setPieceAll(terrain,site)||!newGround(terrain,site,true)?null:site;
    }

    /** region(), but only a tier-0 (3.24) site: never plans a tier-1 cell, so tier-0 admission never depends on one. */
    static Site regionTier0(long seed,int rx,int rz) {
        if(new Terrain(seed).random(rx,rz,909)>=StructureRates.PREVIOUS_DENSITY)return null;
        return region(seed,rx,rz);
    }
    /** expansionRegion(), tier 0 only (as regionTier0). */
    static Site expansionTier0(long seed,int rx,int rz) {
        if(new Terrain(seed).random(rx,rz,1909)>=StructureRates.PREVIOUS_DENSITY)return null;
        return expansionRegion(seed,rx,rz);
    }
    /** sites(), tier 0 only: exactly what sites() returned under 3.24. Asked by everything placed before 3.25. */
    static List<Site> sitesTier0(long seed,int cx,int cz) {
        List<Site> result=new ArrayList<>();
        int rx=(int)Math.floorDiv((long)cx*16,REGION),rz=(int)Math.floorDiv((long)cz*16,REGION);
        for(int a=rx-1;a<=rx+1;a++) for(int b=rz-1;b<=rz+1;b++) {
            Site site=regionTier0(seed,a,b);if(site!=null&&site.intersects(cx,cz)) result.add(site);
        }
        int ex=(int)Math.floorDiv((long)cx*16,EXPANSION_REGION),ez=(int)Math.floorDiv((long)cz*16,EXPANSION_REGION);
        for(int a=ex-1;a<=ex+1;a++)for(int b=ez-1;b<=ez+1;b++) {
            Site site=expansionTier0(seed,a,b);if(site!=null&&site.intersects(cx,cz))result.add(site);
        }
        return result;
    }
    /** True when a tier-0 catalogue site builds (Site.envelope) within two blocks of this box, at any height. */
    static boolean catalogueTier0(Terrain t,int x,int z,int sizeX,int sizeZ) {
        for(int cx=(x-2)>>4;cx<=(x+sizeX+1)>>4;cx++)for(int cz=(z-2)>>4;cz<=(z+sizeZ+1)>>4;cz++)
            for(Site s:sitesTier0(t.seed,cx,cz))for(int[] e:s.envelope()) {
                if(e[0]+e[2]+2<=x||x+sizeX+2<=e[0])continue;
                if(e[1]+e[3]+2<=z||z+sizeZ+2<=e[1])continue;
                return true;
            }
        return false;
    }
    /** setPiece() counting the 3.25.0 secondary set pieces too (tier-1 cells only). */
    private static boolean setPieceAll(Terrain terrain,Site site) {
        for(int[] e:site.envelope())if(Megaliths.occupiedAll(terrain,e[0],e[1],e[2],e[3],255))return true;
        return false;
    }
    /**
     * What a tier-1 (3.25.0) cell must also leave alone, because it was placed without knowing about it: chunks
     * that existed before 3.25.0 (StructureRates boundary, the whole reserve as the expansion boundary does),
     * the older lattice rooms, and -- for a legacy cell -- tier-0 expansion sites (a tier-0 expansion site only
     * ever yielded to tier-0 legacy sites; a tier-1 expansion site yields to every legacy site, as before).
     */
    private static boolean newGround(Terrain terrain,Site site,boolean legacy) {
        if(!StructureRates.permits(terrain.seed,site.x,site.z,site.width,site.depth))return false;
        for(int[] e:site.envelope())if(Dungeons.oldRoomNear(terrain,e[0],e[1],e[2],e[3]))return false;
        if(legacy) {
            int ex0=Math.floorDiv(site.x-400,EXPANSION_REGION),ex1=Math.floorDiv(site.x+site.width+16,EXPANSION_REGION);
            int ez0=Math.floorDiv(site.z-400,EXPANSION_REGION),ez1=Math.floorDiv(site.z+site.depth+16,EXPANSION_REGION);
            for(int a=ex0;a<=ex1;a++)for(int b=ez0;b<=ez1;b++) {
                Site e=expansionTier0(terrain.seed,a,b);
                if(e!=null&&site.x<e.x+(long)e.width+16&&site.x+(long)site.width+16>e.x
                    &&site.z<e.z+(long)e.depth+16&&site.z+(long)site.depth+16>e.z)return false;
            }
        }
        return true;
    }
    /** True when a register set piece that is really built (Megaliths.occupied) stands on any ground this site
     * builds on (Site.envelope), at any height: every set piece reaches the surface, the buried ones by their
     * shafts. Admission only; identify() never asks, so a copy already built there is still recognised. */
    private static boolean setPiece(Terrain terrain,Site site) {
        for(int[] e:site.envelope())if(Megaliths.occupied(terrain,e[0],e[1],e[2],e[3],255))return true;
        return false;
    }
    static boolean densityAdmitted(long seed,int rx,int rz,int salt) {
        return densityAdmitted(new Terrain(seed),rx,rz,salt);
    }
    private static boolean densityAdmitted(Terrain terrain,int rx,int rz,int salt) {
        return terrain.random(rx,rz,salt)<RELATIVE_STRUCTURE_DENSITY;
    }
    static boolean intersectsSpawnExclusion(int x,int z,int width,int depth) {
        long right=(long)x+width-1,bottom=(long)z+depth-1;
        long dx=x>0?x:right<0?-right:0,dz=z>0?z:bottom<0?-bottom:0;
        return dx*dx+dz*dz<(long)SPAWN_EXCLUSION_RADIUS*SPAWN_EXCLUSION_RADIUS;
    }
    private static final class RegionKey {
        final long seed;final int x,z;
        RegionKey(long seed,int x,int z){this.seed=seed;this.x=x;this.z=z;}
        public int hashCode(){return (int)Terrain.mix(seed+341873128712L*x+132897987541L*z);}
        public boolean equals(Object o){if(!(o instanceof RegionKey))return false;RegionKey k=(RegionKey)o;return seed==k.seed&&x==k.x&&z==k.z;}
    }
}
