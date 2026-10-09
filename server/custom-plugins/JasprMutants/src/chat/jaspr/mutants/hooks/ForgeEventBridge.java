package chat.jaspr.mutants.hooks;

import chumbanotz.mutantbeasts.item.MBItems;
import java.lang.reflect.Field;
import java.util.logging.Logger;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.Item;
import net.minecraft.item.ItemBow;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumHand;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.item.ItemTossEvent;
import net.minecraftforge.event.entity.living.LivingDropsEvent;
import net.minecraftforge.event.entity.living.LivingEntityUseItemEvent;
import net.minecraftforge.event.entity.player.ArrowLooseEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import org.bukkit.GameMode;
import org.bukkit.craftbukkit.v1_12_R1.entity.CraftEntity;
import org.bukkit.craftbukkit.v1_12_R1.entity.CraftItem;
import org.bukkit.craftbukkit.v1_12_R1.entity.CraftLivingEntity;
import org.bukkit.craftbukkit.v1_12_R1.entity.CraftPlayer;
import org.bukkit.craftbukkit.v1_12_R1.inventory.CraftItemStack;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.EquipmentSlot;

/**
 * The Forge events the mod subscribes to (chumbanotz.mutantbeasts.EventHandler), posted on the shim's EVENT_BUS from
 * the Bukkit event (or tick) that fires at the same point of the game, plus the Forge item hooks vanilla Paper never
 * calls. Each mapping keeps Forge's order and result; PORT_NOTES.md lists them.
 * - PlayerInteractEvent.EntityInteract: PlayerInteractEntityEvent (both fire before Entity.processInitialInteract;
 *   Forge's cancel skips the interaction, here the Bukkit event is cancelled).
 * - LivingDropsEvent: EntityDeathEvent (cancelled = no item drops, experience unchanged, as Forge).
 * - ItemTossEvent: PlayerDropItemEvent (the dropped EntityItem before it joins the world).
 * - ArrowLooseEvent: EntityShootBowEvent of a player (Forge fires before the bow shoots; a cancelled Bukkit event also
 *   ends ItemBow.onPlayerStoppedUsing before any damage, ammo or stat). The charge is recovered from the event's force.
 * - LivingEntityUseItemEvent.Tick: once per tick for each player using an item (Forge: EntityLivingBase.updateActiveHand).
 * - TickEvent.PlayerTickEvent: START and END for every player each tick (Forge fires both from EntityPlayer.onUpdate).
 * - ExplosionEvent.Detonate of vanilla explosions: the mod removes Creeper Shard items from the affected entities; on
 *   CraftBukkit an explosion skips an entity whose damage event is cancelled (no damage, no knockback).
 * - Item.canDestroyBlockInCreative (Creeper Shard, Endersoul Hand): creative block breaking is refused like a sword's.
 * - Item.canDisableShield (Hulk Hammer): a blocked hit disables the player's shield (EntityPlayer.disableShield(true)).
 */
public final class ForgeEventBridge implements Listener {
    private static Logger log = Logger.getLogger("JasprMutants");
    private static Field useCountField;
    private static int failures;
    private long posted;

    public static void setLogger(Logger logger) {
        log = logger;
    }

    public long postedCount() {
        return this.posted;
    }

    private void fail(String hook, Throwable e) {
        if (failures++ < 20) log.warning("MUTANTS_HOOK_FAILED hook=" + hook + " reason=" + e.getClass().getSimpleName());
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onInteractEntity(PlayerInteractEntityEvent event) {
        if (event.getClass() != PlayerInteractEntityEvent.class) return; // INTERACT_AT is Forge's EntityInteractSpecific
        try {
            EntityPlayer player = ((CraftPlayer) event.getPlayer()).getHandle();
            EnumHand hand = event.getHand() == EquipmentSlot.OFF_HAND ? EnumHand.OFF_HAND : EnumHand.MAIN_HAND;
            PlayerInteractEvent.EntityInteract forge = new PlayerInteractEvent.EntityInteract(player, hand, ((CraftEntity) event.getRightClicked()).getHandle());
            this.posted++;
            if (MinecraftForge.EVENT_BUS.post(forge)) event.setCancelled(true);
        } catch (RuntimeException e) {
            this.fail("entity_interact", e);
        }
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onDeath(EntityDeathEvent event) {
        if (event.getDrops().isEmpty()) return;
        try {
            EntityLivingBase entity = ((CraftLivingEntity) event.getEntity()).getHandle();
            net.minecraft.util.DamageSource source = entity.getLastDamageSource();
            if (source == null) source = net.minecraft.util.DamageSource.GENERIC;
            LivingDropsEvent forge = new LivingDropsEvent(entity, source, new java.util.ArrayList<EntityItem>(), 0, entity.getRevengeTarget() != null);
            this.posted++;
            if (MinecraftForge.EVENT_BUS.post(forge)) event.getDrops().clear();
        } catch (RuntimeException e) {
            this.fail("living_drops", e);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onToss(PlayerDropItemEvent event) {
        try {
            EntityItem item = (EntityItem) ((CraftItem) event.getItemDrop()).getHandle();
            ItemTossEvent forge = new ItemTossEvent(item, ((CraftPlayer) event.getPlayer()).getHandle());
            this.posted++;
            if (MinecraftForge.EVENT_BUS.post(forge)) event.setCancelled(true);
        } catch (RuntimeException e) {
            this.fail("item_toss", e);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onShootBow(EntityShootBowEvent event) {
        if (!(event.getEntity() instanceof Player) || event.getBow() == null) return;
        try {
            EntityPlayer player = ((CraftPlayer) event.getEntity()).getHandle();
            ItemStack bow = CraftItemStack.asNMSCopy(event.getBow());
            ItemStack held = player.getHeldItem(player.getActiveHand() == null ? EnumHand.MAIN_HAND : player.getActiveHand());
            if (held.getItem() == bow.getItem()) bow = held; // the real stack, so damageItem reaches it as on Forge
            ArrowLooseEvent forge = new ArrowLooseEvent(player, bow, player.world, chargeOf(event.getForce()), true);
            this.posted++;
            if (MinecraftForge.EVENT_BUS.post(forge)) event.setCancelled(true);
        } catch (RuntimeException e) {
            this.fail("arrow_loose", e);
        }
    }

    /** The use ticks i with ItemBow.getArrowVelocity(i) == force (the inverse of (f*f + 2f) / 3, f = i / 20). */
    static int chargeOf(float force) {
        if (force >= 1.0F) return 20;
        int best = 0;
        for (int i = 0; i <= 20; i++) {
            if (Math.abs(ItemBow.getArrowVelocity(i) - force) < Math.abs(ItemBow.getArrowVelocity(best) - force)) best = i;
        }
        return best;
    }

    /** Explosions skip Creeper Shard items (the mod's ExplosionEvent.Detonate listener). */
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        EntityDamageEvent.DamageCause cause = event.getCause();
        if (cause != EntityDamageEvent.DamageCause.ENTITY_EXPLOSION && cause != EntityDamageEvent.DamageCause.BLOCK_EXPLOSION) return;
        if (!(event.getEntity() instanceof CraftItem)) return;
        EntityItem item = (EntityItem) ((CraftItem) event.getEntity()).getHandle();
        if (item.getItem().getItem() == MBItems.CREEPER_SHARD) event.setCancelled(true);
    }

    /** Item.canDestroyBlockInCreative: false for the Creeper Shard and the Endersoul Hand (Forge's default: not a sword). */
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onCreativeBreak(BlockBreakEvent event) {
        if (event.getPlayer().getGameMode() != GameMode.CREATIVE) return;
        Item held = ((CraftPlayer) event.getPlayer()).getHandle().getHeldItemMainhand().getItem();
        if (held == MBItems.CREEPER_SHARD || held == MBItems.ENDERSOUL_HAND) event.setCancelled(true);
    }

    /** Item.canDisableShield (Hulk Hammer): Forge's EntityPlayer.blockUsingShield then calls disableShield(true). */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockedHit(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player) || !(event.getDamager() instanceof CraftLivingEntity)) return;
        if (event.getDamage(EntityDamageEvent.DamageModifier.BLOCKING) >= 0.0D) return;
        try {
            EntityPlayer victim = ((CraftPlayer) event.getEntity()).getHandle();
            EntityLivingBase attacker = ((CraftLivingEntity) event.getDamager()).getHandle();
            ItemStack weapon = attacker.getHeldItemMainhand();
            if (weapon.getItem() == MBItems.HULK_HAMMER && chumbanotz.mutantbeasts.MBConfig.ITEMS.hulkHammerDisablesShields) {
                victim.disableShield(true);
            }
        } catch (RuntimeException e) {
            this.fail("disable_shield", e);
        }
    }

    /** Once per server tick: PlayerTickEvent START/END and the item-use tick of every player. */
    public void tick(Iterable<? extends Player> players) {
        for (Player p : players) {
            EntityPlayerMP player = ((CraftPlayer) p).getHandle();
            try {
                MinecraftForge.EVENT_BUS.post(new TickEvent.PlayerTickEvent(TickEvent.Phase.START, player));
                MinecraftForge.EVENT_BUS.post(new TickEvent.PlayerTickEvent(TickEvent.Phase.END, player));
                this.posted += 2;
                ItemStack active = player.getActiveItemStack();
                if (!active.isEmpty()) {
                    int count = player.getItemInUseCount();
                    LivingEntityUseItemEvent.Tick forge = new LivingEntityUseItemEvent.Tick(player, active, count);
                    MinecraftForge.EVENT_BUS.post(forge);
                    this.posted++;
                    if (forge.getDuration() != count) setUseCount(player, forge.getDuration());
                }
            } catch (RuntimeException | ReflectiveOperationException e) {
                this.fail("player_tick", e);
            }
        }
    }

    private static void setUseCount(EntityLivingBase entity, int value) throws ReflectiveOperationException {
        if (useCountField == null) {
            Field f = EntityLivingBase.class.getDeclaredField("bp"); // Spigot name of activeItemStackUseCount
            f.setAccessible(true);
            useCountField = f;
        }
        useCountField.setInt(entity, value);
    }
}
