package chat.jaspr.biomes;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.bukkit.block.Biome;

/** One explicit replacement for every protocol-340 biome carrier. */
public final class Catalog {
    public static final class Profile {
        public final int index, height, relief, surface, surfaceData, under, underData, density, water;
        public final Biome slot;
        public final String name, inspiration, tree, landmark, atmosphere;
        Profile(int i, String[] f) {
            index=i; slot=Biome.valueOf(f[0]); name=f[1]; inspiration=f[2];
            height=Integer.parseInt(f[3]); relief=Integer.parseInt(f[4]);
            String[] s=f[5].split(":"), u=f[6].split(":");
            surface=Integer.parseInt(s[0]);surfaceData=Integer.parseInt(s[1]);
            under=Integer.parseInt(u[0]);underData=Integer.parseInt(u[1]);
            tree=f[7];density=Integer.parseInt(f[8]);landmark=f[9];atmosphere=f[10];water=Integer.parseInt(f[11],16);
        }
    }
    public static final List<Profile> ALL=load();
    private static List<Profile> load() {
        List<Profile> result=new ArrayList<>(); Set<Biome> slots=EnumSet.noneOf(Biome.class);Set<String> names=new HashSet<>();
        try (BufferedReader in=new BufferedReader(new InputStreamReader(Catalog.class.getResourceAsStream("/biomes.tsv"),StandardCharsets.UTF_8))) {
            String line; while((line=in.readLine())!=null) {
                if(line.trim().isEmpty()||line.startsWith("#"))continue;
                String[] f=line.split("\\|",-1);if(f.length!=12)throw new IOException("Expected twelve biome fields");
                Profile p=new Profile(result.size(),f);
                if(!slots.add(p.slot)||!names.add(p.name)||p.height<45||p.height>110||p.relief>40)throw new IOException("Invalid/duplicate biome "+p.name);
                result.add(p);
            }
        } catch(Exception e){throw new ExceptionInInitializerError(e);}
        if(result.size()!=62||slots.size()!=Biome.values().length)throw new ExceptionInInitializerError("Incomplete biome replacement: "+result.size());
        return Collections.unmodifiableList(result);
    }
    public static Profile bySlot(Biome slot){for(Profile p:ALL)if(p.slot==slot)return p;throw new IllegalArgumentException(slot.name());}
}
