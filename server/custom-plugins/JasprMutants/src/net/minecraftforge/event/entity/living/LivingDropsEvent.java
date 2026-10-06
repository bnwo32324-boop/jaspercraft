package net.minecraftforge.event.entity.living;

import java.util.List;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.util.DamageSource;
import net.minecraftforge.fml.common.eventhandler.Cancelable;

/**
 * JasperCraft port shim of Forge's LivingDropsEvent. Fed from Bukkit's EntityDeathEvent (the same point of
 * EntityLivingBase.onDeath where Forge posts it); cancelling clears the death drops, as Forge's cancel does.
 */
@Cancelable
public class LivingDropsEvent extends LivingEvent {
    private final DamageSource source;
    private final List<EntityItem> drops;
    private final int lootingLevel;
    private final boolean recentlyHit;

    public LivingDropsEvent(EntityLivingBase entity, DamageSource source, List<EntityItem> drops, int lootingLevel, boolean recentlyHit) {
        super(entity);
        this.source = source;
        this.drops = drops;
        this.lootingLevel = lootingLevel;
        this.recentlyHit = recentlyHit;
    }

    public DamageSource getSource() {
        return this.source;
    }

    public List<EntityItem> getDrops() {
        return this.drops;
    }

    public int getLootingLevel() {
        return this.lootingLevel;
    }

    public boolean isRecentlyHit() {
        return this.recentlyHit;
    }
}
