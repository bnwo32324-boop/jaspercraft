package chat.jaspr.enchant.fx;

import chat.jaspr.enchant.E;
import chat.jaspr.enchant.util.Guard;
import chat.jaspr.enchant.util.Nms;
import com.destroystokyo.paper.event.entity.EntityAddToWorldEvent;
import com.destroystokyo.paper.event.player.PlayerPickupExperienceEvent;
import net.minecraft.server.v1_12_R1.EnchantmentManager;
import net.minecraft.server.v1_12_R1.EntityExperienceOrb;
import net.minecraft.server.v1_12_R1.EntityHuman;
import net.minecraft.server.v1_12_R1.EntityItem;
import net.minecraft.server.v1_12_R1.EntityLiving;
import net.minecraft.server.v1_12_R1.EntityPlayer;
import net.minecraft.server.v1_12_R1.ItemStack;
import org.bukkit.GameMode;
import org.bukkit.entity.ExperienceOrb;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.ItemSpawnEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerItemDamageEvent;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Rusted and Rune: Revival (SME ItemStackMixin), Curse of Possession, Curse of Decay (Forge
 * EntityItem.lifespan emulated through the item's age), Advanced Mending (PlayerPickupXpEvent).
 */
public final class ItemsFx implements Listener {

    /** ItemStack.damageItem with SME's Rusted/Revival mixins, for any living holder. */
    public static void damageItem(ItemStack stack, int amount, EntityLiving entity) {
        if (stack == null || stack.isEmpty()) return;
        if (entity instanceof EntityPlayer) {
            // PlayerItemDamageEvent below applies Rusted and Revival
            stack.damage(amount, entity);
            return;
        }
        if (!stack.f()) return;
        amount = rusted(stack, amount);
        Random rand = entity.getRandom();
        boolean broken = stack.isDamaged(amount, rand, null);
        if (broken && revival(stack, rand)) broken = false;
        if (broken) {
            entity.b(stack);
            stack.subtract(1);
            stack.setData(0);
        }
    }

    static int rusted(ItemStack stack, int amount) {
        if (amount > 0) {
            int level = Nms.level(E.RUSTED, stack);
            if (level > 0) return amount * (level + 1);
        }
        return amount;
    }

    /** returns true when Revival saves a broken item (item damage already set to the saved value) */
    static boolean revival(ItemStack instance, Random rand) {
        int level = Nms.level(E.RUNE_REVIVAL, instance);
        if (level <= 0) return false;
        int durability = instance.k();
        float extraChance = durability > 1250 ? 0 : durability > 750 ? 4 : durability > 200 ? 6 : durability > 80 ? 8 : 10;
        extraChance = extraChance / 100F;
        boolean shouldSave = rand.nextFloat() < (0.15F + ((float) level * 0.15F)) + extraChance;
        if (!shouldSave) return false;
        instance.setData(Math.max(0, instance.k() - (int) ((float) instance.k() * (0.25F + ((float) level * rand.nextFloat() / 3.0F)))));
        return true;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerItemDamage(PlayerItemDamageEvent e) {
        Guard.run("rusted_revival", () -> {
            ItemStack stack = Nms.nms(e.getItem());
            if (stack.isEmpty()) return;
            int dmg = rusted(stack, e.getDamage());
            e.setDamage(dmg);
            if (Nms.level(E.RUNE_REVIVAL, stack) <= 0) return;
            if (stack.getData() + dmg <= stack.k()) return; // not breaking
            EntityHuman player = (EntityHuman) Nms.living(e.getPlayer());
            Random rand = player.getRandom();
            // vanilla applies the damage first, then SME's WrapOperation decides whether the break is saved
            stack.setData(stack.getData() + dmg);
            if (revival(stack, rand)) {
                e.setCancelled(true);
                Fx.customSound(player, "rune_revival", 2F, 0.8F + 0.4F * rand.nextFloat());
            } else {
                stack.setData(stack.getData() - dmg);
            }
        });
    }

    // ------------------------------------------------------------------ Possession / Decay

    private static int despawnRate(org.bukkit.World w) {
        try {
            return ((org.bukkit.craftbukkit.v1_12_R1.CraftWorld) w).getHandle().spigotConfig.itemDespawnRate;
        } catch (Throwable t) {
            return 6000;
        }
    }

    /** Forge EntityItem.lifespan = n: despawn once age reaches n. */
    static void setLifespan(Item item, int lifespan) {
        EntityItem ei = (EntityItem) Nms.entity(item);
        if (ei == null) return;
        int rate = despawnRate(item.getWorld());
        int age = Nms.itemAge(ei);
        Nms.setItemAge(ei, rate - lifespan + Math.max(0, age));
    }

    /** CurseofPossession.onItemTossEvent (HIGHEST) */
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onToss(PlayerDropItemEvent e) {
        Guard.run("curseofpossession.toss", () -> {
            Player p = e.getPlayer();
            if (p.getGameMode() == GameMode.CREATIVE) return;
            Item drop = e.getItemDrop();
            ItemStack orig = Nms.nms(drop.getItemStack());
            if (orig.isEmpty() || Nms.level(E.CURSEOFPOSSESSION, orig) <= 0) return;
            HashMap<Integer, org.bukkit.inventory.ItemStack> left = p.getInventory().addItem(drop.getItemStack().clone());
            if (left.isEmpty()) {
                drop.remove(); // dead item is not added to the world
                return;
            }
            // SME: entityDropItem at eye height - 0.3, owner-only, no pickup delay, invulnerable
            EntityItem ei = (EntityItem) Nms.entity(drop);
            EntityHuman h = (EntityHuman) Nms.living(p);
            ei.setPosition(h.locX, h.locY + (double) (h.getHeadHeight() - 0.3F), h.locZ);
            Random r = h.getRandom();
            drop.setVelocity(new Vector(r.nextDouble() * 0.2D - 0.1D, 0.2D, r.nextDouble() * 0.2D - 0.1D));
            ei.d(h.getName());
            drop.setPickupDelay(0);
            drop.setInvulnerable(true);
        });
    }

    /** CurseofPossession.onEntityJoinWorldEvent (HIGHEST) and CurseofDecay.onEntityJoinWorld (LOW) */
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onItemSpawn(ItemSpawnEvent e) {
        final Item item = e.getEntity();
        if (item.isDead()) return;
        Guard.run("curseofpossession.join", () -> {
            ItemStack copy = Nms.nms(item.getItemStack());
            if (copy.isEmpty() || Nms.level(E.CURSEOFPOSSESSION, copy) <= 0) return;
            EntityItem ei = (EntityItem) Nms.entity(item);
            EntityHuman player = ei.world.findNearbyPlayer(ei, 8.0);
            if (player != null && !player.abilities.canInstantlyBuild) {
                if (player.isAlive()) {
                    ItemStack give = copy.cloneItemStack();
                    if (player.inventory.pickup(give)) {
                        e.setCancelled(true);
                        return;
                    }
                }
                setLifespan(item, 5); // curseOfPossessionDeathDeletion = true
                item.setPickupDelay(10);
            }
        });
        if (e.isCancelled()) return;
        Guard.run("curseofdecay.join", () -> decay(item));
    }

    static void decay(Item item) {
        ItemStack s = Nms.nms(item.getItemStack());
        if (s.isEmpty() || Nms.level(E.CURSEOFDECAY, s) <= 0) return;
        if (item.getScoreboardTags().contains("sme_decay")) return;
        item.addScoreboardTag("sme_decay");
        setLifespan(item, 80);
        item.setPickupDelay(10);
    }

    /** items loaded from chunks also pass EntityJoinWorldEvent in Forge */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onAddToWorld(EntityAddToWorldEvent e) {
        if (!(e.getEntity() instanceof Item)) return;
        Guard.run("curseofdecay.load", () -> decay((Item) e.getEntity()));
    }

    // ------------------------------------------------------------------ Advanced Mending

    /** PlayerPickupXpEvent: repair Advanced Mending items first (prioritize damaged, 1.5x ratio). */
    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onPickupXp(PlayerPickupExperienceEvent e) {
        Guard.run("advancedmending", () -> {
            ExperienceOrb orbB = e.getExperienceOrb();
            EntityExperienceOrb orb = (EntityExperienceOrb) Nms.entity(orbB);
            EntityHuman player = (EntityHuman) Nms.living(e.getPlayer());
            if (orb == null || player == null || orb.value <= 0) return;
            List<ItemStack> damaged = new ArrayList<>();
            for (ItemStack s : E.ADVANCEDMENDING.a(player)) {
                if (!s.isEmpty() && EnchantmentManager.getEnchantmentLevel(E.ADVANCEDMENDING, s) > 0 && s.h()) damaged.add(s);
            }
            if (damaged.isEmpty()) return;
            ItemStack stack = damaged.get(player.getRandom().nextInt(damaged.size()));
            if (!stack.isEmpty() && stack.h()) {
                float ratio = 2.0F; // Item.getXpRepairRatio default
                int value = Math.min(roundAverage(orb.value * ratio * 1.5F), stack.i());
                orb.value -= roundAverage(value / ratio);
                stack.setData(stack.i() - value);
                if (orb.value < 0) orb.value = 0;
            }
        });
    }

    private static int roundAverage(float value) {
        double floor = Math.floor(value);
        return (int) floor + (Math.random() < value - floor ? 1 : 0);
    }

    static Map<String, Integer> debugCounts() {
        return new HashMap<>();
    }
}
