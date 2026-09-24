package chat.jaspr.apocalypse;

import com.google.gson.Gson;
import com.mojang.authlib.GameProfile;
import fr.xephi.authme.api.v3.AuthMeApi;
import fr.xephi.authme.data.auth.PlayerAuth;
import fr.xephi.authme.data.auth.PlayerCache;
import java.io.*;
import java.lang.reflect.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import net.minecraft.server.v1_12_R1.EntityPlayer;
import net.minecraft.server.v1_12_R1.PlayerInteractManager;
import net.minecraft.server.v1_12_R1.NBTTagCompound;
import net.minecraft.server.v1_12_R1.NBTTagList;
import net.minecraft.server.v1_12_R1.NBTTagString;
import net.minecraft.server.v1_12_R1.NBTCompressedStreamTools;
import org.bukkit.*;
import org.bukkit.attribute.*;
import org.bukkit.craftbukkit.v1_12_R1.CraftServer;
import org.bukkit.craftbukkit.v1_12_R1.CraftWorld;
import org.bukkit.craftbukkit.v1_12_R1.entity.CraftPlayer;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.entity.*;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.*;
import org.bukkit.inventory.*;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.plugin.RegisteredListener;
import org.bukkit.potion.*;
import org.bukkit.util.Vector;

/** No real clients or production accounts. Paper inventories, event executors, native .dat storage,
 * real AuthMe cache, and a native EntityPlayer are exercised in two separate JVMs. */
public final class StatsRuntimeProbe implements Listener {
    private final JavaPlugin harness;private ApocalypsePlugin plugin;private SurvivorStats stats;private World world;private PlayerCache auth;
    private final List<String> failures=new ArrayList<>(),phases=new ArrayList<>();private final List<String> authenticatedNames=new ArrayList<>();
    private int assertions;private boolean cancelEarly,cancelLate;private Actor closingActor,staleClosingActor;private final Map<String,Actor> respawnFailures=new LinkedHashMap<>();private final int phase=Integer.getInteger("jaspr.stats.phase",0);
    private interface Checked{void run()throws Exception;}
    private StatsRuntimeProbe(JavaPlugin harness){this.harness=harness;}
    public static void install(JavaPlugin harness){
        try{File cwd=new File(".").getCanonicalFile();if(!Boolean.getBoolean("jaspr.stats.fixture")||!cwd.getName().equals("server")||!cwd.getParentFile().getName().startsWith("jaspr-stats-runtime-"))throw new IllegalStateException("Not an isolated stats fixture");}
        catch(IOException error){throw new IllegalStateException(error);}
        StatsRuntimeProbe probe=new StatsRuntimeProbe(harness);Bukkit.getScheduler().runTaskLater(harness,probe::begin,2);
    }
    private void check(boolean ok,String message){assertions++;if(!ok)throw new AssertionError(message);}
    private void near(double actual,double expected,String label){check(Math.abs(actual-expected)<1e-5,label+" actual="+actual+" expected="+expected);}
    private void test(String name,Checked body){try{body.run();phases.add(name);harness.getLogger().info("STATS_CASE_PASS "+name+" assertions="+assertions);}catch(Throwable error){while(error instanceof InvocationTargetException)error=error.getCause();failures.add(name+": "+error);harness.getLogger().severe("STATS_CASE_FAIL "+name+": "+error);error.printStackTrace();}}
    private static Object field(Object object,String name)throws Exception{Field f=object.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(object);}
    private static Object invoke(Object object,String method,Class<?>[] types,Object... args)throws Exception{Method m=object.getClass().getDeclaredMethod(method,types);m.setAccessible(true);return m.invoke(object,args);}
    private SurvivorStats.Profile profile(Player player)throws Exception{return (SurvivorStats.Profile)invoke(stats,"profile",new Class<?>[]{Player.class},player);}
    private void refresh(Player player)throws Exception{invoke(stats,"refresh",new Class<?>[]{Player.class},player);}
    @SuppressWarnings("unchecked") private void ready(Player player)throws Exception{((Map<UUID,Long>)field(stats,"purchaseReady")).remove(player.getUniqueId());((Map<UUID,Long>)field(stats,"menuReady")).remove(player.getUniqueId());}
    private int points(Player player){return StatRules.points(player.getLevel(),player.getExp());}
    private static UUID id(String name){return UUID.nameUUIDFromBytes(("jaspr.stats.fixture:"+name).getBytes(StandardCharsets.UTF_8));}
    private void login(Player player,boolean value){String name=player.getName();if(value){auth.updatePlayer(new PlayerAuth.Builder().name(name).realName(name).build());authenticatedNames.add(name);}else auth.removePlayer(name);}
    private Path lifeFile(Player player){return plugin.getDataFolder().toPath().resolve("stats/lives/"+player.getUniqueId()+".life");}
    private void begin(){
        test("setup",()->{
            check(phase==1||phase==2,"known phase");check(Bukkit.getOnlinePlayers().isEmpty(),"fixture has no network players");
            plugin=(ApocalypsePlugin)Bukkit.getPluginManager().getPlugin("JasprApocalypse");stats=plugin.stats();world=Bukkit.getWorld("world");
            check(stats!=null && stats.metrics().contains("statsLocked=false"),"real plugin started stats");
            auth=(PlayerCache)field(AuthMeApi.getInstance(),"playerCache");check(auth!=null,"real AuthMe cache available");
            // Keep unrelated gameplay listeners from consuming fake Player events. Subject's actual
            // registered Bukkit event executors are retained and used below, including cancellation.
            Bukkit.getPluginManager().registerEvents(this,harness);world.setGameRuleValue("doMobSpawning","false");
        });
        if(stats==null||auth==null){finish();return;}
        if(phase==1){
            test("six-caps-real-XP-checkpoints",this::caps);
            test("authentication-dead-modes-and-XP-denials",this::denials);
            test("menu-holder-owner-drag-click-protection",this::menus);
            test("only-owned-attributes-no-potion-mutation",this::attributes);
            test("melee-bow-firearm-damage-and-cancellation",this::combat);
            test("invalid-tags-and-storage-fail-closed",this::failures);
            test("death-keepInventory-and-prior-life-NBT",this::death);
            test("native-CraftPlayer-save-and-restart-stage",this::nativeStage);
            test("respawn-unconfirmed-save-stage",this::respawnFailureStage);
        }else{
            test("native-CraftPlayer-restart-XP-tags-inventory",this::nativeRestart);
            test("stale-prior-life-NBT-restart-tombstone",this::staleRestart);
        }
        Bukkit.getScheduler().runTaskLater(harness,()->{test("scheduled-lifecycle-and-final-safety",this::scheduled);if(phase==1)for(Map.Entry<String,Actor> entry:respawnFailures.entrySet())test("respawn-"+entry.getKey()+"-fails-closed",()->respawnFailureResult(entry.getValue()));finish();},3);
    }
    private void caps()throws Exception{
        Actor actor=new Actor("caps");login(actor.player,true);actor.xp(50000);actor.total=900000;
        actor.tags.add("unrelated:fixture");int expected=50000,saves=0;check(plugin.isSurvivor(actor.player),"authenticated real API");
        for(StatRules.Stat stat:StatRules.Stat.values())for(int rank=0;rank<10;rank++){
            ready(actor.player);int price=StatRules.cost(rank);check(stats.purchase(actor.player,stat),"buy "+stat+" rank "+rank);expected-=price;saves++;
            check(actor.saves==saves,"one player save per upgrade");check(points(actor.player)==expected && actor.total==expected,"raw spend, not lifetime total");
            check(profile(actor.player).ranks.rank(stat)==rank+1,"one rank per transaction");
            verifyCheckpoint(actor.player,expected,profile(actor.player).ranks.tag());check(actor.tags.contains("unrelated:fixture"),"unrelated scoreboard tag retained");
        }
        check(expected==49670 && profile(actor.player).ranks.total()==60,"all 60 prices charged exactly");
        for(StatRules.Stat stat:StatRules.Stat.values()){ready(actor.player);check(!stats.purchase(actor.player,stat),"capped stat not purchasable");}
        check(actor.saves==60 && points(actor.player)==expected,"cap denial no XP or saves");
        Actor debounce=new Actor("debounce");login(debounce.player,true);debounce.xp(500);check(stats.purchase(debounce.player,StatRules.Stat.POWER),"initial purchase");check(!stats.purchase(debounce.player,StatRules.Stat.POWER),"repeated click debounced");check(points(debounce.player)==499,"no repeat charge");
        stats.quit(new PlayerQuitEvent(actor.player,"fixture"));Actor reloaded=new Actor("caps");reloaded.load();login(reloaded.player,true);
        check(profile(reloaded.player).ranks.total()==60 && points(reloaded.player)==expected,"logout/load keeps ranks and spent XP");
    }
    private void denials()throws Exception{
        Actor actor=new Actor("denials");actor.xp(500);
        check(!stats.purchase(actor.player,StatRules.Stat.VITALITY),"unauthenticated denied");stats.open(actor.player);check(!(actor.top.getHolder() instanceof SurvivorStats.Panel),"unauthenticated menu denied");
        login(actor.player,true);actor.online=false;check(!stats.purchase(actor.player,StatRules.Stat.VITALITY),"offline denied");actor.online=true;
        actor.dead=true;check(!stats.purchase(actor.player,StatRules.Stat.VITALITY),"dead denied");stats.open(actor.player);check(!(actor.top.getHolder() instanceof SurvivorStats.Panel),"dead menu denied");actor.dead=false;
        for(GameMode mode:new GameMode[]{GameMode.CREATIVE,GameMode.SPECTATOR}){actor.mode=mode;check(!stats.purchase(actor.player,StatRules.Stat.VITALITY),"mode denied "+mode);}
        check(actor.saves==0 && points(actor.player)==500 && !Files.exists(lifeFile(actor.player)),"denials do not initialize durable life or spend XP");
        actor.mode=GameMode.SURVIVAL;actor.xp(0);ready(actor.player);check(!stats.purchase(actor.player,StatRules.Stat.POWER),"insufficient points");check(points(actor.player)==0 && actor.saves==0,"insufficient unchanged");
        for(float bar:new float[]{Float.NaN,Float.POSITIVE_INFINITY,-.1f,1}){actor.level=0;actor.bar=bar;ready(actor.player);check(!stats.purchase(actor.player,StatRules.Stat.POWER),"bad bar denied "+bar);}
        actor.level=1000000;actor.bar=0;ready(actor.player);check(!stats.purchase(actor.player,StatRules.Stat.POWER),"overflow denied");check(actor.saves==0,"invalid balance no saves");
        actor.mode=GameMode.ADVENTURE;actor.xp(Integer.MAX_VALUE);ready(actor.player);check(stats.purchase(actor.player,StatRules.Stat.POWER),"adventure/INT_MAX valid");
        check(points(actor.player)==Integer.MAX_VALUE-1,"INT_MAX spent exactly");verifyCheckpoint(actor.player,Integer.MAX_VALUE-1,profile(actor.player).ranks.tag());
    }
    /** Dispatch through the actual RegisteredListener wrappers, which implement ignoreCancelled. */
    private void dispatch(Event event)throws Exception{for(RegisteredListener listener:event.getHandlers().getRegisteredListeners())if(listener.getListener()==stats||listener.getListener()==this)listener.callEvent(event);}
    private InventoryClickEvent click(Actor actor,InventoryView view,int slot,ClickType type,boolean cancelled)throws Exception{
        InventoryClickEvent event=new InventoryClickEvent(view,InventoryType.SlotType.CONTAINER,slot,type,type==ClickType.NUMBER_KEY?InventoryAction.HOTBAR_SWAP:type.isShiftClick()?InventoryAction.MOVE_TO_OTHER_INVENTORY:InventoryAction.PICKUP_ALL,0);
        event.setCancelled(cancelled);dispatch(event);return event;
    }
    private void menus()throws Exception{
        Actor actor=new Actor("menus");login(actor.player,true);actor.xp(1000);stats.open(actor.player);
        check(actor.top.getHolder() instanceof SurvivorStats.Panel && actor.top.getSize()==45,"real Paper panel created");
        SurvivorStats.Panel panel=(SurvivorStats.Panel)actor.top.getHolder();check(panel.owner.equals(actor.uuid),"owner encoded in holder");
        check(click(actor,actor.view(),20,ClickType.LEFT,false).isCancelled(),"selection canceled transfer");check(panel.selected==StatRules.Stat.POWER,"select actual power slot");check(actor.saves==0,"select not purchase");
        for(ClickType type:new ClickType[]{ClickType.SHIFT_LEFT,ClickType.SHIFT_RIGHT,ClickType.NUMBER_KEY,ClickType.DOUBLE_CLICK,ClickType.DROP,ClickType.CONTROL_DROP,ClickType.MIDDLE,ClickType.WINDOW_BORDER_LEFT}){
            ready(actor.player);check(click(actor,actor.view(),31,type,false).isCancelled(),"blocked special click "+type);check(actor.saves==0,"special click cannot buy "+type);
        }
        check(click(actor,actor.view(),31,ClickType.LEFT,true).isCancelled() && actor.saves==0,"pre-cancelled click cannot buy");
        for(int raw:new int[]{-999,45,50,80})check(click(actor,actor.view(),raw,ClickType.LEFT,false).isCancelled(),"bottom/outside blocked "+raw);
        InventoryDragEvent drag=new InventoryDragEvent(actor.view(),new ItemStack(Material.STONE),new ItemStack(Material.STONE,2),false,Collections.singletonMap(31,new ItemStack(Material.STONE)));
        dispatch(drag);check(drag.isCancelled() && actor.saves==0,"drag cannot enter menu");
        Actor thief=new Actor("thief");login(thief.player,true);thief.xp(1000);thief.top=actor.top;check(click(thief,thief.view(),31,ClickType.LEFT,false).isCancelled() && thief.saves==0,"another player cannot use owner panel");
        InventoryView stale=actor.view();actor.top=Bukkit.createInventory(null,45,"Elsewhere");check(click(actor,stale,31,ClickType.LEFT,false).isCancelled() && actor.saves==0,"stale view not current top rejected");
        actor.top=Bukkit.createInventory(null,45,ChatColor.DARK_RED+"Survivor Upgrades");actor.top.setItem(31,new ItemStack(Material.EMERALD));
        check(!click(actor,actor.view(),31,ClickType.LEFT,false).isCancelled() && actor.saves==0,"matching title/item is not a panel");
        actor.top=panel.inventory;login(actor.player,false);click(actor,actor.view(),31,ClickType.LEFT,false);check(actor.saves==0,"auth revoked while open");login(actor.player,true);
        actor.dead=true;click(actor,actor.view(),31,ClickType.LEFT,false);check(actor.saves==0,"dead while open");actor.dead=false;
        ready(actor.player);check(click(actor,actor.view(),31,ClickType.RIGHT,false).isCancelled(),"right click purchase retains item");check(actor.saves==1 && points(actor.player)==999 && profile(actor.player).ranks.rank(StatRules.Stat.POWER)==1,"one valid menu purchase");
        actor.mode=GameMode.CREATIVE;ready(actor.player);click(actor,actor.view(),31,ClickType.LEFT,false);check(actor.saves==1,"creative view cannot buy");
        closingActor=new Actor("closing");login(closingActor.player,true);stats.open(closingActor.player);check(click(closingActor,closingActor.view(),40,ClickType.LEFT,false).isCancelled(),"close button keeps inventory protected");
        staleClosingActor=new Actor("stale_closing");login(staleClosingActor.player,true);stats.open(staleClosingActor.player);click(staleClosingActor,staleClosingActor.view(),40,ClickType.LEFT,false);staleClosingActor.top=Bukkit.createInventory(null,9,"New screen");
    }
    private void seed(Actor actor,int...ranks)throws Exception{
        UUID life=UUID.randomUUID();new StatLifeStore(lifeFile(actor.player).getParent().toFile()).write(actor.uuid,life);
        actor.tags.add(new StatRules.Ranks(life,ranks).tag());actor.save();stats.quit(new PlayerQuitEvent(actor.player,"seed"));
    }
    private void attributes()throws Exception{
        Actor actor=new Actor("attributes");login(actor.player,true);actor.xp(500);seed(actor,10,10,10,10,10,10);
        Map<Attribute,Double> bases=new EnumMap<>(Attribute.class);for(Attr attr:actor.attrs.values()){bases.put(attr.attr,attr.base);attr.mods.add(new AttributeModifier(UUID.randomUUID(),"external-kit",.2,AttributeModifier.Operation.ADD_NUMBER));}
        int external=actor.modCount();double health=actor.health;refresh(actor.player);
        check(actor.modCount()==external+5,"five owned attribute modifiers (power event only)");
        near(actor.attrs.get(Attribute.GENERIC_MAX_HEALTH).getValue(),40.2,"vitality cap");near(actor.health,health,"vitality does not heal");
        near(actor.attrs.get(Attribute.GENERIC_MOVEMENT_SPEED).getValue(),.32,"speed additive +20% base");
        near(actor.attrs.get(Attribute.GENERIC_ARMOR).getValue(),5.2,"armor cap");near(actor.attrs.get(Attribute.GENERIC_ATTACK_SPEED).getValue(),5,"haste additive");near(actor.attrs.get(Attribute.GENERIC_KNOCKBACK_RESISTANCE).getValue(),.45,"stability additive");
        for(int i=0;i<50;i++)refresh(actor.player);check(actor.modCount()==external+5,"no duplicate refresh modifiers");
        for(Attr attr:actor.attrs.values())near(attr.base,bases.get(attr.attr),"never replaces base "+attr.attr);
        check(actor.potionCalls==0 && actor.player.getActivePotionEffects().contains(actor.potion),"native potion effects untouched");
        actor.health=40;actor.mode=GameMode.CREATIVE;refresh(actor.player);check(actor.modCount()==external,"creative strips only owned");near(actor.health,20.2,"health clamps after owned vitality removal");
        actor.mode=GameMode.SURVIVAL;refresh(actor.player);login(actor.player,false);refresh(actor.player);check(actor.modCount()==external,"auth loss removes only owned bonuses");
        login(actor.player,true);refresh(actor.player);stats.died(deathEvent(actor.player,true));check(actor.modCount()==external && profile(actor.player).ranks.total()==0,"death strips only stats");
        check(actor.potionCalls==0,"death leaves potion lifecycle to Minecraft");
    }
    private LivingEntity target(World targetWorld){return (LivingEntity)Proxy.newProxyInstance(getClass().getClassLoader(),new Class<?>[]{LivingEntity.class},(proxy,m,args)->{switch(m.getName()){case "getUniqueId":return id("target");case "getWorld":return targetWorld;case "isValid":return true;case "getType":return EntityType.ZOMBIE;case "getLocation":return new Location(targetWorld,0,65,4);case "getHealth":return 200d;default:return zero(m.getReturnType());}});}
    @EventHandler(priority=EventPriority.LOW) public void early(EntityDamageByEntityEvent event){if(cancelEarly)event.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGHEST) public void late(EntityDamageByEntityEvent event){if(cancelLate)event.setCancelled(true);}
    private void combat()throws Exception{
        Actor attacker=new Actor("combat");login(attacker.player,true);seed(attacker,0,10,0,0,0,0);LivingEntity victim=target(world);
        Projectile arrow=(Projectile)Proxy.newProxyInstance(getClass().getClassLoader(),new Class<?>[]{Projectile.class},(proxy,m,args)->m.getName().equals("getShooter")?attacker.player:zero(m.getReturnType()));
        for(EntityDamageEvent.DamageCause cause:new EntityDamageEvent.DamageCause[]{EntityDamageEvent.DamageCause.ENTITY_ATTACK,EntityDamageEvent.DamageCause.ENTITY_SWEEP_ATTACK,EntityDamageEvent.DamageCause.PROJECTILE}){
            Entity source=cause==EntityDamageEvent.DamageCause.PROJECTILE?arrow:attacker.player;
            EntityDamageByEntityEvent hit=new EntityDamageByEntityEvent(source,victim,cause,25);dispatch(hit);near(hit.getDamage(),35,"rank10 boost "+cause);
            cancelEarly=true;hit=new EntityDamageByEntityEvent(source,victim,cause,25);dispatch(hit);cancelEarly=false;near(hit.getDamage(),25,"early cancellation not modified "+cause);check(hit.isCancelled(),"early canceled preserved");
            cancelLate=true;hit=new EntityDamageByEntityEvent(source,victim,cause,25);dispatch(hit);cancelLate=false;check(hit.isCancelled(),"late protection remains canceled "+cause);
        }
        for(EntityDamageEvent.DamageCause cause:new EntityDamageEvent.DamageCause[]{EntityDamageEvent.DamageCause.MAGIC,EntityDamageEvent.DamageCause.THORNS,EntityDamageEvent.DamageCause.ENTITY_EXPLOSION}){EntityDamageByEntityEvent hit=new EntityDamageByEntityEvent(attacker.player,victim,cause,25);dispatch(hit);near(hit.getDamage(),25,"not weapon damage "+cause);}
        for(double amount:new double[]{0,-1,Double.NaN,Double.POSITIVE_INFINITY}){EntityDamageByEntityEvent hit=new EntityDamageByEntityEvent(attacker.player,victim,EntityDamageEvent.DamageCause.ENTITY_ATTACK,amount);dispatch(hit);check(Double.doubleToLongBits(hit.getDamage())==Double.doubleToLongBits(amount),"invalid/nonpositive damage untouched");}
        login(attacker.player,false);EntityDamageByEntityEvent hit=new EntityDamageByEntityEvent(attacker.player,victim,EntityDamageEvent.DamageCause.ENTITY_ATTACK,25);dispatch(hit);near(hit.getDamage(),25,"unauthenticated damage no bonus");login(attacker.player,true);
        attacker.mode=GameMode.CREATIVE;hit=new EntityDamageByEntityEvent(attacker.player,victim,EntityDamageEvent.DamageCause.ENTITY_ATTACK,25);dispatch(hit);near(hit.getDamage(),25,"creative damage no bonus");attacker.mode=GameMode.SURVIVAL;
        Actor playerVictim=new Actor("pvp");login(playerVictim.player,true);world.setPVP(false);hit=new EntityDamageByEntityEvent(attacker.player,playerVictim.player,EntityDamageEvent.DamageCause.ENTITY_ATTACK,25);dispatch(hit);near(hit.getDamage(),25,"PVP off no bonus");world.setPVP(true);
        playerVictim.mode=GameMode.CREATIVE;hit=new EntityDamageByEntityEvent(attacker.player,playerVictim.player,EntityDamageEvent.DamageCause.ENTITY_ATTACK,25);dispatch(hit);near(hit.getDamage(),25,"protected player no bonus");
        // Guns actually call LivingEntity.damage(amount, shooter). The separate native test below
        // proves Paper turns this exact pathway into a cancellable attacker-attributed event.
        NativePlayer nativeShooter=nativePlayer("gun_native");login(nativeShooter,true);SurvivorStats.setExperience(nativeShooter,500);check(stats.purchase(nativeShooter,StatRules.Stat.POWER),"native shooter buys power");
        Zombie zombie=world.spawn(new Location(world,8,65,8),Zombie.class);zombie.setAI(false);zombie.getAttribute(Attribute.GENERIC_ARMOR).setBaseValue(0);zombie.setHealth(20);zombie.setNoDamageTicks(0);
        zombie.damage(5,nativeShooter);near(zombie.getHealth(),14.8,"native firearm damage path receives stat multiplier");
        zombie.setNoDamageTicks(0);double hp=zombie.getHealth();cancelEarly=true;zombie.damage(5,nativeShooter);cancelEarly=false;near(zombie.getHealth(),hp,"native firearm early canceled no harm");
        zombie.setNoDamageTicks(0);cancelLate=true;zombie.damage(5,nativeShooter);cancelLate=false;near(zombie.getHealth(),hp,"native firearm late canceled no harm");zombie.remove();
        // Invoke the real firearm ray/aggregation path, then native zombie.damage and the stats listener.
        Arsenal arsenal=(Arsenal)field(plugin,"arsenal");Class<?> gunType=Class.forName("chat.jaspr.apocalypse.Arsenal$Gun");
        Object rifle=Enum.valueOf((Class)gunType,"RIFLE");Class<?> targetType=Class.forName("chat.jaspr.apocalypse.Arsenal$Target");
        Constructor<?> ctor=targetType.getDeclaredConstructor(LivingEntity.class,net.minecraft.server.v1_12_R1.AxisAlignedBB.class);ctor.setAccessible(true);
        // Stay inside one explicitly resident chunk, not the unloaded-neighbor collision guard.
        world.getChunkAt(0,0).load();Zombie gunTarget=world.spawn(new Location(world,8.5,90,12),Zombie.class);gunTarget.setAI(false);gunTarget.getAttribute(Attribute.GENERIC_MAX_HEALTH).setBaseValue(200);gunTarget.getAttribute(Attribute.GENERIC_ARMOR).setBaseValue(0);gunTarget.setHealth(200);gunTarget.setNoDamageTicks(0);
        List<Object> targets=Collections.singletonList(ctor.newInstance(gunTarget,new net.minecraft.server.v1_12_R1.AxisAlignedBB(7,89,11,10,94,13)));
        Location eye=new Location(world,8.5,91.6,8.5);Vector forward=new Vector(0,0,1);
        invoke(arsenal,"fire",new Class<?>[]{Player.class,Location.class,Vector.class,gunType,List.class},nativeShooter,eye,forward,rifle,targets);
        near(gunTarget.getHealth(),200-32*1.04,"actual Last Light rifle applies Power exactly once");
        double before=gunTarget.getHealth();gunTarget.setNoDamageTicks(0);cancelEarly=true;
        try{invoke(arsenal,"fire",new Class<?>[]{Player.class,Location.class,Vector.class,gunType,List.class},nativeShooter,eye,forward,rifle,targets);}finally{cancelEarly=false;}
        near(gunTarget.getHealth(),before,"actual rifle honors early cancellation");gunTarget.setNoDamageTicks(0);cancelLate=true;
        try{invoke(arsenal,"fire",new Class<?>[]{Player.class,Location.class,Vector.class,gunType,List.class},nativeShooter,eye,forward,rifle,targets);}finally{cancelLate=false;}
        near(gunTarget.getHealth(),before,"actual rifle honors late cancellation");gunTarget.remove();
    }
    private void failures()throws Exception{
        int index=0;for(String record:new String[]{StatRules.PREFIX+"bad",StatRules.PREFIX+UUID.randomUUID()+":11,0,0,0,0,0",StatRules.PREFIX+UUID.randomUUID()+":1,0,0,0,0,0"}){
            Actor actor=new Actor("badtag"+(index++));login(actor.player,true);actor.xp(500);actor.tags.add(record);check(!stats.purchase(actor.player,StatRules.Stat.POWER),"bad or checkpointless tag locked");check(actor.saves==0 && points(actor.player)==500 && actor.tags.contains(record),"invalid tag not overwritten/spent");check(profile(actor.player).locked,"invalid profile locked");
        }
        Actor duplicate=new Actor("duplicate");login(duplicate.player,true);duplicate.xp(500);seed(duplicate,1,0,0,0,0,0);duplicate.tags.add(StatRules.zero(UUID.randomUUID()).tag());int prior=duplicate.saves;
        check(!stats.purchase(duplicate.player,StatRules.Stat.POWER) && profile(duplicate.player).locked && duplicate.saves==prior,"duplicate tags fail closed");
        for(String mode:new String[]{"throw","noop","wrong-xp","wrong-tag","missing-tag"}){
            Actor actor=new Actor("save_"+mode.replace('-','_'));login(actor.player,true);actor.xp(500);actor.tags.add("foreign");actor.save();byte[] old=Files.readAllBytes(SurvivorStats.playerFile(actor.player).toPath());actor.saving=mode;
            check(!stats.purchase(actor.player,StatRules.Stat.POWER),"unconfirmed checkpoint rejected "+mode);check(points(actor.player)==500 && actor.total==500 && profile(actor.player).ranks.total()==0 && profile(actor.player).locked,"rollback in-memory/no spend/locked "+mode);
            check(actor.tags.size()==1 && actor.tags.contains("foreign"),"rollback previous tags "+mode);int writes=actor.saves;ready(actor.player);check(!stats.purchase(actor.player,StatRules.Stat.POWER)&&actor.saves==writes,"locked profile cannot retry "+mode);
            if(mode.equals("throw")||mode.equals("noop"))check(Arrays.equals(old,Files.readAllBytes(SurvivorStats.playerFile(actor.player).toPath())),"unwritten native data unchanged "+mode);
        }
        Actor full=new Actor("fulltags");login(full.player,true);full.xp(500);full.refuseTag=true;check(!stats.purchase(full.player,StatRules.Stat.POWER) && full.saves==0 && points(full.player)==500,"tag capacity failure no XP spent");
        Actor blocked=new Actor("blockedlife");login(blocked.player,true);blocked.xp(500);profile(blocked.player);Path file=lifeFile(blocked.player);Files.createDirectories(file);Files.write(file.resolve("occupied"),new byte[]{1});check(!stats.purchase(blocked.player,StatRules.Stat.POWER)&&points(blocked.player)==500&&blocked.saves==0,"life checkpoint write failure prevents XP spending");
        Actor corrupt=new Actor("corruptlife");login(corrupt.player,true);corrupt.xp(500);Files.write(lifeFile(corrupt.player),"broken\n".getBytes(StandardCharsets.UTF_8));check(!stats.purchase(corrupt.player,StatRules.Stat.POWER)&&profile(corrupt.player).locked&&corrupt.saves==0,"corrupt life storage locks");
        Actor noStorage=new Actor("nostorage");login(noStorage.player,true);noStorage.xp(500);Object old=field(stats,"lives");Field life=stats.getClass().getDeclaredField("lives");life.setAccessible(true);life.set(stats,null);
        try{check(!stats.purchase(noStorage.player,StatRules.Stat.POWER)&&noStorage.saves==0&&points(noStorage.player)==500,"unavailable store fails closed");}finally{life.set(stats,old);}
    }
    private PlayerDeathEvent deathEvent(Player player,boolean keep){PlayerDeathEvent event=new PlayerDeathEvent(player,new ArrayList<ItemStack>(),0,"fixture death");event.setKeepInventory(keep);event.setKeepLevel(keep);return event;}
    private void death()throws Exception{
        for(boolean keep:new boolean[]{false,true}){
            Actor actor=new Actor("death_"+keep);login(actor.player,true);actor.xp(700);seed(actor,10,9,8,7,6,5);byte[] stale=Files.readAllBytes(SurvivorStats.playerFile(actor.player).toPath());UUID old=profile(actor.player).ranks.life;
            refresh(actor.player);stats.open(actor.player);world.setGameRuleValue("keepInventory",Boolean.toString(keep));actor.dead=true;PlayerDeathEvent event=deathEvent(actor.player,keep);stats.died(event);
            check(profile(actor.player).ranks.total()==0 && !profile(actor.player).ranks.life.equals(old),"all six zero/new life keepInventory="+keep);check(event.getKeepInventory()==keep && event.getKeepLevel()==keep,"no native keep flags changed");
            check(!(actor.top.getHolder() instanceof SurvivorStats.Panel),"death closes panel");check(actor.modCount()==0,"death removes every own attribute");
            UUID checkpoint=new StatLifeStore(lifeFile(actor.player).getParent().toFile()).read(actor.uuid);check(checkpoint.equals(profile(actor.player).ranks.life),"death checkpoint durable before respawn");
            // Deliberately restore the previous player save while preserving the tombstone.
            Files.write(SurvivorStats.playerFile(actor.player).toPath(),stale);stats.quit(new PlayerQuitEvent(actor.player,"death crash"));Actor loaded=new Actor("death_"+keep);loaded.load();login(loaded.player,true);
            check(profile(loaded.player).ranks.total()==0 && profile(loaded.player).ranks.life.equals(checkpoint),"stale same-UUID NBT cannot resurrect old life");refresh(loaded.player);check(loaded.modCount()==0,"no stale attribute gain");
            stats.respawn(new PlayerRespawnEvent(loaded.player,new Location(world,0,65,0),false));
        }
        world.setGameRuleValue("keepInventory","false");
        Actor denied=new Actor("death_checkpoint_blocked");login(denied.player,true);denied.xp(600);seed(denied,1,2,3,4,5,6);refresh(denied.player);
        Files.delete(lifeFile(denied.player));Files.createDirectory(lifeFile(denied.player));Files.write(lifeFile(denied.player).resolve("occupied"),new byte[]{1});stats.died(deathEvent(denied.player,true));
        check(profile(denied.player).locked && profile(denied.player).ranks.total()==0,"failed death checkpoint locks new zero life");check(denied.modCount()==0 && points(denied.player)==600,"failed death checkpoint removes bonuses without spending XP");
        check(StatRules.parse(denied.tags.stream().filter(t->t.startsWith(StatRules.PREFIX)).findFirst().get()).total()==0,"death failure best-effort zero-tag fallback");
    }
    private void nativeStage()throws Exception{
        NativePlayer player=nativePlayer("persist_native");login(player,true);check(plugin.isSurvivor(player),"native actor authenticated");SurvivorStats.setExperience(player,2000);player.addScoreboardTag("fixture:unrelated");player.getInventory().setItem(0,new ItemStack(Material.LOG,13));
        check(stats.purchase(player,StatRules.Stat.VITALITY),"native first save transaction");ready(player);check(stats.purchase(player,StatRules.Stat.POWER),"native second save transaction");
        verifyCheckpoint(player,1998,profile(player).ranks.tag());check(SurvivorStats.playerFile(player).toPath().startsWith(new File("world/playerdata").toPath().toAbsolutePath().normalize()),"uses actual WorldNBTStorage directory");
        stats.quit(new PlayerQuitEvent(player,"fixture logout"));NativePlayer fresh=nativePlayer("persist_native");fresh.loadData();login(fresh,true);check(points(fresh)==1998 && profile(fresh).ranks.total()==2,"actual CraftPlayer.loadData rehydrates");
        NativePlayer dead=nativePlayer("stale_native");login(dead,true);SurvivorStats.setExperience(dead,1000);check(stats.purchase(dead,StatRules.Stat.VITALITY),"native stale vitality purchase");ready(dead);check(stats.purchase(dead,StatRules.Stat.POWER),"native stale-life fixture purchase saves prior vitality modifier");
        String old=profile(dead).ranks.tag();stats.died(deathEvent(dead,true));check(profile(dead).ranks.total()==0,"native death zeroes cached ranks");
        // Death intentionally leaves the old .dat; second JVM must invalidate it by the .life file.
        check(read(SurvivorStats.playerFile(dead)).getList("Tags",8).getString(0).equals(old),"prior-life native save retained for crash/restart proof");
    }
    private void nativeRestart()throws Exception{
        NativePlayer player=nativePlayer("persist_native");player.loadData();login(player,true);check(points(player)==1998,"raw XP across JVM restart");
        SurvivorStats.Profile state=profile(player);check(state.ranks.rank(StatRules.Stat.VITALITY)==1 && state.ranks.rank(StatRules.Stat.POWER)==1 && state.ranks.total()==2,"native saved ranks restart");
        check(player.getScoreboardTags().contains("fixture:unrelated"),"foreign native tag across restart");check(player.getInventory().getItem(0).equals(new ItemStack(Material.LOG,13)),"native inventory unchanged");
        refresh(player);near(player.getAttribute(Attribute.GENERIC_MAX_HEALTH).getValue(),22,"native vitality reapplied once");refresh(player);near(player.getAttribute(Attribute.GENERIC_MAX_HEALTH).getValue(),22,"no duplicate native attribute");
        ready(player);check(stats.purchase(player,StatRules.Stat.POWER),"restart next rank available");verifyCheckpoint(player,1996,profile(player).ranks.tag());
    }
    private void staleRestart()throws Exception{
        NativePlayer player=nativePlayer("stale_native");player.loadData();login(player,true);check(StatRules.parse(player.getScoreboardTags().stream().filter(t->t.startsWith(StatRules.PREFIX)).findFirst().get()).total()==2,"disk still contains old life's vitality/power");
        near(player.getAttribute(Attribute.GENERIC_MAX_HEALTH).getValue(),22,"old native .dat contains owned vitality modifier");
        UUID death=new StatLifeStore(lifeFile(player).getParent().toFile()).read(player.getUniqueId());check(profile(player).ranks.total()==0 && profile(player).ranks.life.equals(death),"restart tombstone wins over old native ranks");
        refresh(player);near(player.getAttribute(Attribute.GENERIC_MAX_HEALTH).getValue(),20,"zero state on recovery");
        stats.respawn(new PlayerRespawnEvent(player,new Location(world,0,65,0),false));
    }
    private void scheduled()throws Exception{
        check(Bukkit.getOnlinePlayers().isEmpty(),"scripted/native actors never placed in online player list");
        if(phase==1){for(boolean keep:new boolean[]{false,true}){Actor actor=new Actor("death_"+keep);actor.load();check(StatRules.parse(actor.tags.stream().filter(t->t.startsWith(StatRules.PREFIX)).findFirst().get()).total()==0,"scheduled respawn persisted cleared life "+keep);}
            check(closingActor!=null && !(closingActor.top.getHolder() instanceof SurvivorStats.Panel),"scheduled close closes matching panel");check(staleClosingActor!=null && staleClosingActor.top.getTitle().equals("New screen"),"scheduled close does not close a different screen");}
        else{NativePlayer player=nativePlayer("stale_native");player.loadData();check(StatRules.parse(player.getScoreboardTags().stream().filter(t->t.startsWith(StatRules.PREFIX)).findFirst().get()).total()==0,"native stale recovery writes zero tag after respawn");}
    }
    private void respawnFailureStage()throws Exception{
        for(String mode:new String[]{"noop","wrong-xp","runtime"}){
            Actor actor=new Actor("respawn_"+mode);login(actor.player,true);actor.xp(500);seed(actor,1,1,1,1,1,1);
            byte[] before=Files.readAllBytes(SurvivorStats.playerFile(actor.player).toPath());stats.died(deathEvent(actor.player,true));
            Files.write(SurvivorStats.playerFile(actor.player).toPath(),before);actor.saving=mode;respawnFailures.put(mode,actor);
            stats.respawn(new PlayerRespawnEvent(actor.player,new Location(world,0,65,0),false));
        }
    }
    private void respawnFailureResult(Actor actor)throws Exception{
        check(profile(actor.player).locked,"native saveData can silently fail/throw: unconfirmed respawn save must lock stats and log ("+actor.saving+")");
        check(profile(actor.player).ranks.total()==0 && points(actor.player)==500,"failed respawn never restores upgrades or spends XP");
    }
    private void finish(){
        for(String name:authenticatedNames)if(auth!=null)auth.removePlayer(name);HandlerList.unregisterAll(this);
        Map<String,Object> result=new LinkedHashMap<>();result.put("phase",phase);result.put("assertions",assertions);result.put("passed",phases);result.put("failures",failures);result.put("success",failures.isEmpty());
        result.put("scope","Current production sources in isolated Paper; real AuthMe cache; Bukkit event executors/inventories; native CraftPlayer save/load; scripted Player/Attribute fault injection. No browser keybind or real network login proof.");
        harness.getLogger().info("STATS_RUNTIME_RESULT "+new Gson().toJson(result));Bukkit.getScheduler().runTaskLater(harness,Bukkit::shutdown,1);
    }
    private void verifyCheckpoint(Player player,int xp,String tag)throws Exception{
        NBTTagCompound nbt=read(SurvivorStats.playerFile(player));check(nbt.getInt("XpTotal")==xp && nbt.getInt("XpLevel")==player.getLevel() && Float.floatToIntBits(nbt.getFloat("XpP"))==Float.floatToIntBits(player.getExp()),"atomic XP checkpoint fields match");
        NBTTagList list=nbt.getList("Tags",8);int found=0;for(int i=0;i<list.size();i++)if(list.getString(i).startsWith(StatRules.PREFIX)){check(list.getString(i).equals(tag),"same native save has exact rank tag");found++;}check(found==1,"one rank tag in native checkpoint");
    }
    private static NBTTagCompound read(File file)throws IOException{try(InputStream in=new FileInputStream(file)){return NBTCompressedStreamTools.a(in);}}
    private NativePlayer nativePlayer(String name)throws Exception{
        CraftServer server=(CraftServer)Bukkit.getServer();net.minecraft.server.v1_12_R1.WorldServer nativeWorld=((CraftWorld)world).getHandle();
        EntityPlayer handle=new EntityPlayer(server.getHandle().getServer(),nativeWorld,new GameProfile(id(name),name),new PlayerInteractManager(nativeWorld));
        NativePlayer player=new NativePlayer(server,handle);Field wrapper=net.minecraft.server.v1_12_R1.Entity.class.getDeclaredField("bukkitEntity");wrapper.setAccessible(true);wrapper.set(handle,player);handle.setPosition(0,65,0);return player;
    }
    private static final class NativePlayer extends CraftPlayer {
        NativePlayer(CraftServer server,EntityPlayer handle){super(server,handle);}
        @Override public boolean isOnline(){return true;}
        @Override public GameMode getGameMode(){return GameMode.SURVIVAL;}
        @Override public void sendMessage(String message){}
        @Override public void playSound(Location location,Sound sound,float volume,float pitch){}
    }
    private static Object zero(Class<?> type){if(type==boolean.class)return false;if(type==int.class)return 0;if(type==long.class)return 0L;if(type==double.class)return 0d;if(type==float.class)return 0f;return null;}
    private static final class Attr implements AttributeInstance {
        final Attribute attr;double base;final List<AttributeModifier> mods=new ArrayList<>();Attr(Attribute attr,double base){this.attr=attr;this.base=base;}
        public Attribute getAttribute(){return attr;}public double getBaseValue(){return base;}public void setBaseValue(double value){throw new AssertionError("Stats replaced base attribute");}
        public Collection<AttributeModifier> getModifiers(){return new ArrayList<>(mods);}public void addModifier(AttributeModifier mod){for(AttributeModifier m:mods)if(m.getUniqueId().equals(mod.getUniqueId()))throw new IllegalArgumentException("Duplicate modifier");mods.add(mod);}public void removeModifier(AttributeModifier mod){mods.remove(mod);}
        public double getValue(){double value=base;for(AttributeModifier mod:mods)value+=mod.getAmount();return value;}public double getDefaultValue(){return base;}
    }
    private final class Actor {
        final String name;final UUID uuid;final Player player;final PlayerInventory inventory;final Set<String> tags=new LinkedHashSet<>();final Map<Attribute,Attr> attrs=new EnumMap<>(Attribute.class);
        final PotionEffect potion=new PotionEffect(PotionEffectType.SPEED,600,2);int potionCalls;int level,total,saves;float bar;double health=20;boolean online=true,dead,refuseTag;GameMode mode=GameMode.SURVIVAL;String saving="normal";
        Inventory top;ItemStack[] items=new ItemStack[41];
        Actor(String name){this.name=name;uuid=id(name);top=Bukkit.createInventory(null,9,"Fixture");
            for(Attribute attr:Attribute.values())attrs.put(attr,new Attr(attr,attr==Attribute.GENERIC_MAX_HEALTH?20:attr==Attribute.GENERIC_MOVEMENT_SPEED?.1:attr==Attribute.GENERIC_ATTACK_SPEED?4:0));
            inventory=(PlayerInventory)Proxy.newProxyInstance(getClass().getClassLoader(),new Class<?>[]{PlayerInventory.class},(proxy,m,args)->{switch(m.getName()){
                case "getSize":return 41;case "getType":return InventoryType.PLAYER;case "getItem":return items[(Integer)args[0]];case "getContents":case "getStorageContents":return items.clone();case "getArmorContents":return new ItemStack[4];case "getHeldItemSlot":return 0;case "getItemInMainHand":return items[0];default:return zero(m.getReturnType());}});
            player=(Player)Proxy.newProxyInstance(getClass().getClassLoader(),new Class<?>[]{Player.class},(proxy,m,args)->{switch(m.getName()){
                case "getName":case "getDisplayName":return name;case "getUniqueId":return uuid;case "isOnline":case "isValid":return online;case "isDead":return dead;case "getGameMode":return mode;case "getWorld":return world;
                case "getLocation":return new Location(world,0,65,0);case "getEyeLocation":return new Location(world,0,66.62,0);case "getVelocity":return new Vector();case "getType":return EntityType.PLAYER;
                case "getHealth":return health;case "setHealth":health=(Double)args[0];return null;case "getAttribute":return attrs.get((Attribute)args[0]);
                case "getActivePotionEffects":return Collections.singletonList(potion);case "addPotionEffect":case "addPotionEffects":case "removePotionEffect":potionCalls++;throw new AssertionError("Stats changed potion state");
                case "getLevel":return level;case "setLevel":level=(Integer)args[0];return null;case "getExp":return bar;case "setExp":bar=(Float)args[0];return null;case "getTotalExperience":return total;case "setTotalExperience":total=(Integer)args[0];return null;
                case "getScoreboardTags":return new LinkedHashSet<>(tags);case "addScoreboardTag":return !refuseTag && tags.add((String)args[0]);case "removeScoreboardTag":return tags.remove((String)args[0]);case "saveData":save();return null;
                case "getInventory":return inventory;case "getOpenInventory":return view();case "openInventory":top=(Inventory)args[0];return view();case "closeInventory":top=Bukkit.createInventory(null,9,"Fixture");return null;
                case "equals":return proxy==args[0];case "hashCode":return uuid.hashCode();case "toString":return name;default:return zero(m.getReturnType());}});
        }
        void xp(int points){SurvivorStats.setExperience(player,points);}
        int modCount(){int n=0;for(Attr attr:attrs.values())n+=attr.mods.size();return n;}
        InventoryView view(){final Inventory current=top;return new InventoryView(){public Inventory getTopInventory(){return current;}public Inventory getBottomInventory(){return inventory;}public HumanEntity getPlayer(){return player;}public InventoryType getType(){return InventoryType.CHEST;}};}
        void save()throws IOException{
            saves++;if(saving.equals("throw"))throw new IOException("fixture injected save failure");if(saving.equals("runtime"))throw new IllegalStateException("fixture injected native save runtime failure");if(saving.equals("noop"))return;
            NBTTagCompound nbt=new NBTTagCompound();nbt.setInt("XpTotal",saving.equals("wrong-xp")?total+1:total);nbt.setInt("XpLevel",level);nbt.setFloat("XpP",bar);nbt.setString("FixtureUntouched","inventory/stats sentinel");nbt.setLong("UUIDMost",uuid.getMostSignificantBits());nbt.setLong("UUIDLeast",uuid.getLeastSignificantBits());
            NBTTagList tagList=new NBTTagList();for(String tag:tags)if(!(saving.equals("missing-tag")&&tag.startsWith(StatRules.PREFIX)))tagList.add(new NBTTagString(saving.equals("wrong-tag")&&tag.startsWith(StatRules.PREFIX)?StatRules.zero(UUID.randomUUID()).tag():tag));nbt.set("Tags",tagList);
            Path target=SurvivorStats.playerFile(player).toPath(),temporary=target.resolveSibling(uuid+".fixture-temp");try(OutputStream out=Files.newOutputStream(temporary)){NBTCompressedStreamTools.a(nbt,out);}Files.move(temporary,target,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);
        }
        void load()throws IOException{NBTTagCompound nbt=read(SurvivorStats.playerFile(player));level=nbt.getInt("XpLevel");total=nbt.getInt("XpTotal");bar=nbt.getFloat("XpP");tags.clear();NBTTagList list=nbt.getList("Tags",8);for(int i=0;i<list.size();i++)tags.add(list.getString(i));}
    }
}
