package chat.jaspr.biomes;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

/** Authored, versioned room graphs. The resource is required; there is no generic-ruin fallback. */
public final class StructureCatalog {
    public static final int TILE = 12;
    /** The v1 selection pool is immutable; additions never change its order or random selection modulus. */
    public static final int LEGACY_COUNT = 104;
    private static final String LEGACY_SHA256 = "a37a35bbd474b455b7ae88410f28113be8c80bcf0c64a11b42f1f25cd8ef9733";
    static final String EXPANSION_ROOMS = "RVLEIJHYOQXZabcegijklmtvwp";
    public static final class Design {
        public final String id, name, family;
        public final int tier;
        /** Compatibility snapshot. Selection uses a private copy, so callers cannot change worldgen. */
        public final int[] biomes;
        public final boolean exclusive;
        public final String mode;
        public final int columns, rows;
        private final int[] allowed;
        private final char[][] rooms;
        private final boolean expansion;

        private Design(String[] f, boolean expansion) {
            this.expansion=expansion;
            id=f[0]; name=f[1]; family=f[2]; tier=Integer.parseInt(f[3]);
            String[] ids=f[4].split(","); allowed=new int[ids.length];
            for(int i=0;i<ids.length;i++) allowed[i]=Integer.parseInt(ids[i]);
            biomes=allowed.clone(); exclusive=Boolean.parseBoolean(f[5]); mode=f[6];
            String[] lines=f[7].split("/",-1); rows=lines.length;
            int width=0; for(String line:lines) width=Math.max(width,line.length()); columns=width;
            rooms=new char[rows][columns];
            for(int z=0;z<rows;z++) for(int x=0;x<columns;x++)
                rooms[z][x]=x<lines[z].length()?lines[z].charAt(x):'.';
            validate();
        }
        public char room(int x,int z) { return x<0||z<0||x>=columns||z>=rows?'.':rooms[z][x]; }
        public boolean accepts(int biome) { for(int b:allowed) if(b==biome) return true; return false; }
        /** True only for append-only commissions after the original 104 v1 entries. */
        public boolean isExpansion() { return expansion; }
        public int roomCount() { int n=0; for(char[] row:rooms) for(char c:row) if(c!='.') n++; return n; }
        private void validate() {
            if(!id.matches("[a-z0-9_]+")||tier<1||tier>5||columns>20||rows>20||roomCount()<3)
                throw new IllegalArgumentException("Invalid design "+id);
            if(!mode.matches("surface|buried|underwater")||exclusive&&allowed.length!=1)
                throw new IllegalArgumentException("Invalid placement/biomes "+id);
            Set<Integer> unique=new HashSet<>();
            for(int b:allowed) if(b<0||b>=62||!unique.add(b)) throw new IllegalArgumentException("Invalid biome "+id);
            int start=-1; for(int z=0;z<rows;z++) for(int x=0;x<columns;x++) {
                char c=room(x,z);
                if(".hMsSKTDUBFWCGAP=oq23468".indexOf(c)<0&&EXPANSION_ROOMS.indexOf(c)<0)
                    throw new IllegalArgumentException("Unknown room "+c+" in "+id);
                if(c!='.') start=z*columns+x;
            }
            Set<Integer> seen=new HashSet<>(); ArrayDeque<Integer> pending=new ArrayDeque<>();
            seen.add(start); pending.add(start);
            while(!pending.isEmpty()) { int n=pending.remove(); int x=n%columns,z=n/columns;
                for(int[] d:DIRS) { int nx=x+d[0],nz=z+d[1],p=nz*columns+nx;
                    if(room(nx,nz)!='.'&&seen.add(p)) pending.add(p);
                }
            }
            if(seen.size()!=roomCount()) throw new IllegalArgumentException("Disconnected room graph "+id);
        }
    }
    static final int[][] DIRS={{1,0},{-1,0},{0,1},{0,-1}};
    public static final List<Design> ALL=load();
    private static final List<List<Design>> CHOICES=index(0,ALL.size());
    private static final List<List<Design>> LEGACY_CHOICES=index(0,LEGACY_COUNT);
    private static final List<List<Design>> EXPANSION_CHOICES=index(LEGACY_COUNT,ALL.size());
    private StructureCatalog() {}
    public static List<Design> choices(int biome) {
        checkBiome(biome);
        return CHOICES.get(biome);
    }
    /** Frozen v1 pool. Use this for persisted v1 sites and legacy regional selection. */
    public static List<Design> legacyChoices(int biome) {
        checkBiome(biome); return LEGACY_CHOICES.get(biome);
    }
    /** New commissions only, in append order; contains exclusive and shared choices for every biome. */
    public static List<Design> expansionChoices(int biome) {
        checkBiome(biome); return EXPANSION_CHOICES.get(biome);
    }
    private static void checkBiome(int biome) {
        if(biome<0||biome>=62) throw new IllegalArgumentException("Unknown biome "+biome);
    }
    public static Design byId(String id) {
        for(Design d:ALL) if(d.id.equals(id)) return d;
        throw new IllegalArgumentException("Unknown structure "+id);
    }
    static int floors(char c) {
        if(c>='2'&&c<='8') return c-'0';
        if(c=='T') return 7; if(c=='K') return 4; if(c=='M'||c=='S') return 2;
        if(c=='O'||c=='k') return 3;
        if("VLHYZgpt".indexOf(c)>=0) return 2;
        return 1;
    }
    static boolean open(char c) { return c=='='||c=='o'||c=='W'||c=='C'||c=='A'||"aejv".indexOf(c)>=0; }
    private static List<Design> load() {
        InputStream stream=StructureCatalog.class.getResourceAsStream("/structures/catalog-v1.tsv");
        if(stream==null) throw new ExceptionInInitializerError("Missing /structures/catalog-v1.tsv; copy resources/structures into the jar");
        List<Design> all=new ArrayList<>(); Set<String> ids=new HashSet<>(); StringBuilder legacy=new StringBuilder();
        try(BufferedReader in=new BufferedReader(new InputStreamReader(stream,StandardCharsets.UTF_8))) {
            String line; int n=0;
            while((line=in.readLine())!=null) { n++; if(line.trim().isEmpty()||line.startsWith("#")) continue;
                String[] f=line.split("\\|",-1);
                if(f.length!=8) throw new IOException("Expected eight structure fields on line "+n);
                if(!f[5].equals("true")&&!f[5].equals("false")) throw new IOException("Invalid exclusivity on line "+n);
                if(all.size()<LEGACY_COUNT) legacy.append(line).append('\n');
                Design d=new Design(f,all.size()>=LEGACY_COUNT);
                if(!ids.add(d.id)) throw new IOException("Duplicate structure "+d.id); all.add(d);
            }
            byte[] hash=MessageDigest.getInstance("SHA-256").digest(legacy.toString().getBytes(StandardCharsets.UTF_8));
            StringBuilder hex=new StringBuilder(); for(byte value:hash) hex.append(String.format("%02x",value&255));
            if(!LEGACY_SHA256.equals(hex.toString())) throw new IOException("Original 104 structure rows changed or reordered; v1 compatibility requires the frozen catalogue");
        } catch(Exception e) { throw new ExceptionInInitializerError(e); }
        if(all.size()<LEGACY_COUNT+100) throw new ExceptionInInitializerError("At least 100 expansion commissions required after the original 104");
        return Collections.unmodifiableList(all);
    }
    private static List<List<Design>> index(int from,int to) {
        List<List<Design>> result=new ArrayList<>();
        for(int b=0;b<62;b++) { List<Design> list=new ArrayList<>(); boolean special=false,shared=false;
            for(Design d:ALL.subList(from,to)) if(d.accepts(b)) { list.add(d); special|=d.exclusive; shared|=!d.exclusive; }
            if(!special||!shared) throw new ExceptionInInitializerError("Missing exclusive/shared structures for biome "+b);
            result.add(Collections.unmodifiableList(list));
        }
        return Collections.unmodifiableList(result);
    }
}
