package chat.jaspr.biomes;

import java.io.*;
import java.lang.reflect.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.logging.Logger;
import org.bukkit.*;
import org.bukkit.block.*;
import org.bukkit.craftbukkit.v1_12_R1.generator.CraftChunkData;
import org.bukkit.entity.Player;
import org.bukkit.event.block.*;
import org.bukkit.event.player.*;
import org.bukkit.event.world.WorldInitEvent;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.plugin.*;
import org.bukkit.scheduler.*;
import fr.xephi.authme.api.v3.AuthMeApi;

/** Executable tests: actual Paper 1.12.2 ChunkData/event classes, small proxy server/player fixtures.
 * No running server, network listener, player files or production jar writes. */
public final class LiminalSliceTest {
    private static int assertions;
    private static final Logger LOG=Logger.getLogger("liminal-test");
    private static final Map<String,TestWorld> worlds=new LinkedHashMap<>();
    private static final Map<UUID,TestPlayer> players=new HashMap<>();
    private static final List<Runnable> ticks=new ArrayList<>();
    private static LiminalWorld service;
    private static Plugin auth;
    private static boolean primary=true,authEnabled=true,spawnPreloadDisabled;
    private static int creates;
    private static Path data;

    public static void main(String[] args)throws Exception{
        data=Paths.get(args[0]).resolve("plugin-data");
        installServer();persistence();lifecycle();geometry();travel();everyDoor();authentication();recovery();terrainEpochs();protection();
        service.stop();
        System.out.println("LIMINAL_TESTS_OK assertions="+assertions+" rooms=16 reachableDoorHalves=128 reciprocalEdges=64");
    }
    private static void check(boolean condition,String message){assertions++;if(!condition)throw new AssertionError(message);}
    private static void equal(Location actual,Location expected,String message){
        check(actual.getWorld()==expected.getWorld()&&actual.distanceSquared(expected)<1e-12
                &&actual.getYaw()==expected.getYaw()&&actual.getPitch()==expected.getPitch(),message+" actual="+actual+" expected="+expected);
    }
    private static Object defaultValue(Class<?> type){
        if(type==boolean.class)return false;if(type==int.class)return 0;if(type==long.class)return 0L;
        if(type==double.class)return 0d;if(type==float.class)return 0f;if(type==byte.class)return (byte)0;if(type==short.class)return (short)0;return null;
    }
    private interface Call {Object call(Object proxy,Method method,Object[] args)throws Throwable;}
    @SuppressWarnings("unchecked") private static <T>T proxy(Class<T> type,Call call){
        return (T)Proxy.newProxyInstance(LiminalSliceTest.class.getClassLoader(),new Class<?>[]{type},(object,method,args)->{
            if(method.getName().equals("hashCode"))return System.identityHashCode(object);
            if(method.getName().equals("equals"))return object==args[0];
            if(method.getName().equals("toString"))return "Test"+type.getSimpleName();
            return call.call(object,method,args==null?new Object[0]:args);
        });
    }
    private static Plugin plugin(){return proxy(Plugin.class,(p,m,a)->{
        switch(m.getName()){
            case "getDataFolder":return data.toFile();case "getLogger":return LOG;case "isEnabled":return true;
            case "getName":return "LiminalSliceTest";case "getServer":return Bukkit.getServer();
            default:return defaultValue(m.getReturnType());
        }
    });}
    private static void installServer(){
        PluginManager manager=proxy(PluginManager.class,(p,m,a)->m.getName().equals("getPlugin")&&a[0].equals("AuthMe")?auth:defaultValue(m.getReturnType()));
        BukkitScheduler scheduler=proxy(BukkitScheduler.class,(p,m,a)->{
            if(m.getName().equals("runTaskTimer")){
                check(((Long)a[2])>=1,"STARTUP must defer world creation");Runnable runnable=(Runnable)a[1];ticks.add(runnable);
                return proxy(BukkitTask.class,(p2,m2,a2)->{if(m2.getName().equals("cancel"))ticks.remove(runnable);return defaultValue(m2.getReturnType());});
            }
            return defaultValue(m.getReturnType());
        });
        Server server=proxy(Server.class,(p,m,a)->{
            switch(m.getName()){
                case "getLogger":return LOG;case "getName":return "LiminalSliceTest";case "getVersion":return "Paper 1.12.2 fixture";case "getBukkitVersion":return "1.12.2";
                case "isPrimaryThread":return primary;case "getPluginManager":return manager;case "getScheduler":return scheduler;
                case "getOnlinePlayers":{List<Player> result=new ArrayList<>();for(TestPlayer player:players.values())if(player.online)result.add(player.api);return result;}
                case "getPlayer":{TestPlayer player=players.get(a[0]);return player==null?null:player.api;}
                case "getWorlds":{List<World> result=new ArrayList<>();for(TestWorld world:worlds.values())result.add(world.api);return result;}
                case "getWorld":{for(TestWorld world:worlds.values())if(world.name.equals(a[0])||world.id.equals(a[0]))return world.api;return null;}
                case "createChunkData":return new CraftChunkData((World)a[0]);
                case "createWorld":{
                    creates++;WorldCreator creator=(WorldCreator)a[0];check(creator.environment()==World.Environment.NORMAL,"real NORMAL protocol environment");
                    check(!creator.generateStructures(),"no vanilla structure expansion");
                    TestWorld created=new TestWorld(creator.name(),creator.generator());worlds.put(created.name,created);
                    service.initialized(new WorldInitEvent(created.api));spawnPreloadDisabled=!created.keepSpawn;return created.api;
                }
                default:return defaultValue(m.getReturnType());
            }
        });Bukkit.setServer(server);
    }
    private static void tick(){for(Runnable runnable:new ArrayList<>(ticks))runnable.run();}
    private static LiminalWorld.ReturnStore store(){return new LiminalWorld.ReturnStore(data.resolve("liminal-returns-v1").toFile(),LOG);}
    private static Path record(UUID id){return data.resolve("liminal-returns-v1").resolve(id+".properties");}
    private static Properties properties(Path file)throws IOException{
        Properties result=new Properties();try(InputStream in=Files.newInputStream(file)){result.load(in);}return result;
    }
    private static void legacyReturn(TestPlayer player,Location origin)throws IOException{
        Properties saved=new Properties();saved.setProperty("version","1");saved.setProperty("player",player.id.toString());
        saved.setProperty("world",origin.getWorld().getUID().toString());saved.setProperty("x",Double.toString(origin.getX()));
        saved.setProperty("y",Double.toString(origin.getY()));saved.setProperty("z",Double.toString(origin.getZ()));
        saved.setProperty("yaw",Float.toString(origin.getYaw()));saved.setProperty("pitch",Float.toString(origin.getPitch()));
        Files.createDirectories(record(player.id).getParent());
        try(OutputStream out=Files.newOutputStream(record(player.id))){saved.store(out,"Pre-epoch version 1 fixture");}
    }
    private static void epoch(String value)throws IOException{
        Files.createDirectories(data);Files.write(data.resolve("terrain-epoch.txt"),(value+"\n").getBytes(StandardCharsets.UTF_8));
    }
    private static void restart(){service.stop();service=new LiminalWorld(plugin());service.start();}

    private static void persistence()throws Exception{
        Path folder=data.resolve("store-tests");UUID player=UUID.randomUUID(),other=UUID.randomUUID(),world=UUID.randomUUID();
        LiminalWorld.ReturnStore original=new LiminalWorld.ReturnStore(folder.toFile(),LOG);
        LiminalWorld.ReturnPoint value=new LiminalWorld.ReturnPoint(world,-17.25,65.125,324.875,172.75f,-31.125f);
        check(original.write(player,value),"durable write succeeds");
        LiminalWorld.ReturnStore restarted=new LiminalWorld.ReturnStore(folder.toFile(),LOG);
        LiminalWorld.ReturnPoint read=restarted.read(player);
        check(read!=null&&read.world.equals(world)&&read.x==value.x&&read.y==value.y&&read.z==value.z&&read.yaw==value.yaw&&read.pitch==value.pitch,"restart retains every coordinate/orientation and UUID");
        check("structures-v2".equals(read.terrainEpoch),"old ReturnPoint constructor preserves legacy epoch API");
        Properties tagged=properties(folder.resolve(player+".properties"));
        check("2".equals(tagged.getProperty("version"))&&"structures-v2".equals(tagged.getProperty("terrainEpoch")),"new writes persist version 2 and an explicit epoch");
        check(restarted.write(other,value),"independent second player");
        check(restarted.write(player,new LiminalWorld.ReturnPoint(world,1.25,71,2.75,3,4)),"atomic replacement supported");
        check(original.read(player).x==1.25,"new origin replaces previous trip");
        Properties missingEpoch=properties(folder.resolve(player+".properties"));missingEpoch.remove("terrainEpoch");
        try(OutputStream out=Files.newOutputStream(folder.resolve(player+".properties"))){missingEpoch.store(out,"Incomplete version 2 fixture");}
        byte[] incomplete=Files.readAllBytes(folder.resolve(player+".properties"));
        check(restarted.read(player)==null,"version 2 without an epoch cannot masquerade as a legacy record");
        check(Arrays.equals(incomplete,Files.readAllBytes(folder.resolve(player+".properties"))),"invalid version 2 read leaves the original record untouched");
        Files.write(folder.resolve(player+".properties"),"version=1\nworld=bogus\nx=NaN\n".getBytes(StandardCharsets.UTF_8));
        check(restarted.read(player)==null&&restarted.read(other)!=null,"one corrupted record cannot discard another player's return");
        Path blocked=data.resolve("not-a-directory");Files.write(blocked,new byte[]{1});
        check(!new LiminalWorld.ReturnStore(blocked.toFile(),LOG).write(player,value),"I/O failure denies save");
        restarted.remove(other);check(!original.exists(other),"successful return cleanup is visible after restart");
    }
    private static void lifecycle(){
        service=new LiminalWorld(plugin(),HorrorPlugin::authenticated);System.setProperty("jaspr.biomes.fixture","true");service.start();service.start();
        check(ticks.size()==1&&creates==0,"start idempotent and creation delayed");tick();check(creates==0,"no world creation before normal world exists");
        worlds.put("world",new TestWorld("world",null));tick();
        check(creates==1&&spawnPreloadDisabled,"WorldInit disables spawn preload before createWorld returns");
        TestWorld dimension=worlds.get(LiminalWorld.WORLD_NAME);
        check(!dimension.id.equals(worlds.get("world").id),"world has independent UUID");check(dimension.borderSize==64,"finite border");
        tick();check(creates==1,"no repeated dimension creation");
        service.stop();check(ticks.isEmpty(),"stop cancels own task");
        service=new LiminalWorld(plugin(),HorrorPlugin::authenticated);service.start();tick();check(creates==1,"restart reuses actual world");
    }
    private static void geometry(){
        TestWorld dimension=worlds.get(LiminalWorld.WORLD_NAME);Set<Integer> heights=new HashSet<>(),reachable=new HashSet<>();
        Set<String> names=new HashSet<>(),layouts=new HashSet<>();
        for(int room=0;room<LiminalGenerator.ROOM_COUNT;room++){
            int cx=room%4,cz=room/4,base=LiminalGenerator.floor(room);heights.add(base);
            ChunkGenerator.ChunkData chunk=dimension.chunk(cx,cz);Set<String> walkable=flood(chunk,room==0?8:8,room==0?8:7,base+1);
            for(int x=0;x<16;x++)for(int y=0;y<256;y++)for(int z=0;z<16;z++){
                int id=chunk.getTypeId(x,y,z);
                check(id!=41&&id!=42&&id!=57,"valuable storage block used as Fold construction material room="+room);
            }
            names.add(LiminalGenerator.roomName(room));StringBuilder layout=new StringBuilder();
            for(int x=4;x<=11;x++)for(int y=0;y<=7;y++)for(int z=4;z<=11;z++)layout.append(chunk.getTypeId(x,base+y,z)).append(',');layouts.add(layout.toString());
            for(int side=0;side<4;side++){
                Location door=LiminalGenerator.door(dimension.api,room,side);int x=door.getBlockX()&15,z=door.getBlockZ()&15;
                check(chunk.getType(x,base+1,z)==Material.IRON_DOOR_BLOCK&&chunk.getType(x,base+2,z)==Material.IRON_DOOR_BLOCK,"both door halves actually generated");
                check(LiminalGenerator.doorAt(door)==side&&LiminalGenerator.doorAt(door.clone().add(0,1,0))==side,"both halves resolve same graph door");
                boolean adjacent=false;for(int[] delta:new int[][]{{0,1},{1,0},{0,-1},{-1,0}})adjacent|=walkable.contains((x+delta[0])+":"+(z+delta[1]));
                check(adjacent,"door reachable on foot room="+room+" side="+side);
                Location landing=LiminalGenerator.arrival(dimension.api,room,side);
                check(walkable.contains((landing.getBlockX()&15)+":"+(landing.getBlockZ()&15)),"arrival connects to all doors");
                int target=LiminalGenerator.destination(room,side);check(LiminalGenerator.destination(target,LiminalGenerator.opposite(side))==room,"reciprocal graph edge");
            }
            if(room>0)check(walkable.contains("4:9")||walkable.contains("4:11"),"loot chest accessible");
        }
        Queue<Integer> queue=new ArrayDeque<>();queue.add(0);reachable.add(0);
        while(!queue.isEmpty()){int room=queue.remove();for(int side=0;side<4;side++){int next=LiminalGenerator.destination(room,side);if(reachable.add(next))queue.add(next);}}
        check(reachable.size()==16&&heights.size()==4,"connected non-Euclidean graph spans four elevations");
        check(names.size()==16&&layouts.size()==16,"all sixteen rooms have distinct names and voxel layouts");
        check(LiminalGenerator.destination(0,0)!=0&&LiminalGenerator.floor(LiminalGenerator.destination(0,0))!=LiminalGenerator.floor(0),"north edge folds to distant room at another elevation");
        check(LiminalGenerator.lootMarkers(dimension.api).size()==15,"one unfilled loot marker per exploration room");
        for(Location marker:LiminalGenerator.lootMarkers(dimension.api))check(marker.getBlock().getType()==Material.CHEST,"loot marker matches actual chest");
        for(int[] outside:new int[][]{{-1,0},{4,0},{0,-1},{0,4},{100000,-100000}}){
            ChunkGenerator.ChunkData chunk=dimension.chunk(outside[0],outside[1]);boolean empty=true;
            for(int x=0;x<16;x++)for(int y=0;y<256;y++)for(int z=0;z<16;z++)empty&=chunk.getTypeId(x,y,z)==0;
            check(empty,"fringe chunks contain no generated structures");
        }
    }
    private static Set<String> flood(ChunkGenerator.ChunkData chunk,int startX,int startZ,int y){
        Set<String> visited=new HashSet<>();Queue<int[]> queue=new ArrayDeque<>();queue.add(new int[]{startX,startZ});
        while(!queue.isEmpty()){
            int[] p=queue.remove();int x=p[0],z=p[1];String key=x+":"+z;
            if(x<0||x>15||z<0||z>15||visited.contains(key)||chunk.getType(x,y,z)!=Material.AIR||chunk.getType(x,y+1,z)!=Material.AIR||!chunk.getType(x,y-1,z).isSolid())continue;
            visited.add(key);for(int[] delta:new int[][]{{0,1},{1,0},{0,-1},{-1,0}})queue.add(new int[]{x+delta[0],z+delta[1]});
        }return visited;
    }
    private static TestPlayer player(){TestPlayer player=new TestPlayer(new Location(worlds.get("world").api,100.25,65,100.75,33.5f,-19.25f));players.put(player.id,player);return player;}
    private static void click(TestPlayer player,Location location,EquipmentSlot hand){service.clicked(new PlayerInteractEvent(player.api,Action.RIGHT_CLICK_BLOCK,null,location.getBlock(),BlockFace.UP,hand));}
    private static void travel()throws Exception{
        TestPlayer player=player();Location origin=player.location.clone();
        check(service.enter(player.api),"entry succeeds");check(player.recordExistedAtEntry,"return record already exists inside teleport callback");
        check(service.isInside(player.api)&&store().read(player.id)!=null,"entry keeps return record");
        LiminalWorld.ReturnPoint first=store().read(player.id);check(!service.enter(player.api)&&store().read(player.id).x==first.x,"repeat entry does not replace original return");
        TestWorld dimension=worlds.get(LiminalWorld.WORLD_NAME);
        player.location=LiminalGenerator.arrival(dimension.api,0,0);click(player,LiminalGenerator.door(dimension.api,0,0),EquipmentSlot.HAND);
        equal(player.location,LiminalGenerator.arrival(dimension.api,5,2),"click crosses real graph edge to remote elevation");
        check(service.leave(player.api),"return ignores door cooldown");equal(player.location,origin,"exact original position, yaw and pitch restored");
        check(!store().exists(player.id),"record removed only after successful return");
        check(service.enter(player.api),"second trip starts");player.cancelTeleport=true;
        check(!service.leave(player.api)&&service.isInside(player.api)&&store().exists(player.id),"cancelled return retains escape record");
        player.cancelTeleport=false;check(service.leave(player.api),"cancelled return is retryable");
        player.cancelTeleport=true;check(!service.enter(player.api)&&!service.isInside(player.api)&&store().exists(player.id),"cancelled entry is recoverable");player.cancelTeleport=false;
        check(service.leave(player.api),"failed entry record can be cleared via return");
        player.location.setY(300);check(!service.enter(player.api),"invalid source coordinates deny entry without exception");player.location=origin.clone();
        primary=false;check(!service.enter(player.api),"async entry rejected");primary=true;
        Path blocked=data.resolve("liminal-returns-v1");Path parked=data.resolve("parked-returns");Files.move(blocked,parked);Files.write(blocked,new byte[]{0});
        try{check(!service.enter(player.api)&&!service.isInside(player.api),"disk save failure never teleports player");}
        finally{Files.delete(blocked);Files.move(parked,blocked);}
        player.location=LiminalGenerator.foyer(dimension.api);store().remove(player.id);
        check(service.leave(player.api)&&player.location.getWorld()==worlds.get("world").api,"orphaned player can always request fallback return");
        check(player.forbiddenStateChanges==0,"inventory/gamemode/XP state untouched");
        player.online=false;
    }
    private static void everyDoor(){
        TestWorld dimension=worlds.get(LiminalWorld.WORLD_NAME);
        for(int room=0;room<16;room++)for(int side=0;side<4;side++){
            TestPlayer player=player();Location origin=player.location.clone();check(service.enter(player.api),"door sweep enters with durable origin");
            player.location=LiminalGenerator.arrival(dimension.api,room,side);
            Location block=LiminalGenerator.door(dimension.api,room,side);if((room+side)%2==0)block.add(0,1,0);
            click(player,block,EquipmentSlot.HAND);
            equal(player.location,LiminalGenerator.arrival(dimension.api,LiminalGenerator.destination(room,side),LiminalGenerator.opposite(side)),"runtime door graph room="+room+" side="+side);
            check(service.leave(player.api),"every graph destination retains immediate escape");equal(player.location,origin,"every door preserves original return");
            check(player.forbiddenStateChanges==0,"every door retains player state");player.online=false;
        }
    }
    private static void authentication(){
        System.clearProperty("jaspr.biomes.fixture");TestPlayer absent=player();
        check(!service.enter(absent.api),"absent AuthMe fails closed");absent.online=false;
        auth=proxy(Plugin.class,(p,m,a)->m.getName().equals("isEnabled")?authEnabled:defaultValue(m.getReturnType()));
        TestPlayer player=player();AuthMeApi.authenticated=false;
        check(!service.enter(player.api)&&!store().exists(player.id),"unauthenticated entry blocked");
        AuthMeApi.authenticated=true;check(service.enter(player.api),"authenticated entry accepted");
        TestWorld dimension=worlds.get(LiminalWorld.WORLD_NAME);player.location=LiminalGenerator.arrival(dimension.api,0,0);Location before=player.location.clone();
        AuthMeApi.authenticated=false;click(player,LiminalGenerator.door(dimension.api,0,0),EquipmentSlot.HAND);equal(player.location,before,"direct door event independently guards authentication");
        check(!service.leave(player.api)&&store().exists(player.id),"unauthenticated exit does not bypass AuthMe");
        AuthMeApi.authenticated=true;click(player,LiminalGenerator.door(dimension.api,0,0),EquipmentSlot.OFF_HAND);equal(player.location,before,"off-hand duplicate does not travel");
        authEnabled=false;check(!service.leave(player.api),"disabled AuthMe fails closed");authEnabled=true;
        AuthMeApi.fail=true;check(!service.leave(player.api),"unavailable AuthMe API fails closed");AuthMeApi.fail=false;
        check(service.leave(player.api),"authenticated exit accepted");
        PlayerTeleportEvent teleport=new PlayerTeleportEvent(player.api,player.location,LiminalGenerator.foyer(dimension.api),PlayerTeleportEvent.TeleportCause.COMMAND);
        AuthMeApi.authenticated=false;service.travel(teleport);check(teleport.isCancelled(),"external teleports cannot bypass authentication");AuthMeApi.authenticated=true;
        teleport=new PlayerTeleportEvent(player.api,player.location,LiminalGenerator.foyer(dimension.api),PlayerTeleportEvent.TeleportCause.COMMAND);
        service.travel(teleport);check(!teleport.isCancelled()&&store().exists(player.id),"external entry also durably saves return");
        teleport=new PlayerTeleportEvent(player.api,player.location,new Location(dimension.api,100000,65,100000));service.travel(teleport);
        check(teleport.isCancelled(),"outside-boundary teleports rejected before chunk access");
        service.leave(player.api);player.online=false;auth=null;System.setProperty("jaspr.biomes.fixture","true");
    }
    private static void recovery()throws Exception{
        TestWorld dimension=worlds.get(LiminalWorld.WORLD_NAME),normal=worlds.get("world");
        TestPlayer player=player();Location origin=player.location.clone();check(service.enter(player.api),"restart trip enters");
        player.location=LiminalGenerator.arrival(dimension.api,12,1);Location room=player.location.clone();
        service.quit(new PlayerQuitEvent(player.api,""));service.stop();
        check(store().exists(player.id),"logout and stop retain durable return");
        service=new LiminalWorld(plugin(),HorrorPlugin::authenticated);service.start();tick();equal(player.location,room,"valid room retained through restart");
        check(service.leave(player.api),"return remains usable after restart");equal(player.location,origin,"restart return restores original orientation");
        check(service.enter(player.api),"corrupt record scenario enters");Files.write(record(player.id),"broken".getBytes(StandardCharsets.UTF_8));
        service.joined(new PlayerJoinEvent(player.api,""));tick();check(!service.isInside(player.api)&&!store().exists(player.id),"corrupt record joins recover to normal spawn");
        player.location=origin.clone();check(service.enter(player.api),"blocked return scenario enters");
        normal.overrides.put("100:65:100",Material.STONE);check(service.leave(player.api),"blocked origin finds a safe nearby return");
        check(player.location.getBlockX()!=100||player.location.getBlockY()!=65||player.location.getBlockZ()!=100,"does not put player inside obstruction");normal.overrides.clear();
        player.location=origin.clone();check(service.enter(player.api),"missing source world enters");
        TestWorld replacement=new TestWorld("world",null);worlds.put("world",replacement);
        check(service.leave(player.api),"reset source-world UUID falls back");equal(player.location,replacement.spawn,"fallback never reuses saved coordinates by world name");
        worlds.put("world",normal);player.location=origin.clone();check(service.enter(player.api),"missing-dimension scenario enters");
        // Simulate Paper recovering player data into its main-world spawn when the dimension is absent.
        player.location=normal.spawn.clone();service.joined(new PlayerJoinEvent(player.api,""));tick();equal(player.location,origin,"pending record restores origin after dimension missing at login");
        player.location=origin.clone();check(service.enter(player.api),"bad room login enters");player.location=new Location(dimension.api,8000,-50,8000);
        service.joined(new PlayerJoinEvent(player.api,""));tick();equal(player.location,LiminalGenerator.foyer(dimension.api),"out-of-bounds saved location is recovered without remote chunk loads");
        player.location=LiminalGenerator.arrival(dimension.api,0,0);
        PlayerMoveEvent move=new PlayerMoveEvent(player.api,player.location,new Location(dimension.api,-100,65,8));service.moved(move);
        equal(move.getTo(),LiminalGenerator.foyer(dimension.api),"walking/flying outside bounds returns to foyer");
        player.location=LiminalGenerator.foyer(dimension.api);click(player,LiminalGenerator.exitMarker(dimension.api),EquipmentSlot.HAND);
        check(!service.isInside(player.api),"foyer light provides command-independent escape");
        check(service.enter(player.api),"predicted no-op light click scenario enters");
        PlayerInteractEvent predicted=new PlayerInteractEvent(player.api,Action.RIGHT_CLICK_BLOCK,null,LiminalGenerator.exitMarker(dimension.api).getBlock(),BlockFace.UP,EquipmentSlot.HAND);
        predicted.setCancelled(true);service.clicked(predicted);check(!service.isInside(player.api),"vanilla no-op cancellation does not swallow the escape light");
        player.location=origin.clone();check(service.enter(player.api),"delayed-auth recovery scenario enters");player.location=normal.spawn.clone();
        auth=proxy(Plugin.class,(p,m,a)->m.getName().equals("isEnabled")?authEnabled:defaultValue(m.getReturnType()));
        System.clearProperty("jaspr.biomes.fixture");AuthMeApi.authenticated=false;service.joined(new PlayerJoinEvent(player.api,""));tick();
        equal(player.location,normal.spawn,"join recovery waits for authentication");check(store().exists(player.id),"auth wait retains saved return");
        AuthMeApi.authenticated=true;tick();equal(player.location,origin,"delayed authentication releases queued recovery");
        auth=null;System.setProperty("jaspr.biomes.fixture","true");
        player.online=false;
    }
    private static void terrainEpochs()throws Exception{
        TestWorld normal=worlds.get("world"),dimension=worlds.get(LiminalWorld.WORLD_NAME);
        worlds.put("world_nether",new TestWorld("world_nether",null,World.Environment.NETHER));
        worlds.put("world_the_end",new TestWorld("world_the_end",null,World.Environment.THE_END));
        int staleCases=0,freshCases=0,legacyCases=0;
        for(String name:new String[]{"world","world_nether","world_the_end"}){
            TestWorld source=worlds.get(name);Location origin=new Location(source.api,100.25,65,100.75,33.5f,-19.25f);
            Location room=LiminalGenerator.arrival(dimension.api,12,1);
            epoch("structures-v2");TestPlayer legacy=player();legacy.location=room.clone();legacyReturn(legacy,origin);
            byte[] legacyBytes=Files.readAllBytes(record(legacy.id));
            LiminalWorld.ReturnPoint loaded=store().read(legacy.id);
            check(loaded!=null&&loaded.world.equals(source.id)&&"structures-v2".equals(loaded.terrainEpoch),"version 1 remains readable as the legacy epoch for "+name);
            check(Arrays.equals(legacyBytes,Files.readAllBytes(record(legacy.id))),"legacy read does not migrate the record");
            check(service.leave(legacy.api),"legacy epoch exit succeeds for "+name);
            equal(legacy.location,origin,"version 1 exact return remains compatible before reset");
            check(legacy.forbiddenStateChanges==0,"legacy exit retains player state");legacy.online=false;legacyCases++;

            for(boolean versionOne:new boolean[]{true,false})for(boolean savedInside:new boolean[]{false,true}){
                epoch("structures-v2");TestPlayer player=player();player.location=origin.clone();
                check(service.enter(player.api),"pre-reset trip enters from "+name);
                if(versionOne)legacyReturn(player,origin);
                byte[] before=Files.readAllBytes(record(player.id));
                UUID savedWorld=store().read(player.id).world;
                player.location=room.clone();service.quit(new PlayerQuitEvent(player.api,""));service.stop();
                epoch("details-v3");
                // Paper/HorrorPlugin may restore the saved Fold player either inside it or at the new spawn.
                player.location=(savedInside?room:normal.spawn).clone();Location login=player.location.clone();
                player.cancelTeleport=true;int remoteReads=source.remoteBlockReads;
                service=new LiminalWorld(plugin());service.start();tick();
                equal(player.location,login,"epoch change retains saved Fold location or cancelled spawn recovery");
                check(savedWorld.equals(source.api.getUID()),"terrain reset retains the origin UUID for "+name);
                check(Arrays.equals(before,Files.readAllBytes(record(player.id))),"restart/auth recovery never rewrites old returns");
                check(!service.leave(player.api),"cancelled stale return reports failure");
                check(Arrays.equals(before,Files.readAllBytes(record(player.id))),"cancelled stale exit retains exact old record");
                check(source.remoteBlockReads==remoteReads,"stale return never probes valid pre-reset coordinates in "+name);
                player.cancelTeleport=false;
                if(savedInside)check(service.leave(player.api),"saved-inside-Fold stale return exits successfully");
                else tick();
                equal(player.location,normal.spawn,"changed epoch returns to normal spawn from "+name+" version1="+versionOne+" inside="+savedInside);
                check(!store().exists(player.id),"stale record removed only after confirmed exit");
                check(player.forbiddenStateChanges==0,"stale epoch recovery retains inventory/mode/XP");
                player.online=false;staleCases++;
            }

            for(String freshEpoch:new String[]{"details-v3","surface-v4","sparse-v5","rare-v6","rare-v7"}){
                epoch(freshEpoch);TestPlayer fresh=player();fresh.location=origin.clone();
                check(service.enter(fresh.api),"current-epoch trip enters from "+name+" at "+freshEpoch);
                Properties saved=properties(record(fresh.id));
                check("2".equals(saved.getProperty("version"))&&freshEpoch.equals(saved.getProperty("terrainEpoch")),"new return records explicitly persist "+freshEpoch);
                byte[] before=Files.readAllBytes(record(fresh.id));fresh.location=room.clone();restart();tick();
                equal(fresh.location,room,"current-epoch saved Fold room survives restart at "+freshEpoch);
                check(Arrays.equals(before,Files.readAllBytes(record(fresh.id))),"current-epoch restart does not rewrite the return at "+freshEpoch);
                check(service.leave(fresh.api),"current-epoch exit succeeds from "+name+" at "+freshEpoch);
                equal(fresh.location,origin,"fresh current-epoch return preserves exact dimension/coordinates/orientation at "+freshEpoch);
                check(!store().exists(fresh.id)&&fresh.forbiddenStateChanges==0,"fresh "+freshEpoch+" return completes without player-state edits");
                fresh.online=false;freshCases++;
            }
        }
        TestPlayer waiting=player();Location oldOrigin=waiting.location.clone();
        waiting.location=LiminalGenerator.foyer(dimension.api);legacyReturn(waiting,oldOrigin);
        byte[] pending=Files.readAllBytes(record(waiting.id));int remoteReads=normal.remoteBlockReads;
        auth=proxy(Plugin.class,(p,m,a)->m.getName().equals("isEnabled")?authEnabled:defaultValue(m.getReturnType()));
        System.clearProperty("jaspr.biomes.fixture");AuthMeApi.authenticated=false;
        service.joined(new PlayerJoinEvent(waiting.api,""));tick();
        check(!service.leave(waiting.api)&&Arrays.equals(pending,Files.readAllBytes(record(waiting.id))),"stale epoch cannot bypass authentication or discard its record");
        AuthMeApi.authenticated=true;System.setProperty("jaspr.biomes.fixture","true");auth=null;
        worlds.remove("world");
        try{check(!service.leave(waiting.api)&&Arrays.equals(pending,Files.readAllBytes(record(waiting.id))),"missing normal-world spawn retains the stale escape record");}
        finally{worlds.put("world",normal);}
        // Obstruct every possible fallback landing, including the bounded search above/below spawn.
        for(int x=-9;x<=9;x++)for(int z=-9;z<=9;z++)for(int y=58;y<=73;y++)normal.overrides.put(x+":"+y+":"+z,Material.STONE);
        try{check(!service.leave(waiting.api)&&Arrays.equals(pending,Files.readAllBytes(record(waiting.id))),"unsafe normal spawn never consumes a stale return");}
        finally{normal.overrides.clear();}
        waiting.noArrival=true;
        check(!service.leave(waiting.api)&&Arrays.equals(pending,Files.readAllBytes(record(waiting.id))),"reported teleport success without actual arrival retains the old record");
        waiting.noArrival=false;
        check(service.leave(waiting.api),"blocked stale return can retry once normal spawn is available");
        equal(waiting.location,normal.spawn,"delayed stale exit still uses safe normal spawn");
        check(normal.remoteBlockReads==remoteReads&&waiting.forbiddenStateChanges==0,"failed stale recovery never inspects old coordinates or edits player state");
        waiting.online=false;

        TestPlayer unknown=player();epoch("unrecognized-epoch");
        check(!service.enter(unknown.api)&&!store().exists(unknown.id),"unknown current epoch denies a new return write and entry");
        unknown.location=LiminalGenerator.foyer(dimension.api);legacyReturn(unknown,oldOrigin);pending=Files.readAllBytes(record(unknown.id));
        unknown.cancelTeleport=true;
        check(!service.leave(unknown.api)&&Arrays.equals(pending,Files.readAllBytes(record(unknown.id))),"unknown current epoch preserves a cancelled escape record");
        unknown.cancelTeleport=false;check(service.leave(unknown.api),"unknown epoch still permits a safe normal-world escape");
        equal(unknown.location,normal.spawn,"unknown epoch never trusts saved coordinates");
        check(unknown.forbiddenStateChanges==0,"unknown epoch fallback retains player state");unknown.online=false;
        Files.delete(data.resolve("terrain-epoch.txt"));
        System.out.println("LIMINAL_EPOCHS_OK stale="+staleCases+" fresh="+freshCases+" legacy="+legacyCases);
    }
    private static void protection(){
        TestWorld dimension=worlds.get(LiminalWorld.WORLD_NAME);TestPlayer player=player();
        BlockBreakEvent foyerBreak=new BlockBreakEvent(dimension.api.getBlockAt(8,64,8),player.api);service.broken(foyerBreak);check(foyerBreak.isCancelled(),"foyer protected");
        BlockBreakEvent roomBreak=new BlockBreakEvent(dimension.api.getBlockAt(24,96,8),player.api);service.broken(roomBreak);check(!roomBreak.isCancelled(),"exploration room can be mined to escape");
        BlockBreakEvent ordinaryBreak=new BlockBreakEvent(worlds.get("world").api.getBlockAt(8,64,8),player.api);service.broken(ordinaryBreak);check(!ordinaryBreak.isCancelled(),"normal world unaffected");
        player.online=false;
    }

    private static final class TestWorld {
        final String name;final UUID id=UUID.randomUUID();final ChunkGenerator generator;final World.Environment environment;World api;
        final Map<String,ChunkGenerator.ChunkData> chunks=new HashMap<>();final Map<String,Material> overrides=new HashMap<>();
        Location spawn;boolean keepSpawn=true;double borderSize=59999968,borderX=0,borderZ=0;int remoteBlockReads;
        TestWorld(String name,ChunkGenerator generator){this(name,generator,World.Environment.NORMAL);}
        TestWorld(String name,ChunkGenerator generator,World.Environment environment){
            this.name=name;this.generator=generator;this.environment=environment;
            WorldBorder border=proxy(WorldBorder.class,(p,m,a)->{
                switch(m.getName()){
                    case "setCenter":borderX=(Double)a[0];borderZ=(Double)a[1];return null;case "setSize":borderSize=(Double)a[0];return null;
                    case "getSize":return borderSize;case "getCenter":return new Location(api,borderX,0,borderZ);
                    case "isInside":{Location at=(Location)a[0];return Math.abs(at.getX()-borderX)<borderSize/2&&Math.abs(at.getZ()-borderZ)<borderSize/2;}
                    default:return defaultValue(m.getReturnType());
                }
            });
            api=proxy(World.class,(p,m,a)->{
                switch(m.getName()){
                    case "getName":return name;case "getUID":return id;case "getEnvironment":return environment;case "getGenerator":return generator;
                    case "getWorldBorder":return border;case "getMaxHeight":return 256;case "getSeed":return 37L;
                    case "getSpawnLocation":return spawn.clone();case "setSpawnLocation":spawn=new Location(api,(Integer)a[0],(Integer)a[1],(Integer)a[2]);return true;
                    case "setKeepSpawnInMemory":keepSpawn=(Boolean)a[0];return null;case "getKeepSpawnInMemory":return keepSpawn;
                    case "getBlockAt":{if(a[0] instanceof Location){Location at=(Location)a[0];return block(at.getBlockX(),at.getBlockY(),at.getBlockZ());}return block((Integer)a[0],(Integer)a[1],(Integer)a[2]);}
                    case "getHighestBlockYAt":return 64;
                    default:return defaultValue(m.getReturnType());
                }
            });spawn=new Location(api,.5,65,.5);
        }
        ChunkGenerator.ChunkData chunk(int cx,int cz){
            String key=cx+":"+cz;ChunkGenerator.ChunkData data=chunks.get(key);if(data==null){
                ChunkGenerator.BiomeGrid grid=proxy(ChunkGenerator.BiomeGrid.class,(p,m,a)->m.getName().equals("getBiome")?Biome.PLAINS:defaultValue(m.getReturnType()));
                data=generator.generateChunkData(api,new Random(0),cx,cz,grid);chunks.put(key,data);
            }return data;
        }
        Material material(int x,int y,int z){
            Material override=overrides.get(x+":"+y+":"+z);if(override!=null)return override;
            if(generator==null)return y<=64?Material.STONE:Material.AIR;
            // Safety checks must reject bad coordinates before any chunk/block lookup.
            check(x>=0&&x<64&&z>=0&&z<64,"dimension block access remains within finite footprint");
            return chunk(x>>4,z>>4).getType(x&15,y,z&15);
        }
        Block block(int x,int y,int z){if(Math.abs(x)>32||Math.abs(z)>32)remoteBlockReads++;return proxy(Block.class,(p,m,a)->{
            switch(m.getName()){
                case "getWorld":return api;case "getLocation":return new Location(api,x,y,z);case "getX":return x;case "getY":return y;case "getZ":return z;
                case "getType":return material(x,y,z);case "getTypeId":return material(x,y,z).getId();case "setType":overrides.put(x+":"+y+":"+z,(Material)a[0]);return null;
                case "getRelative":{BlockFace face=(BlockFace)a[0];return block(x+face.getModX(),y+face.getModY(),z+face.getModZ());}
                case "getState":return proxy(Sign.class,(p2,m2,a2)->defaultValue(m2.getReturnType()));
                default:return defaultValue(m.getReturnType());
            }
        });}
    }
    private static final class TestPlayer {
        final UUID id=UUID.randomUUID();Player api;Location location;boolean online=true,cancelTeleport,noArrival,recordExistedAtEntry;int forbiddenStateChanges;
        TestPlayer(Location initial){location=initial;
            api=proxy(Player.class,(p,m,a)->{
                switch(m.getName()){
                    case "getUniqueId":return id;case "getName":return "LiminalTester";case "isOnline":return online;
                    case "getWorld":return location.getWorld();case "getLocation":return location.clone();
                    case "teleport":{
                        Location destination=(Location)a[0];if(service.isInside(destination.getWorld())&&!service.isInside(location.getWorld()))recordExistedAtEntry=store().read(id)!=null;
                        PlayerTeleportEvent event=new PlayerTeleportEvent(api,location.clone(),destination.clone(),PlayerTeleportEvent.TeleportCause.PLUGIN);service.travel(event);
                        if(cancelTeleport||event.isCancelled())return false;if(!noArrival)location=event.getTo().clone();return true;
                    }
                    case "getInventory":case "getEnderChest":case "setGameMode":case "setExp":case "setLevel":case "setTotalExperience":case "setHealth":case "setFoodLevel":case "setGlowing":case "loadData":case "saveData":
                        forbiddenStateChanges++;throw new AssertionError("Unexpected player-state access: "+m.getName());
                    default:return defaultValue(m.getReturnType());
                }
            });
        }
    }
}
