package chat.jaspr.dungeon;

import java.util.*;
import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.block.Block;
import org.bukkit.entity.*;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.material.MaterialData;
import org.bukkit.util.Vector;

/**
 * Generation 7: the live stage of one room's perils: PerilRoom decides, this acts in the run's world. It never reads a block
 * from an unloaded chunk (that would load it), writes blocks without physics, spawns falling blocks that never place
 * themselves (Perils cancels their landing), and hurts, burns or throws only players Perils calls eligible: alive, in
 * Survival or Adventure, in this very room and outside the arrival circle. A rejected particle or sound never stops a room.
 */
final class PerilWorld implements PerilStage {
    /** Blocks a falling stalactite is never drawn as: bedrock, spawners, portals, command and structure blocks, barriers. */
    private static final Set<Integer> NOT_ROCK = new HashSet<>(Arrays.asList(7, 52, 119, 120, 137, 166, 209, 210, 211, 217, 255));
    private final Perils owner;private final Encounters.Run run;private final World world;

    PerilWorld(Perils owner, Encounters.Run run) { this.owner = owner;this.run = run;world = run.world; }

    /** The room's own rock if it is a plain solid block, else stone: a falling stalactite is always a full block. */
    static Material material(int packed) {
        int id = packed & 4095;Material m = id == 0 || NOT_ROCK.contains(id) ? null : Material.getMaterial(id);
        return m == null || !m.isBlock() || !m.isSolid() || !m.isOccluding() ? Material.STONE : m;
    }
    /** The data the material keeps from the packed block (stone's own when it stood in for another block). */
    static byte data(int packed, Material m) { return (byte) (m.getId() == (packed & 4095) ? (packed >>> 12) & 15 : 0); }
    private static MaterialData dust(int packed) { Material m = material(packed);return new MaterialData(m, data(packed, m)); }
    private boolean loaded(int x, int z) { return world.isChunkLoaded(x >> 4, z >> 4); }

    // ---------------------------------------------------------------- blocks and bodies
    @Override public int get(int x, int y, int z) {
        if (!loaded(x, z)) return -1;
        Block b = world.getBlockAt(x, y, z);return b.getTypeId() | ((b.getData() & 15) << 12);
    }
    @Override public boolean set(int x, int y, int z, int packed) {
        if (!loaded(x, z)) return false;
        world.getBlockAt(x, y, z).setTypeIdAndData(packed & 4095, (byte) ((packed >>> 12) & 15), false);return true;
    }
    @Override public int occupants(int x, int y, int z) {
        int out = 0;
        for (Entity e : world.getNearbyEntities(new Location(world, x + .5, y + .5, z + .5), .5, .5, .5)) {
            if (!(e instanceof LivingEntity)) continue;
            out |= e instanceof Player ? (owner.eligible((Player) e, run) ? ELIGIBLE : PLAYER) : MOB;
        }
        return out;
    }
    @Override public Object drop(int x, int y, int z, int packed) {
        if (!loaded(x, z) || owner.flying() >= Perils.MAX_FLYING) return null;
        Material m = material(packed);
        try {
            FallingBlock f = world.spawnFallingBlock(new Location(world, x + .5, y, z + .5), m, data(packed, m));
            if (f == null) return null;
            if (!f.isValid()) { f.remove();return null; }
            f.setDropItem(false);f.setHurtEntities(false);f.addScoreboardTag(Perils.TAG);
            if (!owner.track(f, run)) { f.remove();return null; }
            return f;
        } catch (RuntimeException ex) { failed(PerilMarks.Kind.STALACTITE, "spawn-" + ex.getClass().getSimpleName());return null; }
    }
    @Override public void discard(Object handle) {
        if (!(handle instanceof Entity)) return;
        owner.untrack((Entity) handle);((Entity) handle).remove();
    }

    // ---------------------------------------------------------------- looks and sounds
    /** The particle each look is drawn with (a look made of block dust takes the block's material as its data). */
    static Particle particle(Fx fx) {
        switch (fx) {
            case DUST: return Particle.FALLING_DUST;
            case CRACKLE: return Particle.BLOCK_CRACK;
            case SHADOW: case STEAM: return Particle.CLOUD;
            case SMOKE: return Particle.SMOKE_LARGE;
            case FLAME: return Particle.FLAME;
            case LAVA_POP: return Particle.LAVA;
            case LAVA_DRIP: return Particle.DRIP_LAVA;
            default: return Particle.EXPLOSION_NORMAL;
        }
    }
    static Sound sound(Snd snd) {
        switch (snd) {
            case CRACK: case BREAK: return Sound.BLOCK_STONE_BREAK;
            case RUMBLE: case CRUMBLE: return Sound.BLOCK_GRAVEL_BREAK;
            case THUD: return Sound.BLOCK_STONE_FALL;
            case MEND: case REGROW: return Sound.BLOCK_STONE_PLACE;
            case HISS: return Sound.BLOCK_FIRE_EXTINGUISH;
            case ERUPT: return Sound.BLOCK_LAVA_EXTINGUISH;
            case FLARE: return Sound.ENTITY_BLAZE_SHOOT;
            default: return Sound.BLOCK_LAVA_POP;
        }
    }
    private void spawn(Fx fx, double x, double y, double z, int count, double ox, double oy, double oz, double extra, int block) {
        Particle p = particle(fx);
        if (p.getDataType() == MaterialData.class) world.spawnParticle(p, x, y, z, count, ox, oy, oz, extra, dust(block));
        else world.spawnParticle(p, x, y, z, count, ox, oy, oz, extra);
    }
    @Override public void fx(Fx fx, double x, double y, double z, int count, double spread, int block) {
        try {
            switch (fx) {
                case DUST: spawn(fx, x, y, z, Math.max(1, count), spread, .05, spread, 0, block);break;
                case CRACKLE: spawn(fx, x, y, z, count * 4, spread, .05, spread, .1, block);break;
                case SHADOW: spawn(fx, x, y, z, count, spread, .02, spread, 0, block);break;
                case STEAM: spawn(fx, x, y, z, count, spread, .15, spread, .06, block);break;
                case SMOKE: spawn(fx, x, y, z, count, spread, .1, spread, .02, block);break;
                case FLAME: spawn(fx, x, y, z, count, spread, .15, spread, .03, block);break;
                case LAVA_POP: spawn(fx, x, y, z, count, spread, .05, spread, 0, block);break;
                case LAVA_DRIP: spawn(fx, x, y, z, count, spread, .1, spread, 0, block);break;
                default:
                    spawn(fx, x, y, z, count, spread, .1, spread, .02, block);
                    spawn(Fx.CRACKLE, x, y + .2, z, count * 3, spread, .15, spread, .1, block);
            }
        } catch (RuntimeException ex) { failed(null, "fx-" + ex.getClass().getSimpleName()); }
    }
    @Override public void sound(Snd snd, double x, double y, double z, float volume, float pitch) {
        try { world.playSound(new Location(world, x, y, z), sound(snd), volume, pitch); } catch (RuntimeException ex) { failed(null, "sound-" + ex.getClass().getSimpleName()); }
    }

    // ---------------------------------------------------------------- what happens to players
    /** A single blow never takes more than 85% of a full health bar: a peril alone does not kill from full health. */
    @Override public boolean hurt(UUID who, double amount, PerilMarks.Kind source, int x, int z) {
        Player p = Bukkit.getPlayer(who);
        if (!owner.eligible(p, run)) return false;
        Location l = p.getLocation();
        if (HazardCatalog.safe(run.room, l.getX(), l.getZ())) return false;
        double most = p.getAttribute(Attribute.GENERIC_MAX_HEALTH) == null ? 20 : p.getAttribute(Attribute.GENERIC_MAX_HEALTH).getValue();
        double dealt = Math.min(amount, Math.max(1, most * .85));
        p.damage(dealt);owner.hit(run, p, source, dealt);return true;
    }
    @Override public void burn(UUID who, int ticks) {
        Player p = Bukkit.getPlayer(who);
        if (owner.eligible(p, run)) p.setFireTicks(Math.max(p.getFireTicks(), ticks));
    }
    @Override public void toss(UUID who, double vx, double vy, double vz) {
        Player p = Bukkit.getPlayer(who);
        if (owner.eligible(p, run)) p.setVelocity(new Vector(vx, vy, vz));
    }
    @Override public void lift(int x, int y, int z) {
        for (Entity e : world.getNearbyEntities(new Location(world, x + .5, y + .5, z + .5), .5, .5, .5)) {
            if (!(e instanceof Player) || !owner.eligible((Player) e, run)) continue;
            Player p = (Player) e;Location l = p.getLocation();
            p.teleport(new Location(world, x + .5, y + 1, z + .5, l.getYaw(), l.getPitch()), PlayerTeleportEvent.TeleportCause.PLUGIN);p.setFallDistance(0);p.setVelocity(new Vector());
        }
    }

    // ---------------------------------------------------------------- diagnostics
    @Override public void failed(PerilMarks.Kind kind, String reason) { owner.failure(run.key, kind, reason); }
    @Override public void note(PerilMarks.Kind kind, String phase, int x, int z) {}
}
