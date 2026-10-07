package chat.jaspr.apocalypse;

import com.google.gson.Gson;
import fr.xephi.authme.api.v3.AuthMeApi;
import fr.xephi.authme.data.auth.PlayerAuth;
import fr.xephi.authme.data.auth.PlayerCache;
import java.io.*;
import java.lang.reflect.*;
import java.util.*;
import net.minecraft.server.v1_12_R1.AxisAlignedBB;
import net.minecraft.server.v1_12_R1.NBTCompressedStreamTools;
import net.minecraft.server.v1_12_R1.NBTTagCompound;
import org.bukkit.*;
import org.bukkit.attribute.*;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.command.*;
import org.bukkit.craftbukkit.v1_12_R1.inventory.CraftItemStack;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.*;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.*;
import org.bukkit.inventory.*;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.*;
import org.bukkit.util.Vector;
import org.bukkit.util.io.*;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;

/**
 * Real Paper checks of the Field Journal's server half (2026-10-07). The real JasprApocalypse, JasprRPG, JasprDisasters and
 * JasprInvasions jars are loaded next to JasprJournal, and the payload a scripted player would get is inspected: the Blood Moon
 * countdown (the siege's own rule, with the configured interval), the invasion mark, the coarse disaster hint, the stat summary
 * (checked against an independent computation) and the silent item effects; plus the hello handshake, its bounds, and the way a
 * missing plugin hides only its own row. Nothing is sent over a network (the player is a scripted interface); the wire itself is
 * checked in the real browser (tests/journal-browser.cjs).
 */
public final class JournalProbe extends JavaPlugin implements Listener {
    private World world;
    private Actor actor;
    private Plugin journal;
    private int assertions;
    private final List<String> failures = new ArrayList<String>();
    private final Map<String,Object> metrics = new LinkedHashMap<String,Object>();
    private boolean run;
    private interface Checked { void run() throws Exception; }
    @Override public void onEnable() {
        try {
            File cwd = new File(".").getCanonicalFile();
            if (!Boolean.getBoolean("jaspr.apocalypse.smoke") || !cwd.getParentFile().getName().matches("apocalypse-smoke-[0-9a-f-]{36}"))
                throw new IllegalStateException("Journal probe requires isolated fixture");
            Bukkit.getPluginManager().registerEvents(this,this);
            getLogger().info("APOCALYPSE_SMOKE_ARMED journal probe");
        } catch (Exception failure) { throw new IllegalStateException(failure); }
    }
    @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof ConsoleCommandSender) || run) return true;
        run=true; Bukkit.getScheduler().runTask(this, new Runnable() { public void run() { begin(); } }); return true;
    }
    private void check(boolean value,String message) { assertions++; if(!value) throw new AssertionError(message); }
    private void phase(String name,Checked action) {
        try { action.run(); getLogger().info("APOCALYPSE_SMOKE_PHASE "+name+" PASS assertions="+assertions); }
        catch(Throwable failure) { while(failure.getCause()!=null) failure=failure.getCause(); failures.add(name+": "+failure); failure.printStackTrace(); }
    }
    private static Object field(Object object,String name) throws Exception {
        Class<?> type=object instanceof Class?(Class<?>)object:object.getClass();
        Field field=type.getDeclaredField(name);field.setAccessible(true);return field.get(object instanceof Class?null:object);
    }
    private static void setField(Object object,String name,Object value) throws Exception {
        Field field=object.getClass().getDeclaredField(name);field.setAccessible(true);field.set(object,value);
    }
    private static Object call(Object object,String name,Class<?>[] types,Object... args) throws Exception {
        Class<?> type=object instanceof Class?(Class<?>)object:object.getClass();
        Method method=type.getDeclaredMethod(name,types);method.setAccessible(true);return method.invoke(object instanceof Class?null:object,args);
    }
    private static Plugin plugin(String name){return Bukkit.getPluginManager().getPlugin(name);}
    private JsonObject panel() throws Exception {
        String json=(String)journal.getClass().getMethod("panelJson",Player.class).invoke(journal,actor.player);
        return new JsonParser().parse(json).getAsJsonObject();
    }
    private void time(long fullTime){world.setFullTime(fullTime);}
    private static byte[] wire(String text){
        byte[] utf=text.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        java.io.ByteArrayOutputStream out=new java.io.ByteArrayOutputStream();int n=utf.length;
        while((n&~0x7F)!=0){out.write((n&0x7F)|0x80);n>>>=7;}out.write(n);out.write(utf,0,utf.length);return out.toByteArray();
    }

    private void begin() {
        phase("setup",()->{
            check(Bukkit.getOnlinePlayers().isEmpty(),"No real players in fixture");
            world=Bukkit.getWorlds().get(0);
            for(String name:new String[]{"JasprJournal","JasprApocalypse","JasprRPG","JasprDisasters","JasprInvasions","JasprGear"}){
                Plugin p=plugin(name);check(p!=null&&p.isEnabled(),name+" is loaded and enabled in the fixture");
            }
            journal=plugin("JasprJournal");
            actor=new Actor("anon_0707");
            check(journal.getDescription().getVersion().equals("1.0.1"),"Journal version");
        });
        if(actor==null){finish();return;}
        phase("payload-basics",this::basics);
        phase("blood-moon-uses-the-sieges-rule",this::bloodMoon);
        phase("invasion-mark",this::invasion);
        phase("disaster-hint-never-the-time",this::disasters);
        phase("stat-summary-matches-an-independent-computation",this::stats);
        phase("silent-item-effects",this::effects);
        phase("worn-trinkets",this::trinkets);
        phase("hello-handshake-and-bounds",this::hello);
        phase("a-missing-plugin-hides-only-its-row",this::missing);
        phase("no-source-failed",()->{
            Object failuresMap=field(field(journal,"sources"),"failures");
            check(((Map<?,?>)failuresMap).isEmpty(),"no source ever failed: "+failuresMap);
        });
        finish();
    }

    private void basics() throws Exception {
        time(24000L*2+5000L);actor.level=7;actor.exp=0.5f;
        JsonObject p=panel();
        check(p.get("v").getAsInt()==1,"version 1");
        check(p.get("d").getAsLong()==2&&"Morning".equals(p.get("ph").getAsString()),"day number and phase come from the main world's clock");
        check(p.get("lv").getAsInt()==7&&p.get("xp").getAsInt()==20,"experience level and bar (half full = 20 of 40)");
        check(p.has("bm")&&p.has("iv")&&p.has("dz")&&p.has("rp")&&p.has("fx"),"every part is present when every plugin runs: "+p);
        check(p.get("fx").getAsJsonArray().size()==0,"the scripted player has no effects");
        for(long t:new long[]{0,5999,6000,11999,12000,12999,13000,22999,23000,23999}){time(t);String ph=panel().get("ph").getAsString();
            String want=t<6000?"Morning":t<12000?"Midday":t<13000?"Dusk":t<23000?"Night":"Dawn";check(want.equals(ph),"phase at "+t+": "+ph);}
        check(panel().toString().length()<600,"a small payload: "+panel().toString().length());
    }

    private void bloodMoon() throws Exception {
        Plugin apocalypse=plugin("JasprApocalypse");
        check(apocalypse.getConfig().getInt("siege.blood-moon-every-nights",3)==3,"the fixture runs the shipped interval of 3");
        // Blood Moon nights are the nights of days 2, 5, 8 (the siege rule: (day + 1) % 3 == 0, between ticks 13000 and 23000).
        long[][] cases={{0,2},{24000,1},{48000,0},{48000+12999,0},{48000+13000,-2},{48000+22999,-2},{48000+23000,2},{72000,2},{96000,1},{120000,0}};
        for(long[] c:cases){time(c[0]);JsonObject p=panel();check(p.has("bm")&&p.get("bm").getAsInt()==(int)c[1],"time "+c[0]+": expected "+c[1]+" got "+p.get("bm"));}
        // The interval is read from the siege's own configuration every time.
        apocalypse.getConfig().set("siege.blood-moon-every-nights",2);
        try{
            time(0);check(panel().get("bm").getAsInt()==1,"every 2nd night: the night of day 1 is the first");
            time(24000+13500);check(panel().get("bm").getAsInt()==-2,"and it is under way then");
            apocalypse.getConfig().set("siege.blood-moon-every-nights",0);
            check(!panel().has("bm"),"an interval of 0 means no Blood Moons: the row is not sent");
        }finally{apocalypse.getConfig().set("siege.blood-moon-every-nights",3);}
        // It asks the real rule: SiegeRules itself agrees with every answer at a sweep of times.
        Class<?> rules=apocalypse.getClass().getClassLoader().loadClass("chat.jaspr.apocalypse.SiegeRules");
        for(long t=0;t<24000L*12;t+=1500){time(t);
            boolean now=(Boolean)call(rules,"bloodMoon",new Class<?>[]{long.class,int.class},t,3);int bm=panel().get("bm").getAsInt();
            check(now==(bm==-2),"time "+t+": under way iff the siege rule says so ("+now+" vs "+bm+")");
            if(!now)check(bm>=0&&bm<=2,"never more than two nights away with an interval of 3: "+bm+" at "+t);}
    }

    private void invasion() throws Exception {
        Plugin inv=plugin("JasprInvasions");
        time(24000L*3+500);
        JsonArray none=panel().get("iv").getAsJsonArray();
        check(none.get(0).getAsInt()==0,"nothing marked yet: "+none);
        call(inv,"noteSleep",new Class<?>[]{Player.class},actor.player);
        JsonArray marked=panel().get("iv").getAsJsonArray();
        int days=(Integer)field(field(inv,"settings"),"daysAfterSleep");
        check(marked.get(0).getAsInt()==1&&marked.get(1).getAsInt()==(int)((24000L*3+500+days*24000L)/24000L),"slept on day 3: waiting until day "+(3+days)+": "+marked);
        time(24000L*3+500+days*24000L-1);check(panel().get("iv").getAsJsonArray().get(0).getAsInt()==1,"one tick before the day it is still waiting");
        time(24000L*3+500+days*24000L);check(panel().get("iv").getAsJsonArray().get(0).getAsInt()==2,"from then on: any night");
        // Under way beats the mark (put the player in the plugin's active map, as a real invasion does, then take them out again).
        @SuppressWarnings("unchecked") Map<UUID,Object> active=(Map<UUID,Object>)field(inv,"active");
        active.put(actor.uuid,null);
        try{check(panel().get("iv").getAsJsonArray().get(0).getAsInt()==3,"under way");}finally{active.remove(actor.uuid);}
        // Reading never creates a record for someone who has none.
        Actor stranger=new Actor("anon_0708");
        Object store=field(inv,"store");int before=((Collection<?>)call(store,"all",new Class<?>[]{})).size();
        String json=(String)journal.getClass().getMethod("panelJson",Player.class).invoke(journal,stranger.player);
        int after=((Collection<?>)call(store,"all",new Class<?>[]{})).size();
        check(before==after,"asking for a stranger's panel creates no invasion record ("+before+" -> "+after+")");
        check(new JsonParser().parse(json).getAsJsonObject().get("iv").getAsJsonArray().get(0).getAsInt()==0,"a stranger has no mark");
        // Clean up what this probe wrote.
        Object progress=null;for(Object pr:(Collection<?>)call(store,"all",new Class<?>[]{}))if(actor.uuid.equals(field(pr,"id")))progress=pr;
        if(progress!=null)setField(progress,"sleptAt",-1L);
    }

    private void disasters() throws Exception {
        Plugin dis=plugin("JasprDisasters");
        long original=(Long)field(dis,"nextAt");
        try{
            setField(dis,"nextAt",System.currentTimeMillis()+3L*24000L*50L);
            JsonArray quiet=panel().get("dz").getAsJsonArray();check(quiet.get(0).getAsInt()==0&&quiet.get(1).getAsString().isEmpty(),"due in 3 days: quiet "+quiet);
            setField(dis,"nextAt",System.currentTimeMillis()+24000L*50L-5000L);
            check(panel().get("dz").getAsJsonArray().get(0).getAsInt()==1,"due within a Minecraft day: brewing");
            setField(dis,"nextAt",System.currentTimeMillis()+1000L);
            check(panel().get("dz").getAsJsonArray().get(0).getAsInt()==1,"due in a second: still only 'brewing'");
            setField(dis,"nextAt",System.currentTimeMillis()-1000L);
            check(panel().get("dz").getAsJsonArray().get(0).getAsInt()==1,"overdue: brewing");
            String text=panel().toString();
            check(!text.contains(String.valueOf(original))&&!text.contains("next"),"no time or timestamp is in the payload: "+text);
            check(dis.getClass().getMethod("journalState").invoke(dis) instanceof String,"the door answers with a plain string");
        }finally{setField(dis,"nextAt",original);}
    }

    /** What the stat sheet says, computed again here from the stat table and the shipped price curve. */
    private void stats() throws Exception {
        Plugin rpg=plugin("JasprRPG");
        Class<?> typeClass=rpg.getClass().getClassLoader().loadClass("chat.jaspr.rpg.StatType");
        Object[] all=typeClass.getEnumConstants();
        check(all.length>=45,"the fixture has the full stat table: "+all.length);
        Object store=call(rpg,"stats",new Class<?>[]{});
        Object sheet=call(store,"get",new Class<?>[]{Player.class},actor.player);
        Method set=sheet.getClass().getDeclaredMethod("set",typeClass,int.class);set.setAccessible(true);
        Method byKey=typeClass.getDeclaredMethod("byKey",String.class);byKey.setAccessible(true);
        Object engineering=byKey.invoke(null,"engineering"),health=byKey.invoke(null,"health");
        try{
            check(engineering!=null&&health!=null,"two stat keys exist");
            set.invoke(sheet,engineering,3);set.invoke(sheet,health,10);
            Field capField=typeClass.getDeclaredField("cap");capField.setAccessible(true);
            for(int xp:new int[]{0,5,6,7,11,50,200}){
                actor.level=xp;JsonArray rp=panel().get("rp").getAsJsonArray();
                int ranks=0,raised=0,total=0,affordable=0,cheapest=-1;
                for(Object stat:all){
                    total++;int level=stat==engineering?3:stat==health?10:0;if(level>0){ranks+=level;raised++;}
                    int cap=capField.getInt(stat);if(level>=cap)continue;
                    double raw=level>=10?50:Math.pow(level,1.6d)+6.0d+level;int cost=Math.max(1,(int)raw);
                    if(cheapest<0||cost<cheapest)cheapest=cost;if(xp>=cost)affordable++;
                }
                check(rp.get(0).getAsInt()==ranks&&rp.get(1).getAsInt()==raised&&rp.get(2).getAsInt()==total&&rp.get(3).getAsInt()==affordable&&rp.get(4).getAsInt()==cheapest,
                    "xp level "+xp+": expected "+ranks+","+raised+","+total+","+affordable+","+cheapest+" got "+rp);
            }
            // Reading changes nothing.
            check((Integer)call(sheet,"totalLevels",new Class<?>[]{})==13,"the sheet is untouched by reading");
        }finally{set.invoke(sheet,engineering,0);set.invoke(sheet,health,0);actor.level=0;}
        // A player with no sheet at all: all zeros, everything affordable at a high level.
        Actor fresh=new Actor("anon_0709");fresh.level=100;
        JsonArray rp=new JsonParser().parse((String)journal.getClass().getMethod("panelJson",Player.class).invoke(journal,fresh.player)).getAsJsonObject().get("rp").getAsJsonArray();
        check(rp.get(0).getAsInt()==0&&rp.get(1).getAsInt()==0&&rp.get(3).getAsInt()==rp.get(2).getAsInt(),"a player who never trained: no ranks, every stat affordable at level 100: "+rp);
    }

    private void effects() throws Exception {
        try{
            actor.effects.add(new PotionEffect(PotionEffectType.WATER_BREATHING,12000,0,true,false));      // item-kept long effect
            actor.effects.add(new PotionEffect(PotionEffectType.FAST_DIGGING,70,1,true,false));            // refreshed every second
            actor.effects.add(new PotionEffect(PotionEffectType.SPEED,400,2));                              // a potion: has a box already
            actor.effects.add(new PotionEffect(PotionEffectType.SLOW,400,0,true,true));                     // a beacon: ambient with particles
            actor.effects.add(new PotionEffect(PotionEffectType.JUMP,400,0,false,false));                  // plugin effect, not ambient
            JsonArray fx=panel().get("fx").getAsJsonArray();
            check(fx.size()==2,"only ambient, particle-less effects are listed: "+fx);
            Map<String,Integer> by=new LinkedHashMap<String,Integer>();for(int i=0;i<fx.size();i++)by.put(fx.get(i).getAsJsonArray().get(0).getAsString(),fx.get(i).getAsJsonArray().get(1).getAsInt());
            check(by.containsKey("Water Breathing")&&by.get("Water Breathing")==600,"a long one shows its seconds: "+by);
            check(by.containsKey("Haste II")&&by.get("Haste II")==0,"an item-kept one shows no time, with its level: "+by);
            check(by.size()==2,"nothing else: "+by);
            actor.effects.clear();
            for(int i=0;i<30;i++)actor.effects.add(new PotionEffect(PotionEffectType.values()[1+i%25],400+i,0,true,false));
            check(panel().get("fx").getAsJsonArray().size()<=12,"at most twelve are sent");
        }finally{actor.effects.clear();}
        check(panel().get("fx").getAsJsonArray().size()==0,"and none when there are none");
    }

    /** The owner's report: trinkets worn in the gear column must be listed (they are not potion effects). */
    private void trinkets() throws Exception {
        Plugin gear=plugin("JasprGear");
        Class<?> api=gear.getClass().getClassLoader().loadClass("chat.jaspr.gear.GearApi"),itemType=gear.getClass().getClassLoader().loadClass("chat.jaspr.gear.GearItem");
        Method create=api.getDeclaredMethod("create",String.class);create.setAccessible(true);
        Method byId=itemType.getDeclaredMethod("byId",String.class);byId.setAccessible(true);
        check(!panel().has("gw"),"nothing worn: no trinket list");
        Object prof=call(gear,"profile",new Class<?>[]{Player.class},actor.player);
        ItemStack[] slots=(ItemStack[])field(prof,"slots");
        try{
            slots[0]=(ItemStack)create.invoke(null,"rebreather");
            slots[3]=(ItemStack)create.invoke(null,"capacitor_belt");
            slots[6]=(ItemStack)create.invoke(null,"scrap_magnet");
            slots[5]=(ItemStack)create.invoke(null,"teddy_bear");
            JsonArray gw=panel().get("gw").getAsJsonArray();
            check(gw.size()==4,"four worn trinkets are listed: "+gw);
            String[] order={"rebreather","capacitor_belt","teddy_bear","scrap_magnet"};   // slot order: neck, belt, body, charm
            for(int i=0;i<order.length;i++){
                Object item=byId.invoke(null,order[i]);
                String title=(String)itemType.getField("title").get(item);String[] effects=(String[])itemType.getField("effects").get(item);
                check(gw.get(i).getAsJsonArray().get(0).getAsString().equals(title.replaceAll("[^\\x20-\\x7e]","")),"slot "+i+" is "+title+": "+gw.get(i));
                check(gw.get(i).getAsJsonArray().get(1).getAsString().equals(effects[0].replaceAll("[^\\x20-\\x7e\"\\\\]","").replace("\"","")),"its headline is the first tooltip line: "+gw.get(i));
            }
            // Non-trinket stacks in a slot (or junk) are ignored.
            slots[1]=new ItemStack(Material.STONE_HOE);slots[2]=new ItemStack(Material.DIRT,3);
            check(panel().get("gw").getAsJsonArray().size()==4,"a plain stone hoe and dirt in the gear slots are not trinkets");
            // The change key: the same payload while nothing changes, a different one when something is taken off.
            String before=panel().toString();slots[3]=null;
            check(!panel().toString().equals(before)&&panel().get("gw").getAsJsonArray().size()==3,"taking one off changes the list");
        }finally{java.util.Arrays.fill(slots,null);}
        check(!panel().has("gw"),"all taken off: no trinket list again");
        // A player whose gear profile is not loaded has none, and asking loads nothing.
        Actor stranger=new Actor("anon_0710");
        Map<?,?> loaded=(Map<?,?>)field(gear,"profiles");int before=loaded.size();
        String json=(String)journal.getClass().getMethod("panelJson",Player.class).invoke(journal,stranger.player);
        check(!json.contains("\"gw\"")&&loaded.size()==before,"asking about a player with no loaded profile loads nothing");
    }

    private void hello() throws Exception {
        Method receive=journal.getClass().getMethod("onPluginMessageReceived",String.class,Player.class,byte[].class);
        Map<?,?> clients=(Map<?,?>)field(journal,"clients");
        long hellos=(Long)field(journal,"hellos"),rejected=(Long)field(journal,"rejected");
        // Wrong channel, malformed and unknown messages never register a client.
        receive.invoke(journal,"jaspr:other",actor.player,wire("hello 1"));
        check(clients.isEmpty(),"a hello on another channel is ignored");
        for(byte[] bad:new byte[][]{new byte[0],wire("hello 2"),wire("HELLO 1"),wire(""),new byte[100],new byte[]{(byte)0xFF,(byte)0xFF,(byte)0xFF,(byte)0xFF,(byte)0xFF},new byte[]{5,'h','e'},wire("hello 1 ").length>0?wire("hello 1 "):null}){
            receive.invoke(journal,"jaspr:journal",actor.player,bad);check(clients.isEmpty(),"a bad message registers no client");}
        check((Long)field(journal,"rejected")>=rejected+7,"bad messages are counted");
        // A real hello registers the player once (the send itself needs a real connection: the scripted player cannot take one).
        receive.invoke(journal,"jaspr:journal",actor.player,wire("hello 1"));
        check(clients.size()==1&&clients.containsKey(actor.uuid),"hello 1 registers the client");
        check((Long)field(journal,"hellos")==hellos+1,"and is counted");
        // More than six messages a second from one player are dropped (clear the client's window, then send ten hellos).
        Object client=clients.get(actor.uuid);setField(client,"messages",0);
        long r0=(Long)field(journal,"rejected");
        for(int i=0;i<10;i++)receive.invoke(journal,"jaspr:journal",actor.player,wire("hello 1"));
        check((Long)field(journal,"rejected")>=r0+3,"flooding is refused: "+((Long)field(journal,"rejected")-r0));
        clients.clear();
        check(journal.getDescription().getVersion().length()>0,"still running");
    }

    private void missing() throws Exception {
        PluginManager pm=Bukkit.getPluginManager();
        Plugin dis=plugin("JasprDisasters"),rpg=plugin("JasprRPG");
        try{
            pm.disablePlugin(dis);
            JsonObject p=panel();check(!p.has("dz")&&p.has("bm")&&p.has("iv")&&p.has("rp"),"Disasters off: only the disaster hint disappears: "+p);
            pm.disablePlugin(rpg);
            p=panel();check(!p.has("rp")&&p.has("bm")&&p.has("iv")&&p.get("lv")!=null,"stat sheet off: no stat summary, the level is still shown: "+p);
        }finally{pm.enablePlugin(dis);pm.enablePlugin(rpg);}
        JsonObject back=panel();check(back.has("dz")&&back.has("rp"),"re-enabled plugins come back: "+back);
        Object failuresMap=field(field(journal,"sources"),"failures");
        check(((Map<?,?>)failuresMap).isEmpty(),"a plugin that is simply off is not a failure: "+failuresMap);
    }

    private void finish(){
        metrics.put("scope","Isolated Paper 1.12 JVM with the real Apocalypse, RPG, Disasters, Invasions and Journal plugins; scripted Player interface, no network");
        Map<String,Object> result=new LinkedHashMap<String,Object>();result.put("success",failures.isEmpty());result.put("assertions",assertions);result.put("failures",failures);result.put("metrics",metrics);
        getLogger().info("APOCALYPSE_SMOKE_RESULT "+new Gson().toJson(result));
    }

    private static Object zero(Class<?> type){if(type==boolean.class)return false;if(type==int.class)return 0;if(type==long.class)return 0L;if(type==double.class)return 0d;if(type==float.class)return 0f;return null;}
    private static final class Attr implements AttributeInstance {
        final Attribute attribute;double base;final List<AttributeModifier> mods=new ArrayList<AttributeModifier>();
        Attr(Attribute attribute,double base){this.attribute=attribute;this.base=base;}
        public Attribute getAttribute(){return attribute;}public double getBaseValue(){return base;}public void setBaseValue(double base){this.base=base;}
        public Collection<AttributeModifier> getModifiers(){return new ArrayList<AttributeModifier>(mods);}public void addModifier(AttributeModifier m){mods.add(m);}public void removeModifier(AttributeModifier m){mods.remove(m);}
        public double getValue(){double value=base;for(AttributeModifier m:mods)value+=m.getAmount();return value;}public double getDefaultValue(){return base;}
    }
    private final class Actor {
        final String name;final UUID uuid=UUID.randomUUID();final Player player;final PlayerInventory inventory;
        ItemStack[] storage=new ItemStack[36],armor=new ItemStack[4];ItemStack cursor,offhand;
        final Map<Attribute,Attr> attributes=new EnumMap<Attribute,Attr>(Attribute.class);
        final Set<String> scoreboardTags=new HashSet<String>();
        final List<String> messages=new ArrayList<String>();
        final List<PotionEffect> effects=new ArrayList<PotionEffect>();int level;float exp;
        World currentWorld=world;Location location;GameMode mode=GameMode.SURVIVAL;boolean dead,sneaking,op;double health=20;int food=20,fire;float saturation=0;
        Actor(String name){
            location=new Location(currentWorld,.5,90,.5);
            this.name=name;for(Attribute attr:Attribute.values())attributes.put(attr,new Attr(attr,attr==Attribute.GENERIC_MAX_HEALTH?20:attr==Attribute.GENERIC_MOVEMENT_SPEED?.1:attr==Attribute.GENERIC_ATTACK_SPEED?4:0));
            inventory=(PlayerInventory)Proxy.newProxyInstance(getClassLoader(),new Class<?>[]{PlayerInventory.class},(p,m,a)->{
                switch(m.getName()){
                    case "getArmorContents":return armor;case "getStorageContents":return storage;case "getContents":return storage;
                    case "getHeldItemSlot":return 0;case "getItemInMainHand":return storage[0];case "setItemInMainHand":storage[0]=(ItemStack)a[0];return null;
                    case "getItemInOffHand":return offhand;case "setItemInOffHand":offhand=(ItemStack)a[0];return null;
                    case "getItem":return storage[(Integer)a[0]];case "setItem":storage[(Integer)a[0]]=(ItemStack)a[1];return null;case "getSize":return 41;
                    case "addItem":{Map<Integer,ItemStack> left=new HashMap<Integer,ItemStack>();
                        for(ItemStack stack:(ItemStack[])a[0]){boolean done=false;
                            for(int i=0;i<storage.length&&!done;i++)if(storage[i]==null){storage[i]=stack;done=true;}
                            if(!done)left.put(left.size(),stack);}
                        return left;}
                    case "getType":return InventoryType.PLAYER;default:return zero(m.getReturnType());
                }
            });
            player=(Player)Proxy.newProxyInstance(getClassLoader(),new Class<?>[]{Player.class},(p,m,a)->{
                switch(m.getName()){
                    case "getName":case "getDisplayName":return name;case "getUniqueId":return uuid;case "isOnline":case "isValid":return true;
                    case "isDead":return dead;case "getGameMode":return mode;case "getWorld":return currentWorld;case "getInventory":return inventory;
                    case "getAttribute":return attributes.get((Attribute)a[0]);case "getLocation":return location.clone();case "getEyeLocation":return location.clone().add(0,1.62,0);
                    case "teleport":location=((Location)a[0]).clone();currentWorld=location.getWorld();return true;
                    case "sendMessage":if(a[0] instanceof String)messages.add((String)a[0]);else if(a[0] instanceof String[])messages.addAll(Arrays.asList((String[])a[0]));return null;
                    case "isOp":return op;case "setOp":op=(Boolean)a[0];return null;
                    case "getScoreboardTags":return Collections.unmodifiableSet(scoreboardTags);
                    case "addScoreboardTag":return scoreboardTags.add((String)a[0]);case "removeScoreboardTag":return scoreboardTags.remove((String)a[0]);
                    case "getVelocity":return new Vector();case "isSneaking":return sneaking;case "getHealth":return health;case "setHealth":health=(Double)a[0];return null;
                    case "getFoodLevel":return food;case "setFoodLevel":food=(Integer)a[0];return null;case "getSaturation":return saturation;case "setSaturation":saturation=(Float)a[0];return null;
                    case "getFireTicks":return fire;case "setFireTicks":fire=(Integer)a[0];return null;case "getActivePotionEffects":return new ArrayList<PotionEffect>(effects);case "getLevel":return level;case "getExp":return exp;
                    case "addPotionEffect":case "addPotionEffects":case "removePotionEffect":throw new AssertionError("Equipment must not touch potions");
                    case "getItemOnCursor":return cursor;case "setItemOnCursor":cursor=(ItemStack)a[0];return null;
                    case "equals":return p==a[0];case "hashCode":return uuid.hashCode();case "toString":return name;case "getType":return EntityType.PLAYER;
                    default:return zero(m.getReturnType());
                }
            });
        }
        int modifierCount(){int count=0;for(Attr attr:attributes.values())count+=attr.mods.size();return count;}
    }
}
