package chat.jaspr.enchant.util;

/** Runs one enchantment's logic so a failure in it never breaks combat or other enchantments. */
public final class Guard {
    private Guard() {}

    public interface Body {
        void run() throws Throwable;
    }

    public static void run(String where, Body body) {
        try {
            body.run();
        } catch (Throwable t) {
            Log.error(where, t);
        }
    }
}
