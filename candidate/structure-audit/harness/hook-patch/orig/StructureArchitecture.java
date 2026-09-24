package chat.jaspr.biomes;

import org.bukkit.generator.ChunkGenerator;

/** Chunk-clipped architectural vocabulary: occupied volumes only, with per-column footings. */
final class StructureArchitecture {
    private StructureArchitecture() {}
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
                    int h=t.sample(ox+x,oz+z).y;b.put(ox+x,h+1,oz+z,44,3);
                }
                continue;
            }
            int wx=ox+x,wz=oz+z,f=s.floor(r,x,z),ground=t.sample(wx,wz).y;
            b.removeVegetation(wx,ground+1,wz,ground+19,t.sample(wx,wz).profile.tree);
            // Embedded stepped piers support only an occupied column, leaving deep ores/caves untouched.
            b.column(wx,Math.min(f-2,ground-2),wz,f,98,2);
            b.put(wx,f,wz,floor,floor==5?1:0);
            int stairs=surfaceStair(s,r,x,z);
            if(stairs>=0)b.put(wx,f,wz,109,stairs);
            boolean edge=!footprint(c,x-1,z)||!footprint(c,x+1,z)||!footprint(c,x,z-1)||!footprint(c,x,z+1);
            boolean door=s.doorway(r,x,z);
            // Never clear the reserved rectangle or the hill above a roof. Each room owns its interior.
            b.column(wx,f+1,wz,open?f+4:top-1,0,0);
            if(!open) {
                for(int level=0;level<storeys;level++) {
                    int base=r.floor+level*6;
                    if(level>0)b.put(wx,base,wz,floor,floor==5?1:0);
                    if(edge)for(int dy=1;dy<=5;dy++) {
                        if(level==0&&door&&base+dy<=f+3)continue;
                        int id=wall,datum=wall==98?(scar(s,wx,wz,dy)%5==0?1:2):wallData;
                        if((c=='G'||c=='F'||c=='P'||!s.design.mode.equals("buried"))&&dy>=2&&dy<=3&&(x==0||x==11?z%4==2:x%4==2)) {id=20;datum=0;}
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
                    if((x==2||x==9)&&z%3==0)b.put(wx,top-1,wz,109,x==2?0:1);
                } else if(c=='K'||c=='T') {
                    if(edge&&(x+z)%2==0)b.column(wx,top+1,wz,top+2,98,0);
                }
                if(!wet&&!s.design.mode.equals("buried")&&x>=8&&x<=10&&z>=2&&z<=4&&scar(s,ox,oz,32)%3==0)
                    b.column(wx,top,wz,top+4,0,0);
                if(wet&&ground<63)b.column(wx,top+1,wz,62,9,0);
            } else if(c=='=') {
                if(x==6||z==6)b.put(wx,f,wz,159,4);
            } else if(c=='o') {
                if(x>=2&&x<=3&&z>=2&&z<=3) {b.put(wx,f,wz,3,0);b.put(wx,f+1,wz,38,0);}
            }
        }
        if(storeys>1)for(int level=0;level<storeys-1;level++) staircase(b,ox,r.floor+level*6,oz);
        if(c=='C')mast(s,r,b,ox,oz);
        if(s.design.family.equals("telecom")&&(c=='C'||c=='='))wire(s,r,b,ox,oz);
        if(c=='F'||c=='W')aircraft(s,r,b,ox,oz);
        if(c=='A')arch(b,ox,r.floor,oz);
        furniture(s,r,b,ox,oz);
        if(StructureCatalog.EXPANSION_ROOMS.indexOf(c)>=0) {
            expansionFurniture(r,b,ox,oz);
            expansionRoof(r,b,ox,oz);
        }
        if(!wet&&!s.design.mode.equals("buried")&&!StructureCatalog.open(c)) {
            for(int lx:new int[]{1,10})for(int lz:new int[]{1,10})if(footprint(c,lx,lz)&&scar(s,ox+lx,oz+lz,33)%3==0)
                b.put(ox+lx,r.floor+1,oz+lz,44,3);
        }
        // Entrances/cross-terrace openings are cut last to keep raised sills and their headroom usable.
        for(int x=0;x<12;x++)for(int z=0;z<12;z++)if(s.doorway(r,x,z)) {
            int f=s.floor(r,x,z);b.column(ox+x,f+1,oz+z,f+3,0,0);
        }
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
    private static int surfaceStair(StructurePlanner.Site s,StructurePlanner.Room r,int x,int z) {
        int f=s.floor(r,x,z);
        if(x<11&&s.floor(r,x+1,z)>f)return 0;
        if(x>0&&s.floor(r,x-1,z)>f)return 1;
        if(z<11&&s.floor(r,x,z+1)>f)return 2;
        if(z>0&&s.floor(r,x,z-1)>f)return 3;
        return -1;
    }
    private static void staircase(Brush b,int ox,int floor,int oz) {
        // Two-wide, six-riser flights; adjacent storeys share a guarded stairwell with six-block pitch.
        for(int z=3;z<=8;z++)for(int x=2;x<=3;x++) {
            int step=z-2;
            b.column(ox+x,floor+step+1,oz+z,floor+step+3,0,0);
            b.put(ox+x,floor+step-1,oz+z,98,0);
            b.put(ox+x,floor+step,oz+z,109,2);
            b.put(ox+1,floor+step+1,oz+z,85,0);
            b.put(ox+4,floor+step+1,oz+z,85,0);
        }
    }
    private static void furniture(StructurePlanner.Site s,StructurePlanner.Room r,Brush b,int ox,int oz) {
        char c=r.type;
        for(int level=0;level<StructureCatalog.floors(c);level++) {
            int y=r.floor+level*6;
            if(c=='h'||c=='M') {
                b.put(ox+8,y+1,oz+2,58,0);b.put(ox+9,y+1,oz+2,61,3);
                b.put(ox+8,y+1,oz+3,85,0);b.put(ox+8,y+2,oz+3,72,0);b.put(ox+9,y+1,oz+3,53,1);
                if(c=='M')b.column(ox+9,y+1,oz+9,y+3,47,0);
            } else if(c=='s'||c=='S') {
                for(int z:new int[]{2,9}) {b.put(ox+8,y+1,oz+z,85,0);b.put(ox+8,y+2,oz+z,72,0);b.put(ox+9,y+1,oz+z,53,1);}
                b.column(ox+10,y+1,oz+3,y+3,47,0);b.put(ox+1,y+2,oz+6,159,15);
            } else if(c=='B') {
                b.put(ox+9,y+1,oz+2,61,3);b.put(ox+9,y+1,oz+3,145,0);
                b.column(ox+9,y+1,oz+9,y+2,42,0);b.put(ox+8,y+1,oz+2,69,5);
            } else if(c=='U') {
                for(int z=2;z<=4;z++){b.put(ox+9,y,oz+z,9,0);b.put(ox+10,y+1,oz+z,101,0);}
                b.put(ox+9,y+3,oz+3,101,0);b.put(ox+8,y+1,oz+2, cauldron(),0);
            } else if(c=='F') {
                for(int z:new int[]{2,4,9}) {b.put(ox+4,y+1,oz+z,53,2);b.put(ox+7,y+1,oz+z,53,2);}
                b.put(ox+6,y+1,oz+1,69,5);
            } else if(c=='P') {
                for(int z:new int[]{2,9})b.column(ox+9,y+1,oz+z,y+3,159,4);
                b.put(ox+6,y+5,oz+6,169,0);
            } else if(c=='G') {
                b.put(ox+9,y+1,oz+3,168,2);b.put(ox+9,y+2,oz+3,20,0);b.put(ox+9,y+3,oz+3,169,0);
                b.put(ox+9,y+1,oz+9, cauldron(),0);
            } else if(c=='D'||c=='q'||c=='K'||c=='T') {
                b.column(ox+9,y+1,oz+2,y+2,98,c=='q'?1:3);
                b.put(ox+9,y+3,oz+2,50,5);b.put(ox+9,y+1,oz+9,44,0);
            } else if(c>='2'&&c<='8') {
                b.put(ox+8,y+1,oz+2,85,0);b.put(ox+8,y+2,oz+2,72,0);
                b.put(ox+9,y+1,oz+3,53,1);b.column(ox+9,y+1,oz+9,y+2,47,0);
            }
            // Low local light keeps navigable interiors readable; biome/world lighting is untouched.
            if(!StructureCatalog.open(c)&&c!='P')b.put(ox+6,y+4,oz+1,50,3);
        }
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
    private static void expansionFurniture(StructurePlanner.Room r,Brush b,int ox,int oz) {
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
                    prop(r,b,ox,oz,level,9,2,3,69,5);
                    props(r,b,ox,oz,level,8,1,10,10,1,10,44,0);
                    props(r,b,ox,oz,level,8,3,1,10,3,1,159,level==0?11:4);
                    for(int z:new int[]{2,6,9})for(int x:new int[]{1,4,7,10})
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
                    prop(r,b,ox,oz,level,10,1,2,42,0);
                    prop(r,b,ox,oz,level,10,2,2,69,5);
                    break;
                case 'J': // Raised examination couches, privacy screens and washbasins.
                    for(int x:new int[]{2,9})for(int z:new int[]{2,9}) {
                        props(r,b,ox,oz,level,x,1,z,x,1,z+1,44,7);
                        prop(r,b,ox,oz,level,x,2,z,171,0);
                        prop(r,b,ox,oz,level,x+1,2,z,102,0);
                    }
                    prop(r,b,ox,oz,level,9,1,4,118,2);
                    prop(r,b,ox,oz,level,1,2,1,159,14);
                    break;
                case 'H': // Choir stalls, elevated lectern and stone vault ribs; upper choir is stair-accessible.
                    props(r,b,ox,oz,level,8,1,2,10,1,2,134,3);
                    props(r,b,ox,oz,level,8,1,10,10,1,10,134,2);
                    prop(r,b,ox,oz,level,9,1,4,47,0);
                    prop(r,b,ox,oz,level,9,2,4,158,2);
                    for(int x:new int[]{1,10})for(int z:new int[]{1,4,8,10}) {
                        props(r,b,ox,oz,level,x,1,z,x,4,z,98,3);
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
                    }
                    break;
                case 'O': // Survey library, instrument gallery, then a diagonal telescope on the third floor.
                    if(level<2) {
                        props(r,b,ox,oz,level,9,1,2,10,level==0?3:1,4,level==0?47:155,0);
                        prop(r,b,ox,oz,level,8,2,2,151,0);
                        prop(r,b,ox,oz,level,9,1,10,58,0);
                    } else {
                        prop(r,b,ox,oz,level,8,1,3,139,0);
                        prop(r,b,ox,oz,level,8,2,3,42,0);
                        prop(r,b,ox,oz,level,9,3,3,154,4);
                        prop(r,b,ox,oz,level,10,3,3,95,11);
                        prop(r,b,ox,oz,level,9,2,4,69,5);
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
                        prop(r,b,ox,oz,level,x,5,z,109,x<6?0:1);
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
                        props(r,b,ox,oz,level,9,1,z,9,5,z,17,1);
                        props(r,b,ox,oz,level,3,5,z,9,5,z,17,5);
                    }
                    props(r,b,ox,oz,level,8,1,2,8,2,4,1,1);
                    prop(r,b,ox,oz,level,8,2,3,15,0);
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
                        props(r,b,ox,oz,level,x+2,1,z+1,x+2,3,z+1,85,0);
                    }
                    break;
                case 'k': // Sealed relic cases and an upper bell cage, all off the central encounter dais.
                    for(int z:new int[]{2,10}) {
                        props(r,b,ox,oz,level,8,1,z,10,1,z,155,2);
                        props(r,b,ox,oz,level,8,2,z,10,3,z,95,level==2?4:10);
                        prop(r,b,ox,oz,level,9,2,z,level==2?41:216,0);
                        props(r,b,ox,oz,level,8,4,z,10,4,z,44,7);
                    }
                    break;
                case 'l': // Raked seating corners face a side stage; cross aisles and encounter centre stay level.
                    for(int x:new int[]{1,8})for(int z:new int[]{2,4,9})
                        props(r,b,ox,oz,level,x,1,z,x+2,1,z,134,z==9?2:3);
                    props(r,b,ox,oz,level,1,1,10,4,1,10,5,1);
                    props(r,b,ox,oz,level,1,2,10,1,4,10,159,14);
                    props(r,b,ox,oz,level,10,2,10,10,4,10,159,14);
                    break;
                case 'm': // An inset mosaic atlas, drafting tables and triangulation instruments.
                    for(int x=1;x<=4;x++)for(int z=1;z<=4;z++)
                        prop(r,b,ox,oz,level,x,0,z,159,(x+z)%3==0?11:5);
                    props(r,b,ox,oz,level,8,1,2,10,1,3,85,0);
                    props(r,b,ox,oz,level,8,2,2,10,2,3,72,0);
                    prop(r,b,ox,oz,level,9,3,2,151,0);
                    props(r,b,ox,oz,level,1,1,10,3,2,10,47,0);
                    break;
                case 't': // Guard racks and sleeping quarters beneath a crenellated parapet.
                    props(r,b,ox,oz,level,9,1,1,10,3,1,101,0);
                    props(r,b,ox,oz,level,8,1,2,10,1,3,126,1);
                    props(r,b,ox,oz,level,9,1,10,10,1,10,170,0);
                    prop(r,b,ox,oz,level,9,1,1,42,0);
                    prop(r,b,ox,oz,level,9,2,1,145,0);
                    break;
                case 'v': // Open wind arcade has corner benches and tall, interrupted stone arches.
                    props(r,b,ox,oz,level,3,1,3,4,1,3,44,0);
                    props(r,b,ox,oz,level,8,1,9,9,1,9,44,0);
                    break;
                case 'w': // Recessed plunge pools, tiled rims, side benches and overhead shower rails.
                    for(int x:new int[]{2,8})for(int z:new int[]{2,9}) {
                        props(r,b,ox,oz,level,x,0,z,x+1,0,z+1,9,0);
                        prop(r,b,ox,oz,level,x+2,1,z,156,1);
                        props(r,b,ox,oz,level,x,4,z,x+1,4,z,101,0);
                    }
                    prop(r,b,ox,oz,level,10,1,4,118,3);
                    break;
                case 'p': // Relay cabinets, lever panels and cable trays; recording consoles above.
                    props(r,b,ox,oz,level,9,1,1,10,3,4,159,15);
                    for(int z:new int[]{1,3}) {
                        prop(r,b,ox,oz,level,8,1,z,42,0);
                        prop(r,b,ox,oz,level,8,2,z,69,5);
                        prop(r,b,ox,oz,level,8,3,z,77,1);
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
                if(x>=8&&x<=9&&z>=2&&z<=3)b.column(wx,top+1,wz,top+(c=='b'?8:5),c=='b'?112:45,0);
                if(x==3&&z>=2&&z<=9)b.put(wx,top+1,wz,167,0);
            } else if(c=='L'||c=='J'||c=='p') {
                if(x>=8&&x<=10&&z>=1&&z<=4) {
                    b.put(wx,top+1,wz,42,0);
                    b.put(wx,top+2,wz,z%2==0?101:44,0);
                }
            } else if(c=='R'||c=='i'||c=='l') {
                if(x>=1&&x<=10)b.put(wx,top+1+(c=='l'?z/4:x/4),wz,c=='i'?5:44,c=='i'?1:0);
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
        int x=s.entryX(),z=s.entryZ();boolean wet=s.design.mode.equals("underwater");
        for(int i=0;i<s.approach.length;i++) {
            int floor=s.approach[i],wz=z+i+1;
            if(!b.intersects(x-2,wz,5,1))continue;
            int before=i==0?s.y:s.approach[i-1],after=i+1<s.approach.length?s.approach[i+1]:floor;
            for(int dx=-2;dx<=2;dx++) {
                int ground=t.sample(x+dx,wz).y;
                b.column(x+dx,Math.min(ground-2,floor-2),wz,floor,98,2);
                if(Math.abs(dx)<2) {
                    b.column(x+dx,floor+1,wz,floor+4,0,0);
                    b.put(x+dx,floor,wz,after>floor?109:before>floor?109:98,after>floor?2:before>floor?3:0);
                    if(floor<63)b.put(x+dx,floor+4,wz,wet?20:98,0);
                } else {
                    b.column(x+dx,floor+1,wz,floor+(floor<63?4:1),floor<63?(wet?20:98):85,0);
                }
            }
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
            if(wx>=x&&(long)wx<x+16L&&wz>=z&&(long)wz<z+16L&&y>0&&y<250)data.setBlock(wx-x,y,wz-z,id,(byte)datum);
        }
        void column(int wx,int low,int wz,int high,int id,int datum){for(int y=Math.max(1,low);y<=Math.min(249,high);y++)put(wx,y,wz,id,datum);}
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
