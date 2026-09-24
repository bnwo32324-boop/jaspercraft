package chat.jaspr.biomes;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
/** Compiles/runs separately against the previously deployed plugin and the candidate. */
public final class LegacyPlanFingerprint {
    public static void main(String[] args)throws Exception {
        boolean rows=args.length>0&&args[0].equals("--rows");
        MessageDigest hash=MessageDigest.getInstance("SHA-256");int count=0;
        for(long seed:new long[]{4425965048829651136L,12345L,-1L})for(int x=-9;x<=9;x++)for(int z=-9;z<=9;z++) {
            StructurePlanner.Site site=StructurePlanner.region(seed,x,z);if(site==null)continue;count++;
            StringBuilder out=new StringBuilder(site.key).append('/').append(site.x).append('/').append(site.y).append('/').append(site.z).append('/').append(site.width).append('/').append(site.depth);
            for(StructurePlanner.Marker marker:site.markers())out.append('|').append(marker.kind).append('/').append(marker.ordinal).append('/').append(marker.x).append('/').append(marker.y).append('/').append(marker.z);
            if(rows)System.out.println("SITE "+out);
            hash.update(out.toString().getBytes(StandardCharsets.UTF_8));
        }
        StringBuilder hex=new StringBuilder();for(byte b:hash.digest())hex.append(String.format("%02x",b&255));
        System.out.println("LEGACY_PLANS count="+count+" sha256="+hex);
    }
}
