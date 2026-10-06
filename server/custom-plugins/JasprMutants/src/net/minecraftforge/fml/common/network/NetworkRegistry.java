package net.minecraftforge.fml.common.network;

import java.util.HashMap;
import java.util.Map;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.network.simpleimpl.SimpleNetworkWrapper;

/** JasperCraft port shim of FML's NetworkRegistry. */
public enum NetworkRegistry {
    INSTANCE;

    private final Map<Object, IGuiHandler> guiHandlers = new HashMap<Object, IGuiHandler>();

    public SimpleNetworkWrapper newSimpleChannel(String name) {
        return new SimpleNetworkWrapper(name);
    }

    public void registerGuiHandler(Object mod, IGuiHandler handler) {
        this.guiHandlers.put(mod, handler);
    }

    /**
     * FML's EntityPlayer.openGui on the server: asks the mod's IGuiHandler for a server container. Mutant Creatures'
     * ServerProxy returns null (its only GUI is client-side), and then FML does nothing on the server.
     */
    public Object getRemoteGuiContainer(Object mod, int modGuiId, EntityPlayer player, World world, int x, int y, int z) {
        IGuiHandler handler = this.guiHandlers.get(mod);
        return handler == null ? null : handler.getServerGuiElement(modGuiId, player, world, x, y, z);
    }

    /**
     * FML's TargetPoint. JasperCraft port: Paper runs several worlds that share a dimension id, so a point also
     * carries its world when built from one (chat.jaspr.mutants.net.ModChannel sends only to that world's players).
     */
    public static class TargetPoint {
        public final double x;
        public final double y;
        public final double z;
        public final double range;
        public final int dimension;
        public final World world;

        public TargetPoint(int dimension, double x, double y, double z, double range) {
            this(null, dimension, x, y, z, range);
        }

        public TargetPoint(World world, double x, double y, double z, double range) {
            this(world, world.provider.getDimensionType().getId(), x, y, z, range);
        }

        private TargetPoint(World world, int dimension, double x, double y, double z, double range) {
            this.world = world;
            this.x = x;
            this.y = y;
            this.z = z;
            this.range = range;
            this.dimension = dimension;
        }
    }
}
