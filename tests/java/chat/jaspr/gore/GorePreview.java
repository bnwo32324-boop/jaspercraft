package chat.jaspr.gore;

import org.bukkit.*;
import org.bukkit.command.*;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;
import java.util.*;

/** Isolated loopback fixture only. Never installed in the real server. */
public final class GorePreview extends JavaPlugin implements Listener {
    private LivingEntity subject;
    private String current = "ZOMBIE";
    private boolean cycling;
    public void onEnable() {
        if (!Boolean.getBoolean("jaspr.gore.preview")) throw new IllegalStateException("Fixture guard missing");
        getServer().getPluginManager().registerEvents(this, this);
        World w = getServer().getWorlds().get(0);
        w.setGameRuleValue("doMobSpawning", "false");
        w.setGameRuleValue("doDaylightCycle", "false");
        w.setGameRuleValue("doWeatherCycle", "false");
        w.setTime(6000);w.setStorm(false);w.setSpawnLocation(0,64,0);
        getLogger().info("GORE_PREVIEW_READY (no production worlds/accounts loaded)");
    }
    @EventHandler public void join(PlayerJoinEvent e) {
        getServer().getScheduler().runTaskLater(this, () -> {
            Player p=e.getPlayer();p.setGameMode(GameMode.CREATIVE);
            p.teleport(new Location(p.getWorld(),.5,64,.5,0,8));
            show("ZOMBIE");
        }, 40);
    }
    private void show(String name) {
        World w=getServer().getWorlds().get(0);
        for(Entity e:w.getEntities())if(!(e instanceof Player))e.remove();
        EntityType type=EntityType.valueOf(name.toUpperCase(Locale.ROOT));
        if(!type.isAlive()||!type.isSpawnable()||type==EntityType.ARMOR_STAND)throw new IllegalArgumentException("Not a mob");
        subject=(LivingEntity)w.spawnEntity(new Location(w,.5,64,5.5,180,0),type);
        subject.setAI(false);subject.setRemoveWhenFarAway(false);subject.setGlowing(false);
        subject.setCustomName(type.name());subject.setCustomNameVisible(true);subject.setFireTicks(0);
        if(subject instanceof Zombie)((Zombie)subject).setBaby(false);
        if(subject instanceof Slime)((Slime)subject).setSize(3);
        if(subject instanceof Skeleton)subject.getEquipment().setHelmet(new org.bukkit.inventory.ItemStack(Material.IRON_HELMET));
        if(subject instanceof Zombie)subject.getEquipment().setHelmet(new org.bukkit.inventory.ItemStack(Material.IRON_HELMET));
        current=type.name();
        getLogger().info("GORE_SUBJECT "+current+" health="+subject.getHealth());
    }
    @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if(!(sender instanceof ConsoleCommandSender))return true;
        try {
            if(args.length==0){show(current);return true;}
            if(args[0].equals("cycle")) {
                if(cycling)return true;cycling=true;
                List<EntityType> types=new ArrayList<>();
                for(EntityType t:EntityType.values())if(t.isAlive()&&t.isSpawnable()&&t!=EntityType.ARMOR_STAND)types.add(t);
                for(int i=0;i<types.size();i++) {
                    final EntityType t=types.get(i);final int offset=i*100;
                    getServer().getScheduler().runTaskLater(this,()->show(t.name()),offset+1);
                    getServer().getScheduler().runTaskLater(this,()->health(.65),offset+20);
                    getServer().getScheduler().runTaskLater(this,()->health(.3),offset+40);
                    getServer().getScheduler().runTaskLater(this,()->health(0),offset+60);
                }
                getServer().getScheduler().runTaskLater(this,()->{cycling=false;getLogger().info("GORE_CYCLE_COMPLETE types="+types.size());},types.size()*100+1);
            } else if(args[0].equals("list")) {
                for(EntityType t:EntityType.values())if(t.isAlive()&&t.isSpawnable()&&t!=EntityType.ARMOR_STAND)getLogger().info("GORE_TYPE "+t.name());
            } else if(args[0].equals("hit")) {
                if(subject!=null&&!subject.isDead())subject.damage(Double.parseDouble(args[1]));
                getLogger().info("GORE_HIT "+current+" health="+(subject==null?-1:subject.getHealth()));
            } else if(args[0].equals("night")) getServer().getWorlds().get(0).setTime(18000);
            else if(args[0].equals("day")) getServer().getWorlds().get(0).setTime(6000);
            else show(args[0]);
        } catch(Exception e){getLogger().warning(e.toString());}
        return true;
    }
    @SuppressWarnings("deprecation") private void health(double ratio) {
        if(subject!=null&&!subject.isDead()) {
            subject.setHealth(subject.getMaxHealth()*ratio);
            if(ratio>0)subject.playEffect(EntityEffect.HURT);
            getLogger().info("GORE_STAGE "+current+" ratio="+ratio);
        }
    }
}
