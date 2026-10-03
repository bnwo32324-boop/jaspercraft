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

        // Sort: the creative screen's tab order (building, decorations, redstone, transportation, miscellaneous with the
        // materials, food, tools, combat, brewing; no tab last)
        check(ChestSorter.tab(Material.STONE) == 0 && ChestSorter.tab(Material.COBBLESTONE) == 0, "stone: building blocks");
        check(ChestSorter.tab(Material.FLOWER_POT_ITEM) == 1, "flower pot: decorations " + ChestSorter.tab(Material.FLOWER_POT_ITEM));
        check(ChestSorter.tab(Material.REDSTONE) == 2, "redstone: redstone");
        check(ChestSorter.tab(Material.MINECART) == 3, "minecart: transportation");
        check(ChestSorter.tab(Material.DIAMOND) == 4 && ChestSorter.tab(Material.IRON_INGOT) == 4 && ChestSorter.tab(Material.BUCKET) == 4, "diamond, bucket: miscellaneous");
        check(ChestSorter.tab(Material.BREAD) == 5, "bread: food");
        check(ChestSorter.tab(Material.IRON_PICKAXE) == 6, "pickaxe: tools");
        check(ChestSorter.tab(Material.DIAMOND_SWORD) == 7, "sword: combat");
        check(ChestSorter.tab(Material.POTION) == 8, "potion: brewing");
        check(ChestSorter.tab(Material.FIREWORK) == 10, "firework rocket: no tab in 1.12, last");
        ItemStack[] chest = new ItemStack[27];
        chest[0] = new ItemStack(Material.BREAD, 5);
        chest[2] = stack(Material.DIAMOND_PICKAXE, 106, true);
        chest[3] = new ItemStack(Material.COBBLESTONE, 10);
        chest[5] = stack(Material.DIAMOND_PICKAXE, 300, false);
        chest[6] = new ItemStack(Material.STONE, 3);
        chest[8] = new ItemStack(Material.COBBLESTONE, 60);
        chest[9] = stack(Material.WOOL, 14, false);
        chest[10] = stack(Material.WOOL, 0, false);
        chest[11] = new ItemStack(Material.DIAMOND, 2);
        chest[12] = stack(Material.DIAMOND_PICKAXE, 5, false);
        chest[20] = new ItemStack(Material.COBBLESTONE, 64);
        ItemStack[] sorted = ChestSorter.sorted(chest);
        check(sorted != null && sorted.length == 27, "sorted, same size");
        String got = "";
        for (ItemStack it : sorted) got += it == null ? "." : it.getType().name() + ":" + it.getDurability() + "x" + it.getAmount() + (ChestSorter.custom(it) ? "*" : "") + " ";
        String want = "STONE:0x3 COBBLESTONE:0x64 COBBLESTONE:0x64 COBBLESTONE:0x6 WOOL:0x1 WOOL:14x1 DIAMOND:0x2 BREAD:0x5 "
            + "DIAMOND_PICKAXE:5x1 DIAMOND_PICKAXE:300x1 DIAMOND_PICKAXE:106x1* ";
        check(got.startsWith(want), "sorted order: " + got);
        check(got.substring(want.length()).replace(".", "").trim().isEmpty(), "empty slots last: " + got);
        check(ChestSorter.same(chest, sorted), "nothing gained or lost");
        // a stack above its normal size stays whole (so the result always fits); a full chest stays full
        ItemStack[] big = new ItemStack[3];
        big[0] = new ItemStack(Material.ENDER_PEARL, 40);
        big[1] = new ItemStack(Material.ENDER_PEARL, 16);
        big[2] = new ItemStack(Material.DIRT, 64);
        ItemStack[] bigSorted = ChestSorter.sorted(big);
        check(bigSorted != null && bigSorted[0].getType() == Material.DIRT && bigSorted[1].getAmount() == 40 && bigSorted[2].getAmount() == 16
            && ChestSorter.same(big, bigSorted), "an over-full pearl stack is left whole");
        ItemStack[] full = new ItemStack[27];
        for (int i = 0; i < 27; i++) full[i] = stack(Material.WOOL, i % 16, false);
        check(ChestSorter.same(full, ChestSorter.sorted(full)), "a full chest of mixed wool");
        ItemStack named = new ItemStack(Material.DIAMOND_SWORD);
        ItemMeta nm = named.getItemMeta(); nm.setDisplayName("Aardvark"); named.setItemMeta(nm);
        ItemStack[] swords = {new ItemStack(Material.DIAMOND_SWORD), named};
        ItemStack[] swordsSorted = ChestSorter.sorted(swords);
        check(swordsSorted[0].hasItemMeta() == false && "Aardvark".equals(swordsSorted[1].getItemMeta().getDisplayName()), "unnamed first, then by name; never merged");

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
