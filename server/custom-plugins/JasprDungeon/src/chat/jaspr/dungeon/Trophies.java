package chat.jaspr.dungeon;

import java.util.*;
import net.minecraft.server.v1_12_R1.NBTTagCompound;
import net.minecraft.server.v1_12_R1.NBTTagList;
import org.bukkit.*;
import org.bukkit.craftbukkit.v1_12_R1.inventory.CraftItemStack;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.*;
import org.bukkit.event.player.*;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.*;
import org.bukkit.potion.*;
import org.bukkit.util.Vector;

/**
 * Generation 7: the Floor Guardians' trophies and the victor's reward (TrophyCatalog). Descents calls award() when a floor's
 * Descent or the Throne is absolved (its guardian has fallen) and victory() when a winner has stepped through the Throne's well.
 * Every member of the run who stood in the arena gets the trophy, once per run. The trophy weapons are stone-sword carriers with
 * their own attack modifiers and icon; their abilities live here, bounded by TrophyCatalog and always room-bound in the dungeon.
 * A trophy's power answers only monsters: a blow on a player with one is cancelled (no PvP), and Relics keeps them out of the
 * anvil, the enchanting table and the crafting grid and stops them digging.
 *
 * Logs DUNGEON_TROPHY_AWARDED, DUNGEON_TROPHY_UNCLAIMED, DUNGEON_TROPHY_MISSING, DUNGEON_VICTORY_REWARD, DUNGEON_VICTORY_DEFERRED.
 */
public final class Trophies implements Listener {
    private final DungeonPlugin plugin;
    /** Awards made (run/trophy/player), so a reopened arena or a second step into the well never pays twice; the oldest go first. */
    private final Map<String,Long> given=new LinkedHashMap<String,Long>(){@Override protected boolean removeEldestEntry(Map.Entry<String,Long> e){return size()>4096;}};
    /** Mercy's Last Key: when each monster may be locked again; and when each wielder's next kill heals. */
    private final Map<UUID,Long> locks=new HashMap<>(),mended=new HashMap<>();
    /** Deepbreaker: a wielder's blows in a row and the time of the last one. */
    private final Map<UUID,long[]> rhythm=new HashMap<>();
    /** Sceptre of the Abyss: when each wielder's next Tide is ready. */
    private final Map<UUID,Long> tide=new HashMap<>();
    /** True while a trophy deals its own ability damage: those hits never count as fresh blows. */
    private boolean pulsing;
    private static final long UUID_MOST=0x4A41535052545259L;
    Trophies(DungeonPlugin plugin){this.plugin=plugin;}

    // ------------------------------------------------------------ the items
    /** A fresh trophy: a weapon on its stone-sword band with its own damage and speed, or the victor's relic. */
    public static ItemStack create(TrophyCatalog.Trophy t){
        if(!t.weapon())return Relics.create(Relics.Type.valueOf(t.name()));
        ItemStack item=new ItemStack(Material.STONE_SWORD);ItemMeta m=item.getItemMeta();m.setDisplayName(ChatColor.GOLD+t.title);
        List<String> lore=new ArrayList<>();lore.add(ChatColor.DARK_PURPLE+"Dungeon trophy | Floor "+Floors.numeral(t.floor)+" | "+t.from);
        for(String line:t.lines)lore.add(ChatColor.GRAY+line);
        lore.add(ChatColor.GRAY+"Damage "+number(t.damage)+" | "+number(t.speed)+" blows a second");
        lore.add(ChatColor.DARK_GRAY+"Unbreakable. Only monsters feel it: no PvP.");
        lore.add(ChatColor.DARK_GRAY+"Only found in the Dungeon Dimension.");m.setLore(lore);item.setItemMeta(m);
        ItemStack marked=Relics.edit(item,d->{d.setString("kind","trophy");d.setString("id",t.name());d.setInt("version",1);});
        // Its own main-hand modifiers replace the stone sword's (Skin.apply keeps a list that is not empty).
        net.minecraft.server.v1_12_R1.ItemStack n=CraftItemStack.asNMSCopy(marked);NBTTagCompound tag=n.getTag();NBTTagList modifiers=new NBTTagList();
        modifiers.add(modifier("generic.attackDamage",t.damage-1,0x4000000000000001L+t.band));modifiers.add(modifier("generic.attackSpeed",t.speed-4,0x4100000000000001L+t.band));
        tag.set("AttributeModifiers",modifiers);n.setTag(tag);
        return Skin.apply(CraftItemStack.asBukkitCopy(n),Skin.SWORD,t.band);
    }
    private static NBTTagCompound modifier(String attribute,double amount,long least){
        NBTTagCompound c=new NBTTagCompound();c.setString("AttributeName",attribute);c.setString("Name","jaspr_trophy");c.setDouble("Amount",amount);
        c.setInt("Operation",0);c.setLong("UUIDMost",UUID_MOST);c.setLong("UUIDLeast",least);c.setString("Slot","mainhand");return c;
    }
    private static String number(double v){return v==Math.rint(v)?String.valueOf((int)v):String.valueOf(v);}
    /** The trophy weapon an item is, or null (the laurel is a relic: Relics.type). */
    public static TrophyCatalog.Trophy type(ItemStack item){
        NBTTagCompound d=Relics.data(item);if(d==null||!"trophy".equals(d.getString("kind")))return null;
        try{TrophyCatalog.Trophy t=TrophyCatalog.Trophy.valueOf(d.getString("id"));return t.weapon()&&Skin.is(item,Skin.SWORD,t.band)?t:null;}catch(IllegalArgumentException ex){return null;}
    }
    /** The victor's chronicle: who won, which run, and when. */
    static ItemStack chronicle(Player p,Sessions.Session run){
        ItemStack book=new ItemStack(Material.WRITTEN_BOOK);BookMeta meta=(BookMeta)book.getItemMeta();
        meta.setTitle("The Unbowed");meta.setAuthor("The Last Candle");meta.setDisplayName(ChatColor.GOLD+"Chronicle of the Unbowed: "+p.getName());
        meta.setPages("The Chronicle of the Unbowed\n\n"+p.getName()+"\nRun #"+(run==null?"?":String.valueOf(run.id))+", "+java.time.LocalDate.now()+"\n\nWent down through the House of Mercy, the Underworks and the Abyssal Citadel, and came home.",
            "The Gaoler of Mercy kept the first stair. The Deep Tyrant kept the second. The Abyssal Sovereign kept the Throne.\n\nNone of them could keep "+p.getName()+".",
            "Every keeper in these pages believed fear could hold a door shut. This one was opened from the inside.\n\n- The Last Candle");
        book.setItemMeta(meta);return book;
    }

    // ------------------------------------------------------------ awarding
    /** Puts the items in the inventory; what does not fit drops at the player's feet. The number dropped. */
    private static int deliver(Player p,List<ItemStack> items){
        int dropped=0;for(ItemStack item:items)for(ItemStack extra:p.getInventory().addItem(item).values()){p.getWorld().dropItemNaturally(p.getLocation(),extra);dropped++;}
        p.saveData();return dropped;
    }
    private static int free(Player p){int n=0;for(ItemStack i:p.getInventory().getStorageContents())if(i==null||i.getType()==Material.AIR)n++;return n;}
    /** A floor's guardian has fallen (Descents: its arena is absolved): everyone standing in the arena receives its trophy. */
    public void award(Encounters.Run a){
        if(a==null||a.world==null||a.room==null||!a.room.finale())return;
        TrophyCatalog.Trophy t=TrophyCatalog.guardian(a.room.floor);
        if(t==null){plugin.getLogger().warning("DUNGEON_TROPHY_MISSING floor="+a.room.floor);return;}
        Sessions.Session run=plugin.sessions==null?null:plugin.sessions.of(a.world);String key=plugin.roomKey(a.world,a.room);int n=0;
        for(Player p:a.world.getPlayers()){
            if(p.isDead()||p.getGameMode()==GameMode.SPECTATOR||!plugin.roomKey(p.getLocation()).equals(key))continue;
            String id=(run==null?a.world.getName():"run"+run.id)+"/"+t.name()+"/"+p.getUniqueId();if(given.containsKey(id))continue;given.put(id,System.currentTimeMillis());
            // The arena stays while the run lives, so a full pack drops the trophy at the winner's feet, in reach.
            int dropped=deliver(p,Collections.singletonList(create(t)));n++;
            p.sendMessage(ChatColor.GOLD+t.from+" leaves you "+t.title+"."+(dropped>0?ChatColor.YELLOW+" Your pack is full: it lies at your feet.":ChatColor.GRAY+" A trophy of the Dungeon Dimension."));
            p.playSound(p.getLocation(),Sound.UI_TOAST_CHALLENGE_COMPLETE,.8f,1.2f);
            plugin.getLogger().info("DUNGEON_TROPHY_AWARDED run="+(run==null?"-":String.valueOf(run.id))+" player="+p.getName()+" floor="+a.room.floor+" trophy="+t.name()+" dropped="+dropped);
        }
        if(n==0)plugin.getLogger().info("DUNGEON_TROPHY_UNCLAIMED room="+key+" floor="+a.room.floor+" reason=arenaEmpty");
    }
    /**
     * A winner stepped through the Throne's well (Descents, after sending them home): the victor's laurel and their chronicle.
     * Should the way home have been obstructed they are still in the run, whose world is deleted with it: then nothing may be
     * dropped there, so a full pack defers the reward until they step into the well again.
     */
    public void victory(Player p,Sessions.Session run){
        if(p==null||!p.isOnline())return;
        String id=(run==null?"run-":"run"+run.id)+"/VICTORY/"+p.getUniqueId();if(given.containsKey(id))return;
        List<ItemStack> items=Arrays.asList(create(TrophyCatalog.victory()),chronicle(p,run));
        if(plugin.inside(p.getWorld())&&free(p)<items.size()){
            p.sendMessage(ChatColor.YELLOW+"Make room for the victor's reward ("+items.size()+" free slots), then step into the well again.");
            plugin.getLogger().info("DUNGEON_VICTORY_DEFERRED run="+(run==null?"-":String.valueOf(run.id))+" player="+p.getName()+" reason=packFull");return;
        }
        given.put(id,System.currentTimeMillis());int dropped=deliver(p,items);
        p.sendMessage(ChatColor.GOLD+"The Laurel of the Unbowed is yours: equip it in your Dungeon Reliquary."+(dropped>0?ChatColor.YELLOW+" Your pack was full: the rest lies at your feet.":""));
        p.playSound(p.getLocation(),Sound.UI_TOAST_CHALLENGE_COMPLETE,1f,.8f);
        plugin.getLogger().info("DUNGEON_VICTORY_REWARD run="+(run==null?"-":String.valueOf(run.id))+" player="+p.getName()+" dropped="+dropped);
    }

    // ------------------------------------------------------------ the weapons
    private boolean boss(Entity e){return e!=null&&e.getScoreboardTags().contains("jpd_slot:0")&&plugin.inside(e.getWorld())&&plugin.room(e.getLocation()).bossRoom();}
    /** A monster the wielder may strike: in the dungeon only in their own room (Encounters' rule), outside in their own world. */
    private boolean reachable(Player p,LivingEntity m){
        if(m==null||m instanceof Player||!Relics.hostile(m)||m.isDead()||!m.isValid())return false;
        if(plugin.inside(p.getWorld())||plugin.inside(m.getWorld()))return plugin.encounters!=null&&plugin.encounters.canTargetInRoom(p,m);
        return m.getWorld().equals(p.getWorld());
    }
    /** Monsters near a point the wielder may strike, nearest first, at most max (none at all past 128 bodies nearby). */
    private List<LivingEntity> foes(Player p,Location at,double radius,int max,Entity except){
        List<LivingEntity> out=new ArrayList<>();Collection<Entity> near=at.getWorld().getNearbyEntities(at,radius,Math.max(2,radius),radius);if(near.size()>128)return out;
        for(Entity e:near)if(e!=except&&e instanceof LivingEntity&&e.getLocation().distanceSquared(at)<=radius*radius&&reachable(p,(LivingEntity)e))out.add((LivingEntity)e);
        out.sort(Comparator.comparingDouble(e->e.getLocation().distanceSquared(at)));return out.size()>max?new ArrayList<>(out.subList(0,max)):out;
    }
    private void hit(Player p,LivingEntity m,double amount){pulsing=true;try{m.damage(amount,p);}finally{pulsing=false;}}
    /** No PvP with a trophy (owner rule for the dungeon's weapons: no one-shots on players): such a blow, sweep included, is cancelled. */
    @EventHandler(priority=EventPriority.HIGH,ignoreCancelled=true) public void guard(EntityDamageByEntityEvent e){
        if(e.getDamager() instanceof Player&&e.getEntity() instanceof Player&&type(((Player)e.getDamager()).getInventory().getItemInMainHand())!=null)e.setCancelled(true);
    }
    /** An accepted melee blow with a trophy on a monster: the Key locks it, the Deepbreaker counts toward its tremor. */
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true) public void blow(EntityDamageByEntityEvent e){
        if(pulsing||e.getFinalDamage()<=0||e.getCause()!=EntityDamageEvent.DamageCause.ENTITY_ATTACK||!(e.getDamager() instanceof Player)||!(e.getEntity() instanceof LivingEntity))return;
        if(plugin.relics!=null&&plugin.relics.echoing())return;
        Player p=(Player)e.getDamager();LivingEntity foe=(LivingEntity)e.getEntity();TrophyCatalog.Trophy t=type(p.getInventory().getItemInMainHand());
        if(t==null||foe instanceof Player||!Relics.hostile(foe))return;long now=System.currentTimeMillis();
        if(t==TrophyCatalog.Trophy.MERCYS_LAST_KEY){
            if(now<locks.getOrDefault(foe.getUniqueId(),0L))return;
            if(locks.size()>256){locks.values().removeIf(until->until<now);if(locks.size()>256)locks.clear();}
            locks.put(foe.getUniqueId(),now+t.cooldownMillis);Relics.status(foe,PotionEffectType.SLOW,30,boss(foe)?0:2);
            foe.getWorld().playSound(foe.getLocation(),Sound.BLOCK_IRON_TRAPDOOR_CLOSE,.8f,.6f);
        }else if(t==TrophyCatalog.Trophy.DEEPBREAKER){
            long[] r=rhythm.computeIfAbsent(p.getUniqueId(),k->new long[2]);if(now-r[1]>t.cooldownMillis)r[0]=0;r[0]++;r[1]=now;
            if(r[0]<3)return;r[0]=0;
            for(LivingEntity m:foes(p,foe.getLocation(),t.radius,t.targets,foe)){hit(p,m,t.power);push(foe.getLocation(),m);}
            foe.getWorld().spawnParticle(Particle.EXPLOSION_NORMAL,foe.getLocation().add(0,.2,0),10,1.4,.1,1.4,.02);
            foe.getWorld().playSound(foe.getLocation(),Sound.ENTITY_GENERIC_EXPLODE,.5f,.5f);
        }
    }
    /** A bounded shove away from the tremor's heart; bosses stand firm and the room keeps its monsters. */
    private void push(Location from,LivingEntity m){
        if(boss(m)||m.isDead())return;Vector away=m.getLocation().toVector().subtract(from.toVector()).setY(0);
        if(away.lengthSquared()>1e-4)m.setVelocity(away.normalize().multiply(.6).setY(.2));
    }
    /** Mercy's Last Key: a kill with it in hand restores a heart, at most every 3 seconds. */
    @EventHandler public void kill(EntityDeathEvent e){
        Player p=e.getEntity().getKiller();if(p==null||!Relics.hostile(e.getEntity())||type(p.getInventory().getItemInMainHand())!=TrophyCatalog.Trophy.MERCYS_LAST_KEY)return;
        long now=System.currentTimeMillis();if(now<mended.getOrDefault(p.getUniqueId(),0L))return;
        mended.put(p.getUniqueId(),now+3000);Relics.heal(p,TrophyCatalog.Trophy.MERCYS_LAST_KEY.power);
    }
    /** The Sceptre's Abyssal Tide (right-click). Bukkit pre-cancels air clicks of inert items, so this listens to cancelled ones too. */
    @EventHandler(priority=EventPriority.MONITOR) public void use(PlayerInteractEvent e){
        if(e.getHand()!=EquipmentSlot.HAND||(e.getAction()!=Action.RIGHT_CLICK_AIR&&e.getAction()!=Action.RIGHT_CLICK_BLOCK))return;
        Player p=e.getPlayer();TrophyCatalog.Trophy t=type(e.getItem());if(t!=TrophyCatalog.Trophy.ABYSSAL_SCEPTRE||p.isDead()||p.getGameMode()==GameMode.SPECTATOR)return;
        // Opening a chest with the sceptre in hand opens the chest only; the arrival circle stays a place of peace.
        if(e.getAction()==Action.RIGHT_CLICK_BLOCK&&e.getClickedBlock()!=null&&e.getClickedBlock().getType()==Material.CHEST)return;
        if(plugin.sanctuary!=null&&plugin.sanctuary.contains(p.getLocation()))return;
        long now=System.currentTimeMillis(),ready=tide.getOrDefault(p.getUniqueId(),0L);
        if(now<ready){bar(p,ChatColor.DARK_PURPLE+"The Abyss gathers: "+((ready-now+999)/1000)+"s");return;}
        List<LivingEntity> near=foes(p,p.getLocation(),t.radius,t.targets,null);near.removeIf(m->!p.hasLineOfSight(m));
        if(near.isEmpty()){bar(p,ChatColor.GRAY+"No monster in sight for the Abyssal Tide.");return;}
        if(tide.size()>256)tide.values().removeIf(until->until<now);tide.put(p.getUniqueId(),now+t.cooldownMillis);
        for(LivingEntity m:near){hit(p,m,t.power);if(m.isValid()&&!m.isDead())Relics.status(m,PotionEffectType.WITHER,60,0);}
        PotionEffect guard=p.getPotionEffect(PotionEffectType.DAMAGE_RESISTANCE);if(guard==null||guard.getAmplifier()==0&&guard.getDuration()<60)p.addPotionEffect(new PotionEffect(PotionEffectType.DAMAGE_RESISTANCE,60,0,true,false),true);
        Location c=p.getLocation();for(int k=0;k<16;k++){double a=k*Math.PI/8;p.getWorld().spawnParticle(Particle.SPELL_WITCH,c.getX()+2.5*Math.cos(a),c.getY()+.6,c.getZ()+2.5*Math.sin(a),2,.1,.2,.1,0);}
        p.getWorld().playSound(c,Sound.ENTITY_WITHER_SHOOT,.6f,.6f);bar(p,ChatColor.DARK_PURPLE+"The Abyssal Tide takes "+near.size()+(near.size()==1?" monster.":" monsters."));
    }
    private static void bar(Player p,String text){p.spigot().sendMessage(net.md_5.bungee.api.ChatMessageType.ACTION_BAR,net.md_5.bungee.api.chat.TextComponent.fromLegacyText(text));}
    /** The Tide keeps its cooldown across a reconnect (its map is pruned of spent entries instead); the rest is forgotten. */
    @EventHandler public void quit(PlayerQuitEvent e){UUID id=e.getPlayer().getUniqueId();mended.remove(id);rhythm.remove(id);}
}
