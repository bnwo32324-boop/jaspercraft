package chat.jaspr.bettercombat.test;

import org.bukkit.*;
import org.bukkit.Material;
import org.bukkit.command.*;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.block.Action;
import org.bukkit.craftbukkit.v1_12_R1.entity.CraftPlayer;
import org.bukkit.craftbukkit.v1_12_R1.inventory.CraftItemStack;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import net.minecraft.server.v1_12_R1.*;
import org.bukkit.craftbukkit.v1_12_R1.entity.CraftLivingEntity;
import java.util.*;

/** Test-only fixture, NEVER part of the deliverable plugin. Uses a genuine connected player.
 * Copied from the 2026-09-30 port's candidate (tests/CombatFixture.java) and extended for the dual-wield fixes (2026-10-11):
 * a plugin item ability (claim), a plugin player interaction (guard), player-vs-player with a shield (pvp), game modes. */
public final class CombatFixture extends JavaPlugin implements Listener {
    LivingEntity victim;
    LivingEntity secondary;
    boolean cancel;
    double lastBase;
    int events;
    int uses;
    Material claim;
    boolean guard;
    int claims, guarded;
    boolean rewrite;
    int rewrites;
    @Override public void onEnable(){Bukkit.getPluginManager().registerEvents(this,this);}
    @EventHandler public void join(PlayerJoinEvent e){
        e.getPlayer().teleport(new Location(e.getPlayer().getWorld(),.5,64,.5,0,0));
        e.getPlayer().setGameMode(GameMode.SURVIVAL);
        e.getPlayer().setFoodLevel(20);
    }
    @EventHandler(priority=EventPriority.LOWEST) public void damage(EntityDamageByEntityEvent e){
        if(e.getEntity()==victim){lastBase=e.getDamage();events++;if(cancel)e.setCancelled(true);}
    }
    @EventHandler public void use(PlayerInteractEvent e){if(e.getAction().name().startsWith("RIGHT_CLICK"))uses++;}
    /** Like JasprRPG's armament XP: every blow rewrites the attacker's main-hand weapon (CraftBukkit sends that slot at once). */
    @EventHandler(priority=EventPriority.HIGH,ignoreCancelled=true) public void rewriteHand(EntityDamageByEntityEvent e){
        if(!rewrite||!(e.getDamager() instanceof Player))return;
        Player a=(Player)e.getDamager();ItemStack w=a.getInventory().getItemInMainHand();
        if(w==null||w.getType()==Material.AIR)return;
        ItemStack c=w.clone();org.bukkit.inventory.meta.ItemMeta m=c.getItemMeta();m.setLore(Arrays.asList("blows "+(++rewrites)));c.setItemMeta(m);
        a.getInventory().setItemInMainHand(c);
    }
    /** Like a staff / wand / trinket handler: claims the main hand's right click in the air. */
    @EventHandler(priority=EventPriority.HIGH) public void claimUse(PlayerInteractEvent e){
        if(claim!=null&&e.getAction()==Action.RIGHT_CLICK_AIR&&e.getItem()!=null&&e.getItem().getType()==claim){e.setCancelled(true);claims++;}
    }
    /** Like reviving a downed player or boarding a tank: handles a right click on a player. */
    @EventHandler(priority=EventPriority.HIGH) public void guardPlayers(PlayerInteractEntityEvent e){
        if(guard&&e.getRightClicked() instanceof Player){e.setCancelled(true);guarded++;}
    }
    @Override public boolean onCommand(CommandSender sender,Command command,String label,String[] args){
        if(!(sender instanceof ConsoleCommandSender))return true;
        try{
            Player p=Bukkit.getPlayerExact(args[1]);if(p==null)throw new IllegalStateException("player absent");
            if(args[0].equals("auth")){
                org.bukkit.plugin.Plugin auth=Bukkit.getPluginManager().getPlugin("AuthMe");
                if(auth!=null){
                    Class<?> c=Class.forName("fr.xephi.authme.api.v3.AuthMeApi",true,auth.getClass().getClassLoader());
                    Object api=c.getMethod("getInstance").invoke(null);
                    c.getMethod("registerPlayer",String.class,String.class).invoke(api,p.getName(),UUID.randomUUID().toString());
                    c.getMethod("forceLogin",Player.class).invoke(api,p);
                }
                getLogger().info("FIXTURE_AUTH "+p.getName());
            }else if(args[0].equals("setup")){
                for(org.bukkit.entity.Entity e:p.getWorld().getEntities())if(!(e instanceof Player))e.remove();
                for(int y=64;y<68;y++)for(int x=-2;x<=2;x++)p.getWorld().getBlockAt(x,y,2).setType(Material.AIR);
                p.teleport(new Location(p.getWorld(),.5,64,.5,0,0));
                p.setSprinting(false);
                for(Player other:Bukkit.getOnlinePlayers())if(other!=p)other.teleport(new Location(p.getWorld(),20.5,64,20.5));
                p.getInventory().clear();p.getInventory().setHeldItemSlot(0);
                p.getInventory().setItemInMainHand(new ItemStack(Material.DIAMOND_SWORD));
                p.getInventory().setItemInOffHand(new ItemStack(Material.DIAMOND_SWORD));
                double z=args.length>2?Double.parseDouble(args[2]):4;
                EntityType type=args.length>3?EntityType.valueOf(args[3]):EntityType.ZOMBIE;
                victim=(LivingEntity)p.getWorld().spawnEntity(new Location(p.getWorld(),.5,64,z+.5),type);
                if(victim instanceof Zombie)((Zombie)victim).setBaby(false);
                victim.setAI(false);victim.setRemoveWhenFarAway(false);victim.setMaxHealth(1000);victim.setHealth(1000);
                // Natural zombie equipment is randomized even with zero base armor.
                // Keep the arithmetic fixture unarmored; protection events remain fully enabled.
                if(victim.getEquipment()!=null)victim.getEquipment().setArmorContents(new ItemStack[4]);
                ((CraftLivingEntity)victim).getHandle().getAttributeInstance(GenericAttributes.h).setValue(0);
                if(type==EntityType.WOLF){Location aimed=p.getLocation();aimed.setPitch((float)Math.toDegrees(Math.atan2(1.62-.4,z)));p.teleport(aimed);}
                secondary=null;cancel=false;lastBase=0;events=0;uses=0;
                getLogger().info("FIXTURE_READY "+p.getName()+" "+victim.getUniqueId());
            }else if(args[0].equals("claim")){claim=args[2].equals("off")?null:Material.valueOf(args[2]);claims=0;
            }else if(args[0].equals("guard")){guard=args[2].equals("on");guarded=0;
            }else if(args[0].equals("rewrite")){rewrite=args[2].equals("on");rewrites=0;
            }else if(args[0].equals("gm")){p.setGameMode(GameMode.valueOf(args[2]));
            }else if(args[0].equals("health")){victim.setHealth(Double.parseDouble(args[2]));
            }else if(args[0].equals("pvp")){
                Player other=Bukkit.getPlayerExact(args[2]);if(other==null)throw new IllegalStateException("second player absent");
                for(org.bukkit.entity.Entity e:p.getWorld().getEntities())if(!(e instanceof Player))e.remove();
                for(int y=64;y<68;y++)for(int x=-2;x<=2;x++)p.getWorld().getBlockAt(x,y,2).setType(Material.AIR);
                p.teleport(new Location(p.getWorld(),.5,64,.5,0,0));other.teleport(new Location(p.getWorld(),.5,64,3.5,180,0));
                p.setGameMode(GameMode.SURVIVAL);other.setGameMode(GameMode.SURVIVAL);p.setSprinting(false);
                p.getInventory().clear();p.getInventory().setHeldItemSlot(0);
                p.getInventory().setItemInMainHand(new ItemStack(Material.DIAMOND_SWORD));p.getInventory().setItemInOffHand(new ItemStack(Material.DIAMOND_SWORD));
                other.getInventory().clear();other.getInventory().setItemInOffHand(args.length>3&&args[3].equals("shield")?new ItemStack(Material.SHIELD):null);
                other.setMaxHealth(1000);other.setHealth(1000);other.setNoDamageTicks(0);
                victim=other;secondary=null;cancel=false;lastBase=0;events=0;uses=0;
                getLogger().info("FIXTURE_READY "+p.getName()+" "+victim.getUniqueId());
            }else if(args[0].equals("wall")){
                for(int y=64;y<68;y++)for(int x=-2;x<=2;x++)p.getWorld().getBlockAt(x,y,2).setType(Material.STONE);
            }else if(args[0].equals("cancel")){cancel=true;
            }else if(args[0].equals("equip")){
                ItemStack item=new ItemStack(Material.valueOf(args[3]),1);
                if(args[2].equals("O"))p.getInventory().setItemInOffHand(item);else p.getInventory().setItemInMainHand(item);
            }else if(args[0].equals("enchant")){
                net.minecraft.server.v1_12_R1.ItemStack item=args[2].equals("O")?((CraftPlayer)p).getHandle().getItemInOffHand():((CraftPlayer)p).getHandle().getItemInMainHand();
                item.addEnchantment(Enchantment.c(Integer.parseInt(args[3])),Integer.parseInt(args[4]));
                p.updateInventory();
            }else if(args[0].equals("metadata")){
                // Real NBT serialized over Minecraft packets, never a JavaScript item mock.
                net.minecraft.server.v1_12_R1.ItemStack item=args[2].equals("O")?((CraftPlayer)p).getHandle().getItemInOffHand():((CraftPlayer)p).getHandle().getItemInMainHand();
                NBTTagCompound tag=item.getTag();if(tag==null)tag=new NBTTagCompound();
                String flavor=args[3];
                if(flavor.equals("named")||flavor.equals("lore")){
                    NBTTagCompound display=new NBTTagCompound();display.setString("Name","Combat regression sword");
                    if(flavor.equals("lore")){NBTTagList lore=new NBTTagList();lore.add(new NBTTagString("Actual network item metadata"));display.set("Lore",lore);}
                    tag.set("display",display);
                }else if(flavor.equals("unbreakable")){tag.setBoolean("Unbreakable",true);
                }else if(flavor.equals("custommelee")){
                    NBTTagCompound data=new NBTTagCompound();data.setString("id","thermal_machete");data.setString("arsenalMark","jaspr-arsenal-v1");tag.set("JasprApocalypse",data);
                }else if(flavor.equals("gear")){tag.setString("JasprGear","regression-reserved");
                }else if(!flavor.equals("empty")){throw new IllegalArgumentException("Unknown metadata flavor");}
                item.setTag(tag);p.updateInventory();getLogger().info("FIXTURE_METADATA "+p.getName()+" "+args[2]+" "+flavor);
            }else if(args[0].equals("hurt")){p.setHealth(10);
            }else if(args[0].equals("op")){p.setOp(Boolean.parseBoolean(args[2]));
            }else if(args[0].equals("haste")){p.addPotionEffect(new org.bukkit.potion.PotionEffect(org.bukkit.potion.PotionEffectType.FAST_DIGGING,100,Integer.parseInt(args[2])),true);
            }else if(args[0].equals("pet")){((Tameable)victim).setOwner(p);victim.setMaxHealth(1000);victim.setHealth(1000);
            }else if(args[0].equals("sprint")){p.setSprinting(true);
            }else if(args[0].equals("secondary")){
                secondary=(LivingEntity)p.getWorld().spawnEntity(victim.getLocation().add(1,0,0),EntityType.ZOMBIE);
                ((Zombie)secondary).setBaby(false);
                secondary.setAI(false);secondary.setMaxHealth(1000);secondary.setHealth(1000);
                if(secondary.getEquipment()!=null)secondary.getEquipment().setArmorContents(new ItemStack[4]);
                ((CraftLivingEntity)secondary).getHandle().getAttributeInstance(GenericAttributes.h).setValue(0);
            }else if(args[0].equals("wear")){
                net.minecraft.server.v1_12_R1.ItemStack item=((CraftPlayer)p).getHandle().getItemInOffHand();
                item.setData(p.getInventory().getItemInOffHand().getType().getMaxDurability());p.updateInventory();
            }else if(args[0].equals("slow")){
                net.minecraft.server.v1_12_R1.ItemStack item=((CraftPlayer)p).getHandle().getItemInOffHand();
                NBTTagCompound tag=new NBTTagCompound(),modifier=new NBTTagCompound();NBTTagList list=new NBTTagList();
                modifier.setString("AttributeName","generic.attackSpeed");modifier.setString("Name","fixture slow weapon");
                modifier.setDouble("Amount",-3.99);modifier.setInt("Operation",0);modifier.setLong("UUIDMost",7);modifier.setLong("UUIDLeast",9);modifier.setString("Slot","mainhand");
                list.add(modifier);tag.set("AttributeModifiers",list);item.setTag(tag);p.updateInventory();
            }else if(args[0].equals("offset")){victim.teleport(victim.getLocation().add(Double.parseDouble(args[2]),0,0));
            }else if(args[0].equals("gun")){
                net.minecraft.server.v1_12_R1.ItemStack item=new net.minecraft.server.v1_12_R1.ItemStack(Items.DIAMOND_HOE);
                NBTTagCompound tag=new NBTTagCompound(),data=new NBTTagCompound();data.setString("id",args.length>2?args[2]:"portal_gun");
                data.setString("arsenalMark","jaspr-arsenal-v1");tag.set("JasprApocalypse",data);tag.setBoolean("Unbreakable",true);item.setTag(tag);
                p.getInventory().setItemInMainHand(CraftItemStack.asBukkitCopy(item));
            }else if(args[0].equals("report")){
                getLogger().info("COMBAT_REPORT {\"victimId\":"+(victim==null?-1:victim.getEntityId())+",\"claims\":"+claims+",\"guarded\":"+guarded+",\"health\":"+(victim==null?-1:victim.getHealth())+",\"secondaryHealth\":"+(secondary==null?-1:secondary.getHealth())+",\"fire\":"+(victim==null?0:victim.getFireTicks())+",\"events\":"+events+",\"uses\":"+uses+",\"base\":"+lastBase+",\"mainDamage\":"+p.getInventory().getItemInMainHand().getDurability()+",\"offDamage\":"+p.getInventory().getItemInOffHand().getDurability()+",\"offType\":\""+p.getInventory().getItemInOffHand().getType().name()+"\",\"playerHealth\":"+p.getHealth()+",\"mainStrength\":"+((CraftPlayer)p).getHandle().n(0)+",\"sprinting\":"+p.isSprinting()+"}");
            }else if(args[0].equals("config")){
                JavaPlugin mod=(JavaPlugin)Bukkit.getPluginManager().getPlugin("JasprBetterCombat");
                String key=args[2],value=args[3];
                mod.getConfig().set(key,value.equals("true")||value.equals("false")?Boolean.parseBoolean(value):Double.parseDouble(value));
                mod.saveConfig();Bukkit.dispatchCommand(sender,"bettercombat reload");
            }else if(args[0].equals("configlist")){
                JavaPlugin mod=(JavaPlugin)Bukkit.getPluginManager().getPlugin("JasprBetterCombat");
                mod.getConfig().set(args[2],args[3].equals("-")?Collections.emptyList():Arrays.asList(args[3].split(",")));
                mod.saveConfig();Bukkit.dispatchCommand(sender,"bettercombat reload");
            }else if(args[0].equals("menu")){
                getLogger().info("COMBAT_MENU "+p.getOpenInventory().getTitle()+" "+p.getOpenInventory().getTopInventory().getSize());
            }
        }catch(Exception e){getLogger().severe("FIXTURE_ERROR "+e);}
        return true;
    }
}
