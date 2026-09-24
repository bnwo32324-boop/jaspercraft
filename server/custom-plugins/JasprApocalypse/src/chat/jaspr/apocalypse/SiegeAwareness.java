package chat.jaspr.apocalypse;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Iterator;
import java.util.List;
import java.util.ArrayList;
import java.util.UUID;
import java.util.function.Predicate;
import org.bukkit.Location;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.entity.Zombie;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.craftbukkit.v1_12_R1.entity.CraftZombie;
import net.minecraft.server.v1_12_R1.Vec3D;

/** Original, bounded stimulus memory. Signals carry positions, never a hidden player's live path. */
public final class SiegeAwareness implements Listener {
    static final class Memory {
        UUID owner;
        Location goal, origin;
        long expires, lastSignal;
        Player visible;
        int sightCursor;
        void clear() { owner=null; goal=null; origin=null; visible=null; expires=0; }
    }
    private static final class Signal {
        final UUID owner;
        final Location at;
        final long born, expires, id;
        final double radius;
        final boolean scent;
        Signal(Player p, Location at, double radius, long now, long ttl, long id, boolean scent) {
            owner=p.getUniqueId(); this.at=at.clone(); this.radius=radius; born=now;
            expires=now+ttl; this.id=id; this.scent=scent;
        }
    }
    private final Predicate<Player> eligible;
    private final Deque<Signal> signals=new ArrayDeque<>();
    private final double range, leash, ownerLeash, aggro;
    private final int signalCap, pursuitTicks, soundTicks, scentTicks, rayLimit;
    private int rays;
    private long now, sequence;

    SiegeAwareness(FileConfiguration config, Predicate<Player> eligible) {
        this.eligible=eligible;
        range=SiegeRules.clamp(config.getDouble("siege.detection-range",96),16,96);
        leash=SiegeRules.clamp(config.getDouble("siege.pursuit-leash",112),16,128);
        ownerLeash=SiegeRules.clamp(config.getDouble("siege.signal-owner-leash",40),8,48);
        aggro=SiegeRules.clamp(config.getDouble("siege.aggro-range",32),8,64);
        signalCap=SiegeRules.clamp(config.getInt("siege.max-signals",128),8,256);
        pursuitTicks=SiegeRules.clamp(config.getInt("siege.pursuit-ticks",160),20,400);
        soundTicks=SiegeRules.clamp(config.getInt("siege.sound-memory-ticks",100),20,200);
        scentTicks=SiegeRules.clamp(config.getInt("siege.scent-memory-ticks",240),20,400);
        rayLimit=SiegeRules.clamp(config.getInt("siege.sight-rays-per-pass",96),8,192);
    }
    void begin(long tick) {
        now=tick; rays=rayLimit;
        for(Iterator<Signal> it=signals.iterator();it.hasNext();) {
            Signal s=it.next(); if(s.expires<=now || !SiegeTraversal.loaded(s.at))it.remove();
        }
    }
    void clear() { signals.clear(); }
    int signalCount() { return signals.size(); }
    double range() { return range; }

    /** Main server thread only. Radius is clamped; ineligible sources produce no signal. */
    public void noise(Player source, Location position, double radius) { signal(source,position,radius,false); }
    private void signal(Player source, Location position, double radius, boolean scent) {
        if(source==null || !eligible.test(source) || !SiegeTraversal.loaded(position)
                || source.getWorld()!=position.getWorld() || !Double.isFinite(radius) || radius<=0)return;
        // Coalesce high-rate shots/mining at one position without growing a second cooldown map.
        for(Iterator<Signal> it=signals.descendingIterator();it.hasNext();) {
            Signal s=it.next();
            if(s.owner.equals(source.getUniqueId()) && s.scent==scent && now-s.born<10
                    && s.at.getWorld()==position.getWorld() && s.at.distanceSquared(position)<4) {
                if(radius<=s.radius)return;
                it.remove();break;
            }
        }
        while(signals.size()>=signalCap)signals.removeFirst();
        signals.addLast(new Signal(source,position,Math.min(scent?24:48,radius),now,
                scent?scentTicks:soundTicks,++sequence,scent));
    }
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true)
    public void mine(BlockBreakEvent e) { noise(e.getPlayer(),e.getBlock().getLocation(),24); }
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true)
    public void place(BlockPlaceEvent e) { noise(e.getPlayer(),e.getBlock().getLocation(),16); }
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true)
    public void wound(EntityDamageEvent e) {
        if(e.getEntity() instanceof Player && e.getFinalDamage()>0)
            signal((Player)e.getEntity(),e.getEntity().getLocation(),24,true);
    }
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true)
    public void explosion(EntityExplodeEvent e) {
        // Attribute environmental blasts only to nearby eligible survivors; never attract to an empty world.
        int count=0;
        for(Player p:e.getLocation().getWorld().getPlayers()) {
            if(++count>32)break;
            if(p.getLocation().distanceSquared(e.getLocation())<=48*48)noise(p,e.getLocation(),48);
        }
    }
    @EventHandler public void quit(PlayerQuitEvent e) {
        signals.removeIf(s->s.owner.equals(e.getPlayer().getUniqueId()));
    }

    boolean valid(Memory m, Zombie z, List<Player> players, long tick) {
        if(m.goal==null)return false;
        Player p=find(players,m.owner);
        // A hunter close enough to touch its target never loses interest on a timer, a leash, or
        // a stale goal position. Dropping pursuit while standing next to someone is what left
        // hordes frozen mid-yard, and it is the one case that outranks every other rule here.
        if(p!=null && eligible.test(p) && p.getWorld()==z.getWorld()
                && z.getLocation().distanceSquared(p.getLocation())<=aggro*aggro)return true;
        if(tick>=m.expires || !SiegeTraversal.loaded(m.goal) || m.origin==null
                || m.goal.getWorld()!=z.getWorld() || m.origin.getWorld()!=z.getWorld()
                || z.getLocation().distanceSquared(m.origin)>leash*leash)return false;
        return p!=null && eligible.test(p) && p.getWorld()==z.getWorld()
                && p.getLocation().distanceSquared(m.goal)<=ownerLeash*ownerLeash;
    }
    private Player find(List<Player> players, UUID owner) {
        for(Player p:players)if(p.getUniqueId().equals(owner))return p;
        return null;
    }
    void update(Zombie z, Memory m, List<Player> players) {
        Player previous=m.visible;
        m.visible=null;
        if(!valid(m,z,players,now))m.clear();
        Player seen=null;
        List<Player> candidates=new ArrayList<>();
        for(Player p:players) {
            if(candidates.size()<32 && eligible.test(p)&&p.getWorld()==z.getWorld()
                    && z.getLocation().distanceSquared(p.getLocation())<=range*range)candidates.add(p);
        }
        candidates.sort((a,b)->Double.compare(z.getLocation().distanceSquared(a.getLocation()),z.getLocation().distanceSquared(b.getLocation())));
        // Vicinity beats stealth. Anything this close is simply noticed: no ray budget, no line
        // of sight, no candidate rotation. Previously every hunter got one occluded ray per scan
        // and a shared budget of 96, so in a fenced, wooded area most of a horde failed its only
        // check, kept a goal it had already reached, expired, and stood still facing whichever
        // way it last walked. Close range now short-circuits all of that.
        if(!candidates.isEmpty()) {
            Player closest=candidates.get(0);
            if(z.getLocation().distanceSquared(closest.getLocation())<=aggro*aggro) {
                if(m.origin==null || !closest.getUniqueId().equals(m.owner))m.origin=z.getLocation();
                m.owner=closest.getUniqueId(); m.goal=closest.getLocation();
                m.expires=now+pursuitTicks; m.visible=closest; m.sightCursor=0;
                return;
            }
        }
        // One ray per hunter per scan prevents crowded servers starving the tail of the horde.
        // Occluded candidates rotate; a still-visible target has priority over that rotation.
        if(!candidates.isEmpty()) {
            Player candidate=previous!=null&&candidates.contains(previous)?previous:candidates.get(Math.floorMod(m.sightCursor,candidates.size()));
            if(canSee(z,candidate))seen=candidate;else m.sightCursor++;
        }
        if(seen!=null) {
            if(m.origin==null || !seen.getUniqueId().equals(m.owner))m.origin=z.getLocation();
            m.owner=seen.getUniqueId(); m.goal=seen.getLocation(); m.expires=now+pursuitTicks; m.visible=seen;
            return;
        }
        Signal chosen=null; double score=Double.MAX_VALUE;
        for(Signal s:signals) {
            if(s.id<=m.lastSignal || s.expires<=now || s.at.getWorld()!=z.getWorld())continue;
            Player p=find(players,s.owner);
            if(p==null || !eligible.test(p) || p.getWorld()!=s.at.getWorld()
                    || p.getLocation().distanceSquared(s.at)>ownerLeash*ownerLeash)continue;
            double fade=s.scent?(double)(s.expires-now)/(s.expires-s.born):1;
            double r=s.radius*fade, d=z.getLocation().distanceSquared(s.at);
            if(d<=r*r && d<score) { chosen=s; score=d; }
        }
        if(chosen!=null) {
            if(m.origin==null)m.origin=z.getLocation();
            m.owner=chosen.owner; m.goal=chosen.at.clone(); m.expires=Math.min(chosen.expires,now+pursuitTicks);
            m.lastSignal=chosen.id;
        }
        if(m.goal!=null && z.getLocation().distanceSquared(m.goal)<2.25) m.clear();
    }
    boolean canSee(Zombie z, Player p) {
        if(!eligible.test(p)||p.getWorld()!=z.getWorld()||!SiegeTraversal.loaded(p.getLocation()))return false;
        double reach=range;
        org.bukkit.block.Block block=p.getLocation().getBlock();
        long time=p.getWorld().getTime();
        // Bukkit 1.12 reports raw sky light (15 outdoors even at midnight), not perceived night light.
        int sky=block.getLightFromSky();
        if(time>=13000&&time<=23000)sky=Math.max(0,sky-11);
        if(p.getWorld().hasStorm())sky=Math.max(0,sky-3);
        int light=Math.max(block.getLightFromBlocks(),sky);
        if(light<8)reach=Math.min(reach,48);
        if(p.isSneaking())reach*=light<8?.4:.65;
        if(p.hasPotionEffect(PotionEffectType.INVISIBILITY))reach=Math.min(reach,8);
        if(z.getLocation().distanceSquared(p.getLocation())>reach*reach || rays<=0)return false;
        rays--;
        return lineOfSight(z,p.getEyeLocation());
    }
    static boolean lineOfSight(Zombie z, Location end) {
        Location from=z.getEyeLocation();
        if(end.getWorld()!=from.getWorld()||from.distanceSquared(end)>96*96)return false;
        int steps=Math.max(1,(int)Math.ceil(from.distance(end)*2));
        for(int i=0;i<=steps;i++) {
            double t=(double)i/steps;
            Location sample=from.clone().add((end.getX()-from.getX())*t,(end.getY()-from.getY())*t,(end.getZ()-from.getZ())*t);
            if(!SiegeTraversal.loaded(sample))return false;
        }
        return ((CraftZombie)z).getHandle().world.rayTrace(new Vec3D(from.getX(),from.getY(),from.getZ()),
                new Vec3D(end.getX(),end.getY(),end.getZ()),false,true,false)==null;
    }
}
