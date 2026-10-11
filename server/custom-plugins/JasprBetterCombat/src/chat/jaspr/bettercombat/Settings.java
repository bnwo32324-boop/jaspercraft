package chat.jaspr.bettercombat;

import java.util.*;
import net.minecraft.server.v1_12_R1.Item;
import net.minecraft.server.v1_12_R1.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.configuration.file.FileConfiguration;

public final class Settings {
    public final boolean enabled, longer, wider, randomCrits, refund, weaker, full, sprint, sweep, hitSound, critSound, offhand;
    public final double width, chance, efficiency;
    public final Set<String> blacklist, classes, items;
    private final List<Class<?>> itemTypes=new ArrayList<>(),entityTypes=new ArrayList<>();
    public Settings(FileConfiguration c) {
        enabled=c.getBoolean("enabled",true); longer=c.getBoolean("longer-attack",true);
        wider=c.getBoolean("wider-attack",true); randomCrits=c.getBoolean("random-crits",true);
        refund=c.getBoolean("refund-energy-on-miss",true); weaker=c.getBoolean("weaker-offhand",true);
        full=c.getBoolean("require-full-energy",false); sprint=c.getBoolean("attack-and-sprint",true);
        sweep=c.getBoolean("more-sweep-animation",true); hitSound=c.getBoolean("additional-hit-sound",true);
        critSound=c.getBoolean("additional-crit-sound",true); offhand=c.getBoolean("enable-offhand-attacks",true);
        width=number(c,"wider-attack-width",1.5,0,64); chance=number(c,"crit-chance",.3,0,1);
        efficiency=number(c,"offhand-efficiency",.5,0,16384);
        blacklist=new HashSet<>(c.getStringList("offhand-entity-blacklist"));
        classes=new HashSet<>(c.getStringList("offhand-item-classes"));
        items=new HashSet<>(c.getStringList("offhand-item-whitelist"));
        for(String name:classes) {
            String simple=name;
            if(name.equals("SWORD"))simple="ItemSword";else if(name.equals("AXE"))simple="ItemAxe";
            else if(name.equals("SPADE"))simple="ItemSpade";else if(name.equals("PICKAXE"))simple="ItemPickaxe";
            else if(name.equals("HOE"))simple="ItemHoe";else if(name.equals("TOOL"))simple="ItemTool";
            Class<?> type=vanillaClass(simple,"net.minecraft.item.",Item.class);if(type!=null)itemTypes.add(type);
        }
        for(String name:blacklist) {
            Class<?> type=vanillaClass(name,"net.minecraft.entity.",Entity.class);
            if(type!=null)entityTypes.add(type);
            else try{Class<?> b=EntityType.valueOf(name).getEntityClass();if(b!=null)entityTypes.add(b);}catch(IllegalArgumentException ignored){}
        }
    }
    public boolean usable(Item item){for(Class<?> type:itemTypes)if(type.isInstance(item))return true;return false;}
    public boolean blocked(Entity entity){
        for(Class<?> type:entityTypes)if(type.isInstance(entity)||type.isInstance(entity.getBukkitEntity()))return true;
        return false;
    }
    /** The same test for a registered entity class (network type id {@code id}), for the client's interaction hint. */
    public boolean blockedType(Class<?> nms,int id){
        EntityType bukkit=id>0?EntityType.fromId(id):null;
        Class<?> api=bukkit==null?null:bukkit.getEntityClass();
        for(Class<?> type:entityTypes)if(type.isAssignableFrom(nms)||api!=null&&type.isAssignableFrom(api))return true;
        return false;
    }
    public boolean blockedPlayers(){
        for(Class<?> type:entityTypes)if(type.isAssignableFrom(net.minecraft.server.v1_12_R1.EntityPlayer.class)||type.isAssignableFrom(org.bukkit.entity.Player.class))return true;
        return false;
    }
    private static Class<?> vanillaClass(String name,String prefix,Class<?> base){
        // Translate Minecraft class names only; never load arbitrary user-specified Java code.
        if(name.startsWith(prefix))name=name.substring(name.lastIndexOf('.')+1);
        if(!name.matches("(?:Item|Entity)[A-Za-z0-9_]+"))return null;
        try{Class<?> type=Class.forName("net.minecraft.server.v1_12_R1."+name,false,Settings.class.getClassLoader());return base.isAssignableFrom(type)?type:null;}
        catch(ClassNotFoundException e){return null;}
    }
    private static double number(FileConfiguration c,String key,double fallback,double min,double max) {
        double value=c.getDouble(key,fallback);
        return Double.isFinite(value)?Math.max(min,Math.min(max,value)):fallback;
    }
}
