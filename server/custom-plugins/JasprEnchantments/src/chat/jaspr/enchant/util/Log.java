package chat.jaspr.enchant.util;

import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Privacy-safe logging: messages carry enchantment/handler names, entity types and counts only
 * (never player addresses, UUIDs of real people, chat or tokens). Repeated failures are throttled.
 */
public final class Log {
    private Log() {}

    private static Logger logger = Logger.getLogger("JasprEnchantments");
    private static final Map<String, Integer> COUNTS = new HashMap<>();

    public static void init(Logger l) {
        if (l != null) logger = l;
    }

    public static void info(String msg) {
        logger.info(msg);
    }

    public static void warn(String msg) {
        logger.warning(msg);
    }

    /** Logs the first 3 failures of a handler with a short trace, then every 500th. */
    public static synchronized void error(String where, Throwable t) {
        int n = COUNTS.merge(where, 1, Integer::sum);
        if (n <= 3 || n % 500 == 0) {
            StringBuilder sb = new StringBuilder("SME_ERROR handler=").append(where).append(" count=").append(n)
                    .append(" error=").append(t.getClass().getSimpleName());
            String m = t.getMessage();
            if (m != null) sb.append(": ").append(redact(m));
            StackTraceElement[] st = t.getStackTrace();
            for (int i = 0; i < Math.min(4, st.length); i++) sb.append(" at ").append(st[i]);
            logger.log(Level.WARNING, sb.toString());
        }
    }

    public static synchronized int errorCount() {
        int s = 0;
        for (int v : COUNTS.values()) s += v;
        return s;
    }

    /** strip anything that looks like an IPv4/IPv6 address */
    public static String redact(String s) {
        if (s == null) return null;
        s = s.replaceAll("\\b\\d{1,3}(\\.\\d{1,3}){3}(:\\d+)?\\b", "<ip>");
        s = s.replaceAll("\\b([0-9a-fA-F]{1,4}:){3,7}[0-9a-fA-F]{1,4}\\b", "<ip>");
        if (s.length() > 300) s = s.substring(0, 300) + "...";
        return s;
    }
}
