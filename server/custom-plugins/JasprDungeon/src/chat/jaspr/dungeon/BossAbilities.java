package chat.jaspr.dungeon;

import chat.jaspr.dungeon.BossKitCatalog.Ability;
import chat.jaspr.dungeon.BossKitCatalog.Element;
import chat.jaspr.dungeon.BossKitCatalog.Move;
import java.util.*;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.entity.*;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

/**
 * Generation 7: the boss moves themselves (BossKits plays a fight, BossKitCatalog sets the numbers). A move is planned when it
 * begins and its marks are frozen there, so moving the boss or its target never moves an advertised hit. It is telegraphed by
 * those marks (refreshed every REFRESH ticks, at most MAX_POINTS particles a refresh), a sound and a cue, then struck from the
 * same marks. Multi-pulse moves (spikes, rings, quakes, the Eclipse) mark each pulse anew before it lands. Strikes hurt only
 * through BossKits.hurt (eligible players of the room, outside the doorway margin, dealt by the boss itself).
 */
final class BossAbilities {
    enum Kind { SHOT, FANGS, METEOR, CLOUD }
    static final double MARK_Y=Layout.FLOOR+1.15;
    static final int MAX_POINTS=64,REFRESH=5;
    private final BossKits kits;private final DungeonPlugin plugin;private final Random random=new Random();
    private final Map<String,Particle> particles=new HashMap<>();private final Map<String,Sound> sounds=new HashMap<>();private final Map<String,Material> blocks=new HashMap<>();
    BossAbilities(BossKits kits){this.kits=kits;this.plugin=kits.plugin;}

    /** A move in flight. Marks: {x, z, r} circles, {x, z, r1, r2} bands, {x1, z1, x2, z2, half width} lanes. */
    static final class Cast {
        final Move move;final Ability ability;final Element look;final long begin;final int telegraph,count,pulses;final double damage;
        long due,until;int stage,pulse,stuck,tail;double x,z,angle,length,width,travelled,ring;boolean jumped,done;double[] dest;UUID target;Location last;
        final List<double[]> marks=new ArrayList<>(),points=new ArrayList<>();final List<LivingEntity> chosen=new ArrayList<>();
        final Set<UUID> hit=new HashSet<>(),hit2=new HashSet<>();boolean[] resolved=new boolean[0];
        Cast(Move move,Element look,long begin,int telegraph,int count,int pulses,double damage){
            this.move=move;this.ability=move.ability;this.look=look;this.begin=begin;this.telegraph=telegraph;this.count=count;this.pulses=Math.max(1,pulses);this.damage=damage;
        }
        /** Roughly how long the move takes from its first mark to its last hit (BossKits holds the signature back that long). */
        int length(){return telegraph*pulses+tail;}
    }

    // ---------------------------------------------------------------- planning
    /** Plans a move; null when it has nothing to aim at (no target, no add to use, no room for the boss's body). */
    Cast begin(BossKits.Fight f,Move m,long now,int countOverride){
        List<Player> targets=kits.targets(f);
        boolean aimless=m.ability==Ability.SUMMON||m.ability==Ability.ENRAGE||m.ability==Ability.SHIELD||m.ability==Ability.LEECH||m.ability==Ability.CLONES;
        if(targets.isEmpty()&&!aimless)return null;
        Element look=m.element(f.kit);int pulses=m.ability==Ability.ECLIPSE&&f.kit.desperate&&f.phase>=2?m.pulses+1:m.pulses;
        Cast c=new Cast(m,look,now,BossKitCatalog.telegraph(m,f.floor,f.phase,f.enraged),countOverride>0?countOverride:BossKitCatalog.count(m,f.floor,f.phase),pulses,
            BossKitCatalog.damage(m,f.floor,f.tier,f.dread,f.kit.guardian));
        Player t=targets.isEmpty()?null:targets.get(random.nextInt(targets.size()));if(t!=null)c.target=t.getUniqueId();
        Location b=f.boss.getLocation();c.x=b.getX();c.z=b.getZ();Layout.Room r=f.run.room;double half=half(f);
        switch(m.ability){
            case SUMMON:{
                int n=Math.min(c.count,kits.addsCap(f)-BossKits.prune(f.summoned));if(n<=0)return null;
                double a0=random.nextDouble()*Math.PI*2,rad=Math.max(m.radius,half+2.5);
                for(int i=0;i<n;i++){double a=a0+Math.PI*2*i/n;double[] p=inside(r,c.x+Math.cos(a)*rad,c.z+Math.sin(a)*rad);c.marks.add(new double[]{p[0],p[1],1.2});}
                break;}
            case CHARGE:{
                if(!mobile(f))return null;Location tl=t.getLocation();double d=Math.hypot(tl.getX()-c.x,tl.getZ()-c.z);
                c.angle=d<.5?random.nextDouble()*Math.PI*2:Math.atan2(tl.getZ()-c.z,tl.getX()-c.x);c.width=Math.max(m.radius,half+.6);
                c.length=clip(r,c.x,c.z,c.angle,Math.max(6,Math.min(24,d+4)));if(c.length<4)return null;
                c.marks.add(new double[]{c.x,c.z,c.x+Math.cos(c.angle)*c.length,c.z+Math.sin(c.angle)*c.length,c.width});c.tail=40;break;}
            case LEAP_SLAM:{
                if(!mobile(f))return null;Location tl=t.getLocation();double[] p=inside(r,tl.getX(),tl.getZ());c.dest=landing(f,p[0],p[1]);if(c.dest==null)return null;
                c.x=c.dest[0];c.z=c.dest[1];c.marks.add(new double[]{c.x,c.z,m.radius});c.tail=30;break;}
            case VOLLEY:c.tail=8*c.pulses;break;
            case BEAM:{
                Location tl=t.getLocation();double a0=Math.atan2(tl.getZ()-c.z,tl.getX()-c.x);
                for(int i=0;i<c.count;i++){double a=a0+(i-(c.count-1)/2.0)*Math.toRadians(c.count>=3?28:22),len=clip(r,c.x,c.z,a,26);
                    if(len>=4)c.marks.add(new double[]{c.x,c.z,c.x+Math.cos(a)*len,c.z+Math.sin(a)*len,m.radius});}
                if(c.marks.isEmpty())return null;break;}
            case METEOR_RAIN: case LIGHTNING: case POISON_CLOUD:{
                scatter(f,c,targets,m.ability==Ability.POISON_CLOUD?Math.min(3.5,m.radius):m.radius,m.ability==Ability.METEOR_RAIN&&f.kit.guardian?14:8);
                // A cloud lingers: its whole disc stays off the doorway margin, so a doorway remains a reliable escape.
                if(m.ability==Ability.POISON_CLOUD)for(double[] k:c.marks){double e=EncounterCatalog.EXIT_MARGIN+k[2]+.5;k[0]=Math.max(r.x+e,Math.min(r.x+r.w-e,k[0]));k[1]=Math.max(r.z+e,Math.min(r.z+r.d-e,k[1]));}
                if(c.marks.isEmpty())return null;if(m.ability==Ability.METEOR_RAIN)c.tail=60;break;}
            case BLINK_STRIKE:{
                if(!mobile(f))return null;Location tl=t.getLocation();double dx=tl.getX()-c.x,dz=tl.getZ()-c.z,d=Math.max(.5,Math.hypot(dx,dz));
                // Behind the target: past it, on the far side from the boss.
                double[] p=inside(r,tl.getX()+dx/d*(half+1.6),tl.getZ()+dz/d*(half+1.6));c.dest=landing(f,p[0],p[1]);if(c.dest==null)return null;
                c.angle=Math.atan2(tl.getZ()-c.dest[1],tl.getX()-c.dest[0]);c.marks.add(new double[]{c.dest[0],c.dest[1],m.radius+half*.5});break;}
            case VORTEX:{double[] p=inside(r,c.x,c.z);c.x=p[0];c.z=p[1];c.marks.add(new double[]{c.x,c.z,m.radius});c.marks.add(new double[]{c.x,c.z,burstRadius(f)});c.tail=40;break;}
            case SPIKES:{spikes(f,c,t.getLocation());if(c.marks.isEmpty())return null;break;}
            case SNARE:{snares(f,c,targets);if(c.marks.isEmpty())return null;break;}
            case CLONES:{
                if(BossKits.prune(f.clones)>0)return null;double a0=random.nextDouble()*Math.PI*2;
                for(int i=0;i<c.count;i++){double a=a0+Math.PI*2*i/c.count;double[] p=inside(r,c.x+Math.cos(a)*m.radius,c.z+Math.sin(a)*m.radius);c.marks.add(new double[]{p[0],p[1],1});}
                break;}
            case SHIELD:{
                if(f.shielded(now)||BossKits.prune(f.wardens)>0)return null;double a0=random.nextDouble()*Math.PI*2,rad=Math.max(5,half+3);
                for(int i=0;i<c.count;i++){double a=a0+Math.PI*2*i/c.count;double[] p=inside(r,c.x+Math.cos(a)*rad,c.z+Math.sin(a)*rad);c.marks.add(new double[]{p[0],p[1],1.2});}
                break;}
            case LEECH:{c.chosen.addAll(nearest(f.summoned,b,m.radius,c.count));if(c.chosen.isEmpty())return null;break;}
            case SACRIFICE:{c.chosen.addAll(menacing(f,targets,c.count));if(c.chosen.isEmpty())return null;break;}
            case FIRE_RING:{double[] p=inside(r,c.x,c.z);c.x=p[0];c.z=p[1];band(f,c);break;}
            case ROAR:{c.width=m.radius+half;c.marks.add(new double[]{c.x,c.z,c.width});break;}
            case QUAKE_LINES:{Location tl=t.getLocation();c.angle=Math.atan2(tl.getZ()-c.z,tl.getX()-c.x);spokes(f,c);if(c.marks.isEmpty())return null;break;}
            case ENRAGE:if(f.enraged)return null;break;
            case ECLIPSE:{eclipse(f,c,targets);if(c.marks.isEmpty())return null;break;}
            default:return null;
        }
        c.due=now+c.telegraph;plot(c);
        kits.say(f,m);play(f,b,sound(look.sound),.9f,m.ability.heavy?.7f:1.1f);
        if(m.ability==Ability.ECLIPSE)for(Player v:kits.viewers(f))v.sendTitle(ChatColor.DARK_PURPLE+m.name,ChatColor.LIGHT_PURPLE+"Reach a lit circle!",5,Math.max(20,c.telegraph-10),10);
        telegraph(f,c,now);
        return c;
    }

    // ---------------------------------------------------------------- the move in flight
    void step(BossKits.Fight f,long now){
        Cast c=f.cast;if(c==null)return;
        if(now<c.due){
            if((now-c.begin)%REFRESH==0)telegraph(f,c,now);
            // The leap's hop stays under the roof: a scaled body that pokes through it would be put back at its station (Encounters' containment).
            if(c.ability==Ability.LEAP_SLAM&&!c.jumped&&c.due-now<=10){c.jumped=true;double room=f.run.room.roof()-(f.boss.getLocation().getY()+f.boss.getHeight())-.6;if(room>.4)f.boss.setVelocity(new Vector(0,Math.min(.75,Math.sqrt(.16*room)),0));}
            if(c.ability==Ability.ECLIPSE&&(c.due-now)%20==0)for(Player v:kits.viewers(f))v.sendActionBar(ChatColor.DARK_PURPLE+c.move.name+" in "+(c.due-now)/20+"... reach a lit circle!");
            return;
        }
        switch(c.ability){
            case SUMMON: summon(f,c);c.done=true;break;
            case CHARGE: if(c.stage==0){c.stage=1;c.until=now+(long)Math.ceil(c.length/speed(f))+8;c.last=f.boss.getLocation();}dash(f,c,now);break;
            case LEAP_SLAM: if(c.stage==0){slam(f,c);c.stage=1;c.ring=Math.max(1,c.move.radius*.6);}else wave(f,c);break;
            case VOLLEY: if(c.stage==0){c.stage=1;c.until=now;}if(now>=c.until){salvo(f,c);c.pulse++;c.until=now+8;if(c.pulse>=c.pulses)c.done=true;}break;
            case BEAM: beam(f,c);c.done=true;break;
            case METEOR_RAIN:
                if(c.stage==0){c.stage=1;c.until=now+100;drop(f,c);}
                else{if((now-c.begin)%REFRESH==0)telegraph(f,c,now);if(now>=c.until)for(int i=0;i<c.marks.size();i++)strike(f,c,i);}
                c.done=all(c.resolved);break;
            case BLINK_STRIKE: blink(f,c);c.done=true;break;
            case VORTEX: if(c.stage==0){c.stage=1;c.until=now+40;}pull(f,c,now);if(now>=c.until){implode(f,c);c.done=true;}break;
            case SPIKES:{fangs(f,c);c.pulse++;Player t=c.pulse<c.pulses?aim(f,c):null;if(t==null){c.done=true;break;}spikes(f,c,t.getLocation());if(c.marks.isEmpty())c.done=true;else rearm(f,c,now);break;}
            case LIGHTNING: lightning(f,c);c.done=true;break;
            case SNARE: snare(f,c,now);c.done=true;break;
            case CLONES: clones(f,c,now);c.done=true;break;
            case SHIELD: raise(f,c,now);c.done=true;break;
            case LEECH: leech(f,c);c.done=true;break;
            case SACRIFICE: sacrifice(f,c);c.done=true;break;
            case FIRE_RING: ringStrike(f,c);if(++c.pulse<c.pulses){band(f,c);rearm(f,c,now);}else c.done=true;break;
            case POISON_CLOUD: clouds(f,c);c.done=true;break;
            case ROAR: roar(f,c);c.done=true;break;
            case QUAKE_LINES: quake(f,c);if(++c.pulse<c.pulses){spokes(f,c);if(c.marks.isEmpty())c.done=true;else rearm(f,c,now);}else c.done=true;break;
            case ENRAGE: kits.enrage(f,"move");c.done=true;break;
            case ECLIPSE:
                darkness(f,c);
                if(++c.pulse<c.pulses){eclipse(f,c,kits.targets(f));if(c.marks.isEmpty())c.done=true;else{rearm(f,c,now);for(Player v:kits.viewers(f))v.sendTitle(ChatColor.DARK_PURPLE+c.move.name,ChatColor.LIGHT_PURPLE+"Again! Reach a lit circle!",5,Math.max(20,(int)(c.due-now)-10),10);}}
                else c.done=true;break;
            default: c.done=true;
        }
        if(c.done&&f.cast==c){f.cast=null;f.nextCast=now+BossKitCatalog.gap(f.kit,f.floor,f.phase,f.enraged);}
    }
    /** The next pulse of a multi-pulse move: fresh marks and a fresh warning, never shorter than the move's minimum. */
    private void rearm(BossKits.Fight f,Cast c,long now){
        c.due=now+BossKitCatalog.telegraph(c.move,f.floor,f.phase,f.enraged);c.hit.clear();plot(c);play(f,f.boss.getLocation(),sound(c.look.sound),.8f,.8f);telegraph(f,c,now);
    }
    /** The cast's own target if it still stands in the room, else any eligible player. */
    private Player aim(BossKits.Fight f,Cast c){
        Player t=c.target==null?null:Bukkit.getPlayer(c.target);if(kits.eligible(t,f.run))return t;
        List<Player> ts=kits.targets(f);return ts.isEmpty()?null:ts.get(random.nextInt(ts.size()));
    }
    void abandon(BossKits.Fight f,Cast c){if(c.ability==Ability.CHARGE&&c.stage==1&&f.boss.isValid())f.boss.setVelocity(new Vector(0,Math.min(0,f.boss.getVelocity().getY()),0));c.done=true;}
    // ---------------------------------------------------------------- strikes
    private void summon(BossKits.Fight f,Cast c){
        List<EncounterCatalog.Species> pool=BossKitCatalog.summons(c.move,f.run.room.theme,f.floor);int free=kits.addsCap(f)-BossKits.prune(f.summoned),made=0;
        for(double[] k:c.marks){
            if(free<=0)break;EncounterCatalog.Species s=pool.get(random.nextInt(pool.size()));Location at=new Location(f.run.world,k[0],Layout.FLOOR+1,k[1]);
            LivingEntity e=kits.add(f,s,at,c.move.elite,(c.move.elite?ChatColor.DARK_PURPLE+"Elite ":ChatColor.GRAY.toString())+pretty(s));
            erupt(f,at,c.look);if(e!=null){f.summoned.add(e);free--;made++;}
        }
        if(made>0)play(f,f.boss.getLocation(),Sound.ENTITY_ZOMBIE_INFECT,.7f,.6f);
    }
    /** The charge: the boss runs the marked lane; whoever is still in it where the body passes is struck and thrown aside. */
    private void dash(BossKits.Fight f,Cast c,long now){
        Location bl=f.boss.getLocation();double moved=Math.hypot(bl.getX()-c.last.getX(),bl.getZ()-c.last.getZ());c.travelled+=moved;c.last=bl;
        c.stuck=moved<.12&&now-c.due>3?c.stuck+1:0;
        if(c.travelled>=c.length||now>=c.until||c.stuck>=4){f.boss.setVelocity(new Vector(0,Math.min(0,f.boss.getVelocity().getY()),0));burst(f,bl.clone().add(0,.3,0),Particle.EXPLOSION_NORMAL,6);play(f,bl,Sound.ENTITY_IRONGOLEM_ATTACK,1f,.6f);c.done=true;return;}
        double speed=speed(f);f.boss.setVelocity(new Vector(Math.cos(c.angle)*speed,Math.min(.1,f.boss.getVelocity().getY()),Math.sin(c.angle)*speed));
        double[] lane=c.marks.get(0);double reach=half(f)+1.6;
        for(Player p:kits.targets(f)){
            Location l=p.getLocation();if(c.hit.contains(p.getUniqueId()))continue;
            if(offSegment(lane,l.getX(),l.getZ())<=c.width&&Math.hypot(l.getX()-bl.getX(),l.getZ()-bl.getZ())<=reach){c.hit.add(p.getUniqueId());if(kits.hurt(f,p,c.damage))knock(f,p,bl,1.0,.45);}
        }
        if((now&1)==0)for(Player v:kits.viewers(f))v.spawnParticle(particle(c.look.burst),bl.getX(),bl.getY()+.3,bl.getZ(),3,half(f)*.4,.1,half(f)*.4,0);
    }
    /** The leap lands on its marked circle (the body only where it fits), then a shockwave ring runs outward: jump it. */
    private void slam(BossKits.Fight f,Cast c){
        if(c.dest!=null)teleport(f,c.dest[0],c.dest[1],f.boss.getLocation().getYaw());
        double[] k=c.marks.get(0);Location at=new Location(f.run.world,k[0],Layout.FLOOR+1,k[1]);
        for(Player p:kits.targets(f)){Location l=p.getLocation();if(Math.hypot(l.getX()-k[0],l.getZ()-k[1])<=k[2]&&kits.hurt(f,p,c.damage))knock(f,p,at,.7,.5);}
        burst(f,at.clone().add(0,.2,0),Particle.EXPLOSION_LARGE,3);play(f,at,Sound.ENTITY_GENERIC_EXPLODE,.8f,.6f);play(f,at,Sound.ENTITY_IRONGOLEM_ATTACK,1f,.5f);
    }
    private void wave(BossKits.Fight f,Cast c){
        c.ring+=.6;double max=c.move.radius+6+f.floor;if(c.ring>max){c.done=true;return;}
        List<double[]> ring=new ArrayList<>();circle(ring,c.x,c.z,c.ring,16);draw(f,Particle.CRIT,ring,Layout.FLOOR+1.2);
        Location at=new Location(f.run.world,c.x,Layout.FLOOR+1,c.z);
        for(Player p:kits.targets(f)){
            Location l=p.getLocation();double d=Math.hypot(l.getX()-c.x,l.getZ()-c.z);
            if(!p.isOnGround()||Math.abs(d-c.ring)>.7||!c.hit2.add(p.getUniqueId()))continue;
            if(kits.hurt(f,p,c.damage*.6))knock(f,p,at,.5,.35);
        }
    }
    private void salvo(BossKits.Fight f,Cast c){
        int aimed=0;for(Player p:kits.targets(f)){if(++aimed>4)break;for(int i=0;i<c.count;i++)shoot(f,c,p,i);}
        BossKitCatalog.Shot s=c.look.shot;
        play(f,f.boss.getLocation(),s==BossKitCatalog.Shot.SMALL_FIREBALL?Sound.ENTITY_BLAZE_SHOOT:s==BossKitCatalog.Shot.ARROW?Sound.ENTITY_SKELETON_SHOOT
            :s==BossKitCatalog.Shot.SHULKER_BULLET?Sound.ENTITY_SHULKER_SHOOT:s==BossKitCatalog.Shot.WITHER_SKULL?Sound.ENTITY_WITHER_SHOOT:Sound.ENTITY_SNOWBALL_THROW,.8f,.9f);
    }
    /** One projectile from the boss's chest height, just outside its body, fanned by its index. Its hit is dealt by the boss (BossKits.shot). */
    private void shoot(BossKits.Fight f,Cast c,Player p,int i){
        if(!kits.room())return;World w=f.run.world;Location bl=f.boss.getLocation(),pl=p.getLocation();double half=half(f),h=Math.min(Math.max(1.2,f.boss.getHeight()*.6),3.5);
        double hx=pl.getX()-bl.getX(),hz=pl.getZ()-bl.getZ(),hd=Math.max(.01,Math.hypot(hx,hz));hx/=hd;hz/=hd;
        double spread=(i-(c.count-1)/2.0)*.12,cos=Math.cos(spread),sin=Math.sin(spread),rx=hx*cos-hz*sin,rz=hx*sin+hz*cos;
        Location from=new Location(w,bl.getX()+rx*(half+.6),bl.getY()+h,bl.getZ()+rz*(half+.6));
        Vector dir=new Vector(pl.getX()-from.getX(),pl.getY()+1.1-from.getY(),pl.getZ()-from.getZ());double dist=dir.length();if(dist<.1)return;
        Vector aim=new Vector(dir.getX()*cos-dir.getZ()*sin,dir.getY(),dir.getX()*sin+dir.getZ()*cos).normalize();
        Projectile shot;
        switch(c.look.shot){
            case SMALL_FIREBALL:{SmallFireball b=w.spawn(from,SmallFireball.class);b.setShooter(f.boss);b.setDirection(aim);b.setVelocity(aim.clone().multiply(.7));b.setIsIncendiary(false);b.setYield(0);shot=b;break;}
            case ARROW:{Arrow a=w.spawnArrow(from,aim.clone().add(new Vector(0,dist*.012,0)),1.9f,2f);a.setShooter(f.boss);a.setPickupStatus(Arrow.PickupStatus.DISALLOWED);shot=a;break;}
            case SHULKER_BULLET:{ShulkerBullet b=w.spawn(from,ShulkerBullet.class);b.setShooter(f.boss);b.setTarget(p);shot=b;break;}
            case WITHER_SKULL:{WitherSkull k=w.spawn(from,WitherSkull.class);k.setShooter(f.boss);k.setDirection(aim);k.setVelocity(aim.clone().multiply(.55));k.setYield(0);k.setIsIncendiary(false);shot=k;break;}
            default:{Snowball s=w.spawn(from,Snowball.class);s.setShooter(f.boss);s.setVelocity(aim.clone().multiply(1.45).add(new Vector(0,dist*.012,0)));shot=s;break;}
        }
        kits.track(f,shot,Kind.SHOT,c.damage,100,c.look,c,-1);
    }
    /** A projectile reached a player: its boss deals the hit, so Encounters' room rules decide. */
    void shot(BossKits.Fight f,BossKits.Spawned s,Player p){if(kits.eligible(p,f.run)){p.damage(s.damage,f.boss);if(kits.eligible(p,f.run))afflict(f,p,s.look,30);}}
    private void beam(BossKits.Fight f,Cast c){
        for(Player p:kits.targets(f)){Location l=p.getLocation();for(double[] s:c.marks)if(offSegment(s,l.getX(),l.getZ())<=s[4]){if(kits.hurt(f,p,c.damage))afflict(f,p,c.look,40);break;}}
        List<double[]> pts=new ArrayList<>();for(double[] s:c.marks)line(pts,s[0],s[1],s[2],s[3],1);thin(pts,40);
        Particle burst=particle(c.look.burst);for(Player v:kits.viewers(f))for(double[] q:pts){v.spawnParticle(burst,q[0],Layout.FLOOR+1.6,q[1],1,0,.2,0,0);v.spawnParticle(Particle.END_ROD,q[0],Layout.FLOOR+1.1,q[1],1,0,0,0,0);}
        play(f,f.boss.getLocation(),Sound.ENTITY_EVOCATION_ILLAGER_CAST_SPELL,1f,.6f);
    }
    /** Falling blocks from the roof over every marked circle: never placed (BossKits.landed), each strikes its own circle as it lands. */
    private void drop(BossKits.Fight f,Cast c){
        c.resolved=new boolean[c.marks.size()];World w=f.run.world;Material m=material(c.look.meteor);int roof=f.run.room.roof();
        for(int i=0;i<c.marks.size();i++){
            double[] k=c.marks.get(i);double x=Math.floor(k[0])+.5,z=Math.floor(k[1])+.5;int top=column(w,x,z,Layout.FLOOR+1,roof-2);FallingBlock b=null;
            if(top>=Layout.FLOOR+3&&kits.room())b=w.spawnFallingBlock(new Location(w,x,top,z),m,(byte)0);
            if(b!=null){b.setDropItem(false);b.setHurtEntities(false);if(kits.track(f,b,Kind.METEOR,c.damage,90,c.look,c,i)!=null)continue;}
            strike(f,c,i);
        }
        play(f,f.boss.getLocation(),Sound.BLOCK_GRAVEL_BREAK,.9f,.5f);
    }
    void land(BossKits.Fight f,BossKits.Spawned s){if(s.cast!=null)strike(f,s.cast,s.index);}
    private void strike(BossKits.Fight f,Cast c,int i){
        if(i<0||i>=c.marks.size()||i<c.resolved.length&&c.resolved[i])return;if(i<c.resolved.length)c.resolved[i]=true;
        double[] k=c.marks.get(i);Location at=new Location(f.run.world,k[0],Layout.FLOOR+1,k[1]);
        for(Player p:kits.targets(f)){Location l=p.getLocation();if(Math.hypot(l.getX()-k[0],l.getZ()-k[1])<=k[2]&&kits.hurt(f,p,c.damage))afflict(f,p,c.look,40);}
        for(Player v:kits.viewers(f)){v.spawnParticle(Particle.EXPLOSION_LARGE,k[0],at.getY()+.3,k[1],1,0,0,0,0);v.spawnParticle(particle(c.look.burst),k[0],at.getY()+.4,k[1],8,k[2]*.4,.2,k[2]*.4,.02);}
        play(f,at,Sound.ENTITY_GENERIC_EXPLODE,.45f,.9f);
    }
    private void blink(BossKits.Fight f,Cast c){
        Location from=f.boss.getLocation();double[] k=c.marks.get(0);
        if(teleport(f,c.dest[0],c.dest[1],yaw(Math.cos(c.angle),Math.sin(c.angle))))burst(f,from.clone().add(0,1,0),Particle.PORTAL,24);
        Location at=new Location(f.run.world,k[0],Layout.FLOOR+1,k[1]);
        for(Player p:kits.targets(f)){Location l=p.getLocation();if(Math.hypot(l.getX()-k[0],l.getZ()-k[1])<=k[2]&&kits.hurt(f,p,c.damage))knock(f,p,at,.8,.35);}
        burst(f,at.clone().add(0,1,0),Particle.SWEEP_ATTACK,4);play(f,at,Sound.ENTITY_ENDERMEN_TELEPORT,1f,.8f);play(f,at,Sound.ENTITY_PLAYER_ATTACK_SWEEP,1f,.7f);
    }
    /** The maelstrom drags whoever stands in its reach toward the well (slower than a sprint), then the well bursts. */
    private void pull(BossKits.Fight f,Cast c,long now){
        double reach=c.marks.get(0)[2];
        for(Player p:kits.targets(f)){
            Location l=p.getLocation();double dx=c.x-l.getX(),dz=c.z-l.getZ(),d=Math.hypot(dx,dz);if(d>reach||d<.6||!EncounterCatalog.hazardAllowed(f.run.room,l.getX(),l.getY(),l.getZ()))continue;
            Vector v=p.getVelocity();double nx=v.getX()+.07*dx/d,nz=v.getZ()+.07*dz/d,h=Math.hypot(nx,nz);if(h>.38){nx*=.38/h;nz*=.38/h;}
            p.setVelocity(new Vector(nx,Math.max(-.6,Math.min(.42,v.getY())),nz));
        }
        if(now%2==0)for(Player v:kits.viewers(f))v.spawnParticle(Particle.PORTAL,c.x,Layout.FLOOR+1.5,c.z,14,reach*.35,.3,reach*.35,.6);
        if((now-c.begin)%REFRESH==0)telegraph(f,c,now);
    }
    private void implode(BossKits.Fight f,Cast c){
        double r=c.marks.get(1)[2];Location at=new Location(f.run.world,c.x,Layout.FLOOR+1,c.z);
        for(Player p:kits.targets(f)){Location l=p.getLocation();if(Math.hypot(l.getX()-c.x,l.getZ()-c.z)<=r&&kits.hurt(f,p,c.damage)){afflict(f,p,c.look,40);knock(f,p,at,.6,.5);}}
        burst(f,at.clone().add(0,.5,0),Particle.EXPLOSION_LARGE,3);burst(f,at.clone().add(0,.5,0),particle(c.look.burst),16);play(f,at,Sound.ENTITY_GENERIC_EXPLODE,.8f,.5f);
    }
    /** Spikes: even pulses run a line of fangs from the boss toward the target, odd pulses close a ring of fangs around the target. */
    private void spikes(BossKits.Fight f,Cast c,Location tl){
        c.marks.clear();Location b=f.boss.getLocation();
        if(c.pulse%2==0){double a=Math.atan2(tl.getZ()-b.getZ(),tl.getX()-b.getX()),start=half(f)+1;for(int i=0;i<c.count;i++){double d=start+1.25*i;spot(f,c,b.getX()+Math.cos(a)*d,b.getZ()+Math.sin(a)*d);}}
        else for(int i=0;i<c.count;i++){double a=Math.PI*2*i/c.count;spot(f,c,tl.getX()+Math.cos(a)*2.6,tl.getZ()+Math.sin(a)*2.6);}
    }
    /** A one-cell mark on open floor inside the room's interior (never the doorway margin), once. */
    private void spot(BossKits.Fight f,Cast c,double x,double z){
        x=Math.floor(x)+.5;z=Math.floor(z)+.5;
        if(!EncounterCatalog.hazardAllowed(f.run.room,x,Layout.FLOOR+1,z)||!standable(f.run.world,x,z))return;
        for(double[] k:c.marks)if(Math.abs(k[0]-x)<.5&&Math.abs(k[1]-z)<.5)return;
        c.marks.add(new double[]{x,z,.6});
    }
    private void fangs(BossKits.Fight f,Cast c){
        World w=f.run.world;
        for(double[] k:c.marks){if(!kits.room())break;EvokerFangs fang=w.spawn(new Location(w,k[0],Layout.FLOOR+1,k[1]),EvokerFangs.class);fang.setOwner(f.boss);kits.track(f,fang,Kind.FANGS,c.damage,40,c.look,c,-1);}
        play(f,f.boss.getLocation(),Sound.ENTITY_EVOCATION_FANGS_ATTACK,1f,.8f);
    }
    /** Silent bolts (the spigot variant keeps the thunder out of every other room) on the marked circles. */
    private void lightning(BossKits.Fight f,Cast c){
        World w=f.run.world;
        for(double[] k:c.marks){
            Location at=new Location(w,k[0],Layout.FLOOR+1,k[1]);w.spigot().strikeLightningEffect(at,true);play(f,at,Sound.ENTITY_LIGHTNING_IMPACT,.8f,1f);
            for(Player p:kits.targets(f)){Location l=p.getLocation();if(Math.hypot(l.getX()-k[0],l.getZ()-k[1])<=k[2]&&c.hit.add(p.getUniqueId())&&kits.hurt(f,p,c.damage))afflict(f,p,c.look,20);}
        }
    }
    /** Snares gather under up to three players: their own cell and its neighbours. */
    private void snares(BossKits.Fight f,Cast c,List<Player> targets){
        int[][] cells={{0,0},{1,0},{-1,0},{0,1},{0,-1},{1,1}};int n=Math.min(c.count,cells.length),aimed=0;
        for(Player p:targets){if(++aimed>3)break;Location l=p.getLocation();for(int i=0;i<n;i++)spot(f,c,l.getBlockX()+cells[i][0]+.5,l.getBlockZ()+cells[i][1]+.5);}
    }
    /** Webs (only into air, never by the chest or the Descent's well, gone in a few seconds) or a numbing frost; both slow whoever stayed. */
    private void snare(BossKits.Fight f,Cast c,long now){
        World w=f.run.world;Layout.Room r=f.run.room;boolean webs=c.look.webs;long until=now+70+10*f.floor;
        if(webs)for(double[] k:c.marks){
            Block b=w.getBlockAt((int)Math.floor(k[0]),Layout.FLOOR+1,(int)Math.floor(k[1]));
            if(b.getType()!=Material.AIR||!b.getRelative(0,-1,0).getType().isOccluding()||nearChest(r,b.getX(),b.getZ()))continue;
            if(kits.web(f,b,until))b.setType(Material.WEB,false);
        }
        double reach=webs?.9:Math.max(.9,c.move.radius);
        for(Player p:kits.targets(f)){Location l=p.getLocation();for(double[] k:c.marks)if(Math.hypot(l.getX()-k[0],l.getZ()-k[1])<=reach){
            if(kits.hurt(f,p,c.damage)){p.addPotionEffect(new PotionEffect(PotionEffectType.SLOW,webs?40:50,webs?1:2));if(!webs)p.addPotionEffect(new PotionEffect(PotionEffectType.SLOW_DIGGING,50,0));}break;}}
        Particle burst=particle(c.look.burst);for(Player v:kits.viewers(f))for(double[] k:c.marks)v.spawnParticle(burst,k[0],Layout.FLOOR+1.3,k[1],3,.25,.2,.25,0);
        play(f,new Location(w,c.x,Layout.FLOOR+1,c.z),webs?Sound.ENTITY_SPIDER_AMBIENT:Sound.BLOCK_GLASS_BREAK,.8f,.7f);
    }
    /** Copies of the boss (its name, a minion's body, a touch of health) appear around it; the boss trades places with one. They vanish soon. */
    private void clones(BossKits.Fight f,Cast c,long now){
        EncounterCatalog.Species s=Encounters.speciesOf(f.boss);if(!BossKitCatalog.summonable(s))s=BossKitCatalog.summons(c.move,f.run.room.theme,f.floor).get(0);
        String name=f.boss.getCustomName();World w=f.run.world;
        for(double[] k:c.marks){
            Location at=new Location(w,k[0],Layout.FLOOR+1,k[1]);LivingEntity e=plugin.encounters.summon(f.run,s,at,2+2*f.floor,1+f.floor,name);
            if(e!=null){e.setCustomNameVisible(true);e.addScoreboardTag(BossKits.ADD_TAG);f.clones.add(e);}burst(f,at.clone().add(0,1,0),Particle.SPELL_WITCH,10);
        }
        f.clonesUntil=now+160+20*f.floor;
        if(!f.clones.isEmpty()&&mobile(f)){
            LivingEntity swap=f.clones.get(random.nextInt(f.clones.size()));Location from=f.boss.getLocation(),to=swap.getLocation();
            if(teleport(f,to.getX(),to.getZ(),from.getYaw()))swap.teleport(from);
        }
        play(f,f.boss.getLocation(),Sound.ENTITY_ILLUSION_ILLAGER_MIRROR_MOVE,1f,.9f);
    }
    /** The ward: elite wardens appear around the boss; while any lives (at most twenty or twenty-five seconds) the boss takes no harm. */
    private void raise(BossKits.Fight f,Cast c,long now){
        List<EncounterCatalog.Species> pool=BossKitCatalog.summons(c.move,f.run.room.theme,f.floor);World w=f.run.world;
        for(double[] k:c.marks){
            Location at=new Location(w,k[0],Layout.FLOOR+1,k[1]);LivingEntity e=kits.add(f,pool.get(random.nextInt(pool.size())),at,true,ChatColor.AQUA+"Warden of "+f.name);
            erupt(f,at,c.look);if(e!=null){e.setCustomNameVisible(true);f.wardens.add(e);}
        }
        if(f.wardens.isEmpty())return;
        f.shieldUntil=now+(f.kit.guardian?500:400);
        for(Player v:kits.viewers(f))v.sendMessage(ChatColor.AQUA+f.name+" is warded: kill its "+f.wardens.size()+" warden"+(f.wardens.size()==1?"":"s")+" to break the ward.");
        play(f,f.boss.getLocation(),Sound.BLOCK_PORTAL_TRIGGER,.6f,1.4f);
    }
    /** The boss drinks from its own summons (they lose half their health or die) and mends: at most 5% (guardians 3%) of its body a cast. */
    private void leech(BossKits.Fight f,Cast c){
        double heal=0;Location bl=f.boss.getLocation();
        for(LivingEntity e:c.chosen){
            if(!e.isValid()||e.isDead())continue;double take=Math.min(e.getHealth(),Math.max(4,e.getMaxHealth()*.5));heal+=take*2;
            Location el=e.getLocation();List<double[]> pts=new ArrayList<>();line(pts,el.getX(),el.getZ(),bl.getX(),bl.getZ(),1.5);thin(pts,8);draw(f,Particle.HEART,pts,Layout.FLOOR+1.8);
            e.damage(take);
        }
        heal=Math.min(heal,f.max()*(f.kit.guardian?.03:.05));if(heal<=0||!f.boss.isValid()||f.boss.isDead())return;
        f.boss.setHealth(Math.min(f.max(),f.boss.getHealth()+heal));
        burst(f,bl.clone().add(0,1,0),Particle.HEART,6);play(f,bl,Sound.ENTITY_WITCH_DRINK,1f,.7f);
        for(Player v:kits.viewers(f))v.sendMessage(ChatColor.GRAY+f.name+" drinks from its servants and mends.");
    }
    /** The marked servants burst where they stand; the boss feeds on the offering (stronger blows for ten seconds unless already enraged). */
    private void sacrifice(BossKits.Fight f,Cast c){
        boolean fed=false;
        for(LivingEntity e:c.chosen){
            if(!e.isValid()||e.isDead())continue;Location at=e.getLocation();
            for(Player p:kits.targets(f)){Location l=p.getLocation();if(Math.hypot(l.getX()-at.getX(),l.getZ()-at.getZ())<=c.move.radius&&Math.abs(l.getY()-at.getY())<3&&kits.hurt(f,p,c.damage)){afflict(f,p,c.look,40);knock(f,p,at,.6,.35);}}
            burst(f,at.clone().add(0,.5,0),Particle.EXPLOSION_LARGE,2);burst(f,at.clone().add(0,.5,0),particle(c.look.burst),12);play(f,at,Sound.ENTITY_GENERIC_EXPLODE,.6f,1.1f);
            e.remove();f.summoned.remove(e);fed=true;
        }
        if(!fed)return;
        if(!f.enraged)f.boss.addPotionEffect(new PotionEffect(PotionEffectType.INCREASE_DAMAGE,200,0,true,true),true);
        for(Player v:kits.viewers(f))v.sendMessage(ChatColor.GRAY+f.name+" feeds on the offering: its blows grow heavier.");
    }
    private void band(BossKits.Fight f,Cast c){c.marks.clear();double inner=Math.max(c.move.radius,half(f)+1.5)+4.5*c.pulse;c.marks.add(new double[]{c.x,c.z,inner,inner+2.5});}
    private void ringStrike(BossKits.Fight f,Cast c){
        double[] k=c.marks.get(0);
        for(Player p:kits.targets(f)){Location l=p.getLocation();double d=Math.hypot(l.getX()-k[0],l.getZ()-k[1]);if(d>=k[2]&&d<=k[3]&&kits.hurt(f,p,c.damage))afflict(f,p,c.look,40);}
        List<double[]> pts=new ArrayList<>();circle(pts,k[0],k[1],(k[2]+k[3])/2,28);
        Particle burst=particle(c.look.burst);for(Player v:kits.viewers(f))for(double[] q:pts)v.spawnParticle(burst,q[0],Layout.FLOOR+1.3,q[1],1,.3,.2,.3,0);
        play(f,new Location(f.run.world,k[0],Layout.FLOOR+1,k[1]),c.look==Element.FIRE?Sound.ENTITY_BLAZE_SHOOT:Sound.ENTITY_GENERIC_EXPLODE,.8f,.7f);
    }
    private void clouds(BossKits.Fight f,Cast c){
        World w=f.run.world;PotionEffectType type=effect(f,c.look);int life=Math.min(160,100+20*f.floor);
        for(double[] k:c.marks){
            if(!kits.room())break;AreaEffectCloud cl=w.spawn(new Location(w,k[0],Layout.FLOOR+1,k[1]),AreaEffectCloud.class);
            cl.setRadius((float)Math.min(3.5,k[2]));cl.setDuration(life);cl.setReapplicationDelay(20);cl.setWaitTime(5);cl.setRadiusPerTick(0f);cl.setRadiusOnUse(0f);cl.setDurationOnUse(0);
            cl.setParticle(Particle.SPELL_MOB);cl.setColor(Color.fromRGB(c.look.rgb));cl.addCustomEffect(new PotionEffect(type,Math.min(100,60+10*f.floor),f.floor>=3?1:0),true);cl.setSource(f.boss);
            kits.track(f,cl,Kind.CLOUD,0,life+10,c.look,c,-1);
        }
        play(f,new Location(w,c.x,Layout.FLOOR+1,c.z),Sound.BLOCK_LAVA_EXTINGUISH,.8f,.6f);
    }
    private void roar(BossKits.Fight f,Cast c){
        double[] k=c.marks.get(0);Location at=new Location(f.run.world,k[0],Layout.FLOOR+1,k[1]);
        for(Player p:kits.targets(f)){Location l=p.getLocation();if(Math.hypot(l.getX()-k[0],l.getZ()-k[1])<=k[2]&&kits.hurt(f,p,c.damage)){knock(f,p,at,1.1,.45);p.addPotionEffect(new PotionEffect(PotionEffectType.SLOW,60,1));}}
        List<double[]> pts=new ArrayList<>();circle(pts,k[0],k[1],k[2],20);draw(f,Particle.EXPLOSION_NORMAL,pts,Layout.FLOOR+1.3);
        play(f,f.boss.getLocation(),Sound.ENTITY_ENDERDRAGON_GROWL,1f,.7f);
    }
    /** Fault lines run out from where the boss slammed; each pulse turns them half a gap, so the last pulse's gaps are struck next. */
    private void spokes(BossKits.Fight f,Cast c){
        c.marks.clear();int n=Math.max(1,c.count);double a0=c.angle+(c.pulse%2==1?Math.PI/n:0)+(c.pulse>=2?Math.PI/(2*n):0),start=half(f)*.5;
        for(int i=0;i<n;i++){double a=a0+Math.PI*2*i/n,len=clip(f.run.room,c.x,c.z,a,16);
            if(len>start+2)c.marks.add(new double[]{c.x+Math.cos(a)*start,c.z+Math.sin(a)*start,c.x+Math.cos(a)*len,c.z+Math.sin(a)*len,c.move.radius});}
    }
    private void quake(BossKits.Fight f,Cast c){
        Location at=new Location(f.run.world,c.x,Layout.FLOOR+1,c.z);
        for(Player p:kits.targets(f)){Location l=p.getLocation();for(double[] s:c.marks)if(offSegment(s,l.getX(),l.getZ())<=s[4]){if(kits.hurt(f,p,c.damage))knock(f,p,at,.4,.5);break;}}
        List<double[]> pts=new ArrayList<>();for(double[] s:c.marks)line(pts,s[0],s[1],s[2],s[3],2);thin(pts,MAX_POINTS);draw(f,Particle.EXPLOSION_NORMAL,pts,Layout.FLOOR+1.2);
        play(f,at,Sound.ENTITY_GENERIC_EXPLODE,.7f,.5f);
    }
    /**
     * The Eclipse (owner: "legitimately incredibly powerful", yet fair): the whole arena darkens except a few lit circles, one
     * placed five to nine cells from each player on open floor, long enough ahead (ArenaTelegraph) to walk there even slowed.
     */
    private void eclipse(BossKits.Fight f,Cast c,List<Player> targets){
        c.marks.clear();Layout.Room r=f.run.room;World w=f.run.world;double rad=c.move.radius;
        for(Player p:targets){
            if(c.marks.size()>=c.count)break;Location l=p.getLocation();boolean placed=false;
            for(int k=0;k<24&&!placed;k++){double a=random.nextDouble()*Math.PI*2,d=5+random.nextDouble()*4;double[] q=inside(r,l.getX()+Math.cos(a)*d,l.getZ()+Math.sin(a)*d);
                if(standable(w,q[0],q[1])&&apart(c,q[0],q[1],rad*2)){c.marks.add(new double[]{q[0],q[1],rad});placed=true;}}
            if(!placed&&apart(c,l.getX(),l.getZ(),rad)){double[] q=inside(r,l.getX(),l.getZ());c.marks.add(new double[]{q[0],q[1],rad});}
        }
        for(int k=0;k<60&&c.marks.size()<c.count;k++){double[] q=inside(r,r.x+4.5+random.nextDouble()*(r.w-9),r.z+4.5+random.nextDouble()*(r.d-9));if(standable(w,q[0],q[1])&&apart(c,q[0],q[1],rad*2))c.marks.add(new double[]{q[0],q[1],rad});}
    }
    private void darkness(BossKits.Fight f,Cast c){
        for(Player p:kits.targets(f)){
            Location l=p.getLocation();boolean safe=false;for(double[] k:c.marks)if(Math.hypot(l.getX()-k[0],l.getZ()-k[1])<=k[2]){safe=true;break;}
            if(safe)continue;if(kits.hurt(f,p,c.damage))p.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS,40,0));
            for(Player v:kits.viewers(f))v.spawnParticle(Particle.SMOKE_LARGE,l.getX(),l.getY()+1,l.getZ(),20,.6,.8,.6,.02);
        }
        play(f,f.boss.getLocation(),Sound.ENTITY_WITHER_SHOOT,1f,.5f);play(f,f.boss.getLocation(),Sound.ENTITY_LIGHTNING_THUNDER,.5f,.6f);
    }

    // ---------------------------------------------------------------- telegraphs and presence (bounded particles, room viewers only)
    private void telegraph(BossKits.Fight f,Cast c,long now){
        List<Player> viewers=kits.viewers(f);if(viewers.isEmpty())return;Particle mark=particle(c.look.mark);
        switch(c.ability){
            case VOLLEY: case ENRAGE: case SHIELD: case CLONES:{
                Location b=f.boss.getLocation();List<double[]> pts=new ArrayList<>();circle(pts,b.getX(),b.getZ(),half(f)+1,12);
                draw(f,c.ability==Ability.ENRAGE?Particle.VILLAGER_ANGRY:mark,pts,b.getY()+1.2);
                if(c.ability==Ability.SHIELD||c.ability==Ability.CLONES)draw(f,mark,c.points,MARK_Y);break;}
            case LEECH:{
                Location b=f.boss.getLocation();List<double[]> pts=new ArrayList<>();
                for(LivingEntity e:c.chosen)if(e.isValid()){Location el=e.getLocation();line(pts,el.getX(),el.getZ(),b.getX(),b.getZ(),1.5);}
                thin(pts,MAX_POINTS);draw(f,Particle.SPELL_WITCH,pts,Layout.FLOOR+1.8);break;}
            case SACRIFICE:{
                List<double[]> pts=new ArrayList<>();for(LivingEntity e:c.chosen)if(e.isValid()){Location el=e.getLocation();circle(pts,el.getX(),el.getZ(),c.move.radius,12);}
                thin(pts,MAX_POINTS);draw(f,mark,pts,MARK_Y);
                if((now-c.begin)%10==0)for(LivingEntity e:c.chosen)if(e.isValid())play(f,e.getLocation(),Sound.ENTITY_CREEPER_PRIMED,.6f,.8f);break;}
            case ECLIPSE:{
                draw(f,Particle.END_ROD,c.points,MARK_Y+.2);
                for(Player p:kits.targets(f)){Location l=p.getLocation();for(Player v:viewers)v.spawnParticle(Particle.SMOKE_LARGE,l.getX(),l.getY()+.2,l.getZ(),6,2.5,.1,2.5,0);}break;}
            case METEOR_RAIN:{
                if(c.stage==0){draw(f,mark,c.points,MARK_Y);break;}
                List<double[]> pts=new ArrayList<>();for(int i=0;i<c.marks.size();i++)if(i>=c.resolved.length||!c.resolved[i]){double[] k=c.marks.get(i);circle(pts,k[0],k[1],k[2],12);}
                thin(pts,MAX_POINTS);draw(f,mark,pts,MARK_Y);break;}
            default: draw(f,mark,c.points,MARK_Y);
        }
    }
    /** The marks as particle points: circle outlines (small spots as a single point), both edges of a band, both edges of a lane. */
    private static void plot(Cast c){
        c.points.clear();
        for(double[] k:c.marks){
            if(k.length==3){if(k[2]<1)c.points.add(new double[]{k[0],k[1]});else{circle(c.points,k[0],k[1],k[2],Math.max(6,Math.min(20,(int)Math.round(k[2]*5))));c.points.add(new double[]{k[0],k[1]});}}
            else if(k.length==4){circle(c.points,k[0],k[1],k[2],Math.max(8,Math.min(24,(int)Math.round(k[2]*4))));circle(c.points,k[0],k[1],k[3],Math.max(8,Math.min(24,(int)Math.round(k[3]*4))));}
            else if(k.length==5){double dx=k[2]-k[0],dz=k[3]-k[1],len=Math.max(.01,Math.hypot(dx,dz)),nx=-dz/len*k[4],nz=dx/len*k[4];line(c.points,k[0]+nx,k[1]+nz,k[2]+nx,k[3]+nz,1.6);line(c.points,k[0]-nx,k[1]-nz,k[2]-nx,k[3]-nz,1.6);}
        }
        thin(c.points,MAX_POINTS);
    }
    private void draw(BossKits.Fight f,Particle p,List<double[]> pts,double y){for(Player v:kits.viewers(f))for(double[] q:pts)v.spawnParticle(p,q[0],y,q[1],1,0,0,0,0);}
    void aura(BossKits.Fight f){
        List<Player> viewers=kits.viewers(f);if(viewers.isEmpty())return;Location b=f.boss.getLocation();double half=half(f),h=f.boss.getHeight();Particle mark=particle(f.kit.element.mark);
        for(Player v:viewers){
            v.spawnParticle(mark,b.getX(),b.getY()+.2,b.getZ(),3,half*.6,.1,half*.6,0);
            if(f.dread)v.spawnParticle(Particle.SPELL_WITCH,b.getX(),b.getY()+h*.5,b.getZ(),4,half*.5,h*.3,half*.5,0);
            if(f.enraged)v.spawnParticle(Particle.VILLAGER_ANGRY,b.getX(),b.getY()+h+.3,b.getZ(),1,half*.3,.1,half*.3,0);
        }
    }
    /** The ward: a double ring of light around the boss and a thread to each warden that holds it. */
    void ward(BossKits.Fight f){
        Location b=f.boss.getLocation();double h=f.boss.getHeight();List<double[]> pts=new ArrayList<>();circle(pts,b.getX(),b.getZ(),half(f)+.7,12);
        for(Player v:kits.viewers(f))for(double[] q:pts){v.spawnParticle(Particle.SPELL_INSTANT,q[0],b.getY()+.6,q[1],1,0,0,0,0);v.spawnParticle(Particle.SPELL_INSTANT,q[0],b.getY()+h*.6,q[1],1,0,0,0,0);}
        List<double[]> threads=new ArrayList<>();for(LivingEntity w:f.wardens)if(w.isValid()){Location wl=w.getLocation();line(threads,wl.getX(),wl.getZ(),b.getX(),b.getZ(),2);}
        thin(threads,32);draw(f,Particle.CRIT_MAGIC,threads,Layout.FLOOR+2);
    }
    void unward(BossKits.Fight f){Location b=f.boss.getLocation();burst(f,b.clone().add(0,1,0),Particle.SPELL_INSTANT,30);play(f,b,Sound.BLOCK_GLASS_BREAK,1f,.6f);}
    void deflect(BossKits.Fight f,EntityDamageEvent e){
        Location b=f.boss.getLocation();burst(f,b.clone().add(0,1.2,0),Particle.CRIT_MAGIC,8);play(f,b,Sound.ITEM_SHIELD_BLOCK,.9f,.8f);
        if(e instanceof EntityDamageByEntityEvent){Entity d=((EntityDamageByEntityEvent)e).getDamager();if(d instanceof Projectile&&((Projectile)d).getShooter() instanceof Entity)d=(Entity)((Projectile)d).getShooter();
            if(d instanceof Player)((Player)d).sendActionBar(ChatColor.AQUA+"The ward holds: kill the wardens!");}
    }
    void vanish(BossKits.Fight f,List<LivingEntity> clones){for(LivingEntity e:clones)if(e.isValid()&&!e.isDead()){burst(f,e.getLocation().add(0,1,0),Particle.SPELL_WITCH,10);e.remove();}}
    void fell(BossKits.Fight f,Location at){burst(f,at.clone().add(0,1,0),f.kit.guardian?Particle.EXPLOSION_HUGE:Particle.EXPLOSION_LARGE,f.kit.guardian?3:2);burst(f,at.clone().add(0,1,0),particle(f.kit.element.burst),30);}
    /** The fight is over: the move in flight stops, its summons, wardens and copies go (crumbling when the boss fell), snares and spawned entities go. */
    int end(BossKits.Fight f,boolean crumble){
        if(f.cast!=null){abandon(f,f.cast);f.cast=null;}
        int n=0;
        for(List<LivingEntity> list:Arrays.asList(f.summoned,f.wardens,f.clones))for(LivingEntity e:list)if(e.isValid()&&!e.isDead()){if(crumble){burst(f,e.getLocation().add(0,.8,0),Particle.SMOKE_NORMAL,8);n++;}e.remove();}
        if(crumble&&n>0)play(f,f.boss.getLocation(),Sound.BLOCK_GRAVEL_BREAK,.9f,.6f);
        f.summoned.clear();f.wardens.clear();f.clones.clear();f.shieldUntil=0;
        kits.clearWebs(f,true);kits.purge(f.key);
        return n;
    }

    // ---------------------------------------------------------------- helpers
    private static void afflict(BossKits.Fight f,Player p,Element look,int ticks){
        ticks=Math.min(60,ticks);
        switch(look){
            case FIRE: p.setFireTicks(Math.max(p.getFireTicks(),ticks));break;
            case FROST: p.addPotionEffect(new PotionEffect(PotionEffectType.SLOW,ticks,1));break;
            case TIDE: case STONE: p.addPotionEffect(new PotionEffect(PotionEffectType.SLOW,ticks,0));break;
            case ROT: case THORN: p.addPotionEffect(new PotionEffect(PotionEffectType.POISON,ticks,0));break;
            case BONE: case BLOOD: case SHADOW: p.addPotionEffect(new PotionEffect(f.floor>=2?PotionEffectType.WITHER:PotionEffectType.WEAKNESS,ticks,0));break;
            case VOID: p.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS,Math.min(30,ticks),0));break;
            case LIGHT: p.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS,ticks,0));break;
            case IRON: p.addPotionEffect(new PotionEffect(PotionEffectType.SLOW_DIGGING,ticks,0));break;
            default: break;
        }
    }
    /** A cloud's effect: the element's own; Floor I never withers (as its trapped rooms never do). */
    private static PotionEffectType effect(BossKits.Fight f,Element look){
        PotionEffectType t=PotionEffectType.getByName(look.cloud);if(t==null)t=PotionEffectType.POISON;
        return f.floor<2&&t.equals(PotionEffectType.WITHER)?PotionEffectType.POISON:t;
    }
    private void knock(BossKits.Fight f,Player p,Location from,double h,double y){
        if(!kits.eligible(p,f.run))return;Vector v=p.getLocation().toVector().subtract(from.toVector());v.setY(0);if(v.lengthSquared()<1e-4)v=new Vector(1,0,0);
        v.normalize().multiply(h);v.setY(y);p.setVelocity(v);
    }
    private void play(BossKits.Fight f,Location at,Sound s,float volume,float pitch){if(s!=null)for(Player v:kits.viewers(f))v.playSound(at,s,volume,pitch);}
    private void burst(BossKits.Fight f,Location at,Particle p,int n){for(Player v:kits.viewers(f))v.spawnParticle(p,at.getX(),at.getY(),at.getZ(),n,.4,.4,.4,.02);}
    private void erupt(BossKits.Fight f,Location at,Element look){for(Player v:kits.viewers(f)){v.spawnParticle(Particle.SMOKE_LARGE,at.getX(),at.getY()+.2,at.getZ(),8,.4,.1,.4,.02);v.spawnParticle(particle(look.burst),at.getX(),at.getY()+.4,at.getZ(),10,.4,.3,.4,.02);}}
    private double half(BossKits.Fight f){return plugin.encounters.bodyOf(f.boss).width/2;}
    private static double speed(BossKits.Fight f){return .85+.12*(f.floor-1);}
    private static double burstRadius(BossKits.Fight f){return 2.5+.25*(f.floor-1);}
    /** Bodies that cannot be thrown about (a shulker, a body without AI) never charge, leap or blink. */
    private static boolean mobile(BossKits.Fight f){return !(f.boss instanceof Shulker)&&f.boss.hasAI();}
    private static float yaw(double dx,double dz){return (float)Math.toDegrees(Math.atan2(-dx,dz));}
    /** The nearest cell (within four) where the boss's whole scaled body fits on the floor, or null. */
    private double[] landing(BossKits.Fight f,double x,double z){
        int bx=(int)Math.floor(x),bz=(int)Math.floor(z);
        for(int r=0;r<=4;r++)for(int dx=-r;dx<=r;dx++)for(int dz=-r;dz<=r;dz++){if(Math.max(Math.abs(dx),Math.abs(dz))!=r)continue;double cx=bx+dx+.5,cz=bz+dz+.5;if(fits(f,cx,cz))return new double[]{cx,cz};}
        return null;
    }
    private boolean fits(BossKits.Fight f,double x,double z){
        final World w=f.run.world;if(!w.isChunkLoaded((int)Math.floor(x)>>4,(int)Math.floor(z)>>4))return false;Scaling.Body body=plugin.encounters.bodyOf(f.boss);
        return EncounterCatalog.fits(f.run.room,body.width,body.height,x,Layout.FLOOR+1,z,new EncounterCatalog.Blocks(){
            public boolean air(int bx,int by,int bz){return w.getBlockAt(bx,by,bz).getType()==Material.AIR;}
            public boolean floor(int bx,int by,int bz){Material m=w.getBlockAt(bx,by,bz).getType();return m.isOccluding()&&m!=Material.MAGMA&&m!=Material.CACTUS&&m!=Material.SOUL_SAND;}
        });
    }
    /** Moves the boss within its room, only where its whole body fits; never out of the room. */
    private boolean teleport(BossKits.Fight f,double x,double z,float yaw){
        if(!fits(f,x,z))return false;boolean ok=f.boss.teleport(new Location(f.run.world,x,Layout.FLOOR+1,z,yaw,0));
        if(ok){f.boss.setFallDistance(0);f.boss.setVelocity(new Vector());}return ok;
    }
    private void scatter(BossKits.Fight f,Cast c,List<Player> targets,double radius,double spread){
        Layout.Room r=f.run.room;List<Player> ts=new ArrayList<>(targets);Collections.shuffle(ts,random);
        for(Player p:ts){if(c.marks.size()>=Math.min(3,c.count))break;Location l=p.getLocation();double[] q=inside(r,l.getX(),l.getZ());if(apart(c,q[0],q[1],radius*1.5))c.marks.add(new double[]{q[0],q[1],radius});}
        for(int k=0;k<48&&c.marks.size()<c.count&&!ts.isEmpty();k++){
            Location l=ts.get(random.nextInt(ts.size())).getLocation();double a=random.nextDouble()*Math.PI*2,d=2+random.nextDouble()*spread;
            double[] q=inside(r,l.getX()+Math.cos(a)*d,l.getZ()+Math.sin(a)*d);if(apart(c,q[0],q[1],radius*1.5))c.marks.add(new double[]{q[0],q[1],radius});
        }
    }
    private static List<LivingEntity> nearest(List<LivingEntity> list,Location at,double radius,int n){
        List<LivingEntity> out=new ArrayList<>();for(LivingEntity e:list)if(e.isValid()&&!e.isDead()&&e.getWorld().equals(at.getWorld())&&e.getLocation().distanceSquared(at)<=radius*radius)out.add(e);
        out.sort(Comparator.comparingDouble(e->e.getLocation().distanceSquared(at)));return out.size()>n?new ArrayList<>(out.subList(0,n)):out;
    }
    /** The boss's own summons nearest to its foes (within fourteen cells of one). */
    private static List<LivingEntity> menacing(BossKits.Fight f,List<Player> targets,int n){
        final Map<LivingEntity,Double> d=new HashMap<>();
        for(LivingEntity e:f.summoned){if(!e.isValid()||e.isDead())continue;double best=1e18;Location el=e.getLocation();for(Player p:targets)if(p.getWorld().equals(el.getWorld()))best=Math.min(best,el.distanceSquared(p.getLocation()));if(best<=14*14)d.put(e,best);}
        List<LivingEntity> out=new ArrayList<>(d.keySet());out.sort(Comparator.comparingDouble(d::get));return out.size()>n?new ArrayList<>(out.subList(0,n)):out;
    }
    /** Clamped into the room's interior, where hits are allowed (the doorway margin stays a reliable escape). */
    private static double[] inside(Layout.Room r,double x,double z){double m=EncounterCatalog.EXIT_MARGIN+.5;return new double[]{Math.max(r.x+m,Math.min(r.x+r.w-m,x)),Math.max(r.z+m,Math.min(r.z+r.d-m,z))};}
    /** How far a line from (x, z) at angle a may run before it leaves the interior. */
    private static double clip(Layout.Room r,double x,double z,double a,double len){double s=0;while(s+.5<=len&&EncounterCatalog.hazardAllowed(r,x+Math.cos(a)*(s+.5),Layout.FLOOR+1,z+Math.sin(a)*(s+.5)))s+=.5;return s;}
    static double offSegment(double[] s,double x,double z){double dx=s[2]-s[0],dz=s[3]-s[1],l2=dx*dx+dz*dz,t=l2<1e-9?0:Math.max(0,Math.min(1,((x-s[0])*dx+(z-s[1])*dz)/l2));return Math.hypot(x-(s[0]+t*dx),z-(s[1]+t*dz));}
    private static void circle(List<double[]> out,double x,double z,double r,int n){for(int i=0;i<n;i++){double a=Math.PI*2*i/n;out.add(new double[]{x+Math.cos(a)*r,z+Math.sin(a)*r});}}
    private static void line(List<double[]> out,double x1,double z1,double x2,double z2,double step){double len=Math.hypot(x2-x1,z2-z1);int k=Math.max(1,(int)Math.ceil(len/step));for(int i=0;i<=k;i++){double t=i/(double)k;out.add(new double[]{x1+(x2-x1)*t,z1+(z2-z1)*t});}}
    private static void thin(List<double[]> pts,int max){if(pts.size()<=max)return;List<double[]> keep=new ArrayList<>(max);for(int i=0;i<max;i++)keep.add(pts.get(i*pts.size()/max));pts.clear();pts.addAll(keep);}
    private static boolean apart(Cast c,double x,double z,double d){for(double[] k:c.marks)if(Math.hypot(k[0]-x,k[1]-z)<d)return false;return true;}
    private static boolean all(boolean[] b){if(b.length==0)return false;for(boolean v:b)if(!v)return false;return true;}
    /** Open floor: a solid, cool block underfoot and two cells of air. Read only. */
    private static boolean standable(World w,double x,double z){
        int bx=(int)Math.floor(x),bz=(int)Math.floor(z);if(!w.isChunkLoaded(bx>>4,bz>>4))return false;Material below=w.getBlockAt(bx,Layout.FLOOR,bz).getType();
        return below.isOccluding()&&below!=Material.MAGMA&&w.getBlockAt(bx,Layout.FLOOR+1,bz).getType()==Material.AIR&&w.getBlockAt(bx,Layout.FLOOR+2,bz).getType()==Material.AIR;
    }
    private static boolean nearChest(Layout.Room r,int x,int z){return Math.abs(x-r.cx())<=1&&Math.abs(z-(r.cz()+4))<=1||r.finale()&&Math.abs(x-r.cx())<=2&&Math.abs(z-r.cz())<=2;}
    /** Highest air block of the clear column standing on the floor, from 'from' up to max; -1 when blocked or not loaded. Read only. */
    private static int column(World w,double x,double z,int from,int max){
        int bx=(int)Math.floor(x),bz=(int)Math.floor(z);if(from>max||!w.isChunkLoaded(bx>>4,bz>>4)||w.getBlockAt(bx,from,bz).getType()!=Material.AIR)return -1;
        int y=from;while(y<max&&w.getBlockAt(bx,y+1,bz).getType()==Material.AIR)y++;return y;
    }
    private Particle particle(String name){return particles.computeIfAbsent(name,n->{try{return Particle.valueOf(n);}catch(IllegalArgumentException ex){return Particle.SPELL_WITCH;}});}
    private Sound sound(String name){return sounds.computeIfAbsent(name,n->{try{return Sound.valueOf(n);}catch(IllegalArgumentException ex){return Sound.BLOCK_NOTE_BASS;}});}
    private Material material(String name){return blocks.computeIfAbsent(name,n->{Material m=Material.getMaterial(n);return m==null||!m.isBlock()?Material.COBBLESTONE:m;});}
    static String pretty(EncounterCatalog.Species s){StringBuilder b=new StringBuilder();for(String w:s.name().toLowerCase(Locale.ROOT).split("_")){if(b.length()>0)b.append(' ');b.append(Character.toUpperCase(w.charAt(0))).append(w.substring(1));}return b.toString();}
}
