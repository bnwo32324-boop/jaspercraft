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

/** Actual Paper/NBT/recipes/raycasts/scheduler with deterministic player-interface fixtures. No network clients. */
public final class EquipmentProbe extends JavaPlugin implements Listener {
    private ApocalypsePlugin plugin;
    private Arsenal arsenal;
    private ExpeditionEquipment equipment;
    private World world;
    private PlayerCache auth;
    private Actor actor;
    private int assertions;
    private final List<String> failures = new ArrayList<String>();
    private final Map<String,Object> metrics = new LinkedHashMap<String,Object>();
    private boolean cancelHealing, cancelFood, cancelDamage, run;
    private interface Checked { void run() throws Exception; }
    @Override public void onEnable() {
        try {
            File cwd = new File(".").getCanonicalFile();
            if (!Boolean.getBoolean("jaspr.apocalypse.smoke") || !cwd.getParentFile().getName().matches("apocalypse-smoke-[0-9a-f-]{36}"))
                throw new IllegalStateException("Equipment probe requires isolated fixture");
            Bukkit.getPluginManager().registerEvents(this,this);
            getLogger().info("APOCALYPSE_SMOKE_ARMED equipment probe");
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
    private static void setField(Object object,String name,Object value) throws Exception {
        Field field=object.getClass().getDeclaredField(name);field.setAccessible(true);field.set(object,value);
    }
    private static NBTTagCompound data(ItemStack item) { return CraftItemStack.asNMSCopy(item).getTag().getCompound("JasprApocalypse"); }
    private void login(boolean value) {
        login(actor,value);
    }
    private void login(Actor who,boolean value) {
        if(value) auth.updatePlayer(new PlayerAuth.Builder().name(who.name).realName(who.name).build());
        else auth.removePlayer(who.name);
    }
    private void begin() {
        phase("setup",()->{
            check(Bukkit.getOnlinePlayers().isEmpty(),"No real players in fixture");
            plugin=(ApocalypsePlugin)Bukkit.getPluginManager().getPlugin("JasprApocalypse");
            arsenal=(Arsenal)field(plugin,"arsenal"); equipment=(ExpeditionEquipment)field(plugin,"equipment");
            world=Bukkit.getWorld("world");auth=(PlayerCache)field(AuthMeApi.getInstance(),"playerCache");
            actor=new Actor("anon_0706"); login(true);
            check(plugin.authenticated(actor.player),"Real AuthMe cache recognizes persistent guest fixture actor");
            for(int x=-1;x<=2;x++)for(int z=-1;z<=2;z++)world.getChunkAt(x,z).load();
            world.setGameRuleValue("doMobSpawning","false");
        });
        if(actor==null){finish();return;}
        phase("teleport-requests",this::teleportRequests);
        phase("catalogue-NBT-forgery",this::items);
        phase("tagged-crafting-and-anvil",this::crafting);
        phase("full-set-attributes-and-auth",this::armor);
        phase("melee-supplies-and-protection",this::combat);
        phase("daylight-cycle-and-mining",this::daylight);
        phase("guaranteed-flesh-drops",this::flesh);
        phase("gun-auth-rays-and-stats",this::guns);
        phase("all-gun-hit-aggregation-and-walls",this::hitLanes);
        phase("all-gun-reload-cancellation",this::reloadMatrix);
        phase("reload-start-and-cancel",this::reloadStart);
        phase("sentry-placement-and-pickup",this::sentry);
        phase("waypoint-store-and-menu",this::waypoints);
        Bukkit.getScheduler().runTaskLater(this,()->{
            phase("reload-completion",this::reloadDone);
            phase("burst-start",this::burstStart);
            Bukkit.getScheduler().runTaskLater(this,()->{
                phase("burst-completion-and-cancellation",this::burstDone);
                phase("expansion-reload-start",this::expansionReloadStart);
                Bukkit.getScheduler().runTaskLater(this,()->{
                    phase("expansion-reload-done",this::expansionReloadDone);
                    phase("expansion-burst-start",()->startBurst("blackbox"));
                    Bukkit.getScheduler().runTaskLater(this,()->{
                        phase("expansion-burst-done",()->endBurst("blackbox"));
                        phase("heavy-burst-start",()->startBurst("deadfrequency"));
                        Bukkit.getScheduler().runTaskLater(this,()->{
                            phase("heavy-burst-done",()->endBurst("deadfrequency"));
                            phase("lifecycle-cleanup",this::lifecycle);
                            finish();
                        },26L);
                    },26L);
                },110L);
            },26L);
        },55L);
    }
    private void teleportRequests() throws Exception {
        final Actor recipient=new Actor("teleportfixture");
        login(recipient,true);
        final Map<String,Actor> players=new HashMap<String,Actor>();
        players.put(actor.name.toLowerCase(Locale.ROOT),actor);
        players.put(recipient.name.toLowerCase(Locale.ROOT),recipient);
        TeleportRequests requests=new TeleportRequests(plugin,new TeleportRequests.Lookup(){
            @Override public Player byName(String name){Actor found=players.get(name.toLowerCase(Locale.ROOT));return found==null?null:found.player;}
            @Override public Player byId(UUID id){for(Actor found:players.values())if(found.uuid.equals(id))return found.player;return null;}
        });
        try {
            actor.op=false;recipient.op=false;
            actor.location=new Location(world,1.5,80,1.5);recipient.location=new Location(world,40.5,91,-12.5);
            Location before=actor.location.clone();
            check(requests.command(actor.player,"tpa",new String[]{recipient.name}),"Non-op /tpa handled");
            check(actor.location.equals(before),"Request does not teleport before consent");
            check(requests.command(recipient.player,"tpaccept",new String[0]),"Non-op /tpaccept handled");
            check(actor.location.equals(recipient.location),"Accepted non-op request teleports requester to recipient");

            actor.location=new Location(world,5.5,82,5.5);recipient.location=new Location(world,60.5,92,-8.5);
            before=actor.location.clone();requests.command(actor.player,"tpa",new String[]{recipient.name});
            requests.command(recipient.player,"tpdeny",new String[0]);
            check(actor.location.equals(before)&&requests.metrics().endsWith("0"),"Denied request never teleports and is consumed");

            actor.op=true;recipient.op=false;
            actor.location=new Location(world,-30.5,77,14.5);recipient.location=new Location(world,90.5,88,20.5);
            Location requesterPosition=actor.location.clone();
            requests.command(actor.player,"tpahere",new String[]{recipient.name});
            requests.command(recipient.player,"tpaccept",new String[0]);
            check(recipient.location.equals(requesterPosition),"Non-op recipient can accept an op request and teleport to requester");

            actor.op=false;recipient.op=true;
            actor.location=new Location(world,7.5,80,7.5);recipient.location=new Location(world,70.5,90,7.5);
            before=actor.location.clone();requests.command(actor.player,"tp",new String[]{recipient.name});
            check(actor.location.equals(before),"Compatibility /tp also waits for consent");
            requests.command(recipient.player,"tpaccept",new String[0]);
            check(actor.location.equals(recipient.location),"Compatibility /tp completes after /tpaccept");

            actor.location=new Location(world,9.5,80,9.5);before=actor.location.clone();
            requests.command(actor.player,"tpa",new String[]{recipient.name});
            requests.command(actor.player,"tpcancel",new String[0]);
            requests.command(recipient.player,"tpaccept",new String[0]);
            check(actor.location.equals(before)&&requests.metrics().endsWith("0"),"Cancelled request cannot be accepted or reused");

            login(recipient,false);
            requests.command(actor.player,"tpa",new String[]{recipient.name});
            check(requests.metrics().endsWith("0"),"Signed-out recipients cannot receive requests");
            check(!actor.messages.isEmpty()&&!recipient.messages.isEmpty(),"Both request participants receive feedback");
        } finally {
            requests.stop();login(recipient,false);actor.op=false;actor.location=new Location(world,.5,90,.5);
        }
    }

    private ItemStack roundTrip(ItemStack item) throws Exception {
        ByteArrayOutputStream bytes=new ByteArrayOutputStream();
        try(BukkitObjectOutputStream out=new BukkitObjectOutputStream(bytes)){out.writeObject(item);}
        ItemStack result;
        try(BukkitObjectInputStream in=new BukkitObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))){result=(ItemStack)in.readObject();}
        check(persistedEquivalent(item,result),"Bukkit NBT serialization "+ApocalypseItems.id(item));
        bytes=new ByteArrayOutputStream();NBTCompressedStreamTools.a(CraftItemStack.asNMSCopy(result).save(new NBTTagCompound()),bytes);
        result=CraftItemStack.asBukkitCopy(new net.minecraft.server.v1_12_R1.ItemStack(NBTCompressedStreamTools.a(new ByteArrayInputStream(bytes.toByteArray()))));
        check(persistedEquivalent(item,result),"Compressed disk NBT "+ApocalypseItems.id(item));return result;
    }
    private boolean persistedEquivalent(ItemStack expected,ItemStack actual) {
        if(!"guide".equals(ApocalypseItems.id(expected)))return expected.equals(actual);
        if(actual==null||expected.getType()!=actual.getType()||expected.getAmount()!=actual.getAmount()
            ||!ApocalypseItems.id(expected).equals(ApocalypseItems.id(actual)))return false;
        org.bukkit.inventory.meta.BookMeta left=(org.bukkit.inventory.meta.BookMeta)expected.getItemMeta();
        org.bukkit.inventory.meta.BookMeta right=(org.bukkit.inventory.meta.BookMeta)actual.getItemMeta();
        return Objects.equals(left.getTitle(),right.getTitle())&&Objects.equals(left.getAuthor(),right.getAuthor())
            &&Objects.equals(left.getPages(),right.getPages())
            &&data(expected).getInt("tier")==data(actual).getInt("tier");
    }
    private void items() throws Exception {
        check(ApocalypseItems.catalogue().size()==89,"89 stable ids including supplies and artifacts");
        check(ApocalypseItems.catalogue("gun").size()==35,"35 guns");
        check(ApocalypseItems.catalogue("melee").size()==24,"24 melee");
        check(ApocalypseItems.catalogue("armor").size()==16,"16 armor pieces");
        check(ApocalypseItems.catalogue("material").size()==4,"4 materials");
        check(ApocalypseItems.catalogue("consumable").size()==4,"4 consumables");
        check(ApocalypseItems.catalogue("artifact").size()==2,"2 artifacts");
        check(ApocalypseItems.catalogue("block").size()==1,"1 placeable block");
        try { ApocalypseItems.catalogue().clear();check(false,"Catalogue mutable"); } catch(UnsupportedOperationException expected){check(true,"Immutable catalogue");}
        try { ApocalypseItems.gear("unknown");check(false,"Unknown item accepted"); } catch(IllegalArgumentException expected){check(true,"Unknown item rejected");}
        Set<String> serials=new HashSet<String>();
        for(String id:ApocalypseItems.catalogue().keySet()) {
            ItemStack item=roundTrip(ApocalypseItems.expedition(id,9));
            check(id.equals(ApocalypseItems.id(item)),"Stable id "+id);
            check(data(item).getInt("tier")==5 && data(ApocalypseItems.expedition(id,-2)).getInt("tier")==1,"Clamped tier "+id);
            if(ApocalypseItems.catalogue("supply").containsKey(id)||ApocalypseItems.catalogue("artifact").containsKey(id))continue;
            boolean gun=ApocalypseItems.catalogue("gun").containsKey(id);
            if(gun)check(call(Arsenal.class,"identify",new Class<?>[]{ItemStack.class},item)!=null,"Gun valid "+id);
            else check(ExpeditionEquipment.verified(item),"Equipment valid "+id);
            ItemStack fake=new ItemStack(item.getType(),1,item.getDurability());fake.setItemMeta(item.getItemMeta());
            // ItemMeta can carry unknown NBT; construct cosmetic-only metadata instead.
            org.bukkit.inventory.meta.ItemMeta cosmetic=new ItemStack(item.getType()).getItemMeta();
            cosmetic.setDisplayName(item.getItemMeta().getDisplayName()); cosmetic.setLore(item.getItemMeta().getLore()); cosmetic.setUnbreakable(true);fake.setItemMeta(cosmetic);
            fake=ApocalypseItems.mark(fake,id);
            check(gun?call(Arsenal.class,"identify",new Class<?>[]{ItemStack.class},fake)==null:!ExpeditionEquipment.verified(fake),"Id/name/lore cannot forge "+id);
            if(gun) {
                check(data(item).getInt("rounds")==0,"Every loot firearm starts empty "+id);
                Object definition=gun(item);int capacity=(Integer)field(definition,"capacity");
                for(int bad:new int[]{-1,capacity+1,Integer.MAX_VALUE}) {
                    ItemStack malformed=editData(item,"rounds",bad);
                    check(gun(malformed)==null,"Impossible magazine rejected "+id+"/"+bad);
                }
                check(gun(editData(item,"serial",""))==null,"Missing serial rejected "+id);
                check(gun(editData(item,"arsenalMark","cosmetic"))==null,"Incorrect mark rejected "+id);
                ItemStack wrong=item.clone();wrong.setDurability((short)(item.getDurability()-1));
                check(gun(wrong)==null,"Wrong gun model band rejected "+id);
            }
            if(item.getItemMeta().isUnbreakable()) {
                String serial=data(item).getString("serial");UUID.fromString(serial);check(serials.add(serial),"Unique serial "+id);
                check(!serial.equals(data(ApocalypseItems.gear(id)).getString("serial")),"Fresh factory serial "+id);
                item.setAmount(2);check(gun?call(Arsenal.class,"identify",new Class<?>[]{ItemStack.class},item)==null:!ExpeditionEquipment.verified(item),"Stacked gear rejected "+id);
            }
        }
        for(String id:ApocalypseItems.catalogue().keySet()) {
            ItemStack request=creativeRequest(id),issued=CreativeCatalogue.issue(request);
            check(ApocalypseItems.id(request).isEmpty(),"Creative token is inert "+id);
            check(issued!=null&&id.equals(ApocalypseItems.id(issued)),"Creative token issues exact item "+id);
            check(data(issued).getInt("tier")==5,"Creative item has tier five provenance "+id);
        }
        check(CreativeCatalogue.issue(creativeRequest("unknown"))==null,"Unknown creative token rejected");
        CreativeCatalogue creative=(CreativeCatalogue)field(plugin,"creativeCatalogue");
        View creativeView=new View(actor.inventory,InventoryType.PLAYER);
        actor.mode=GameMode.CREATIVE;
        InventoryCreativeEvent creativeEvent=new InventoryCreativeEvent(creativeView,InventoryType.SlotType.CONTAINER,0,creativeRequest("rifle"));
        creative.onCreative(creativeEvent);
        check(!creativeEvent.isCancelled()&&"rifle".equals(ApocalypseItems.id(creativeEvent.getCursor())),"Authenticated Creative event issues exact item");
        actor.mode=GameMode.SURVIVAL;
        creativeEvent=new InventoryCreativeEvent(creativeView,InventoryType.SlotType.CONTAINER,0,creativeRequest("rifle"));creative.onCreative(creativeEvent);
        check(creativeEvent.isCancelled()&&creativeEvent.getCursor().getType()==Material.AIR,"Survival request rejected");
        actor.mode=GameMode.CREATIVE;login(false);
        creativeEvent=new InventoryCreativeEvent(creativeView,InventoryType.SlotType.CONTAINER,0,creativeRequest("rifle"));creative.onCreative(creativeEvent);
        check(creativeEvent.isCancelled()&&creativeEvent.getCursor().getType()==Material.AIR,"Unauthenticated Creative request rejected");login(true);
        creativeEvent=new InventoryCreativeEvent(creativeView,InventoryType.SlotType.CONTAINER,0,creativeRequest("unknown"));creative.onCreative(creativeEvent);
        check(creativeEvent.isCancelled()&&creativeEvent.getCursor().getType()==Material.AIR,"Unknown Creative request rejected");actor.mode=GameMode.SURVIVAL;
        check(ApocalypseItems.gear("weapon_core").getType()==Material.QUARTZ,"Weapon Core cannot become a vanilla Nether Star");
        String guide=String.join("\n",((org.bukkit.inventory.meta.BookMeta)ApocalypseItems.guide().getItemMeta()).getPages());
        check(guide.contains("reset world")&&!guide.contains("old builds remain"),"Reset-world guide current");
        metrics.put("catalogue",ApocalypseItems.catalogue());
    }
    private Recipe recipe(String suffix) {
        Iterator<Recipe> recipes=Bukkit.recipeIterator();
        while(recipes.hasNext()){Recipe recipe=recipes.next();if(recipe instanceof Keyed&&((Keyed)recipe).getKey().getKey().equals(suffix))return recipe;}
        throw new AssertionError("Missing recipe "+suffix);
    }
    private final class View extends InventoryView {
        final Inventory top; final InventoryType type;
        View(Inventory top,InventoryType type){this.top=top;this.type=type;}
        public Inventory getTopInventory(){return top;}public Inventory getBottomInventory(){return actor.inventory;}
        public HumanEntity getPlayer(){return actor.player;}public InventoryType getType(){return type;}
    }
    private CraftingInventory craftingInventory() {
        net.minecraft.server.v1_12_R1.Container container=new net.minecraft.server.v1_12_R1.Container(){
            public InventoryView getBukkitView(){return null;}
            public boolean canUse(net.minecraft.server.v1_12_R1.EntityHuman human){return true;}
            public void a(net.minecraft.server.v1_12_R1.IInventory inventory){}
        };
        net.minecraft.server.v1_12_R1.InventoryCraftResult result=new net.minecraft.server.v1_12_R1.InventoryCraftResult();
        net.minecraft.server.v1_12_R1.InventoryCrafting matrix=new net.minecraft.server.v1_12_R1.InventoryCrafting(container,3,3);
        matrix.resultInventory=result;
        return new org.bukkit.craftbukkit.v1_12_R1.inventory.CraftInventoryCrafting(matrix,result);
    }
    private void crafting() throws Exception {
        for(String id:new String[]{"ammo_power","trauma_kit","coolant_injector","alloy_plate"}){
            String[] ingredients=(String[])call(ExpeditionEquipment.class,"recipeIngredients",new Class<?>[]{String.class},id);ItemStack[] matrix=new ItemStack[9];
            for(int i=0;i<ingredients.length;i++)matrix[i]=ingredients[i].startsWith("@")?new ItemStack(Material.valueOf(ingredients[i].substring(1))):ApocalypseItems.gear(ingredients[i]);
            check((Boolean)call(ExpeditionEquipment.class,"validRecipe",new Class<?>[]{String.class,ItemStack[].class},id,matrix),"Exact crafting inputs "+id);
            CraftingInventory inventory=craftingInventory();
            inventory.setMatrix(matrix);View view=new View(inventory,InventoryType.WORKBENCH);Recipe recipe=recipe("expedition_"+id);
            CraftItemEvent event=new CraftItemEvent(recipe,view,InventoryType.SlotType.RESULT,0,ClickType.LEFT,InventoryAction.PICKUP_ALL);
            arsenal.craft(event);check(!event.isCancelled(),"Normal authorized craft "+id);
            check(inventory.getResult().equals(call(ExpeditionEquipment.class,"recipeOutput",new Class<?>[]{String.class},id)),"Craft output retains marker and yield "+id);
            for(ClickType click:new ClickType[]{ClickType.SHIFT_LEFT,ClickType.NUMBER_KEY,ClickType.DROP,ClickType.CREATIVE}){
                event=new CraftItemEvent(recipe,view,InventoryType.SlotType.RESULT,0,click,InventoryAction.MOVE_TO_OTHER_INVENTORY);
                arsenal.craft(event);check(event.isCancelled(),"No batch/creative crafting "+id+"/"+click);
            }
            login(false);event=new CraftItemEvent(recipe,view,InventoryType.SlotType.RESULT,0,ClickType.LEFT,InventoryAction.PICKUP_ALL);arsenal.craft(event);
            check(event.isCancelled(),"AuthMe craft guard "+id);login(true);
            matrix[0]=new ItemStack(matrix[0].getType());check(!(Boolean)call(ExpeditionEquipment.class,"validRecipe",new Class<?>[]{String.class,ItemStack[].class},id,matrix),"Unmarked recipe input rejected "+id);
        }
        List<String> craftableGuns=new ArrayList<String>(Arsenal.catalogue().keySet());craftableGuns.add("portal_gun");
        check(craftableGuns.size()==36,"All 35 firearms plus Portal Gun have recipes");
        for(String id:craftableGuns){
            Blueprints.Blueprint blueprint=Blueprints.of(id);check(blueprint!=null,"Blueprint exists "+id);
            ItemStack[] matrix=new ItemStack[9];int first=-1;
            for(int slot=0;slot<9;slot++){
                char symbol=blueprint.shape[slot/3].charAt(slot%3);if(symbol=='.')continue;
                matrix[slot]=new ItemStack(Blueprints.material(symbol),1,Blueprints.data(symbol));
                check(ApocalypseItems.id(matrix[slot]).isEmpty(),"Plain vanilla ingredient "+id+"/"+slot);
                if(first<0)first=slot;
            }
            check((Boolean)call(Arsenal.class,"validIngredients",new Class<?>[]{String.class,ItemStack[].class},id,matrix),"Exact vanilla recipe "+id);
            ItemStack[] mirrored=new ItemStack[9];for(int slot=0;slot<9;slot++)mirrored[slot]=matrix[(slot/3)*3+2-slot%3];
            check((Boolean)call(Arsenal.class,"validIngredients",new Class<?>[]{String.class,ItemStack[].class},id,mirrored),"Mirrored vanilla recipe "+id);
            Recipe gunRecipe=recipe("jaspr_"+id);check(id.equals(ApocalypseItems.id(gunRecipe.getResult())),"Registered recipe output "+id);
            matrix[first]=ApocalypseItems.mark(matrix[first],"tagged_substitute");
            check(!(Boolean)call(Arsenal.class,"validIngredients",new Class<?>[]{String.class,ItemStack[].class},id,matrix),"Tagged custom substitute rejected "+id);
        }
        for(String id:new String[]{"whisper","rifle","mono_katana","bulwark_helmet"}){
            ItemStack item=ApocalypseItems.gear(id);ItemStack original=item.clone();
            AnvilInventory anvil=new org.bukkit.craftbukkit.v1_12_R1.inventory.CraftInventoryAnvil(new Location(world,0,90,0),
                new net.minecraft.server.v1_12_R1.InventorySubcontainer("fixture",true,2),new net.minecraft.server.v1_12_R1.InventoryCraftResult(),null);anvil.setItem(0,item);
            PrepareAnvilEvent event=new PrepareAnvilEvent(new View(anvil,InventoryType.ANVIL),item.clone());
            arsenal.anvil(event);equipment.anvil(event);check(event.getResult()==null,"Anvil rejects gear "+id);
            check(original.equals(anvil.getItem(0)),"Anvil preserves input NBT "+id);
            CraftingInventory inv=craftingInventory();
            inv.setMatrix(new ItemStack[]{item,item,null,null,null,null,null,null,null}); inv.setResult(item.clone());
            PrepareItemCraftEvent repair=new PrepareItemCraftEvent(inv,new View(inv,InventoryType.WORKBENCH),true);
            arsenal.prepareCraft(repair);equipment.repair(repair);check(inv.getResult()==null,"Vanilla repair blocked "+id);
            check(original.equals(inv.getMatrix()[0]),"Repair preserves input NBT "+id);
        }
        FurnaceRecipe smelt=null;
        Iterator<Recipe> recipes=Bukkit.recipeIterator();
        while(recipes.hasNext()){
            Recipe candidate=recipes.next();
            if(candidate instanceof FurnaceRecipe
                && ((FurnaceRecipe)candidate).getInput().getType()==Material.ROTTEN_FLESH
                && ApocalypseItems.id(((FurnaceRecipe)candidate).getResult()).equals("sanitized_flesh")) smelt=(FurnaceRecipe)candidate;
        }
        check(smelt!=null,"Sanitized flesh smelts in a furnace");
    }
    private void equip(String set){String[] parts={"boots","leggings","chestplate","helmet"};for(int i=0;i<4;i++)actor.armor[i]=ApocalypseItems.gear(set+"_"+parts[i]);}
    private void refresh() throws Exception {call(equipment,"refresh",new Class<?>[]{Player.class},actor.player);}
    private void armor() throws Exception {
        Attr speed=actor.attributes.get(Attribute.GENERIC_MOVEMENT_SPEED);speed.base=.137;
        AttributeModifier other=new AttributeModifier(UUID.randomUUID(),"external",.019,AttributeModifier.Operation.ADD_NUMBER);speed.addModifier(other);
        double original=speed.getValue();
        for(String set:new String[]{"bulwark","ranger","spectre","hazmat"}){
            equip(set);refresh();check((Integer)call(ExpeditionEquipment.class,"fullSet",new Class<?>[]{Player.class},actor.player)>=0,"Fullset identity "+set);
            check(speed.base==.137&&speed.mods.contains(other),"Preserve base and external modifier "+set);
            int count=actor.modifierCount();refresh();check(actor.modifierCount()==count,"No duplicate buffs "+set);
            actor.armor[2]=null;refresh();check(actor.modifierCount()==1&&speed.getValue()==original,"Partial set strips only owned buffs "+set);
        }
        equip("bulwark");actor.armor[1]=ApocalypseItems.gear("ranger_leggings");refresh();check(actor.modifierCount()==1,"Mixed set grants nothing");
        equip("ranger");refresh();check(speed.getValue()>original,"Ranger grants speed");
        actor.mode=GameMode.CREATIVE;refresh();check(actor.modifierCount()==1,"Creative strips perks");
        actor.mode=GameMode.SURVIVAL;refresh();login(false);refresh();check(actor.modifierCount()==1,"AuthMe logout strips perks");login(true);
        actor.currentWorld=worldNamed("jaspr_backrooms");refresh();check(speed.getValue()>original,"Backrooms equipment enabled");
        check(!plugin.enabledWorld(actor.currentWorld),"Backrooms does not silently enable siege/ruins");
        actor.currentWorld=worldNamed("unrelated");refresh();check(actor.modifierCount()==1,"Unrelated world strips perks");actor.currentWorld=world;
        actor.dead=true;refresh();check(actor.modifierCount()==1,"Dead player cannot receive buffs");actor.dead=false;
        check(actor.potion.getDuration()==400&&actor.potion.getAmplifier()==2,"Existing potion untouched");
        actor.armor=new ItemStack[4];refresh();
    }
    private World worldNamed(String name){return (World)Proxy.newProxyInstance(getClassLoader(),new Class<?>[]{World.class},(p,m,a)->m.getName().equals("getName")?name:m.getName().equals("getEnvironment")?World.Environment.NORMAL:zero(m.getReturnType()));}
    private void combat() throws Exception {
        Zombie target=world.spawn(new Location(world,8,90,8),Zombie.class);target.setAI(false);
        try {
            Set<Double> damage=new HashSet<Double>();
            for(String id:ApocalypseItems.catalogue("melee").keySet()){
                actor.storage[0]=ApocalypseItems.gear(id);((Map<?,?>)field(equipment,"meleeReady")).clear();
                EntityDamageByEntityEvent event=new EntityDamageByEntityEvent(actor.player,target,EntityDamageEvent.DamageCause.ENTITY_ATTACK,7);
                equipment.melee(event);check(!event.isCancelled()&&event.getDamage()>7,"Melee has real damage "+id);damage.add(event.getDamage());
                EntityDamageByEntityEvent second=new EntityDamageByEntityEvent(actor.player,target,EntityDamageEvent.DamageCause.ENTITY_ATTACK,7);
                equipment.melee(second);check(second.isCancelled(),"Melee cooldown "+id);
            }
            check(damage.size()>=7,"Distinct melee damage profiles");
            actor.mode=GameMode.CREATIVE;
            EntityDamageByEntityEvent blocked=new EntityDamageByEntityEvent(actor.player,target,EntityDamageEvent.DamageCause.ENTITY_ATTACK,7);
            equipment.melee(blocked);check(blocked.isCancelled(),"No creative equipment damage");actor.mode=GameMode.SURVIVAL;
            cancelDamage=true;((Map<?,?>)field(equipment,"meleeReady")).clear();
            blocked=new EntityDamageByEntityEvent(actor.player,target,EntityDamageEvent.DamageCause.ENTITY_ATTACK,7);Bukkit.getPluginManager().callEvent(blocked);
            check(blocked.isCancelled(),"Damage protection can cancel equipment hit");cancelDamage=false;
        } finally {target.remove();}
        equip("hazmat");refresh();EntityDamageEvent fire=new EntityDamageEvent(actor.player,EntityDamageEvent.DamageCause.FIRE,10);equipment.hazards(fire);
        check(Math.abs(fire.getDamage()-6.5)<.0001,"Hazmat fire mitigation");actor.armor[0]=null;
        fire=new EntityDamageEvent(actor.player,EntityDamageEvent.DamageCause.FIRE,10);equipment.hazards(fire);check(fire.getDamage()==10,"No hazard buff for partial set");refresh();
        actor.health=10;actor.storage[0]=ApocalypseItems.gear("trauma_kit");cancelHealing=true;equipment.consume(interact());
        check(actor.health==10&&actor.storage[0]!=null,"Cancelled healing consumes nothing");cancelHealing=false;equipment.consume(interact());
        check(actor.health==18&&actor.storage[0]==null,"Trauma consumes exactly one, heals eight");
        actor.storage[0]=ApocalypseItems.gear("trauma_kit");equipment.consume(interact());check(actor.health==18&&actor.storage[0]!=null,"Supply cooldown prevents repeat healing");
        ((Map<?,?>)field(equipment,"supplyReady")).clear();actor.food=10;actor.storage[0]=ApocalypseItems.gear("field_ration");cancelFood=true;equipment.consume(interact());
        check(actor.food==10&&actor.storage[0]!=null,"Cancelled food consumes nothing");cancelFood=false;equipment.consume(interact());check(actor.food==16&&actor.storage[0]==null,"Ration functional");
        ((Map<?,?>)field(equipment,"supplyReady")).clear();actor.food=10;actor.saturation=0;actor.storage[0]=ApocalypseItems.gear("sanitized_flesh");equipment.consume(interact());
        check(actor.food==18&&actor.saturation==8&&actor.storage[0]==null,"Sanitized flesh restores 8 food and saturation");
        check(actor.player.getActivePotionEffects().size()==1,"Sanitized flesh never sickens");
        actor.storage[0]=ApocalypseItems.gear("sanitized_flesh");equipment.consume(interact());
        check(actor.food==20&&actor.storage[0]==null,"Staple food has no shared cooldown");
        actor.storage[0]=ApocalypseItems.gear("sanitized_flesh");equipment.consume(interact());
        check(actor.food==20&&actor.storage[0]!=null,"Full hunger keeps the meal");
        // Offhand parity: eating works from the off hand and consumes the offhand stack.
        ((Map<?,?>)field(equipment,"supplyReady")).clear();((Map<?,?>)field(equipment,"lastConsume")).clear();
        actor.food=10;actor.saturation=0;actor.storage[0]=null;actor.offhand=ApocalypseItems.gear("sanitized_flesh");
        equipment.consume(interact(EquipmentSlot.OFF_HAND));
        check(actor.food==18&&actor.saturation==8&&actor.offhand==null,"Offhand sanitized flesh restores 8 food and saturation");
        check(actor.player.getActivePotionEffects().size()==1,"Offhand sanitized flesh never sickens");
        actor.offhand=ApocalypseItems.gear("sanitized_flesh");equipment.consume(interact(EquipmentSlot.OFF_HAND));
        check(actor.food==20&&actor.offhand==null,"Offhand staple has no shared cooldown");
        actor.offhand=ApocalypseItems.gear("sanitized_flesh");equipment.consume(interact(EquipmentSlot.OFF_HAND));
        check(actor.food==20&&actor.offhand!=null,"Offhand full hunger keeps the meal");
        // Single-eat: one click with food in both hands eats once, main hand wins.
        ((Map<?,?>)field(equipment,"lastConsume")).clear();
        actor.food=10;actor.saturation=0;actor.storage[0]=ApocalypseItems.gear("sanitized_flesh");actor.offhand=ApocalypseItems.gear("sanitized_flesh");
        equipment.consume(interact(EquipmentSlot.HAND));equipment.consume(interact(EquipmentSlot.OFF_HAND));
        check(actor.food==18&&actor.storage[0]==null&&actor.offhand!=null,"Main hand wins single eat");
        // Offhand still works when main hand holds an unusable consumable.
        ((Map<?,?>)field(equipment,"supplyReady")).clear();((Map<?,?>)field(equipment,"lastConsume")).clear();
        actor.health=20;actor.food=10;actor.saturation=0;actor.storage[0]=ApocalypseItems.gear("trauma_kit");actor.offhand=ApocalypseItems.gear("sanitized_flesh");
        equipment.consume(interact(EquipmentSlot.OFF_HAND));
        check(actor.food==18&&actor.offhand==null&&actor.storage[0]!=null,"Offhand eats when main cannot be used");
        actor.storage[0]=null;actor.offhand=null;
        ((Map<?,?>)field(equipment,"supplyReady")).clear();((Map<?,?>)field(equipment,"lastConsume")).clear();actor.fire=100;actor.storage[0]=ApocalypseItems.gear("coolant_injector");login(false);equipment.consume(interact());
        check(actor.fire==100&&actor.storage[0]!=null,"AuthMe guards consumables");login(true);actor.mode=GameMode.CREATIVE;equipment.consume(interact());
        check(actor.fire==100&&actor.storage[0]!=null,"Creative cannot consume/farm supplies");actor.mode=GameMode.SURVIVAL;equipment.consume(interact());check(actor.fire==0&&actor.storage[0]==null,"Coolant functional");
        actor.armor=new ItemStack[4];refresh();
    }
    @EventHandler(priority=EventPriority.HIGHEST) public void healing(EntityRegainHealthEvent event){if(cancelHealing)event.setCancelled(true);}
    private void flesh() throws Exception {
        Object siege=field(plugin,"siege");
        for(String kind:new String[]{"walker","brute"}){
            Zombie corpse=(Zombie)call(siege,"spawn",new Class<?>[]{Location.class,String.class},new Location(world,.5,90,.5),kind);
            check(corpse!=null,"Flesh fixture spawn "+kind);
            try {
                // Day/night damage is synchronized (full values around the clock): walker 2, brute 4.
                double expected="brute".equals(kind)?4.0:2.0;
                check(corpse.getAttribute(Attribute.GENERIC_ATTACK_DAMAGE).getValue()==expected,"Synchronized damage "+kind);
                EntityDeathEvent death=new EntityDeathEvent(corpse,new ArrayList<ItemStack>());
                call(siege,"death",new Class<?>[]{EntityDeathEvent.class},death);
                check(death.getDrops().size()==1&&death.getDrops().get(0).getType()==Material.ROTTEN_FLESH
                    &&death.getDrops().get(0).getAmount()==1,"Custom "+kind+" always drops exactly one rotten flesh");
            } finally { corpse.remove(); }
        }
    }
    private void daylight() throws Exception {
        Class<?> directorType=Class.forName("chat.jaspr.apocalypse.SiegeDirector");
        Class<?> rulesType=Class.forName("chat.jaspr.apocalypse.SiegeRules");
        check((Boolean)call(directorType,"isDaytime",new Class<?>[]{Long.TYPE},0L)
            &&(Boolean)call(directorType,"isDaytime",new Class<?>[]{Long.TYPE},6000L)
            &&(Boolean)call(directorType,"isDaytime",new Class<?>[]{Long.TYPE},12499L),"Morning is day");
        check(!(Boolean)call(directorType,"isDaytime",new Class<?>[]{Long.TYPE},12500L)
            &&!(Boolean)call(directorType,"isDaytime",new Class<?>[]{Long.TYPE},18000L)
            &&!(Boolean)call(directorType,"isDaytime",new Class<?>[]{Long.TYPE},23500L),"Afternoon is night");
        check((Boolean)call(directorType,"isDaytime",new Class<?>[]{Long.TYPE},23501L),"Late edge is day");
        check((Integer)call(rulesType,"breakPasses",new Class<?>[]{Material.class},Material.STONE)==7
            &&(Integer)call(rulesType,"breakPasses",new Class<?>[]{Material.class},Material.LOG)==4,"Siege mining passes halved");
        check((Integer)call(rulesType,"breakPasses",new Class<?>[]{Material.class},Material.IRON_BLOCK)==12
            &&(Integer)call(rulesType,"breakPasses",new Class<?>[]{Material.class},Material.DIRT)==3,"Metal and dirt passes halved");
        check((Integer)call(rulesType,"breakPasses",new Class<?>[]{Material.class},Material.BEDROCK)==0
            &&(Integer)call(rulesType,"breakPasses",new Class<?>[]{Material.class},Material.CHEST)==0,"Protected blocks still excluded");
        Object siege=field(plugin,"siege");
        world.setTime(6000);call(siege,"daylightPass",new Class<?>[]{});
        Zombie day=(Zombie)call(siege,"spawn",new Class<?>[]{Location.class,String.class},new Location(world,.5,90,.5),"walker");
        check(day!=null,"Daylight fixture spawn");
        try {
            check(day.getAttribute(Attribute.GENERIC_MAX_HEALTH).getValue()==14.0,"Day spawn weakened to half health");
            check(day.getAttribute(Attribute.GENERIC_ATTACK_DAMAGE).getValue()==2.0,"Day/night damage synchronized");
            check(day.getHealth()==14.0,"Day spawn health clamped to weakened maximum");
            call(directorType,"setDayScaled",new Class<?>[]{LivingEntity.class,Boolean.TYPE},day,true);
            check(day.getAttribute(Attribute.GENERIC_MAX_HEALTH).getValue()==14.0,"Weakness never stacks");
            call(directorType,"setDayScaled",new Class<?>[]{LivingEntity.class,Boolean.TYPE},day,false);
            check(day.getAttribute(Attribute.GENERIC_MAX_HEALTH).getValue()==28.0,"Night restores full health");
            check(day.getAttribute(Attribute.GENERIC_ATTACK_DAMAGE).getValue()==2.0,"Night restores full damage");
            // Movement slow lives in JasprDaylight (absent from this fixture): Apocalypse alone must not touch speed.
            check(day.getAttribute(Attribute.GENERIC_MOVEMENT_SPEED).getValue()==0.23,"Day spawn leaves base speed alone");
            org.bukkit.entity.Cow cow=(org.bukkit.entity.Cow)world.spawnEntity(new Location(world,2.5,90,.5),org.bukkit.entity.EntityType.COW);
            try {
                EntityCombustEvent sun=new EntityCombustEvent(day,8);
                Bukkit.getPluginManager().callEvent(sun);check(sun.isCancelled(),"Daylight never burns hunters");
                EntityCombustEvent wild=new EntityCombustEvent(cow,8);
                Bukkit.getPluginManager().callEvent(wild);check(wild.isCancelled(),"Daylight never burns vanilla mobs either");
                EntityCombustByBlockEvent lava=new EntityCombustByBlockEvent(world.getBlockAt(2,89,0),day,8);
                Bukkit.getPluginManager().callEvent(lava);check(!lava.isCancelled(),"Fire blocks still burn");
            } finally { cow.remove(); }
            int messages=actor.messages.size();
            world.setTime(18000);call(siege,"daylightPass",new Class<?>[]{});
            java.util.Map<?,?> phases=(java.util.Map<?,?>)field(siege,"lastDayPhase");
            check(Boolean.FALSE.equals(phases.get(world.getUID())),"Night phase tracked");
            check(day.getAttribute(Attribute.GENERIC_MAX_HEALTH).getValue()==28.0,"Night pass restores hunter");
            world.setTime(6000);call(siege,"daylightPass",new Class<?>[]{});
            check(Boolean.TRUE.equals(phases.get(world.getUID())),"Day phase tracked");
            check(day.getAttribute(Attribute.GENERIC_MAX_HEALTH).getValue()==14.0,"Day pass weakens hunter");
            check(day.getAttribute(Attribute.GENERIC_MOVEMENT_SPEED).getValue()==0.23,"Day pass leaves speed to Daylight");
        } finally { day.remove(); world.setTime(6000); }
    }
    @EventHandler(priority=EventPriority.HIGHEST) public void food(FoodLevelChangeEvent event){if(cancelFood)event.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGHEST) public void damage(EntityDamageByEntityEvent event){if(cancelDamage)event.setCancelled(true);}
    private PlayerInteractEvent interact(){return interact(EquipmentSlot.HAND);}
    private PlayerInteractEvent interact(EquipmentSlot hand){
        ItemStack inHand = hand == EquipmentSlot.OFF_HAND ? actor.offhand : actor.storage[0];
        PlayerInteractEvent event=new PlayerInteractEvent(actor.player,Action.RIGHT_CLICK_AIR,inHand,null,BlockFace.SELF,hand);event.setUseItemInHand(Event.Result.ALLOW);return event;
    }
    private Block mount(SentryTurret sentry,int x,List<Block> bodies) throws Exception {
        Block ground=null;
        for(int y=100;y>0;y--){Block candidate=world.getBlockAt(x,y,40);if(candidate.getType().isOccluding()){ground=candidate;break;}}
        if(ground==null)return null;
        actor.storage[0]=ExpeditionEquipment.item("sentry_turret");
        PlayerInteractEvent event=new PlayerInteractEvent(actor.player,Action.RIGHT_CLICK_BLOCK,actor.storage[0],ground,BlockFace.UP,EquipmentSlot.HAND);
        sentry.interact(event);
        Block body=ground.getRelative(BlockFace.UP);
        if(body.getType()==Material.DISPENSER)bodies.add(body);
        return body;
    }
    private boolean packHoldsTurret() {
        for(ItemStack stack:actor.storage)if(SentryTurret.verified(stack))return true;
        return false;
    }
    private void sentry() throws Exception {
        SentryTurret sentry=(SentryTurret)field(plugin,"sentry");
        check(sentry!=null,"Sentry subsystem started");
        // Later delayed phases still need this phase's actor storage (pending reload).
        ItemStack[] savedStorage=actor.storage.clone();
        GameMode savedMode=actor.mode;
        actor.mode=GameMode.SURVIVAL;actor.dead=false;actor.sneaking=false;actor.currentWorld=world;login(true);
        ItemStack ingot=new ItemStack(Material.IRON_INGOT),block=new ItemStack(Material.IRON_BLOCK);
        ItemStack[] good=new ItemStack[9];
        good[1]=ingot;good[3]=ingot;good[4]=block;good[5]=ingot;good[7]=ingot;
        check((Boolean)call(sentry,"validMatrix",new Class<?>[]{ItemStack[].class},(Object)good),"Turret recipe shape");
        ItemStack[] tagged=good.clone();tagged[1]=ExpeditionEquipment.item("alloy_plate");
        check(!(Boolean)call(sentry,"validMatrix",new Class<?>[]{ItemStack[].class},(Object)tagged),"Turret rejects tagged substitutes");
        ItemStack[] sparse=good.clone();sparse[7]=null;
        check(!(Boolean)call(sentry,"validMatrix",new Class<?>[]{ItemStack[].class},(Object)sparse),"Turret rejects partial matrix");
        List<Block> bodies=new ArrayList<Block>();
        actor.mode=GameMode.CREATIVE;
        Recipe turretRecipe=recipe("sentry_turret");
        for(GameMode mode:new GameMode[]{GameMode.SURVIVAL,GameMode.CREATIVE}){
            actor.mode=mode;
            CraftingInventory turretInventory=craftingInventory();turretInventory.setMatrix(good.clone());
            View turretView=new View(turretInventory,InventoryType.WORKBENCH);
            check((Boolean)call(sentry,"craftable",new Class<?>[]{Player.class},actor.player),"Turret craft gate opens in "+mode);
            PrepareItemCraftEvent prepared=new PrepareItemCraftEvent(turretInventory,turretView,true){
                @Override public Recipe getRecipe(){return turretRecipe;}
            };sentry.prepareCraft(prepared);
            check(turretInventory.getResult()!=null&&SentryTurret.verified(turretInventory.getResult()),"Turret recipe prepares in "+mode);
            CraftItemEvent crafted=new CraftItemEvent(turretRecipe,turretView,InventoryType.SlotType.RESULT,0,ClickType.LEFT,InventoryAction.PICKUP_ALL);
            sentry.craft(crafted);
            check(!crafted.isCancelled()&&SentryTurret.verified(crafted.getCurrentItem()),"Turret recipe crafts in "+mode);
        }
        actor.mode=GameMode.CREATIVE;
        Block creativeBody=mount(sentry,36,bodies);
        check(creativeBody!=null&&creativeBody.getType()==Material.DISPENSER,"Creative mounting places body");
        check(actor.storage[0]!=null,"Creative mounting consumes nothing");
        actor.mode=GameMode.SURVIVAL;
        Block body=mount(sentry,38,bodies);
        check(body!=null&&body.getType()==Material.DISPENSER,"Survival mounting places body");
        check(actor.storage[0]==null,"Survival mounting consumes the single");
        call(sentry,"tick",new Class<?>[]{});
        Map<String,?> records=(Map<String,?>)field(sentry,"turrets");
        check(records.size()==2,"Two mounted turrets tracked");
        String bodyKey="world:"+body.getX()+","+body.getY()+","+body.getZ();
        Object record=records.get(bodyKey);
        check(record!=null,"Turret record keyed by position");
        UUID standId=(UUID)field(record,"stand");
        check(standId!=null&&Bukkit.getEntity(standId) instanceof ArmorStand,"Tracking head assembled");
        ArmorStand stand=(ArmorStand)Bukkit.getEntity(standId);
        check(SentryTurret.verified(stand.getHelmet()),"Head wears the turret model");
        check(stand.isSmall(),"Tracking head uses the compact mount");
        check(stand.getLocation().getBlockX()==body.getX()&&Math.abs(stand.getLocation().getY()-(body.getY()+1.1))<0.000001
            &&stand.getLocation().getBlockZ()==body.getZ(),"Six-times head base rests on the body");
        Location origin=new Location(world,0,64,0);
        for(double[] lane:new double[][]{{10,0},{-10,0},{0,10},{0,-10},{7,7},{-7,5}}){
            Location aim=new Location(world,lane[0],64,lane[1]);
            double yaw=(Double)call(SentryTurret.class,"yawTo",new Class<?>[]{Location.class,Location.class},origin,aim);
            Vector dir=new Location(world,0,64,0,(float)yaw,0).getDirection();
            Vector want=new Vector(lane[0],0,lane[1]).normalize();
            check(dir.distance(want)<0.001,"Aim yaw faces the target");
        }
        double yawLevel=(Double)call(SentryTurret.class,"yawTo",new Class<?>[]{Location.class,Location.class},origin,new Location(world,10,64,0));
        double yawHigh=(Double)call(SentryTurret.class,"yawTo",new Class<?>[]{Location.class,Location.class},origin,new Location(world,10,80,0));
        check(Math.abs(yawLevel-yawHigh)<1e-9,"Aim yaw ignores elevation");
        actor.storage[0]=new ItemStack(Material.STICK);
        PlayerInteractEvent gui=new PlayerInteractEvent(actor.player,Action.RIGHT_CLICK_BLOCK,actor.storage[0],body,BlockFace.UP,EquipmentSlot.HAND);
        sentry.interact(gui);
        check(gui.isCancelled(),"Body right-click opens settings, never the dispenser");
        actor.sneaking=true;
        PlayerInteractEvent collect=new PlayerInteractEvent(actor.player,Action.RIGHT_CLICK_BLOCK,actor.storage[0],body,BlockFace.UP,EquipmentSlot.HAND);
        sentry.interact(collect);
        actor.sneaking=false;
        check(body.getType()==Material.AIR,"Collected body removed");
        check(!records.containsKey(bodyKey),"Collected turret forgotten");
        check(Bukkit.getEntity(standId)==null||!Bukkit.getEntity(standId).isValid(),"Collected head removed");
        check(packHoldsTurret(),"Collected single returns to the pack");
        String legacyKey="world:"+creativeBody.getX()+","+creativeBody.getY()+","+creativeBody.getZ();
        Object legacyRecord=records.get(legacyKey);
        check(legacyRecord!=null,"Creative record present for migration");
        ArmorStand legacy=world.spawn(new Location(world,creativeBody.getX()+0.5,creativeBody.getY()+1.0,creativeBody.getZ()+0.5),ArmorStand.class);
        legacy.setSmall(true);
        legacy.addScoreboardTag("jaspr_sentry");
        legacy.setCustomName(legacyKey);
        legacy.setHelmet(new ItemStack(Material.DISPENSER));
        setField(legacyRecord,"stand",null);
        call(sentry,"tick",new Class<?>[]{});
        Object migrated=records.get(legacyKey);
        UUID rebuiltId=(UUID)field(migrated,"stand");
        check(rebuiltId!=null&&!rebuiltId.equals(legacy.getUniqueId()),"Legacy head rebuilt, not adopted");
        check(!legacy.isValid(),"Legacy head removed");
        ArmorStand rebuilt=(ArmorStand)Bukkit.getEntity(rebuiltId);
        check(rebuilt!=null&&rebuilt.isSmall()&&SentryTurret.verified(rebuilt.getHelmet()),"Rebuilt compact head with model");
        Block taken=world.getBlockAt(body.getX(),body.getY(),body.getZ());
        taken.setType(Material.STONE);
        actor.storage[0]=ExpeditionEquipment.item("sentry_turret");
        PlayerInteractEvent blocked=new PlayerInteractEvent(actor.player,Action.RIGHT_CLICK_BLOCK,actor.storage[0],taken.getRelative(BlockFace.DOWN),BlockFace.UP,EquipmentSlot.HAND);
        sentry.interact(blocked);
        check(blocked.isCancelled()&&taken.getType()==Material.STONE,"Occupied cell rejected");
        check(actor.storage[0]!=null,"Rejected mounting consumes nothing");
        taken.setType(Material.AIR);
        for(int x:new int[]{40,42,44,46,34})mount(sentry,x,bodies);
        check(records.size()==6,"Per-player cap holds six");
        actor.storage[0]=ExpeditionEquipment.item("sentry_turret");
        Block extra=world.getBlockAt(32,body.getY(),40);
        Block extraGround=world.getBlockAt(32,body.getY()-1,40);
        PlayerInteractEvent capped=new PlayerInteractEvent(actor.player,Action.RIGHT_CLICK_BLOCK,actor.storage[0],extraGround,BlockFace.UP,EquipmentSlot.HAND);
        sentry.interact(capped);
        check(extra.getType()!=Material.DISPENSER&&records.size()==6,"Seventh mounting rejected at cap");
        for(Block placed:bodies){
            for(Entity entity:placed.getWorld().getNearbyEntities(placed.getLocation().add(0.5,1,0.5),3,3,3))
                if(entity instanceof ArmorStand&&entity.getScoreboardTags().contains("jaspr_sentry"))entity.remove();
            if(placed.getType()==Material.DISPENSER)placed.setType(Material.AIR);
        }
        records.clear();
        check(records.isEmpty(),"Sentry fixture cleaned");
        actor.storage=savedStorage;actor.sneaking=false;actor.mode=savedMode;actor.currentWorld=world;
    }
    private void waypoints() throws Exception {
        Waypoints manager=(Waypoints)field(plugin,"waypoints");
        check(manager!=null,"Waypoints subsystem started");
        ItemStack[] savedStorage=actor.storage.clone();
        GameMode savedMode=actor.mode;boolean savedSneaking=actor.sneaking;World savedWorld=actor.currentWorld;boolean savedDead=actor.dead;
        try {
            actor.mode=GameMode.SURVIVAL;actor.dead=false;actor.sneaking=false;actor.currentWorld=world;login(true);
            UUID id=actor.player.getUniqueId();
            check("JW00b1njcl71njc54".equals(new Waypoint(0,"Home",123,64,-456,"world",11,false,0).coordHolder()),"V1 holder");
            check("JWe1515occg25ecn4".equals(new Waypoint(14,"Nether Hub",-30000000,-64,30000000,"world",5,true,0).coordHolder()),"V2 holder");
            check("JN0Home".equals(new Waypoint(0,"Home",0,0,0,"world",0,false,0).nameHolder()),"Name holder");
            long[] parsed=Waypoint.parseCoordHolder("JW00b1njcl71njc54");
            check(parsed!=null&&parsed[0]==0&&parsed[1]==0&&parsed[2]==11&&parsed[3]==123&&parsed[4]==-456,"V1 parse");
            check(Waypoint.parseCoordHolder("JW00b1njcl7") == null
                && Waypoint.parseCoordHolder("XX00b1njcl71njc54") == null
                && Waypoint.parseCoordHolder(null) == null,"Malformed holders fail closed");
            check("A B".equals(Waypoint.sanitizeName("  A  B  "))
                && "Waypoint".equals(Waypoint.sanitizeName("   "))
                && Waypoint.sanitizeName("0123456789012345678901234567890123456789").length()==36,"Name sanitize");
            Waypoint round=new Waypoint(14,"Nether Hub",-30000000,-64,30000000,"world_nether",5,true,12345);
            Waypoint back=Waypoint.deserialize((Map<?,?>)round.serialize());
            check(back!=null&&back.slot==14&&back.name.equals("Nether Hub")&&back.x==-30000000&&back.y==-64
                &&back.z==30000000&&back.world.equals("world_nether")&&back.color==5&&back.death
                &&back.createdAt==12345,"Store round trip");
            for(int i=0;i<12;i++) {
                Waypoint created=manager.create(id,"P"+i,new Location(world,i*10,64,0),i);
                check(created!=null&&created.slot==i,"Manual slot fill "+i);
            }
            check(manager.create(id,"Overflow",new Location(world,999,64,0),0)==null,"Manual cap holds twelve");
            check(manager.visible(id).size()==12,"Twelve stored");
            check(manager.setActive(id,3)&&manager.activeSlot(id)==3,"Track waypoint");
            check(!manager.setActive(id,99)&&manager.activeSlot(id)==3,"Track rejects unknown slot");
            check(manager.recolor(id,3),"Recolor");
            check(manager.bySlot(id,3).color==4,"Color cycles");
            check(manager.delete(id,3)&&manager.bySlot(id,3)==null,"Delete frees slot");
            Waypoint refill=manager.create(id,"Refill",new Location(world,5,64,0),0);
            check(refill!=null&&refill.slot==3,"Freed slot reused");
            for(int i=0;i<4;i++)manager.recordDeath(actor.player);
            int deaths=0;for(Waypoint waypoint:manager.visible(id))if(waypoint.death)deaths++;
            check(deaths==3,"Deathpoints capped at three");
            check(manager.activeSlot(id)!=null&&manager.bySlot(id,manager.activeSlot(id)).death,"Newest death auto-tracked");
            check(manager.delete(id,5),"Free a slot for naming");
            manager.beginCreate(actor.player);
            manager.completeNaming(actor.player,"  Base Camp  ");
            Waypoint named=null;for(Waypoint waypoint:manager.visible(id))if(waypoint.name.equals("Base Camp"))named=waypoint;
            check(named!=null,"Chat naming creates waypoint");
            manager.beginCreate(actor.player);
            manager.completeNaming(actor.player,"cancel");
            check(manager.visible(id).size()==15,"Cancelled naming creates nothing");
            manager.beginCreate(actor.player);
            java.util.Set<Player> recipients=new java.util.HashSet<Player>();
            AsyncPlayerChatEvent chat=new AsyncPlayerChatEvent(true,actor.player,"hello",recipients);
            manager.naming(chat);
            check(chat.isCancelled(),"Naming chat never leaks");
            manager.completeNaming(actor.player,"cancel");
            WaypointMenu.Menu menu=new WaypointMenu.Menu(manager,actor.player);
            check(menu.getInventory().getSize()==54,"Menu is a 54-slot chest");
            check(menu.getInventory().getItem(45)!=null&&menu.getInventory().getItem(45).getType()==Material.EMERALD,"Create button placed");
            check(menu.getInventory().getItem(53)!=null&&menu.getInventory().getItem(53).getType()==Material.BARRIER,"Close button placed");
            check(menu.getInventory().getItem(49)!=null&&menu.getInventory().getItem(49).getType()==Material.COMPASS,"Nearest button placed");
            int icons=0;for(int i=0;i<45;i++)if(menu.getInventory().getItem(i)!=null)icons++;
            check(icons==15,"Menu lists every waypoint");
            check(menu.getInventory().getItem(0).getItemMeta().getDisplayName().contains("P0"),"Menu shows names");
            WaypointMenu.click(manager,menu,actor.player,0,ClickType.LEFT);
            check(manager.activeSlot(id)==0,"Menu left-click tracks");
            int beforeColor=manager.bySlot(id,0).color;
            WaypointMenu.click(manager,menu,actor.player,0,ClickType.RIGHT);
            check(manager.bySlot(id,0).color==((beforeColor+1)&15),"Menu right-click recolors");
            WaypointMenu.click(manager,menu,actor.player,1,ClickType.SHIFT_LEFT);
            check(manager.bySlot(id,1)!=null,"First shift-click only arms delete");
            WaypointMenu.click(manager,menu,actor.player,1,ClickType.SHIFT_LEFT);
            check(manager.bySlot(id,1)==null,"Second shift-click deletes");
            WaypointMenu.click(manager,menu,actor.player,40,ClickType.LEFT);
            check(manager.metrics().startsWith("waypoints="),"Metrics report");
            org.bukkit.scoreboard.Scoreboard board=manager.buildBoard(actor.player);
            check(board.getObjective("jwp")!=null&&board.getObjective("jwp").getDisplayName().startsWith("JWP v1 n="),"Hidden objective synced");
            java.util.Set<String> entries=board.getEntries();
            check(entries.size()==2*manager.visible(id).size(),"Every waypoint syncs two scores");
            for(Waypoint waypoint:manager.visible(id))
                check(entries.contains(waypoint.coordHolder())&&entries.contains(waypoint.nameHolder()),"Holder synced slot "+waypoint.slot);
            check(!entries.contains("JW00b1njcl71njc54")&&!entries.contains("JNeNether Hub"),"Unrelated holders absent");
            Waypoint first=manager.visible(id).get(0);
            check(board.getObjective("jwp").getScore(first.coordHolder()).getScore()==first.y,"Coord score carries y");
            check(manager.command(actor.player,new String[]{"add","Cmd"})&&manager.bySlot(id,manager.visible(id).size()>0?manager.visible(id).get(0).slot:-1)!=null,"Command add works");
            manager.command(actor.player,new String[]{"track","Cmd"});
            Waypoint tracked=null;for(Waypoint waypoint:manager.visible(id))if(waypoint.name.equals("Cmd"))tracked=waypoint;
            check(tracked!=null&&manager.activeSlot(id)==tracked.slot,"Command track works");
            manager.command(actor.player,new String[]{"delete","Cmd"});
            check(!manager.visible(id).stream().anyMatch(w->w.name.equals("Cmd")),"Command delete works");
            manager.command(actor.player,new String[]{"list"});
            manager.command(actor.player,new String[]{"bogus"});
            check(Waypoints.compassLine("Home",11,0,1,0,1,1234,0).contains("\u2191"),"Compass ahead");
            check(Waypoints.compassLine("Home",11,0,1,1,0,10,0).contains("\u2190"),"Compass left");
            check(Waypoints.compassLine("Home",11,0,1,0,-1,10,0).contains("\u2193"),"Compass behind");
            check(Waypoints.compassLine("Home",11,0,1,-1,0,10,0).contains("\u2192"),"Compass right");
            check(Waypoints.compassLine("Home",11,0,1,0,0,0,0).contains("\u25cf"),"Compass on top");
            check(Waypoints.compassLine("Home",11,0,1,0,1,10,20).contains("\u25b2"),"Compass above tag");
            check(Waypoints.compassLine("Home",11,0,1,0,1,10,-20).contains("\u25bc"),"Compass below tag");
            check(Waypoints.compassLine("Home",11,0,1,0,1,1234,0).contains("1,234m"),"Compass distance format");
            call(manager,"save",new Class<?>[]{});
            org.bukkit.configuration.file.YamlConfiguration disk=
                org.bukkit.configuration.file.YamlConfiguration.loadConfiguration((java.io.File)field(manager,"file"));
            java.util.List<java.util.Map<?, ?>> rows=disk.getMapList("players."+id.toString()+".waypoints");
            check(rows.size()==manager.visible(id).size(),"Persistence writes every row");
            check("P0".equals(rows.get(0).get("name")),"Persisted row keeps fields");
            call(manager,"load",new Class<?>[]{});
            check(manager.visible(id).size()==rows.size(),"Persistence reloads every row");
            check(manager.bySlot(id,0)!=null&&manager.bySlot(id,0).name.equals("P0"),"Reloaded row matches");
            for(Waypoint waypoint:new ArrayList<Waypoint>(manager.visible(id)))manager.delete(id,waypoint.slot);
            check(manager.visible(id).isEmpty(),"Waypoint fixture cleaned");
        } finally {
            for(Waypoint waypoint:new ArrayList<Waypoint>(manager.visible(actor.player.getUniqueId())))manager.delete(actor.player.getUniqueId(),waypoint.slot);
            actor.storage=savedStorage;actor.sneaking=false;actor.mode=savedMode;actor.currentWorld=savedWorld;actor.dead=savedDead;
        }
    }
    private Object gun(ItemStack item) throws Exception{return call(Arsenal.class,"identify",new Class<?>[]{ItemStack.class},item);}
    private ItemStack loaded(String id,int count) throws Exception {ItemStack item=ApocalypseItems.gear(id);Object gun=gun(item);return (ItemStack)call(Arsenal.class,"rounds",new Class<?>[]{ItemStack.class,gun.getClass(),int.class},item,gun,count);}
    private void ready() throws Exception{((Map<?,?>)field(arsenal,"readyAt")).clear();}
    private void guns() throws Exception {
        Set<String> profiles=new HashSet<String>();
        for(String id:ApocalypseItems.catalogue("gun").keySet()){
            actor.storage[0]=loaded(id,1);Object gun=gun(actor.storage[0]);
            profiles.add(field(gun,"damage")+"/"+field(gun,"range")+"/"+field(gun,"cooldownMs"));
            check(data(roundTrip(actor.storage[0])).getInt("rounds")==1,"Loaded magazine persistence "+id);
            login(false);ready();arsenal.interact(interact());check(data(actor.storage[0]).getInt("rounds")==1,"Gun auth guard "+id);login(true);
            PlayerInteractEvent denied=interact();denied.setUseItemInHand(Event.Result.DENY);arsenal.interact(denied);check(data(actor.storage[0]).getInt("rounds")==1,"Item-use protection "+id);
            arsenal.interact(interact());check(data(actor.storage[0]).getInt("rounds")==0,"Actual trigger consumes round "+id);
        }
        check(profiles.size()==35,"All guns have different damage/range/cooldown");
        Object scout=gun(ApocalypseItems.gear("longwatch"));
        double near=(Double)call(Arsenal.class,"shotDamage",new Class<?>[]{scout.getClass(),double.class,int.class,boolean.class},scout,20d,0,true);
        double far=(Double)call(Arsenal.class,"shotDamage",new Class<?>[]{scout.getClass(),double.class,int.class,boolean.class},scout,40d,0,true);
        check(Math.abs(far-near*1.35)<.0001,"Scout range mechanic");
        Object rail=gun(ApocalypseItems.gear("railgun"));
        check(Math.abs((Double)call(Arsenal.class,"shotDamage",new Class<?>[]{rail.getClass(),double.class,int.class,boolean.class},rail,20d,2,true)-64)<.0001,"Old rail penetration preserved");
        Method intersect=Arsenal.class.getDeclaredMethod("intersection",Vector.class,Vector.class,AxisAlignedBB.class,double.class);intersect.setAccessible(true);
        AxisAlignedBB box=new AxisAlignedBB(1,1,1,2,2,2);
        check((Double)intersect.invoke(null,new Vector(1.5,1.5,1.5),new Vector(0,0,1),box,10d)==0,"Ray starts inside target");
        check(Double.isInfinite((Double)intersect.invoke(null,new Vector(0,0,0),new Vector(0,0,1),box,10d)),"Axis-parallel ray miss");
        Class<?> cacheType=Class.forName("chat.jaspr.apocalypse.Arsenal$CollisionCache");Constructor<?> constructor=cacheType.getDeclaredConstructor(World.class);constructor.setAccessible(true);
        Method wall=Arsenal.class.getDeclaredMethod("wallDistance",cacheType,Vector.class,Vector.class,double.class);wall.setAccessible(true);
        for(Material material:new Material[]{Material.STONE,Material.THIN_GLASS,Material.FENCE,Material.STEP,Material.COBBLESTONE_STAIRS}){
            world.getBlockAt(0,90,4).setType(material);
            double distance=(Double)wall.invoke(null,constructor.newInstance(world),new Vector(.5,90.25,.5),new Vector(0,0,1),12d);
            check(distance<4.5,"Native partial collision stops ray "+material);world.getBlockAt(0,90,4).setType(Material.AIR);
        }
        int chunks=world.getLoadedChunks().length;
        double boundary=(Double)wall.invoke(null,constructor.newInstance(world),new Vector(.5,90,.5),new Vector(1,0,0),112d);
        check(boundary<112&&world.getLoadedChunks().length==chunks,"Unloaded boundary never loads chunks");
        metrics.put("rayAndGunCoverage","Real native boxes, old rail falloff, all gun triggers and serialized magazines; player interface fixtures");
    }
    private ItemStack editData(ItemStack item,String key,Object value) {
        net.minecraft.server.v1_12_R1.ItemStack nms=CraftItemStack.asNMSCopy(item);
        NBTTagCompound root=nms.getTag(),d=root.getCompound("JasprApocalypse");
        if(value instanceof Integer)d.setInt(key,(Integer)value);else d.setString(key,(String)value);
        root.set("JasprApocalypse",d);nms.setTag(root);return CraftItemStack.asBukkitCopy(nms);
    }
    private ItemStack creativeRequest(String id) {
        net.minecraft.server.v1_12_R1.ItemStack nms=CraftItemStack.asNMSCopy(new ItemStack(Material.STICK));
        NBTTagCompound root=nms.hasTag()?nms.getTag():new NBTTagCompound(),request=new NBTTagCompound();
        request.setString("id",id);root.set("JasprCreative",request);nms.setTag(root);
        return CraftItemStack.asBukkitCopy(nms);
    }
    private void hitLanes() throws Exception {
        // Real ray/collision/aggregation code, scripted hit recipient. Never fake a native CraftPlayer.
        Class<?> targetType=Class.forName("chat.jaspr.apocalypse.Arsenal$Target");
        Constructor<?> ctor=targetType.getDeclaredConstructor(LivingEntity.class,AxisAlignedBB.class);ctor.setAccessible(true);
        final double[] received={0,0};
        LivingEntity target=(LivingEntity)Proxy.newProxyInstance(getClassLoader(),new Class<?>[]{LivingEntity.class},(p,m,a)->{
            switch(m.getName()) {
                case "isValid":return true;case "isDead":return false;case "hashCode":return 902;
                case "equals":return p==a[0];case "getType":return EntityType.ZOMBIE;
                case "damage":received[0]++;received[1]+=(Double)a[0];check(a.length==2&&a[1]==actor.player,"Hit retains cancellable Bukkit damage source");return null;
                case "setHealth":case "setNoDamageTicks":throw new AssertionError("Bypassed native damage");
                default:return zero(m.getReturnType());
            }
        });
        List<Object> targets=Collections.singletonList(ctor.newInstance(target,new AxisAlignedBB(-4,90,5,5,94,6)));
        Location eye=actor.player.getEyeLocation();Vector direction=new Vector(0,0,1);
        for(String id:ApocalypseItems.catalogue("gun").keySet()) {
            Object gun=gun(ApocalypseItems.gear(id));received[0]=received[1]=0;
            call(arsenal,"fire",new Class<?>[]{Player.class,Location.class,Vector.class,gun.getClass(),List.class},actor.player,eye,direction,gun,targets);
            check(received[0]==1&&received[1]>0&&received[1]<=156,"One aggregated native damage call for volley "+id);
            for(int x=-2;x<=2;x++)for(int y=90;y<=93;y++)world.getBlockAt(x,y,4).setType(Material.STONE);
            received[0]=received[1]=0;
            try {call(arsenal,"fire",new Class<?>[]{Player.class,Location.class,Vector.class,gun.getClass(),List.class},actor.player,eye,direction,gun,targets);
                check(received[0]==0,"Solid wall stops all body-piercing and occult rays "+id);
            } finally {for(int x=-2;x<=2;x++)for(int y=90;y<=93;y++)world.getBlockAt(x,y,4).setType(Material.AIR);}
            double base=(Double)field(gun,"damage");int penetration=(Integer)field(gun,"penetration");
            double previous=Double.POSITIVE_INFINITY;
            for(int body=0;body<penetration;body++) {
                double value=(Double)call(Arsenal.class,"shotDamage",new Class<?>[]{gun.getClass(),double.class,int.class,boolean.class},gun,40d,body,true);
                check(Double.isFinite(value)&&value>0&&value<=base*1.35&&value<=previous,"Bounded penetration damage "+id+"/"+body);previous=value;
            }
        }
    }
    private void reloadMatrix() throws Exception {
        for(String id:ApocalypseItems.catalogue("gun").keySet()) {
            actor.storage=new ItemStack[36];actor.storage[0]=loaded(id,0);actor.sneaking=true;
            actor.storage[1]=ApocalypseItems.scrap(64);ready();arsenal.interact(interact());
            check(((Map<?,?>)field(arsenal,"reloading")).isEmpty(),"Marked salvage cannot reload "+id);
            actor.storage[1]=new ItemStack(Material.IRON_NUGGET,64);ready();arsenal.interact(interact());
            check(((Map<?,?>)field(arsenal,"reloading")).size()==1,"Reload scheduled for every profile "+id);
            check(actor.storage[1].getAmount()==64,"Reload costs nothing before completion "+id);
            arsenal.held(new PlayerItemHeldEvent(actor.player,0,1));
            check(((Map<?,?>)field(arsenal,"reloading")).isEmpty()&&data(actor.storage[0]).getInt("rounds")==0&&actor.storage[1].getAmount()==64,"Cancel preserves ammo and magazine "+id);
        }
        actor.sneaking=false;actor.storage=new ItemStack[36];
    }
    private void expansionReloadStart() throws Exception {
        actor.storage=new ItemStack[36];actor.storage[0]=loaded("ironpsalm",0);actor.storage[1]=new ItemStack(Material.IRON_NUGGET,10);actor.sneaking=true;ready();
        arsenal.interact(interact());actor.sneaking=false;
        check(((Map<?,?>)field(arsenal,"reloading")).size()==1,"Longest reload scheduled");
    }
    private void expansionReloadDone() throws Exception {
        check(data(actor.storage[0]).getInt("rounds")==3&&actor.storage[1].getAmount()==1,"Fractional heavy ammo remainder preserved: 10 / 3 => 3 rounds, 1 spare");
        check(((Map<?,?>)field(arsenal,"reloading")).isEmpty(),"Longest reload released");
    }
    private void startBurst(String id) throws Exception {
        actor.storage[0]=loaded(id,3);ready();arsenal.interact(interact());
        check(data(actor.storage[0]).getInt("rounds")==2,"First new burst shot immediate "+id);
    }
    private void endBurst(String id) throws Exception {
        check(data(actor.storage[0]).getInt("rounds")==0&&((Map<?,?>)field(arsenal,"bursting")).isEmpty(),"New burst completes exactly three rounds "+id);
        actor.storage[0]=loaded(id,3);ready();arsenal.interact(interact());arsenal.world(new PlayerChangedWorldEvent(actor.player,world));
        check(data(actor.storage[0]).getInt("rounds")==2&&((Map<?,?>)field(arsenal,"bursting")).isEmpty(),"New burst travel cancellation retains pending rounds "+id);
    }
    private String reloadSerial;
    private void reloadStart() throws Exception {
        actor.storage[0]=loaded("rifle",0);actor.storage[1]=new ItemStack(Material.IRON_NUGGET,30);actor.sneaking=true;ready();
        arsenal.interact(interact());check(((Map<?,?>)field(arsenal,"reloading")).size()==1,"Reload scheduled");
        arsenal.held(new PlayerItemHeldEvent(actor.player,0,1));check(((Map<?,?>)field(arsenal,"reloading")).isEmpty(),"Slot switch cancels reload");
        check(actor.storage[1].getAmount()==30,"Cancelled reload costs no ammo");
        ready();arsenal.interact(interact());reloadSerial=data(actor.storage[0]).getString("serial");actor.sneaking=false;
    }
    private void reloadDone() throws Exception {
        check(data(actor.storage[0]).getInt("rounds")==18&&actor.storage[1].getAmount()==12,"Timed reload charges exact marked ammo at completion");
        check(reloadSerial.equals(data(actor.storage[0]).getString("serial")),"Reload keeps serial");
        check(((Map<?,?>)field(arsenal,"reloading")).isEmpty(),"Reload task released");
    }
    private void burstStart() throws Exception {actor.storage[0]=loaded("tempest",3);ready();arsenal.interact(interact());check(data(actor.storage[0]).getInt("rounds")==2,"First burst shot immediate");}
    private void burstDone() throws Exception {
        check(data(actor.storage[0]).getInt("rounds")==0,"Two scheduled burst shots consumed");
        check(((Map<?,?>)field(arsenal,"bursting")).isEmpty(),"Burst task map released");
        actor.storage[0]=loaded("tempest",3);ready();arsenal.interact(interact());arsenal.world(new PlayerChangedWorldEvent(actor.player,world));
        check(((Map<?,?>)field(arsenal,"bursting")).isEmpty()&&data(actor.storage[0]).getInt("rounds")==2,"Travel cancels burst without charging pending shots");
    }
    private void lifecycle() throws Exception {
        equip("bulwark");refresh();equipment.quit(new PlayerQuitEvent(actor.player,"fixture"));check(actor.modifierCount()==1,"Quit strips only owned modifiers");
        arsenal.stop();check(((Map<?,?>)field(arsenal,"bursting")).isEmpty()&&((Map<?,?>)field(arsenal,"reloading")).isEmpty(),"Stop cancels gun tasks");
        Iterator<Recipe> recipes=Bukkit.recipeIterator();while(recipes.hasNext()){Recipe recipe=recipes.next();if(recipe instanceof Keyed)check(!((Keyed)recipe).getKey().getKey().startsWith("expedition_"),"Owned recipes removed");}
        arsenal.start();check(recipe("expedition_ammo_power")!=null,"Recipes restart cleanly");
        equipment.stop();equipment.start();check(field(equipment,"task")!=null,"Equipment restarts cleanly");
    }
    private void finish(){
        if(auth!=null&&actor!=null)auth.removePlayer(actor.name);
        metrics.put("scope","Isolated Paper 1.12 JVM; native NBT/collision/recipes/scheduler; scripted Player and Attribute interfaces, not client animation/network tests");
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
