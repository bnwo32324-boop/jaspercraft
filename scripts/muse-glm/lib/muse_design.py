"""Gameplay design for the Muse+GLM_Maps pack: theme, dangers, custom mob, hazards, special loot, bosses.

Everything is derived deterministically from the design's file stem, so rebuilding never reshuffles a structure.
Owner brief (2026-09-26): every structure carries dangers from the Overworld, the Nether AND the End, plus its own
custom mob (not zombies); the dangers are unique to each structure; each structure has its own special loot; ten
bosses for these structures. Validation (validate()) enforces the uniqueness claims.
"""
from __future__ import annotations

import hashlib
import json
import random
import re
from pathlib import Path

# ---------------------------------------------------------------- placement overrides (reviewed per design)
HABITAT = {
    "N035_military_base_medieval_fortress_aerial_military_base": {"habitat": "sky"},
    "N071_cinderella_castle": {"habitat": "bedrock", "maxHeight": 255},
    "N036_castlevania_castle_structure": {"habitat": "tall", "anchor": 1},
    "B011_medieval_fishing_port_at_jackdaw_hollow": {"habitat": "shore", "waterline": 1},
    "B068_lighthouse_functional_medieval_lighthouse_structure": {"habitat": "shore", "waterline": 1},
    "B028_native_american_village_with_watchtower": {"habitat": "land", "anchor": 1},
    "B039_temple_asian_style_temple_structure": {"habitat": "land", "anchor": 1},
    "N053_pirate_ship_build": {"habitat": "ocean_surface", "waterline": 3},
}

# ---------------------------------------------------------------- themes
THEME_RULES = [
    ("infernal", r"nether"),
    ("void", r"chrono|end_of_time|sonic|space|kami|aerial"),
    ("frost", r"(?:^|_)ice(?:_|$)|icelandic|snow|frost"),
    ("maritime", r"ship|port|fishing|lighthouse|island|seaside|sunny|pirate|harbor|resort|chum"),
    ("ancient", r"egyptian|desert|jungle|ancient|ruins|overgrown"),
    ("sacred", r"church|cathedral|chapel|shrine|temple|monastery|legislative"),
    ("arcane", r"wizard|enchanting|library|bookshop|observatory|research|museum|lavender|mario|castlevania"),
    ("martial", r"castle|fortress|arena|colosseum|outpost|watchtower|military|gate|keep|prison|court|stadium|royal|guard"),
    ("craft", r"blacksmith|forge|factory|warehouse|storage|mining|mine_|depot|industrial|engineering|plant|carriage"
              r"|printworks|junkyard|sewers|dungeon|maze|redstone|garage|parking"),
    ("rural", r"farm|windmill|stable|greenhouse|cabin|shelter|homestead|berry|hydroponics|arboretum|tree|village"
              r"|hillside|dwelling|bee_|market"),
]
DEFAULT_THEME = "civic"

# Mob pools per theme. Every structure gets at least one Overworld, one Nether and one End mob (owner brief).
OVERWORLD = {
    "arcane": ["WITCH", "EVOKER", "ILLUSIONER", "SILVERFISH", "VINDICATOR"],
    "sacred": ["STRAY", "SKELETON", "WITCH", "VINDICATOR", "SPIDER"],
    "martial": ["SKELETON", "VINDICATOR", "STRAY", "SPIDER", "CREEPER"],
    "craft": ["SILVERFISH", "CREEPER", "CAVE_SPIDER", "SKELETON", "SLIME"],
    "rural": ["SPIDER", "CAVE_SPIDER", "WITCH", "SLIME", "WOLF", "RABBIT"],
    "civic": ["CREEPER", "SPIDER", "WITCH", "VINDICATOR", "SKELETON", "SLIME"],
    "maritime": ["GUARDIAN", "STRAY", "SKELETON", "WITCH", "SPIDER"],
    "infernal": ["SKELETON", "WITCH", "CREEPER", "CAVE_SPIDER"],
    "ancient": ["HUSK", "SPIDER", "CAVE_SPIDER", "SKELETON", "SILVERFISH"],
    "frost": ["STRAY", "POLAR_BEAR", "SKELETON", "WITCH", "WOLF"],
    "void": ["SILVERFISH", "EVOKER", "WITCH", "CREEPER", "STRAY"],
}
NETHER = {
    "arcane": ["BLAZE", "MAGMA_CUBE", "WITHER_SKELETON"], "sacred": ["WITHER_SKELETON", "BLAZE", "PIG_ZOMBIE"],
    "martial": ["WITHER_SKELETON", "PIG_ZOMBIE", "BLAZE"], "craft": ["MAGMA_CUBE", "BLAZE", "WITHER_SKELETON"],
    "rural": ["MAGMA_CUBE", "PIG_ZOMBIE", "BLAZE"], "civic": ["BLAZE", "MAGMA_CUBE", "WITHER_SKELETON", "PIG_ZOMBIE"],
    "maritime": ["MAGMA_CUBE", "BLAZE", "WITHER_SKELETON"], "infernal": ["BLAZE", "WITHER_SKELETON", "MAGMA_CUBE", "GHAST", "PIG_ZOMBIE"],
    "ancient": ["WITHER_SKELETON", "BLAZE", "MAGMA_CUBE"], "frost": ["BLAZE", "WITHER_SKELETON", "MAGMA_CUBE"],
    "void": ["BLAZE", "GHAST", "WITHER_SKELETON"],
}
END = {
    "arcane": ["ENDERMAN", "SHULKER", "ENDERMITE"], "sacred": ["ENDERMAN", "ENDERMITE", "SHULKER"],
    "martial": ["ENDERMAN", "SHULKER", "ENDERMITE"], "craft": ["ENDERMITE", "ENDERMAN", "SHULKER"],
    "rural": ["ENDERMAN", "ENDERMITE", "SHULKER"], "civic": ["ENDERMAN", "SHULKER", "ENDERMITE"],
    "maritime": ["ENDERMAN", "SHULKER", "ENDERMITE"], "infernal": ["ENDERMAN", "ENDERMITE", "SHULKER"],
    "ancient": ["ENDERMITE", "ENDERMAN", "SHULKER"], "frost": ["ENDERMAN", "SHULKER", "ENDERMITE"],
    "void": ["SHULKER", "ENDERMAN", "ENDERMITE"],
}
# Custom (signature) mob bases per theme -- deliberately not zombies.
SIGNATURE_BASES = {
    "arcane": ["EVOKER", "WITCH", "ILLUSIONER", "ENDERMAN", "BLAZE", "STRAY", "VEX"],
    "sacred": ["STRAY", "WITHER_SKELETON", "WITCH", "VEX", "SKELETON", "IRON_GOLEM"],
    "martial": ["VINDICATOR", "WITHER_SKELETON", "SKELETON", "IRON_GOLEM", "SPIDER", "POLAR_BEAR"],
    "craft": ["IRON_GOLEM", "MAGMA_CUBE", "SILVERFISH", "CAVE_SPIDER", "CREEPER", "BLAZE"],
    "rural": ["SPIDER", "WOLF", "RABBIT", "SLIME", "WITCH", "POLAR_BEAR"],
    "civic": ["ENDERMAN", "CREEPER", "SPIDER", "VINDICATOR", "IRON_GOLEM", "WITCH"],
    "maritime": ["GUARDIAN", "SKELETON", "STRAY", "WITCH", "SLIME", "POLAR_BEAR"],
    "infernal": ["BLAZE", "WITHER_SKELETON", "MAGMA_CUBE", "PIG_ZOMBIE", "GHAST"],
    "ancient": ["SPIDER", "CAVE_SPIDER", "SKELETON", "WITCH", "SILVERFISH", "HUSK"],
    "frost": ["STRAY", "POLAR_BEAR", "WOLF", "SNOWMAN", "SKELETON"],
    "void": ["ENDERMAN", "SHULKER", "ENDERMITE", "VEX", "EVOKER", "STRAY"],
}
NOUN = {
    "SPIDER": "Weaver", "CAVE_SPIDER": "Skitterling", "SKELETON": "Bonewright", "STRAY": "Frostbone",
    "WITHER_SKELETON": "Ashen Knight", "BLAZE": "Pyre", "MAGMA_CUBE": "Slagheart", "SLIME": "Ooze",
    "ENDERMAN": "Stalker", "ENDERMITE": "Voidmite", "SILVERFISH": "Gnawer", "WITCH": "Hexwife",
    "VINDICATOR": "Reaver", "EVOKER": "Invoker", "VEX": "Wisp", "ILLUSIONER": "Mirage", "CREEPER": "Sapper",
    "POLAR_BEAR": "Maul", "WOLF": "Hound", "IRON_GOLEM": "Sentinel", "GUARDIAN": "Lurker", "RABBIT": "Killer Hare",
    "PIG_ZOMBIE": "Brute", "GHAST": "Wailer", "SHULKER": "Turret", "SNOWMAN": "Frost Mote", "HUSK": "Mummy",
}
ABILITIES = {  # Java: chat.jaspr.muse.Ability
    "FIREBALL": "Cinder", "FROST_NOVA": "Rime", "VOID_BOLT": "Void", "WEB_SHOT": "Silk", "VENOM_SPIT": "Venom",
    "BLINK": "Flicker", "LIFE_DRAIN": "Leech", "LEAP": "Pouncing", "SLAM": "Quake", "ARROW_VOLLEY": "Volley",
    "FANGS": "Fang", "MURK": "Murk", "WITHER_TOUCH": "Blight", "EMBER_AURA": "Ember", "BROOD": "Brood",
    "VOLATILE": "Volatile", "WARD": "Warded", "PHANTOM": "Phantom", "THORNS": "Thorned", "STORM": "Storm",
    "HOOK": "Hooking", "ALCHEMY": "Alchemic", "DRIFT": "Drifting", "FROSTBOUND": "Frostbound",
    "SPLIT": "Splitting", "AMBUSH": "Stalking", "SHRIEK": "Shrieking", "RALLY": "Warcry",
}
# Abilities that make no sense for a base (e.g. a stationary shulker cannot LEAP).
BANNED = {
    "SHULKER": {"LEAP", "BLINK", "AMBUSH", "HOOK", "SLAM"}, "GUARDIAN": {"LEAP", "SLAM", "AMBUSH"},
    "GHAST": {"LEAP", "SLAM", "HOOK", "THORNS"}, "SNOWMAN": {"THORNS"},
    "SLIME": {"ARROW_VOLLEY"}, "MAGMA_CUBE": {"ARROW_VOLLEY"}, "RABBIT": {"ARROW_VOLLEY", "WARD"},
}
THEME_ABILITIES = {
    "arcane": ["VOID_BOLT", "BLINK", "FANGS", "ALCHEMY", "MURK", "PHANTOM", "DRIFT", "LIFE_DRAIN", "SHRIEK"],
    "sacred": ["LIFE_DRAIN", "STORM", "WARD", "WITHER_TOUCH", "FANGS", "MURK", "SHRIEK", "RALLY"],
    "martial": ["ARROW_VOLLEY", "SLAM", "HOOK", "WARD", "RALLY", "LEAP", "THORNS", "FIREBALL"],
    "craft": ["SLAM", "VOLATILE", "EMBER_AURA", "BROOD", "THORNS", "FIREBALL", "SPLIT", "HOOK"],
    "rural": ["WEB_SHOT", "VENOM_SPIT", "LEAP", "BROOD", "SPLIT", "AMBUSH", "ALCHEMY", "SHRIEK"],
    "civic": ["AMBUSH", "VOLATILE", "BLINK", "HOOK", "PHANTOM", "ALCHEMY", "STORM", "RALLY"],
    "maritime": ["HOOK", "FROST_NOVA", "ARROW_VOLLEY", "DRIFT", "STORM", "VENOM_SPIT", "SPLIT", "MURK"],
    "infernal": ["FIREBALL", "EMBER_AURA", "WITHER_TOUCH", "VOLATILE", "SLAM", "LIFE_DRAIN", "WARD"],
    "ancient": ["VENOM_SPIT", "WEB_SHOT", "MURK", "WITHER_TOUCH", "BROOD", "AMBUSH", "FANGS", "SPLIT"],
    "frost": ["FROST_NOVA", "FROSTBOUND", "ARROW_VOLLEY", "LEAP", "SLAM", "DRIFT", "WARD"],
    "void": ["VOID_BOLT", "BLINK", "DRIFT", "PHANTOM", "AMBUSH", "STORM", "SPLIT", "MURK"],
}
HAZARDS = {  # Java: chat.jaspr.muse.Hazard -- kind: params
    "MIASMA": ["POISON", "WITHER", "WEAKNESS", "SLOW", "HUNGER", "NAUSEA", "BLINDNESS"],
    "ARROW_TRAP": ["PLAIN", "FLAME", "VENOM", "FROST"],
    "FANG_TRAP": ["LINE", "RING"],
    "BRAZIER_GUST": ["EMBER", "INFERNO"],
    "ICE_SNARE": ["CHILL", "DEEPFREEZE"],
    "VOID_RIFT": ["LEVITATE", "MITES"],
    "STORM_CALL": ["BOLT", "TEMPEST"],
    "RUBBLE_FALL": ["STONE", "ANVIL"],
    "MAGMA_SURGE": ["SMALL", "LARGE"],
    "SWARM": ["SILVERFISH", "ENDERMITE", "CAVE_SPIDER"],
    "HAUNT": ["VEX", "PHANTOM_BELL"],
    "GRAVITY_WELL": ["PULL", "CRUSH"],
    "CURSE_AURA": ["FATIGUE", "WEAKNESS"],
    "DARKNESS": ["BLIND", "WHISPER"],
    "GHAST_BARRAGE": ["VOLLEY", "SIEGE"],
    "SHULKER_NEST": ["PAIR", "RING"],
    "BLAZE_VENT": ["JET", "RING"],
    "WEB_SNARE": ["NET", "COCOON"],
}
THEME_HAZARDS = {
    "arcane": ["VOID_RIFT", "HAUNT", "CURSE_AURA", "MIASMA", "FANG_TRAP", "DARKNESS", "SHULKER_NEST"],
    "sacred": ["HAUNT", "STORM_CALL", "DARKNESS", "CURSE_AURA", "FANG_TRAP", "MIASMA"],
    "martial": ["ARROW_TRAP", "RUBBLE_FALL", "GHAST_BARRAGE", "BRAZIER_GUST", "GRAVITY_WELL", "SHULKER_NEST"],
    "craft": ["RUBBLE_FALL", "MAGMA_SURGE", "SWARM", "BRAZIER_GUST", "ARROW_TRAP", "BLAZE_VENT"],
    "rural": ["WEB_SNARE", "SWARM", "MIASMA", "STORM_CALL", "ARROW_TRAP", "HAUNT"],
    "civic": ["SWARM", "DARKNESS", "RUBBLE_FALL", "GRAVITY_WELL", "VOID_RIFT", "STORM_CALL", "SHULKER_NEST"],
    "maritime": ["ICE_SNARE", "STORM_CALL", "MIASMA", "ARROW_TRAP", "GRAVITY_WELL", "HAUNT"],
    "infernal": ["BLAZE_VENT", "MAGMA_SURGE", "GHAST_BARRAGE", "BRAZIER_GUST", "CURSE_AURA", "FANG_TRAP"],
    "ancient": ["WEB_SNARE", "SWARM", "MIASMA", "FANG_TRAP", "RUBBLE_FALL", "DARKNESS"],
    "frost": ["ICE_SNARE", "ARROW_TRAP", "STORM_CALL", "GRAVITY_WELL", "HAUNT", "SHULKER_NEST"],
    "void": ["VOID_RIFT", "GRAVITY_WELL", "SHULKER_NEST", "DARKNESS", "GHAST_BARRAGE", "STORM_CALL"],
}
BASE_HEALTH = {"SPIDER": 16, "CAVE_SPIDER": 12, "SKELETON": 20, "STRAY": 20, "WITHER_SKELETON": 20, "BLAZE": 20,
               "MAGMA_CUBE": 16, "SLIME": 16, "ENDERMAN": 40, "ENDERMITE": 8, "SILVERFISH": 8, "WITCH": 26,
               "VINDICATOR": 24, "EVOKER": 24, "VEX": 14, "ILLUSIONER": 32, "CREEPER": 20, "POLAR_BEAR": 30,
               "WOLF": 20, "IRON_GOLEM": 60, "GUARDIAN": 30, "RABBIT": 10, "PIG_ZOMBIE": 20, "GHAST": 10,
               "SHULKER": 30, "SNOWMAN": 16, "HUSK": 20}

# ---------------------------------------------------------------- special loot (unique per structure)
ITEMS = {
    "blade": ["DIAMOND_SWORD", "IRON_SWORD", "GOLD_SWORD"], "axe": ["DIAMOND_AXE", "IRON_AXE"],
    "bow": ["BOW"], "pick": ["DIAMOND_PICKAXE", "IRON_PICKAXE"], "shovel": ["DIAMOND_SPADE"],
    "hoe": ["DIAMOND_HOE"], "rod": ["FISHING_ROD"], "shield": ["SHIELD"],
    "helm": ["DIAMOND_HELMET", "IRON_HELMET", "GOLD_HELMET"], "chest": ["DIAMOND_CHESTPLATE", "IRON_CHESTPLATE"],
    "legs": ["DIAMOND_LEGGINGS", "IRON_LEGGINGS"], "boots": ["DIAMOND_BOOTS", "IRON_BOOTS", "LEATHER_BOOTS"],
    "tome": ["ENCHANTED_BOOK"],
}
THEME_ITEMS = {
    "martial": ["blade", "axe", "bow", "shield", "chest", "helm"], "craft": ["pick", "axe", "shovel", "legs", "blade"],
    "arcane": ["tome", "blade", "bow", "helm", "boots"], "sacred": ["shield", "chest", "blade", "helm", "tome"],
    "rural": ["hoe", "axe", "rod", "boots", "bow"], "maritime": ["rod", "boots", "blade", "bow", "helm"],
    "infernal": ["blade", "chest", "bow", "boots", "axe"], "void": ["blade", "boots", "bow", "legs", "tome"],
    "frost": ["blade", "boots", "bow", "chest", "axe"], "ancient": ["axe", "blade", "bow", "helm", "tome"],
    "civic": ["pick", "shield", "legs", "bow", "blade", "tome"],
}
V = "minecraft:"
S = "somanyenchantments:"
# Enchantment groups: at most one pick per group, groups chosen are mutually compatible (vanilla + SME rules).
EGROUPS = {
    "blade": [
        [(V + "sharpness", 5), (S + "advancedsharpness", 5), (V + "smite", 5), (S + "advancedsmite", 5), (V + "bane_of_arthropods", 5)],
        [(S + "lifesteal", 4), (S + "blessededge", 5)],
        [(V + "fire_aspect", 2), (S + "advancedfireaspect", 2), (S + "fieryedge", 2), (S + "wateraspect", 5), (S + "cryogenic", 3)],
        [(V + "looting", 3), (S + "advancedlooting", 3)],
        [(S + "parry", 3), (S + "counterattack", 3)],
        [(S + "viper", 5), (S + "darkshadows", 3), (S + "mortalitas", 8)],
        [(S + "purgingblade", 5), (S + "spellbreaker", 5)],
        [(S + "truestrike", 1), (S + "unsheathing", 2)],
        [(V + "unbreaking", 3)], [(S + "arcslash", 3), (V + "sweeping", 3)],
        [(S + "clearskiesfavor", 5), (S + "lunasblessing", 5), (S + "solsblessing", 5), (S + "wintersgrace", 5)],
    ],
    "axe": [
        [(V + "sharpness", 5), (S + "penetratingedge", 6)], [(S + "culling", 3)], [(S + "brutality", 5)],
        [(S + "disarmament", 5)], [(S + "desolator", 4), (S + "purification", 5)], [(V + "efficiency", 5)],
        [(V + "unbreaking", 3)],
    ],
    "bow": [
        [(V + "power", 5), (S + "advancedpower", 5)], [(V + "punch", 2), (S + "advancedpunch", 2)],
        [(V + "flame", 1), (S + "advancedflame", 1), (S + "lesserflame", 1)], [(S + "splitshot", 4)],
        [(S + "rune_arrowpiercing", 4)], [(S + "strafe", 6)], [(V + "unbreaking", 3)], [(S + "pushing", 2)],
    ],
    "pick": [[(V + "efficiency", 5), (S + "advancedefficiency", 5)], [(V + "fortune", 3), (S + "smelter", 1)],
             [(V + "unbreaking", 3)], [(S + "reinforcedsharpness", 5)], [(V + "mending", 1), (S + "advancedmending", 1)]],
    "shovel": [[(V + "efficiency", 5), (S + "advancedefficiency", 5)], [(S + "smelter", 1), (V + "fortune", 3)],
               [(V + "unbreaking", 3)], [(V + "mending", 1)]],
    "hoe": [[(S + "jaggedrake", 5)], [(S + "plowing", 1)], [(S + "moisturized", 1)], [(V + "unbreaking", 3)]],
    "rod": [[(V + "luck_of_the_sea", 3), (S + "advancedluckofthesea", 3)], [(V + "lure", 3), (S + "advancedlure", 3)],
            [(V + "unbreaking", 3)], [(V + "mending", 1)]],
    "shield": [[(S + "burningshield", 4)], [(S + "empowereddefence", 4)], [(S + "naturalblocking", 3)],
               [(S + "rune_resurrection", 2)], [(V + "unbreaking", 3)]],
    "helm": [[(V + "protection", 4), (S + "advancedprotection", 4), (S + "magicprotection", 4), (S + "physicalprotection", 4)],
             [(S + "combatmedic", 3)], [(V + "respiration", 3)], [(V + "aqua_affinity", 1)], [(V + "unbreaking", 3)]],
    "chest": [[(V + "protection", 4), (S + "advancedprotection", 4), (S + "advancedfireprotection", 4), (S + "advancedblastprotection", 4), (S + "advancedprojectileprotection", 4)],
              [(S + "strengthenedvitality", 5)], [(S + "innerberserk", 4)], [(V + "thorns", 3), (S + "advancedthorns", 3), (S + "burningthorns", 3)],
              [(V + "unbreaking", 3)]],
    "legs": [[(V + "protection", 4), (S + "advancedprotection", 4), (S + "physicalprotection", 4)], [(S + "evasion", 3)],
             [(V + "unbreaking", 3)], [(V + "mending", 1)]],
    "boots": [[(V + "feather_falling", 4), (S + "advancedfeatherfalling", 4)], [(S + "lightweight", 3)],
              [(V + "depth_strider", 3), (S + "swiftswimming", 3), (V + "frost_walker", 2)], [(S + "magmawalker", 2)],
              [(V + "protection", 4)], [(V + "unbreaking", 3)]],
    "tome": [[(S + "advancedsharpness", 5), (S + "advancedprotection", 4), (S + "advancedpower", 5), (S + "advancedefficiency", 5)],
             [(S + "lifesteal", 4), (S + "evasion", 3), (S + "splitshot", 4), (S + "smelter", 1)],
             [(S + "arcslash", 3), (S + "combatmedic", 3), (S + "strafe", 6), (S + "adept", 3)],
             [(S + "ancientswordmastery", 3), (S + "supremesharpness", 5), (S + "rune_revival", 2), (S + "upgradedpotentials", 1)]],
}
KIND_NOUNS = {"blade": ["Blade", "Edge", "Sabre", "Longsword"], "axe": ["Cleaver", "Hatchet", "Waraxe"],
              "bow": ["Longbow", "Recurve", "Warbow"], "pick": ["Pick", "Delver"], "shovel": ["Spade"],
              "hoe": ["Rake", "Scythe-Hoe"], "rod": ["Angler", "Line"], "shield": ["Aegis", "Bulwark"],
              "helm": ["Helm", "Crown", "Visor"], "chest": ["Cuirass", "Hauberk", "Mantle"], "legs": ["Greaves", "Tassets"],
              "boots": ["Treads", "Striders", "Sabatons"], "tome": ["Codex", "Grimoire", "Folio"]}

# ---------------------------------------------------------------- ten bosses (one per chosen structure)
BOSSES = {
    "N036_castlevania_castle_structure": {
        "key": "sovereign", "name": "Dracul, the Night Sovereign", "type": "WITHER_SKELETON", "health": 700, "damage": 13,
        "abilities": ["BAT_SWARM", "BLOOD_DRAIN", "MIST_BLINK", "SUMMON_KNIGHTS"], "weapon": "nightfall_scythe"},
    "N071_cinderella_castle": {
        "key": "stepmother", "name": "The Midnight Stepmother", "type": "WITCH", "health": 560, "damage": 10,
        "abilities": ["POTION_BARRAGE", "MIDNIGHT_TOLL", "PUMPKIN_HEX", "SUMMON_RATS"], "weapon": "glass_rapier"},
    "B059_nether_portal_hub_structure": {
        "key": "pyrarch", "name": "Pyrarch Vulcanis", "type": "BLAZE", "health": 600, "damage": 12,
        "abilities": ["FIREBALL_VOLLEY", "ERUPTION", "MAGMA_SPAWN", "HEAT_AURA"], "weapon": "magmaforged_greataxe"},
    "N076_kami_s_tower_dragon_ball_structure": {
        "key": "skysage", "name": "Kaio, the Skybound Sage", "type": "ENDERMAN", "health": 620, "damage": 12,
        "abilities": ["VOID_BOLTS", "MIST_BLINK", "METEOR", "GRAVITY_SLAM"], "weapon": "skybreaker_blade"},
    # Moved from N014 (Icelandic Court and Prison, 182x161): no admissible site in 400 test-server samples.
    "B061_fortress_eldern_hold_fortress_with_catapults": {
        "key": "magistrate", "name": "The Frost Magistrate", "type": "STRAY", "health": 580, "damage": 11,
        "abilities": ["RIME_VOLLEY", "FROST_NOVA", "ICE_PRISON", "SUMMON_BEARS"], "weapon": "verdict_of_rime"},
    "B048_arena_minecraft_roman_colosseum_mob_arena": {
        "key": "gladius", "name": "Gladius Rex, Champion of the Pit", "type": "VINDICATOR", "health": 640, "damage": 14,
        "abilities": ["CHARGE", "WHIRLWIND", "SUMMON_GLADIATORS", "WARCRY"], "weapon": "champions_gladius"},
    # Moved from N022 (Diamond Casino, 218x206): no admissible site in 400 test-server samples.
    "N045_casino_hall_working_minecraft_casino": {
        "key": "house", "name": "The House", "type": "EVOKER", "health": 540, "damage": 10,
        "abilities": ["FANG_LINES", "SUMMON_CHIPS", "ROULETTE", "JACKPOT"], "weapon": "high_roller_sabre"},
    "B026_gothic_cathedral_build": {
        "key": "bishop", "name": "The Hollow Bishop", "type": "ILLUSIONER", "health": 560, "damage": 11,
        "abilities": ["HOLY_FIRE", "BLINDING_LIGHT", "SUMMON_CHOIR", "LIFE_DRAIN"], "weapon": "censer_mace"},
    "N073_thousand_sunny_one_piece_ship_schematic": {
        "key": "admiral", "name": "Admiral Brinehook", "type": "SKELETON", "health": 600, "damage": 12,
        "abilities": ["CANNONADE", "GRAPPLE", "SUMMON_CREW", "CUTLASS_LUNGE"], "weapon": "brinehook_cutlass"},
    "N004_hidden_underground_village": {
        "key": "matriarch", "name": "The Burrow Matriarch", "type": "SPIDER", "health": 580, "damage": 11,
        "abilities": ["WEB_BARRAGE", "BROOD", "POISON_BITE", "POUNCE"], "weapon": "silkfang_dagger"},
}


def theme_of(stem: str) -> str:
    name = stem.lower()
    for theme, rx in THEME_RULES:
        if re.search(rx, name):
            return theme
    return DEFAULT_THEME


def rng_for(stem: str, salt: str) -> random.Random:
    return random.Random(int(hashlib.sha256((salt + ":" + stem).encode()).hexdigest()[:16], 16))


_used_sig: set = set()
_used_names: set = set()
_used_hazard: set = set()
_used_special: set = set()
_used_special_names: set = set()


def key_word(stem: str) -> str:
    """The most distinctive word of the design name, for naming its custom mob and relic."""
    skip = {"medieval", "minecraft", "build", "structure", "design", "schematic", "the", "and", "with", "of", "a",
            "at", "style", "modern", "small", "large", "grand", "compact", "simple", "classic", "cozy", "rustic",
            "detailed", "functional", "decorated", "fantasy", "giant", "massive", "big", "s", "for", "in"}
    words = [w for w in stem.split("_")[1:] if w not in skip and not w.isdigit()]
    words.sort(key=lambda w: (-len(w), w))
    return (words[0] if words else stem.split("_")[1]).capitalize()


def signature(stem, theme, tier, entry):
    r = rng_for(stem, "sig")
    bases = SIGNATURE_BASES[theme][:]
    if entry["habitat"] not in ("ocean_surface", "shore"):
        bases = [b for b in bases if b != "GUARDIAN"] or bases
    pool = THEME_ABILITIES[theme]
    for _ in range(400):
        base = r.choice(bases)
        ab = [a for a in pool if a not in BANNED.get(base, set())]
        a1, a2 = r.sample(ab, 2)
        combo = (base, frozenset((a1, a2)))
        if combo in _used_sig:
            continue
        name = f"{key_word(stem)} {ABILITIES[a1]} {NOUN[base]}"
        if name in _used_names:
            continue
        _used_sig.add(combo)
        _used_names.add(name)
        hp = round(BASE_HEALTH[base] * (1.6 + 0.35 * tier))
        return {"name": name, "type": base, "health": hp, "damage": round(3 + 1.5 * tier, 1),
                "speed": round(0.24 + 0.015 * tier, 3), "abilities": [a1, a2], "count": 1 + (tier >= 4)}
    raise AssertionError("could not find a unique custom mob for " + stem)


def garrison(stem, theme, tier, entry):
    r = rng_for(stem, "garrison")
    wet = entry["habitat"] in ("ocean_surface", "shore")
    big_open = tier >= 4
    over = [m for m in OVERWORLD[theme] if m != "GUARDIAN" or wet]
    neth = [m for m in NETHER[theme] if m != "GHAST" or big_open]
    out = [{"type": r.choice(over), "count": 2 + tier // 2, "realm": "overworld"},
           {"type": r.choice(neth), "count": 1 + tier // 2, "realm": "nether"},
           {"type": r.choice(END[theme]), "count": 1 + tier // 3, "realm": "end"}]
    if tier >= 3:
        rest = [m for m in over if m != out[0]["type"]]
        out.append({"type": r.choice(rest), "count": 1 + tier // 3, "realm": "overworld"})
    for g in out:
        if g["type"] in ("GHAST", "SHULKER", "IRON_GOLEM"):
            g["count"] = min(g["count"], 2)
    return out


def hazards(stem, theme):
    r = rng_for(stem, "hazard")
    kinds = THEME_HAZARDS[theme]
    for _ in range(600):
        k1, k2 = r.sample(kinds, 2)
        p1, p2 = r.choice(HAZARDS[k1]), r.choice(HAZARDS[k2])
        key = frozenset(((k1, p1), (k2, p2)))
        if key in _used_hazard:
            continue
        _used_hazard.add(key)
        return [{"kind": k1, "param": p1}, {"kind": k2, "param": p2}]
    raise AssertionError("no unique hazard pair for " + stem)


def special(stem, theme, tier, pretty):
    r = rng_for(stem, "special")
    for _ in range(600):
        kind = r.choice(THEME_ITEMS[theme])
        material = r.choice(ITEMS[kind])
        groups = EGROUPS[kind]
        n = min(len(groups), 2 + (tier >= 3) + (tier >= 5))
        chosen = r.sample(range(len(groups)), n)
        enchants = []
        for gi in sorted(chosen):
            key, mx = r.choice(groups[gi])
            lvl = mx if mx == 1 else max(1, min(mx, r.randint((mx + 1) // 2, mx) - (0 if tier >= 4 else 1)))
            enchants.append([key, lvl])
        sig = (material, tuple(sorted(tuple(e) for e in enchants)))
        if sig in _used_special:
            continue
        noun = r.choice(KIND_NOUNS[kind])
        name = f"{key_word(stem)} {noun}"
        if name in _used_special_names:
            name = f"{noun} of the {pretty}"
        if name in _used_special_names:
            continue
        _used_special.add(sig)
        _used_special_names.add(name)
        return {"name": name, "material": material, "kind": kind, "enchants": enchants,
                "lore": f"Recovered from the {pretty}."}
    raise AssertionError("no unique special for " + stem)


# Measured admission rate per design (test-server fixture "musemaps calibrate 120": the real placement rule on
# 120 random cells each). A design's pick weight is mean/rate, clamped, so each lands about equally often.
_CALIBRATION = json.loads((Path(__file__).resolve().parents[1] / "calibration.json").read_text(encoding="utf-8"))
_MEAN = sum(_CALIBRATION.values()) / len(_CALIBRATION)


def weight_of(design_id: str) -> float:
    rate = _CALIBRATION.get(design_id)
    if rate is None:
        return 1.0
    return round(max(0.5, min(20.0, _MEAN / rate if rate > 0 else 20.0)), 3)


def design(stem: str, entry: dict) -> dict:
    theme = theme_of(stem)
    tier = entry["tier"]
    boss = BOSSES.get(stem)
    if boss:
        tier = 5
    return {
        "theme": theme, "tier": tier, "weight": weight_of(entry["id"]),
        "danger": {"garrison": garrison(stem, theme, tier, entry), "signature": signature(stem, theme, tier, entry),
                   "hazards": hazards(stem, theme)},
        "special": special(stem, theme, tier, entry["name"]),
        "boss": dict(boss) if boss else None,
    }


def validate(catalog):
    assert len(catalog) == 139, len(catalog)
    ids = [e["id"] for e in catalog]
    assert len(set(ids)) == len(ids)
    sigs = {(e["danger"]["signature"]["type"], tuple(sorted(e["danger"]["signature"]["abilities"]))) for e in catalog}
    assert len(sigs) == len(catalog), "custom mobs must be unique"
    full = {repr((e["danger"]["garrison"], e["danger"]["signature"], e["danger"]["hazards"])) for e in catalog}
    assert len(full) == len(catalog), "danger profiles must be unique"
    specials = {(e["special"]["material"], repr(e["special"]["enchants"])) for e in catalog}
    assert len(specials) == len(catalog), "special loot must be unique"
    for e in catalog:
        realms = {g["realm"] for g in e["danger"]["garrison"]}
        assert realms >= {"overworld", "nether", "end"}, e["id"]
        assert e["danger"]["signature"]["type"] not in ("ZOMBIE", "ZOMBIE_VILLAGER"), e["id"]
    bosses = [e for e in catalog if e["boss"]]
    assert len(bosses) == 10 and len({b["boss"]["key"] for b in bosses}) == 10
    zombie_like = sum(e["danger"]["signature"]["type"] in ("HUSK", "PIG_ZOMBIE") for e in catalog)
    assert zombie_like <= len(catalog) // 8, zombie_like


def register(catalog) -> str:
    lines = ["# Muse+GLM_Maps -- structure register (generated by scripts/muse-glm/build_muse_pack.py)", "",
             "The owner's 139 Muse+GLM_Maps schematics, placed by the JasprMuseMaps plugin (separate from every other",
             "structure pack; `/where` names them \"Muse+GLM_Maps\"). Each has dangers from the Overworld, the Nether and",
             "the End, its own custom mob, two hazards and its own special loot; ten have a boss.", "",
             "| Structure | Theme | Tier | Custom mob | Garrison | Hazards | Special loot | Boss |",
             "|---|---|---|---|---|---|---|---|"]
    for e in catalog:
        d = e["danger"]
        sig = d["signature"]
        gar = ", ".join(f"{g['count']}x {g['type'].title().replace('_', ' ')}" for g in d["garrison"])
        hz = ", ".join(f"{h['kind'].title().replace('_', ' ')} ({h['param'].lower()})" for h in d["hazards"])
        sp = e["special"]
        ench = ", ".join(f"{k.split(':')[1]} {v}" for k, v in sp["enchants"])
        boss = e["boss"]["name"] if e["boss"] else ""
        lines.append(f"| {e['name']} | {e['theme']} | {e['tier']} | {sig['name']} ({sig['type'].title().replace('_', ' ')}: "
                     f"{', '.join(a.title().replace('_', ' ') for a in sig['abilities'])}) | {gar} | {hz} | "
                     f"{sp['name']} ({sp['material'].title().replace('_', ' ')}: {ench}) | {boss} |")
    return "\n".join(lines) + "\n"
