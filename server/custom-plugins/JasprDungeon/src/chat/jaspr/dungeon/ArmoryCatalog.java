package chat.jaspr.dungeon;

import java.util.*;

/** Pure definitions/math. No Bukkit, client registry, or dependency on another gun plugin. */
public final class ArmoryCatalog {
    private ArmoryCatalog() {}
    public static final String NBT_KEY="JasprPenitentArmory";
    public static final int VERSION=1, MAX_RAYS=6, MAX_TARGETS=4;
    public enum Kind { MELEE, MAGIC, GUN }
    public enum Mechanic {
        SIPHON, ENFEEBLE, CLEAVE, REND, STAGGER, EXECUTE, THRUST, WARD,
        CHAIN, PRISM, BRAND, ROOT, PRECISE, BURST, SCATTER, RAIL,
        IMPACT, VENOM, CHILL, DRILL, RICOCHET, CROSS, DOUBLET, ABSOLVE
    }
    public enum Type {
        WAX_SCALPEL("Wax Scalpel","IRON_SWORD",Kind.MELEE,Mechanic.SIPHON,18,1,5,3,1,1,1,0,0,650,0,1800,"An accepted strike restores one health; 1.8s mercy."),
        CLOISTER_FLAIL("Cloister Flail","IRON_AXE",Kind.MELEE,Mechanic.ENFEEBLE,19,2,6,3,1,1,1,0,0,900,0,1500,"Accepted blows weaken a hostile for three seconds."),
        BASILICA_CLEAVER("Basilica Cleaver","DIAMOND_AXE",Kind.MELEE,Mechanic.CLEAVE,20,3,7,3,1,1,1,0,0,1000,0,1800,"Cleave up to two visible hostiles within 2.6 blocks."),
        HOSPICE_LANCET("Hospice Lancet","STONE_SWORD",Kind.MELEE,Mechanic.REND,21,2,4,3,1,1,1,0,0,700,0,2000,"Three attributed wound pulses, one health each."),
        INQUISITOR_MAUL("Inquisitor's Maul","IRON_AXE",Kind.MELEE,Mechanic.STAGGER,22,3,8,3,1,1,1,0,0,1200,0,1800,"Slow II for two seconds; heavy, deliberate strikes."),
        VELVET_RAPIER("Velvet Rapier","GOLD_SWORD",Kind.MELEE,Mechanic.EXECUTE,23,3,5,3,1,1,1,0,0,650,0,1300,"Adds four damage below 30% target health."),
        AMBER_GLAIVE("Amber Glaive","DIAMOND_SWORD",Kind.MELEE,Mechanic.THRUST,24,4,7,3,1,1,1,0,0,1000,0,1800,"Thrust four damage into one aligned foe behind the first."),
        SILENT_MALLET("Silent Mallet","STONE_AXE",Kind.MELEE,Mechanic.WARD,25,2,6,3,1,1,1,0,0,950,0,6000,"Accepted blows grant a brief two-heart absorption ward."),
        CARRION_CENSER("Carrion Censer","BLAZE_ROD",Kind.MAGIC,Mechanic.CHAIN,26,3,5,18,1,1,1,0,0,1400,0,0,"A bolt arcs to two visible nearby hostiles at half strength."),
        OPAL_PRISM("Opal Prism","PRISMARINE_SHARD",Kind.MAGIC,Mechanic.PRISM,27,4,3.5,22,3,1,2,7,0,1400,0,0,"Three diverging rays each pierce two hostiles."),
        SUNLESS_BRAND("Sunless Brand","BLAZE_ROD",Kind.MAGIC,Mechanic.BRAND,28,3,5,20,1,1,1,0,0,1700,0,0,"Brands one foe with three safe, attributed two-damage pulses."),
        PALE_LANTERN("Pale Lantern","GHAST_TEAR",Kind.MAGIC,Mechanic.ROOT,29,2,4,18,1,1,1,0,0,1200,0,0,"A precise ray binds its victim with Slow III for 1.5s."),
        BAPTIST_NEEDLER("Baptist Needler","IRON_BARDING",Kind.GUN,Mechanic.PRECISE,24,1,6,30,1,1,1,0,8,550,1400,0,"An accurate single round with no random spread."),
        BELFRY_REPEATER("Belfry Repeater","IRON_BARDING",Kind.GUN,Mechanic.BURST,25,2,4.5,26,1,3,1,0,12,1400,1800,0,"Three rounds 11 ticks apart; hold this gun through the burst."),
        CARRION_SCATTERGUN("Carrion Scattergun","IRON_BARDING",Kind.GUN,Mechanic.SCATTER,26,2,2,15,6,1,1,12,4,1100,1800,0,"Six aimed pellets; overlapping pellets combine into one hit."),
        OPALINE_RAIL("Opaline Rail","DIAMOND_BARDING",Kind.GUN,Mechanic.RAIL,27,4,9,36,1,1,4,0,3,1800,2400,0,"Pierces four aligned foes; each penetration loses 22%."),
        FOUNDRY_CANNON("Foundry Cannon","GOLD_BARDING",Kind.GUN,Mechanic.IMPACT,28,3,11,25,1,1,1,0,3,1300,2200,0,"Heavy slug with a small, room-bounded shove."),
        MENAGERIE_DARTER("Menagerie Darter","IRON_BARDING",Kind.GUN,Mechanic.VENOM,29,2,3,24,1,1,1,0,6,800,1600,0,"Venom delivers three attributed one-damage pulses."),
        SCRIPTORIUM_SIPHON("Scriptorium Siphon","IRON_BARDING",Kind.GUN,Mechanic.CHILL,30,2,5,24,1,1,1,0,7,650,1700,0,"Ink rounds slow for three seconds; no liquid is placed."),
        VESPER_DRILL("Vesper Drill","GOLD_BARDING",Kind.GUN,Mechanic.DRILL,31,3,7,28,1,1,2,0,5,950,1900,0,"Two-target drilling round also weakens accepted victims."),
        PAUPERS_RICOCHET("Pauper's Ricochet","IRON_BARDING",Kind.GUN,Mechanic.RICOCHET,32,3,6,24,1,1,1,0,6,1000,1800,0,"One visible secondary foe within four blocks takes half damage."),
        ASTRAL_ORRERY("Astral Orrery","DIAMOND_BARDING",Kind.GUN,Mechanic.CROSS,33,4,2.5,24,5,1,1,6,5,1300,2100,0,"Five rays form a cross: center, left, right, above, below."),
        LABYRINTH_DOUBLE("Labyrinth Double","GOLD_BARDING",Kind.GUN,Mechanic.DOUBLET,34,3,6,28,1,2,1,0,8,1000,1800,0,"Two accurate rounds 11 ticks apart; two rounds per trigger."),
        LAST_ABSOLUTION("Last Absolution","DIAMOND_BARDING",Kind.GUN,Mechanic.ABSOLVE,35,5,9,32,1,1,1,0,4,1500,2400,0,"Adds five damage below 35% target health; deepest boss loot.");

        public final String title,material,description;
        public final Kind kind;
        public final Mechanic mechanic;
        public final int theme,rank,rays,burst,pierce,magazine;
        public final double damage,range,spreadDegrees;
        public final long cooldownMillis,reloadMillis,procMillis;
        Type(String title,String material,Kind kind,Mechanic mechanic,int theme,int rank,double damage,double range,
             int rays,int burst,int pierce,double spread,int magazine,long cooldown,long reload,long proc,String description){
            this.title=title;this.material=material;this.kind=kind;this.mechanic=mechanic;this.theme=theme;this.rank=rank;
            this.damage=damage;this.range=range;this.rays=rays;this.burst=burst;this.pierce=pierce;spreadDegrees=spread;
            this.magazine=magazine;cooldownMillis=cooldown;reloadMillis=reload;procMillis=proc;this.description=description;
        }
        public boolean gun(){return kind==Kind.GUN;}
        public boolean ranged(){return kind!=Kind.MELEE;}
        public int triggerCost(){return gun()?burst:0;}
        public String id(){return name().toLowerCase(Locale.ROOT);}
    }
    public static List<Type> all(){return Collections.unmodifiableList(Arrays.asList(Type.values()));}
    public static boolean eligible(Type t,int tier,String kind){
        if("REFUGE".equals(kind)||tier<1||tier>5)return false;
        return t.rank<=("BOSS".equals(kind)?Math.min(5,tier+1):Math.min(3,tier))
            &&(t.rank<4||"BOSS".equals(kind))&&(t.rank<5||tier>=4);
    }
    public static int weight(Type t,int theme){return t.theme==theme?6:t.theme%6==Math.floorMod(theme,6)?2:1;}
    public static Type roll(long hash,int theme,int tier,String kind){
        if("REFUGE".equals(kind)||tier<1||tier>5)return null;
        int chance="BOSS".equals(kind)?100:"BATTLE".equals(kind)||"GAUNTLET".equals(kind)?12+tier*4:8;
        long n=Layout.mix(hash^0x41524d4f52594cL);
        if(Math.floorMod(n,100)>=chance)return null;
        int total=0;for(Type t:Type.values())if(eligible(t,tier,kind))total+=weight(t,theme);
        if(total==0)return null;
        int pick=(int)Math.floorMod(Layout.mix(n^0x5348454c4cL),total);
        for(Type t:Type.values())if(eligible(t,tier,kind)){pick-=weight(t,theme);if(pick<0)return t;}
        throw new AssertionError("Armory pool");
    }
    /** Horizontal and vertical angular offsets; no accuracy lottery for center rays. */
    public static double[] spread(Type t,int ray){
        if(ray<0||ray>=t.rays)throw new IllegalArgumentException("ray");
        if(t.mechanic==Mechanic.CROSS){double[][] cross={{0,0},{-1,0},{1,0},{0,-1},{0,1}};return new double[]{cross[ray][0]*t.spreadDegrees,cross[ray][1]*t.spreadDegrees};}
        if(t.rays==1)return new double[]{0,0};
        if(t.mechanic==Mechanic.SCATTER){double[][] fan={{0,0},{-.9,-.3},{.9,-.3},{-.5,.55},{.5,.55},{0,-.75}};return new double[]{fan[ray][0]*t.spreadDegrees,fan[ray][1]*t.spreadDegrees};}
        return new double[]{(ray-(t.rays-1)*.5)*t.spreadDegrees,0};
    }
    public static double penetrationDamage(Type t,int depth){return t.damage*Math.pow(.78,Math.max(0,Math.min(MAX_TARGETS-1,depth)));}
    public static boolean safePoint(Layout.Room r,double x,double y,double z){
        return r!=null&&r.kind!=Layout.Kind.REFUGE&&Double.isFinite(x)&&Double.isFinite(y)&&Double.isFinite(z)
            &&x>=r.x+4&&x<r.x+r.w-4&&z>=r.z+4&&z<r.z+r.d-4&&y>=Layout.FLOOR+1&&y<r.roof()-1;
    }
    /** Exact ray/AABB slab intersection. Direction must be normalized by the caller. */
    public static double rayBox(double[] origin,double[] direction,double[] low,double[] high,double limit){
        double enter=0,exit=limit;
        if(!Double.isFinite(limit)||limit<0)return Double.POSITIVE_INFINITY;
        for(int axis=0;axis<3;axis++){
            double o=origin[axis],d=direction[axis];
            if(!Double.isFinite(o)||!Double.isFinite(d)||!Double.isFinite(low[axis])||!Double.isFinite(high[axis])||low[axis]>high[axis])return Double.POSITIVE_INFINITY;
            if(Math.abs(d)<1e-9){if(o<low[axis]||o>high[axis])return Double.POSITIVE_INFINITY;}
            else{double a=(low[axis]-o)/d,b=(high[axis]-o)/d;enter=Math.max(enter,Math.min(a,b));exit=Math.min(exit,Math.max(a,b));if(enter>exit)return Double.POSITIVE_INFINITY;}
        }
        return enter<=limit?enter:Double.POSITIVE_INFINITY;
    }
    public static List<String> audit(){
        List<String> errors=new ArrayList<>();Set<String> ids=new HashSet<>();Set<Mechanic> mechanics=EnumSet.noneOf(Mechanic.class);int guns=0,other=0;
        for(Type t:Type.values()){
            if(!ids.add(t.id())||!mechanics.add(t.mechanic))errors.add("duplicate "+t);
            if(t.gun())guns++;else other++;
            if(t.theme<18||t.theme>35||t.rank<1||t.rank>5||t.rays<1||t.rays>MAX_RAYS||t.pierce<1||t.pierce>MAX_TARGETS
                ||t.damage<=0||t.damage>12||t.range<3||t.range>36||t.cooldownMillis<400||t.burst<1||t.burst>3)errors.add("bounds "+t);
            if(t.gun()&&(t.magazine<t.triggerCost()||t.reloadMillis<1000||t.cooldownMillis<(t.burst-1)*550+100))errors.add("timing/ammo "+t);
            if(!t.gun()&&(t.magazine!=0||t.reloadMillis!=0||t.burst!=1))errors.add("magic/melee ammo "+t);
        }
        if(guns<12||other<12)errors.add("content counts");return Collections.unmodifiableList(errors);
    }
}
