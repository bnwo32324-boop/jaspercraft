package chat.jaspr.biomes;

import java.io.*;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.function.Predicate;
import java.util.logging.Logger;
import org.bukkit.*;
import org.bukkit.block.*;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.block.*;
import org.bukkit.event.entity.*;
import org.bukkit.event.player.*;
import org.bukkit.event.world.WorldInitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;
import org.spigotmc.event.player.PlayerSpawnLocationEvent;

/** A real Bukkit NORMAL world (its own uid.dat), using only the 1.12.2 protocol environments.
 * Integrator owns commands/entrance detection: call start/stop, enter and leave on the main thread.
 * This class owns interior interactions and recovery. It never edits inventory, XP or game mode. */
public final class LiminalWorld implements Listener {
    public static final String WORLD_NAME="jaspr_backrooms";
    private static final String LEGACY_TERRAIN_EPOCH="structures-v2";
    private final Plugin plugin;
    private final Predicate<Player> authentication;
    private final ReturnStore returns;
    private final Set<UUID> recovering=new HashSet<>(),transferring=new HashSet<>();
    private final Map<UUID,Long> lastDoor=new HashMap<>();
    private BukkitTask task;
    private World world;
    private boolean running,creating;

    public LiminalWorld(HorrorPlugin plugin){this((Plugin)plugin,HorrorPlugin::authenticated);}
    LiminalWorld(Plugin plugin){this(plugin,HorrorPlugin::authenticated);}
    // Explicit, package-private authentication seam for synthetic tests only; no implicit permissive fallback.
    LiminalWorld(Plugin plugin,Predicate<Player> authentication){
        this.plugin=Objects.requireNonNull(plugin);this.authentication=Objects.requireNonNull(authentication);
        returns=new ReturnStore(new File(plugin.getDataFolder(),"liminal-returns-v1"),plugin.getLogger());
    }

    /** Idempotent. World creation occurs on a later tick, after STARTUP plugins/world initialization. */
    public void start(){
        if(running)return;
        if(!Bukkit.isPrimaryThread())throw new IllegalStateException("LiminalWorld requires the server thread");
        running=true;Bukkit.getPluginManager().registerEvents(this,plugin);
        for(Player player:Bukkit.getOnlinePlayers())queueRecovery(player);
        task=Bukkit.getScheduler().runTaskTimer(plugin,this::tick,1,20);
    }
    /** Does not teleport, unload the world, clear durable returns or modify player state on shutdown. */
    public void stop(){
        running=false;if(task!=null){task.cancel();task=null;}
        HandlerList.unregisterAll(this);recovering.clear();transferring.clear();lastDoor.clear();world=null;
    }
    public boolean isInside(Player player){return player!=null&&isInside(player.getWorld());}
    public boolean isInside(World candidate){return candidate!=null&&WORLD_NAME.equals(candidate.getName());}

    /** True only after confirmed arrival. Failed authentication/save/teleport leaves the player outside. */
    public boolean enter(Player player){
        if(!ready(player))return false;
        if(isInside(player)){hint(player);return false;}
        World target=ensureWorld();if(target==null){player.sendMessage(ChatColor.GRAY+"The threshold is not ready. Try again shortly.");return false;}
        if(!saveReturn(player.getUniqueId(),player.getLocation())){
            player.sendMessage(ChatColor.RED+"Your return point could not be saved. Entry cancelled.");return false;
        }
        boolean arrived=teleport(player,LiminalGenerator.foyer(target));
        if(arrived){recovering.remove(player.getUniqueId());hint(player);}
        return arrived;
    }
    /** /wasteland return: valid even with a missing record/world; false when outside with no pending trip.
     * Failed/cancelled teleports retain the record. No entry/door cooldown applies to escape. */
    public boolean leave(Player player){
        if(!ready(player)||(!isInside(player)&&!returns.exists(player.getUniqueId())))return false;
        ReturnPoint point=returns.read(player.getUniqueId());Location destination=returnLocation(point);
        if(destination==null){player.sendMessage(ChatColor.RED+"No safe normal-world spawn is available yet. Your return point is retained; retry /wasteland return.");return false;}
        if(!teleport(player,destination)||isInside(player))return false;
        returns.remove(player.getUniqueId());recovering.remove(player.getUniqueId());lastDoor.remove(player.getUniqueId());
        player.sendMessage(ChatColor.GRAY+"You are back. Your belongings and game mode are unchanged.");return true;
    }
    private boolean ready(Player player){return running&&Bukkit.isPrimaryThread()&&player!=null&&player.isOnline()&&!player.isDead()&&authenticated(player);}
    private boolean saveReturn(UUID player,Location origin){
        String epoch=currentTerrainEpoch();if(epoch==null)return false;
        try{return returns.write(player,ReturnPoint.from(origin,epoch));}
        catch(IllegalArgumentException ex){plugin.getLogger().warning("LIMINAL_RETURN_REJECTED "+player+": invalid source location");return false;}
    }
    private static boolean validTerrainEpoch(String epoch){return LEGACY_TERRAIN_EPOCH.equals(epoch)||"details-v3".equals(epoch)||"surface-v4".equals(epoch)||"sparse-v5".equals(epoch)||"rare-v6".equals(epoch)||"rare-v7".equals(epoch);}
    private String currentTerrainEpoch(){
        try{
            String epoch;
            if(plugin instanceof HorrorPlugin)epoch=((HorrorPlugin)plugin).terrainEpoch();
            else{
                // Preserve the Plugin-based fixture constructors without inventing a separate epoch authority.
                Path file=plugin.getDataFolder().toPath().resolve("terrain-epoch.txt");
                try{epoch=new String(Files.readAllBytes(file),StandardCharsets.UTF_8).trim();}
                catch(NoSuchFileException absent){epoch=LEGACY_TERRAIN_EPOCH;}
            }
            if(!validTerrainEpoch(epoch))throw new IOException("Unsupported terrain epoch");
            return epoch;
        }catch(IOException|RuntimeException ex){
            plugin.getLogger().warning("LIMINAL_TERRAIN_EPOCH_UNAVAILABLE: "+ex.getClass().getSimpleName());return null;
        }
    }
    private boolean authenticated(Player player){
        try{return authentication.test(player);}catch(LinkageError|RuntimeException ex){return false;}
    }
    private World ensureWorld(){
        if(!running||creating||!Bukkit.isPrimaryThread())return null;
        if(world!=null&&Bukkit.getWorld(world.getUID())==world)return world;
        // Never create a dimension synchronously during load: STARTUP may have no overworld yet.
        if(normalWorld()==null)return null;
        creating=true;
        try{
            World candidate=Bukkit.getWorld(WORLD_NAME);
            if(candidate==null)candidate=new WorldCreator(WORLD_NAME).environment(World.Environment.NORMAL)
                    .generator(new LiminalGenerator()).generateStructures(false).createWorld();
            if(candidate==null)return null;
            if(!(candidate.getGenerator() instanceof LiminalGenerator)){
                plugin.getLogger().severe("LIMINAL_WORLD_CONFLICT: "+WORLD_NAME+" is not using LiminalGenerator");return null;
            }
            candidate.setKeepSpawnInMemory(false);candidate.setSpawnFlags(false,false);
            candidate.setPVP(false);candidate.setStorm(false);candidate.setThundering(false);candidate.setTime(18000);
            candidate.setGameRuleValue("doMobSpawning","false");candidate.setGameRuleValue("doDaylightCycle","false");
            candidate.setGameRuleValue("doWeatherCycle","false");candidate.setGameRuleValue("doFireTick","false");
            candidate.getWorldBorder().setCenter(LiminalGenerator.SIZE/2.0,LiminalGenerator.SIZE/2.0);
            candidate.getWorldBorder().setSize(LiminalGenerator.SIZE);candidate.getWorldBorder().setWarningDistance(0);
            candidate.getWorldBorder().setDamageAmount(0);
            prepareFoyer(candidate);candidate.setSpawnLocation(8,65,8);world=candidate;
            return world;
        }catch(RuntimeException ex){plugin.getLogger().severe("LIMINAL_WORLD_FAILED: "+ex.getClass().getSimpleName());return null;}
        finally{creating=false;}
    }
    private void prepareFoyer(World target){
        // Only the protected arrival pad is repaired. No room-wide terrain reset on reload/entry.
        for(int x=7;x<=9;x++)for(int z=7;z<=9;z++){
            target.getBlockAt(x,64,z).setType(Material.WOOD,false);
            target.getBlockAt(x,65,z).setType(Material.AIR,false);target.getBlockAt(x,66,z).setType(Material.AIR,false);
        }
        target.getBlockAt(8,65,5).setType(Material.SEA_LANTERN,false);
        Block sign=target.getBlockAt(8,66,5);sign.setType(Material.SIGN_POST,false);sign.setData((byte)0,false);
        BlockState state=sign.getState();if(state instanceof Sign){
            Sign text=(Sign)state;text.setLine(0,ChatColor.DARK_RED+"ROOMS DO NOT FIT");text.setLine(1,"Right-click light");
            text.setLine(2,"/wasteland return");text.setLine(3,"Always an exit");text.update(true,false);
        }
    }
    private void hint(Player player){player.sendMessage(ChatColor.YELLOW+"The doors fold space. Right-click an iron door to cross. The foyer light returns you; /wasteland return works anywhere here.");}
    private boolean teleport(Player player,Location destination){
        UUID id=player.getUniqueId();if(!transferring.add(id))return false;
        try{
            if(!player.teleport(destination,PlayerTeleportEvent.TeleportCause.PLUGIN))return false;
            Location actual=player.getLocation();
            if(actual.getWorld()!=destination.getWorld()||actual.distanceSquared(destination)>1)return false;
            player.setFallDistance(0);player.setVelocity(new Vector());return true;
        }finally{transferring.remove(id);}
    }
    private World normalWorld(){
        World normal=Bukkit.getWorld("world");if(normal!=null&&normal.getEnvironment()==World.Environment.NORMAL&&!isInside(normal))return normal;
        for(World candidate:Bukkit.getWorlds())if(candidate.getEnvironment()==World.Environment.NORMAL&&!isInside(candidate))return candidate;
        return null;
    }
    private Location returnLocation(ReturnPoint saved){
        // A terrain reset preserves UUIDs. Reject old coordinates BEFORE any world/block lookup.
        // An unknown current epoch also takes the safe-spawn path; the record remains until confirmed exit.
        if(saved!=null&&saved.terrainEpoch.equals(currentTerrainEpoch())){
            World original=Bukkit.getWorld(saved.world);
            if(original!=null&&!isInside(original)){
                Location exact=saved.location(original);Location safe=findSafe(exact,4);
                if(safe!=null)return safe;
            }
        }
        World normal=normalWorld();if(normal==null)return null;
        Location spawn=normal.getSpawnLocation();Location safe=findSafe(spawn,8);if(safe!=null)return safe;
        // Search only a bounded 17x17 patch around spawn, including cave/roof height changes.
        for(int radius=0;radius<=8;radius++)for(int dx=-radius;dx<=radius;dx++)for(int dz=-radius;dz<=radius;dz++){
            if(Math.max(Math.abs(dx),Math.abs(dz))!=radius)continue;
            Location column=spawn.clone().add(dx,0,dz);if(!reasonable(column))continue;
            column.setX(column.getBlockX()+.5);column.setZ(column.getBlockZ()+.5);
            column.setY(normal.getHighestBlockYAt(column.getBlockX(),column.getBlockZ())+1);
            safe=findSafe(column,0);if(safe!=null)return safe;
        }
        return null; // Never put a player in lava/void just to report a successful return.
    }
    private Location findSafe(Location origin,int radius){
        if(!reasonable(origin))return null;
        if(safeFeet(origin))return origin.clone();
        for(int ring=0;ring<=radius;ring++)for(int dx=-ring;dx<=ring;dx++)for(int dz=-ring;dz<=ring;dz++){
            if(Math.max(Math.abs(dx),Math.abs(dz))!=ring)continue;
            for(int delta=0;delta<=6;delta++)for(int sign:delta==0?new int[]{1}:new int[]{1,-1}){
                Location at=origin.clone();at.setX(origin.getBlockX()+dx+.5);at.setZ(origin.getBlockZ()+dz+.5);at.setY(origin.getBlockY()+delta*sign);
                if(isInside(at.getWorld())&&LiminalGenerator.roomAt(at)!=LiminalGenerator.roomAt(origin))continue;
                if(safeFeet(at))return at;
            }
        }
        return null;
    }
    private boolean reasonable(Location at){
        return at!=null&&at.getWorld()!=null&&Double.isFinite(at.getX())&&Double.isFinite(at.getY())&&Double.isFinite(at.getZ())
                &&Float.isFinite(at.getYaw())&&Float.isFinite(at.getPitch())&&Math.abs(at.getX())<29999980&&Math.abs(at.getZ())<29999980
                &&at.getY()>=1&&at.getY()<at.getWorld().getMaxHeight()-2
                &&at.getWorld().getWorldBorder().isInside(at)&&(!isInside(at.getWorld())||LiminalGenerator.inBounds(at));
    }
    private boolean safeFeet(Location at){
        if(!reasonable(at))return false;
        // Full player footprint, not just the block containing its center. Retain fractional coordinates.
        int bottom=(int)Math.floor(at.getY()),top=(int)Math.floor(at.getY()+1.8);
        for(int x=(int)Math.floor(at.getX()-.3);x<=(int)Math.floor(at.getX()+.3);x++)
            for(int z=(int)Math.floor(at.getZ()-.3);z<=(int)Math.floor(at.getZ()+.3);z++){
                Material floor=at.getWorld().getBlockAt(x,bottom-1,z).getType();
                if(!floor.isSolid()||floor.isTransparent()||floor==Material.MAGMA||floor==Material.CACTUS)return false;
                for(int y=bottom;y<=top;y++)if(at.getWorld().getBlockAt(x,y,z).getType()!=Material.AIR)return false;
            }
        return true;
    }
    private void queueRecovery(Player player){if(isInside(player)||returns.exists(player.getUniqueId()))recovering.add(player.getUniqueId());}
    private void tick(){
        if(!running)return;
        ensureWorld();
        for(UUID id:new ArrayList<>(recovering)){
            Player player=Bukkit.getPlayer(id);if(player==null||!player.isOnline()){recovering.remove(id);continue;}
            if(!ready(player))continue; // AuthMe may finish seconds/minutes after PlayerJoinEvent.
            if(!isInside(player)||returns.read(id)==null){leave(player);continue;}
            if(!safeFeet(player.getLocation())){
                World target=ensureWorld();if(target==null){leave(player);continue;}
                if(!teleport(player,LiminalGenerator.foyer(target)))continue;
            }
            recovering.remove(id);hint(player);
        }
    }
    @EventHandler(priority=EventPriority.MONITOR) public void joined(PlayerJoinEvent event){queueRecovery(event.getPlayer());}
    @EventHandler(priority=EventPriority.HIGHEST) public void initialized(WorldInitEvent event){
        // CraftServer fires this BEFORE its spawn-region preload loop (WorldCreator has no flag in 1.12).
        if(isInside(event.getWorld())&&event.getWorld().getGenerator() instanceof LiminalGenerator)event.getWorld().setKeepSpawnInMemory(false);
    }
    @EventHandler public void quit(PlayerQuitEvent event){UUID id=event.getPlayer().getUniqueId();recovering.remove(id);lastDoor.remove(id);}
    @EventHandler(priority=EventPriority.HIGHEST) public void spawn(PlayerSpawnLocationEvent event){
        // This only normalizes a spawn within the same dimension. Authentication still controls travel.
        if(isInside(event.getSpawnLocation().getWorld())&&!safeFeet(event.getSpawnLocation())){
            World target=ensureWorld();if(target!=null)event.setSpawnLocation(LiminalGenerator.foyer(target));
        }
    }
    @EventHandler(priority=EventPriority.MONITOR) public void respawn(PlayerRespawnEvent event){
        // Keep ordinary death/inventory rules; recover a pending trip after normal respawn/login settles.
        if(isInside(event.getPlayer())||returns.exists(event.getPlayer().getUniqueId()))recovering.add(event.getPlayer().getUniqueId());
    }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void travel(PlayerTeleportEvent event){
        if(event.getTo()==null||!isInside(event.getTo().getWorld()))return;
        Player player=event.getPlayer();
        if(!ready(player)||ensureWorld()==null||!safeFeet(event.getTo())){event.setCancelled(true);return;}
        // Covers other plugins/admin teleports into this dimension as well as this class's entry path.
        if(!isInside(event.getFrom().getWorld())&&!transferring.contains(player.getUniqueId())
                &&!saveReturn(player.getUniqueId(),event.getFrom()))event.setCancelled(true);
    }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void portal(PlayerPortalEvent event){if(isInside(event.getFrom().getWorld()))event.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void moved(PlayerMoveEvent event){
        if(event instanceof PlayerTeleportEvent||!isInside(event.getPlayer())||event.getTo()==null)return;
        if(!LiminalGenerator.inBounds(event.getTo())){
            if(!ready(event.getPlayer())){event.setCancelled(true);return;}
            World target=ensureWorld();if(target!=null){event.setTo(LiminalGenerator.foyer(target));event.getPlayer().setFallDistance(0);}
            else event.setCancelled(true);
        }
    }
    @EventHandler(priority=EventPriority.HIGHEST) public void clicked(PlayerInteractEvent event){
        // Vanilla may predict a no-op (cancelled event) for an empty-hand click on the return light.
        // Keep that interaction cancelled, but independently authorize our dimension travel below.
        if(event.getHand()!=EquipmentSlot.HAND||event.getAction()!=Action.RIGHT_CLICK_BLOCK||!isInside(event.getPlayer()))return;
        Block block=event.getClickedBlock();if(block==null)return;
        Location at=block.getLocation();Player player=event.getPlayer();
        boolean exit=at.getBlockX()==8&&at.getBlockZ()==5&&(at.getBlockY()==65||at.getBlockY()==66);
        int side=LiminalGenerator.doorAt(at);
        if(!exit&&(side<0||block.getType()!=Material.IRON_DOOR_BLOCK))return;
        event.setCancelled(true);
        // Direct event guard: never trust an unauthenticated right-click or off-hand duplicate.
        if(!ready(player)||player.getLocation().distanceSquared(at.clone().add(.5,.5,.5))>36)return;
        if(exit){leave(player);return;}
        long now=System.nanoTime();Long last=lastDoor.get(player.getUniqueId());if(last!=null&&now-last<700000000L)return;
        int room=(at.getBlockZ()>>4)*LiminalGenerator.SIDE+(at.getBlockX()>>4);
        int next=LiminalGenerator.destination(room,side);
        Location destination=findSafe(LiminalGenerator.arrival(player.getWorld(),next,LiminalGenerator.opposite(side)),3);
        if(destination==null){player.sendMessage(ChatColor.GRAY+"That room is obstructed. Another door or /wasteland return will get you out.");return;}
        if(teleport(player,destination))lastDoor.put(player.getUniqueId(),now);
    }
    private boolean foyer(Location at){return at!=null&&isInside(at.getWorld())&&at.getBlockX()>=0&&at.getBlockX()<16&&at.getBlockZ()>=0&&at.getBlockZ()<16;}
    @EventHandler(ignoreCancelled=true) public void broken(BlockBreakEvent event){if(foyer(event.getBlock().getLocation()))event.setCancelled(true);}
    @EventHandler(ignoreCancelled=true) public void placed(BlockPlaceEvent event){if(foyer(event.getBlock().getLocation()))event.setCancelled(true);}
    @EventHandler(ignoreCancelled=true) public void bucket(PlayerBucketEmptyEvent event){if(foyer(event.getBlockClicked().getRelative(event.getBlockFace()).getLocation()))event.setCancelled(true);}
    @EventHandler(ignoreCancelled=true) public void flow(BlockFromToEvent event){if(foyer(event.getToBlock().getLocation()))event.setCancelled(true);}
    @EventHandler(ignoreCancelled=true) public void burn(BlockBurnEvent event){if(foyer(event.getBlock().getLocation()))event.setCancelled(true);}
    @EventHandler(ignoreCancelled=true) public void ignite(BlockIgniteEvent event){if(foyer(event.getBlock().getLocation()))event.setCancelled(true);}
    @EventHandler(ignoreCancelled=true) public void change(EntityChangeBlockEvent event){if(foyer(event.getBlock().getLocation()))event.setCancelled(true);}
    @EventHandler(ignoreCancelled=true) public void blast(EntityExplodeEvent event){event.blockList().removeIf(block->foyer(block.getLocation()));}
    @EventHandler(ignoreCancelled=true) public void blast(BlockExplodeEvent event){event.blockList().removeIf(block->foyer(block.getLocation()));}
    @EventHandler(ignoreCancelled=true) public void hurt(EntityDamageEvent event){if(event.getEntity() instanceof Player&&foyer(event.getEntity().getLocation()))event.setCancelled(true);}
    @EventHandler(ignoreCancelled=true) public void creature(CreatureSpawnEvent event){if(foyer(event.getLocation()))event.setCancelled(true);}
    @EventHandler(ignoreCancelled=true) public void extend(BlockPistonExtendEvent event){
        if(foyer(event.getBlock().getLocation())||foyer(event.getBlock().getRelative(event.getDirection()).getLocation())){event.setCancelled(true);return;}
        for(Block block:event.getBlocks())if(foyer(block.getLocation())||foyer(block.getRelative(event.getDirection()).getLocation())){event.setCancelled(true);break;}
    }
    @EventHandler(ignoreCancelled=true) public void retract(BlockPistonRetractEvent event){
        if(foyer(event.getBlock().getLocation())){event.setCancelled(true);return;}
        for(Block block:event.getBlocks())if(foyer(block.getLocation())||foyer(block.getRelative(event.getDirection()).getLocation())){event.setCancelled(true);break;}
    }

    static final class ReturnPoint {
        final UUID world;final double x,y,z;final float yaw,pitch;final String terrainEpoch;
        ReturnPoint(UUID world,double x,double y,double z,float yaw,float pitch){
            this(world,x,y,z,yaw,pitch,LEGACY_TERRAIN_EPOCH);
        }
        ReturnPoint(UUID world,double x,double y,double z,float yaw,float pitch,String terrainEpoch){
            if(world==null||!Double.isFinite(x)||!Double.isFinite(y)||!Double.isFinite(z)||!Float.isFinite(yaw)||!Float.isFinite(pitch)
                    ||Math.abs(x)>=29999980||Math.abs(z)>=29999980||y<1||y>254)throw new IllegalArgumentException("Invalid return coordinates");
            if(!validTerrainEpoch(terrainEpoch))throw new IllegalArgumentException("Invalid return terrain epoch");
            this.world=world;this.x=x;this.y=y;this.z=z;this.yaw=yaw;this.pitch=pitch;this.terrainEpoch=terrainEpoch;
        }
        static ReturnPoint from(Location at){return from(at,LEGACY_TERRAIN_EPOCH);}
        static ReturnPoint from(Location at,String epoch){return new ReturnPoint(at.getWorld().getUID(),at.getX(),at.getY(),at.getZ(),at.getYaw(),at.getPitch(),epoch);}
        Location location(World target){return new Location(target,x,y,z,yaw,pitch);}
    }
    /** Per-player atomic records keep one damaged file from discarding other players' escape paths. */
    static final class ReturnStore {
        private final Path directory;private final Logger logger;
        ReturnStore(File directory,Logger logger){this.directory=directory.toPath();this.logger=logger;}
        private Path file(UUID player){return directory.resolve(player+".properties");}
        boolean exists(UUID player){return Files.exists(file(player));}
        boolean write(UUID player,ReturnPoint point){
            Path temp=null;
            try{
                Files.createDirectories(directory);Properties p=new Properties();p.setProperty("version","2");p.setProperty("player",player.toString());
                p.setProperty("terrainEpoch",point.terrainEpoch);
                p.setProperty("world",point.world.toString());p.setProperty("x",Double.toString(point.x));p.setProperty("y",Double.toString(point.y));p.setProperty("z",Double.toString(point.z));
                p.setProperty("yaw",Float.toString(point.yaw));p.setProperty("pitch",Float.toString(point.pitch));
                temp=Files.createTempFile(directory,player+"-",".pending");
                try(FileOutputStream out=new FileOutputStream(temp.toFile())){p.store(out,"Liminal return: saved BEFORE entry");out.flush();out.getChannel().force(true);}
                // Same directory/volume. If atomic replacement is unsupported, deny entry rather than risk the saved return.
                Files.move(temp,file(player),StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);
                forceDirectory();return true;
            }catch(IOException|RuntimeException ex){logger.severe("LIMINAL_RETURN_SAVE_FAILED "+player+": "+ex.getClass().getSimpleName());return false;}
            finally{if(temp!=null)try{Files.deleteIfExists(temp);}catch(IOException ignored){}}
        }
        ReturnPoint read(UUID player){
            if(!exists(player))return null;
            try(InputStream in=Files.newInputStream(file(player))){
                Properties p=new Properties();p.load(in);
                String version=p.getProperty("version");
                if(!("1".equals(version)||"2".equals(version))||!player.toString().equals(p.getProperty("player")))throw new IOException("Invalid return record");
                String epoch="1".equals(version)?LEGACY_TERRAIN_EPOCH:p.getProperty("terrainEpoch");
                return new ReturnPoint(UUID.fromString(p.getProperty("world")),Double.parseDouble(p.getProperty("x")),Double.parseDouble(p.getProperty("y")),
                        Double.parseDouble(p.getProperty("z")),Float.parseFloat(p.getProperty("yaw")),Float.parseFloat(p.getProperty("pitch")),epoch);
            }catch(IOException|RuntimeException ex){logger.warning("LIMINAL_RETURN_INVALID "+player+": using safe normal-world spawn");return null;}
        }
        void remove(UUID player){try{Files.deleteIfExists(file(player));forceDirectory();}catch(IOException ex){logger.warning("LIMINAL_RETURN_CLEANUP_FAILED "+player);}}
        private void forceDirectory(){
            // POSIX supports directory fsync; Windows denies directory channels but the file was forced above.
            try(FileChannel channel=FileChannel.open(directory,StandardOpenOption.READ)){channel.force(true);}catch(IOException|UnsupportedOperationException ignored){}
        }
    }
}
