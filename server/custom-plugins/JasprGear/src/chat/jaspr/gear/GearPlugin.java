package chat.jaspr.gear;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.io.File;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.craftbukkit.v1_12_R1.entity.CraftPlayer;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Zombie;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerGameModeChangeEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.Recipe;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.plugin.messaging.PluginMessageListener;

/**
 * Survivor Gear: seven typed trinket slots per player (neck, ring, ring, belt, head, body,
 * charm), fifteen trinkets, a browser inventory panel over the jaspr:gear plugin channel and a
 * server-side /gear menu for every other client. The server is authoritative for every move.
 */
public final class GearPlugin extends JavaPlugin implements Listener, PluginMessageListener, TabCompleter {
    public static final String CHANNEL = "jaspr:gear";
    static final int PROTOCOL = 1;

    private final Map<UUID, GearProfile> profiles = new HashMap<UUID, GearProfile>();
    private final Map<Player, List<ItemStack>> deathDrops = new IdentityHashMap<Player, List<ItemStack>>();
    private final Random random = new Random();
    GearStore store;
    GearAbilities abilities;
    GearVitals vitals;
    private int tick, recipes, equips, unequips, mobDrops, supplyDrops, deathDropCount, hellos, rejected;
    private int supplyRecipes;
    private Method authGetter, authCheck, spawnerCheck;
    private boolean authMissing, spawnerMissing;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        store = new GearStore(new File(getDataFolder(), "players"), getLogger());
        abilities = new GearAbilities(this);
        vitals = new GearVitals(this);
        registerRecipes();
        getServer().getPluginManager().registerEvents(this, this);
        getServer().getPluginManager().registerEvents(abilities, this);
        getServer().getPluginManager().registerEvents(vitals, this);
        getServer().getMessenger().registerIncomingPluginChannel(this, CHANNEL, this);
        getCommand("gear").setExecutor(this);
        getCommand("gear").setTabCompleter(this);
        for (Player p : getServer().getOnlinePlayers()) { profile(p); abilities.apply(p, profile(p)); }
        getServer().getScheduler().runTaskTimer(this, () -> {
            tick += 2;
            long now = System.currentTimeMillis();
            for (Player p : getServer().getOnlinePlayers()) {
                GearProfile prof = profiles.get(p.getUniqueId());
                if (prof != null) { abilities.fastTick(p, prof, now, tick); vitals.fast(p, prof, now); }
            }
            if (tick % 20 == 0) {
                for (Player p : getServer().getOnlinePlayers()) {
                    GearProfile prof = profiles.get(p.getUniqueId());
                    if (prof != null) { abilities.apply(p, prof); abilities.slowTick(p, prof, now); vitals.second(p, prof, now); }
                }
                vitals.mobSecond(now);
            }
        }, 20L, 2L);
        getServer().getScheduler().runTaskTimer(this, () -> vitals.tick(System.currentTimeMillis()), 20L, 1L);
        getLogger().info("GEAR_READY items=" + GearItem.values().length + " consumables=" + GearConsumable.values().length
            + " statuses=" + GearStatus.values().length + " slots=" + GearType.SLOT_COUNT
            + " recipes=" + recipes + " channel=" + CHANNEL + " protocol=" + PROTOCOL);
        if (Boolean.getBoolean("jaspr.gear.selftest")) getServer().getScheduler().runTask(this, () -> new GearSelfTest(this).run(getServer().getConsoleSender()));
    }

    @Override
    public void onDisable() {
        for (Player p : getServer().getOnlinePlayers()) {
            GearProfile prof = profiles.get(p.getUniqueId());
            if (prof == null) continue;
            abilities.clear(p, prof);
            vitals.clear(p);
            if (p.getOpenInventory().getTopInventory().getHolder() instanceof MenuHolder) p.closeInventory();
        }
        for (GearProfile prof : profiles.values()) {
            if (!prof.frozen) store.saveAsync(prof); // an unreadable gear file is never overwritten
            store.saveVitalsAsync(prof);
        }
        store.flush();
        getLogger().info("GEAR_STOPPED profiles=" + profiles.size() + " writes=" + store.writes + " failures=" + store.failures);
    }

    // ------------------------------------------------------------------ profiles

    GearProfile profile(Player p) {
        GearProfile prof = profiles.get(p.getUniqueId());
        if (prof == null) {
            prof = new GearProfile(p.getUniqueId());
            prof.frozen = !store.load(prof);
            store.loadVitals(prof, System.currentTimeMillis());
            profiles.put(p.getUniqueId(), prof);
        }
        return prof;
    }

    /** The loaded profile, or null (never touches the disk; for hot event paths). */
    GearProfile existing(Player p) { return profiles.get(p.getUniqueId()); }

    void saveLater(GearProfile prof) {
        store.saveVitalsAsync(prof); // own file, so an unreadable gear file never blocks it
        if (prof.frozen) { getLogger().warning("GEAR_SAVE_SKIPPED player=" + prof.uuid + " reason=unreadable-file"); return; }
        store.saveAsync(prof);
    }

    boolean authenticated(Player p) {
        if (authMissing) return true;
        try {
            if (authGetter == null) {
                Class<?> api = Class.forName("fr.xephi.authme.api.v3.AuthMeApi", true, getClassLoader());
                authGetter = api.getMethod("getInstance");
                authCheck = api.getMethod("isAuthenticated", Player.class);
            }
            Object instance = authGetter.invoke(null);
            return instance != null && Boolean.TRUE.equals(authCheck.invoke(instance, p));
        } catch (ClassNotFoundException e) {
            authMissing = getServer().getPluginManager().getPlugin("AuthMe") == null;
            return authMissing;
        } catch (Exception e) {
            return false;
        }
    }

    private boolean usable(Player p) {
        return p.isOnline() && !p.isDead() && p.getGameMode() != GameMode.SPECTATOR && authenticated(p);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent e) {
        final Player p = e.getPlayer();
        GearProfile prof = profile(p);
        prof.capable = false;
        prof.magnet = false;
        vitals.resume(prof, System.currentTimeMillis());
        getServer().getScheduler().runTask(this, () -> { if (p.isOnline()) { abilities.apply(p, prof); vitals.speed(p, prof, System.currentTimeMillis()); } });
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent e) {
        GearProfile prof = profiles.get(e.getPlayer().getUniqueId());
        if (prof == null) return;
        abilities.clear(e.getPlayer(), prof);
        abilities.forget(prof.uuid);
        vitals.clear(e.getPlayer());
        prof.capable = false;
        saveLater(prof);
        vitals.suspend(prof, System.currentTimeMillis());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onRespawn(PlayerRespawnEvent e) { later(e.getPlayer()); }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onWorld(PlayerChangedWorldEvent e) { later(e.getPlayer()); }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMode(PlayerGameModeChangeEvent e) { later(e.getPlayer()); }

    private void later(final Player p) {
        getServer().getScheduler().runTask(this, () -> {
            if (!p.isOnline()) return;
            GearProfile prof = profile(p);
            abilities.apply(p, prof);
            sendState(p, prof);
            prof.hudSig = null;
            vitals.pushHud(p, prof);
        });
    }

    // ------------------------------------------------------------------ equip / unequip

    /** Shared click rule for the browser panel and the /gear menu. */
    boolean click(Player p, GearProfile prof, int slot, boolean shift) {
        if (slot < 0 || slot >= GearType.SLOT_COUNT) return false;
        ItemStack current = prof.slots[slot];
        if (shift) {
            if (GearItems.empty(current)) return false;
            if (!p.getInventory().addItem(current.clone()).isEmpty()) {
                // addItem may have placed nothing for an unstackable single; nothing was lost.
                GearAbilities.bar(p, ChatColor.RED + "Your inventory is full");
                return false;
            }
            prof.slots[slot] = null;
            changed(p, prof, false, slot, current);
            return true;
        }
        ItemStack cursor = p.getItemOnCursor();
        if (GearItems.empty(cursor)) {
            if (GearItems.empty(current)) return false;
            prof.slots[slot] = null;
            p.setItemOnCursor(current);
            changed(p, prof, false, slot, current);
            return true;
        }
        GearItem item = GearItems.identify(cursor);
        if (item == null || cursor.getAmount() != 1 || !item.type.fits(slot)) {
            GearAbilities.bar(p, ChatColor.RED + "Only " + GearType.SLOTS[slot].label.toLowerCase(Locale.ROOT)
                + " or any-slot gear fits there");
            p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BASS, 0.6f, 0.6f);
            return false;
        }
        prof.slots[slot] = cursor.clone();
        p.setItemOnCursor(GearItems.empty(current) ? null : current);
        changed(p, prof, true, slot, prof.slots[slot]);
        return true;
    }

    private void changed(Player p, GearProfile prof, boolean equipped, int slot, ItemStack stack) {
        GearItem item = GearItems.identify(stack);
        if (equipped) equips++; else unequips++;
        getLogger().info((equipped ? "GEAR_EQUIP" : "GEAR_UNEQUIP") + " player=" + p.getUniqueId() + " slot=" + slot
            + " item=" + (item == null ? "unknown" : item.id));
        p.playSound(p.getLocation(), Sound.ITEM_ARMOR_EQUIP_GENERIC, 0.7f, equipped ? 1.2f : 0.8f);
        saveLater(prof);
        abilities.apply(p, prof);
        sendState(p, prof);
        refreshMenu(p, prof);
    }

    private boolean equipStack(Player p, GearProfile prof, ItemStack stack) {
        GearItem item = GearItems.identify(stack);
        if (item == null || stack.getAmount() != 1) return false;
        int free = prof.freeSlotFor(item);
        if (free < 0) {
            GearAbilities.bar(p, ChatColor.RED + "No free " + (item.type == GearType.ANY ? "gear" : item.type.label.toLowerCase(Locale.ROOT)) + " slot");
            return false;
        }
        prof.slots[free] = stack.clone();
        changed(p, prof, true, free, prof.slots[free]);
        return true;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onUse(PlayerInteractEvent e) {
        Action action = e.getAction();
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) return;
        ItemStack stack = e.getItem();
        GearConsumable supply = GearItems.consumable(stack);
        if (supply != null) { useConsumable(e, supply); return; }
        if (GearItems.identify(stack) == null && !GearItems.isIcon(stack)) return;
        e.setUseItemInHand(Event.Result.DENY); // gear is never a working hoe
        if (GearItems.isIcon(stack)) return;
        Player p = e.getPlayer();
        if (action == Action.RIGHT_CLICK_BLOCK && e.getClickedBlock() != null && !p.isSneaking()
            && interactive(e.getClickedBlock().getType())) return;
        e.setCancelled(true);
        long now = System.currentTimeMillis();
        GearProfile prof = profile(p);
        if (now - prof.handEquipAt < 150 || !usable(p)) return;
        prof.handEquipAt = now;
        if (equipStack(p, prof, stack)) {
            if (e.getHand() == EquipmentSlot.OFF_HAND) p.getInventory().setItemInOffHand(null);
            else p.getInventory().setItemInMainHand(null);
        }
    }

    /** Right-click with a consumable: one dose, never a working hoe; doors/chests still open. */
    private void useConsumable(PlayerInteractEvent e, GearConsumable supply) {
        e.setUseItemInHand(Event.Result.DENY);
        Player p = e.getPlayer();
        if (e.getAction() == Action.RIGHT_CLICK_BLOCK && e.getClickedBlock() != null && !p.isSneaking()
            && interactive(e.getClickedBlock().getType())) return;
        e.setCancelled(true);
        long now = System.currentTimeMillis();
        GearProfile prof = profile(p);
        if (now < prof.useReady || !usable(p)) return;
        prof.useReady = now + GearVitals.USE_COOLDOWN_MS;
        boolean off = e.getHand() == EquipmentSlot.OFF_HAND;
        ItemStack held = off ? p.getInventory().getItemInOffHand() : p.getInventory().getItemInMainHand();
        if (GearItems.consumable(held) != supply || held.getAmount() != 1) return;
        if (!vitals.use(p, prof, supply)) return;
        getLogger().info("GEAR_CONSUME player=" + p.getUniqueId() + " item=" + supply.id);
        if (p.getGameMode() == GameMode.CREATIVE) return; // Creative keeps its supplies, like vanilla
        ItemStack after = GearItems.afterDose(held);
        if (off) p.getInventory().setItemInOffHand(after); else p.getInventory().setItemInMainHand(after);
    }

    static boolean interactive(Material m) {
        String n = m.name();
        return m.isBlock() && (n.contains("DOOR") || n.contains("GATE") || n.contains("CHEST") || n.contains("BUTTON")
            || n.contains("LEVER") || n.contains("BED") || n.contains("FURNACE") || n.contains("TABLE") || n.contains("ANVIL")
            || n.contains("SHULKER") || n.contains("HOPPER") || n.contains("DISPENSER") || n.contains("DROPPER")
            || n.contains("STAND") || n.contains("BEACON") || n.contains("JUKEBOX") || n.contains("NOTE") || n.contains("CAKE")
            || n.contains("DIODE") || n.contains("COMPARATOR") || n.contains("WORKBENCH") || n.contains("SIGN")
            || n.contains("CAULDRON") || n.contains("FLOWER_POT") || n.contains("COMMAND") || n.contains("STRUCTURE"));
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player)) return;
        final Player p = (Player) e.getWhoClicked();
        Inventory top = e.getView().getTopInventory();
        if (top.getHolder() instanceof MenuHolder) { menuClick(e, p, (MenuHolder) top.getHolder()); return; }
        if (e.isCancelled() || e.getView().getType() != InventoryType.CRAFTING || !e.isShiftClick()) return;
        if (!(e.getClickedInventory() instanceof PlayerInventory) || e.getSlot() >= 36) return;
        GearProfile prof = profile(p);
        if (!prof.capable || p.getGameMode() == GameMode.CREATIVE) return;
        final ItemStack snapshot = e.getCurrentItem();
        GearItem item = GearItems.identify(snapshot);
        if (item == null || prof.freeSlotFor(item) < 0) return;
        e.setCancelled(true);
        final int invSlot = e.getSlot();
        final ItemStack copy = snapshot.clone();
        getServer().getScheduler().runTask(this, () -> fromInventory(p, invSlot, copy));
    }

    private void fromInventory(Player p, int invSlot, ItemStack expected) {
        if (!p.isOnline() || !usable(p)) return;
        ItemStack now = p.getInventory().getItem(invSlot);
        if (now == null || !now.isSimilar(expected) || now.getAmount() != 1) { p.updateInventory(); return; }
        GearProfile prof = profile(p);
        if (equipStack(p, prof, now)) p.getInventory().setItem(invSlot, null);
        p.updateInventory();
    }

    private void menuClick(InventoryClickEvent e, final Player p, final MenuHolder holder) {
        int raw = e.getRawSlot();
        int topSize = e.getView().getTopInventory().getSize();
        ClickType click = e.getClick();
        boolean inTop = raw >= 0 && raw < topSize;
        if (click == ClickType.DOUBLE_CLICK || e.getAction() == InventoryAction.COLLECT_TO_CURSOR) { e.setCancelled(true); return; }
        if (!inTop) {
            if (!e.isShiftClick()) return; // ordinary inventory management stays vanilla
            e.setCancelled(true);
            final ItemStack snapshot = e.getCurrentItem();
            if (GearItems.identify(snapshot) == null || !(e.getClickedInventory() instanceof PlayerInventory)) return;
            final int invSlot = e.getSlot();
            final ItemStack copy = snapshot.clone();
            getServer().getScheduler().runTask(this, () -> { fromInventory(p, invSlot, copy); refreshMenu(p, profile(p)); });
            return;
        }
        e.setCancelled(true);
        if (raw == 7) {
            final boolean deposit = !e.isRightClick();
            final int levels = e.isShiftClick() ? Integer.MAX_VALUE : 1;
            getServer().getScheduler().runTask(this, () -> {
                if (!p.isOnline() || p.getOpenInventory().getTopInventory().getHolder() != holder || !usable(p)) return;
                bank(p, profile(p), deposit, levels);
            });
            return;
        }
        if (raw >= GearType.SLOT_COUNT) return;
        final int slot = raw;
        final boolean shift = e.isShiftClick();
        final int hotbar = click == ClickType.NUMBER_KEY ? e.getHotbarButton() : -1;
        getServer().getScheduler().runTask(this, () -> {
            if (!p.isOnline() || p.getOpenInventory().getTopInventory().getHolder() != holder || !usable(p)) return;
            GearProfile prof = profile(p);
            if (hotbar >= 0) hotbarSwap(p, prof, slot, hotbar); else click(p, prof, slot, shift);
            refreshMenu(p, prof);
            p.updateInventory();
        });
    }

    private void hotbarSwap(Player p, GearProfile prof, int slot, int hotbar) {
        ItemStack held = p.getInventory().getItem(hotbar);
        ItemStack current = prof.slots[slot];
        if (!GearItems.empty(held)) {
            GearItem item = GearItems.identify(held);
            if (item == null || held.getAmount() != 1 || !item.type.fits(slot)) return;
        } else if (GearItems.empty(current)) return;
        prof.slots[slot] = GearItems.empty(held) ? null : held.clone();
        p.getInventory().setItem(hotbar, GearItems.empty(current) ? null : current);
        changed(p, prof, !GearItems.empty(held), slot, GearItems.empty(held) ? current : prof.slots[slot]);
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onDrag(InventoryDragEvent e) {
        if (!(e.getView().getTopInventory().getHolder() instanceof MenuHolder)) return;
        int topSize = e.getView().getTopInventory().getSize();
        for (int raw : e.getRawSlots()) if (raw < topSize) { e.setCancelled(true); return; }
    }

    // ------------------------------------------------------------------ menu (all clients)

    static final class MenuHolder implements InventoryHolder {
        final UUID owner;
        Inventory inventory;
        MenuHolder(UUID owner) { this.owner = owner; }
        @Override public Inventory getInventory() { return inventory; }
    }

    void openMenu(Player p) {
        MenuHolder holder = new MenuHolder(p.getUniqueId());
        holder.inventory = Bukkit.createInventory(holder, 9, "Survivor Gear");
        render(holder.inventory, profile(p));
        p.openInventory(holder.inventory);
    }

    void render(Inventory inv, GearProfile prof) {
        for (int i = 0; i < GearType.SLOT_COUNT; i++)
            inv.setItem(i, GearItems.empty(prof.slots[i]) ? GearItems.icon(i) : prof.slots[i].clone());
        int journal = journalSlot(prof);
        inv.setItem(7, named(new ItemStack(Material.EXP_BOTTLE), ChatColor.GOLD + "XP Bank",
            journal < 0 ? ChatColor.GRAY + "Wear a Field Journal to bank XP"
                : ChatColor.WHITE + "Stored: " + GearItems.storedXp(prof.slots[journal]) + " / " + BANK_CAP + " XP",
            ChatColor.GRAY + "Left-click: deposit 1 level",
            ChatColor.GRAY + "Right-click: withdraw 1 level",
            ChatColor.DARK_GRAY + "Shift-click: everything"));
        inv.setItem(8, named(new ItemStack(Material.BOOK), ChatColor.GOLD + "Survivor Gear",
            ChatColor.GRAY + "Click a slot with gear on the cursor to wear it",
            ChatColor.GRAY + "Click worn gear to take it back",
            ChatColor.GRAY + "Shift-click gear below to equip it",
            ChatColor.GRAY + "Right-click gear in your hand to equip",
            ChatColor.DARK_GRAY + "Keys G arc, H dodge/blink, J magnet",
            ChatColor.DARK_GRAY + "Abilities spend adrenaline: /gear vitals"));
    }

    private void refreshMenu(Player p, GearProfile prof) {
        Inventory top = p.getOpenInventory().getTopInventory();
        if (top.getHolder() instanceof MenuHolder) render(top, prof);
    }

    private static ItemStack named(ItemStack stack, String name, String... lore) {
        ItemMeta meta = stack.getItemMeta();
        meta.setDisplayName(name);
        if (lore.length > 0) meta.setLore(Arrays.asList(lore));
        stack.setItemMeta(meta);
        return stack;
    }

    // ------------------------------------------------------------------ browser panel channel

    @Override
    public void onPluginMessageReceived(String channel, Player p, byte[] data) {
        if (!CHANNEL.equals(channel) || p == null) return;
        GearProfile prof = profile(p);
        if (!prof.allowMessage(System.currentTimeMillis(), 20)) {
            if (!prof.rateWarned) { prof.rateWarned = true; getLogger().warning("GEAR_NET_RATE_LIMIT player=" + p.getUniqueId()); }
            return;
        }
        String text = decode(data);
        if (text == null || text.length() > 64) { rejected++; return; }
        String[] parts = text.split(" ");
        try {
            switch (parts[0]) {
                case "hello":
                    boolean first = !prof.capable;
                    prof.capable = true;
                    prof.protocol = parts.length > 1 ? Integer.parseInt(parts[1]) : 0;
                    if (first) { hellos++; getLogger().info("GEAR_CLIENT_HELLO player=" + p.getUniqueId() + " protocol=" + prof.protocol); }
                    sendState(p, prof);
                    prof.hudSig = null;
                    vitals.pushHud(p, prof); // protocol 2+: adrenaline bar and status HUD
                    break;
                case "click":
                    if (parts.length < 4 || !usable(p) || p.getGameMode() == GameMode.CREATIVE
                        || p.getOpenInventory().getType() != InventoryType.CRAFTING) { rejected++; sendState(p, prof); return; }
                    click(p, prof, Integer.parseInt(parts[1]), "1".equals(parts[3]));
                    sendState(p, prof);
                    break;
                case "key":
                    if (parts.length >= 2 && usable(p)) abilities.key(p, prof, parts[1]);
                    break;
                default:
                    rejected++;
            }
        } catch (NumberFormatException e) {
            rejected++;
        }
    }

    static String decode(byte[] data) {
        if (data == null || data.length < 1 || data.length > 300) return null;
        int value = 0, shift = 0, at = 0;
        while (true) {
            if (at >= data.length || shift > 28) return null;
            byte b = data[at++];
            value |= (b & 0x7F) << shift;
            if ((b & 0x80) == 0) break;
            shift += 7;
        }
        if (value < 0 || at + value != data.length) return null;
        return new String(data, at, value, StandardCharsets.UTF_8);
    }

    static String stateJson(GearProfile prof) {
        JsonObject root = new JsonObject();
        root.addProperty("v", PROTOCOL);
        JsonArray slots = new JsonArray();
        int budget = 30000;
        for (ItemStack stack : prof.slots) {
            String snbt = GearItems.toSnbt(stack);
            if (snbt.length() > 4000 || snbt.length() > budget) {
                GearItem item = GearItems.identify(stack);
                snbt = item == null ? "" : GearItems.canonicalTag(item).toString();
            }
            budget -= snbt.length();
            slots.add(snbt);
        }
        root.add("slots", slots);
        return root.toString();
    }

    void sendState(Player p, GearProfile prof) {
        if (!prof.capable || !p.isOnline()) return;
        sendRaw(p, stateJson(prof));
    }

    /** One jaspr:gear payload (writeString JSON) to a client that said hello. */
    boolean sendRaw(Player p, String json) {
        try {
            net.minecraft.server.v1_12_R1.PacketDataSerializer buf =
                new net.minecraft.server.v1_12_R1.PacketDataSerializer(io.netty.buffer.Unpooled.buffer());
            buf.a(json);
            ((CraftPlayer) p).getHandle().playerConnection.sendPacket(
                new net.minecraft.server.v1_12_R1.PacketPlayOutCustomPayload(CHANNEL, buf));
            return true;
        } catch (RuntimeException error) {
            getLogger().warning("GEAR_SYNC_FAILED player=" + p.getUniqueId() + " error=" + error.getClass().getSimpleName());
            return false;
        }
    }

    // ------------------------------------------------------------------ death, drops, crafting

    @EventHandler(priority = EventPriority.NORMAL)
    public void onDeath(PlayerDeathEvent e) {
        Player p = e.getEntity();
        GearProfile prof = profile(p);
        abilities.clear(p, prof);
        prof.shieldHits = 0;
        if (e.getKeepInventory()) return;
        List<ItemStack> added = new ArrayList<ItemStack>();
        for (int i = 0; i < prof.slots.length; i++) {
            if (GearItems.empty(prof.slots[i])) continue;
            ItemStack drop = prof.slots[i];
            e.getDrops().add(drop);
            added.add(drop);
            prof.slots[i] = null;
        }
        if (added.isEmpty()) return;
        deathDrops.put(p, added);
        deathDropCount += added.size();
        saveLater(prof);
        sendState(p, prof);
        getLogger().info("GEAR_DEATH_DROP player=" + p.getUniqueId() + " items=" + added.size());
    }

    /** If a later handler turned keepInventory on, the drops are discarded: put the gear back. */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeathFinal(PlayerDeathEvent e) {
        List<ItemStack> added = deathDrops.remove(e.getEntity());
        if (added == null || !e.getKeepInventory()) return;
        GearProfile prof = profile(e.getEntity());
        int restored = 0;
        for (ItemStack stack : added) {
            boolean present = false;
            for (java.util.Iterator<ItemStack> it = e.getDrops().iterator(); it.hasNext(); ) {
                if (it.next() == stack) { it.remove(); present = true; break; }
            }
            GearItem item = GearItems.identify(stack);
            int free = item == null ? -1 : prof.freeSlotFor(item);
            if (free >= 0) { prof.slots[free] = stack; restored++; }
            else if (present) e.getDrops().add(stack);
        }
        saveLater(prof);
        getLogger().info("GEAR_DEATH_RESTORED player=" + e.getEntity().getUniqueId() + " items=" + restored);
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onMobDeath(EntityDeathEvent e) {
        LivingEntity dead = e.getEntity();
        if (dead instanceof Player || !GearAbilities.hostile(dead) || dead.getKiller() == null || spawned(dead)) return;
        double supply = Math.max(0.0, Math.min(0.05, getConfig().getDouble("drops.supply-chance", 0.01)));
        if (random.nextDouble() < supply) {
            // Phase 2: scavenged supplies (candy and bandages; stims only rarely).
            GearConsumable pick = GearApi.pickSupply(random, 2);
            if (pick != null) { e.getDrops().add(GearItems.create(pick)); supplyDrops++; }
        }
        double chance = getConfig().getDouble(dead instanceof Zombie ? "drops.zombie-chance" : "drops.hostile-chance", 0.0035);
        if (random.nextDouble() >= Math.max(0.0, Math.min(0.05, chance))) return;
        int total = 0;
        for (GearItem item : GearItem.values()) total += item.weight();
        int roll = random.nextInt(total);
        GearItem pick = GearItem.values()[0];
        for (GearItem item : GearItem.values()) { roll -= item.weight(); if (roll < 0) { pick = item; break; } }
        e.getDrops().add(GearItems.create(pick));
        mobDrops++;
        getLogger().info("GEAR_MOB_DROP item=" + pick.id + " mob=" + dead.getType().name() + " killer=" + dead.getKiller().getUniqueId());
    }

    private boolean spawned(LivingEntity entity) {
        if (spawnerMissing) return false;
        try {
            if (spawnerCheck == null) spawnerCheck = entity.getClass().getMethod("fromMobSpawner");
            return Boolean.TRUE.equals(spawnerCheck.invoke(entity));
        } catch (NoSuchMethodException e) {
            spawnerMissing = true;
            return false;
        } catch (Exception e) {
            return false;
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPrepare(PrepareItemCraftEvent e) {
        Recipe recipe = e.getRecipe();
        boolean gearResult = recipe != null && (GearItems.identify(recipe.getResult()) != null || GearItems.consumable(recipe.getResult()) != null);
        for (ItemStack in : e.getInventory().getMatrix()) {
            if (GearItems.empty(in)) continue;
            boolean gearInput = GearItems.identify(in) != null || GearItems.isIcon(in) || GearItems.consumable(in) != null;
            if ((gearInput && !gearResult) || (gearResult && GearItems.tagged(in))) {
                e.getInventory().setResult(null);
                return;
            }
        }
    }

    private void registerRecipes() {
        for (GearItem item : GearItem.values()) {
            try {
                ShapedRecipe recipe = new ShapedRecipe(new NamespacedKey(this, item.id), GearItems.create(item));
                recipe.shape(item.shape);
                for (Map.Entry<Character, String> in : item.ingredientMap().entrySet()) {
                    String[] parts = in.getValue().split(":");
                    Material material = Material.valueOf(parts[0]);
                    if (parts.length > 1) recipe.setIngredient(in.getKey(), material, Integer.parseInt(parts[1]));
                    else recipe.setIngredient(in.getKey(), material);
                }
                if (getServer().addRecipe(recipe)) recipes++;
            } catch (RuntimeException error) {
                getLogger().warning("GEAR_RECIPE_FAILED item=" + item.id + " error=" + error);
            }
        }
        for (GearConsumable item : GearConsumable.values()) {
            if (item.shape == null) continue;
            try {
                ShapedRecipe recipe = new ShapedRecipe(new NamespacedKey(this, item.id), GearItems.create(item));
                recipe.shape(item.shape);
                for (Map.Entry<Character, String> in : item.ingredientMap().entrySet()) recipe.setIngredient(in.getKey(), Material.valueOf(in.getValue()));
                if (getServer().addRecipe(recipe)) supplyRecipes++;
            } catch (RuntimeException error) {
                getLogger().warning("GEAR_RECIPE_FAILED item=" + item.id + " error=" + error);
            }
        }
    }

    static String recipeText(GearConsumable item) {
        if (item.shape == null) return "loot only";
        StringBuilder out = new StringBuilder();
        for (String row : item.shape) out.append('[').append(row.replace(' ', '.')).append("] ");
        for (Map.Entry<Character, String> in : item.ingredientMap().entrySet())
            out.append(in.getKey()).append('=').append(GearAbilities.pretty(in.getValue())).append(' ');
        return out.toString().trim();
    }

    static String recipeText(GearItem item) {
        StringBuilder out = new StringBuilder();
        for (String row : item.shape) out.append('[').append(row.replace(' ', '.')).append("] ");
        for (Map.Entry<Character, String> in : item.ingredientMap().entrySet())
            out.append(in.getKey()).append('=').append(GearAbilities.pretty(in.getValue().split(":")[0]))
               .append(in.getValue().contains(":") ? "(" + in.getValue().split(":")[1] + ")" : "").append(' ');
        return out.toString().trim();
    }

    // ------------------------------------------------------------------ command

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String sub = args.length == 0 ? "" : args[0].toLowerCase(Locale.ROOT);
        Player p = sender instanceof Player ? (Player) sender : null;
        if (sub.equals("selftest")) {
            if (p != null) { sender.sendMessage(ChatColor.RED + "Console only."); return true; }
            new GearSelfTest(this).run(sender);
            return true;
        }
        if (sub.equals("status")) {
            if (p != null && !p.hasPermission("jasprgear.admin")) { sender.sendMessage(ChatColor.RED + "Not allowed."); return true; }
            sender.sendMessage("GEAR_STATUS profiles=" + profiles.size() + " recipes=" + recipes + " equips=" + equips
                + " unequips=" + unequips + " mobDrops=" + mobDrops + " supplyDrops=" + supplyDrops + " deathDrops=" + deathDropCount
                + " hellos=" + hellos + " rejected=" + rejected + " writes=" + store.writes + " saveFailures=" + store.failures
                + " " + abilities.metrics() + " " + vitals.metrics());
            return true;
        }
        if (sub.equals("open") && p == null && args.length > 1) {
            Player target = getServer().getPlayerExact(args[1]);
            if (target == null) { sender.sendMessage("No such player."); return true; }
            openMenu(target);
            sender.sendMessage("GEAR_MENU_OPENED player=" + target.getUniqueId());
            return true;
        }
        if (sub.equals("peek")) {
            if (p != null) { sender.sendMessage(ChatColor.RED + "Console only."); return true; }
            if (args.length < 2) { sender.sendMessage("Usage: gear peek <player|uuid>"); return true; }
            UUID id;
            Player online = getServer().getPlayerExact(args[1]);
            try { id = online != null ? online.getUniqueId() : UUID.fromString(args[1]); } catch (IllegalArgumentException e) { sender.sendMessage("Unknown player/uuid."); return true; }
            GearProfile prof = profiles.get(id);
            String source = "memory";
            if (prof == null) { prof = new GearProfile(id); source = store.load(prof) ? "disk" : "unreadable"; }
            StringBuilder out = new StringBuilder("GEAR_PEEK uuid=" + id + " source=" + source);
            for (int i = 0; i < GearType.SLOT_COUNT; i++) {
                GearItem item = GearItems.identify(prof.slots[i]);
                out.append(" ").append(i).append("=").append(item == null ? "-" : item.id);
            }
            sender.sendMessage(out.toString());
            return true;
        }
        if (sub.equals("effect")) {
            if (p != null && !p.hasPermission("jasprgear.admin")) { sender.sendMessage(ChatColor.RED + "Not allowed."); return true; }
            if (args.length < 3) { sender.sendMessage("Usage: /gear effect <player> <bleed|ice|vigor|volt|para|clear> [seconds]"); return true; }
            Player target = getServer().getPlayerExact(args[1]);
            if (target == null) { sender.sendMessage("No such player."); return true; }
            if (args[2].equalsIgnoreCase("clear")) {
                for (GearStatus s : GearStatus.values()) vitals.cure(target, s, null);
                sender.sendMessage("GEAR_EFFECT cleared player=" + target.getUniqueId());
                return true;
            }
            GearStatus status = GearStatus.byId(args[2]);
            if (status == null) { sender.sendMessage("Unknown status."); return true; }
            long seconds = 10;
            if (args.length > 3) try { seconds = Math.max(1, Math.min(600, Long.parseLong(args[3]))); } catch (NumberFormatException ex) { seconds = 10; }
            boolean ok = vitals.apply(target, status, seconds * 1000L, false);
            sender.sendMessage("GEAR_EFFECT player=" + target.getUniqueId() + " status=" + status.id + " applied=" + ok);
            return true;
        }
        if (sub.equals("give")) {
            if (p != null && !p.hasPermission("jasprgear.admin")) { sender.sendMessage(ChatColor.RED + "Not allowed."); return true; }
            if (args.length < 3) { sender.sendMessage("Usage: /gear give <player|*> <id|all>"); return true; }
            List<Player> targets = new ArrayList<Player>();
            if (args[1].equals("*")) targets.addAll(getServer().getOnlinePlayers());
            else if (getServer().getPlayerExact(args[1]) != null) targets.add(getServer().getPlayerExact(args[1]));
            if (targets.isEmpty()) { sender.sendMessage("No such player."); return true; }
            for (Player target : targets) {
                for (GearItem item : GearItem.values()) {
                    if (args[2].equalsIgnoreCase("all") || item.id.equalsIgnoreCase(args[2])) {
                        for (ItemStack left : target.getInventory().addItem(GearItems.create(item)).values())
                            target.getWorld().dropItem(target.getLocation(), left);
                    }
                }
                for (GearConsumable item : GearConsumable.values()) {
                    if (args[2].equalsIgnoreCase("supplies") || item.id.equalsIgnoreCase(args[2])) {
                        for (ItemStack left : target.getInventory().addItem(GearItems.create(item)).values())
                            target.getWorld().dropItem(target.getLocation(), left);
                    }
                }
                getLogger().info("GEAR_GIVE by=" + sender.getName() + " to=" + target.getUniqueId() + " item=" + args[2]);
            }
            return true;
        }
        if (sub.equals("recipes")) {
            for (GearItem item : GearItem.values()) {
                if (args.length > 1 && !item.id.startsWith(args[1].toLowerCase(Locale.ROOT))) continue;
                sender.sendMessage(ChatColor.GOLD + item.title + ChatColor.GRAY + " (" + item.type.label + "): " + ChatColor.WHITE + recipeText(item));
            }
            for (GearConsumable item : GearConsumable.values()) {
                if (item.shape == null || (args.length > 1 && !item.id.startsWith(args[1].toLowerCase(Locale.ROOT)))) continue;
                sender.sendMessage(ChatColor.GOLD + item.title + ChatColor.GRAY + " (consumable): " + ChatColor.WHITE + recipeText(item));
            }
            return true;
        }
        if (p == null) { sender.sendMessage("Players only."); return true; }
        if (!usable(p)) { sender.sendMessage(ChatColor.RED + "Not right now."); return true; }
        GearProfile prof = profile(p);
        switch (sub) {
            case "":
            case "open":
            case "menu":
                openMenu(p);
                return true;
            case "arc":
            case "dodge":
            case "magnet":
                abilities.key(p, prof, sub);
                return true;
            case "vitals":
            case "effects":
            case "adrenaline":
                p.sendMessage(vitals.describe(p, prof));
                p.sendMessage(ChatColor.GRAY + "Costs: arc " + GearVitals.COST_ARC + ", dodge " + GearVitals.COST_DODGE + ", blink "
                    + GearVitals.COST_BLINK + ", ender chest " + GearVitals.COST_CHEST + ", repel " + GearVitals.COST_REPEL
                    + ", magnet on " + GearVitals.COST_MAGNET);
                return true;
            case "bank": {
                String mode = args.length > 1 ? args[1].toLowerCase(Locale.ROOT) : "";
                if (!mode.equals("deposit") && !mode.equals("withdraw")) {
                    int slot = journalSlot(prof);
                    p.sendMessage(ChatColor.GOLD + "XP Bank: " + ChatColor.WHITE + (slot < 0 ? "wear a Field Journal first"
                        : GearItems.storedXp(prof.slots[slot]) + " / " + BANK_CAP + " XP stored")
                        + ChatColor.GRAY + " - /gear bank deposit|withdraw [levels|all]");
                    return true;
                }
                int levels = 1;
                if (args.length > 2) {
                    if (args[2].equalsIgnoreCase("all")) levels = Integer.MAX_VALUE;
                    else try { levels = Math.max(1, Math.min(100, Integer.parseInt(args[2]))); } catch (NumberFormatException ex) { levels = 1; }
                }
                bank(p, prof, mode.equals("deposit"), levels);
                return true;
            }
            case "scan": {
                String filter = args.length > 1 ? args[1].toLowerCase(Locale.ROOT) : "any";
                List<String> ok = Arrays.asList("any", "coal", "iron", "gold", "redstone", "lapis", "diamond", "emerald", "quartz");
                if (!ok.contains(filter)) { p.sendMessage(ChatColor.GRAY + "Scan filters: " + String.join(", ", ok)); return true; }
                prof.scanFilter = filter;
                p.sendMessage(ChatColor.GOLD + "Thermal Goggles scan filter: " + ChatColor.WHITE + filter);
                return true;
            }
            case "list": {
                p.sendMessage(ChatColor.GOLD + "Worn gear:");
                for (int i = 0; i < GearType.SLOT_COUNT; i++) {
                    GearItem item = GearItems.identify(prof.slots[i]);
                    p.sendMessage(ChatColor.GRAY + " " + GearType.SLOTS[i].label + ": " + (item == null ? ChatColor.DARK_GRAY + "empty"
                        : ChatColor.WHITE + item.title + ChatColor.GRAY + " - " + String.join("; ", item.effects)));
                }
                return true;
            }
            default:
                p.sendMessage(ChatColor.GOLD + "Survivor Gear" + ChatColor.GRAY + " - 7 slots: neck, ring, ring, belt, head, body, charm.");
                p.sendMessage(ChatColor.GRAY + "/gear - open the gear menu (browser players also see the slots in their inventory, E)");
                p.sendMessage(ChatColor.GRAY + "/gear list | vitals | recipes [id] | bank | scan <ore|any> | arc | dodge | magnet");
                p.sendMessage(ChatColor.GRAY + "Abilities spend adrenaline (it refills over time); supplies: right-click to use");
                p.sendMessage(ChatColor.GRAY + "Keys: G arc shot, H dodge/blink (sneak: ender chest), J magnet (sneak: repel)");
                return true;
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> out = new ArrayList<String>();
        if (args.length == 1) {
            for (String s : Arrays.asList("help", "list", "vitals", "recipes", "bank", "scan", "arc", "dodge", "magnet"))
                if (s.startsWith(args[0].toLowerCase(Locale.ROOT))) out.add(s);
        } else if (args.length == 2 && args[0].equalsIgnoreCase("recipes")) {
            for (GearItem item : GearItem.values()) if (item.id.startsWith(args[1].toLowerCase(Locale.ROOT))) out.add(item.id);
        }
        return out;
    }

    // ------------------------------------------------------------------ Field Journal XP bank

    static final int BANK_CAP = 1395; // exactly 30 levels from zero

    static int journalSlot(GearProfile prof) {
        for (int i = 0; i < prof.slots.length; i++) if (GearItems.identify(prof.slots[i]) == GearItem.FIELD_JOURNAL) return i;
        return -1;
    }

    static int xpToNext(int level) { return level >= 30 ? 9 * level - 158 : level >= 15 ? 5 * level - 38 : 2 * level + 7; }

    static int pointsForLevel(int level) {
        int total = 0;
        for (int i = 0; i < Math.min(level, 20000); i++) total += xpToNext(i);
        return total;
    }

    static int totalXp(Player p) { return pointsForLevel(p.getLevel()) + Math.round(p.getExp() * xpToNext(p.getLevel())); }

    static void setTotalXp(Player p, int total) {
        p.setExp(0f);
        p.setLevel(0);
        p.setTotalExperience(0);
        if (total > 0) p.giveExp(total);
    }

    /** Moves experience between the player and the worn journal. levels = MAX_VALUE for all. */
    void bank(Player p, GearProfile prof, boolean deposit, int levels) {
        int slot = journalSlot(prof);
        if (slot < 0) { GearAbilities.bar(p, ChatColor.GRAY + "Wear a Field Journal to bank XP"); return; }
        int stored = GearItems.storedXp(prof.slots[slot]);
        int total = totalXp(p), level = p.getLevel(), amount;
        if (deposit) {
            int wanted = levels == Integer.MAX_VALUE ? total : total - pointsForLevel(Math.max(0, level - levels));
            amount = Math.max(0, Math.min(wanted, BANK_CAP - stored));
            if (amount <= 0) { GearAbilities.bar(p, ChatColor.GRAY + (stored >= BANK_CAP ? "The journal is full" : "No experience to bank")); return; }
            setTotalXp(p, total - amount);
            prof.slots[slot] = GearItems.withStoredXp(prof.slots[slot], stored + amount);
        } else {
            int wanted = levels == Integer.MAX_VALUE ? stored : pointsForLevel(level + Math.min(levels, 1000)) - total;
            amount = Math.max(0, Math.min(wanted, stored));
            if (amount <= 0) { GearAbilities.bar(p, ChatColor.GRAY + "The journal is empty"); return; }
            setTotalXp(p, total + amount);
            prof.slots[slot] = GearItems.withStoredXp(prof.slots[slot], stored - amount);
        }
        int now = GearItems.storedXp(prof.slots[slot]);
        p.playSound(p.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.6f, deposit ? 0.7f : 1.3f);
        GearAbilities.bar(p, ChatColor.GOLD + "Field Journal: " + ChatColor.WHITE + (deposit ? "banked " : "withdrew ") + amount
            + " XP" + ChatColor.GRAY + " (" + now + " stored)");
        getLogger().info("GEAR_XP_BANK player=" + p.getUniqueId() + " op=" + (deposit ? "deposit" : "withdraw") + " amount=" + amount + " stored=" + now);
        saveLater(prof);
        sendState(p, prof);
        refreshMenu(p, prof);
    }

    Map<UUID, GearProfile> profiles() { return profiles; }
    int recipeCount() { return recipes; }
}
