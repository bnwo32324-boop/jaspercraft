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
        ChunkData data=createChunkData(world);
        for(int x=0;x<16;x++)for(int z=0;z<16;z++)biomes.setBiome(x,z,Biome.PLAINS);
        // View-distance fringe chunks stay empty. No loads, populators or unbounded structure growth.
        if(cx<0||cx>=SIDE||cz<0||cz>=SIDE)return data;
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
        return data;
    }
    /** Each room has a different silhouette and furnishings. Preserve a two-block cross and outer aisle. */
    private static void furnish(ChunkData data,int room,int base,int roof){
        for(int x=4;x<=11;x++)for(int z=4;z<=11;z++){
            if(x==8||x==9||z==8||z==9||x==4&&z==10)continue;
            switch(room){
                case 1: // Copy office: desks, chair backs and bulky white copiers.
                    if(z==5||z==10){put(data,x,base+1,z,Material.WOOD);if(x%3==1)put(data,x,base+2,z,Material.QUARTZ_BLOCK);}
                    if(z==6&&x%2==0)data.setBlock(x,base+1,z,53,(byte)3);break;
                case 2: // Archive: tall parallel shelves with open reading aisles.
                    if(x==5||x==7||x==11)for(int y=1;y<=4;y++)put(data,x,base+y,z,Material.BOOKSHELF);break;
                case 3: // Sunken bath: water is BELOW the dry circulation paths, contained by the floor edges.
                    put(data,x,base-1,z,Material.PRISMARINE);put(data,x,base,z,Material.STATIONARY_WATER);
                    if(x==5&&z==5)put(data,x,base+1,z,Material.SEA_LANTERN);break;
                case 4: // Repeated low stone tunnel arches, with a visible high ceiling beyond them.
                    if(z==4||z==7||z==11){
                        if(x==4||x==7||x==11)for(int y=1;y<=3;y++)put(data,x,base+y,z,Material.SMOOTH_BRICK);
                        put(data,x,base+4,z,Material.SMOOTH_BRICK);
                    }break;
                case 5: // Waiting room benches facing empty counters.
                    if(z==5||z==7||z==11)data.setBlock(x,base+1,z,134,(byte)2);
                    if(x==11&&z<=7)put(data,x,base+2,z,Material.WOOD);break;
                case 6: // Pipe gallery: low-value polished-andesite risers and iron-bar cross-pipes.
                    if((x==5||x==10)&&(z==5||z==10))for(int y=1;y<roof-base;y++)data.setBlock(x,base+y,z,1,(byte)6);
                    if(z==5||z==10)put(data,x,roof-1,z,Material.IRON_FENCE);break;
                case 7: // Classroom: individual desks plus a dark chalkboard wall.
                    if(x%2==0&&z%2==0){put(data,x,base+1,z,Material.FENCE);put(data,x,base+2,z,Material.WOOD_PLATE);}
                    if(z==4&&x<=7)for(int y=2;y<=3;y++)data.setBlock(x,base+y,z,159,(byte)15);break;
                case 8: // Depot: irregular stacks, never extra loot-bearing tile entities.
                    if((x+z)%3!=0)for(int y=1;y<=1+(x*3+z)%3;y++)put(data,x,base+y,z,y==1?Material.LOG:Material.WOOD);break;
                case 9: // Glass laboratory tanks around empty stone worktops.
                    if(x==4||x==7||x==10||z==4||z==7||z==11){
                        put(data,x,base+1,z,Material.QUARTZ_BLOCK);
                        for(int y=2;y<=3;y++)data.setBlock(x,base+y,z,95,(byte)5);
                    }break;
                case 10: // Inverted hall: furniture hangs above; the ordinary floor is conspicuously empty.
                    if(z%2==0){data.setBlock(x,roof-1,z,53,(byte)6);if(x%2==0)put(data,x,roof-2,z,Material.FENCE);}break;
                case 11: // Empty theatre with raked red seats and a raised dark stage.
                    if(z==5||z==7||z==11){data.setBlock(x,base+1,z,156,(byte)2);data.setBlock(x,base,z,159,(byte)14);}
                    if(z==4)put(data,x,base+1,z,Material.COAL_BLOCK);break;
                case 12: // Four stair flights end at floating landings that cannot meet.
                    int step=1+(z-4)%4;
                    for(int y=1;y<step;y++)put(data,x,base+y,z,Material.SMOOTH_BRICK);
                    data.setBlock(x,base+step,z,109,(byte)2);break;
                case 13: // Black chapel: four pillars, a broken altar, suspended stone ribs.
                    if((x==5||x==10)&&(z==5||z==10))for(int y=1;y<roof-base;y++)put(data,x,base+y,z,Material.OBSIDIAN);
                    if(z==4||z==11)put(data,x,roof-1,z,Material.SMOOTH_BRICK);
                    if(x==6&&z==6)put(data,x,base+1,z,Material.ENCHANTMENT_TABLE);break;
                case 14: // Hotel alcoves: L-shaped partitions surrounding low beds/bedside shelves.
                    if(x==7||x==11||z==4)for(int y=1;y<=3;y++)data.setBlock(x,base+y,z,159,(byte)8);
                    else if(z==6||z==10)data.setBlock(x,base+1,z,35,(byte)14);break;
                case 15: // Glazed wells reveal a deep void, bounded below and around each shaft.
                    for(int y=base-12;y<base;y++)put(data,x,y,z,Material.AIR);
                    put(data,x,base-13,z,Material.COAL_BLOCK);put(data,x,base,z,Material.GLASS);break;
                default:break;
            }
        }
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
