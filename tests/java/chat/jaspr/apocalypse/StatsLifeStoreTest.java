package chat.jaspr.apocalypse;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

public final class StatsLifeStoreTest {
    private static int checks;private interface IO{void run()throws Exception;}
    private static void check(boolean ok,String label){checks++;if(!ok)throw new AssertionError(label);}
    private static void rejects(IO action,String label)throws Exception{try{action.run();throw new AssertionError("Accepted "+label);}catch(IOException expected){checks++;}}
    public static void main(String[] args)throws Exception{
        Path dir=Files.createTempDirectory(Paths.get(args[0]),"life-");StatLifeStore store=new StatLifeStore(dir.toFile());
        UUID player=UUID.randomUUID(),life=UUID.randomUUID();Path record=dir.resolve(player+".life");
        check(store.read(player)==null,"missing file is new life");store.write(player,life);
        check(store.read(player).equals(life),"first durable read");check(new StatLifeStore(dir.toFile()).read(player).equals(life),"independent restart read");
        for(int i=0;i<100;i++){UUID next=UUID.randomUUID();store.write(player,next);check(store.read(player).equals(next),"death supersedes earlier life");}
        try(java.util.stream.Stream<Path> files=Files.list(dir)){check(files.count()==1,"no leftover temporary checkpoints");}
        for(String bad:new String[]{"",life.toString(),"JASPR_STATS_LIFE_V2\n"+life+"\n","JASPR_STATS_LIFE_V1\n"+life,"JASPR_STATS_LIFE_V1\n"+life+"\n\n","JASPR_STATS_LIFE_V1\n1-1-1-1-1\n","JASPR_STATS_LIFE_V1\n"+life.toString().toUpperCase(Locale.ROOT)+"\n","JASPR_STATS_LIFE_V1\n"+life+" extra\n"}){
            Files.write(record,bad.getBytes(StandardCharsets.UTF_8));rejects(()->store.read(player),"malformed checkpoint");
        }
        Files.write(record,new byte[129]);rejects(()->store.read(player),"oversized checkpoint");
        Files.delete(record);Files.createDirectory(record);Files.write(record.resolve("occupant"),new byte[]{1});
        rejects(()->store.write(player,life),"blocked target fails closed");
        check(Files.readAllBytes(record.resolve("occupant"))[0]==1,"failure preserves unrelated target content");
        try(java.util.stream.Stream<Path> files=Files.list(dir)){check(files.count()==1,"failed write cleans temporary file");}
        Path notDir=dir.resolve("blocked");Files.write(notDir,new byte[]{1});rejects(()->new StatLifeStore(notDir.toFile()),"storage not a directory");
        String symlink="not supported";Path outside=Files.createTempDirectory(Paths.get(args[0]),"outside-");Path link=dir.resolve("linked");
        try{Files.createSymbolicLink(link,outside);symlink="tested";rejects(()->new StatLifeStore(link.toFile()),"linked directory");Path child=dir.resolve(UUID.randomUUID()+".life");Files.createSymbolicLink(child,outside.resolve("victim"));UUID id=UUID.fromString(child.getFileName().toString().replace(".life",""));rejects(()->store.write(id,life),"linked record");rejects(()->store.read(id),"linked record read");check(!Files.exists(outside.resolve("victim")),"no writes through symlink");}
        catch(UnsupportedOperationException|FileSystemException error){symlink="SKIP "+error.getClass().getSimpleName();}
        System.out.println("STATS_LIFE_PASS checks="+checks+" symlink="+symlink);
    }
}
