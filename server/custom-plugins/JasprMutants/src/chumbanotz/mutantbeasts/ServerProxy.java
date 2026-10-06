package chumbanotz.mutantbeasts;

import chumbanotz.mutantbeasts.util.IProxy;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.world.World;

public class ServerProxy
implements IProxy {
    @Override
    public void preInit() {
    }

    @Override
    public void init() {
    }

    @Override
    public World getWorldClient() {
        return null;
    }

    @Override
    public Object getMutantSkeletonArmorModel() {
        return null;
    }

    public Object getClientGuiElement(int ID, EntityPlayer player, World world, int x, int y, int z) {
        throw new IllegalArgumentException("Can't call client gui element on the server!");
    }

    public Object getServerGuiElement(int ID, EntityPlayer player, World world, int x, int y, int z) {
        return null;
    }
}
