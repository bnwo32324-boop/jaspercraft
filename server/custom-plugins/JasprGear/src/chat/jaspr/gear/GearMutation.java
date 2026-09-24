package chat.jaspr.gear;

import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;

/**
 * Phase 3: the nine Trinkets-and-Baubles races as apocalyptic mutations (design reference only,
 * no code copied). BASELINE is the unmutated human. A survivor carries at most one mutation; a
 * Mutagen serum grants it and a Purge Serum removes it. Size changes are deliberately left out
 * (the browser client and the server cannot agree on a resized player safely) and nothing grants
 * night vision.
 *
 * id is the persistence/wire key (never rename). Attribute modifiers use fixed per-mutation UUIDs
 * and, unlike trinket modifiers, stay on the player while offline: the mutation is permanent, and
 * keeping them avoids a Brute logging in with its extra hearts clamped away.
 */
enum GearMutation {
    BASELINE("baseline", "Baseline", "Human", '7', null, 0, 0L,
        new String[]{"No mutation"}),
    BURROWER("burrower", "Burrower", "Dwarf", '6', "Seismic Sense", 15, 8_000L,
        new String[]{"-10% speed, +2 melee, +2 armor", "Never suffocates; ores may drop double", "[R] Seismic Sense: ore scan 12m"}),
    STALKER("stalker", "Stalker", "Elf", '2', "Fade", 30, 20_000L,
        new String[]{"+10% speed, +20% attack speed", "Arrows fly 25% faster, hit 20% harder", "[R] Fade: hostiles lose you (8s)"}),
    FERAL("feral", "Feral", "Faelis", '6', "Pounce", 15, 4_000L,
        new String[]{"+15% speed, -2 hearts, climbs walls", "+3 bare-handed damage, -50% fall", "[R] Pounce: leap, next hit +4"}),
    SPRITE("sprite", "Sprite", "Fairy", 'd', "Mending Mist", 30, 15_000L,
        new String[]{"Flight (costs adrenaline), no falls", "-3 hearts, half melee damage", "[R] Mending Mist: heal nearby (6s)"}),
    SCAVENGER("scavenger", "Scavenger", "Goblin", 'a', "Rummage", 30, 60_000L,
        new String[]{"+15% speed, -2 hearts, +2 luck", "Kills may drop extra loot", "[R] Rummage: search for supplies"}),
    BRUTE("brute", "Brute", "Titan", 'c', "Ground Slam", 30, 10_000L,
        new String[]{"+4 hearts, +30% melee, +50% KB resist", "-20% attack speed; sinks in water", "[R] Ground Slam: 4 dmg around you"}),
    CHARGER("charger", "Charger", "Taurus", 'e', "Stampede", 25, 8_000L,
        new String[]{"+3 hearts, +2 melee, -5% speed", "-10% melee damage taken", "[R] Stampede: charge through foes"}),
    WYRM("wyrm", "Wyrm", "Dragon", '5', "Fire Breath", 30, 6_000L,
        new String[]{"+2 hearts, +1 melee", "Immune to fire, -50% lava", "[R] Fire Breath: 6m cone"});

    final String id;
    final String title;
    final String inspiredBy;
    final char color;
    /** Active ability on R (null for BASELINE), its adrenaline cost and cooldown. */
    final String ability;
    final int cost;
    final long cooldownMs;
    final String[] effects;
    private Map<Attribute, AttributeModifier> modifiers = Collections.emptyMap();

    GearMutation(String id, String title, String inspiredBy, char color, String ability, int cost, long cooldownMs, String[] effects) {
        this.id = id;
        this.title = title;
        this.inspiredBy = inspiredBy;
        this.color = color;
        this.ability = ability;
        this.cost = cost;
        this.cooldownMs = cooldownMs;
        this.effects = effects;
    }

    Map<Attribute, AttributeModifier> modifiers() { return modifiers; }

    private void mod(Attribute attribute, double amount, AttributeModifier.Operation op) {
        if (modifiers.isEmpty()) modifiers = new EnumMap<Attribute, AttributeModifier>(Attribute.class);
        UUID uuid = UUID.nameUUIDFromBytes(("jaspr-gear:mutation:" + id + ":" + attribute.name()).getBytes(StandardCharsets.UTF_8));
        modifiers.put(attribute, new AttributeModifier(uuid, "jaspr_mutation_" + id, amount, op));
    }

    static {
        AttributeModifier.Operation add = AttributeModifier.Operation.ADD_NUMBER, scalar = AttributeModifier.Operation.ADD_SCALAR;
        BURROWER.mod(Attribute.GENERIC_MOVEMENT_SPEED, -0.10, scalar);
        BURROWER.mod(Attribute.GENERIC_ATTACK_DAMAGE, 2.0, add);
        BURROWER.mod(Attribute.GENERIC_ARMOR, 2.0, add);
        STALKER.mod(Attribute.GENERIC_MOVEMENT_SPEED, 0.10, scalar);
        STALKER.mod(Attribute.GENERIC_ATTACK_SPEED, 0.20, scalar);
        FERAL.mod(Attribute.GENERIC_MOVEMENT_SPEED, 0.15, scalar);
        FERAL.mod(Attribute.GENERIC_MAX_HEALTH, -4.0, add);
        SPRITE.mod(Attribute.GENERIC_MAX_HEALTH, -6.0, add);
        SPRITE.mod(Attribute.GENERIC_ATTACK_DAMAGE, -0.5, scalar);
        SCAVENGER.mod(Attribute.GENERIC_MOVEMENT_SPEED, 0.15, scalar);
        SCAVENGER.mod(Attribute.GENERIC_MAX_HEALTH, -4.0, add);
        SCAVENGER.mod(Attribute.GENERIC_LUCK, 2.0, add);
        BRUTE.mod(Attribute.GENERIC_MAX_HEALTH, 8.0, add);
        BRUTE.mod(Attribute.GENERIC_ATTACK_DAMAGE, 0.30, scalar);
        BRUTE.mod(Attribute.GENERIC_ATTACK_SPEED, -0.20, scalar);
        BRUTE.mod(Attribute.GENERIC_KNOCKBACK_RESISTANCE, 0.5, add);
        BRUTE.mod(Attribute.GENERIC_MOVEMENT_SPEED, -0.05, scalar);
        CHARGER.mod(Attribute.GENERIC_MAX_HEALTH, 6.0, add);
        CHARGER.mod(Attribute.GENERIC_ATTACK_DAMAGE, 2.0, add);
        CHARGER.mod(Attribute.GENERIC_MOVEMENT_SPEED, -0.05, scalar);
        WYRM.mod(Attribute.GENERIC_MAX_HEALTH, 4.0, add);
        WYRM.mod(Attribute.GENERIC_ATTACK_DAMAGE, 1.0, add);
    }

    static GearMutation byId(String id) {
        if (id == null) return null;
        String key = id.toLowerCase(Locale.ROOT);
        for (GearMutation m : values()) if (m.id.equals(key)) return m;
        return null;
    }
}
