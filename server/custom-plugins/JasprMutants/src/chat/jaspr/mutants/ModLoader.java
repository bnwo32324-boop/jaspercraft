package chat.jaspr.mutants;

import chat.jaspr.mutants.registry.EntityRegistry;
import chat.jaspr.mutants.registry.ItemRegistry;
import chat.jaspr.mutants.registry.ObjectHolders;
import chat.jaspr.mutants.registry.RecipeRegistry;
import chat.jaspr.mutants.registry.SoundRegistry;
import chumbanotz.mutantbeasts.EventHandler;
import chumbanotz.mutantbeasts.MutantBeasts;
import chumbanotz.mutantbeasts.RegistryHandler;
import chumbanotz.mutantbeasts.ServerProxy;
import chumbanotz.mutantbeasts.compat.MBCompatHandler;
import chumbanotz.mutantbeasts.item.MBItems;
import chumbanotz.mutantbeasts.util.MBSoundEvents;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;

/**
 * FML's mod lifecycle for Mutant Creatures, as a dedicated Forge server runs it: construct the @Mod class (with its
 * @Mod.Instance and the serverSide @SidedProxy), subscribe the @Mod.EventBusSubscriber classes, preInit, the registry
 * events in Forge's order (items, then entities and sound events; recipes after init), object holders, init.
 * Configuration (Forge's ConfigManager) is loaded before construction by the plugin.
 */
public final class ModLoader {
    private static MutantBeasts mod;

    private ModLoader() {
    }

    public static void construct() {
        mod = new MutantBeasts();
        MutantBeasts.INSTANCE = mod;               // @Mod.Instance("mutantbeasts")
        MutantBeasts.PROXY = new ServerProxy();     // @SidedProxy(serverSide = "chumbanotz.mutantbeasts.ServerProxy")
        MinecraftForge.EVENT_BUS.register(RegistryHandler.class); // @Mod.EventBusSubscriber
        MinecraftForge.EVENT_BUS.register(EventHandler.class);
        MinecraftForge.EVENT_BUS.register(MBCompatHandler.class);
    }

    public static void preInit() {
        mod.preInit(new FMLPreInitializationEvent());
    }

    public static void registries() {
        MinecraftForge.EVENT_BUS.post(new RegistryEvent.Register<net.minecraft.item.Item>(new ResourceLocation("minecraft", "items"), ItemRegistry.INSTANCE));
        MinecraftForge.EVENT_BUS.post(new RegistryEvent.Register<net.minecraftforge.fml.common.registry.EntityEntry>(new ResourceLocation("minecraft", "entities"), EntityRegistry.INSTANCE));
        MinecraftForge.EVENT_BUS.post(new RegistryEvent.Register<net.minecraft.util.SoundEvent>(new ResourceLocation("minecraft", "soundevents"), SoundRegistry.INSTANCE));
    }

    public static int objectHolders() {
        return ObjectHolders.apply(MBItems.class) + ObjectHolders.apply(MBSoundEvents.class);
    }

    public static void init() {
        mod.init(new FMLInitializationEvent());
    }

    public static void recipeRegistry() {
        MinecraftForge.EVENT_BUS.post(new RegistryEvent.Register<net.minecraft.item.crafting.IRecipe>(new ResourceLocation("minecraft", "recipes"), RecipeRegistry.INSTANCE));
    }
}
