package chat.jaspr.apocalypse;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * One player waypoint. Runtime sync to the browser client rides a hidden vanilla
 * scoreboard objective ("jwp", never given a display slot), so markers work at any
 * distance with no custom protocol. The JavaScript mirror lives in
 * client-mods/waypoint-codec.js; both sides pin the same golden vectors.
 *
 * Coord holder: "JW" + slot(b36,1) + death(1:"1"/"0") + color(b36,1) + x(6) + z(6).
 * x/z are offset by +100,000,000 and base36-encoded, lowercase, zero-padded to 6.
 * Name holder: "JN" + slot(1) + sanitized name (<=36 chars). Coord score = block y.
 * Objective display: "JWP v1 n=<count>".
 */
public final class Waypoint {
    // Wool/data-value order so menu icons match beam colors exactly.
    public static final String[] COLOR_NAMES = {"White", "Orange", "Magenta", "Light Blue",
        "Yellow", "Lime", "Pink", "Gray", "Light Gray", "Cyan", "Purple", "Blue",
        "Brown", "Green", "Red", "Black"};
    public static final int[] COLOR_RGB = {0xFFFFFF, 0xFF7F00, 0xFF00FF, 0x00BFFF,
        0xFFFF00, 0x00FF00, 0xFF69B4, 0x808080, 0xC0C0C0, 0x00CED1, 0x800080, 0x0000FF,
        0x8B4513, 0x008000, 0xFF0000, 0x000000};
    public static final int DEFAULT_COLOR = 5;
    /** Approximate chat colors in the same wool/data-value order as COLOR_NAMES. */
    public static final org.bukkit.ChatColor[] CHAT = {org.bukkit.ChatColor.WHITE, org.bukkit.ChatColor.GOLD,
        org.bukkit.ChatColor.LIGHT_PURPLE, org.bukkit.ChatColor.AQUA, org.bukkit.ChatColor.YELLOW,
        org.bukkit.ChatColor.GREEN, org.bukkit.ChatColor.LIGHT_PURPLE, org.bukkit.ChatColor.GRAY,
        org.bukkit.ChatColor.WHITE, org.bukkit.ChatColor.DARK_AQUA, org.bukkit.ChatColor.DARK_PURPLE,
        org.bukkit.ChatColor.BLUE, org.bukkit.ChatColor.GOLD, org.bukkit.ChatColor.DARK_GREEN,
        org.bukkit.ChatColor.DARK_RED, org.bukkit.ChatColor.BLACK};
    static final String OBJECTIVE = "jwp";
    static final String DISPLAY_PREFIX = "JWP v1 n=";
    private static final String B36 = "0123456789abcdefghijklmnopqrstuvwxyz";
    private static final long COORD_OFFSET = 100_000_000L;
    static final int COORD_DIGITS = 6;
    static final int MAX_NAME = 36;

    public final int slot;
    public String name;
    public int x, y, z;
    public String world;
    public int color;
    public boolean death;
    public long createdAt;

    public Waypoint(int slot, String name, int x, int y, int z, String world, int color, boolean death, long createdAt) {
        this.slot = slot;
        this.name = name;
        this.x = x;
        this.y = y;
        this.z = z;
        this.world = world;
        this.color = color & 15;
        this.death = death;
        this.createdAt = createdAt;
    }

    public static String base36(long value, int digits) {
        StringBuilder out = new StringBuilder();
        long v = value;
        for (int i = 0; i < digits; i++) {
            out.append(B36.charAt((int) (v % 36)));
            v /= 36;
        }
        return out.reverse().toString();
    }

    public static long unbase36(String text) {
        long value = 0;
        for (int i = 0; i < text.length(); i++) {
            int digit = B36.indexOf(text.charAt(i));
            if (digit < 0) throw new IllegalArgumentException("Bad base36: " + text);
            value = value * 36 + digit;
        }
        return value;
    }

    /** Holder carrying position/color; null when out of range. */
    public String coordHolder() {
        if (slot < 0 || slot >= 36 || Math.abs((long) x) > 30_000_000 || Math.abs((long) z) > 30_000_000) return null;
        return "JW" + B36.charAt(slot) + (death ? '1' : '0') + B36.charAt(color)
            + base36(x + COORD_OFFSET, COORD_DIGITS) + base36(z + COORD_OFFSET, COORD_DIGITS);
    }

    public String nameHolder() {
        return "JN" + B36.charAt(slot) + sanitizeName(name);
    }

    public static String sanitizeName(String name) {
        if (name == null) return "Waypoint";
        String clean = name.replace('\u00a7', '?').trim().replaceAll("\\s+", " ");
        if (clean.isEmpty()) return "Waypoint";
        return clean.length() > MAX_NAME ? clean.substring(0, MAX_NAME) : clean;
    }

    /** Parse a coord holder back; null on any malformed input. Never throws. */
    public static long[] parseCoordHolder(String holder) {
        try {
            if (holder == null || holder.length() != 2 + 1 + 1 + 1 + COORD_DIGITS + COORD_DIGITS) return null;
            if (!holder.startsWith("JW")) return null;
            int slot = B36.indexOf(holder.charAt(2));
            char deathFlag = holder.charAt(3);
            int color = B36.indexOf(holder.charAt(4));
            if (slot < 0 || color < 0 || (deathFlag != '0' && deathFlag != '1')) return null;
            long x = unbase36(holder.substring(5, 11)) - COORD_OFFSET;
            long z = unbase36(holder.substring(11, 17)) - COORD_OFFSET;
            if (Math.abs(x) > 30_000_000 || Math.abs(z) > 30_000_000) return null;
            return new long[]{slot, deathFlag == '1' ? 1 : 0, color, x, z};
        } catch (RuntimeException e) {
            return null;
        }
    }

    public Map<String, Object> serialize() {
        Map<String, Object> map = new LinkedHashMap<String, Object>();
        map.put("slot", slot);
        map.put("name", name);
        map.put("x", x);
        map.put("y", y);
        map.put("z", z);
        map.put("world", world);
        map.put("color", color);
        map.put("death", death);
        map.put("createdAt", createdAt);
        return map;
    }

    @SuppressWarnings("unchecked")
    public static Waypoint deserialize(Map<?, ?> map) {
        try {
            int slot = ((Number) map.get("slot")).intValue();
            String name = sanitizeName(String.valueOf(map.get("name")));
            int x = ((Number) map.get("x")).intValue();
            int y = ((Number) map.get("y")).intValue();
            int z = ((Number) map.get("z")).intValue();
            String world = String.valueOf(map.get("world"));
            if (slot < 0 || slot >= 36 || world.length() > 64) return null;
            int color = map.get("color") instanceof Number ? ((Number) map.get("color")).intValue() & 15 : DEFAULT_COLOR;
            boolean death = Boolean.TRUE.equals(map.get("death"));
            long createdAt = map.get("createdAt") instanceof Number ? ((Number) map.get("createdAt")).longValue() : 0;
            return new Waypoint(slot, name, x, y, z, world, color, death, createdAt);
        } catch (RuntimeException e) {
            return null;
        }
    }

    /**
     * Eight-way direction arrow from look vector to target using pure vector math
     * (no yaw convention): f = forward component, r = right component.
     */
    public static String arrow(double dirX, double dirZ, double toX, double toZ) {
        double length = Math.hypot(toX, toZ);
        if (length < 1e-9) return "\u25cf";
        double tx = toX / length, tz = toZ / length;
        double f = dirX * tx + dirZ * tz;
        double r = dirX * tz - dirZ * tx;
        int octant = (int) Math.round(Math.atan2(r, f) / (Math.PI / 4)) & 7;
        return ARROWS8[octant];
    }

    private static final String[] ARROWS8 = {"\u2191", "\u2197", "\u2192", "\u2198", "\u2193", "\u2199", "\u2190", "\u2196"};

    public static String verticalTag(int dy) {
        if (dy > 12) return " \u25b2";
        if (dy < -12) return " \u25bc";
        return "";
    }

    public static String formatDistance(int meters) {
        return String.format("%,dm", meters);
    }

    public static List<String> tableJson() {
        List<String> rows = new ArrayList<String>();
        for (int i = 0; i < COLOR_NAMES.length; i++) {
            rows.add(COLOR_NAMES[i] + "=#" + String.format("%06X", COLOR_RGB[i]));
        }
        return rows;
    }
}
