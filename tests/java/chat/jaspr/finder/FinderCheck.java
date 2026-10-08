package chat.jaspr.finder;

import java.nio.charset.StandardCharsets;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.ShulkerBox;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BlockStateMeta;
import org.bukkit.inventory.meta.ItemMeta;

/** Offline check for the Chest Finder: request parsing, item matching (wear, JasperCraft items, damage kinds, shulker
 * boxes), the plugin-message decode and the direction words; Find on an inventory slot (the request built from a real
 * stack, the "find slot" message and the lookup in the window that is open); with a file argument, that every request
 * the crafting panel can send parses. Prints FINDER_OK checks=N. */
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

        // Find on an inventory slot (Shift + right click): the request is built from the server's own copy of the stack
        FindRequest worn = FindRequest.of(stack(Material.DIAMOND_PICKAXE, 900, false), "Diamond Pickaxe");
        check(worn != null && worn.material == Material.DIAMOND_PICKAXE && !worn.exact && worn.damage == 0, "a worn vanilla pickaxe is asked for without its wear");
        check(worn.matches(stack(Material.DIAMOND_PICKAXE, 0, false)) && worn.matches(stack(Material.DIAMOND_PICKAXE, 1200, false)), "any wear matches");
        check(!worn.matches(stack(Material.DIAMOND_PICKAXE, 106, true)), "never an armoury pickaxe built on the same base");
        FindRequest model = FindRequest.of(stack(Material.DIAMOND_PICKAXE, 106, true), "Titan Pickaxe");
        check(model.exact && model.damage == 106 && model.matches(stack(Material.DIAMOND_PICKAXE, 106, true)), "a JasperCraft model item is exact");
        check(!model.matches(stack(Material.DIAMOND_PICKAXE, 107, true)) && !model.matches(stack(Material.DIAMOND_PICKAXE, 106, false)), "only that model");
        FindRequest wool = FindRequest.of(stack(Material.WOOL, 14, false), "Red Wool");
        check(!wool.exact && wool.damage == 14 && wool.matches(stack(Material.WOOL, 14, false)) && !wool.matches(stack(Material.WOOL, 0, false)), "wool: its colour");
        check(FindRequest.of(new ItemStack(Material.DIRT, 64), null).title.equals("Dirt"), "no title from the client: the material's name");
        ItemStack called = new ItemStack(Material.DIAMOND_SWORD);
        ItemMeta calledMeta = called.getItemMeta(); calledMeta.setDisplayName("§bVermilion §lBlade"); called.setItemMeta(calledMeta);
        check(FindRequest.of(called, "").title.equals("Vermilion Blade"), "no title from the client: the item's own name, colour codes stripped");
        check(FindRequest.of(called, "§cWhat the client says§r\u0007").title.equals("What the client says"), "the client's title wins, cleaned");
        check(FindRequest.of(called, new String(new char[120]).replace('\0', 'q')).title.length() == FindRequest.MAX_TITLE, "title bounded");
        check(FindRequest.of(null, "x") == null && FindRequest.of(new ItemStack(Material.AIR), "x") == null, "an empty stack finds nothing");
        // the message: "find slot <window id> <slot> [title]"
        check(java.util.Arrays.equals(FinderPlugin.slotRef("find slot 0 12 Emerald Boots"), new int[]{0, 12}), "inventory window 0, slot 12");
        check(java.util.Arrays.equals(FinderPlugin.slotRef("find slot 37 3"), new int[]{37, 3}) && FinderPlugin.slotTitle("find slot 37 3").isEmpty(), "no title");
        check("Emerald Boots".equals(FinderPlugin.slotTitle("find slot 0 12 Emerald Boots")), "title kept whole, spaces included");
        for (String bad : new String[]{null, "", "find slot", "find slot 1", "find slot x 1", "find slot 1 y", "find slot -1 3", "find slot 3 -1", "find slot 256 0",
                "find slot 0 256", "find slot 0 99999999999 x", "find minecraft:stone 0 0 x", "find slotted 0 1 x", "slot 0 1", "find slot 0 1 " + new String(new char[200]).replace('\0', 'z')})
            check(FinderPlugin.slotRef(bad) == null, "slot request rejected: " + bad);
        // the server's own copy of the slot, only in the window that is open
        net.minecraft.server.v1_12_R1.InventorySubcontainer held = new net.minecraft.server.v1_12_R1.InventorySubcontainer("check", false, 4);
        held.setItem(1, org.bukkit.craftbukkit.v1_12_R1.inventory.CraftItemStack.asNMSCopy(stack(Material.DIAMOND_PICKAXE, 33, false)));
        held.setItem(3, org.bukkit.craftbukkit.v1_12_R1.inventory.CraftItemStack.asNMSCopy(stack(Material.DIAMOND_PICKAXE, 106, true)));
        net.minecraft.server.v1_12_R1.Container open = new net.minecraft.server.v1_12_R1.Container() {
            { windowId = 5; for (int i = 0; i < 4; i++) a(new net.minecraft.server.v1_12_R1.Slot(held, i, 0, 0)); }
            @Override public org.bukkit.inventory.InventoryView getBukkitView() { return null; }
            @Override public boolean canUse(net.minecraft.server.v1_12_R1.EntityHuman human) { return true; }
        };
        FinderPlugin.SlotLookup pickaxe = FinderPlugin.lookup(open, 5, 1);
        check(pickaxe.problem == null && pickaxe.stack.getType() == Material.DIAMOND_PICKAXE && pickaxe.stack.getDurability() == 33, "slot 1 holds the pickaxe");
        FindRequest slotRequest = FindRequest.of(pickaxe.stack, "Diamond Pickaxe");
        check(slotRequest.matches(stack(Material.DIAMOND_PICKAXE, 0, false)) && !slotRequest.exact, "and asks for diamond pickaxes");
        FindRequest slotModel = FindRequest.of(FinderPlugin.lookup(open, 5, 3).stack, "Titan Pickaxe");
        check(slotModel.exact && slotModel.damage == 106, "the unbreakable one in slot 3 is exact");
        check("empty".equals(FinderPlugin.lookup(open, 5, 0).problem) && FinderPlugin.lookup(open, 5, 0).stack == null, "an empty slot");
        check("stale".equals(FinderPlugin.lookup(open, 6, 1).problem) && "stale".equals(FinderPlugin.lookup(open, 0, 1).problem), "another window than the open one");
        check("stale".equals(FinderPlugin.lookup(null, 5, 1).problem), "no window at all");
        check("range".equals(FinderPlugin.lookup(open, 5, 4).problem) && "range".equals(FinderPlugin.lookup(open, 5, 200).problem), "past the last slot");

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
        // every "find slot" message the inventory menu can send (written by tests/chest-finder.test.cjs) is well formed, and
        // whatever name rides along becomes a clean, bounded title
        if (args.length > 1) {
            int slots = 0;
            for (String line : java.nio.file.Files.readAllLines(java.nio.file.Paths.get(args[1]), StandardCharsets.UTF_8)) {
                if (line.isEmpty()) continue;
                int[] ref = FinderPlugin.slotRef(line);
                check(ref != null && ref[0] == 9, "slot request parses: " + line);
                String title = FindRequest.clean(FinderPlugin.slotTitle(line));
                check(title.length() <= FindRequest.MAX_TITLE && title.indexOf('§') < 0, "title is clean: " + line);
                check(FindRequest.of(new ItemStack(Material.DIAMOND_PICKAXE), FinderPlugin.slotTitle(line)).title.length() > 0, "a title is always found: " + line);
                slots++;
            }
            System.out.println("FINDER_SLOT requests=" + slots);
        }
        System.out.println("FINDER_OK checks=" + checks);
    }
}
