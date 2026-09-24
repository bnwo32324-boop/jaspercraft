package chat.jaspr.biomes;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.bukkit.Chunk;
import org.bukkit.ChunkSnapshot;
import org.bukkit.generator.ChunkGenerator;

/** Optional artificial landmarks/litter; density zero preserves the existing natural landscape. */
public final class BiomeDetails {
    public static final int MAX_BLOCKS_PER_CHUNK=224, MAX_NATIVE_BLOCKS=96;
    static final int MAX_FEATURE_BLOCKS=64, MAX_RELIEF=3, SPAWN_HALO=100, PORTAL_HALO=2;
    enum Motif {
        BOAT, PIER, BOLLARDS, CULVERT, PIPE, DRAIN, BENCH, WALL, MOSAIC, TRUSS, STEPS, VENT,
        GRAVES, CHIPPED_COLUMN, CISTERN, FROST, SLUICE, MONOLITH, ARCH, TORII, ROOTS, ALTAR,
        AWNING, SIGNAL, BARRICADE, CART, FENCE, REEDS, FALLEN_TRUNK, HUSK, CRYSTAL, CAIRN,
        RAIL, FOSSIL, RIBS, STUMP, WELL, PLINTH, WHEEL, SCAFFOLD, SLAG, SHELL, THRONE,
        GIBBET, BELFRY, STAKES, SARCOPHAGUS
    }
    enum Litter { SALT, SCRAP, SHARDS, TILES, ASH, SILT, ICE_CHIPS, PETALS, SEDGE, THORNS, BONES, SPLINTERS }
    public static final class Detail {
        public final int index, density;
        public final String identity;
        final List<Motif> motifs;
        final Litter litter;
        final int body, trim, accent;
        Detail(Catalog.Profile p,String[] f) {
            index=p.index;identity=f[1];density=Integer.parseInt(f[9]);
            motifs=Collections.unmodifiableList(Arrays.asList(Motif.valueOf(f[2]),Motif.valueOf(f[3]),Motif.valueOf(f[4])));
            litter=Litter.valueOf(f[5]);body=palette(f[6]);trim=palette(f[7]);accent=palette(f[8]);
            if(identity.trim().isEmpty()||new HashSet<>(motifs).size()!=3||(density!=0&&(density<3||density>4)))
                throw new IllegalArgumentException("Invalid biome detail "+p.name);
        }
    }
    public static final List<Detail> ALL=load();
    private BiomeDetails() {}

    private static List<Detail> load() {
        List<Detail> result=new ArrayList<>();Set<String> identities=new HashSet<>();
        try(BufferedReader in=new BufferedReader(new InputStreamReader(Objects.requireNonNull(
                BiomeDetails.class.getResourceAsStream("/biome-details.tsv"),"Missing biome-details.tsv"),StandardCharsets.UTF_8))) {
            String line;
            while((line=in.readLine())!=null) {
                if(line.trim().isEmpty()||line.startsWith("#"))continue;
                String[] f=line.split("\\|",-1);
                if(f.length!=10||result.size()>=Catalog.ALL.size())throw new IllegalArgumentException("Expected ten detail fields");
                Catalog.Profile p=Catalog.ALL.get(result.size());
                if(!p.slot.name().equals(f[0]))throw new IllegalArgumentException("Detail carrier/order mismatch: "+f[0]);
                Detail detail=new Detail(p,f);
                if(!identities.add(detail.identity))throw new IllegalArgumentException("Duplicate detail identity "+detail.identity);
                result.add(detail);
            }
            if(result.size()!=62)throw new IllegalArgumentException("Every biome needs explicit details: "+result.size());
        } catch(Exception e) {throw new ExceptionInInitializerError(e);}
        return Collections.unmodifiableList(result);
    }
    // Deliberately excludes ores, storage blocks, light sources, hazards, falling blocks, plants and tile entities.
    static boolean detailMaterial(int id) {
        switch(id) {
            case 1:case 4:case 5:case 17:case 20:case 24:case 35:case 43:case 44:case 45:case 48:
            case 80:case 85:case 98:case 101:case 102:case 112:case 113:case 125:case 126:case 139:
            case 159:case 162:case 172:case 174:case 179:case 216:return true;
            default:return false;
        }
    }
    private static int palette(String value) {
        String[] fields=value.split(":",-1);
        if(fields.length!=2)throw new IllegalArgumentException("Invalid detail palette "+value);
        int id=Integer.parseInt(fields[0]),data=Integer.parseInt(fields[1]);
        if(!detailMaterial(id)||data<0||data>15)throw new IllegalArgumentException("Unsafe detail palette "+value);
        return id<<4|data;
    }
    static boolean reserved(int cx,int cz) {
        long x=(long)cx*16,z=(long)cz*16;
        if(x<SPAWN_HALO&&x+16>-SPAWN_HALO&&z<SPAWN_HALO&&z+16>-SPAWN_HALO)return true;
        // Equivalent to checking every sanctuary in a two-chunk halo, including negative coordinates.
        int dx=Math.floorMod(cx-8,256),dz=Math.floorMod(cz,256);
        return Math.min(dx,256-dx)<=PORTAL_HALO&&Math.min(dz,256-dz)<=PORTAL_HALO;
    }

    /** Called after trees and before large structure stamping. The supplied heights are never changed. */
    public static void decorate(ChunkGenerator.ChunkData d,Terrain terrain,int cx,int cz,int[][] heights) {
        if(reserved(cx,cz))return;
        if(StructureRates.newSanctuaryHalo(terrain,cx,cz))return;     // 3.25.0 sanctuaries keep the same halo
        if(StructureRates.sanctuary2Halo(terrain,cx,cz))return;       // and the 3.28.0 ones
        decorate(new Overworld(d,terrain,cx,cz,heights),terrain.seed,cx,cz,false);
    }

    /** Native surfaces use the original snapshot for ownership and the chunk for collision checking. */
    static int decorateNative(Chunk c,ChunkSnapshot snapshot,long seed,boolean nether) {
        if(nativeReserved(c.getX(),c.getZ()))return 0;
        return decorate(new Native(c,snapshot,seed,nether),seed,c.getX(),c.getZ(),true);
    }
    static boolean nativeReserved(int cx,int cz) {
        // Leave Nether arrival space, the dragon arena, exit fountain and the End arrival platform alone.
        long x=(long)cx*16,z=(long)cz*16;
        return x<192&&x+16>-192&&z<192&&z+16>-192;
    }

    private static int decorate(Ground ground,long seed,int cx,int cz,boolean nativeRealm) {
        int remaining=nativeRealm?MAX_NATIVE_BLOCKS:MAX_BLOCKS_PER_CHUNK;
        long chunkSeed=Terrain.mix(seed+341873128712L*cx+132897987541L*cz+0x36d9a4257bL);
        int phase=(int)Math.floorMod(chunkSeed,3L),turn=(int)(chunkSeed>>>12)&3;
        for(int k=0;k<(nativeRealm?2:4);k++) {
            int quadrant=(k+turn)&3,ox=(quadrant&1)*8,oz=(quadrant>>>1)*8;
            int variant=(int)(Terrain.mix(chunkSeed+k*0x9e3779b97f4a7c15L)>>>33);
            for(int retry=0;retry<4;retry++) {
                int pick=(variant+retry)&3,x=ox+3+(pick&1),z=oz+3+(pick>>>1);
                Detail p=ground.detail(x,z);
                // Zero disables artificial detail in every realm, independently of nearby biome settings.
                if(p.density==0||(!nativeRealm&&k>=p.density))break;
                Motif motif=p.motifs.get((phase+k)%3);
                Shape shape=shape(p,motif,x,z,variant&3,variant>>>2);
                int placed=place(ground,shape,remaining,nativeRealm);
                if(placed>0){remaining-=placed;break;}
            }
        }
        for(int k=0;k<(nativeRealm?3:6);k++) {
            long bits=Terrain.mix(chunkSeed+0x7331L+k*19777L);
            int quadrant=(k+turn)&3,x=(quadrant&1)*8+2+((int)(bits>>>8)&3),z=(quadrant>>>1)*8+2+((int)(bits>>>14)&3);
            Detail p=ground.detail(x,z);
            if(p.density==0)continue;
            // Sparse-tier biomes keep their authored palette, but only half the litter passes run.
            if(!nativeRealm&&p.density==3&&k>=3)continue;
            remaining-=place(ground,litter(p,x,z,(int)bits&3,(int)(bits>>>33)),remaining,nativeRealm);
        }
        return (nativeRealm?MAX_NATIVE_BLOCKS:MAX_BLOCKS_PER_CHUNK)-remaining;
    }

    private interface Ground {
        int height(int x,int z);
        int type(int x,int y,int z);
        int maxHeight();
        Detail detail(int x,int z);
        boolean floor(int x,int y,int z);
        boolean clear(int x,int y,int z);
        boolean neighborhood(int minX,int minZ,int maxX,int maxZ,int bottom,int top);
        void set(int x,int y,int z,int state);
    }
    private static final class Overworld implements Ground {
        final ChunkGenerator.ChunkData data;final Terrain terrain;final int cx,cz;final int[][] heights;
        Overworld(ChunkGenerator.ChunkData data,Terrain terrain,int cx,int cz,int[][] heights) {
            this.data=data;this.terrain=terrain;this.cx=cx;this.cz=cz;this.heights=heights;
        }
        public int height(int x,int z) {
            int h=heights[x][z];
            // The generator freezes sea level above submerged terrain. Use that existing support without
            // cutting the ice seal or rewriting the caller's seabed heights.
            return h<62&&data.getTypeId(x,62,z)==79?62:h;
        }
        public int type(int x,int y,int z){return data.getTypeId(x,y,z);}
        public int maxHeight(){return data.getMaxHeight();}
        public Detail detail(int x,int z){return ALL.get(terrain.sample(cx*16+x,cz*16+z).profile.index);}
        public boolean floor(int x,int y,int z) {
            switch(type(x,y,z)) {
                case 1:case 2:case 3:case 12:case 13:case 24:case 79:case 80:case 82:case 87:case 88:
                case 121:case 159:case 172:case 174:case 179:return true;
                default:return false;
            }
        }
        public boolean clear(int x,int y,int z){int id=type(x,y,z);return id==0||id==78||id==8||id==9;}
        public boolean neighborhood(int a,int b,int c,int d,int e,int f){return true;}
        public void set(int x,int y,int z,int state){data.setBlock(x,y,z,state>>>4,(byte)(state&15));}
    }
    private static final class Native implements Ground {
        final Chunk chunk;final ChunkSnapshot snapshot;final boolean nether;final int[][] heights=new int[16][16];final long seed;
        Native(Chunk chunk,ChunkSnapshot snapshot,long seed,boolean nether) {
            this.chunk=chunk;this.snapshot=snapshot;this.seed=seed;this.nether=nether;
            for(int x=0;x<16;x++)for(int z=0;z<16;z++) {
                heights[x][z]=-1;
                // Pick one exposed floor per column; never scan roofs or load a neighboring column.
                for(int y=24;y<126;y++)if(snapshot.getBlockTypeId(x,y,z)==(nether?87:121)&&snapshot.getBlockTypeId(x,y+1,z)==0) {
                    heights[x][z]=y;break;
                }
            }
        }
        public int height(int x,int z){return heights[x][z];}
        public int type(int x,int y,int z){return chunk.getBlock(x,y,z).getTypeId();}
        public int maxHeight(){return 128;}
        public Detail detail(int x,int z){return ALL.get(OuterRealms.profile(seed,nether,chunk.getX()*16+x,chunk.getZ()*16+z).index);}
        public boolean floor(int x,int y,int z){return snapshot.getBlockTypeId(x,y,z)==(nether?87:121);}
        public boolean clear(int x,int y,int z){return snapshot.getBlockTypeId(x,y,z)==0&&type(x,y,z)==0;}
        public boolean neighborhood(int a,int b,int c,int d,int e,int f){return naturalNeighborhood(snapshot,nether,a,b,c,d,e,f);}
        public void set(int x,int y,int z,int state){
            chunk.getBlock(x,y,z).setTypeIdAndData(state>>>4,(byte)(state&15),false);
            if(CaptureHook.sink!=null)CaptureHook.chunk(chunk,x,y,z,state>>>4,state&15,"detail"); // test harness only
        }
    }
    static boolean naturalNeighborhood(ChunkSnapshot s,boolean nether,int x0,int z0,int x1,int z1,int y0,int y1) {
        if(x0<0||z0<0||x1>15||z1>15||y0<1||y1>127)return false;
        for(int x=x0;x<=x1;x++)for(int z=z0;z<=z1;z++)for(int y=y0;y<=y1;y++) {
            int id=s.getBlockTypeId(x,y,z);
            // Fortress brick/fences, End cities, obsidian pillars, portals, chorus and liquids all veto placement.
            if(id!=0&&id!=(nether?87:121)&&!(nether&&(id==88||id==13||id==153)))return false;
        }
        return true;
    }

    private static int key(int x,int y,int z){return (x*16+z)*256+y;}
    static final class Shape {
        final int x,z,rotation;final Map<Integer,Integer> voxels=new LinkedHashMap<>();
        boolean waterline;
        Shape(int x,int z,int rotation){this.x=x;this.z=z;this.rotation=rotation;}
        void at(int u,int y,int v,int state) {
            for(int r=0;r<rotation;r++){int old=u;u=-v;v=old;}
            // Store relative coordinates in a 9x9x16 box; supports and clipping are checked before any writes.
            if(Math.abs(u)>4||Math.abs(v)>4||y<0||y>7)throw new IllegalArgumentException("Unbounded detail shape");
            int id=state>>>4,data=state&15;
            if((id==17||id==162)&&(data&12)!=0&&(data&12)!=12&&(rotation&1)!=0)data^=12;
            voxels.put(((u+4)*9+v+4)*16+y,id<<4|data);
        }
        void post(int u,int v,int height,int state){for(int y=0;y<height;y++)at(u,y,v,state);}
    }
    private static int place(Ground g,Shape shape,int budget,boolean nativeRealm) {
        if(shape.voxels.isEmpty()||budget<=0)return 0;
        int low=256,high=-1,minX=16,maxX=-1,minZ=16,maxZ=-1,top=0;
        for(int key:shape.voxels.keySet()) {
            int x=shape.x+key/16/9-4,z=shape.z+key/16%9-4,y=key%16;
            // The untouched perimeter and two-wide cross leave passage between compact clearing features.
            if(x<1||x>14||z<1||z>14||x==7||x==8||z==7||z==8)return 0;
            int h=g.height(x,z);
            if(h<1||h>=g.maxHeight()-8||!g.floor(x,h,z))return 0;
            low=Math.min(low,h);high=Math.max(high,h);top=Math.max(top,y);
            minX=Math.min(minX,x);maxX=Math.max(maxX,x);minZ=Math.min(minZ,z);maxZ=Math.max(maxZ,z);
        }
        if(high-low>MAX_RELIEF)return 0;
        int base=high+1;
        // Shallow-water posts reach the waterline on real foundations; deep-water remnants remain on the bed.
        if(!nativeRealm&&shape.waterline&&high<62&&low>=55)base=63;
        if(base+top+2>=g.maxHeight())return 0;
        if(!g.neighborhood(minX-1,minZ-1,maxX+1,maxZ+1,low-1,base+top+2))return 0;
        Map<Integer,Integer> blocks=new LinkedHashMap<>();
        for(Map.Entry<Integer,Integer> p:shape.voxels.entrySet()) {
            int k=p.getKey(),x=shape.x+k/16/9-4,z=shape.z+k/16%9-4,y=base+k%16;
            if(k%16==0)for(int support=g.height(x,z)+1;support<base;support++)blocks.put(key(x,support,z),p.getValue());
            blocks.put(key(x,y,z),p.getValue());
        }
        if(blocks.size()>Math.min(budget,MAX_FEATURE_BLOCKS))return 0;
        for(int k:blocks.keySet())if(!g.clear(k/256/16,k%256,k/256%16))return 0;
        // A broken arch may have air below its lintel, but every connected component must reach actual ground.
        Set<Integer> connected=new HashSet<>();ArrayDeque<Integer> queue=new ArrayDeque<>();
        for(int k:blocks.keySet())if(k%256==g.height(k/256/16,k/256%16)+1){connected.add(k);queue.add(k);}
        while(!queue.isEmpty()) {
            int k=queue.remove();
            for(int offset:new int[]{4096,-4096,256,-256,1,-1})if(blocks.containsKey(k+offset)&&connected.add(k+offset))queue.add(k+offset);
        }
        if(connected.size()!=blocks.size())return 0;
        for(Map.Entry<Integer,Integer> b:blocks.entrySet()){int k=b.getKey();g.set(k/256/16,k%256,k/256%16,b.getValue());}
        return blocks.size();
    }

    static Shape shape(Detail p,Motif motif,int x,int z,int rotation,int variant) {
        Shape s=new Shape(x,z,rotation);int b=p.body,t=p.trim,a=p.accent,n=variant&1;
        switch(motif) {
            case BOAT:
                for(int v=-2;v<=2;v++){s.at(0,0,v,b);if(Math.abs(v)<2){s.at(-1,0,v,t);s.at(1,0,v,t);s.at(-1,1,v,b);if(v!=n)s.at(1,1,v,b);}}
                s.at(0,1,-2,t);s.at(0,1,2,t);s.post(0,0,3,a);break;
            case PIER:
                s.waterline=true;
                for(int u:new int[]{-1,1})for(int v:new int[]{-2,2})s.post(u,v,2,b);
                for(int v=-2;v<=2;v++)for(int u=-1;u<=1;u++)if(u!=1||v!=n)s.at(u,2,v,t);
                s.at(-1,3,-2,a);break;
            case BOLLARDS:
                s.waterline=true;
                for(int u=-2;u<=2;u+=2){s.post(u,0,2+(u==0?n:0),b);s.at(u,2+(u==0?n:0),0,t);s.at(u,0,1,a);}break;
            case CULVERT:
                for(int v=-2;v<=2;v++)for(int u:new int[]{-2,2})s.post(u,v,3,b);
                for(int u=-2;u<=2;u++)for(int v=-1;v<=1;v++)s.at(u,3,v,t);
                s.at(-2,3,-2,a);s.at(2,3,2,a);break;
            case PIPE:
                for(int u=-2;u<=2;u++)s.at(u,0,0,b);
                s.post(2,0,3,t);s.at(2,2,1,b);s.at(0,1,0,a);s.at(-2,0,-1,t);break;
            case DRAIN:
                for(int v=-2;v<=2;v++){s.at(-1,0,v,b);s.at(1,0,v,b);s.at(0,0,v,v%2==0?a:t);}
                s.post(-1,-2,2,t);s.at(1,1,2,t);break;
            case BENCH:
                s.post(-2,0,2,b);s.post(2,0,2,b);
                for(int u=-2;u<=2;u++){s.at(u,1,0,t);if(u!=n)s.post(u,1,3,b);}
                s.at(-2,2,0,a);break;
            case WALL:
                for(int u=-2;u<=0;u++)s.post(u,0,2+(u==-2?1:0),b);
                for(int v=1;v<=2;v++)s.post(-2,v,2,t);
                s.post(2,0,2,b);s.at(1,0,2,a);break;
            case MOSAIC:
                for(int u=-2;u<=2;u++)for(int v=-2;v<=2;v++)if(Math.abs(u)+Math.abs(v)<4&&(u!=2||v!=n))s.at(u,0,v,(u==0||v==0)?a:((u+v)&1)==0?b:t);
                break;
            case TRUSS:
                for(int u:new int[]{-2,2})s.post(u,0,4,b);
                for(int u=-2;u<=2;u++)s.at(u,3,0,t);
                s.at(-1,2,0,a);s.at(1,2,0,a);s.at(-2,0,1,t);s.at(2,0,-1,t);break;
            case STEPS:
                for(int v=-2;v<=1;v++)for(int u=-1;u<=1;u++)s.post(u,v,(v+2)/2+1,b);
                s.post(-1,2,3,t);s.post(0,2,3,b);s.at(1,0,2,a);break;
            case VENT:
                for(int u=-1;u<=1;u++)for(int v=-1;v<=1;v++)if(u!=0||v!=0)s.post(u,v,(u==0||v==0)?3:1,b);
                s.at(0,3,-1,t);s.at(-1,3,0,t);s.at(1,3,0,a);break;
            case GRAVES:
                for(int u:new int[]{-1,1}){for(int v=-1;v<=1;v++)s.at(u,0,v,b);s.post(u,-1,3-(u==1?n:0),t);s.at(u,1,1,a);}break;
            case CHIPPED_COLUMN:
                for(int u=-1;u<=1;u++)s.at(u,0,0,b);
                s.post(0,0,4+n,t);s.at(-1,3+n,0,a);s.at(1,0,1,t);s.at(-1,0,-1,b);break;
            case CISTERN:
                for(int u=-1;u<=1;u++)for(int v=-1;v<=1;v++)s.at(u,0,v,b);
                for(int v=-1;v<=1;v++){s.at(-1,1,v,t);s.at(1,1,v,t);}s.at(0,1,-1,a);s.at(-1,2,-1,t);break;
            case FROST:
                s.post(0,0,5+n,b);s.post(-1,0,3,b);s.post(1,0,2,t);
                s.post(-2,1,2,b);s.post(1,-1,3+n,b);s.at(0,4+n,1,a);s.at(2,0,1,t);break;
            case SLUICE:
                for(int v=-2;v<=2;v++){s.at(-1,0,v,b);s.at(1,0,v,b);}
                s.post(-1,-1,3,t);s.post(1,-1,3,t);for(int u=-1;u<=1;u++)s.at(u,3,-1,b);s.at(0,2,-1,a);break;
            case MONOLITH:
                s.post(-1,0,5,b);s.post(0,0,3+n,t);s.post(1,0,2,a);s.at(-1,4,1,t);s.at(1,0,1,b);break;
            case ARCH:
                s.post(-2,0,4,b);s.post(2,0,3+n,b);s.at(-1,3,0,t);s.at(-1,4,0,t);
                s.at(0,4,0,a);if(n==1)s.at(1,4,0,t);s.at(-2,0,1,t);s.at(2,0,-1,t);break;
            case TORII:
                for(int u:new int[]{-2,2})s.post(u,0,4,b);
                for(int u=-2;u<=2;u++){s.at(u,3,0,t);s.at(u,4,0,a);}s.at(-2,5,0,t);s.at(2,5,0,t);break;
            case ROOTS:
                s.post(0,0,3,b);s.post(-1,0,2,b);s.post(0,1,2,t);
                for(int u=-2;u<=2;u++)s.at(u,0,0,b);
                for(int v=-2;v<=2;v++)s.at(0,0,v,t);
                s.at(-2,0,-1,b);s.at(1,0,2,b);s.at(-1,0,-2,a);s.at(1,2,0,a);s.at(0,3,0,a);break;
            case ALTAR:
                for(int u:new int[]{-1,1})for(int v:new int[]{-1,1})s.at(u,0,v,b);
                for(int u=-1;u<=1;u++)for(int v=-1;v<=1;v++)s.at(u,1,v,t);
                s.at(0,2,0,a);s.post(-1,1,3,b);s.at(1,0,2,a);break;
            case AWNING:
                s.post(-2,0,4,b);s.post(2,0,4,b);
                for(int u=-2;u<=2;u++)for(int v=0;v<=2;v++)if(u!=n||v<2)s.at(u,3,v,(u&1)==0?t:a);
                s.at(-2,2,2,a);break;
            case SIGNAL:
                s.post(0,0,6,b);s.at(-1,0,0,t);s.at(1,0,0,t);s.at(0,0,1,t);
                for(int u=-2;u<=2;u++)s.at(u,4,0,a);s.at(0,3,-1,t);s.at(1,5,0,a);break;
            case BARRICADE:
                for(int u:new int[]{-2,2}){s.post(u,0,2,b);s.at(u,0,-1,t);s.at(u,0,1,t);s.at(u+(u<0?1:-1),1,0,a);}break;
            case CART:
                for(int v=-1;v<=1;v++)for(int u=-1;u<=1;u++)s.at(u,1,v,b);
                for(int u:new int[]{-1,1})for(int v:new int[]{-1,1})s.at(u,0,v,t);
                s.at(0,2,0,a);s.at(0,1,2,b);s.at(-1,2,-1,t);s.at(1,2,-1,t);break;
            case FENCE:
                for(int u=-2;u<=0;u++)s.post(u,0,u==-1?1:2,b);
                s.post(-2,1,2,t);s.post(2,0,2,b);s.at(2,0,1,a);break;
            case REEDS:
                s.waterline=true;
                s.post(0,0,4,b);s.post(-1,1,2,t);s.post(1,1,3,b);s.post(1,-1,2,b);s.post(-1,-1,3,t);
                s.at(0,4,0,a);s.at(1,3,1,a);break;
            case FALLEN_TRUNK:
                for(int u=-2;u<=2;u++)s.at(u,0,0,logAxis(b,4));
                s.at(-2,1,0,b);s.at(-2,0,-1,t);s.at(1,0,1,logAxis(b,8));s.at(1,0,2,a);s.at(-1,0,1,t);break;
            case HUSK:
                s.post(-1,0,3,b);s.at(0,2,0,b);s.at(0,3,0,b);s.at(1,3,0,t);s.at(1,4,0,t);
                s.at(-2,0,0,b);s.at(-1,0,1,t);s.at(0,3,-1,a);s.at(1,5,0,a);break;
            case CRYSTAL:
                s.post(0,0,3,b);s.at(1,2,0,b);s.at(1,3,0,t);s.at(1,4,0,a);
                s.post(-1,1,2,b);s.at(-2,1,1,t);s.at(-2,2,1,a);s.post(1,-1,2,t);s.at(0,0,-1,b);break;
            case CAIRN:
                for(int u=-1;u<=1;u++)for(int v=-1;v<=1;v++)if(u!=1||v!=1)s.at(u,0,v,b);
                s.post(0,0,3,t);s.at(-1,1,0,t);s.at(0,1,-1,b);s.at(0,3,0,a);break;
            case RAIL:
                for(int v=-2;v<=2;v++)for(int u=-1;u<=1;u++)s.at(u,0,v,(v&1)==0?b:t);
                for(int v=-2;v<=2;v++){s.at(-1,1,v,a);if(v!=n)s.at(1,1,v,t);}break;
            case FOSSIL:
                for(int v=-2;v<=2;v++)s.at(0,0,v,b);
                for(int v:new int[]{-1,1})for(int u:new int[]{-1,1}){s.post(u,v,2,b);s.at(u*2,1,v,t);s.at(u*2,0,v,t);}
                s.at(0,1,-2,a);s.at(1,0,-2,b);break;
            case RIBS:
                for(int v:new int[]{-1,1})for(int u:new int[]{-2,2}){s.post(u,v,3,b);s.at(u/2,2,v,t);}
                for(int v=-2;v<=2;v++)s.at(0,0,v,a);s.at(0,2,-1,b);s.at(0,3,-1,t);break;
            case STUMP:
                for(int u=-1;u<=0;u++)for(int v=-1;v<=0;v++)s.post(u,v,2+(u==v?n:0),b);
                s.at(-2,0,-1,t);s.at(1,0,0,t);s.at(0,0,1,b);s.at(0,0,-2,a);break;
            case WELL:
                for(int u=-1;u<=1;u++)for(int v=-1;v<=1;v++)if((u!=0||v!=0)&&(u!=0||v!=1))s.at(u,0,v,b);
                s.post(-1,0,3,t);s.post(1,0,3,t);for(int u=-1;u<=1;u++)s.at(u,3,0,b);s.at(0,1,-1,a);break;
            case PLINTH:
                for(int u=-2;u<=2;u++)for(int v=-1;v<=1;v++)if(u!=2||v!=1)s.at(u,0,v,b);
                s.post(-1,0,3,t);s.post(0,0,2,t);s.at(-1,3,0,a);s.at(1,1,0,a);break;
            case WHEEL:
                for(int u=-1;u<=1;u++){s.at(u,0,0,b);s.at(u,4,0,t);}
                for(int u:new int[]{-2,2})for(int y=1;y<=3;y++)s.at(u,y,0,t);
                for(int u:new int[]{-1,1}){s.at(u,1,0,t);s.at(u,3,0,t);}
                for(int u=-1;u<=1;u++)s.at(u,2,0,b);s.at(0,2,1,a);break;
            case SCAFFOLD:
                for(int u:new int[]{-1,1})for(int v:new int[]{-1,1})s.post(u,v,3,b);
                for(int u=-1;u<=1;u++){s.at(u,3,-1,t);s.at(u,3,1,t);}s.at(-1,3,0,t);s.at(0,3,0,a);break;
            case SLAG:
                s.post(0,0,3,b);s.post(1,0,2,b);s.post(1,1,2,t);s.at(0,0,1,t);s.at(-1,0,1,a);
                s.post(-1,-1,2,t);s.at(0,0,-1,b);s.at(2,0,0,a);break;
            case SHELL:
                for(int v=-1;v<=1;v++){s.post(-1,v,2,b);s.post(1,v,2,b);}for(int u=-1;u<=1;u++)s.post(u,-1,2,t);
                for(int u=-1;u<=1;u++)s.at(u,2,-1,b);s.at(-1,2,0,a);s.at(1,2,0,t);break;
            case THRONE:
                for(int u=-1;u<=1;u++)for(int v=-1;v<=1;v++)s.at(u,0,v,b);
                for(int u=-1;u<=1;u++)s.post(u,-1,u==0?4:3,t);
                s.post(-1,0,2,b);s.post(1,0,2,b);s.at(0,3,-1,a);break;
            case GIBBET:
                s.post(-1,0,5,b);s.at(-2,0,0,t);s.at(-1,0,1,t);
                for(int u=-1;u<=2;u++)s.at(u,4,0,b);s.at(0,3,0,t);s.at(2,3,0,a);break;
            case BELFRY:
                for(int u:new int[]{-1,1})s.post(u,0,5,b);
                for(int u=-1;u<=1;u++)s.at(u,4,0,t);s.at(0,3,0,a);s.at(0,5,0,t);
                s.at(-1,0,1,b);s.at(1,0,1,b);break;
            case STAKES:
                for(int v=-2;v<=2;v+=2){int u=v==0?1:-1,h=2+((variant+v)&1);s.post(u,v,h,b);s.at(u,h,v,a);s.at(u+1,0,v,t);}break;
            case SARCOPHAGUS:
                for(int u=-1;u<=1;u++)for(int v=-2;v<=2;v++)s.at(u,0,v,b);
                for(int v=-2;v<=2;v++){s.at(-1,1,v,t);if(v!=n)s.at(1,1,v,t);if(v<1)s.at(0,1,v,b);}
                s.at(0,2,-2,a);s.at(0,1,2,a);break;
            default:throw new AssertionError(motif);
        }
        return s;
    }
    private static int logAxis(int state,int axis){return state>>>4==17||state>>>4==162?(state&~12)|axis:state;}
    private static Shape litter(Detail p,int x,int z,int rotation,int variant) {
        Shape s=new Shape(x,z,rotation);int b=p.body,t=p.trim,a=p.accent,n=(variant&1)==0?-1:1;
        switch(p.litter) {
            case SPLINTERS:s.at(-1,0,0,logAxis(b,4));s.at(0,0,0,logAxis(b,4));s.at(1,0,n,t);break;
            case SEDGE:s.post(0,0,2,b);s.at(1,0,n,t);s.at(-1,0,0,a);break;
            case ICE_CHIPS:s.post(0,0,2,b);s.at(-1,0,0,t);s.at(1,0,n,b);break;
            case THORNS:s.post(0,0,2,b);s.at(1,1,0,a);s.at(-1,0,n,t);break;
            case BONES:s.at(-1,0,0,t);s.at(0,0,0,b);s.at(1,0,0,b);s.at(1,0,n,a);break;
            case PETALS:s.at(0,0,0,a);s.at(-1,0,n,a);s.at(1,0,0,t);break;
            case SHARDS:s.post(0,0,2,a);s.at(1,0,n,t);s.at(-1,0,0,b);break;
            case TILES:s.at(0,0,0,a);s.at(1,0,0,b);s.at(1,0,n,t);s.at(-1,0,n,b);break;
            case ASH:s.at(0,0,0,t);s.at(-1,0,0,b);s.at(1,0,n,t);break;
            case SILT:s.at(-1,0,0,b);s.at(0,0,n,t);s.at(1,0,0,a);break;
            case SALT:s.at(0,0,0,t);s.at(1,0,n,a);s.at(-1,0,0,a);break;
            case SCRAP:s.at(0,0,0,b);s.at(0,1,0,t);s.at(1,0,n,a);s.at(-1,0,0,t);break;
            default:throw new AssertionError(p.litter);
        }
        return s;
    }
}
