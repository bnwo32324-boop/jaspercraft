package chat.jaspr.enchant.fx;

import chat.jaspr.enchant.E;
import chat.jaspr.enchant.util.Nms;
import net.minecraft.server.v1_12_R1.Block;
import net.minecraft.server.v1_12_R1.BlockBed;
import net.minecraft.server.v1_12_R1.BlockCobbleWall;
import net.minecraft.server.v1_12_R1.BlockFence;
import net.minecraft.server.v1_12_R1.BlockFenceGate;
import net.minecraft.server.v1_12_R1.BlockHay;
import net.minecraft.server.v1_12_R1.BlockPosition;
import net.minecraft.server.v1_12_R1.DamageSource;
import net.minecraft.server.v1_12_R1.EntityHuman;
import net.minecraft.server.v1_12_R1.EntityLiving;
import net.minecraft.server.v1_12_R1.EnumMoveType;
import net.minecraft.server.v1_12_R1.IBlockData;
import net.minecraft.server.v1_12_R1.Material;
import net.minecraft.server.v1_12_R1.MathHelper;
import net.minecraft.server.v1_12_R1.MobEffect;
import net.minecraft.server.v1_12_R1.MobEffects;


/**
 * Light Weight / Heavy Weight. LivingFallEvent (distance, damage multiplier) is applied to the fall damage
 * before LivingAttackEvent; LivingJumpEvent is emulated for non-player mobs (player jumps are client side
 * and handled by the patched client).
 */
public final class MovementFx {
    private MovementFx() {}

    public static long fallAdjusted = 0, mobJumps = 0;

    public static void register() {
        Pipeline.pre("fall.weight", c -> {
            if (c.source != DamageSource.FALL) return;
            EntityLiving v = c.victim;
            int lw = Fx.max(E.LIGHTWEIGHT, v);
            int hw = Fx.max(E.HEAVYWEIGHT, v);
            if (lw <= 0 && hw <= 0) return;
            // reconstruct the vanilla fall(distance, multiplier) call
            float distance = v.fallDistance;
            float multiplier = 1.0F;
            BlockPosition pos = new BlockPosition(MathHelper.floor(v.locX), MathHelper.floor(v.locY - 0.20000000298023224D), MathHelper.floor(v.locZ));
            IBlockData state = v.world.getType(pos);
            if (state.getMaterial() == Material.AIR) {
                IBlockData down = v.world.getType(pos.down());
                Block b = down.getBlock();
                if (b instanceof BlockFence || b instanceof BlockCobbleWall || b instanceof BlockFenceGate) state = down;
            }
            Block landed = state.getBlock();
            if (landed instanceof BlockHay) multiplier = 0.2F;
            else if (landed instanceof BlockBed) distance *= 0.5F;
            MobEffect jump = v.getEffect(MobEffects.JUMP);
            float jb = jump == null ? 0.0F : (float) (jump.getAmplifier() + 1);
            int vanillaDamage = MathHelper.f((distance - 3.0F - jb) * multiplier);
            if (vanillaDamage != (int) Math.round(c.event.getDamage())) {
                // unknown fall context (another plugin changed it): scale proportionally instead
                distance = (float) c.event.getDamage() / Math.max(multiplier, 1.0E-4F) + 3.0F + jb;
            }
            if (lw > 0) distance = MathHelper.a(distance - 4 - lw * 2, 0, 20);
            if (hw > 0) multiplier = multiplier * (1.0F + (float) hw * 0.25F);
            int dmg = MathHelper.f((distance - 3.0F - jb) * multiplier);
            fallAdjusted++;
            if (dmg <= 0) {
                c.attackCanceled = true;
                return;
            }
            c.event.setDamage(dmg);
        });
    }

    /** called every tick (before entity updates) for mobs wearing Light/Heavy Weight */
    public static void mobTick(EntityLiving e) {
        if (e instanceof EntityHuman) return;
        // vanilla sets jumpTicks = 10 when jump() runs; it is decremented at the start of the next update
        if (Nms.jumpTicks(e) != 10 || e.onGround || e.motY <= 0.0D || e.isInWater() || e.au()) return;
        // the mob left the ground this tick with upward motion: it jumped (vanilla jump(): motY = 0.42 + boost)
        int lw = Fx.max(E.LIGHTWEIGHT, e);
        int hw = Fx.max(E.HEAVYWEIGHT, e);
        double k = 1.0D;
        if (lw > 0) k *= (1.05D + (double) lw * 0.15D);
        if (hw > 0) k *= (1.0D - (double) hw * 0.1D);
        if (k == 1.0D) return;
        // motY now = (J - 0.08) * 0.98 after one travel step; recover J, redo the step with J*k
        double j = e.motY / 0.98D + 0.08D;
        double dy = j * k - j;
        e.move(EnumMoveType.SELF, 0.0D, dy, 0.0D);
        e.motY = (j * k - 0.08D) * 0.98D;
        e.velocityChanged = true;
        mobJumps++;
    }

    static int levelOf(EntityLiving e) {
        return Nms.maxLevel(E.LIGHTWEIGHT, e) + Nms.maxLevel(E.HEAVYWEIGHT, e);
    }
}
