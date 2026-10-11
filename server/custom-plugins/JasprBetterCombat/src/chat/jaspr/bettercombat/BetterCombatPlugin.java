/* Combat algorithm adapted from Better Combat Rebirth Helpers.java (SanAndreasP).
 * That file carries CC BY-NC-SA 4.0; see THIRD_PARTY_NOTICES.txt for original
 * attribution, upstream README licensing discrepancy, and adaptation details.
 */
package chat.jaspr.bettercombat;

import com.google.gson.JsonObject;
import java.io.*;
import java.lang.reflect.Method;
import java.util.*;
import net.minecraft.server.v1_12_R1.*;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.command.*;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.craftbukkit.v1_12_R1.entity.CraftEntity;
import org.bukkit.craftbukkit.v1_12_R1.entity.CraftPlayer;
import org.bukkit.entity.Player;
import org.bukkit.entity.Tameable;
import org.bukkit.event.*;
import org.bukkit.event.entity.EntityCombustByEntityEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerAnimationEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.plugin.messaging.PluginMessageListener;
import org.bukkit.util.Vector;

/** Paper adapter. All combat runs on the server thread through the normal damage event pipeline. */
public final class BetterCombatPlugin extends JavaPlugin implements Listener, PluginMessageListener {
    public static final String CHANNEL="jaspr:combat";
    Settings settings;
    long tick;
    private final Map<UUID,State> states=new HashMap<>();
    private final Map<UUID,Long> offImmunity=new HashMap<>();
    private final Random random=new Random();
    private Method authGetter, authCheck;
    private YamlConfiguration saved;
    private File stateFile;
    private File preferenceFile;
    private YamlConfiguration preferences;
    private CombatConfigMenu configMenu;
    private LiveCombatProbe liveProbe;
    static final class State {
        long offUntil, lastMain=-1, lastOff=-1, lastHello=-100;
        boolean connected;
        int messages, offSwing;
        String lastState;
        long lastSync;
    }
    @Override public void onEnable() {
        saveDefaultConfig(); settings=new Settings(getConfig());
        stateFile=new File(getDataFolder(),"cooldowns.yml"); saved=YamlConfiguration.loadConfiguration(stateFile);
        preferenceFile=new File(getDataFolder(),"preferences.yml");preferences=YamlConfiguration.loadConfiguration(preferenceFile);
        getServer().getMessenger().registerIncomingPluginChannel(this,CHANNEL,this);
        getServer().getMessenger().registerOutgoingPluginChannel(this,CHANNEL);
        getServer().getPluginManager().registerEvents(this,this);
        configMenu=new CombatConfigMenu(this);
        getServer().getPluginManager().registerEvents(configMenu,this);
        liveProbe=new LiveCombatProbe(this);
        getServer().getScheduler().runTaskTimer(this,()->{
            tick++;
            for(Player p:Bukkit.getOnlinePlayers()) {
                State s=states.get(p.getUniqueId());
                if(s==null) continue;
                s.messages=0;
                if(s.connected && tick%2==0) sync(p,s);
            }
            if(tick%100==0) offImmunity.values().removeIf(until->until<=tick);
        },1,1);
    }
    @Override public void onDisable() {
        if(liveProbe!=null)liveProbe.close();
        for(Map.Entry<UUID,State> e:states.entrySet()) saveState(e.getKey(),e.getValue());
        saveStates(); states.clear(); offImmunity.clear();
    }
    private State state(Player p) {
        return states.computeIfAbsent(p.getUniqueId(),id->{
            State s=new State(); s.offUntil=tick+Math.max(0,Math.min(1200,saved.getInt(id.toString(),0))); return s;
        });
    }
    private void saveState(UUID id,State s) { saved.set(id.toString(),s.offUntil>tick?s.offUntil-tick:null); }
    private void saveStates() { try { saved.save(stateFile); } catch(IOException e) { getLogger().warning("Cooldown save: "+e); } }
    boolean motion(Player p){return preferences.getBoolean(p.getUniqueId()+".motion",true);}
    void toggleMotion(Player p){
        preferences.set(p.getUniqueId()+".motion",!motion(p));
        try{preferences.save(preferenceFile);}catch(IOException e){getLogger().warning("Preference save: "+e);}
        State s=states.get(p.getUniqueId());if(s!=null)sync(p,s);
    }
    @EventHandler public void quit(PlayerQuitEvent e) {
        State s=states.remove(e.getPlayer().getUniqueId());
        if(s!=null) { saveState(e.getPlayer().getUniqueId(),s); saveStates(); }
    }
    boolean authenticated(Player p) {
        if(getServer().getPluginManager().getPlugin("AuthMe")==null) return true;
        try {
            if(authGetter==null) {
                Class<?> api=Class.forName("fr.xephi.authme.api.v3.AuthMeApi",true,getClassLoader());
                authGetter=api.getMethod("getInstance"); authCheck=api.getMethod("isAuthenticated",Player.class);
            }
            return Boolean.TRUE.equals(authCheck.invoke(authGetter.invoke(null),p));
        } catch(ReflectiveOperationException e) { return false; }
    }
    @Override public void onPluginMessageReceived(String channel,Player p,byte[] bytes) {
        if(!CHANNEL.equals(channel)||!Bukkit.isPrimaryThread()) return;
        State s=state(p);
        if(++s.messages>6) return;
        final String message;
        try { message=Wire.decode(bytes); } catch(IOException e) { return; }
        if("hello 1".equals(message)) {
            if(tick-s.lastHello<20) return;
            s.lastHello=tick; s.connected=true; getLogger().info("COMBAT_CLIENT_HELLO player="+p.getName());sync(p,s); return;
        }
        if(!s.connected || !authenticated(p)) return;
        if("config open".equals(message)) { configMenu.open(p); return; }
        if(!settings.enabled || p.isDead() || p.getGameMode()==GameMode.SPECTATOR) return;
        if("attack M".equals(message)) { if(s.lastMain==tick) return; s.lastMain=tick; attack(p,s,false); }
        else if("attack O".equals(message)) { if(s.lastOff==tick) return; s.lastOff=tick; attack(p,s,true); }
    }
    static boolean specialUse(ItemStack stack) {
        if(stack.isEmpty()) return false;
        NBTTagCompound tag=stack.getTag();
        // Jasper maps non-melee equipment onto tools. Never steal those tools' right-clicks.
        Item item=stack.getItem();
        boolean melee=item instanceof ItemSword || item instanceof ItemAxe || item instanceof ItemSpade || item instanceof ItemPickaxe;
        return tag!=null && (tag.hasKey("JasprGear") || tag.hasKey("JasprGearUse") || tag.hasKey("JasprGearPack") ||
                tag.hasKey("JasprGearIcon") || tag.hasKey("JasprPortalGun") || tag.hasKey("JasprApocalypse")&&!melee);
    }
    boolean offWeapon(ItemStack stack) {
        if(stack.isEmpty() || specialUse(stack)) return false;
        Item item=stack.getItem();
        return settings.usable(item) || settings.items.contains(Item.REGISTRY.b(item).toString());
    }
    int offPeriod(EntityPlayer p) {
        double speed=4;
        for(Map.Entry<String,AttributeModifier> e:p.getItemInOffHand().a(EnumItemSlot.MAINHAND).entries())
            if(e.getKey().contains("attackSpeed")) speed=e.getValue().d();
        return CombatRules.period(speed);
    }
    float offDamage(EntityPlayer p) {
        double damage=4;
        for(Map.Entry<String,AttributeModifier> e:p.getItemInOffHand().a(EnumItemSlot.MAINHAND).entries())
            if(e.getKey().contains("attackDamage")) damage=e.getValue().d();
        return (float)((1+damage)*(settings.weaker?settings.efficiency:1));
    }
    boolean offTarget(Player p,Entity e) {
        if(!(e instanceof EntityLiving) || settings.blocked(e)) return false;
        org.bukkit.entity.Entity b=e.getBukkitEntity();
        return !(b instanceof Tameable) || !p.getUniqueId().equals(((Tameable)b).getOwner()==null?null:((Tameable)b).getOwner().getUniqueId());
    }
    /** Select the closest expanded entity box, clipped at the first real block collision. */
    Entity target(Player p) {
        EntityPlayer n=((CraftPlayer)p).getHandle(); Location eye=p.getEyeLocation(); Vector look=eye.getDirection();
        double reach=settings.longer?5:4;
        Vec3D from=new Vec3D(eye.getX(),eye.getY(),eye.getZ());
        Vec3D to=new Vec3D(from.x+look.getX()*reach,from.y+look.getY()*reach,from.z+look.getZ()*reach);
        MovingObjectPosition block=n.world.rayTrace(from,to,false,true,false);
        double limit=block==null?reach:Math.sqrt(from.distanceSquared(block.pos));
        double[] origin={from.x,from.y,from.z},dir={look.getX(),look.getY(),look.getZ()};
        Entity closest=null;
        double width=settings.wider?settings.width:.5;
        // Same swept player box + 1 broad-phase as the upstream ray, not a whole-world scan.
        AxisAlignedBB sweep=n.getBoundingBox().b(look.getX()*reach,look.getY()*reach,look.getZ()*reach).grow(1,1,1);
        for(Entity e:n.world.getEntities(n,sweep)) {
            if(!e.isAlive() || !e.isInteractable() || e==n.getVehicle() || e.isPassenger()&&e.getVehicle()==n) continue;
            double half=e.width*width;
            double d=CombatRules.intersect(origin,dir,new double[]{e.locX-half,e.locY,e.locZ-half},
                    new double[]{e.locX+half,e.locY+e.length,e.locZ+half},limit);
            if(d<limit || d==0 && closest==null) { limit=d; closest=e; }
        }
        return closest;
    }
    void attack(Player p,State s,boolean off) {
        EntityPlayer n=((CraftPlayer)p).getHandle();
        if(n.isHandRaised() || specialUse(n.getItemInMainHand())) return;
        if(off && (!settings.offhand || !offWeapon(n.getItemInOffHand()))) return;
        float strength=off?CombatRules.strength((int)Math.max(0,s.offUntil-tick),offPeriod(n)):n.n(.5f);
        if(settings.full && strength<1) return;
        Entity victim=target(p);
        if(off && victim!=null && !offTarget(p,victim)) return;
        PlayerAnimationEvent animation=new PlayerAnimationEvent(p);
        Bukkit.getPluginManager().callEvent(animation);
        // Do not call EntityPlayer.swingArm: Paper resets main-hand energy for BOTH
        // hands. Broadcast the same native visual while keeping our hand timers intact.
        if(!animation.isCancelled()) ((WorldServer)n.world).getTracker().a(n,new PacketPlayOutAnimation(n,off?3:0));
        if(victim==null) {
            if(!settings.refund) { if(off) consumeOff(n,s); else n.ds(); }
            sync(p,s); return;
        }
        if(!victim.bd() || victim.t(n)) return;
        if(off && offImmunity.getOrDefault(victim.getUniqueID(),0L)>tick) return;
        if(settings.sweep && victim.noDamageTicks<=10) sweepParticle(p);
        if(off) consumeOff(n,s); else n.ds();
        strike(p,n,victim,off,strength);
        sync(p,s);
    }
    private void consumeOff(EntityPlayer p,State state) {
        state.offUntil=tick+offPeriod(p);
        // PacketOffhandCooldown -> ScheduledPacketTask -> ClientProxy.spawnSweep.
        // A sequence distinguishes a fresh attack from routine cooldown updates.
        if(settings.sweep || p.getItemInOffHand().getItem() instanceof ItemSword) {
            state.offSwing=state.offSwing==Integer.MAX_VALUE?1:state.offSwing+1;
        }
    }
    /** Algorithm port of Helpers.attackTargetEntityItem; attribution is in THIRD_PARTY_NOTICES. */
    void strike(Player player,EntityPlayer p,Entity target,boolean off,float strength) {
        ItemStack weapon=off?p.getItemInOffHand():p.getItemInMainHand();
        float base=off?offDamage(p):(float)p.getAttributeInstance(GenericAttributes.ATTACK_DAMAGE).getValue();
        float ench=EnchantmentManager.a(weapon,target instanceof EntityLiving?((EntityLiving)target).getMonsterType():EnumMonsterType.UNDEFINED);
        boolean crit=settings.randomCrits && random.nextFloat()<settings.chance && !p.isSprinting();
        float amount=CombatRules.damage(base,ench,strength,crit);
        if(!Float.isFinite(amount)||amount<=0) return;
        boolean strong=strength>.9f, sprint=p.isSprinting()&&strong;
        int knock=EnchantmentManager.getEnchantmentLevel(Enchantments.KNOCKBACK,weapon)+(sprint?1:0);
        int fire=EnchantmentManager.getEnchantmentLevel(Enchantments.FIRE_ASPECT,weapon);
        boolean sweeping=strong&&!crit&&!sprint&&p.onGround&&(p.J-p.I)<p.cy()&&weapon.getItem() instanceof ItemSword;
        float health=target instanceof EntityLiving?((EntityLiving)target).getHealth():0;
        int previousFire=target.getBukkitEntity().getFireTicks();
        if(fire>0 && previousFire<=0) ignite(player,target,1,off);
        double mx=target.motX,my=target.motY,mz=target.motZ;
        boolean hit=damage(p,target,amount,off,false);
        if(!hit) {
            sound(player,Sound.ENTITY_PLAYER_ATTACK_NODAMAGE);
            if(previousFire<=0) target.getBukkitEntity().setFireTicks(previousFire);
            return;
        }
        if(knock>0) {
            if(target instanceof EntityLiving) ((EntityLiving)target).a(p,knock*.5f,Math.sin(Math.toRadians(p.yaw)),-Math.cos(Math.toRadians(p.yaw)));
            else target.f(-Math.sin(Math.toRadians(p.yaw))*knock*.5,.1,Math.cos(Math.toRadians(p.yaw))*knock*.5);
            p.motX*=.6; p.motZ*=.6;
            if(!settings.sprint) p.setSprinting(false);
        }
        if(sprint) sound(player,Sound.ENTITY_PLAYER_ATTACK_KNOCKBACK);
        if(sweeping) {
            for(EntityLiving other:p.world.a(EntityLiving.class,target.getBoundingBox().grow(1,.25,1))) {
                if(other==p||other==target||p.r(other)||p.h(other)>=9 || off&&!offTarget(player,other)) continue;
                // Unlike the original's mistaken primary-victim capability, each swept target owns its timer.
                if(damage(p,other,1,off,true)) other.a(p,.4f,Math.sin(Math.toRadians(p.yaw)),-Math.cos(Math.toRadians(p.yaw)));
            }
            sound(player,Sound.ENTITY_PLAYER_ATTACK_SWEEP);
        }
        if(sweeping) sweepParticle(player);
        if(target instanceof EntityPlayer && target.velocityChanged) {
            ((EntityPlayer)target).playerConnection.sendPacket(new PacketPlayOutEntityVelocity(target));
            target.velocityChanged=false; target.motX=mx; target.motY=my; target.motZ=mz;
        }
        if(crit) { p.a(target); if(off) sound(player,Sound.ENTITY_PLAYER_ATTACK_CRIT); }
        else if(!sweeping) sound(player,strong?Sound.ENTITY_PLAYER_ATTACK_STRONG:Sound.ENTITY_PLAYER_ATTACK_WEAK);
        if(ench>0) p.b(target);
        if(!weapon.isEmpty() && !(weapon.getItem() instanceof ItemSpade)) {
            if(settings.hitSound && (!settings.critSound||!crit)) customSound(player,"player.swordslash");
            if(settings.critSound && crit) customSound(player,"player.criticalstrike");
        }
        if(target instanceof EntityPlayer && weapon.getItem() instanceof ItemAxe) {
            EntityPlayer defender=(EntityPlayer)target;
            ItemStack shield=defender.isHandRaised()?defender.cJ():ItemStack.a;
            if(shield.getItem() instanceof ItemShield && random.nextFloat()<.25+.05*EnchantmentManager.getEnchantmentLevel(Enchantments.DIG_SPEED,weapon)+(sprint?.75:0)) {
                defender.getCooldownTracker().a(shield.getItem(),100); p.world.broadcastEntityEffect(defender,(byte)30);
            }
        }
        p.z(target);
        Entity durabilityTarget=target;
        if(target instanceof EntityComplexPart && ((EntityComplexPart)target).owner instanceof EntityLiving)
            durabilityTarget=(EntityLiving)((EntityComplexPart)target).owner;
        if(durabilityTarget instanceof EntityLiving) {
            EntityLiving living=(EntityLiving)durabilityTarget;
            // Use the selected hand for enchantment callbacks without losing any mutated/broken stack.
            ItemStack main=p.getItemInMainHand(),other=p.getItemInOffHand();
            try {
                if(off) { p.a(EnumHand.MAIN_HAND,other); p.a(EnumHand.OFF_HAND,main); }
                if(target instanceof EntityLiving) EnchantmentManager.a((EntityLiving)target,p);
                EnchantmentManager.b(p,target);
                weapon.a(living,p);
            } finally {
                if(off) { ItemStack changed=p.getItemInOffHand();p.a(EnumHand.OFF_HAND,p.getItemInMainHand());p.a(EnumHand.MAIN_HAND,changed); }
            }
            if(weapon.isEmpty()) p.a(off?EnumHand.OFF_HAND:EnumHand.MAIN_HAND,ItemStack.a);
            float delta=target instanceof EntityLiving?health-living.getHealth():0;
            if(delta>0) p.a(StatisticList.y,Math.round(delta*10));
            if(fire>0) ignite(player,target,fire*4,off);
            if(delta>2) ((WorldServer)p.world).sendParticles(p,EnumParticle.DAMAGE_INDICATOR,false,target.locX,target.locY+target.length*.5,target.locZ,(int)(delta*.5),.1,0,.1,.2);
        }
        p.applyExhaustion(.3f);
    }
    boolean damage(EntityPlayer p,Entity victim,float value,boolean off,boolean sweep) {
        DamageSource source=DamageSource.playerAttack(p);
        if(sweep) source.sweep();
        if(!off) return victim.damageEntity(source,value);
        if(!(victim instanceof EntityLiving) || offImmunity.getOrDefault(victim.getUniqueID(),0L)>tick) return false;
        EntityLiving v=(EntityLiving)victim;
        int hurt=v.hurtTicks, immunity=v.noDamageTicks; float last=v.lastDamage;
        ItemStack main=p.getItemInMainHand(),other=p.getItemInOffHand();
        try {
            p.a(EnumHand.MAIN_HAND,other); p.a(EnumHand.OFF_HAND,main);
            v.hurtTicks=0; v.noDamageTicks=0;
            boolean result=v.damageEntity(source,value);
            if(result) offImmunity.put(v.getUniqueID(),tick+10);
            return result;
        } finally {
            ItemStack changed=p.getItemInOffHand();p.a(EnumHand.OFF_HAND,p.getItemInMainHand());p.a(EnumHand.MAIN_HAND,changed);
            v.hurtTicks=hurt; v.noDamageTicks=immunity; v.lastDamage=last;
        }
    }
    private void ignite(Player player,Entity target,int seconds,boolean off) {
        EntityPlayer p=((CraftPlayer)player).getHandle();
        ItemStack main=p.getItemInMainHand(),other=p.getItemInOffHand();
        try {
            // Existing enchantment handlers inspect the attacking hand during combustion too.
            if(off) { p.a(EnumHand.MAIN_HAND,other); p.a(EnumHand.OFF_HAND,main); }
            EntityCombustByEntityEvent event=new EntityCombustByEntityEvent(player,target.getBukkitEntity(),seconds);
            Bukkit.getPluginManager().callEvent(event);
            if(!event.isCancelled()) target.setOnFire(event.getDuration());
        } finally {
            if(off) { ItemStack changed=p.getItemInOffHand();p.a(EnumHand.OFF_HAND,p.getItemInMainHand());p.a(EnumHand.MAIN_HAND,changed); }
        }
    }
    private void sound(Player p,Sound s) { p.getWorld().playSound(p.getLocation(),s,1,1); }
    private void customSound(Player p,String s) { p.getWorld().playSound(p.getLocation(),"minecraft:jaspr.bettercombat."+s,1,1); }
    private void sweepParticle(Player p) {
        double x=-Math.sin(Math.toRadians(p.getLocation().getYaw())),z=Math.cos(Math.toRadians(p.getLocation().getYaw()));
        Location l=p.getLocation().add(x,((CraftPlayer)p).getHandle().length*.5,z);
        p.getWorld().spawnParticle(org.bukkit.Particle.SWEEP_ATTACK,l,0,x,0,z);
    }
    private void sync(Player player,State state) {
        EntityPlayer p=((CraftPlayer)player).getHandle();
        JsonObject data=new JsonObject(); data.addProperty("v",1); data.addProperty("enabled",settings.enabled);
        data.addProperty("range",settings.longer?5:4); data.addProperty("width",settings.wider?settings.width:.5);
        data.addProperty("refund",settings.refund); data.addProperty("full",settings.full);
        data.addProperty("dual",settings.offhand);
        data.addProperty("motion",motion(player));
        data.addProperty("off",settings.offhand&&offWeapon(p.getItemInOffHand()));
        data.addProperty("main",!specialUse(p.getItemInMainHand()));
        data.addProperty("use",p.getItemInMainHand().getItemUseMaxDuration()>0);
        data.addProperty("mainItem",Item.getId(p.getItemInMainHand().getItem()));
        data.addProperty("offItem",Item.getId(p.getItemInOffHand().getItem()));
        data.addProperty("remaining",Math.max(0,state.offUntil-tick)); data.addProperty("period",offPeriod(p));
        data.addProperty("offSwing",state.offSwing);
        String json=data.toString();
        if(json.equals(state.lastState)&&tick-state.lastSync<20)return;
        state.lastState=json;state.lastSync=tick;
        // Feature negotiation is explicit; Eagler clients do not register every Bukkit channel.
        p.playerConnection.sendPacket(new PacketPlayOutCustomPayload(CHANNEL,
                new PacketDataSerializer(io.netty.buffer.Unpooled.wrappedBuffer(Wire.encode(json)))));
    }
    @Override public boolean onCommand(CommandSender s,Command c,String label,String[] args) {
        if(args.length==1 && args[0].equalsIgnoreCase("settings") && s instanceof Player) {
            if(authenticated((Player)s)) configMenu.open((Player)s);
            return true;
        }
        if(args.length==1 && args[0].equalsIgnoreCase("reload")) {
            if(!s.hasPermission("jasprbettercombat.admin")) { s.sendMessage("Administrator permission required."); return true; }
            reloadConfig(); settings=new Settings(getConfig()); s.sendMessage("Better Combat configuration reloaded."); return true;
        }
        s.sendMessage("Better Combat Rebirth 1.5.6 port: "+(settings.enabled?"enabled":"disabled")+"; reach "+(settings.longer?5:4)+"; off-hand "+settings.offhand+".");
        s.sendMessage("Left click: main hand. Right click with a second melee weapon: off hand. F swaps hands."); return true;
    }
}
