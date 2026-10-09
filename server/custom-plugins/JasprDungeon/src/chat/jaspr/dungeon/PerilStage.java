package chat.jaspr.dungeon;

import java.util.UUID;

/**
 * Generation 7: what a room's perils may do to the world, and nothing more. PerilRoom decides (when to warn, when to strike,
 * what to restore); a stage acts. The live stage (PerilWorld) talks to Bukkit; PerilsAuditTest's stage records every call and
 * plays the falling blocks itself, so the timing, the caps and the restoration are proven without a server. Block values are
 * packed legacy ids (id | data << 12), exactly as DungeonGenerator draws them.
 */
interface PerilStage {
    /** Particle looks: the live stage maps each to a Bukkit particle. */
    enum Fx { DUST, CRACKLE, SHADOW, STEAM, SMOKE, FLAME, LAVA_POP, LAVA_DRIP, IMPACT }
    /** Sound cues: the live stage maps each to a Bukkit sound. */
    enum Snd { CRACK, RUMBLE, THUD, CRUMBLE, BREAK, MEND, HISS, ERUPT, FLARE, POP, REGROW }
    /** What occupies a cell's space: an eligible player, any other player (Creative, Spectator), another living body. */
    int ELIGIBLE = 1, PLAYER = 2, MOB = 4;

    /** A player who may be hurt, where they stand this tick. */
    final class Body {
        final UUID id;final double x, y, z;
        Body(UUID id, double x, double y, double z) { this.id = id; this.x = x; this.y = y; this.z = z; }
    }

    /** The legacy block now at the cell, or -1 when its chunk is not loaded. */
    int get(int x, int y, int z);
    /** Writes a block without physics; false when its chunk is not loaded. */
    boolean set(int x, int y, int z, int packed);
    /** ELIGIBLE | PLAYER | MOB bits of whoever overlaps the cell's space (0 when it is clear). */
    int occupants(int x, int y, int z);
    /** A falling block that starts in this cell and never places itself; its handle, or null when refused. */
    Object drop(int x, int y, int z, int packed);
    /** Ends a falling block early. */
    void discard(Object handle);
    void fx(Fx fx, double x, double y, double z, int count, double spread, int block);
    void sound(Snd snd, double x, double y, double z, float volume, float pitch);
    /** Damage from a peril at cell (x, z); true when it was applied. */
    boolean hurt(UUID who, double amount, PerilMarks.Kind source, int x, int z);
    void burn(UUID who, int ticks);
    void toss(UUID who, double vx, double vy, double vz);
    /** Raises every eligible player overlapping the cell onto its top (the way out of a pit that will not let go). */
    void lift(int x, int y, int z);
    /** A peril could not act as the generator drew it (said once per room and reason). */
    void failed(PerilMarks.Kind kind, String reason);
    /** A phase of a peril (warn, strike, restore) at cell (x, z): diagnostics and the audit's timing record. */
    void note(PerilMarks.Kind kind, String phase, int x, int z);
}
