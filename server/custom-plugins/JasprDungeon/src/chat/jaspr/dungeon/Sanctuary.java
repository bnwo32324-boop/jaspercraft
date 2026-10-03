package chat.jaspr.dungeon;

import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.entity.*;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.potion.*;

/** Safety belongs to the refuge, not a timer that players can carry into combat. */
public final class Sanctuary implements Listener {
    private final DungeonPlugin plugin;private int ticks;
    public Sanctuary(DungeonPlugin plugin){this.plugin=plugin;}
    public boolean contains(Location l){return l!=null&&plugin.inside(l.getWorld())&&plugin.room(l).kind==Layout.Kind.REFUGE;}
    public static boolean hazard(Material m){return m==Material.FIRE||m==Material.LAVA||m==Material.STATIONARY_LAVA||m==Material.CACTUS||m==Material.MAGMA||m==Material.PORTAL||m==Material.ENDER_PORTAL;}
    /** Two blocks of headroom, full support, no adjacent hazards and inside the world border. */
    public static boolean safeFloor(Location l){
        if(l==null||l.getWorld()==null||l.getY()<2||l.getY()>l.getWorld().getMaxHeight()-3)return false;
        World w=l.getWorld();WorldBorder b=w.getWorldBorder();Location c=b.getCenter();double half=b.getSize()/2-1;
        if(Math.abs(l.getX()-c.getX())>half||Math.abs(l.getZ()-c.getZ())>half)return false;
        Block feet=l.getBlock();Material below=feet.getRelative(0,-1,0).getType();
        if(!below.isOccluding()||hazard(below))return false;
        int headY=(int)Math.floor(l.getY()+1.8-0.000001);
        for(int y=feet.getY();y<=headY;y++)if(w.getBlockAt(feet.getX(),y,feet.getZ()).getType()!=Material.AIR)return false;
        for(int dx=-1;dx<=1;dx++)for(int dz=-1;dz<=1;dz++)for(int dy=-1;dy<=1;dy++)if(hazard(feet.getRelative(dx,dy,dz).getType()))return false;
        return true;
    }
    /** Never edit a damaged arrival room to force travel; choose a safe square or fail closed. */
    public Location arrival(){
        return arrival(plugin.ensureWorld());
    }
    public Location arrival(World w){
        for(int r=0;r<=6;r++)for(int dx=-r;dx<=r;dx++)for(int dz=-r;dz<=r;dz++){
            if(Math.max(Math.abs(dx),Math.abs(dz))!=r)continue;
            Location l=new Location(w,16.5+dx,65,16.5+dz,180,0);
            if(contains(l)&&safeFloor(l))return l;
        }
        return null;
    }
    public void calm(Player p){p.setFireTicks(0);p.setFallDistance(0);
        for(PotionEffectType t:new PotionEffectType[]{PotionEffectType.POISON,PotionEffectType.WITHER,PotionEffectType.HARM,PotionEffectType.LEVITATION})p.removePotionEffect(t);
    }
    public void tick(){boolean cleanup=++ticks%20==0;for(World world:plugin.dungeonWorlds()){for(Player p:world.getPlayers())if(contains(p.getLocation()))calm(p);
        // Inspect only the already-loaded 64x64 refuge parcel; never load chunks for cleanup.
        if(cleanup)for(int x=0;x<4;x++)for(int z=0;z<4;z++)if(world.isChunkLoaded(x,z))for(Entity e:world.getChunkAt(x,z).getEntities())
            if(contains(e.getLocation())&&(e instanceof Monster||e instanceof Slime||e instanceof Projectile||e instanceof AreaEffectCloud))e.remove();
        }
    }
    @EventHandler(priority=EventPriority.LOWEST,ignoreCancelled=true) public void damage(EntityDamageEvent e){
        if(contains(e.getEntity().getLocation())){e.setCancelled(true);if(e.getEntity() instanceof Player)calm((Player)e.getEntity());}
        if(e instanceof EntityDamageByEntityEvent){Entity source=((EntityDamageByEntityEvent)e).getDamager();
            if(source instanceof Projectile&&((Projectile)source).getShooter() instanceof Entity)source=(Entity)((Projectile)source).getShooter();
            if(contains(source.getLocation()))e.setCancelled(true);
        }
    }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void target(EntityTargetLivingEntityEvent e){if(e.getTarget()!=null&&contains(e.getTarget().getLocation()))e.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void combust(EntityCombustEvent e){if(contains(e.getEntity().getLocation()))e.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void splash(PotionSplashEvent e){for(LivingEntity v:e.getAffectedEntities())if(contains(v.getLocation()))e.setIntensity(v,0);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void cloud(AreaEffectCloudApplyEvent e){e.getAffectedEntities().removeIf(v->contains(v.getLocation()));}
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true) public void arrive(PlayerTeleportEvent e){if(contains(e.getTo()))calm(e.getPlayer());}
}
