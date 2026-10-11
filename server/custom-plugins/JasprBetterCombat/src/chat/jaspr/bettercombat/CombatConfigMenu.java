package chat.jaspr.bettercombat;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.*;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.ItemMeta;

/** Server-authoritative UI adapter for the shipped mod's Forge GuiConfig options.
 * Inventories are only views: no GUI item can be taken, shift-moved or hotbar-swapped.
 * Other players can inspect settings; only authenticated administrators can edit.
 */
final class CombatConfigMenu implements Listener {
    private final BetterCombatPlugin plugin;
    private final Map<UUID,Edit> edits=new ConcurrentHashMap<>();
    private final Map<UUID,Long> resetConfirm=new HashMap<>();
    private static final String ADMIN="jasprbettercombat.admin";
    private static final class Option {
        final String key,label,help; final Object value; final double min,max,step;
        Option(String k,String l,Object v,String h){this(k,l,v,h,0,0,0);}
        Option(String k,String l,Object v,String h,double n,double x,double s){key=k;label=l;value=v;help=h;min=n;max=x;step=s;}
    }
    private static final Option[] OPTIONS={
        new Option("enabled","Better Combat enabled",true,"Enable this server's Better Combat adapter."),
        new Option("longer-attack","Longer Attack",true,"Adds one block of melee reach (5 instead of 4)."),
        new Option("wider-attack","Wider Attack",true,"Makes melee targets easier to hit."),
        new Option("wider-attack-width","Wider Attack Width",1.5,"Horizontal hitbox multiplier; vanilla is 0.5.",0,64,.1),
        new Option("random-crits","Random Crits",true,"Release 1.5.6 replaces falling critical hits."),
        new Option("crit-chance","Random Crit Chance",.3,"Chance from 0 to 1; sprinting prevents critical hits.",0,1,.05),
        new Option("refund-energy-on-miss","Refound energy on miss",true,"A missed attack does not consume energy."),
        new Option("weaker-offhand","Weaker Left Arm",true,"Scale off-hand damage by Offhand Efficiency."),
        new Option("offhand-efficiency","Offhand Efficiency",.5,"Damage multiplier; 0.5 means half damage.",0,16384,.05),
        new Option("require-full-energy","Attacks require full energy",false,"Prevent attacks until the selected hand is ready."),
        new Option("attack-and-sprint","Attack and Sprint",true,"Attacks do not cancel sprinting."),
        new Option("more-sweep-animation","More swipe animation",true,"Allow non-swords to create swipe animations."),
        new Option("additional-hit-sound","Additional hit sound",true,"Original swordslash.ogg; shovels are excluded."),
        new Option("additional-crit-sound","Additional crit sound",true,"Original criticalstrike.ogg; shovels are excluded."),
        new Option("enable-offhand-attacks","Enable Offhand Attacks",true,"Right-click with a second eligible weapon/tool."),
        new Option("offhand-item-classes","Item Class Whitelist",Arrays.asList("SWORD","AXE","SPADE","PICKAXE","HOE"),"Vanilla class aliases or original Minecraft class names."),
        new Option("offhand-item-whitelist","Item Whitelist",Collections.emptyList(),"Namespaced vanilla item IDs, e.g. minecraft:stick."),
        new Option("offhand-entity-blacklist","Entity Blacklist",Arrays.asList("HORSE","ARMOR_STAND","VILLAGER"),"Entity names/classes; owned pets are also protected.")
    };
    private static final class Menu implements InventoryHolder {
        final UUID owner; Inventory inventory;
        Menu(UUID owner){this.owner=owner;inventory=Bukkit.createInventory(this,54,"Better Combat | Settings");}
        @Override public Inventory getInventory(){return inventory;}
    }
    private static final class Edit {
        final Option option; final long expires=System.currentTimeMillis()+60000;
        Edit(Option option){this.option=option;}
    }
    CombatConfigMenu(BetterCombatPlugin plugin){this.plugin=plugin;}
    void open(Player p){
        if(!plugin.authenticated(p))return;
        Menu m=new Menu(p.getUniqueId());
        for(int i=0;i<OPTIONS.length;i++){
            Option o=OPTIONS[i];Object v=plugin.getConfig().get(o.key,o.value);
            String action=o.value instanceof Boolean?"Click: toggle":o.value instanceof Number?"Left/right: +/-; Shift-click: exact value":"Click: edit comma-separated list in chat";
            m.inventory.setItem(i,icon(o.value instanceof Boolean?Material.LEVER:o.value instanceof Number?Material.REDSTONE:Material.BOOK,
                ChatColor.AQUA+o.label,ChatColor.YELLOW+String.valueOf(v),ChatColor.GRAY+o.help,ChatColor.WHITE+action));
        }
        m.inventory.setItem(45,icon(Material.BOOK,ChatColor.GOLD+"Better Combat Rebirth 1.5.6",ChatColor.GRAY+"Original mod options; shared server settings.",
            p.hasPermission(ADMIN)?ChatColor.GREEN+"Administrator: edits save immediately.":ChatColor.YELLOW+"Read-only. An administrator must change settings."));
        m.inventory.setItem(36,icon(Material.DIAMOND_SWORD,ChatColor.AQUA+"JasperCraft melee animations: "+(plugin.motion(p)?"ON":"OFF"),
            ChatColor.GRAY+"Personal, optional JasperCraft enhancement.",ChatColor.GRAY+"OFF preserves upstream's vanilla arm swings.",ChatColor.WHITE+"Click: toggle. Saved across rejoins/restarts."));
        m.inventory.setItem(47,icon(Material.PAPER,ChatColor.YELLOW+"Restore original defaults",ChatColor.GRAY+"Click twice within 5 seconds to confirm."));
        m.inventory.setItem(49,icon(Material.BARRIER,ChatColor.RED+"Done"));
        p.openInventory(m.inventory);
    }
    private static ItemStack icon(Material material,String title,String...lines){
        ItemStack s=new ItemStack(material);ItemMeta meta=s.getItemMeta();meta.setDisplayName(title);meta.setLore(Arrays.asList(lines));s.setItemMeta(meta);return s;
    }
    private boolean editable(Player p){return p.hasPermission(ADMIN)&&plugin.authenticated(p);}
    @EventHandler public void click(InventoryClickEvent e){
        if(!(e.getView().getTopInventory().getHolder() instanceof Menu))return;
        e.setCancelled(true);
        if(!(e.getWhoClicked() instanceof Player))return;
        Player p=(Player)e.getWhoClicked();Menu m=(Menu)e.getView().getTopInventory().getHolder();
        if(!m.owner.equals(p.getUniqueId())||e.getRawSlot()<0||e.getRawSlot()>=54)return;
        int slot=e.getRawSlot();
        if(slot==49){later(p::closeInventory);return;}
        if(slot==36){if(plugin.authenticated(p)){plugin.toggleMotion(p);later(()->open(p));}return;}
        if(!editable(p)){p.sendMessage(ChatColor.YELLOW+"Combat settings are server-wide. Administrator permission required.");return;}
        if(slot==47){
            Long until=resetConfirm.get(p.getUniqueId());
            if(until==null||until<System.currentTimeMillis()){
                resetConfirm.put(p.getUniqueId(),System.currentTimeMillis()+5000);p.sendMessage(ChatColor.YELLOW+"Click Restore original defaults again within 5 seconds.");return;
            }
            resetConfirm.remove(p.getUniqueId());
            for(Option o:OPTIONS)plugin.getConfig().set(o.key,o.value);
            persist();later(()->open(p));return;
        }
        if(slot>=OPTIONS.length)return;
        Option o=OPTIONS[slot];
        if(o.value instanceof Boolean){plugin.getConfig().set(o.key,!plugin.getConfig().getBoolean(o.key,(Boolean)o.value));persist();later(()->open(p));}
        else if(o.value instanceof Number&&!e.isShiftClick()){
            double value=plugin.getConfig().getDouble(o.key,((Number)o.value).doubleValue())+(e.isRightClick()?-o.step:o.step);
            plugin.getConfig().set(o.key,Math.max(o.min,Math.min(o.max,Math.rint(value*1000000)/1000000)));persist();later(()->open(p));
        }else{
            edits.put(p.getUniqueId(),new Edit(o));later(p::closeInventory);
            p.sendMessage(ChatColor.AQUA+o.label+": enter a value in chat within 60 seconds. 'cancel' keeps it; '-' clears a list.");
        }
    }
    @EventHandler public void drag(InventoryDragEvent e){if(e.getView().getTopInventory().getHolder() instanceof Menu)e.setCancelled(true);}
    @EventHandler(priority=EventPriority.LOWEST) public void chat(AsyncPlayerChatEvent e){
        Edit edit=edits.remove(e.getPlayer().getUniqueId());if(edit==null||edit.expires<System.currentTimeMillis())return;
        e.setCancelled(true);String text=e.getMessage().trim();Player p=e.getPlayer();
        later(()->{
            if(!p.isOnline()||!editable(p))return;
            try{
                if(!text.equalsIgnoreCase("cancel")){
                    Object value;
                    if(edit.option.value instanceof Number){
                        double n=Double.parseDouble(text);if(!Double.isFinite(n)||n<edit.option.min||n>edit.option.max)throw new IllegalArgumentException("Value out of range.");value=n;
                    }else{
                        if(text.length()>500)throw new IllegalArgumentException("List is too long.");
                        List<String> list=new ArrayList<>();if(!text.equals("-"))for(String token:text.split(",")){
                            token=token.trim();if(!token.matches("[A-Za-z0-9_.$:/-]+"))throw new IllegalArgumentException("Use comma-separated IDs or classes.");list.add(token);
                        }value=list;
                    }
                    plugin.getConfig().set(edit.option.key,value);persist();
                }
            }catch(IllegalArgumentException ex){p.sendMessage(ChatColor.RED+"Not saved: "+ex.getMessage());}
            open(p);
        });
    }
    @EventHandler public void quit(PlayerQuitEvent e){edits.remove(e.getPlayer().getUniqueId());resetConfirm.remove(e.getPlayer().getUniqueId());}
    private void persist(){plugin.saveConfig();plugin.settings=new Settings(plugin.getConfig());}
    private void later(Runnable r){Bukkit.getScheduler().runTask(plugin,r);}
}
