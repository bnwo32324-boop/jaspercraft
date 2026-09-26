package chat.jaspr.enchant;

import chat.jaspr.enchant.data.Incompat;
import chat.jaspr.enchant.fx.ArrowFx;
import chat.jaspr.enchant.fx.CombatFx;
import chat.jaspr.enchant.fx.Crits;
import chat.jaspr.enchant.fx.DigFx;
import chat.jaspr.enchant.fx.Enchanting;
import chat.jaspr.enchant.fx.FireFx;
import chat.jaspr.enchant.fx.ItemsFx;
import chat.jaspr.enchant.fx.KnockbackFx;
import chat.jaspr.enchant.fx.LootFx;
import chat.jaspr.enchant.fx.MiscFx;
import chat.jaspr.enchant.fx.MovementFx;
import chat.jaspr.enchant.fx.NmsHooks;
import chat.jaspr.enchant.fx.Pipeline;
import chat.jaspr.enchant.fx.TableFx;
import chat.jaspr.enchant.fx.TempBlocks;
import chat.jaspr.enchant.fx.TickFx;
import chat.jaspr.enchant.fx.ToolsFx;
import chat.jaspr.enchant.fx.UpgradeGui;
import chat.jaspr.enchant.fx.Upgrading;
import chat.jaspr.enchant.nms.Registrar;
import chat.jaspr.enchant.nms.SlotTypes;
import chat.jaspr.enchant.nms.SmeEnchantment;
import chat.jaspr.enchant.util.Log;
import chat.jaspr.enchant.util.Nms;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * JasprEnchantments: So Many Enchantments 1.0.9 for Paper 1.12.2. Registration happens in onLoad
 * (ids 72..201 must exist before worlds and players load); behaviour is wired in onEnable.
 */
public final class EnchantPlugin extends JavaPlugin {
    public static final String VERSION = "1.0.0";
    private static EnchantPlugin instance;
    private Throwable loadError;

    public static EnchantPlugin get() {
        return instance;
    }

    @Override
    public void onLoad() {
        instance = this;
        Log.init(getLogger());
        try {
            Registrar.registerAll();
            E.bind();
            Log.info("SME_REGISTERED nms=" + Registrar.BY_INDEX.length + " bukkit=" + Registrar.bukkitRegistered
                    + " customTargets=" + SlotTypes.customTargetsWorking + (SlotTypes.failure == null ? "" : " targetFailure=" + SlotTypes.failure)
                    + " unresolvedIncompat=" + Incompat.UNRESOLVED.size());
        } catch (Throwable t) {
            loadError = t;
            Log.error("register", t);
        }
    }

    @Override
    public void onEnable() {
        if (loadError != null || !Registrar.registered) {
            Log.warn("SME_FAILED registration did not complete; behaviour disabled");
            return;
        }
        if (!Nms.reflectionOk()) Log.warn("SME_WARN some private NMS fields are missing (see SME_REFLECT_MISSING lines)");
        Enchanting.initBlacklists();
        Upgrading.init();
        UpgradeGui.init(this);
        getDataFolder().mkdirs();
        // load: STARTUP enables before the worlds exist; temp blocks left by a crash are reverted once they do
        getServer().getScheduler().runTask(this, () -> TempBlocks.init(getDataFolder()));

        NmsHooks.register();
        MovementFx.register();
        Crits.register();
        FireFx.register();
        CombatFx.register(this);
        ArrowFx.register();
        KnockbackFx.register();
        LootFx.register();

        PluginManager pm = getServer().getPluginManager();
        pm.registerEvents(new Crits(), this);      // before the pipeline: forced-crit sweep suppression
        pm.registerEvents(new Pipeline(), this);
        pm.registerEvents(new FireFx(), this);
        pm.registerEvents(new KnockbackFx(), this);
        pm.registerEvents(new ArrowFx(), this);
        pm.registerEvents(new ItemsFx(), this);
        pm.registerEvents(new LootFx(), this);
        pm.registerEvents(new TableFx(), this);
        pm.registerEvents(new TempBlocks(), this);
        pm.registerEvents(new DigFx(), this);
        pm.registerEvents(new UpgradeGui(), this);
        pm.registerEvents(new ToolsFx(), this);
        pm.registerEvents(new MiscFx(), this);
        getServer().getScheduler().runTaskTimer(this, new TickFx(), 1L, 1L);
        if (getCommand("jsme") != null) getCommand("jsme").setExecutor(new AdminCommand());

        int first = Registrar.BY_INDEX[0].def.id, last = Registrar.BY_INDEX[Registrar.BY_INDEX.length - 1].def.id;
        Log.info("SME_READY enchantments=" + Registrar.bukkitRegistered + " ids=" + first + "-" + last + " version=" + VERSION);
        Log.info("SME_WIRING handlers=" + Pipeline.handlerCount() + " upgradeRecipes=" + Upgrading.RECIPES.size()
                + " customTargets=" + SlotTypes.customTargetsWorking);
        if (Boolean.getBoolean("jaspr.sme.selftest")) {
            getServer().getScheduler().runTaskLater(this, new SelfTest(this), 60L);
        }
    }

    @Override
    public void onDisable() {
        try {
            UpgradeGui.closeAll();
        } catch (Throwable t) {
            Log.error("disable.gui", t);
        }
        try {
            int n = TempBlocks.revertAll();
            Log.info("SME_DISABLED tempBlocksReverted=" + n);
        } catch (Throwable t) {
            Log.error("disable.tempblocks", t);
        }
        // the NMS enchantments stay registered until restart; without hooks they are inert
        SmeEnchantment.hooks = null;
    }
}
