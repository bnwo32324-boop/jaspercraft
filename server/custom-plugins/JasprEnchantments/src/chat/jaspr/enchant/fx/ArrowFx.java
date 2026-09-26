package chat.jaspr.enchant.fx;

import chat.jaspr.enchant.E;
import chat.jaspr.enchant.util.Guard;
import chat.jaspr.enchant.util.Nms;
import com.destroystokyo.paper.event.entity.EntityKnockbackByEntityEvent;
import net.minecraft.server.v1_12_R1.Enchantments;
import net.minecraft.server.v1_12_R1.EntityArrow;
import net.minecraft.server.v1_12_R1.EntityHuman;
import net.minecraft.server.v1_12_R1.EntityLiving;
import net.minecraft.server.v1_12_R1.ItemArrow;
import net.minecraft.server.v1_12_R1.ItemBow;
import net.minecraft.server.v1_12_R1.ItemStack;
import net.minecraft.server.v1_12_R1.Items;
import net.minecraft.server.v1_12_R1.MathHelper;
import net.minecraft.server.v1_12_R1.MinecraftServer;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;

import java.lang.reflect.Method;
import java.util.Iterator;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.WeakHashMap;

import static chat.jaspr.enchant.fx.Pipeline.LOWEST;
import static chat.jaspr.enchant.fx.Pipeline.NORMAL;

/**
 * SME ArrowPropertiesHandler (capability -> scoreboard tags on the arrow), Split Shot (ArrowLooseEvent),
 * Curse of Inaccuracy (EntityArrowMixin on shoot(Entity,...)), Strafe's iframe reset.
 */
public final class ArrowFx implements Listener {
    private static final Random RANDOM = new Random();
    public static long arrowsTagged = 0, splitArrows = 0;

    static final String HANDLED = "sme_arrow", RESET = "sme_reset", FLAME = "sme_flame:", PIERCE = "sme_pierce:", DRAG = "sme_drag:";

    static int intTag(Set<String> tags, String prefix) {
        for (String t : tags) if (t.startsWith(prefix)) {
            try {
                return Integer.parseInt(t.substring(prefix.length()));
            } catch (NumberFormatException ignored) {
            }
        }
        return 0;
    }

    static float floatTag(Set<String> tags, String prefix) {
        for (String t : tags) if (t.startsWith(prefix)) {
            try {
                return Float.parseFloat(t.substring(prefix.length()));
            } catch (NumberFormatException ignored) {
            }
        }
        return 0.0F;
    }

    static ItemStack heldBow(EntityLiving e) {
        ItemStack bow = e.getItemInMainHand();
        if (!(bow.getItem() instanceof ItemBow)) bow = e.getItemInOffHand();
        if (!(bow.getItem() instanceof ItemBow)) return ItemStack.a;
        return bow;
    }

    /** ArrowPropertiesHandler.setArrowEnchantmentsFromStack */
    static void apply(ItemStack bow, EntityArrow arrow) {
        Set<String> tags = arrow.getScoreboardTags();
        int powerless = Nms.level(E.POWERLESS, bow);
        if (powerless > 0) {
            arrow.c(arrow.k() - 0.5D - powerless * 0.5D);
            if (powerless > 2 || RANDOM.nextFloat() < powerless * 0.4F) arrow.setCritical(false);
        }
        int advPower = Nms.level(E.ADVANCEDPOWER, bow);
        if (advPower > 0) {
            arrow.c(arrow.k() + 1.25D + (double) advPower * 0.75D);
            if (advPower >= 4 || RANDOM.nextFloat() < advPower * 0.25F) arrow.setCritical(true);
        }
        int advPunch = Nms.level(E.ADVANCEDPUNCH, bow);
        if (advPunch > 0) arrow.setKnockbackStrength(1 + advPunch * 2);
        int pierce = Nms.level(E.RUNE_ARROWPIERCING, bow);
        if (pierce > 0) tags.add(PIERCE + pierce);
        int drag = Nms.level(E.DRAGGING, bow);
        if (drag > 0) tags.add(DRAG + (1.25F + drag * 1.75F));
        int strafe = Nms.level(E.STRAFE, bow);
        if (RANDOM.nextFloat() < 0.125F * strafe) tags.add(RESET);
        int flame = 0;
        if (Nms.level(E.LESSERFLAME, bow) > 0) flame = 1;
        if (Nms.level(E.ADVANCEDFLAME, bow) > 0) {
            arrow.setOnFire(200);
            flame = 2;
        }
        if (Nms.level(E.SUPREMEFLAME, bow) > 0) {
            arrow.setOnFire(400);
            flame = 3;
        }
        if (Nms.level(E.EXTINGUISH, bow) > 0) {
            arrow.extinguish();
            flame = -1;
        }
        if (flame != 0) tags.add(FLAME + flame);
        tags.add(HANDLED);
        arrowsTagged++;
    }

    /** EntityJoinWorldEvent (HIGH): every arrow shot by a living entity holding a bow */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onLaunch(ProjectileLaunchEvent e) {
        if (!(e.getEntity() instanceof Arrow)) return;
        Guard.run("arrow.properties", () -> {
            EntityArrow arrow = (EntityArrow) Nms.entity(e.getEntity());
            if (arrow == null || !(arrow.shooter instanceof EntityLiving)) return;
            if (arrow.getScoreboardTags().contains(HANDLED)) return;
            ItemStack bow = heldBow((EntityLiving) arrow.shooter);
            if (bow.isEmpty()) return;
            apply(bow, arrow);
            if (Pipeline.debug) chat.jaspr.enchant.util.Log.info("SME_DEBUG arrow shooter=" + arrow.shooter.getBukkitEntity().getType()
                    + " damage=" + arrow.k() + " crit=" + arrow.isCritical() + " knockback=" + arrow.knockbackStrength + " tags=" + arrow.getScoreboardTags()
                    + " speed=" + Math.sqrt(arrow.motX * arrow.motX + arrow.motY * arrow.motY + arrow.motZ * arrow.motZ));
        });
    }

    // ------------------------------------------------------------------ inaccuracy + split shot

    /** EntityArrowMixin: shoot(Entity,...) with Curse of Inaccuracy */
    static void shoot(EntityArrow arrow, EntityLiving shooter, float pitch, float yaw, float velocity, float inaccuracy) {
        ItemStack bow = heldBow(shooter);
        int level = bow.isEmpty() ? 0 : Nms.level(E.CURSEOFINACCURACY, bow);
        if (level > 0 && shooter.getRandom().nextFloat() < ((float) level * 0.20F)) inaccuracy += (float) level * 10.0F;
        arrow.a(shooter, pitch, yaw, 0.0F, velocity, inaccuracy);
    }

    private static Method findAmmo;

    static ItemStack findAmmo(ItemBow bowItem, EntityHuman player, ItemStack bow) throws Exception {
        if (findAmmo == null) {
            findAmmo = ItemBow.class.getDeclaredMethod("a", EntityHuman.class, ItemStack.class);
            findAmmo.setAccessible(true);
        }
        return (ItemStack) findAmmo.invoke(bowItem, player, bow);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onShoot(EntityShootBowEvent e) {
        if (!(e.getEntity() instanceof Player)) return;
        Guard.run("splitshot_inaccuracy", () -> {
            EntityHuman player = (EntityHuman) Nms.living(e.getEntity());
            ItemStack bow = Nms.nms(e.getBow());
            if (!(bow.getItem() instanceof ItemBow)) return;
            float f = e.getForce();
            // Split Shot (ArrowLooseEvent LOW, runs before the vanilla arrow is spawned)
            int level = Nms.level(E.SPLITSHOT, bow);
            for (int x = 0; x < level; x++) {
                boolean flag = player.abilities.canInstantlyBuild || Nms.level(Enchantments.ARROW_INFINITE, bow) > 0;
                ItemStack ammo = findAmmo((ItemBow) bow.getItem(), player, bow);
                if (ammo.isEmpty() && !flag) continue;
                if (ammo.isEmpty()) ammo = new ItemStack(Items.ARROW);
                if ((double) f >= 0.1D) {
                    ItemArrow itemarrow = (ItemArrow) (ammo.getItem() instanceof ItemArrow ? ammo.getItem() : Items.ARROW);
                    EntityArrow arrow = itemarrow.a(player.world, ammo, player);
                    shoot(arrow, player, player.pitch, player.yaw, f * 3.0F, 3.0F + player.getRandom().nextFloat() * 6.0F);
                    if (f == 1.0F) arrow.setCritical(true);
                    int j = Nms.level(Enchantments.ARROW_DAMAGE, bow);
                    if (j > 0) arrow.c(arrow.k() + (double) j * 0.5D + 0.5D);
                    int k = Nms.level(Enchantments.ARROW_KNOCKBACK, bow);
                    if (k > 0) arrow.setKnockbackStrength(k);
                    if (Nms.level(Enchantments.ARROW_FIRE, bow) > 0) arrow.setOnFire(100);
                    bow.damage(1, player);
                    arrow.fromPlayer = EntityArrow.PickupStatus.CREATIVE_ONLY;
                    player.world.addEntity(arrow);
                    splitArrows++;
                }
            }
            // Curse of Inaccuracy on the vanilla arrow
            if (e.getProjectile() instanceof Arrow) {
                int inacc = Nms.level(E.CURSEOFINACCURACY, bow);
                if (inacc > 0 && player.getRandom().nextFloat() < ((float) inacc * 0.20F)) {
                    EntityArrow va = (EntityArrow) Nms.entity(e.getProjectile());
                    va.a(player, player.pitch, player.yaw, 0.0F, f * 3.0F, 1.0F + (float) inacc * 10.0F);
                }
            }
        });
    }

    // ------------------------------------------------------------------ hit handlers

    private static final WeakHashMap<EntityLiving, Integer> RESET_PENDING = new WeakHashMap<>();

    public static void register() {
        // onArrowHitHurt (LivingHurtEvent LOWEST, registered before the enchantment handlers)
        Pipeline.hurt(LOWEST, -1, "arrow.piercing", c -> {
            if (!(c.immediate instanceof EntityArrow) || !"arrow".equals(c.source.translationIndex)) return;
            int pierce = intTag(c.immediate.getScoreboardTags(), PIERCE);
            if (pierce > 0 && c.source instanceof net.minecraft.server.v1_12_R1.EntityDamageSource) {
                float cur = Ctx.piercing(c.source);
                Ctx.setPiercing(c.source, Math.min(cur + 0.25F * (float) pierce, 1.0F));
            }
        });
        // onArrowHitDamage (LivingDamageEvent NORMAL)
        Pipeline.damage(NORMAL, -1, "arrow.damage", c -> {
            if (!(c.immediate instanceof EntityArrow) || !"arrow".equals(c.source.translationIndex)) return;
            EntityArrow arrow = (EntityArrow) c.immediate;
            Set<String> tags = arrow.getScoreboardTags();
            if (!tags.contains(HANDLED)) return;
            EntityLiving victim = c.victim;
            int flame = intTag(tags, FLAME);
            int secs = flame == 1 ? 2 : flame == 2 ? 15 : flame == 3 ? 30 : 0;
            if (secs > 0) FireFx.setFire(victim, secs);
            if (flame == -1) victim.extinguish();
            float drag = floatTag(tags, DRAG);
            if (drag > 0) {
                double m = -0.6F * drag / MathHelper.sqrt(arrow.motX * arrow.motX + arrow.motZ * arrow.motZ);
                victim.f(arrow.motX * m, 0.1, arrow.motZ * m);
                victim.velocityChanged = true;
            }
            if (tags.contains(RESET)) {
                victim.noDamageTicks = 0;
                RESET_PENDING.put(victim, MinecraftServer.currentTick);
            }
        });
    }

    /** CraftBukkit sets noDamageTicks after the damage event; the standard knockback that follows is the first hook after it. */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onKnockback(EntityKnockbackByEntityEvent e) {
        if (RESET_PENDING.isEmpty()) return;
        EntityLiving v = Nms.living(e.getEntity());
        Integer t = v == null ? null : RESET_PENDING.remove(v);
        if (t != null && t == MinecraftServer.currentTick) v.noDamageTicks = 0;
    }

    /** a second arrow in the same tick (split shot) must see the reset before its own damage */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onHit(ProjectileHitEvent e) {
        if (RESET_PENDING.isEmpty() || e.getHitEntity() == null) return;
        EntityLiving v = Nms.living(e.getHitEntity());
        Integer t = v == null ? null : RESET_PENDING.remove(v);
        if (t != null && t == MinecraftServer.currentTick) v.noDamageTicks = 0;
    }

    public static void tick() {
        if (RESET_PENDING.isEmpty()) return;
        for (Iterator<Map.Entry<EntityLiving, Integer>> it = RESET_PENDING.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<EntityLiving, Integer> en = it.next();
            it.remove();
            if (en.getValue() >= MinecraftServer.currentTick - 1) en.getKey().noDamageTicks = 0;
        }
    }
}
