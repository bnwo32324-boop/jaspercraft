package chat.jaspr.dungeon;

import java.io.File;
import java.util.*;
import java.util.function.Supplier;
import com.google.gson.GsonBuilder;
import net.minecraft.server.v1_12_R1.NBTTagCompound;
import org.bukkit.*;
import org.bukkit.craftbukkit.v1_12_R1.inventory.CraftItemStack;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.inventory.*;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.ItemMeta;

/** Native Creative templates are only requests. The server issues a fresh canonical item. */
public final class CreativeCatalog implements Listener {
    public static final String KEY="JasprDungeonCreative";
    public static final class Entry {
        public final String id,category;private final Supplier<ItemStack> factory;
        Entry(String id,String category,Supplier<ItemStack> factory){this.id=id;this.category=category;this.factory=factory;}
        public ItemStack create(){return factory.get();}
    }
    private final LinkedHashMap<String,Entry> entries=new LinkedHashMap<>();
    private final DungeonPlugin plugin;
    public CreativeCatalog(DungeonPlugin plugin){this.plugin=plugin;
        add("pouch","gear",Relics::createPouch);
        for(Relics.Type type:Relics.Type.values())add("bauble_"+type.name().toLowerCase(Locale.ROOT),"gear",()->Relics.create(type));
        for(int theme=0;theme<LootCatalog.PROFILES.size();theme++){
            final int chapter=theme;final LootCatalog.Profile profile=LootCatalog.profile(theme);
            add("lore_"+theme,"artifact",()->Rewards.lore(chapter));
            for(LootCatalog.Gear family:profile.gear)for(boolean fine:new boolean[]{false,true}){
                final boolean quality=fine;
                add("gear_"+theme+"_"+family.name().toLowerCase(Locale.ROOT)+(quality?"_exalted":"_pilgrim"),"gear",()->Rewards.gear(profile,family,quality?5:2,quality,quality));
            }
        }
        for(ArmoryCatalog.Type type:ArmoryCatalog.Type.values())add("armory_"+type.name().toLowerCase(Locale.ROOT),type.kind==ArmoryCatalog.Kind.GUN?"gun":"melee",()->Arsenal.create(type));
    }
    private void add(String id,String category,Supplier<ItemStack> factory){id="penitent_"+id;if(entries.containsKey(id))throw new IllegalStateException("Duplicate dungeon creative id "+id);entries.put(id,new Entry(id,category,factory));}
    public List<Entry> entries(){return Collections.unmodifiableList(new ArrayList<>(entries.values()));}
    public ItemStack create(String id){Entry e=entries.get(id);return e==null?null:e.create();}
    public static String request(ItemStack item){if(item==null||item.getType()==Material.AIR)return null;net.minecraft.server.v1_12_R1.ItemStack n=CraftItemStack.asNMSCopy(item);if(!n.hasTag()||!n.getTag().hasKeyOfType(KEY,10))return null;return n.getTag().getCompound(KEY).getString("id");}
    public static ItemStack template(Entry e){net.minecraft.server.v1_12_R1.ItemStack n=CraftItemStack.asNMSCopy(e.create());NBTTagCompound tag=n.hasTag()?n.getTag():new NBTTagCompound(),req=new NBTTagCompound();req.setString("id",e.id);tag.set(KEY,req);n.setTag(tag);return CraftItemStack.asBukkitCopy(n);}
    public void export(File target)throws Exception{
        List<Map<String,Object>> out=new ArrayList<>();
        for(Entry e:entries.values()){
            ItemStack item=template(e);net.minecraft.server.v1_12_R1.ItemStack n=CraftItemStack.asNMSCopy(item);NBTTagCompound saved=n.save(new NBTTagCompound());
            String title=item.hasItemMeta()&&item.getItemMeta().hasDisplayName()?ChatColor.stripColor(item.getItemMeta().getDisplayName()):item.getType().name();
            Map<String,Object> row=new LinkedHashMap<>();row.put("id",e.id);row.put("title",title);row.put("category",e.category);row.put("material",saved.getString("id"));row.put("model",(int)item.getDurability());row.put("color","\u00a7d");row.put("snbt",saved.toString());
            row.put("search",("dungeon dimension exclusive "+e.id.replace('_',' ')+" "+title+" "+e.category+" "+(item.hasItemMeta()&&item.getItemMeta().hasLore()?String.join(" ",item.getItemMeta().getLore()):"")).toLowerCase(Locale.ROOT));out.add(row);
        }
        RoomStore.atomic(target,new GsonBuilder().disableHtmlEscaping().setPrettyPrinting().create().toJson(out));
    }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void pickup(InventoryCreativeEvent e){String id=request(e.getCursor());if(id==null)return;ItemStack canonical=e.getWhoClicked().getGameMode()==GameMode.CREATIVE?create(id):null;if(canonical==null){e.setCancelled(true);return;}e.setCursor(canonical);}
    private static final class Menu implements InventoryHolder {final UUID owner;final int page;final Inventory inventory;Menu(Player p,int page){owner=p.getUniqueId();this.page=page;inventory=Bukkit.createInventory(this,54,"Dungeon items - "+(page+1));}public Inventory getInventory(){return inventory;}}
    public boolean open(Player p,int requested){if(p.getGameMode()!=GameMode.CREATIVE){p.sendMessage(ChatColor.RED+"The item catalogue requires Creative mode. Find these items in Dungeon Dimension chests in Survival.");return false;}int page=Math.max(0,Math.min((entries.size()-1)/45,requested));Menu m=new Menu(p,page);List<Entry> list=entries();for(int slot=0;slot<45&&page*45+slot<list.size();slot++)m.inventory.setItem(slot,list.get(page*45+slot).create());m.inventory.setItem(45,button("Previous page"));m.inventory.setItem(49,button("Creative catalogue: "+entries.size()+" items"));m.inventory.setItem(53,button("Next page"));p.openInventory(m.inventory);return true;}
    private ItemStack button(String title){ItemStack i=new ItemStack(Material.PAPER);ItemMeta m=i.getItemMeta();m.setDisplayName(ChatColor.GOLD+title);i.setItemMeta(m);return i;}
    @EventHandler(priority=EventPriority.HIGHEST) public void click(InventoryClickEvent e){InventoryHolder holder=e.getView().getTopInventory().getHolder();if(!(holder instanceof Menu))return;e.setCancelled(true);Menu m=(Menu)holder;Player p=(Player)e.getWhoClicked();if(!m.owner.equals(p.getUniqueId())||p.getGameMode()!=GameMode.CREATIVE){p.closeInventory();return;}int slot=e.getRawSlot();if(slot==45||slot==53){Bukkit.getScheduler().runTask(plugin,()->open(p,m.page+(slot==45?-1:1)));return;}int index=m.page*45+slot;if(slot<0||slot>=45||index>=entries.size())return;if(p.getInventory().firstEmpty()<0){p.sendMessage("Make an inventory space first.");return;}p.getInventory().addItem(entries().get(index).create());}
    @EventHandler(priority=EventPriority.HIGHEST) public void drag(InventoryDragEvent e){if(e.getView().getTopInventory().getHolder() instanceof Menu)e.setCancelled(true);}
}
