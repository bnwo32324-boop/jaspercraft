package chat.jaspr.dungeon;

import java.util.*;
import java.util.function.Supplier;
import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.entity.*;
import org.bukkit.event.hanging.HangingBreakEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.ChunkUnloadEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.*;
import org.bukkit.projectiles.ProjectileSource;
import org.bukkit.util.Vector;

/**
 * Generation 5 (owner 2026-10-03): some rooms are trapped, and seizing dangers (gravity well, shockwave, creeping dark,
 * frost gusts) are rare and "disabled if you conquer the room".
 *
 * The runtime half of HazardCatalog. A trapped room cycles through its live dangers: each one picks a player who can
 * be hurt, telegraphs from the marks the generator drew near them, strikes, then rests. Once a room is absolved (every
 * enemy killed) its seizing dangers fall still at once -- a pending strike is dropped and a well's pull ends -- and its
 * ordinary dangers carry on at a slower pace. Traps never place, break or change a block, never aim into the arrival
 * circle, and only ever wound eligible players; whatever they spawn is tagged, tracked and removed when the room
 * sleeps. A room whose traps fail is switched off on its own and logged once.
 */
public final class Hazards implements Listener {
    static final String TAG="jpd_trap";
    private static final int MAX_TRAPS=256,MAX_KEYS=4096;
    private final DungeonPlugin plugin;private final Random random=new Random();
    private final Map<String,Site> sites=new HashMap<>();private final Map<UUID,Trap> traps=new LinkedHashMap<>();
    private final Set<String> armed=new LinkedHashSet<>(),disabled=new HashSet<>(),warned=new HashSet<>();
    private Map<String,Encounters.Run> runs=Collections.emptyMap();private Encounters.Run blasting;private double blastCap;private long now;

    private interface Mark{boolean at(int x,int z);}
    /** One telegraphed strike. Points are {x, z, a, b}: wall fronts carry the inward direction, masonry its drop height. */
    private static final class Plan {
        final HazardCatalog.Type type;final long at;final UUID target;final double tx,ty,tz;final List<double[]> points=new ArrayList<>();double[] line;
        Plan(HazardCatalog.Type type,long at,Player target){this.type=type;this.at=at;this.target=target.getUniqueId();Location l=target.getLocation();tx=l.getX();ty=l.getY();tz=l.getZ();}
    }
    /** A shockwave ring or a gravity well, which keeps acting for a few ticks after its strike. */
    private static final class Wave {
        final HazardCatalog.Type type;final double x,z,max,damage;final long start;double radius;final Set<UUID> hit=new HashSet<>();
        Wave(HazardCatalog.Type type,double x,double z,double max,double damage,long start){this.type=type;this.x=x;this.z=z;this.max=max;this.damage=damage;this.start=start;}
    }
    private static final class Site {final Encounters.Run run;final long start;long next;int cursor;Plan plan;Wave wave;Site(Encounters.Run run,long now){this.run=run;start=now;next=now+20;}}
    private static final class Trap {
        final Entity entity;final double damage;final long born;final int life;final String room;boolean done;
        Trap(Entity entity,double damage,long born,int life,String room){this.entity=entity;this.damage=damage;this.born=born;this.life=life;this.room=room;}
    }

    public Hazards(DungeonPlugin plugin){this.plugin=plugin;}

    // ---------------------------------------------------------------- lifecycle
    /** Called by Encounters once per server tick, after presence was refreshed. Never throws. */
    public void tick(Map<String,Encounters.Run> active){
        now++;runs=active==null?Collections.<String,Encounters.Run>emptyMap():active;
        try{
            if(now%20==0)sites.keySet().retainAll(runs.keySet());
            for(Encounters.Run run:new ArrayList<>(runs.values())){
                if(run==null||run.world==null||run.room.hazards.length==0||disabled.contains(run.key))continue;
                Site s=sites.get(run.key);
                // Absolved: the room's seizing dangers fall still at once, mid-telegraph or mid-pull.
                if(run.state.cleared&&s!=null){
                    if(s.plan!=null&&HazardCatalog.seizes(s.plan.type)){s.plan=null;s.next=Math.max(s.next,now+20);}
                    if(s.wave!=null&&HazardCatalog.seizes(s.wave.type))s.wave=null;
                }
                if(HazardCatalog.live(run.room,run.state.cleared).length==0){if(s!=null){s.plan=null;s.wave=null;}continue;}
                // An empty room forgets its pending strike, so nobody walks back into a stale telegraph.
                if(run.players.isEmpty()){if(s!=null){s.plan=null;s.wave=null;s.next=Math.max(s.next,now+40);}continue;}
                try{if(s==null||s.run!=run){s=new Site(run,now);sites.put(run.key,s);arm(run);}step(s);}
                catch(RuntimeException ex){disable(run.key,ex);}
            }
            sweep();
        }catch(RuntimeException ex){if(warned.add("tick"))plugin.getLogger().warning("DUNGEON_HAZARD_TICK_FAILED "+ex);}
    }
    public void sleep(String roomKey){
        if(roomKey==null)return;sites.remove(roomKey);
        Iterator<Trap> it=traps.values().iterator();while(it.hasNext()){Trap t=it.next();if(t.room.equals(roomKey)){t.done=true;t.entity.remove();it.remove();}}
    }
    public void close(){sites.clear();for(Trap t:traps.values()){t.done=true;t.entity.remove();}traps.clear();runs=Collections.emptyMap();blasting=null;}
    /** Test hook for the separately packaged probe: plan a strike of this type now against an eligible player. */
    public boolean force(Encounters.Run run,HazardCatalog.Type type){
        if(run==null||run.world==null||disabled.contains(run.key))return false;
        if(run.state.cleared&&HazardCatalog.seizes(type))return false;   // stilled in an absolved room, forced or not
        Site s=sites.get(run.key);if(s==null||s.run!=run){s=new Site(run,now-40);sites.put(run.key,s);arm(run);}
        List<Player> targets=eligible(run);if(targets.isEmpty()||s.plan!=null)return false;
        Plan p=new Plan(type,now+windup(run),targets.get(0));plan(run,p);if(p.points.isEmpty()&&p.line==null)return false;
        s.plan=p;cue(run,p);telegraph(run,p);return true;
    }
    public boolean disabled(String key){return disabled.contains(key);}
    public int traps(){return traps.size();}
    /** Test and diagnostic view of a room's pending strike and lingering wave: "plan=TYPE wave=TYPE" or "-". */
    public String pending(String key){Site s=sites.get(key);return "plan="+(s==null||s.plan==null?"-":s.plan.type.name())+" wave="+(s==null||s.wave==null?"-":s.wave.type.name());}
    private void arm(Encounters.Run run){
        if(!armed.add(run.key))return;if(armed.size()>MAX_KEYS){Iterator<String> it=armed.iterator();it.next();it.remove();}
        StringBuilder b=new StringBuilder();for(HazardCatalog.Type t:run.room.hazards){if(b.length()>0)b.append(',');b.append(t.name());}
        plugin.getLogger().info("DUNGEON_HAZARDS_ARMED room="+run.key+" types="+b);
    }
    private void disable(String key,RuntimeException ex){sites.remove(key);if(disabled.size()<MAX_KEYS&&disabled.add(key))plugin.getLogger().warning("DUNGEON_HAZARD_DISABLED room="+key+" "+ex);}

    private void step(Site s){
        Encounters.Run run=s.run;
        if(s.wave!=null)wave(s);
        Plan p=s.plan;
        if(p!=null){
            if(now<p.at){if((p.at-now)%4==0)telegraph(run,p);return;}
            s.plan=null;fire(s,p);s.next=now+period(run);return;
        }
        if(now<s.next)return;
        List<Player> targets=eligible(run);if(targets.isEmpty()){s.next=now+20;return;}
        HazardCatalog.Type[] live=HazardCatalog.live(run.room,run.state.cleared);if(live.length==0){s.next=now+20;return;}
        HazardCatalog.Type type=live[Math.floorMod(s.cursor,live.length)];
        if(s.wave!=null&&(type==HazardCatalog.Type.SHOCKWAVE||type==HazardCatalog.Type.GRAVITY_WELL)){s.next=now+10;return;}
        s.cursor++;
        p=new Plan(type,Math.max(now+windup(run),s.start+40),targets.get(random.nextInt(targets.size())));
        plan(run,p);
        if(p.points.isEmpty()&&p.line==null){s.next=now+20;return;}
        s.plan=p;cue(run,p);telegraph(run,p);
    }
    private static int tier(Encounters.Run run){return EncounterCatalog.threat(run.room.tier);}
    private static int windup(Encounters.Run run){return Math.max(16,26-2*tier(run));}
    private static long period(Encounters.Run run){double p=Math.max(50,100-8*tier(run))+(run.room.kind==Layout.Kind.BOSS?30:0);return (long)(run.state.cleared?p*1.5:p);}
    private double base(Encounters.Run run){return (1.5+.9*tier(run))*Math.max(1,Math.min(1.45,plugin.dangerMultiplier(run.world)));}

    // ---------------------------------------------------------------- who may be hurt
    private List<Player> eligible(Encounters.Run run){List<Player> out=new ArrayList<>();for(UUID id:run.players){Player p=Bukkit.getPlayer(id);if(eligible(p,run))out.add(p);}return out;}
    /** Only these players are ever hurt: alive, in Survival or Adventure, standing in this very room and outside the refuge. */
    private boolean eligible(Player p,Encounters.Run run){
        if(p==null||run==null||!p.isOnline()||p.isDead()||!run.players.contains(p.getUniqueId())||!p.getWorld().equals(run.world)||plugin.sanctuary==null)return false;
        GameMode g=p.getGameMode();if(g!=GameMode.SURVIVAL&&g!=GameMode.ADVENTURE)return false;
        Location l=p.getLocation();return run.key.equals(plugin.roomKey(l))&&!plugin.sanctuary.contains(l);
    }
    /** Damage callbacks can move or kill a player; the result says whether they are still a target for a status. */
    private boolean hurt(Encounters.Run run,Player v,double amount){
        if(!eligible(v,run)||HazardCatalog.safe(run.room,v.getLocation().getX(),v.getLocation().getZ()))return false;
        v.damage(amount);return eligible(v,run);
    }
    private List<Player> area(Encounters.Run run,double x,double z,double radius,double yMax,double amount,Set<UUID> once){
        List<Player> out=new ArrayList<>();
        for(Player v:eligible(run)){Location l=v.getLocation();if(l.getY()<yMax&&flat(l,x,z)<=radius&&once.add(v.getUniqueId())&&hurt(run,v,amount))out.add(v);}
        return out;
    }
    private List<Player> swept(Encounters.Run run,double[] line,double width,double amount){
        List<Player> out=new ArrayList<>();
        for(Player v:eligible(run)){Location l=v.getLocation();if(l.getY()<68.5&&offLine(line,l.getX(),l.getZ())<=width&&hurt(run,v,amount))out.add(v);}
        return out;
    }

    // ---------------------------------------------------------------- planning: from the marks near the target
    private void plan(Encounters.Run run,Plan p){
        final Layout.Room r=run.room;final World w=run.world;final double tx=p.tx,tz=p.tz;final int tier=tier(run);
        switch(p.type){
            case FLAME_VENTS:{for(int[] c:near(r,tx,tz,5,4,(mx,mz)->HazardCatalog.vent(mx,mz)&&HazardCatalog.open(r,mx,mz)))add(p,r,c[0]+.5,c[1]+.5,0);add(p,r,tx,tz,0);break;}
            case DART_SLITS:{p.points.addAll(walls(r,tx,tz,12,3,(mx,mz)->HazardCatalog.slit(r,mx,mz)));if(p.points.isEmpty())p.points.add(edge(r,tx,tz));break;}
            case EMBER_BOLTS:{p.points.addAll(walls(r,tx,tz,14,2,(mx,mz)->HazardCatalog.socket(r,mx,mz)));if(p.points.isEmpty())p.points.add(edge(r,tx,tz));break;}
            case SPIKE_RUNES: runes(r,p);break;
            case FALLING_MASONRY:{for(int[] c:near(r,tx,tz,3,2,HazardCatalog::crack))drop(w,r,p,c[0]+.5,c[1]+.5,Layout.FLOOR+1);drop(w,r,p,tx,tz,(int)Math.floor(p.ty+.5));break;}
            case MIASMA:{for(int[] c:near(r,tx,tz,5,2,(mx,mz)->HazardCatalog.seep(mx,mz)&&HazardCatalog.open(r,mx,mz)))add(p,r,c[0]+.5,c[1]+.5,0);if(p.points.isEmpty())add(p,r,tx,tz,0);break;}
            case FROST_GUSTS:{int row=nearest((int)Math.floor(tz),10,5);double z=Math.abs(tz-(row+.5))<=4&&row+.5>=r.z+3&&row+.5<r.z+r.d-3?row+.5:clampZ(r,tz);p.line=new double[]{r.x+3,z,r.x+r.w-3,z};break;}
            case SMITE:{add(p,r,tx,tz,0);if(tier>=3){double ang=random.nextDouble()*Math.PI*2,dd=2+random.nextDouble();add(p,r,tx+Math.cos(ang)*dd,tz+Math.sin(ang)*dd,0);}break;}
            case SHOCKWAVE:{double[] c=crossing(r,tx,tz);double cx=r.cx()+.5,cz=r.cz()+.5;if(dist2(c[0],c[1],tx,tz)<dist2(cx,cz,tx,tz)&&fits(r,c[0],c[1])){cx=c[0];cz=c[1];}if(!add(p,r,cx,cz,0))add(p,r,tx,tz,0);break;}
            case PHANTOM_BLADES: blades(r,p);break;
            case POTION_RAIN:{int n=2+tier/2;for(int[] c:near(r,tx,tz,3,n,HazardCatalog::spout))add(p,r,c[0]+.5,c[1]+.5,0);
                for(int k=0;k<16&&p.points.size()<n;k++){double ang=random.nextDouble()*Math.PI*2,dd=random.nextDouble()*3;add(p,r,tx+Math.cos(ang)*dd,tz+Math.sin(ang)*dd,0);}break;}
            case GRAVITY_WELL:{double[] c=crossing(r,tx,tz);if(dist2(c[0],c[1],tx,tz)>81||!add(p,r,c[0],c[1],0)){double ang=random.nextDouble()*Math.PI*2;add(p,r,tx+Math.cos(ang)*3,tz+Math.sin(ang)*3,0);}if(p.points.isEmpty())add(p,r,tx,tz,0);break;}
            case CREEPING_DARK: add(p,r,tx,tz,0);break;
            case BLAST_SPORES:{
                // A pod sits against the wall; it bursts one step into the room.
                for(int[] c:near(r,tx,tz,8,2,(mx,mz)->HazardCatalog.pod(r,mx,mz))){int rx=c[0]-r.x,rz=c[1]-r.z;add(p,r,c[0]+.5+(rx==2?1:rx==r.w-3?-1:0),c[1]+.5+(rz==2?1:rz==r.d-3?-1:0),0);}
                if(p.points.isEmpty()){double ang=random.nextDouble()*Math.PI*2,dd=2+random.nextDouble();if(!add(p,r,tx+Math.cos(ang)*dd,tz+Math.sin(ang)*dd,0))add(p,r,tx,tz,0);}break;}
            default: break;
        }
    }
    /** Marked cells of this room within radius of the target, nearest first: {x, z, distance squared}. */
    private static List<int[]> near(Layout.Room r,double tx,double tz,int radius,int max,Mark m){
        int bx=(int)Math.floor(tx),bz=(int)Math.floor(tz);List<int[]> out=new ArrayList<>();
        for(int x=Math.max(r.x,bx-radius);x<=Math.min(r.x+r.w-1,bx+radius);x++)for(int z=Math.max(r.z,bz-radius);z<=Math.min(r.z+r.d-1,bz+radius);z++){
            int d=(x-bx)*(x-bx)+(z-bz)*(z-bz);if(d<=radius*radius&&m.at(x,z))out.add(new int[]{x,z,d});
        }
        out.sort(Comparator.comparingInt((int[] c)->c[2]));return out.size()>max?out.subList(0,max):out;
    }
    /** Inner-wall marks as launch points {x, z, dx, dz, d2}: those within reach of the target, else the single nearest. */
    private static List<double[]> walls(Layout.Room r,double tx,double tz,int reach,int max,Mark m){
        List<double[]> all=new ArrayList<>();
        for(int a=0;a<r.d;a++){face(all,r,r.x+1,r.z+a,tx,tz,m);face(all,r,r.x+r.w-2,r.z+a,tx,tz,m);}
        for(int a=0;a<r.w;a++){face(all,r,r.x+a,r.z+1,tx,tz,m);face(all,r,r.x+a,r.z+r.d-2,tx,tz,m);}
        all.sort(Comparator.comparingDouble((double[] f)->f[4]));
        List<double[]> out=new ArrayList<>();for(double[] f:all)if(out.size()<max&&f[4]<=reach*reach)out.add(f);
        if(out.isEmpty()&&!all.isEmpty())out.add(all.get(0));return out;
    }
    private static void face(List<double[]> out,Layout.Room r,int x,int z,double tx,double tz,Mark m){
        if(!m.at(x,z))return;double[] f=HazardCatalog.front(r,x,z);out.add(new double[]{f[0],f[1],f[2],f[3],dist2(f[0],f[1],tx,tz)});
    }
    /** Fallback launch point: the strip in front of the nearest wall, level with the target. */
    private static double[] edge(Layout.Room r,double tx,double tz){
        double w=tx-r.x,e=r.x+r.w-tx,n=tz-r.z,s=r.z+r.d-tz,m=Math.min(Math.min(w,e),Math.min(n,s));
        if(m==w)return new double[]{r.x+2.5,clampZ(r,tz),1,0};if(m==e)return new double[]{r.x+r.w-2.5,clampZ(r,tz),-1,0};
        if(m==n)return new double[]{clampX(r,tx),r.z+2.5,0,1};return new double[]{clampX(r,tx),r.z+r.d-2.5,0,-1};
    }
    /** Eleven fangs flaring out from the nearest rune channel along its direction, through where the target stands. */
    private void runes(Layout.Room r,Plan p){
        double cx=clampX(r,p.tx),cz=clampZ(r,p.tz);int col=nearest((int)Math.floor(p.tx),16,8),row=nearest((int)Math.floor(p.tz),16,8);
        double dc=Math.abs(p.tx-(col+.5)),dr=Math.abs(p.tz-(row+.5));
        boolean colOk=dc<=4&&col+.5>=r.x+3&&col+.5<r.x+r.w-3,rowOk=dr<=4&&row+.5>=r.z+3&&row+.5<r.z+r.d-3,alongZ;double at;
        if(colOk&&(!rowOk||dc<=dr))alongZ=true;else if(rowOk)alongZ=false;else alongZ=random.nextBoolean();at=alongZ?Math.floor(cx)+.5:Math.floor(cz)+.5;
        for(int k=-5;k<=5;k++){double x=alongZ?at:Math.floor(cx)+.5+k,z=alongZ?Math.floor(cz)+.5+k:at;if(fits(r,x,z))p.points.add(new double[]{x,z,0,0});}
    }
    /** The blade track (every eighth row or column) nearest the target, across the whole interior. */
    private static void blades(Layout.Room r,Plan p){
        int col=Math.max(r.x+8,Math.min(r.x+r.w-8,nearest((int)Math.floor(p.tx),8,0))),row=Math.max(r.z+8,Math.min(r.z+r.d-8,nearest((int)Math.floor(p.tz),8,0)));
        p.line=Math.abs(p.tx-(col+.5))<=Math.abs(p.tz-(row+.5))?new double[]{col+.5,r.z+3,col+.5,r.z+r.d-3}:new double[]{r.x+3,row+.5,r.x+r.w-3,row+.5};
    }
    /** A masonry point at a cell centre, dropped from the top of the clear column above it (chandeliers and webs stop it). */
    private static void drop(World w,Layout.Room r,Plan p,double x,double z,int from){
        if(p.points.size()>=3)return;x=Math.floor(clampX(r,x))+.5;z=Math.floor(clampZ(r,z))+.5;
        int top=column(w,x,z,Math.max(Layout.FLOOR+1,from),r.roof()-1);if(top>=Layout.FLOOR+2)add(p,r,x,z,top);
    }
    /** The lane crossing (x%32==16, z%32==16) of this room nearest the target, as a cell centre. */
    private static double[] crossing(Layout.Room r,double tx,double tz){
        int x=Math.max(r.x+16,Math.min(r.x+r.w-16,nearest((int)Math.floor(tx),32,16))),z=Math.max(r.z+16,Math.min(r.z+r.d-16,nearest((int)Math.floor(tz),32,16)));
        return new double[]{x+.5,z+.5};
    }
    /** Adds an interior point, pulled inside when needed and kept apart from the points already chosen. */
    private static boolean add(Plan p,Layout.Room r,double x,double z,double extra){
        x=clampX(r,x);z=clampZ(r,z);if(!fits(r,x,z))return false;
        for(double[] q:p.points)if(Math.abs(q[0]-x)<1&&Math.abs(q[1]-z)<1)return false;
        p.points.add(new double[]{x,z,extra,0});return true;
    }
    /** Strike points stay in the interior (relative 3..w-4, 3..d-4) and out of the arrival circle. */
    private static boolean fits(Layout.Room r,double x,double z){return x>=r.x+3&&x<r.x+r.w-3&&z>=r.z+3&&z<r.z+r.d-3&&!HazardCatalog.safe(r,x,z);}
    private static double clampX(Layout.Room r,double x){return Math.max(r.x+3.3,Math.min(r.x+r.w-3.3,x));}
    private static double clampZ(Layout.Room r,double z){return Math.max(r.z+3.3,Math.min(r.z+r.d-3.3,z));}
    private static int nearest(int v,int m,int rem){int below=v-Math.floorMod(v-rem,m);return v-below<=m/2?below:below+m;}
    private static double dist2(double ax,double az,double bx,double bz){return (ax-bx)*(ax-bx)+(az-bz)*(az-bz);}
    private static double flat(Location l,double x,double z){return Math.sqrt(dist2(l.getX(),l.getZ(),x,z));}
    /** Distance from an axis-aligned interior line; 99 beyond its ends, so a line never reaches past the interior. */
    private static double offLine(double[] l,double x,double z){
        if(l[0]==l[2])return z<l[1]||z>=l[3]?99:Math.abs(x-l[0]);
        return x<l[0]||x>=l[2]?99:Math.abs(z-l[1]);
    }
    private static boolean loaded(World w,double x,double z){return w.isChunkLoaded((int)Math.floor(x)>>4,(int)Math.floor(z)>>4);}
    /** Highest air block of the clear column standing on the floor from 'from' up to max; -1 when blocked or not loaded. Read only. */
    private static int column(World w,double x,double z,int from,int max){
        if(from>max||!loaded(w,x,z))return -1;int bx=(int)Math.floor(x),bz=(int)Math.floor(z),y=from;
        if(w.getBlockAt(bx,y,bz).getType()!=Material.AIR)return -1;
        while(y<max&&w.getBlockAt(bx,y+1,bz).getType()==Material.AIR)y++;return y;
    }
    private static double[] anchor(Plan p){
        if(!p.points.isEmpty())return p.points.get(0);
        double[] l=p.line;return l[0]==l[2]?new double[]{l[0],Math.max(l[1],Math.min(l[3],p.tz))}:new double[]{Math.max(l[0],Math.min(l[2],p.tx)),l[1]};
    }

    // ---------------------------------------------------------------- telegraphs (at most ~40 particles a refresh)
    private void cue(Encounters.Run run,Plan p){
        Sound s;float pitch;
        switch(p.type){
            case FLAME_VENTS: s=Sound.ITEM_FIRECHARGE_USE;pitch=.6f;break;
            case DART_SLITS: s=Sound.BLOCK_DISPENSER_FAIL;pitch=.6f;break;
            case SPIKE_RUNES: s=Sound.ENTITY_EVOCATION_ILLAGER_PREPARE_ATTACK;pitch=.8f;break;
            case FALLING_MASONRY: s=Sound.BLOCK_GRAVEL_BREAK;pitch=.5f;break;
            case MIASMA: s=Sound.BLOCK_BREWING_STAND_BREW;pitch=.6f;break;
            case FROST_GUSTS: s=Sound.BLOCK_SNOW_BREAK;pitch=.5f;break;
            case SMITE: s=Sound.BLOCK_END_PORTAL_FRAME_FILL;pitch=1.4f;break;
            case SHOCKWAVE: s=Sound.ENTITY_ENDERDRAGON_GROWL;pitch=.5f;break;
            case PHANTOM_BLADES: s=Sound.ENTITY_EVOCATION_ILLAGER_CAST_SPELL;pitch=1.3f;break;
            case POTION_RAIN: s=Sound.ENTITY_WITCH_AMBIENT;pitch=.8f;break;
            case GRAVITY_WELL: s=Sound.BLOCK_PORTAL_AMBIENT;pitch=.6f;break;
            case CREEPING_DARK: s=Sound.ENTITY_ILLUSION_ILLAGER_PREPARE_BLINDNESS;pitch=.7f;break;
            case BLAST_SPORES: s=Sound.ENTITY_CREEPER_PRIMED;pitch=.7f;break;
            case EMBER_BOLTS: default: s=Sound.ENTITY_BLAZE_AMBIENT;pitch=.7f;break;
        }
        double[] at=anchor(p);run.world.playSound(new Location(run.world,at[0],66,at[1]),s,p.type==HazardCatalog.Type.SHOCKWAVE?.45f:.8f,pitch);
    }
    private void telegraph(Encounters.Run run,Plan p){
        World w=run.world;
        switch(p.type){
            case FLAME_VENTS: for(double[] q:p.points){w.spawnParticle(Particle.SMOKE_LARGE,q[0],65.1,q[1],3,.15,.05,.15,.01);w.spawnParticle(Particle.LAVA,q[0],65.2,q[1],1,.1,0,.1,0);}break;
            case DART_SLITS: for(double[] q:p.points)w.spawnParticle(Particle.SMOKE_NORMAL,q[0]-q[2]*.4,66.5,q[1]-q[3]*.4,4,.04,.08,.04,.005);break;
            case EMBER_BOLTS: for(double[] q:p.points){w.spawnParticle(Particle.FLAME,q[0]-q[2]*.4,67.5,q[1]-q[3]*.4,4,.06,.08,.06,.004);w.spawnParticle(Particle.SMOKE_NORMAL,q[0]-q[2]*.4,67.8,q[1]-q[3]*.4,2,.05,.05,.05,.01);}break;
            case SPIKE_RUNES: for(double[] q:p.points)w.spawnParticle(Particle.SPELL_WITCH,q[0],65.15,q[1],2,.2,0,.2,0);break;
            case FALLING_MASONRY: for(double[] q:p.points){w.spawnParticle(Particle.CLOUD,q[0],q[2]+.7,q[1],2,.25,.05,.25,0);w.spawnParticle(Particle.SUSPENDED_DEPTH,q[0],q[2]+.4,q[1],6,.3,.3,.3,0);w.spawnParticle(Particle.CLOUD,q[0],65.1,q[1],1,.3,0,.3,0);}break;
            case MIASMA: for(double[] q:p.points){tint(w,q[0],65.2,q[1],1.8,10,.3,.85,.15);tint(w,q[0],65.4,q[1],0,1,.3,.85,.15);}break;
            case FROST_GUSTS: line(w,Particle.SNOW_SHOVEL,p.line,65.4,40);break;
            case SMITE: for(double[] q:p.points){ring(w,Particle.SPELL_INSTANT,q[0],65.15,q[1],1.5,10);w.spawnParticle(Particle.SPELL_INSTANT,q[0],66,q[1],3,.1,.6,.1,0);}break;
            case SHOCKWAVE:{double[] q=p.points.get(0);w.spawnParticle(Particle.CRIT,q[0],65.3,q[1],12,.5,.1,.5,.05);ring(w,Particle.CRIT,q[0],65.2,q[1],1.5,12);break;}
            case PHANTOM_BLADES: line(w,Particle.CRIT_MAGIC,p.line,65.9,36);break;
            case POTION_RAIN: for(double[] q:p.points)ring(w,Particle.SPELL_WITCH,q[0],65.15,q[1],.9,6);break;
            case GRAVITY_WELL:{double[] q=p.points.get(0);w.spawnParticle(Particle.PORTAL,q[0],65.6,q[1],24,1.5,.3,1.5,.8);ring(w,Particle.SPELL_WITCH,q[0],65.15,q[1],1.3,8);break;}
            case CREEPING_DARK:{double[] q=p.points.get(0);ring(w,Particle.SMOKE_LARGE,q[0],65.3,q[1],5,20);w.spawnParticle(Particle.SMOKE_LARGE,q[0],65.5,q[1],6,.6,.2,.6,0);break;}
            case BLAST_SPORES: for(double[] q:p.points){tint(w,q[0],65.4,q[1],.8,8,.55,.8,.2);w.spawnParticle(Particle.SMOKE_NORMAL,q[0],65.6,q[1],2,.2,.1,.2,.01);}break;
            default: break;
        }
    }
    private static void ring(World w,Particle f,double x,double y,double z,double radius,int n){for(int i=0;i<n;i++){double a=Math.PI*2*i/n;w.spawnParticle(f,x+Math.cos(a)*radius,y,z+Math.sin(a)*radius,1,0,0,0,0);}}
    /** Coloured spell particles: with a count of 0 the offsets are the colour. */
    private static void tint(World w,double x,double y,double z,double radius,int n,double r,double g,double b){for(int i=0;i<n;i++){double a=Math.PI*2*i/n;w.spawnParticle(Particle.SPELL_MOB,x+Math.cos(a)*radius,y,z+Math.sin(a)*radius,0,r,g,b,1);}}
    private void line(World w,Particle f,double[] l,double y,int n){
        double len=Math.max(Math.abs(l[2]-l[0]),Math.abs(l[3]-l[1]));int k=Math.max(2,Math.min(n,(int)Math.ceil(len)));double phase=random.nextDouble();
        for(int i=0;i<k;i++){double t=Math.min(1,(i+phase)/k);w.spawnParticle(f,l[0]+(l[2]-l[0])*t,y,l[1]+(l[3]-l[1])*t,1,0,0,0,0);}
    }

    // ---------------------------------------------------------------- strikes
    private void fire(Site s,Plan p){
        final Encounters.Run run=s.run;final World w=run.world;final Layout.Room r=run.room;final double base=base(run);final int tier=tier(run);
        Player who=Bukkit.getPlayer(p.target);final Location aim=eligible(who,run)?who.getLocation():new Location(w,p.tx,p.ty,p.tz);
        Set<UUID> once=new HashSet<>();double[] mid=anchor(p);Location sound=new Location(w,mid[0],66,mid[1]);
        switch(p.type){
            case FLAME_VENTS:
                for(double[] q:p.points){w.spawnParticle(Particle.FLAME,q[0],66.3,q[1],8,.12,.7,.12,.02);for(Player v:area(run,q[0],q[1],1.15,68,base*1.2,once))v.setFireTicks(Math.max(v.getFireTicks(),60));}
                w.playSound(sound,Sound.ENTITY_BLAZE_SHOOT,.8f,.6f);break;
            case DART_SLITS:
                for(double[] q:p.points){
                    final Location from=new Location(w,q[0],66.5,q[1]);double dx=aim.getX()-q[0],dz=aim.getZ()-q[1],flat=Math.sqrt(dx*dx+dz*dz);
                    // Lift the aim for the arrow's drop; the rare far fallback flies faster so it still arrives.
                    final float speed=(float)Math.min(2.8,Math.max(1.7,flat/7));final double t=flat/speed;
                    final Vector dir=flat<.1?new Vector(q[2],0,q[3]):new Vector(dx,aim.getY()+1.1-66.5+.025*t*t,dz);
                    Arrow a=spawn(run,from,()->w.spawnArrow(from,dir,speed,2f),base,80);if(a!=null)a.setPickupStatus(Arrow.PickupStatus.DISALLOWED);
                    w.playSound(from,Sound.BLOCK_DISPENSER_LAUNCH,.7f,1.2f);
                }
                break;
            case EMBER_BOLTS:
                for(double[] q:p.points){
                    final Location from=new Location(w,q[0],67.5,q[1]);Vector d=new Vector(aim.getX()-q[0],aim.getY()+1-67.5,aim.getZ()-q[1]);
                    if(d.lengthSquared()<.01)d=new Vector(q[2],0,q[3]);d.normalize();
                    SmallFireball f=spawn(run,from,()->w.spawn(from,SmallFireball.class),base*1.2,100);
                    if(f!=null){f.setDirection(d);f.setVelocity(d.clone().multiply(.6));f.setIsIncendiary(false);f.setYield(0);}
                    w.playSound(from,Sound.ENTITY_BLAZE_SHOOT,.7f,1.1f);
                }
                break;
            case SPIKE_RUNES:
                for(double[] q:p.points){final Location at=new Location(w,q[0],Layout.FLOOR+1,q[1]);if(spawn(run,at,()->w.spawn(at,EvokerFangs.class),base*1.3,40)==null)area(run,q[0],q[1],.8,68,base*1.3,once);}
                w.playSound(sound,Sound.ENTITY_EVOCATION_FANGS_ATTACK,.8f,.9f);break;
            case FALLING_MASONRY:
                for(double[] q:p.points){
                    final Location at=new Location(w,q[0],q[2],q[1]);final Material m=random.nextInt(3)==0?Material.GRAVEL:Material.COBBLESTONE;
                    FallingBlock b=spawn(run,at,()->w.spawnFallingBlock(at,m,(byte)0),base*1.5,100);if(b!=null){b.setDropItem(false);b.setHurtEntities(false);}
                }
                w.playSound(sound,Sound.BLOCK_STONE_BREAK,.9f,.5f);break;
            case MIASMA:
                for(double[] q:p.points){
                    final Location at=new Location(w,q[0],Layout.FLOOR+1,q[1]);AreaEffectCloud c=spawn(run,at,()->w.spawn(at,AreaEffectCloud.class),0,130);if(c==null)continue;
                    c.setRadius(2f);c.setDuration(100);c.setReapplicationDelay(20);c.setWaitTime(5);c.setRadiusPerTick(0f);c.setRadiusOnUse(0f);c.setDurationOnUse(0);
                    c.setParticle(Particle.SPELL_MOB);c.setColor(Color.fromRGB(80,190,40));
                    c.addCustomEffect(new PotionEffect(plugin.realm(w)>=2?PotionEffectType.WITHER:PotionEffectType.POISON,60+10*tier,tier>=4?1:0),true);
                }
                w.playSound(sound,Sound.BLOCK_LAVA_EXTINGUISH,.8f,.6f);break;
            case FROST_GUSTS:
                line(w,Particle.SNOW_SHOVEL,p.line,65.6,32);line(w,Particle.CLOUD,p.line,65.3,12);
                for(Player v:swept(run,p.line,1,base*.9)){v.addPotionEffect(new PotionEffect(PotionEffectType.SLOW,60,1));if(tier>=3)v.addPotionEffect(new PotionEffect(PotionEffectType.SLOW_DIGGING,60,0));}
                w.playSound(sound,Sound.BLOCK_GLASS_BREAK,.7f,.6f);break;
            case SMITE:
                // Silent, harmless bolt: the spigot variant keeps the thunder out of every other room in the world.
                for(double[] q:p.points){Location at=new Location(w,q[0],Layout.FLOOR+1,q[1]);w.spigot().strikeLightningEffect(at,true);w.playSound(at,Sound.ENTITY_LIGHTNING_IMPACT,1f,1f);area(run,q[0],q[1],1.5,99,base*1.6,once);}
                break;
            case SHOCKWAVE:{
                double[] q=p.points.get(0);s.wave=new Wave(p.type,q[0],q[1],Math.max(8,Math.min(18,Math.min(r.w,r.d)/2.0-3)),base,now);
                w.spawnParticle(Particle.EXPLOSION_NORMAL,q[0],65.3,q[1],6,.4,.1,.4,.02);w.playSound(sound,Sound.ENTITY_GENERIC_EXPLODE,.6f,.5f);break;}
            case PHANTOM_BLADES:
                line(w,Particle.SWEEP_ATTACK,p.line,65.9,24);swept(run,p.line,.9,base*1.3);w.playSound(sound,Sound.ENTITY_PLAYER_ATTACK_SWEEP,1f,.7f);break;
            case POTION_RAIN:{
                PotionType kind=tier<=1?PotionType.SLOWNESS:tier<=3?PotionType.POISON:PotionType.INSTANT_DAMAGE;
                for(double[] q:p.points){
                    int top=column(w,q[0],q[1],Layout.FLOOR+1,r.roof()-1);if(top<0)continue;
                    final Location at=new Location(w,q[0],Math.min(Math.min(r.roof()-1.5,71.5),top+.3),q[1]);
                    SplashPotion pot=spawn(run,at,()->w.spawn(at,SplashPotion.class),0,100);if(pot==null)continue;
                    ItemStack item=new ItemStack(Material.SPLASH_POTION);PotionMeta meta=(PotionMeta)item.getItemMeta();meta.setBasePotionData(new PotionData(kind));item.setItemMeta(meta);
                    pot.setItem(item);pot.setVelocity(new Vector(0,-.5,0));
                }
                w.playSound(sound,Sound.ENTITY_WITCH_THROW,.8f,.7f);break;}
            case GRAVITY_WELL:{double[] q=p.points.get(0);s.wave=new Wave(p.type,q[0],q[1],6.5,base*.8,now);w.playSound(sound,Sound.ENTITY_ENDERMEN_TELEPORT,.8f,.5f);break;}
            case CREEPING_DARK:{
                double[] q=p.points.get(0);w.spawnParticle(Particle.SMOKE_LARGE,q[0],65.8,q[1],30,2.5,.6,2.5,.01);
                for(Player v:area(run,q[0],q[1],5,99,base*.7,once))v.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS,60,0));
                w.playSound(sound,Sound.ENTITY_WITHER_AMBIENT,.5f,.6f);break;}
            case BLAST_SPORES:
                // The plugin already empties dungeon explosion block lists; blast() keeps the burst off everything but eligible players.
                for(double[] q:p.points){if(!loaded(w,q[0],q[1]))continue;blasting=run;blastCap=base*1.8;try{w.createExplosion(q[0],65.5,q[1],(float)(1.4+.15*tier),false,false);}finally{blasting=null;}}
                break;
            default: break;
        }
    }
    private void wave(Site s){
        Wave v=s.wave;Encounters.Run run=s.run;World w=run.world;long age=now-v.start;
        if(v.type==HazardCatalog.Type.SHOCKWAVE){
            v.radius+=.55;if(v.radius>v.max){s.wave=null;return;}
            for(int i=0;i<16;i++){double a=Math.PI*2*i/16,x=v.x+Math.cos(a)*v.radius,z=v.z+Math.sin(a)*v.radius;if(fits(run.room,x,z))w.spawnParticle(Particle.CRIT,x,65.2,z,1,0,.05,0,0);}
            // Jumping over the ring is the dodge: only players on the ground are caught, once per wave.
            for(Player p:eligible(run)){
                Location l=p.getLocation();double dx=l.getX()-v.x,dz=l.getZ()-v.z,d=Math.sqrt(dx*dx+dz*dz);
                if(!p.isOnGround()||Math.abs(d-v.radius)>.7||!v.hit.add(p.getUniqueId()))continue;
                if(hurt(run,p,v.damage)){if(d<.01){dx=1;dz=0;d=1;}p.setVelocity(new Vector(dx/d*.6,.35,dz/d*.6));}
            }
            return;
        }
        if(age>=50){s.wave=null;return;}
        if(age%2==0)w.spawnParticle(Particle.PORTAL,v.x,65.6,v.z,16,1.5,.3,1.5,.6);
        if(age%4==0)ring(w,Particle.SPELL_WITCH,v.x,65.15,v.z,1.3,8);
        for(Player p:eligible(run)){
            Location l=p.getLocation();double dx=v.x-l.getX(),dz=v.z-l.getZ(),d=Math.sqrt(dx*dx+dz*dz);if(d>v.max)continue;
            if(d>.6){Vector vel=p.getVelocity();double nx=vel.getX()+.09*dx/d,nz=vel.getZ()+.09*dz/d,h=Math.sqrt(nx*nx+nz*nz);if(h>.45){nx*=.45/h;nz*=.45/h;}p.setVelocity(new Vector(nx,Math.max(-.6,Math.min(.42,vel.getY())),nz));}
            if(age>0&&age%10==0&&d<=1.3&&hurt(run,p,v.damage)&&plugin.realm(w)>=1)p.addPotionEffect(new PotionEffect(PotionEffectType.WITHER,40,0));
        }
    }

    // ---------------------------------------------------------------- trap entities
    /** Spawns, tags and tracks one trap entity; null when its chunk is not loaded, the budget is spent or the server refused it. */
    private <T extends Entity> T spawn(Encounters.Run run,Location at,Supplier<T> make,double damage,int life){
        if(traps.size()>=MAX_TRAPS||!loaded(run.world,at.getX(),at.getZ()))return null;
        T e;try{e=make.get();}catch(RuntimeException ex){if(warned.add("spawn"))plugin.getLogger().warning("DUNGEON_HAZARD_SPAWN_FAILED room="+run.key+" "+ex);return null;}
        if(e==null)return null;if(!e.isValid()){e.remove();return null;}
        e.addScoreboardTag(TAG);e.addScoreboardTag("jpd_room:"+run.key);traps.put(e.getUniqueId(),new Trap(e,damage,now,life,run.key));return e;
    }
    private void sweep(){
        List<Trap> fallen=null;Iterator<Trap> it=traps.values().iterator();
        while(it.hasNext()){
            Trap t=it.next();if(t.entity.isValid()&&now-t.born<=t.life)continue;
            // Masonry that broke on a ledge or never landed still resolves where it came to rest.
            if(t.entity instanceof FallingBlock&&!t.done){if(fallen==null)fallen=new ArrayList<>();fallen.add(t);}
            t.done=true;t.entity.remove();it.remove();
        }
        if(fallen!=null)for(Trap t:fallen){Location l=t.entity.getLocation();land(t,l.getWorld(),l.getBlockX(),Math.floor(l.getY()),l.getBlockZ());}
    }
    private void land(Trap t,World w,int bx,double by,int bz){
        Encounters.Run run=runs.get(t.room);if(run==null||w==null||!w.equals(run.world))return;
        double x=bx+.5,z=bz+.5;w.spawnParticle(Particle.EXPLOSION_NORMAL,x,by+.3,z,3,.3,.1,.3,.01);w.playSound(new Location(w,x,by,z),Sound.BLOCK_STONE_BREAK,.9f,.6f);
        for(Player v:eligible(run)){Location l=v.getLocation();if(flat(l,x,z)<=1.4&&l.getY()>=by-.5&&l.getY()<=by+2.5)hurt(run,v,t.damage);}
    }
    private static boolean tagged(Entity e){return e!=null&&e.getScoreboardTags().contains(TAG);}
    /** The tagged trap behind a damager: the entity itself, or the shooter of a projectile. */
    private static Entity trap(Entity e){
        if(tagged(e))return e;
        if(e instanceof Projectile){ProjectileSource s=((Projectile)e).getShooter();if(s instanceof Entity&&tagged((Entity)s))return (Entity)s;}
        return null;
    }
    /** Traps wound only players who could be struck in the trap's own live room, never the refuge; unknown traps wound nobody. */
    private boolean harmable(Trap t,Entity victim){
        if(t==null||!(victim instanceof Player)||plugin.sanctuary==null||plugin.sanctuary.contains(victim.getLocation()))return false;
        Encounters.Run run=runs.get(t.room);return run!=null&&eligible((Player)victim,run);
    }

    // ---------------------------------------------------------------- listeners
    @EventHandler(priority=EventPriority.HIGH,ignoreCancelled=true) public void damage(EntityDamageByEntityEvent e){
        Entity source=trap(e.getDamager());if(source==null)return;Trap t=traps.get(source.getUniqueId());
        if(!harmable(t,e.getEntity()))e.setCancelled(true);else if(t.damage>0)e.setDamage(t.damage);
    }
    @EventHandler(priority=EventPriority.HIGH,ignoreCancelled=true) public void blast(EntityDamageEvent e){
        if(blasting==null||e.getCause()!=EntityDamageEvent.DamageCause.BLOCK_EXPLOSION&&e.getCause()!=EntityDamageEvent.DamageCause.ENTITY_EXPLOSION)return;
        // Dropped items, frames, stands and mobs are never hurt by a spore; players' damage stays on the trap scale.
        if(!(e.getEntity() instanceof Player)||plugin.sanctuary==null||plugin.sanctuary.contains(e.getEntity().getLocation())||!eligible((Player)e.getEntity(),blasting))e.setCancelled(true);
        else e.setDamage(Math.min(e.getDamage(),blastCap));
    }
    @EventHandler(priority=EventPriority.HIGH,ignoreCancelled=true) public void hanging(HangingBreakEvent e){if(blasting!=null)e.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGH,ignoreCancelled=true) public void combust(EntityCombustByEntityEvent e){Entity source=trap(e.getCombuster());if(source!=null&&!harmable(traps.get(source.getUniqueId()),e.getEntity()))e.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGH,ignoreCancelled=true) public void cloud(AreaEffectCloudApplyEvent e){if(!tagged(e.getEntity()))return;final Trap t=traps.get(e.getEntity().getUniqueId());e.getAffectedEntities().removeIf(v->!harmable(t,v));}
    @EventHandler(priority=EventPriority.HIGH,ignoreCancelled=true) public void splash(PotionSplashEvent e){
        if(!tagged(e.getPotion()))return;Trap t=traps.get(e.getPotion().getUniqueId());Encounters.Run run=t==null?null:runs.get(t.room);
        // Base potions last a minute or more; a trap's splash lasts as long as the room's other statuses (60+10*tier ticks).
        int longest=0;for(PotionEffect f:e.getPotion().getEffects())if(!f.getType().isInstant())longest=Math.max(longest,f.getDuration());
        double scale=run==null||longest==0?1:Math.min(1,(60+10*tier(run))/(double)longest);
        for(LivingEntity v:e.getAffectedEntities())if(!harmable(t,v))e.setIntensity(v,0);else e.setIntensity(v,e.getIntensity(v)*scale);
    }
    /** Masonry never becomes a block: the landing is cancelled even when another listener already did so, then it strikes. */
    @EventHandler(priority=EventPriority.MONITOR) public void landed(EntityChangeBlockEvent e){
        if(!(e.getEntity() instanceof FallingBlock)||!tagged(e.getEntity()))return;
        e.setCancelled(true);Trap t=traps.remove(e.getEntity().getUniqueId());e.getEntity().remove();
        if(t!=null&&!t.done){t.done=true;land(t,e.getBlock().getWorld(),e.getBlock().getX(),e.getBlock().getY(),e.getBlock().getZ());}
    }
    /** Trap entities never outlive their chunk: none is saved, and any left over from a crash is removed on load. */
    @EventHandler(priority=EventPriority.MONITOR) public void chunkLoad(ChunkLoadEvent e){if(plugin.inside(e.getWorld()))for(Entity en:e.getChunk().getEntities())if(tagged(en)&&!traps.containsKey(en.getUniqueId()))en.remove();}
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true) public void chunkUnload(ChunkUnloadEvent e){if(plugin.inside(e.getWorld()))for(Entity en:e.getChunk().getEntities())if(tagged(en)){Trap t=traps.remove(en.getUniqueId());if(t!=null)t.done=true;en.remove();}}
}
