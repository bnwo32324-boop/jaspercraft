package chat.jaspr.nether;

import java.util.Random;
import org.bukkit.Material;
import org.bukkit.SkullType;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.block.Chest;
import org.bukkit.block.CreatureSpawner;
import org.bukkit.block.Skull;
import org.bukkit.craftbukkit.v1_12_R1.CraftWorld;
import org.bukkit.entity.EntityType;

/** Tile entities and residents of placed structures, done through Bukkit after the batched block flush. */
final class StructureOps {
    private static final BlockFace[] ROT16 = {BlockFace.SOUTH, BlockFace.SOUTH_SOUTH_WEST, BlockFace.SOUTH_WEST, BlockFace.WEST_SOUTH_WEST,
        BlockFace.WEST, BlockFace.WEST_NORTH_WEST, BlockFace.NORTH_WEST, BlockFace.NORTH_NORTH_WEST, BlockFace.NORTH,
        BlockFace.NORTH_NORTH_EAST, BlockFace.NORTH_EAST, BlockFace.EAST_NORTH_EAST, BlockFace.EAST, BlockFace.EAST_SOUTH_EAST,
        BlockFace.SOUTH_EAST, BlockFace.SOUTH_SOUTH_EAST};

    private final NetherPlugin plugin;
    int chests, spawners, skulls, residents, lavaTicks, journals, glmTiles, glmLoot, signs, banners, pots;

    StructureOps(NetherPlugin plugin) { this.plugin = plugin; }

    void placeChest(World w, int x, int y, int z, int facing, String table, Random r) {
        if (!w.isChunkLoaded(x >> 4, z >> 4)) return;
        Block b = w.getBlockAt(x, y, z);
        b.setTypeIdAndData(Blocks.CHEST, (byte) facing, false);
        BlockState s = b.getState();
        if (!(s instanceof Chest)) return;
        org.bukkit.inventory.Inventory inv = ((Chest) s).getBlockInventory();
        Loot.fill(inv, table, r); // live inventory; never update() afterwards
        if (table.equals("jaspr:wonder/camp") && plugin.gen != null) {
            int slot = inv.firstEmpty();
            if (slot >= 0) inv.setItem(slot, Wonders.journal(plugin.gen, x, z, r));
            journals++;
        }
        chests++;
    }

    void placeSpawner(World w, int x, int y, int z, String mob) { placeSpawner(w, x, y, z, mob, false); }

    /**
     * A spawner block. An inert one (the GLM strongholds') never spawns by itself: {@link GlmLife} brings its creature
     * while a player is near, so it can make any Nether creature in any light; the block shows which one it makes.
     */
    void placeSpawner(World w, int x, int y, int z, String mob, boolean inert) {
        if (!w.isChunkLoaded(x >> 4, z >> 4)) return;
        Block b = w.getBlockAt(x, y, z);
        b.setType(Material.MOB_SPAWNER, false);
        BlockState s = b.getState();
        if (!(s instanceof CreatureSpawner)) return;
        Mobs.Spec spec = Mobs.KINDS.get(mob);
        EntityType type = spec != null ? spec.base : mob.endsWith("ghast") ? EntityType.GHAST : mob.equals("magma_cube") ? EntityType.MAGMA_CUBE
            : mob.equals("wither_skeleton") ? EntityType.WITHER_SKELETON : mob.equals("zombie_pigman") ? EntityType.PIG_ZOMBIE : EntityType.BLAZE;
        CreatureSpawner cs = (CreatureSpawner) s;
        cs.setSpawnedType(type);
        if (inert) cs.setRequiredPlayerRange(0);
        s.update(true, false);
        spawners++;
    }

    private static final org.bukkit.Material[] POT_PLANTS = {Material.RED_MUSHROOM, Material.BROWN_MUSHROOM, Material.DEAD_BUSH};

    /** A tile block of a GLM build: the block (with its tile entity), then its loot, text, pattern, head or plant. */
    void placeGlmTile(World w, GlmSites.Pending p, Random r) {
        if (!w.isChunkLoaded(p.x >> 4, p.z >> 4)) return;
        Block b = w.getBlockAt(p.x, p.y, p.z);
        int id = p.block >> 4, meta = p.block & 15;
        GlmBuild.Tile t = p.t;
        glmTiles++;
        switch (t.type) {
            case GlmBuild.T_CHEST: {
                b.setTypeIdAndData(id, (byte) meta, false);
                if (p.table.isEmpty()) break;
                BlockState s = b.getState();
                if (s instanceof Chest) { Loot.fill(((Chest) s).getBlockInventory(), p.table, r); glmLoot++; } // live inventory; never update() afterwards
                break;
            }
            case GlmBuild.T_SIGN: {
                b.setTypeIdAndData(id, (byte) meta, false);
                boolean text = false;
                if (t.lines != null) for (String l : t.lines) if (!l.isEmpty()) text = true;
                if (!text) break;
                BlockState s = b.getState();
                if (s instanceof org.bukkit.block.Sign) {
                    for (int i = 0; i < 4; i++) ((org.bukkit.block.Sign) s).setLine(i, t.lines[i]);
                    s.update(true, false);
                    signs++;
                }
                break;
            }
            case GlmBuild.T_BANNER: {
                b.setTypeIdAndData(id, (byte) meta, false);
                BlockState s = b.getState();
                if (s instanceof org.bukkit.block.Banner) {
                    org.bukkit.block.Banner bn = (org.bukkit.block.Banner) s;
                    bn.setBaseColor(org.bukkit.DyeColor.getByDyeData((byte) t.base));
                    java.util.List<org.bukkit.block.banner.Pattern> list = new java.util.ArrayList<>();
                    for (int i = 0; i < t.patterns.length; i++) {
                        org.bukkit.block.banner.PatternType pt = org.bukkit.block.banner.PatternType.getByIdentifier(t.patterns[i]);
                        if (pt != null) list.add(new org.bukkit.block.banner.Pattern(org.bukkit.DyeColor.getByDyeData((byte) t.patternColours[i]), pt));
                    }
                    bn.setPatterns(list);
                    s.update(true, false);
                    banners++;
                }
                break;
            }
            case GlmBuild.T_SKULL: {
                b.setTypeIdAndData(Blocks.SKULL, (byte) meta, false);
                BlockState s = b.getState();
                if (!(s instanceof Skull)) break;
                ((Skull) s).setSkullType(t.skull == 2 ? SkullType.ZOMBIE : t.skull == 4 ? SkullType.CREEPER : SkullType.SKELETON);
                if (meta == 1) ((Skull) s).setRotation(ROT16[(t.rot + p.rot * 4) & 15]);
                s.update(true, false);
                skulls++;
                break;
            }
            case GlmBuild.T_POT: {
                b.setTypeIdAndData(Material.FLOWER_POT.getId(), (byte) 0, false);
                if (t.item == null || t.item.isEmpty() || t.item.endsWith(":air")) break;
                BlockState s = b.getState();
                if (s instanceof org.bukkit.block.FlowerPot) {
                    ((org.bukkit.block.FlowerPot) s).setContents(new org.bukkit.material.MaterialData(POT_PLANTS[r.nextInt(POT_PLANTS.length)]));
                    s.update(true, false);
                    pots++;
                }
                break;
            }
            default:
        }
    }

    void placeSkull(World w, int x, int y, int z, int type, int rot) {
        if (!w.isChunkLoaded(x >> 4, z >> 4)) return;
        Block b = w.getBlockAt(x, y, z);
        b.setTypeIdAndData(Blocks.SKULL, (byte) 1, false);
        BlockState s = b.getState();
        if (!(s instanceof Skull)) return;
        ((Skull) s).setSkullType(type == 0 ? SkullType.SKELETON : type == 2 ? SkullType.ZOMBIE : SkullType.SKELETON);
        ((Skull) s).setRotation(ROT16[rot & 15]);
        s.update(true, false);
        skulls++;
    }

    void spawnResident(World w, int x, int y, int z, String kind) {
        if (plugin.mobs == null || !w.isChunkLoaded(x >> 4, z >> 4)) return;
        String k = kind.replace("netherex:", "");
        org.bukkit.entity.LivingEntity e = plugin.mobs.spawn(k.equals("gold_golem") ? "gold_golem" : "pigtificate", new org.bukkit.Location(w, x + 0.5, y, z + 0.5), false);
        if (e == null) return;
        plugin.mobs.setHome(e, x, y, z);   // residents keep to their post (stall, gate, vault)
        residents++;
    }

    void tickLava(World w, int x, int y, int z) {
        try {
            ((CraftWorld) w).getHandle().a(new net.minecraft.server.v1_12_R1.BlockPosition(x, y, z), net.minecraft.server.v1_12_R1.Blocks.FLOWING_LAVA, 10);
            lavaTicks++;
        } catch (RuntimeException ignored) { }
    }
}
