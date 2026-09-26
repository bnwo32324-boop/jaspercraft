package chat.jaspr.enchant;

import chat.jaspr.enchant.fx.ArrowFx;
import chat.jaspr.enchant.fx.DigFx;
import chat.jaspr.enchant.fx.LootFx;
import chat.jaspr.enchant.fx.NmsHooks;
import chat.jaspr.enchant.fx.Pipeline;
import chat.jaspr.enchant.fx.TableFx;
import chat.jaspr.enchant.fx.TempBlocks;
import chat.jaspr.enchant.fx.TickFx;
import chat.jaspr.enchant.fx.ToolsFx;
import chat.jaspr.enchant.fx.UpgradeGui;
import chat.jaspr.enchant.nms.Registrar;
import chat.jaspr.enchant.nms.SmeEnchantment;
import chat.jaspr.enchant.util.Log;
import net.minecraft.server.v1_12_R1.ItemEnchantedBook;
import net.minecraft.server.v1_12_R1.WeightedRandomEnchant;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.craftbukkit.v1_12_R1.inventory.CraftItemStack;
import org.bukkit.entity.Player;

/** /jsme stats | /jsme book <regname> [level] | /jsme enchant <regname> <level> (ops only, for testing). */
final class AdminCommand implements CommandExecutor {
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.isOp()) {
            sender.sendMessage("Operators only.");
            return true;
        }
        if (args.length == 0 || args[0].equalsIgnoreCase("stats")) {
            sender.sendMessage("SME: enchantments=" + Registrar.bukkitRegistered + " damageEvents=" + Pipeline.eventsSeen
                    + " altPasses=" + NmsHooks.altPasses + " arrows=" + ArrowFx.arrowsTagged + " split=" + ArrowFx.splitArrows
                    + " strafeTicks=" + TickFx.strafeTicks + " tables=" + TableFx.tablesRecomputed + " lootFixed=" + TableFx.lootFixed
                    + " tempIce=" + TempBlocks.iceCount() + " tempMagma=" + TempBlocks.magmaCount() + " digAssist=" + DigFx.assisted
                    + " upgrades=" + UpgradeGui.upgrades + " smelted=" + ToolsFx.smelted + " looting=" + LootFx.lootingAdjusted
                    + " errors=" + Log.errorCount());
            return true;
        }
        if (args[0].equalsIgnoreCase("debug")) {
            Pipeline.debug = args.length > 1 ? args[1].equalsIgnoreCase("on") : !Pipeline.debug;
            sender.sendMessage("SME debug=" + Pipeline.debug);
            return true;
        }
        if (!(sender instanceof Player) || args.length < 2) {
            sender.sendMessage("/jsme book <regname> [level] | /jsme enchant <regname> <level>");
            return true;
        }
        Player p = (Player) sender;
        SmeEnchantment e = Registrar.byReg(args[1].toLowerCase());
        if (e == null) {
            sender.sendMessage("Unknown SME enchantment " + args[1]);
            return true;
        }
        int level = args.length > 2 ? Integer.parseInt(args[2]) : e.getMaxLevel();
        if (args[0].equalsIgnoreCase("book")) {
            p.getInventory().addItem(CraftItemStack.asBukkitCopy(ItemEnchantedBook.a(new WeightedRandomEnchant(e, level))));
        } else if (args[0].equalsIgnoreCase("enchant")) {
            org.bukkit.inventory.ItemStack hand = p.getInventory().getItemInMainHand();
            if (hand == null || hand.getType() == org.bukkit.Material.AIR) return true;
            hand.addUnsafeEnchantment(org.bukkit.enchantments.Enchantment.getById(e.def.id), level);
            p.getInventory().setItemInMainHand(hand);
        }
        return true;
    }
}
