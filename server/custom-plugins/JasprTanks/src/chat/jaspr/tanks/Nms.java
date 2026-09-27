package chat.jaspr.tanks;

import java.lang.reflect.Field;
import net.minecraft.server.v1_12_R1.AxisAlignedBB;
import net.minecraft.server.v1_12_R1.EntityLiving;
import net.minecraft.server.v1_12_R1.EntityTNTPrimed;
import net.minecraft.server.v1_12_R1.IScoreboardCriteria;
import net.minecraft.server.v1_12_R1.PacketPlayOutMount;
import net.minecraft.server.v1_12_R1.PacketPlayOutScoreboardObjective;
import net.minecraft.server.v1_12_R1.PacketPlayOutScoreboardScore;
import net.minecraft.server.v1_12_R1.PlayerConnection;
import net.minecraft.server.v1_12_R1.Scoreboard;
import net.minecraft.server.v1_12_R1.ScoreboardObjective;
import net.minecraft.server.v1_12_R1.ScoreboardScore;
import org.bukkit.Location;
import org.bukkit.craftbukkit.v1_12_R1.CraftWorld;
import org.bukkit.craftbukkit.v1_12_R1.entity.CraftEntity;
import org.bukkit.craftbukkit.v1_12_R1.entity.CraftPlayer;
import org.bukkit.craftbukkit.v1_12_R1.entity.CraftTNTPrimed;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.TNTPrimed;

/** The few Paper 1.12.2 internals Bukkit does not expose. Every call fails soft. */
final class Nms {
    private Nms() {}

    static final float VANILLA_STEP = 0.6f;
    private static Field mountVehicle, mountPassengers, tntSource;

    /** Server copy of the step height, so the movement check replays the client's 1-block steps exactly. */
    static void stepHeight(Player player, float height) {
        try { ((CraftPlayer) player).getHandle().P = height; } catch (Throwable ignored) { }
    }

    /**
     * Tells one viewer that {@code passengers} ride {@code vehicle}. Only clients learn this: on the server the
     * tank parts stay independent entities, so plugin teleports (which refuse players carrying passengers)
     * keep working, while every client pins the parts to the player's own interpolated position.
     */
    static boolean mount(Player viewer, Entity vehicle, int[] passengers) {
        try {
            if (mountVehicle == null) {
                mountVehicle = PacketPlayOutMount.class.getDeclaredField("a");
                mountPassengers = PacketPlayOutMount.class.getDeclaredField("b");
                mountVehicle.setAccessible(true);
                mountPassengers.setAccessible(true);
            }
            PacketPlayOutMount packet = new PacketPlayOutMount();
            mountVehicle.setInt(packet, vehicle.getEntityId());
            mountPassengers.set(packet, passengers.clone());
            ((CraftPlayer) viewer).getHandle().playerConnection.sendPacket(packet);
            return true;
        } catch (Throwable e) {
            return false;
        }
    }

    /** Credits the shell's explosion to its driver (death messages, PvP rules, kill statistics). */
    static void source(TNTPrimed tnt, Player shooter) {
        try {
            if (tntSource == null) {
                tntSource = EntityTNTPrimed.class.getDeclaredField("source");
                tntSource.setAccessible(true);
            }
            EntityLiving living = ((CraftPlayer) shooter).getHandle();
            tntSource.set(((CraftTNTPrimed) tnt).getHandle(), living);
        } catch (Throwable ignored) { }
    }

    /** Explosion owned by {@code source}, so EntityExplodeEvent and damage events name the shell. */
    static boolean explode(Entity source, Location at, float power, boolean fire) {
        try {
            ((CraftWorld) at.getWorld()).getHandle().createExplosion(((CraftEntity) source).getHandle(),
                at.getX(), at.getY(), at.getZ(), power, fire, true);
            return true;
        } catch (Throwable e) {
            return false;
        }
    }

    /**
     * Adds (or removes) a hidden scoreboard objective on one client only, holding a single "cooldown" score.
     * Vanilla syncs only objectives that sit in a display slot, so the browser learns about tanks from these
     * two packets; the server keeps no scoreboard state. Clients refuse a second add of the same name, so
     * callers send each add once per connection and remove it before sending again.
     */
    static boolean advertise(Player player, String name, String display, int cooldown, boolean add) {
        try {
            Scoreboard board = new Scoreboard();
            ScoreboardObjective objective = board.registerObjective(name, IScoreboardCriteria.criteria.get("dummy"));
            objective.setDisplayName(display);
            PlayerConnection connection = ((CraftPlayer) player).getHandle().playerConnection;
            if (connection == null) return false;
            if (!add) {
                connection.sendPacket(new PacketPlayOutScoreboardObjective(objective, 1));
                return true;
            }
            ScoreboardScore score = board.getPlayerScoreForObjective("cooldown", objective);
            score.setScore(cooldown);
            connection.sendPacket(new PacketPlayOutScoreboardObjective(objective, 0));
            connection.sendPacket(new PacketPlayOutScoreboardScore(score));
            return true;
        } catch (Throwable e) {
            return false;
        }
    }

    /**
     * Moves a tank part even while it carries riders (Bukkit refuses to teleport vehicles). The world tick then
     * re-seats the riders on it.
     */
    static void move(Entity entity, Location at) {
        try { ((CraftEntity) entity).getHandle().setPositionRotation(at.getX(), at.getY(), at.getZ(), 0f, 0f); }
        catch (Throwable e) { entity.teleport(at); }
    }

    /** {minX,minY,minZ,maxX,maxY,maxZ} or null. */
    static double[] box(Entity entity) {
        try {
            AxisAlignedBB b = ((CraftEntity) entity).getHandle().getBoundingBox();
            return new double[] {b.a, b.b, b.c, b.d, b.e, b.f};
        } catch (Throwable e) {
            return null;
        }
    }
}
