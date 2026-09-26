package chat.jaspr.enchant.fx;

import chat.jaspr.enchant.E;
import chat.jaspr.enchant.util.Guard;
import chat.jaspr.enchant.util.Nms;
import net.minecraft.server.v1_12_R1.Block;
import net.minecraft.server.v1_12_R1.BlockDirt;
import net.minecraft.server.v1_12_R1.BlockGrass;
import net.minecraft.server.v1_12_R1.BlockMycel;
import net.minecraft.server.v1_12_R1.BlockPosition;
import net.minecraft.server.v1_12_R1.BlockSoil;
import net.minecraft.server.v1_12_R1.Blocks;
import net.minecraft.server.v1_12_R1.Enchantments;
import net.minecraft.server.v1_12_R1.EntityHuman;
import net.minecraft.server.v1_12_R1.EnumDirection;
import net.minecraft.server.v1_12_R1.EnumHand;
import net.minecraft.server.v1_12_R1.IBlockData;
import net.minecraft.server.v1_12_R1.ItemBlock;
import net.minecraft.server.v1_12_R1.ItemHoe;
import net.minecraft.server.v1_12_R1.ItemStack;
import net.minecraft.server.v1_12_R1.MathHelper;
import net.minecraft.server.v1_12_R1.MinecraftServer;
import net.minecraft.server.v1_12_R1.RecipesFurnace;
import net.minecraft.server.v1_12_R1.World;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.craftbukkit.v1_12_R1.inventory.CraftItemStack;
import org.bukkit.entity.Item;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.ItemSpawnEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.InventoryHolder;

import java.util.Random;

/** Moisturized and Plowing (UseHoeEvent LOWEST), Smelter (HarvestDropsEvent LOW). */
public final class ToolsFx implements Listener {
    public static long tilled = 0, smelted = 0;

    private static boolean tillable(Block b) {
        return b instanceof BlockDirt || b instanceof BlockGrass || b instanceof BlockMycel;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onHoe(PlayerInteractEvent e) {
        if (e.getAction() != Action.RIGHT_CLICK_BLOCK || e.getClickedBlock() == null) return;
        if (e.useItemInHand() == org.bukkit.event.Event.Result.DENY) return;
        Guard.run("hoe", () -> {
            EntityHuman player = (EntityHuman) Nms.living(e.getPlayer());
            EnumHand hand = e.getHand() == EquipmentSlot.OFF_HAND ? EnumHand.OFF_HAND : EnumHand.MAIN_HAND;
            ItemStack hoe = player.b(hand);
            if (!(hoe.getItem() instanceof ItemHoe)) return;
            int moist = Nms.level(E.MOISTURIZED, hoe), plow = Nms.level(E.PLOWING, hoe);
            if (moist <= 0 && plow <= 0) return;
            org.bukkit.block.Block cb = e.getClickedBlock();
            BlockPosition pos = new BlockPosition(cb.getX(), cb.getY(), cb.getZ());
            EnumDirection face = EnumDirection.valueOf(e.getBlockFace().name());
            if (!player.a(pos.shift(face), face, hoe)) return; // UseHoeEvent only fires when the player may edit
            World world = player.world;
            Random rng = player.getRandom();
            boolean allow = false;
            if (moist > 0) {
                IBlockData state = world.getType(pos);
                if (tillable(state.getBlock())) {
                    world.setTypeAndData(pos, Blocks.FARMLAND.getBlockData().set(BlockSoil.MOISTURE, 7), 3);
                    world.a(pos, world.getType(pos).getBlock(), MathHelper.nextInt(rng, 120, 240));
                    allow = true;
                    Fx.sound(player, Sound.ITEM_HOE_TILL, SoundCategory.PLAYERS, 1.0F, 1.0F);
                    tilled++;
                }
            }
            if (plow > 0) {
                int range = Math.min(9, plow);
                for (int x = -range; x <= range; x++) {
                    for (int z = -range; z <= range; z++) {
                        BlockPosition m = pos.a(x, 0, z);
                        IBlockData state = world.getType(m);
                        if (tillable(state.getBlock())) {
                            world.setTypeAndData(m, moist > 0 ? Blocks.FARMLAND.getBlockData().set(BlockSoil.MOISTURE, 7)
                                    : Blocks.FARMLAND.getBlockData(), 3);
                            world.a(m, world.getType(m).getBlock(), MathHelper.nextInt(rng, 120, 240));
                            hoe.damage(1, player);
                            allow = true;
                            Fx.sound(player, Sound.ITEM_HOE_TILL, SoundCategory.PLAYERS, 1.0F, 1.0F);
                            tilled++;
                        }
                    }
                }
            }
            if (allow) {
                // Forge onHoeUse ALLOW: damage the hoe once more and skip vanilla tilling
                e.setUseItemInHand(org.bukkit.event.Event.Result.DENY);
                if (!hoe.isEmpty()) hoe.damage(1, player);
            }
        });
    }

    // ------------------------------------------------------------------ Smelter

    private static int ctxTick = -1;
    private static String ctxWorld;
    private static int ctxX, ctxY, ctxZ, ctxFortune;
    private static Random ctxRng;
    private static boolean spawning = false;

    private static boolean canSilk(Block b) {
        try {
            return (Boolean) Nms.invoke(b, Block.class, "n", new Class<?>[0]);
        } catch (Throwable t) {
            return true;
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent e) {
        ctxTick = -1;
        if (!e.isDropItems()) return;
        Guard.run("smelter.break", () -> {
            EntityHuman player = (EntityHuman) Nms.living(e.getPlayer());
            ItemStack tool = player.getItemInMainHand();
            if (tool.isEmpty()) return;
            org.bukkit.block.Block b = e.getBlock();
            if (b.getState() instanceof InventoryHolder) return;
            World w = player.world;
            BlockPosition pos = new BlockPosition(b.getX(), b.getY(), b.getZ());
            IBlockData state = w.getType(pos);
            if (Nms.level(Enchantments.SILK_TOUCH, tool) > 0 && canSilk(state.getBlock())) return;
            if (player.isSneaking()) return;
            if (Nms.level(E.SMELTER, tool) <= 0) return;
            if (!(tool.b(state) || tool.getItem().getDestroySpeed(tool, state) > 1.0F)) return;
            ctxTick = MinecraftServer.currentTick;
            ctxWorld = b.getWorld().getName();
            ctxX = b.getX();
            ctxY = b.getY();
            ctxZ = b.getZ();
            ctxFortune = Nms.level(Enchantments.LOOT_BONUS_BLOCKS, tool);
            ctxRng = player.getRandom();
        });
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onDrop(ItemSpawnEvent e) {
        if (spawning || ctxTick != MinecraftServer.currentTick) return;
        Item item = e.getEntity();
        org.bukkit.Location l = item.getLocation();
        if (!l.getWorld().getName().equals(ctxWorld)) return;
        if (l.getX() < ctxX || l.getX() > ctxX + 1 || l.getY() < ctxY || l.getY() > ctxY + 1 || l.getZ() < ctxZ || l.getZ() > ctxZ + 1) return;
        Guard.run("smelter.drop", () -> {
            ItemStack orig = CraftItemStack.asNMSCopy(item.getItemStack());
            if (orig.isEmpty()) return;
            int origAmount = orig.getCount();
            ItemStack result = RecipesFurnace.getInstance().getResult(new ItemStack(orig.getItem(), 1, orig.getData()));
            if (result.isEmpty()) return;
            if (ctxFortune > 0 && !(result.getItem() instanceof ItemBlock)) origAmount *= 1 + ctxRng.nextInt(ctxFortune + 1);
            int dropAmount = origAmount * result.getCount();
            boolean first = true;
            while (dropAmount > 0) {
                int toDrop = Math.min(dropAmount, result.getMaxStackSize());
                dropAmount -= toDrop;
                ItemStack out = new ItemStack(result.getItem(), toDrop, result.getData());
                if (first) {
                    item.setItemStack(CraftItemStack.asBukkitCopy(out));
                    first = false;
                } else {
                    spawning = true;
                    try {
                        l.getWorld().dropItem(l, CraftItemStack.asBukkitCopy(out));
                    } finally {
                        spawning = false;
                    }
                }
            }
            smelted++;
        });
    }
}
