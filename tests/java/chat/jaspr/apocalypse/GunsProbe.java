package chat.jaspr.apocalypse;

import com.google.gson.Gson;
import fr.xephi.authme.api.v3.AuthMeApi;
import fr.xephi.authme.data.auth.PlayerAuth;
import fr.xephi.authme.data.auth.PlayerCache;
import java.io.*;
import java.lang.reflect.*;
import java.util.*;
import net.minecraft.server.v1_12_R1.AxisAlignedBB;
import net.minecraft.server.v1_12_R1.NBTCompressedStreamTools;
import net.minecraft.server.v1_12_R1.NBTTagCompound;
import org.bukkit.*;
import org.bukkit.attribute.*;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.command.*;
import org.bukkit.craftbukkit.v1_12_R1.inventory.CraftItemStack;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.*;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.*;
import org.bukkit.inventory.*;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.*;
import org.bukkit.util.Vector;
import org.bukkit.util.io.*;

/**
 * Real Paper/NBT/raycast/scheduler checks for the guns and the Portal Gun (2026-10-05, owner): an empty gun reloads by itself while the
 * inventory holds ammunition (sneak + right-click still reloads early), and the Portal Gun alternates blue and orange with no sneaking.
 * Deterministic player-interface fixtures; no network clients.
 */
public final class GunsProbe extends JavaPlugin implements Listener {
    private ApocalypsePlugin plugin;
    private Arsenal arsenal;
    private World world;
    private PlayerCache auth;
    private Actor actor;
    private int assertions;
    private final List<String> failures = new ArrayList<String>();
    private final Map<String,Object> metrics = new LinkedHashMap<String,Object>();
    private boolean run;
    private interface Checked { void run() throws Exception; }
    @Override public void onEnable() {
        try {
            File cwd = new File(".").getCanonicalFile();
            if (!Boolean.getBoolean("jaspr.apocalypse.smoke") || !cwd.getParentFile().getName().matches("apocalypse-smoke-[0-9a-f-]{36}"))
                throw new IllegalStateException("Guns probe requires isolated fixture");
            Bukkit.getPluginManager().registerEvents(this,this);
            getLogger().info("APOCALYPSE_SMOKE_ARMED guns probe");
        } catch (Exception failure) { throw new IllegalStateException(failure); }
    }
    @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof ConsoleCommandSender) || run) return true;
        run=true; Bukkit.getScheduler().runTask(this, new Runnable() { public void run() { begin(); } }); return true;
    }
    private void check(boolean value,String message) { assertions++; if(!value) throw new AssertionError(message); }
    private void phase(String name,Checked action) {
        try { action.run(); getLogger().info("APOCALYPSE_SMOKE_PHASE "+name+" PASS assertions="+assertions); }
        catch(Throwable failure) { while(failure.getCause()!=null) failure=failure.getCause(); failures.add(name+": "+failure); failure.printStackTrace(); }
    }
    private static Object field(Object object,String name) throws Exception {
        Field field=object.getClass().getDeclaredField(name);field.setAccessible(true);return field.get(object);
    }
    private static Object call(Object object,String name,Class<?>[] types,Object... args) throws Exception {
        Class<?> type=object instanceof Class?(Class<?>)object:object.getClass();
        Method method=type.getDeclaredMethod(name,types);method.setAccessible(true);return method.invoke(object instanceof Class?null:object,args);
    }
    private static NBTTagCompound data(ItemStack item) { return CraftItemStack.asNMSCopy(item).getTag().getCompound("JasprApocalypse"); }
    private void login(boolean value) {
        if(value) auth.updatePlayer(new PlayerAuth.Builder().name(actor.name).realName(actor.name).build());
        else auth.removePlayer(actor.name);
    }
    private void begin() {
        phase("setup",()->{
            check(Bukkit.getOnlinePlayers().isEmpty(),"No real players in fixture");
            plugin=(ApocalypsePlugin)Bukkit.getPluginManager().getPlugin("JasprApocalypse");
            arsenal=(Arsenal)field(plugin,"arsenal");
            world=Bukkit.getWorld("world");auth=(PlayerCache)field(AuthMeApi.getInstance(),"playerCache");
            actor=new Actor("anon_0706"); login(true);
            check(plugin.authenticated(actor.player),"Real AuthMe cache recognizes persistent guest fixture actor");
            for(int x=-1;x<=2;x++)for(int z=-1;z<=2;z++)world.getChunkAt(x,z).load();
            world.setGameRuleValue("doMobSpawning","false");
        });
        if(actor==null){finish();return;}
        phase("every-gun-reloads-by-itself",this::everyGun);
        phase("portal-gun-alternation",this::portalGunAlternation);
        phase("portal-gun-lore-upgrade",this::portalGunLore);
        // Last: it ends by starting a burst whose remaining shots fire on later ticks, so the actor must stay as it is.
        phase("auto-reload-rules",this::autoReloadRules);
        Bukkit.getScheduler().runTaskLater(this,()->{
            phase("burst-last-shot-reloads",this::burstDone);
            phase("lifecycle-cleanup",()->{arsenal.stop();check(((Map<?,?>)field(arsenal,"bursting")).isEmpty()&&((Map<?,?>)field(arsenal,"reloading")).isEmpty(),"Stop cancels gun tasks");arsenal.start();});
            finish();
        },60L);
    }
    private Object gun(ItemStack item) throws Exception{return call(Arsenal.class,"identify",new Class<?>[]{ItemStack.class},item);}
    private ItemStack loaded(String id,int count) throws Exception {ItemStack item=ApocalypseItems.gear(id);Object gun=gun(item);return (ItemStack)call(Arsenal.class,"rounds",new Class<?>[]{ItemStack.class,gun.getClass(),int.class},item,gun,count);}
    private void ready() throws Exception{((Map<?,?>)field(arsenal,"readyAt")).clear();}
    private PlayerInteractEvent interact(){
        PlayerInteractEvent event=new PlayerInteractEvent(actor.player,Action.RIGHT_CLICK_AIR,actor.storage[0],null,BlockFace.SELF,EquipmentSlot.HAND);event.setUseItemInHand(Event.Result.ALLOW);return event;
    }
    private Map<?,?> reloading() throws Exception { return (Map<?,?>)field(arsenal,"reloading"); }
    private void cancelReload() { arsenal.held(new PlayerItemHeldEvent(actor.player,0,1)); }
    private void reset() throws Exception { login(true);actor.mode=GameMode.SURVIVAL;actor.sneaking=false;actor.storage=new ItemStack[36];reloading().clear();ready(); }

    /** Every firearm: an empty click and the last round both start the reload when iron nuggets are carried. */
    private void everyGun() throws Exception {
        int guns=0;
        for(String id:ApocalypseItems.catalogue("gun").keySet()){
            reset();
            actor.storage[0]=loaded(id,0);actor.storage[1]=new ItemStack(Material.IRON_NUGGET,64);arsenal.interact(interact());
            check(reloading().size()==1,"An empty gun reloads by itself: "+id);
            check(actor.storage[1].getAmount()==64&&data(actor.storage[0]).getInt("rounds")==0,"The automatic reload costs nothing before it completes: "+id);
            cancelReload();check(reloading().isEmpty(),"Switching slot cancels it: "+id);
            actor.storage[0]=loaded(id,1);ready();arsenal.interact(interact());
            check(data(actor.storage[0]).getInt("rounds")==0&&reloading().size()==1,"The last round starts the reload: "+id);
            cancelReload();check(reloading().isEmpty(),"... and it can be cancelled: "+id);
            guns++;
        }
        check(guns==ApocalypseItems.catalogue("gun").size()&&guns>=35,"Every firearm covered: "+guns);
        metrics.put("gunsCovered",guns);
    }
    /** The rules around it: no ammunition, marked salvage, rounds left, sneak + right-click, creative, a burst gun. */
    private void autoReloadRules() throws Exception {
        reset();
        // No ammunition at all: nothing starts and the player is told why.
        ((Map<?,?>)field(arsenal,"messageAt")).clear();actor.storage[0]=loaded("rifle",0);int told=actor.messages.size();arsenal.interact(interact());
        check(reloading().isEmpty()&&actor.messages.size()>told,"An empty gun with no ammunition does not reload and says so");
        // Marked salvage is not ammunition.
        actor.storage[1]=ApocalypseItems.scrap(64);ready();arsenal.interact(interact());
        check(reloading().isEmpty(),"Marked salvage is not ammunition for the automatic reload");
        // The last round without anything to load it with.
        actor.storage[1]=null;actor.storage[0]=loaded("rifle",1);ready();arsenal.interact(interact());
        check(data(actor.storage[0]).getInt("rounds")==0&&reloading().isEmpty(),"The last round starts no reload without ammunition");
        // Fewer nuggets than one round costs.
        Object shotgun=gun(loaded("shotgun",0));int cost=(Integer)field(shotgun,"ammoCost");
        if(cost>1){actor.storage[1]=new ItemStack(Material.IRON_NUGGET,cost-1);actor.storage[0]=loaded("shotgun",0);ready();arsenal.interact(interact());
            check(reloading().isEmpty(),"Fewer nuggets than a round costs does not start a reload");}
        // A gun that still has rounds fires, it does not reload.
        actor.storage[1]=new ItemStack(Material.IRON_NUGGET,30);actor.storage[0]=loaded("rifle",5);ready();arsenal.interact(interact());
        check(data(actor.storage[0]).getInt("rounds")==4&&reloading().isEmpty(),"A gun with rounds left fires, it does not reload");
        // Sneak + right-click on a partly filled magazine still reloads early (the old behaviour).
        actor.sneaking=true;ready();arsenal.interact(interact());
        check(reloading().size()==1&&data(actor.storage[0]).getInt("rounds")==4,"Sneak + right-click still reloads before the magazine is empty");
        cancelReload();check(reloading().isEmpty(),"The early reload is cancellable");
        // Sneaking with an empty gun reloads too (and only once).
        actor.storage[0]=loaded("rifle",0);ready();arsenal.interact(interact());check(reloading().size()==1,"Sneak + right-click on an empty gun reloads");
        arsenal.interact(interact());check(reloading().size()==1,"A second click while reloading does not start another");
        cancelReload();actor.sneaking=false;
        // A reload that is under way is not restarted by the last round of another gun, and a completed one fills the magazine.
        actor.storage[0]=loaded("rifle",0);actor.storage[1]=new ItemStack(Material.IRON_NUGGET,30);ready();arsenal.interact(interact());
        check(reloading().size()==1,"Reload under way");
        // A burst gun: the first shot of the burst does not reload yet; the last one does (checked once the burst has finished).
        cancelReload();actor.storage[0]=loaded("tempest",3);actor.storage[1]=new ItemStack(Material.IRON_NUGGET,30);ready();arsenal.interact(interact());
        check(data(actor.storage[0]).getInt("rounds")==2&&reloading().isEmpty(),"The burst's first shot does not reload");
    }
    private void burstDone() throws Exception {
        check(data(actor.storage[0]).getInt("rounds")==0&&((Map<?,?>)field(arsenal,"bursting")).isEmpty(),"The burst has fired its last round");
        check(reloading().size()==1,"The burst's last shot starts the automatic reload");
        cancelReload();check(reloading().isEmpty(),"The reload after a burst is cancellable");
    }
    /** PortalGun is package-private and this probe has its own class loader, so it is driven by reflection. */
    private void fire(Object gun) throws Exception { call(gun,"onInteract",new Class<?>[]{PlayerInteractEvent.class},interact()); }
    /** The Portal Gun alternates blue and orange by itself; sneaking changes nothing. */
    private void portalGunAlternation() throws Exception {
        Object gun=field(plugin,"portalGun");Map<?,?> pairs=(Map<?,?>)field(gun,"pairs");
        reset();pairs.clear();actor.messages.clear();
        for(int y=90;y<=94;y++)for(int x=-4;x<=4;x++)world.getBlockAt(x,y,6).setType(Material.STONE);
        try {
            actor.storage[0]=ApocalypseItems.expedition("portal_gun",5);
            String lore=String.valueOf(actor.storage[0].getItemMeta().getLore());
            check(lore.contains("alternate")&&!lore.contains("cyan")&&!lore.contains("amber")&&!lore.contains("Sneak"),"The Portal Gun's lore describes the alternating shots");
            fire(gun);
            Object pair=pairs.get(actor.uuid);
            check(pair!=null&&field(pair,"blue")!=null&&field(pair,"orange")==null,"The first shot opens the blue portal");
            String last=actor.messages.get(actor.messages.size()-1);
            check(last.contains("Blue portal set")&&last.contains("Next: orange"),"The player is told which colour comes next");
            fire(gun);
            check(field(pair,"blue")!=null&&field(pair,"orange")!=null,"The second shot opens the orange portal");
            last=actor.messages.get(actor.messages.size()-1);
            check(last.contains("Orange portal set")&&last.contains("linked")&&last.contains("Next: blue"),"The orange shot links the pair and names the next colour");
            Object blue=field(pair,"blue"),orange=field(pair,"orange");
            fire(gun);
            check(field(pair,"blue")!=blue&&field(pair,"orange")==orange,"The third shot opens the blue portal again");
            // Sneaking changes nothing: the next shot is orange all the same.
            actor.sneaking=true;blue=field(pair,"blue");
            fire(gun);
            check(field(pair,"orange")!=orange&&field(pair,"blue")==blue,"Sneaking does not choose the colour: the next shot is orange");
            actor.sneaking=false;
            // The portals are drawn in their own colours.
            Object bluePortal=field(pair,"blue"),orangePortal=field(pair,"orange");
            check(!(Boolean)field(bluePortal,"orange")&&(Boolean)field(orangePortal,"orange"),"Each portal knows its colour");
            // A shot that finds no surface does not take its turn.
            boolean next=(Boolean)field(pair,"nextOrange");
            for(int y=90;y<=94;y++)for(int x=-4;x<=4;x++)world.getBlockAt(x,y,6).setType(Material.AIR);
            fire(gun);
            check((Boolean)field(pair,"nextOrange")==next&&actor.messages.get(actor.messages.size()-1).contains("No surface"),"A shot that finds no surface keeps its turn");
            String metricsText=(String)call(gun,"metrics",new Class<?>[]{});
            check(metricsText.contains("openPortals=2"),"Two portals open for the owner: "+metricsText);
        } finally {
            for(int y=90;y<=94;y++)for(int x=-4;x<=4;x++)world.getBlockAt(x,y,6).setType(Material.AIR);
            pairs.clear();
        }
    }
    /** Portal guns made before the shots alternated get today's lore; nothing else about them changes. */
    private void portalGunLore() throws Exception {
        ItemStack fresh=ApocalypseItems.expedition("portal_gun",3);
        ItemStack old=fresh.clone();org.bukkit.inventory.meta.ItemMeta meta=old.getItemMeta();
        meta.setLore(Arrays.asList(ChatColor.GRAY+"Right-click: cyan portal",ChatColor.GRAY+"Sneak + right-click: amber portal",ChatColor.GRAY+"Anyone can travel through your portals",ChatColor.DARK_GRAY+"Recovered expedition equipment"));
        old.setItemMeta(meta);
        ItemStack refreshed=(ItemStack)call(ExpeditionEquipment.class,"refreshPortalGunLore",new Class<?>[]{ItemStack.class},old);
        check(refreshed!=old&&refreshed.getItemMeta().getLore().equals(fresh.getItemMeta().getLore()),"An old Portal Gun gets the new lore");
        check(data(refreshed).getString("serial").equals(data(old).getString("serial"))&&data(refreshed).getInt("tier")==data(old).getInt("tier")&&refreshed.getDurability()==1160,"Its serial, tier and model band are untouched");
        check(call(ExpeditionEquipment.class,"refreshPortalGunLore",new Class<?>[]{ItemStack.class},refreshed)==refreshed,"A current Portal Gun is left alone (same stack back)");
        ItemStack other=ApocalypseItems.gear("trauma_kit");
        check(call(ExpeditionEquipment.class,"refreshPortalGunLore",new Class<?>[]{ItemStack.class},other)==other,"Other items are left alone");
        check(call(ExpeditionEquipment.class,"refreshPortalGunLore",new Class<?>[]{ItemStack.class},new Object[]{null})==null,"No item, no change");
    }
    private void finish(){
        if(auth!=null&&actor!=null)auth.removePlayer(actor.name);
        metrics.put("scope","Isolated Paper 1.12 JVM; native NBT/raycast/scheduler; scripted Player and Attribute interfaces, not client animation/network tests");
        Map<String,Object> result=new LinkedHashMap<String,Object>();result.put("success",failures.isEmpty());result.put("assertions",assertions);result.put("failures",failures);result.put("metrics",metrics);
        getLogger().info("APOCALYPSE_SMOKE_RESULT "+new Gson().toJson(result));
    }

    private static Object zero(Class<?> type){if(type==boolean.class)return false;if(type==int.class)return 0;if(type==long.class)return 0L;if(type==double.class)return 0d;if(type==float.class)return 0f;return null;}
    private static final class Attr implements AttributeInstance {
        final Attribute attribute;double base;final List<AttributeModifier> mods=new ArrayList<AttributeModifier>();
        Attr(Attribute attribute,double base){this.attribute=attribute;this.base=base;}
        public Attribute getAttribute(){return attribute;}public double getBaseValue(){return base;}public void setBaseValue(double base){this.base=base;}
        public Collection<AttributeModifier> getModifiers(){return new ArrayList<AttributeModifier>(mods);}public void addModifier(AttributeModifier m){mods.add(m);}public void removeModifier(AttributeModifier m){mods.remove(m);}
        public double getValue(){double value=base;for(AttributeModifier m:mods)value+=m.getAmount();return value;}public double getDefaultValue(){return base;}
    }
    private final class Actor {
        final String name;final UUID uuid=UUID.randomUUID();final Player player;final PlayerInventory inventory;
        ItemStack[] storage=new ItemStack[36],armor=new ItemStack[4];ItemStack cursor,offhand;
        final Map<Attribute,Attr> attributes=new EnumMap<Attribute,Attr>(Attribute.class);
        final Set<String> scoreboardTags=new HashSet<String>();
        final List<String> messages=new ArrayList<String>();
        final PotionEffect potion=new PotionEffect(PotionEffectType.SPEED,400,2);
        World currentWorld=world;Location location;GameMode mode=GameMode.SURVIVAL;boolean dead,sneaking,op;double health=20;int food=20,fire;float saturation=0;
        Actor(String name){
            location=new Location(currentWorld,.5,90,.5);
            this.name=name;for(Attribute attr:Attribute.values())attributes.put(attr,new Attr(attr,attr==Attribute.GENERIC_MAX_HEALTH?20:attr==Attribute.GENERIC_MOVEMENT_SPEED?.1:attr==Attribute.GENERIC_ATTACK_SPEED?4:0));
            inventory=(PlayerInventory)Proxy.newProxyInstance(getClassLoader(),new Class<?>[]{PlayerInventory.class},(p,m,a)->{
                switch(m.getName()){
                    case "getArmorContents":return armor;case "getStorageContents":return storage;case "getContents":return storage;
                    case "getHeldItemSlot":return 0;case "getItemInMainHand":return storage[0];case "setItemInMainHand":storage[0]=(ItemStack)a[0];return null;
                    case "getItemInOffHand":return offhand;case "setItemInOffHand":offhand=(ItemStack)a[0];return null;
                    case "getItem":return storage[(Integer)a[0]];case "setItem":storage[(Integer)a[0]]=(ItemStack)a[1];return null;case "getSize":return 41;
                    case "addItem":{Map<Integer,ItemStack> left=new HashMap<Integer,ItemStack>();
                        for(ItemStack stack:(ItemStack[])a[0]){boolean done=false;
                            for(int i=0;i<storage.length&&!done;i++)if(storage[i]==null){storage[i]=stack;done=true;}
                            if(!done)left.put(left.size(),stack);}
                        return left;}
                    case "getType":return InventoryType.PLAYER;default:return zero(m.getReturnType());
                }
            });
            player=(Player)Proxy.newProxyInstance(getClassLoader(),new Class<?>[]{Player.class},(p,m,a)->{
                switch(m.getName()){
                    case "getName":case "getDisplayName":return name;case "getUniqueId":return uuid;case "isOnline":case "isValid":return true;
                    case "isDead":return dead;case "getGameMode":return mode;case "getWorld":return currentWorld;case "getInventory":return inventory;
                    case "getAttribute":return attributes.get((Attribute)a[0]);case "getLocation":return location.clone();case "getEyeLocation":return location.clone().add(0,1.62,0);
                    case "teleport":location=((Location)a[0]).clone();currentWorld=location.getWorld();return true;
                    case "sendMessage":if(a[0] instanceof String)messages.add((String)a[0]);else if(a[0] instanceof String[])messages.addAll(Arrays.asList((String[])a[0]));return null;
                    case "isOp":return op;case "setOp":op=(Boolean)a[0];return null;
                    case "getScoreboardTags":return Collections.unmodifiableSet(scoreboardTags);
                    case "addScoreboardTag":return scoreboardTags.add((String)a[0]);case "removeScoreboardTag":return scoreboardTags.remove((String)a[0]);
                    case "getVelocity":return new Vector();case "isSneaking":return sneaking;case "getHealth":return health;case "setHealth":health=(Double)a[0];return null;
                    case "getFoodLevel":return food;case "setFoodLevel":food=(Integer)a[0];return null;case "getSaturation":return saturation;case "setSaturation":saturation=(Float)a[0];return null;
                    case "getFireTicks":return fire;case "setFireTicks":fire=(Integer)a[0];return null;case "getActivePotionEffects":return Collections.singletonList(potion);
                    case "addPotionEffect":case "addPotionEffects":case "removePotionEffect":throw new AssertionError("Equipment must not touch potions");
                    case "getItemOnCursor":return cursor;case "setItemOnCursor":cursor=(ItemStack)a[0];return null;
                    case "equals":return p==a[0];case "hashCode":return uuid.hashCode();case "toString":return name;case "getType":return EntityType.PLAYER;
                    default:return zero(m.getReturnType());
                }
            });
        }
        int modifierCount(){int count=0;for(Attr attr:attributes.values())count+=attr.mods.size();return count;}
    }
}
