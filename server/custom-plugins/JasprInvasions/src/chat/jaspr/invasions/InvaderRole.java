package chat.jaspr.invasions;

/**
 * What an invader does when the direct route to its target is blocked.
 *
 * Vanilla mobs give up the moment a wall is in the way. These roles are what make an invasion feel
 * like a siege rather than a mob spawn: someone digs, someone climbs, someone shoots.
 */
enum InvaderRole {
    /** Walks in and hits things. The bulk of every wave. */
    GRUNT,
    /** Digs through whatever is between it and its target. */
    MINER,
    /** Pillars upward to reach a target standing above it. */
    SOLDIER,
    /** Keeps its distance and shoots. Assigned automatically to bow users. */
    RANGED
}
