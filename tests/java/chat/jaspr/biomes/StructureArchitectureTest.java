package chat.jaspr.biomes;

import java.util.*;
import org.bukkit.Material;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.material.MaterialData;

/** Standalone JVM tests against the actual 1.12.2 ChunkData contract; never starts or loads a World. */
public final class StructureArchitectureTest {
    private static long checks;
    private static void check(boolean ok,String message){checks++;if(!ok)throw new AssertionError(message);}
    public static void main(String[] args) {
        catalog(); regions();
        int count=0,sanitizedMaterialWrites=0;Set<Long> rendered=new HashSet<>();
        for(StructureCatalog.Design d:StructureCatalog.ALL) {
            StructurePlanner.Site site=StructurePlanner.preview(8675309L,d,-407,-315);
            WorldData world=new WorldData(site);world.stamp(false);
            verifyAccess(site,world);
            check(world.writes(41)==0,"Gold storage block rendered in "+d.id);
            check(world.writes(42)==0,"Iron storage block rendered in "+d.id);
            check(world.writes(57)==0,"Diamond storage block rendered in "+d.id);
            sanitizedMaterialWrites+=world.writes(1,6);
            check(rendered.add(world.digest()),"Duplicate rendered architecture "+d.id);
            if(count%13==0||d.family.equals("plane")||d.family.equals("metropolis")) {
                WorldData reversed=new WorldData(site);reversed.stamp(true);
                check(world.same(reversed),"Chunk order changed blocks "+d.id);
            }
            check(world.type(site.x,12,site.z)==56,"Deep native ores preserved "+d.id);
            check(world.type(site.x,0,site.z)==7,"Native bedrock preserved "+d.id);
            count++;if(count%10==0)System.out.println("Architecture access/geometry verified "+count+"/"+StructureCatalog.ALL.size());
        }
        // Height extremes, changing seeds, and negative chunk boundaries exercise the actual stairs.
        for(long seed:new long[]{0,-1,Long.MIN_VALUE,123456789}) {
            for(String id:new String[]{"listening_pines_cabin","rustwater_metropolis","frostcrown_star_bastion","sunken_dome_district","spiral_prison_dungeon","split_airliner"}) {
                StructurePlanner.Site site=StructurePlanner.preview(seed,StructureCatalog.byId(id),-2049,1535);
                WorldData world=new WorldData(site);world.stamp(false);verifyAccess(site,world);
            }
        }
        check(sanitizedMaterialWrites>0,"Material sanitizer was not exercised");
        System.out.println("STRUCTURE_ARCHITECTURE_PASS designs="+count+" renderedUnique="+rendered.size()+" sanitizedMaterialWrites="+sanitizedMaterialWrites+" checks="+checks+" cache="+StructurePlanner.cachedRegions());
    }
    private static void catalog() {
        check(StructureCatalog.ALL.size()>=100,"100 designs");Set<String> ids=new HashSet<>(),shapes=new HashSet<>();
        for(StructureCatalog.Design d:StructureCatalog.ALL) {
            check(ids.add(d.id),"Unique id");shapes.add(shape(d));
            if(d.exclusive)check(d.biomes.length==1,"Exclusive design has one biome");
        }
        check(shapes.size()>=100,"100 silhouettes after ignoring names, palettes, room types, mirror and rotation: "+shapes.size());
        for(int biome=0;biome<62;biome++) {
            boolean special=false,shared=false;
            for(StructureCatalog.Design d:StructureCatalog.choices(biome)) {check(d.accepts(biome),"Biome choice");special|=d.exclusive;shared|=!d.exclusive;}
            check(special&&shared,"Exclusive and themed choices for biome "+biome);
        }
        StructureCatalog.Design d=StructureCatalog.ALL.get(0);int original=d.biomes[0];d.biomes[0]=61;
        check(d.accepts(original)&&!d.accepts(61),"Compatibility array cannot mutate planner choices");d.biomes[0]=original;
    }
    private static String shape(StructureCatalog.Design d) {
        String best=null;
        for(int flip=0;flip<2;flip++)for(int rot=0;rot<4;rot++) {
            List<int[]> points=new ArrayList<>();int minX=999,minZ=999;
            for(int rz=0;rz<d.rows;rz++)for(int rx=0;rx<d.columns;rx++)if(d.room(rx,rz)!='.') {
                int x=flip==0?rx:-rx,z=rz;
                for(int i=0;i<rot;i++){int temp=x;x=-z;z=temp;}
                minX=Math.min(minX,x);minZ=Math.min(minZ,z);points.add(new int[]{x,z});
            }
            List<String> normalized=new ArrayList<>();for(int[] p:points)normalized.add((p[0]-minX)+","+(p[1]-minZ));
            Collections.sort(normalized);String key=normalized.toString();if(best==null||key.compareTo(best)<0)best=key;
        }
        return best;
    }
    private static void regions() {
        Map<String,String> fingerprints=new HashMap<>();int sites=0;
        for(int rx=-14;rx<=14;rx++)for(int rz=-14;rz<=14;rz++) {
            StructurePlanner.Site s=StructurePlanner.region(12345,rx,rz);if(s==null)continue;sites++;
            check(s.design.accepts(new Terrain(12345).sample(s.anchorX,s.anchorZ).profile.index),"Anchor biome");
            int samples=0,wet=0,accepted=0;Terrain terrain=new Terrain(12345);
            for(StructurePlanner.Room room:s.rooms)for(int dx:new int[]{1,6,10})for(int dz:new int[]{1,6,10}) {
                Terrain.Sample point=terrain.sample(s.x+4+room.col*12+dx,s.z+4+room.row*12+dz);samples++;
                if(point.y<62)wet++;if(s.design.accepts(point.profile.index))accepted++;
            }
            check(accepted*3>=samples*2,"Majority actual room area matches biome");
            if(s.design.mode.equals("underwater"))check(wet*10>=samples*9,"Underwater footprint has water");
            fingerprints.put(rx+":"+rz,fingerprint(s));
            for(int dx=0;dx<=1;dx++)for(int dz=-1;dz<=1;dz++)if(dx!=0||dz==1) {
                StructurePlanner.Site other=StructurePlanner.region(12345,rx+dx,rz+dz);if(other==null)continue;
                check(Math.hypot(s.anchorX-other.anchorX,s.anchorZ-other.anchorZ)>=StructurePlanner.MIN_ANCHOR_SPACING,"Anchor spacing");
                check(s.x+s.width<=other.x||other.x+other.width<=s.x||s.z+s.depth<=other.z||other.z+other.depth<=s.z,"Reserved footprints overlap");
            }
            for(int cx=Math.floorDiv(s.x,16);cx<=Math.floorDiv(s.x+s.width-1,16);cx++)
                for(int cz=Math.floorDiv(s.z,16);cz<=Math.floorDiv(s.z+s.depth-1,16);cz++) {
                    check(!HorrorGenerator.portalChunk(cx,cz),"Portal sanctuary preserved");
                    // Sample corners and room-centre chunks, instead of probing every blank reserved chunk.
                    if((cx==Math.floorDiv(s.x,16)||cx==Math.floorDiv(s.x+s.width-1,16))&&(cz==Math.floorDiv(s.z,16)||cz==Math.floorDiv(s.z+s.depth-1,16))) {
                        boolean found=false;for(StructurePlanner.Site seen:StructurePlanner.sites(12345,cx,cz))found|=seen.key.equals(s.key);
                        check(found,"Intersection lookup at negative/positive bounds");
                    }
                }
        }
        check(sites>=25&&sites<=90,"Ten-percent regional occupancy "+sites);
        check(StructurePlanner.cachedRegions()<=StructurePlanner.CACHE_LIMIT,"Bounded cache including misses");
        for(int rx=14;rx>=-14;rx--)for(int rz=14;rz>=-14;rz--) {
            StructurePlanner.Site s=StructurePlanner.region(12345,rx,rz);
            check(Objects.equals(fingerprints.get(rx+":"+rz),s==null?null:fingerprint(s)),"Eviction/request order changed site");
        }
        System.out.println("Regional spacing/biome/reservation/cache verified: "+sites+" sites across 841 regions");
    }
    private static String fingerprint(StructurePlanner.Site s) {
        StringBuilder b=new StringBuilder(s.key).append(':').append(s.x).append(':').append(s.y).append(':').append(s.z);
        for(StructurePlanner.Marker m:s.markers())b.append('/').append(m.kind).append(':').append(m.ordinal).append(':').append(m.x).append(':').append(m.y).append(':').append(m.z);
        return b.toString();
    }
    private static void verifyAccess(StructurePlanner.Site s,WorldData w) {
        String label=s.design.id+" seed="+s.seed;
        // Start at the far surface landing, then flood only two-block-high walkable spaces with <=1 rises.
        int last=s.approach.length-1,ex=s.entryX(),ez=s.entryZ()+s.approach.length,ey=s.approach[last]+1;
        check(ey>=64,"Surface landing "+label);
        check(w.walk(ex,ey,ez),"Entrance landing obstructed "+label);
        BitSet seen=new BitSet(s.width*s.depth*256);ArrayDeque<Integer> todo=new ArrayDeque<>();
        int start=encode(s,ex,ey,ez);seen.set(start);todo.add(start);
        while(!todo.isEmpty()) {
            int n=todo.remove(),y=n%256,x=n/256%s.width+s.x,z=n/256/s.width+s.z;
            for(int[] dir:StructureCatalog.DIRS)for(int dy:new int[]{0,1,-1}) {
                int nx=x+dir[0],ny=y+dy,nz=z+dir[1];
                if(nx<s.x||nx>=s.x+s.width||nz<s.z||nz>=s.z+s.depth||ny<1||ny>246)continue;
                int at=encode(s,nx,ny,nz);if(seen.get(at))continue;
                if(w.walk(nx,ny,nz)&&(dy<=0||pass(w.type(x,y+2,z)))) {seen.set(at);todo.add(at);}
            }
        }
        int boss=0,mob=0,ordinal=0;
        for(StructurePlanner.Room room:s.rooms)for(int floor=0;floor<StructureCatalog.floors(room.type);floor++) {
            int x=s.x+4+room.col*12+6,z=s.z+4+room.row*12+6,y=room.floor+floor*6+1;
            check(seen.get(encode(s,x,y,z)),"Unreachable room/storey "+room.col+","+room.row+" floor="+floor+" "+label);
        }
        for(StructurePlanner.Marker m:s.markers()) {
            check(m.ordinal==ordinal++,"Stable ordinals");
            if(m.kind.equals("boss")||m.kind.equals("mob")) {
                check(w.type(m.x,m.y,m.z)==0&&w.type(m.x,m.y+1,m.z)==0,"Mob feet/head AIR "+label);
                check(seen.get(encode(s,m.x,m.y,m.z)),"Mob/boss inaccessible "+label);
                if(m.kind.equals("boss"))boss++;else mob++;
            } else if(m.kind.equals("door")) {
                check(w.type(m.x,m.y,m.z)==201,"Clickable purpur marker "+label);
                check(seen.get(encode(s,m.x,m.y+1,m.z)),"Door inaccessible "+label);
            } else {
                check(w.type(m.x,m.y,m.z)==54&&w.type(m.x,m.y+1,m.z)==0,"Exact usable chest coordinate "+label);
                boolean reach=false;for(int[] dir:StructureCatalog.DIRS)reach|=seen.get(encode(s,m.x+dir[0],m.y,m.z+dir[1]));
                check(reach,"Chest inaccessible "+m.kind+" "+label);
            }
        }
        check(boss==(s.design.tier>=3?1:0)&&mob<=7,"Bounded encounters "+label);
        int unsafe=0;for(TestChunk c:w.chunks.values())for(short raw:c.blocks)if(raw>=0&&(raw>>>4)==209)unsafe++;
        check(unsafe==0,"No unsafe END_GATEWAY");
        if(s.design.mode.equals("underwater"))verifyWaterSeal(s,w);
    }
    private static void verifyWaterSeal(StructurePlanner.Site s,WorldData w) {
        BitSet water=new BitSet(s.width*s.depth*64);ArrayDeque<Integer> todo=new ArrayDeque<>();
        for(int x=s.x;x<s.x+s.width;x++)for(int z=s.z;z<s.z+s.depth;z++)for(int y=20;y<=62;y++)if(pass(w.type(x,y,z))) {
            boolean source=w.type(x,y+1,z)==9;
            for(int[] d:StructureCatalog.DIRS)source|=w.type(x+d[0],y,z+d[1])==9;
            if(source){int at=((z-s.z)*s.width+x-s.x)*64+y;water.set(at);todo.add(at);}
        }
        while(!todo.isEmpty()) {
            int n=todo.remove(),y=n%64,x=n/64%s.width+s.x,z=n/64/s.width+s.z;
            for(int[] d:new int[][]{{1,0,0},{-1,0,0},{0,0,1},{0,0,-1},{0,-1,0}}) {
                int nx=x+d[0],ny=y+d[1],nz=z+d[2];if(nx<s.x||nx>=s.x+s.width||nz<s.z||nz>=s.z+s.depth||ny<20)continue;
                int at=((nz-s.z)*s.width+nx-s.x)*64+ny;
                if(!water.get(at)&&pass(w.type(nx,ny,nz))){water.set(at);todo.add(at);}
            }
        }
        for(StructurePlanner.Room room:s.rooms) {
            int x=s.x+4+room.col*12+6,z=s.z+4+room.row*12+6,y=room.floor+1;
            check(!water.get(((z-s.z)*s.width+x-s.x)*64+y),"Underwater room leaks "+s.design.id+" "+room.col+","+room.row);
        }
    }
    private static int encode(StructurePlanner.Site s,int x,int y,int z){return ((z-s.z)*s.width+(x-s.x))*256+y;}
    private static boolean pass(int id){return id==0||id==50||id==38||id==31||id==78||id==69||id==77;}
    private static boolean solid(int id){return id!=0&&id!=8&&id!=9&&id!=10&&id!=11&&!pass(id)&&id!=85&&id!=101&&id!=198;}
    private static final class WorldData {
        final StructurePlanner.Site site;final Map<Long,TestChunk> chunks=new HashMap<>();
        WorldData(StructurePlanner.Site site){this.site=site;}
        TestChunk chunk(int cx,int cz){long key=((long)cx<<32)^(cz&0xffffffffL);TestChunk c=chunks.get(key);if(c==null){c=new TestChunk(site.seed,cx,cz);chunks.put(key,c);}return c;}
        int type(int x,int y,int z){return chunk(Math.floorDiv(x,16),Math.floorDiv(z,16)).getTypeId(Math.floorMod(x,16),y,Math.floorMod(z,16));}
        boolean walk(int x,int y,int z){return pass(type(x,y,z))&&pass(type(x,y+1,z))&&solid(type(x,y-1,z));}
        void stamp(boolean reverse){List<int[]> order=new ArrayList<>();for(int cx=Math.floorDiv(site.x,16);cx<=Math.floorDiv(site.x+site.width-1,16);cx++)for(int cz=Math.floorDiv(site.z,16);cz<=Math.floorDiv(site.z+site.depth-1,16);cz++)order.add(new int[]{cx,cz});if(reverse)Collections.shuffle(order,new Random(999));for(int[] at:order)site.stamp(chunk(at[0],at[1]),at[0],at[1]);}
        boolean same(WorldData other){for(Map.Entry<Long,TestChunk> c:chunks.entrySet()){TestChunk o=other.chunks.get(c.getKey());if(o==null||!Arrays.equals(c.getValue().blocks,o.blocks))return false;}return true;}
        long digest(){long value=0;for(Map.Entry<Long,TestChunk> c:chunks.entrySet())value+=Terrain.mix(c.getKey()+Arrays.hashCode(c.getValue().blocks));return value;}
        int writes(int id){int count=0;for(TestChunk c:chunks.values())for(short raw:c.blocks)if(raw>=0&&(raw>>>4)==id)count++;return count;}
        int writes(int id,int data){int count=0;for(TestChunk c:chunks.values())for(short raw:c.blocks)if(raw>=0&&(raw>>>4)==id&&(raw&15)==data)count++;return count;}
    }
    private static final class TestChunk implements ChunkGenerator.ChunkData {
        final short[] blocks=new short[65536];final Terrain.Sample[] ground=new Terrain.Sample[256];final Terrain terrain;final int cx,cz;
        TestChunk(long seed,int cx,int cz){this.cx=cx;this.cz=cz;terrain=new Terrain(seed);Arrays.fill(blocks,(short)-1);}
        private int index(int x,int y,int z){if(x<0||x>=16||z<0||z>=16||y<0||y>=256)throw new AssertionError("Cross-chunk/out-of-height write or read "+x+","+y+","+z);return (x*16+z)*256+y;}
        public int getMaxHeight(){return 256;}
        public void setBlock(int x,int y,int z,Material type){setBlock(x,y,z,type.getId(),(byte)0);}
        public void setBlock(int x,int y,int z,MaterialData data){setBlock(x,y,z,data.getItemTypeId(),data.getData());}
        public void setBlock(int x,int y,int z,int type){setBlock(x,y,z,type,(byte)0);}
        public void setBlock(int x,int y,int z,int type,byte data){blocks[index(x,y,z)]=(short)(type<<4|(data&15));}
        public int getTypeId(int x,int y,int z){short stored=blocks[index(x,y,z)];if(stored>=0)return stored>>>4;Terrain.Sample g=ground[x*16+z];if(g==null)ground[x*16+z]=g=terrain.sample(cx*16+x,cz*16+z);if(y==0)return 7;if(y==12)return 56;if(y<g.y-3)return 1;if(y<g.y)return g.profile.under;if(y==g.y)return g.profile.surface;return y<=62?9:0;}
        public byte getData(int x,int y,int z){short s=blocks[index(x,y,z)];return s<0?0:(byte)(s&15);}
        public Material getType(int x,int y,int z){return Material.getMaterial(getTypeId(x,y,z));}
        public MaterialData getTypeAndData(int x,int y,int z){return new MaterialData(getTypeId(x,y,z),getData(x,y,z));}
        public void setRegion(int a,int b,int c,int d,int e,int f,Material m){setRegion(a,b,c,d,e,f,m.getId(),0);}
        public void setRegion(int a,int b,int c,int d,int e,int f,MaterialData m){setRegion(a,b,c,d,e,f,m.getItemTypeId(),m.getData());}
        public void setRegion(int a,int b,int c,int d,int e,int f,int m){setRegion(a,b,c,d,e,f,m,0);}
        public void setRegion(int a,int b,int c,int d,int e,int f,int m,int data){for(int x=a;x<d;x++)for(int y=b;y<e;y++)for(int z=c;z<f;z++)setBlock(x,y,z,m,(byte)data);}
    }
}
