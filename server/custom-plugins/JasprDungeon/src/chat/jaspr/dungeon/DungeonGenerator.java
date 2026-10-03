package chat.jaspr.dungeon;

import java.util.Random;
import org.bukkit.*;
import org.bukkit.generator.ChunkGenerator;

/** No world reads/populators and no cross-chunk writes: generation order cannot change rooms. */
public class DungeonGenerator extends ChunkGenerator {
    public final Layout layout;
    public DungeonGenerator(long seed){this(seed,0);}
    public DungeonGenerator(long seed,int realm){layout=new Layout(seed,realm);}

    private static int data(int id,int value){return id|(value<<12);}
    // Wall, floor, trim, timber, glass. Ordinary construction materials, never ores/metal/gem blocks.
    private static final int[][] PALETTES={
        {98,98,data(98,3),data(5,1),data(95,7)},                 // Weeping Cellar
        {216,98,155,data(5,5),95},                              // Ossuary
        {data(168,2),data(98,1),data(168,1),data(5,1),data(95,11)},// Drowned Confessional
        {112,45,215,data(5,5),data(95,1)},                       // Cinder Chapel
        {155,data(159,15),data(98,3),data(5,5),data(95,7)},       // Hollow Choir
        {data(159,14),data(159,12),data(17,3),data(5,3),data(95,12)},// Rotten Nursery
        {data(159,7),data(98,2),data(1,5),data(5,1),data(95,8)}, // Ashen Archive
        {data(159,14),data(179,2),201,data(5,5),data(95,14)},     // Vermilion Court
        {155,data(1,3),174,data(5,2),data(95,3)},                // Frozen Sacristy
        {48,data(3,2),data(17,1),data(5,1),data(95,13)},         // Rootbound Crypt
        {data(1,5),data(98,3),data(159,1),data(5,1),data(95,4)}, // Clockwork Penance
        {data(159,9),data(1,3),data(159,5),data(5,2),data(95,5)},// Plague Infirmary
        {data(1,3),155,data(159,15),data(5,5),data(95,8)},       // Mirror Tribunal
        {155,data(24,2),216,data(5,2),95},                      // Salt Cathedral
        {data(168,2),168,data(168,1),data(5,1),data(95,9)},      // Sunken Reliquary
        {data(5,5),data(98,2),data(162,1),data(5,5),data(95,12)},// Gallows Refectory
        {data(159,11),data(159,15),201,data(5,5),data(95,11)},   // Starless Observatory
        {data(159,13),48,162,data(5,4),data(95,10)},             // Thorn Sanctuary
        {data(159,4),data(24,2),216,data(5,2),data(95,4)},       // Wax Sepulchre
        {215,data(159,14),data(179,1),data(5,5),data(95,14)},    // Sanguine Cloister
        {data(98,2),data(1,3),data(155,1),data(5,1),95},        // Shattered Basilica
        {data(159,5),data(3,2),data(17,3),data(5,3),data(95,2)},// Fungal Hospice
        {data(1,5),data(159,15),data(98,3),data(5,5),data(95,7)},// Iron Inquisition (stone, not iron)
        {data(159,10),data(159,12),201,data(5,5),data(95,10)},  // Velvet Catacomb
        {data(179,2),data(24,2),data(159,1),data(5,4),data(95,1)},// Amber Baptistry
        {data(98,3),data(1,5),data(159,7),data(5,1),data(95,8)},// Silent Belfry
        {data(159,12),48,216,data(5,3),data(95,13)},             // Carrion Conservatory
        {155,data(159,2),data(168,1),data(5,2),data(95,6)},     // Opaline Sepulcher
        {112,data(1,5),data(159,15),data(5,5),data(95,4)},      // Sunless Foundry
        {216,data(159,8),data(155,2),data(5,2),95},             // Pale Menagerie
        {168,data(98,1),data(168,2),data(5,1),data(95,3)},      // Flooded Scriptorium
        {data(159,15),data(98,2),data(159,10),data(5,5),data(95,15)},// Obsidian Vestry (dark masonry)
        {data(24,2),data(159,12),data(159,4),data(5,4),data(95,4)},// Gilded Pauperhouse (no gold)
        {201,data(159,11),155,data(5,2),data(95,2)},             // Astral Chancel
        {data(159,7),data(98,1),data(159,11),data(5,1),data(95,11)},// Mourning Labyrinth
        {data(155,2),data(1,3),216,data(5,2),data(95,0)}        // Last Absolution
    };

    @Override public ChunkData generateChunkData(World world,Random random,int cx,int cz,BiomeGrid biomes){
        ChunkData out=createChunkData(world);
        for(int x=0;x<16;x++)for(int z=0;z<16;z++){
            int wx=cx*16+x,wz=cz*16+z;Layout.Room r=layout.at(wx,wz);biomes.setBiome(x,z,org.bukkit.block.Biome.PLAINS);
            for(int y=62;y<=84;y++){int b=block(r,wx,y,wz);if(b!=0)out.setBlock(x,y,z,b&4095,(byte)(b>>>12));}
        }
        return out;
    }
    /** Packed legacy block ID/data; shared by generation and the worldless exact-geometry audit. */
    public static int block(Layout.Room r,int x,int y,int z){
        if(y<62||y>84)return 0;
        if(y==62||y==84)return 7;
        if(y==63)return 1;
        int[] palette=PALETTES[r.theme];int wall=palette[0],floor=palette[1],trim=palette[2];
        if(r.theme==0){long h=Layout.mix(r.hash^(x*73428767L)^(z*912367L));wall=floor=data(98,(int)Math.floorMod(h,3));}
        if(y==64)return Math.floorMod(x,8)==4&&Math.floorMod(z,8)==4?89:floor;
        if(y>=r.roof())return y==r.roof()?wall:1;
        boolean edge=x<r.x+2||x>=r.x+r.w-2||z<r.z+2||z>=r.z+r.d-2;
        if(edge){
            boolean opening=(Layout.Room.lane(z)&&(x<r.x+2||x>=r.x+r.w-2))||(Layout.Room.lane(x)&&(z<r.z+2||z>=r.z+r.d-2));
            // Bars physically contain mobs. Player transitions are handled by DungeonPlugin.walk.
            if(opening&&y<=68)return r.door(x,z)?101:0;
            if(opening&&y==69)return 89;
            return y==65||y==r.roof()-1?trim:wall;
        }
        // Preserve the interaction coordinates used by Encounters.chest.
        if(x==r.cx()&&z==r.cz()+4&&y==65)return data(54,2);
        if(r.kind==Layout.Kind.REFUGE){
            // Only the original (0,0) refuge owns the fixed return frame installed by Gates.
            if(r.x==0&&r.z==0){
                if(z==8&&x>=8&&x<=11&&y>=65&&y<=69)return x==8||x==11||y==65||y==69?98:0;
                // A half-step on both faces makes the raised portal sill reachable without jumping.
                if((z==7||z==9)&&x>=9&&x<=10&&y==65)return data(44,5);
                if((x==7||x==24)&&z==23){if(y==65)return 47;if(y==66)return data(50,5);}
            }
            return 0;
        }
        return decoration(r,x,y,z,palette);
    }

    /** Each structure occupies one complete 7x7 bay. Lanes never shear a structure into fragments. */
    private static int decoration(Layout.Room r,int x,int y,int z,int[] p){
        int lx=x-r.x,lz=z-r.z;
        int ax=r.x+(lx/32)*32+(lx%32<16?7:25),az=r.z+(lz/32)*32+(lz%32<16?7:25);
        int dx=x-ax,dz=z-az,a=Math.abs(dx),b=Math.abs(dz),h=y-65;
        if(a>3||b>3)return 0;
        // Reject the whole footprint if future parcel/lane changes make a bay unsafe.
        if(ax-3<r.x+2||ax+3>=r.x+r.w-2||az-3<r.z+2||az+3>=r.z+r.d-2
            ||r.clearLane(ax-3,az-3)||r.clearLane(ax-3,az+3)||r.clearLane(ax+3,az-3)||r.clearLane(ax+3,az+3))return 0;
        int wall=p[0],trim=p[2],wood=p[3],glass=p[4];
        switch(r.motif){
            case 0: // Vault: four full-height piers and a connected illuminated roof cap.
                if(a==2&&b==2)return y==r.roof()-1?89:wall;
                if(y==r.roof()-2&&(a==2&&b<=2||b==2&&a<=2))return trim;
                break;
            case 1: // Tomb: paired three-block sarcophagi with intact lids and headstones.
                if(a==2&&b<=1&&h<=1)return h==1?data(44,5):wall;
                if(a==2&&dz==-2&&h<=2)return h==2?216:trim;
                break;
            case 2: // Archive: opposing book stacks joined by an overhead timber lintel.
                if(a==3&&b<=2&&h<=3)return h==3?wood:47;
                if(b==2&&h==3)return wood;
                break;
            case 3: // Cistern: unbroken stone bottom and four complete walls around still water.
                if(a<=2&&b<=2){if(h==0)return trim;if(h==1)return a==2||b==2?wall:9;}
                if(a==2&&b==2&&h==2)return trim;
                break;
            case 4: // Chapel: two complete pews facing a raised lectern.
                if(b==2&&a<=2&&h==0)return data(109,dz<0?2:3);
                if(dx==0&&dz==0&&h==0)return wood;
                if(dx==0&&dz==0&&h==1)return data(44,5);
                break;
            case 5: // Open cage: full plinth/roof/rim with a deliberate two-high front entrance.
                if(a<=2&&b<=2){
                    if(h==0||h==4)return trim;
                    if(h>=1&&h<=3&&(a==2||b==2)&&!(dx==0&&dz==-2&&h<=2))return 101;
                }
                if(dx==0&&dz==-3&&h==0)return data(44,5);
                break;
            case 6: // Nursery: four cradle posts, rails and a web canopy attached to them.
                if(a==2&&b==1&&h<=3)return wood;
                if(b==1&&a<=2&&h==0)return wood;
                if(a<=2&&b<=1&&h==3)return 30;
                break;
            case 7: // Ossuary: stepped bone altar with a supported votive.
                if(a<=2&&b<=2&&h==0)return wall;
                if(a<=1&&b<=1&&h==1)return 216;
                if(a==0&&b==0&&h==2)return data(50,5);
                break;
            case 8: // Aqueduct: paired piers and a sealed elevated trough; dry passage beneath.
                if(a==2&&b==2&&h<=3)return wall;
                if(a<=2&&b<=2&&h==3)return trim;
                if(a<=2&&b<=2&&h==4)return a==2||b==2?wall:9;
                break;
            case 9: // Confessional: three walls, full roof and front entry, no isolated cubicle.
                if(a<=2&&b<=2&&(h==3||h<=2&&(a==2||dz==2)))return wood;
                if(dx==0&&dz==1&&h==0)return data(109,3);
                break;
            case 10: // Processional arch: two broad piers and a freestanding lintel.
                if(a==3&&b<=1&&h<=4)return wall;
                if(a<=3&&b<=1&&h==4)return a==0?89:trim;
                break;
            case 11: // Bell tower: four posts, a crossbeam and an attached stone bell.
                if(a==2&&b==2&&h<=5)return wood;
                if(a<=2&&b<=2&&h==5)return trim;
                if(a==0&&b==0&&h>=3&&h<=4)return h==3?89:101;
                break;
            case 12: // Mirror screens: three glass panels in complete stone frames.
                if((dx==-2||dx==0||dx==2)&&b<=2&&h<=3)return b==2||h==0||h==3?trim:glass;
                break;
            case 13: // Salt font: diamond plinth and smaller diamond crown around a lamp.
                if(a+b<=3&&h==0)return wall;
                if(a+b<=1&&h==1)return trim;
                if(a==0&&b==0&&h==2)return 89;
                break;
            case 14: // Root crypt: branched logs and persistent (non-decaying) leaves.
                if(a==0&&b==0&&h<=4)return data(17,1);
                if(h==3&&(a<=2&&dz==0||b<=2&&dx==0))return data(17,13);
                if(h==4&&a+b<=3)return data(18,5);
                break;
            case 15: // Clock: upright masonry dial and a pendulum below its hub.
                if(dz==0&&a==3&&h<=5)return wall;
                if(dz==0&&a<=3&&h>=3&&h<=5)return a==3||h==3||h==5?trim:glass;
                if(dx==0&&dz==0&&h>=1&&h<=2)return h==1?trim:101;
                break;
            case 16: // Infirmary: two cots, headboards and a glass medicine cabinet.
                if(a==2&&b<=1&&h==0)return data(44,0);
                if(a==2&&dz==2&&h<=1)return wood;
                if(dx==0&&dz==2&&h<=2)return h==1?glass:trim;
                break;
            case 17: // Tribunal: open dais with raised chair and twin side rails.
                if(a<=2&&b<=2&&h==0)return trim;
                if(dx==0&&dz==1&&h==1)return data(109,3);
                if(dx==0&&dz==2&&h<=2)return wall;
                if(a==2&&b<=2&&h==1)return 101;
                if(a<=1&&dz==-3&&h==0)return data(44,5);
                break;
            case 18: // Display: sealed glass vitrine on a base; symbolic bone, no loot block.
                if(a<=1&&b<=1){
                    if(h==0||h==4)return trim;
                    if(h<=3&&(a==1||b==1))return glass;
                    if(a==0&&b==0&&h==1)return 216;
                }
                break;
            case 19: // Gallows: braced timber posts and short chain above a clear floor.
                if(a==2&&dz==0&&h<=5)return wood;
                if(a<=2&&dz==0&&h==5)return wood;
                if(dx==0&&dz==0&&h>=3&&h<=4)return 101;
                if(a==2&&b==1&&h==0)return trim;
                break;
            case 20: // Refectory: continuous low table and two complete bench rows.
                if(b<=2&&dx==0&&h<=1)return wood;
                if(a==2&&b<=2&&h==0)return data(109,dx<0?0:1);
                if(dx==0&&b==2&&h==2)return data(50,5);
                break;
            case 21: // Orrery: pedestal supporting a vertical ring and luminous center.
                if(a<=1&&b<=1&&h==0)return wall;
                if(dx==0&&dz==0&&h<=3)return h==3?89:trim;
                if(dz==0&&a+Math.abs(h-3)>=2&&a+Math.abs(h-3)<=3)return glass;
                break;
            case 22: // Thorn bower: branching posts joined by a permanent leaf canopy.
                if(a==2&&b==2&&h<=3)return data(17,3);
                if(h==3&&(a==2&&b<=2||b==2&&a<=2))return data(17,15);
                if(a<=2&&b<=2&&h==4)return data(18,7);
                break;
            case 23: // Ossuary spiral: five rising, floor-supported plinths and a bone finial.
                if(dx==-2&&b<=2&&h<=dz+2)return trim;
                if(dz==2&&dx>=-1&&dx<=2&&h<=4)return wall;
                if(dx==2&&dz==1&&h<=2)return 216;
                break;
            case 24: // Five wax-like columns at different heights with supported candles.
                if((dx==0&&b<=2&&b!=1)||(dz==0&&a==2)){
                    int top=a+b==0?4:dx<0||dz<0?2:3;
                    if(h<=top)return h==top?data(50,5):trim;
                }
                break;
            case 25: // Two long arcades: four end pillars carry parallel overhead galleries.
                if(a==2&&b<=3&&(b==3&&h<=4||h==4))return h==4?trim:wall;
                break;
            case 26: // Broken buttress: tall rear pier, short attached beam and stepped foot.
                if(dx==-3&&b<=1&&h<=6)return wall;
                if(dx>=-2&&dx<=0&&b<=1&&h==5)return trim;
                if(dx>=-2&&dx<=1&&b<=1&&h<=1-dx)return wall;
                break;
            case 27: // Fungal umbrella: a central stem supports a broad diamond cap.
                if(dx==0&&dz==0&&h<=3)return wood;
                if(h==3&&a<=1&&b<=1||h==4&&a+b<=3)return trim;
                break;
            case 28: // Inquisitor's rack: high beam and low crossbar between two uprights.
                if(dz==0&&a<=2&&(a==2&&h<=5||h==2||h==5))return a==2?wall:wood;
                if(a==2&&b==1&&h==0)return trim;
                break;
            case 29: // Wide canopy with corner columns, high curtains and a complete roof.
                if(a==3&&b==2&&h<=4)return wood;
                if(a<=3&&b<=2&&h==4||a==3&&b<=2&&h==3)return glass;
                break;
            case 30: // Two separate, sealed one-cell baptism wells.
                if(a>=1&&a<=3&&b<=1&&h<=1)return h==1&&a==2&&b==0?9:trim;
                break;
            case 31: // Seven solid organ pipes rise above an attached two-level keyboard.
                if(dz==2&&a<=3&&h<=3+Math.floorMod(dx+3,3))return trim;
                if(dz==1&&a<=2&&h<=1)return wood;
                break;
            case 32: // Three staggered T perches with distinct heights and broad feet.
                if((dx==-2&&dz==-2||dx==0&&dz==0||dx==2&&dz==2)&&h<=3+dx/2)return wood;
                if((dx==-2&&Math.abs(dz+2)<=1&&h==2)||(dx==0&&b<=1&&h==3)||(dx==2&&Math.abs(dz-2)<=1&&h==4))return 216;
                break;
            case 33: // Triptych: three stepped glass panels on a single continuous base.
                if(dz==0&&a<=3&&h==0)return trim;
                if(dz==0&&(a==0||a==2)&&h<=5-a)return glass;
                if(dz==0&&a==1&&h<=2)return trim;
                break;
            case 34: // Dry crucible: full plinth, tall hollow rim and luminous solid contents.
                if(a<=2&&b<=2){
                    if(h==0)return wall;
                    if(h<=3&&(a==2||b==2))return trim;
                    if(h==1)return 89;
                }
                break;
            case 35: // Two open-front pens, joined back rail, and a clear central aisle.
                if(a>=1&&a<=3&&b<=2&&(h==0||h<=2&&(a==1||a==3||dz==2)))return h==0?trim:101;
                break;
            case 36: // Staggered desks: solid legs, writing tops and full bookcase backs.
                if(dx>=-3&&dx<=-1&&dz>=-2&&dz<=-1&&h<=1)return wood;
                if(dx>=1&&dx<=3&&dz>=1&&dz<=2&&h<=1)return wood;
                if(dx>=-3&&dx<=-1&&dz==-2&&h==2||dx>=1&&dx<=3&&dz==2&&h==2)return 47;
                break;
            case 37: // Two broad wardrobes with full backs and shelf-separated glass fronts.
                if(a==2&&b<=2&&h<=4)return b==2||h%2==0?wood:glass;
                if(a==3&&b<=2&&h<=4)return wood;
                break;
            case 38: // A supported three-wide stair rises east onto a full stone landing.
                if(b<=1&&dx>=-3&&dx<=1){
                    int top=dx+3;
                    if(h<top)return wall;
                    if(h==top)return data(109,0);
                }
                if(dx==2&&b<=1&&h<=4)return trim;
                break;
            case 39: // Upright crosswheel: open quadrants surrounding a luminous axle.
                if(dz==0&&(a==3&&h<=6||a<=3&&(h==0||h==3||h==6)||dx==0&&h<=6))return dx==0&&h==3?89:trim;
                break;
            case 40: // Alternating mourning screens leave an S-shaped route around their ends.
                if(dx==-2&&dz>=-3&&dz<=1&&h<=2||dx==2&&dz>=-1&&dz<=3&&h<=2)return wall;
                if(dx==0&&b<=1&&h==0)return data(44,5);
                break;
            case 41: // Crown: broad solid dais and eight rising teeth around a low center.
                if(a<=2&&b<=2&&h==0)return wall;
                if((a==2&&b==2||a==0&&b==2||a==2&&b==0)&&h<=3)return trim;
                if(a==0&&b==0&&h==1)return 89;
                break;
            case 42: // Three connected tunnel ribs with an unobstructed axial passage.
                if((dz==-2||dz==0||dz==2)&&a<=2&&(a==2&&h<=4||h==4))return wall;
                if(a==2&&b<=2&&h==3)return wood;
                break;
            case 43: // Unequal obelisks share a low bridge, all parts rooted in the floor.
                if(dx==-1&&dz==0&&h<=6||dx==1&&dz==0&&h<=4)return trim;
                if(dx==0&&dz==0&&h==1)return wood;
                if(a<=2&&b<=1&&h==0)return wall;
                break;
            case 44: // Single cantilever with a suspended, framed lantern clear of the floor.
                if(dx==-3&&dz==0&&h<=6||dx>=-3&&dx<=1&&dz==0&&h==6)return wood;
                if(dx==1&&dz==0&&h==5)return 101;
                if(dx>=0&&dx<=2&&b<=1&&h>=2&&h<=4)return dx==1&&dz==0&&h==3?89:glass;
                break;
            case 45: // Contained five-cell cross pool with a continuous square surround.
                if(a<=2&&b<=2&&h<=1)return h==1&&a+b<=1?9:trim;
                if(a==2&&b==0&&h==2)return data(50,5);
                break;
            case 46: // Forked roots: two trunks join a central crown and two leaf fans.
                if(a==2&&dz==0&&h<=4)return wood;
                if(b==0&&a<=2&&h==4)return wood;
                if(h==5&&((dx==-2&&b<=2)||(dx==2&&b<=2)))return data(18,5);
                if(dx==0&&dz==0&&h==0)return 216;
                break;
            case 47: // Four facing chairs around a low court, each backed by a tall post.
                if(h==0&&a+b==2&&(a==0||b==0))return data(109,dx<0?0:dx>0?1:dz<0?2:3);
                if(a+b==3&&(a==0||b==0)&&h<=2)return wall;
                if(dx==0&&dz==0&&h==0)return data(44,5);
                break;
            default: throw new IllegalArgumentException("Unknown dungeon motif: "+r.motif);
        }
        return 0;
    }
    @Override public Location getFixedSpawnLocation(World world,Random random){return new Location(world,16.5,65,16.5);}
    @Override public boolean canSpawn(World world,int x,int z){return x==16&&z==16;}
}
