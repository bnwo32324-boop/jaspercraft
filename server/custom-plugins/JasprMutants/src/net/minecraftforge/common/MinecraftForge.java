package net.minecraftforge.common;

import net.minecraftforge.fml.common.eventhandler.EventBus;

/** JasperCraft port shim of Forge's MinecraftForge: the one EVENT_BUS the glue (chat.jaspr.mutants) feeds from Paper. */
public class MinecraftForge {
    public static final EventBus EVENT_BUS = new EventBus();
    public static final EventBus TERRAIN_GEN_BUS = new EventBus();
    public static final EventBus ORE_GEN_BUS = new EventBus();
}
