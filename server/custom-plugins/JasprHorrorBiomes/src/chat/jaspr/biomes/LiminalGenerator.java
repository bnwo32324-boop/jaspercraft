package chat.jaspr.biomes;

import java.util.*;
import org.bukkit.*;
import org.bukkit.block.*;
import org.bukkit.generator.BlockPopulator;
import org.bukkit.generator.ChunkGenerator;

/** Finite rooms, entirely chunk-local. Door links are a graph, never adjacent corridors. */
public final class LiminalGenerator extends ChunkGenerator {
    public static final int SIDE=4, ROOM_COUNT=SIDE*SIDE, SIZE=SIDE*16;
    // North, east, south, west. Opposite doors undo the corresponding graph edge.
    private static final int[] LINKS={5,1,-5,-1};
    private static final int[][] DOORS={{8,1},{14,8},{8,14},{1,8}};
    private static final int[][] LANDINGS={{8,3},{12,8},{8,12},{3,8}};
    private static final int[][] LABELS={{6,2,3},{13,6,4},{10,13,2},{2,10,5}};
    private static final String[] NAMES={"Return Foyer","Copy Office","Lost Archive","Drowned Bath","Service Tunnel","Waiting Room","Pipe Gallery","Night Classroom",
            "Crate Depot","Green Laboratory","Inverted Hall","Empty Theatre","Stair to Nowhere","Black Chapel","Last Hotel","No Sky Well"};

    public static int floor(int room){checkRoom(room);return room==0?64:48+Math.floorMod(room*3,4)*16;}
    public static int destination(int room,int door){checkRoom(room);checkDoor(door);return Math.floorMod(room+LINKS[door],ROOM_COUNT);}
    public static int opposite(int door){checkDoor(door);return (door+2)%4;}
    private static void checkRoom(int room){if(room<0||room>=ROOM_COUNT)throw new IllegalArgumentException("Room out of bounds");}
    private static void checkDoor(int door){if(door<0||door>=4)throw new IllegalArgumentException("Door out of bounds");}
    public static String roomName(int room){checkRoom(room);return NAMES[room];}

    public static Location foyer(World world){return new Location(world,8.5,65,8.5,180,0);}
    public static Location exitMarker(World world){return new Location(world,8,65,5);}
    public static Location door(World world,int room,int side){
        checkRoom(room);checkDoor(side);
        return new Location(world,(room%SIDE)*16+DOORS[side][0],floor(room)+1,(room/SIDE)*16+DOORS[side][1]);
    }
    public static Location arrival(World world,int room,int side){
        checkRoom(room);checkDoor(side);
        return new Location(world,(room%SIDE)*16+LANDINGS[side][0]+.5,floor(room)+1,
                (room/SIDE)*16+LANDINGS[side][1]+.5,new float[]{0,90,180,-90}[side],0);
    }
    /** Fresh locations; reading markers never loads a chunk or fills a chest. Foyer has no loot. */
    public static List<Location> lootMarkers(World world){
        List<Location> result=new ArrayList<>();
        for(int room=1;room<ROOM_COUNT;room++)result.add(new Location(world,(room%SIDE)*16+4,floor(room)+1,(room/SIDE)*16+10));
        return Collections.unmodifiableList(result);
    }
    public static boolean inBounds(Location at){
        return at!=null&&Double.isFinite(at.getX())&&Double.isFinite(at.getY())&&Double.isFinite(at.getZ())
                &&at.getX()>=2&&at.getX()<SIZE-2&&at.getZ()>=2&&at.getZ()<SIZE-2&&at.getY()>=2&&at.getY()<144;
    }
    public static int roomAt(Location at){
        if(!inBounds(at))return -1;
        return (at.getBlockZ()>>4)*SIDE+(at.getBlockX()>>4);
    }
    /** Match either half of a generated door; random player-built iron doors are ordinary blocks. */
    public static int doorAt(Location at){
        if(at==null||at.getBlockX()<0||at.getBlockX()>=SIZE||at.getBlockZ()<0||at.getBlockZ()>=SIZE)return -1;
        int room=(at.getBlockZ()>>4)*SIDE+(at.getBlockX()>>4),y=at.getBlockY()-floor(room);
        if(y!=1&&y!=2)return -1;
        for(int side=0;side<4;side++)if((at.getBlockX()&15)==DOORS[side][0]&&(at.getBlockZ()&15)==DOORS[side][1])return side;
        return -1;
    }

    @Override public ChunkData generateChunkData(World world,Random ignored,int cx,int cz,BiomeGrid biomes){
        // The server gets `chunk`; builders write through `data`, which is the same object unless the
        // structure-audit test harness is attached (CaptureHook.tap).
        ChunkData chunk=createChunkData(world),data=CaptureHook.tap(chunk,world.getName(),cx,cz,"fold");
        for(int x=0;x<16;x++)for(int z=0;z<16;z++)biomes.setBiome(x,z,Biome.PLAINS);
        // View-distance fringe chunks stay empty. No loads, populators or unbounded structure growth.
        if(cx<0||cx>=SIDE||cz<0||cz>=SIDE)return chunk;
        int room=cz*SIDE+cx,base=floor(room),roof=base+5+room%4;
        data.setRegion(1,base-1,1,15,base,15,Material.STONE);
        Material flooring=room==3||room==9?Material.QUARTZ_BLOCK:room==4||room==6||room==15?Material.SMOOTH_BRICK:Material.WOOD;
        data.setRegion(1,base,1,15,base+1,15,flooring);
        data.setRegion(1,base+1,1,15,roof,15,Material.AIR);
        for(int x=1;x<=14;x++)for(int z=1;z<=14;z++){
            int color=room==3?3:room==4||room==6?7:room==9?5:room==13?15:room==11?14:4;
            if(x==1||x==14||z==1||z==14)data.setRegion(x,base+1,z,x+1,roof,z+1,159,color);
            data.setBlock(x,roof,z,Material.QUARTZ_BLOCK);
            if(x%4==0&&z%4==0)data.setBlock(x,roof,z,Material.SEA_LANTERN);
        }
        if(room!=0){
            furnish(data,room,base,roof);
            // 1.12's native palette has chest facings 2..5; data 0 is not a valid chest state.
            data.setBlock(4,base+1,10,54,(byte)2);
        }
        // Audit: fittings that say what each room is for (room 0 included); aisles, landings,
        // the cross, door/label cells, the chest and the foyer arrival pad stay clear.
        fixtures(data,room,base,roof);
        for(int side=0;side<4;side++){
            int x=DOORS[side][0],z=DOORS[side][1];
            data.setBlock(x,base+1,z,71,(byte)((side+1)%4));
            data.setBlock(x,base+2,z,71,(byte)8);
            data.setBlock(x,base+3,z,Material.SEA_LANTERN);
            data.setBlock(LABELS[side][0],base+2,LABELS[side][1],68,(byte)LABELS[side][2]);
        }
        if(room==0){
            data.setBlock(8,65,5,Material.SEA_LANTERN);
            data.setBlock(8,66,5,63,(byte)0);
        }
        return chunk;
    }
    /** Each room has a different silhouette and furnishings. Preserve a two-block cross and outer aisle. */
    private static void furnish(ChunkData data,int room,int base,int roof){
        // Audit: the wells' shaft housing (stone brick, x/z 3..12 under the floor); the loop below carves the
        // twelve-deep shafts into it, so every well is a lined shaft instead of a plate hanging in the void.
        if(room==15)data.setRegion(3,base-13,3,13,base,13,Material.SMOOTH_BRICK);
        for(int x=4;x<=11;x++)for(int z=4;z<=11;z++){
            if(x==8||x==9||z==8||z==9||x==4&&z==10)continue;
            switch(room){
                case 1: // Copy office: desks, chair backs and bulky white copiers.
                    // Audit: spruce desk tops (read against the oak floor), paper stacks on the free desks,
                    // chairs south of both desk rows with the seat facing the desk (53:2 = back to the south).
                    if(z==5||z==10){data.setBlock(x,base+1,z,5,(byte)1);if(x%3==1)put(data,x,base+2,z,Material.QUARTZ_BLOCK);else data.setBlock(x,base+2,z,171,(byte)0);}
                    if((z==6||z==11&&x!=4)&&x%2==0)data.setBlock(x,base+1,z,53,(byte)2);break;
                case 2: // Archive: tall parallel shelves with open reading aisles.
                    if(x==5||x==7||x==11)for(int y=1;y<=4;y++)put(data,x,base+y,z,Material.BOOKSHELF);break;
                case 3: // Sunken bath: water is BELOW the dry circulation paths, contained by the floor edges.
                    // Audit: the lamp that floated on the water becomes a sunken pool light, one per pool.
                    put(data,x,base-1,z,(x==5||x==10)&&(z==5||z==10)?Material.SEA_LANTERN:Material.PRISMARINE);
                    put(data,x,base,z,Material.STATIONARY_WATER);break;
                case 4: // Repeated low stone tunnel arches, with a visible high ceiling beyond them.
                    if(z==4||z==7||z==11){
                        if(x==4||x==7||x==11)for(int y=1;y<=3;y++)put(data,x,base+y,z,Material.SMOOTH_BRICK);
                        // Audit: the one lintel cell under a ceiling lantern becomes a lit keystone.
                        put(data,x,base+4,z,x%4==0&&z%4==0?Material.SEA_LANTERN:Material.SMOOTH_BRICK);
                    }break;
                case 5: // Waiting room benches facing empty counters (the counter is built in fixtures()).
                    if(z==5||z==7||z==11)data.setBlock(x,base+1,z,134,(byte)2);break;
                case 6: // Pipe gallery: low-value polished-andesite risers and iron-bar cross-pipes.
                    if((x==5||x==10)&&(z==5||z==10))for(int y=1;y<roof-base;y++)data.setBlock(x,base+y,z,1,(byte)6);
                    break; // Audit: the overhead runs are laid wall to wall in fixtures().
                case 7: // Classroom: individual desks plus a dark chalkboard wall (the board is set into the wall in fixtures()).
                    if(x%2==0&&z%2==0){put(data,x,base+1,z,Material.FENCE);put(data,x,base+2,z,Material.WOOD_PLATE);
                        data.setBlock(x,base+1,z+1,53,(byte)2);} // Audit: a chair behind every desk, facing the board.
                    break;
                case 8: // Depot: irregular stacks, never extra loot-bearing tile entities.
                    // Audit: spruce crates on horizontal oak-log skids, lids on the single crates, slab pallets in the gaps.
                    if((x+z)%3!=0){int top=1+(x*3+z)%3;
                        for(int y=1;y<=top;y++){if(y==1&&top>1)data.setBlock(x,base+y,z,17,(byte)(x%2==0?4:8));else data.setBlock(x,base+y,z,5,(byte)1);}
                        if(top==1)data.setBlock(x,base+2,z,96,(byte)0);
                    }else data.setBlock(x,base+1,z,126,(byte)0);break;
                case 9: // Glass laboratory tanks around empty stone worktops.
                    if(x==4||x==7||x==10||z==4||z==7||z==11){
                        put(data,x,base+1,z,Material.QUARTZ_BLOCK);
                        // Audit: the closed NW enclosure stays a full glass tank; elsewhere the quartz runs are
                        // waist-high benches with a pane screen along their back edge.
                        if(x<=7&&z<=7)for(int y=2;y<=3;y++)data.setBlock(x,base+y,z,95,(byte)5);
                        else if(x==10&&z<=7||z==11&&x<=7)for(int y=2;y<=3;y++)data.setBlock(x,base+y,z,160,(byte)5);
                    }else if(x<=6&&z<=6){ // Audit: the tank holds water and a green specimen.
                        for(int y=1;y<=3;y++)put(data,x,base+y,z,Material.STATIONARY_WATER);
                        if(x==5&&z==5)put(data,x,base+1,z,Material.SLIME_BLOCK);
                    }break;
                case 10: // Inverted hall: furniture hangs above; the ordinary floor is conspicuously empty.
                    // Audit: nothing hangs over the (4,4) ceiling lantern; an oak slab under each hanging fence is its table top.
                    if(z%2==0&&!(x%4==0&&z%4==0)){data.setBlock(x,roof-1,z,53,(byte)6);
                        if(x%2==0){put(data,x,roof-2,z,Material.FENCE);data.setBlock(x,roof-3,z,126,(byte)8);}}break;
                case 11: // Empty theatre with raked red seats and a raised dark stage (the stage deck is built in fixtures()).
                    if(z==5||z==7||z==11){data.setBlock(x,base+1,z,156,(byte)2);data.setBlock(x,base,z,159,(byte)14);}break;
                case 12: // Four stair flights end at floating landings that cannot meet.
                    // Audit: each flight is two steps and a real landing with two blocks of headroom; the north and
                    // south tops face each other across the empty cross at the same height and never join.
                    if(x==4&&z==11)break; // the chest column: no half flight running into the chest
                    if(z==4||z==11)data.setBlock(x,base+1,z,109,(byte)(z==4?2:3));
                    else if(z==5||z==10){put(data,x,base+1,z,Material.SMOOTH_BRICK);data.setBlock(x,base+2,z,109,(byte)(z==5?2:3));}
                    else for(int y=1;y<=2;y++)put(data,x,base+y,z,Material.SMOOTH_BRICK);break;
                case 13: // Black chapel: four pillars, a broken altar, suspended stone ribs (ribs and altar in fixtures()).
                    if((x==5||x==10)&&(z==5||z==10))for(int y=1;y<roof-base;y++)put(data,x,base+y,z,Material.OBSIDIAN);
                    // Audit: dark-oak pews facing the altar at the head of the chapel (164:2 = back to the south).
                    else if(z==6||z==7||z==11&&x!=4)data.setBlock(x,base+1,z,164,(byte)2);break;
                case 14: // Hotel alcoves: L-shaped partitions surrounding low beds/bedside shelves.
                    if(x==7||x==11||z==4)for(int y=1;y<=3;y++)data.setBlock(x,base+y,z,159,(byte)8);
                    break; // Audit: two-block low beds and bedside shelves are placed in fixtures().
                case 15: // Glazed wells reveal a deep void, bounded below and around each shaft.
                    for(int y=base-12;y<base;y++)put(data,x,y,z,Material.AIR);
                    data.setBlock(x,base-13,z,251,(byte)15);put(data,x,base,z,Material.GLASS);break; // black concrete, was coal
                default:break;
            }
        }
    }
    /** Audit: fixed fittings per room outside the quadrant loop (walls, outer aisle, ceiling, floor inlays).
     *  Never on the cross, the landings, the door cells, the label signs or the chest front. */
    private static void fixtures(ChunkData data,int room,int base,int roof){
        switch(room){
            case 0: // Return Foyer: waiting benches against the side walls, a quartz reception counter with a clerk's chair.
                for(int z=3;z<=13;z++)if(z<=5||z>=11){data.setBlock(2,base+1,z,53,(byte)1);data.setBlock(13,base+1,z,53,(byte)0);}
                for(int x=10;x<=12;x++){data.setBlock(x,base+1,4,155,(byte)0);data.setBlock(x,base+2,4,44,(byte)7);}
                data.setBlock(10,base+1,3,155,(byte)0);data.setBlock(10,base+2,3,44,(byte)7);
                data.setBlock(11,base+1,3,53,(byte)3);break;
            case 1: // Copy Office: grey filing cabinets with drawer pulls along the side walls, a water cooler.
                for(int z=3;z<=13;z++)if(z<=5||z>=11)for(int y=1;y<=2;y++){
                    data.setBlock(2,base+y,z,43,(byte)8);data.setBlock(3,base+y,z,77,(byte)1);
                    data.setBlock(13,base+y,z,43,(byte)8);data.setBlock(12,base+y,z,77,(byte)2);
                }
                data.setBlock(13,base+1,2,155,(byte)0);data.setBlock(13,base+2,2,95,(byte)3);break;
            case 3: // Drowned Bath: quartz benches, wash basins on the north wall, a prismarine-tiled dado.
                for(int z=3;z<=13;z++)if(z<=5||z>=11){data.setBlock(2,base+1,z,156,(byte)1);data.setBlock(13,base+1,z,156,(byte)0);}
                for(int x:new int[]{3,4,5,10,11,12})data.setBlock(x,base+1,2,118,(byte)3);
                for(int i=1;i<=14;i++)for(int[] c:new int[][]{{i,1},{i,14},{1,i},{14,i}}){
                    data.setBlock(c[0],base+1,c[1],168,(byte)1);data.setBlock(c[0],base+4,c[1],168,(byte)2);
                }break; // door cells are re-set by the door loop
            case 4: // Service Tunnel: iron-bar conduits, flush floor hatches, switch panels, a drip cauldron.
                for(int i=2;i<=13;i++){
                    if(i<=6||i>=10){data.setBlock(i,base+3,2,101,(byte)0);data.setBlock(i,base+3,13,101,(byte)0);}
                    if(i<=6||i>=11){data.setBlock(2,base+3,i,101,(byte)0);data.setBlock(13,base+3,i,101,(byte)0);}
                }
                data.setBlock(9,base,5,167,(byte)8);data.setBlock(9,base,11,167,(byte)8);
                data.setBlock(8,base+2,7,69,(byte)1);data.setBlock(12,base+2,4,77,(byte)1);data.setBlock(12,base+2,11,77,(byte)1);
                data.setBlock(13,base+1,13,118,(byte)1);break;
            case 5: // Waiting Room: the empty counter the benches face, with a closed service window, pots and a water cooler.
                for(int x:new int[]{3,4,5,6,11,12})data.setBlock(x,base+1,3,5,(byte)0);
                for(int x:new int[]{4,5,12})data.setBlock(x,base+2,3,102,(byte)0);
                data.setBlock(3,base+2,3,140,(byte)0);data.setBlock(11,base+2,3,140,(byte)0);
                data.setBlock(13,base+1,12,118,(byte)3);break;
            case 6: // Pipe Gallery: overhead runs wall to wall, a crossing lower pair, wall pipe banks, valves, drains, a leak.
                for(int i=2;i<=13;i++){
                    if(i!=5&&i!=10){data.setBlock(i,roof-1,5,101,(byte)0);data.setBlock(i,roof-1,10,101,(byte)0);
                        data.setBlock(5,base+4,i,101,(byte)0);data.setBlock(10,base+4,i,101,(byte)0);}
                    // wall ducts in the risers' polished andesite, above head height, clear of the doors and labels
                    if(i<=6||i>=11)data.setBlock(2,base+4,i,1,(byte)6);
                    if(i<=4||i>=11)data.setBlock(13,base+4,i,1,(byte)6);
                }
                data.setBlock(6,base+2,5,69,(byte)1);data.setBlock(9,base+2,5,69,(byte)2);
                data.setBlock(6,base+2,10,69,(byte)1);data.setBlock(9,base+2,10,69,(byte)2);
                data.setBlock(5,base,7,167,(byte)8);data.setBlock(10,base,7,167,(byte)8);
                data.setBlock(11,base+1,5,118,(byte)1);break;
            case 7: // Night Classroom: chalkboard set into the north wall over a chalk ledge; a teacher's desk facing the class.
                for(int x=10;x<=13;x++){for(int y=2;y<=4;y++)data.setBlock(x,base+y,1,159,(byte)15);data.setBlock(x,base+1,2,126,(byte)8);}
                data.setBlock(12,base+1,5,5,(byte)0);data.setBlock(13,base+1,5,5,(byte)0);
                data.setBlock(12,base+1,4,53,(byte)3);data.setBlock(13,base+2,5,140,(byte)0);break;
            case 9: // Green Laboratory: sinks set into the benches, end-rod tube racks, a sample pot (no container tiles).
                data.setBlock(11,base+1,4,118,(byte)3);data.setBlock(11,base+1,11,118,(byte)3);
                data.setBlock(7,base+2,10,198,(byte)1);data.setBlock(10,base+2,10,198,(byte)1);
                data.setBlock(11,base+2,7,140,(byte)0);break;
            case 10: // Inverted Hall: the missing south row of hanging chairs and tables, red rugs inlaid in the ceiling-floor.
                for(int x=4;x<=11;x++){
                    if(x==8||x==9)continue;
                    if(x!=4){data.setBlock(x,roof-1,12,53,(byte)6);if(x%2==0){put(data,x,roof-2,12,Material.FENCE);data.setBlock(x,roof-3,12,126,(byte)8);}}
                    for(int z:new int[]{4,5,6,10,11,12})if(!(x%4==0&&z%4==0))data.setBlock(x,roof,z,35,(byte)14);
                }break;
            case 11: // Empty Theatre: a raised dark-oak stage deck either side of the north door, red curtains in the north wall.
                for(int x=2;x<=13;x++){
                    if(x>=8&&x<=9)continue;
                    for(int z=2;z<=4;z++)data.setBlock(x,base+1,z,126,(byte)5);
                    if(x<=5||x>=11)for(int y=base+2;y<roof;y++)data.setBlock(x,y,1,35,(byte)14);
                }break;
            case 13: // Black Chapel: ribs on the pillar lines, wall to wall; the broken altar at the head of the chapel.
                for(int x=2;x<=13;x++)if(x!=5&&x!=10){put(data,x,roof-1,5,Material.SMOOTH_BRICK);put(data,x,roof-1,10,Material.SMOOTH_BRICK);}
                for(int x:new int[]{4,5,7,10,11})data.setBlock(x,base+1,4,98,(byte)2);
                put(data,6,base+1,4,Material.ENCHANTMENT_TABLE);
                for(int x:new int[]{4,7,11})data.setBlock(x,base+2,4,76,(byte)5);break;
            case 14: // Last Hotel: two-block low beds (pillow at the partition) and bookshelf nightstands.
                for(int[] bed:new int[][]{{5,6,6,6},{10,6,10,5},{5,10,6,10},{10,10,10,11}}){
                    data.setBlock(bed[0],base+1,bed[1],126,(byte)8);data.setBlock(bed[0],base+2,bed[1],171,(byte)14);
                    data.setBlock(bed[2],base+1,bed[3],126,(byte)8);data.setBlock(bed[2],base+2,bed[3],171,(byte)0);
                }
                put(data,6,base+1,5,Material.BOOKSHELF);put(data,6,base+1,11,Material.BOOKSHELF);break;
            case 15: // No Sky Well: chiselled well-heads around each pane; weathered shaft walls below.
                for(int x=3;x<=12;x++)for(int z=3;z<=12;z++){
                    if(well(x,z))continue;
                    boolean edge=false;
                    for(int dx=-1;dx<=1;dx++)for(int dz=-1;dz<=1;dz++)edge|=well(x+dx,z+dz);
                    if(edge)data.setBlock(x,base,z,98,(byte)3);
                    for(int y=base-13;y<base;y++){int h=(x*7+y*13+z*5)&7;if(h<2)data.setBlock(x,y,z,98,(byte)(h==0?2:1));}
                }break;
            default:break;
        }
    }
    private static boolean well(int x,int z){
        return x>=4&&x<=11&&z>=4&&z<=11&&x!=8&&x!=9&&z!=8&&z!=9&&!(x==4&&z==10);
    }
    private static void put(ChunkData data,int x,int y,int z,Material type){data.setBlock(x,y,z,type);}
    @Override public List<BlockPopulator> getDefaultPopulators(World world){
        return Collections.singletonList(new BlockPopulator(){
            @Override public void populate(World target,Random ignored,Chunk chunk){
                if(chunk.getX()<0||chunk.getX()>=SIDE||chunk.getZ()<0||chunk.getZ()>=SIDE)return;
                int room=chunk.getZ()*SIDE+chunk.getX();
                for(int side=0;side<4;side++){
                    BlockState state=chunk.getBlock(LABELS[side][0],floor(room)+2,LABELS[side][1]).getState();
                    if(!(state instanceof Sign))continue;
                    Sign sign=(Sign)state;int next=destination(room,side);
                    sign.setLine(0,"ROOM "+String.format(Locale.ROOT,"%02d",room));sign.setLine(1,roomName(room));
                    sign.setLine(2,"Door -> "+String.format(Locale.ROOT,"%02d",next));sign.setLine(3,"Right-click door");sign.update(true,false);
                }
                ChunkLight.initialize(chunk);
            }
        });
    }
    @Override public Location getFixedSpawnLocation(World world,Random ignored){return foyer(world);}
    @Override public boolean canSpawn(World world,int x,int z){return x>=6&&x<=10&&z>=7&&z<=10;}
}
