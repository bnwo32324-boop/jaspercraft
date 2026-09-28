package chat.jaspr.atlas;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.Sign;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

/**
 * What the Concord's knowledge does. The Oath held near Kallias makes him mortal. The Hymn sung at Melaina's three
 * Stilling Fonts (hold it, right-click the Font) turns them back into water. The Counterpoint, carried while striking
 * the Great Engine's Governors deep, middle, high, stills them; the wrong order wakes them all again and burns the hand.
 * The Charter read to Keleos's three Edict Stones (hold it, right-click the stone) silences them; near a speaking stone,
 * without the Charter in hand, you agree to everything and your limbs grow heavy. The Light of Theano, held at the
 * Cinder Heart's root, breaks the Heart. Silenced mechanisms stay silent for everyone, for good.
 */
final class Mechanisms implements Listener {
    private final AtlasPlugin plugin;
    private final Map<UUID, Long> told = new HashMap<>();
    long silenced, wrongOrder, compelled;

    Mechanisms(AtlasPlugin plugin) { this.plugin = plugin; }

    int count(String prefix) { int n = 0; for (String m : plugin.state().mechanisms) if (m.startsWith(prefix)) n++; return n; }

    boolean oathNear(LivingEntity kallias) {
        for (Player p : kallias.getWorld().getPlayers())
            if (p.getGameMode() != GameMode.SPECTATOR && !p.isDead() && p.getLocation().distanceSquared(kallias.getLocation()) < 24 * 24 && Items.holding(p, "oath")) return true;
        return false;
    }

    /** Why a boss cannot be hurt right now (the message players see), or null if it can. */
    String shield(Bosses.Fight f, LivingEntity e) {
        switch (f.boss) {
            case KALLIAS: return oathNear(e) ? null : ChatColor.DARK_RED + "Kallias shrugs off the blow: \"I do not tire.\" " + ChatColor.GRAY + "(Hold the Oath of Kallias within 24 blocks of him.)";
            case MELAINA: return count("font:") >= 3 ? null : ChatColor.DARK_RED + "The wound closes like water. Nothing dies near her Fonts. " + ChatColor.GRAY + "(Hold the Hymn of Passage and right-click each of her three Stilling Fonts.)";
            case DAIDAROS: return count("governor:") >= 3 ? null : ChatColor.DARK_RED + "The Engine's shield turns the blow. " + ChatColor.GRAY + "(Carry the Counterpoint and strike the three Governors, in its order.)";
            case KELEOS: return count("edict:") >= 3 ? null : ChatColor.DARK_RED + "Your arm stops of itself: you have agreed not to strike. " + ChatColor.GRAY + "(Hold the Founding Charter and right-click each of his three Edict Stones.)";
            case PYRARCH: return f.phase == 3 && !f.heartBroken ? ChatColor.DARK_RED + "Black fire swallows the blow. " + ChatColor.GRAY + "(Carry the Light of Theano to the Heart's root, the black column above the throne, and hold it there.)" : null;
            default: return null;
        }
    }

    /** Called while a boss fights: Kallias hears his Oath. */
    void during(Bosses.Fight f, LivingEntity e, List<Player> near) {
        if (f.boss == Bosses.Boss.KALLIAS && !f.oathSeen && oathNear(e)) {
            f.oathSeen = true;
            e.getWorld().playSound(e.getLocation(), Sound.BLOCK_NOTE_BELL, 1f, 0.7f);
            Bosses.say(near, ChatColor.DARK_RED + "Kallias " + ChatColor.GRAY + "falters. \"I take the shield of the Concord... and I will carry it...\" For a heartbeat he is only a man, and the crown is only iron.");
            plugin.getLogger().info("ATLAS_MECHANISM kind=oath heard=true");
        }
    }

    // ------------------------------------------------------------------ the Fonts and the Edict Stones

    @EventHandler(priority = EventPriority.HIGH)
    public void touch(PlayerInteractEvent e) {
        if (e.getAction() != Action.RIGHT_CLICK_BLOCK || e.getHand() != EquipmentSlot.HAND) return;
        Block b = e.getClickedBlock();
        if (b == null || !plugin.isAtlas(b.getWorld()) || !plugin.registry().ready()) return;
        if (use(e.getPlayer(), b)) e.setCancelled(true);
    }

    /** A player touches a block: if it belongs to a Font or an Edict Stone, the mechanism answers (true: handled). */
    boolean use(Player p, Block b) {
        State s = plugin.state();
        for (Registry.Spot spot : plugin.registry().all("mech:font:")) {
            if (dist(b, spot) > 3.3 || Math.abs(b.getY() - spot.y) > 3) continue;
            String key = spot.kind.substring(5);
            if (s.mechanisms.contains(key) || s.bossesFallen.containsKey("melaina")) { p.sendMessage(ChatColor.GRAY + "This Font is only water now. It flows."); return true; }
            if (!Items.holding(p, "hymn")) { warn(p, ChatColor.GRAY + "The water is black and perfectly still. It refuses you. " + ChatColor.DARK_GRAY + "(Iaso of Hieranthe knows the Hymn that quiets the Fonts. Hold it and touch the Font.)"); return true; }
            silence(key, p);
            quietFont(b.getWorld(), spot);
            int n = count("font:");
            say(b.getWorld(), spot, ChatColor.AQUA + p.getName() + " sings the Hymn of Passage over a Stilling Font. " + ChatColor.GRAY + "The black water shivers and remembers it may flow. (" + n + "/3)"
                + (n == 3 ? ChatColor.GOLD + " The Stiller can be hurt now." : ""));
            return true;
        }
        for (Registry.Spot spot : plugin.registry().all("mech:edict:")) {
            if (dist(b, spot) > 4.6 || b.getY() < spot.y - 3 || b.getY() > spot.y + 14) continue;
            String key = spot.kind.substring(5);
            if (s.mechanisms.contains(key) || s.bossesFallen.containsKey("keleos")) { p.sendMessage(ChatColor.GRAY + "This stone no longer speaks."); return true; }
            if (!Items.holding(p, "charter")) { warn(p, ChatColor.GRAY + "The stone hums. You find you agree with it. " + ChatColor.DARK_GRAY + "(Hesper of Mnemeia keeps the Charter. Hold it and touch the stone.)"); return true; }
            silence(key, p);
            quietStone(b.getWorld(), spot);
            int n = count("edict:");
            say(b.getWorld(), spot, ChatColor.AQUA + p.getName() + " reads the First Article to an Edict Stone: " + ChatColor.WHITE + "\"No law shall bind the tongue of a free citizen.\" "
                + ChatColor.GRAY + "The stone's hum falters, and stops. (" + n + "/3)" + (n == 3 ? ChatColor.GOLD + " The Magistrate can be hurt now." : ""));
            return true;
        }
        return false;
    }

    private static double dist(Block b, Registry.Spot s) { double dx = b.getX() - s.x, dz = b.getZ() - s.z; return Math.sqrt(dx * dx + dz * dz); }

    private void silence(String key, Player p) {
        plugin.state().mechanisms.add(key);
        silenced++;
        plugin.saveStateSoon();
        plugin.getLogger().info("ATLAS_MECHANISM kind=" + key + " silenced=true by=" + p.getName());
    }

    private void warn(Player p, String msg) {
        Long last = told.get(p.getUniqueId());
        if (last != null && System.currentTimeMillis() - last < 2500) return;
        told.put(p.getUniqueId(), System.currentTimeMillis());
        p.sendMessage(msg);
    }

    private static void say(World w, Registry.Spot spot, String msg) {
        Location at = new Location(w, spot.x, spot.y, spot.z);
        for (Player p : w.getPlayers()) if (p.getLocation().distanceSquared(at) < 64 * 64) p.sendMessage(msg);
    }

    /** The Font's black basin turns to water; its brazier-stand becomes a flower pot (as the freed garden has it). */
    @SuppressWarnings("deprecation")
    private static void quietFont(World w, Registry.Spot spot) {
        for (int dx = -1; dx <= 1; dx++)
            for (int dz = -1; dz <= 1; dz++) {
                Block basin = w.getBlockAt(spot.x + dx, spot.y - 1, spot.z + dz);
                if (basin.getType() == Material.CONCRETE) basin.setTypeIdAndData(9, (byte) 0, false);
            }
        Block stand = w.getBlockAt(spot.x, spot.y, spot.z);
        if (stand.getType() == Material.BREWING_STAND) stand.setType(Material.FLOWER_POT, false);
        Location at = new Location(w, spot.x + 0.5, spot.y + 0.5, spot.z + 0.5);
        w.spawnParticle(Particle.WATER_SPLASH, at, 60, 1, 0.3, 1, 0.1);
        w.playSound(at, Sound.BLOCK_NOTE_HARP, 1f, 0.8f);
        w.playSound(at, Sound.BLOCK_WATER_AMBIENT, 1f, 1f);
    }

    /** The obelisk's burning crown goes cold (chiseled quartz, as the freed square has it) and its sign says so. */
    @SuppressWarnings("deprecation")
    private static void quietStone(World w, Registry.Spot spot) {
        for (int dx = -4; dx <= 4; dx++)
            for (int dz = -4; dz <= 4; dz++)
                for (int y = spot.y; y <= spot.y + 14; y++) {
                    Block b = w.getBlockAt(spot.x + dx, y, spot.z + dz);
                    if (b.getType() == Material.MAGMA) b.setTypeIdAndData(155, (byte) 1, false);
                    else if (y <= spot.y + 2 && (b.getType() == Material.WALL_SIGN || b.getType() == Material.SIGN_POST)) {
                        BlockState st = b.getState();
                        if (st instanceof Sign) { Sign sg = (Sign) st; sg.setLine(0, "THIS STONE"); sg.setLine(1, "NO LONGER"); sg.setLine(2, "SPEAKS."); sg.setLine(3, "~ THE CONCORD"); sg.update(true, false); }
                    }
                }
        Location at = new Location(w, spot.x + 0.5, spot.y + 3, spot.z + 0.5);
        w.spawnParticle(Particle.CLOUD, at, 40, 0.8, 3, 0.8, 0.02);
        w.playSound(at, Sound.BLOCK_FIRE_EXTINGUISH, 1f, 0.7f);
    }

    // ------------------------------------------------------------------ the Governors of the Great Engine

    @EventHandler(priority = EventPriority.HIGH)
    public void strike(BlockDamageEvent e) {
        Block b = e.getBlock();
        if (!plugin.isAtlas(b.getWorld()) || b.getType() != Material.END_ROD && b.getType() != Material.SEA_LANTERN || !plugin.registry().ready()) return;
        if (strikeAt(e.getPlayer(), b)) e.setCancelled(true);
    }

    /** A player strikes a block: if it is one of the Great Engine's Governors, the Engine answers (true: handled). */
    boolean strikeAt(Player p, Block b) {
        for (Registry.Spot spot : plugin.registry().all("mech:governor:")) {
            if (dist(b, spot) > 2.5 || Math.abs(b.getY() - spot.y) > 2) continue;
            State s = plugin.state();
            String key = spot.kind.substring(5);
            int k = Integer.parseInt(key.substring(9));
            if (s.bossesFallen.containsKey("daidaros") || s.mechanisms.contains(key)) { warn(p, ChatColor.GRAY + "This Governor is silent."); return true; }
            if (!Items.has(p, "counterpoint")) {
                warn(p, ChatColor.GRAY + "Your blow rings off the Governor. You cannot find the point where its note breaks. " + ChatColor.DARK_GRAY + "(Perdix of Lampsa has Daidaros's Counterpoint.)");
                return true;
            }
            boolean inOrder = true;
            for (int j = 0; j < k; j++) if (!s.mechanisms.contains("governor:" + j)) inOrder = false;
            if (!inOrder) { wrongOrder(p, b.getWorld()); return true; }
            silence(key, p);
            quietGovernor(b.getWorld(), spot);
            int n = count("governor:");
            say(b.getWorld(), spot, ChatColor.AQUA + p.getName() + " finds the breaking point of the " + (k == 0 ? "deep" : k == 1 ? "middle" : "high") + " Governor. "
                + ChatColor.GRAY + "Its note stops. (" + n + "/3)" + (n == 3 ? ChatColor.GOLD + " The Engine's shield is down: the Forgemaster can be hurt." : ""));
            return true;
        }
        return false;
    }

    /** The Engine punishes a Governor struck out of order: every silenced voice sings again, and the hand is burned. */
    private void wrongOrder(Player p, World w) {
        State s = plugin.state();
        s.mechanisms.removeIf(m -> m.startsWith("governor:"));
        plugin.saveStateSoon();
        wrongOrder++;
        for (Registry.Spot g : plugin.registry().all("mech:governor:")) wakeGovernor(w, g);
        p.damage(6);
        p.setFireTicks(60);
        p.setVelocity(p.getLocation().getDirection().multiply(-1.2).setY(0.5));
        w.playSound(p.getLocation(), Sound.ENTITY_BLAZE_HURT, 1f, 0.5f);
        p.sendMessage(ChatColor.DARK_RED + "The Engine rings out: the silenced voices rise again, and the note burns your hand. " + ChatColor.GRAY + "(The order matters. What does the Counterpoint say?)");
        plugin.getLogger().info("ATLAS_MECHANISM kind=governor wrongOrder=true by=" + p.getName());
    }

    private static void quietGovernor(World w, Registry.Spot spot) {
        for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++) for (int dy = -2; dy <= 2; dy++) {
            Block b = w.getBlockAt(spot.x + dx, spot.y + dy, spot.z + dz);
            if (b.getType() == Material.END_ROD) b.setType(Material.AIR, false);
        }
        Location at = new Location(w, spot.x + 0.5, spot.y, spot.z + 0.5);
        w.spawnParticle(Particle.SMOKE_NORMAL, at, 30, 0.3, 0.3, 0.3, 0.02);
        w.playSound(at, Sound.BLOCK_FIRE_EXTINGUISH, 1f, 1.2f);
    }

    @SuppressWarnings("deprecation")
    private static void wakeGovernor(World w, Registry.Spot spot) {
        for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++) for (int dy = -2; dy <= 2; dy++) {
            Block b = w.getBlockAt(spot.x + dx, spot.y + dy, spot.z + dz);
            if (b.getType() == Material.SEA_LANTERN && b.getRelative(0, 1, 0).isEmpty()) b.getRelative(0, 1, 0).setTypeIdAndData(198, (byte) 1, false);
        }
    }

    // ------------------------------------------------------------------ the Cinder Heart and the Light

    static int heartRootY() { return Terrain.base(Realm.Place.ANTHRAKION) + Anthrakion.HEIGHT + 1; }

    /** A player holding the Light at the Heart's root for five seconds breaks the Heart. */
    void channelHeart(Bosses.Fight f, LivingEntity pyrarch, List<Player> near) {
        int ry = heartRootY();
        Player bearer = null;
        for (Player p : near) {
            Location l = p.getLocation();
            double dx = l.getX() - (Realm.TX + 0.5), dz = l.getZ() - (Realm.TZ + 0.5);
            if (dx * dx + dz * dz <= 4.5 * 4.5 && l.getY() >= ry - 2 && l.getY() <= ry + 6 && Items.holding(p, "light")) { bearer = p; break; }
        }
        if (bearer == null) { f.channel = Math.max(0, f.channel - 0.5); return; }
        f.channel += 0.5;
        World w = pyrarch.getWorld();
        Location heart = new Location(w, Realm.TX + 0.5, ry + 15, Realm.TZ + 0.5);
        w.spawnParticle(Particle.END_ROD, bearer.getLocation().add(0, 1.5, 0), 12, 0.2, 2, 0.2, 0.05);
        int pct = (int) Math.min(100, f.channel * 20);
        for (Player p : near) p.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent(ChatColor.AQUA + "The Light of Theano: " + pct + "%"));
        if (f.channel < 5) return;
        f.heartBroken = true;
        w.spawnParticle(Particle.EXPLOSION_HUGE, heart, 6, 3, 3, 3, 0);
        w.spawnParticle(Particle.END_ROD, heart, 300, 4, 4, 4, 0.2);
        w.playSound(heart, Sound.ENTITY_GENERIC_EXPLODE, 2f, 0.5f);
        w.playSound(heart, Sound.ENTITY_LIGHTNING_THUNDER, 2f, 0.6f);
        pyrarch.addPotionEffect(new PotionEffect(PotionEffectType.SLOW, 20 * 60, 1, true, false));
        pyrarch.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, 20 * 60, 1, true, false));
        Bosses.say(near, ChatColor.AQUA + bearer.getName() + " raises the Light of Theano. " + ChatColor.GRAY + "The two halves of the Star remember each other: the Cinder Heart cracks from end to end, and the black fire round the Pyrarch goes out. "
            + ChatColor.GOLD + "He can be hurt.");
        plugin.getLogger().info("ATLAS_HEART_BROKEN by=" + bearer.getName());
    }

    // ------------------------------------------------------------------ while the mechanisms work

    /** Once a second: speaking Edict Stones compel whoever stands near them without the Charter in hand. */
    void tick() {
        World w = plugin.atlas();
        if (w == null || !plugin.registry().ready()) return;
        State s = plugin.state();
        if (s.bossesFallen.containsKey("keleos")) return;
        for (Registry.Spot spot : plugin.registry().all("mech:edict:")) {
            if (s.mechanisms.contains(spot.kind.substring(5)) || !w.isChunkLoaded(spot.x >> 4, spot.z >> 4)) continue;
            Location at = new Location(w, spot.x + 0.5, spot.y, spot.z + 0.5);
            for (Player p : w.getPlayers()) {
                if (p.getGameMode() != GameMode.SURVIVAL && p.getGameMode() != GameMode.ADVENTURE || p.getLocation().distanceSquared(at) > 7 * 7) continue;
                if (Items.holding(p, "charter")) continue;
                p.addPotionEffect(new PotionEffect(PotionEffectType.SLOW, 60, 1, true, false), true);
                p.addPotionEffect(new PotionEffect(PotionEffectType.CONFUSION, 100, 0, true, false), true);
                compelled++;
                warn(p, ChatColor.DARK_PURPLE + "\"Yes,\" you hear yourself say. \"Yes. Yes.\" " + ChatColor.GRAY + "(A speaking Edict Stone. Hold the Founding Charter.)");
            }
        }
    }

    /** Near Melaina, while any Font sings, the fallen rise again. */
    @EventHandler
    public void risesAgain(EntityDeathEvent e) {
        LivingEntity dead = e.getEntity();
        if (!Dominion.isDominion(dead) || Npcs.tagValue(dead, Bosses.TAG) != null || !plugin.isAtlas(dead.getWorld())) return;
        State s = plugin.state();
        if (s.bossesFallen.containsKey("melaina") || count("font:") >= 3) return;
        Registry.Spot garden = plugin.registry().spot("boss:melaina");
        if (garden == null || dead.getLocation().distanceSquared(garden.at(dead.getWorld())) > 40 * 40) return;
        String kind = Npcs.tagValue(dead, Npcs.DOM_KIND);
        Dominion.Kind k = kind == null ? null : Dominion.Kind.of(kind);
        if (k == null) return;
        Location at = dead.getLocation();
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (plugin.atlas() == null || at.getWorld() != plugin.atlas() || count("font:") >= 3) return;
            LivingEntity again = plugin.dominion().raise(at, k, false);
            again.addScoreboardTag("atlas_minion");
            at.getWorld().spawnParticle(Particle.SPELL_MOB, at.clone().add(0, 1, 0), 20, 0.3, 0.6, 0.3, 0);
            for (Entity x : again.getNearbyEntities(16, 6, 16)) if (x instanceof Player) x.sendMessage(ChatColor.GRAY + "It rises again. Nothing dies near the Fonts.");
        }, 60L);
    }
}
