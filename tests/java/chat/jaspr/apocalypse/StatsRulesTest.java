package chat.jaspr.apocalypse;

import java.util.*;

/** Pure contracts against the shipping rules, not a copy of their implementation. */
public final class StatsRulesTest {
    private static long checks;
    private static void check(boolean ok,String text){checks++;if(!ok)throw new AssertionError(text);}
    private static void rejects(Runnable action,String label){try{action.run();throw new AssertionError("Accepted "+label);}catch(IllegalArgumentException|NullPointerException expected){checks++;}}
    private static void roundTrip(int xp){
        int level=StatRules.levelFor(xp);float bar=StatRules.progressFor(xp);
        check(StatRules.xpAtLevel(level)<=xp && StatRules.xpAtLevel(level+1)>xp,"level brackets "+xp);
        check(bar>=0 && bar<1 && Float.isFinite(bar),"finite bar "+xp);
        check(StatRules.points(level,bar)==xp,"XP never created/lost "+xp+" level="+level+" bar="+bar);
    }
    public static void main(String[] ignored){
        int[] thresholds={0,7,16,27,40,55,72,91,112,135,160,187,216,247,280,315,352,394,441,493,550,612,679,751,828,910,997,1089,1186,1288,1395,1507,1628};
        for(int level=0;level<thresholds.length;level++){
            check(StatRules.xpAtLevel(level)==thresholds[level],"independent native threshold "+level);
            int width=level+1<thresholds.length?thresholds[level+1]-thresholds[level]:130;
            check(StatRules.xpToNext(level)==width,"next-level width "+level);
            for(int i=0;i<width;i++)roundTrip(thresholds[level]+i);
        }
        for(int xp=0;xp<100000;xp++)roundTrip(xp);
        Random random=new Random(0x517a75L);for(int i=0;i<150000;i++)roundTrip(random.nextInt(Integer.MAX_VALUE));
        for(int xp=Integer.MAX_VALUE-10000;xp<Integer.MAX_VALUE;xp++)roundTrip(xp);
        roundTrip(Integer.MAX_VALUE);
        int maxLevel=StatRules.levelFor(Integer.MAX_VALUE);
        for(int level=0;level<=maxLevel;level++){
            long base=StatRules.xpAtLevel(level);
            check(StatRules.xpAtLevel(level+1)-base==StatRules.xpToNext(level),"continuous curve "+level);
            roundTrip((int)base);if(base>0)roundTrip((int)base-1);
        }
        for(int bad:new int[]{-1,Integer.MIN_VALUE,1000001,Integer.MAX_VALUE}){
            rejects(()->StatRules.xpAtLevel(bad),"level "+bad);rejects(()->StatRules.xpToNext(bad),"next level "+bad);
        }
        rejects(()->StatRules.levelFor(-1),"negative points");rejects(()->StatRules.progressFor(-1),"negative progress");
        for(float bad:new float[]{-0.1f,1,2,Float.NaN,Float.POSITIVE_INFINITY,Float.NEGATIVE_INFINITY})rejects(()->StatRules.points(0,bad),"bad bar "+bad);
        rejects(()->StatRules.points(maxLevel+1,0),"INT_MAX overflow");
        check(StatRules.xpAtLevel(1000000)>Integer.MAX_VALUE,"large curve stays long");
        int[] prices={1,2,3,4,5,6,7,8,9,10};UUID life=UUID.fromString("12345678-1234-5678-9abc-123456789abc");
        StatRules.Ranks ranks=StatRules.zero(life);int sum=0;
        check(StatRules.Stat.values().length==6 && StatRules.CAP==10,"six capped stats");
        for(StatRules.Stat stat:StatRules.Stat.values())for(int rank=0;rank<10;rank++){
            check(StatRules.cost(rank)==prices[rank],"escalation "+rank);
            if(rank>0)check(StatRules.cost(rank)==StatRules.cost(rank-1)+1,"one XP step per rank");
            StatRules.Ranks old=ranks;ranks=ranks.upgrade(stat);sum++;
            check(old.rank(stat)==rank && ranks.rank(stat)==rank+1,"immutable upgrade");
            check(ranks.total()==sum && ranks.life.equals(life),"same-life six independent ranks");
            check(StatRules.parse(ranks.tag()).tag().equals(ranks.tag()),"canonical roundtrip");
            int cost=StatRules.cost(rank);for(int xp:new int[]{cost,cost+1,50000,Integer.MAX_VALUE}){
                int remaining=xp-cost;roundTrip(remaining);check((long)StatRules.points(StatRules.levelFor(remaining),StatRules.progressFor(remaining))+cost==xp,"purchase conserves raw XP");
            }
        }
        final StatRules.Ranks capped=ranks;for(StatRules.Stat stat:StatRules.Stat.values())rejects(()->capped.upgrade(stat),"cap "+stat);
        for(int rank:new int[]{-1,10,Integer.MAX_VALUE})rejects(()->StatRules.cost(rank),"price rank "+rank);
        int[] mutable={1,2,3,4,5,6};StatRules.Ranks copied=new StatRules.Ranks(life,mutable);mutable[0]=9;check(copied.rank(StatRules.Stat.VITALITY)==1,"defensive rank copy");
        rejects(()->new StatRules.Ranks(life,new int[5]),"wrong rank count");rejects(()->new StatRules.Ranks(life,new int[]{11,0,0,0,0,0}),"too high rank");
        String prefix=StatRules.PREFIX+life+":";
        for(String suffix:new String[]{"","0,0,0,0,0","0,0,0,0,0,0,0","11,0,0,0,0,0","-1,0,0,0,0,0","01,0,0,0,0,0","+1,0,0,0,0,0"," 1,0,0,0,0,0","0,0,0,0,0,0\n","0:0,0,0,0,0,0","١,0,0,0,0,0"})rejects(()->StatRules.parse(prefix+suffix),"malformed ranks");
        for(String text:new String[]{null,"", "jaspr_stats_v2:"+life+":0,0,0,0,0,0",StatRules.PREFIX+life.toString().toUpperCase(Locale.ROOT)+":0,0,0,0,0,0",StatRules.PREFIX+"1-1-1-1-1:0,0,0,0,0,0"})rejects(()->StatRules.parse(text),"invalid record");
        System.out.println("STATS_RULES_PASS checks="+checks+" maxLevel="+maxLevel+" thresholds=33");
    }
}
