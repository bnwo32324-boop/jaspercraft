package chat.jaspr.backrooms;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.server.v1_12_R1.NBTTagCompound;
import net.minecraft.server.v1_12_R1.NBTTagList;
import org.bukkit.ChatColor;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.craftbukkit.v1_12_R1.inventory.CraftItemStack;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

/**
 * The Backrooms' own gear (owner, 2026-09-30: custom armour, trinkets, tools and weapons themed for each liminal space).
 * Every level has a four-piece armour set with a set bonus, a weapon, a tool and two trinkets; the Poolrooms' final
 * boss also gives the Exit Sign. All are vanilla items carrying NBT {JasprBackrooms:{id:"..."}} (what the effects look
 * for; survival cannot forge it), a name in the level's colour, and lore that says what they do.
 */
final class Items {
    private Items() {}

    enum Kind { HELMET, CHEST, LEGS, BOOTS, WEAPON, TOOL, TRINKET, CONSUMABLE }

    static final class Def {
        final String id, name;
        final Level level;
        final Kind kind;
        final Material material;
        final String[] lore;
        final Map<Enchantment, Integer> enchants = new LinkedHashMap<>();
        final List<Object[]> mods = new ArrayList<>();
        Color dye;
        boolean unbreakable, glint;
        Def(String id, Level level, Kind kind, Material material, String name, String... lore) {
            this.id = id; this.level = level; this.kind = kind; this.material = material; this.name = name; this.lore = lore;
        }
        Def ench(Enchantment e, int n) { enchants.put(e, n); return this; }
        Def dye(int rgb) { dye = Color.fromRGB(rgb); return this; }
        /** An attribute modifier: generic attribute name, amount (operation 0), slot. */
        Def mod(String attribute, double amount, String slot) { mods.add(new Object[] {attribute, amount, slot}); return this; }
        Def unbreakable() { unbreakable = true; return this; }
        Def glint() { glint = true; return this; }
        boolean armour() { return kind == Kind.HELMET || kind == Kind.CHEST || kind == Kind.LEGS || kind == Kind.BOOTS; }
    }

    static final Map<String, Def> DEFS = new LinkedHashMap<>();
    /** Each level's armour set: its name and what wearing all four pieces does. */
    static final String[] SET_NAMES = {"Wanderer", "Warehouse Worker", "Boiler Suit", "Lineman", "Executive", "Night Watch", "Lifeguard"};
    static final String[] SET_BONUS = {
        "Unnoticed: monsters only notice you from 8 blocks away",
        "Steady: no knockback, crates cannot crush you",
        "Fireproof: no heat, no burns",
        "Insulated: shocks and lightning cannot hurt you",
        "Productive: Haste II, and office monsters ignore you until you strike",
        "Street-wise: Mimics show their names, arrows hit you for half",
        "At home in water: breathe, swim fast, heal, no undertow"};

    private static Def def(Def d) { DEFS.put(d.id, d); return d; }

    private static void set(Level lv, String[] ids, String[] names, Material[] mats, int dye, Enchantment main, int level, Enchantment extra, int extraLevel) {
        Kind[] kinds = {Kind.HELMET, Kind.CHEST, Kind.LEGS, Kind.BOOTS};
        for (int i = 0; i < 4; i++) {
            Def d = def(new Def(ids[i], lv, kinds[i], mats[i], names[i], "Set: " + SET_NAMES[lv.index()] + " (wear all four)", SET_BONUS[lv.index()]));
            d.ench(main, level);
            if (extra != null) d.ench(extra, extraLevel);
            if (mats[i].name().startsWith("LEATHER")) d.dye(dye);
            d.ench(Enchantment.DURABILITY, 2 + lv.index() / 3);
        }
    }

    static {
        Material LH = Material.LEATHER_HELMET, LC = Material.LEATHER_CHESTPLATE, LL = Material.LEATHER_LEGGINGS, LB = Material.LEATHER_BOOTS;
        Enchantment PROT = Enchantment.PROTECTION_ENVIRONMENTAL, FIRE = Enchantment.PROTECTION_FIRE, BLAST = Enchantment.PROTECTION_EXPLOSIONS,
            PROJ = Enchantment.PROTECTION_PROJECTILE, FALL = Enchantment.PROTECTION_FALL, SHARP = Enchantment.DAMAGE_ALL, EFF = Enchantment.DIG_SPEED,
            UNB = Enchantment.DURABILITY;
        // ---- Level 1: the Yellow Rooms ----
        set(Level.YELLOW, new String[] {"wanderer_hood", "wanderer_vest", "wanderer_trousers", "wanderer_sneakers"},
            new String[] {"Wanderer's Hood", "Wallpaper Vest", "Carpet Trousers", "Damp Sneakers"}, new Material[] {LH, LC, LL, LB}, 0xE3CF7A, PROT, 1, null, 0);
        def(new Def("rebar", Level.YELLOW, Kind.WEAPON, Material.STICK, "Rebar", "A length of steel pulled from a wall.", "Heavy and slow."))
            .mod("generic.attackDamage", 6.0, "mainhand").mod("generic.attackSpeed", -2.8, "mainhand").glint();
        def(new Def("utility_knife", Level.YELLOW, Kind.TOOL, Material.SHEARS, "Utility Knife", "Cuts carpet, cobwebs and worse."))
            .ench(EFF, 3).ench(UNB, 2).mod("generic.attackDamage", 3.0, "mainhand").mod("generic.attackSpeed", -1.0, "mainhand");
        def(new Def("canteen", Level.YELLOW, Kind.TRINKET, Material.GLASS_BOTTLE, "Almond Water Canteen", "Right-click: drink (heals 3 hearts", "and Regeneration). Refills in 90 s.")).glint();
        def(new Def("flickering_bulb", Level.YELLOW, Kind.TRINKET, Material.GLOWSTONE_DUST, "Flickering Bulb", "Carried: Regeneration while you are", "below 40% health.")).glint();
        // ---- Level 2: the Warehouse ----
        set(Level.WAREHOUSE, new String[] {"hard_hat", "hi_vis_vest", "work_trousers", "steel_toe_boots"},
            new String[] {"Hard Hat", "Hi-Vis Vest", "Work Trousers", "Steel-Toe Boots"}, new Material[] {Material.GOLD_HELMET, LC, LL, Material.IRON_BOOTS}, 0xF2A10F, PROT, 2, PROJ, 1);
        DEFS.get("work_trousers").dye(0x2C3E66);
        def(new Def("crowbar", Level.WAREHOUSE, Kind.WEAPON, Material.IRON_AXE, "Crowbar", "Opens crates. And heads.")).ench(SHARP, 2).ench(Enchantment.KNOCKBACK, 1).ench(UNB, 2);
        def(new Def("pallet_hook", Level.WAREHOUSE, Kind.TOOL, Material.IRON_PICKAXE, "Pallet Hook", "Built for moving heavy things.")).ench(EFF, 4).ench(Enchantment.LOOT_BONUS_BLOCKS, 1).ench(UNB, 2);
        def(new Def("forklift_key", Level.WAREHOUSE, Kind.TRINKET, Material.TRIPWIRE_HOOK, "Forklift Key", "Carried: Speed.")).glint();
        def(new Def("packing_tape", Level.WAREHOUSE, Kind.TRINKET, Material.SLIME_BALL, "Roll of Packing Tape", "Off hand: Resistance while you are", "below half health.")).glint();
        // ---- Level 3: the Maintenance Tunnels ----
        set(Level.TUNNELS, new String[] {"welding_mask", "boiler_suit", "asbestos_trousers", "hobnail_boots"},
            new String[] {"Welding Mask", "Boiler Suit", "Asbestos Trousers", "Hobnail Boots"}, new Material[] {Material.IRON_HELMET, LC, LL, Material.CHAINMAIL_BOOTS}, 0xC4541A, FIRE, 3, PROT, 1);
        DEFS.get("asbestos_trousers").dye(0x7A7A74);
        DEFS.get("hobnail_boots").ench(FALL, 3);
        def(new Def("pipe_wrench", Level.TUNNELS, Kind.WEAPON, Material.IRON_HOE, "Pipe Wrench", "Still hot from the pipes: sets foes alight."))
            .mod("generic.attackDamage", 8.0, "mainhand").mod("generic.attackSpeed", -3.0, "mainhand").ench(Enchantment.FIRE_ASPECT, 1).ench(UNB, 3);
        def(new Def("valve_key", Level.TUNNELS, Kind.TOOL, Material.IRON_SPADE, "Valve Key", "Turns what will not turn.")).ench(EFF, 4).ench(UNB, 3);
        def(new Def("pressure_gauge", Level.TUNNELS, Kind.TRINKET, Material.GOLD_PLATE, "Pressure Gauge", "Carried: heat rises at half speed.")).glint();
        def(new Def("coolant_vial", Level.TUNNELS, Kind.TRINKET, Material.PRISMARINE_CRYSTALS, "Coolant Vial", "Right-click: cool down, put out fire", "and resist fire for 20 s. Every 90 s.")).glint();
        // ---- Level 4: the Electrical Corridors ----
        set(Level.ELECTRICAL, new String[] {"insulated_helmet", "rubber_apron", "lineman_trousers", "rubber_boots"},
            new String[] {"Insulated Helmet", "Rubber Apron", "Lineman's Trousers", "Rubber Boots"}, new Material[] {Material.CHAINMAIL_HELMET, LC, Material.CHAINMAIL_LEGGINGS, LB}, 0x1E1E1E, PROT, 3, BLAST, 2);
        DEFS.get("rubber_boots").dye(0xE6C229);
        def(new Def("arc_baton", Level.ELECTRICAL, Kind.WEAPON, Material.BLAZE_ROD, "Arc Baton", "Every blow arcs to a second foe nearby."))
            .mod("generic.attackDamage", 7.0, "mainhand").mod("generic.attackSpeed", -2.4, "mainhand").glint();
        def(new Def("wire_cutters", Level.ELECTRICAL, Kind.TOOL, Material.SHEARS, "Wire Cutters", "Insulated handles.")).ench(EFF, 5).ench(UNB, 3);
        def(new Def("surge_protector", Level.ELECTRICAL, Kind.TRINKET, Material.REDSTONE_COMPARATOR, "Surge Protector", "Carried: shocks, lightning and", "explosions hurt you 70% less.")).glint();
        def(new Def("capacitor", Level.ELECTRICAL, Kind.TRINKET, Material.REDSTONE_TORCH_ON, "Capacitor", "Right-click: discharge into up to three", "monsters within 10 blocks. Every 30 s.")).glint();
        // ---- Level 5: the Abandoned Office ----
        set(Level.OFFICE, new String[] {"executive_fedora", "suit_jacket", "suit_trousers", "oxford_shoes"},
            new String[] {"Executive Fedora", "Suit Jacket", "Suit Trousers", "Oxford Shoes"}, new Material[] {LH, Material.IRON_CHESTPLATE, Material.IRON_LEGGINGS, LB}, 0x333338, PROT, 3, null, 0);
        DEFS.get("oxford_shoes").dye(0x3B2412).ench(FALL, 4);
        def(new Def("letter_opener", Level.OFFICE, Kind.WEAPON, Material.IRON_SWORD, "Letter Opener", "Quick and quiet.")).ench(SHARP, 3).ench(UNB, 3)
            .mod("generic.attackDamage", 6.0, "mainhand").mod("generic.attackSpeed", -1.6, "mainhand");
        def(new Def("staple_gun", Level.OFFICE, Kind.TOOL, Material.BOW, "Staple Gun", "Fires faster than it should.")).ench(Enchantment.ARROW_DAMAGE, 3).ench(Enchantment.ARROW_KNOCKBACK, 1).ench(UNB, 2);
        def(new Def("employee_badge", Level.OFFICE, Kind.TRINKET, Material.NAME_TAG, "Employee ID Badge", "Carried: monsters of the office and", "the city ignore you until you strike.")).glint();
        def(new Def("cold_coffee", Level.OFFICE, Kind.TRINKET, Material.BOWL, "Cold Coffee", "Right-click: Haste II and Speed II", "for 30 s. Every 2 minutes.")).glint();
        // ---- Level 6: the Endless City ----
        set(Level.CITY, new String[] {"watchman_cap", "trench_coat", "patrol_trousers", "pavement_boots"},
            new String[] {"Watchman's Cap", "Trench Coat", "Patrol Trousers", "Pavement Boots"}, new Material[] {Material.IRON_HELMET, LC, Material.DIAMOND_LEGGINGS, Material.DIAMOND_BOOTS}, 0x8B7355, PROT, 3, PROJ, 3);
        def(new Def("stop_sign", Level.CITY, Kind.WEAPON, Material.SIGN, "Stop Sign", "STOP.")).mod("generic.attackDamage", 9.0, "mainhand")
            .mod("generic.attackSpeed", -2.9, "mainhand").ench(Enchantment.KNOCKBACK, 2).glint();
        def(new Def("jackhammer", Level.CITY, Kind.TOOL, Material.DIAMOND_PICKAXE, "Jackhammer", "Road works. Not here, though.")).ench(EFF, 5).ench(UNB, 3);
        def(new Def("dead_mans_watch", Level.CITY, Kind.TRINKET, Material.WATCH, "Dead Man's Watch", "Carried: once every 5 minutes, a blow", "that would kill you leaves you alive.")).glint();
        def(new Def("subway_token", Level.CITY, Kind.TRINKET, Material.GOLD_NUGGET, "Subway Token", "Right-click: back to the landing of the", "level you are in. Every 5 minutes.")).glint();
        // ---- Level 7: the Poolrooms ----
        set(Level.POOLS, new String[] {"swim_cap", "lifeguard_top", "swim_trunks", "flippers"},
            new String[] {"Swim Cap", "Lifeguard's Top", "Swim Trunks", "Flippers"}, new Material[] {Material.DIAMOND_HELMET, Material.DIAMOND_CHESTPLATE, Material.DIAMOND_LEGGINGS, Material.DIAMOND_BOOTS}, 0, PROT, 4, null, 0);
        DEFS.get("swim_cap").ench(Enchantment.OXYGEN, 3).ench(Enchantment.WATER_WORKER, 1);
        DEFS.get("flippers").ench(Enchantment.DEPTH_STRIDER, 3).ench(FALL, 4);
        def(new Def("tidebreaker", Level.POOLS, Kind.WEAPON, Material.DIAMOND_SWORD, "Tidebreaker", "Deals double damage to anything in water.")).ench(SHARP, 5).ench(UNB, 3);
        def(new Def("pool_skimmer", Level.POOLS, Kind.TOOL, Material.FISHING_ROD, "Pool Skimmer", "Hooks anything. Pulls hard.")).ench(Enchantment.LURE, 3).ench(Enchantment.LUCK, 3).ench(UNB, 3);
        def(new Def("rubber_duck", Level.POOLS, Kind.TRINKET, Material.RABBIT_FOOT, "Rubber Duck", "Carried: breathe under water, no", "undertow, and heal while in water.")).glint();
        def(new Def("whistle", Level.POOLS, Kind.TRINKET, Material.IRON_NUGGET, "Lifeguard's Whistle", "Right-click: monsters within 12 blocks", "are slowed and weakened. Every 45 s.")).glint();
        // ---- the way out ----
        def(new Def("exit_sign", Level.POOLS, Kind.TRINKET, Material.ITEM_FRAME, "The Exit Sign", "You found the way out of the Backrooms.", "Carried: Speed, Resistance, and", "Regeneration below 40% health.")).glint();
        def(new Def("almond_water", Level.YELLOW, Kind.CONSUMABLE, Material.POTION, "Almond Water", "Heals, and calms the nerves."));
    }

    /** The level items of a kind (armour pieces, weapon, tool, trinkets) that drop in a level. */
    static List<Def> of(Level lv) {
        List<Def> out = new ArrayList<>();
        for (Def d : DEFS.values()) if (d.level == lv && d.kind != Kind.CONSUMABLE && !d.id.equals("exit_sign")) out.add(d);
        return out;
    }

    static List<Def> set(Level lv) {
        List<Def> out = new ArrayList<>();
        for (Def d : DEFS.values()) if (d.level == lv && d.armour()) out.add(d);
        return out;
    }

    // ---- making them --------------------------------------------------------------------------------------------------
    static ItemStack make(String id) {
        Def d = DEFS.get(id);
        return d == null ? null : make(d);
    }

    static ItemStack make(Def d) {
        ItemStack s = new ItemStack(d.material);
        ItemMeta m = s.getItemMeta();
        m.setDisplayName(ChatColor.RESET + "" + d.level.colour + d.name);
        List<String> lore = new ArrayList<>();
        for (String line : d.lore) lore.add(ChatColor.GRAY + line);
        lore.add(ChatColor.DARK_GRAY + (d.id.equals("exit_sign") ? "The Backrooms" : "Backrooms - " + d.level.title));
        m.setLore(lore);
        for (Map.Entry<Enchantment, Integer> e : d.enchants.entrySet()) m.addEnchant(e.getKey(), e.getValue(), true);
        if (d.glint && d.enchants.isEmpty()) { m.addEnchant(Enchantment.DURABILITY, 1, true); m.addItemFlags(ItemFlag.HIDE_ENCHANTS); }
        if (d.dye != null && m instanceof LeatherArmorMeta) ((LeatherArmorMeta) m).setColor(d.dye);
        if (d.unbreakable) m.setUnbreakable(true);
        if (m instanceof PotionMeta && d.id.equals("almond_water")) {
            PotionMeta p = (PotionMeta) m;
            p.setColor(Color.fromRGB(0xF2E6C9));
            p.addCustomEffect(new PotionEffect(PotionEffectType.HEAL, 1, 0), true);
            p.addCustomEffect(new PotionEffect(PotionEffectType.REGENERATION, 160, 0), true);
        }
        s.setItemMeta(m);
        return tag(s, d);
    }

    private static ItemStack tag(ItemStack s, Def d) {
        net.minecraft.server.v1_12_R1.ItemStack n = CraftItemStack.asNMSCopy(s);
        NBTTagCompound tag = n.hasTag() ? n.getTag() : new NBTTagCompound();
        NBTTagCompound mine = new NBTTagCompound();
        mine.setString("id", d.id);
        tag.set("JasprBackrooms", mine);
        if (!d.mods.isEmpty()) {
            NBTTagList list = new NBTTagList();
            int k = 0;
            for (Object[] mod : d.mods) {
                NBTTagCompound a = new NBTTagCompound();
                a.setString("AttributeName", (String) mod[0]);
                a.setString("Name", "jaspr_backrooms_" + d.id);
                a.setDouble("Amount", (Double) mod[1]);
                a.setInt("Operation", 0);
                UUID u = UUID.nameUUIDFromBytes(("jaspr_backrooms:" + d.id + ":" + k++).getBytes(java.nio.charset.StandardCharsets.UTF_8));
                a.setLong("UUIDMost", u.getMostSignificantBits());
                a.setLong("UUIDLeast", u.getLeastSignificantBits());
                a.setString("Slot", (String) mod[2]);
                list.add(a);
            }
            tag.set("AttributeModifiers", list);
        }
        n.setTag(tag);
        return CraftItemStack.asBukkitCopy(n);
    }

    // ---- recognising them ---------------------------------------------------------------------------------------------
    /** The Backrooms id of a stack, or null. */
    static String id(ItemStack s) {
        if (s == null || s.getType() == Material.AIR || !s.hasItemMeta()) return null;
        try {
            net.minecraft.server.v1_12_R1.ItemStack n = CraftItemStack.asNMSCopy(s);
            if (!n.hasTag() || !n.getTag().hasKeyOfType("JasprBackrooms", 10)) return null;
            String id = n.getTag().getCompound("JasprBackrooms").getString("id");
            return id.isEmpty() ? null : id;
        } catch (RuntimeException e) {
            return null;
        }
    }

    static Def def(ItemStack s) { String id = id(s); return id == null ? null : DEFS.get(id); }

    static List<String> ids() { return Collections.unmodifiableList(new ArrayList<>(DEFS.keySet())); }
}
