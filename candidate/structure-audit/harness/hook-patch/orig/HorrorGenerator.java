package chat.jaspr.biomes;

import java.util.*;
import org.bukkit.*;
import org.bukkit.generator.*;

/** Full overworld replacement. Regional sites stamp only the currently generated chunk. */
public final class HorrorGenerator extends ChunkGenerator {
    @Override public ChunkData generateChunkData(World w,Random ignored,int cx,int cz,BiomeGrid biomes){
        Terrain t=new Terrain(w.getSeed());ChunkData d=createChunkData(w);int[][] heights=new int[16][16];boolean[][] sea=new boolean[16][16];
        Caves caves=new Caves(t);SurfaceOpenings openings=new SurfaceOpenings(t,caves);
        SurfaceOpenings.Cut cut=new SurfaceOpenings.Cut();
        double[][][] field=new double[Caves.LAT_XZ][Caves.LAT_XZ][Caves.LAT_Y];
        int[][] lattice=new int[Caves.LAT_XZ][Caves.LAT_XZ];
        caves.sampleChunk(cx,cz,field,lattice);
        for(int x=0;x<16;x++)for(int z=0;z<16;z++){
            int wx=cx*16+x,wz=cz*16+z;Terrain.Sample s=t.sample(wx,wz);Catalog.Profile p=s.profile;int h=s.y;heights[x][z]=h;
            biomes.setBiome(x,z,p.slot);d.setRegion(x,0,z,x+1,1,z+1,Material.BEDROCK);
            d.setRegion(x,1,z,x+1,h-3,z+1,Material.STONE);
            d.setRegion(x,h-3,z,x+1,h,z+1,p.under,p.underData);d.setBlock(x,h,z,p.surface,(byte)p.surfaceData);
            // Open the sky into the rock before carving, so a ravine's walls expose
            // whatever caves run behind them instead of stopping at a sealed surface.
            int style=Caves.styleAt(lattice,x,z),lava=Caves.lavaLevel(style);
            int ground=h;
            // Below sea level the column is ocean floor. Nothing may be cut into it:
            // a trench through a sea bed is a dry trench with a wall of water beside
            // it, because generated water is still water and still water never flows.
            boolean submerged=h<62;sea[x][z]=submerged;
            openings.sample(wx,wz,h,cut);
            if(submerged){cut.reset();}
            int openFloor=-1;
            if(cut.depth>0){
                int floor=Math.max(6,h-cut.depth);
                for(int y=floor+1;y<=h;y++)d.setBlock(x,y,z,0,(byte)0);
                int[] bed=openingFloor(cut.style,p);
                d.setBlock(x,floor,z,bed[0],(byte)bed[1]);
                openFloor=floor;
                ground=floor;
            } else if(cut.rise>0){
                int[] spoil=openingRim(cut.style,p);
                for(int y=h+1;y<=Math.min(250,h+cut.rise);y++)d.setBlock(x,y,z,spoil[0],(byte)spoil[1]);
                ground=Math.min(250,h+cut.rise);
                d.setBlock(x,ground,z,p.surface,(byte)p.surfaceData);
            }
            heights[x][z]=ground;
            double breach=caves.breach(wx,wz);
            // Up to and including the surface block. Stopping one short leaves a
            // single block of turf roofing every breach, which is precisely how
            // caves stayed invisible from above no matter how many there were.
            // Under an ocean the carve stops three blocks short of the sea bed, so the
            // water above always keeps a floor. This is what vanilla does, and skipping
            // it is what left whole seas hanging over open caves.
            int top=Math.min(Caves.CEILING,submerged?h-3:ground);
            for(int y=Caves.FLOOR;y<=top;y++){
                double damp=Caves.surfaceDamping(y,ground,breach);
                if(damp<=0)continue;
                if(Caves.at(field,x,y,z)*damp<=0)continue;
                d.setBlock(x,y,z,y<lava?Material.STATIONARY_LAVA:Material.AIR);
            }
            // The pool goes in only now. Filling before the carve meant the carve could
            // punch the floor out from under the water it had just placed, which is
            // exactly the curtain of water left hanging in mid-air.
            // -- unless the carve has hollowed out the rock beneath the floor, in
            // which case there is nothing for a floor to rest on and the opening
            // simply falls through into the cave below. Re-laying the bed there
            // makes a one-block lid hanging over the void: the crater floors and
            // collapse slabs found floating over caverns.
            if(openFloor>=0&&d.getTypeId(x,openFloor-1,z)!=0){
                int[] bed=openingFloor(cut.style,p);
                d.setBlock(x,openFloor,z,bed[0],(byte)bed[1]);
                if(cut.fill!=0)for(int y=openFloor+1;y<=Math.min(cut.fillTop,h);y++)d.setBlock(x,y,z,cut.fill,(byte)0);
            } else if(openFloor>=0){
                d.setBlock(x,openFloor,z,0,(byte)0);
            }
            if(h<62&&cut.depth==0){d.setRegion(x,h+1,z,x+1,63,z+1,Material.STATIONARY_WATER);if(p.atmosphere.equals("snow"))d.setBlock(x,62,z,Material.ICE);}
            else if(cut.depth>0){ }
            // The dressing below goes on the surface block. If the carve has just taken
            // the surface block, there is nothing to dress, and dressing the empty air
            // where it used to be is a grass block hanging over a pit.
            else if(d.getTypeId(x,h,z)==0){ }
            else if(p.atmosphere.equals("snow")&&p.surface!=174)d.setBlock(x,h+1,z,Material.SNOW);
            // Occasional ordinary grass patches let seeds, animals and sustainable farms remain possible.
            else if(p.tree.matches("pine|ancient|scrub|swamp")&&t.random(wx,wz,51)<.08){d.setBlock(x,h,z,Material.GRASS);if(t.random(wx,wz,52)<.5)d.setBlock(x,h+1,z,31,(byte)1);}
            else if(p.tree.equals("blossom")&&t.random(wx,wz,54)<.10)d.setBlock(x,h+1,z,38,(byte)4);
        }
        // Before the ore and the decoration go in, so neither is scoured off a wall
        // that was only ever grit in the first place.
        Floaters.Apron apron=apron(t,caves,openings,field,lattice,cx,cz);
        if(!Boolean.getBoolean("jaspr.noprune"))Floaters.despeckle(d,apron);
        OreVeins.stamp(d,t,caves,cx,cz);
        CaveDecor.decorate(d,t,caves,cx,cz,heights,lattice);
        settle(d,sea);
        // Everything the carve orphaned comes down here, and heights[][] is restated
        // from what is actually left standing. Trees, ground cover and litter are all
        // placed on heights[][], so a stale entry is a tree planted on a hole.
        if(!Boolean.getBoolean("jaspr.noprune"))Floaters.prune(d,heights,apron);
        if(Boolean.getBoolean("jaspr.audit")){int a=Floaters.audit(d);if(a>0)Bukkit.getLogger().info("[AUDIT] afterPrune="+a+" chunk="+cx+","+cz);}
        // Springs, already run to rest. Nothing here is left for the server to tick.
        if(!Boolean.getBoolean("jaspr.nosprings"))CaveSprings.pour(d,t,cx,cz);
        Catalog.Profile center=t.sample(cx*16+8,cz*16+8).profile;
        if(portalChunk(cx,cz)){sanctuary(d,heights);}
        else{
            Random r=new Random(Terrain.mix(w.getSeed()+cx*91337L+cz*19777L));
            for(int k=0;k<center.density;k++){
                int x=3+r.nextInt(10),z=3+r.nextInt(10),y=heights[x][z]+1;
                if(y<=63||d.getTypeId(x,y,z)!=0&&d.getTypeId(x,y,z)!=78)continue;
                if(Floaters.loose(d.getTypeId(x,y-1,z)))continue;      // nothing to stand on
                if(roofed(d,x,y,z))continue;                           // a cave floor, not a clearing
                if(!Boolean.getBoolean("jaspr.notrees"))tree(d,x,y,z,center.tree,r);
            }
            if(Boolean.getBoolean("jaspr.audit")){int a=Floaters.audit(d);if(a>0)Bukkit.getLogger().info("[AUDIT] afterTrees="+a+" chunk="+cx+","+cz);}
            BiomeDetails.decorate(d,t,cx,cz,heights);
            if(Boolean.getBoolean("jaspr.audit")){int a=Floaters.audit(d);if(a>0)Bukkit.getLogger().info("[AUDIT] afterDetails="+a+" chunk="+cx+","+cz);}
            if(!Boolean.getBoolean("jaspr.nosites"))for(StructurePlanner.Site site:WorldgenExpansion.sites(w,cx,cz))site.stamp(d,cx,cz);
        }
        return d;
    }
    /**
     * The ring of columns one block past the chunk's edge, worked out the same way
     * the neighbouring chunk will work them out when it is built: terrain to the
     * heightmap, the opening cut, the cave carve. Nothing is written; this is only
     * so the clean-up passes can tell a block on the border that is leaning on real
     * rock next door from one that is hanging over the same pit. Decorations are
     * not modelled -- they sit on terrain, they do not hold it up.
     */
    private static Floaters.Apron apron(Terrain t,Caves caves,SurfaceOpenings openings,double[][][] field,int[][] lattice,int cx,int cz){
        final boolean[][] ring=new boolean[68][256];
        SurfaceOpenings.Cut cut=new SurfaceOpenings.Cut();
        for(int x=-1;x<=16;x++)for(int z=-1;z<=16;z++){
            int slot=ringSlot(x,z);if(slot<0)continue;
            boolean[] col=ring[slot];
            int wx=cx*16+x,wz=cz*16+z;
            Terrain.Sample s=t.sample(wx,wz);int h=s.y;
            int style=Caves.styleAt(lattice,x,z);
            boolean submerged=h<62;
            openings.sample(wx,wz,h,cut);if(submerged)cut.reset();
            int ground=h,floor=-1;
            if(cut.depth>0){floor=Math.max(6,h-cut.depth);ground=floor;}
            else if(cut.rise>0)ground=Math.min(250,h+cut.rise);
            for(int y=0;y<=ground&&y<256;y++)col[y]=true;
            if(floor>=0)for(int y=floor+1;y<=h&&y<256;y++)col[y]=false;
            double breach=caves.breach(wx,wz);
            int top=Math.min(Caves.CEILING,submerged?h-3:ground);
            for(int y=Caves.FLOOR;y<=top;y++){
                double damp=Caves.surfaceDamping(y,ground,breach);
                if(damp<=0)continue;
                if(Caves.at(field,x,y,z)*damp<=0)continue;
                col[y]=false;
            }
            if(floor>=0)col[floor]=col[floor-1];
            col[0]=true;
        }
        // Rock past the edge only counts if it is itself grounded: connected down to
        // bedrock through the ring, either straight down its own column or along the
        // ring to a column that is. A crater rim heaped over a cave, with the cave
        // bitten out just beneath it, is rock all the way along the seam on both
        // sides -- and each chunk was keeping its half on the word of the other.
        final boolean[][] support=new boolean[68][256];
        int[] stack=new int[68*256];int sp=0;
        for(int slot=0;slot<68;slot++)for(int y=0;y<=4;y++)if(ring[slot][y]){support[slot][y]=true;stack[sp++]=slot*256+y;}
        while(sp>0){
            int cell=stack[--sp],slot=cell/256,y=cell%256;
            int[][] next={{slot,y+1},{slot,y-1},{ringNeighbour(slot,-1),y},{ringNeighbour(slot,1),y}};
            for(int[] n:next){
                if(n[0]<0||n[1]<0||n[1]>255)continue;
                if(!ring[n[0]][n[1]]||support[n[0]][n[1]])continue;
                support[n[0]][n[1]]=true;stack[sp++]=n[0]*256+n[1];
            }
        }
        return new Floaters.Apron(){public boolean rock(int x,int y,int z){
            int slot=ringSlot(x,z);
            if(slot<0||y<0||y>255)return false;
            return support[slot][y];
        }};
    }
    /** The slot next along the same edge, or -1 at the end of it. */
    private static int ringNeighbour(int slot,int dir){
        int n=slot+dir;
        if(slot<18)return n>=0&&n<18?n:-1;
        if(slot<36)return n>=18&&n<36?n:-1;
        if(slot<52)return n>=36&&n<52?n:-1;
        return n>=52&&n<68?n:-1;
    }
    private static int ringSlot(int x,int z){
        if(x==-1)return z+1;
        if(x==16)return 18+z+1;
        if(z==-1&&x>=0&&x<=15)return 36+x;
        if(z==16&&x>=0&&x<=15)return 52+x;
        return -1;
    }
    /** What the bottom of each kind of opening is floored with. */
    private static int[] openingFloor(int style,Catalog.Profile p){
        switch(style){
            case SurfaceOpenings.CREVASSE: return new int[]{174,0};      // packed ice
            case SurfaceOpenings.CIRQUE:   return new int[]{80,0};       // snow
            case SurfaceOpenings.CALDERA:  return new int[]{87,0};       // netherrack
            case SurfaceOpenings.CENOTE:   return new int[]{13,0};       // gravel
            case SurfaceOpenings.COLLAPSE: return new int[]{48,0};       // mossy cobble
            case SurfaceOpenings.CRATER:   return new int[]{4,0};        // cobble
            case SurfaceOpenings.SINKHOLE: return new int[]{13,0};
            default:                       return new int[]{1,0};        // bare stone
        }
    }
    /** And what the spoil heaped on its lip is made of. */
    private static int[] openingRim(int style,Catalog.Profile p){
        switch(style){
            case SurfaceOpenings.CALDERA: return new int[]{87,0};
            case SurfaceOpenings.CRATER:  return new int[]{4,0};
            case SurfaceOpenings.CIRQUE:  return new int[]{80,0};
            default:                      return new int[]{p.under,p.underData};
        }
    }
    /**
     * Drains liquid the terrain cannot actually hold.
     *
     * Generated liquid is placed in its still form and never receives a block
     * update, so it keeps whatever shape the generator left it in for good: a
     * sheet hanging over a cave the carve opened underneath it, a curtain down
     * the wall of a ravine, a single block stranded in mid-air. Vanilla never
     * shows this because vanilla never puts water anywhere it would have to run.
     *
     * Two rules, applied until nothing moves. A block with air beneath it falls.
     * A block whose neighbour is an open drop -- air with air below it -- pours
     * out sideways and is gone as well. Sweeping upward lets both cascade, so an
     * entire stranded column clears in one pass instead of one block per pass.
     *
     * Sea columns are exempt. An ocean is held up by nothing but its own extent,
     * and the second rule, let loose on one, would empty it a column at a time.
     */
    private static void settle(ChunkData d,boolean[][] sea){
        int budget=4096;
        for(int pass=0;pass<6&&budget>0;pass++){
            boolean moved=false;
            for(int y=2;y<=96;y++)for(int x=0;x<16;x++)for(int z=0;z<16;z++){
                int id=d.getTypeId(x,y,z);
                if(id!=8&&id!=9&&id!=10&&id!=11)continue;
                boolean spill=d.getTypeId(x,y-1,z)==0;
                if(!spill&&!sea[x][z])
                    spill=leak(d,x-1,z,y)||leak(d,x+1,z,y)||leak(d,x,z-1,y)||leak(d,x,z+1,y);
                if(!spill)continue;
                d.setBlock(x,y,z,0,(byte)0);moved=true;
                if(--budget<=0)return;
            }
            if(!moved)return;
        }
    }

    /** Is the column beside this one an open drop rather than a wall? */
    private static boolean leak(ChunkData d,int x,int z,int y){
        if(x<0||x>15||z<0||z>15)return false;
        return d.getTypeId(x,y,z)==0&&d.getTypeId(x,y-1,z)==0;
    }

    /** Is there rock overhead? A breach can leave real ground forty blocks underground. */
    private static boolean roofed(ChunkData d,int x,int y,int z){
        int ceiling=Math.min(d.getMaxHeight()-1,y+40);
        for(int k=y+1;k<=ceiling;k++)if(!Floaters.loose(d.getTypeId(x,k,z)))return true;
        return false;
    }
    public static boolean portalChunk(int cx,int cz){return Math.floorMod(cx,256)==8&&Math.floorMod(cz,256)==0;}
    private void tree(ChunkData d,int x,int y,int z,String style,Random r){
        if(style.equals("none"))return;
        if(style.equals("cactus")){if(d.getTypeId(x,y-1,z)!=12)d.setBlock(x,y-1,z,12,(byte)1);for(int i=0;i<2+r.nextInt(3);i++)d.setBlock(x,y+i,z,Material.CACTUS);return;}
        int h=4+r.nextInt(style.equals("ancient")?10:5),trunk=17,data=style.equals("pine")?1:0;
        if(style.equals("pine"))h=7+r.nextInt(5);
        if(style.equals("scrub"))h=2+r.nextInt(3);
        if(style.equals("petrified")){trunk=1;data=5;}if(style.equals("crystal")){trunk=159;data=13;h=2+r.nextInt(5);}
        for(int i=0;i<h;i++)d.setBlock(x,y+i,z,trunk,(byte)data);
        // Forks at different elevations replace the repeated straight fencepost silhouette.
        int orientation=r.nextInt(4),branches=style.equals("scrub")?2:3;
        for(int branch=0;branch<branches;branch++){
            int dir=(orientation+branch)%4,dx=dir==0?1:dir==2?-1:0,dz=dir==1?1:dir==3?-1:0;
            int base=y+Math.max(1,h-3-branch%2),len=1+r.nextInt(2),rise=base;
            for(int n=1;n<=len;n++){
                int by=base+n/2;
                // Step out first, then up. Going out and up in one move leaves the far
                // block touching the branch only at a corner, which is not a join at
                // all: the tip hangs in the air with a gap you can see through.
                if(by!=rise)block(d,x+dx*n,rise,z+dz*n,trunk,trunk==17?(dx!=0?data|4:data|8):data);
                block(d,x+dx*n,by,z+dz*n,trunk,trunk==17?(dx!=0?data|4:data|8):data);
                if(n==len)block(d,x+dx*n,by+1,z+dz*n,trunk,data);
                rise=by;
            }
        }
        if(style.equals("ancient")||style.equals("swamp"))for(int dir=0;dir<4;dir++){
            int dx=dir==0?1:dir==2?-1:0,dz=dir==1?1:dir==3?-1:0;
            for(int dy=0;dy<=1;dy++)if(d.getTypeId(x+dx,y+dy,z+dz)==0)block(d,x+dx,y+dy,z+dz,trunk,data);
        }
        if(style.matches("dead|thorn|petrified|crystal"))return;
        if(style.equals("frost")){
            block(d,x,y+h,z,80,0);block(d,x-1,y+h-1,z,174,0);block(d,x+1,y+h-2,z,174,0);
            if(r.nextBoolean())block(d,x,y+h-3,z+1,80,0);return;
        }
        if(style.equals("pine")){
            for(int layer=0;layer<6;layer++){
                int radius=layer<2?3:layer<4?2:1;
                for(int a=-radius;a<=radius;a++)for(int b=-radius;b<=radius;b++)
                    if(Math.abs(a)+Math.abs(b)<=radius+1&&(a!=0||b!=0)&&r.nextInt(12)!=0)leaf(d,x+a,y+h-6+layer,z+b,18,5);
            }
            leaf(d,x,y+h,z,18,5);return;
        }
        int radius=style.equals("ancient")?3:2;
        for(int a=-radius;a<=radius;a++)for(int b=-radius;b<=radius;b++)for(int c=-1;c<=1;c++){
            if(Math.abs(a)+Math.abs(b)+Math.abs(c)>radius+2||r.nextInt(9)==0)continue;
            int block=18,leaf=(style.equals("pine")?1:0)|4;
            if(style.equals("red")){block=159;leaf=14;}if(style.equals("blossom")){block=159;leaf=r.nextBoolean()?6:2;}
            if(style.equals("frost")){block=80;leaf=0;}
            leaf(d,x+a,y+h+c,z+b,block,leaf);
        }
    }
    private static void leaf(ChunkData d,int x,int y,int z,int id,int data){if(x>=0&&x<16&&z>=0&&z<16&&y>0&&y<250&&d.getTypeId(x,y,z)==0)d.setBlock(x,y,z,id,(byte)data);}
    private static void block(ChunkData d,int x,int y,int z,int id,int data){if(x>=0&&x<16&&z>=0&&z<16&&y>0&&y<250)d.setBlock(x,y,z,id,(byte)data);}
    private void sanctuary(ChunkData d,int[][] ground){
        int base=Math.max(65,ground[8][8]);
        // Preserve the original twelve unfilled frames and obsidian basin independently of rare sites.
        for(int x=3;x<=12;x++)for(int z=3;z<=12;z++){
            int g=ground[x][z];for(int y=Math.min(g,base)-2;y<=base;y++)block(d,x,y,z,98,2);
            for(int y=base+1;y<=base+6;y++)block(d,x,y,z,0,0);
        }
        for(int x=3;x<=12;x++)for(int z=3;z<=12;z++)if(x==3||x==12||z==3||z==12)for(int y=1;y<=3;y++)block(d,x,base+y,z,98,2);
        for(int i=6;i<=8;i++){block(d,i,base+1,5,120,0);block(d,i,base+1,9,120,2);block(d,5,base+1,i,120,3);block(d,9,base+1,i,120,1);}
        for(int x=6;x<=8;x++)for(int z=6;z<=8;z++)block(d,x,base,z,49,0);
        for(int y=1;y<=3;y++){block(d,7,base+y,3,0,0);block(d,8,base+y,3,0,0);}
    }
    @Override public Location getFixedSpawnLocation(World w,Random r){return new Location(w,.5,new Terrain(w.getSeed()).sample(0,0).y+1,.5);}
    @Override public boolean canSpawn(World w,int x,int z){return new Terrain(w.getSeed()).sample(x,z).y>=64;}
    @Override public List<BlockPopulator> getDefaultPopulators(World w){return Collections.singletonList(new RuinSupplies());}
}
