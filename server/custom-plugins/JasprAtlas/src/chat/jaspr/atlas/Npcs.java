package chat.jaspr.atlas;

import java.util.Random;
import net.minecraft.server.v1_12_R1.BlockPosition;
import net.minecraft.server.v1_12_R1.EnumColor;
import net.minecraft.server.v1_12_R1.TileEntity;
import net.minecraft.server.v1_12_R1.TileEntityBed;
import org.bukkit.ChatColor;
import org.bukkit.Chunk;
import org.bukkit.DyeColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.block.Banner;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.Chest;
import org.bukkit.block.CreatureSpawner;
import org.bukkit.block.FlowerPot;
import org.bukkit.block.Sign;
import org.bukkit.block.banner.Pattern;
import org.bukkit.block.banner.PatternType;
import org.bukkit.craftbukkit.v1_12_R1.CraftWorld;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Blaze;
import org.bukkit.entity.Cow;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Evoker;
import org.bukkit.entity.Husk;
import org.bukkit.entity.IronGolem;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Sheep;
import org.bukkit.entity.Skeleton;
import org.bukkit.entity.SkeletonHorse;
import org.bukkit.entity.Villager;
import org.bukkit.entity.Vindicator;
import org.bukkit.entity.Wolf;
import org.bukkit.entity.Zombie;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.material.MaterialData;

/**
 * Brings the plan's people and fittings to life when a chunk is populated: named citizens at their work (posed, so they
 * stay where they belong; children and travellers walk), merchants, the key figures (invulnerable and unique), Talos
 * automata, farm animals, the Dominion's garrisons by role, the Bound and named captives; and it finishes chests,
 * signs, spawners, beds, banners, flower pots and statues. Markers (bosses, mechanisms, heliodromes, berths, camps,
 * wards, the monument) spawn nothing: the {@link Registry} finds them from the plan.
 */
final class Npcs implements AtlasPopulator.Sink {
    static final String TAG = "atlas_npc", KEY = "atlas_key:", CITIZEN = "atlas_citizen", MERCHANT = "atlas_merchant:", TALOS = "atlas_talos",
        DOMINION = "atlas_dominion", DOM_KIND = "atlas_dom:", BOUND = "atlas_bound", CAPTIVE = "atlas_captive:", CAMP = "atlas_camp:", CHOIR = "atlas_choir",
        STAND = "atlas_stand", EXEMPT = "jaspr_daylight_exempt";

    private final AtlasPlugin plugin;
    volatile long spawned, finished;

    Npcs(AtlasPlugin plugin) { this.plugin = plugin; }

    @Override
    @SuppressWarnings("deprecation")
    public void tile(World world, Chunk chunk, Canvas.Tile t, Random random) {
        Block b = world.getBlockAt(t.x, t.y, t.z);
        switch (t.kind) {
            case Canvas.CHEST_TILE: {
                if (b.getType() != Material.CHEST) return;
                BlockState st = b.getState();
                if (st instanceof Chest) Loot.fill(plugin, ((Chest) st).getBlockInventory(), t.what, t.extras, new Random(Hash.of(plugin.seed(), t.x, t.y, t.z)));
                finished++;
                return;
            }
            case Canvas.SIGN_TILE: case Canvas.STANDING_SIGN_TILE: {
                BlockState st = b.getState();
                if (!(st instanceof Sign)) return;
                String[] lines = t.what.split("\n", -1);
                for (int i = 0; i < 4 && i < lines.length; i++) ((Sign) st).setLine(i, lines[i]);
                st.update(true, false);
                finished++;
                return;
            }
            case Canvas.SPAWNER_TILE: {
                BlockState st = b.getState();
                if (!(st instanceof CreatureSpawner)) return;
                try { ((CreatureSpawner) st).setSpawnedType(EntityType.valueOf(t.what)); } catch (IllegalArgumentException ignored) { }
                ((CreatureSpawner) st).setDelay(200);
                st.update(true, false);
                return;
            }
            case Canvas.DISPENSER_TILE: {
                BlockState st = b.getState();
                if (st instanceof org.bukkit.block.Dispenser) ((org.bukkit.block.Dispenser) st).getInventory().addItem(new ItemStack(Material.ARROW, 16));
                return;
            }
            case Canvas.BED_TILE: {
                TileEntity te = ((CraftWorld) world).getHandle().getTileEntity(new BlockPosition(t.x, t.y, t.z));
                if (te instanceof TileEntityBed) { ((TileEntityBed) te).a(EnumColor.fromColorIndex(t.meta)); te.update(); }
                return;
            }
            case Canvas.BANNER_TILE: banner(b, t.what); return;
            case Canvas.POT_TILE: pot(b, t.what); return;
            case Canvas.STAND_TILE: stand(world, t); return;
            case Canvas.NPC_TILE: npc(world, t); return;
            default:
        }
    }

    // ------------------------------------------------------------------ fittings

    private static void banner(Block b, String pattern) {
        BlockState st = b.getState();
        if (!(st instanceof Banner)) return;
        Banner banner = (Banner) st;
        if (pattern.equals("concord")) {
            banner.setBaseColor(DyeColor.LIGHT_BLUE);
            banner.addPattern(new Pattern(DyeColor.WHITE, PatternType.RHOMBUS_MIDDLE));
            banner.addPattern(new Pattern(DyeColor.WHITE, PatternType.CROSS));
            banner.addPattern(new Pattern(DyeColor.LIGHT_BLUE, PatternType.CIRCLE_MIDDLE));
            banner.addPattern(new Pattern(DyeColor.WHITE, PatternType.BORDER));
        } else {   // black_ash: the Dominion's standard, a red ember on black between grey spikes
            banner.setBaseColor(DyeColor.BLACK);
            banner.addPattern(new Pattern(DyeColor.GRAY, PatternType.TRIANGLES_BOTTOM));
            banner.addPattern(new Pattern(DyeColor.RED, PatternType.CIRCLE_MIDDLE));
            banner.addPattern(new Pattern(DyeColor.BLACK, PatternType.FLOWER));
            banner.addPattern(new Pattern(DyeColor.GRAY, PatternType.TRIANGLES_TOP));
        }
        banner.update(true, false);
    }

    @SuppressWarnings("deprecation")
    private static void pot(Block b, String plant) {
        BlockState st = b.getState();
        if (!(st instanceof FlowerPot) || plant.equals("empty")) return;
        String[] p = plant.split(":");
        Material m = p[0].equals("red_flower") ? Material.RED_ROSE : p[0].equals("sapling") ? Material.SAPLING : p[0].equals("cactus") ? Material.CACTUS
            : p[0].equals("deadbush") ? Material.DEAD_BUSH : Material.YELLOW_FLOWER;
        byte data = p.length > 1 ? (byte) Integer.parseInt(p[1]) : 0;
        ((FlowerPot) st).setContents(new MaterialData(m, data));
        st.update(true, false);
    }

    private static ItemStack dyed(Material m, int rgb) {
        ItemStack i = new ItemStack(m);
        LeatherArmorMeta meta = (LeatherArmorMeta) i.getItemMeta();
        meta.setColor(org.bukkit.Color.fromRGB(rgb));
        i.setItemMeta(meta);
        return i;
    }

    private void stand(World w, Canvas.Tile t) {
        Location at = new Location(w, t.x + 0.5, t.y, t.z + 0.5, t.meta, 0);
        ArmorStand s = w.spawn(at, ArmorStand.class, a -> {
            a.addScoreboardTag(STAND);
            a.setGravity(false);
            a.setBasePlate(false);
            a.setArms(true);
            EntityEquipment e = a.getEquipment();
            switch (t.what) {
                case "scarecrow":
                    e.setHelmet(new ItemStack(Material.PUMPKIN)); e.setChestplate(dyed(Material.LEATHER_CHESTPLATE, 0x8a6a3a)); e.setLeggings(dyed(Material.LEATHER_LEGGINGS, 0x6a5030));
                    break;
                case "target":
                    e.setChestplate(dyed(Material.LEATHER_CHESTPLATE, 0xd0c0a0)); a.setCustomName(ChatColor.GRAY + "Training post");
                    break;
                case "hoplite_rack":
                    e.setHelmet(dyed(Material.LEATHER_HELMET, 0xe8e8f0)); e.setChestplate(dyed(Material.LEATHER_CHESTPLATE, 0x3a78c8)); e.setLeggings(dyed(Material.LEATHER_LEGGINGS, 0xe8e8f0));
                    e.setItemInMainHand(new ItemStack(Material.WOOD_SWORD)); e.setItemInOffHand(new ItemStack(Material.SHIELD));
                    break;
                default:   // orc_rack
                    e.setHelmet(dyed(Material.LEATHER_HELMET, 0x1a1a1a)); e.setChestplate(dyed(Material.LEATHER_CHESTPLATE, 0x2a1a1a)); e.setItemInMainHand(new ItemStack(Material.STONE_AXE));
            }
        });
        spawned++;
    }

    // ------------------------------------------------------------------ people and creatures

    private void npc(World w, Canvas.Tile t) {
        String kind = t.what;
        int colon = kind.indexOf(':');
        String family = colon < 0 ? kind : kind.substring(0, colon), what = colon < 0 ? "" : kind.substring(colon + 1);
        Location at = new Location(w, t.x + 0.5, t.y, t.z + 0.5, t.meta, 0);
        long h = Hash.of(plugin.seed() ^ 0x9EC7L, t.x, t.y, t.z);
        switch (family) {
            case "citizen": citizen(at, what, h, false); break;
            case "merchant": citizen(at, what, h, true); break;
            case "key": if (plugin.registry().keyAlive(what)) return; keyFigure(at, what); break;
            case "talker": if (plugin.registry().talkerAlive(what)) return; talker(at, what); break;
            case "talos": talos(at); break;
            case "animal": animal(at, what); break;
            case "dominion": dominion(at, what, h); break;
            case "bound": bound(at, what, h, t); break;
            case "captive": if (!plugin.state().captivesRescued.containsKey(what)) captive(at, what); break;
            case "choir": choir(at, Integer.parseInt(what), h); break;
            default: return;   // markers: boss, mech, heliodrome, berth, camp, ward, monument
        }
        spawned++;
    }

    static Villager.Profession professionOf(String role) {
        switch (role) {
            case "farmer": case "shepherd": case "gardener": case "miller": case "beekeeper": case "water_carrier": case "settler": case "kelani_herder": case "grocer":
            case "baker": case "weaver": case "potter": case "villa_owner": case "host": return Villager.Profession.FARMER;
            case "librarian": case "scribe": case "teacher": case "student": case "philosopher": case "poet": case "reader": case "copyist": case "astronomer":
            case "cartographer": case "archon": return Villager.Profession.LIBRARIAN;
            case "priest": case "worshipper": case "pilgrim": case "mourner": case "healer": case "patient": case "apothecary": case "keeper_of_return": return Villager.Profession.PRIEST;
            case "smith": case "mechanic": case "lumenwright": case "engineer": case "hoplite": case "quartermaster": case "signaller": case "trainer": case "athlete": return Villager.Profession.BLACKSMITH;
            case "bath_keeper": case "bather": case "actor": return Villager.Profession.BUTCHER;
            default: return Villager.Profession.NITWIT;
        }
    }

    static String title(String role) {
        String r = role.replace('_', ' ');
        return r.substring(0, 1).toUpperCase() + r.substring(1);
    }

    /** A citizen: named, dressed by calling, posed at their work (children, travellers and settlers walk). */
    private void citizen(Location at, String role, long h, boolean merchant) {
        boolean walker = role.equals("child") || role.equals("traveller") || role.equals("athlete");
        boolean female = (h & 1) == 0;
        String name = Names.person(h >>> 1, female);
        at.getWorld().spawn(at, Villager.class, v -> {
            v.setProfession(professionOf(role));
            v.setCustomName(ChatColor.WHITE + name + ChatColor.GRAY + ", " + title(role).toLowerCase());
            v.setCustomNameVisible(false);
            v.setRemoveWhenFarAway(false);
            v.addScoreboardTag(TAG);
            v.addScoreboardTag(CITIZEN);
            v.addScoreboardTag("atlas_role:" + role);
            if (merchant) v.addScoreboardTag(MERCHANT + role);
            if (role.equals("child")) v.setBaby();
            if (!walker) v.setAI(false);
            v.setCollidable(false);
        });
    }

    /** A key figure: unique, invulnerable, always at their post. */
    void keyFigure(Location at, String id) {
        Lore.Figure fig = Lore.figure(id);
        if (fig == null) return;
        at.getWorld().spawn(at, Villager.class, v -> {
            v.setProfession(fig.profession);
            v.setCustomName(ChatColor.AQUA + fig.name + ChatColor.GRAY + ", " + fig.title);
            v.setCustomNameVisible(true);
            v.setRemoveWhenFarAway(false);
            v.setInvulnerable(true);
            v.setAI(false);
            v.setCollidable(false);
            v.addScoreboardTag(TAG);
            v.addScoreboardTag(KEY + id);
        });
        plugin.getLogger().info("ATLAS_KEY_FIGURE_PLACED id=" + id + " at=" + at.getBlockX() + "," + at.getBlockY() + "," + at.getBlockZ());
    }

    /** One of the Dominion's talkers: Uzgar the Ashborn deserter, or Vesk the Gnawling informer. Invulnerable, unmoving. */
    void talker(Location at, String id) {
        boolean vesk = id.equals("vesk");
        LivingEntity e = vesk
            ? at.getWorld().spawn(at, Zombie.class, z -> { z.setBaby(true); dress(z, 0x3a3020, Material.STICK); })
            : at.getWorld().spawn(at, Husk.class, z -> { dress(z, 0x2a2a2a, Material.STICK); z.getEquipment().setItemInMainHand(null); });
        e.setCustomName(vesk ? ChatColor.GOLD + "Vesk" + ChatColor.GRAY + ", Gnawling informer" : ChatColor.GOLD + "Uzgar" + ChatColor.GRAY + ", Ashborn deserter");
        e.setCustomNameVisible(true);
        e.setRemoveWhenFarAway(false);
        e.setInvulnerable(true);
        e.setAI(false);
        e.setSilent(true);
        e.setCollidable(false);
        e.addScoreboardTag(TAG);
        e.addScoreboardTag("atlas_talker");
        e.addScoreboardTag("atlas_talker:" + id);
        e.addScoreboardTag(EXEMPT);
        plugin.registry().remember("talker:" + id, e);
        plugin.getLogger().info("ATLAS_TALKER_PLACED id=" + id);
    }

    private void talos(Location at) {
        at.getWorld().spawn(at, IronGolem.class, g -> {
            g.setCustomName(ChatColor.AQUA + "Talos" + ChatColor.GRAY + ", automaton of the Concord");
            g.setCustomNameVisible(false);
            g.setRemoveWhenFarAway(false);
            g.setPlayerCreated(true);   // vanilla golems then never turn on players by themselves
            g.addScoreboardTag(TAG);
            g.addScoreboardTag(TALOS);
        });
    }

    private void animal(Location at, String what) {
        if (what.equals("sheep")) at.getWorld().spawn(at, Sheep.class, s -> { s.setRemoveWhenFarAway(false); s.addScoreboardTag(TAG); });
        else at.getWorld().spawn(at, Cow.class, s -> { s.setRemoveWhenFarAway(false); s.addScoreboardTag(TAG); });
    }

    // ------------------------------------------------------------------ the Dominion

    /** A member of a Dominion garrison, dressed and weighted for its role. */
    LivingEntity dominion(Location at, String kind, long h) {
        World w = at.getWorld();
        LivingEntity e;
        switch (kind) {
            case "ashborn_bowman": e = w.spawn(at, Skeleton.class, s -> dress(s, 0x2a2a2a, Material.BOW)); break;
            case "blackshield": e = w.spawn(at, Husk.class, z -> { dress(z, 0x151515, Material.IRON_SWORD); z.getEquipment().setChestplate(new ItemStack(Material.CHAINMAIL_CHESTPLATE)); z.getEquipment().setItemInOffHand(new ItemStack(Material.SHIELD)); }); break;
            case "gnawling": e = w.spawn(at, Zombie.class, z -> { z.setBaby(true); dress(z, 0x3a3020, Material.STONE_SWORD); }); break;
            case "taskmaster": e = w.spawn(at, Vindicator.class, v -> v.getEquipment().setItemInMainHand(new ItemStack(Material.STONE_AXE))); break;
            case "priest": e = w.spawn(at, Evoker.class, v -> { }); break;
            case "troll": e = w.spawn(at, IronGolem.class, g -> g.setPlayerCreated(false)); break;
            case "emberkin": e = w.spawn(at, Blaze.class, b -> { }); break;
            case "warg": e = w.spawn(at, Wolf.class, wf -> { wf.setAngry(true); }); break;
            case "black_horse": e = w.spawn(at, SkeletonHorse.class, sh -> { sh.setTamed(false); }); break;
            case "warchief": e = w.spawn(at, Zombie.class, z -> { dress(z, 0x3a0a0a, Material.IRON_AXE); z.getEquipment().setChestplate(new ItemStack(Material.IRON_CHESTPLATE)); }); break;
            default: e = w.spawn(at, Zombie.class, z -> dress(z, 0x202020, Material.STONE_SWORD));
        }
        Dominion.Kind k = Dominion.Kind.of(kind);
        e.setCustomName(ChatColor.DARK_RED + (k == null ? "Ashborn" : k.title) + (k != null && k.named ? ChatColor.GRAY + " " + Names.ashborn(h) : ""));
        e.setCustomNameVisible(false);
        e.setRemoveWhenFarAway(false);
        e.addScoreboardTag(TAG);
        e.addScoreboardTag(DOMINION);
        e.addScoreboardTag(DOM_KIND + kind);
        e.addScoreboardTag(EXEMPT);
        if (k != null) k.apply(e);
        return e;
    }

    private static void dress(LivingEntity e, int rgb, Material weapon) {
        EntityEquipment q = e.getEquipment();
        q.setHelmet(dyed(Material.LEATHER_HELMET, rgb));   // a helmet: the Ashborn do not burn in daylight
        q.setChestplate(dyed(Material.LEATHER_CHESTPLATE, rgb));
        q.setItemInMainHand(new ItemStack(weapon));
        q.setHelmetDropChance(0f); q.setChestplateDropChance(0f); q.setItemInMainHandDropChance(0.04f);
    }

    /** One of the Bound: a labourer, a silenced citizen, a stilled patient or a Kelani herder, held at their work. */
    private void bound(Location at, String what, long h, Canvas.Tile t) {
        Plans.Site site = plugin.plans().siteAt(t.x, t.z, 4);
        if (site != null && plugin.state().campsFreed.contains(site.i + ":" + site.j)) {
            // This camp was freed before its people were ever seen here: they are free people now.
            if (!what.equals("stilled")) citizen(at, "freed", h, false);
            return;
        }
        boolean female = (h & 1) == 0;
        String name = Names.person(h >>> 1, female);
        at.getWorld().spawn(at, Villager.class, v -> {
            v.setProfession(what.equals("stilled") ? Villager.Profession.PRIEST : Villager.Profession.NITWIT);
            v.setCustomName(ChatColor.GRAY + name + ", " + (what.equals("stilled") ? "stilled" : what.equals("citizen") ? "silenced" : "bound"));
            v.setCustomNameVisible(false);
            v.setRemoveWhenFarAway(false);
            v.setAI(false);
            v.setInvulnerable(true);
            v.setCollidable(false);
            v.addScoreboardTag(TAG);
            v.addScoreboardTag(BOUND);
            v.addScoreboardTag("atlas_bound_kind:" + what);
            Plans.Site s = plugin.plans().siteAt(t.x, t.z, 4);
            if (s != null) v.addScoreboardTag(CAMP + s.i + ":" + s.j);
            else {
                Realm.Place p = Realm.placeAt(t.x, t.z);
                if (p != null) v.addScoreboardTag(CAMP + p.name());
            }
        });
    }

    /** A named captive: someone with a story, waiting for rescue. */
    void captive(Location at, String id) {
        Lore.Captive c = Lore.captive(id);
        if (c == null) return;
        at.getWorld().spawn(at, Villager.class, v -> {
            v.setProfession(c.profession);
            v.setCustomName(ChatColor.YELLOW + c.name + ChatColor.GRAY + ", " + c.title);
            v.setCustomNameVisible(true);
            v.setRemoveWhenFarAway(false);
            v.setAI(false);
            v.setInvulnerable(true);
            v.setCollidable(false);
            v.addScoreboardTag(TAG);
            v.addScoreboardTag(CAPTIVE + id);
        });
    }

    private void choir(Location at, int n, long h) {
        at.getWorld().spawn(at, Villager.class, v -> {
            v.setProfession(Villager.Profession.NITWIT);
            v.setCustomName(ChatColor.GRAY + Names.person(h, (n & 1) == 0) + ", of the Chained Choir");
            v.setRemoveWhenFarAway(false);
            v.setAI(false);
            v.setInvulnerable(true);
            v.addScoreboardTag(TAG);
            v.addScoreboardTag(CHOIR);
        });
    }

    static boolean has(Entity e, String tag) { return e.getScoreboardTags().contains(tag); }

    static String tagValue(Entity e, String prefix) {
        for (String t : e.getScoreboardTags()) if (t.startsWith(prefix)) return t.substring(prefix.length());
        return null;
    }

    static void maxHealth(LivingEntity e, double hp) {
        e.getAttribute(Attribute.GENERIC_MAX_HEALTH).setBaseValue(hp);
        e.setHealth(hp);
    }
}
