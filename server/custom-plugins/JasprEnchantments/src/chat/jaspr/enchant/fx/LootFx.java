package chat.jaspr.enchant.fx;

import chat.jaspr.enchant.E;
import chat.jaspr.enchant.util.Guard;
import chat.jaspr.enchant.util.Nms;
import net.minecraft.server.v1_12_R1.DamageSource;
import net.minecraft.server.v1_12_R1.EnchantmentManager;
import net.minecraft.server.v1_12_R1.Enchantments;
import net.minecraft.server.v1_12_R1.EntityAnimal;
import net.minecraft.server.v1_12_R1.EntityBlaze;
import net.minecraft.server.v1_12_R1.EntityCaveSpider;
import net.minecraft.server.v1_12_R1.EntityChicken;
import net.minecraft.server.v1_12_R1.EntityCow;
import net.minecraft.server.v1_12_R1.EntityCreeper;
import net.minecraft.server.v1_12_R1.EntityEnderDragon;
import net.minecraft.server.v1_12_R1.EntityEnderman;
import net.minecraft.server.v1_12_R1.EntityFishingHook;
import net.minecraft.server.v1_12_R1.EntityGhast;
import net.minecraft.server.v1_12_R1.EntityHuman;
import net.minecraft.server.v1_12_R1.EntityIronGolem;
import net.minecraft.server.v1_12_R1.EntityLiving;
import net.minecraft.server.v1_12_R1.EntityMagmaCube;
import net.minecraft.server.v1_12_R1.EntityMushroomCow;
import net.minecraft.server.v1_12_R1.EntityOcelot;
import net.minecraft.server.v1_12_R1.EntityPig;
import net.minecraft.server.v1_12_R1.EntityPigZombie;
import net.minecraft.server.v1_12_R1.EntitySheep;
import net.minecraft.server.v1_12_R1.EntitySkeleton;
import net.minecraft.server.v1_12_R1.EntitySkeletonWither;
import net.minecraft.server.v1_12_R1.EntitySlime;
import net.minecraft.server.v1_12_R1.EntitySpider;
import net.minecraft.server.v1_12_R1.EntitySquid;
import net.minecraft.server.v1_12_R1.EntityVillager;
import net.minecraft.server.v1_12_R1.EntityWither;
import net.minecraft.server.v1_12_R1.EntityZombie;
import net.minecraft.server.v1_12_R1.GenericAttributes;
import net.minecraft.server.v1_12_R1.ItemFishingRod;
import net.minecraft.server.v1_12_R1.ItemStack;
import net.minecraft.server.v1_12_R1.Items;
import net.minecraft.server.v1_12_R1.MathHelper;
import net.minecraft.server.v1_12_R1.MinecraftServer;
import net.minecraft.server.v1_12_R1.NBTTagCompound;
import org.bukkit.craftbukkit.v1_12_R1.inventory.CraftItemStack;
import org.bukkit.entity.Item;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityResurrectEvent;
import org.bukkit.event.player.PlayerFishEvent;

import java.util.Map;
import java.util.Random;
import java.util.WeakHashMap;

/**
 * Deaths and loot: looting changes (Advanced Looting mixin, Butchering/Luck Magnification/Ascetic
 * LootingLevelEvent), Ascetic and Culling drops, Mortalitas, Adept XP, Rune: Resurrection, fishing
 * luck/lure and Ascetic's ItemFishedEvent.
 */
public final class LootFx implements Listener {

    /** last successful damage source per victim (Forge passes it to the death events) */
    private static final WeakHashMap<EntityLiving, Object[]> LAST = new WeakHashMap<>();

    static DamageSource deathSource(EntityLiving v) {
        Object[] o = LAST.get(v);
        if (o == null || (Integer) o[1] < MinecraftServer.currentTick - 1) return null;
        return (DamageSource) o[0];
    }

    // ------------------------------------------------------------------ looting

    /** EnchantmentAdvancedLooting.getLevelMult (random per call) */
    static int advancedLootingValue(EntityLiving e) {
        int level = Nms.mainLevel(E.ADVANCEDLOOTING, e);
        if (level <= 0) return 0;
        int r = 2 + 2 * level;
        if (Math.random() < 0.25F) r = 3 + 3 * level;
        return r;
    }

    /** ForgeHooks.getLootingLevel with SME's mixin and LootingLevelEvent handlers (HIGH, HIGH, LOW) */
    static int smeLooting(EntityLiving victim, DamageSource source) {
        net.minecraft.server.v1_12_R1.Entity ts = source == null ? null : source.getEntity();
        int looting = 0;
        if (ts instanceof EntityLiving) {
            EntityLiving k = (EntityLiving) ts;
            looting = EnchantmentManager.g(k) + advancedLootingValue(k);
        }
        if (Ctx.allowed(source)) {
            EntityLiving a = (EntityLiving) ts;
            int b = Fx.max(E.BUTCHERING, a);
            if (b > 0 && victim instanceof EntityAnimal) looting += b;
            if (a instanceof EntityHuman) {
                int lm = Fx.max(E.LUCKMAGNIFICATION, a);
                if (lm > 0) looting += (int) (a.getAttributeInstance(GenericAttributes.j).getValue() * (double) lm / 2.0D);
            }
            int asc = Fx.max(E.ASCETIC, a);
            if (asc > 0) looting = Math.max(0, looting - asc);
        }
        return looting;
    }

    /** killer weapon whose Looting level is temporarily set so vanilla loot generation uses SME's looting */
    private static final WeakHashMap<EntityLiving, Object[]> RESTORE = new WeakHashMap<>();
    public static long lootingAdjusted = 0;

    private static void restore(EntityLiving victim) {
        Object[] r = RESTORE.remove(victim);
        if (r == null) return;
        ItemStack stack = (ItemStack) r[0];
        int original = (Integer) r[1];
        Map<net.minecraft.server.v1_12_R1.Enchantment, Integer> m = EnchantmentManager.a(stack);
        if (original > 0) m.put(Enchantments.LOOT_BONUS_MOBS, original);
        else m.remove(Enchantments.LOOT_BONUS_MOBS);
        EnchantmentManager.a(m, stack);
    }

    public static void register() {
        Pipeline.post("loot.lastSource", c -> LAST.put(c.victim, new Object[]{c.source, MinecraftServer.currentTick}));
        // Rune: Resurrection (checkTotemDeathProtection HEAD) and looting, both need "this hit kills"
        Pipeline.post("loot.fatal", c -> {
            EntityLiving v = c.victim;
            float after = v.getHealth() - (float) c.event.getFinalDamage();
            if (after > 0.0F) return;
            if (resurrect(c, after)) return;
            if (v instanceof EntityHuman) return;
            DamageSource src = c.source;
            net.minecraft.server.v1_12_R1.Entity ts = src.getEntity();
            if (!(ts instanceof EntityLiving)) return;
            EntityLiving killer = (EntityLiving) ts;
            int vanilla = EnchantmentManager.g(killer);
            int sme = smeLooting(v, src);
            if (sme == vanilla) return;
            ItemStack stack = killer.getItemInMainHand();
            if (stack.isEmpty()) return; // looting can only live on the main-hand item
            Map<net.minecraft.server.v1_12_R1.Enchantment, Integer> m = EnchantmentManager.a(stack);
            int original = m.getOrDefault(Enchantments.LOOT_BONUS_MOBS, 0);
            if (sme > 0) m.put(Enchantments.LOOT_BONUS_MOBS, sme);
            else m.remove(Enchantments.LOOT_BONUS_MOBS);
            EnchantmentManager.a(m, stack);
            RESTORE.put(v, new Object[]{stack, original});
            lootingAdjusted++;
        });
    }

    /** runs just before vanilla death handling would see health <= 0 */
    private static boolean resurrect(Ctx c, float after) {
        EntityLiving v = c.victim;
        if (c.source.ignoresInvulnerability()) return false;
        ItemStack stack = v.getItemInMainHand();
        int level = Nms.level(E.RUNE_RESURRECTION, stack);
        if (level <= 0) {
            stack = v.getItemInOffHand();
            level = Nms.level(E.RUNE_RESURRECTION, stack);
        }
        if (level <= 0) return false;
        Map<net.minecraft.server.v1_12_R1.Enchantment, Integer> m = EnchantmentManager.a(stack);
        m.remove(E.RUNE_RESURRECTION);
        EnchantmentManager.a(m, stack);
        float healthAfter = Math.min(v.getMaxHealth(), v.getMaxHealth() * 0.5F * (float) level);
        // CraftBukkit applies this event's result afterwards: health -= final damage, absorption -= consumed absorption.
        // Leave a tiny final damage so the hit still counts as a successful hit (hurt animation, iframes).
        float consumedAbsorption = (float) -c.event.getDamage(org.bukkit.event.entity.EntityDamageEvent.DamageModifier.ABSORPTION);
        final float eps = 0.001F;
        org.bukkit.event.entity.EntityDamageEvent ev = c.event;
        org.bukkit.event.entity.EntityDamageEvent.DamageModifier carrier = ev.isApplicable(org.bukkit.event.entity.EntityDamageEvent.DamageModifier.MAGIC)
                ? org.bukkit.event.entity.EntityDamageEvent.DamageModifier.MAGIC : org.bukkit.event.entity.EntityDamageEvent.DamageModifier.BASE;
        ev.setDamage(carrier, ev.getDamage(carrier) + (eps - ev.getFinalDamage()));
        v.removeAllEffects();
        v.addEffect(new net.minecraft.server.v1_12_R1.MobEffect(net.minecraft.server.v1_12_R1.MobEffects.REGENERATION, 900, 1));
        v.addEffect(new net.minecraft.server.v1_12_R1.MobEffect(net.minecraft.server.v1_12_R1.MobEffects.ABSORBTION, 100, 1));
        v.setAbsorptionHearts(v.getAbsorptionHearts() + consumedAbsorption);
        v.setHealth(healthAfter + eps);
        RUNE_IFRAMES.put(v, MinecraftServer.currentTick);
        resurrections++;
        return true;
    }

    public static long resurrections = 0;
    private static final WeakHashMap<EntityLiving, Integer> RUNE_IFRAMES = new WeakHashMap<>();

    /** hurtResistantTime = max + 10 (CraftBukkit resets it to max after the event; fixed on the next tick) */
    public static void tick() {
        if (!RUNE_IFRAMES.isEmpty()) {
            for (Map.Entry<EntityLiving, Integer> en : RUNE_IFRAMES.entrySet()) {
                EntityLiving v = en.getKey();
                if (v.isAlive()) v.noDamageTicks = Math.max(v.noDamageTicks, v.maxNoDamageTicks + 10 - 1);
            }
            RUNE_IFRAMES.clear();
        }
        if (!RESTORE.isEmpty()) {
            for (EntityLiving v : new java.util.ArrayList<>(RESTORE.keySet())) restore(v);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onResurrect(EntityResurrectEvent e) {
        EntityLiving v = Nms.living(e.getEntity());
        if (v != null) restore(v);
    }

    // ------------------------------------------------------------------ death

    @EventHandler(priority = EventPriority.HIGH)
    public void onDeath(EntityDeathEvent e) {
        final LivingEntity le = e.getEntity();
        final EntityLiving victim = Nms.living(le);
        if (victim == null) return;
        Guard.run("loot.restore", () -> restore(victim));
        final DamageSource source = deathSource(victim);
        // Mortalitas (LivingDeathEvent LOW)
        Guard.run("mortalitas", () -> {
            if (!Ctx.allowed(source)) return;
            EntityLiving a = (EntityLiving) source.getEntity();
            ItemStack stack = a.getItemInMainHand();
            if (stack.isEmpty()) return;
            int level = Nms.level(E.MORTALITAS, stack);
            if (level > 0) {
                NBTTagCompound tag = stack.getTag();
                if (tag == null) tag = new NBTTagCompound();
                float amount = tag.getFloat("MortalitasDamage");
                amount += (0.05F / (float) E.MORTALITAS.getMaxLevel()) * (float) level;
                amount = MathHelper.a(amount, 0, level);
                tag.setFloat("MortalitasDamage", amount);
                stack.setTag(tag);
            }
        });
        // Adept (LivingExperienceDropEvent): attacking player, dropped XP > 0
        Guard.run("adept", () -> {
            if (le.getKiller() == null || e.getDroppedExp() <= 0) return;
            EntityHuman player = (EntityHuman) Nms.living(le.getKiller());
            int level = Fx.max(E.ADEPT, player);
            if (level > 0) {
                boolean boss = victim instanceof EntityWither || victim instanceof EntityEnderDragon;
                float multi = boss ? 0.5F : 0.15F;
                int added = Math.max((level + 1) / 2, (int) (e.getDroppedExp() * level * multi));
                e.setDroppedExp(e.getDroppedExp() + added);
            }
        });
        // LivingDropsEvent: Ascetic (HIGH, may cancel), Culling (NORMAL)
        final boolean[] canceled = {false};
        Guard.run("ascetic.drops", () -> {
            if (!Ctx.allowed(source)) return;
            if (victim instanceof EntityHuman) return;
            EntityLiving a = (EntityLiving) source.getEntity();
            int level = Fx.max(E.ASCETIC, a);
            if (level > 0) {
                if (victim instanceof EntityWither || victim instanceof EntityEnderDragon) return;
                if (a.getRandom().nextFloat() < 0.25F * (float) level) {
                    e.getDrops().clear();
                    canceled[0] = true;
                }
            }
        });
        if (canceled[0]) return;
        Guard.run("culling.drops", () -> {
            if (source == null || !Ctx.culling(source)) return;
            if (victim.getRandom().nextFloat() <= 0.25F) {
                ItemStack skull = skull(victim);
                if (skull != null) e.getDrops().add(CraftItemStack.asBukkitCopy(skull));
            }
        });
    }

    static ItemStack skull(EntityLiving entity) {
        ItemStack skull;
        if (entity instanceof EntitySkeleton) return new ItemStack(Items.SKULL, 1, 0);
        if (entity instanceof EntitySkeletonWither) return new ItemStack(Items.SKULL, 1, 1);
        if (entity instanceof EntityZombie && !(entity instanceof EntityPigZombie)) return new ItemStack(Items.SKULL, 1, 2);
        if (entity instanceof EntityCreeper) return new ItemStack(Items.SKULL, 1, 4);
        String owner;
        if (entity instanceof EntityHuman) owner = entity.getName();
        else if (entity instanceof EntityBlaze) owner = "MHF_Blaze";
        else if (entity instanceof EntityPigZombie) owner = "MHF_PigZombie";
        else if (entity instanceof EntityCaveSpider) owner = "MHF_CaveSpider";
        else if (entity instanceof EntitySpider) owner = "MHF_Spider";
        else if (entity instanceof EntityChicken) owner = "MHF_Chicken";
        else if (entity instanceof EntityMushroomCow) owner = "MHF_MushroomCow";
        else if (entity instanceof EntityCow) owner = "MHF_Cow";
        else if (entity instanceof EntityEnderman) owner = "MHF_Enderman";
        else if (entity instanceof EntityGhast) owner = "MHF_Ghast";
        else if (entity instanceof EntityIronGolem) owner = "MHF_Golem";
        else if (entity instanceof EntityMagmaCube) owner = "MHF_LavaSlime";
        else if (entity instanceof EntityOcelot) owner = "MHF_Ocelot";
        else if (entity instanceof EntityPig) owner = "MHF_Pig";
        else if (entity instanceof EntitySheep) owner = "MHF_Sheep";
        else if (entity instanceof EntitySlime) owner = "MHF_Slime";
        else if (entity instanceof EntitySquid) owner = "MHF_Squid";
        else if (entity instanceof EntityVillager) owner = "MHF_Villager";
        else return null;
        skull = new ItemStack(Items.SKULL, 1, 3);
        NBTTagCompound tag = new NBTTagCompound();
        tag.setString("SkullOwner", owner);
        skull.setTag(tag);
        return skull;
    }

    // ------------------------------------------------------------------ fishing

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onFishCast(PlayerFishEvent e) {
        if (e.getState() != PlayerFishEvent.State.FISHING) return;
        Guard.run("fishing.luck_lure", () -> {
            EntityHuman p = (EntityHuman) Nms.living(e.getPlayer());
            ItemStack rod = p.getItemInMainHand();
            if (!(rod.getItem() instanceof ItemFishingRod)) rod = p.getItemInOffHand();
            if (!(rod.getItem() instanceof ItemFishingRod)) return;
            EntityFishingHook hook = (EntityFishingHook) Nms.entity(e.getHook());
            int luck = luckValue(rod), lure = lureValue(rod);
            if (luck != 0 || lure != 0) Nms.addHookLuckLure(hook, luck, lure);
        });
    }

    /** EnchantmentAdvancedLuckOfTheSea.getLevelValue */
    static int luckValue(ItemStack s) {
        int level = Nms.level(E.ADVANCEDLUCKOFTHESEA, s);
        if (level <= 0) return 0;
        int r = 2 + 2 * level;
        if (Math.random() < 0.25F) r = 3 + 3 * level;
        return r;
    }

    /** EnchantmentAdvancedLure.getLevelValue */
    static int lureValue(ItemStack s) {
        int level = Nms.level(E.ADVANCEDLURE, s);
        if (level <= 0) return 0;
        int r = 1 + level;
        if (Math.random() < 0.25F) r = 2 + level;
        return r;
    }

    /** Ascetic ItemFishedEvent (HIGH): cancelled catch = no item, no XP */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onCatch(PlayerFishEvent e) {
        if (e.getState() != PlayerFishEvent.State.CAUGHT_FISH || !(e.getCaught() instanceof Item)) return;
        Guard.run("ascetic.fishing", () -> {
            EntityHuman p = (EntityHuman) Nms.living(e.getPlayer());
            int level = Fx.max(E.ASCETIC, p);
            if (level > 0) {
                Random r = p.getRandom();
                if (r.nextFloat() < 0.25F * (float) level) {
                    ((Item) e.getCaught()).remove();
                    e.setExpToDrop(0);
                }
            }
        });
    }
}
