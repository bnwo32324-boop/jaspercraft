package net.minecraftforge.fml.common.network;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.world.World;

/** JasperCraft port shim of FML's IGuiHandler (the mod's only GUI, the tracker screen, is client-side). */
public interface IGuiHandler {
    Object getServerGuiElement(int ID, EntityPlayer player, World world, int x, int y, int z);

    Object getClientGuiElement(int ID, EntityPlayer player, World world, int x, int y, int z);
}
