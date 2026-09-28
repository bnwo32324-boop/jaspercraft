package chat.jaspr.atlas;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.md_5.bungee.api.chat.BaseComponent;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.ComponentBuilder;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.inventory.EquipmentSlot;

/**
 * Heliodromes: rings of quartz round a lumen core, the Concord's light-gates. Walking into a ring discovers it (for that
 * player, for good). Touching the light at its core opens the list of rings you have found; choose one and the light
 * carries you there. Rings in liberated Dominion forts wake when their province is freed.
 */
final class Heliodromes implements Listener {
    private final AtlasPlugin plugin;
    private final Map<UUID, Long> lastJump = new HashMap<>();
    long discovered, jumps;

    Heliodromes(AtlasPlugin plugin) { this.plugin = plugin; }

    static String id(Registry.Spot s) { return s.kind.substring("heliodrome:".length()); }

    /** A ring is awake if it stands in the Concord, or in a liberated province. */
    boolean awake(Registry.Spot s) {
        Realm.Zone z = Realm.zone(s.x, s.z);
        return z.province == null || plugin.state().liberated(z.province);
    }

    static String name(Registry.Spot s) {
        switch (id(s)) {
            case "threshold": return "The Threshold";
            case "astreion": return "Astreion, the Agora";
            case "last_watch": return "The Last Watch";
            case "lampsa": return "Lampsa, west ring";
            case "lampsa_centre": return "Lampsa, the Mechaneion";
            case "hieranthe": return "Hieranthe, south ring";
            case "hieranthe_centre": return "Hieranthe, the temples";
            case "mnemeia": return "Mnemeia, east ring";
            case "mnemeia_centre": return "Mnemeia, the Archive";
            case "pylon": return "The Pylon of Teeth";
            default: return s.extra == null ? id(s) : s.extra;
        }
    }

    /** Every second: players walking into a ring discover it. */
    void tick() {
        World w = plugin.atlas();
        if (w == null || !plugin.registry().ready()) return;
        for (Player p : w.getPlayers()) {
            if (p.getGameMode() == GameMode.SPECTATOR) continue;
            Location l = p.getLocation();
            for (Registry.Spot s : plugin.registry().all("heliodrome:")) {
                double dx = l.getX() - (s.x + 0.5), dz = l.getZ() - (s.z + 0.5);
                if (dx * dx + dz * dz > 36 || Math.abs(l.getY() - s.y) > 4 || !awake(s)) continue;
                State.Player rec = plugin.state().player(p.getUniqueId(), p.getName());
                if (rec.heliodromes.add(id(s))) {
                    discovered++;
                    plugin.saveStateSoon();
                    p.sendMessage(ChatColor.AQUA + "Heliodrome found: " + ChatColor.WHITE + name(s) + ChatColor.GRAY + ". Touch the light at the heart of any ring to travel to the rings you have found.");
                    p.playSound(l, Sound.BLOCK_NOTE_CHIME, 1f, 1.5f);
                    plugin.getLogger().info("ATLAS_HELIODROME_FOUND player=" + p.getName() + " id=" + id(s));
                }
            }
        }
    }

    /** The ring whose core is this block (the lumen core or the rod above it), or null. */
    Registry.Spot ringAt(Block b) {
        if (b.getType() != Material.SEA_LANTERN && b.getType() != Material.END_ROD) return null;
        for (Registry.Spot s : plugin.registry().all("heliodrome:")) {
            double dx = b.getX() - s.x, dz = b.getZ() - s.z;
            if (dx * dx + dz * dz <= 2.3 && b.getY() >= s.y - 1 && b.getY() <= s.y + 2) return s;
        }
        return null;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void touch(PlayerInteractEvent e) {
        if (e.getAction() != Action.RIGHT_CLICK_BLOCK || e.getHand() != EquipmentSlot.HAND || e.getClickedBlock() == null) return;
        if (!plugin.isAtlas(e.getClickedBlock().getWorld()) || !plugin.registry().ready()) return;
        Registry.Spot here = ringAt(e.getClickedBlock());
        if (here == null) return;
        e.setCancelled(true);
        open(e.getPlayer(), here);
    }

    /** The list of rings a player can travel to from this one (book with tappable names, and chat). */
    void open(Player p, Registry.Spot here) {
        if (!awake(here)) { p.sendMessage(ChatColor.GRAY + "The ring is dark. It will wake when this land is free."); return; }
        State.Player rec = plugin.state().player(p.getUniqueId(), p.getName());
        if (rec.heliodromes.add(id(here))) plugin.saveStateSoon();
        List<Registry.Spot> to = new ArrayList<>();
        for (Registry.Spot s : plugin.registry().all("heliodrome:")) if (s != here && rec.heliodromes.contains(id(s)) && awake(s)) to.add(s);
        if (to.isEmpty()) { p.sendMessage(ChatColor.AQUA + name(here) + ChatColor.GRAY + ": the light hums, but you have found no other ring yet. Walk into another heliodrome's ring to find it."); return; }
        List<BaseComponent[]> pages = new ArrayList<>();
        ComponentBuilder b = new ComponentBuilder("");
        for (BaseComponent c : TextComponent.fromLegacyText(ChatColor.DARK_BLUE + "HELIODROME\n" + ChatColor.BLACK + name(here) + "\n\nWhere should the light carry you?\n")) b.append(c, ComponentBuilder.FormatRetention.NONE);
        int rows = 6;
        for (Registry.Spot s : to) {
            if (rows > 12) { pages.add(b.create()); b = new ComponentBuilder(""); rows = 0; }
            b.append("\n> " + name(s), ComponentBuilder.FormatRetention.NONE).color(net.md_5.bungee.api.ChatColor.DARK_GREEN).event(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/atlas go " + id(s)));
            rows += 1 + name(s).length() / 17;
        }
        pages.add(b.create());
        Talk.openBook(p, pages);
        p.sendMessage(ChatColor.AQUA + name(here) + ChatColor.GRAY + ": where should the light carry you?");
        for (Registry.Spot s : to) {
            TextComponent c = new TextComponent("  > " + name(s));
            c.setColor(net.md_5.bungee.api.ChatColor.GREEN);
            c.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/atlas go " + id(s)));
            p.spigot().sendMessage(c);
        }
    }

    /** "/atlas go &lt;id&gt;": travel from the ring you stand at to one you have found. */
    void go(Player p, String id) {
        World w = plugin.atlas();
        if (w == null || p.getWorld() != w) return;
        Registry.Spot from = null, to = plugin.registry().spot("heliodrome:" + id);
        for (Registry.Spot s : plugin.registry().all("heliodrome:")) if (p.getLocation().distanceSquared(s.at(w)) < 10 * 10) from = s;
        State.Player rec = plugin.state().player(p.getUniqueId(), p.getName());
        if (from == null) { p.sendMessage(ChatColor.GRAY + "You must stand in a heliodrome's ring to travel."); return; }
        if (to == null || !rec.heliodromes.contains(id) || !awake(to)) { p.sendMessage(ChatColor.GRAY + "The light does not know that ring. Find it first."); return; }
        Long last = lastJump.get(p.getUniqueId());
        if (last != null && System.currentTimeMillis() - last < 3000) return;
        lastJump.put(p.getUniqueId(), System.currentTimeMillis());
        Location src = p.getLocation();
        w.spawnParticle(Particle.END_ROD, src.clone().add(0, 1, 0), 40, 0.4, 1, 0.4, 0.05);
        w.playSound(src, Sound.ITEM_CHORUS_FRUIT_TELEPORT, 1f, 1.4f);
        w.getChunkAt(to.x >> 4, to.z >> 4).load(true);
        Location dest = Bosses.standable(new Location(w, to.x + 0.5, to.y, to.z + 2.5, 0, 0));
        if (p.isInsideVehicle()) p.leaveVehicle();
        p.setFallDistance(0);
        boolean ok = p.teleport(dest, PlayerTeleportEvent.TeleportCause.PLUGIN);
        w.spawnParticle(Particle.END_ROD, dest.clone().add(0, 1, 0), 40, 0.4, 1, 0.4, 0.05);
        w.playSound(dest, Sound.ITEM_CHORUS_FRUIT_TELEPORT, 1f, 1.6f);
        jumps++;
        plugin.getLogger().info("ATLAS_HELIODROME_JUMP player=" + p.getName() + " from=" + id(from) + " to=" + id + " ok=" + ok);
    }
}
