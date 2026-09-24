package chat.jaspr.gear;

import com.destroystokyo.paper.event.player.PlayerJumpEvent;
import java.util.List;
import java.util.Map;
import java.util.Random;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.block.Block;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Creature;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.entity.EntityTargetEvent;
import org.bukkit.event.entity.EntityTargetLivingEntityEvent;
import org.bukkit.event.player.PlayerToggleFlightEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

/**
 * Phase 3 mutations: passives (attribute modifiers, damage rules, climbing, flight, loot) and the
 * R-key active abilities, which spend adrenaline through GearVitals like the trinket keys.
 * Ability damage is dealt with GearAbilities.arcing set, so it never re-triggers on-hit gear.
 */
final class GearMutations implements Listener {
    static final double FERAL_FIST = 3.0, POUNCE_BONUS = 4.0, STALKER_ARROW_DAMAGE = 1.2, STALKER_ARROW_SPEED = 1.25;
    static final double PROSPECT_CHANCE = 0.20, SCROUNGE_CHANCE = 0.20, FLIGHT_COST_PER_SECOND = 2.0, FLIGHT_MIN_START = 5.0;
    static final long FADE_MS = 8_000L, POUNCE_MS = 2_500L, MIST_MS = 6_000L, CHARGE_MS = 1_200L;

    private final GearPlugin plugin;
    private final Random random = new Random();
    int mutated, purged, fired, prospects, scrounges, grounded;

    GearMutations(GearPlugin plugin) { this.plugin = plugin; }

    static boolean survival(Player p) {
        return p.getGameMode() == GameMode.SURVIVAL || p.getGameMode() == GameMode.ADVENTURE;
    }

    // ================================================================ passives

    /** Modifiers of the current mutation on, every other mutation's off; health clamped; flight synced. */
    void apply(Player p, GearProfile prof) {
        for (GearMutation m : GearMutation.values())
            for (Map.Entry<Attribute, AttributeModifier> e : m.modifiers().entrySet())
                GearAbilities.setModifier(p, e.getKey(), e.getValue(), m == prof.mutation);
        if (!p.isDead() && p.getHealth() > p.getMaxHealth()) p.setHealth(p.getMaxHealth());
        flight(p, prof);
    }

    /** Sprite flight: allowed while it has adrenaline; only ever revokes flight it granted itself. */
    void flight(Player p, GearProfile prof) {
        boolean want = survival(p) && GearAbilities.active(p) && prof.mutation == GearMutation.SPRITE
            && prof.adrenaline >= 1.0 && !prof.has(GearStatus.PARALYSIS, System.currentTimeMillis());
        if (want) {
            if (!p.getAllowFlight()) p.setAllowFlight(true);
            prof.grantedFlight = true;
        } else if (prof.grantedFlight) {
            prof.grantedFlight = false;
            if (survival(p)) {
                if (p.isFlying()) {
                    p.setFlying(false);
                    grounded++;
                    if (prof.mutation == GearMutation.SPRITE && GearAbilities.active(p))
                        GearAbilities.bar(p, ChatColor.LIGHT_PURPLE + "Out of adrenaline" + ChatColor.GRAY + " - your wings give out");
                }
                p.setAllowFlight(false);
            }
        }
    }

    /** Quit / disable: never leave flight we granted in the saved player data. */
    void clear(Player p, GearProfile prof) {
        if (!prof.grantedFlight) return;
        prof.grantedFlight = false;
        if (survival(p)) { p.setFlying(false); p.setAllowFlight(false); }
    }

    /** Every 2 ticks: Feral climbing, Brute sinking, the Charger's stampede, the Wyrm's fireproof hide. */
    void fast(Player p, GearProfile prof, long now, int tick) {
        GearMutation m = prof.mutation;
        if (m == GearMutation.BASELINE || !GearAbilities.active(p)) return;
        Location loc = p.getLocation();
        boolean liquid = loc.getBlock().isLiquid();
        if (m == GearMutation.FERAL && !p.isOnGround() && !p.isFlying() && !liquid && GearAbilities.facingWall(p)
            && !prof.worn().contains(GearItem.RAZOR_CLAWS)) {
            p.setFallDistance(0f);
            if (p.isSneaking()) p.setVelocity(new Vector(0, 0.0, 0));
            else if (loc.getPitch() < 55f) p.setVelocity(new Vector(0, 0.22, 0));
            if ((tick & 7) == 0) p.getWorld().playSound(loc, Sound.BLOCK_GRAVEL_STEP, 0.3f, 1.6f);
        } else if (m == GearMutation.BRUTE && liquid && !p.isOnGround() && !p.isFlying()) {
            Vector v = p.getVelocity();
            p.setVelocity(v.setY(v.getY() - 0.03)); // heavy: sinks, swimming up is slow work
        } else if (m == GearMutation.WYRM && p.getFireTicks() > 0) {
            p.setFireTicks(0);
        } else if (m == GearMutation.CHARGER && now < prof.chargeUntil) {
            charge(p, prof);
        }
        if (m != GearMutation.CHARGER || now >= prof.chargeUntil) prof.chargeHit.clear();
    }

    /** Once a second: Sprite flight cost and Mending Mist pulses. */
    void second(Player p, GearProfile prof, long now) {
        if (prof.mutation == GearMutation.SPRITE && prof.grantedFlight && p.isFlying() && survival(p)) {
            GearVitals.gain(prof, -FLIGHT_COST_PER_SECOND);
            plugin.vitals.pushHud(p, prof);
        }
        if (now < prof.mistUntil && GearAbilities.active(p)) mist(p);
        flight(p, prof);
    }

    // ================================================================ injection

    /** Serum use. BASELINE = Purge Serum. Returns true when the serum was consumed. */
    boolean inject(Player p, GearProfile prof, GearMutation target) {
        GearMutation old = prof.mutation;
        Location at = p.getLocation();
        if (target == GearMutation.BASELINE) {
            if (old == GearMutation.BASELINE) { GearAbilities.bar(p, ChatColor.GRAY + "No mutation to purge"); return false; }
            prof.mutation = GearMutation.BASELINE;
            purged++;
            p.getWorld().playSound(at, Sound.ENTITY_ZOMBIE_VILLAGER_CURE, 0.5f, 1.4f);
            GearAbilities.bar(p, ChatColor.GREEN + "Purged" + ChatColor.GRAY + " - the " + old.title + " mutation burns out of you");
        } else {
            if (old == target) { GearAbilities.bar(p, ChatColor.GRAY + "You are already a " + target.title); return false; }
            if (old != GearMutation.BASELINE) {
                GearAbilities.bar(p, ChatColor.RED + "Purge your " + old.title + " mutation first" + ChatColor.GRAY + " (Purge Serum)");
                return false;
            }
            prof.mutation = target;
            mutated++;
            p.getWorld().playSound(at, Sound.ENTITY_ZOMBIE_VILLAGER_CONVERTED, 0.6f, 0.9f);
            p.getWorld().spawnParticle(Particle.SPELL_WITCH, at.clone().add(0, 1, 0), 30, 0.4, 0.8, 0.4, 0.05);
            p.sendMessage("§" + target.color + "Mutation: " + target.title + ChatColor.GRAY + " (" + target.inspiredBy + ")");
            for (String line : target.effects) p.sendMessage(ChatColor.GRAY + " " + line);
            GearAbilities.bar(p, "§" + target.color + "You mutated into a " + target.title);
        }
        prof.abilityReady = 0;
        prof.fadeUntil = prof.pounceUntil = prof.mistUntil = prof.chargeUntil = 0;
        prof.vitalsDirty = true;
        apply(p, prof);
        plugin.store.saveVitalsAsync(prof);
        plugin.getLogger().info("GEAR_MUTATION player=" + p.getUniqueId() + " from=" + old.id + " to=" + prof.mutation.id);
        return true;
    }

    // ================================================================ R key

    void key(Player p, GearProfile prof) {
        if (!GearAbilities.active(p)) return;
        GearMutation m = prof.mutation;
        if (m == GearMutation.BASELINE || m.ability == null) { GearAbilities.bar(p, ChatColor.GRAY + "No mutation - a Mutagen serum would give you one"); return; }
        long now = System.currentTimeMillis();
        if (now < prof.abilityReady) { GearAbilities.cooldown(p, m.ability, prof.abilityReady - now); return; }
        if (!plugin.vitals.afford(p, prof, m.cost, m.ability)) return;
        boolean ok;
        switch (m) {
            case BURROWER:
                plugin.abilities.scan(p, prof, 12, "Seismic Sense");
                p.getWorld().playSound(p.getLocation(), Sound.BLOCK_STONE_HIT, 0.8f, 0.5f);
                ok = true;
                break;
            case STALKER: ok = fade(p, prof, now); break;
            case FERAL: ok = pounce(p, prof, now); break;
            case SPRITE:
                prof.mistUntil = now + MIST_MS;
                mist(p);
                GearAbilities.bar(p, ChatColor.LIGHT_PURPLE + "Mending Mist" + ChatColor.GRAY + " - healing everyone close (6s)");
                ok = true;
                break;
            case SCAVENGER: ok = rummage(p); break;
            case BRUTE: ok = slam(p); break;
            case CHARGER:
                prof.chargeUntil = now + CHARGE_MS;
                prof.chargeHit.clear();
                p.getWorld().playSound(p.getLocation(), Sound.ENTITY_IRONGOLEM_ATTACK, 0.8f, 0.7f);
                GearAbilities.bar(p, ChatColor.YELLOW + "Stampede!");
                ok = true;
                break;
            case WYRM: ok = breath(p); break;
            default: ok = false;
        }
        if (!ok) return;
        prof.abilityReady = now + m.cooldownMs;
        GearAbilities.armReady(prof, m.ability, prof.abilityReady);
        plugin.vitals.spend(p, prof, m.cost);
        fired++;
    }

    private void hurt(Player p, LivingEntity target, double damage) {
        plugin.abilities.arcing = true;
        try { target.damage(damage, p); } finally { plugin.abilities.arcing = false; }
    }

    private boolean fade(Player p, GearProfile prof, long now) {
        prof.fadeUntil = now + FADE_MS;
        int lost = 0;
        for (Entity e : p.getNearbyEntities(32, 16, 32)) {
            if (e instanceof Creature && ((Creature) e).getTarget() == p && e.getLocation().distanceSquared(p.getLocation()) > 16) {
                ((Creature) e).setTarget(null);
                lost++;
            }
        }
        p.getWorld().spawnParticle(Particle.SMOKE_NORMAL, p.getLocation().add(0, 1, 0), 20, 0.3, 0.6, 0.3, 0.01);
        p.getWorld().playSound(p.getLocation(), Sound.BLOCK_CLOTH_STEP, 0.6f, 0.6f);
        GearAbilities.bar(p, ChatColor.DARK_GREEN + "Fade" + ChatColor.GRAY + " - " + lost + (lost == 1 ? " hostile lost" : " hostiles lost") + " you (8s)");
        return true;
    }

    private boolean pounce(Player p, GearProfile prof, long now) {
        Vector dir = p.getLocation().getDirection().setY(0);
        if (dir.lengthSquared() < 1e-4) dir = new Vector(0, 0, 1);
        p.setVelocity(dir.normalize().multiply(1.25).setY(0.45));
        prof.pounceUntil = now + POUNCE_MS;
        prof.pounceArmed = true;
        p.getWorld().playSound(p.getLocation(), Sound.ENTITY_CAT_HISS, 0.7f, 1.2f);
        return true;
    }

    private void mist(Player p) {
        Location c = p.getLocation();
        p.getWorld().spawnParticle(Particle.VILLAGER_HAPPY, c.clone().add(0, 1, 0), 14, 2.0, 0.6, 2.0, 0);
        for (Player other : p.getWorld().getPlayers()) {
            if (!GearAbilities.active(other) || other.getLocation().distanceSquared(c) > 25) continue;
            if (other.getHealth() < other.getMaxHealth()) other.setHealth(Math.min(other.getMaxHealth(), other.getHealth() + 1.0));
            plugin.vitals.cure(other, GearStatus.BLEED, "the mist closed the wound");
        }
    }

    private boolean rummage(Player p) {
        double roll = random.nextDouble();
        ItemStack found = null;
        if (roll < 0.35) {
            GearConsumable pick = GearApi.pickSupply(random, 1);
            if (pick != null) found = GearItems.create(pick);
        } else if (roll < 0.70) {
            switch (random.nextInt(4)) {
                case 0: found = new ItemStack(Material.IRON_NUGGET, 1 + random.nextInt(3)); break;
                case 1: found = new ItemStack(Material.STRING, 1 + random.nextInt(2)); break;
                case 2: found = new ItemStack(Material.SULPHUR, 1); break;
                default: found = new ItemStack(Material.ARROW, 2 + random.nextInt(3)); break;
            }
        }
        p.getWorld().playSound(p.getLocation(), Sound.BLOCK_GRAVEL_BREAK, 0.7f, 1.3f);
        if (found == null) { GearAbilities.bar(p, ChatColor.GREEN + "Rummage" + ChatColor.GRAY + " - nothing useful here"); return true; }
        String name = found.hasItemMeta() && found.getItemMeta().hasDisplayName()
            ? ChatColor.stripColor(found.getItemMeta().getDisplayName()) : GearAbilities.pretty(found.getType().name());
        for (ItemStack left : p.getInventory().addItem(found.clone()).values()) p.getWorld().dropItem(p.getLocation(), left);
        GearAbilities.bar(p, ChatColor.GREEN + "Rummage: " + ChatColor.WHITE + "found " + found.getAmount() + "x " + name);
        scrounges++;
        return true;
    }

    private boolean slam(Player p) {
        Location c = p.getLocation();
        int hit = 0;
        for (Entity e : p.getNearbyEntities(4, 2, 4)) {
            if (!(e instanceof LivingEntity) || e.isDead() || !GearAbilities.targetable(p, e)) continue;
            hurt(p, (LivingEntity) e, 4.0);
            Vector away = e.getLocation().toVector().subtract(c.toVector()).setY(0);
            if (away.lengthSquared() < 1e-4) away = new Vector(1, 0, 0);
            e.setVelocity(away.normalize().multiply(1.0).setY(0.5));
            hit++;
        }
        p.getWorld().spawnParticle(Particle.EXPLOSION_LARGE, c, 1, 0, 0, 0, 0);
        p.getWorld().spawnParticle(Particle.CRIT, c.clone().add(0, 0.2, 0), 30, 2.0, 0.1, 2.0, 0.1);
        p.getWorld().playSound(c, Sound.ENTITY_GENERIC_EXPLODE, 0.5f, 0.6f);
        GearAbilities.bar(p, ChatColor.RED + "Ground Slam: " + ChatColor.WHITE + hit + ChatColor.GRAY + " hit");
        return true;
    }

    private void charge(Player p, GearProfile prof) {
        Vector dir = p.getLocation().getDirection().setY(0);
        if (dir.lengthSquared() < 1e-4) return;
        dir.normalize();
        p.setVelocity(dir.clone().multiply(0.85).setY(Math.min(0.1, p.getVelocity().getY())));
        p.getWorld().spawnParticle(Particle.CLOUD, p.getLocation(), 4, 0.3, 0.05, 0.3, 0.01);
        for (Entity e : p.getNearbyEntities(1.6, 1.2, 1.6)) {
            if (!(e instanceof LivingEntity) || e.isDead() || !GearAbilities.targetable(p, e) || !prof.chargeHit.add(e.getUniqueId())) continue;
            hurt(p, (LivingEntity) e, 5.0);
            e.setVelocity(dir.clone().multiply(1.2).setY(0.4));
            p.getWorld().playSound(e.getLocation(), Sound.ENTITY_PLAYER_ATTACK_KNOCKBACK, 0.8f, 0.8f);
        }
    }

    private boolean breath(Player p) {
        Location eye = p.getEyeLocation();
        Vector dir = eye.getDirection().normalize();
        for (double d = 1.0; d <= 6.0; d += 0.5)
            p.getWorld().spawnParticle(Particle.FLAME, eye.clone().add(dir.clone().multiply(d)), 3, d * 0.12, d * 0.12, d * 0.12, 0.01);
        p.getWorld().playSound(eye, Sound.ENTITY_BLAZE_SHOOT, 0.8f, 0.8f);
        int hit = 0;
        for (Entity e : p.getNearbyEntities(6, 6, 6)) {
            if (!(e instanceof LivingEntity) || e.isDead() || !GearAbilities.targetable(p, e)) continue;
            LivingEntity le = (LivingEntity) e;
            Vector to = le.getEyeLocation().toVector().subtract(eye.toVector());
            if (to.length() > 6.5 || to.angle(dir) > Math.toRadians(35) || !p.hasLineOfSight(le)) continue;
            le.setFireTicks(Math.max(le.getFireTicks(), 80));
            hurt(p, le, 3.0);
            hit++;
        }
        GearAbilities.bar(p, ChatColor.DARK_PURPLE + "Fire Breath: " + ChatColor.WHITE + hit + ChatColor.GRAY + " scorched");
        return true;
    }

    // ================================================================ events

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onHurt(EntityDamageEvent e) {
        if (!(e.getEntity() instanceof Player)) return;
        Player p = (Player) e.getEntity();
        GearProfile prof = plugin.existing(p);
        if (prof == null || prof.mutation == GearMutation.BASELINE) return;
        GearMutation m = prof.mutation;
        switch (e.getCause()) {
            case SUFFOCATION:
                if (m == GearMutation.BURROWER) e.setCancelled(true);
                break;
            case FALLING_BLOCK:
                if (m == GearMutation.BURROWER) e.setDamage(e.getDamage() * 0.5);
                break;
            case FALL:
                if (m == GearMutation.SPRITE || (m == GearMutation.FERAL && System.currentTimeMillis() < prof.pounceUntil)) e.setCancelled(true);
                else if (m == GearMutation.FERAL) e.setDamage(e.getDamage() * 0.5);
                break;
            case FIRE: case FIRE_TICK: case HOT_FLOOR:
                if (m == GearMutation.WYRM) { e.setCancelled(true); p.setFireTicks(0); }
                break;
            case LAVA:
                if (m == GearMutation.WYRM) e.setDamage(e.getDamage() * 0.5);
                break;
            case ENTITY_ATTACK: case ENTITY_SWEEP_ATTACK:
                if (m == GearMutation.CHARGER) e.setDamage(e.getDamage() * 0.9);
                break;
            default:
                break;
        }
    }

    /** Feral fists and pounce strike; Stalker arrows. HIGH, like the trinket on-hit effects. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onHit(EntityDamageByEntityEvent e) {
        if (plugin.abilities.arcing || !(e.getEntity() instanceof LivingEntity)) return;
        Entity damager = e.getDamager();
        if (damager instanceof Player && e.getCause() == EntityDamageEvent.DamageCause.ENTITY_ATTACK) {
            Player p = (Player) damager;
            GearProfile prof = plugin.existing(p);
            if (prof == null || prof.mutation != GearMutation.FERAL) return;
            ItemStack hand = p.getInventory().getItemInMainHand();
            if (hand == null || hand.getType() == Material.AIR) e.setDamage(e.getDamage() + FERAL_FIST);
            if (prof.pounceArmed && System.currentTimeMillis() < prof.pounceUntil) {
                prof.pounceArmed = false;
                e.setDamage(e.getDamage() + POUNCE_BONUS);
                e.getEntity().getWorld().spawnParticle(Particle.CRIT, ((LivingEntity) e.getEntity()).getEyeLocation(), 12, 0.3, 0.3, 0.3, 0.1);
            }
        } else if (damager instanceof Arrow && ((Arrow) damager).getShooter() instanceof Player) {
            GearProfile prof = plugin.existing((Player) ((Arrow) damager).getShooter());
            if (prof != null && prof.mutation == GearMutation.STALKER) e.setDamage(e.getDamage() * STALKER_ARROW_DAMAGE);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBow(EntityShootBowEvent e) {
        if (!(e.getEntity() instanceof Player)) return;
        GearProfile prof = plugin.existing((Player) e.getEntity());
        if (prof != null && prof.mutation == GearMutation.STALKER && e.getProjectile() != null)
            e.getProjectile().setVelocity(e.getProjectile().getVelocity().multiply(STALKER_ARROW_SPEED));
    }

    /** Fade: hostiles farther than 4 blocks cannot pick you as a target (unless you hit them). */
    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onTarget(EntityTargetLivingEntityEvent e) {
        if (!(e.getTarget() instanceof Player) || e.getReason() == EntityTargetEvent.TargetReason.TARGET_ATTACKED_ENTITY) return;
        GearProfile prof = plugin.existing((Player) e.getTarget());
        if (prof == null || System.currentTimeMillis() >= prof.fadeUntil) return;
        if (e.getEntity().getLocation().distanceSquared(e.getTarget().getLocation()) > 16) e.setCancelled(true);
    }

    /** Burrower prospecting: 20% of ores mined without Silk Touch drop one extra of their drop. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent e) {
        Player p = e.getPlayer();
        GearProfile prof = plugin.existing(p);
        if (prof == null || prof.mutation != GearMutation.BURROWER || !survival(p) || !e.isDropItems()) return;
        Block b = e.getBlock();
        if (GearAbilities.oreName(b.getType()) == null) return;
        ItemStack tool = p.getInventory().getItemInMainHand();
        if (tool != null && tool.containsEnchantment(Enchantment.SILK_TOUCH)) return;
        if (random.nextDouble() >= PROSPECT_CHANCE) return;
        java.util.Collection<ItemStack> drops = b.getDrops(tool);
        if (drops.isEmpty()) return;
        ItemStack extra = drops.iterator().next().clone();
        extra.setAmount(1);
        b.getWorld().dropItemNaturally(b.getLocation().add(0.5, 0.5, 0.5), extra);
        prospects++;
    }

    /** Scavenger: 20% of kills drop one extra copy of an ordinary drop (never gear or supplies). */
    @EventHandler(priority = EventPriority.HIGH)
    public void onKill(EntityDeathEvent e) {
        LivingEntity dead = e.getEntity();
        Player killer = dead.getKiller();
        if (killer == null || dead instanceof Player || plugin.spawned(dead)) return;
        GearProfile prof = plugin.existing(killer);
        if (prof == null || prof.mutation != GearMutation.SCAVENGER || random.nextDouble() >= SCROUNGE_CHANCE) return;
        List<ItemStack> drops = e.getDrops();
        java.util.List<ItemStack> plain = new java.util.ArrayList<ItemStack>();
        for (ItemStack d : drops) if (!GearItems.empty(d) && GearItems.identify(d) == null && GearItems.consumable(d) == null) plain.add(d);
        if (plain.isEmpty()) return;
        ItemStack extra = plain.get(random.nextInt(plain.size())).clone();
        extra.setAmount(1);
        drops.add(extra);
        scrounges++;
    }

    @EventHandler(ignoreCancelled = true)
    public void onToggleFlight(PlayerToggleFlightEvent e) {
        if (!e.isFlying()) return;
        GearProfile prof = plugin.existing(e.getPlayer());
        if (prof == null || !prof.grantedFlight || !survival(e.getPlayer())) return;
        if (prof.adrenaline < FLIGHT_MIN_START) {
            e.setCancelled(true);
            GearAbilities.bar(e.getPlayer(), ChatColor.LIGHT_PURPLE + "Too tired to fly" + ChatColor.GRAY + " - needs " + (int) FLIGHT_MIN_START + " adrenaline");
        }
    }

    /** Feral legs: every jump goes a little higher (the Sprinter's Brace keeps its own launch). */
    @EventHandler(ignoreCancelled = true)
    public void onJump(PlayerJumpEvent e) {
        Player p = e.getPlayer();
        GearProfile prof = plugin.existing(p);
        if (prof == null || prof.mutation != GearMutation.FERAL || p.isSneaking() || !GearAbilities.active(p)) return;
        if (p.isSprinting() && prof.worn().contains(GearItem.SPRINT_BRACE)) return;
        Vector v = p.getVelocity();
        p.setVelocity(v.setY(Math.max(v.getY(), 0.52)));
    }

    String describe(GearProfile prof) {
        GearMutation m = prof.mutation;
        StringBuilder out = new StringBuilder("§").append(m.color).append("Mutation: ").append(m.title)
            .append(ChatColor.GRAY).append(" (").append(m.inspiredBy).append(")");
        if (m.ability != null) out.append(ChatColor.GRAY).append(" - [R] ").append(m.ability).append(", ").append(m.cost).append(" adrenaline, ")
            .append(m.cooldownMs / 1000).append("s");
        return out.toString();
    }

    String metrics() {
        return "mutated=" + mutated + " purged=" + purged + " mutationAbilities=" + fired + " prospects=" + prospects
            + " scrounges=" + scrounges + " flightGrounded=" + grounded;
    }
}
