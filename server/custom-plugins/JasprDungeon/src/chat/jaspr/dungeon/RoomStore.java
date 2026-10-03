package chat.jaspr.dungeon;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import org.bukkit.configuration.file.YamlConfiguration;

/** Small independent room journals: memory is bounded by the active-room limit. */
public final class RoomStore {
    private final File directory;
    public RoomStore(File directory){this.directory=directory;}
    /** triggered: a treasure room or shrine whose guardians have woken. claimed: the chest has been filled. */
    public static final class State {public int killed;public boolean cleared,claimed,triggered;}
    public File file(Layout.Room r){return new File(new File(directory,Math.floorDiv(r.x,512)+"_"+Math.floorDiv(r.z,512)),r.id()+".yml");}
    public State load(Layout.Room r) throws Exception {
        State s=new State();File f=file(r);if(!f.exists())return s;
        YamlConfiguration y=new YamlConfiguration();y.load(f);
        int version=y.getInt("version");if(version<1||version>2||!r.id().equals(y.getString("room")))throw new IOException("Invalid room journal: "+r.id());
        s.killed=y.getInt("killed");s.cleared=y.getBoolean("cleared");s.claimed=y.getBoolean("claimed");s.triggered=y.getBoolean("triggered");return s;
    }
    public void save(Layout.Room r,State s) throws IOException {
        YamlConfiguration y=new YamlConfiguration();y.set("version",2);y.set("room",r.id());y.set("killed",s.killed);y.set("cleared",s.cleared);y.set("claimed",s.claimed);y.set("triggered",s.triggered);atomic(file(r),y.saveToString());
    }
    public static void atomic(File file,String contents) throws IOException {
        Files.createDirectories(file.toPath().getParent());Path temp=Files.createTempFile(file.toPath().getParent(),file.getName(),".pending");
        try {Files.write(temp,contents.getBytes(StandardCharsets.UTF_8));try(java.nio.channels.FileChannel c=java.nio.channels.FileChannel.open(temp,StandardOpenOption.WRITE)){c.force(true);}
            try{Files.move(temp,file.toPath(),StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}catch(AtomicMoveNotSupportedException e){Files.move(temp,file.toPath(),StandardCopyOption.REPLACE_EXISTING);}
        }finally{Files.deleteIfExists(temp);}
    }
}
