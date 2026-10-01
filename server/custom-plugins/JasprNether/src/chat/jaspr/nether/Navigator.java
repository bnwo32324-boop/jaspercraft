package chat.jaspr.nether;

import java.util.List;
import java.util.Locale;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

/** Admin navigation (/jnether goto) and player-facing test scenarios (/jnether test) for QA and the lead. */
final class Navigator {
    private Navigator() {}

    static Location find(NetherPlugin plugin, Location from, String target) {
        World w = from.getWorld();
        if (!plugin.isNether(w)) return null;
        String t = target.replace('-', '_');
        if (t.equals("urn") || t.equals("statue") || t.equals("font")) {
            Registry.Entry best = null;
            double bd = Double.MAX_VALUE;
            for (Registry.Entry e : plugin.registry.near(from.getBlockX(), from.getBlockZ(), 6000, t)) {
                double d = Math.pow(e.cx() - from.getX(), 2) + Math.pow(e.cz() - from.getZ(), 2);
                if (d < bd && w.getBlockAt(e.x1, e.y1, e.z1).getTypeId() == (t.equals("statue") ? Blocks.STATUE : Blocks.URN) >> 4) { bd = d; best = e; }
            }
            if (best == null) return null;
            plugin.getLogger().info("NETHER_GOTO_POINT type=" + t + " at=" + best.x1 + "," + best.y1 + "," + best.z1);
            for (int[] o : new int[][]{{2, 0}, {-2, 0}, {0, 2}, {0, -2}, {1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
                Location l = safe(w, best.x1 + o[0], best.z1 + o[1], best.y1 + 2, best.y1 - 3);
                if (l != null) {   // facing the point, so a right-click reaches it
                    org.bukkit.util.Vector look = new org.bukkit.util.Vector(best.x1 + 0.5, best.y1 + 0.5, best.z1 + 0.5).subtract(l.toVector().add(new org.bukkit.util.Vector(0, 1.62, 0)));
                    l.setDirection(look);
                    return l;
                }
            }
            return new Location(w, best.x1 + 0.5, best.y1 + 1, best.z1 + 0.5);
        }
        if (t.startsWith("ordeal:")) return ordeal(plugin, from, t.substring(7));
        if (COLOSSAL.contains(t) || (Lords.isLord(t) && Lords.DEFS.get(t).hoard != null)) return colossal(plugin, from, t);
        GlmSites.Site g = glmSite(plugin, from, t);
        if (g != null) {
            // the plan knows every build, generated or not: arrive on its cavern floor, at the edge of its box
            plugin.getLogger().info("NETHER_GOTO_GLM build=" + g.e.key + " tier=" + g.tier + " at=" + g.x + "," + g.floor + "," + g.z);
            // a Lord's stronghold: beside its arena (flying Lords circle above their lair: the ground under it)
            int[] a = plugin.gen.glm.arena(g);
            if (a != null) {
                populate(w, a[0], a[2]);
                boolean flies = Lords.DEFS.get(g.e.lord).flies;
                for (int[] o : new int[][]{{0, 6}, {6, 0}, {0, -6}, {-6, 0}, {4, 4}, {-4, -4}, {0, 10}, {10, 0}, {0, -10}, {-10, 0}}) {
                    populate(w, a[0] + o[0], a[2] + o[1]);
                    Location l = dry(safe(w, a[0] + o[0], a[2] + o[1], flies ? a[1] - 6 : a[1] + 4, flies ? g.floor - 2 : a[1] - 4));
                    if (l != null) { l.setDirection(new Vector(-o[0], flies ? 1.2 : 0, -o[1])); return l; }
                }
            }
            // just outside the build's own box, inside its cavern (the margin around a build is at least ten blocks)
            int depth = (g.rot & 1) == 0 ? g.e.sz : g.e.sx;
            for (int back = depth / 2 + 4; back >= 0; back -= 2) {
                populate(w, g.x, g.z + back);
                Location l = dry(safe(w, g.x, g.z + back, g.floor + 4, g.floor - 2));
                if (l != null) { l.setDirection(new Vector(0, 0, -1)); return l; }
            }
            return new Location(w, g.x + 0.5, Math.min(120, g.floor + g.e.maxCeil - 2), g.z + 0.5);
        }
        if (t.equals("glm") || t.equals("lord") || Lords.isLord(t)) return null;
        Mega.Kind mk = Mega.Kind.byId(t);
        if (mk != null || t.equals("mega")) {
            // the plan knows every site, generated or not: arrive on the cavern floor in front of it
            Mega.Site s = plugin.gen.nearestMega(mk, from.getBlockX(), from.getBlockZ(), 8);
            if (s == null) return null;
            for (int back = 70; back >= 40; back -= 10) {
                Location l = safe(w, s.x, s.z + back, s.y + 16, s.y - 4);
                if (l != null) return l;
            }
            return new Location(w, s.x + 0.5, s.y + 30, s.z + 60.5);
        }
        for (String type : new String[]{"city", "shrine", "village", "bn", "wonder"}) {
            if (!t.equals(type) && !(type.equals("bn") && t.equals("structure"))) continue;
            List<Registry.Entry> list = plugin.registry.near(from.getBlockX(), from.getBlockZ(), 6000, type);
            Registry.Entry best = null;
            double bd = Double.MAX_VALUE;
            for (Registry.Entry e : list) {
                double d = Math.pow(e.cx() - from.getX(), 2) + Math.pow(e.cz() - from.getZ(), 2);
                if (d < bd) { bd = d; best = e; }
            }
            if (best == null) return null;
            Location l = safe(w, best.cx(), best.cz(), best.y2 + 2, best.y1);
            return l != null ? l : new Location(w, best.cx() + 0.5, best.y2 + 1, best.cz() + 0.5);
        }
        Biomes.Nex nex = null;
        Biomes.Bn bn = null;
        for (Biomes.Nex n : Biomes.Nex.values()) if (n.name().toLowerCase(Locale.ROOT).equals(t)) nex = n;
        for (Biomes.Bn b : Biomes.Bn.values()) if (b.name().toLowerCase(Locale.ROOT).equals(t)) bn = b;
        if (nex == null && bn == null) return null;
        int ox = from.getBlockX(), oz = from.getBlockZ();
        for (int r = 0; r <= 3200; r += 32) {
            for (int i = -r; i <= r; i += 32) for (int k = 0; k < 4; k++) {
                int x = ox + (k == 0 ? i : k == 1 ? r : k == 2 ? -i : -r), z = oz + (k == 0 ? -r : k == 1 ? i : k == 2 ? r : -i);
                if (r > 0 && k > 0 && Math.abs(i) == r) continue;
                Biomes.Nex here = plugin.gen.biomes.nex(x, z);
                if (nex != null && here != nex) continue;
                if (bn != null) {
                    if (here != Biomes.Nex.HELL) continue;
                    for (int y = 100; y >= 36; y -= 8) {
                        if (plugin.gen.biomes.bn(x, y, z) != bn) continue;
                        Location l = safe(w, x, z, y + 8, y - 12);
                        if (l != null && plugin.gen.biomes.bn(l.getBlockX(), l.getBlockY(), l.getBlockZ()) == bn) return l;
                    }
                    continue;
                }
                Location l = safe(w, x, z, 110, 32);
                if (l != null) return l;
            }
        }
        return null;
    }

    static final java.util.Set<String> COLOSSAL = new java.util.HashSet<>(java.util.Arrays.asList("colossus", "great_pyramid", "caldera_citadel",
        "catacombs", "heart", "warden"));

    /**
     * The colossal structures (inside the cavern, facing the structure), the Catacombs (the nearest open corridor, the
     * Heart's north gate, the nearest Warden's door) and their bosses (beside their place once it is drawn, otherwise
     * the structure or hall that keeps them).
     */
    static Location colossal(NetherPlugin plugin, Location from, String t) {
        if (plugin.gen == null) return null;
        World w = from.getWorld();
        Lords.Def lord = Lords.DEFS.get(t);
        if (lord != null) {
            Registry.Entry best = null;
            double bd = Double.MAX_VALUE;
            for (Registry.Entry e : plugin.registry.near(from.getBlockX(), from.getBlockZ(), 8000, "lord")) {
                if (!e.name.equals(t)) continue;
                double d = Math.pow(e.x1 - from.getX(), 2) + Math.pow(e.z1 - from.getZ(), 2);
                if (d < bd) { bd = d; best = e; }
            }
            if (best != null) {
                plugin.getLogger().info("NETHER_GOTO_BOSS boss=" + t + " at=" + best.x1 + "," + best.y1 + "," + best.z1);
                populate(w, best.x1, best.z1);
                for (int[] o : new int[][]{{0, 5}, {5, 0}, {0, -5}, {-5, 0}, {3, 3}, {-3, -3}, {0, 8}, {8, 0}, {0, -8}, {-8, 0}}) {
                    Location l = dry(safe(w, best.x1 + o[0], best.z1 + o[1], best.y1 + 3, best.y1 - 3));
                    if (l != null) { l.setDirection(new Vector(-o[0], 0, -o[1])); return l; }
                }
                return new Location(w, best.x1 + 0.5, best.y1 + 1, best.z1 + 0.5);
            }
        }
        String home = t;
        if (lord != null) {
            if (lord.hoard.startsWith("jaspr:depths")) home = lord.champion ? "warden" : "heart";
            else home = lord.hoard.contains("pyramid") ? "great_pyramid" : "caldera_citadel";
        }
        Depths dp = plugin.gen.depths;
        switch (home) {
            case "catacombs": {
                if (dp == null) return null;
                int ci = Math.floorDiv(Math.max(dp.hx - Depths.HALF + 20, Math.min(dp.hx + Depths.HALF - 20, from.getBlockX())), Depths.P);
                int cj = Math.floorDiv(Math.max(dp.hz - Depths.HALF + 20, Math.min(dp.hz + Depths.HALF - 20, from.getBlockZ())), Depths.P);
                for (int r = 0; r <= 24; r++) for (int i = ci - r; i <= ci + r; i++) for (int j = cj - r; j <= cj + r; j++) {
                    if (Math.max(Math.abs(i - ci), Math.abs(j - cj)) != r) continue;
                    int x = i * Depths.P + 6, z = j * Depths.P + 6;
                    if (dp.top(x, z) < Depths.CORR) continue;
                    return under(plugin, w, x, z);
                }
                return null;
            }
            case "heart": return dp == null ? null : under(plugin, w, dp.hx, dp.hz - 59);
            case "warden": {
                if (dp == null) return null;
                int[][] off = {{0, -Depths.WARD}, {Depths.WARD, 0}, {0, Depths.WARD}, {-Depths.WARD, 0}};
                int pick = lord != null ? java.util.Arrays.asList(Depths.WARDENS).indexOf(t) : -1;
                double bd = Double.MAX_VALUE;
                if (pick < 0) for (int k = 0; k < 4; k++) {
                    double d = Math.pow(dp.hx + off[k][0] * Depths.P - from.getX(), 2) + Math.pow(dp.hz + off[k][1] * Depths.P - from.getZ(), 2);
                    if (d < bd) { bd = d; pick = k; }
                }
                return under(plugin, w, dp.hx + off[pick][0] * Depths.P, dp.hz + off[pick][1] * Depths.P - 28);
            }
            default: {
                Colossi.Kind k = home.equals("colossus") ? null : Colossi.Kind.byId(home);
                for (Colossi.Site s : plugin.gen.colossi.near(k, from.getBlockX(), from.getBlockZ(), 6)) {
                    if (!plugin.gen.colossusBuilt(s)) continue;
                    plugin.getLogger().info("NETHER_GOTO_COLOSSUS kind=" + s.kind.id + " at=" + s.x + "," + s.y + "," + s.z);
                    for (int q = 0; q < 16; q++) {
                        double a = q * Math.PI / 8;
                        int x = s.x + (int) Math.round(Math.cos(a) * (s.kind.radius - 30)), z = s.z + (int) Math.round(Math.sin(a) * (s.kind.radius - 30));
                        populate(w, x, z);
                        Location l = dry(safe(w, x, z, s.y + 20, s.y - 2));
                        if (l != null) { l.setDirection(new Vector(s.x - x, 0, s.z - z)); return l; }
                    }
                    return new Location(w, s.x + 0.5, s.y + 40, s.z + 0.5);
                }
                return null;
            }
        }
    }

    /**
     * Beside the nearest trap, puzzle or seal of an id (ordeal:pyramid_canopic, ordeal:citadel_rite, ordeal:depths_heart,
     * ordeal:arrows ...) in the built colossi within three cells or the Catacombs around the Heart, facing it (QA).
     */
    static Location ordeal(NetherPlugin plugin, Location from, String id) {
        if (plugin.gen == null) return null;
        World w = from.getWorld();
        List<Ordeals.Ordeal> all = new java.util.ArrayList<>();
        for (Colossi.Site s : plugin.gen.colossi.near(null, from.getBlockX(), from.getBlockZ(), 3))
            if (plugin.gen.colossusBuilt(s)) all.addAll(Colossi.design(s.kind).ordeals(s));
        Depths dp = plugin.gen.depths;
        if (dp != null) all.addAll(dp.ordealsNear(dp.hx, dp.hz, 70));
        Ordeals.Ordeal best = null;
        double bd = Double.MAX_VALUE;
        for (Ordeals.Ordeal o : all) {
            if (!o.id.equals(id)) continue;
            double d = Math.pow((o.x1 + o.x2) / 2.0 - from.getX(), 2) + Math.pow((o.z1 + o.z2) / 2.0 - from.getZ(), 2);
            if (d < bd) { bd = d; best = o; }
        }
        if (best == null) return null;
        int cx = (best.x1 + best.x2) / 2, cz = (best.z1 + best.z2) / 2;
        populate(w, cx, cz);
        plugin.getLogger().info("NETHER_GOTO_ORDEAL id=" + id + " type=" + best.type + " box=" + best.x1 + "," + best.y1 + "," + best.z1 + ".." + best.x2 + "," + best.y2 + "," + best.z2);
        boolean seal = best.type == Ordeals.Type.KEYSEAL || best.type == Ordeals.Type.BOSSSEAL;
        if (seal) {
            // a door: stand two blocks out from its face, on whichever side is open
            boolean alongX = best.x2 - best.x1 >= best.z2 - best.z1;
            for (int side = -1; side <= 1; side += 2) for (int out = 2; out <= 3; out++) {
                int x = alongX ? cx : (side < 0 ? best.x1 : best.x2) + side * out, z = alongX ? (side < 0 ? best.z1 : best.z2) + side * out : cz;
                Location l = safe(w, x, z, best.y1 + 2, best.y1 - 2);
                if (l == null) continue;
                l.setDirection(new Vector(cx + 0.5, best.y1 + 1, cz + 0.5).subtract(l.toVector().add(new Vector(0, 1.62, 0))));
                return l;
            }
        }
        // a trap or a puzzle room: standing inside its zone (feet between its bottom and its top)
        for (int[] q : new int[][]{{cx, cz}, {best.x1, best.z1}, {best.x2, best.z2}, {best.x1, best.z2}, {best.x2, best.z1}}) {
            Location l = safe(w, q[0], q[1], best.y2 - 1, best.y1 - 2);
            if (l != null && l.getY() >= best.y1 && l.getY() <= best.y2) return l;
        }
        return new Location(w, cx + 0.5, best.y1, cz + 0.5);
    }

    /** A spot on the Catacombs' floor (the chunks around drawn first). */
    private static Location under(NetherPlugin plugin, World w, int x, int z) {
        populate(w, x, z);
        plugin.getLogger().info("NETHER_GOTO_CATACOMBS at=" + x + "," + (Depths.FLOOR + 1) + "," + z);
        Location l = new Location(w, x + 0.5, Depths.FLOOR + 1, z + 0.5);
        l.setDirection(new Vector(0, 0, 1));
        return l;
    }

    /** The nearest GLM build for a goto target: "glm" (any), "lord" (any Lord), a Lord's id, or a build's key (n153). */
    static GlmSites.Site glmSite(NetherPlugin plugin, Location from, String t) {
        if (plugin.gen == null) return null;
        int x = from.getBlockX(), z = from.getBlockZ();
        GlmSites.Site best = null;
        double bd = Double.MAX_VALUE;
        String key = t.toUpperCase(Locale.ROOT);
        boolean any = t.equals("glm"), lord = t.equals("lord") || Lords.isLord(t), byKey = plugin.gen.glm.entry(key) != null;
        if (!any && !lord && !byKey) return null;
        for (GlmSites glm : new GlmSites[]{plugin.gen.glm, plugin.gen.legacyGlm}) {
        for (GlmSites.Tier tier : GlmSites.Tier.values()) {
            if (lord && tier != GlmSites.Tier.LORD) continue;
            int cells = any ? 2 : tier == GlmSites.Tier.COMMON ? 10 : tier == GlmSites.Tier.GREAT ? 7 : 5;
            int cx = Math.floorDiv(x, glm.cell(tier)), cz = Math.floorDiv(z, glm.cell(tier));
            for (int dx = -cells; dx <= cells; dx++) for (int dz = -cells; dz <= cells; dz++) {
                GlmSites.Site s = glm.site(tier, cx + dx, cz + dz);
                if (s == null || (glm.legacy ? !Boolean.TRUE.equals(plugin.gen.legacyRegistry.glmDecision(s.decisionTier(), s.cellX, s.cellZ))
                    : Boolean.FALSE.equals(plugin.registry.glmDecision(s.decisionTier(), s.cellX, s.cellZ)))) continue;
                if (byKey && !s.e.key.equals(key)) continue;
                if (Lords.isLord(t) && !t.equals(s.e.lord)) continue;
                double d = s.dist(x, z);
                if (d < bd) { bd = d; best = s; }
            }
        }
        }
        return best;
    }

    /** Loads the chunks around a column so it is populated (a chunk is decorated once its neighbours are loaded). */
    static void populate(World w, int x, int z) {
        for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) w.getChunkAt((x >> 4) + dx, (z >> 4) + dz);
    }

    /** The spot, unless it stands in or on a liquid. */
    static Location dry(Location l) {
        if (l == null) return null;
        Block feet = l.getBlock(), under = feet.getRelative(0, -1, 0);
        if (feet.isLiquid() || feet.getRelative(0, 1, 0).isLiquid() || under.isLiquid()) return null;
        return l;
    }

    /** Highest standing spot between top and bottom: solid, non-lava floor with two blocks of air. */
    static Location safe(World w, int x, int z, int top, int bottom) {
        w.getChunkAt(x >> 4, z >> 4);
        for (int y = Math.min(125, top); y > Math.max(2, bottom); y--) {
            Block b = w.getBlockAt(x, y, z);
            Material m = b.getType();
            if (!m.isSolid() || m == Material.MAGMA || m == Material.BEDROCK) continue;
            if (b.getRelative(0, 1, 0).getType() == Material.AIR && b.getRelative(0, 2, 0).getType() == Material.AIR)
                return b.getLocation().add(0.5, 1, 0.5);
        }
        return null;
    }

    static void scenario(NetherPlugin plugin, Player p, String what) {
        Location l = p.getLocation();
        Vector f = l.getDirection().setY(0);
        if (f.lengthSquared() < 1e-4) f = new Vector(0, 0, 1);
        f.normalize();
        LivingEntity e = null;
        switch (what.startsWith("brew") ? "brew" : what) {
            case "wight": e = plugin.mobs.spawn("wight", l.clone().add(f.clone().multiply(3)), false); break;
            case "coolmar": e = plugin.mobs.spawn("coolmar_spider", l.clone().add(f.clone().multiply(3)), false); break;
            case "brute": e = plugin.mobs.spawn("brute", l.clone().add(f.clone().multiply(10)), false); break;
            case "frost": e = plugin.mobs.spawn("frost", l.clone().add(f.clone().multiply(8)).add(0, 2, 0), false); break;
            case "creeper": e = plugin.mobs.spawn("spore_creeper", l.clone().add(f.clone().multiply(4)), false); break;
            case "spore": {
                e = plugin.mobs.spawnSpore(l.clone().add(f.clone().multiply(1.5)), 4);
                Mobs.T t = e == null ? null : plugin.mobs.track(e);
                if (t != null) plugin.mobs.setStage(t, 4);
                break;
            }
            case "thorn": l.getBlock().getRelative(f.getX() > 0.5 ? 1 : f.getX() < -0.5 ? -1 : 0, 0, f.getZ() > 0.5 ? 1 : f.getZ() < -0.5 ? -1 : 0)
                .setTypeIdAndData(Blocks.THORNSTALK >> 4, (byte) (Blocks.THORNSTALK & 15), false); break;
            case "egg": l.getBlock().setTypeIdAndData(Blocks.EGG_PLANT >> 4, (byte) (Blocks.EGG_PLANT & 15), false); break;
            case "bluefire": {
                Block b = l.getBlock().getRelative(f.getX() > 0.5 ? 1 : f.getX() < -0.5 ? -1 : 0, 0, f.getZ() > 0.5 ? 1 : f.getZ() < -0.5 ? -1 : 0);
                b.getRelative(0, -1, 0).setTypeIdAndData(Blocks.FROSTBURN_ICE >> 4, (byte) (Blocks.FROSTBURN_ICE & 15), false);
                b.setType(Material.FIRE, false);
                break;
            }
            case "brew": {
                Block b = l.getBlock().getRelative(f.getX() > 0.5 ? 2 : f.getX() < -0.5 ? -2 : 0, 0, f.getZ() > 0.5 ? 2 : f.getZ() < -0.5 ? -2 : 0);
                b.setType(Material.BREWING_STAND);
                org.bukkit.inventory.BrewerInventory inv = ((org.bukkit.block.BrewingStand) b.getState()).getInventory();
                for (int i = 0; i < 3; i++) {
                    org.bukkit.inventory.ItemStack pot = new org.bukkit.inventory.ItemStack(Material.POTION);
                    org.bukkit.inventory.meta.PotionMeta pm = (org.bukkit.inventory.meta.PotionMeta) pot.getItemMeta();
                    pm.setBasePotionData(new org.bukkit.potion.PotionData(org.bukkit.potion.PotionType.AWKWARD));
                    pot.setItemMeta(pm);
                    inv.setItem(i, pot);
                }
                String ing = what.length() > 4 ? what.substring(5) : "ghast_meat_raw";
                inv.setIngredient(Items.create(Items.DEFS.containsKey(ing) ? ing : "ghast_meat_raw", 1));
                inv.setFuel(new org.bukkit.inventory.ItemStack(Material.BLAZE_POWDER, 1));
                break;
            }
            case "ores": {
                Block b = l.getBlock();
                b.getRelative(2, 0, 0).setTypeIdAndData(Blocks.AMETHYST_ORE >> 4, (byte) (Blocks.AMETHYST_ORE & 15), false);
                b.getRelative(2, 1, 0).setTypeIdAndData(Blocks.RIME_ORE >> 4, (byte) (Blocks.RIME_ORE & 15), false);
                b.getRelative(-2, 0, 0).setTypeIdAndData(Blocks.CINCINNASITE_ORE >> 4, (byte) (Blocks.CINCINNASITE_ORE & 15), false);
                b.getRelative(-2, 1, 0).setType(Material.QUARTZ_ORE, false);
                break;
            }
            case "rime": {
                Block b = l.getBlock().getRelative(3, 0, 0);
                b.setTypeIdAndData(Blocks.RIME_BLOCK >> 4, (byte) (Blocks.RIME_BLOCK & 15), false);
                plugin.mechanics.registerRime(b);
                b.getRelative(1, 0, 0).setType(Material.STATIONARY_LAVA, false);
                b.getRelative(0, 1, 0).setType(Material.AIR, false);
                LivingEntity z = (LivingEntity) p.getWorld().spawnEntity(b.getLocation().add(0.5, 1, 0.5), org.bukkit.entity.EntityType.ZOMBIE);
                z.setRemoveWhenFarAway(false);
                z.getEquipment().setHelmet(new org.bukkit.inventory.ItemStack(Material.LEATHER_HELMET));
                e = z;
                break;
            }
            case "golem": {
                Block base = l.getBlock().getRelative(0, 0, 3);
                base.setType(Material.GOLD_BLOCK, false);
                base.getRelative(0, 1, 0).setType(Material.GOLD_BLOCK, false);
                base.getRelative(1, 1, 0).setType(Material.GOLD_BLOCK, false);
                base.getRelative(-1, 1, 0).setType(Material.GOLD_BLOCK, false);
                base.getRelative(0, 2, 0).setType(Material.AIR, false);
                break;
            }
            case "statue": {
                Block b = l.getBlock().getRelative(0, 0, 2);
                b.setTypeIdAndData(Blocks.STATUE >> 4, (byte) (Blocks.STATUE & 15), false);
                plugin.registry.add("statue", "test", b.getX(), b.getY(), b.getZ(), b.getX(), b.getY(), b.getZ());
                break;
            }
            default: p.sendMessage(ChatColor.RED + "Unknown scenario"); return;
        }
        if (e != null) e.setRemoveWhenFarAway(false);
        plugin.getLogger().info("NETHER_TEST_SCENARIO name=" + what + " spawned=" + (e != null));
    }
}
