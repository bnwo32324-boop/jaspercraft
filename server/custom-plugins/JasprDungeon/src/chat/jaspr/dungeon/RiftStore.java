package chat.jaspr.dungeon;

import java.io.File;
import java.io.IOException;
import java.util.*;
import org.bukkit.configuration.file.YamlConfiguration;

/** Strict identity and per-player journals; no Bukkit world access, usable by pure audits. */
public final class RiftStore {
    private final File directory;
    public final String root;
    public final long rootSeed;
    public final int generation;
    public RiftStore(File directory,String root,long rootSeed,int generation) throws Exception {
        if(root==null||!root.matches("[a-z0-9_-]+"))throw new IOException("Unsafe rift root name");
        this.directory=directory;this.root=root;this.rootSeed=rootSeed;this.generation=generation;
        File file=manifestFile();
        if(file.exists()){YamlConfiguration y=new YamlConfiguration();y.load(file);validateManifest(y);}
        else RoomStore.atomic(file,manifest().saveToString());
    }
    public File manifestFile(){return new File(directory,"manifest.yml");}
    public File playerFile(UUID id){return new File(new File(directory,"players"),id+".yml");}
    private YamlConfiguration identity(){
        YamlConfiguration y=new YamlConfiguration();y.set("version",RiftCatalog.VERSION);y.set("generation",generation);
        y.set("root",root);y.set("seed",rootSeed);return y;
    }
    public YamlConfiguration manifest(){
        YamlConfiguration y=identity();
        for(int realm=1;realm<=RiftCatalog.COUNT;realm++){
            String p="realms."+realm+".";y.set(p+"world",RiftCatalog.worldName(root,realm));y.set(p+"seed",RiftCatalog.seed(rootSeed,realm));
        }
        return y;
    }
    private void validateIdentity(YamlConfiguration y) throws IOException {
        require(integer(y.get("version"))==RiftCatalog.VERSION,"Rift schema mismatch");
        require(integer(y.get("generation"))==generation,"Rift generation mismatch");
        require(root.equals(y.get("root")),"Rift root mismatch");
        require(integer(y.get("seed"))==rootSeed,"Rift root seed mismatch");
    }
    public void validateManifest(YamlConfiguration y) throws IOException {
        validateIdentity(y);
        require(y.isConfigurationSection("realms")&&y.getConfigurationSection("realms").getKeys(false).size()==RiftCatalog.COUNT,"Expected exactly three realm identities");
        for(int realm=1;realm<=RiftCatalog.COUNT;realm++){
            String p="realms."+realm+".";
            require(RiftCatalog.worldName(root,realm).equals(y.get(p+"world")),"Rift world identity mismatch");
            require(integer(y.get(p+"seed"))==RiftCatalog.seed(rootSeed,realm),"Rift seed mismatch");
        }
    }
    public static final class Parent {
        public final int target,roomX,roomZ;
        public final String world;
        public final UUID uid;
        public final long seed;
        public final double x,y,z,yaw,pitch;
        public Parent(int target,String world,UUID uid,long seed,int roomX,int roomZ,double x,double y,double z,double yaw,double pitch){
            this.target=target;this.world=world;this.uid=uid;this.seed=seed;this.roomX=roomX;this.roomZ=roomZ;
            this.x=x;this.y=y;this.z=z;this.yaw=yaw;this.pitch=pitch;
        }
    }
    public static final class Journal {
        public final Map<Integer,Parent> parents=new TreeMap<>();
        public long cooldownUntil;
    }
    public Journal read(UUID id) throws Exception {
        File f=playerFile(id);if(!f.exists())return new Journal();
        YamlConfiguration y=new YamlConfiguration();y.load(f);return decode(id,y);
    }
    public Journal decode(UUID id,YamlConfiguration y) throws IOException {
        validateIdentity(y);require(id.toString().equals(y.get("player")),"Player journal identity mismatch");
        Journal journal=new Journal();journal.cooldownUntil=integer(y.get("cooldown"));
        require(journal.cooldownUntil>=0,"Invalid rift cooldown");
        Object raw=y.get("parents");require(raw instanceof List,"Missing parent list");
        List<?> list=(List<?>)raw;require(list.size()<=RiftCatalog.COUNT,"Too many rift parents");
        for(Object entry:list){
            require(entry instanceof Map,"Invalid rift parent");Map<?,?> m=(Map<?,?>)entry;
            long target=integer(m.get("target"));require(target>=1&&target<=RiftCatalog.COUNT,"Invalid target realm");int realm=(int)target;
            String name=RiftCatalog.worldName(root,realm-1);require(name.equals(m.get("world")),"Rift parent is not the preceding realm");
            long seed=integer(m.get("seed"));require(seed==(realm==1?rootSeed:RiftCatalog.seed(rootSeed,realm-1)),"Rift parent seed mismatch");
            UUID uid;try{uid=UUID.fromString((String)m.get("uuid"));}catch(Exception ex){throw new IOException("Invalid parent world UUID",ex);}
            long roomX=integer(m.get("roomX")),roomZ=integer(m.get("roomZ"));
            require(roomX>=-30000000&&roomX<=30000000&&roomZ>=-30000000&&roomZ<=30000000&&roomX%32==0&&roomZ%32==0,"Invalid parent room");
            double x=number(m.get("x")),yy=number(m.get("y")),z=number(m.get("z")),yaw=number(m.get("yaw")),pitch=number(m.get("pitch"));
            require(Math.abs(x)<29999000&&Math.abs(z)<29999000&&yy>=2&&yy<253&&Math.abs(yaw)<=360000&&Math.abs(pitch)<=90,"Invalid saved landing");
            require(journal.parents.put(realm,new Parent(realm,name,uid,seed,(int)roomX,(int)roomZ,x,yy,z,yaw,pitch))==null,"Duplicate parent realm");
        }
        return journal;
    }
    public YamlConfiguration encode(UUID id,Journal journal) throws IOException {
        YamlConfiguration y=identity();y.set("player",id.toString());y.set("cooldown",journal.cooldownUntil);
        List<Map<String,Object>> list=new ArrayList<>();
        for(Parent p:journal.parents.values()){
            Map<String,Object> m=new LinkedHashMap<>();m.put("target",p.target);m.put("world",p.world);m.put("uuid",p.uid.toString());m.put("seed",p.seed);
            m.put("roomX",p.roomX);m.put("roomZ",p.roomZ);m.put("x",p.x);m.put("y",p.y);m.put("z",p.z);m.put("yaw",p.yaw);m.put("pitch",p.pitch);list.add(m);
        }
        y.set("parents",list);decode(id,y);return y;
    }
    public void write(UUID id,Journal journal) throws IOException {RoomStore.atomic(playerFile(id),encode(id,journal).saveToString());}
    public static long integer(Object value) throws IOException {
        if(!(value instanceof Byte||value instanceof Short||value instanceof Integer||value instanceof Long))throw new IOException("Missing or non-integral rift identity value");
        return ((Number)value).longValue();
    }
    private static double number(Object value) throws IOException {
        require(value instanceof Number,"Missing or nonnumeric landing");double n=((Number)value).doubleValue();require(Double.isFinite(n),"Nonfinite landing");return n;
    }
    private static void require(boolean condition,String message) throws IOException {if(!condition)throw new IOException(message);}
}
