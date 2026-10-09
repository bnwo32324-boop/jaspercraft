package net.minecraftforge.fml.common.gameevent;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraftforge.fml.common.eventhandler.Event;
import net.minecraftforge.fml.relauncher.Side;

/** JasperCraft port shim of FML's TickEvent; PlayerTickEvent START/END are fed by chat.jaspr.mutants.hooks.TickHooks. */
public class TickEvent extends Event {
    public enum Type { WORLD, PLAYER, CLIENT, SERVER, RENDER }

    public enum Phase { START, END }

    public final Type type;
    public final Side side;
    public final Phase phase;

    public TickEvent(Type type, Side side, Phase phase) {
        this.type = type;
        this.side = side;
        this.phase = phase;
    }

    public static class PlayerTickEvent extends TickEvent {
        public final EntityPlayer player;

        public PlayerTickEvent(Phase phase, EntityPlayer player) {
            super(Type.PLAYER, player.world.isRemote ? Side.CLIENT : Side.SERVER, phase);
            this.player = player;
        }
    }
}
