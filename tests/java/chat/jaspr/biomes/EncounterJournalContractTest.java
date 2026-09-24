package chat.jaspr.biomes;

import java.io.*;
import java.nio.file.*;
import java.util.*;

/** Standalone tests of the actual write-ahead ledger, with no Bukkit mocks. */
public final class EncounterJournalContractTest {
    private static int assertions;
    private static void check(boolean value,String message) { assertions++; if(!value)throw new AssertionError(message); }
    private interface Attempt { void run() throws Exception; }
    private static void rejects(Attempt work,String message) throws Exception {
        assertions++; try { work.run(); } catch(IOException expected) { return; } throw new AssertionError(message);
    }
    public static void main(String[] args) throws Exception {
        Path directory=Files.createTempDirectory("jaspr-encounter-journal-test-");
        UUID world=UUID.randomUUID();
        EncounterJournal.Record first=new EncounterJournal.Record(world,"v2:site:-14:31",17,-1,null,true,"OSSUARY_REGENT",4,-3,72,32);
        check(first.id.equals(EncounterJournal.identity(world,first.site,17,-1)),"deterministic UUID");
        check(!first.id.equals(EncounterJournal.identity(UUID.randomUUID(),first.site,17,-1)),"world UUID isolates records");
        check(!first.id.equals(EncounterJournal.identity(world,first.site,18,-1)),"ordinal isolates markers");
        check(!first.id.equals(EncounterJournal.identity(world,first.site,17,0)),"summon namespace cannot alias boss");
        File path=directory.resolve("encounters.bin").toFile();
        try(EncounterJournal ledger=new EncounterJournal(path)) {
            rejects(()->ledger.put(first.state(EncounterJournal.State.ACTIVE)),"cannot spawn without claim");
            ledger.put(first);
            rejects(()->{try(EncounterJournal duplicate=new EncounterJournal(path)) { }},"exclusive writer lock");
        }
        try(EncounterJournal ledger=new EncounterJournal(path)) {
            check(ledger.get(first.id).state==EncounterJournal.State.CLAIMED,"crash before spawn preserves claim");
            EncounterJournal.Record active=first.state(EncounterJournal.State.ACTIVE).position(0,2).position(1,2).progress(2,4);
            ledger.put(active);
            check(ledger.get(first.id).residency.contains(EncounterJournal.chunk(-1,2)),"source chunk retained across crash-before-move");
            check(ledger.get(first.id).residency.contains(EncounterJournal.chunk(1,2)),"destination persisted before move");
            rejects(()->ledger.put(first.state(EncounterJournal.State.ACTIVE)),"phase and summon budget cannot reset");
        }
        try(EncounterJournal ledger=new EncounterJournal(path)) {
            EncounterJournal.Record active=ledger.get(first.id);
            check(active.phase==2 && active.summons==4,"restart preserves phase and finite summons");
            check(active.residency.size()==3,"restart preserves all possible resident chunks");
            ledger.put(active.state(EncounterJournal.State.CLAIMED)); // Serialized missing-entity recovery.
            ledger.put(active.state(EncounterJournal.State.ACTIVE));
            ledger.put(active.state(EncounterJournal.State.KILLED));
            rejects(()->ledger.put(active),"killed marker cannot resurrect");
        }
        try(EncounterJournal ledger=new EncounterJournal(path)) {
            check(ledger.size()==1 && ledger.get(first.id).state==EncounterJournal.State.KILLED,"only durable kill unlocks after restart");
            for(int slot=0;slot<4;slot++) {
                EncounterJournal.Record add=new EncounterJournal.Record(world,first.site,first.ordinal,slot,first.id,false,"ASH_SHAMBLER",4,-3,72,32);
                ledger.put(add); ledger.put(add.state(EncounterJournal.State.RETIRED));
                rejects(()->ledger.put(add),"retired summons cannot regenerate");
            }
            check(ledger.size()==5,"four finite distinct summon slots");
        }
        File truncated=directory.resolve("truncated.bin").toFile(); Files.copy(path.toPath(),truncated.toPath());
        try(RandomAccessFile file=new RandomAccessFile(truncated,"rw")) { file.setLength(file.length()-3); }
        long tornLength=truncated.length();
        rejects(()->{try(EncounterJournal ledger=new EncounterJournal(truncated)) { }},"torn kill/retire frame must fail closed");
        check(truncated.length()==tornLength,"failure cannot erase evidence or reset ledger");
        File corrupted=directory.resolve("corrupted.bin").toFile(); Files.copy(path.toPath(),corrupted.toPath());
        try(RandomAccessFile file=new RandomAccessFile(corrupted,"rw")) { file.seek(25); int b=file.read(); file.seek(25); file.write(b^0x20); }
        rejects(()->{try(EncounterJournal ledger=new EncounterJournal(corrupted)) { }},"checksum rejects changed record data");
        System.out.println("ENCOUNTER_JOURNAL_PASS assertions="+assertions+" fixture="+directory);
    }
}
