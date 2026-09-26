package chat.jaspr.nether;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.craftbukkit.v1_12_R1.inventory.CraftItemStack;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.PigZombie;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockIgniteEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerItemDamageEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

/**
 * Block and item mechanics of both mods: thornstalk / egg plant / cactus contact, blue fire, rime blocks (freezer),
 * Arctic Abyss freezing, ore drops and Nethermites, foods, Dull Mirror, Rime and Steel, Wither Dust, Ghast Queen Tears,
 * the Gold Golem build, Respawner Statues, armour set bonuses, Amedian/Cincinnasite tool wear and the hammer.
 * The periodic scan is bounded (players x nearby entities, capped).
 */
final class Mechanics implements Listener {
    private final NetherPlugin plugin;
    private final Random random = new Random();
    private final Set<Long> rime = new HashSet<>();
    private final File rimeFile;
    private final List<LivingEntity> curing = new ArrayList<>();
    int thornHits, eggPoisons, cactusHits, blueBurns, rimeFreezes, arcticFreezes, oreDrops, nethermites, statues;

    Mechanics(NetherPlugin plugin) {
        this.plugin = plugin;
        rimeFile = new File(plugin.getDataFolder(), "rime.txt");
        try {
            if (rimeFile.exists()) for (String l : Files.readAllLines(rimeFile.toPath(), StandardCharsets.UTF_8)) if (!l.trim().isEmpty()) rime.add(Long.parseLong(l.trim()));
        } catch (Exception e) { plugin.getLogger().warning("NETHER_RIME_READ_FAILED reason=" + e.getClass().getSimpleName()); }
    }

    private static long pack(int x, int y, int z) { return ((long) (x & 0x3FFFFFF) << 38) | ((long) (z & 0x3FFFFFF) << 12) | (y & 0xFFF); }
    private static int ux(long p) { return (int) (p >> 38) << 6 >> 6; }
    private static int uz(long p) { return (int) (p << 26 >> 38); }
    private static int uy(long p) { return (int) (p & 0xFFF); }

    private void saveRime() {
        try {
            plugin.getDataFolder().mkdirs();
            List<String> l = new ArrayList<>();
            for (long p : rime) l.add(Long.toString(p));
            Files.write(rimeFile.toPath(), l, StandardCharsets.UTF_8);
        } catch (Exception e) { plugin.getLogger().warning("NETHER_RIME_WRITE_FAILED reason=" + e.getClass().getSimpleName()); }
    }

    // ---- periodic (every 4 ticks) ------------------------------------------------------------------------------------
    void tick(long ticks) {
        World w = plugin.nether;
        if (w == null || plugin.gen == null) return;
        int budget = 240;
        Set<Entity> seen = new HashSet<>();
        for (Player p : w.getPlayers()) {
            if (budget <= 0) break;
            contact(p);
            budget--;
            if ((ticks % 20) == 0) ambient(p);
            for (Entity n : p.getNearbyEntities(24, 12, 24)) {
                if (budget <= 0) break;
                if (!(n instanceof LivingEntity) || !seen.add(n)) continue;
                contact((LivingEntity) n);
                if ((ticks % 20) == 0) arctic((LivingEntity) n);
                budget--;
            }
        }
        if ((ticks % 24) == 0) rimeTick(w);
        if ((ticks % 100) == 0) cureTick();
    }

    private void contact(LivingEntity e) {
        if (e.isDead()) return;
        if (e instanceof Player && (((Player) e).getGameMode() == GameMode.CREATIVE || ((Player) e).getGameMode() == GameMode.SPECTATOR)) return;
        Location l = e.getLocation();
        double w = e.getWidth() / 2 + 0.08, h = e.getHeight();
        World world = e.getWorld();
        int x0 = (int) Math.floor(l.getX() - w), x1 = (int) Math.floor(l.getX() + w);
        int z0 = (int) Math.floor(l.getZ() - w), z1 = (int) Math.floor(l.getZ() + w);
        int y0 = (int) Math.floor(l.getY()), y1 = (int) Math.floor(l.getY() + h);
        boolean thorn = false, egg = false, cactus = false, fire = false;
        for (int x = x0; x <= x1; x++) for (int y = y0; y <= y1; y++) for (int z = z0; z <= z1; z++) {
            Block b = world.getBlockAt(x, y, z);
            int id = b.getTypeId();
            if (id == Blocks.THORNSTALK_ID) thorn = true;
            else if (id == Blocks.EGG_PLANT_ID) egg = true;
            else if (id == Material.FIRE.getId()) fire |= blueFireAt(b);
            else if ((id == Blocks.BARREL_CACTUS >> 4 || (id == Blocks.AGAVE >> 4 && b.getData() == (Blocks.AGAVE & 15))
                || (id == Blocks.NETHER_CACTUS >> 4 && b.getData() == (Blocks.NETHER_CACTUS & 15))) && onGravel(b)) cactus = true;
        }
        EntityType t = e.getType();
        if (thorn && t != EntityType.WITHER_SKELETON && t != EntityType.PIG_ZOMBIE && !"spinout".equals(plugin.mobs.kind(e))) {
            e.damage(1.0);
            thornHits++;
        }
        if (egg && !e.hasPotionEffect(PotionEffectType.POISON) && t != EntityType.GHAST && t != EntityType.PIG_ZOMBIE) {
            e.addPotionEffect(new PotionEffect(PotionEffectType.POISON, 100, 3), true);
            e.getWorld().spawnParticle(Particle.SPELL_MOB, l.clone().add(0, 0.4, 0), 0, 0.46, 0.28, 0.55, 1);
            eggPoisons++;
        }
        if (cactus) { e.damage(1.5); cactusHits++; }
        if (fire && !plugin.effects.has(e, Effects.Kind.FIRE_BURNING)) {
            plugin.effects.apply(e, Effects.Kind.FIRE_BURNING, random.nextInt(71));
            blueBurns++;
        }
    }

    private boolean onGravel(Block b) {
        for (int i = 1; i <= 3; i++) {
            Material m = b.getRelative(0, -i, 0).getType();
            if (m == Material.GRAVEL) return true;
            if (m != Material.STAINED_CLAY) return false;
        }
        return false;
    }

    /** Blue fire: fire standing on frostburn ice, fire inside the Arctic Abyss, or a registered blue-fire spot. */
    boolean blueFireAt(Block b) {
        if (!plugin.isNether(b.getWorld()) || plugin.gen == null) return false;
        Block below = b.getRelative(0, -1, 0);
        if ((below.getTypeId() << 4 | below.getData()) == Blocks.FROSTBURN_ICE) return true;
        if (plugin.gen.biomes.nex(b.getX(), b.getZ()) == Biomes.Nex.ARCTIC_ABYSS) return true;
        return plugin.registry.at(b.getX(), b.getY(), b.getZ(), "bluefire") != null;
    }

    /** NetherEx Arctic Abyss: every living tick 1/512 -> Frozen 300 ticks for mobs (players exempt by default). */
    private void arctic(LivingEntity e) {
        if (e instanceof Player || plugin.gen.biomes.nex(e.getLocation().getBlockX(), e.getLocation().getBlockZ()) != Biomes.Nex.ARCTIC_ABYSS) return;
        if (plugin.effects.has(e, Effects.Kind.FROZEN) || random.nextInt(512) >= 20) return;
        if (!plugin.effects.canFreeze(e)) return;
        plugin.effects.apply(e, Effects.Kind.FROZEN, 300);
        arcticFreezes++;
    }

    private void ambient(Player p) {
        World w = p.getWorld();
        Location l = p.getLocation();
        for (int i = 0; i < 40; i++) {
            Block b = w.getBlockAt(l.getBlockX() + random.nextInt(25) - 12, l.getBlockY() + random.nextInt(13) - 6, l.getBlockZ() + random.nextInt(25) - 12);
            int c = (b.getTypeId() << 4) | b.getData();
            if (c == Blocks.SMOKER && b.getRelative(0, 1, 0).getType() == Material.AIR)
                w.spawnParticle(Particle.SMOKE_LARGE, b.getLocation().add(0.5, 1.1, 0.5), 1, 0.05, 0.1, 0.05, 0.01);
            else if (c == Blocks.EGG_PLANT) w.spawnParticle(Particle.SPELL_MOB, b.getLocation().add(random.nextDouble(), 0.4, random.nextDouble()), 0, 0.46, 0.28, 0.55, 1);
            else if (c == Blocks.HYPHAE && b.getRelative(0, 1, 0).getType() == Material.AIR && plugin.gen.biomes.nex(b.getX(), b.getZ()) == Biomes.Nex.FUNGI_FOREST)
                w.spawnParticle(Particle.TOWN_AURA, b.getLocation().add(random.nextDouble(), 1.1, random.nextDouble()), 1, 0, 0, 0, 0);
        }
    }

    // ---- rime blocks ---------------------------------------------------------------------------------------------------
    private void rimeTick(World w) {
        if (rime.isEmpty()) return;
        for (long p : rime) {
            int x = ux(p), y = uy(p), z = uz(p);
            if (!w.isChunkLoaded(x >> 4, z >> 4)) continue;
            Block b = w.getBlockAt(x, y, z);
            if (b.getTypeId() != Blocks.RIME_BLOCK_ID) continue;
            for (int dx = -1; dx <= 1; dx++) for (int dy = -1; dy <= 1; dy++) for (int dz = -1; dz <= 1; dz++) {
                Block n = b.getRelative(dx, dy, dz);
                if (n.getType() == Material.STATIONARY_WATER && n.getData() == 0) { n.setType(Material.ICE); rimeFreezes++; }
                else if (n.getType() == Material.STATIONARY_LAVA && n.getData() == 0) { n.setType(Material.MAGMA); rimeFreezes++; }
            }
            for (Entity n : w.getNearbyEntities(b.getLocation().add(0.5, 0.5, 0.5), 1.6, 1.6, 1.6)) {
                if (n instanceof LivingEntity && !(n instanceof Player) && !plugin.effects.has(n, Effects.Kind.FROZEN)) {
                    plugin.effects.apply((LivingEntity) n, Effects.Kind.FROZEN, 300);
                    rimeFreezes++;
                }
            }
        }
    }

    /** Registers a rime block placed by something other than a player (admin/test tools). */
    void registerRime(Block b) { rime.add(pack(b.getX(), b.getY(), b.getZ())); saveRime(); }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent e) {
        String id = Items.id(e.getItemInHand());
        if (id == null) return;
        Block b = e.getBlockPlaced();
        if (id.equals("rime_block")) { rime.add(pack(b.getX(), b.getY(), b.getZ())); saveRime(); return; }
        if (id.equals("amethyst_block")) golem(b, e.getPlayer());
    }

    /** NetherEx Gold Golem: an amethyst block on top of a T of gold blocks. */
    private void golem(Block head, Player p) {
        Block body = head.getRelative(0, -1, 0), base = body.getRelative(0, -1, 0);
        if (body.getType() != Material.GOLD_BLOCK || base.getType() != Material.GOLD_BLOCK) return;
        BlockFace[][] arms = {{BlockFace.EAST, BlockFace.WEST}, {BlockFace.NORTH, BlockFace.SOUTH}};
        for (BlockFace[] a : arms) {
            if (body.getRelative(a[0]).getType() == Material.GOLD_BLOCK && body.getRelative(a[1]).getType() == Material.GOLD_BLOCK) {
                head.setType(Material.AIR); body.setType(Material.AIR); base.setType(Material.AIR);
                body.getRelative(a[0]).setType(Material.AIR); body.getRelative(a[1]).setType(Material.AIR);
                LivingEntity g = plugin.mobs.spawn("gold_golem", base.getLocation().add(0.5, 0.05, 0.5), false);
                if (g instanceof org.bukkit.entity.IronGolem) ((org.bukkit.entity.IronGolem) g).setPlayerCreated(true);
                base.getWorld().spawnParticle(Particle.SNOWBALL, base.getLocation().add(0.5, 1.5, 0.5), 120, 0.5, 1, 0.5, 0);
                plugin.mobs.ability("gold_golem_built");
                return;
            }
        }
    }

    // ---- breaking: ores, rime blocks, flora drops, hammer --------------------------------------------------------------
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent e) {
        Block b = e.getBlock();
        Player p = e.getPlayer();
        long key = pack(b.getX(), b.getY(), b.getZ());
        if (b.getTypeId() == Blocks.RIME_BLOCK_ID && rime.remove(key)) {
            saveRime();
            e.setDropItems(false);
            if (p.getGameMode() != GameMode.CREATIVE) b.getWorld().dropItemNaturally(b.getLocation().add(0.5, 0.5, 0.5), Items.create("rime_block", 1));
            return;
        }
        if (plugin.registry != null && b.getTypeId() == Blocks.STATUE >> 4 && plugin.registry.at(b.getX(), b.getY(), b.getZ(), "statue") != null) return;
        ItemStack tool = p.getInventory().getItemInMainHand();
        String held = Items.id(tool);
        if (held != null && held.endsWith("_amedian_hammer") && !hammering) hammer(b, p);   // the hammer works in every world
        if (!plugin.isNether(b.getWorld())) return;
        int id = b.getTypeId(), data = b.getData();
        int fortune = tool == null ? 0 : tool.getEnchantmentLevel(org.bukkit.enchantments.Enchantment.LOOT_BONUS_BLOCKS);
        String drop = null;
        int exp = 0;
        boolean ore = false;
        if (id == Blocks.AMETHYST_ORE_ID) { drop = "amethyst_crystal"; exp = 2 + random.nextInt(4); ore = true; }
        else if (id == Blocks.RIME_ORE_ID) { drop = "rime_crystal"; exp = 2 + random.nextInt(4); ore = true; }
        else if (id == Blocks.CINCINNASITE_ORE_ID || id == Material.GLOWING_REDSTONE_ORE.getId()) { drop = "cincinnasite"; ore = true; }
        else if (id == Material.QUARTZ_ORE.getId()) ore = true;
        if (drop != null) {
            e.setDropItems(false);
            e.setExpToDrop(exp);
            if (p.getGameMode() != GameMode.CREATIVE && isPickaxe(tool)) {
                int n = Math.max(0, random.nextInt(fortune + 2) - 1) + 1;
                b.getWorld().dropItemNaturally(b.getLocation().add(0.5, 0.5, 0.5), Items.create(drop, n));
                oreDrops++;
            }
        } else if ((id << 4 | data) == Blocks.BLACK_APPLE) {
            e.setDropItems(false);
            b.getWorld().dropItemNaturally(b.getLocation().add(0.5, 0.5, 0.5), Items.create("black_apple", 1));
        } else if ((id << 4 | data) == Blocks.INK_BUSH) {
            e.setDropItems(false);
            b.getWorld().dropItemNaturally(b.getLocation().add(0.5, 0.5, 0.5), new ItemStack(Material.INK_SACK, 1 + random.nextInt(3)));
        } else if (id == Blocks.NETHER_REED >> 4) {
            e.setDropItems(false);
            b.getWorld().dropItemNaturally(b.getLocation().add(0.5, 0.5, 0.5), Items.create("nether_reed", 1));
        } else if (id == Blocks.HUGE_BROWN && (data == 10 || data == 15)) {
            e.setDropItems(false);
            b.getWorld().dropItemNaturally(b.getLocation().add(0.5, 0.5, 0.5), Items.create("stalagnate", 1));
        }
        if (ore && p.getGameMode() != GameMode.CREATIVE && random.nextInt(64) == 0) {
            plugin.mobs.spawn("nethermite", b.getLocation().add(0.5, 0, 0.5), false);
            nethermites++;
        }
    }

    /** Explosions in the Nether never yield the vanilla loot of an ore stand-in (no emeralds, lapis or redstone). */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityExplode(org.bukkit.event.entity.EntityExplodeEvent e) { explodedOres(e.blockList()); }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockExplode(org.bukkit.event.block.BlockExplodeEvent e) { explodedOres(e.blockList()); }

    private void explodedOres(List<Block> blocks) {
        for (java.util.Iterator<Block> it = blocks.iterator(); it.hasNext(); ) {
            Block b = it.next();
            if (!plugin.isNether(b.getWorld())) return;
            int id = b.getTypeId();
            String drop = id == Blocks.AMETHYST_ORE_ID ? "amethyst_crystal" : id == Blocks.RIME_ORE_ID ? "rime_crystal"
                : (id == Blocks.CINCINNASITE_ORE_ID || id == Material.GLOWING_REDSTONE_ORE.getId()) ? "cincinnasite" : null;
            if (drop == null) continue;
            it.remove();
            b.setType(Material.AIR, false);
            b.getWorld().dropItemNaturally(b.getLocation().add(0.5, 0.5, 0.5), Items.create(drop, 1));
        }
    }

    private boolean hammering;
    private void hammer(Block center, Player p) {
        hammering = true;
        try {
            float pitch = p.getLocation().getPitch();
            BlockFace face = Math.abs(pitch) > 50 ? BlockFace.UP : facing(p);
            for (int a = -1; a <= 1; a++) for (int c = -1; c <= 1; c++) {
                if (a == 0 && c == 0) continue;
                Block b = face == BlockFace.UP ? center.getRelative(a, 0, c)
                    : (face == BlockFace.NORTH || face == BlockFace.SOUTH) ? center.getRelative(a, c, 0) : center.getRelative(0, c, a);
                Material m = b.getType();
                if (m == Material.AIR || m == Material.BEDROCK || m == Material.OBSIDIAN || m == Material.ENDER_PORTAL_FRAME || m == Material.BARRIER
                    || m == Material.MOB_SPAWNER || b.getState() instanceof org.bukkit.inventory.InventoryHolder) continue;
                if (!m.isSolid()) continue;
                BlockBreakEvent ev = new BlockBreakEvent(b, p);
                Bukkit.getPluginManager().callEvent(ev);
                if (!ev.isCancelled()) b.breakNaturally(p.getInventory().getItemInMainHand());
            }
        } finally { hammering = false; }
    }

    static BlockFace facing(Player p) {
        int i = Math.round(p.getLocation().getYaw() / 90f) & 3;
        return new BlockFace[]{BlockFace.SOUTH, BlockFace.WEST, BlockFace.NORTH, BlockFace.EAST}[i];
    }

    private static boolean isPickaxe(ItemStack s) { return s != null && s.getType().name().endsWith("_PICKAXE"); }

    /** Tool wear: Amedian lasts 2250 uses on a diamond base (1561); Cincinnasite 512 on iron (250) / 2048 on diamond. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onWear(PlayerItemDamageEvent e) {
        String id = Items.id(e.getItem());
        if (id == null) return;
        double keep;
        if (id.contains("_amedian_")) keep = 1 - 1561.0 / 2250;
        else if (id.equals("cincinnasite_pickaxe") || id.equals("cincinnasite_axe")) keep = 1 - 250.0 / 512;
        else if (id.endsWith("_diamond") && id.startsWith("cincinnasite")) keep = 1 - 1561.0 / 2048;
        else return;
        if (random.nextDouble() < keep) e.setCancelled(true);
    }

    // ---- interactions: foods, mirror, statues, wither dust, rime and steel, tears ----------------------------------
    @EventHandler(priority = EventPriority.HIGH)
    public void onConsume(PlayerItemConsumeEvent e) {
        String id = Items.id(e.getItem());
        Player p = e.getPlayer();
        if (id == null) return;
        int food = -1; float sat = 0; boolean bowl = false;
        switch (id) {
            case "ghast_meat_raw": food = 4; sat = 4f; p.addPotionEffect(new PotionEffect(PotionEffectType.LEVITATION, 100, 1), true); break;
            case "ghast_meat_cooked": food = 8; sat = 16f; p.addPotionEffect(new PotionEffect(PotionEffectType.LEVITATION, 200, 1), true); break;
            case "congealed_magma_cream": food = 1; sat = 0.6f; p.addPotionEffect(new PotionEffect(PotionEffectType.FIRE_RESISTANCE, 200, 1), true); break;
            case "black_apple": food = 6; sat = 6f; p.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 40, 3), true); break;
            case "enoki_mushroom": food = 3; sat = 4.2f; break;
            case "brown_elder_mushroom": food = 20 - p.getFoodLevel(); sat = 0; p.damage(4 + random.nextInt(5)); break;
            case "red_elder_mushroom": p.setHealth(p.getMaxHealth()); p.setFoodLevel(0); food = 0; break;
            case "stalagnate_bowl_wart": food = 4; sat = 4.8f; bowl = true; break;
            case "stalagnate_bowl_mushroom": food = 6; sat = 7.2f; bowl = true; break;
            case "stalagnate_bowl_apple": food = 8; sat = 9.6f; bowl = true; break;
            case "potion_freezing": case "potion_frigid_health": case "potion_dispersal": case "potion_sorrow": {
                Effects.Kind k = id.equals("potion_freezing") ? Effects.Kind.FROZEN : id.equals("potion_frigid_health") ? Effects.Kind.FROSTBITTEN
                    : id.equals("potion_dispersal") ? Effects.Kind.INFESTED : Effects.Kind.CRYING;
                if (k == Effects.Kind.FROZEN && !plugin.effects.canFreeze(p)) break;
                plugin.effects.apply(p, k, 600);
                return;
            }
            default: return;
        }
        if (food < 0) return;
        e.setCancelled(true);
        ItemStack hand = e.getItem().clone();
        boolean main = Items.is(p.getInventory().getItemInMainHand(), id);
        ItemStack slot = main ? p.getInventory().getItemInMainHand() : p.getInventory().getItemInOffHand();
        if (p.getGameMode() != GameMode.CREATIVE) {
            if (slot.getAmount() > 1) slot.setAmount(slot.getAmount() - 1); else slot = bowl ? Items.create("stalagnate_bowl", 1) : null;
            if (main) p.getInventory().setItemInMainHand(slot); else p.getInventory().setItemInOffHand(slot);
            if (bowl && hand.getAmount() > 1) p.getInventory().addItem(Items.create("stalagnate_bowl", 1));
        }
        p.setFoodLevel(Math.min(20, p.getFoodLevel() + food));
        p.setSaturation(Math.min(p.getFoodLevel(), p.getSaturation() + sat));
        p.getWorld().playSound(p.getLocation(), Sound.ENTITY_PLAYER_BURP, 0.5f, 1f);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onUse(PlayerInteractEvent e) {
        if (e.getAction() != Action.RIGHT_CLICK_BLOCK && e.getAction() != Action.RIGHT_CLICK_AIR) return;
        Player p = e.getPlayer();
        ItemStack item = e.getItem();
        String id = Items.id(item);
        Block b = e.getClickedBlock();
        if (b != null && b.getTypeId() == Blocks.STATUE >> 4 && plugin.registry != null && plugin.registry.at(b.getX(), b.getY(), b.getZ(), "statue") != null) {
            e.setCancelled(true);
            if (item == null || item.getType() != Material.GLOWSTONE_DUST) { Effects.bar(p, ChatColor.RED + "" + ChatColor.BOLD + "You must hold Glowstone Dust to set your spawn point here"); return; }
            if (p.getGameMode() != GameMode.CREATIVE) item.setAmount(item.getAmount() - 1);
            BlockFace f = facing(p).getOppositeFace();
            Location spawn = b.getRelative(f).getLocation().add(0.5, 0, 0.5);
            p.setBedSpawnLocation(spawn, true);
            b.getWorld().spawnParticle(Particle.REDSTONE, b.getLocation().add(0.5, 1.5, 0.5), 100, 0.5, 0.5, 0.5, 0);
            p.getWorld().playSound(b.getLocation(), Sound.ITEM_TOTEM_USE, 0.7f, 1f);
            Effects.bar(p, ChatColor.DARK_GREEN + "" + ChatColor.BOLD + "Your spawn point was set here");
            statues++;
            return;
        }
        if (id == null) return;
        switch (id) {
            case "dull_mirror":
                if (!p.isSneaking()) return;
                e.setCancelled(true);
                mirror(p, item);
                break;
            case "wither_dust":
                if (b != null && (b.getType() == Material.BROWN_MUSHROOM || b.getType() == Material.RED_MUSHROOM)) {
                    e.setCancelled(true);
                    boolean grown = plugin.isNether(b.getWorld()) && growElder(b);
                    if (grown) {
                        if (p.getGameMode() != GameMode.CREATIVE) item.setAmount(item.getAmount() - 1);
                        b.getWorld().spawnParticle(Particle.VILLAGER_HAPPY, b.getLocation().add(0.5, 0.5, 0.5), 12, 0.4, 0.4, 0.4, 0);
                    }
                    plugin.getLogger().info("NETHER_WITHER_DUST result=" + (grown ? "elder_mushroom" : plugin.isNether(b.getWorld()) ? "no_room" : "not_nether"));
                }
                break;
            default:
        }
    }

    /** Wither Dust on a small mushroom in the Nether grows a huge Elder Mushroom (vanilla huge-mushroom shape). */
    private boolean growElder(Block b) {
        boolean brown = b.getType() == Material.BROWN_MUSHROOM;
        int h = 4 + random.nextInt(3);
        for (int y = 1; y <= h + 1; y++) for (int dx = -3; dx <= 3; dx++) for (int dz = -3; dz <= 3; dz++) {
            if (y <= 3 && (dx != 0 || dz != 0)) continue;
            if (b.getRelative(dx, y, dz).getType() != Material.AIR) return false;
        }
        int capId = brown ? Blocks.ELDER_CAP_BROWN_ID : Blocks.ELDER_CAP_RED_ID;
        for (int y = 0; y < h; y++) b.getRelative(0, y, 0).setTypeIdAndData(Blocks.ELDER_STEM >> 4, (byte) (Blocks.ELDER_STEM & 15), false);
        int top = h;
        if (brown) {
            for (int dx = -3; dx <= 3; dx++) for (int dz = -3; dz <= 3; dz++) {
                if (Math.abs(dx) == 3 && Math.abs(dz) == 3) continue;
                b.getRelative(dx, top, dz).setTypeIdAndData(capId, (byte) 14, false);
            }
        } else {
            for (int y = top - 3; y <= top; y++) {
                int r = y < top ? 2 : 1;
                for (int dx = -r; dx <= r; dx++) for (int dz = -r; dz <= r; dz++) {
                    if (y < top && Math.abs(dx) < 2 && Math.abs(dz) < 2) continue;
                    b.getRelative(dx, y, dz).setTypeIdAndData(capId, (byte) 14, false);
                }
            }
        }
        plugin.mobs.ability("elder_mushroom_grown");
        return true;
    }

    private void mirror(Player p, ItemStack item) {
        net.minecraft.server.v1_12_R1.ItemStack n = CraftItemStack.asNMSCopy(item);
        net.minecraft.server.v1_12_R1.NBTTagCompound tag = n.getTag().getCompound("JasprNether");
        ItemMeta meta;
        if (!tag.hasKey("sx")) {
            Location l = p.getLocation();
            tag.setString("sw", l.getWorld().getName());
            tag.setInt("sx", l.getBlockX()); tag.setInt("sy", l.getBlockY()); tag.setInt("sz", l.getBlockZ());
            if (!tag.hasKey("charges")) tag.setInt("charges", 5);
            p.setBedSpawnLocation(l, true);
            p.sendMessage(ChatColor.BLUE + "Spawn point set to: " + l.getBlockX() + " " + l.getBlockY() + " " + l.getBlockZ());
            plugin.getLogger().info("NETHER_MIRROR anchored world=" + l.getWorld().getName());
            p.getWorld().playSound(l, Sound.BLOCK_PORTAL_AMBIENT, 0.5f, 1f);
        } else {
            tag.remove("sw"); tag.remove("sx"); tag.remove("sy"); tag.remove("sz");
            p.sendMessage(ChatColor.RED + "Spawn point removed");
            p.getWorld().playSound(p.getLocation(), Sound.BLOCK_FIRE_AMBIENT, 1f, 1f);
        }
        n.getTag().set("JasprNether", tag);
        ItemStack out = CraftItemStack.asBukkitCopy(n);
        meta = out.getItemMeta();
        List<String> lore = new ArrayList<>();
        lore.add(ChatColor.GRAY + (tag.hasKey("sx") ? "Anchored: " + tag.getInt("sx") + " " + tag.getInt("sy") + " " + tag.getInt("sz") : "Not anchored"));
        lore.add(ChatColor.GRAY + "Anchored respawns left: " + (tag.hasKey("charges") ? tag.getInt("charges") : 5));
        lore.add(ChatColor.DARK_GRAY + "NetherEx");
        meta.setLore(lore);
        out.setItemMeta(meta);
        p.getInventory().setItemInMainHand(out);
    }

    private final java.util.Map<java.util.UUID, ItemStack> keptMirrors = new java.util.HashMap<>();

    @EventHandler(priority = EventPriority.HIGH)
    public void onDeath(PlayerDeathEvent e) {
        for (java.util.Iterator<ItemStack> it = e.getDrops().iterator(); it.hasNext(); ) {
            ItemStack s = it.next();
            if (Items.is(s, "dull_mirror")) { it.remove(); keptMirrors.put(e.getEntity().getUniqueId(), s); break; }
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onRespawn(PlayerRespawnEvent e) {
        ItemStack m = keptMirrors.remove(e.getPlayer().getUniqueId());
        if (m == null) return;
        net.minecraft.server.v1_12_R1.ItemStack n = CraftItemStack.asNMSCopy(m);
        net.minecraft.server.v1_12_R1.NBTTagCompound tag = n.getTag().getCompound("JasprNether");
        if (tag.hasKey("sx")) {
            int charges = tag.hasKey("charges") ? tag.getInt("charges") : 5;
            World w = Bukkit.getWorld(tag.getString("sw"));
            if (charges > 0 && w != null) {
                e.setRespawnLocation(new Location(w, tag.getInt("sx") + 0.5, tag.getInt("sy"), tag.getInt("sz") + 0.5));
                tag.setInt("charges", charges - 1);
                plugin.getLogger().info("NETHER_MIRROR respawn world=" + w.getName() + " chargesLeft=" + (charges - 1));
                if (charges - 1 <= 0) { tag.remove("sw"); tag.remove("sx"); tag.remove("sy"); tag.remove("sz"); }
            }
        }
        n.getTag().set("JasprNether", tag);
        ItemStack out = CraftItemStack.asBukkitCopy(n);
        Bukkit.getScheduler().runTask(plugin, () -> e.getPlayer().getInventory().addItem(out));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onIgnite(BlockIgniteEvent e) {
        if (e.getCause() != BlockIgniteEvent.IgniteCause.FLINT_AND_STEEL || e.getPlayer() == null || plugin.registry == null) return;
        if (!plugin.isNether(e.getBlock().getWorld())) return;
        if (Items.is(e.getPlayer().getInventory().getItemInMainHand(), "rime_and_steel")) {
            Block b = e.getBlock();
            plugin.registry.add("bluefire", "rime_and_steel", b.getX(), b.getY(), b.getZ(), b.getX(), b.getY(), b.getZ());
        }
    }

    /** Ghast Queen Tears on a weakened Zombie Pigman: 3-5 minutes later it becomes a Pigtificate. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityUse(PlayerInteractEntityEvent e) {
        if (!(e.getRightClicked() instanceof PigZombie)) return;
        Player p = e.getPlayer();
        ItemStack hand = p.getInventory().getItemInMainHand();
        PigZombie z = (PigZombie) e.getRightClicked();
        if (!Items.is(hand, "ghast_queen_tear") || !z.hasPotionEffect(PotionEffectType.WEAKNESS)) return;
        e.setCancelled(true);
        if (p.getGameMode() != GameMode.CREATIVE) { hand.setAmount(hand.getAmount() - 1); p.getInventory().setItemInMainHand(hand.getAmount() <= 0 ? null : hand); }
        int ticks = 3600 + random.nextInt(2401);
        z.removePotionEffect(PotionEffectType.WEAKNESS);
        z.addPotionEffect(new PotionEffect(PotionEffectType.INCREASE_DAMAGE, ticks, 0), true);
        z.addScoreboardTag("jn_cure_" + (plugin.getServer().getWorlds().get(0).getFullTime() + ticks));
        z.setRemoveWhenFarAway(false);
        curing.add(z);
        z.getWorld().playSound(z.getLocation(), Sound.ENTITY_ZOMBIE_VILLAGER_CURE, 1f, 1f);
    }

    private void cureTick() {
        long now = plugin.getServer().getWorlds().get(0).getFullTime();
        for (java.util.Iterator<LivingEntity> it = curing.iterator(); it.hasNext(); ) {
            LivingEntity z = it.next();
            if (!z.isValid()) { it.remove(); continue; }
            for (String tag : z.getScoreboardTags()) if (tag.startsWith("jn_cure_") && Long.parseLong(tag.substring(8)) <= now) {
                Location l = z.getLocation();
                z.remove();
                it.remove();
                LivingEntity v = plugin.mobs.spawn("pigtificate", l, false);
                if (v != null) v.addPotionEffect(new PotionEffect(PotionEffectType.CONFUSION, 200, 0));
                plugin.mobs.ability("pigman_cured");
                break;
            }
        }
    }

    // ---- armour sets -------------------------------------------------------------------------------------------------
    boolean fullSet(Player p, String set) {
        for (ItemStack s : p.getInventory().getArmorContents()) if (!Items.isArmorOf(Items.id(s), set)) return false;
        return true;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onFire(EntityDamageEvent e) {
        if (!(e.getEntity() instanceof Player)) return;
        EntityDamageEvent.DamageCause c = e.getCause();
        boolean fire = c == EntityDamageEvent.DamageCause.FIRE || c == EntityDamageEvent.DamageCause.FIRE_TICK || c == EntityDamageEvent.DamageCause.LAVA
            || c == EntityDamageEvent.DamageCause.HOT_FLOOR;
        if (fire && fullSet((Player) e.getEntity(), "orange_salamander_hide")) { e.setCancelled(true); e.getEntity().setFireTicks(0); }
    }
}
