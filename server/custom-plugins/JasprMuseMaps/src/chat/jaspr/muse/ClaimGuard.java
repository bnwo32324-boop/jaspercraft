package chat.jaspr.muse;

import chat.jaspr.biomes.Terrain;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

/** JasprMuseMaps copy of JasprImportedWorldgen 1.2.x ClaimGuard (same rules; Design/Pack.Blocks in place of
 * SiteSpec/Occupancy). Exact lattice-footprint exclusion for the pre-existing Claude set pieces and rooms.
 * API drift is fatal: imported generation must not guess around another agent's work. */
public final class ClaimGuard {
    private final int[] mc,mx,mz,mh;
    private final long[] ms;
    private final Method fits,claimed,baseY;
    private final int[] dc,dx,dz,dh;
    private final long[] da,db;
    private final boolean[] surface;
    private final Method dungeonAnchor,dungeonSurface;
    private final Method portalChunk;
    private final Field ax,ay,az;
    /* 1.1.0: HorrorBiomes 3.25.0's extra sites (secondary set-piece lattices, the third room lattice, the new
     * sanctuaries) and the census fix: imported sites keep off set pieces and rooms in plan view. */
    private final int[] mc2,dc2; private final long[] ms2,dsc; private final int[] mm;
    private final Method secondaryBase,newSanctuary;
    /* 1.2.0: where each buried room's ladder shaft comes up (Dungeons.ENTRY: room-relative dx, dz, ...). */
    private final int[][] entry;

    public ClaimGuard() throws ReflectiveOperationException {
        Class<?> m=Class.forName("chat.jaspr.biomes.Megaliths");
        mc=ints(m,"C_CELL");mx=ints(m,"C_SX");mz=ints(m,"C_SZ");mh=ints(m,"C_HEIGHT");ms=longs(m,"C_SALT");
        if(mc.length!=62||mx.length!=62||mz.length!=62||mh.length!=62||ms.length!=62)
            throw new ReflectiveOperationException("Megalith claim schema changed");
        fits=method(m,"fits",Terrain.class,int.class,int.class,int.class);
        claimed=method(m,"claimed",Terrain.class,int.class,int.class,int.class,int.class,int.class);
        baseY=method(m,"baseY",Terrain.class,int.class,int.class,int.class);
        Class<?> d=Class.forName("chat.jaspr.biomes.Dungeons");
        dc=ints(d,"D_CELL");dx=ints(d,"D_SX");dz=ints(d,"D_SZ");dh=ints(d,"D_SY");
        da=longs(d,"D_SALT_A");db=longs(d,"D_SALT_B");surface=booleans(d,"D_SURFACE");
        if(dc.length!=14||dx.length!=14||dz.length!=14||dh.length!=14||da.length!=14||db.length!=14||surface.length!=14)
            throw new ReflectiveOperationException("Dungeon claim schema changed");
        dungeonAnchor=method(d,"anchor",Terrain.class,int.class,int.class,int.class,long.class,int.class,int.class);
        dungeonSurface=method(d,"surfaceAnchor",Terrain.class,int.class,int.class,int.class,long.class,int.class,int.class);
        mc2=ints(m,"C_CELL2");ms2=longs(m,"C_SALT2");mm=ints(m,"C_MODE");
        if(mc2.length!=62||ms2.length!=62||mm.length!=62) throw new ReflectiveOperationException("Secondary claim schema changed");
        secondaryBase=method(m,"secondaryBase",Terrain.class,int.class,int.class,int.class);
        dc2=ints(d,"D_CELL_C");dsc=longs(d,"D_SALT_C");
        if(dc2.length!=14||dsc.length!=14) throw new ReflectiveOperationException("Third room lattice schema changed");
        newSanctuary=method(Class.forName("chat.jaspr.biomes.StructureRates"),"sanctuary",Terrain.class,int.class,int.class);
        entry=(int[][])field(d,"ENTRY").get(null);
        for(int i=0;i<surface.length;i++)
            if(!surface[i]&&(i>=entry.length||entry[i]==null||entry[i].length<2||entry[i][0]<0||entry[i][0]>=dx[i]||entry[i][1]<0||entry[i][1]>=dz[i]))
                throw new ReflectiveOperationException("Buried room entrance schema changed");
        Class<?> a=Class.forName("chat.jaspr.biomes.Dungeons$Anchor");
        ax=field(a,"x");ay=field(a,"y");az=field(a,"z");
        // HorrorGenerator stamps a portal sanctuary at (8, 0) modulo 256 chunks.
        // Keep this schema check fail-closed if its placement rule ever changes.
        Class<?> generator=Class.forName("chat.jaspr.biomes.HorrorGenerator");
        portalChunk=method(generator,"portalChunk",int.class,int.class);
        if(!Boolean.TRUE.equals(portalChunk.invoke(null,8,0))
                ||!Boolean.TRUE.equals(portalChunk.invoke(null,-248,-256))
                ||Boolean.TRUE.equals(portalChunk.invoke(null,7,0))
                ||Boolean.TRUE.equals(portalChunk.invoke(null,8,1)))
            throw new ReflectiveOperationException("Sanctuary placement schema changed");
    }
    private static Field field(Class<?> c,String name) throws ReflectiveOperationException {
        Field f=c.getDeclaredField(name);f.setAccessible(true);return f;
    }
    private static Method method(Class<?> c,String name,Class<?>...args) throws ReflectiveOperationException {
        Method m=c.getDeclaredMethod(name,args);m.setAccessible(true);return m;
    }
    private static int[] ints(Class<?> c,String name)throws ReflectiveOperationException{return (int[])field(c,name).get(null);}
    private static long[] longs(Class<?> c,String name)throws ReflectiveOperationException{return (long[])field(c,name).get(null);}
    private static boolean[] booleans(Class<?> c,String name)throws ReflectiveOperationException{return (boolean[])field(c,name).get(null);}
    private static boolean overlaps(int x,int y,int z,int sx,int sy,int sz,
                                    int ox,int oy,int oz,int osx,int osy,int osz) {
        return x<(long)ox+osx+2&&x+(long)sx+2>ox&&z<(long)oz+osz+2&&z+(long)sz+2>oz
            &&y<(long)oy+osy+2&&y+(long)sy+2>oy;
    }
    /** Exclude the sanctuary chunk and the two-chunk halo reserved by BiomeDetails.
     * The arithmetic selects at most one candidate per axis for these sub-256-chunk sites. */
    private boolean sanctuaryConflict(int x,int z,int sx,int sz) throws ReflectiveOperationException {
        long lowX=Math.floorDiv((long)x-2,16)-2,highX=Math.floorDiv((long)x+sx+1,16)+2;
        long lowZ=Math.floorDiv((long)z-2,16)-2,highZ=Math.floorDiv((long)z+sz+1,16)+2;
        long candidateX=lowX+Math.floorMod(8-lowX,256);
        long candidateZ=lowZ+Math.floorMod(-lowZ,256);
        if(candidateX>highX||candidateZ>highZ)return false;
        return Boolean.TRUE.equals(portalChunk.invoke(null,(int)candidateX,(int)candidateZ));
    }
    /** Plan-view overlap: box a (grown by `grow`) against box b. */
    private static boolean flat(int x,int z,int sx,int sz,int ox,int oz,int osx,int osz,int grow) {
        return x<(long)ox+osx+grow&&x+(long)sx+grow>ox&&z<(long)oz+osz+grow&&z+(long)sz+grow>oz;
    }
    /** The 3.25.0 sanctuaries: a seeded share of the square centres at (136, 128) modulo 256 chunks, two-chunk halo. */
    private boolean newSanctuaryConflict(Terrain terrain,int x,int z,int sx,int sz) throws ReflectiveOperationException {
        long lowX=Math.floorDiv((long)x-2,16)-2,highX=Math.floorDiv((long)x+sx+1,16)+2;
        long lowZ=Math.floorDiv((long)z-2,16)-2,highZ=Math.floorDiv((long)z+sz+1,16)+2;
        for(long cx=lowX+Math.floorMod(136-lowX,256);cx<=highX;cx+=256)
            for(long cz=lowZ+Math.floorMod(128-lowZ,256);cz<=highZ;cz+=256)
                if(Boolean.TRUE.equals(newSanctuary.invoke(null,terrain,(int)cx,(int)cz)))return true;
        return false;
    }
    /**
     * 1.1.0 (census 2026-09-23): an imported site stamps over whatever HorrorBiomes built in its chunks, and the
     * 3D claim boxes missed what rises out of them -- the ladder shafts from buried rooms and set pieces to
     * daylight, set-piece footings and the excavation round their pads, the clearing round surface rooms. So a
     * new imported site keeps its footprint two blocks clear, in plan view and at any height, of every built
     * set piece (primary or 3.25.0 secondary) grown by its three-block excavation margin, of every admitted
     * lattice room (all three lattices) grown by one block (buried: shaft lining) or six (surface: clearing),
     * and of the 3.25.0 sanctuaries. Only admission changes: plans already frozen in the ledger are untouched.
     */
    private boolean planViewConflict(Terrain terrain,int x,int z,int sx,int sz) throws Exception {
        if(newSanctuaryConflict(terrain,x,z,sx,sz))return true;
        for(int k=0;k<mc.length;k++) for(int lat=0;lat<2;lat++) {
            int cell=lat==0?mc[k]:mc2[k]; long salt=lat==0?ms[k]:ms2[k];
            int rx=(int)Math.floorMod(Terrain.mix(terrain.seed+salt)>>>3,(long)cell);
            int rz=(int)Math.floorMod(Terrain.mix(terrain.seed+salt+17L)>>>3,(long)cell);
            int lowX=Math.floorDiv(x-mx[k]-8,16),highX=Math.floorDiv(x+sx+8,16);
            int lowZ=Math.floorDiv(z-mz[k]-8,16),highZ=Math.floorDiv(z+sz+8,16);
            for(int acx=lowX+Math.floorMod(rx-lowX,cell);acx<=highX;acx+=cell)
                for(int acz=lowZ+Math.floorMod(rz-lowZ,cell);acz<=highZ;acz+=cell) {
                    int ox=acx*16+1,oz=acz*16+1;
                    if(!flat(x,z,sx,sz,ox-3,oz-3,mx[k]+6,mz[k]+6,2)) continue;
                    boolean built=lat==0
                        ?Boolean.TRUE.equals(fits.invoke(null,terrain,k,ox,oz))&&!Boolean.TRUE.equals(claimed.invoke(null,terrain,ox,oz,mx[k],mz[k],k))
                        :(Integer)secondaryBase.invoke(null,terrain,k,acx,acz)>=0;
                    if(built) return true;
                }
        }
        for(int k=0;k<dc.length;k++) for(int lat=0;lat<3;lat++) {
            long salt=lat==0?da[k]:lat==1?db[k]:dsc[k];
            if(salt==0) continue;
            int cell=lat==2?dc2[k]:dc[k], grow=surface[k]?6:1;
            int rx=(int)Math.floorMod(Terrain.mix(terrain.seed+salt)>>>3,(long)cell);
            int rz=(int)Math.floorMod(Terrain.mix(terrain.seed+salt+17L)>>>3,(long)cell);
            int lowX=Math.floorDiv(x-dx[k]-12,16),highX=Math.floorDiv(x+sx+12,16);
            int lowZ=Math.floorDiv(z-dz[k]-12,16),highZ=Math.floorDiv(z+sz+12,16);
            for(int acx=lowX+Math.floorMod(rx-lowX,cell);acx<=highX;acx+=cell)
                for(int acz=lowZ+Math.floorMod(rz-lowZ,cell);acz<=highZ;acz+=cell) {
                    int ox=acx*16+2,oz=acz*16+2;
                    if(!flat(x,z,sx,sz,ox-grow,oz-grow,dx[k]+2*grow,dz[k]+2*grow,2)) continue;
                    Object anchor=(surface[k]?dungeonSurface:dungeonAnchor).invoke(null,terrain,acx,acz,cell,salt,dx[k],dz[k]);
                    if(anchor!=null&&ax.getInt(anchor)==ox&&az.getInt(anchor)==oz) return true;
                }
        }
        return false;
    }
    /**
     * 1.2.0: admission for big designs (Occupancy.big). planViewConflict turns a site away when any built set
     * piece or room lies anywhere under its rectangle, at any height, and for the ten largest land designs that
     * was every site. Here a site is refused only where the design's own records (Occupancy cells) come within
     * two blocks of what HorrorBiomes really builds there:
     *  - a set piece on either lattice: its footprint and three-block excavation margin, from ten under its floor
     *    upward (footings below; excavation, risers and headroom above -- a buried one's risers are not traced,
     *    so its whole footprint stays a riser zone up to the sky);
     *  - a surface room: its footprint and six-block clearing, from twelve under its floor upward;
     *  - a buried room: its box with the one-block lining, and the lined 3x3 ladder shaft from its entrance
     *    (Dungeons.ENTRY) up to daylight and the headroom and crowns cleared over it;
     *  - the sanctuaries exactly as before (the whole rectangle).
     * The 3D claim boxes of conflicts() lie inside these envelopes. Fails closed like conflicts().
     */
    public boolean envelopeConflicts(Terrain terrain,int x,int y,int z,Catalog.Design site,Pack.Blocks occ) {
        final int g=2;
        try {
            int sx=site.width(),sz=site.depth();
            if(sanctuaryConflict(x,z,sx,sz)||newSanctuaryConflict(terrain,x,z,sx,sz))return true;
            for(int k=0;k<mc.length;k++) for(int lat=0;lat<2;lat++) {
                int cell=lat==0?mc[k]:mc2[k]; long salt=lat==0?ms[k]:ms2[k];
                int rx=(int)Math.floorMod(Terrain.mix(terrain.seed+salt)>>>3,(long)cell);
                int rz=(int)Math.floorMod(Terrain.mix(terrain.seed+salt+17L)>>>3,(long)cell);
                int lowX=Math.floorDiv(x-mx[k]-8,16),highX=Math.floorDiv(x+sx+8,16);
                int lowZ=Math.floorDiv(z-mz[k]-8,16),highZ=Math.floorDiv(z+sz+8,16);
                for(int acx=lowX+Math.floorMod(rx-lowX,cell);acx<=highX;acx+=cell)
                    for(int acz=lowZ+Math.floorMod(rz-lowZ,cell);acz<=highZ;acz+=cell) {
                        int ox=acx*16+1,oz=acz*16+1;
                        if(!flat(x,z,sx,sz,ox-3,oz-3,mx[k]+6,mz[k]+6,g)) continue;
                        int base=lat==0
                            ?(Boolean.TRUE.equals(fits.invoke(null,terrain,k,ox,oz))&&!Boolean.TRUE.equals(claimed.invoke(null,terrain,ox,oz,mx[k],mz[k],k))
                                ?(Integer)baseY.invoke(null,terrain,k,acx,acz):-1)
                            :(Integer)secondaryBase.invoke(null,terrain,k,acx,acz);
                        if(base<0) continue;
                        if(occ.meets(x,y,z,ox-3-g,oz-3-g,ox+mx[k]+3+g,oz+mz[k]+3+g,base-10,255)) return true;
                    }
            }
            for(int k=0;k<dc.length;k++) for(int lat=0;lat<3;lat++) {
                long salt=lat==0?da[k]:lat==1?db[k]:dsc[k];
                if(salt==0) continue;
                int cell=lat==2?dc2[k]:dc[k], grow=surface[k]?6:1;
                int rx=(int)Math.floorMod(Terrain.mix(terrain.seed+salt)>>>3,(long)cell);
                int rz=(int)Math.floorMod(Terrain.mix(terrain.seed+salt+17L)>>>3,(long)cell);
                int lowX=Math.floorDiv(x-dx[k]-12,16),highX=Math.floorDiv(x+sx+12,16);
                int lowZ=Math.floorDiv(z-dz[k]-12,16),highZ=Math.floorDiv(z+sz+12,16);
                for(int acx=lowX+Math.floorMod(rx-lowX,cell);acx<=highX;acx+=cell)
                    for(int acz=lowZ+Math.floorMod(rz-lowZ,cell);acz<=highZ;acz+=cell) {
                        int ox=acx*16+2,oz=acz*16+2;
                        if(!flat(x,z,sx,sz,ox-grow,oz-grow,dx[k]+2*grow,dz[k]+2*grow,g)) continue;
                        Object anchor=(surface[k]?dungeonSurface:dungeonAnchor).invoke(null,terrain,acx,acz,cell,salt,dx[k],dz[k]);
                        if(anchor==null||ax.getInt(anchor)!=ox||az.getInt(anchor)!=oz) continue;
                        int oy=ay.getInt(anchor);
                        if(surface[k]) {
                            if(occ.meets(x,y,z,ox-6-g,oz-6-g,ox+dx[k]+6+g,oz+dz[k]+6+g,oy-12,255)) return true;
                            continue;
                        }
                        if(occ.meets(x,y,z,ox-1-g,oz-1-g,ox+dx[k]+1+g,oz+dz[k]+1+g,oy-3,oy+dh[k]+3)) return true;
                        int ex=ox+entry[k][0],ez=oz+entry[k][1];
                        if(occ.meets(x,y,z,ex-1-g,ez-1-g,ex+2+g,ez+2+g,oy,255)) return true;
                    }
            }
            return false;
        } catch(Exception e) {throw new IllegalStateException("Existing structure envelope check failed closed",e);}
    }
    public boolean conflicts(Terrain terrain,int x,int y,int z,Catalog.Design site) {
        try {
            int sx=site.width(),sy=site.height(),sz=site.depth();
            if(sanctuaryConflict(x,z,sx,sz))return true;
            if(planViewConflict(terrain,x,z,sx,sz))return true;
            for(int k=0;k<mc.length;k++) {
                int cell=mc[k];
                int rx=(int)Math.floorMod(Terrain.mix(terrain.seed+ms[k])>>>3,(long)cell);
                int rz=(int)Math.floorMod(Terrain.mix(terrain.seed+ms[k]+17L)>>>3,(long)cell);
                int lowX=Math.floorDiv(x-mx[k]-18,16),highX=Math.floorDiv(x+sx+18,16);
                int lowZ=Math.floorDiv(z-mz[k]-18,16),highZ=Math.floorDiv(z+sz+18,16);
                for(int acx=lowX;acx<=highX;acx++) {
                    if(Math.floorMod(acx,cell)!=rx) continue;
                    for(int acz=lowZ;acz<=highZ;acz++) {
                        if(Math.floorMod(acz,cell)!=rz) continue;
                        int ox=acx*16+1,oz=acz*16+1;
                        if(x>=ox+mx[k]+2||x+sx+2<=ox||z>=oz+mz[k]+2||z+sz+2<=oz) continue;
                        if(!Boolean.TRUE.equals(fits.invoke(null,terrain,k,ox,oz))) continue;
                        if(Boolean.TRUE.equals(claimed.invoke(null,terrain,ox,oz,mx[k],mz[k],k))) continue;
                        int oy=(Integer)baseY.invoke(null,terrain,k,acx,acz);
                        if(oy>=0&&overlaps(x,y,z,sx,sy,sz,ox,oy-10,oz,mx[k],mh[k]+17,mz[k])) return true;
                    }
                }
            }
            for(int k=0;k<dc.length;k++) for(long salt:new long[]{da[k],db[k]}) {
                if(salt==0) continue;
                int cell=dc[k];
                int rx=(int)Math.floorMod(Terrain.mix(terrain.seed+salt)>>>3,(long)cell);
                int rz=(int)Math.floorMod(Terrain.mix(terrain.seed+salt+17L)>>>3,(long)cell);
                int lowX=Math.floorDiv(x-dx[k]-18,16),highX=Math.floorDiv(x+sx+18,16);
                int lowZ=Math.floorDiv(z-dz[k]-18,16),highZ=Math.floorDiv(z+sz+18,16);
                for(int acx=lowX;acx<=highX;acx++) {
                    if(Math.floorMod(acx,cell)!=rx) continue;
                    for(int acz=lowZ;acz<=highZ;acz++) {
                        if(Math.floorMod(acz,cell)!=rz) continue;
                        Method method=surface[k]?dungeonSurface:dungeonAnchor;
                        Object anchor=method.invoke(null,terrain,acx,acz,cell,salt,dx[k],dz[k]);
                        if(anchor==null) continue;
                        int ox=ax.getInt(anchor),oy=ay.getInt(anchor),oz=az.getInt(anchor);
                        if(overlaps(x,y,z,sx,sy,sz,ox,oy-2,oz,dx[k],dh[k]+5,dz[k])) return true;
                    }
                }
            }
            return false;
        } catch(Exception e) {throw new IllegalStateException("Existing structure claim check failed closed",e);}
    }
}
