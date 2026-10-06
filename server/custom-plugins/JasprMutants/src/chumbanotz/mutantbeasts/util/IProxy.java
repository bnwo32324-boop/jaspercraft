package chumbanotz.mutantbeasts.util;

import net.minecraft.world.World;
import net.minecraftforge.fml.common.network.IGuiHandler;

public interface IProxy
extends IGuiHandler {
    public void preInit();

    public void init();

    public World getWorldClient();

    public Object getMutantSkeletonArmorModel();
}
