package chat.jaspr.biomes;
import java.lang.reflect.Field;
import net.minecraft.server.v1_12_R1.BiomeBase;
import org.bukkit.craftbukkit.v1_12_R1.block.CraftBlock;
/** Pinned 1.12.2 metadata; protocol IDs stay intact for browser compatibility. */
public final class BiomeMetadata {
    private static void set(Object target,String name,Object value)throws ReflectiveOperationException{Field f=BiomeBase.class.getDeclaredField(name);f.setAccessible(true);f.set(target,value);}
    public static void apply(){
        try{for(Catalog.Profile p:Catalog.ALL){
            BiomeBase b=CraftBlock.biomeToBiomeBase(p.slot);if(b==null)throw new IllegalStateException("Missing biome "+p.slot);
            boolean snow=p.atmosphere.equals("snow"),dry=p.atmosphere.matches("ash|dust|rust");
            set(b,"y",p.name);set(b,"B",snow?-.5f:dry?1.1f:.6f);set(b,"C",dry?.05f:.7f);set(b,"D",p.water);set(b,"E",snow);set(b,"F",!dry);
            if(b.getTemperature()!=(snow?-.5f:dry?1.1f:.6f))throw new IllegalStateException("Wrong pinned climate field");
        }}catch(ReflectiveOperationException ex){throw new IllegalStateException("Incompatible Paper biome metadata; refusing fallback",ex);}
    }
}
