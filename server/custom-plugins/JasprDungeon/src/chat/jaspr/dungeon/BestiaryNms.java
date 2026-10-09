package chat.jaspr.dungeon;

import net.minecraft.server.v1_12_R1.EntityInsentient;
import net.minecraft.server.v1_12_R1.EntitySkeletonAbstract;
import net.minecraft.server.v1_12_R1.EntityZombie;
import net.minecraft.server.v1_12_R1.NBTTagCompound;
import net.minecraft.server.v1_12_R1.NBTTagList;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.craftbukkit.v1_12_R1.entity.CraftEntity;
import org.bukkit.craftbukkit.v1_12_R1.inventory.CraftItemStack;
import org.bukkit.entity.LivingEntity;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.LeatherArmorMeta;

/**
 * Generation 7: every 1.12 implementation-specific call of the bestiary in one small adapter (like Arsenal's Bridge), so what
 * touches the server's internals can be read in one place. The server's own mob controllers do what vanilla bow skeletons do:
 * walk to a point, strafe while facing, face an entity, raise the arms. Callers wrap these in the bestiary's failure handling.
 * {@link #hook} is a seam for BestiarySimTest, which runs the real bestiary against fake worlds and mobs in a plain JVM; it is
 * null on a server.
 */
final class BestiaryNms {
    private BestiaryNms() {}

    /** The simulation's replacement for everything below. */
    interface Hook {
        ItemStack item(String spec);
        ItemStack gun(ArmoryCatalog.Type type);
        boolean walk(LivingEntity e, double x, double y, double z, double speed);
        void stop(LivingEntity e);
        void strafe(LivingEntity e, float forward, float side);
        void face(LivingEntity e, org.bukkit.entity.Entity target);
        void arms(LivingEntity e, boolean up);
    }
    static Hook hook;

    private static EntityInsentient mob(org.bukkit.entity.Entity e) {
        net.minecraft.server.v1_12_R1.Entity h = ((CraftEntity) e).getHandle();
        return h instanceof EntityInsentient ? (EntityInsentient) h : null;
    }
    /** Starts a walk to the point at a speed factor of the mob's own walking speed; false when it found no path. */
    static boolean walk(LivingEntity e, double x, double y, double z, double speed) {
        if (hook != null) return hook.walk(e, x, y, z, speed);
        EntityInsentient m = mob(e);
        return m != null && m.getNavigation().a(x, y, z, speed);
    }
    /** Ends any walk in progress. */
    static void stop(LivingEntity e) {
        if (hook != null) { hook.stop(e); return; }
        EntityInsentient m = mob(e);
        if (m != null) m.getNavigation().p();
    }
    /** One tick of walking by input, forward and sideways (negative: backwards and to the left), without turning: the bow skeleton's strafe. */
    static void strafe(LivingEntity e, float forward, float side) {
        if (hook != null) { hook.strafe(e, forward, side); return; }
        EntityInsentient m = mob(e);
        if (m != null) m.getControllerMove().a(forward, side);
    }
    /** Turns the body and head toward the entity by at most forty degrees. */
    static void face(LivingEntity e, org.bukkit.entity.Entity target) {
        if (hook != null) { hook.face(e, target); return; }
        EntityInsentient m = mob(e);
        if (m != null) m.a(((CraftEntity) target).getHandle(), 40f, 40f);
    }
    /** The arms raised as when aiming (skeletons and strays) or as a zombie reaches; harmless on every other mob. */
    static void arms(LivingEntity e, boolean up) {
        if (hook != null) { hook.arms(e, up); return; }
        net.minecraft.server.v1_12_R1.Entity h = ((CraftEntity) e).getHandle();
        if (h instanceof EntitySkeletonAbstract) ((EntitySkeletonAbstract) h).p(up);
        else if (h instanceof EntityZombie) ((EntityZombie) h).a(up);
    }

    private static final long UUID_MOST = 0x4A41535053424553L, UUID_LEAST = 0x544941525901L;
    /**
     * The same item with its attribute modifiers replaced by a harmless one, so armour is only for show: an iron chestplate on a
     * mob would give it six armour points no health number says, and the player's blows would fall short without telling anyone
     * why. (An item's own list of modifiers replaces the ones its kind carries, as Skin does for trinkets.)
     */
    static ItemStack plain(ItemStack item) {
        net.minecraft.server.v1_12_R1.ItemStack nms = CraftItemStack.asNMSCopy(item);
        NBTTagCompound tag = nms.hasTag() ? nms.getTag() : new NBTTagCompound();
        NBTTagCompound none = new NBTTagCompound();
        none.setString("AttributeName", "generic.armor");
        none.setString("Name", "jaspr_for_show");
        none.setDouble("Amount", 0);
        none.setInt("Operation", 0);
        none.setLong("UUIDMost", UUID_MOST);
        none.setLong("UUIDLeast", UUID_LEAST);
        NBTTagList list = new NBTTagList();
        list.add(none);
        tag.set("AttributeModifiers", list);
        nms.setTag(tag);
        return CraftItemStack.asBukkitCopy(nms);
    }
    /** An item from a catalogue spec ("MATERIAL" or "MATERIAL#rrggbb" for dyed leather); armour pieces are for show only. */
    static ItemStack item(String spec) {
        if (spec == null) return null;
        if (hook != null) return hook.item(spec);
        Material m = Material.valueOf(BestiaryCatalog.material(spec));
        ItemStack i = new ItemStack(m);
        int dye = BestiaryCatalog.color(spec);
        if (dye >= 0) {
            ItemMeta meta = i.getItemMeta();
            if (meta instanceof LeatherArmorMeta) { ((LeatherArmorMeta) meta).setColor(Color.fromRGB(dye)); i.setItemMeta(meta); }
        }
        String n = m.name();
        return n.endsWith("_HELMET") || n.endsWith("_CHESTPLATE") || n.endsWith("_LEGGINGS") || n.endsWith("_BOOTS") ? plain(i) : i;
    }
    /** A dungeon gun exactly as the player's own (so the client draws the same model). */
    static ItemStack gun(ArmoryCatalog.Type type) { return hook != null ? hook.gun(type) : Arsenal.create(type); }
}
