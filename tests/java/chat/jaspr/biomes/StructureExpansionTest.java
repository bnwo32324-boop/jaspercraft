package chat.jaspr.biomes;

import java.lang.reflect.Constructor;
import java.util.*;
import org.bukkit.Material;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.material.MaterialData;

/** Catalogue and voxel access checks against Paper's real ChunkData interface, without a server/World. */
public final class StructureExpansionTest {
    private static long checks;
    private static void check(boolean ok,String message) {
        checks++; if(!ok)throw new AssertionError(message);
    }
    public static void main(String[] args) throws Exception {
        if(args.length>0&&args[0].equals("--load-only")) {
            System.out.println("CATALOG_LOAD_PASS "+StructureCatalog.ALL.size()); return;
        }
        catalogue();
        int designs=0,expansions=0;
        Set<Long> rendered=new HashSet<>();
        for(StructureCatalog.Design design:StructureCatalog.ALL) {
            StructurePlanner.Site site=StructurePlanner.preview(8675309L,design,-407,-315);
            Voxels world=new Voxels(site); world.stamp(false);
            access(site,world);
            if(design.isExpansion()) {
                check(rendered.add(world.digest()),"Duplicate expansion rendering "+design.id);
                expansions++;
                if(expansions%19==0) {
                    Voxels reversed=new Voxels(site); reversed.stamp(true);
                    check(world.same(reversed),"Chunk-order dependency "+design.id);
                }
            }
            check(world.type(site.x,0,site.z)==7,"Bedrock overwritten "+design.id);
            check(world.type(site.x,12,site.z)==56,"Deep ore overwritten "+design.id);
            designs++;
            if(designs%30==0)System.out.println("Expansion/legacy voxel access "+designs+"/"+StructureCatalog.ALL.size());
        }
        // Isolate room programs from graph shape and compare actual geometry, not room names.
        Set<Long> motifs=new HashSet<>(); int variants=0;
        for(char type:StructureCatalog.EXPANSION_ROOMS.toCharArray()) {
            String graph=""+type+type+"/"+type+type;
            for(String mode:new String[]{"surface","buried","underwater"}) {
                StructureCatalog.Design design=synthetic(type,mode,graph);
                StructurePlanner.Site site=StructurePlanner.preview(-1,design,-2049,1535);
                Voxels world=new Voxels(site); world.stamp(false); access(site,world);
                Voxels shuffled=new Voxels(site); shuffled.stamp(true);
                check(world.same(shuffled),"Motif crosses chunk ownership "+type+" "+mode);
                if(mode.equals("surface"))check(motifs.add(world.digest()),"Indistinguishable motif "+type);
                variants++;
            }
        }
        for(long seed:new long[]{0,Long.MIN_VALUE,123456789}) {
            for(String id:new String[]{"rustwater_last_train","glass_reactor_isotope_wells",
                    "frostcrown_ninth_meridian","brine_lantern_ferry_crypt","rootbound_herbarium_cloister"}) {
                StructurePlanner.Site site=StructurePlanner.preview(seed,StructureCatalog.byId(id),-1025,-1);
                Voxels world=new Voxels(site);world.stamp(false);access(site,world);
            }
        }
        System.out.println("STRUCTURE_EXPANSION_PASS designs="+designs+" new="+expansions+
                " motifs="+motifs.size()+" modeVariants="+variants+" checks="+checks);
    }
    private static StructureCatalog.Design synthetic(char type,String mode,String graph) throws Exception {
        Constructor<StructureCatalog.Design> constructor=StructureCatalog.Design.class.getDeclaredConstructor(String[].class,boolean.class);
        constructor.setAccessible(true);
        return constructor.newInstance(new String[]{"probe_"+(int)type,"Motif probe","probe","3","0","true",mode,graph},true);
    }
    private static void catalogue() {
        check(StructureCatalog.LEGACY_COUNT==104,"Frozen legacy count");
        check(StructureCatalog.ALL.size()>=230,"126 or more expansion designs");
        for(int biome=0;biome<62;biome++) {
            List<StructureCatalog.Design> old=new ArrayList<>(),extra=new ArrayList<>();
            boolean exclusive=false,shared=false;
            for(int i=0;i<StructureCatalog.ALL.size();i++) {
                StructureCatalog.Design design=StructureCatalog.ALL.get(i);
                check(design.isExpansion()==(i>=104),"Wrong expansion identity "+design.id);
                if(design.accepts(biome)) {
                    (i<104?old:extra).add(design);
                    if(i>=104){exclusive|=design.exclusive;shared|=!design.exclusive;}
                }
            }
            check(old.equals(StructureCatalog.legacyChoices(biome)),"Legacy choice membership/order "+biome);
            check(extra.equals(StructureCatalog.expansionChoices(biome)),"Expansion choice membership/order "+biome);
            List<StructureCatalog.Design> all=new ArrayList<>(old);all.addAll(extra);
            check(all.equals(StructureCatalog.choices(biome)),"Combined choices changed order "+biome);
            check(exclusive&&shared,"Expansion exclusive/shared coverage "+biome);
            immutable(StructureCatalog.legacyChoices(biome));
            immutable(StructureCatalog.expansionChoices(biome));
        }
        for(int bad:new int[]{-1,62,Integer.MAX_VALUE}) {
            try { StructureCatalog.legacyChoices(bad);throw new AssertionError("Accepted legacy biome "+bad); }
            catch(IllegalArgumentException expected){checks++;}
            try { StructureCatalog.expansionChoices(bad);throw new AssertionError("Accepted expansion biome "+bad); }
            catch(IllegalArgumentException expected){checks++;}
        }
        for(StructureCatalog.Design design:StructureCatalog.ALL) {
            int first=design.biomes[0];design.biomes[0]=-1;
            check(design.accepts(first)&&!design.accepts(-1),"Mutable biome snapshot leaked "+design.id);
            design.biomes[0]=first;
            check(design.columns*12+8<=344&&design.rows*12+104<=344,"Expansion grid envelope "+design.id);
        }
        // Explicitly freeze the old vocabulary's floor/open/footprint contracts, including digits 5/7.
        for(char type:".hMsSKTDUBFWCGAP=oq2345678".toCharArray()) {
            int floors=type>='2'&&type<='8'?type-'0':type=='T'?7:type=='K'?4:type=='M'||type=='S'?2:1;
            check(StructureCatalog.floors(type)==floors,"Legacy floors changed "+type);
            check(StructureCatalog.open(type)==("=oWCA".indexOf(type)>=0),"Legacy openness changed "+type);
            for(int x=-1;x<=12;x++)for(int z=-1;z<=12;z++) {
                boolean inside=x>=0&&z>=0&&x<=11&&z<=11;
                if(inside) {
                    if(type=='F')inside=x>=3&&x<=8||z>=5&&z<=7;
                    else if(type=='W')inside=z>=5&&z<=7||x>=5&&x<=7;
                    else if(type=='C'||type=='A')inside=x>=4&&x<=8||z>=4&&z<=8;
                    else if(type=='G'||type=='T'||type=='K')inside=Math.min(x,11-x)+Math.min(z,11-z)>=3;
                }
                check(StructureArchitecture.footprint(type,x,z)==inside,"Legacy footprint changed "+type);
            }
        }
    }
    private static void immutable(List<StructureCatalog.Design> designs) {
        try { designs.clear();throw new AssertionError("Mutable choice list"); }
        catch(UnsupportedOperationException expected){checks++;}
    }
    private static void access(StructurePlanner.Site site,Voxels world) {
        String label=site.design.id+" "+site.design.mode+" seed="+site.seed;
        int ex=site.entryX(),ez=site.entryZ()+site.approach.length,ey=site.approach[site.approach.length-1]+1;
        check(ey>=64&&world.walk(ex,ey,ez),"Blocked surface landing "+label);
        BitSet seen=new BitSet();ArrayDeque<Integer> todo=new ArrayDeque<>();
        int start=encode(site,ex,ey,ez);seen.set(start);todo.add(start);
        while(!todo.isEmpty()) {
            int at=todo.remove(),y=at%256,x=at/256%site.width+site.x,z=at/256/site.width+site.z;
            for(int[] d:StructureCatalog.DIRS)for(int dy:new int[]{0,1,-1}) {
                int nx=x+d[0],ny=y+dy,nz=z+d[1];
                if(!inside(site,nx,nz)||ny<1||ny>246)continue;
                int next=encode(site,nx,ny,nz);
                if(!seen.get(next)&&world.walk(nx,ny,nz)&&(dy<=0||pass(world.type(x,y+2,z)))) {
                    seen.set(next);todo.add(next);
                }
            }
        }
        for(StructurePlanner.Room room:site.rooms) {
            int ox=site.x+4+room.col*12,oz=site.z+4+room.row*12;
            for(int level=0;level<StructureCatalog.floors(room.type);level++) {
                check(seen.get(encode(site,ox+6,room.floor+level*6+1,oz+6)),
                        "Unreachable room/storey "+room.type+" at "+room.col+","+room.row+":"+level+" "+label);
            }
            if(StructureCatalog.EXPANSION_ROOMS.indexOf(room.type)>=0) {
                for(int x=0;x<12;x++)for(int z=0;z<12;z++)if(site.doorway(room,x,z)) {
                    int floor=site.floor(room,x,z);
                    for(int dy=1;dy<=3;dy++)check(world.type(ox+x,floor+dy,oz+z)==0,"Obstructed doorway "+label);
                }
            }
            if(room.type=='R') {
                int rails=0;
                for(int x=0;x<12;x++)for(int z=0;z<12;z++)if(world.type(ox+x,room.floor+1,oz+z)==66) {
                    rails++;
                    check(solid(world.type(ox+x,room.floor,oz+z)),"Unsupported Metro rail "+label);
                }
                check(rails>=6,"Metro platform lost its track segments "+label);
            }
        }
        int boss=0,ordinal=0;
        for(StructurePlanner.Marker marker:site.markers()) {
            check(marker.ordinal==ordinal++,"Non-sequential marker identity "+label);
            if(marker.kind.equals("boss")||marker.kind.equals("mob")) {
                check(world.type(marker.x,marker.y,marker.z)==0&&world.type(marker.x,marker.y+1,marker.z)==0,"Blocked encounter "+label);
                check(seen.get(encode(site,marker.x,marker.y,marker.z)),"Unreachable encounter "+label);
                if(marker.kind.equals("boss"))boss++;
            } else if(marker.kind.equals("door")) {
                check(world.type(marker.x,marker.y,marker.z)==201,"Moved gate marker "+label);
                check(seen.get(encode(site,marker.x,marker.y+1,marker.z)),"Unreachable gate "+label);
            } else {
                check(world.type(marker.x,marker.y,marker.z)==54&&world.type(marker.x,marker.y+1,marker.z)==0,"Moved/covered cache "+label);
                boolean adjacent=false;
                for(int[] d:StructureCatalog.DIRS)adjacent|=seen.get(encode(site,marker.x+d[0],marker.y,marker.z+d[1]));
                check(adjacent,"Inaccessible cache "+label);
            }
        }
        check(boss==(site.design.tier>=3?1:0),"Missing boss position "+label);
        if(site.design.mode.equals("underwater"))waterSeal(site,world);
    }
    private static void waterSeal(StructurePlanner.Site site,Voxels world) {
        BitSet wet=new BitSet();ArrayDeque<Integer> todo=new ArrayDeque<>();
        // Include internal tanks: flowing water may spread sideways/downward, never upward.
        for(int x=site.x;x<site.x+site.width;x++)for(int z=site.z;z<site.z+site.depth;z++)for(int y=20;y<=62;y++) {
            if(!pass(world.type(x,y,z)))continue;
            boolean source=world.type(x,y+1,z)==9;
            for(int[] d:StructureCatalog.DIRS)source|=world.type(x+d[0],y,z+d[1])==9;
            if(source){int at=encode(site,x,y,z);wet.set(at);todo.add(at);}
        }
        int[][] flow={{1,0,0},{-1,0,0},{0,0,1},{0,0,-1},{0,-1,0}};
        while(!todo.isEmpty()) {
            int at=todo.remove(),y=at%256,x=at/256%site.width+site.x,z=at/256/site.width+site.z;
            for(int[] d:flow) {
                int nx=x+d[0],ny=y+d[1],nz=z+d[2];if(!inside(site,nx,nz)||ny<20)continue;
                int next=encode(site,nx,ny,nz);
                if(!wet.get(next)&&pass(world.type(nx,ny,nz))) {wet.set(next);todo.add(next);}
            }
        }
        for(StructurePlanner.Room room:site.rooms)for(int level=0;level<StructureCatalog.floors(room.type);level++) {
            int x=site.x+4+room.col*12+6,z=site.z+4+room.row*12+6,y=room.floor+level*6+1;
            check(!wet.get(encode(site,x,y,z)),"Water reaches occupied storey "+site.design.id+" "+room.col+","+room.row);
        }
    }
    private static boolean inside(StructurePlanner.Site s,int x,int z){return x>=s.x&&x<s.x+s.width&&z>=s.z&&z<s.z+s.depth;}
    private static int encode(StructurePlanner.Site s,int x,int y,int z){return ((z-s.z)*s.width+x-s.x)*256+y;}
    private static boolean pass(int id){return id==0||id==50||id==38||id==31||id==78||id==69||id==77||id==66||id==171;}
    private static boolean solid(int id){return !pass(id)&&id!=8&&id!=9&&id!=10&&id!=11&&id!=85&&id!=101&&id!=198;}
    private static final class Voxels {
        final StructurePlanner.Site site;final Map<Long,ProbeChunk> chunks=new HashMap<>();
        Voxels(StructurePlanner.Site site){this.site=site;}
        ProbeChunk chunk(int cx,int cz) {
            long key=((long)cx<<32)^(cz&0xffffffffL);ProbeChunk value=chunks.get(key);
            if(value==null){value=new ProbeChunk(site.seed,cx,cz);chunks.put(key,value);}return value;
        }
        int type(int x,int y,int z){return chunk(Math.floorDiv(x,16),Math.floorDiv(z,16)).getTypeId(Math.floorMod(x,16),y,Math.floorMod(z,16));}
        boolean walk(int x,int y,int z){return pass(type(x,y,z))&&pass(type(x,y+1,z))&&solid(type(x,y-1,z));}
        void stamp(boolean shuffle) {
            List<int[]> order=new ArrayList<>();
            for(int cx=Math.floorDiv(site.x,16);cx<=Math.floorDiv(site.x+site.width-1,16);cx++)
                for(int cz=Math.floorDiv(site.z,16);cz<=Math.floorDiv(site.z+site.depth-1,16);cz++)order.add(new int[]{cx,cz});
            if(shuffle)Collections.shuffle(order,new Random(741));
            for(int[] at:order)site.stamp(chunk(at[0],at[1]),at[0],at[1]);
        }
        long digest(){long result=0;for(Map.Entry<Long,ProbeChunk> entry:chunks.entrySet())result+=Terrain.mix(entry.getKey()+Arrays.hashCode(entry.getValue().blocks));return result;}
        boolean same(Voxels other) {
            for(Map.Entry<Long,ProbeChunk> entry:chunks.entrySet()) {
                ProbeChunk right=other.chunks.get(entry.getKey());
                // Access/water checks may read (without writing) an extra perimeter chunk.
                if(right==null) {for(short value:entry.getValue().blocks)if(value!=-1)return false;}
                else if(!Arrays.equals(entry.getValue().blocks,right.blocks))return false;
            }
            return true;
        }
    }
    private static final class ProbeChunk implements ChunkGenerator.ChunkData {
        final short[] blocks=new short[65536];final Terrain.Sample[] ground=new Terrain.Sample[256];
        final Terrain terrain;final int cx,cz;
        ProbeChunk(long seed,int cx,int cz){this.cx=cx;this.cz=cz;terrain=new Terrain(seed);Arrays.fill(blocks,(short)-1);}
        private int index(int x,int y,int z) {
            if(x<0||x>=16||z<0||z>=16||y<0||y>=256)throw new AssertionError("Cross-chunk/out-of-height access "+x+","+y+","+z);
            return (x*16+z)*256+y;
        }
        public int getMaxHeight(){return 256;}
        public void setBlock(int x,int y,int z,Material type){setBlock(x,y,z,type.getId(),(byte)0);}
        public void setBlock(int x,int y,int z,MaterialData data){setBlock(x,y,z,data.getItemTypeId(),data.getData());}
        public void setBlock(int x,int y,int z,int type){setBlock(x,y,z,type,(byte)0);}
        public void setBlock(int x,int y,int z,int type,byte data) {
            Material material=Material.getMaterial(type);
            if(material==null||!material.isBlock()||type==209)throw new AssertionError("Unsafe/non-vanilla block "+type);
            blocks[index(x,y,z)]=(short)(type<<4|(data&15));
        }
        public int getTypeId(int x,int y,int z) {
            short value=blocks[index(x,y,z)];if(value>=0)return value>>>4;
            Terrain.Sample sample=ground[x*16+z];if(sample==null)ground[x*16+z]=sample=terrain.sample(cx*16+x,cz*16+z);
            if(y==0)return 7;if(y==12)return 56;if(y<sample.y-3)return 1;
            if(y<sample.y)return sample.profile.under;if(y==sample.y)return sample.profile.surface;return y<=62?9:0;
        }
        public byte getData(int x,int y,int z){short v=blocks[index(x,y,z)];return v<0?0:(byte)(v&15);}
        public Material getType(int x,int y,int z){return Material.getMaterial(getTypeId(x,y,z));}
        public MaterialData getTypeAndData(int x,int y,int z){return new MaterialData(getTypeId(x,y,z),getData(x,y,z));}
        public void setRegion(int a,int b,int c,int d,int e,int f,Material material){setRegion(a,b,c,d,e,f,material.getId(),0);}
        public void setRegion(int a,int b,int c,int d,int e,int f,MaterialData material){setRegion(a,b,c,d,e,f,material.getItemTypeId(),material.getData());}
        public void setRegion(int a,int b,int c,int d,int e,int f,int type){setRegion(a,b,c,d,e,f,type,0);}
        public void setRegion(int a,int b,int c,int d,int e,int f,int type,int data) {
            for(int x=a;x<d;x++)for(int y=b;y<e;y++)for(int z=c;z<f;z++)setBlock(x,y,z,type,(byte)data);
        }
    }
}
