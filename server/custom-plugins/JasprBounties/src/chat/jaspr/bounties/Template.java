package chat.jaspr.bounties;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.EnderDragon;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Ghast;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Shulker;
import org.bukkit.entity.Slime;
import org.bukkit.entity.Wolf;

/**
 * One kind of bounty on the board: what to do, how much of it, where it can be done and what tier it pays as. The list
 * is fixed in code (ids are saved in player files, so an id is never reused for something else); a player is offered a
 * realm's bounties once they have set foot in that realm.
 */
final class Template {
    enum Tier {
        EASY("Easy", ChatColor.GREEN), MEDIUM("Medium", ChatColor.YELLOW), HARD("Hard", ChatColor.RED), WEEKLY("Weekly", ChatColor.LIGHT_PURPLE);
        final String label;
        final ChatColor colour;
        Tier(String label, ChatColor colour) { this.label = label; this.colour = colour; }
    }

    enum Goal { KILL, BOSS, MINE, CHOP, HARVEST, FISH, SMELT, BREED, TAME, ENCHANT, TRAVEL }

    /** Where a bounty can be done. OVERWORLD bounties are open to everyone; the others once a player has been there. */
    enum Realm {
        OVERWORLD("world", "the overworld"), NETHER("world_nether", "the Nether"), RUINS("jaspr_ruins", "Drownhollow"),
        ATLAS("jaspr_atlas", "Atlas"), BACKROOMS("jaspr_levels", "the Backrooms");
        final String world, title;
        Realm(String world, String title) { this.world = world; this.title = title; }
        static Realm ofWorld(String name) {
            for (Realm r : values()) if (r.world.equals(name)) return r;
            return null;
        }
        static Realm parse(String s) {
            for (Realm r : values()) if (r.name().equalsIgnoreCase(s)) return r;
            return null;
        }
    }

    final String id, title, hint;
    final Tier tier;
    final Goal goal;
    final Realm realm;
    final Material icon;
    final int min, max;
    final Predicate<LivingEntity> mob;
    final Predicate<Block> block;
    final Set<Material> items;

    private Template(String id, Tier tier, Goal goal, Realm realm, Material icon, int min, int max, String title, String hint,
                     Predicate<LivingEntity> mob, Predicate<Block> block, Set<Material> items) {
        this.id = id; this.tier = tier; this.goal = goal; this.realm = realm; this.icon = icon; this.min = min; this.max = max;
        this.title = title; this.hint = hint; this.mob = mob; this.block = block; this.items = items;
    }

    /** The title with the amount filled in ("Slay 12 Zombies"). */
    String title(int need) { return String.format(title, need); }

    // ---- matching helpers --------------------------------------------------------------------------------------------

    static boolean tagged(Entity e, String tag) { return e.getScoreboardTags().contains(tag); }

    static boolean tagPrefix(Entity e, String prefix) {
        for (String t : e.getScoreboardTags()) if (t.startsWith(prefix)) return true;
        return false;
    }

    /** A creature that belongs to one of the realms' own bestiaries (counted by its realm's bounties, not as a plain zombie). */
    static boolean realmCreature(Entity e) {
        return tagged(e, "jaspr_horror") || tagged(e, "jn_mob") || tagged(e, "br_mob") || tagged(e, "atlas_npc") || tagged(e, "jaspr_boss");
    }

    /** Anything that fights players: vanilla monsters and every realm's hostile creatures, never townsfolk or pets. */
    static boolean hostile(LivingEntity e) {
        if (tagged(e, "atlas_npc")) return tagged(e, "atlas_dominion") || tagPrefix(e, "atlas_boss:");
        if (tagged(e, "br_kind:CITIZEN") || tagged(e, "jn_k_pigtificate")) return false;
        if (tagged(e, "jaspr_horror") || tagged(e, "br_mob") || tagged(e, "jn_mob") || tagged(e, "jaspr_invader") || tagged(e, "jaspr_boss")) return true;
        if (e instanceof Wolf) return ((Wolf) e).isAngry() && !((Wolf) e).isTamed();
        return e instanceof Monster || e instanceof Slime || e instanceof Ghast || e instanceof Shulker || e instanceof EnderDragon;
    }

    private static Predicate<LivingEntity> plain(EntityType... types) {
        EnumSet<EntityType> set = EnumSet.copyOf(Arrays.asList(types));
        return e -> set.contains(e.getType()) && !realmCreature(e);
    }

    private static Predicate<LivingEntity> tag(String tag) { return e -> tagged(e, tag); }

    private static Predicate<LivingEntity> inWorld(String world, EntityType... types) {
        EnumSet<EntityType> set = EnumSet.copyOf(Arrays.asList(types));
        return e -> set.contains(e.getType()) && world.equals(e.getWorld().getName());
    }

    private static Predicate<Block> blocks(Material... types) {
        EnumSet<Material> set = EnumSet.copyOf(Arrays.asList(types));
        return b -> set.contains(b.getType());
    }

    static final Set<Material> ORES = Collections.unmodifiableSet(EnumSet.of(Material.COAL_ORE, Material.IRON_ORE, Material.GOLD_ORE, Material.DIAMOND_ORE,
        Material.REDSTONE_ORE, Material.GLOWING_REDSTONE_ORE, Material.LAPIS_ORE, Material.EMERALD_ORE, Material.QUARTZ_ORE));
    static final Set<Material> LOGS = Collections.unmodifiableSet(EnumSet.of(Material.LOG, Material.LOG_2));

    /** A fully grown crop (wheat, carrots, potatoes, beetroot, nether wart). */
    @SuppressWarnings("deprecation")
    static boolean grown(Block b) {
        switch (b.getType()) {
            case CROPS: case CARROT: case POTATO: return b.getData() >= 7;
            case BEETROOT_BLOCK: case NETHER_WARTS: return b.getData() >= 3;
            default: return false;
        }
    }

    /** Blocks whose placing by a player is remembered, so placing and breaking them again earns nothing. */
    static boolean watchedPlacement(Material m) { return ORES.contains(m) || LOGS.contains(m); }

    // ---- the list ----------------------------------------------------------------------------------------------------

    static final Map<String, Template> ALL;
    static {
        List<Template> t = new ArrayList<>();
        Realm O = Realm.OVERWORLD, N = Realm.NETHER, R = Realm.RUINS, A = Realm.ATLAS, B = Realm.BACKROOMS;
        Tier E = Tier.EASY, M = Tier.MEDIUM, H = Tier.HARD, W = Tier.WEEKLY;

        // easy: everyday survival
        t.add(kill("e_zombie", E, O, Material.ROTTEN_FLESH, 8, 14, "Slay %d Zombies", "They walk at night and in the dark.",
            plain(EntityType.ZOMBIE, EntityType.HUSK, EntityType.ZOMBIE_VILLAGER)));
        t.add(kill("e_skeleton", E, O, Material.BONE, 6, 10, "Slay %d Skeletons", "Archers of the night; mind your shield.",
            plain(EntityType.SKELETON, EntityType.STRAY)));
        t.add(kill("e_spider", E, O, Material.STRING, 6, 10, "Slay %d Spiders", "In caves, and in the open at night.",
            plain(EntityType.SPIDER, EntityType.CAVE_SPIDER)));
        t.add(block("e_coal", E, Goal.MINE, O, Material.COAL, 16, 24, "Mine %d Coal Ore", "Any pickaxe; not silk touch.", blocks(Material.COAL_ORE)));
        t.add(block("e_iron", E, Goal.MINE, O, Material.IRON_INGOT, 8, 14, "Mine %d Iron Ore", "Stone pickaxe or better.", blocks(Material.IRON_ORE)));
        t.add(block("e_logs", E, Goal.CHOP, O, Material.LOG, 32, 48, "Chop %d Logs", "Any tree, any wood.", blocks(Material.LOG, Material.LOG_2)));
        t.add(block("e_crops", E, Goal.HARVEST, O, Material.WHEAT, 24, 40, "Harvest %d grown crops",
            "Fully grown wheat, carrots, potatoes, beetroot or nether wart.", Template::grown));
        t.add(simple("e_fish", E, Goal.FISH, O, Material.FISHING_ROD, 5, 8, "Catch %d fish", "A fishing rod and some patience."));
        t.add(simple("e_breed", E, Goal.BREED, O, Material.WHEAT, 4, 6, "Breed %d animals", "Feed two animals of a kind."));
        t.add(simple("e_travel", E, Goal.TRAVEL, O, Material.LEATHER_BOOTS, 1000, 1500, "Travel %d blocks", "On foot, swimming, riding or by boat."));
        t.add(items("e_smelt", E, O, Material.FURNACE, 12, 20, "Smelt %d Iron Ingots", "Take them out of a furnace.", Material.IRON_INGOT));
        t.add(items("e_cook", E, O, Material.COOKED_BEEF, 10, 16, "Cook %d meals", "Meat or fish from a furnace or smoker.",
            Material.COOKED_BEEF, Material.GRILLED_PORK, Material.COOKED_CHICKEN, Material.COOKED_MUTTON, Material.COOKED_RABBIT, Material.COOKED_FISH,
            Material.BAKED_POTATO));

        // medium: a proper outing
        t.add(kill("m_creeper", M, O, Material.SULPHUR, 5, 8, "Slay %d Creepers", "Before they get close.", plain(EntityType.CREEPER)));
        t.add(kill("m_enderman", M, O, Material.ENDER_PEARL, 3, 5, "Slay %d Endermen", "Look at their feet, then strike.", plain(EntityType.ENDERMAN)));
        t.add(kill("m_witch", M, O, Material.GLASS_BOTTLE, 2, 3, "Slay %d Witches", "In swamps and dark woods.", plain(EntityType.WITCH)));
        t.add(kill("m_monsters", M, O, Material.IRON_SWORD, 20, 30, "Slay %d monsters", "Any hostile creature, anywhere.", Template::hostile));
        t.add(block("m_gold", M, Goal.MINE, O, Material.GOLD_INGOT, 6, 10, "Mine %d Gold Ore", "Deep underground; iron pickaxe.", blocks(Material.GOLD_ORE)));
        t.add(block("m_redstone", M, Goal.MINE, O, Material.REDSTONE, 8, 12, "Mine %d Redstone Ore", "The deepest layers.",
            blocks(Material.REDSTONE_ORE, Material.GLOWING_REDSTONE_ORE)));
        t.add(block("m_lapis", M, Goal.MINE, O, Material.INK_SACK, 4, 6, "Mine %d Lapis Ore", "Deep, beside the gold.", blocks(Material.LAPIS_ORE)));
        t.add(simple("m_enchant", M, Goal.ENCHANT, O, Material.ENCHANTMENT_TABLE, 2, 3, "Enchant %d items", "At an enchanting table."));
        t.add(simple("m_tame", M, Goal.TAME, O, Material.BONE, 1, 2, "Tame %d animals", "Wolves with bones, cats with fish, horses by riding."));
        t.add(simple("m_travel", M, Goal.TRAVEL, O, Material.CHAINMAIL_BOOTS, 2500, 3500, "Travel %d blocks", "Explore: on foot, swimming, riding or by boat."));
        t.add(kill("m_nether", M, N, Material.NETHERRACK, 10, 15, "Slay %d Nether creatures", "Anything hostile in the Nether.",
            e -> "world_nether".equals(e.getWorld().getName()) && hostile(e)));
        t.add(block("m_quartz", M, Goal.MINE, N, Material.QUARTZ, 16, 24, "Mine %d Nether Quartz Ore", "In the Nether's netherrack.", blocks(Material.QUARTZ_ORE)));
        t.add(kill("m_horror", M, R, Material.PRISMARINE_SHARD, 6, 10, "Slay %d Drownhollow horrors", "Inside the drowned ruins and their catacombs.",
            tag("jaspr_horror")));
        t.add(kill("m_dominion", M, A, Material.IRON_CHESTPLATE, 5, 8, "Slay %d Dominion soldiers", "Their camps and fortresses in Atlas.",
            tag("atlas_dominion")));
        t.add(kill("m_backrooms", M, B, Material.SPONGE, 5, 8, "Slay %d Backrooms entities", "Anything that hunts you in the Levels.",
            e -> tagged(e, "br_mob") && !tagged(e, "br_boss") && hostile(e)));

        // hard: a day's work
        t.add(block("h_diamond", H, Goal.MINE, O, Material.DIAMOND, 3, 5, "Mine %d Diamond Ore", "The bottom of the world; iron pickaxe.",
            blocks(Material.DIAMOND_ORE)));
        t.add(kill("h_monsters", H, O, Material.DIAMOND_SWORD, 45, 60, "Slay %d monsters", "Any hostile creature, anywhere.", Template::hostile));
        t.add(block("h_ores", H, Goal.MINE, O, Material.IRON_PICKAXE, 48, 64, "Mine %d ores of any kind", "Coal, iron, gold, redstone, lapis, diamond...",
            b -> ORES.contains(b.getType())));
        t.add(kill("h_creeper", H, O, Material.TNT, 10, 14, "Slay %d Creepers", "Strike first, then step back.", plain(EntityType.CREEPER)));
        t.add(kill("h_enderman", H, O, Material.EYE_OF_ENDER, 6, 9, "Slay %d Endermen", "Look at their feet, then strike.", plain(EntityType.ENDERMAN)));
        t.add(simple("h_travel", H, Goal.TRAVEL, O, Material.IRON_BOOTS, 5000, 6000, "Travel %d blocks", "A long walk: on foot, swimming, riding or by boat."));
        t.add(simple("h_fish", H, Goal.FISH, O, Material.RAW_FISH, 16, 24, "Catch %d fish", "A long afternoon at the water."));
        t.add(kill("h_blaze", H, N, Material.BLAZE_ROD, 6, 10, "Slay %d Blazes", "Around the Nether's fortresses.", inWorld("world_nether", EntityType.BLAZE)));
        t.add(kill("h_nether_elite", H, N, Material.MAGMA_CREAM, 2, 4, "Slay %d elite Nether creatures", "The stronger of their kind, in strongholds.",
            tag("jn_elite")));
        t.add(kill("h_nether_many", H, N, Material.NETHER_BRICK_ITEM, 25, 35, "Slay %d Nether creatures", "Anything hostile in the Nether.",
            e -> "world_nether".equals(e.getWorld().getName()) && hostile(e)));
        t.add(kill("h_elder", H, R, Material.PRISMARINE_CRYSTALS, 1, 2, "Slay %d Elder horrors", "The purple-hazed elders, far from the gates.",
            tag("jaspr_horror_elite")));
        t.add(kill("h_horrors", H, R, Material.SEA_LANTERN, 15, 20, "Slay %d Drownhollow horrors", "Deep in the ruins: crypts, catacombs, old cities.",
            tag("jaspr_horror")));
        t.add(kill("h_dominion", H, A, Material.DIAMOND_CHESTPLATE, 12, 16, "Slay %d Dominion soldiers", "Their camps and fortresses in Atlas.",
            tag("atlas_dominion")));
        t.add(kill("h_backrooms_elite", H, B, Material.GOLD_NUGGET, 1, 2, "Slay %d elite Backrooms entities", "The marked ones, deeper in the Levels.",
            tag("br_elite")));

        // weekly: the big contracts
        t.add(kill("w_slayer", W, O, Material.DIAMOND_SWORD, 250, 350, "Slay %d monsters", "Any hostile creature, anywhere, all week.", Template::hostile));
        t.add(block("w_miner", W, Goal.MINE, O, Material.DIAMOND_PICKAXE, 150, 200, "Mine %d ores", "Any ore, any realm, all week.", b -> ORES.contains(b.getType())));
        t.add(simple("w_traveler", W, Goal.TRAVEL, O, Material.DIAMOND_BOOTS, 15000, 20000, "Travel %d blocks", "Go and see the world."));
        t.add(kill("w_invaders", W, O, Material.SKULL_ITEM, 25, 40, "Slay %d invaders", "They come for those who sleep (see /invasion status).",
            tag("jaspr_invader")));
        t.add(kill("w_wither", W, O, Material.NETHER_STAR, 1, 1, "Slay the Wither", "Build it from soul sand and three wither skulls. Bring friends.",
            e -> e.getType() == EntityType.WITHER));
        t.add(boss("w_lord", N, Material.BLAZE_POWDER, "Conquer a Nether Lord", "Ten Lords hold strongholds in the Nether; the guide's compass knows the way.",
            tag("jn_lord")));
        t.add(boss("w_ghast_queen", N, Material.GHAST_TEAR, "Slay the Ghast Queen", "The Urn of Sorrow, on the Spore Cathedral's crown.",
            tag("jn_k_ghast_queen")));
        t.add(boss("w_warden", R, Material.EYE_OF_ENDER, "Slay a Drownhollow Warden", "The cult sites hold five Wardens; each guards a Seal.",
            e -> tagged(e, "jaspr_boss") && "jaspr_ruins".equals(e.getWorld().getName())));
        t.add(boss("w_atlas_boss", A, Material.GOLD_HELMET, "Defeat a lord of Atlas", "The Dominion's warlords and the titans of Atlas.",
            e -> tagPrefix(e, "atlas_boss:")));
        t.add(boss("w_backrooms_boss", B, Material.REDSTONE_LAMP_OFF, "Defeat a Backrooms boss", "Each Level has its keeper.", tag("br_boss")));

        Map<String, Template> m = new LinkedHashMap<>();
        for (Template x : t) if (m.put(x.id, x) != null) throw new IllegalStateException("duplicate bounty id " + x.id);
        ALL = Collections.unmodifiableMap(m);
    }

    private static Template kill(String id, Tier tier, Realm realm, Material icon, int min, int max, String title, String hint, Predicate<LivingEntity> mob) {
        return new Template(id, tier, Goal.KILL, realm, icon, min, max, title, hint, mob, null, null);
    }

    private static Template boss(String id, Realm realm, Material icon, String title, String hint, Predicate<LivingEntity> mob) {
        return new Template(id, Tier.WEEKLY, Goal.BOSS, realm, icon, 1, 1, title, hint, mob, null, null);
    }

    private static Template block(String id, Tier tier, Goal goal, Realm realm, Material icon, int min, int max, String title, String hint, Predicate<Block> block) {
        return new Template(id, tier, goal, realm, icon, min, max, title, hint, null, block, null);
    }

    private static Template items(String id, Tier tier, Realm realm, Material icon, int min, int max, String title, String hint, Material... results) {
        return new Template(id, tier, Goal.SMELT, realm, icon, min, max, title, hint, null, null, EnumSet.copyOf(Arrays.asList(results)));
    }

    private static Template simple(String id, Tier tier, Goal goal, Realm realm, Material icon, int min, int max, String title, String hint) {
        return new Template(id, tier, goal, realm, icon, min, max, title, hint, null, null, null);
    }

    static Template get(String id) { return ALL.get(id); }

    /** Bounties of a tier that a player who has been to these realms can be offered. */
    static List<Template> offer(Tier tier, Set<Realm> realms) {
        List<Template> out = new ArrayList<>();
        for (Template t : ALL.values()) if (t.tier == tier && (t.realm == Realm.OVERWORLD || realms.contains(t.realm))) out.add(t);
        return out;
    }
}
