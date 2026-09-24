package chat.jaspr.biomes;

import java.io.*;
import java.nio.channels.FileLock;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.CRC32;

/** Append-only, forced write-ahead ledger. An unreadable ledger never becomes an empty ledger.
 * Single-writer file locking also prevents two runtime instances from claiming the same marker.
 * Keep this file alongside the world's uid.dat when backing up/restoring encounter terrain. */
final class EncounterJournal implements Closeable {
    private static final int MAGIC = 0x4a484531, VERSION = 2, MAX_FRAME = 16384;
    enum State { CLAIMED, ACTIVE, KILLED, RETIRED }

    static final class Record {
        final UUID id, world, parent;
        final String site, theme;
        final int ordinal, slot, tier, x, y, z, chunkX, chunkZ, phase, summons;
        final boolean boss;
        final State state;
        final Set<Long> residency;

        Record(UUID world, String site, int ordinal, int slot, UUID parent, boolean boss,
               String theme, int tier, int x, int y, int z) {
            this(identity(world, site, ordinal, slot), world, site, ordinal, slot, parent, boss,
                 theme, tier, x, y, z, x >> 4, z >> 4, 0, 0, State.CLAIMED,
                 Collections.singleton(chunk(x >> 4,z >> 4)));
        }

        private Record(UUID id, UUID world, String site, int ordinal, int slot, UUID parent,
                       boolean boss, String theme, int tier, int x, int y, int z,
                       int chunkX, int chunkZ, int phase, int summons, State state, Set<Long> residency) {
            this.id=id; this.world=world; this.site=site; this.ordinal=ordinal; this.slot=slot;
            this.parent=parent; this.boss=boss; this.theme=theme; this.tier=tier;
            this.x=x; this.y=y; this.z=z; this.chunkX=chunkX; this.chunkZ=chunkZ;
            this.phase=phase; this.summons=summons; this.state=state;
            this.residency=Collections.unmodifiableSet(new LinkedHashSet<>(residency));
        }

        Record state(State value) { return copy(chunkX, chunkZ, phase, summons, value); }
        Record position(int cx, int cz) { return copy(cx, cz, phase, summons, state); }
        Record progress(int nextPhase, int usedSummons) { return copy(chunkX, chunkZ, nextPhase, usedSummons, state); }
        private Record copy(int cx, int cz, int p, int s, State value) {
            Set<Long> places=new LinkedHashSet<>(residency); places.add(chunk(cx,cz));
            return new Record(id,world,site,ordinal,slot,parent,boss,theme,tier,x,y,z,cx,cz,p,s,value,places);
        }
        boolean terminal() { return state==State.KILLED || state==State.RETIRED; }
    }

    private final RandomAccessFile file;
    private final FileLock lock;
    private final Map<UUID,Record> records = new LinkedHashMap<>();
    private boolean failed;

    EncounterJournal(File path) throws IOException {
        File dir=path.getAbsoluteFile().getParentFile();
        if(!dir.isDirectory() && !dir.mkdirs())throw new IOException("Cannot create encounter data directory");
        file=new RandomAccessFile(path,"rw");
        FileLock acquired=null;
        try {
            acquired=file.getChannel().tryLock();
            if(acquired==null)throw new IOException("Encounter journal already in use");
            lock=acquired;
            if(file.length()==0) { file.writeInt(MAGIC); file.writeInt(VERSION); file.getFD().sync(); }
            read();
        } catch(IOException | RuntimeException ex) {
            if(acquired!=null)acquired.release();
            file.close();
            throw new IOException("Encounter journal unavailable; encounters and vaults stay locked",ex);
        }
    }

    static UUID identity(UUID world, String site, int ordinal, int slot) {
        // Length prefix avoids ambiguous site/ordinal delimiters. Summon slots have their own namespace.
        String name="jaspr-encounter-v1:"+world+":"+site.length()+":"+site+":"+ordinal+":"+slot;
        return UUID.nameUUIDFromBytes(name.getBytes(StandardCharsets.UTF_8));
    }
    static long chunk(int x,int z) { return ((long)x<<32) | (z & 0xffffffffL); }

    synchronized Record get(UUID id) { return records.get(id); }
    synchronized int size() { return records.size(); }
    synchronized boolean healthy() { return !failed; }

    synchronized void put(Record next) throws IOException {
        if(failed)throw new IOException("Encounter journal is latched closed after an I/O failure");
        validate(next,records.get(next.id));
        byte[] bytes=encode(next); CRC32 crc=new CRC32(); crc.update(bytes);
        try {
            file.seek(file.length()); file.writeInt(bytes.length); file.write(bytes); file.writeInt((int)crc.getValue());
            file.getFD().sync(); // Commit BEFORE exposing state, spawning, retiring or unlocking loot.
            records.put(next.id,next);
        } catch(IOException ex) { failed=true; throw ex; }
    }

    private void read() throws IOException {
        file.seek(0);
        if(file.readInt()!=MAGIC || file.readInt()!=VERSION)throw new IOException("Unknown encounter ledger format");
        while(file.getFilePointer()<file.length()) {
            // A torn final write could have been a kill. Fail closed, never silently discard it.
            int length=file.readInt();
            if(length<1 || length>MAX_FRAME || file.length()-file.getFilePointer()<length+4L)
                throw new IOException("Incomplete encounter journal frame");
            byte[] bytes=new byte[length]; file.readFully(bytes); CRC32 crc=new CRC32(); crc.update(bytes);
            if(file.readInt()!=(int)crc.getValue())throw new IOException("Encounter journal checksum mismatch");
            Record r=decode(bytes); validate(r,records.get(r.id)); records.put(r.id,r);
        }
    }

    private static void validate(Record r, Record old) throws IOException {
        if(!r.id.equals(identity(r.world,r.site,r.ordinal,r.slot)) || r.tier<1 || r.tier>5
                || r.phase<0 || r.phase>2 || r.summons<0 || r.summons>4 || r.slot < -1 || r.slot>3
                || (r.slot==-1)!=(r.parent==null) || (r.parent!=null && r.boss)
                || r.residency.isEmpty() || r.residency.size()>256 || !r.residency.contains(chunk(r.x>>4,r.z>>4))
                || !r.residency.contains(chunk(r.chunkX,r.chunkZ)))
            throw new IOException("Invalid encounter identity/progress");
        if(old==null) {
            if(r.state!=State.CLAIMED)throw new IOException("Encounter must be claimed first");
        } else if(!old.world.equals(r.world) || !old.site.equals(r.site) || !old.theme.equals(r.theme)
                || old.ordinal!=r.ordinal || old.slot!=r.slot || !Objects.equals(old.parent,r.parent)
                || old.boss!=r.boss || old.tier!=r.tier || old.x!=r.x || old.y!=r.y || old.z!=r.z
                || r.phase<old.phase || r.summons<old.summons
                || !r.residency.containsAll(old.residency)
                || (old.terminal() && r.state!=old.state)) {
            throw new IOException("Encounter identity or terminal state cannot be reset");
        }
    }

    private static byte[] encode(Record r) throws IOException {
        ByteArrayOutputStream bytes=new ByteArrayOutputStream(); DataOutputStream out=new DataOutputStream(bytes);
        uuid(out,r.id); uuid(out,r.world); out.writeUTF(r.site); out.writeInt(r.ordinal); out.writeInt(r.slot);
        out.writeBoolean(r.parent!=null); if(r.parent!=null)uuid(out,r.parent);
        out.writeBoolean(r.boss); out.writeUTF(r.theme); out.writeInt(r.tier);
        out.writeInt(r.x); out.writeInt(r.y); out.writeInt(r.z); out.writeInt(r.chunkX); out.writeInt(r.chunkZ);
        out.writeInt(r.phase); out.writeInt(r.summons); out.writeUTF(r.state.name());
        out.writeInt(r.residency.size()); for(long place:r.residency)out.writeLong(place); out.flush();
        if(bytes.size()>MAX_FRAME)throw new IOException("Encounter record too large");
        return bytes.toByteArray();
    }

    private static Record decode(byte[] bytes) throws IOException {
        DataInputStream in=new DataInputStream(new ByteArrayInputStream(bytes));
        try {
            UUID id=uuid(in),world=uuid(in); String site=in.readUTF(); int ordinal=in.readInt(),slot=in.readInt();
            UUID parent=in.readBoolean()?uuid(in):null; boolean boss=in.readBoolean(); String theme=in.readUTF();
            int tier=in.readInt(),x=in.readInt(),y=in.readInt(),z=in.readInt(),cx=in.readInt(),cz=in.readInt(),phase=in.readInt(),summons=in.readInt();
            State state=State.valueOf(in.readUTF()); int count=in.readInt();
            if(count<1 || count>256)throw new IOException("Invalid encounter residency");
            Set<Long> places=new LinkedHashSet<>(); for(int i=0;i<count;i++)places.add(in.readLong());
            Record r=new Record(id,world,site,ordinal,slot,parent,boss,theme,tier,x,y,z,cx,cz,phase,summons,state,places);
            if(in.available()!=0)throw new IOException("Trailing encounter frame data");
            return r;
        } catch(IllegalArgumentException ex) { throw new IOException("Invalid encounter state",ex); }
    }
    private static void uuid(DataOutputStream out, UUID id) throws IOException { out.writeLong(id.getMostSignificantBits()); out.writeLong(id.getLeastSignificantBits()); }
    private static UUID uuid(DataInputStream in) throws IOException { return new UUID(in.readLong(),in.readLong()); }
    @Override public synchronized void close() throws IOException { try { lock.release(); } finally { file.close(); } }
}
