package chat.jaspr.disasters;

/**
 * One running disaster.
 *
 * Exactly one of these exists at a time, server-wide. Every implementation is expected to be
 * cheap: bounded in duration, centred on a single player, and able to undo anything that would
 * otherwise outlive it.
 */
interface Disaster {
    /** Advances the event. Called from the plugin's single shared loop every few ticks. */
    void tick(long nowTicks);

    /** True once the event has run its course and the slot can be released. */
    boolean isFinished();

    /** Ends the event early and restores anything it changed. Safe to call twice. */
    void cancel();

    /** The player this event is happening to. */
    String targetName();
}
