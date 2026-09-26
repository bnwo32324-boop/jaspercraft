package chat.jaspr.enchant.fx;

import chat.jaspr.enchant.E;
import chat.jaspr.enchant.util.Guard;
import chat.jaspr.enchant.util.Nms;
import net.minecraft.server.v1_12_R1.AttributeInstance;
import net.minecraft.server.v1_12_R1.AttributeModifier;
import net.minecraft.server.v1_12_R1.AxisAlignedBB;
import net.minecraft.server.v1_12_R1.BlockPosition;
import net.minecraft.server.v1_12_R1.Enchantment;
import net.minecraft.server.v1_12_R1.EnchantmentManager;
import net.minecraft.server.v1_12_R1.Entity;
import net.minecraft.server.v1_12_R1.EntityArrow;
import net.minecraft.server.v1_12_R1.EntityHuman;
import net.minecraft.server.v1_12_R1.EntityInsentient;
import net.minecraft.server.v1_12_R1.EntityLiving;
import net.minecraft.server.v1_12_R1.EntityPlayer;
import net.minecraft.server.v1_12_R1.GenericAttributes;
import net.minecraft.server.v1_12_R1.IProjectile;
import net.minecraft.server.v1_12_R1.Item;
import net.minecraft.server.v1_12_R1.ItemBow;
import net.minecraft.server.v1_12_R1.ItemEnchantedBook;
import net.minecraft.server.v1_12_R1.ItemStack;
import net.minecraft.server.v1_12_R1.MinecraftServer;
import net.minecraft.server.v1_12_R1.MobEffect;
import net.minecraft.server.v1_12_R1.MobEffects;
import net.minecraft.server.v1_12_R1.PlayerInventory;
import net.minecraft.server.v1_12_R1.Vec3D;
import net.minecraft.server.v1_12_R1.WorldServer;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.craftbukkit.v1_12_R1.CraftWorld;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.WeakHashMap;

/**
 * Per-tick behaviour: Forge PlayerTickEvent (Curse of Holding, Luck Magnification, Pandora's Curse,
 * Unsheathing tracking), LivingUpdateEvent every 20 ticks (Strengthened Vitality, Breached Plating),
 * LivingEntityUseItemEvent.Tick (Pushing, Strafe), the held-item attack-speed modifiers (Swifter Slashes,
 * Heavy Weight) and frostWalk-style Magma Walker, plus the mob jump emulation.
 */
public final class TickFx implements Runnable {

    public static final class PlayerState {
        Item lastHeld = null;
        public int lastSwapTime = 0;
        public int lastUnsheatheTrigger = 0;
        BlockPosition lastPos = null;
    }

    private static final WeakHashMap<EntityHuman, PlayerState> STATES = new WeakHashMap<>();

    public static PlayerState state(EntityHuman h) {
        PlayerState s = STATES.get(h);
        if (s == null) {
            s = new PlayerState();
            STATES.put(h, s);
        }
        return s;
    }

    /** pandorasCurseInterval default */
    public static int pandoraInterval = 600;

    static final UUID SS_UUID = UUID.fromString("fc1c8dca-9411-4a4e-97a4-90e66c883a77");
    static final UUID HW_UUID = UUID.fromString("e2765897-134f-4c14-a535-29c3ae5c7a21");
    static final UUID SV_UUID = UUID.fromString("eabe21c1-dc07-4ca0-9992-468ef792ef49");
    static final UUID BP_UUID = UUID.fromString("8e07ebc9-f6f6-439c-8e65-00a70cd0b226");

    /** mobs with Magma Walker / Light Weight / Heavy Weight, refreshed every 20 ticks */
    private static final WeakHashMap<EntityLiving, BlockPosition> MOB_WATCH = new WeakHashMap<>();

    public static long strafeTicks = 0, pushTicks = 0;

    @Override
    public void run() {
        int now = MinecraftServer.currentTick;
        for (World bw : Bukkit.getWorlds()) {
            WorldServer w = ((CraftWorld) bw).getHandle();
            List<Entity> list = new ArrayList<>(w.entityList);
            for (Entity ent : list) {
                if (!(ent instanceof EntityLiving) || ent.dead) continue;
                EntityLiving e = (EntityLiving) ent;
                if (e.isHandRaised()) Guard.run("tick.bow", () -> bowTick(e));
                if (e.ticksLived % 20 == 0) Guard.run("tick.living20", () -> living20(e));
                if (e instanceof EntityPlayer) Guard.run("tick.player", () -> playerTick((EntityPlayer) e));
            }
        }
        if (!MOB_WATCH.isEmpty()) {
            for (Map.Entry<EntityLiving, BlockPosition> en : new ArrayList<>(MOB_WATCH.entrySet())) {
                EntityLiving e = en.getKey();
                if (e.dead) {
                    MOB_WATCH.remove(e);
                    continue;
                }
                Guard.run("tick.mobmove", () -> {
                    MovementFx.mobTick(e);
                    BlockPosition pos = new BlockPosition(e);
                    if (!pos.equals(en.getValue())) {
                        MOB_WATCH.put(e, pos);
                        int mw = TempBlocks.magmaWalkerLevel(e);
                        if (mw > 0) TempBlocks.walkOnMagma(e, pos, mw);
                    }
                });
            }
        }
        Guard.run("tick.knockback", KnockbackFx::tick);
        Guard.run("tick.arrows", ArrowFx::tick);
        Guard.run("tick.loot", LootFx::tick);
        Guard.run("tick.dig", DigFx::tick);
        Guard.run("tick.tempblocks", TempBlocks::tick);
        if (now % 20 == 0) Guard.run("tick.cleanup", NmsHooks::cleanup);
        if (now % 100 == 0) Guard.run("tick.save", TempBlocks::save);
    }

    // ------------------------------------------------------------------ LivingEntityUseItemEvent.Tick

    private static void bowTick(EntityLiving e) {
        ItemStack bow = e.getActiveItem();
        if (bow.isEmpty() || !(bow.getItem() instanceof ItemBow)) return;
        // Pushing (HIGH, registry index 47)
        if (e.ticksLived % 10 != 0) {
            int level = Nms.level(E.PUSHING, bow);
            if (level > 0) {
                pushTicks++;
                AxisAlignedBB axis = new AxisAlignedBB(new BlockPosition(e)).g(4 + level * 2);
                for (Entity ent : e.world.a(Entity.class, axis)) {
                    if (ent instanceof EntityInsentient || ent instanceof IProjectile) {
                        if (ent.dead) continue;
                        if (ent instanceof EntityArrow && ent.onGround) continue;
                        Vec3D v = new Vec3D(ent.locX - e.locX, ent.locY - e.locY, ent.locZ - e.locZ);
                        double distance = v.b() + 0.1D;
                        double diminish = 10 / distance / Math.max(50 - level * 10, 1);
                        ent.motX += v.x / Math.max(5.25 - level * 1.5, 1) * diminish;
                        ent.motY += v.y / Math.max(6.25 - level * 1.25, 1) * diminish;
                        ent.motZ += v.z / Math.max(5.25 - level * 1.5, 1) * diminish;
                    }
                }
            }
        }
        // Strafe (HIGH, registry index 55): shorten the remaining use count so the draw charges faster
        int strafe = Nms.level(E.STRAFE, bow);
        if (strafe > 0) {
            strafeTicks++;
            int duration = Nms.useCount(e);
            if (strafe < 4) {
                if (duration % (5 - strafe) == 0) Nms.setUseCount(e, duration - 1);
            } else {
                Nms.setUseCount(e, duration - (strafe - 3));
            }
        }
    }

    // ------------------------------------------------------------------ LivingUpdateEvent (ticksExisted % 20)

    private static void setModifier(AttributeInstance inst, UUID id, String name, double amount, int op) {
        if (inst == null) return;
        AttributeModifier previous = inst.a(id);
        if (previous == null) {
            inst.b(new AttributeModifier(id, name, amount, op));
        } else if (previous.d() != amount) {
            inst.b(id);
            inst.b(new AttributeModifier(id, name, amount, op));
        }
    }

    private static void removeModifier(AttributeInstance inst, UUID id) {
        if (inst != null && inst.a(id) != null) inst.b(id);
    }

    private static void living20(EntityLiving e) {
        int sv = Nms.maxLevel(E.STRENGTHENEDVITALITY, e);
        AttributeInstance mh = e.getAttributeInstance(GenericAttributes.maxHealth);
        if (sv > 0) setModifier(mh, SV_UUID, "StrengthenedVitalityBoost", 0.1D * (double) sv, 2);
        else removeModifier(mh, SV_UUID);
        int bp = Nms.maxLevel(E.BREACHEDPLATING, e);
        AttributeInstance ar = e.getAttributeInstance(GenericAttributes.h);
        if (bp > 0) setModifier(ar, BP_UUID, "BreachedPlatingDebuff", -0.1D * (double) bp, 2);
        else removeModifier(ar, BP_UUID);
        if (!(e instanceof EntityHuman)) {
            boolean watch = Nms.maxLevel(E.MAGMAWALKER, e) > 0 || Nms.maxLevel(E.LIGHTWEIGHT, e) > 0 || Nms.maxLevel(E.HEAVYWEIGHT, e) > 0;
            if (watch) {
                if (!MOB_WATCH.containsKey(e)) MOB_WATCH.put(e, new BlockPosition(e));
            } else MOB_WATCH.remove(e);
        }
    }

    // ------------------------------------------------------------------ PlayerTickEvent

    private static void playerTick(EntityPlayer p) {
        PlayerState st = state(p);
        int tl = p.ticksLived;
        // Curse of Holding (START, every 9 ticks)
        if (tl % 9 == 0) {
            int level = Nms.maxLevel(E.CURSEOFHOLDING, p);
            if (level > 0) {
                p.addEffect(new MobEffect(MobEffects.SLOWER_MOVEMENT, 10, level - 1, false, false));
                p.addEffect(new MobEffect(MobEffects.WEAKNESS, 10, level > 1 ? 1 : 0, false, false));
                p.addEffect(new MobEffect(MobEffects.A, 10, level - 1, false, false));
                if (level > 1) {
                    p.addEffect(new MobEffect(MobEffects.HUNGER, 10, level - 1, false, false));
                    p.addEffect(new MobEffect(MobEffects.SLOWER_DIG, 10, level - 1, false, false));
                }
            }
            // Luck Magnification (END, every 9 ticks)
            int lm = Nms.maxLevel(E.LUCKMAGNIFICATION, p);
            if (lm > 0) p.addEffect(new MobEffect(MobEffects.z, 10, lm - 1, true, false));
        }
        // Pandora's Curse (END)
        if (tl % pandoraInterval == 0) Guard.run("pandorascurse", () -> pandora(p));
        // Unsheathing (END): remember when the held item changed
        ItemStack cur = p.getItemInMainHand();
        if (!cur.isEmpty() && cur.getItem() != st.lastHeld) st.lastSwapTime = tl;
        st.lastHeld = cur.isEmpty() ? null : cur.getItem();
        // Swifter Slashes / Heavy Weight main-hand attack speed (ItemMixin.getAttributeModifiers)
        boolean nbtModifiers = cur.hasTag() && cur.getTag().hasKeyOfType("AttributeModifiers", 9);
        int ss = nbtModifiers ? 0 : Nms.level(E.SWIFTERSLASHES, cur);
        int hw = nbtModifiers ? 0 : Nms.level(E.HEAVYWEIGHT, cur);
        AttributeInstance speed = p.getAttributeInstance(GenericAttributes.g);
        if (ss > 0) setModifier(speed, SS_UUID, "swifterSlashes", 0.20D * (double) ss, 1);
        else removeModifier(speed, SS_UUID);
        if (hw > 0) setModifier(speed, HW_UUID, "heavyWeight", ((double) hw * 0.10D + 0.20) * -1.0D, 1);
        else removeModifier(speed, HW_UUID);
        // Magma Walker (EntityLivingBase.frostWalk on block position change)
        BlockPosition pos = new BlockPosition(p);
        if (!pos.equals(st.lastPos)) {
            st.lastPos = pos;
            int mw = TempBlocks.magmaWalkerLevel(p);
            if (mw > 0) TempBlocks.walkOnMagma(p, pos, mw);
        }
    }

    /** EnchantmentPandorasCurse.onPlayerTickEvent */
    private static void pandora(EntityPlayer player) {
        PlayerInventory inv = player.inventory;
        ItemStack cursed = ItemStack.a;
        int curseLevel = 0;
        List<ItemStack> candidates = new ArrayList<>();
        List<List<ItemStack>> groups = new ArrayList<>();
        groups.add(inv.extraSlots);
        groups.add(inv.armor);
        groups.add(inv.items);
        for (List<ItemStack> g : groups) {
            for (ItemStack stack : g) {
                if (!stack.isEmpty() && !(stack.getItem() instanceof ItemEnchantedBook)) {
                    int lvl = EnchantmentManager.getEnchantmentLevel(E.PANDORASCURSE, stack);
                    if (lvl > 0) {
                        cursed = stack;
                        curseLevel = lvl;
                    } else if (stack.getMaxStackSize() == 1) candidates.add(stack);
                }
            }
        }
        if (cursed.isEmpty() || candidates.isEmpty()) return;
        int orig = curseLevel;
        List<Enchantment> curses = Fx.curses();
        Random rand = player.world.random;
        for (ItemStack stack : candidates) {
            if (!stack.canEnchant()) continue;
            if (curseLevel <= 5 && rand.nextInt(8) < 1) {
                Enchantment curse = curses.get(rand.nextInt(curses.size()));
                if (curse != E.PANDORASCURSE && curse.canEnchant(stack)) {
                    boolean compat = true;
                    for (Enchantment ench : EnchantmentManager.a(stack).keySet()) {
                        if (!ench.c(curse)) {
                            compat = false;
                            break;
                        }
                    }
                    if (compat) {
                        curseLevel++;
                        stack.addEnchantment(curse, rand.nextInt(curse.getMaxLevel()) + 1);
                    }
                }
            }
        }
        if (curseLevel != orig || curseLevel > 5) {
            Map<Enchantment, Integer> enchants = EnchantmentManager.a(cursed);
            if (curseLevel > 5) {
                enchants.remove(E.PANDORASCURSE);
                Fx.customSound(player, "pandora_removal", 0.8F, (rand.nextFloat() - rand.nextFloat()) * 0.1F + 1.4F);
            } else {
                enchants.put(E.PANDORASCURSE, curseLevel);
            }
            EnchantmentManager.a(enchants, cursed);
        }
    }
}
