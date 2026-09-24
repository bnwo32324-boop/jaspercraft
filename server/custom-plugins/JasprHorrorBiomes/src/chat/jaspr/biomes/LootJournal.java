package chat.jaspr.biomes;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.CRC32;

/** Write-ahead, fail-closed claims. A killed process cannot turn a claimed chest into fresh loot. */
public final class LootJournal implements Closeable {
    private static final int MAGIC=0x4a534c32, LIMIT=400000;
    private final Set<String> claims=new HashSet<>();
    private final RandomAccessFile file;
    private final java.nio.channels.FileLock lock;
    public LootJournal(File path) throws IOException {
        File parent=path.getParentFile();if(!parent.isDirectory()&&!parent.mkdirs())throw new IOException("Cannot create loot journal directory");
        file=new RandomAccessFile(path,"rw");
        java.nio.channels.FileLock acquired=null;
        try {
            acquired=file.getChannel().tryLock();if(acquired==null)throw new IOException("Loot journal already in use");lock=acquired;
            if(file.length()==0){file.writeInt(MAGIC);file.getFD().sync();}
            file.seek(0);if(file.readInt()!=MAGIC)throw new IOException("Wrong loot journal version");
            while(file.getFilePointer()<file.length()){
                int length=file.readUnsignedShort();if(length<1||length>512)throw new IOException("Invalid loot record length");
                byte[] bytes=new byte[length];file.readFully(bytes);int expected=file.readInt();CRC32 crc=new CRC32();crc.update(bytes);
                if((int)crc.getValue()!=expected)throw new IOException("Loot journal checksum mismatch");
                claims.add(new String(bytes,StandardCharsets.UTF_8));if(claims.size()>LIMIT)throw new IOException("Loot journal capacity exceeded");
            }
        } catch(IOException|RuntimeException e){if(acquired!=null)acquired.release();file.close();throw new IOException("Loot journal unavailable",e);}
    }
    public boolean claimed(String key){return claims.contains(key);}
    public int size(){return claims.size();}
    public synchronized boolean claim(String key)throws IOException {
        if(claims.contains(key))return false;
        byte[] bytes=key.getBytes(StandardCharsets.UTF_8);if(bytes.length<1||bytes.length>512||claims.size()>=LIMIT)throw new IOException("Loot journal capacity exceeded");
        CRC32 crc=new CRC32();crc.update(bytes);file.seek(file.length());file.writeShort(bytes.length);file.write(bytes);file.writeInt((int)crc.getValue());file.getFD().sync();
        claims.add(key);return true;
    }
    public void close()throws IOException{lock.release();file.close();}
}
