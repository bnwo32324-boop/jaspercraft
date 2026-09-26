package chat.jaspr.enchant.fx;

import chat.jaspr.enchant.E;
import chat.jaspr.enchant.nms.SmeEnchantment;
import chat.jaspr.enchant.util.Nms;
import net.minecraft.server.v1_12_R1.BlockPosition;
import net.minecraft.server.v1_12_R1.DamageSource;
import net.minecraft.server.v1_12_R1.Enchantment;
import net.minecraft.server.v1_12_R1.EnchantmentManager;
import net.minecraft.server.v1_12_R1.Enchantments;
import net.minecraft.server.v1_12_R1.Entity;
import net.minecraft.server.v1_12_R1.EntityHuman;
import net.minecraft.server.v1_12_R1.EntityLiving;
import net.minecraft.server.v1_12_R1.EntityPlayer;
import net.minecraft.server.v1_12_R1.ItemStack;
import net.minecraft.server.v1_12_R1.MathHelper;
import net.minecraft.server.v1_12_R1.Vec3D;
import net.minecraft.server.v1_12_R1.World;
import net.minecraft.server.v1_12_R1.WorldData;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/** Ports of SME EnchantUtil plus small shared helpers. */
public final class Fx {
    private Fx() {}

    public static final Random RANDOM = new Random();

    /** ModConfig.miscellaneous.enableWeatherChanges (default true) */
    public static boolean weatherChanges = true;

    public static int lvl(Enchantment e, ItemStack s) {
        return Nms.level(e, s);
    }

    public static int max(Enchantment e, EntityLiving ent) {
        return Nms.maxLevel(e, ent);
    }

    public static int main(Enchantment e, EntityLiving ent) {
        return Nms.mainLevel(e, ent);
    }

    // ------------------------------------------------------------ EnchantUtil

    private static List<Enchantment> curses;

    /** EnchantUtil.getCurses: every curse except Pandora's Curse, in registry order */
    public static List<Enchantment> curses() {
        if (curses == null) {
            List<Enchantment> l = new ArrayList<>();
            for (Enchantment e : Enchantment.enchantments) {
                if (e.isCursed() && e != E.PANDORASCURSE) l.add(e);
            }
            curses = l;
        }
        return curses;
    }

    public static boolean canSeeSky(EntityLiving e) {
        if (e == null) return false;
        return e.world.i(new BlockPosition(e.locX, e.locY, e.locZ));
    }

    public static void setRaining(World w) {
        if (!weatherChanges) return;
        int i = (300 + RANDOM.nextInt(600)) * 20;
        WorldData d = w.getWorldData();
        d.i(0);
        d.setWeatherDuration(i);
        d.setThunderDuration(i);
        d.setStorm(true);
        d.setThundering(false);
    }

    public static void setThundering(World w) {
        if (!weatherChanges) return;
        int i = (300 + RANDOM.nextInt(600)) * 20;
        WorldData d = w.getWorldData();
        d.i(0);
        d.setWeatherDuration(i);
        d.setThunderDuration(i);
        d.setStorm(true);
        d.setThundering(true);
    }

    public static void setClear(World w) {
        if (!weatherChanges) return;
        int i = (300 + RANDOM.nextInt(600)) * 20;
        WorldData d = w.getWorldData();
        d.i(i);
        d.setWeatherDuration(0);
        d.setThunderDuration(0);
        d.setStorm(false);
        d.setThundering(false);
    }

    /** EnchantUtil.knockBackIgnoreKBRes */
    public static void knockBackIgnoreKBRes(Entity e, float strength, double xRatio, double zRatio) {
        e.impulse = true;
        float f = MathHelper.sqrt(Math.max(0.1F, xRatio * xRatio + zRatio * zRatio));
        e.motX /= 2.0;
        e.motZ /= 2.0;
        e.motX -= xRatio / (double) f * (double) strength;
        e.motZ -= zRatio / (double) f * (double) strength;
        if (!Double.isFinite(e.motX)) e.motX = 0;
        if (!Double.isFinite(e.motZ)) e.motZ = 0;
        if (e.onGround) {
            e.motY /= 2.0;
            e.motY += strength;
            if (e.motY > 0.4) e.motY = 0.4;
        }
        if (!Double.isFinite(e.motY)) e.motY = 0;
        e.velocityChanged = true;
    }

    /** EnchantUtil.canBlockDamageSource */
    public static boolean canBlock(DamageSource src, EntityLiving e) {
        if (!src.ignoresArmor() && e.isBlocking()) {
            Vec3D v = src.v();
            if (v != null) {
                Vec3D look = e.e(1.0F);
                Vec3D dir = v.a(new Vec3D(e.locX, e.locY, e.locZ)).a();
                dir = new Vec3D(dir.x, 0.0D, dir.z);
                return dir.b(look) < 0.0D;
            }
        }
        return false;
    }

    /** EnchantUtil.getDamageAfterMagicAbsorb */
    public static float afterMagicAbsorb(float damage, float modifiers) {
        float f = MathHelper.a(modifiers * 1.5F, 0.0F, 60.0F);
        return damage * (1.0F - f / 80.0F);
    }

    // ------------------------------------------------------------ sounds / misc

    public static void customSound(Entity at, String event, float volume, float pitch) {
        if (at == null) return;
        Location l = new Location(at.world.getWorld(), at.locX, at.locY, at.locZ);
        at.world.getWorld().playSound(l, "somanyenchantments:" + event, SoundCategory.PLAYERS, volume, pitch);
    }

    public static void sound(Entity at, Sound s, SoundCategory cat, float volume, float pitch) {
        Location l = new Location(at.world.getWorld(), at.locX, at.locY, at.locZ);
        at.world.getWorld().playSound(l, s, cat, volume, pitch);
    }

    public static boolean isPlayer(Entity e) {
        return e instanceof EntityPlayer;
    }

    public static boolean creative(EntityHuman h) {
        return h.abilities.canInstantlyBuild;
    }

    public static int sweepLevel(SmeEnchantment e, EntityLiving ent) {
        return EnchantmentManager.a(e, ent);
    }

    public static Enchantment fireProt() {
        return Enchantments.PROTECTION_FIRE;
    }
}
