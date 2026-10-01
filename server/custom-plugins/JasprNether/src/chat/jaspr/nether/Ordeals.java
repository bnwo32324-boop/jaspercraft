package chat.jaspr.nether;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.FallingBlock;
import org.bukkit.entity.Player;
import org.bukkit.entity.SmallFireball;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

/**
 * The traps, puzzles and seals of the colossal structures and the Endless Catacombs (owner, 2026-10-01: "Add loot,
 * enemies, different dangers, puzzles, and traps"). They are not stored anywhere: each structure's design lays them
 * out from its plan, in world coordinates, and this class brings them to life around the players:
 * <ul>
 *   <li>traps: arrows from slits in the walls, floors that fall away over pits, rubble from the ceiling, flame vents on a
 *       rhythm, poison gas in tombs, fireball cannons on the walls, and floors of tiles where only some are safe;</li>
 *   <li>puzzles: levers or braziers to be set in an order told elsewhere (wrong, and the room strikes back; right, and a
 *       niche or a gate opens, and the reward is handed over);</li>
 *   <li>seals: doors that part for one who carries every key (the jars, seals and keys the mini-bosses drop; never
 *       used up), or that open when a boss falls.</li>
 * </ul>
 * Opened seals close again after a while (and on shutdown), never on a player. Nothing here is night vision,
 * full-bright or glowing (owner rule). Work is bounded: only ordeals within reach of a player are looked at.
 */
final class Ordeals implements Listener {
    enum Type { ARROWS, COLLAPSE, RUBBLE, FLAMES, GAS, CANNON, PATH, LEVERS, BRAZIERS, KEYSEAL, BOSSSEAL }

    /** One trap, puzzle or seal: its zone (world, inclusive) and what it needs. */
    static final class Ordeal {
        final Type type; final String id;
        final int x1, y1, z1, x2, y2, z2;
        int[] emit;          // arrows: the wall box the arrows come from; cannon: the gun's position
        int block;           // collapse/path: the floor block; rubble: what falls; seals: what the door is made of
        int period;          // flames: the rhythm in ticks
        int[][] parts;       // levers/braziers: their positions
        int[] order;         // the order the parts must be set in
        int[] seal;          // the box a puzzle opens
        int[] strike;        // where a wrong answer strikes
        String[] keys;       // a key seal's keys (item ids)
        String boss;         // a boss seal's boss
        String reward;       // a puzzle's reward (item id), handed to whoever solves it
        int salt;            // path: which tiles are safe
        Ordeal(Type type, String id, int[] box) {
            this.type = type; this.id = id;
            x1 = box[0]; y1 = box[1]; z1 = box[2]; x2 = box[3]; y2 = box[4]; z2 = box[5];
        }
        boolean contains(double x, double y, double z) { return x >= x1 && x < x2 + 1 && y >= y1 && y < y2 + 1 && z >= z1 && z < z2 + 1; }
        boolean near(int x, int z, int r) { return x >= x1 - r && x <= x2 + r && z >= z1 - r && z <= z2 + r; }
        String key() { return type + ":" + id + "@" + x1 + "," + y1 + "," + z1; }
    }

    /** A structure's local frame, as {@link Draw.Frame} turns it, for laying ordeals out in world coordinates. */
    static final class Frame {
        final int ox, oz, rot;
        Frame(int ox, int oz, int rot) { this.ox = ox; this.oz = oz; this.rot = rot & 3; }
        int x(int u, int v) { switch (rot) { case 1: return ox - v; case 2: return ox - u; case 3: return ox + v; default: return ox + u; } }
        int z(int u, int v) { switch (rot) { case 1: return oz + u; case 2: return oz - v; case 3: return oz - u; default: return oz + v; } }
        int[] at(int u, int y, int v) { return new int[]{x(u, v), y, z(u, v)}; }
        int[] box(int u0, int y0, int v0, int u1, int y1, int v1) {
            int xa = x(u0, v0), za = z(u0, v0), xb = x(u1, v1), zb = z(u1, v1);
            return new int[]{Math.min(xa, xb), Math.min(y0, y1), Math.min(za, zb), Math.max(xa, xb), Math.max(y0, y1), Math.max(za, zb)};
        }
    }

    // ---- the kinds, as the designs lay them out --------------------------------------------------------------------------
    static Ordeal arrows(int[] zone, int[] walls) { Ordeal o = new Ordeal(Type.ARROWS, "arrows", zone); o.emit = walls; return o; }
    static Ordeal collapse(int[] floor, int block) { Ordeal o = new Ordeal(Type.COLLAPSE, "collapse", floor); o.block = block; return o; }
    static Ordeal rubble(int[] zone, int block) { Ordeal o = new Ordeal(Type.RUBBLE, "rubble", zone); o.block = block; return o; }
    static Ordeal flames(int[] zone, int period) { Ordeal o = new Ordeal(Type.FLAMES, "flames", zone); o.period = Math.max(40, period); return o; }
    static Ordeal gas(int[] zone) { return new Ordeal(Type.GAS, "gas", zone); }
    static Ordeal cannon(int[] zone, int[] gun) { Ordeal o = new Ordeal(Type.CANNON, "cannon", zone); o.emit = gun; return o; }
    /** A floor of tiles (2x2) where the safe ones are those {@link #safeTile} picks; the rest fall away. */
    static Ordeal path(int[] floor, int block, int salt) { Ordeal o = new Ordeal(Type.PATH, "path", floor); o.block = block; o.salt = salt; return o; }
    static Ordeal levers(String id, int[] room, int[][] levers, int[] order, int[] seal, int sealBlock, String reward, int[] strike) {
        Ordeal o = new Ordeal(Type.LEVERS, id, room);
        o.parts = levers; o.order = order; o.seal = seal; o.block = sealBlock; o.reward = reward; o.strike = strike;
        return o;
    }
    static Ordeal braziers(String id, int[] room, int[][] braziers, int[] order, int[] seal, int sealBlock, String reward, int[] strike) {
        Ordeal o = new Ordeal(Type.BRAZIERS, id, room);
        o.parts = braziers; o.order = order; o.seal = seal; o.block = sealBlock; o.reward = reward; o.strike = strike;
        return o;
    }
    static Ordeal keySeal(String id, int[] door, int block, String... keys) { Ordeal o = new Ordeal(Type.KEYSEAL, id, door); o.block = block; o.keys = keys; return o; }
    static Ordeal bossSeal(String id, int[] door, int block, String boss) { Ordeal o = new Ordeal(Type.BOSSSEAL, id, door); o.block = block; o.boss = boss; return o; }

    /** Whether tile (tx, tz) of a path is safe (deterministic, the same in drawing and here). */
    static boolean safeTile(int tx, int tz, int salt) { return Draw.rnd(tx, 3, tz, salt) < 0.45 || Math.floorMod(tx + tz, 7) == 0; }

    // ---- state ------------------------------------------------------------------------------------------------------------
    private final NetherPlugin plugin;
    private final Random random = new Random();
    private final Map<String, Long> cooldown = new HashMap<>();
    private final Map<String, Integer> step = new HashMap<>();             // puzzle progress
    private final Map<String, Long> puzzleReset = new HashMap<>();
    private final Map<String, Long> rewarded = new HashMap<>();            // player + puzzle -> when last rewarded
    private static final class Opening { final World w; final int[] box; final int block; final long closeAt; Opening(World w, int[] box, int block, long closeAt) { this.w = w; this.box = box; this.block = block; this.closeAt = closeAt; } }
    private final Map<String, Opening> open = new HashMap<>();
    private static final class Hole { final World w; final int x, y, z, block; final long restoreAt; Hole(World w, int x, int y, int z, int block, long restoreAt) { this.w = w; this.x = x; this.y = y; this.z = z; this.block = block; this.restoreAt = restoreAt; } }
    private final List<Hole> holes = new ArrayList<>();
    private long now;
    long arrows, collapses, rubbles, flames, gassed, cannons, solved, failed, sealsOpened, refused, tilesFallen;

    Ordeals(NetherPlugin plugin) { this.plugin = plugin; }

    String describe() {
        return "arrows=" + arrows + " collapses=" + collapses + " rubble=" + rubbles + " flames=" + flames + " gassed=" + gassed + " cannons=" + cannons
            + " tilesFallen=" + tilesFallen + " solved=" + solved + " failed=" + failed + " sealsOpened=" + sealsOpened + " refused=" + refused + " open=" + open.size();
    }

    /** The ordeals within r blocks of (x, z): the built colossus there and the Catacombs' rooms around. */
    List<Ordeal> near(int x, int z, int r) {
        List<Ordeal> out = new ArrayList<>();
        if (plugin.gen == null) return out;
        for (Colossi.Site s : plugin.gen.colossi.touching(x - r, z - r, x + r, z + r)) {
            if (!plugin.gen.colossusBuilt(s)) continue;
            for (Ordeal o : Colossi.design(s.kind).ordeals(s)) if (o.near(x, z, r)) out.add(o);
        }
        if (plugin.gen.depths != null) out.addAll(plugin.gen.depths.ordealsNear(x, z, r));
        return out;
    }

    void tick(long ticks) {
        now = ticks;
        World w = plugin.nether;
        if (w == null || plugin.gen == null) return;
        if ((ticks % 5) == 0) {
            for (Player p : w.getPlayers()) {
                if (p.isDead() || p.getGameMode() == GameMode.CREATIVE || p.getGameMode() == GameMode.SPECTATOR) continue;
                Location l = p.getLocation();
                for (Ordeal o : near(l.getBlockX(), l.getBlockZ(), 24)) {
                    try { trigger(w, o, p, l); } catch (RuntimeException ex) {
                        if (plugin.warnings++ < 40) plugin.getLogger().warning("NETHER_ORDEAL_FAILED type=" + o.type + " id=" + o.id + " reason=" + ex.getClass().getSimpleName() + " at=" + NetherPlugin.where(ex));
                    }
                }
            }
        }
        if ((ticks % 10) == 0) { restore(); closeSeals(false); resetPuzzles(w); }
    }

    private boolean ready(Ordeal o, String what, int ticks) {
        String k = o.key() + what;
        Long t = cooldown.get(k);
        if (t != null && now < t) return false;
        cooldown.put(k, now + ticks);
        if (cooldown.size() > 20000) cooldown.clear();
        return true;
    }

    private void trigger(World w, Ordeal o, Player p, Location l) {
        boolean in = o.contains(l.getX(), l.getY(), l.getZ());
        switch (o.type) {
            case ARROWS: if (in && ready(o, "", 50)) volley(w, o, p); break;
            case COLLAPSE: if (o.contains(l.getX(), l.getY() - 0.5, l.getZ()) || (in && l.getBlockY() - 1 == o.y1)) fall(w, o, p); break;
            case RUBBLE: if (in && ready(o, "", 110)) rubble(w, o, p); break;
            case FLAMES: flameTick(w, o, p, in); break;
            case GAS:
                if (in) {
                    p.addPotionEffect(new PotionEffect(PotionEffectType.POISON, 60, 0, false, true), true);
                    p.addPotionEffect(new PotionEffect(PotionEffectType.SLOW, 40, 0, false, true), true);
                    if (ready(o, "p", 10)) w.spawnParticle(Particle.SPELL_MOB, l.clone().add(0, 1, 0), 0, 0.35, 0.8, 0.25, 1);
                    if (ready(o, "c", 40)) gassed++;
                }
                if (ready(o, "a", 20) && l.distanceSquared(new Location(w, (o.x1 + o.x2) / 2.0, o.y1, (o.z1 + o.z2) / 2.0)) < 18 * 18)
                    for (int i = 0; i < 6; i++) w.spawnParticle(Particle.SPELL_MOB, o.x1 + random.nextDouble() * (o.x2 - o.x1 + 1), o.y1 + random.nextDouble() * 2,
                        o.z1 + random.nextDouble() * (o.z2 - o.z1 + 1), 0, 0.3, 0.75, 0.2, 1);
                break;
            case CANNON: if (in && ready(o, "", 70 + random.nextInt(50))) cannon(w, o, p); break;
            case PATH: if (o.contains(l.getX(), l.getY() - 0.5, l.getZ())) pathTile(w, o, l); break;
            default:
        }
    }

    // ---- traps ------------------------------------------------------------------------------------------------------------
    private void volley(World w, Ordeal o, Player p) {
        int[] e = o.emit;
        Location t = p.getLocation().add(0, 1.1, 0);
        int made = 0;
        for (int i = 0; i < 6; i++) {
            // a slit on one of the two long walls of the emitter box, near the player's place along it
            boolean alongX = (e[3] - e[0]) >= (e[5] - e[2]);
            double ax, az;
            if (alongX) { ax = clamp(t.getX() + random.nextInt(7) - 3, e[0], e[3]); az = random.nextBoolean() ? e[2] + 0.5 : e[5] + 0.5; }
            else { az = clamp(t.getZ() + random.nextInt(7) - 3, e[2], e[5]); ax = random.nextBoolean() ? e[0] + 0.5 : e[3] + 0.5; }
            Location from = new Location(w, ax, Math.max(e[1], Math.min(e[4], t.getBlockY())) + 0.5, az);
            Vector dir = t.toVector().subtract(from.toVector());
            if (dir.lengthSquared() < 1) continue;
            from.add(dir.clone().normalize().multiply(0.6));
            Arrow a = w.spawnArrow(from, dir.normalize(), 1.6f, 6f);
            a.setPickupStatus(Arrow.PickupStatus.DISALLOWED);
            a.setMetadata("jn_trap", new FixedMetadataValue(plugin, Boolean.TRUE));
            made++;
        }
        if (made > 0) { w.playSound(t, Sound.ENTITY_SKELETON_SHOOT, 1.4f, 0.6f); arrows++; }
    }

    private static double clamp(double v, int a, int b) { return Math.max(Math.min(a, b) + 0.5, Math.min(Math.max(a, b) + 0.5, v)); }

    /** The floor zone falls away (its blocks of the floor material) for five seconds, then comes back. */
    private void fall(World w, Ordeal o, Player p) {
        if (!ready(o, "", 140)) return;
        int n = 0;
        for (int x = o.x1; x <= o.x2; x++) for (int z = o.z1; z <= o.z2; z++) for (int y = o.y1; y <= o.y2; y++) n += drop(w, x, y, z, o.block, 100);
        if (n > 0) {
            collapses++;
            w.playSound(p.getLocation(), Sound.BLOCK_GRAVEL_BREAK, 1.6f, 0.5f);
            Effects.bar(p, ChatColor.GOLD + "The floor gives way!");
        }
    }

    private int drop(World w, int x, int y, int z, int block, int ticks) {
        if (!w.isChunkLoaded(x >> 4, z >> 4)) return 0;
        Block b = w.getBlockAt(x, y, z);
        if (b.getTypeId() != (block >> 4) || b.getData() != (block & 15)) return 0;
        b.setType(Material.AIR, false);
        holes.add(new Hole(w, x, y, z, block, now + ticks));
        w.spawnParticle(Particle.BLOCK_CRACK, x + 0.5, y + 0.5, z + 0.5, 6, 0.3, 0.2, 0.3, 0, new org.bukkit.material.MaterialData(block >> 4, (byte) (block & 15)));
        return 1;
    }

    private void restore() {
        Iterator<Hole> it = holes.iterator();
        while (it.hasNext()) {
            Hole h = it.next();
            if (now < h.restoreAt) continue;
            if (h.w.isChunkLoaded(h.x >> 4, h.z >> 4)) {
                Block b = h.w.getBlockAt(h.x, h.y, h.z);
                boolean someone = false;          // never close a floor on a player in it, or in the pit beneath it
                for (Player p : h.w.getPlayers()) {
                    Location l = p.getLocation();
                    if (Math.abs(l.getX() - (h.x + 0.5)) < 1.3 && Math.abs(l.getZ() - (h.z + 0.5)) < 1.3 && l.getY() > h.y - 12 && l.getY() < h.y + 1.2) someone = true;
                }
                if (someone) continue;
                if (b.getType() == Material.AIR) b.setTypeIdAndData(h.block >> 4, (byte) (h.block & 15), false);
            }
            it.remove();
        }
    }

    private void rubble(World w, Ordeal o, Player p) {
        Location l = p.getLocation();
        int made = 0;
        for (int i = 0; i < 4; i++) {
            Location at = l.clone().add(random.nextInt(3) - 1 + 0.5, 0, random.nextInt(3) - 1 + 0.5);
            at.setY(Math.min(o.y2 + 0.2, l.getY() + 3.6));
            if (at.getBlock().getType() != Material.AIR) continue;
            @SuppressWarnings("deprecation")
            FallingBlock fb = w.spawnFallingBlock(at, o.block >> 4, (byte) (o.block & 15));
            fb.setDropItem(false);
            fb.setHurtEntities(true);
            fb.setMetadata("jn_trap", new FixedMetadataValue(plugin, Boolean.TRUE));
            made++;
        }
        plugin.mobs.hurt(p, null, 3 * plugin.mobs.dmgMult);
        w.playSound(l, Sound.BLOCK_GRAVEL_BREAK, 1.8f, 0.4f);
        Effects.bar(p, ChatColor.GOLD + "The ceiling is coming down!");
        if (made > 0) rubbles++;
    }

    /** Flame vents: smoke as a warning, then a second and a half of fire, every period. */
    private void flameTick(World w, Ordeal o, Player p, boolean in) {
        long phase = Math.floorMod(now + (o.x1 * 31 + o.z1 * 17), o.period);
        boolean warn = phase >= o.period - 20 && phase < o.period - 5, burning = phase < 30;
        Location c = new Location(w, (o.x1 + o.x2) / 2.0 + 0.5, o.y1 + 0.2, (o.z1 + o.z2) / 2.0 + 0.5);
        if (c.distanceSquared(p.getLocation()) > 24 * 24) return;
        if (ready(o, "fx", 5)) {
            int area = (o.x2 - o.x1 + 1) * (o.z2 - o.z1 + 1), n = Math.min(24, Math.max(3, area / 2));
            for (int i = 0; i < n; i++) {
                double x = o.x1 + random.nextDouble() * (o.x2 - o.x1 + 1), z = o.z1 + random.nextDouble() * (o.z2 - o.z1 + 1);
                if (burning) w.spawnParticle(Particle.FLAME, x, o.y1 + 0.1, z, 0, 0, 0.35, 0, 1);
                else if (warn) w.spawnParticle(Particle.SMOKE_NORMAL, x, o.y1 + 0.1, z, 0, 0, 0.1, 0, 1);
            }
            if (burning && phase < 5) w.playSound(c, Sound.ITEM_FIRECHARGE_USE, 1f, 0.6f);
        }
        if (burning && in && ready(o, "hit", 15)) {
            p.setFireTicks(Math.max(p.getFireTicks(), 60));
            plugin.mobs.hurt(p, null, 2.5 * plugin.mobs.dmgMult);
            flames++;
        }
    }

    private void cannon(World w, Ordeal o, Player p) {
        Location from = new Location(w, o.emit[0] + 0.5, o.emit[1] + 0.5, o.emit[2] + 0.5);
        Location to = p.getEyeLocation();
        Vector dir = to.toVector().subtract(from.toVector());
        if (dir.lengthSquared() < 4 || dir.lengthSquared() > 48 * 48) return;
        // line of sight: the gun must see the player
        Vector step = dir.clone().normalize();
        Location probe = from.clone();
        for (int k = 0; k < (int) dir.length() - 1; k++) {
            probe.add(step);
            if (k > 1 && probe.getBlock().getType().isOccluding()) return;
        }
        SmallFireball b = w.spawn(from.clone().add(step.clone().multiply(1.2)), SmallFireball.class);
        b.setDirection(step.multiply(0.8));
        b.setIsIncendiary(false);
        b.setMetadata("jn_trap", new FixedMetadataValue(plugin, Boolean.TRUE));
        w.playSound(from, Sound.ENTITY_BLAZE_SHOOT, 1.6f, 0.7f);
        cannons++;
    }

    private void pathTile(World w, Ordeal o, Location l) {
        int x = l.getBlockX(), z = l.getBlockZ();
        int tx = Math.floorDiv(x - o.x1, 2), tz = Math.floorDiv(z - o.z1, 2);
        if (safeTile(tx, tz, o.salt)) return;
        String k = "tile" + tx + "," + tz;
        if (!ready(o, k, 120)) return;
        int n = 0;
        for (int dx = 0; dx < 2; dx++) for (int dz = 0; dz < 2; dz++) n += drop(w, o.x1 + tx * 2 + dx, o.y1, o.z1 + tz * 2 + dz, o.block, 120);
        if (n > 0) { tilesFallen++; w.playSound(l, Sound.BLOCK_STONE_BREAK, 1.5f, 0.5f); }
    }

    // ---- puzzles ----------------------------------------------------------------------------------------------------------
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onUse(PlayerInteractEvent e) {
        if (e.getAction() != Action.RIGHT_CLICK_BLOCK || e.getClickedBlock() == null) return;
        Block b = e.getClickedBlock();
        if (!plugin.isNether(b.getWorld()) || plugin.gen == null) return;
        Player p = e.getPlayer();
        if (e.getHand() != EquipmentSlot.HAND) {
            // the off hand: nothing is placed against a seal
            for (Ordeal o : near(b.getX(), b.getZ(), 4))
                if ((o.type == Type.KEYSEAL || o.type == Type.BOSSSEAL) && o.contains(b.getX() + 0.5, b.getY() + 0.5, b.getZ() + 0.5)) { e.setCancelled(true); return; }
            return;
        }
        for (Ordeal o : near(b.getX(), b.getZ(), 4)) {
            switch (o.type) {
                case LEVERS:
                    if (b.getType() != Material.LEVER) break;
                    for (int i = 0; i < o.parts.length; i++) if (same(o.parts[i], b)) {
                        final int idx = i;
                        org.bukkit.Bukkit.getScheduler().runTask(plugin, () -> leverMoved(b.getWorld(), o, idx, p));
                        return;
                    }
                    break;
                case BRAZIERS: {
                    ItemStack hand = e.getItem();
                    if (hand == null || (hand.getType() != Material.FLINT_AND_STEEL && hand.getType() != Material.FIREBALL)) break;
                    for (int i = 0; i < o.parts.length; i++) if (same(o.parts[i], b)) {
                        final int idx = i;
                        org.bukkit.Bukkit.getScheduler().runTask(plugin, () -> brazierLit(b.getWorld(), o, idx, p));
                        return;
                    }
                    break;
                }
                case KEYSEAL: if (o.contains(b.getX() + 0.5, b.getY() + 0.5, b.getZ() + 0.5)) { e.setCancelled(true); keySeal(b.getWorld(), o, p); return; } break;
                case BOSSSEAL:
                    if (o.contains(b.getX() + 0.5, b.getY() + 0.5, b.getZ() + 0.5)) {
                        e.setCancelled(true);
                        Lords.Def d = Lords.DEFS.get(o.boss);
                        Effects.bar(p, ChatColor.GOLD + "Sealed until " + (d == null ? "its keeper" : d.name) + " falls");
                        return;
                    }
                    break;
                default:
            }
        }
    }

    private static boolean same(int[] q, Block b) { return q[0] == b.getX() && q[1] == b.getY() && q[2] == b.getZ(); }

    private void leverMoved(World w, Ordeal o, int idx, Player p) {
        Block b = w.getBlockAt(o.parts[idx][0], o.parts[idx][1], o.parts[idx][2]);
        if (b.getType() != Material.LEVER || (b.getData() & 8) == 0) return;     // only switching one on counts
        advance(w, o, idx, p);
    }

    private void brazierLit(World w, Ordeal o, int idx, Player p) {
        Block b = w.getBlockAt(o.parts[idx][0], o.parts[idx][1] + 1, o.parts[idx][2]);
        if (b.getType() != Material.FIRE) return;
        advance(w, o, idx, p);
    }

    private void advance(World w, Ordeal o, int idx, Player p) {
        String k = o.key();
        int at = step.getOrDefault(k, 0);
        puzzleReset.put(k, now + 20 * 90);
        if (o.order[at] != idx) {
            step.put(k, 0);
            failed++;
            p.sendTitle(ChatColor.DARK_RED + "Wrong", ChatColor.GOLD + "the room strikes back", 5, 40, 10);
            punish(w, o, p);
            org.bukkit.Bukkit.getScheduler().runTaskLater(plugin, () -> resetParts(w, o), 30L);
            plugin.getLogger().info("NETHER_PUZZLE_FAILED id=" + o.id + " step=" + at);
            return;
        }
        at++;
        w.playSound(p.getLocation(), Sound.BLOCK_NOTE_CHIME, 1f, 0.5f + at * 0.25f);
        if (at < o.order.length) { step.put(k, at); Effects.bar(p, ChatColor.YELLOW + "Something stirs (" + at + "/" + o.order.length + ")"); return; }
        step.put(k, 0);
        solved++;
        openSeal(w, o.seal, o.block, k, 20 * 120);
        String rk = p.getUniqueId() + "|" + o.id;
        Long last = rewarded.get(rk);
        if (o.reward != null && (last == null || now - last > 20 * 600)) {
            rewarded.put(rk, now);
            ItemStack r = Items.create(o.reward, 1);
            if (r != null && !p.getInventory().addItem(r).isEmpty()) p.getWorld().dropItemNaturally(p.getLocation(), r);
        }
        p.sendTitle(ChatColor.GOLD + "The way opens", ChatColor.YELLOW + (o.reward == null ? "" : Items.name(o.reward)), 10, 60, 20);
        w.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f);
        plugin.getLogger().info("NETHER_PUZZLE_SOLVED id=" + o.id + " player=" + p.getUniqueId() + " reward=" + o.reward);
        org.bukkit.Bukkit.getScheduler().runTaskLater(plugin, () -> resetParts(w, o), 20L * 120);
    }

    /** A wrong answer: arrows and gas where the design says, and a burst of flame at the player. */
    private void punish(World w, Ordeal o, Player p) {
        if (o.strike != null) {
            Ordeal a = arrows(o.strike, o.strike);
            volley(w, a, p);
        }
        p.addPotionEffect(new PotionEffect(PotionEffectType.POISON, 80, 0, false, true), true);
        p.setFireTicks(Math.max(p.getFireTicks(), 40));
        w.spawnParticle(Particle.FLAME, p.getLocation().add(0, 1, 0), 40, 0.6, 0.8, 0.6, 0.05);
    }

    private void resetParts(World w, Ordeal o) {
        for (int[] q : o.parts) {
            if (!w.isChunkLoaded(q[0] >> 4, q[2] >> 4)) continue;
            Block b = w.getBlockAt(q[0], q[1], q[2]);
            if (o.type == Type.LEVERS && b.getType() == Material.LEVER) b.setData((byte) (b.getData() & 7), true);
            if (o.type == Type.BRAZIERS) { Block f = b.getRelative(0, 1, 0); if (f.getType() == Material.FIRE) f.setType(Material.AIR, false); }
        }
    }

    private void resetPuzzles(World w) {
        Iterator<Map.Entry<String, Long>> it = puzzleReset.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, Long> e = it.next();
            if (now < e.getValue()) continue;
            step.remove(e.getKey());
            it.remove();
        }
    }

    // ---- seals ------------------------------------------------------------------------------------------------------------
    private void keySeal(World w, Ordeal o, Player p) {
        List<String> missing = new ArrayList<>();
        for (String k : o.keys) if (!carries(p, k)) missing.add(Items.name(k));
        if (!missing.isEmpty()) {
            refused++;
            p.sendMessage(ChatColor.GOLD + "The seal will not part. " + ChatColor.GRAY + "Still missing: " + String.join(", ", missing));
            Effects.bar(p, ChatColor.GOLD + "The seal wants " + missing.size() + " more");
            return;
        }
        if (openSeal(w, new int[]{o.x1, o.y1, o.z1, o.x2, o.y2, o.z2}, o.block, o.key(), 20 * 60)) {
            p.sendTitle(ChatColor.GOLD + "The seal parts", ChatColor.YELLOW + "for a minute", 5, 40, 10);
            plugin.getLogger().info("NETHER_SEAL_OPENED id=" + o.id + " by=key player=" + p.getUniqueId());
        }
    }

    static boolean carries(Player p, String id) {
        for (ItemStack s : p.getInventory().getContents()) if (Items.is(s, id)) return true;
        return Items.is(p.getInventory().getItemInOffHand(), id);
    }

    /** A boss fell near (x, z): the seals it kept open for a while. */
    void bossFell(String boss, World w, int x, int z) {
        for (Ordeal o : near(x, z, 96)) if (o.type == Type.BOSSSEAL && o.boss.equals(boss)) {
            if (openSeal(w, new int[]{o.x1, o.y1, o.z1, o.x2, o.y2, o.z2}, o.block, o.key(), 20 * 180))
                plugin.getLogger().info("NETHER_SEAL_OPENED id=" + o.id + " by=boss boss=" + boss);
        }
    }

    private boolean openSeal(World w, int[] box, int block, String key, int ticks) {
        Opening prev = open.get(key);
        if (prev != null) { open.put(key, new Opening(w, box, block, Math.max(prev.closeAt, now + ticks))); return false; }
        for (int x = box[0]; x <= box[3]; x++) for (int y = box[1]; y <= box[4]; y++) for (int z = box[2]; z <= box[5]; z++) {
            if (!w.isChunkLoaded(x >> 4, z >> 4)) continue;
            Block b = w.getBlockAt(x, y, z);
            if (b.getType() != Material.AIR) b.setType(Material.AIR, false);
        }
        open.put(key, new Opening(w, box, block, now + ticks));
        w.playSound(new Location(w, (box[0] + box[3]) / 2.0, box[1] + 1, (box[2] + box[5]) / 2.0), Sound.BLOCK_PISTON_CONTRACT, 2f, 0.5f);
        sealsOpened++;
        return true;
    }

    /** Closes the seals whose time is up (all of them on shutdown), never on a player. */
    void closeSeals(boolean all) {
        Iterator<Map.Entry<String, Opening>> it = open.entrySet().iterator();
        while (it.hasNext()) {
            Opening o = it.next().getValue();
            if (!all && now < o.closeAt) continue;
            int[] b = o.box;
            if (!all) {
                boolean someone = false;
                for (Player p : o.w.getPlayers()) {
                    Location l = p.getLocation();
                    if (l.getX() >= b[0] - 0.3 && l.getX() < b[3] + 1.3 && l.getZ() >= b[2] - 0.3 && l.getZ() < b[5] + 1.3 && l.getY() >= b[1] - 1.8 && l.getY() < b[4] + 1) someone = true;
                }
                if (someone) continue;
            }
            for (int x = b[0]; x <= b[3]; x++) for (int y = b[1]; y <= b[4]; y++) for (int z = b[2]; z <= b[5]; z++) {
                if (!o.w.isChunkLoaded(x >> 4, z >> 4)) continue;
                Block blk = o.w.getBlockAt(x, y, z);
                if (blk.getType() == Material.AIR) blk.setTypeIdAndData(o.block >> 4, (byte) (o.block & 15), false);
            }
            it.remove();
        }
    }

    /** Shutdown: every opened seal shut, every fallen floor back. */
    void shutdown() {
        closeSeals(true);
        for (Hole h : holes) if (h.w.isChunkLoaded(h.x >> 4, h.z >> 4)) {
            Block b = h.w.getBlockAt(h.x, h.y, h.z);
            if (b.getType() == Material.AIR) b.setTypeIdAndData(h.block >> 4, (byte) (h.block & 15), false);
        }
        holes.clear();
    }

    // ---- the ordeals' blocks are not to be taken apart ---------------------------------------------------------------------
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent e) {
        Block b = e.getBlock();
        if (!plugin.isNether(b.getWorld()) || plugin.gen == null) return;
        if (e.getPlayer().getGameMode() == GameMode.CREATIVE) return;
        for (Ordeal o : near(b.getX(), b.getZ(), 2)) {
            boolean part = false;
            if (o.parts != null) for (int[] q : o.parts) if (same(q, b) || (o.type == Type.BRAZIERS && q[0] == b.getX() && q[1] + 1 == b.getY() && q[2] == b.getZ())) part = true;
            boolean seal = (o.type == Type.KEYSEAL || o.type == Type.BOSSSEAL) && o.contains(b.getX() + 0.5, b.getY() + 0.5, b.getZ() + 0.5);
            boolean niche = o.seal != null && b.getX() >= o.seal[0] && b.getX() <= o.seal[3] && b.getY() >= o.seal[1] && b.getY() <= o.seal[4] && b.getZ() >= o.seal[2] && b.getZ() <= o.seal[5];
            if (part || seal || niche) {
                e.setCancelled(true);
                Effects.bar(e.getPlayer(), ChatColor.GOLD + "Ancient work: it will not break");
                return;
            }
        }
    }

    /** No explosion takes a seal, a niche's door or a puzzle's part apart. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onExplode(org.bukkit.event.entity.EntityExplodeEvent e) {
        if (plugin.gen == null || !plugin.isNether(e.getLocation().getWorld())) return;
        List<Ordeal> os = near(e.getLocation().getBlockX(), e.getLocation().getBlockZ(), 12);
        if (os.isEmpty()) return;
        e.blockList().removeIf(b -> guarded(os, b));
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockExplode(org.bukkit.event.block.BlockExplodeEvent e) {
        if (plugin.gen == null || !plugin.isNether(e.getBlock().getWorld())) return;
        List<Ordeal> os = near(e.getBlock().getX(), e.getBlock().getZ(), 12);
        if (os.isEmpty()) return;
        e.blockList().removeIf(b -> guarded(os, b));
    }

    private static boolean guarded(List<Ordeal> os, Block b) {
        for (Ordeal o : os) {
            if (o.parts != null) for (int[] q : o.parts) if (same(q, b) || (o.type == Type.BRAZIERS && q[0] == b.getX() && q[1] + 1 == b.getY() && q[2] == b.getZ())) return true;
            if ((o.type == Type.KEYSEAL || o.type == Type.BOSSSEAL) && o.contains(b.getX() + 0.5, b.getY() + 0.5, b.getZ() + 0.5)) return true;
            if (o.seal != null && b.getX() >= o.seal[0] && b.getX() <= o.seal[3] && b.getY() >= o.seal[1] && b.getY() <= o.seal[4] && b.getZ() >= o.seal[2] && b.getZ() <= o.seal[5]) return true;
        }
        return false;
    }

    /** The traps' rubble never lands as blocks. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onLand(EntityChangeBlockEvent e) {
        if (e.getEntity() instanceof FallingBlock && e.getEntity().hasMetadata("jn_trap")) {
            e.setCancelled(true);
            e.getEntity().remove();
        }
    }
}
