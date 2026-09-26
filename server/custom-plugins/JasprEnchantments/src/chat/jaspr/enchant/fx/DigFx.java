package chat.jaspr.enchant.fx;

import chat.jaspr.enchant.E;
import chat.jaspr.enchant.util.Guard;
import chat.jaspr.enchant.util.Nms;
import net.minecraft.server.v1_12_R1.BlockPosition;
import net.minecraft.server.v1_12_R1.EnchantmentManager;
import net.minecraft.server.v1_12_R1.EntityHuman;
import net.minecraft.server.v1_12_R1.EntityPlayer;
import net.minecraft.server.v1_12_R1.IBlockData;
import net.minecraft.server.v1_12_R1.ItemStack;
import net.minecraft.server.v1_12_R1.ItemTool;
import net.minecraft.server.v1_12_R1.Material;
import net.minecraft.server.v1_12_R1.MathHelper;
import net.minecraft.server.v1_12_R1.MinecraftServer;
import net.minecraft.server.v1_12_R1.MobEffects;
import org.bukkit.GameMode;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockDamageEvent;
import org.bukkit.event.player.PlayerAnimationEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/**
 * Advanced Efficiency (mixin on EnchantmentHelper.getEfficiencyModifier: +floor(2.5*level)) and
 * Inefficient (BreakSpeed: /(level^2+1)) on the server. Block breaking is client-timed; the server
 * would otherwise delay SME-fast digs, so this "dig assist" breaks the block through the player's
 * PlayerInteractManager once SME's progress reaches 1.0 while the player keeps swinging at it, and
 * refuses breaks that are faster than Inefficient allows.
 */
public final class DigFx implements Listener {

    private static final class Dig {
        final BlockPosition pos;
        final int startTick;
        float progress;
        int lastSwing;
        final boolean faster;

        Dig(BlockPosition pos, int tick, float first, boolean faster) {
            this.pos = pos;
            this.startTick = tick;
            this.progress = first;
            this.lastSwing = tick;
            this.faster = faster;
        }
    }

    private static final Map<UUID, Dig> DIGS = new HashMap<>();
    public static long assisted = 0, instant = 0, refused = 0;
    private static boolean breaking = false;

    /** EntityHuman.getDigSpeed with SME's efficiency and Inefficient changes */
    static float digSpeed(EntityHuman p, IBlockData state, BlockPosition pos, boolean sme) {
        float f = p.inventory.a(state);
        if (f > 1.0F) {
            int i = EnchantmentManager.getDigSpeedEnchantmentLevel(p);
            if (sme) {
                int adv = Nms.mainLevel(E.ADVANCEDEFFICIENCY, p);
                if (adv > 0) i += MathHelper.d(adv * 2.5F);
            }
            ItemStack main = p.getItemInMainHand();
            if (i > 0 && !main.isEmpty()) f += (float) (i * i + 1);
        }
        if (p.hasEffect(MobEffects.FASTER_DIG)) f *= 1.0F + (float) (p.getEffect(MobEffects.FASTER_DIG).getAmplifier() + 1) * 0.2F;
        if (p.hasEffect(MobEffects.SLOWER_DIG)) {
            float m;
            switch (p.getEffect(MobEffects.SLOWER_DIG).getAmplifier()) {
                case 0: m = 0.3F; break;
                case 1: m = 0.09F; break;
                case 2: m = 0.0027F; break;
                default: m = 8.1E-4F;
            }
            f *= m;
        }
        if (p.a(Material.WATER) && !EnchantmentManager.h(p)) f /= 5.0F;
        if (!p.onGround) f /= 5.0F;
        if (sme) {
            ItemStack stack = p.getItemInMainHand();
            if (stack.getItem() instanceof ItemTool) {
                int level = Nms.level(E.INEFFICIENT, stack);
                if (level > 0 && (stack.b(state) || stack.getItem().getDestroySpeed(stack, state) > 1.0F)) {
                    f = f / (level * level + 1.F);
                }
            }
        }
        return f < 0 ? 0 : f;
    }

    /** Block.getPlayerRelativeBlockHardness */
    static float strength(EntityHuman p, IBlockData state, BlockPosition pos, boolean sme) {
        float hardness = state.b(p.world, pos);
        if (hardness < 0.0F) return 0.0F;
        float speed = digSpeed(p, state, pos, sme);
        return !p.hasBlock(state) ? speed / hardness / 100.0F : speed / hardness / 30.0F;
    }

    static boolean relevant(EntityHuman p) {
        ItemStack s = p.getItemInMainHand();
        return Nms.level(E.ADVANCEDEFFICIENCY, s) > 0 || Nms.level(E.INEFFICIENT, s) > 0;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamage(BlockDamageEvent e) {
        if (e.getPlayer().getGameMode() == GameMode.CREATIVE) return;
        Guard.run("dig.start", () -> {
            EntityHuman p = (EntityHuman) Nms.living(e.getPlayer());
            DIGS.remove(p.getUniqueID());
            if (!relevant(p)) return;
            org.bukkit.block.Block b = e.getBlock();
            BlockPosition pos = new BlockPosition(b.getX(), b.getY(), b.getZ());
            IBlockData state = p.world.getType(pos);
            float vanilla = strength(p, state, pos, false);
            float sme = strength(p, state, pos, true);
            if (Float.compare(vanilla, sme) == 0) return;
            if (Pipeline.debug) chat.jaspr.enchant.util.Log.info("SME_DEBUG dig start vanilla=" + vanilla + " sme=" + sme);
            if (sme >= 1.0F) {
                e.setInstaBreak(true);
                instant++;
                return;
            }
            DIGS.put(p.getUniqueID(), new Dig(pos, MinecraftServer.currentTick, sme, sme > vanilla));
        });
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onSwing(PlayerAnimationEvent e) {
        Dig d = DIGS.get(e.getPlayer().getUniqueId());
        if (d != null) d.lastSwing = MinecraftServer.currentTick;
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        DIGS.remove(e.getPlayer().getUniqueId());
    }

    /** Inefficient: refuse breaks that SME's slower speed has not earned yet */
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent e) {
        if (breaking || e.getPlayer().getGameMode() == GameMode.CREATIVE) return;
        Guard.run("dig.enforce", () -> {
            Dig d = DIGS.remove(e.getPlayer().getUniqueId());
            if (d == null || d.faster) return;
            org.bukkit.block.Block b = e.getBlock();
            if (b.getX() != d.pos.getX() || b.getY() != d.pos.getY() || b.getZ() != d.pos.getZ()) return;
            EntityHuman p = (EntityHuman) Nms.living(e.getPlayer());
            IBlockData state = p.world.getType(d.pos);
            float s = strength(p, state, d.pos, true);
            int elapsed = MinecraftServer.currentTick - d.startTick;
            if (s * (float) (elapsed + 1) < 0.7F) {
                e.setCancelled(true);
                refused++;
            }
        });
    }

    public static void tick() {
        if (DIGS.isEmpty()) return;
        int now = MinecraftServer.currentTick;
        for (Iterator<Map.Entry<UUID, Dig>> it = DIGS.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<UUID, Dig> en = it.next();
            Dig d = en.getValue();
            if (!d.faster) {
                if (now - d.lastSwing > 200) it.remove();
                continue;
            }
            org.bukkit.entity.Player bp = org.bukkit.Bukkit.getPlayer(en.getKey());
            if (bp == null || now - d.lastSwing > 10) {
                it.remove();
                continue;
            }
            EntityPlayer p = (EntityPlayer) Nms.living(bp);
            IBlockData state = p.world.getType(d.pos);
            if (state.getMaterial() == Material.AIR) {
                it.remove();
                continue;
            }
            d.progress += strength(p, state, d.pos, true);
            if (d.progress >= 1.0F) {
                it.remove();
                breaking = true;
                try {
                    if (p.playerInteractManager.breakBlock(d.pos)) assisted++;
                    if (Pipeline.debug) chat.jaspr.enchant.util.Log.info("SME_DEBUG dig assisted ticks=" + (now - d.startTick));
                    p.world.c(p.getId(), d.pos, -1);
                } finally {
                    breaking = false;
                }
            }
        }
    }
}
