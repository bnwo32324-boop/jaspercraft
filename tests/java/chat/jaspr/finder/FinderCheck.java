package chat.jaspr.finder;

import java.nio.charset.StandardCharsets;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.ShulkerBox;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BlockStateMeta;
import org.bukkit.inventory.meta.ItemMeta;

/** Offline check for the Chest Finder: request parsing, item matching (wear, JasperCraft items, damage kinds, shulker
 * boxes), the plugin-message decode and the direction words; with a file argument, that every request the crafting panel
 * can send parses. Prints FINDER_OK checks=N. */
public final class FinderCheck {
    static int checks;

    static void check(boolean ok, String what) { checks++; if (!ok) throw new AssertionError(what); }

    private static void fakeServer() {
        org.bukkit.Server server = (org.bukkit.Server) java.lang.reflect.Proxy.newProxyInstance(FinderCheck.class.getClassLoader(),
            new Class<?>[]{org.bukkit.Server.class}, (proxy, m, a) -> {
                switch (m.getName()) {
                    case "getItemFactory": return org.bukkit.craftbukkit.v1_12_R1.inventory.CraftItemFactory.instance();
                    case "getLogger": return java.util.logging.Logger.getLogger("FinderCheck");
                    case "getName": case "getVersion": case "getBukkitVersion": return "offline";
                    default:
                        Class<?> t = m.getReturnType();
                        if (t == boolean.class) return false;
                        if (t == int.class) return 0;
                        if (t == long.class) return 0L;
                        if (t == double.class) return 0.0;
                        return null;
                }
            });
        org.bukkit.Bukkit.setServer(server);
    }

    @SuppressWarnings("deprecation")
    static ItemStack stack(Material m, int damage, boolean unbreakable) {
        ItemStack s = new ItemStack(m, 1, (short) damage);
        if (unbreakable) { ItemMeta meta = s.getItemMeta(); meta.setUnbreakable(true); s.setItemMeta(meta); }
        return s;
    }

    static byte[] encode(String text) {
        byte[] utf = text.getBytes(StandardCharsets.UTF_8);
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        int v = utf.length;
        while ((v & ~0x7F) != 0) { out.write((v & 0x7F) | 0x80); v >>>= 7; }
        out.write(v);
        out.write(utf, 0, utf.length);
        return out.toByteArray();
    }

    public static void main(String[] args) throws Exception {
        net.minecraft.server.v1_12_R1.DispenserRegistry.c();
        fakeServer();

        // parsing
        FindRequest pick = FindRequest.parse("find minecraft:diamond_pickaxe 0 0 Diamond Pickaxe");
        check(pick != null && pick.material == Material.DIAMOND_PICKAXE && pick.damage == 0 && !pick.exact, "vanilla pickaxe parses");
        check("Diamond Pickaxe".equals(pick.title), "title kept");
        check(FindRequest.parse("find minecraft:planks 2 0").title.equals("Wood"), "missing title falls back to the material");
        check(FindRequest.parse("find minecraft:stone 0 0 §cRed§r Stone\u0007").title.equals("Red Stone"), "colour codes and control characters stripped");
        check(FindRequest.parse("find minecraft:stone 0 0 " + new String(new char[120]).replace('\0', 'x')).title.length() == FindRequest.MAX_TITLE, "title bounded");
        for (String bad : new String[]{null, "", "fill minecraft:stone 0 0 x", "find minecraft:stone", "find minecraft:stone -1 0 x",
                "find minecraft:stone 40000 0 x", "find minecraft:no_such_item 0 0 x", "find Minecraft:Stone 0 0 x", "find stone 0 0 x",
                "find minecraft:air 0 0 x", "find minecraft:stone zero 0 x", "find minecraft:stone 0 0 " + new String(new char[300]).replace('\0', 'y')})
            check(FindRequest.parse(bad) == null, "rejected: " + bad);

        // matching: vanilla durable items match whatever their wear, never a JasperCraft item on the same base
        check(pick.matches(stack(Material.DIAMOND_PICKAXE, 0, false)), "new pickaxe");
        check(pick.matches(stack(Material.DIAMOND_PICKAXE, 900, false)), "worn pickaxe");
        check(!pick.matches(stack(Material.DIAMOND_PICKAXE, 106, true)), "an armoury pickaxe is not a diamond pickaxe");
        check(!pick.matches(stack(Material.IRON_PICKAXE, 0, false)), "other material");
        check(!pick.matches(null) && !pick.matches(new ItemStack(Material.AIR)), "empty slots");
        // exact: a JasperCraft model item matches that item only
        FindRequest titan = FindRequest.parse("find minecraft:diamond_pickaxe 106 1 Titan Pickaxe");
        check(titan.exact && titan.matches(stack(Material.DIAMOND_PICKAXE, 106, true)), "exact JasperCraft item");
        check(!titan.matches(stack(Material.DIAMOND_PICKAXE, 107, true)), "another JasperCraft item on the same base");
        check(!titan.matches(stack(Material.DIAMOND_PICKAXE, 106, false)), "a worn vanilla pickaxe at the same damage");
        // kinds told apart by damage
        FindRequest red = FindRequest.parse("find minecraft:wool 14 0 Red Wool");
        check(red.matches(stack(Material.WOOL, 14, false)) && !red.matches(stack(Material.WOOL, 0, false)), "wool colours");
        FindRequest spruce = FindRequest.parse("find minecraft:planks 1 0 Spruce Wood Planks");
        check(spruce.matches(stack(Material.WOOD, 1, false)) && !spruce.matches(stack(Material.WOOD, 0, false)), "plank kinds");

        // a shulker box in a chest is searched, one level deep
        ItemStack box = new ItemStack(Material.PURPLE_SHULKER_BOX);
        BlockStateMeta meta = (BlockStateMeta) box.getItemMeta();
        ShulkerBox state = (ShulkerBox) meta.getBlockState();
        state.getInventory().setItem(5, stack(Material.WOOL, 14, false));
        meta.setBlockState(state);
        box.setItemMeta(meta);
        check(red.matches(box), "red wool inside a shulker box");
        check(!spruce.matches(box), "box without the item");
        check(FindRequest.parse("find minecraft:purple_shulker_box 0 0 Purple Shulker Box").matches(box), "the box itself");

        // plugin message decode: VarInt length + UTF-8, nothing else
        check("find minecraft:stone 0 0 Stone".equals(FinderPlugin.decode(encode("find minecraft:stone 0 0 Stone"))), "decode");
        String longer = "find minecraft:stone 0 0 " + new String(new char[150]).replace('\0', 'z');
        check(longer.equals(FinderPlugin.decode(encode(longer))), "two-byte length");
        byte[] good = encode("find x");
        check(FinderPlugin.decode(java.util.Arrays.copyOf(good, good.length - 1)) == null, "truncated");
        check(FinderPlugin.decode(java.util.Arrays.copyOf(good, good.length + 1)) == null, "trailing bytes");
        check(FinderPlugin.decode(new byte[0]) == null && FinderPlugin.decode(null) == null, "empty");
        check(FinderPlugin.decode(new byte[]{(byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, 1}) == null, "runaway VarInt");
        check(FinderPlugin.decode(new byte[700]) == null, "oversized message");

        // direction words (Minecraft: +z is south, +x is east)
        Location o = new Location(null, 0, 64, 0);
        check(FinderPlugin.direction(o, new Location(null, 0, 64, 10)).equals("south"), "south");
        check(FinderPlugin.direction(o, new Location(null, 0, 64, -10)).equals("north"), "north");
        check(FinderPlugin.direction(o, new Location(null, 10, 64, 0)).equals("east"), "east");
        check(FinderPlugin.direction(o, new Location(null, -10, 64, 0)).equals("west"), "west");
        check(FinderPlugin.direction(o, new Location(null, 7, 64, -7)).equals("north-east"), "north-east");
        check(FinderPlugin.direction(o, new Location(null, 0.5, 70, 0.5)).equals("right here, above"), "above");
        check(FinderPlugin.direction(o, new Location(null, -8, 50, 8)).equals("south-west, below"), "south-west below");

        // what counts as storage
        check(FinderPlugin.storage(Material.CHEST) && FinderPlugin.storage(Material.TRAPPED_CHEST) && FinderPlugin.storage(Material.RED_SHULKER_BOX)
            && FinderPlugin.storage(Material.HOPPER) && !FinderPlugin.storage(Material.FURNACE) && !FinderPlugin.storage(Material.ENDER_CHEST), "storage kinds");

        // every request the crafting panel can send (written by tests/chest-finder.test.cjs) names a real item
        if (args.length > 0) {
            int panel = 0;
            for (String line : java.nio.file.Files.readAllLines(java.nio.file.Paths.get(args[0]), StandardCharsets.UTF_8)) {
                if (line.isEmpty()) continue;
                FindRequest r = FindRequest.parse(line);
                check(r != null, "panel request parses: " + line);
                panel++;
            }
            System.out.println("FINDER_PANEL requests=" + panel);
        }
        System.out.println("FINDER_OK checks=" + checks);
    }
}
