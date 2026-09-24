package chat.jaspr.biomes;
import org.bukkit.Chunk;
import org.bukkit.craftbukkit.v1_12_R1.CraftChunk;
import net.minecraft.server.v1_12_R1.ChunkSection;
/** Native-valued local sky/block light flood before a generated chunk is sent.
 * This does not need absent neighbor chunks or make anything emissive. */
public final class ChunkLight {
    public static void initialize(Chunk chunk){
        net.minecraft.server.v1_12_R1.Chunk n=((CraftChunk)chunk).getHandle();ChunkSection[] sections=n.getSections();
        int top=sections.length-1;while(top>0&&sections[top]==null)top--;int height=(top+1)*16,size=height*256;
        byte[] light=new byte[size],opacity=new byte[size];int[] queue=new int[size*16];int head=0,tail=0;
        for(int z=0;z<16;z++)for(int x=0;x<16;x++){
            int sky=15;for(int y=height-1;y>=0;y--){int at=(y<<8)|(z<<4)|x,o=n.getBlockData(x,y,z).c();opacity[at]=(byte)Math.min(15,o);if(o==0&&sky<15)o=1;sky=Math.max(0,sky-o);light[at]=(byte)sky;if(sky>1)queue[tail++]=at;}
        }
        while(head<tail){int at=queue[head++],x=at&15,z=(at>>4)&15,y=at>>8,v=light[at];
            if(x>0)tail=spread(at-1,v,opacity,light,queue,tail);if(x<15)tail=spread(at+1,v,opacity,light,queue,tail);
            if(z>0)tail=spread(at-16,v,opacity,light,queue,tail);if(z<15)tail=spread(at+16,v,opacity,light,queue,tail);
            if(y>0)tail=spread(at-256,v,opacity,light,queue,tail);if(y<height-1)tail=spread(at+256,v,opacity,light,queue,tail);
        }
        for(int y=0;y<height;y++){ChunkSection section=sections[y>>4];if(section==null)continue;for(int z=0;z<16;z++)for(int x=0;x<16;x++)section.a(x,y&15,z,light[(y<<8)|(z<<4)|x]);}
        // Custom ChunkData does not seed block emission. Use native emitter values, not full-bright entities.
        java.util.Arrays.fill(light,(byte)0);head=0;tail=0;
        for(int y=0;y<height;y++)for(int z=0;z<16;z++)for(int x=0;x<16;x++){int at=(y<<8)|(z<<4)|x,v=n.getBlockData(x,y,z).d();if(v>0){light[at]=(byte)Math.min(15,v);queue[tail++]=at;}}
        while(head<tail){int at=queue[head++],x=at&15,z=(at>>4)&15,y=at>>8,v=light[at];
            if(x>0)tail=spread(at-1,v,opacity,light,queue,tail);if(x<15)tail=spread(at+1,v,opacity,light,queue,tail);
            if(z>0)tail=spread(at-16,v,opacity,light,queue,tail);if(z<15)tail=spread(at+16,v,opacity,light,queue,tail);
            if(y>0)tail=spread(at-256,v,opacity,light,queue,tail);if(y<height-1)tail=spread(at+256,v,opacity,light,queue,tail);
        }
        for(int y=0;y<height;y++){ChunkSection section=sections[y>>4];if(section==null)continue;for(int z=0;z<16;z++)for(int x=0;x<16;x++)section.b(x,y&15,z,light[(y<<8)|(z<<4)|x]);}
        n.markDirty();
    }
    private static int spread(int at,int source,byte[] opacity,byte[] light,int[] queue,int tail){int value=source-Math.max(1,opacity[at]);if(value>light[at]){light[at]=(byte)value;if(value>1)queue[tail++]=at;}return tail;}
}
