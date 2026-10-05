package chat.jaspr.trinketprobe;

import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.*;
import java.util.function.Consumer;
import net.minecraft.server.v1_12_R1.NBTTagCompound;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.craftbukkit.v1_12_R1.inventory.CraftItemStack;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Owner 2026-10-05: every trinket has an icon of its own. Loads the real plugin jars (never enabling them: only their item code is
 * called) from plugin-lib/ and checks, on the real server's item classes, that every trinket, bauble and seal is an unbreakable
 * stone carrier of the catalogue's band that its plugin still recognises, and that items made before the icons are upgraded.
 * Command: trkprobe dungeon | ruins | nether | backrooms
 */
public final class TrinketProbe extends JavaPlugin {
    private final Map<String, ClassLoader> loaders = new HashMap<>();
    private int checks;

    private void check(boolean ok, String what) { checks++; if (!ok) throw new IllegalStateException(what); }

    private Class<?> cls(String plugin, String name) throws Exception {
        ClassLoader loader = loaders.get(plugin);
        if (loader == null) {
            File lib = new File(getDataFolder().getParentFile().getParentFile(), "plugin-lib");
            // A folder of freshly compiled classes, or a jar.
            File jar = new File(lib, plugin + ".jar"), dir = new File(lib, plugin);
            File source = dir.isDirectory() ? dir : jar;
            if (!source.exists()) throw new IllegalStateException("missing " + source);
            loader = new URLClassLoader(new URL[]{source.toURI().toURL()}, getClass().getClassLoader());
            loaders.put(plugin, loader);
        }
        return Class.forName(name, true, loader);
    }
    private static Object field(Object o, String name) throws Exception { Field f = o.getClass().getDeclaredField(name); f.setAccessible(true); return f.get(o); }
    private static Object staticField(Class<?> c, String name) throws Exception { Field f = c.getDeclaredField(name); f.setAccessible(true); return f.get(null); }
    private static Method method(Class<?> c, String name, Class<?>... types) throws Exception { Method m = c.getDeclaredMethod(name, types); m.setAccessible(true); return m; }

    /** An unbreakable stone carrier of that band that kept its name, lore and an attribute list of its own. */
    private void skinned(ItemStack item, Material carrier, int band, String label) {
        check(item != null && item.getType() == carrier, label + " is a " + (item == null ? "null" : item.getType()));
        check(item.getDurability() == band, label + " band " + item.getDurability() + " not " + band);
        check(item.getAmount() == 1, label + " amount");
        ItemMeta m = item.getItemMeta();
        check(m.isUnbreakable(), label + " unbreakable");
        check(m.hasItemFlag(ItemFlag.HIDE_UNBREAKABLE) && m.hasItemFlag(ItemFlag.HIDE_ATTRIBUTES), label + " hides durability and attributes");
        check(m.hasDisplayName() && m.hasLore(), label + " keeps its name and lore");
        NBTTagCompound tag = CraftItemStack.asNMSCopy(item).getTag();
        check(tag != null && tag.hasKeyOfType("AttributeModifiers", 9) && tag.getList("AttributeModifiers", 10).size() >= 1, label + " has an attribute list of its own (the tool's attack is replaced)");
    }
    private static String nbtId(ItemStack item, String key) { NBTTagCompound t = CraftItemStack.asNMSCopy(item).getTag(); return t == null || !t.hasKeyOfType(key, 10) ? null : t.getCompound(key).getString("id"); }
    private static ItemStack named(Material m, int data, String name, List<String> lore) {
        ItemStack item = new ItemStack(m, 1, (short) data); ItemMeta meta = item.getItemMeta(); meta.setDisplayName(name); meta.setLore(lore); item.setItemMeta(meta); return item;
    }
    private static ItemStack tagged(ItemStack item, String key, String id) {
        net.minecraft.server.v1_12_R1.ItemStack n = CraftItemStack.asNMSCopy(item);
        NBTTagCompound tag = n.hasTag() ? n.getTag() : new NBTTagCompound(), mine = new NBTTagCompound();
        mine.setString("id", id); tag.set(key, mine); n.setTag(tag); return CraftItemStack.asBukkitCopy(n);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private void dungeon(CommandSender out) throws Exception {
        Class<?> relics = cls("JasprDungeon", "chat.jaspr.dungeon.Relics"), type = cls("JasprDungeon", "chat.jaspr.dungeon.Relics$Type");
        Object[] types = type.getEnumConstants();
        check(types.length == 72, "72 baubles");
        Method create = relics.getMethod("create", type), typeOf = relics.getMethod("type", ItemStack.class), createPouch = relics.getMethod("createPouch"), pouchOf = relics.getMethod("pouch", ItemStack.class);
        Method upgrade = relics.getMethod("upgrade", Inventory.class), upgraded = method(relics, "upgraded", ItemStack.class), edit = relics.getMethod("edit", ItemStack.class, Consumer.class);
        Inventory old = Bukkit.createInventory(null, 90);
        for (Object t : types) {
            String name = ((Enum) t).name();
            int band = ((Enum) t).ordinal() + 1;
            ItemStack fresh = (ItemStack) create.invoke(null, t);
            skinned(fresh, Material.STONE_SWORD, band, name);
            check(typeOf.invoke(null, fresh) == t, name + " is recognised as itself");
            check(name.equals(nbtId(fresh, "JasprPenitentRelic")), name + " keeps its id");
            // A bauble made before icons: the stand-in item with the same name, lore and tags.
            Material legacy = (Material) type.getField("material").get(t);
            ItemMeta fm = fresh.getItemMeta();
            ItemStack stand = named(legacy, 0, fm.getDisplayName(), fm.getLore());
            stand = (ItemStack) edit.invoke(null, stand, (Consumer<NBTTagCompound>) d -> { d.setString("kind", "relic"); d.setString("id", name); d.setInt("version", 1); });
            check(typeOf.invoke(null, stand) == t, name + " made before icons is still recognised");
            ItemStack up = (ItemStack) upgraded.invoke(null, stand);
            check(up != null, name + " is upgraded");
            skinned(up, Material.STONE_SWORD, band, name + " (upgraded)");
            check(typeOf.invoke(null, up) == t && name.equals(nbtId(up, "JasprPenitentRelic")), name + " upgraded keeps its identity");
            check(upgraded.invoke(null, up) == null && upgraded.invoke(null, fresh) == null, name + " is upgraded only once");
            old.addItem(stand);
        }
        // The pouch: its identity (uuid, sockets) survives the new look.
        ItemStack pouch = (ItemStack) createPouch.invoke(null);
        skinned(pouch, Material.STONE_SWORD, 73, "pouch");
        check(Boolean.TRUE.equals(pouchOf.invoke(null, pouch)), "the new pouch is a pouch");
        String uuid = CraftItemStack.asNMSCopy(pouch).getTag().getCompound("JasprPenitentRelic").getString("uuid");
        ItemStack standPouch = named(Material.RABBIT_HIDE, 0, "Dungeon Reliquary", Arrays.asList("Right-click to equip two distinct dungeon relics."));
        standPouch = (ItemStack) edit.invoke(null, standPouch, (Consumer<NBTTagCompound>) d -> { d.setString("kind", "pouch"); d.setString("uuid", uuid); d.setString("slot0", "SALT_TEAR"); d.setInt("version", 1); });
        check(Boolean.TRUE.equals(pouchOf.invoke(null, standPouch)), "an old pouch is still a pouch");
        ItemStack upPouch = (ItemStack) upgraded.invoke(null, standPouch);
        skinned(upPouch, Material.STONE_SWORD, 73, "upgraded pouch");
        NBTTagCompound pd = CraftItemStack.asNMSCopy(upPouch).getTag().getCompound("JasprPenitentRelic");
        check(uuid.equals(pd.getString("uuid")) && "SALT_TEAR".equals(pd.getString("slot0")), "the pouch keeps its uuid and its sockets");
        check(Boolean.TRUE.equals(pouchOf.invoke(null, upPouch)), "an upgraded pouch is a pouch");
        old.addItem(standPouch);
        int n = (Integer) upgrade.invoke(null, old);
        check(n == 73, "an inventory of 72 old baubles and an old pouch upgrades 73 items, got " + n);
        for (ItemStack item : old.getContents()) if (item != null) check(item.getType() == Material.STONE_SWORD, "every old item became a carrier");
        check((Integer) upgrade.invoke(null, old) == 0, "a second pass changes nothing");
        out.sendMessage("TRK_DETAIL dungeon baubles=72 pouch=1 upgraded=" + n);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private void ruins(CommandSender out) throws Exception {
        Class<?> trinkets = cls("JasprRuins", "chat.jaspr.ruins.Trinkets"), trinket = cls("JasprRuins", "chat.jaspr.ruins.Trinkets$Trinket"), seal = cls("JasprRuins", "chat.jaspr.ruins.Trinkets$Seal");
        Method item = method(trinkets, "item", trinket), sealItem = method(trinkets, "seal", seal), trinketOf = method(trinkets, "trinketOf", ItemStack.class), sealOf = method(trinkets, "sealOf", ItemStack.class);
        Method upgraded = method(trinkets, "upgraded", ItemStack.class), upgrade = method(trinkets, "upgrade", Inventory.class);
        Method band = method(trinket, "band"), sealBand = method(seal, "band");
        Inventory old = Bukkit.createInventory(null, 54);
        int skinnedCount = 0;
        for (Object t : trinket.getEnumConstants()) {
            String name = ((Enum) t).name();
            int b = (Integer) band.invoke(t);
            ItemStack fresh = (ItemStack) item.invoke(null, t);
            Material material = (Material) field(t, "material");
            int data = (Short) field(t, "data");
            ItemMeta fm = fresh.getItemMeta();
            check(trinketOf.invoke(null, fresh) == t, name + " is recognised as itself");
            ItemStack stand = named(material, data, fm.getDisplayName(), fm.getLore());
            check(trinketOf.invoke(null, stand) == t, name + " made before icons is still recognised");
            if (b > 0) {
                skinnedCount++;
                skinned(fresh, Material.STONE_SPADE, b, name);
                ItemStack up = (ItemStack) upgraded.invoke(null, stand);
                skinned(up, Material.STONE_SPADE, b, name + " (upgraded)");
                check(trinketOf.invoke(null, up) == t, name + " upgraded keeps its identity");
                old.addItem(stand);
            } else {
                // The Faceless Mask and the Crown are worn in the helmet slot: they stay a skull and a helmet.
                check(fresh.getType() == material && upgraded.invoke(null, stand) == null, name + " keeps its vanilla item");
            }
        }
        check(skinnedCount == 8, "eight relics are skinned, got " + skinnedCount);
        int seals = 0;
        for (Object s : seal.getEnumConstants()) {
            String name = ((Enum) s).name();
            int b = (Integer) sealBand.invoke(s);
            check(b == 11 + ((Enum) s).ordinal(), name + " band");
            ItemStack fresh = (ItemStack) sealItem.invoke(null, s);
            skinned(fresh, Material.STONE_SPADE, b, "seal " + name);
            check(sealOf.invoke(null, fresh) == s, name + " is recognised as itself");
            ItemMeta fm = fresh.getItemMeta();
            ItemStack stand = named((Material) field(s, "material"), 0, fm.getDisplayName(), fm.getLore());
            check(sealOf.invoke(null, stand) == s, name + " made before icons is still recognised");
            skinned((ItemStack) upgraded.invoke(null, stand), Material.STONE_SPADE, b, "seal " + name + " (upgraded)");
            old.addItem(stand); seals++;
        }
        check(seals == 5, "five seals");
        int n = (Integer) upgrade.invoke(null, old);
        check(n == 13, "old relics and seals upgrade (13), got " + n);
        check((Integer) upgrade.invoke(null, old) == 0, "a second pass changes nothing");
        out.sendMessage("TRK_DETAIL ruins relics=8 seals=5 wornKept=2 upgraded=" + n);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private void nether(CommandSender out) throws Exception {
        Class<?> items = cls("JasprNether", "chat.jaspr.nether.Items"), def = cls("JasprNether", "chat.jaspr.nether.Items$Def");
        Map<String, Object> defs = (Map<String, Object>) staticField(items, "DEFS");
        Method create = method(items, "create", String.class, int.class), id = method(items, "id", ItemStack.class), upgraded = method(items, "upgraded", ItemStack.class), upgrade = method(items, "upgrade", Inventory.class);
        Field skin = def.getDeclaredField("skin"); skin.setAccessible(true);
        Inventory old = Bukkit.createInventory(null, 54);
        int skinned = 0;
        for (Map.Entry<String, Object> e : defs.entrySet()) {
            int b = skin.getInt(e.getValue());
            ItemStack fresh = (ItemStack) create.invoke(null, e.getKey(), 1);
            check(e.getKey().equals(id.invoke(null, fresh)), e.getKey() + " is recognised as itself");
            if (b <= 0) { check(fresh.getType() != Material.STONE_SPADE, e.getKey() + " keeps its vanilla stand-in"); continue; }
            skinned++;
            skinned(fresh, Material.STONE_SPADE, b, e.getKey());
            NBTTagCompound tag = CraftItemStack.asNMSCopy(fresh).getTag();
            check(tag.getList("AttributeModifiers", 10).size() >= 1, e.getKey() + " attributes");
            // Made before icons: the same item on its vanilla stand-in.
            ItemStack stand = tagged(named((Material) field(e.getValue(), "mat"), (Short) field(e.getValue(), "data"), fresh.getItemMeta().getDisplayName(), fresh.getItemMeta().getLore()), "JasprNether", e.getKey());
            ItemStack up = (ItemStack) upgraded.invoke(null, stand);
            skinned(up, Material.STONE_SPADE, b, e.getKey() + " (upgraded)");
            check(e.getKey().equals(id.invoke(null, up)), e.getKey() + " upgraded keeps its identity");
            old.addItem(stand);
        }
        check(skinned == 12, "twelve Nether trinkets are skinned (ten, and two relics of the colossi), got " + skinned);
        int n = (Integer) upgrade.invoke(null, old);
        check(n == 12, "old trinkets upgrade (12), got " + n);
        check((Integer) upgrade.invoke(null, old) == 0, "a second pass changes nothing");
        // The off-hand modifiers survive the carrier switch: the Brimstone Idol still adds attack damage in the off hand.
        NBTTagCompound idol = CraftItemStack.asNMSCopy((ItemStack) create.invoke(null, "brimstone_idol", 1)).getTag();
        boolean offhand = false;
        for (int i = 0; i < idol.getList("AttributeModifiers", 10).size(); i++) offhand |= "offhand".equals(idol.getList("AttributeModifiers", 10).get(i).getString("Slot"));
        check(offhand, "the Brimstone Idol keeps its off-hand attack modifier");
        out.sendMessage("TRK_DETAIL nether trinkets=12 upgraded=" + n);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private void backrooms(CommandSender out) throws Exception {
        Class<?> items = cls("JasprBackrooms", "chat.jaspr.backrooms.Items"), def = cls("JasprBackrooms", "chat.jaspr.backrooms.Items$Def");
        Map<String, Object> defs = (Map<String, Object>) staticField(items, "DEFS");
        Method make = method(items, "make", String.class), id = method(items, "id", ItemStack.class), upgraded = method(items, "upgraded", ItemStack.class), upgrade = method(items, "upgrade", Inventory.class);
        Field skin = def.getDeclaredField("skin"); skin.setAccessible(true);
        Inventory old = Bukkit.createInventory(null, 54);
        int skinned = 0;
        for (Map.Entry<String, Object> e : defs.entrySet()) {
            int b = skin.getInt(e.getValue());
            ItemStack fresh = (ItemStack) make.invoke(null, e.getKey());
            check(e.getKey().equals(id.invoke(null, fresh)), e.getKey() + " is recognised as itself");
            if (b <= 0) { check(fresh.getType() != Material.STONE_SPADE, e.getKey() + " keeps its vanilla stand-in"); continue; }
            skinned++;
            skinned(fresh, Material.STONE_SPADE, b, e.getKey());
            ItemStack stand = tagged(named((Material) field(e.getValue(), "material"), 0, fresh.getItemMeta().getDisplayName(), fresh.getItemMeta().getLore()), "JasprBackrooms", e.getKey());
            ItemStack up = (ItemStack) upgraded.invoke(null, stand);
            skinned(up, Material.STONE_SPADE, b, e.getKey() + " (upgraded)");
            check(e.getKey().equals(id.invoke(null, up)), e.getKey() + " upgraded keeps its identity");
            old.addItem(stand);
        }
        check(skinned == 15, "fifteen Backrooms trinkets are skinned, got " + skinned);
        int n = (Integer) upgrade.invoke(null, old);
        check(n == 15, "old trinkets upgrade (15), got " + n);
        check((Integer) upgrade.invoke(null, old) == 0, "a second pass changes nothing");
        out.sendMessage("TRK_DETAIL backrooms trinkets=15 upgraded=" + n);
    }

    private void effect(String player, org.bukkit.potion.PotionEffect effect) {
        org.bukkit.entity.Player p = Bukkit.getPlayerExact(player);
        if (p == null) throw new IllegalArgumentException("no such player " + player);
        p.addPotionEffect(effect, true);
    }

    @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String what = args.length == 0 ? "" : args[0];
        try {
            checks = 0;
            switch (what) {
                case "dungeon": dungeon(sender); break;
                case "ruins": ruins(sender); break;
                case "nether": nether(sender); break;
                case "backrooms": backrooms(sender); break;
                // Browser test helpers: a silent effect (ambient, no particles: how every item keeps an effect on its bearer), a shown one, none.
                case "silent": effect(args[1], new org.bukkit.potion.PotionEffect(org.bukkit.potion.PotionEffectType.WATER_BREATHING, 12000, 0, true, false)); break;
                case "shown": effect(args[1], new org.bukkit.potion.PotionEffect(org.bukkit.potion.PotionEffectType.SPEED, 12000, 0, false, true)); break;
                case "clear": { org.bukkit.entity.Player p = Bukkit.getPlayerExact(args[1]); for (org.bukkit.potion.PotionEffect e : new ArrayList<>(p.getActivePotionEffects())) p.removePotionEffect(e.getType()); break; }
                default: throw new IllegalArgumentException("usage: trkprobe dungeon|ruins|nether|backrooms");
            }
            sender.sendMessage("TRK_OK " + what + " checks=" + checks);
        } catch (Throwable t) {
            Throwable cause = t instanceof java.lang.reflect.InvocationTargetException && t.getCause() != null ? t.getCause() : t;
            sender.sendMessage("TRK_FAIL " + what + " " + cause);
            cause.printStackTrace();
        }
        return true;
    }
}
