package chat.jaspr.nether;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.bukkit.Bukkit;
import org.bukkit.Keyed;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.BrewEvent;
import org.bukkit.event.inventory.FurnaceSmeltEvent;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.inventory.BrewerInventory;
import org.bukkit.inventory.FurnaceRecipe;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.ShapelessRecipe;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionType;

/**
 * Recipes of both mods that matter for gameplay. Bukkit 1.12 recipes cannot match NBT, so each recipe is registered
 * on its base materials (for correct consumption) and PrepareItemCraftEvent checks the actual JasprNether ids, sets
 * our result, or clears it when a vanilla item stands in for a custom one. Where a vanilla recipe already has the same
 * shape (hide armour, wither bone -> dust, stalagnate mushroom stew) only the result is overridden.
 */
final class Crafting implements Listener {
    /** Ingredient: a JasprNether id, or a vanilla material (data -1 = any). */
    static final class Ing {
        final String id; final Material mat; final int data;
        Ing(String id) { this.id = id; Items.Def d = Items.DEFS.get(id); mat = d.mat; data = d.data; }
        Ing(Material m, int data) { id = null; mat = m; this.data = data; }
        boolean matches(ItemStack s) {
            if (s == null || s.getType() == Material.AIR) return false;
            String sid = Items.id(s);
            if (id != null) return id.equals(sid);
            return sid == null && s.getType() == mat && (data < 0 || s.getDurability() == data);
        }
    }

    static final class R {
        final String key, result; final int amount; final String[] shape; final Map<Character, Ing> keys = new HashMap<>();
        final List<Ing> shapeless; final boolean register;
        R(String key, String result, int amount, String[] shape, List<Ing> shapeless, boolean register) {
            this.key = key; this.result = result; this.amount = amount; this.shape = shape; this.shapeless = shapeless; this.register = register;
        }
        R k(char c, Ing i) { keys.put(c, i); return this; }
    }

    private final NetherPlugin plugin;
    final List<R> recipes = new ArrayList<>();
    int crafted, brewed, smelted;

    Crafting(NetherPlugin plugin) { this.plugin = plugin; }

    private R shaped(String key, String result, int amount, boolean register, String... shape) { R r = new R(key, result, amount, shape, null, register); recipes.add(r); return r; }
    private void shapeless(String key, String result, int amount, boolean register, Ing... ings) { recipes.add(new R(key, result, amount, null, java.util.Arrays.asList(ings), register)); }
    private static Ing x(String id) { return new Ing(id); }
    private static Ing v(Material m) { return new Ing(m, -1); }
    private static Ing v(Material m, int d) { return new Ing(m, d); }

    void register() {
        // ---- NetherEx ----
        shaped("amethyst_block", "amethyst_block", 1, true, "AAA", "AAA", "AAA").k('A', x("amethyst_crystal"));
        shapeless("amethyst_unpack", "amethyst_crystal", 9, true, x("amethyst_block"));
        shaped("rime_block", "rime_block", 1, true, "AAA", "AAA", "AAA").k('A', x("rime_crystal"));
        shapeless("rime_unpack", "rime_crystal", 9, true, x("rime_block"));
        shapeless("wither_dust", "wither_dust", 3, false, x("wither_bone"));            // vanilla bone -> bone meal shape
        shaped("wither_bone", "wither_bone", 1, true, "D", "D", "D").k('D', x("wither_dust"));
        shapeless("frost_powder", "frost_powder", 3, true, x("frost_rod"));
        shaped("blazed_wither_bone", "blazed_wither_bone", 1, true, " P ", "PBP", " P ").k('P', v(Material.BLAZE_POWDER)).k('B', x("wither_bone"));
        shaped("frosted_wither_bone", "frosted_wither_bone", 1, true, " P ", "PBP", " P ").k('P', x("frost_powder")).k('B', x("wither_bone"));
        shapeless("rime_and_steel", "rime_and_steel", 1, true, v(Material.FLINT_AND_STEEL), x("rime_crystal"));
        shapeless("rime_and_steel_2", "rime_and_steel", 1, true, v(Material.IRON_INGOT), v(Material.FLINT), x("rime_crystal"));
        shaped("dull_mirror", "dull_mirror", 1, true, "GGG", "GTG", "GGG").k('G', v(Material.GOLD_INGOT)).k('T', v(Material.GHAST_TEAR));
        String[][] armour = {{"helmet", "AAA", "A A"}, {"chestplate", "A A", "AAA", "AAA"}, {"leggings", "AAA", "A A", "A A"}, {"boots", "A A", "A A"}};
        for (String set : new String[]{"wither_bone", "orange_salamander_hide", "black_salamander_hide"}) {
            for (String[] a : armour) {
                String[] shape = java.util.Arrays.copyOfRange(a, 1, a.length);
                shaped(set + "_" + a[0], set + "_" + a[0], 1, set.equals("wither_bone"), shape).k('A', x(set));
            }
        }
        String[] handles = {"withered", "blazed", "frosted"};
        String[] handleIds = {"wither_bone", "blazed_wither_bone", "frosted_wither_bone"};
        for (int h = 0; h < 3; h++) {
            String p = handles[h];
            Ing H = x(handleIds[h]), M = x("amethyst_block"), O = v(Material.OBSIDIAN);
            shaped(p + "_amedian_sword", p + "_amedian_sword", 1, true, " O ", " M ", " H ").k('O', O).k('M', M).k('H', H);
            shaped(p + "_amedian_pickaxe", p + "_amedian_pickaxe", 1, true, "OMO", " H ", " H ").k('O', O).k('M', M).k('H', H);
            shaped(p + "_amedian_shovel", p + "_amedian_shovel", 1, true, " H ", " M ", " O ").k('O', O).k('M', M).k('H', H);
            shaped(p + "_amedian_axe", p + "_amedian_axe", 1, true, " M ", "MHO", " H ").k('O', O).k('M', M).k('H', H);
            shaped(p + "_amedian_hoe", p + "_amedian_hoe", 1, true, "MO ", " H ", " H ").k('O', O).k('M', M).k('H', H);
            shaped(p + "_amedian_hammer", p + "_amedian_hammer", 1, true, "MOM", "MHM", " H ").k('O', O).k('M', M).k('H', H);
        }
        // ---- BetterNether ----
        shaped("cincinnasite_block", "cincinnasite_block", 1, true, "CC", "CC").k('C', x("cincinnasite"));
        shaped("cincinnasite_pickaxe", "cincinnasite_pickaxe", 1, true, "FFF", " R ", " R ").k('F', x("cincinnasite_forged")).k('R', x("nether_reed"));
        shaped("cincinnasite_axe", "cincinnasite_axe", 1, true, "FF", "FR", " R").k('F', x("cincinnasite_forged")).k('R', x("nether_reed"));
        shaped("cincinnasite_pickaxe_diamond", "cincinnasite_pickaxe_diamond", 1, true, "DPD").k('D', v(Material.DIAMOND)).k('P', x("cincinnasite_pickaxe"));
        shaped("cincinnasite_axe_diamond", "cincinnasite_axe_diamond", 1, true, " D", "DP").k('D', v(Material.DIAMOND)).k('P', x("cincinnasite_axe"));
        shaped("stalagnate_bowl", "stalagnate_bowl", 3, true, "S S", " S ").k('S', x("stalagnate"));
        shaped("stalagnate_bowl_wart", "stalagnate_bowl_wart", 1, true, "WWW", " B ").k('W', v(Material.NETHER_STALK)).k('B', x("stalagnate_bowl"));
        shapeless("stalagnate_bowl_mushroom", "stalagnate_bowl_mushroom", 1, false, v(Material.BROWN_MUSHROOM), v(Material.RED_MUSHROOM), x("stalagnate_bowl"));
        shaped("stalagnate_bowl_apple", "stalagnate_bowl_apple", 1, true, "A", "B").k('A', x("black_apple")).k('B', x("stalagnate_bowl"));

        int n = 0;
        for (R r : recipes) {
            if (!r.register) continue;
            NamespacedKey key = new NamespacedKey(plugin, r.key);
            ItemStack out = Items.create(r.result, r.amount);
            try {
                if (r.shape != null) {
                    ShapedRecipe s = new ShapedRecipe(key, out);
                    s.shape(r.shape);
                    for (Map.Entry<Character, Ing> e : r.keys.entrySet()) s.setIngredient(e.getKey(), e.getValue().mat, Math.max(0, e.getValue().data));
                    Bukkit.addRecipe(s);
                } else {
                    ShapelessRecipe s = new ShapelessRecipe(key, out);
                    for (Ing i : r.shapeless) s.addIngredient(1, i.mat, Math.max(0, i.data));
                    Bukkit.addRecipe(s);
                }
                n++;
            } catch (RuntimeException ex) {
                plugin.getLogger().warning("NETHER_RECIPE_FAILED key=" + r.key + " reason=" + ex.getClass().getSimpleName());
            }
        }
        addSmelt("smelt_cincinnasite", "cincinnasite_forged", Material.SANDSTONE, 2);
        addSmelt("smelt_ghast_meat", "ghast_meat_cooked", Material.SPIDER_EYE, 0);
        addSmelt("smelt_congealed", "congealed_magma_cream", Material.MAGMA_CREAM, 0);
        plugin.getLogger().info("NETHER_RECIPES crafting=" + recipes.size() + " registered=" + n + " smelting=3 brewing=4");
    }

    private void addSmelt(String key, String result, Material in, int data) {
        try { Bukkit.addRecipe(new FurnaceRecipe(Items.create(result, 1), in, data)); } catch (RuntimeException ex) {
            plugin.getLogger().warning("NETHER_RECIPE_FAILED key=" + key + " reason=" + ex.getClass().getSimpleName());
        }
    }

    // ---- matching ------------------------------------------------------------------------------------------------------
    R match(ItemStack[] m) {
        int w = m.length == 9 ? 3 : 2;
        for (R r : recipes) if (r.shape != null ? shapedMatch(r, m, w) : shapelessMatch(r, m)) return r;
        return null;
    }

    private static boolean shapelessMatch(R r, ItemStack[] m) {
        List<Ing> need = new ArrayList<>(r.shapeless);
        for (ItemStack s : m) {
            if (s == null || s.getType() == Material.AIR) continue;
            boolean ok = false;
            for (int i = 0; i < need.size(); i++) if (need.get(i).matches(s)) { need.remove(i); ok = true; break; }
            if (!ok) return false;
        }
        return need.isEmpty();
    }

    private static boolean shapedMatch(R r, ItemStack[] m, int w) {
        int minX = w, minY = w, maxX = -1, maxY = -1;
        for (int i = 0; i < m.length; i++) if (m[i] != null && m[i].getType() != Material.AIR) {
            int x = i % w, y = i / w;
            minX = Math.min(minX, x); maxX = Math.max(maxX, x); minY = Math.min(minY, y); maxY = Math.max(maxY, y);
        }
        if (maxX < 0) return false;
        int sh = r.shape.length, sw = r.shape[0].length();
        // trim the recipe shape as vanilla does
        int rMinX = sw, rMaxX = -1, rMinY = sh, rMaxY = -1;
        for (int y = 0; y < sh; y++) for (int x = 0; x < sw; x++) if (r.shape[y].charAt(x) != ' ') {
            rMinX = Math.min(rMinX, x); rMaxX = Math.max(rMaxX, x); rMinY = Math.min(rMinY, y); rMaxY = Math.max(rMaxY, y);
        }
        if (maxX - minX != rMaxX - rMinX || maxY - minY != rMaxY - rMinY) return false;
        for (int mirror = 0; mirror < 2; mirror++) {
            boolean ok = true;
            for (int y = 0; y <= maxY - minY && ok; y++) for (int x = 0; x <= maxX - minX && ok; x++) {
                int rx = mirror == 0 ? rMinX + x : rMaxX - x;
                char c = r.shape[rMinY + y].charAt(rx);
                ItemStack s = m[(minY + y) * w + minX + x];
                boolean empty = s == null || s.getType() == Material.AIR;
                if (c == ' ') ok = empty;
                else ok = !empty && r.keys.get(c).matches(s);
            }
            if (ok) return true;
        }
        return false;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPrepare(PrepareItemCraftEvent e) {
        ItemStack[] m = e.getInventory().getMatrix();
        R r = match(m);
        if (r != null) { e.getInventory().setResult(Items.create(r.result, r.amount)); return; }
        Recipe chosen = e.getRecipe();
        if (chosen instanceof Keyed && ((Keyed) chosen).getKey().getNamespace().equalsIgnoreCase(plugin.getName())) e.getInventory().setResult(null);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onSmelt(FurnaceSmeltEvent e) {
        String src = Items.id(e.getSource());
        Material m = e.getSource().getType();
        if (m == Material.SANDSTONE && e.getSource().getDurability() == 2 && !"cincinnasite_block".equals(src)) e.setCancelled(true);
        else if (m == Material.SPIDER_EYE && !"ghast_meat_raw".equals(src)) e.setCancelled(true);
        else if (src != null && (m == Material.SANDSTONE || m == Material.SPIDER_EYE || m == Material.MAGMA_CREAM)) smelted++;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBrew(BrewEvent e) {
        BrewerInventory inv = e.getContents();
        String ing = Items.id(inv.getIngredient());
        String out;
        if ("rime_crystal".equals(ing)) out = "freezing";
        else if ("frost_fang".equals(ing)) out = "frigid_health";
        else if ("spore".equals(ing)) out = "dispersal";
        else if ("ghast_meat_raw".equals(ing)) out = "sorrow";
        else return;
        e.setCancelled(true);
        boolean any = false;
        for (int i = 0; i < 3; i++) {
            ItemStack s = inv.getItem(i);
            if (s == null || s.getType() != Material.POTION || Items.id(s) != null) continue;
            if (!(s.getItemMeta() instanceof PotionMeta) || ((PotionMeta) s.getItemMeta()).getBasePotionData().getType() != PotionType.AWKWARD) continue;
            inv.setItem(i, Items.potion(out, Material.POTION));
            any = true;
        }
        if (any) {
            ItemStack in = inv.getIngredient();
            in.setAmount(in.getAmount() - 1);
            inv.setIngredient(in.getAmount() <= 0 ? null : in);
            brewed++;
        }
        plugin.getLogger().info("NETHER_BREW result=" + out + " converted=" + any + " at=" + e.getBlock().getX() + "," + e.getBlock().getY() + "," + e.getBlock().getZ());
    }
}
