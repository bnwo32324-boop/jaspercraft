package chat.jaspr.apocalypse;

import com.google.gson.Gson;
import com.mojang.authlib.GameProfile;
import fr.xephi.authme.api.v3.AuthMeApi;
import fr.xephi.authme.data.auth.PlayerAuth;
import fr.xephi.authme.data.auth.PlayerCache;
import java.io.*;
import java.lang.reflect.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import net.minecraft.server.v1_12_R1.*;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.craftbukkit.v1_12_R1.CraftServer;
import org.bukkit.craftbukkit.v1_12_R1.CraftWorld;
import org.bukkit.craftbukkit.v1_12_R1.entity.CraftPlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

/** Actual online-list CraftPlayer and actual server-stop/save sequence, across separate JVMs.
 * Only network transport is stubbed. No real client, production identity or public server is used. */
public final class StatsShutdownProbe extends JavaPlugin {
    private static final String NAME="statsshutdown";
    private static final UUID ID=UUID.nameUUIDFromBytes("jaspr:isolated:stats-shutdown:v1".getBytes(StandardCharsets.UTF_8));
    private final List<String> failures=new ArrayList<>();
    private final Map<String,Object> metrics=new LinkedHashMap<>();
    private final int phase=Integer.getInteger("jaspr.stats.shutdown.phase",0);
    private final boolean jvmHook=Boolean.getBoolean("jaspr.stats.shutdown.jvmHook");
    private NativePlayer player;private SurvivorStats stats;private PlayerCache auth;private int assertions;private boolean armed;
    @Override public void onEnable(){
        try{File dir=new File(".").getCanonicalFile();if(!Boolean.getBoolean("jaspr.stats.shutdown.fixture")||!dir.getName().equals("server")
            ||!dir.getParentFile().getName().startsWith("jaspr-stats-shutdown-")||phase<1||phase>3)throw new IllegalStateException("Not an isolated shutdown fixture");}
        catch(IOException e){throw new IllegalStateException(e);}
        armed=true;Bukkit.getScheduler().runTaskLater(this,this::run,3);
    }
    private void check(boolean value,String text){assertions++;if(!value)throw new AssertionError(text);}
    private void near(double actual,double expected,String text){check(Math.abs(actual-expected)<1e-6,text+" actual="+actual+" expected="+expected);}
    private static Object field(Object object,String key)throws Exception{Field f=object.getClass().getDeclaredField(key);f.setAccessible(true);return f.get(object);}
    private Object call(String name,Class<?>[] types,Object...args)throws Exception{Method m=SurvivorStats.class.getDeclaredMethod(name,types);m.setAccessible(true);return m.invoke(stats,args);}
    private void refresh()throws Exception{call("refresh",new Class<?>[]{Player.class},player);}
    private int ranks()throws Exception{return ((StatRules.Ranks)field(call("profile",new Class<?>[]{Player.class},player),"ranks")).rank(StatRules.Stat.VITALITY);}
    private long owned(){return player.getAttribute(Attribute.GENERIC_MAX_HEALTH).getModifiers().stream().filter(m->m.getName().equals("jaspr.stats.v1")).count();}
    private NBTTagCompound saved()throws Exception{
        File file=new File("world/playerdata/"+ID+".dat");try(InputStream in=new FileInputStream(file)){return NBTCompressedStreamTools.a(in);}
    }
    private void run(){
        try{
            check(Bukkit.getOnlinePlayers().isEmpty(),"No real players before fixture insertion");
            CraftServer server=(CraftServer)Bukkit.getServer();MinecraftServer nms=server.getServer();
            check(nms.isRunning()&&!nms.isStopped(),"Live server shutdown flags initially false");
            ApocalypsePlugin plugin=(ApocalypsePlugin)Bukkit.getPluginManager().getPlugin("JasprApocalypse");stats=plugin.stats();
            auth=(PlayerCache)field(AuthMeApi.getInstance(),"playerCache");
            WorldServer world=((CraftWorld)Bukkit.getWorld("world")).getHandle();
            EntityPlayer handle=new EntityPlayer(nms,world,new GameProfile(ID,NAME),new PlayerInteractManager(world));
            player=new NativePlayer(server,handle);
            Field wrapper=Entity.class.getDeclaredField("bukkitEntity");wrapper.setAccessible(true);wrapper.set(handle,player);
            handle.playerConnection=new FixtureConnection(nms,handle);handle.setPosition(0,65,0);
            if(phase>1)player.loadData();
            // getOnlinePlayers() is Paper's actual transformed PlayerList.players collection.
            server.getHandle().players.add(handle);
            check(Bukkit.getOnlinePlayers().size()==1&&Bukkit.getOnlinePlayers().contains(player)&&player.isOnline(),"Real native actor is in actual online list");
            auth.updatePlayer(new PlayerAuth.Builder().name(NAME).realName(NAME).build());
            check(plugin.isSurvivor(player),"Actual AuthMe cache authenticates actor");
            if(phase==1)stage();else restarted();
            if(phase==3)manualDisable(plugin);
            metrics.put("healthBeforeShutdown",player.getHealth());metrics.put("maxBeforeShutdown",player.getAttribute(Attribute.GENERIC_MAX_HEALTH).getValue());metrics.put("ownedBeforeShutdown",owned());
        }catch(Throwable error){while(error instanceof InvocationTargetException)error=error.getCause();failures.add(error.toString());error.printStackTrace();}
        // Do not manually invoke native save, remove the online actor, or call plugin.disable here.
        // MinecraftServer.stop must disable plugins then perform its real final player save.
        Bukkit.getScheduler().runTaskLater(this,()->{if(jvmHook)System.exit(0);else Bukkit.shutdown();},1);
    }
    @SuppressWarnings("unchecked") private void stage()throws Exception{
        player.setLevel(60);player.setExp(0);player.setTotalExperience((int)StatRules.xpAtLevel(60));
        for(int rank=0;rank<10;rank++){
            ((Map<UUID,Long>)field(stats,"purchaseReady")).remove(ID);
            check((Boolean)call("purchase",new Class<?>[]{Player.class,StatRules.Stat.class},player,StatRules.Stat.VITALITY),"Native Vitality purchase "+rank);
        }
        check(ranks()==10&&owned()==1,"Rank10 and exactly one owned maximum modifier");
        near(player.getAttribute(Attribute.GENERIC_MAX_HEALTH).getValue(),40,"Native maximum40");
        player.setHealth(36);
        // A live, non-server component disable must continue stripping/clamping.
        check(!((CraftServer)Bukkit.getServer()).getServer().isStopped(),"Manual stop occurs on running server");
        stats.stop();near(player.getHealth(),20,"Manual disable clamps health");near(player.getAttribute(Attribute.GENERIC_MAX_HEALTH).getValue(),20,"Manual disable removes maximum bonus");check(owned()==0,"Manual disable strips exactly owned modifier");
        stats.start();refresh();check(ranks()==10&&owned()==1,"Manual restart reloads ranks and single modifier");
        near(player.getAttribute(Attribute.GENERIC_MAX_HEALTH).getValue(),40,"Manual restart reattaches maximum");near(player.getHealth(),20,"Manual restart grants no free healing");
        player.setHealth(36);player.getInventory().setItem(0,new ItemStack(Material.LOG,13));player.addScoreboardTag("fixture:shutdown-persistence");
        // This change is deliberately UNSAVED: phase2 must see the final shutdown save, not a purchase checkpoint.
        near(saved().getFloat("Health"),20,"Pre-shutdown disk health is still20");
        check(!saved().getList("Tags",8).toString().contains("fixture:shutdown-persistence"),"Post-purchase marker not yet serialized");
        getLogger().info("STATS_SHUTDOWN_STAGED health=36 max=40 owned=1 online="+Bukkit.getOnlinePlayers().size());
    }
    private void restarted()throws Exception{
        near(saved().getFloat("Health"),36,"Native final shutdown save wrote boosted health");
        near(player.getHealth(),36,"Native load restores36 HP before stats login refresh");
        near(player.getAttribute(Attribute.GENERIC_MAX_HEALTH).getValue(),40,"Native load restores max40");
        check(owned()==1,"Disk restores exactly one owned modifier");check(player.getScoreboardTags().contains("fixture:shutdown-persistence"),"Final native save preserved new marker");
        check(player.getInventory().getItem(0).equals(new ItemStack(Material.LOG,13)),"Final native save preserved inventory");
        stats.joined(new PlayerJoinEvent(player,"fixture"));near(player.getHealth(),36,"Join cleanup must not clamp boosted HP");check(owned()==0,"Join removes serialized owned modifier");
        refresh();near(player.getHealth(),36,"Authenticated refresh preserves36 HP");near(player.getAttribute(Attribute.GENERIC_MAX_HEALTH).getValue(),40,"Authenticated refresh restores40 maximum");
        for(int i=0;i<20;i++)refresh();check(owned()==1&&ranks()==10,"Repeated refresh never duplicates bonus or rank");
        getLogger().info("STATS_SHUTDOWN_RESTART_PASS health=36 max=40 owned=1 online="+Bukkit.getOnlinePlayers().size());
    }
    private void manualDisable(ApocalypsePlugin plugin)throws Exception{
        MinecraftServer nms=((CraftServer)Bukkit.getServer()).getServer();
        check(nms.isRunning()&&!nms.isStopped()&&!org.spigotmc.AsyncCatcher.shuttingDown,"Actual manual plugin disable happens outside either global shutdown path");
        Set<String> tags=new HashSet<>(player.getScoreboardTags());
        Bukkit.getPluginManager().disablePlugin(plugin);
        check(!plugin.isEnabled(),"Bukkit actually disabled production plugin");
        check(nms.isRunning()&&!nms.isStopped()&&!org.spigotmc.AsyncCatcher.shuttingDown,"Manual plugin disable does not set global shutdown flags");
        near(player.getHealth(),20,"Actual manual plugin disable clamps boosted health");
        near(player.getAttribute(Attribute.GENERIC_MAX_HEALTH).getValue(),20,"Actual manual plugin disable removes bonus maximum");
        check(owned()==0,"Actual manual plugin disable removes owned modifier");
        check(player.getScoreboardTags().equals(tags),"Actual manual plugin disable preserves rank and lifecycle tags");
        check(player.getInventory().getItem(0).equals(new ItemStack(Material.LOG,13)),"Actual manual plugin disable preserves inventory");
        getLogger().info("STATS_SHUTDOWN_MANUAL_DISABLE_PASS health=20 max=20 owned=0 online="+Bukkit.getOnlinePlayers().size());
    }
    @Override public void onDisable(){
        if(!armed)return;
        try{
            MinecraftServer nms=((CraftServer)Bukkit.getServer()).getServer();Field stop=MinecraftServer.class.getDeclaredField("hasStopped");stop.setAccessible(true);
            metrics.put("isRunningAtDisable",nms.isRunning());metrics.put("isStoppedAtDisable",nms.isStopped());metrics.put("hasStoppedAtDisable",stop.getBoolean(nms));
            metrics.put("asyncCatcherShuttingDown",org.spigotmc.AsyncCatcher.shuttingDown);
            check((nms.isStopped()||org.spigotmc.AsyncCatcher.shuttingDown)&&stop.getBoolean(nms),"Correct global shutdown flag and stop-entry hasStopped before plugin disable");
            check(jvmHook?org.spigotmc.AsyncCatcher.shuttingDown&&!nms.isStopped():nms.isStopped(),"Expected normal-loop versus direct JVM-hook shutdown path");
            check(Bukkit.getOnlinePlayers().contains(player),"Actor remains online through actual global plugin disable");
        }catch(Throwable error){failures.add(error.toString());}
        Map<String,Object> report=new LinkedHashMap<>();report.put("phase",phase);report.put("assertions",assertions);report.put("success",failures.isEmpty());report.put("failures",failures);report.put("metrics",metrics);
        String json=new Gson().toJson(report);
        // Log4j's own shutdown hook can close the logger before Paper disables us.
        // A fixture-local report remains readable after the child JVM fully exits.
        try(OutputStream out=new FileOutputStream("stats-shutdown-phase-"+phase+".json")){
            out.write(json.getBytes(StandardCharsets.UTF_8));
        }catch(IOException error){throw new IllegalStateException("Cannot persist isolated shutdown report",error);}
        getLogger().info("STATS_SHUTDOWN_RESULT "+json);
        // Intentionally retain the player in PlayerList: native savePlayers is the subject under test.
    }
    private static final class NativePlayer extends CraftPlayer{
        NativePlayer(CraftServer server,EntityPlayer handle){super(server,handle);}
        @Override public boolean isOnline(){return ((CraftServer)Bukkit.getServer()).getHandle().players.contains(getHandle());}
        @Override public GameMode getGameMode(){return GameMode.SURVIVAL;}
        @Override public void sendMessage(String message){}
        @Override public void playSound(Location location,Sound sound,float volume,float pitch){}
    }
    private static final class FixtureConnection extends PlayerConnection{
        FixtureConnection(MinecraftServer server,EntityPlayer player){super(server,new NetworkManager(EnumProtocolDirection.SERVERBOUND),player);}
        @Override public void sendPacket(Packet<?> packet){}
        @Override public void disconnect(String message){}
        @Override public void disconnect(IChatBaseComponent message){}
    }
}
