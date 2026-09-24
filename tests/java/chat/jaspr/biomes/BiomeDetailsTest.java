package chat.jaspr.biomes;

import java.lang.reflect.Proxy;
import java.util.*;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.material.MaterialData;

/** Pure JVM contract checks against the real Bukkit interfaces; no Paper process or World loads. */
public final class BiomeDetailsTest {
    private static long checks;
    private static void check(boolean ok,String message){checks++;if(!ok)throw new AssertionError(message);}
    private static final long SEED=0x62b10de7a1L;
    public static void main(String[] args) {
        long started=System.nanoTime();
        recipes();shapes();
        Map<Integer,int[]> points=points(new Terrain(SEED));
        int minFlat=Integer.MAX_VALUE,minReal=Integer.MAX_VALUE,maxAdded=0;
        Set<String> rendered=new HashSet<>();
        for(int biome=0;biome<62;biome++) {
            int[] at=points.get(biome);int cx=at[0],cz=at[1];
            TestChunk flat=new TestChunk(SEED,cx,cz,70),same=new TestChunk(SEED,cx,cz,70);
            int[] before=flat.blocks.clone();int[][] originalHeights=copy(flat.heights);
            BiomeDetails.decorate(flat,new Terrain(SEED),cx,cz,flat.heights);
            BiomeDetails.decorate(same,new Terrain(SEED),cx,cz,same.heights);
            check(Arrays.equals(flat.blocks,same.blocks),"Repeat determinism biome "+biome);
            check(Arrays.deepEquals(flat.heights,originalHeights),"Caller heights mutated "+biome);
            verify(flat,before,BiomeDetails.MAX_BLOCKS_PER_CHUNK,true);
            check(flat.writes>=35,"Sparse flat biome "+biome+": "+flat.writes);
            // Exclude original terrain palettes: uniqueness must come from the added detail geometry/materials.
            StringBuilder added=new StringBuilder();
            for(int k=0;k<before.length;k++)if(before[k]!=flat.blocks[k])added.append(k).append(':').append(flat.blocks[k]).append(';');
            check(rendered.add(added.toString()),"Duplicate detail-only rendering "+biome);
            minFlat=Math.min(minFlat,flat.writes);maxAdded=Math.max(maxAdded,flat.writes);
            TestChunk real=new TestChunk(SEED,cx,cz,-1);before=real.blocks.clone();
            BiomeDetails.decorate(real,new Terrain(SEED),cx,cz,real.heights);
            verify(real,before,BiomeDetails.MAX_BLOCKS_PER_CHUNK,false);
            check(real.writes>=3,"No details on actual biome terrain "+biome+" at "+cx+","+cz);
            minReal=Math.min(minReal,real.writes);maxAdded=Math.max(maxAdded,real.writes);
        }
        protections();orderAndSeeds();obstructions(points);nativeRealms();
        System.out.println("BIOME_DETAILS_PASS biomes=62 motifs="+BiomeDetails.Motif.values().length+" litter="+BiomeDetails.Litter.values().length+
            " renderedUnique="+rendered.size()+" minFlatBlocks="+minFlat+" minActualTerrainBlocks="+minReal+" maxBlocks="+maxAdded+
            " checks="+checks+" elapsedMs="+((System.nanoTime()-started)/1000000));
    }
    private static void recipes() {
        check(BiomeDetails.ALL.size()==62,"62 explicit identities");Set<String> identities=new HashSet<>(),recipes=new HashSet<>();
        EnumSet<BiomeDetails.Motif> motifs=EnumSet.noneOf(BiomeDetails.Motif.class);
        EnumSet<BiomeDetails.Litter> litters=EnumSet.noneOf(BiomeDetails.Litter.class);
        for(BiomeDetails.Detail p:BiomeDetails.ALL) {
            check(p.index==identities.size(),"Carrier order");check(identities.add(p.identity),"Unique identity");
            check(p.motifs.size()==3&&new HashSet<>(p.motifs).size()==3,"Three distinct motifs");
            check(recipes.add(p.motifs+":"+p.litter+":"+p.body+":"+p.trim+":"+p.accent),"Unique rendered recipe");
            motifs.addAll(p.motifs);litters.add(p.litter);
            for(int state:new int[]{p.body,p.trim,p.accent})check(BiomeDetails.detailMaterial(state>>>4),"Safe palette");
        }
        check(motifs.size()==47&&litters.size()==12,"All authored shape/litter implementations used");
        try {BiomeDetails.ALL.clear();throw new AssertionError("Mutable catalog");}catch(UnsupportedOperationException expected){checks++;}
        try {BiomeDetails.ALL.get(0).motifs.clear();throw new AssertionError("Mutable motifs");}catch(UnsupportedOperationException expected){checks++;}
        for(int id:new int[]{0,8,9,10,11,12,13,14,15,16,21,30,41,42,46,49,50,51,52,54,56,57,73,74,79,81,89,120,129,130,133,137,152,153,165,166,169,198,209,210,213,218})
            check(!BiomeDetails.detailMaterial(id),"Unsafe material allowed "+id);
    }
    private static void shapes() {
        Set<String> silhouettes=new HashSet<>();
        BiomeDetails.Detail palette=BiomeDetails.ALL.get(0);
        for(BiomeDetails.Motif motif:BiomeDetails.Motif.values())for(int rotation=0;rotation<4;rotation++)for(int variant=0;variant<8;variant++) {
            BiomeDetails.Shape s=BiomeDetails.shape(palette,motif,3,3,rotation,variant);
            check(s.voxels.size()>=8&&s.voxels.size()<=BiomeDetails.MAX_FEATURE_BLOCKS,"Shape volume "+motif);
            Set<Integer> connected=new HashSet<>();ArrayDeque<Integer> todo=new ArrayDeque<>();
            for(Map.Entry<Integer,Integer> b:s.voxels.entrySet()) {
                int key=b.getKey(),u=key/16/9-4,v=key/16%9-4,y=key%16;
                check(Math.abs(u)<=2&&Math.abs(v)<=2&&y<=6,"Compact geometry "+motif);
                check(BiomeDetails.detailMaterial(b.getValue()>>>4),"Safe shape material");
                if(y==0){connected.add(key);todo.add(key);}
            }
            while(!todo.isEmpty()){int at=todo.remove();for(int offset:new int[]{144,-144,16,-16,1,-1})if(s.voxels.containsKey(at+offset)&&connected.add(at+offset))todo.add(at+offset);}
            check(connected.size()==s.voxels.size(),"Floating component in "+motif+" rotation="+rotation+" variant="+variant);
            if(rotation==0&&variant==0){List<Integer> keys=new ArrayList<>(s.voxels.keySet());Collections.sort(keys);silhouettes.add(keys.toString());}
        }
        check(silhouettes.size()==47,"Distinct geometry after removing palettes: "+silhouettes.size());
        System.out.println("47 distinct landmark geometries; 1,504 rotation/variant support checks passed");
    }
    private static Map<Integer,int[]> points(Terrain terrain) {
        Map<Integer,int[]> result=new TreeMap<>();
        for(int rx=-25;rx<=25&&result.size()<62;rx++)for(int rz=-25;rz<=25;rz++) {
            int cx=rx*24+12,cz=rz*24+12;
            if(BiomeDetails.reserved(cx,cz))continue;
            int id=terrain.sample(cx*16+8,cz*16+8).profile.index;
            if(result.containsKey(id))continue;
            boolean same=true;for(int x:new int[]{0,15})for(int z:new int[]{0,15})same&=terrain.sample(cx*16+x,cz*16+z).profile.index==id;
            if(same)result.put(id,new int[]{cx,cz});
        }
        check(result.size()==62,"All 62 actual terrain regions found");return result;
    }
    private static void verify(TestChunk chunk,int[] before,int budget,boolean flat) {
        check(chunk.writes<=budget,"Chunk block budget");int changed=0;
        Set<Integer> added=new HashSet<>(),connected=new HashSet<>();ArrayDeque<Integer> todo=new ArrayDeque<>();
        for(int x=0;x<16;x++)for(int z=0;z<16;z++)for(int y=0;y<256;y++) {
            int k=TestChunk.key(x,y,z),old=before[k],now=chunk.blocks[k];
            if(y<=chunk.heights[x][z])check(old==now,"Terrain/cave/ore/bedrock altered at "+x+","+y+","+z);
            if(old==now)continue;
            changed++;added.add(k);
            check(y>chunk.heights[x][z],"Writes above original surface only");
            int support=chunk.heights[x][z]<62&&before[TestChunk.key(x,62,z)]>>>4==79?62:chunk.heights[x][z];
            check(y<=support+14,"Height/support budget");
            check(x>0&&x<15&&z>0&&z<15&&x!=7&&x!=8&&z!=7&&z!=8,"Passage reservation");
            int id=old>>>4;check(id==0||id==8||id==9||id==78,"Existing tree/structure replaced");
            check(BiomeDetails.detailMaterial(now>>>4),"Unsafe output material");
            if(y==support+1){connected.add(k);todo.add(k);}
        }
        check(changed==chunk.writes,"No double or redundant writes");
        while(!todo.isEmpty()){int at=todo.remove();for(int offset:new int[]{4096,-4096,256,-256,1,-1})if(added.contains(at+offset)&&connected.add(at+offset))todo.add(at+offset);}
        check(connected.size()==added.size(),"Every detail is connected to terrain, including underwater supports");
        if(flat) {
            // Flood two-block-high walking space; every edge remains connected around the new details.
            BitSet seen=new BitSet(256);ArrayDeque<Integer> walking=new ArrayDeque<>();seen.set(0);walking.add(0);
            while(!walking.isEmpty()) {
                int at=walking.remove(),x=at/16,z=at%16;
                for(int[] d:new int[][]{{1,0},{-1,0},{0,1},{0,-1}}) {
                    int nx=x+d[0],nz=z+d[1];if(nx<0||nx>15||nz<0||nz>15||seen.get(nx*16+nz))continue;
                    int y=chunk.heights[nx][nz]+1;
                    if(pass(chunk.getTypeId(nx,y,nz))&&pass(chunk.getTypeId(nx,y+1,nz))){seen.set(nx*16+nz);walking.add(nx*16+nz);}
                }
            }
            for(int edge=0;edge<16;edge++)for(int key:new int[]{edge,edge*16,edge*16+15,240+edge,7*16+edge,8*16+edge,edge*16+7,edge*16+8})check(seen.get(key),"Open two-wide access lane");
        }
    }
    private static boolean pass(int id){return id==0||id==78||id==31||id==38;}
    private static int[][] copy(int[][] h){int[][] copy=new int[16][];for(int i=0;i<16;i++)copy[i]=h[i].clone();return copy;}
    private static void protections() {
        for(int x=-6;x<=6;x++)for(int z=-6;z<=6;z++)check(BiomeDetails.reserved(x,z),"Spawn square halo "+x+","+z);
        for(int px:new int[]{8,-248,264})for(int pz:new int[]{0,-256,256})for(int dx=-2;dx<=2;dx++)for(int dz=-2;dz<=2;dz++) {
            int cx=px+dx,cz=pz+dz;check(BiomeDetails.reserved(cx,cz),"Portal halo");
            TestChunk data=new TestChunk(SEED,cx,cz,70);int[] before=data.blocks.clone();
            BiomeDetails.decorate(data,new Terrain(SEED),cx,cz,data.heights);
            check(data.writes==0&&Arrays.equals(before,data.blocks),"Portal halo changed");
        }
        check(!BiomeDetails.reserved(-251,0)&&!BiomeDetails.reserved(11,0),"Halo has bounded extent");
        TestChunk spawn=new TestChunk(SEED,0,0,70);BiomeDetails.decorate(spawn,new Terrain(SEED),0,0,spawn.heights);check(spawn.writes==0,"Spawn untouched");
    }
    private static void orderAndSeeds() {
        int[][] order={{-59,-41},{-58,-41},{-59,-40},{-58,-40},{40,51},{41,51},{40,52},{41,52}};
        for(long seed:new long[]{0,-1,Long.MIN_VALUE,123456789L}) {
            Map<String,int[]> expected=new HashMap<>();
            for(int[] at:order){TestChunk c=new TestChunk(seed,at[0],at[1],70);BiomeDetails.decorate(c,new Terrain(seed),at[0],at[1],c.heights);expected.put(Arrays.toString(at),c.blocks.clone());}
            for(int i=order.length-1;i>=0;i--){int[] at=order[i];TestChunk c=new TestChunk(seed,at[0],at[1],70);BiomeDetails.decorate(c,new Terrain(seed),at[0],at[1],c.heights);check(Arrays.equals(expected.get(Arrays.toString(at)),c.blocks),"Generation order/negative chunks");}
        }
        TestChunk a=new TestChunk(1,50,-50,70),b=new TestChunk(2,50,-50,70);
        BiomeDetails.decorate(a,new Terrain(1),50,-50,a.heights);BiomeDetails.decorate(b,new Terrain(2),50,-50,b.heights);
        check(!Arrays.equals(a.blocks,b.blocks),"Seed influences detail geometry");
    }
    private static void obstructions(Map<Integer,int[]> points) {
        for(int biome:new int[]{0,9,13,16,34,39,43,44,50,61}) {
            int[] at=points.get(biome);
            for(int height:new int[]{42,55,59,61,145}) {
                TestChunk c=new TestChunk(SEED,at[0],at[1],height);int[] before=c.blocks.clone();
                BiomeDetails.decorate(c,new Terrain(SEED),at[0],at[1],c.heights);verify(c,before,BiomeDetails.MAX_BLOCKS_PER_CHUNK,false);
                check(c.writes>0,"Wet/high terrain still detailed "+biome+" h="+height);
            }
        }
        int[] at=points.get(43);TestChunk trees=new TestChunk(SEED,at[0],at[1],70);
        for(int x:new int[]{3,11})for(int z:new int[]{3,11}) {
            for(int y=71;y<=76;y++)trees.raw(x,y,z,17,1);
            for(int dx=-1;dx<=1;dx++)for(int dz=-1;dz<=1;dz++)trees.raw(x+dx,76,z+dz,18,5);
        }
        int[] before=trees.blocks.clone();BiomeDetails.decorate(trees,new Terrain(SEED),at[0],at[1],trees.heights);
        verify(trees,before,BiomeDetails.MAX_BLOCKS_PER_CHUNK,false);check(trees.writes>0,"Details still placed around trees");
        TestChunk blocked=new TestChunk(SEED,at[0],at[1],70);
        for(int x=0;x<16;x++)for(int z=0;z<16;z++)for(int y=71;y<85;y++)blocked.raw(x,y,z,201,0);
        before=blocked.blocks.clone();BiomeDetails.decorate(blocked,new Terrain(SEED),at[0],at[1],blocked.heights);
        check(blocked.writes==0&&Arrays.equals(before,blocked.blocks),"Obstructed features rejected atomically");
        TestChunk slopes=new TestChunk(SEED,at[0],at[1],-2);before=slopes.blocks.clone();
        BiomeDetails.decorate(slopes,new Terrain(SEED),at[0],at[1],slopes.heights);verify(slopes,before,BiomeDetails.MAX_BLOCKS_PER_CHUNK,false);
    }
    private static void nativeRealms() {
        Set<Integer> netherIds=new HashSet<>(),endIds=new HashSet<>();
        for(boolean nether:new boolean[]{true,false}) {
            for(int region=-24;region<=24;region++) {
                int cx=region*24+16,cz=14;
                int index=OuterRealms.profile(SEED,nether,cx*16+8,cz*16+8).index;
                Set<Integer> seen=nether?netherIds:endIds;if(!seen.add(index))continue;
                NativeFixture f=new NativeFixture(SEED,nether,cx,cz),same=new NativeFixture(SEED,nether,cx,cz);
                int[] before=f.data.blocks.clone();f.populate(1);same.populate(999999);
                check(Arrays.equals(f.data.blocks,same.data.blocks),"Native external Random independence "+index);
                verifyNative(f,before,true);check(f.biomes==256,"Native biome metadata kept on every column");
            }
            for(int material:new int[]{112,113,201,202,203,204,205,206,49,7,119,120,209,54,52,65,199,200,50,10,11}) {
                NativeFixture f=new NativeFixture(SEED,nether,80,-80);
                // Floor-level sentinel and a wall through an otherwise natural exposed floor.
                f.data.raw(3,64,3,material,0);
                for(int z=0;z<16;z++)for(int y=65;y<=73;y++)f.data.raw(8,y,z,material,0);
                f.capture();int[] before=f.data.blocks.clone();f.populate(7);verifyNative(f,before,false);
                check(f.data.getTypeId(3,64,3)==material,"Native sentinel floor protected "+material);
                for(int z=0;z<16;z++)for(int y=65;y<=73;y++)check(f.data.getTypeId(8,y,z)==material,"Native structure/portal/liquid protected "+material);
            }
            for(int[] at:new int[][]{{0,0},{6,0},{-11,-11},{11,11}}) {
                NativeFixture f=new NativeFixture(SEED,nether,at[0],at[1]);int[] before=f.data.blocks.clone();f.populate(7);
                check(Arrays.equals(before,f.data.blocks)&&f.data.writes==0,"Native arrival/dragon arena protection");
                check(f.biomes==256,"Reserved native chunk still has biome replacement");
            }
            NativeFixture ceiling=new NativeFixture(SEED,nether,80,-80);
            for(int x=0;x<16;x++)for(int z=0;z<16;z++)ceiling.data.raw(x,67,z,nether?112:201,0);
            ceiling.capture();int[] enclosed=ceiling.data.blocks.clone();ceiling.populate(7);
            check(Arrays.equals(enclosed,ceiling.data.blocks),"Natural floor beneath a native structure roof left untouched");
        }
        check(netherIds.size()==9,"All nine native Nether circle identities: "+netherIds);
        check(endIds.size()==5,"All five native End identities: "+endIds);
        System.out.println("Native checks passed: 9 Nether + 5 End profiles, 42 structure/hazard sentinels, arrival/dragon exclusions");
    }
    private static void verifyNative(NativeFixture f,int[] before,boolean requireDetail) {
        int added=0,recolored=0;
        for(int x=0;x<16;x++)for(int z=0;z<16;z++)for(int y=0;y<256;y++) {
            int k=TestChunk.key(x,y,z);if(before[k]==f.data.blocks[k])continue;
            int old=before[k]>>>4;
            if(old==0) {
                added++;check(y>64&&y<80,"Native detail vertical bounds");
                check(BiomeDetails.detailMaterial(f.data.blocks[k]>>>4),"Native safe detail palette");
                check(x>0&&x<15&&z>0&&z<15,"Native additions confined to current chunk");
            } else {recolored++;check(y==64&&old==(f.nether?87:121),"Only native natural exposed floor recolored");}
            for(int dx=-1;dx<=1;dx++)for(int dz=-1;dz<=1;dz++)for(int dy=-1;dy<=1;dy++) {
                int nx=x+dx,nz=z+dz,ny=y+dy;if(nx<0||nx>15||nz<0||nz>15||ny<0||ny>255)continue;
                int neighbor=before[TestChunk.key(nx,ny,nz)]>>>4;
                check(neighbor==0||neighbor==(f.nether?87:121),"Decoration/recolor touched a native structure boundary");
            }
        }
        check(added<=BiomeDetails.MAX_NATIVE_BLOCKS&&recolored<=768,"Native budgets");
        if(requireDetail)check(added>=3,"Native profile is decorated");
        // Added native blocks must also connect to the original natural floor, even after its palette changes.
        TestChunk additions=new TestChunk(SEED,80,-80,64);
        for(int x=0;x<16;x++)for(int z=0;z<16;z++)for(int y=65;y<128;y++) {
            int k=TestChunk.key(x,y,z);if(before[k]==0&&f.data.blocks[k]!=0){additions.blocks[k]=f.data.blocks[k];additions.writes++;}
        }
        TestChunk base=new TestChunk(SEED,80,-80,64);verify(additions,base.blocks,BiomeDetails.MAX_NATIVE_BLOCKS,false);
    }

    private static final class NativeFixture {
        final long seed;final boolean nether;final int cx,cz;final TestChunk data;
        final Chunk chunk;final World world;ChunkSnapshot snapshot;int biomes;
        NativeFixture(long seed,boolean nether,int cx,int cz) {
            this.seed=seed;this.nether=nether;this.cx=cx;this.cz=cz;data=new TestChunk(seed,cx,cz,64);
            for(int x=0;x<16;x++)for(int z=0;z<16;z++)for(int y=1;y<=64;y++)data.raw(x,y,z,nether?87:121,0);
            for(int x=0;x<16;x++)for(int z=0;z<16;z++)data.raw(x,65,z,0,0);
            capture();
            chunk=(Chunk)Proxy.newProxyInstance(Chunk.class.getClassLoader(),new Class<?>[]{Chunk.class},(proxy,method,args)->{
                switch(method.getName()) {
                    case "getX":return cx;case "getZ":return cz;case "getChunkSnapshot":return snapshot;
                    case "getBlock":return block((Integer)args[0],(Integer)args[1],(Integer)args[2]);
                    default:throw new AssertionError("Unexpected chunk API (possible neighbor load): "+method);
                }
            });
            world=(World)Proxy.newProxyInstance(World.class.getClassLoader(),new Class<?>[]{World.class},(proxy,method,args)->{
                switch(method.getName()) {
                    case "getSeed":return seed;case "getEnvironment":return nether?World.Environment.NETHER:World.Environment.THE_END;
                    case "setBiome":check(Math.floorDiv((Integer)args[0],16)==cx&&Math.floorDiv((Integer)args[1],16)==cz,"Native biome write stays in chunk");biomes++;return null;
                    default:throw new AssertionError("Forbidden native World API: "+method);
                }
            });
        }
        void capture() {
            final int[] initial=data.blocks.clone();
            snapshot=(ChunkSnapshot)Proxy.newProxyInstance(ChunkSnapshot.class.getClassLoader(),new Class<?>[]{ChunkSnapshot.class},(proxy,method,args)->{
                if(method.getName().equals("getBlockTypeId"))return initial[TestChunk.key((Integer)args[0],(Integer)args[1],(Integer)args[2])]>>>4;
                throw new AssertionError("Unexpected snapshot API: "+method);
            });
        }
        Block block(int x,int y,int z) {
            TestChunk.key(x,y,z);
            return (Block)Proxy.newProxyInstance(Block.class.getClassLoader(),new Class<?>[]{Block.class},(proxy,method,args)->{
                if(method.getName().equals("getTypeId"))return data.getTypeId(x,y,z);
                if(method.getName().equals("setTypeIdAndData")){check(Boolean.FALSE.equals(args[2]),"No physics/ticking on native placement");data.setBlock(x,y,z,(Integer)args[0],(Byte)args[1]);return true;}
                throw new AssertionError("Unexpected native Block API: "+method);
            });
        }
        void populate(long ignoredRandomSeed){new OuterRealms().populate(world,new Random(ignoredRandomSeed),chunk);}
    }
    private static final class TestChunk implements ChunkGenerator.ChunkData {
        final int[] blocks=new int[65536];final int[][] heights=new int[16][16];int writes;
        TestChunk(long seed,int cx,int cz,int height) {
            Terrain terrain=new Terrain(seed);
            for(int x=0;x<16;x++)for(int z=0;z<16;z++) {
                Terrain.Sample sample=terrain.sample(cx*16+x,cz*16+z);Catalog.Profile p=sample.profile;
                int h=height==-1?sample.y:height==-2?70+((x+z)%4)*4:height;heights[x][z]=h;
                for(int y=0;y<h-3;y++)raw(x,y,z,y==0?7:y==12?56:y==23?0:y==24?15:y==25?16:1,0);
                for(int y=h-3;y<h;y++)raw(x,y,z,p.under,p.underData);raw(x,h,z,p.surface,p.surfaceData);
                if(h<62){for(int y=h+1;y<=62;y++)raw(x,y,z,9,0);if(p.atmosphere.equals("snow"))raw(x,62,z,79,0);}
                else if(p.atmosphere.equals("snow")&&p.surface!=174)raw(x,h+1,z,78,0);
            }
        }
        static int key(int x,int y,int z){if(x<0||x>15||z<0||z>15||y<0||y>255)throw new AssertionError("Cross-chunk/out-of-bounds access "+x+","+y+","+z);return (x*16+z)*256+y;}
        void raw(int x,int y,int z,int id,int data){blocks[key(x,y,z)]=id<<4|data;}
        public int getMaxHeight(){return 256;}
        public void setBlock(int x,int y,int z,Material type){setBlock(x,y,z,type.getId(),(byte)0);}
        public void setBlock(int x,int y,int z,MaterialData type){setBlock(x,y,z,type.getItemTypeId(),type.getData());}
        public void setBlock(int x,int y,int z,int type){setBlock(x,y,z,type,(byte)0);}
        public void setBlock(int x,int y,int z,int type,byte data){writes++;raw(x,y,z,type,data&15);}
        public int getTypeId(int x,int y,int z){return blocks[key(x,y,z)]>>>4;}
        public byte getData(int x,int y,int z){return (byte)(blocks[key(x,y,z)]&15);}
        public Material getType(int x,int y,int z){return Material.getMaterial(getTypeId(x,y,z));}
        public MaterialData getTypeAndData(int x,int y,int z){return new MaterialData(getTypeId(x,y,z),getData(x,y,z));}
        public void setRegion(int a,int b,int c,int d,int e,int f,Material m){setRegion(a,b,c,d,e,f,m.getId(),0);}
        public void setRegion(int a,int b,int c,int d,int e,int f,MaterialData m){setRegion(a,b,c,d,e,f,m.getItemTypeId(),m.getData());}
        public void setRegion(int a,int b,int c,int d,int e,int f,int m){setRegion(a,b,c,d,e,f,m,0);}
        public void setRegion(int a,int b,int c,int d,int e,int f,int m,int data){for(int x=a;x<d;x++)for(int y=b;y<e;y++)for(int z=c;z<f;z++)setBlock(x,y,z,m,(byte)data);}
    }
}
