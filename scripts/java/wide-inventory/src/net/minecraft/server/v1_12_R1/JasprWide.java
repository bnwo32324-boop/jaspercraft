package net.minecraft.server.v1_12_R1;

import io.netty.buffer.Unpooled;
import java.io.File;
import java.nio.charset.StandardCharsets;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.bukkit.Bukkit;
import org.bukkit.craftbukkit.v1_12_R1.inventory.CraftItemStack;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.inventory.InventoryView;

/**
 * Wide inventory (owner, 2026-10-03): "The player's inventory, by default, should be 1.5x as big ... past, present, and
 * future ... This change should affect the hotbar as well."
 *
 * Every row grows from 9 to 14 slots (1.5 x 9 = 13.5, rounded up): a 14-slot hotbar and three 14-slot rows, 56 slots
 * instead of 36. PlayerInventory.items holds 56 stacks for every player; the first 36 keep their vanilla meaning, so
 * saved players load unchanged and Bukkit storage indices stay valid:
 *   items 0..8 hotbar, 9..35 main rows, 36..40 hotbar extension (hotbar positions 10..14),
 *   41..45 / 46..50 / 51..55 the extension of main rows 1 / 2 / 3.
 * The flat inventory index (getItem/setItem, Bukkit PlayerInventory) puts armour at 56..59 and the off hand at 60.
 *
 * Only a client that says it can show the extra slots gets them on the wire: the JasperCraft client sends "wide1" on
 * plugin channel jaspr:inv after it joins; the server answers "wide1", then appends the 20 extra slots (items 36..55, in
 * that order) to the player's inventory window and to every later window that shows the player inventory. Any other
 * client keeps the vanilla 36-slot windows, and pickups only fill its 36 vanilla slots. A file named jaspr-wide.disabled
 * in the server folder makes the server ignore new announcements (rollback without swapping the server jar).
 *
 * The jar patcher (scripts/java/wide-inventory/patcher) rewires PlayerInventory, ContainerPlayer, Container,
 * PlayerConnection, CraftInventoryPlayer, InventoryView and CraftInventoryView to these hooks.
 */
public final class JasprWide {
    public static final String CHANNEL = "jaspr:inv";
    public static final String HELLO = "wide1";
    public static final int VANILLA = 36, SIZE = 56, EXTRA = 20, ARMOR = 56, OFFHAND = 60;
    public static final int WINDOW_VANILLA = 46, WINDOW_WIDE = 66;
    private static final Logger LOG = LogManager.getLogger("JasprWide");

    /** Fill order for a wide player: the 14-slot hotbar, then each 14-slot row left to right. */
    static final int[] WIDE_ORDER = new int[SIZE];
    static final int[] VANILLA_ORDER = new int[VANILLA];
    static {
        int n = 0;
        for (int i = 0; i < 9; i++) WIDE_ORDER[n++] = i;
        for (int i = 36; i < 41; i++) WIDE_ORDER[n++] = i;
        for (int row = 0; row < 3; row++) {
            for (int i = 0; i < 9; i++) WIDE_ORDER[n++] = 9 + row * 9 + i;
            for (int i = 0; i < 5; i++) WIDE_ORDER[n++] = 41 + row * 5 + i;
        }
        for (int i = 0; i < VANILLA; i++) VANILLA_ORDER[i] = i;
    }

    private JasprWide() {}

    // ---- PlayerInventory ----------------------------------------------------------------------------------------

    /** PlayerInventory.e(int): the 9 vanilla hotbar slots and the 5 extension slots. */
    public static boolean hotbar(int i) {
        return i >= 0 && i < 9 || i >= 36 && i < 41;
    }

    static int[] order(PlayerInventory inv) {
        return inv.jasprWide ? WIDE_ORDER : VANILLA_ORDER;
    }

    /** PlayerInventory.getFirstEmptySlotIndex(): the first empty slot the player can see, hotbar first. */
    public static int firstEmpty(PlayerInventory inv) {
        for (int i : order(inv)) if (inv.items.get(i).isEmpty()) return i;
        return -1;
    }

    private static boolean same(ItemStack a, ItemStack b) {
        return a.getItem() == b.getItem() && (!a.usesData() || a.getData() == b.getData()) && ItemStack.equals(a, b);
    }

    private static boolean room(PlayerInventory inv, ItemStack target, ItemStack add) {
        return !target.isEmpty() && same(target, add) && target.isStackable() && target.getCount() < target.getMaxStackSize()
            && target.getCount() < inv.getMaxStackSize();
    }

    /** PlayerInventory.firstPartial(ItemStack): held item, off hand, then the visible slots in fill order. */
    public static int firstPartial(PlayerInventory inv, ItemStack add) {
        int held = inv.itemInHandIndex;
        if ((held < 9 || inv.jasprWide) && room(inv, inv.getItem(held), add)) return held;
        if (room(inv, inv.getItem(OFFHAND), add)) return OFFHAND;
        for (int i : order(inv)) if (room(inv, inv.items.get(i), add)) return i;
        return -1;
    }

    /** CraftBukkit's PlayerInventory.canHold(ItemStack) over the visible slots. */
    public static int canHold(PlayerInventory inv, ItemStack add) {
        int remains = add.getCount();
        for (int i : order(inv)) {
            ItemStack have = inv.getItem(i);
            if (have.isEmpty()) return add.getCount();
            if (room(inv, have, add))
                remains -= (have.getMaxStackSize() < inv.getMaxStackSize() ? have.getMaxStackSize() : inv.getMaxStackSize()) - have.getCount();
            if (remains <= 0) return add.getCount();
        }
        return add.getCount() - remains;
    }

    /** Window-0 slot of a flat inventory index, or -1 when this player's client has no such slot. */
    public static int windowSlot(PlayerInventory inv, int i) {
        if (i >= 0 && i < 9) return 36 + i;
        if (i >= 9 && i < VANILLA) return i;
        if (i >= VANILLA && i < SIZE) return inv.jasprWide ? WINDOW_VANILLA + (i - VANILLA) : -1;
        if (i >= ARMOR && i < OFFHAND) return 8 - (i - ARMOR);
        if (i == OFFHAND) return 45;
        return -1;
    }

    // ---- Containers ---------------------------------------------------------------------------------------------

    /** Container.addSlotListener(ICrafting): a wide player's windows get the 20 extra slots before the first sync. */
    public static void listen(Container container, ICrafting listener) {
        if (listener instanceof EntityPlayer) {
            PlayerInventory inv = ((EntityPlayer) listener).inventory;
            if (inv != null && inv.jasprWide) extend(container, inv);
        }
    }

    /** Appends items 36..55 when the window shows this player's vanilla slots; true if the window has them now. */
    static boolean extend(Container container, PlayerInventory inv) {
        boolean shows = false;
        for (Slot slot : container.slots) {
            if (slot.inventory != inv) continue;
            if (slot.index >= VANILLA && slot.index < SIZE) return true;
            if (slot.index >= 0 && slot.index < VANILLA) shows = true;
        }
        if (!shows) return false;
        for (int i = VANILLA; i < SIZE; i++) container.a(new Slot(inv, i, 0, 0));
        return true;
    }

    /** ContainerPlayer.shiftClick for a widened inventory window; null keeps the vanilla code. */
    public static ItemStack shiftPlayer(ContainerPlayer container, EntityHuman human, int i) {
        if (container.slots.size() < WINDOW_WIDE) return null;
        ItemStack result = ItemStack.a;
        Slot slot = container.slots.get(i);
        if (slot == null || !slot.hasItem()) return result;
        ItemStack stack = slot.getItem();
        result = stack.cloneItemStack();
        EnumItemSlot equip = EntityInsentient.d(result);
        boolean moved;
        if (i == 0) {
            moved = merge(container, stack, RESULT_ORDER);
            if (!moved) return ItemStack.a;
            slot.a(stack, result);
        } else if (i >= 1 && i < 9) {
            moved = merge(container, stack, ALL_ORDER);
        } else if (equip.a() == EnumItemSlot.Function.ARMOR && !container.slots.get(8 - equip.b()).hasItem()) {
            int armor = 8 - equip.b();
            moved = merge(container, stack, new int[] {armor});
        } else if (equip == EnumItemSlot.OFFHAND && !container.slots.get(45).hasItem()) {
            moved = merge(container, stack, new int[] {45});
        } else if (i >= 9 && i < 36 || i >= 51 && i < WINDOW_WIDE) {
            moved = merge(container, stack, HOTBAR_ORDER);
        } else if (i >= 36 && i < 45 || i >= 46 && i < 51) {
            moved = merge(container, stack, MAIN_ORDER);
        } else {
            moved = merge(container, stack, ALL_ORDER);
        }
        if (!moved) return ItemStack.a;
        if (stack.isEmpty()) slot.set(ItemStack.a);
        else slot.f();
        if (stack.getCount() == result.getCount()) return ItemStack.a;
        ItemStack taken = slot.a(human, stack);
        if (i == 0) human.drop(taken, false);
        return result;
    }

    /** Window-0 slots of the hotbar (14) and of the main rows (42), each left to right. */
    static final int[] HOTBAR_ORDER = {36, 37, 38, 39, 40, 41, 42, 43, 44, 46, 47, 48, 49, 50};
    static final int[] MAIN_ORDER = new int[42];
    static final int[] ALL_ORDER = new int[56];
    /** Crafting results go to the far end first, as vanilla: hotbar right to left, then the rows bottom up. */
    static final int[] RESULT_ORDER = new int[56];
    static {
        int n = 0;
        for (int row = 0; row < 3; row++) {
            for (int c = 0; c < 9; c++) MAIN_ORDER[n++] = 9 + row * 9 + c;
            for (int c = 0; c < 5; c++) MAIN_ORDER[n++] = 51 + row * 5 + c;
        }
        System.arraycopy(MAIN_ORDER, 0, ALL_ORDER, 0, 42);
        System.arraycopy(HOTBAR_ORDER, 0, ALL_ORDER, 42, 14);
        for (int k = 0; k < 56; k++) RESULT_ORDER[k] = ALL_ORDER[55 - k];
    }

    /** Container.a(ItemStack, int, int, boolean) over an explicit slot order: top up stacks first, then empty slots. */
    static boolean merge(Container container, ItemStack stack, int[] ids) {
        boolean moved = false;
        if (stack.isStackable()) {
            for (int k = 0; k < ids.length && !stack.isEmpty(); k++) {
                Slot slot = container.slots.get(ids[k]);
                ItemStack have = slot.getItem();
                if (have.isEmpty() || have.getItem() != stack.getItem() || stack.usesData() && stack.getData() != have.getData()
                    || !ItemStack.equals(stack, have)) continue;
                int sum = have.getCount() + stack.getCount();
                if (sum <= stack.getMaxStackSize()) {
                    stack.setCount(0);
                    have.setCount(sum);
                    slot.f();
                    moved = true;
                } else if (have.getCount() < stack.getMaxStackSize()) {
                    stack.subtract(stack.getMaxStackSize() - have.getCount());
                    have.setCount(stack.getMaxStackSize());
                    slot.f();
                    moved = true;
                }
            }
        }
        if (!stack.isEmpty()) {
            for (int id : ids) {
                Slot slot = container.slots.get(id);
                if (slot.getItem().isEmpty() && slot.isAllowed(stack)) {
                    slot.set(stack.cloneAndSubtract(Math.min(stack.getCount(), slot.getMaxStackSize())));
                    slot.f();
                    moved = true;
                    break;
                }
            }
        }
        return moved;
    }

    // ---- PlayerConnection ---------------------------------------------------------------------------------------

    /** Held-slot packet for an extension slot (items 36..40) from a wide client; false leaves it to vanilla. */
    public static boolean held(PlayerConnection connection, PacketPlayInHeldItemSlot packet) {
        EntityPlayer player = connection.player;
        int slot = packet.a();
        if (slot < VANILLA || slot >= 41 || !player.inventory.jasprWide) return false;
        if (player.isFrozen()) return true;
        PlayerItemHeldEvent event = new PlayerItemHeldEvent(connection.getPlayer(), player.inventory.itemInHandIndex, slot);
        Bukkit.getPluginManager().callEvent(event);
        if (event.isCancelled()) {
            connection.sendPacket(new PacketPlayOutHeldItemSlot(player.inventory.itemInHandIndex));
        } else {
            player.inventory.itemInHandIndex = slot;
        }
        player.resetIdleTimer();
        return true;
    }

    /** Highest creative-mode window slot the player's client may set. */
    public static int creativeMax(PlayerConnection connection) {
        return connection.player.inventory.jasprWide && connection.player.defaultContainer.slots.size() >= WINDOW_WIDE ? WINDOW_WIDE - 1 : 45;
    }

    /** Custom payload on jaspr:inv: the client can show the wide inventory. True when the packet was ours. */
    public static boolean payload(PlayerConnection connection, PacketPlayInCustomPayload packet) {
        if (!CHANNEL.equals(packet.a())) return false;
        EntityPlayer player = connection.player;
        String said;
        try {
            PacketDataSerializer data = packet.b();
            int n = Math.min(data.readableBytes(), 32);
            byte[] bytes = new byte[n];
            data.readBytes(bytes);
            // The client writes a PacketBuffer string: a VarInt length (one byte here), then the text.
            int skip = n > 0 && (bytes[0] & 0xFF) == n - 1 ? 1 : 0;
            said = new String(bytes, skip, n - skip, StandardCharsets.UTF_8);
        } catch (RuntimeException e) {
            LOG.warn("JASPR_WIDE_BAD_HELLO player={} {}", player.getName(), e.getClass().getSimpleName());
            return true;
        }
        if (!HELLO.equals(said)) {
            LOG.info("JASPR_WIDE_UNKNOWN_HELLO player={} length={}", player.getName(), said.length());
            return true;
        }
        if (new File("jaspr-wide.disabled").exists()) {
            LOG.info("JASPR_WIDE_DISABLED player={}", player.getName());
            return true;
        }
        enable(player);
        return true;
    }

    /** Turns the wide inventory on for this connection: answer, widen the inventory window, resend it. */
    public static void enable(EntityPlayer player) {
        PlayerInventory inv = player.inventory;
        boolean already = inv.jasprWide;
        inv.jasprWide = true;
        player.playerConnection.sendPacket(new PacketPlayOutCustomPayload(CHANNEL,
            new PacketDataSerializer(Unpooled.wrappedBuffer(HELLO.getBytes(StandardCharsets.UTF_8)))));
        boolean widened = extend(player.defaultContainer, inv);
        player.updateInventory(player.defaultContainer);
        if (inv.itemInHandIndex >= VANILLA) player.playerConnection.sendPacket(new PacketPlayOutHeldItemSlot(inv.itemInHandIndex));
        int extra = 0;
        for (int i = VANILLA; i < SIZE; i++) if (!inv.items.get(i).isEmpty()) extra++;
        LOG.info("JASPR_WIDE_ENABLED player={} again={} window={} extraStacks={} held={}", player.getName(), already,
            widened ? player.defaultContainer.slots.size() : -1, extra, inv.itemInHandIndex);
    }

    // ---- Bukkit -------------------------------------------------------------------------------------------------

    /** CraftInventoryPlayer.setItem: tell the client about the changed slot, if its window has it. */
    public static void bukkitSlot(PlayerInventory inv, int index, org.bukkit.inventory.ItemStack item) {
        if (!(inv.player instanceof EntityPlayer)) return;
        EntityPlayer player = (EntityPlayer) inv.player;
        if (player.playerConnection == null) return;
        int window = windowSlot(inv, index);
        if (window < 0 || window >= player.defaultContainer.slots.size()) return;
        player.playerConnection.sendPacket(new PacketPlayOutSetSlot(player.defaultContainer.windowId, window, CraftItemStack.asNMSCopy(item)));
    }

    /** CraftInventoryPlayer.setHeldItemSlot: vanilla slots for everyone, extension slots for wide players. */
    public static void bukkitHeld(PlayerInventory inv, int slot) {
        if (!(slot >= 0 && slot < 9 || inv.jasprWide && hotbar(slot)))
            throw new IllegalArgumentException("Slot is not between 0 and 8 inclusive");
        inv.itemInHandIndex = slot;
        if (inv.player instanceof EntityPlayer && ((EntityPlayer) inv.player).playerConnection != null)
            ((EntityPlayer) inv.player).playerConnection.sendPacket(new PacketPlayOutHeldItemSlot(slot));
    }

    /** InventoryView.convertSlot with the flat layout above (armour 56..59, off hand 60, extra slots 36..55). */
    public static int convertSlot(InventoryView view, int raw) {
        int top = view.getTopInventory().getSize();
        if (raw < top) return raw;
        int slot = raw - top;
        InventoryType type = view.getType();
        if (type == InventoryType.CRAFTING || type == InventoryType.CREATIVE) {
            if (slot < 4) return ARMOR + 3 - slot;
            if (slot == 40) return OFFHAND;
            if (slot > 40) return slot - 5;
            slot -= 4;
        }
        if (slot >= VANILLA) return slot;
        return slot >= 27 ? slot - 27 : slot + 9;
    }

    /** CraftInventoryView.getSlotType: the vanilla answer, with hotbar/main decided by the slot it lands on. */
    public static InventoryType.SlotType slotType(InventoryView view, int raw, InventoryType.SlotType vanilla) {
        int top = view.getTopInventory().getSize();
        if (raw < top || raw < 0) return vanilla;
        if (view.getType() == InventoryType.CRAFTING && raw - top < 4) return vanilla;
        int flat = convertSlot(view, raw);
        if (flat >= ARMOR) return vanilla;
        return hotbar(flat) ? InventoryType.SlotType.QUICKBAR : InventoryType.SlotType.CONTAINER;
    }
}
