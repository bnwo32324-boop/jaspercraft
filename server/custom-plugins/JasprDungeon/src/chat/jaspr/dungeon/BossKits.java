package chat.jaspr.dungeon;

import chat.jaspr.dungeon.BossKitCatalog.Ability;
import chat.jaspr.dungeon.BossKitCatalog.Kit;
import chat.jaspr.dungeon.BossKitCatalog.Move;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.*;
import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.block.Block;
import org.bukkit.boss.BarColor;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.entity.*;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.ChunkUnloadEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

/**
 * Generation 7 (owner 2026-10-05): "bosses should very commonly summon mobs as well. They should also have custom unique
 * attacks ... I want the bosses to feel more like bosses and not just enemies with scaled-up health"; "some bosses to be
 * legitimately incredibly powerful."
 *
 * The runtime half of BossKitCatalog. Encounters calls attach() when a boss spawns (slot 0 of a boss room, the Descent's
 * guardian, the Throne's sovereign), tick() every tick for a boss room, died() on the boss's death and sleep() when the room
 * sleeps; close() runs on disable. A fight plays its kit: summons every 12-20 s (faster below), one telegraphed move at a time
 * (never over the boss's own signature warning), phases at 66% and 33% health (a new move, quicker cadence, a burst of adds,
 * a line in chat), an enrage timer, and for about one boss room in ten on Floors II and III the Dread variant (twice the
 * health, harder blows, an extra move, a purple bar). Hits land only on eligible players in the boss's own room and are dealt
 * as p.damage(amount, boss), so Encounters' room rules apply; never in the arrival circle or the doorway margin. Nothing a
 * kit spawns leaves its room or outlives it; snares are the only blocks it places, only into air, and they go after a few
 * seconds, when the boss falls, the room sleeps or the plugin stops.
 *
 * Logs DUNGEON_BOSS_KIT (attach), DUNGEON_BOSS_PHASE, DUNGEON_BOSS_ENRAGED, DUNGEON_BOSS_FELL, a DUNGEON_BOSS_ABILITY metrics
 * line every five minutes when anything was cast and at close, DUNGEON_BOSS_KIT_FAILED once per broken move.
 */
public class BossKits implements Listener {
    static final String TAG="jpd_bosskit",ADD_TAG="jpd_bosskit_add",VIGOR_TAG="jpd_vigor:";
    static final int MAX_SPAWNED=320,MAX_WEBS=48,METRICS_TICKS=6000,MAX_FAILURES=6;
    final DungeonPlugin plugin;final BossAbilities moves;final Random random=new Random();
    final Map<String,Fight> fights=new HashMap<>();
    final Map<UUID,Spawned> spawned=new LinkedHashMap<>();
    private final Map<UUID,Fight> bosses=new HashMap<>();
    private final Map<String,Integer> casts=new TreeMap<>();
    private final Set<String> warned=new HashSet<>();
    private int summonedWindow,phasesWindow,enragesWindow,fallenWindow;
    long now;private long swept=-1,metricsAt;private boolean listening;

    /** One boss fight: its kit, phase and enrage, the move in flight, what it summoned, its ward, copies and snares. */
    static final class Fight {
        final Encounters.Run run;final LivingEntity boss;final Kit kit;final String key,name;final boolean dread;final int floor,tier;final double danger,depth;
        final List<Move> moves=new ArrayList<>();
        final EnumMap<Ability,Long> ready=new EnumMap<>(Ability.class),last=new EnumMap<>(Ability.class);
        final Set<Ability> broken=EnumSet.noneOf(Ability.class);
        final List<LivingEntity> summoned=new ArrayList<>(),wardens=new ArrayList<>(),clones=new ArrayList<>();
        final Map<Block,Long> webs=new LinkedHashMap<>();
        final Map<String,Integer> casts=new TreeMap<>();
        BossAbilities.Cast cast;boolean begun,enraged;int phase,burst,added,failures;
        long start,nextCast,nextSummon,enrageAt,shieldUntil,clonesUntil,feedback;
        List<Player> viewers=Collections.emptyList();long viewersAt=-1;
        Fight(Encounters.Run run,LivingEntity boss,Kit kit,String name,boolean dread,double danger){
            this.run=run;this.boss=boss;this.kit=kit;this.key=run.key;this.name=name;this.dread=dread;this.danger=danger;
            floor=run.room.floor;tier=run.room.tier;depth=Floors.depth(run.room);
        }
        /** The body's own max health: phases are fractions of it (the framework may carry more as a damage divisor). */
        double max(){return Math.max(1,boss.getMaxHealth());}
        double fraction(){return Math.max(0,Math.min(1,boss.getHealth()/max()));}
        boolean shielded(long now){return shieldUntil>now;}
    }
    /** An entity a move spawned (projectile, fang, falling block, cloud): its room, what it deals, and for a falling block its mark. */
    static final class Spawned {
        final Entity entity;final String room;final BossAbilities.Kind kind;final double damage;final long born;final int life;final BossKitCatalog.Element look;
        final BossAbilities.Cast cast;final int index;boolean done;
        Spawned(Entity entity,String room,BossAbilities.Kind kind,double damage,long born,int life,BossKitCatalog.Element look,BossAbilities.Cast cast,int index){
            this.entity=entity;this.room=room;this.kind=kind;this.damage=damage;this.born=born;this.life=life;this.look=look;this.cast=cast;this.index=index;
        }
    }

    BossKits(DungeonPlugin plugin){this.plugin=plugin;this.moves=new BossAbilities(this);}

    // ---------------------------------------------------------------- health through the framework
    private static Method vigor;private static boolean looked;
    /** Encounters.vigor(LivingEntity, double) when the framework has it (generation 7: the body keeps the attribute cap and the rest becomes a damage divisor). */
    private static Method vigor(){
        if(looked)return vigor;looked=true;
        for(Method m:Encounters.class.getMethods()){Class<?>[] p=m.getParameterTypes();
            if(m.getName().equals("vigor")&&Modifier.isStatic(m.getModifiers())&&p.length==2&&p[0].isAssignableFrom(LivingEntity.class)&&(p[1]==double.class||p[1]==Double.class)){vigor=m;break;}}
        return vigor;
    }
    /** The health a body stands for: its max health times its vigor divisor (tag jpd_vigor:x1000), when it has one. */
    static double intended(LivingEntity e){
        double v=1;for(String t:e.getScoreboardTags())if(t.startsWith(VIGOR_TAG)){try{v=Math.max(1,Long.parseLong(t.substring(VIGOR_TAG.length()))/1000.0);}catch(NumberFormatException ignored){}}
        return e.getMaxHealth()*v;
    }
    /** Gives a body this much health: through Encounters.vigor when present, else the attribute within the server's cap. */
    void life(LivingEntity e,double hp){
        Method m=vigor();
        // Used at spawn (a Dread boss), so the body starts at its full new health either way.
        if(m!=null)try{m.invoke(null,e,hp);e.setHealth(Math.max(1,e.getMaxHealth()));return;}catch(ReflectiveOperationException|RuntimeException ex){if(warned.add("vigor"))plugin.getLogger().warning("DUNGEON_BOSS_KIT_FAILED stage=vigor "+ex);}
        AttributeInstance a=e.getAttribute(Attribute.GENERIC_MAX_HEALTH);if(a==null)return;
        a.setBaseValue(Math.max(1,hp));e.setHealth(Math.max(1,Math.min(hp,a.getValue())));
    }

    // ---------------------------------------------------------------- the hooks Encounters calls
    void attach(Encounters.Run run,LivingEntity boss){
        if(run==null||boss==null||run.world==null)return;
        try{
            listen();
            Fight old=fights.remove(run.key);if(old!=null){bosses.remove(old.boss.getUniqueId());moves.end(old,false);}
            Kit kit=BossKitCatalog.of(run.room);boolean dread=BossKitCatalog.dread(run.room);
            String name=boss.getCustomName()==null?kit.title:ChatColor.stripColor(boss.getCustomName());
            if(dread){
                // Owner: "some bosses to be legitimately incredibly powerful": a Dread boss has twice the health, harder blows,
                // one more move, its own name and a purple bar.
                life(boss,intended(boss)*2);
                AttributeInstance hit=boss.getAttribute(Attribute.GENERIC_ATTACK_DAMAGE);if(hit!=null)hit.setBaseValue(hit.getBaseValue()*1.25);
                name="Dread "+name;boss.setCustomName(ChatColor.DARK_PURPLE+name);
                if(run.bar!=null){run.bar.setColor(BarColor.PURPLE);run.bar.setTitle(name);}
                run.bossMax=boss.getMaxHealth();
            }
            Fight f=new Fight(run,boss,kit,name,dread,Math.max(1,Math.min(1.45,plugin.dangerMultiplier(run.world))));
            fights.put(run.key,f);bosses.put(boss.getUniqueId(),f);
            plugin.getLogger().info("DUNGEON_BOSS_KIT room="+run.key+" boss="+name+" kit="+kit.id+" floor="+f.floor+" tier="+f.tier+" dread="+dread
                +" moves="+abilities(kit.moves(2,dread))+" health="+Math.round(boss.getMaxHealth())+" intended="+Math.round(intended(boss))+" adds="+addsCap(f));
        }catch(RuntimeException ex){plugin.getLogger().warning("DUNGEON_BOSS_KIT_FAILED room="+run.key+" stage=attach "+ex);}
    }
    void tick(Encounters.Run run,long ticks){
        if(ticks>now)now=ticks;
        if(swept!=now){swept=now;sweep();if(now-metricsAt>=METRICS_TICKS){metricsAt=now;metrics(false);}}
        Fight f=run==null?null:fights.get(run.key);if(f==null||f.run!=run)return;
        try{
            if(run.state.cleared){end(f);return;}
            // A lost body is respawned by Encounters (attach starts a new fight); a dead one is ended by died().
            if(!f.boss.isValid()||f.boss.isDead())return;
            if(!f.begun)begin(f);
            moves.step(f,now);
            upkeep(f);
            if(targets(f).isEmpty())return;
            phase(f);
            if(!f.enraged&&now>=f.enrageAt)enrage(f,"timer");
            if(f.cast==null)next(f);
        }catch(RuntimeException ex){failed(f,f.cast==null?null:f.cast.ability,ex);}
    }
    void died(Encounters.Run run,LivingEntity boss){
        Fight f=run==null||boss==null?null:fights.get(run.key);if(f==null||!f.boss.getUniqueId().equals(boss.getUniqueId()))return;
        fights.remove(run.key);bosses.remove(boss.getUniqueId());fallenWindow++;
        try{
            Location at=boss.getLocation();
            int crumbled=moves.end(f,true);
            int escorts=0;for(Map.Entry<Integer,LivingEntity> m:run.mobs.entrySet())if(m.getKey()!=0&&m.getValue().isValid()&&!m.getValue().isDead())escorts++;
            moves.fell(f,at);
            for(Player v:viewers(f)){
                v.playSound(at,f.kit.guardian?Sound.ENTITY_ENDERDRAGON_DEATH:Sound.ENTITY_WITHER_DEATH,f.kit.guardian?.7f:.45f,1.1f);
                v.sendMessage(ChatColor.GOLD+f.name+" falls."+(crumbled>0?ChatColor.GRAY+" Its "+crumbled+" summoned servant"+(crumbled==1?" crumbles":"s crumble")+" to dust.":""));
                // The loot-ready cue: the room opens when its last escort falls (Encounters says so the moment it happens).
                if(escorts>0)v.sendTitle(ChatColor.GOLD+f.name+" falls",ChatColor.GRAY+"Defeat the last "+escorts+" escort"+(escorts==1?"":"s")+" to unseal the reliquary",5,50,12);
                else v.sendMessage(ChatColor.GRAY+"The reliquary is ready.");
            }
            plugin.getLogger().info("DUNGEON_BOSS_FELL room="+run.key+" boss="+f.name+" kit="+f.kit.id+" dread="+f.dread+" seconds="+(f.begun?(now-f.start)/20:0)
                +" phase="+(f.phase+1)+" enraged="+f.enraged+" casts="+summary(f.casts)+" summoned="+f.added+" crumbled="+crumbled+" escorts="+escorts);
        }catch(RuntimeException ex){plugin.getLogger().warning("DUNGEON_BOSS_KIT_FAILED room="+run.key+" stage=died "+ex);}
    }
    void sleep(Encounters.Run run){
        if(run==null)return;
        Fight f=fights.remove(run.key);
        if(f!=null){bosses.remove(f.boss.getUniqueId());try{moves.end(f,false);}catch(RuntimeException ex){plugin.getLogger().warning("DUNGEON_BOSS_KIT_FAILED room="+run.key+" stage=sleep "+ex);}}
        purge(run.key);
    }
    void close(){
        for(Fight f:new ArrayList<>(fights.values()))try{moves.end(f,false);}catch(RuntimeException ex){plugin.getLogger().warning("DUNGEON_BOSS_KIT_FAILED room="+f.key+" stage=close "+ex);}
        fights.clear();bosses.clear();for(Spawned s:spawned.values()){s.done=true;s.entity.remove();}spawned.clear();
        metrics(true);
    }

    // ---------------------------------------------------------------- the fight's rhythm
    private void begin(Fight f){
        f.begun=true;f.start=now;f.moves.addAll(f.kit.base());if(f.dread&&f.kit.dread!=null)f.moves.add(f.kit.dread);
        f.nextCast=now+60;f.nextSummon=now+Math.min(200,BossKitCatalog.summonEvery(f.kit,f.floor,0,false)/2);f.enrageAt=now+BossKitCatalog.enrage(f.kit,f.floor);
    }
    /** The next move: a summon when due ("very commonly"), else the least recently used move that is ready and has something to aim at. */
    private void next(Fight f){
        if(now>=f.nextSummon&&!f.broken.contains(Ability.SUMMON)){
            int n=f.burst>0?f.burst:BossKitCatalog.summonCount(f.kit,f.floor,f.phase);
            BossAbilities.Cast c=start(f,f.kit.summon(),n);
            f.burst=0;f.nextSummon=now+(c==null?100:BossKitCatalog.summonEvery(f.kit,f.floor,f.phase,f.enraged));
            if(c!=null)return;
        }
        if(now<f.nextCast)return;
        Move pick=null;long oldest=Long.MAX_VALUE;
        for(Move m:f.moves){
            if(m.ability==Ability.SUMMON||f.broken.contains(m.ability)||now<f.ready.getOrDefault(m.ability,0L))continue;
            if(m.ability==Ability.ENRAGE&&f.enraged)continue;
            // One big warning at a time: while the boss's own signature is marked, only light moves begin.
            if(m.ability.heavy&&f.run.warningPattern()!=null)continue;
            long last=f.last.getOrDefault(m.ability,-1L);
            if(last<oldest||last==oldest&&random.nextBoolean()){oldest=last;pick=m;}
        }
        if(pick==null){f.nextCast=now+10;return;}
        if(start(f,pick,0)==null){f.ready.put(pick.ability,now+40);f.nextCast=now+10;}
    }
    private BossAbilities.Cast start(Fight f,Move m,int count){
        BossAbilities.Cast c;
        try{c=moves.begin(f,m,now,count);}catch(RuntimeException ex){failed(f,m.ability,ex);return null;}
        if(c==null)return null;
        f.cast=c;f.last.put(m.ability,now);f.ready.put(m.ability,now+BossKitCatalog.cooldown(m,f.floor,f.phase,f.enraged,f.dread));
        // The boss's signature waits until a big move has landed, so two big warnings never overlap.
        if(m.ability.heavy&&f.run.nextAttack<now+c.length()+20)f.run.nextAttack=now+c.length()+20;
        f.casts.merge(m.ability.name(),1,Integer::sum);casts.merge(m.ability.name(),1,Integer::sum);
        return c;
    }
    /** Phases at 66% and 33% health: the new moves, a burst of adds, quicker cadence (BossKitCatalog.pace), a line in chat. */
    private void phase(Fight f){
        double frac=f.fraction();int want=Math.min(f.kit.phaseCount()-1,frac<.33?2:frac<.66?1:0);
        while(f.phase<want){f.phase++;enter(f);}
    }
    private void enter(Fight f){
        List<Move> unlocked=f.kit.unlocked(f.phase);f.moves.addAll(unlocked);for(Move m:unlocked)f.ready.put(m.ability,now+30);
        f.burst=Math.min(6,f.kit.burst+(f.floor-1));f.nextSummon=Math.min(f.nextSummon,now);
        boolean last=f.phase==f.kit.phaseCount()-1;String numeral=f.phase==1?"II":"III";ChatColor colour=ChatColor.getByChar(f.kit.element.colour);
        for(Player v:viewers(f)){
            v.sendMessage(ChatColor.DARK_RED+f.name+": "+colour+f.kit.shout(f.phase-1));
            v.sendMessage(ChatColor.GRAY+"Phase "+numeral+(last&&f.kit.desperate?" (a desperate last stand)":"")+" - new: "+titles(unlocked)+". Its servants rise.");
            v.sendTitle(ChatColor.DARK_RED+f.name,colour+"Phase "+numeral,5,30,10);
            v.playSound(f.boss.getLocation(),f.kit.guardian?Sound.ENTITY_WITHER_SPAWN:Sound.ENTITY_ENDERDRAGON_GROWL,f.kit.guardian?.6f:.8f,.8f);
        }
        phasesWindow++;
        plugin.getLogger().info("DUNGEON_BOSS_PHASE room="+f.key+" boss="+f.name+" kit="+f.kit.id+" phase="+(f.phase+1)+" health="+Math.round(100*f.fraction())+"% moves="+abilities(unlocked)+" burst="+f.burst+" seconds="+(now-f.start)/20);
        if(last&&f.kit.desperate&&!f.enraged)enrage(f,"desperate");
    }
    /** The enrage timer (or an enrage move, or a desperate last stand): faster, harder, quicker to strike. */
    void enrage(Fight f,String why){
        if(f.enraged)return;f.enraged=true;enragesWindow++;
        f.boss.addPotionEffect(new PotionEffect(PotionEffectType.SPEED,20*60*30,f.kit.guardian?1:0,true,true),true);
        f.boss.addPotionEffect(new PotionEffect(PotionEffectType.INCREASE_DAMAGE,20*60*30,0,true,true),true);
        for(Player v:viewers(f)){
            v.sendMessage(ChatColor.DARK_RED+f.name+": "+ChatColor.RED+f.kit.shout(2));
            v.sendMessage(ChatColor.GRAY+f.name+" is enraged: faster, stronger and quicker to strike.");
            v.playSound(f.boss.getLocation(),Sound.ENTITY_ENDERDRAGON_GROWL,1f,.6f);
        }
        plugin.getLogger().info("DUNGEON_BOSS_ENRAGED room="+f.key+" boss="+f.name+" after="+(now-f.start)/20+"s why="+why);
    }
    /** The ward, the copies and the snares run out; the boss shows what it is. */
    private void upkeep(Fight f){
        if(f.shieldUntil>0){
            prune(f.wardens);
            if(f.wardens.isEmpty()||now>=f.shieldUntil){
                // Wardens that outlast their ward fight on as ordinary summons (counted against the add cap), so wards never pile up adds.
                f.shieldUntil=0;moves.unward(f);f.summoned.addAll(f.wardens);f.wardens.clear();
                for(Player v:viewers(f))v.sendMessage(ChatColor.AQUA+"The ward around "+f.name+" breaks!");
            }else if(now%5==0)moves.ward(f);
        }
        if(!f.clones.isEmpty()&&now>=f.clonesUntil){moves.vanish(f,f.clones);f.clones.clear();}
        prune(f.clones);
        if(!f.webs.isEmpty())clearWebs(f,false);
        if(now%10==0)moves.aura(f);
    }

    // ---------------------------------------------------------------- who may be hurt, who sees
    /** Only these players are ever hurt: alive, in Survival or Adventure, standing in this very room and outside the arrival circle. */
    boolean eligible(Player p,Encounters.Run run){
        if(p==null||run==null||run.world==null||!p.isOnline()||p.isDead()||!p.getWorld().equals(run.world)||!plugin.inside(p.getWorld()))return false;
        GameMode g=p.getGameMode();if(g!=GameMode.SURVIVAL&&g!=GameMode.ADVENTURE)return false;
        Location l=p.getLocation();
        return run.key.equals(plugin.roomKey(l))&&(plugin.sanctuary==null||!plugin.sanctuary.contains(l))&&!HazardCatalog.safe(run.room,l.getX(),l.getZ());
    }
    List<Player> targets(Fight f){List<Player> out=new ArrayList<>();for(UUID id:f.run.players){Player p=Bukkit.getPlayer(id);if(eligible(p,f.run))out.add(p);}return out;}
    /** Everyone standing in the room (any game mode): they see the marks and hear the cues. */
    List<Player> viewers(Fight f){
        if(f.viewersAt==now)return f.viewers;
        List<Player> out=new ArrayList<>();for(Player p:f.run.world.getPlayers())if(f.key.equals(plugin.roomKey(p.getLocation())))out.add(p);
        f.viewers=out;f.viewersAt=now;return out;
    }
    /** A blow from the boss itself (so Encounters' canHarm decides), never in the doorway margin. True while the player can still take a status. */
    boolean hurt(Fight f,Player p,double amount){
        if(amount<=0||!eligible(p,f.run))return false;
        Location l=p.getLocation();if(!EncounterCatalog.hazardAllowed(f.run.room,l.getX(),l.getY(),l.getZ()))return false;
        p.damage(amount,f.boss);return eligible(p,f.run);
    }
    int addsCap(Fight f){return BossKitCatalog.addsCap(f.kit,f.floor,f.dread);}
    static int prune(List<LivingEntity> list){list.removeIf(e->!e.isValid()||e.isDead());return list.size();}
    /** A summoned add: the room's escort formula scaled for chaff or elites, through Encounters.summon (tagged to the room, never counted for its clear). */
    LivingEntity add(Fight f,EncounterCatalog.Species s,Location near,boolean elite,String name){
        LivingEntity e=plugin.encounters.summon(f.run,s,near,BossKitCatalog.addHealth(f.floor,f.tier,f.depth,f.danger,elite),BossKitCatalog.addDamage(f.floor,f.tier,f.danger,elite),name);
        if(e!=null){e.addScoreboardTag(ADD_TAG);f.added++;summonedWindow++;}
        return e;
    }
    /** Tags and tracks an entity a move spawned; null (and removed) when it is invalid or the budget is spent. */
    <T extends Entity> T track(Fight f,T e,BossAbilities.Kind kind,double damage,int life,BossKitCatalog.Element look,BossAbilities.Cast cast,int index){
        if(e==null)return null;if(!e.isValid()||spawned.size()>=MAX_SPAWNED){e.remove();return null;}
        e.addScoreboardTag(TAG);e.addScoreboardTag("jpd_room:"+f.key);
        spawned.put(e.getUniqueId(),new Spawned(e,f.key,kind,damage,now,life,look,cast,index));return e;
    }
    boolean room(){return spawned.size()<MAX_SPAWNED;}
    /** A web snare: registered before it is placed, so a crash between the two never leaves an untracked web. */
    boolean web(Fight f,Block b,long until){if(f.webs.size()>=MAX_WEBS||f.webs.containsKey(b))return false;f.webs.put(b,until);return true;}
    void clearWebs(Fight f,boolean all){
        Iterator<Map.Entry<Block,Long>> it=f.webs.entrySet().iterator();
        while(it.hasNext()){Map.Entry<Block,Long> w=it.next();if(!all&&now<w.getValue())continue;Block b=w.getKey();
            try{if(b.getType()==Material.WEB)b.setType(Material.AIR,false);}catch(RuntimeException ex){if(warned.add("web"))plugin.getLogger().warning("DUNGEON_BOSS_KIT_FAILED room="+f.key+" stage=web "+ex);}
            it.remove();}
    }
    /** Removes every kit entity of a room. */
    void purge(String room){Iterator<Spawned> it=spawned.values().iterator();while(it.hasNext()){Spawned s=it.next();if(s.room.equals(room)){s.done=true;s.entity.remove();it.remove();}}}
    private void end(Fight f){fights.remove(f.key);bosses.remove(f.boss.getUniqueId());moves.end(f,false);purge(f.key);}
    /** Spent entities go; a falling block that broke or never landed still strikes its mark. */
    private void sweep(){
        List<Spawned> late=null;Iterator<Spawned> it=spawned.values().iterator();
        while(it.hasNext()){Spawned s=it.next();if(s.entity.isValid()&&now-s.born<=s.life)continue;
            if(s.kind==BossAbilities.Kind.METEOR&&!s.done){if(late==null)late=new ArrayList<>();late.add(s);}
            s.done=true;s.entity.remove();it.remove();}
        if(late!=null)for(Spawned s:late){Fight f=fights.get(s.room);if(f!=null)try{moves.land(f,s);}catch(RuntimeException ex){failed(f,Ability.METEOR_RAIN,ex);}}
    }
    /** A move that throws is switched off for this fight (logged once); a fight that keeps failing ends quietly. */
    void failed(Fight f,Ability a,RuntimeException ex){
        if(a!=null)f.broken.add(a);
        if(f.cast!=null&&(a==null||f.cast.ability==a)){try{moves.abandon(f,f.cast);}catch(RuntimeException ignored){}f.cast=null;f.nextCast=now+40;}
        String key=f.key+"/"+(a==null?"tick":a.name());
        if(warned.size()<4096&&warned.add(key))plugin.getLogger().warning("DUNGEON_BOSS_KIT_FAILED room="+f.key+" boss="+f.name+" ability="+(a==null?"-":a.name())+" "+ex);
        if(++f.failures>=MAX_FAILURES&&fights.get(f.key)==f){end(f);plugin.getLogger().warning("DUNGEON_BOSS_KIT_DISABLED room="+f.key+" boss="+f.name+" failures="+f.failures);}
    }

    // ---------------------------------------------------------------- cues and diagnostics
    /** Big and fight-changing moves are said in chat (who, what, how to live through it); light ones on the action bar. */
    void say(Fight f,Move m){
        ChatColor colour=ChatColor.getByChar(m.element(f.kit).colour);
        boolean loud=m.ability.heavy||m.ability==Ability.SHIELD||m.ability==Ability.LEECH||m.ability==Ability.CLONES||m.ability==Ability.ENRAGE;
        for(Player v:viewers(f)){
            if(loud)v.sendMessage(colour+f.name+ChatColor.GRAY+" - "+ChatColor.WHITE+m.name+ChatColor.GRAY+": "+m.ability.hint+(m.ability.heavy?"!":"."));
            else v.sendActionBar(colour+m.name+ChatColor.GRAY+": "+m.ability.hint);
        }
    }
    private void metrics(boolean closing){
        if(!closing&&casts.isEmpty())return;
        plugin.getLogger().info("DUNGEON_BOSS_ABILITY casts="+summary(casts)+" fights="+fights.size()+" summoned="+summonedWindow+" phases="+phasesWindow+" enrages="+enragesWindow+" fallen="+fallenWindow+" spawned="+spawned.size()+(closing?" final=true":""));
        casts.clear();summonedWindow=phasesWindow=enragesWindow=fallenWindow=0;
    }
    static String summary(Map<String,Integer> m){StringBuilder b=new StringBuilder();for(Map.Entry<String,Integer> e:m.entrySet()){if(b.length()>0)b.append(',');b.append(e.getKey()).append(':').append(e.getValue());}return b.length()==0?"-":b.toString();}
    static String abilities(List<Move> moves){StringBuilder b=new StringBuilder();for(Move m:moves){if(b.length()>0)b.append(',');b.append(m.ability.name());}return b.toString();}
    static String titles(List<Move> moves){StringBuilder b=new StringBuilder();for(Move m:moves){if(b.length()>0)b.append(", ");b.append(m.name);}return b.toString();}
    /** Test and diagnostic view of a boss room's fight. */
    public String describe(String key){
        Fight f=fights.get(key);if(f==null)return "fight=-";
        return "kit="+f.kit.id+" boss="+f.name+" dread="+f.dread+" phase="+(f.phase+1)+" health="+Math.round(100*f.fraction())+"% enraged="+f.enraged+" cast="+(f.cast==null?"-":f.cast.ability.name())
            +" adds="+prune(f.summoned)+"/"+addsCap(f)+" wardens="+prune(f.wardens)+" clones="+prune(f.clones)+" webs="+f.webs.size()+" warded="+f.shielded(now)+" casts="+summary(f.casts)+" broken="+f.broken;
    }
    /** Test hook (the separately packaged probe): begin this move now in a live fight, cooldowns ignored. */
    public boolean force(Encounters.Run run,String ability){
        Fight f=run==null?null:fights.get(run.key);if(f==null||f.cast!=null||!f.boss.isValid())return false;
        if(!f.begun)begin(f);
        for(Move m:f.kit.moves(2,f.dread))if(m.ability.name().equalsIgnoreCase(ability))return start(f,m,m.ability==Ability.SUMMON?BossKitCatalog.summonCount(f.kit,f.floor,f.phase):0)!=null;
        return false;
    }

    // ---------------------------------------------------------------- listeners (registered on the first boss)
    private void listen(){
        if(listening)return;listening=true;
        try{Bukkit.getPluginManager().registerEvents(this,plugin);}catch(RuntimeException ex){listening=false;plugin.getLogger().warning("DUNGEON_BOSS_KIT_LISTENER_FAILED "+ex);}
    }
    /** A warded boss shrugs blows off while its wardens live. */
    @EventHandler(priority=EventPriority.HIGH,ignoreCancelled=true) public void bossHurt(EntityDamageEvent e){
        Fight f=bosses.get(e.getEntity().getUniqueId());if(f==null||!f.shielded(now)||e.getCause()==EntityDamageEvent.DamageCause.VOID)return;
        e.setCancelled(true);if(now>=f.feedback){f.feedback=now+10;moves.deflect(f,e);}
    }
    /** Fangs wound only eligible players of their room; projectiles, blocks and clouds never wound by themselves (shot() deals a projectile's hit as its boss). */
    @EventHandler(priority=EventPriority.LOW,ignoreCancelled=true) public void struck(EntityDamageByEntityEvent e){
        Spawned s=spawned.get(e.getDamager().getUniqueId());if(s==null)return;
        if(s.kind==BossAbilities.Kind.FANGS){Fight f=fights.get(s.room);if(f!=null&&e.getEntity() instanceof Player&&eligible((Player)e.getEntity(),f.run))e.setDamage(s.damage);else e.setCancelled(true);return;}
        e.setCancelled(true);
    }
    @EventHandler(priority=EventPriority.MONITOR) public void shot(ProjectileHitEvent e){
        Spawned s=spawned.get(e.getEntity().getUniqueId());if(s==null||s.done)return;s.done=true;
        Fight f=fights.get(s.room);Entity v=e.getHitEntity();
        if(f!=null&&v instanceof Player)try{moves.shot(f,s,(Player)v);}catch(RuntimeException ex){failed(f,Ability.VOLLEY,ex);}
        if(e.getEntity() instanceof Arrow)e.getEntity().remove();
    }
    @EventHandler(priority=EventPriority.HIGHEST) public void prime(ExplosionPrimeEvent e){if(spawned.containsKey(e.getEntity().getUniqueId()))e.setCancelled(true);}
    /** A falling block never becomes a block: the landing is cancelled (even when another listener already did), then it strikes its mark. */
    @EventHandler(priority=EventPriority.MONITOR) public void landed(EntityChangeBlockEvent e){
        if(!(e.getEntity() instanceof FallingBlock))return;Spawned s=spawned.remove(e.getEntity().getUniqueId());
        if(s==null){if(e.getEntity().getScoreboardTags().contains(TAG)){e.setCancelled(true);e.getEntity().remove();}return;}
        e.setCancelled(true);e.getEntity().remove();
        Fight f=fights.get(s.room);if(f!=null&&!s.done)try{moves.land(f,s);}catch(RuntimeException ex){failed(f,Ability.METEOR_RAIN,ex);}
        s.done=true;
    }
    @EventHandler(priority=EventPriority.HIGH,ignoreCancelled=true) public void cloud(AreaEffectCloudApplyEvent e){
        Spawned s=spawned.get(e.getEntity().getUniqueId());if(s==null)return;final Fight f=fights.get(s.room);
        e.getAffectedEntities().removeIf(v->!(v instanceof Player)||f==null||!eligible((Player)v,f.run)||!EncounterCatalog.hazardAllowed(f.run.room,v.getLocation().getX(),v.getLocation().getY(),v.getLocation().getZ()));
    }
    /** Kit entities never outlive their chunk: none is saved, and any left over from a crash is removed on load. */
    @EventHandler(priority=EventPriority.MONITOR) public void chunkLoad(ChunkLoadEvent e){if(plugin.inside(e.getWorld()))for(Entity en:e.getChunk().getEntities())if(en.getScoreboardTags().contains(TAG)&&!spawned.containsKey(en.getUniqueId()))en.remove();}
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true) public void chunkUnload(ChunkUnloadEvent e){
        if(plugin.inside(e.getWorld()))for(Entity en:e.getChunk().getEntities())if(en.getScoreboardTags().contains(TAG)){Spawned s=spawned.remove(en.getUniqueId());if(s!=null)s.done=true;en.remove();}
    }
}
