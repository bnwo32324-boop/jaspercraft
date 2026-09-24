package chat.jaspr.invasions;

import org.bukkit.Effect;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Creature;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

/**
 * What separates an invasion from a mob spawn.
 *
 * Vanilla hostiles forget you the moment you break line of sight and give up entirely at a wall.
 * These three behaviours - always knowing where you are, digging through what is in the way, and
 * building up to what is above - are the reason an invasion feels like something hunting you
 * rather than something wandering past. All of it runs on a slow shared timer rather than every
 * tick, because a server that stutters is not scary, it is just broken.
 */
final class InvaderBrain {
    private final InvasionConfig settings;
    private final BlockRestorer restorer;

    InvaderBrain(InvasionConfig settings, BlockRestorer restorer) {
        this.settings = settings;
        this.restorer = restorer;
    }

    /** Runs one invader for one beat. Returns false if it should be dropped from the roster. */
    boolean tick(Invader invader, Player owner, ActiveInvasion invasion, long nowTick) {
        LivingEntity mob = invader.entity;
        if (mob == null || !mob.isValid() || mob.isDead()) return false;

        World world = mob.getWorld();
        if (!world.equals(owner.getWorld())) return false;

        double distanceSquared = mob.getLocation().distanceSquared(owner.getLocation());
        if (distanceSquared > (double) settings.leashRange * settings.leashRange) {
            // Wandered out of the siege entirely. Let it go rather than dragging it back.
            mob.remove();
            return false;
        }

        omniscience(mob, owner, distanceSquared);

        boolean stuck = invader.updateStuck(0.35d);
        if (!stuck || distanceSquared < 6.25d) return true;
        if (nowTick < invader.nextActionTick) return true;

        if (invader.role == InvaderRole.MINER && settings.minersEnabled && settings.breakBlocks) {
            // Miners dig twice as fast: half the configured cooldown between blocks.
            if (dig(invader, owner, nowTick)) invader.nextActionTick = nowTick + Math.max(5L, settings.minerCooldownTicks / 2L);
        } else if (invader.role == InvaderRole.SOLDIER && settings.soldiersEnabled) {
            if (pillar(invader, owner, invasion)) invader.nextActionTick = nowTick + settings.soldierCooldownTicks;
        }
        return true;
    }

    /**
     * The invader always knows where its target is. It does not steal aggression from a player who
     * has waded in to help, though - if it is already fighting someone, it keeps fighting them.
     */
    private void omniscience(LivingEntity mob, Player owner, double distanceSquared) {
        if (!(mob instanceof Creature)) return;
        if (distanceSquared > (double) settings.omniscienceRange * settings.omniscienceRange) return;
        Creature creature = (Creature) mob;
        LivingEntity current = creature.getTarget();
        if (current instanceof Player && current.isValid() && !current.isDead()) return;
        creature.setTarget(owner);
    }

    // ------------------------------------------------------------------ miners

    /** Chews a hole toward the target: through the wall, up through a ceiling, or down a floor. */
    private boolean dig(Invader invader, Player owner, long nowTick) {
        LivingEntity mob = invader.entity;
        Location from = mob.getLocation();
        Vector toward = owner.getLocation().toVector().subtract(from.toVector());
        double verticalGap = toward.getY();
        toward.setY(0.0d);
        if (toward.lengthSquared() < 0.0001d) toward = new Vector(1.0d, 0.0d, 0.0d);
        toward.normalize();

        Block feet = from.clone().add(toward).getBlock();
        Block head = feet.getRelative(0, 1, 0);

        boolean broke = breakBlock(feet, nowTick);
        broke |= breakBlock(head, nowTick);

        if (!broke) {
            // Nothing ahead gave way, so the way through is up or down instead.
            if (verticalGap > 2.0d) {
                broke = breakBlock(from.getBlock().getRelative(0, 2, 0), nowTick);
            } else if (verticalGap < -2.0d) {
                broke = breakBlock(from.getBlock().getRelative(0, -1, 0), nowTick);
            }
        }
        return broke;
    }

    private boolean breakBlock(Block block, long nowTick) {
        if (block == null) return false;
        Material type = block.getType();
        if (type == Material.AIR) return false;
        if (block.getY() < 1 || block.getY() > 254) return false;
        if (settings.unbreakable.contains(type)) return false;
        // Storage, spawners and anything else with contents are never destroyed by an invasion.
        if (block.getState() instanceof org.bukkit.inventory.InventoryHolder) return false;
        if (block.getState() instanceof org.bukkit.block.CreatureSpawner) return false;

        restorer.record(block, nowTick);
        try {
            block.getWorld().playEffect(block.getLocation(), Effect.STEP_SOUND, type);
        } catch (Throwable cosmeticOnly) {
            // Particles are decoration.
        }
        block.setType(Material.AIR, false);
        return true;
    }

    // ------------------------------------------------------------------ soldiers

    /** Builds a column under itself to climb to a target standing above it. */
    private boolean pillar(Invader invader, Player owner, ActiveInvasion invasion) {
        LivingEntity mob = invader.entity;
        Location at = mob.getLocation();
        if (owner.getLocation().getY() - at.getY() < 2.0d) return false;
        if (invader.pillarsPlaced >= settings.maxPillarHeight) return false;

        Block feet = at.getBlock();
        if (feet.getType() != Material.AIR) return false;
        Block above = feet.getRelative(0, 1, 0);
        Block clearance = feet.getRelative(0, 2, 0);
        if (above.getType() != Material.AIR || clearance.getType() != Material.AIR) return false;
        if (feet.getY() >= 253) return false;

        feet.setType(Material.COBBLESTONE, false);
        invasion.notePillar(feet.getLocation());
        invader.pillarsPlaced++;
        mob.teleport(at.clone().add(0.0d, 1.0d, 0.0d));
        return true;
    }
}
