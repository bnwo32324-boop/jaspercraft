package chat.jaspr.rpg;

import java.util.Random;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

/**
 * Everything an enhanced item does in a fight, plus how it earns its levels.
 *
 * Weapons gain experience by dealing damage, armour by taking it. Levelling hands out ability
 * tokens, and the abilities themselves are applied here, each scaled by its own level and by the
 * item's rarity.
 */
final class ArmamentListener implements Listener {
    private final RpgPlugin plugin;
    private final Random random = new Random();

    ArmamentListener(RpgPlugin plugin) { this.plugin = plugin; }

    private RpgConfig settings() { return plugin.settings(); }

    // ------------------------------------------------------------------ dealing damage

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onHit(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof LivingEntity)) return;
        Player attacker = attackerOf(event);
        if (attacker == null) return;

        ItemStack weapon = attacker.getInventory().getItemInMainHand();
        if (!Armament.isEnhanced(weapon) || !Armament.isWeapon(weapon)) return;

        LivingEntity victim = (LivingEntity) event.getEntity();
        Rarity rarity = Armament.rarity(weapon);

        // The rarity is a buff in its own right, applied before any ability is considered.
        if (rarity.bonus > 0.0d) event.setDamage(event.getDamage() * (1.0d + rarity.bonus));

        applyWeaponAbilities(attacker, victim, weapon, rarity, event);

        // Experience scales with the blow actually landed, so a real fight levels a weapon and
        // hitting a chicken repeatedly does not.
        int gained = (int) Math.max(1, Math.round(event.getFinalDamage()));
        ItemStack updated = award(attacker, weapon, gained);
        if (updated != null) attacker.getInventory().setItemInMainHand(updated);
    }

    private Player attackerOf(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Player) return (Player) event.getDamager();
        if (event.getDamager() instanceof Projectile) {
            Projectile shot = (Projectile) event.getDamager();
            if (shot instanceof Arrow && shot.getShooter() instanceof Player) return (Player) shot.getShooter();
        }
        return null;
    }

    private void applyWeaponAbilities(Player attacker, LivingEntity victim, ItemStack weapon,
                                      Rarity rarity, EntityDamageByEntityEvent event) {
        double scale = rarity.effect;

        int fire = Armament.abilityLevel(weapon, AbilityType.FIRE);
        if (fire > 0) victim.setFireTicks(Math.max(victim.getFireTicks(), (int) (fire * 40 * scale)));

        int frost = Armament.abilityLevel(weapon, AbilityType.FROST);
        if (frost > 0) {
            victim.addPotionEffect(new PotionEffect(PotionEffectType.SLOW,
                    (int) (frost * 40 * scale), Math.min(4, frost), true, true), true);
        }

        int poison = Armament.abilityLevel(weapon, AbilityType.POISON);
        if (poison > 0) {
            victim.addPotionEffect(new PotionEffect(PotionEffectType.POISON,
                    (int) (poison * 40 * scale), Math.min(2, poison - 1), true, true), true);
        }

        int innate = Armament.abilityLevel(weapon, AbilityType.INNATE);
        if (innate > 0) {
            // A wound that keeps bleeding, and stacks the more it is reopened.
            victim.addPotionEffect(new PotionEffect(PotionEffectType.WITHER,
                    (int) (innate * 30 * scale), 0, true, true), true);
        }

        int illumination = Armament.abilityLevel(weapon, AbilityType.ILLUMINATION);
        if (illumination > 0) {
            victim.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS,
                    (int) (illumination * 60 * scale), 0, true, true), true);
        }

        int critical = Armament.abilityLevel(weapon, AbilityType.CRITICAL_POINT);
        if (critical > 0 && random.nextInt(100) < critical * 5 * scale) {
            double maxHealth = victim.getMaxHealth();
            double extra = maxHealth * 0.05d * critical * scale;
            event.setDamage(event.getDamage() + extra);
            attacker.playSound(attacker.getLocation(), org.bukkit.Sound.ENTITY_PLAYER_ATTACK_CRIT, 0.8f, 1.2f);
        }

        int bombastic = Armament.abilityLevel(weapon, AbilityType.BOMBASTIC);
        if (bombastic > 0 && random.nextInt(100) < bombastic * 8) {
            victim.getWorld().createExplosion(victim.getLocation().getX(), victim.getLocation().getY(),
                    victim.getLocation().getZ(), (float) (bombastic * 0.8d * scale), false, false);
        }

        int bloodthirst = Armament.abilityLevel(weapon, AbilityType.BLOODTHIRST);
        if (bloodthirst > 0) {
            double healed = event.getFinalDamage() * 0.08d * bloodthirst * scale;
            attacker.setHealth(Math.min(attacker.getMaxHealth(), attacker.getHealth() + healed));
        }
    }

    // ------------------------------------------------------------------ taking damage

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onHurt(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player)) return;
        Player player = (Player) event.getEntity();

        ItemStack[] armour = player.getInventory().getArmorContents();
        boolean changed = false;

        for (int slot = 0; slot < armour.length; slot++) {
            ItemStack piece = armour[slot];
            if (!Armament.isEnhanced(piece) || !Armament.isArmour(piece)) continue;
            Rarity rarity = Armament.rarity(piece);
            double scale = rarity.effect;

            // Each enhanced piece turns aside a share of the blow, by its rarity alone.
            if (rarity.bonus > 0.0d) {
                event.setDamage(event.getDamage() * (1.0d - Math.min(0.55d, rarity.bonus * 0.55d)));
            }

            int hardened = Armament.abilityLevel(piece, AbilityType.HARDENED);
            if (hardened > 0 && random.nextInt(100) < hardened * 4 * scale) {
                event.setCancelled(true);
                player.playSound(player.getLocation(), org.bukkit.Sound.ITEM_SHIELD_BLOCK, 0.9f, 1.0f);
                return;
            }

            int adrenaline = Armament.abilityLevel(piece, AbilityType.ADRENALINE);
            if (adrenaline > 0 && random.nextInt(100) < adrenaline * 8 * scale) {
                player.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION,
                        (int) (adrenaline * 60 * scale), 0, true, true), true);
            }

            if (event instanceof EntityDamageByEntityEvent) {
                LivingEntity striker = strikerOf((EntityDamageByEntityEvent) event);
                if (striker != null) {
                    int molten = Armament.abilityLevel(piece, AbilityType.MOLTEN);
                    if (molten > 0) striker.setFireTicks(Math.max(striker.getFireTicks(), (int) (molten * 40 * scale)));

                    int frozen = Armament.abilityLevel(piece, AbilityType.FROZEN);
                    if (frozen > 0) striker.addPotionEffect(new PotionEffect(PotionEffectType.SLOW,
                            (int) (frozen * 40 * scale), Math.min(3, frozen), true, true), true);

                    int toxic = Armament.abilityLevel(piece, AbilityType.TOXIC);
                    if (toxic > 0) striker.addPotionEffect(new PotionEffect(PotionEffectType.POISON,
                            (int) (toxic * 40 * scale), 0, true, true), true);
                }
            }

            // Armour earns its keep by being hit.
            int gained = (int) Math.max(1, Math.round(event.getFinalDamage()));
            ItemStack updated = award(player, piece, gained);
            if (updated != null) { armour[slot] = updated; changed = true; }
        }

        if (changed) player.getInventory().setArmorContents(armour);
    }

    private LivingEntity strikerOf(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof LivingEntity) return (LivingEntity) event.getDamager();
        if (event.getDamager() instanceof Projectile) {
            Projectile shot = (Projectile) event.getDamager();
            if (shot.getShooter() instanceof LivingEntity) return (LivingEntity) shot.getShooter();
        }
        return null;
    }

    /** Beastial and Remedial are standing effects rather than reactions; the plugin ticks them. */
    void tickArmour(Player player) {
        for (ItemStack piece : player.getInventory().getArmorContents()) {
            if (!Armament.isEnhanced(piece) || !Armament.isArmour(piece)) continue;
            double scale = Armament.rarity(piece).effect;

            int remedial = Armament.abilityLevel(piece, AbilityType.REMEDIAL);
            if (remedial > 0 && player.getHealth() < player.getMaxHealth()) {
                player.setHealth(Math.min(player.getMaxHealth(), player.getHealth() + 0.5d * remedial * scale));
            }

            int beastial = Armament.abilityLevel(piece, AbilityType.BEASTIAL);
            if (beastial > 0 && player.getHealth() <= player.getMaxHealth() * 0.35d) {
                player.addPotionEffect(new PotionEffect(PotionEffectType.INCREASE_DAMAGE,
                        60, Math.min(2, beastial - 1), true, false), true);
            }
        }
    }

    // ------------------------------------------------------------------ kills and acquisition

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onKill(EntityDeathEvent event) {
        Player killer = event.getEntity().getKiller();
        if (killer == null) return;
        ItemStack weapon = killer.getInventory().getItemInMainHand();
        if (!Armament.isEnhanced(weapon)) return;

        int ethereal = Armament.abilityLevel(weapon, AbilityType.ETHEREAL);
        if (ethereal > 0 && weapon.getDurability() > 0) {
            short repaired = (short) Math.max(0, weapon.getDurability() - ethereal * 8);
            weapon.setDurability(repaired);
        }
        ItemStack updated = award(killer, weapon, 10);
        if (updated != null) killer.getInventory().setItemInMainHand(updated);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent event) {
        if (!(event.getEntity() instanceof Player)) return;
        ItemStack item = event.getItem().getItemStack();
        if (!Armament.isEligible(item) || Armament.isEnhanced(item)) return;
        ItemStack rolled = Armament.maybeEnhance(item, settings(), random);
        if (rolled != item) event.getItem().setItemStack(rolled);
    }

    /**
     * Pulling gear out of the creative menu is a creation like any other.
     *
     * The cursor is what the player is about to be holding, so it is replaced in place rather than
     * chased down a tick later, which is what stopped this working before. Creative gets its own
     * roll chance, defaulting to certain, because creative is where gear is spawned to be tested.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onCreative(org.bukkit.event.inventory.InventoryCreativeEvent event) {
        ItemStack item = event.getCursor();
        if (!Armament.isEligible(item) || Armament.isEnhanced(item)) return;
        ItemStack rolled = Armament.maybeEnhance(item, settings(), random, settings().creativeChance);
        if (rolled != item) event.setCursor(rolled);
    }

    /** Results taken out of a furnace, an anvil or any other output slot count as created too. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTakeResult(org.bukkit.event.inventory.InventoryClickEvent event) {
        if (event.getSlotType() != org.bukkit.event.inventory.InventoryType.SlotType.RESULT) return;
        if (!(event.getWhoClicked() instanceof Player)) return;
        final ItemStack item = event.getCurrentItem();
        if (!Armament.isEligible(item) || Armament.isEnhanced(item)) return;
        final ItemStack rolled = Armament.maybeEnhance(item, settings(), random);
        if (rolled != item) event.setCurrentItem(rolled);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onCraft(CraftItemEvent event) {
        final ItemStack result = event.getCurrentItem();
        if (!Armament.isEligible(result) || Armament.isEnhanced(result)) return;
        ItemStack rolled = Armament.maybeEnhance(result, settings(), random);
        if (rolled != result) event.setCurrentItem(rolled);
    }

    // ------------------------------------------------------------------ levelling

    /** Adds experience to an item, announcing and granting tokens on each level gained. */
    private ItemStack award(Player owner, ItemStack item, int amount) {
        RpgConfig config = settings();
        int[] out = new int[2];
        int gained = Armament.addExperience(item, amount, config, out);
        if (gained <= 0 && out[0] == 0) return null;

        int tokens = Armament.tokens(item) + gained * config.tokensPerLevel;
        ItemStack updated = Armament.setLevel(item, out[0], out[1], tokens);

        if (gained > 0) {
            owner.sendMessage(Armament.rarity(item).coloured() + " " + displayName(item)
                    + ChatColor.GRAY + " reached level " + ChatColor.WHITE + out[0]
                    + ChatColor.GRAY + (config.tokensPerLevel > 0
                        ? "  (+" + (gained * config.tokensPerLevel) + " token)" : ""));
            owner.playSound(owner.getLocation(), org.bukkit.Sound.ENTITY_PLAYER_LEVELUP, 0.5f, 1.8f);
        }
        return updated;
    }

    private String displayName(ItemStack item) {
        if (item.hasItemMeta() && item.getItemMeta().hasDisplayName()) return item.getItemMeta().getDisplayName();
        String raw = item.getType().name().replace('_', ' ').toLowerCase(java.util.Locale.ROOT);
        return Character.toUpperCase(raw.charAt(0)) + raw.substring(1);
    }
}
