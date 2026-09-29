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
            Mega.Site s = plugin.gen.mega.nearest(mk, from.getBlockX(), from.getBlockZ(), 8);
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

    /** The nearest GLM build for a goto target: "glm" (any), "lord" (any Lord), a Lord's id, or a build's key (n153). */
    static GlmSites.Site glmSite(NetherPlugin plugin, Location from, String t) {
        if (plugin.gen == null) return null;
        GlmSites glm = plugin.gen.glm;
        int x = from.getBlockX(), z = from.getBlockZ();
        GlmSites.Site best = null;
        double bd = Double.MAX_VALUE;
        String key = t.toUpperCase(Locale.ROOT);
        boolean any = t.equals("glm"), lord = t.equals("lord") || Lords.isLord(t), byKey = glm.entry(key) != null;
        if (!any && !lord && !byKey) return null;
        for (GlmSites.Tier tier : GlmSites.Tier.values()) {
            if (lord && tier != GlmSites.Tier.LORD) continue;
            int cells = any ? 2 : tier == GlmSites.Tier.COMMON ? 10 : tier == GlmSites.Tier.GREAT ? 7 : 5;
            int cx = Math.floorDiv(x, tier.cell), cz = Math.floorDiv(z, tier.cell);
            for (int dx = -cells; dx <= cells; dx++) for (int dz = -cells; dz <= cells; dz++) {
                GlmSites.Site s = glm.site(tier, cx + dx, cz + dz);
                if (s == null || Boolean.FALSE.equals(plugin.registry.glmDecision(tier.name().charAt(0), s.cellX, s.cellZ))) continue;
                if (byKey && !s.e.key.equals(key)) continue;
                if (Lords.isLord(t) && !t.equals(s.e.lord)) continue;
                double d = s.dist(x, z);
                if (d < bd) { bd = d; best = s; }
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
