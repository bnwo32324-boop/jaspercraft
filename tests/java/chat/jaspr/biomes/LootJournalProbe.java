package chat.jaspr.biomes;
import java.io.*;
public final class LootJournalProbe {
    private static void check(boolean ok){if(!ok)throw new AssertionError("Loot journal invariant");}
    public static void main(String[] args)throws Exception {
        File root=new File(args[0]);if(!root.isDirectory())throw new IllegalArgumentException("Fixture directory required");
        File normal=new File(root,"normal.journal");try(LootJournal j=new LootJournal(normal)){check(j.claim("world:structure:1"));check(!j.claim("world:structure:1"));check(j.size()==1);for(int i=0;i<200;i++)check(j.claim("world:structure:"+(i+2)));}
        try(LootJournal j=new LootJournal(normal)){check(j.size()==201);check(j.claimed("world:structure:1"));check(!j.claim("world:structure:1"));check(j.claim("world2:same-structure:1"));}
        File torn=new File(root,"torn.journal");try(LootJournal j=new LootJournal(torn)){j.claim("before-crash");}
        try(RandomAccessFile f=new RandomAccessFile(torn,"rw")){f.seek(f.length());f.writeShort(10);f.writeByte(1);}
        boolean rejected=false;try(LootJournal j=new LootJournal(torn)){}catch(IOException e){rejected=true;}check(rejected);
        File corrupt=new File(root,"corrupt.journal");try(LootJournal j=new LootJournal(corrupt)){j.claim("original");}
        try(RandomAccessFile f=new RandomAccessFile(corrupt,"rw")){f.seek(7);f.writeByte(100);}
        rejected=false;try(LootJournal j=new LootJournal(corrupt)){}catch(IOException e){rejected=true;}check(rejected);
        System.out.println("LOOT_JOURNAL_PASS restart=stable duplicateClaims=denied tornRecord=failClosed checksum=verified");
    }
}
