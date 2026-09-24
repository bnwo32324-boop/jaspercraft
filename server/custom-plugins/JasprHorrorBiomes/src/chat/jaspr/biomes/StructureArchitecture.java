package chat.jaspr.biomes;

import java.util.*;
import org.bukkit.generator.ChunkGenerator;

/** Chunk-clipped architectural vocabulary: occupied volumes only, with per-column footings. */
final class StructureArchitecture {
    private StructureArchitecture() {}
    /** Deepest a footing or pier is carried down through air/water to reach real ground. */
    static final int MAX_PIER=64;
    static void stamp(StructurePlanner.Site s,ChunkGenerator.ChunkData data,int cx,int cz) {
        Brush b=new Brush(data,cx,cz);Terrain terrain=new Terrain(s.seed);
        for(StructurePlanner.Room r:s.rooms) {
            int ox=s.x+StructurePlanner.MARGIN+r.col*12,oz=s.z+StructurePlanner.MARGIN+r.row*12;
            if(!b.intersects(ox,oz,12,12))continue;
            room(s,r,b,terrain,ox,oz);
        }
        approach(s,b,terrain);
        for(StructurePlanner.Marker marker:s.markers()) marker(s,b,marker);
    }
    static boolean footprint(char c,int x,int z) {
        if(x<0||z<0||x>11||z>11)return false;
        if(c=='F')return x>=3&&x<=8||z>=5&&z<=7;
        if(c=='W')return z>=5&&z<=7||x>=5&&x<=7;
        if(c=='C'||c=='A')return x>=4&&x<=8||z>=4&&z<=8;
        if(c=='G'||c=='T'||c=='K')return Math.min(x,11-x)+Math.min(z,11-z)>=3;
        if(c=='R')return x>=1&&x<=10||z>=5&&z<=7;
        if(c=='Q')return x>=2&&x<=9||z>=4&&z<=8;
        if(c=='e')return x>=3&&x<=9||z>=4&&z<=8;
        if(c=='v')return x>=3&&x<=9||z>=3&&z<=9;
        if("IOgkwb".indexOf(c)>=0)return Math.min(x,11-x)+Math.min(z,11-z)>=2;
        if(c=='a')return Math.min(x,11-x)+Math.min(z,11-z)>=1;
        return true;
    }
    private static void room(StructurePlanner.Site s,StructurePlanner.Room r,Brush b,Terrain t,int ox,int oz) {
        char c=r.type;boolean wet=s.design.mode.equals("underwater"),open=StructureCatalog.open(c)&&!wet&&!s.design.mode.equals("buried");
        int storeys=StructureCatalog.floors(c),top=r.floor+storeys*6;
        int wall=wall(s,c),floor=floor(c),wallData=wall==159?7:0;
        for(int x=Math.max(0,b.x-ox);x<Math.min(12,b.x+16-ox);x++) for(int z=Math.max(0,b.z-oz);z<Math.min(12,b.z+16-oz);z++) {
            if(!footprint(c,x,z)) {
                if(!wet&&scar(s,ox+x,oz+z,27)%19==0) {
                    // Rubble only lies on real ground: the first ground under the sampled surface (a cut or cave
                    // mouth there leaves the sample in the air), and only where it is open to the air above.
                    int h=t.sample(ox+x,oz+z).y,rest=b.surface(ox+x,h+2,oz+z,24);
                    if(rest!=Integer.MIN_VALUE&&!b.solid(ox+x,rest+1,oz+z)&&!b.solid(ox+x,rest+2,oz+z))b.put(ox+x,rest+1,oz+z,44,3);
                }
                continue;
            }
            int wx=ox+x,wz=oz+z,f=s.floor(r,x,z),ground=t.sample(wx,wz).y;
            b.removeVegetation(wx,ground+1,wz,ground+19,t.sample(wx,wz).profile.tree);
            // Embedded stepped piers support only an occupied column, leaving deep ores/caves untouched. Where the
            // chunk has no ground under the heightmap's footing (an opening cut, a cave breach, a cave under a
            // buried room) the pier carries on down to the ground that is really there.
            b.column(wx,footing(b,wx,Math.min(f-2,ground-2),wz,pillar(x)&&pillar(z)),wz,f,98,2);
            b.put(wx,f,wz,floor,floor==5?1:0);
            // Terrace stairs stand ON the lower cell, level with the higher one (audit 2026-09: they replaced the
            // lower cell's floor block, which left a full-block step after every run). Under a stair flight the
            // headroom is not there, so those keep the old sunken step.
            int stairs=surfaceStair(s,r,x,z);boolean raised=stairs>=0&&!underFlight(r,x,z);
            if(stairs>=0&&!raised)b.put(wx,f,wz,109,stairs);
            boolean edge=!footprint(c,x-1,z)||!footprint(c,x+1,z)||!footprint(c,x,z-1)||!footprint(c,x,z+1);
            boolean door=s.doorway(r,x,z);
            // Never clear the reserved rectangle or the hill above a roof. Each room owns its interior.
            b.column(wx,f+1,wz,open?f+4:top-1,0,0);
            if(raised)b.put(wx,f+1,wz,109,stairs);
            if(!open) {
                for(int level=0;level<storeys;level++) {
                    int base=r.floor+level*6;
                    if(level>0)b.put(wx,base,wz,floor,floor==5?1:0);
                    if(edge)for(int dy=1;dy<=5;dy++) {
                        if(level==0&&door&&base+dy<=doorTop(s,r,x,z))continue;
                        int id=wall,datum=wall==98?(scar(s,wx,wz,dy)%5==0?1:2):wallData;
                        // Backrooms wallpaper: the gate rooms' walls are yellow below a grey ceiling band.
                        if(c=='P'&&dy<=4)datum=4;
                        // Windows (see windowed()): a buried gate room's blind bays stay yellow wall, and a
                        // tower's top storey has iron-bar arrow loops for its lookout.
                        if(dy>=2&&dy<=3&&(x==0||x==11?z%4==2:x%4==2)&&windowed(s,r,x,z)&&!againstGround(t,c,wx,wz,x,z,base+dy)) {id=(c=='K'||c=='T')&&level==storeys-1?101:20;datum=0;}
                        if(c=='F'&&(x==3||x==8)&&dy>=2&&dy<=3&&z%3==1){id=20;datum=0;}
                        if(c=='F'&&s.design.room(r.col,r.row-1)!='F'&&z<=2&&dy>=2&&dy<=4){id=20;datum=0;}
                        if(c=='h'&&(x==0||x==11)&&(z==0||z==11)) {id=17;datum=1;}
                        boolean transit=x>=5&&x<=7||z>=5&&z<=7;
                        if(!wet&&!s.design.mode.equals("buried")&&!transit&&dy>=2&&scar(s,wx,wz,31)%7<=1) {id=0;datum=0;}
                        b.put(wx,base+dy,wz,id,datum);
                    }
                }
                // Flat structural roofs, vaulted crypts, fuselage curves and pitched timber roofs.
                int roof=wet?20:c=='B'?159:c=='F'?42:98;
                b.put(wx,top,wz,roof,c=='B'?9:0);
                if(c=='h'||c=='M') {
                    int rise=Math.min(x,11-x)/2;
                    b.column(wx,top+1,wz,top+rise,5,1);
                    b.put(wx,top+rise+1,wz,53,x<6?0:1);
                } else if(c=='D'||c=='q') {
                    // Vault ribs are overhead; all walking lanes retain at least three blocks of air.
                    // A rib under the collapsed bay would hang from nothing, so it fell with the roof.
                    if((x==2||x==9)&&z%3==0&&!collapsed(s,ox,oz,x,z))b.put(wx,top-1,wz,109,x==2?0:1);
                } else if(c=='K'||c=='T') {
                    if(edge&&(x+z)%2==0)b.column(wx,top+1,wz,top+2,98,0);
                }
                if(collapsed(s,ox,oz,x,z))b.column(wx,top,wz,top+4,0,0);
                if(wet&&ground<63)b.column(wx,top+1,wz,62,9,0);
            } else if(c=='=') {
                if(x==6||z==6)b.put(wx,f,wz,159,4);
            } else if(c=='o') {
                if(x>=2&&x<=3&&z>=2&&z<=3) {b.put(wx,f,wz,3,0);b.put(wx,f+1,wz,38,0);}
            }
        }
        if(storeys>1)for(int level=0;level<storeys-1;level++) staircase(s,r,b,ox,oz,level);
        if(c=='C')mast(s,r,b,ox,oz);
        if(s.design.family.equals("telecom")&&(c=='C'||c=='='))wire(s,r,b,ox,oz);
        if(c=='F'||c=='W')aircraft(s,r,b,ox,oz);
        if(c=='A')arch(b,ox,r.floor,oz);
        // Corner rubble goes down before the fittings, which take its cell where they need it (it used to be
        // dropped last, straight through a bench, stove or bunk standing in the corner).
        if(!wet&&!s.design.mode.equals("buried")&&!StructureCatalog.open(c)) {
            for(int lx:new int[]{1,10})for(int lz:new int[]{1,10})if(footprint(c,lx,lz)&&scar(s,ox+lx,oz+lz,33)%3==0)
                b.put(ox+lx,r.floor+1,oz+lz,44,3);
        }
        furniture(s,r,b,ox,oz);
        if(StructureCatalog.EXPANSION_ROOMS.indexOf(c)>=0) {
            expansionFurniture(s,r,b,ox,oz);
            expansionRoof(r,b,ox,oz);
        }
        // Entrances/cross-terrace openings are cut last to keep raised sills and their headroom usable.
        for(int x=0;x<12;x++)for(int z=0;z<12;z++)if(s.doorway(r,x,z)) {
            int f=s.floor(r,x,z);b.column(ox+x,f+(raisedStair(s,r,x,z)?2:1),oz+z,doorTop(s,r,x,z),0,0);
        }
    }
    /** The ruin's collapsed roof bay: in an exposed (not buried, not submerged) room whose scar picks it,
     * the roof over x 8..10, z 2..4 is cut away. Anything that would hang from that roof is left out. */
    private static boolean collapsed(StructurePlanner.Site s,int ox,int oz,int x,int z) {
        // Not where the room's own roof plant stands on that bay (engine/kiln flues, lab/ward/relay roof plant):
        // the stack or plant was left hanging over the hole (audit 2026-09, roof-scar-under-roof-feature).
        char c=s.design.room((ox-s.x-StructurePlanner.MARGIN)/12,(oz-s.z-StructurePlanner.MARGIN)/12);
        return "EbJLp".indexOf(c)<0&&!s.design.mode.equals("underwater")&&!s.design.mode.equals("buried")
            &&x>=8&&x<=10&&z>=2&&z<=4&&scar(s,ox,oz,32)%3==0;
    }
    private static int wall(StructurePlanner.Site s,char c) {
        switch(c) {
            case 'R': case 'Z': case 'p': return 45;
            case 'V': case 'L': case 'J': case 'O': return 155;
            case 'E': case 'i': return 42;
            case 'I': case 'w': return 168;
            case 'Y': case 'm': case 'j': return 5;
            case 'g': return 20;
            case 'b': return 112;
            case 'c': return 216;
            case 'l': return 159;
        }
        if(c=='h'||c=='M')return 5;
        if(c=='s'||c=='S')return 45;
        if(c=='G')return 20;
        if(c=='F')return 42;
        if(c=='B'||c=='P'||c>='2'&&c<='8')return 159;
        if(c=='U')return 48;
        return 98;
    }
    private static int floor(char c) {
        if("VJLw".indexOf(c)>=0)return 155;
        if("Ymjl".indexOf(c)>=0)return 5;
        if(c=='I'||c=='g')return 168;
        if(c=='b')return 112;
        if(c=='c'||c=='Q')return 216;
        return c=='h'||c=='M'?5:c=='s'||c=='S'||c=='P'?155:c=='G'?168:98;
    }
    /** A multi-storey room's west terrace lane (door cell included) runs under its stair flight (x 2..3, z 3..8),
     * which leaves no headroom for a raised step; the whole lane keeps the old sunken steps so they stay one run. */
    private static boolean underFlight(StructurePlanner.Room r,int x,int z) {
        return StructureCatalog.floors(r.type)>1&&x<=4&&z>=2&&z<=9;
    }
    /** A terrace stair standing on top of this cell's floor block (see room()). */
    private static boolean raisedStair(StructurePlanner.Site s,StructurePlanner.Room r,int x,int z) {
        return surfaceStair(s,r,x,z)>=0&&!underFlight(r,x,z);
    }
    /** Top of a ground-storey doorway opening: three blocks over its sill, four where a raised terrace stair
     * stands in it (a player climbing the stair beyond would otherwise hit the lintel with his head). */
    private static int doorTop(StructurePlanner.Site s,StructurePlanner.Room r,int x,int z) {
        return s.floor(r,x,z)+(raisedStair(s,r,x,z)?4:3);
    }
    private static int surfaceStair(StructurePlanner.Site s,StructurePlanner.Room r,int x,int z) {
        int f=s.floor(r,x,z);
        if(x<11&&s.floor(r,x+1,z)>f)return 0;
        if(x>0&&s.floor(r,x-1,z)>f)return 1;
        if(z<11&&s.floor(r,x,z+1)>f)return 2;
        if(z>0&&s.floor(r,x,z-1)>f)return 3;
        return -1;
    }
    private static void staircase(StructurePlanner.Site s,StructurePlanner.Room r,Brush b,int ox,int oz,int level) {
        // Two-wide, six-riser flights; adjacent storeys share a guarded stairwell with six-block pitch.
        int floor=r.floor+level*6;
        for(int z=3;z<=8;z++)for(int x=2;x<=3;x++) {
            int step=z-2;
            boolean lane=level==0&&s.doorway(r,0,z);
            b.column(ox+x,floor+step+1,oz+z,floor+step+3,0,0);
            // A west doorway's lane z=5 passes under the third tread, which its neighbours carry instead
            // of a block of its own: that block capped the lane at one block, so the doorway's terrace
            // step ran into the flight's side (~126 rooms in 68 designs). Lanes z 6..7 already pass under.
            if(!(lane&&z==5))b.put(ox+x,floor+step-1,oz+z,98,0);
            b.put(ox+x,floor+step,oz+z,109,2);
            // Audit 2026-09: the balusters stood on air (the lower three per side floated in ~175 designs).
            // A two-block stone-brick string beside the flight now carries them, below the storey above.
            // The open east string runs the whole flight.
            b.column(ox+4,Math.max(floor+1,floor+step-1),oz+z,Math.min(floor+5,floor+step),98,0);
            // The wall-side rail beyond z=5 hung on the wall with air under it (~7,600 posts in the shared2
            // captures) and at z=7 took a block out of the upper floor. Its string now runs the whole flight like
            // the open side's; its z=7 post is left to the upper floor's own block. Over a west doorway's lane
            // (level 0, z 5..7) the rail has a gap instead: the string would cap the lane's headroom.
            if(!lane) {
                b.column(ox+1,Math.max(floor+1,floor+step-1),oz+z,Math.min(floor+5,floor+step),98,0);
                if(step!=5)b.put(ox+1,floor+step+1,oz+z,85,0);
            }
            b.put(ox+4,floor+step+1,oz+z,85,0);
        }
    }
    /** True where room() leaves a wall block on edge cell (x,z) of the given storey at height dy above that
     * storey's floor: not cut by a ground-storey doorway and not lost to a ruin scar (exposed rooms only). */
    private static boolean wallStands(StructurePlanner.Site s,StructurePlanner.Room r,int ox,int oz,int x,int z,int level,int dy) {
        char c=r.type;
        if(!footprint(c,x,z)||footprint(c,x-1,z)&&footprint(c,x+1,z)&&footprint(c,x,z-1)&&footprint(c,x,z+1))return false;
        if(level==0&&s.doorway(r,x,z)&&r.floor+dy<=doorTop(s,r,x,z))return false;
        boolean transit=x>=5&&x<=7||z>=5&&z<=7;
        return s.design.mode.equals("underwater")||s.design.mode.equals("buried")||transit||dy<2||scar(s,ox+x,oz+z,31)%7>1;
    }
    private static void furniture(StructurePlanner.Site s,StructurePlanner.Room r,Brush b,int ox,int oz) {
        char c=r.type;
        int storeys=StructureCatalog.floors(c);
        for(int level=0;level<storeys;level++) {
            int y=r.floor+level*6;
            // Lights go in first: a fitting placed later in the same cell simply takes it.
            if(!StructureCatalog.open(c))lights(s,r,b,ox,oz,level,y);
            if(c=='h'||c=='M') {
                b.put(ox+8,y+1,oz+2,58,0);b.put(ox+9,y+1,oz+2,61,3);
                b.put(ox+8,y+1,oz+3,85,0);b.put(ox+8,y+2,oz+3,72,0);b.put(ox+9,y+1,oz+3,53,1);
                if(c=='M')b.column(ox+9,y+1,oz+9,y+3,47,0);
                home(r,b,ox,oz,level,y);
            } else if(c=='s'||c=='S') {
                for(int z:new int[]{2,9}) {b.put(ox+8,y+1,oz+z,85,0);b.put(ox+8,y+2,oz+z,72,0);b.put(ox+9,y+1,oz+z,53,1);}
                b.column(ox+10,y+1,oz+3,y+3,47,0);
                // The blackboard hangs on the west wall; a west doorway has no wall there (it floated in the opening).
                if(level>0||!s.doorway(r,0,6))b.put(ox+1,y+2,oz+6,159,15);
            } else if(c=='B') {
                // The workshop corner keeps its anvil only in the armoury; elsewhere a workbench stands by the
                // furnace (348 decorative anvils, 31 iron each, were a free iron cache -- valuables policy).
                b.put(ox+9,y+1,oz+2,61,3);b.put(ox+9,y+1,oz+3,cache(s,ox,oz,y).equals("armory")?145:58,0);
                b.column(ox+9,y+1,oz+9,y+2,42,0);b.put(ox+8,y+1,oz+2,69,5);
                bunker(s,r,b,ox,oz,y);
            } else if(c=='U') {
                for(int z=2;z<=4;z++){b.put(ox+9,y,oz+z,9,0);b.put(ox+10,y+1,oz+z,101,0);}
                // The overflow grate hangs from the vault on a bar drop (it floated alone at y+3).
                b.column(ox+9,y+3,oz+3,y+5,101,0);b.put(ox+8,y+1,oz+2, cauldron(),0);
            } else if(c=='F') {
                for(int z:new int[]{2,4,9}) {b.put(ox+4,y+1,oz+z,53,2);b.put(ox+7,y+1,oz+z,53,2);}
                // The lever stands in the north doorway lane: a terrace step there leaves it no floor (it floated
                // over a lower step or replaced a higher one), so it goes in only where that lane is level.
                if(surfaceStair(s,r,6,1)<0)b.put(ox+6,y+1,oz+1,69,5);
            } else if(c=='P') {
                for(int z:new int[]{2,9})b.column(ox+9,y+1,oz+z,y+3,159,4);
                b.put(ox+6,y+5,oz+6,169,0);
                backroom(s,r,b,ox,oz,y);
            } else if(c=='G') {
                b.put(ox+9,y+1,oz+3,168,2);b.put(ox+9,y+2,oz+3,20,0);b.put(ox+9,y+3,oz+3,169,0);
                b.put(ox+9,y+1,oz+9, cauldron(),0);
            } else if(c=='D'||c=='q'||c=='K'||c=='T') {
                b.column(ox+9,y+1,oz+2,y+2,98,c=='q'?1:3);
                b.put(ox+9,y+3,oz+2,50,5);b.put(ox+9,y+1,oz+9,44,0);
                if(c=='K'||c=='T')garrison(s,r,b,ox,oz,level,y);
                else crypt(r,b,ox,oz,y);
            } else if(c>='2'&&c<='8') {
                b.put(ox+8,y+1,oz+2,85,0);b.put(ox+8,y+2,oz+2,72,0);
                b.put(ox+9,y+1,oz+3,53,1);b.column(ox+9,y+1,oz+9,y+2,47,0);
                home(r,b,ox,oz,level,y);
            }
        }
    }
    /** Purposeful light for every enclosed storey (audit 2026-09: one wall torch per storey left ~80% of the
     * reachable interior floors under light 8). Each wall carries a torch at head height beside its windows and
     * door lane (pinwheel positions 3/8), falling back to the old mid-wall mount over the lintel, never on glass,
     * a ruin gap or the stair flight. Rooms whose purpose wants more light also get a ceiling light at the
     * centre: a glowstone lamp in lived-in and working rooms, a sea-lantern panel in clinical, research,
     * school, bunker and submerged spaces. Crypts, sewers, cells, kilns and towers stay torch-lit. */
    private static void lights(StructurePlanner.Site s,StructurePlanner.Room r,Brush b,int ox,int oz,int level,int y) {
        char c=r.type;
        if(c!='G'&&c!='g'&&c!='P') {
            // {inside x, inside z, outward dx, outward dz, torch data} candidates per wall, in order.
            int[][][] walls={
                {{8,1,0,-1,3},{3,1,0,-1,3},{6,1,0,-1,3}},     // north wall, torch faces south
                {{10,8,1,0,2},{10,3,1,0,2},{10,6,1,0,2}},     // east wall, faces west
                {{3,10,0,1,4},{8,10,0,1,4},{6,10,0,1,4}},     // south wall, faces north
                {{1,3,-1,0,1},{1,8,-1,0,1},{1,6,-1,0,1}}};    // west wall, faces east
            // Crypts, sewers, cells, ossuaries and kilns are meant to be dim: two torches, not four.
            int budget="DqUXQcb".indexOf(c)>=0?2:4;
            for(int[][] wall:walls) {
                if(budget==0)break;
                for(int k=0;k<wall.length;k++) {
                    int[] m=wall[k];int dy=k<2?2:4;
                    if(mount(s,r,ox,oz,level,m[0],m[1],m[2],m[3],dy)){b.put(ox+m[0],y+dy,oz+m[1],50,m[4]);budget--;break;}
                }
            }
        }
        int lamp=s.design.mode.equals("underwater")||"LJIwOpRVBsS".indexOf(c)>=0?169
            :"hMYmZEil2345678".indexOf(c)>=0?89:0;
        if(lamp!=0&&"PGgHkKTDqQcUXbF".indexOf(c)<0)b.put(ox+6,y+5,oz+6,lamp,0);
    }
    private static void at(Brush b,int ox,int oz,int x,int y,int z,int id,int datum){b.put(ox+x,y,oz+z,id,datum);}
    /** The loot marker kind on this storey's cache (8, y+1, 8), or "" (StructurePlanner.makeMarkers). */
    private static String cache(StructurePlanner.Site s,int ox,int oz,int y) {
        for(StructurePlanner.Marker m:s.markers())if(m.x==ox+8&&m.z==oz+8&&m.y==y+1)return m.kind;
        return "";
    }
    /** Bunker rooms (348 in 39 designs) were one furnace-and-anvil corner each, 29 identical copies in a vault
     * settlement. The west bays now say what each room was for: a clinic where the planner put the medical
     * cache, an armoury at the armory cache, otherwise quarters, a mess, stores or a plant room, picked per
     * room from the site seed. Palette: grey/white wool, spruce, stone brick, polished andesite, black terracotta. */
    private static void bunker(StructurePlanner.Site s,StructurePlanner.Room r,Brush b,int ox,int oz,int y) {
        String kind=cache(s,ox,oz,y);
        int use=kind.equals("medical")?4:kind.equals("armory")?5:scar(s,ox,oz,41)%4;
        // The design's own purpose weights the mix (audit 2026-09: a medical compound had one clinic in 18 cells, a
        // command bunker no command post): medical and hospital designs turn their mess and plant rooms into
        // wards; command, reactor and telecom designs turn their quarters into control rooms.
        String what=s.design.id+" "+s.design.family;
        if(use==1||use==3) { if(what.contains("medical")||what.contains("hospital")||what.contains("hospice"))use=4; }
        else if(use==0&&(what.contains("command")||what.contains("reactor")||what.contains("telecom")))use=3;
        switch(use) {
            case 0: // quarters: two double bunks along the west wall, a locker between them
                for(int z0:new int[]{1,9}) {
                    for(int z=z0;z<=z0+1;z++){at(b,ox,oz,1,y+1,z,35,7);at(b,ox,oz,1,y+3,z,126,9);at(b,ox,oz,1,y+4,z,171,8);}
                    at(b,ox,oz,2,y+1,z0==1?1:10,85,0);at(b,ox,oz,2,y+2,z0==1?1:10,85,0);at(b,ox,oz,2,y+3,z0==1?1:10,126,9);
                }
                at(b,ox,oz,1,y+1,4,5,1);at(b,ox,oz,1,y+2,4,96,0);
                break;
            case 1: // mess: a trestle table with benches, a stove and a water butt
                for(int z=2;z<=3;z++){at(b,ox,oz,3,y+1,z,85,0);at(b,ox,oz,3,y+2,z,72,0);at(b,ox,oz,2,y+1,z,53,1);at(b,ox,oz,4,y+1,z,53,0);}
                at(b,ox,oz,1,y+1,9,61,5);at(b,ox,oz,1,y+1,10,118,3);at(b,ox,oz,2,y+1,10,5,1);
                break;
            case 2: // stores: crates, feed and cloth bales stacked against the wall
                at(b,ox,oz,1,y+1,1,5,1);at(b,ox,oz,1,y+1,2,5,1);at(b,ox,oz,2,y+1,1,5,1);at(b,ox,oz,1,y+2,1,5,1);
                at(b,ox,oz,1,y+1,9,170,0);at(b,ox,oz,1,y+1,10,170,0);at(b,ox,oz,2,y+1,10,170,0);at(b,ox,oz,1,y+2,10,35,12);
                at(b,ox,oz,3,y+1,1,118,1);
                break;
            case 3: // plant room: a control desk facing a generator block
                for(int z=2;z<=4;z++)at(b,ox,oz,1,y+1,z,159,15);
                at(b,ox,oz,1,y+2,2,69,5);at(b,ox,oz,1,y+2,3,151,0);at(b,ox,oz,1,y+2,4,69,5);at(b,ox,oz,2,y+1,3,134,0);
                for(int z=8;z<=10;z++){at(b,ox,oz,1,y+1,z,1,6);at(b,ox,oz,1,y+2,z,z==9?101:1,z==9?0:6);}
                break;
            case 4: // clinic: two cots with a screen, a basin
                for(int z0:new int[]{2,8}){at(b,ox,oz,1,y+1,z0,35,0);at(b,ox,oz,1,y+1,z0+1,35,0);at(b,ox,oz,2,y+1,z0==2?4:10,102,0);}
                at(b,ox,oz,3,y+1,1,118,2);
                break;
            default: // armoury: fence-post weapon racks under a slab shelf with weapon pegs above, ammunition crates
                for(int z=2;z<=4;z++){at(b,ox,oz,1,y+1,z,85,0);at(b,ox,oz,1,y+2,z,44,5);if(wallStands(s,r,ox,oz,0,z,0,3))at(b,ox,oz,1,y+3,z,131,3);}
                at(b,ox,oz,1,y+1,9,5,1);at(b,ox,oz,1,y+1,10,5,1);at(b,ox,oz,2,y+1,10,5,1);at(b,ox,oz,1,y+2,10,5,1);
                break;
        }
    }
    /** Backroom gates (143 rooms) were grey boxes with two yellow posts. Level-0 dressing in their own palette:
     * yellow wallpaper (room()), a damp yellow carpet over the level floor, and a grid of fluorescent sea-lantern
     * panels under the ceiling. The Fold door marker's cell and the cache are laid over it by marker(). */
    private static void backroom(StructurePlanner.Site s,StructurePlanner.Room r,Brush b,int ox,int oz,int y) {
        for(int x=1;x<=10;x++)for(int z=1;z<=10;z++) {
            if(x==9&&(z==2||z==9)||s.floor(r,x,z)!=r.floor||surfaceStair(s,r,x,z)>=0)continue;
            at(b,ox,oz,x,y+1,z,171,4);
        }
        // No panel under the ruin's collapsed roof bay (x 8..10, z 2..4): the (8, 3) panel hung in the open sky.
        for(int x:new int[]{3,8})for(int z:new int[]{3,8})if(!collapsed(s,ox,oz,x,z))at(b,ox,oz,x,y+5,z,169,0);
    }
    /** Keep (K) and battle-tower (T) storeys were identical: a stair flight, a plinth torch and a slab (31 bare
     * floors in one citadel). Each storey now has a garrison purpose -- guardroom at the gate, then billets,
     * armoury, stores and a command room in turn, and a lookout under the roof with iron-bar arrow loops
     * (room()) and a ladder to a hatch onto the crenellated roof, which nothing reached before. Everything
     * stands in the east bays, clear of the flight (x 1..4), the lanes and the caches. */
    private static void garrison(StructurePlanner.Site s,StructurePlanner.Room r,Brush b,int ox,int oz,int level,int y) {
        int storeys=StructureCatalog.floors(r.type),top=r.floor+storeys*6;
        int use=level==storeys-1?5:level==0?0:1+(level-1)%4;
        switch(use) {
            case 0: // guardroom: a table with stools, a weapon rack, a water butt
                at(b,ox,oz,6,y+1,3,85,0);at(b,ox,oz,6,y+2,3,72,0);at(b,ox,oz,5,y+1,3,53,1);at(b,ox,oz,7,y+1,3,53,0);
                for(int z=3;z<=4;z++){at(b,ox,oz,10,y+1,z,101,0);at(b,ox,oz,10,y+2,z,101,0);}
                at(b,ox,oz,8,y+1,10,118,3);
                break;
            case 1: // billets: a double bunk against the east wall, a cot by the south wall, a rug and a clothes chest
                // The upper bunk hangs on the east wall; where a ruin scar opened that wall it is left out (it floated).
                boolean hung=wallStands(s,r,ox,oz,11,3,level,3)&&wallStands(s,r,ox,oz,11,4,level,3);
                for(int z=3;z<=4;z++){at(b,ox,oz,10,y+1,z,35,7);if(hung){at(b,ox,oz,10,y+3,z,126,9);at(b,ox,oz,10,y+4,z,171,7);}at(b,ox,oz,8,y+1,z,171,8);}
                at(b,ox,oz,8,y+1,9,35,12);at(b,ox,oz,8,y+1,10,35,12);at(b,ox,oz,7,y+1,8,5,1);at(b,ox,oz,10,y+1,9,5,1);
                break;
            case 2: // armoury: fence racks under a slab shelf with weapon pegs above, a bar cage, fletching bales
                for(int z=3;z<=4;z++){at(b,ox,oz,10,y+1,z,85,0);at(b,ox,oz,10,y+2,z,44,5);if(wallStands(s,r,ox,oz,11,z,level,3))at(b,ox,oz,10,y+3,z,131,1);at(b,ox,oz,8,y+1,z,101,0);}
                at(b,ox,oz,8,y+1,9,170,0);at(b,ox,oz,8,y+1,10,170,0);
                // the armourer's bench and quench tub in the south-east bay
                at(b,ox,oz,10,y+1,10,58,0);at(b,ox,oz,10,y+1,9,118,3);
                break;
            case 3: // stores: crates and bales
                at(b,ox,oz,10,y+1,3,5,1);at(b,ox,oz,10,y+1,4,5,1);at(b,ox,oz,10,y+2,3,5,1);
                at(b,ox,oz,8,y+1,9,170,0);at(b,ox,oz,8,y+1,10,170,0);at(b,ox,oz,8,y+2,10,170,0);at(b,ox,oz,7,y+1,3,118,1);
                break;
            case 4: // command room: a map table with chairs, bookshelves
                for(int x=6;x<=7;x++){at(b,ox,oz,x,y+1,3,5,1);at(b,ox,oz,x,y+2,3,171,12);at(b,ox,oz,x,y+1,4,134,2);}
                for(int z=3;z<=4;z++){at(b,ox,oz,10,y+1,z,47,0);at(b,ox,oz,10,y+2,z,47,0);}
                break;
            default: // lookout: a bench under the loops, the alarm horn, a water butt and the roof ladder
                at(b,ox,oz,8,y+1,9,44,5);at(b,ox,oz,8,y+1,10,44,5);
                at(b,ox,oz,8,y+1,3,25,0);at(b,ox,oz,8,y+1,4,118,3);
                // A bell tower's lookout is its belfry: the bell (a bronze-coloured terracotta body) hangs on a post
                // from the roof over the middle of the storey, above head height (the towers had no bell at all).
                if(s.design.id.contains("bell")){at(b,ox,oz,6,y+5,6,85,0);at(b,ox,oz,6,y+4,6,159,4);}
                break;
        }
        if(use==5) {
            // East wall x=11 at z=7 is solid at every storey height (no window, no ruin scar in the transit band)
            // and the roof slab over it holds the last rung, which fills a one-block hatch onto the roof walk.
            b.column(ox+10,y+1,oz+7,top,65,4);
        }
    }
    /** Houses, mansions and tenement storeys had a kitchen corner and nowhere to sleep. A cottage gets a bed in the
     * south-west bay with a chest of drawers and a rug; the upper storeys of mansions and tenements get a bed
     * against the east wall with a wardrobe (the flight owns the west bays there); a tenement's ground storey
     * keeps its kitchen. Wool beds (no bed tile entities in chunk data), spruce, brown carpet. */
    private static void home(StructurePlanner.Room r,Brush b,int ox,int oz,int level,int y) {
        char c=r.type;int colour=level%2==0?14:11;
        if(c=='h') {
            at(b,ox,oz,1,y+1,9,35,14);at(b,ox,oz,1,y+1,10,35,14);at(b,ox,oz,2,y+1,10,5,1);
            for(int x=3;x<=4;x++)for(int z=3;z<=4;z++)at(b,ox,oz,x,y+1,z,171,12);
        } else if(level>0||c=='M') {
            if(level==0){for(int x=6;x<=7;x++)at(b,ox,oz,x,y+1,3,171,12);return;}
            at(b,ox,oz,10,y+1,4,35,colour);at(b,ox,oz,10,y+1,5,35,colour);
            at(b,ox,oz,10,y+1,9,5,1);at(b,ox,oz,10,y+2,9,5,1);
        }
    }
    /** Vaulted dungeon (D) and crypt (q) rooms were empty but for a plinth: two stone tombs with slab lids in the
     * west bays, clear of the west lane. */
    private static void crypt(StructurePlanner.Room r,Brush b,int ox,int oz,int y) {
        int datum=r.type=='q'?1:3;
        for(int z0:new int[]{2,8})for(int z=z0;z<=z0+1;z++){at(b,ox,oz,2,y+1,z,98,datum);at(b,ox,oz,2,y+2,z,44,5);}
        // Cobwebs in two vault corners, against both walls and the vault (never under a collapsed bay: none there).
        at(b,ox,oz,1,y+5,1,30,0);at(b,ox,oz,10,y+5,10,30,0);
        // and drifted into the two floor corners the tombs and the plinth leave free
        at(b,ox,oz,10,y+1,1,30,0);at(b,ox,oz,1,y+1,10,30,0);
    }
    /** A wall light at (x, dy, z): an interior cell off the stair flight, on a wall cell (x+dx, z+dz) that stands
     * at that height and is not glazed there. */
    private static boolean mount(StructurePlanner.Site s,StructurePlanner.Room r,int ox,int oz,int level,int x,int z,int dx,int dz,int dy) {
        char c=r.type;int wx=x+dx,wz=z+dz;
        if(!inside(c,x,z)||StructureCatalog.floors(c)>1&&x>=1&&x<=4&&z>=2&&z<=9)return false;
        if(!wallStands(s,r,ox,oz,wx,wz,level,dy))return false;
        return dy==4||!glazed(s,r,wx,wz,dy);
    }
    /** A footprint cell with footprint on all four sides (not a wall). */
    private static boolean inside(char c,int x,int z) {
        return footprint(c,x,z)&&footprint(c,x-1,z)&&footprint(c,x+1,z)&&footprint(c,x,z-1)&&footprint(c,x,z+1);
    }
    /** Mirrors the window rules of room() for a wall cell at height dy. */
    private static boolean glazed(StructurePlanner.Site s,StructurePlanner.Room r,int x,int z,int dy) {
        char c=r.type;
        if(wall(s,c)==20)return true;
        if(dy>=2&&dy<=3&&(x==0||x==11?z%4==2:x%4==2)&&windowed(s,r,x,z))return true;
        if(c=='F'&&(x==3||x==8)&&dy>=2&&dy<=3&&z%3==1)return true;
        return c=='F'&&s.design.room(r.col,r.row-1)!='F'&&z<=2&&dy>=2&&dy<=4;
    }
    /** A window whose outside column (the first side off the footprint) has ground at or above it would look into
     * the hillside (audit 2026-09: rooms set into slopes had glass against earth); the wall stays solid there. */
    private static boolean againstGround(Terrain t,char c,int wx,int wz,int x,int z,int y) {
        for(int[] d:StructureCatalog.DIRS)if(!footprint(c,x+d[0],z+d[1]))return t.sample(wx+d[0],wz+d[1]).y>=y;
        return false;
    }
    /** Which walls carry the standard window band. Buried backroom gates are glazed only towards a neighbouring
     * room (audit 2026-09: their windows looked into rock); glass habitats and fuselages keep theirs. */
    private static boolean windowed(StructurePlanner.Site s,StructurePlanner.Room r,int x,int z) {
        char c=r.type;
        if(!s.design.mode.equals("buried"))return true;
        if(c=='G'||c=='F')return true;
        if(c!='P')return false;
        return x==0&&s.connected(r,-1,0)||x==11&&s.connected(r,1,0)||z==0&&s.connected(r,0,-1)||z==11&&s.connected(r,0,1);
    }
    private static int cauldron(){return 118;}
    /** Props use the unused corner bays. Cardinal routes, the west stairwell (including landings),
     * and the east cache recess are an explicit contract with StructurePlanner's unchanged markers. */
    private static void prop(StructurePlanner.Room r,Brush b,int ox,int oz,int level,int x,int dy,int z,int id,int datum) {
        if(!footprint(r.type,x,z))return;
        boolean route=x>=5&&x<=7||z>=5&&z<=7;
        // The platform's outer rail continues beside the cache; its inner approach is the x=7 aisle.
        boolean cache=x>=7&&x<=9&&z>=7&&z<=9&&!(r.type=='R'&&x==9);
        if(dy<4&&(route||cache))return;
        if(StructureCatalog.floors(r.type)>1&&x>=1&&x<=4&&z>=2&&z<=10)return;
        b.put(ox+x,r.floor+level*6+dy,oz+z,id,datum);
    }
    private static void props(StructurePlanner.Room r,Brush b,int ox,int oz,int level,
                              int x1,int y1,int z1,int x2,int y2,int z2,int id,int datum) {
        for(int x=x1;x<=x2;x++)for(int z=z1;z<=z2;z++)for(int y=y1;y<=y2;y++)prop(r,b,ox,oz,level,x,y,z,id,datum);
    }
    private static void expansionFurniture(StructurePlanner.Site s,StructurePlanner.Room r,Brush b,int ox,int oz) {
        char c=r.type;
        for(int level=0;level<StructureCatalog.floors(c);level++) {
            switch(c) {
                case 'R': // Two broken track lengths flank an unobstructed steel pedestrian crossing.
                    for(int z=1;z<=10;z++) {
                        prop(r,b,ox,oz,level,9,0,z,5,1);
                        prop(r,b,ox,oz,level,9,1,z,66,0);
                        prop(r,b,ox,oz,level,8,0,z,159,4);
                    }
                    props(r,b,ox,oz,level,2,1,2,3,1,2,109,2);
                    props(r,b,ox,oz,level,2,1,10,3,1,10,109,3);
                    prop(r,b,ox,oz,level,9,1,10,42,0);
                    prop(r,b,ox,oz,level,2,1,3,85,0);
                    prop(r,b,ox,oz,level,2,2,3,77,5);
                    break;
                case 'V': // Ticket counters below, route-map gallery above; barrel ribs overhead.
                    props(r,b,ox,oz,level,8,1,2,10,1,2,155,0);
                    props(r,b,ox,oz,level,8,2,2,10,3,2,102,0);
                    prop(r,b,ox,oz,level,9,1,3,69,5); // a floor lever by the counter (it stood on air one block up)
                    props(r,b,ox,oz,level,8,1,10,10,1,10,44,0);
                    props(r,b,ox,oz,level,8,3,1,10,3,1,159,level==0?11:4);
                    for(int z:new int[]{2,6,9})for(int x:new int[]{1,4,7,10})
                        // The low springers hang on the side wall: none where a ruin scar or a raised doorway took it.
                        if(x>1&&x<10||wallStands(s,r,ox,oz,x==1?0:11,z,level,4))
                            prop(r,b,ox,oz,level,x,x==1||x==10?4:5,z,156,x<6?0:1);
                    break;
                case 'L': // Glazed specimen columns, worktops, sinks and upper observation consoles.
                    for(int z:new int[]{2,4,10}) {
                        prop(r,b,ox,oz,level,9,1,z,155,0);
                        prop(r,b,ox,oz,level,9,2,z,95,level==0?5:3);
                        prop(r,b,ox,oz,level,9,3,z,20,0);
                        prop(r,b,ox,oz,level,9,4,z,167,0);
                    }
                    props(r,b,ox,oz,level,8,1,1,10,1,1,42,0);
                    prop(r,b,ox,oz,level,8,1,3,118,2);
                    prop(r,b,ox,oz,level,10,2,1,69,5);
                    break;
                case 'E': // Boiler banks and an overhead gantry; machinery never bridges walking lanes.
                    for(int x:new int[]{2,9}) {
                        props(r,b,ox,oz,level,x,1,2,x,2,4,61,3);
                        props(r,b,ox,oz,level,x,3,2,x,4,4,42,0);
                        prop(r,b,ox,oz,level,x,1,10,145,0);
                        prop(r,b,ox,oz,level,x,1,9,118,0);
                    }
                    props(r,b,ox,oz,level,2,5,3,9,5,3,101,0);
                    break;
                case 'I': // Two lined water tanks with overhead pipes and valve cabinets.
                    for(int x:new int[]{2,8}) {
                        props(r,b,ox,oz,level,x,0,2,x+1,0,4,9,0);
                        props(r,b,ox,oz,level,x,0,9,x+1,0,10,9,0);
                        props(r,b,ox,oz,level,x,4,2,x+1,4,4,101,0);
                    }
                    props(r,b,ox,oz,level,1,4,3,10,4,3,168,2);
                    // The pipe beam hangs from the vault on two bar drops (it floated where scars took both wall ends).
                    for(int x:new int[]{2,9})if(!collapsed(s,ox,oz,x,3))prop(r,b,ox,oz,level,x,5,3,101,0);
                    prop(r,b,ox,oz,level,10,1,2,42,0);
                    prop(r,b,ox,oz,level,10,2,2,69,5);
                    break;
                case 'J': // Raised examination couches, privacy screens and washbasins.
                    for(int x:new int[]{2,9})for(int z:new int[]{2,9}) {
                        props(r,b,ox,oz,level,x,1,z,x,1,z+1,44,7);
                        prop(r,b,ox,oz,level,x,2,z,171,0);
                        // Screens stand on the floor (beside the cache the couch is left out and the pane hung in air).
                        props(r,b,ox,oz,level,x+1,1,z,x+1,2,z,102,0);
                    }
                    prop(r,b,ox,oz,level,9,1,4,118,2);
                    // The corner panel hangs on the walls; where ruin scars took both it hung in the gap.
                    if(wallStands(s,r,ox,oz,0,1,level,2)||wallStands(s,r,ox,oz,1,0,level,2))prop(r,b,ox,oz,level,1,2,1,159,14);
                    break;
                case 'H': // Choir stalls, elevated lectern and stone vault ribs; upper choir is stair-accessible.
                    props(r,b,ox,oz,level,8,1,2,10,1,2,134,3);
                    props(r,b,ox,oz,level,8,1,10,10,1,10,134,2);
                    prop(r,b,ox,oz,level,9,1,4,47,0);
                    prop(r,b,ox,oz,level,9,2,4,158,2);
                    for(int x:new int[]{1,10})for(int z:new int[]{1,4,8,10}) {
                        props(r,b,ox,oz,level,x,1,z,x,4,z,98,3);
                        // A top-storey rib hangs from the roof alone, so none under the collapsed bay (it floated).
                        if(level<StructureCatalog.floors(c)-1||!collapsed(s,ox,oz,x==1?2:9,z))
                            prop(r,b,ox,oz,level,x==1?2:9,5,z,109,x==1?0:1);
                    }
                    break;
                case 'Y': // Kitchen/refectory downstairs, bunk alcoves in the dormitory above.
                    if(level==0) {
                        props(r,b,ox,oz,level,9,1,2,9,1,4,85,0);
                        props(r,b,ox,oz,level,9,2,2,9,2,4,72,0);
                        props(r,b,ox,oz,level,10,1,2,10,1,4,53,1);
                        prop(r,b,ox,oz,level,9,1,10,61,3);
                    } else {
                        props(r,b,ox,oz,level,9,1,2,10,1,3,126,1);
                        props(r,b,ox,oz,level,9,3,2,10,3,3,126,1);
                        props(r,b,ox,oz,level,10,1,1,10,4,1,17,1);
                        props(r,b,ox,oz,level,9,1,10,10,1,10,170,0);
                        props(r,b,ox,oz,level,8,1,2,8,1,3,171,12); // a rag rug along the bunks
                    }
                    break;
                case 'O': // Survey library, instrument gallery, then a diagonal telescope on the third floor.
                    if(level<2) {
                        props(r,b,ox,oz,level,9,1,2,10,level==0?3:1,4,level==0?47:155,0);
                        prop(r,b,ox,oz,level,9,level==0?4:2,2,151,0); // on the bookcase top below, on the quartz counter upstairs (it floated at (8,2,2))
                        prop(r,b,ox,oz,level,9,1,10,58,0);
                    } else {
                        prop(r,b,ox,oz,level,8,1,3,139,0);
                        prop(r,b,ox,oz,level,8,2,3,42,0);
                        props(r,b,ox,oz,level,9,1,3,9,2,3,101,0); // the tube's stand (it met the pier only at an edge)
                        prop(r,b,ox,oz,level,9,3,3,154,4);
                        prop(r,b,ox,oz,level,10,3,3,95,11);
                        prop(r,b,ox,oz,level,9,1,4,69,5); // a floor lever (it stood on air one block up)
                    }
                    break;
                case 'Q': case 'c': // Ossuary niches versus low mortuary slabs and embalming sinks.
                    for(int x:new int[]{2,9})for(int z:new int[]{2,9}) {
                        props(r,b,ox,oz,level,x,1,z,x,1,z+1,c=='Q'?216:155,0);
                        props(r,b,ox,oz,level,x,2,z,x,2,z+1,44,c=='Q'?0:7);
                        if(c=='Q')prop(r,b,ox,oz,level,x,3,z,216,4);
                        else prop(r,b,ox,oz,level,x+1,1,z,118,1);
                    }
                    for(int z:new int[]{2,5,9})for(int x:new int[]{3,8})
                        if(!collapsed(s,ox,oz,x,z))prop(r,b,ox,oz,level,x,5,z,109,x<6?0:1);
                    // cobwebs drifted into the ends of the vault's long arm (its cross plan has no corners)
                    if(c=='Q'){prop(r,b,ox,oz,level,3,1,1,30,0);prop(r,b,ox,oz,level,8,1,10,30,0);}
                    break;
                case 'X': // Four barred holding bays open into the cross aisle, each with a sleeping ledge.
                    for(int x:new int[]{1,8})for(int z:new int[]{1,9}) {
                        props(r,b,ox,oz,level,x,1,z,x+2,3,z,101,0);
                        props(r,b,ox,oz,level,x,1,z+1,x+1,1,z+1,44,0);
                        prop(r,b,ox,oz,level,x+2,1,z+1,118,0);
                    }
                    break;
                case 'Z': // Tall shelves and an upper manuscript cage, plus a desk outside the cache recess.
                    props(r,b,ox,oz,level,9,1,1,10,3,4,47,0);
                    props(r,b,ox,oz,level,9,1,10,10,3,10,level==0?47:101,0);
                    prop(r,b,ox,oz,level,8,1,3,85,0);
                    prop(r,b,ox,oz,level,8,2,3,72,0);
                    prop(r,b,ox,oz,level,8,1,4,53,3);
                    break;
                case 'a': case 'g': // Open herb cloister versus a two-floor glazed seed house.
                    for(int x:new int[]{2,8})for(int z:new int[]{2,9}) {
                        props(r,b,ox,oz,level,x,0,z,x+1,0,z+1,3,0);
                        props(r,b,ox,oz,level,x,1,z,x+1,1,z+1,38,c=='a'?0:3);
                    }
                    if(c=='g') {
                        props(r,b,ox,oz,level,10,1,1,10,3,4,102,0);
                        prop(r,b,ox,oz,level,9,1,4,118,3);
                    }
                    break;
                case 'b': // Cold kiln mouths, brick flues and stacked slag; no active fire or lava.
                    for(int x:new int[]{2,9}) {
                        props(r,b,ox,oz,level,x,1,2,x,3,4,112,0);
                        prop(r,b,ox,oz,level,x,1,3,61,3);
                        prop(r,b,ox,oz,level,x,2,3,101,0);
                        props(r,b,ox,oz,level,x,1,9,x+1,2,10,87,0);
                    }
                    break;
                case 'e': // Shoring, ore face and a hoist suspended above the excavation's footpath.
                    for(int z:new int[]{2,9}) {
                        // The south post stood in the cache recess, which keeps only y+4 up: its beam floated.
                        // That set is propped at its west end instead.
                        int post=z==2?9:3;
                        props(r,b,ox,oz,level,post,1,z,post,5,z,17,1);
                        props(r,b,ox,oz,level,3,5,z,9,5,z,17,5);
                    }
                    props(r,b,ox,oz,level,8,1,2,8,2,4,1,1);
                    prop(r,b,ox,oz,level,8,2,3,1,1); // plain granite face (was iron ore: no ore blocks in builds)
                    prop(r,b,ox,oz,level,9,1,10,58,0);
                    break;
                case 'i': // A timber hull under repair, net racks, mooring posts and cargo pallets.
                    props(r,b,ox,oz,level,8,1,2,10,1,4,126,1);
                    props(r,b,ox,oz,level,8,2,2,8,2,4,134,0);
                    props(r,b,ox,oz,level,10,2,2,10,2,4,134,1);
                    props(r,b,ox,oz,level,2,1,2,2,3,4,101,0);
                    props(r,b,ox,oz,level,1,1,10,3,2,10,17,1);
                    prop(r,b,ox,oz,level,10,1,10,139,0);
                    break;
                case 'j': // Four trade stalls with counters and cloth awnings at different heights.
                    for(int x:new int[]{1,8})for(int z:new int[]{1,9}) {
                        props(r,b,ox,oz,level,x,1,z,x+2,1,z,5,1);
                        prop(r,b,ox,oz,level,x,2,z,170,0);
                        props(r,b,ox,oz,level,x+2,1,z+1,x+2,z>6?4:3,z+1,85,0); // posts reach the awning (y+4 north, y+5 south)
                    }
                    break;
                case 'k': // Sealed relic cases and an upper bell cage, all off the central encounter dais.
                    for(int z:new int[]{2,10}) {
                        props(r,b,ox,oz,level,8,1,z,10,1,z,155,2);
                        props(r,b,ox,oz,level,8,2,z,10,3,z,95,level==2?4:10);
                        prop(r,b,ox,oz,level,9,2,z,level==2?41:216,0);
                        props(r,b,ox,oz,level,8,4,z,10,4,z,44,7);
                        if(c=='k')props(r,b,ox,oz,level,8,1,z==2?3:9,10,1,z==2?3:9,171,14); // a red runner before the cases
                    }
                    break;
                case 'l': // Raked seating corners face a side stage; cross aisles and encounter centre stay level.
                    for(int x:new int[]{1,8})for(int z:new int[]{2,4,9})
                        props(r,b,ox,oz,level,x,1,z,x+2,1,z,134,z==9?2:3);
                    props(r,b,ox,oz,level,1,1,10,4,1,10,5,1);
                    props(r,b,ox,oz,level,1,2,10,1,4,10,159,14);
                    props(r,b,ox,oz,level,10,1,10,10,4,10,159,14); // floor-length (it hung from the scarred corner)
                    prop(r,b,ox,oz,level,3,2,10,25,0); // the side stage's speaker cabinet
                    break;
                case 'm': // An inset mosaic atlas, drafting tables and triangulation instruments.
                    for(int x=1;x<=4;x++)for(int z=1;z<=4;z++)
                        prop(r,b,ox,oz,level,x,0,z,159,(x+z)%3==0?11:5);
                    props(r,b,ox,oz,level,8,1,2,10,1,3,85,0);
                    props(r,b,ox,oz,level,8,2,2,10,2,3,72,0);
                    prop(r,b,ox,oz,level,9,2,2,151,0); // the instrument stands on the table post (it sat on a plate)
                    props(r,b,ox,oz,level,1,1,10,3,2,10,47,0);
                    break;
                case 't': // Guard racks and sleeping quarters beneath a crenellated parapet.
                    props(r,b,ox,oz,level,9,1,1,10,3,1,101,0);
                    props(r,b,ox,oz,level,8,1,2,10,1,3,126,1);
                    props(r,b,ox,oz,level,9,1,10,10,1,10,170,0);
                    prop(r,b,ox,oz,level,9,1,1,42,0);
                    prop(r,b,ox,oz,level,9,2,1,44,5); // a whetstone bench top (an anvil per storey was a free iron cache)
                    prop(r,b,ox,oz,level,8,1,10,118,3); // the guards' water butt beside the bales
                    break;
                case 'v': // Open wind arcade has corner benches and tall, interrupted stone arches.
                    props(r,b,ox,oz,level,3,1,3,4,1,3,44,0);
                    props(r,b,ox,oz,level,8,1,9,9,1,9,44,0);
                    break;
                case 'w': // Recessed plunge pools, tiled rims, side benches and overhead shower rails.
                    for(int x:new int[]{2,8})for(int z:new int[]{2,9}) {
                        props(r,b,ox,oz,level,x,0,z,x+1,0,z+1,9,0);
                        prop(r,b,ox,oz,level,x+2,1,z,156,1);
                        // Shower screens hang from the vault (the rails floated at y+4); none under the collapsed bay.
                        if(!collapsed(s,ox,oz,x,z))props(r,b,ox,oz,level,x,4,z,x+1,5,z,101,0);
                    }
                    prop(r,b,ox,oz,level,10,1,4,118,3);
                    break;
                case 'p': // Relay cabinets, lever panels and cable trays; recording consoles above.
                    props(r,b,ox,oz,level,9,1,1,10,3,4,159,15);
                    for(int z:new int[]{1,3}) {
                        prop(r,b,ox,oz,level,8,1,z,42,0);
                        prop(r,b,ox,oz,level,8,2,z,69,5);
                        prop(r,b,ox,oz,level,8,3,z,77,2); // on the cabinet face (east), not on the aisle air
                    }
                    props(r,b,ox,oz,level,1,5,3,10,5,3,101,0);
                    props(r,b,ox,oz,level,9,1,10,10,2,10,level==0?42:25,0);
                    break;
                default: throw new IllegalArgumentException("Unrendered expansion room "+c);
            }
        }
    }
    /** Roofs stay inside their own 12-block tile. Only new room codes take this path. The existing
     * structural ceiling remains a waterproof cap under domes, pitched roofs and ventilation crowns. */
    private static void expansionRoof(StructurePlanner.Room r,Brush b,int ox,int oz) {
        char c=r.type;int top=r.floor+StructureCatalog.floors(c)*6;
        for(int x=0;x<12;x++)for(int z=0;z<12;z++) {
            if(!footprint(c,x,z))continue;
            int wx=ox+x,wz=oz+z;
            if(c=='O') {
                double radius=Math.sqrt((x-5.5)*(x-5.5)+(z-5.5)*(z-5.5));
                if(radius<=5.5) {
                    int rise=Math.max(1,5-(int)(radius*radius/7));
                    b.column(wx,top+1,wz,top+rise,z==5||z==6?20:155,0);
                }
            } else if(c=='k') {
                int rise=Math.max(0,8-Math.max(Math.abs(2*x-11),Math.abs(2*z-11)));
                b.column(wx,top+1,wz,top+rise,98,3);
                if(x==5&&z==5)b.column(wx,top+rise+1,wz,top+10,139,0);
            } else if(c=='H'||c=='Y'||c=='g') {
                int rise=Math.min(x,11-x)/(c=='Y'?2:1);
                int id=c=='g'?20:c=='Y'?5:112;
                b.column(wx,top+1,wz,top+rise,id,c=='Y'?1:0);
                b.put(wx,top+rise+1,wz,c=='g'?20:c=='Y'?134:114,c=='g'?0:x<6?0:1);
            } else if(c=='V'||c=='Q'||c=='I'||c=='w') {
                int rise=Math.min(3,Math.min(Math.min(x,11-x),Math.min(z,11-z)));
                b.column(wx,top+1,wz,top+rise,c=='V'?155:c=='I'||c=='w'?168:216,0);
            } else if(c=='E'||c=='b') {
                // The flue starts in the roof slab, so a collapsed roof bay around it leaves it standing.
                if(x>=8&&x<=9&&z>=2&&z<=3)b.column(wx,top,wz,top+(c=='b'?8:5),c=='b'?112:45,0);
                if(x==3&&z>=2&&z<=9)b.put(wx,top+1,wz,167,0);
            } else if(c=='L'||c=='J'||c=='p') {
                if(x>=8&&x<=10&&z>=1&&z<=4) {
                    b.put(wx,top+1,wz,42,0);
                    b.put(wx,top+2,wz,z%2==0?101:44,0);
                }
            } else if(c=='R'||c=='i'||c=='l') {
                // Stepped roof. Each raised strip is carried by a solid step under its whole width; risers at the
                // low edge alone left the strips as shelves with a one/two-block gap under them (~28 designs).
                int step=c=='l'?z:x;
                if(x>=1&&x<=10) {
                    if(step>=4)b.column(wx,top+1,wz,top+step/4,c=='i'?5:43,c=='i'?1:0);
                    b.put(wx,top+1+step/4,wz,c=='i'?5:44,c=='i'?1:0);
                }
            } else if(c=='t'||c=='X'||c=='Z') {
                if((x==0||x==11||z==0||z==11)&&(x+z)%3!=1)b.column(wx,top+1,wz,top+2,c=='Z'?45:98,0);
            } else if(c=='a') {
                if((x==1||x==10)&&(z==1||z==4||z==8||z==10))b.column(wx,r.floor+1,wz,r.floor+5,98,3);
                if(x==1||x==10||z==1||z==10)b.put(wx,r.floor+5,wz,44,0);
            } else if(c=='j') {
                if((x>=1&&x<=4||x>=8&&x<=10)&&(z>=1&&z<=3||z>=9&&z<=10))
                    b.put(wx,r.floor+4+(z>6?1:0),wz,35,x<6?12:14);
            } else if(c=='v') {
                if((x==3||x==9)&&(z==3||z==9))b.column(wx,r.floor+1,wz,r.floor+9,98,3);
                if(z==3&&x>=3&&x<=9||z==9&&x>=3&&x<=7)b.put(wx,r.floor+9,wz,109,x<6?0:1);
            } else if(c=='e') {
                if(x==9&&z==2)b.column(wx,r.floor+1,wz,r.floor+8,17,1);
                if(z==2&&x>=3&&x<=9)b.put(wx,r.floor+8,wz,101,0);
                if(x==3&&z==2)b.column(wx,r.floor+4,wz,r.floor+7,101,0);
            } else if(c=='c'||c=='m') {
                if(x>=3&&x<=8&&z>=2&&z<=9)b.put(wx,top+1,wz,c=='c'?216:126,c=='c'?0:1);
            }
        }
    }
    private static int scar(StructurePlanner.Site s,int x,int z,int salt){return (int)(Terrain.mix(s.seed+x*341873128712L+z*132897987541L+salt)&0x7fffffff);}
    private static void mast(StructurePlanner.Site s,StructurePlanner.Room r,Brush b,int ox,int oz) {
        int y=r.floor;
        for(int x:new int[]{4,8})for(int z:new int[]{4,8})b.column(ox+x,y+1,oz+z,y+23-(x==8&&z==8?7:0),101,0);
        for(int x=4;x<=8;x++)b.put(ox+x,y+18,oz+4,42,0);
        b.column(ox+4,y+19,oz+4,y+26,101,0);b.put(ox+4,y+23,oz+5,167,4);
    }
    private static void wire(StructurePlanner.Site s,StructurePlanner.Room r,Brush b,int ox,int oz) {
        // Continuous broken-line routes across street tiles; both sides agree on boundary cable height.
        if(r.type=='=') {b.column(ox+3,r.floor+1,oz+3,r.floor+10,85,0);for(int x=3;x<=7;x++)b.put(ox+x,r.floor+10,oz+3,85,0);}
        for(int[] dir:StructureCatalog.DIRS)if(s.connected(r,dir[0],dir[1])) {
            int other=s.floors[(r.row+dir[1])*s.design.columns+r.col+dir[0]];
            char adjacent=s.design.room(r.col+dir[0],r.row+dir[1]);if(adjacent!='='&&adjacent!='C')continue;
            int boundary=(r.floor+other)/2+9,previous=r.floor+11,steps=dir[0]+dir[1]>0?5:6;
            for(int i=0;i<=steps;i++) {
                int cy=r.floor+11+(boundary-r.floor-11)*i/steps;
                b.column(ox+6+dir[0]*i,Math.min(previous,cy),oz+6+dir[1]*i,Math.max(previous,cy),101,0);previous=cy;
            }
        }
    }
    private static void aircraft(StructurePlanner.Site s,StructurePlanner.Room r,Brush b,int ox,int oz) {
        int y=r.floor;
        if(r.type=='F') {
            // Glazed cockpit, broken radome and a raised, swept tail identify the wreck from outside.
            if(s.design.room(r.col,r.row-1)!='F') {
                for(int x=4;x<=7;x++){b.put(ox+x,y+3,oz+1,20,0);b.put(ox+x,y+4,oz+2,20,0);}
                b.put(ox+5,y+1,oz+2,77,3);b.put(ox+7,y+1,oz+2,69,5);
            }
            if(s.design.room(r.col,r.row+1)!='F') {
                for(int z=8;z<=10;z++)for(int dy=7;dy<=13-(10-z)*2;dy++)b.put(ox+6,y+dy,oz+z,42,0);
                for(int x=2;x<=9;x++)b.put(ox+x,y+8,oz+9,44,0);
            }
            for(int z=2;z<=9;z++) {b.put(ox+4,y+5,oz+z,44,0);b.put(ox+7,y+5,oz+z,44,0);}
        } else {
            // Sheet-metal wing skin, raised spar, and broken engine pods; the centre remains a walkway.
            for(int x=0;x<12;x++)for(int z=4;z<=8;z++)if(z!=6&&z!=7) {
                b.put(ox+x,y,oz+z,42,0);if(z==4)b.put(ox+x,y+1,oz+z,44,0);
            }
            for(int x=8;x<=10;x++)for(int z=8;z<=10;z++) {
                b.put(ox+x,y,oz+z,42,0);b.put(ox+x,y+1,oz+z,z==10?61:42,3);
            }
        }
    }
    private static void arch(Brush b,int ox,int y,int oz) {
        for(int x:new int[]{4,8})b.column(ox+x,y+1,oz+3,y+9,98,0);
        for(int x=4;x<=8;x++)b.put(ox+x,y+9,oz+3,98,3);
    }
    private static void approach(StructurePlanner.Site s,Brush b,Terrain t) {
        Route route=route(s);int[] floors=route.floor;int n=floors.length;
        int x=s.entryX(),z=s.entryZ();boolean wet=s.design.mode.equals("underwater");
        for(int i=0;i<n;i++) {
            int floor=floors[i],wz=z+i+1;
            if(!b.intersects(x-2,wz,5,1))continue;
            int before=i==0?s.y:floors[i-1],after=i+1<n?floors[i+1]:floor;
            boolean landing=route.landing&&i==n-1,roof=route.roofed[i];
            for(int dx=-2;dx<=2;dx++) {
                if(!b.intersects(x+dx,wz,1,1))continue;
                int ground=t.sample(x+dx,wz).y;
                // The pier runs down to the ground the chunk really has (a cut, a cave breach or a drained bed
                // left the heightmap's footing hanging in the air under ~1,900 approach cells in 210 captures).
                boolean pillar=Math.abs(dx)==2&&(i%4==0||i==n-1)||dx==0&&i==n-1;
                b.column(x+dx,footing(b,x+dx,Math.min(ground-2,floor-2),wz,pillar),wz,floor,98,2);
                if(Math.abs(dx)<2) {
                    b.column(x+dx,floor+1,wz,floor+4,0,0);
                    // The far end's centre is a full block, so a ladder can hang from its face (see below).
                    if(landing)b.put(x+dx,floor,wz,dx==0?98:109,dx==0?0:3);
                    else if(i==n-1&&dx==0)b.put(x,floor,wz,98,0);
                    else b.put(x+dx,floor,wz,after>floor?109:before>floor?109:98,after>floor?2:before>floor?3:0);
                    if(roof)b.put(x+dx,floor+4,wz,wet?20:98,0);
                } else if(!landing) {
                    b.column(x+dx,floor+1,wz,floor+(roof?4:1),roof?(wet?20:98):85,0);
                }
            }
        }
        // Where this chunk shows the ground falling away (or open water) beyond the last cell, a ladder hangs
        // from the causeway's end face down to the real ground, so the approach always meets the world.
        int end=z+n+1,deck=floors[n-1];
        // Foliage hanging over the step off the end blocked the way out (a tree beside the last cell).
        for(int dx=-1;dx<=1;dx++)if(b.intersects(x+dx,end,1,1))for(int y=deck+1;y<=deck+3;y++) {
            int id=b.id(x+dx,y,end);
            if(id==18||id==161||id==106)b.put(x+dx,y,end,0,0);   // leaves and vines only: a cut trunk would float its crown
        }
        if(b.intersects(x,end,1,1)&&!b.ground(x,deck,end)&&!b.ground(x,deck+1,end)&&!b.ground(x,deck+2,end)) {
            int foot=b.footing(x,deck,end);
            if(foot<deck-1) {
                // The rungs hang on the end cell's pier; where that pier found ground higher up (a ledge under the
                // last cell) it is carried down beside the ladder (16 rungs hung on air at one site). When the last
                // cell lies in the neighbouring chunk, this chunk builds its own pier at the end column and the
                // ladder hangs one column further out, so rungs and backing are always built together.
                if(b.intersects(x,end-1,1,1)) {
                    for(int y=foot;y<deck;y++)if(!b.solid(x,y,end-1))b.put(x,y,end-1,98,2);
                    b.column(x,foot,end,deck,65,3);
                } else if(b.intersects(x,end+1,1,1)) {
                    int out=b.footing(x,deck,end+1);
                    b.column(x,Math.min(foot,out),end,deck,98,2);
                    if(out<deck-1)b.column(x,out,end+1,deck,65,3);
                } else b.column(x,foot,end,deck,65,3);
            }
        }
    }
    /** Room-local columns (x and z both pillar lines) that carry a pier through a deep void. */
    private static boolean pillar(int v){return v==0||v==4||v==7||v==11;}
    /** A gap this deep under a footing is bridged by pillars only; shallower ones are filled solid. */
    private static final int DEEP=6;
    /** Where a footing meant to start at y really starts: down to the ground under it when that is within DEEP
     * blocks, or on a pillar column at any depth (to MAX_PIER); other columns over a deeper void keep their slab,
     * which the pillars carry (a building on stilts, a causeway on piers -- not a block under every block). */
    private static int footing(Brush b,int wx,int y,int wz,boolean pillar) {
        int foot=b.footing(wx,y,wz);
        return y-foot>DEEP&&!pillar?y:foot;
    }
    // ------------------------------------------------------------------ the approach as built
    private static final Map<StructurePlanner.Site,Route> ROUTES=new WeakHashMap<>();
    /** How far the causeway may carry on past the planned ramp, inside the site's 96-block approach reserve. */
    private static final int EXTENSION=24;
    /** The approach as built (3.23 structure audit). StructurePlanner.approach is the planned ramp and the envelope
     * the site reserves. It follows Terrain.sample, which knows neither the surface-opening cuts nor the cave carve
     * nor the sea, so in the shared2 captures 53 approaches ended in open water (no way to climb out), 35 ended
     * above or below the ground they were meant to meet and 210 had piers hanging in the air. The built route
     * keeps the plan wherever its far end meets the real ground; otherwise it takes the first of:
     *   a water-line landing (a stair out of the water) when the plan ends over open water;
     *   the ramp re-walked over the real ground, when that ends on the ground;
     *   the plan with its last flights lowered/raised onto the ground beyond the end;
     *   the causeway carried on at deck height (climbing, never falling) to the first ground it meets;
     *   the plan as it is -- approach() then hangs a ladder from its end face.
     * Every value is a pure function of the seed and the site, so every chunk builds the same route. */
    private static final class Route {
        final int[] floor;final boolean[] roofed;final boolean landing;
        Route(int[] floor,boolean[] roofed,boolean landing){this.floor=floor;this.roofed=roofed;this.landing=landing;}
    }
    private static Route route(StructurePlanner.Site s) {
        synchronized(ROUTES){Route known=ROUTES.get(s);if(known!=null)return known;}
        Route made=planRoute(s);
        synchronized(ROUTES){ROUTES.put(s,made);}
        return made;
    }
    private static final int FAIL=0,MEETS=1,LANDING=2;
    private static Route planRoute(StructurePlanner.Site s) {
        Ground g=new Ground(new Terrain(s.seed));
        int[] plan=s.approach;int n=plan.length,max=Math.max(n,Math.min(StructurePlanner.APPROACH,n+EXTENSION));
        int x=s.entryX(),z=s.entryZ();
        int[] top=new int[max+1],liquid=new int[max+1];boolean[] lava=new boolean[max+1],pit=new boolean[max+1];
        for(int i=0;i<=max;i++){top[i]=g.top(x,z+i+1);liquid[i]=g.liquid;lava[i]=g.lava;pit[i]=g.pit;}
        // First only routes that end on open ground: the floor of a ravine, pit or cave breach is walled in (the
        // re-walked ramp led one chapel's approach down into a surface pit it could not leave). Only when no route
        // does, one that ends in such a hole is taken.
        int[] floor=null;int end=FAIL;
        // A meeting column counts as open ground only with no hole in the three columns after it either (a thin
        // fin between two pits is an island: one plateau approach ended on one).
        boolean[] closed=new boolean[max+1];
        for(int i=0;i<=max;i++)for(int k=i;k<=Math.min(max,i+3)&&!closed[i];k++)closed[i]=pit[k];
        for(int pass=0;pass<2&&floor==null;pass++) {
            boolean[] avoid=pass==0?closed:new boolean[max+1];
            int[] made=candidate(s,plan,n,max,top,liquid,lava,avoid);
            if(made!=null){floor=made;end=made.length>0&&liquid[made.length]!=Ground.NONE?LANDING:MEETS;}
        }
        if(floor==null){floor=plan.clone();end=FAIL;}
        // Walkable beyond the end: where the ground past the meeting point steps up or down two blocks or more
        // within LOOK columns (a mesa terrace, a bank), the causeway carries on over it as a stair, so the
        // approach does not end on a shelf the player cannot climb onto (audit 2026-09, coalbreath b).
        if(end==MEETS)floor=onward(floor,max,top,liquid,pit);
        boolean landing=end==LANDING;
        int last=floor.length-1;
        if(landing)floor[last]=liquid[last+1];
        boolean[] roofed=new boolean[floor.length];
        for(int i=0;i<floor.length;i++) {
            // Roofed only where the corridor really runs under ground or water; in a pit, canyon or cave breach
            // it is an open stair (it used to be a stone tube lying in the open wherever the deck was below 63).
            roofed[i]=floor[i]<63&&!(landing&&i==last)&&(liquid[i]!=Ground.NONE&&floor[i]<=liquid[i]||top[i]>floor[i]+1);
        }
        return new Route(floor,roofed,landing);
    }
    /** How far past the meeting point onward() looks for a step the player cannot climb. */
    private static final int LOOK=8;
    /** The first route (see Route) that meets the world at a column not flagged in `avoid`, or null. */
    private static int[] candidate(StructurePlanner.Site s,int[] plan,int n,int max,int[] top,int[] liquid,boolean[] lava,boolean[] avoid) {
        // The plan, unless its end misses the ground.
        if(!avoid[n]&&meets(plan,n,top,liquid,lava)!=FAIL)return plan.clone();
        // The ramp re-walked over the real ground (bridging water and lava at their surface).
        int[] walk=new int[n];int previous=s.y;
        for(int i=0;i<n;i++){walk[i]=previous+Integer.signum(stand(top[i],liquid[i])-previous);previous=walk[i];}
        if(!avoid[n]&&meets(walk,n,top,liquid,lava)!=FAIL)return walk;
        // The plan with its last flights lowered or raised onto the dry ground beyond its end.
        if(liquid[n]==Ground.NONE&&!avoid[n]) {
            int[] adjusted=plan.clone();adjusted[n-1]=top[n];
            for(int i=n-2;i>=0;i--){
                int v=Math.max(adjusted[i+1]-1,Math.min(adjusted[i+1]+1,plan[i]));
                if(v==adjusted[i])break;
                adjusted[i]=v;
            }
            if(Math.abs(adjusted[0]-s.y)<=1)return adjusted;
        }
        // The causeway carried on at deck height, climbing where the ground rises, to the first ground it meets.
        if(max>n) {
            int[] carried=Arrays.copyOf(plan,max);previous=plan[n-1];
            for(int j=n;j<max;j++) {
                carried[j]=previous+(stand(top[j],liquid[j])>previous?1:0);previous=carried[j];
                if(!avoid[j+1]&&meets(carried,j+1,top,liquid,lava)!=FAIL)return Arrays.copyOf(carried,j+1);
            }
        }
        return null;
    }
    /** The route carried on past its meeting point over any step of two or more blocks in the dry, open ground
     * within LOOK columns, one block per column (a stair), until it stands on the ground beyond the step. */
    private static int[] onward(int[] floor,int max,int[] top,int[] liquid,boolean[] pit) {
        int length=floor.length,step=-1,previous=floor[length-1];
        for(int j=length;j<Math.min(max,length+LOOK);j++) {
            if(liquid[j]!=Ground.NONE||pit[j])return floor;
            if(Math.abs(top[j]-previous)>=2){step=j;break;}
            previous=top[j];
        }
        if(step<0)return floor;
        int[] out=Arrays.copyOf(floor,max);int d=floor[length-1];
        for(int j=length;j<max;j++) {
            if(liquid[j]!=Ground.NONE||pit[j])return floor;
            d+=Integer.signum(top[j]-d);out[j]=d;
            // Past the step, with the next column walkable from this cell (the meeting rule of meets()).
            if(j>=step&&Math.abs(top[j]-d)<=1&&liquid[j+1]==Ground.NONE&&!pit[j+1]&&Math.abs(top[j+1]-d)<=1)return Arrays.copyOf(out,j+1);
        }
        return floor;
    }
    /** The deck height that stands on a column: its ground, or one above its water/lava surface. */
    private static int stand(int top,int liquid){return liquid==Ground.NONE?top:Math.max(top,liquid+1);}
    /** Whether a route of this length meets the world at the column just past its end. */
    private static int meets(int[] floor,int length,int[] top,int[] liquid,boolean[] lava) {
        int deck=floor[length-1];
        if(liquid[length]==Ground.NONE)return Math.abs(deck-top[length])<=1?MEETS:FAIL;
        // A landing at the water line: the last cell drops to the surface, one step below the cell before it.
        boolean flat=length<2||floor[length-2]<=liquid[length]+1;
        return !lava[length]&&deck==liquid[length]+1&&flat?LANDING:FAIL;
    }
    /** What HorrorGenerator really leaves at a column, worked out the way it does: the heightmap, the surface-
     * opening cut and the cave carve, with the sea, pools and lava fills as a liquid surface. Trees, pruning and
     * drained water are not modelled; the chunk-local footing scan and end ladder cover what this misses. */
    private static final class Ground {
        static final int NONE=Integer.MIN_VALUE;
        private final Terrain t;private final Caves caves;private final SurfaceOpenings openings;
        private final SurfaceOpenings.Cut cut=new SurfaceOpenings.Cut();
        private final Map<Long,double[][][]> fields=new HashMap<>();
        int liquid=NONE;boolean lava;
        /** The column's ground lies three or more blocks under the undisturbed surface: the floor of a ravine, pit
         * or cave breach, walled in on the sides. */
        boolean pit;
        Ground(Terrain t){this.t=t;caves=new Caves(t);openings=new SurfaceOpenings(t,caves);}
        int top(int wx,int wz) {
            int h=t.sample(wx,wz).y,y=ground(wx,wz,h);
            pit=liquid==NONE&&y<h-2;
            return y;
        }
        private int ground(int wx,int wz,int h) {
            liquid=NONE;lava=false;
            if(h<62){liquid=62;return h;}                       // sea: the carve stops three blocks under its bed
            openings.sample(wx,wz,h,cut);
            int ground=h;
            if(cut.depth>0) {
                ground=Math.max(6,h-cut.depth);
                if(cut.fill!=0){liquid=Math.min(cut.fillTop,h);lava=cut.fill==11;return ground;}
            } else if(cut.rise>0)ground=Math.min(250,h+cut.rise);
            if(ground>Caves.CEILING)return ground;
            int cx=Math.floorDiv(wx,16),cz=Math.floorDiv(wz,16);
            double[][][] field=field(cx,cz);double breach=caves.breach(wx,wz);
            for(int y=ground;y>=Caves.FLOOR;y--) {
                double damp=Caves.surfaceDamping(y,ground,breach);
                if(damp<=0||Caves.at(field,wx-cx*16,y,wz-cz*16)*damp<=0)return y;
            }
            return Caves.FLOOR-1;
        }
        private double[][][] field(int cx,int cz) {
            long key=((long)cx<<32)^(cz&0xffffffffL);
            double[][][] f=fields.get(key);
            if(f==null) {
                f=new double[Caves.LAT_XZ][Caves.LAT_XZ][Caves.LAT_Y];
                caves.sampleChunk(cx,cz,f,new int[Caves.LAT_XZ][Caves.LAT_XZ]);
                fields.put(key,f);
            }
            return f;
        }
    }
    private static void marker(StructurePlanner.Site s,Brush b,StructurePlanner.Marker m) {
        if(m.kind.equals("door")) {
            b.put(m.x,m.y,m.z,201,0);
            b.column(m.x,m.y+1,m.z,m.y+3,0,0);
            b.put(m.x+1,m.y,m.z,201,0);b.put(m.x+1,m.y+1,m.z,198,1);
            b.put(m.x-1,m.y,m.z,201,0);b.put(m.x-1,m.y+1,m.z,198,1);
        } else {
            // Marker columns are always supported and reachable; no inventory/entity state is created here.
            b.put(m.x,m.y-1,m.z,98,0);b.column(m.x,m.y,m.z,m.y+2,0,0);
            if(!m.kind.equals("mob")&&!m.kind.equals("boss"))b.put(m.x,m.y,m.z,54,2);
        }
    }
    private static final class Brush {
        final ChunkGenerator.ChunkData data;final int x,z;
        Brush(ChunkGenerator.ChunkData data,int cx,int cz){this.data=data;x=cx*16;z=cz*16;}
        boolean intersects(int wx,int wz,int width,int depth){return (long)wx+width>x&&wx<(long)x+16&&(long)wz+depth>z&&wz<(long)z+16;}
        void put(int wx,int y,int wz,int id,int datum){
            // Every authored structure block passes through this method. Valuable storage
            // blocks are never valid construction material: preserve the intended colour
            // while replacing them with ordinary decorative blocks that cannot be harvested
            // for ingots or diamonds.
            if(id==42){id=1;datum=6;}       // iron block -> polished andesite
            else if(id==41){id=159;datum=4;} // gold block -> yellow hardened clay
            else if(id==57){id=159;datum=9;} // diamond block -> cyan hardened clay
            if(wx>=x&&(long)wx<x+16L&&wz>=z&&(long)wz<z+16L&&y>0&&y<250){
                data.setBlock(wx-x,y,wz-z,id,(byte)datum);
                CaptureHook.Sink hook=CaptureHook.sink;if(hook!=null)hook.write(null,wx,y,wz,id,datum&15,"catalog"); // test harness only
            }
        }
        void column(int wx,int low,int wz,int high,int id,int datum){for(int y=Math.max(1,low);y<=Math.min(249,high);y++)put(wx,y,wz,id,datum);}
        /** A block in this chunk that something can rest on (not air, liquid or a plant). */
        boolean solid(int wx,int y,int wz){return intersects(wx,wz,1,1)&&y>0&&y<250&&!Floaters.loose(data.getTypeId(wx-x,y,wz-z));}
        int id(int wx,int y,int wz){return intersects(wx,wz,1,1)&&y>0&&y<250?data.getTypeId(wx-x,y,wz-z):0;}
        /** Real ground in this chunk: something solid that is not a tree's foliage. */
        boolean ground(int wx,int y,int wz){
            if(!solid(wx,y,wz))return false;
            int id=data.getTypeId(wx-x,y,wz-z);return id!=18&&id!=161;
        }
        /** The lowest block of a pier/footing meant to start at y: y itself when real ground lies right under it,
         * else the block just above the first real ground further down (air, water, lava, plants and leaves are
         * passed through), looking at most MAX_PIER blocks down. y again when there is none within reach. */
        int footing(int wx,int y,int wz){
            if(!intersects(wx,wz,1,1))return y;
            for(int k=Math.min(y,249);k>1&&y-k<MAX_PIER;k--)if(ground(wx,k-1,wz))return k;
            return y;
        }
        /** The first real ground at or below y (looking `depth` blocks down), or Integer.MIN_VALUE. */
        int surface(int wx,int y,int wz,int depth){
            for(int k=Math.min(y,249);k>0&&y-k<=depth;k--)if(ground(wx,k,wz))return k;
            return Integer.MIN_VALUE;
        }
        void removeVegetation(int wx,int low,int wz,int high,String tree) {
            if(!intersects(wx,wz,1,1))return;
            for(int y=low;y<=high&&y<250;y++) {
                int id=data.getTypeId(wx-x,y,wz-z);
                if(id==17||id==18||id==31||id==38||id==81||id==78||tree.equals("frost")&&(id==80||id==174)
                    ||tree.matches("red|blossom|crystal")&&id==159||tree.equals("petrified")&&id==1)put(wx,y,wz,0,0);
            }
        }
    }
}
