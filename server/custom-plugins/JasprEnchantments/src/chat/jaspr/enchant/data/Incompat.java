package chat.jaspr.enchant.data;

import net.minecraft.server.v1_12_R1.Enchantment;
import net.minecraft.server.v1_12_R1.MinecraftKey;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * SME IncompatibleConfig defaults and ConfigProvider.getIncompatibleEnchantmentsFromConfig, including
 * its substring line match ("configLine.contains(regName.getPath())") and silent skipping of
 * enchantments that do not exist (mujmajnkraftsbettersurvival:*, "magma_walker").
 */
public final class Incompat {
    private Incompat() {}

    public static final String[] GROUPS = {
            "minecraft:protection, minecraft:fire_protection, minecraft:blast_protection, minecraft:projectile_protection, magicprotection, physicalprotection, " +
                    "advancedprotection, advancedfireprotection, advancedblastprotection, advancedprojectileprotection, supremeprotection",
            "physicalprotection, breachedplating",
            "minecraft:feather_falling, advancedfeatherfalling",
            "minecraft:thorns, advancedthorns, burningthorns, meltdown",
            "combatmedic, curseofvulnerability",
            "evasion, heavyweight",
            "lightweight, heavyweight",
            "minecraft:frost_walker, magma_walker",
            "strengthenedvitality, curseofvulnerability",
            "swiftswimming, rusted",
            "minecraft:depth_strider, rusted",
            "minecraft:unbreaking, rusted, instability",
            "minecraft:mending, advancedmending, minecraft:infinity",
            "adept, mujmajnkraftsbettersurvival:education",
            "minecraft:power, advancedpower, powerless",
            "minecraft:punch, advancedpunch, dragging",
            "splitshot, mujmajnkraftsbettersurvival:multishot",
            "minecraft:flame, lesserflame, advancedflame, supremeflame, extinguish",
            "minecraft:infinity, strafe",
            "minecraft:luck_of_the_sea, advancedluckofthesea, ascetic",
            "minecraft:lure, advancedlure",
            "rune_magicalblessing, rune_piercingcapabilities, mujmajnkraftsbettersurvival:penetration",
            "minecraft:efficiency, advancedefficiency, inefficient",
            "minecraft:silk_touch, smelter",
            "viper, darkshadows, mortalitas",
            "criticalstrike, luckmagnification",
            "luckmagnification, ascetic",
            "butchering, defusingedge, inhumane, wateraspect",
            "ashdestroyer, difficultysendowment, reviledblade, instability, cursededge",
            "minecraft:fire_aspect, lesserfireaspect, advancedfireaspect, supremefireaspect, fieryedge, wateraspect, cryogenic, extinguish",
            "cryogenic, desolator, disorientatingblade, envenomed, horsdecombat, levitator, purification",
            "subjectbiology, subjectchemistry, subjectenglish, subjecthistory, subjectmathematics, subjectpe, subjectphysics, subjectgeography",
            "clearskiesfavor, lunasblessing, rainsbestowment, solsblessing, thunderstormsbestowment, wintersgrace",
            "minecraft:knockback, advancedknockback, flinging, mujmajnkraftsbettersurvival:fling, dragging",
            "minecraft:looting, advancedlooting, mujmajnkraftsbettersurvival:education, ascetic",
            "minecraft:sweeping, arcslash",
            "swifterslashes, heavyweight",
            "truestrike, curseofinaccuracy",
            "lifesteal, blessededge",
            "lessersharpness, minecraft:sharpness, advancedsharpness, supremesharpness, reinforcedsharpness, bluntness",
            "lessersmite, minecraft:smite, advancedsmite, supremesmite, blessededge",
            "lesserbaneofarthropods, minecraft:bane_of_arthropods, advancedbaneofarthropods, supremebaneofarthropods",
            "lessersharpness, lessersmite, lesserbaneofarthropods",
            "advancedsharpness, advancedsmite, advancedbaneofarthropods",
            "supremesharpness, supremesmite, supremebaneofarthropods, penetratingedge, spellbreaker",
            "curseofpossession, curseofdecay"
    };

    /** Entries SME could not resolve (for diagnostics). */
    public static final List<String> UNRESOLVED = new ArrayList<>();

    public static Set<Enchantment> compute(SmeDef def, Enchantment self) {
        Set<Enchantment> out = new HashSet<>();
        for (String line : GROUPS) {
            if (!line.contains(def.regName)) continue;
            for (String entry : line.split(",")) {
                entry = entry.trim();
                if (entry.isEmpty()) continue;
                if (!entry.contains(":")) entry = "somanyenchantments:" + entry;
                Enchantment e = Enchantment.enchantments.get(new MinecraftKey(entry));
                if (e == null) {
                    if (!UNRESOLVED.contains(entry)) UNRESOLVED.add(entry);
                } else {
                    out.add(e);
                }
            }
        }
        out.remove(self);
        return out;
    }
}
