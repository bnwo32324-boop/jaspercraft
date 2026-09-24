package chat.jaspr.gear;

import java.util.Locale;

/**
 * Phase 2 custom status effects (Trinkets-and-Baubles counterparts). Tracked server-side by
 * GearVitals with absolute expiry times; never implemented as vanilla potions, never glowing or
 * night vision. id is the wire/persistence key (never rename); hud is the browser HUD label.
 */
enum GearStatus {
    BLEED("bleed", "Bleeding", "Bleed", 'c', true, 30_000L),
    ICE_RESISTANCE("ice", "Ice Resistance", "Ice Res", 'b', false, 600_000L),
    INVIGORATED("vigor", "Invigorated", "Vigor", 'a', false, 600_000L),
    LIGHTNING_RESISTANCE("volt", "Lightning Resistance", "Volt Res", 'e', false, 600_000L),
    PARALYSIS("para", "Paralysis", "Paralysed", 'd', true, 3_000L);

    final String id;
    final String title;
    final String hud;
    final char color;
    final boolean harmful;
    /** Longest duration one application may set (Paralysis is always short). */
    final long maxMs;

    GearStatus(String id, String title, String hud, char color, boolean harmful, long maxMs) {
        this.id = id;
        this.title = title;
        this.hud = hud;
        this.color = color;
        this.harmful = harmful;
        this.maxMs = maxMs;
    }

    static GearStatus byId(String id) {
        if (id == null) return null;
        String key = id.toLowerCase(Locale.ROOT);
        for (GearStatus s : values()) if (s.id.equals(key) || s.name().toLowerCase(Locale.ROOT).equals(key)) return s;
        return null;
    }
}
