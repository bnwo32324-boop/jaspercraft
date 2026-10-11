package chat.jaspr.bettercombat;

import java.io.*;
import java.util.*;
import org.bukkit.*;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.craftbukkit.v1_12_R1.entity.CraftLivingEntity;
import org.bukkit.craftbukkit.v1_12_R1.entity.CraftPlayer;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.entity.*;
import org.bukkit.event.player.*;
import org.bukkit.inventory.ItemStack;
import net.minecraft.server.v1_12_R1.GenericAttributes;

/** Owner-local, expiring acceptance fixture. No command, socket, OP or auth bypass.
 * Only the fresh test guest is eligible. No blocks or other entities are modified.
 * The recovery snapshot is written BEFORE changing that guest and survives restart.
 */
final class LiveCombatProbe implements Listener {
    private static final UUID TEST_ID=UUID.fromString("0006e82e-71e0-3b41-b828-da4626f32a47");
    private final BetterCombatPlugin plugin;
    private final File permit,request,report,recovery;
    private LivingEntity target;
    private long lastRequest=-1,expires;
    private int events;
    private double baseDamage,finalDamage;
    private String action="inactive",error="";
    private UUID targetId;
    LiveCombatProbe(BetterCombatPlugin p){
        plugin=p;permit=new File(p.getDataFolder(),"live-test-permit.yml");
        request=new File(p.getDataFolder(),"live-test-request.yml");
        report=new File(p.getDataFolder(),"live-test-report.yml");
        recovery=new File(p.getDataFolder(),"live-test-recovery.yml");
        Bukkit.getPluginManager().registerEvents(this,p);
        Bukkit.getScheduler().runTaskTimer(p,this::poll,20,20);
    }
    private boolean allowed(){
        if(!permit.isFile())return false;
        YamlConfiguration y=YamlConfiguration.loadConfiguration(permit);
        expires=y.getLong("expires");
        return TEST_ID.toString().equals(y.getString("uuid"))&&"anon_1102".equals(y.getString("name"))
            &&expires>System.currentTimeMillis()&&expires-System.currentTimeMillis()<=30*60*1000L;
    }
    private Player player(){return Bukkit.getPlayer(TEST_ID);}
    private void poll(){
        try{
            if(!allowed()){if(recovery.isFile()&&player()!=null)restore(player());return;}
            if(!request.isFile())return;
            YamlConfiguration q=YamlConfiguration.loadConfiguration(request);long id=q.getLong("id");
            if(id<=lastRequest)return;lastRequest=id;
            Player p=player();if(p==null){action="waiting-for-test-guest";write(null);return;}
            if(!plugin.authenticated(p)){action="waiting-for-normal-authentication";write(p);return;}
            String next=q.getString("action","");
            if("cleanup".equals(next)){restore(p);action="cleaned";write(p);return;}
            if("prepare".equals(next)||"reset".equals(next)){
                save(p);
                removeTarget();
                Location here=p.getLocation();here.setY(160);here.setYaw(0);here.setPitch(0);
                p.setGameMode(GameMode.CREATIVE);p.setAllowFlight(true);p.setFlying(true);p.setInvulnerable(true);
                p.setFallDistance(0);p.setFireTicks(0);p.teleport(here);
                p.getInventory().clear();p.getInventory().setHeldItemSlot(0);
                p.getInventory().setItemInMainHand(new ItemStack(Material.DIAMOND_SWORD));
                p.getInventory().setItemInOffHand(new ItemStack(Material.DIAMOND_SWORD));p.updateInventory();
                double distance=Math.max(1,Math.min(8,q.getDouble("distance",4)));
                double health=Math.max(20,Math.min(1000,q.getDouble("health",200)));
                target=(LivingEntity)p.getWorld().spawnEntity(here.clone().add(0,0,distance),EntityType.ZOMBIE);
                ((Zombie)target).setBaby(false);target.setAI(false);target.setGravity(false);
                target.setSilent(true);target.setRemoveWhenFarAway(false);target.setMaxHealth(health);target.setHealth(health);
                target.setCustomName("JasperCraft acceptance target");target.setCustomNameVisible(true);
                target.getEquipment().setArmorContents(new ItemStack[4]);
                ((CraftLivingEntity)target).getHandle().getAttributeInstance(GenericAttributes.h).setValue(0);
                targetId=target.getUniqueId();events=0;baseDamage=0;finalDamage=0;action=next;
                YamlConfiguration saved=YamlConfiguration.loadConfiguration(recovery);saved.set("target",targetId.toString());saved.save(recovery);
            }else if("equip".equals(next)){
                Material material=Material.valueOf(q.getString("material","DIAMOND_SWORD"));
                if(material==Material.TNT||material.isBlock())throw new IllegalArgumentException("No building/explosive fixtures");
                ItemStack item=material==Material.AIR?null:new ItemStack(material);
                if("O".equals(q.getString("hand")))p.getInventory().setItemInOffHand(item);else p.getInventory().setItemInMainHand(item);
                p.updateInventory();action=next;
            }else if("report".equals(next))action=next;
            else throw new IllegalArgumentException("Unknown acceptance action");
            write(p);
        }catch(Exception e){error=e.toString();plugin.getLogger().warning("LIVE_COMBAT_PROBE "+error);try{write(player());}catch(Exception ignored){}}
    }
    private void save(Player p)throws IOException{
        if(recovery.isFile())return;
        YamlConfiguration y=new YamlConfiguration();y.set("uuid",TEST_ID.toString());y.set("location",p.getLocation());
        y.set("gameMode",p.getGameMode().name());y.set("allowFlight",p.getAllowFlight());y.set("flying",p.isFlying());
        y.set("invulnerable",p.isInvulnerable());y.set("held",p.getInventory().getHeldItemSlot());
        y.set("inventory",Arrays.asList(p.getInventory().getContents()));y.save(recovery);
    }
    private void removeTarget(){
        if(target!=null){target.remove();target=null;}
        if(recovery.isFile()){
            String id=YamlConfiguration.loadConfiguration(recovery).getString("target");
            if(id!=null){UUID u=UUID.fromString(id);for(World w:Bukkit.getWorlds())for(org.bukkit.entity.Entity e:w.getEntities())
                if(u.equals(e.getUniqueId())){e.remove();break;}}
        }
        targetId=null;
    }
    private void restore(Player p)throws IOException{
        removeTarget();if(!recovery.isFile())return;
        YamlConfiguration y=YamlConfiguration.loadConfiguration(recovery);
        if(!TEST_ID.toString().equals(y.getString("uuid")))throw new IOException("Recovery identity mismatch");
        java.util.List<?> items=y.getList("inventory");ItemStack[] a=new ItemStack[items.size()];
        for(int i=0;i<a.length;i++)a[i]=(ItemStack)items.get(i);
        p.getInventory().setContents(a);p.getInventory().setHeldItemSlot(y.getInt("held"));
        p.setGameMode(GameMode.valueOf(y.getString("gameMode")));p.setAllowFlight(y.getBoolean("allowFlight"));
        p.setFlying(y.getBoolean("flying"));p.setInvulnerable(y.getBoolean("invulnerable"));
        p.setFallDistance(0);p.setFireTicks(0);p.teleport((Location)y.get("location"));p.updateInventory();
        File done=new File(recovery.getParentFile(),"live-test-recovery-restored-"+System.currentTimeMillis()+".yml");
        if(!recovery.renameTo(done))throw new IOException("Could not archive restored recovery snapshot");
    }
    @EventHandler(priority=EventPriority.HIGHEST)public void protect(EntityDamageEvent e){
        if(e.getEntity().getUniqueId().equals(TEST_ID)&&recovery.isFile()&&expires>System.currentTimeMillis())e.setCancelled(true);
        if(e.getEntity()==target&&!(e instanceof EntityDamageByEntityEvent))e.setCancelled(true);
    }
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true)public void hit(EntityDamageByEntityEvent e){
        if(e.getEntity()!=target)return;events++;baseDamage=e.getDamage();finalDamage=e.getFinalDamage();
        Bukkit.getScheduler().runTask(plugin,()->{try{write(player());}catch(IOException ignored){}});
    }
    @EventHandler(priority=EventPriority.MONITOR)public void quit(PlayerQuitEvent e){
        if(e.getPlayer().getUniqueId().equals(TEST_ID))try{restore(e.getPlayer());}catch(IOException ex){plugin.getLogger().warning(ex.toString());}
    }
    @EventHandler(priority=EventPriority.MONITOR)public void join(PlayerJoinEvent e){
        if(e.getPlayer().getUniqueId().equals(TEST_ID)&&recovery.isFile())Bukkit.getScheduler().runTask(plugin,()->{
            try{restore(e.getPlayer());}catch(IOException ex){plugin.getLogger().warning(ex.toString());}});
    }
    private void write(Player p)throws IOException{
        YamlConfiguration y=new YamlConfiguration();y.set("date",System.currentTimeMillis());y.set("request",lastRequest);
        y.set("action",action);y.set("error",error);y.set("events",events);y.set("baseDamage",baseDamage);y.set("finalDamage",finalDamage);
        y.set("target",targetId==null?null:targetId.toString());y.set("health",target==null?null:target.getHealth());
        if(p!=null){y.set("authenticated",plugin.authenticated(p));y.set("op",p.isOp());y.set("player",p.getName());
            y.set("main",p.getInventory().getItemInMainHand().getType().name());y.set("off",p.getInventory().getItemInOffHand().getType().name());
            y.set("mainStrength",((CraftPlayer)p).getHandle().n(.5f));}
        y.save(report);
    }
    void close(){try{if(player()!=null)restore(player());else removeTarget();}catch(IOException e){plugin.getLogger().warning(e.toString());}}
}
