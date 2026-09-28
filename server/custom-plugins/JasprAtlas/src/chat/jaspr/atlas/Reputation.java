package chat.jaspr.atlas;

import org.bukkit.ChatColor;
import org.bukkit.entity.Entity;
import org.bukkit.entity.IronGolem;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;

/**
 * Standing with the Concord (-100 .. 150). The Concord is hospitable and slow to anger, and every consequence is said
 * aloud: hurting a citizen costs standing and turns nearby automata on you for a while; killing one costs far more.
 * Low standing closes shops; very low standing sets the automata against you on sight. Standing recovers with time and
 * with deeds (freeing the Bound, breaking tyrants), or at once by paying a fine at the Synedrion. The key figures always
 * speak to you, whatever your standing: what you need to win can never be withheld.
 */
final class Reputation implements Listener {
    static final int BARRED = -40, HOSTILE = -60, MAX = 150, MIN = -100;

    private final AtlasPlugin plugin;

    Reputation(AtlasPlugin plugin) { this.plugin = plugin; }

    int get(Player p) { return plugin.state().player(p.getUniqueId(), p.getName()).standing; }

    boolean barred(Player p) { return get(p) <= BARRED; }

    boolean hostile(Player p) { return get(p) <= HOSTILE; }

    void add(Player p, int delta, String why) {
        State.Player rec = plugin.state().player(p.getUniqueId(), p.getName());
        int before = rec.standing;
        rec.standing = Math.max(MIN, Math.min(MAX, before + delta));
        if (rec.standing == before) return;
        String t = title(rec.standing);
        p.sendMessage((delta > 0 ? ChatColor.GREEN + "+" : ChatColor.RED + "") + delta + " standing with the Concord" + ChatColor.GRAY + " (" + why + "). You are " + t + ".");
        if (before > BARRED && rec.standing <= BARRED) p.sendMessage(ChatColor.RED + "The Concord's merchants will no longer trade with you. " + ChatColor.GRAY + "Pay a fine at the Synedrion, or earn their trust back.");
        if (before > HOSTILE && rec.standing <= HOSTILE) p.sendMessage(ChatColor.DARK_RED + "The automata of the Concord will now defend it against you on sight.");
        plugin.saveStateSoon();
        plugin.getLogger().info("ATLAS_STANDING player=" + p.getName() + " delta=" + delta + " now=" + rec.standing + " why=" + why.replace(' ', '_'));
    }

    static String title(int s) {
        if (s <= HOSTILE) return ChatColor.DARK_RED + "an enemy of the Concord" + ChatColor.GRAY;
        if (s <= BARRED) return ChatColor.RED + "distrusted" + ChatColor.GRAY;
        if (s < 20) return ChatColor.WHITE + "a stranger" + ChatColor.GRAY;
        if (s < 50) return ChatColor.AQUA + "a guest-friend" + ChatColor.GRAY;
        if (s < 100) return ChatColor.AQUA + "a friend of the Concord" + ChatColor.GRAY;
        return ChatColor.GOLD + "a hero of the Concord" + ChatColor.GRAY;
    }

    /** Hurting the Concord's people or automata. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void hurt(EntityDamageByEntityEvent e) {
        Entity victim = e.getEntity();
        if (!plugin.isAtlas(victim.getWorld())) return;
        Player p = attacker(e.getDamager());
        if (p == null) return;
        if (Npcs.has(victim, Npcs.CITIZEN) || victim.getScoreboardTags().stream().anyMatch(t -> t.startsWith(Npcs.KEY))) {
            add(p, -10, "you struck a citizen");
            rouse(p, victim, 24);
        } else if (Npcs.has(victim, Npcs.TALOS)) {
            add(p, -5, "you struck an automaton");
            if (victim instanceof IronGolem) ((IronGolem) victim).setTarget(p);
        }
    }

    @EventHandler
    public void killed(EntityDeathEvent e) {
        LivingEntity dead = e.getEntity();
        if (!plugin.isAtlas(dead.getWorld()) || dead.getKiller() == null) return;
        if (Npcs.has(dead, Npcs.CITIZEN)) { add(dead.getKiller(), -40, "you killed a citizen"); e.getDrops().clear(); }
    }

    /** Automata near the struck one come to its defence (for a while). */
    private void rouse(Player p, Entity near, int range) {
        for (Entity e : near.getNearbyEntities(range, 8, range)) if (e instanceof IronGolem && Npcs.has(e, Npcs.TALOS)) ((IronGolem) e).setTarget(p);
    }

    static Player attacker(Entity damager) {
        if (damager instanceof Player) return (Player) damager;
        if (damager instanceof Projectile && ((Projectile) damager).getShooter() instanceof Player) return (Player) ((Projectile) damager).getShooter();
        return null;
    }

    /** Every minute: standing drifts back toward neutral (up by 1 if below 10), and hostile players are hunted by automata. */
    void tick() {
        for (Player p : plugin.atlasPlayers()) {
            State.Player rec = plugin.state().player(p.getUniqueId(), p.getName());
            if (rec.standing < 10) rec.standing++;
            if (rec.standing <= HOSTILE)
                for (Entity e : p.getNearbyEntities(20, 8, 20)) if (e instanceof IronGolem && Npcs.has(e, Npcs.TALOS)) ((IronGolem) e).setTarget(p);
        }
    }

    /** Pay the Synedrion's fine: standing is restored to neutral. */
    boolean payFine(Player p, int emeralds) {
        if (!p.getInventory().containsAtLeast(new org.bukkit.inventory.ItemStack(org.bukkit.Material.EMERALD), emeralds)) return false;
        p.getInventory().removeItem(new org.bukkit.inventory.ItemStack(org.bukkit.Material.EMERALD, emeralds));
        State.Player rec = plugin.state().player(p.getUniqueId(), p.getName());
        rec.standing = Math.max(rec.standing, 0);
        plugin.saveStateSoon();
        return true;
    }

    static int fine(int standing) { return standing >= 0 ? 0 : Math.min(32, 4 + (-standing) / 4); }
}
