package chat.jaspr.dungeon;

import java.util.Collection;
import java.util.List;
import net.minecraft.server.v1_12_R1.EntityInsentient;
import net.minecraft.server.v1_12_R1.PathfinderGoalSelector;
import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.craftbukkit.v1_12_R1.entity.CraftEntity;
import org.bukkit.entity.*;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.MerchantRecipe;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.util.EulerAngle;

/**
 * The creatures the secrets spawn themselves (the mimic, Monstro and Dinnerbone's guard are room adds made by Encounters.summon):
 * the gilded thief, the lost peddler, the jeb_ sheep and the sword's armour stand. Each carries the secrets' tag before it enters
 * the world (Encounters.natural lets it spawn), a kind tag and its room's tag; none of them is a room slot or counts for a clear.
 * The only NMS here is the thief's: it has no goals of its own, so nothing but this class steers it.
 */
final class SecretMobs {
    private SecretMobs() {}
    static final String KIND = Secrets.TAG + "_", ROOM = Secrets.TAG + "_room:";

    static void tag(Entity e, String kind, String roomKey) {
        e.addScoreboardTag(Secrets.TAG);
        e.addScoreboardTag(KIND + kind);
        if (roomKey != null) e.addScoreboardTag(ROOM + roomKey);
    }
    /** The kind tag among an entity's scoreboard tags ("thief", "peddler" ...), or null when it is none of the secrets'. */
    static String kind(Collection<String> tags) {
        if (tags == null || !tags.contains(Secrets.TAG)) return null;
        for (String t : tags) if (t.startsWith(KIND) && !t.startsWith(ROOM)) return t.substring(KIND.length());
        return null;
    }
    /** The room key among the tags (its own, or the jpd_room tag of an add Encounters made), or null. */
    static String room(Collection<String> tags) {
        if (tags == null) return null;
        for (String t : tags) { if (t.startsWith(ROOM)) return t.substring(ROOM.length()); }
        for (String t : tags) { if (t.startsWith("jpd_room:")) return t.substring(9); }
        return null;
    }
    static String kind(Entity e) { return e == null ? null : kind(e.getScoreboardTags()); }
    static String room(Entity e) { return e == null ? null : room(e.getScoreboardTags()); }

    private static ItemStack gold(Material m) {
        ItemStack i = new ItemStack(m);
        LeatherArmorMeta meta = (LeatherArmorMeta) i.getItemMeta();
        meta.setColor(Color.fromRGB(0xF2, 0xC2, 0x2E));
        i.setItemMeta(meta);
        return i;
    }

    /** A baby zombie in gold leather: quick, fragile, named, harmless, with its own goals removed (Secrets steers it by navigation). */
    static Zombie thief(World w, Location at, String roomKey, double health) {
        Zombie z = w.spawn(at, Zombie.class, e -> {
            tag(e, "thief", roomKey);
            e.setBaby(true);
            e.setCanPickupItems(false);
            e.setRemoveWhenFarAway(false);
            e.setCustomName(ChatColor.GOLD + "Gilded Thief");
            e.setCustomNameVisible(true);
        });
        z.getAttribute(Attribute.GENERIC_MAX_HEALTH).setBaseValue(health);
        z.setHealth(health);
        if (z.getAttribute(Attribute.GENERIC_ATTACK_DAMAGE) != null) z.getAttribute(Attribute.GENERIC_ATTACK_DAMAGE).setBaseValue(0);
        // A baby zombie's own speed boost (x1.5) on 0.20 makes 0.30: a little faster than a sprinting player, never out of reach in a closed room.
        z.getAttribute(Attribute.GENERIC_MOVEMENT_SPEED).setBaseValue(.20);
        EntityEquipment eq = z.getEquipment();
        eq.setHelmet(gold(Material.LEATHER_HELMET));
        eq.setChestplate(gold(Material.LEATHER_CHESTPLATE));
        eq.setLeggings(gold(Material.LEATHER_LEGGINGS));
        eq.setBoots(gold(Material.LEATHER_BOOTS));
        eq.setItemInMainHand(new ItemStack(Material.AIR));
        eq.setItemInOffHand(new ItemStack(Material.AIR));
        eq.setHelmetDropChance(0); eq.setChestplateDropChance(0); eq.setLeggingsDropChance(0); eq.setBootsDropChance(0);
        eq.setItemInMainHandDropChance(0); eq.setItemInOffHandDropChance(0);
        EntityInsentient h = insentient(z);
        if (h != null) {
            h.goalSelector = new PathfinderGoalSelector(h.world.methodProfiler);
            h.targetSelector = new PathfinderGoalSelector(h.world.methodProfiler);
        }
        return z;
    }
    /** The server-side mob behind a Bukkit creature, or null when it is not a CraftBukkit one (a stand-in in a test). */
    private static EntityInsentient insentient(Entity e) {
        if (!(e instanceof CraftEntity)) return null;
        net.minecraft.server.v1_12_R1.Entity h = ((CraftEntity) e).getHandle();
        return h instanceof EntityInsentient ? (EntityInsentient) h : null;
    }
    /** Starts the creature walking to a point; false when it finds no path. */
    static boolean runTo(LivingEntity e, double x, double y, double z, double speed) {
        EntityInsentient h = insentient(e);
        return h != null && h.getNavigation().a(x, y, z, speed);
    }
    static void halt(LivingEntity e) {
        EntityInsentient h = insentient(e);
        if (h != null) h.getNavigation().p();
    }
    /** True when the creature has no path to follow. */
    static boolean idle(LivingEntity e) {
        EntityInsentient h = insentient(e);
        return h == null || h.getNavigation().o();
    }

    /** A peaceful trader that stands where it is put: no AI, invulnerable, never hostile and never wandering out. */
    static Villager peddler(World w, Location at, String roomKey, List<MerchantRecipe> trades) {
        return w.spawn(at, Villager.class, v -> {
            tag(v, "peddler", roomKey);
            v.setProfession(Villager.Profession.FARMER);
            v.setCustomName(ChatColor.GOLD + "Lost Peddler");
            v.setCustomNameVisible(true);
            v.setInvulnerable(true);
            v.setAI(false);
            // Not pushable: a stall keeper who stays where he is put (clicks and trades still reach him).
            v.setCollidable(false);
            v.setRemoveWhenFarAway(false);
            v.setCanPickupItems(false);
            v.setRecipes(trades);
        });
    }
    /** A sheep named jeb_: the client cycles its colours, and so does the server, in case it does not. */
    static Sheep jeb(World w, Location at, String roomKey) {
        return w.spawn(at, Sheep.class, s -> {
            tag(s, "jeb", roomKey);
            s.setCustomName("jeb_");
            s.setCustomNameVisible(false);
            s.setInvulnerable(true);
            s.setColor(DyeColor.WHITE);
            s.setRemoveWhenFarAway(false);
        });
    }
    /** An invisible armour stand whose right arm points back and down: the sword it holds hangs blade-down at the column's top. */
    static ArmorStand swordStand(World w, Location at, String roomKey, ItemStack sword) {
        return w.spawn(at, ArmorStand.class, a -> {
            tag(a, "sword", roomKey);
            a.setVisible(false);
            a.setGravity(false);
            a.setArms(true);
            a.setBasePlate(false);
            a.setInvulnerable(true);
            a.setSilent(true);
            a.setRightArmPose(new EulerAngle(Math.toRadians(90), 0, 0));
            a.setItemInHand(sword);
        });
    }
}
