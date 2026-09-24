package chat.jaspr.apocalypse;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.craftbukkit.v1_12_R1.entity.CraftEntity;
import org.bukkit.craftbukkit.v1_12_R1.entity.CraftZombie;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Zombie;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.util.Vector;
import net.minecraft.server.v1_12_R1.AxisAlignedBB;

/** Velocity-only traversal: native server collision/gravity move the zombie. No teleports. */
public final class SiegeTraversal {
    public static final String CLIMBER_TAG="jaspr_siege_wall_climber";
    private static final String PILLARS_TAG="jaspr_siege_pillars_";
    static final class State {
        Location step, column;
        long until, nextJump, sessionUntil, transferUntil;
        double baseY;
        int placed, detourTicks, columnTicks;
        void cancel() { step=null; }
        void endPursuit() { cancel(); column=null; sessionUntil=0; nextJump=0; transferUntil=0; detourTicks=0; placed=0; columnTicks=0; }
    }
    static final class Budget {
        int edits, motions;
        Budget(int edits,int motions) { this.edits=edits; this.motions=motions; }
    }
    private final int maxRise, maxWallRise, maxPillars, attemptTicks;
    long pillars, wallSteps;
    static void restoreCount(Zombie z,State state) {
        for(String tag:z.getScoreboardTags())if(tag.startsWith(PILLARS_TAG)) {
            try { state.placed=Math.max(state.placed,SiegeRules.clamp(Integer.parseInt(tag.substring(PILLARS_TAG.length())),0,24)); }
            catch(NumberFormatException ignored) { state.placed=24; }
        }
    }
    private static void saveCount(Zombie z,State state) {
        for(String tag:new java.util.ArrayList<>(z.getScoreboardTags()))if(tag.startsWith(PILLARS_TAG))z.removeScoreboardTag(tag);
        z.addScoreboardTag(PILLARS_TAG+state.placed);
    }
    SiegeTraversal(FileConfiguration config) {
        maxRise=SiegeRules.clamp(config.getInt("siege.max-traversal-rise",16),2,24);
        maxWallRise=SiegeRules.clamp(config.getInt("siege.max-wall-climb-rise",48),16,64);
        maxPillars=SiegeRules.clamp(config.getInt("siege.max-pillar-blocks-per-zombie",16),1,24);
        attemptTicks=SiegeRules.clamp(config.getInt("siege.traversal-attempt-ticks",600),40,1200);
    }
    static boolean loaded(Location p) {
        return p!=null && p.getWorld()!=null && Double.isFinite(p.getX()) && Double.isFinite(p.getY())
                && Double.isFinite(p.getZ()) && p.getY()>1 && p.getY()<253
                && p.getWorld().isChunkLoaded(p.getBlockX()>>4,p.getBlockZ()>>4);
    }
    static boolean grief(World w) { return "true".equalsIgnoreCase(w.getGameRuleValue("mobGriefing")); }
    private static AxisAlignedBB box(Zombie z,double x,double y,double zz) {
        AxisAlignedBB b=((CraftZombie)z).getHandle().getBoundingBox();
        return new AxisAlignedBB(b.a+x,b.b+y+.001,b.c+zz,b.d+x,b.e+y,b.f+zz);
    }
    private static boolean loadedBox(World w,AxisAlignedBB b) {
        if(b.b<1||b.e>254)return false;
        // getCubes and entity queries inspect neighboring blocks/chunks as well.
        for(int x=((int)Math.floor(b.a-2))>>4;x<=((int)Math.floor(b.d+2))>>4;x++)
            for(int z=((int)Math.floor(b.c-2))>>4;z<=((int)Math.floor(b.f+2))>>4;z++)
                if(!w.isChunkLoaded(x,z))return false;
        return true;
    }
    static boolean clear(Zombie z,double dx,double dy,double dz) {
        AxisAlignedBB b=box(z,dx,dy,dz);
        return loadedBox(z.getWorld(),b) && ((CraftZombie)z).getHandle().world.getCubes(((CraftZombie)z).getHandle(),b).isEmpty();
    }
    private static boolean vacant(Zombie z,Block block) {
        AxisAlignedBB cube=new AxisAlignedBB(block.getX(),block.getY(),block.getZ(),block.getX()+1,block.getY()+1,block.getZ()+1);
        if(!loadedBox(z.getWorld(),cube))return false;
        // Explicit entity bounds also include this zombie: placing into its feet is forbidden.
        if(((CraftZombie)z).getHandle().getBoundingBox().c(cube))return false;
        for(Entity e:z.getWorld().getNearbyEntities(block.getLocation().add(.5,.5,.5),1.5,2.5,1.5))
            if(e instanceof LivingEntity && ((CraftEntity)e).getHandle().getBoundingBox().c(cube))return false;
        return true;
    }
    private boolean purposeful(Zombie z,Location goal,State s,long tick) {
        if(!loaded(goal)||goal.getWorld()!=z.getWorld())return false;
        Location at=z.getLocation();
        double dx=goal.getX()-at.getX(),dz=goal.getZ()-at.getZ();
        if(goal.getY()<=at.getY()+.65 || dx*dx+dz*dz>36)return false;
        if(s.sessionUntil==0) { s.baseY=at.getY(); s.sessionUntil=tick+attemptTicks; }
        return tick<s.sessionUntil && at.getY()<s.baseY+maxRise && goal.getY()<=s.baseY+maxRise+1;
    }
    /** Called only with a still-valid awareness memory, including between awareness scans. */
    boolean move(Zombie z,Location goal,State s,long tick,Budget budget) {
        if(budget.motions--<=0)return false;
        Location at=z.getLocation();
        if(!loaded(at)||!loaded(goal)||goal.getWorld()!=z.getWorld()) { s.cancel(); halt(z); return false; }
        boolean onColumn=s.column!=null&&tick<s.sessionUntil
                && Math.abs(at.getX()-s.column.getX()-.5)<.8&&Math.abs(at.getZ()-s.column.getZ()-.5)<.8
                && at.getY()>=s.column.getY()+.75&&at.getY()<=s.column.getY()+2.6;
        // Pillar jumps are plugin-authored movement. Do not let their repeated short
        // arcs accumulate fall damage and incorrectly drive the gore renderer to its
        // most severe living-dismemberment stage.
        if(s.step!=null||s.transferUntil>tick||onColumn)z.setFallDistance(0);
        boolean up=purposeful(z,goal,s,tick);
        if(s.step!=null) {
            if(goal.getY()<s.step.getY()+1 || tick>=s.sessionUntil || tick>s.until || !grief(z.getWorld()) || !loaded(s.step)
                    || Math.abs(at.getX()-s.step.getX()-.5)>.22 || Math.abs(at.getZ()-s.step.getZ()-.5)>.22) s.cancel();
            else {
                // Jump first, wait until the entire body has physically cleared the future block.
                halt(z);
                if(at.getY()>=s.step.getY()+1.001 && budget.edits>0) {
                    Block b=s.step.getBlock();
                    if(b.getType()==Material.AIR && vacant(z,b)) {
                        budget.edits--; // Cancelled attempts consume the same global work budget.
                        EntityChangeBlockEvent event=new EntityChangeBlockEvent(z,b,Material.COBBLESTONE,(byte)0);
                        Bukkit.getPluginManager().callEvent(event);
                        if(!event.isCancelled() && loaded(b.getLocation()) && grief(z.getWorld()) && b.getType()==Material.AIR && vacant(z,b)) {
                            b.setType(Material.COBBLESTONE,false); s.column=b.getLocation(); s.placed++; s.columnTicks=0; pillars++;z.setFallDistance(0);saveCount(z,s);
                        }
                    }
                    s.cancel(); s.nextJump=tick+12;
                }
                return true;
            }
        }
        Vector toward=goal.toVector().subtract(at.toVector()).setY(0);
        double distance=toward.length();
        if(distance>.05)toward.multiply(1/distance);
        boolean climber=z.getScoreboardTags().contains(CLIMBER_TAG);
        if(s.transferUntil>tick) {
            if(!z.isOnGround()) { z.setVelocity(toward.clone().multiply(.22*pace(z)).setY(z.getVelocity().getY())); return true; }
            s.transferUntil=0; s.column=null;
        }
        if(s.column!=null && goal.getY()<=s.column.getY()+1.05 && tick<s.sessionUntil) {
            // Land on the completed pillar before the horizontal roof jump; otherwise momentum
            // walks off its edge while the last pillar jump is already descending.
            if(!z.isOnGround() || tick<s.nextJump) { halt(z); return true; }
            if(distance>.6 && distance<=6 && clear(z,0,1.25,0)) {
                for(double d=.75;d<=3;d+=.25) {
                    Location landing=at.clone().add(toward.clone().multiply(d));landing.setY(s.column.getY()+1);
                    Location support=landing.clone().add(0,-.1,0);
                    if(loaded(support)&&support.getBlock().getType().isSolid()
                            && (support.getBlockX()!=s.column.getBlockX()||support.getBlockZ()!=s.column.getBlockZ())
                            && clear(z,toward.getX()*d,0,toward.getZ()*d)) {
                        s.transferUntil=tick+18;s.columnTicks=0;z.setFallDistance(0);z.setVelocity(toward.clone().multiply(.22*pace(z)).setY(.42));return true;
                    }
                }
            }
            // Nowhere to step across to. Wait a beat in case the target moves into reach, then
            // come down and hunt normally instead of standing on the plinth indefinitely.
            if(++s.columnTicks<60) { halt(z); return true; }
            abandonColumn(z,s,at);
        }
        if(s.column!=null && goal.getY()>s.column.getY()+1.05 && tick<s.sessionUntil
                && (!z.isOnGround() || tick<s.nextJump)) {
            halt(z); return true;
        }
        if(s.column!=null && goal.getY()>s.column.getY()+1.05 && (s.placed>=maxPillars||tick>=s.sessionUntil)) {
            // Out of blocks, or out of time, on a column that never got there. This branch used
            // to halt every tick for as long as the goal stayed overhead, which is exactly the
            // "climbs one block then stands there doing nothing" behaviour.
            abandonColumn(z,s,at);
        }
        // A climber must touch a real wall toward its goal. It cannot climb air or pass a lip.
        boolean wallGoal=climber && goal.getY()>at.getY()-.1 && distance<=6
                && s.sessionUntil>tick && at.getY()<s.baseY+maxWallRise;
        if(wallGoal && distance>.3 && !clear(z,toward.getX()*.42,0,toward.getZ()*.42)
                && clear(z,0,.28,0)) {
            // Daylight-sluggish hunters approach the wall slower; the lift stays full so climbs still land.
            z.setVelocity(toward.multiply(.10*pace(z)).setY(.22)); z.setFallDistance(0); wallSteps++;
            return true;
        }
        // Build only when building is the answer: something is genuinely in the way, or the
        // target is more or less straight overhead. Towering up in open ground, where walking
        // would have worked, was both pointless and how zombies stranded themselves.
        boolean blockedAhead=distance>.6 && !clear(z,toward.getX()*.42,0,toward.getZ()*.42);
        boolean overhead=distance<=2.2;
        if(up && (blockedAhead||overhead) && z.isOnGround() && tick>=s.nextJump
                && s.placed<maxPillars && grief(z.getWorld())) {
            double cx=at.getBlockX()+.5-at.getX(),cz=at.getBlockZ()+.5-at.getZ();
            // A footprint wholly inside one column avoids edge suffocation and sideways stair spam.
            if(Math.abs(cx)>.12||Math.abs(cz)>.12) {
                if(clear(z,cx,0,cz))z.setVelocity(new Vector(cx*.3*pace(z),z.getVelocity().getY(),cz*.3*pace(z)));
                return true;
            }
            if(Math.abs(at.getY()-at.getBlockY())<.05 && at.getBlock().getType()==Material.AIR
                    && clear(z,0,1.25,0) && clear(z,0,.6,0)) {
                s.step=at.getBlock().getLocation(); s.until=tick+16; s.nextJump=tick+24;z.setFallDistance(0);
                z.setVelocity(new Vector(0,.46,0)); return true;
            }
        }
        // The attribute already carries the daylight halving, but the clamp would
        // undo it: a Concrete Breacher walks .20, which is .13 after the cut and
        // .0845 after the .65 gait, and a fixed floor of .10 would quietly hand it
        // back its full speed. Scaling the bounds by the same factor keeps the
        // clamp doing its job without cancelling the law.
        double pace=pace(z);
        double speed=SiegeRules.clamp(z.getAttribute(org.bukkit.attribute.Attribute.GENERIC_MOVEMENT_SPEED).getValue()*.65,.10*pace,.23*pace);
        Vector v=toward.multiply(Math.min(speed,distance));
        double y=z.getVelocity().getY();
        if(clear(z,v.getX(),0,v.getZ())) { s.detourTicks=0; z.setVelocity(v.setY(y)); }
        else if(z.isOnGround() && clear(z,0,1.1,0) && clear(z,v.getX()*3,1.1,v.getZ()*3))z.setVelocity(v.setY(.42));
        else if(z.isOnGround() && s.detourTicks++<40) {
            // A short, deterministic wall-following detour can round a protected obstacle.
            // Exhaustion returns to the breacher; there is no expanding path search or chunk cache.
            double sign=(z.getUniqueId().hashCode()&1)==0?1:-1;
            Vector side=new Vector(-v.getZ()*sign,0,v.getX()*sign);
            if(clear(z,side.getX(),0,side.getZ()))z.setVelocity(side.setY(y));else halt(z);
        } else halt(z);
        return false;
    }
    /**
     * Steps off a column that is not working out and goes back to hunting on the ground.
     * The pillar allowance is returned at the same time: it is spent per attempt, not per life,
     * so a zombie that gave up once is not permanently unable to climb anything ever again.
     */
    private static void abandonColumn(Zombie z,State s,Location at) {
        s.cancel(); s.column=null; s.sessionUntil=0; s.transferUntil=0;
        s.columnTicks=0; s.detourTicks=0; s.placed=0; s.baseY=at.getY(); saveCount(z,s);
    }
    // Only when it is actually moving sideways: setVelocity marks the entity dirty and sends a velocity packet to
    // every tracking player, so re-halting a stopped zombie each tick flooded clients (~20 packets/s per zombie).
    static void halt(Zombie z) { Vector v=z.getVelocity(); if(Math.abs(v.getX())>1.0E-3||Math.abs(v.getZ())>1.0E-3) z.setVelocity(new Vector(0,v.getY(),0)); }
    /** Daylight pace for the hard-coded chase velocities, which no attribute reaches. */
    private static double pace(Zombie z) { return SiegeRules.daylightFactor(z.getWorld()); }
}
