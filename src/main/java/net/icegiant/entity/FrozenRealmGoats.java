package net.icegiant.entity;

import net.icegiant.IceGiantMod;
import net.icegiant.dimension.ModDimensions;
import net.minecraft.world.entity.animal.goat.Goat;
import net.minecraftforge.event.entity.EntityEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Mountain goats of the Frozen Realm are a bit bigger than normal goats. Server and client decide the
 * same way (the goat is in the Frozen Realm), so nothing needs to be synced: the hitbox grows here,
 * the model is scaled in client/FrozenRealmGoatRender. A goat that leaves the realm is normal again.
 * Goats spawn on our painted mountain rock thanks to the block tag minecraft:goats_spawnable_on.
 */
@Mod.EventBusSubscriber(modid = IceGiantMod.MOD_ID)
public final class FrozenRealmGoats {

    /** Size factor for goats in the Frozen Realm (hitbox and model). */
    public static final float SCALE = 1.4F;

    private FrozenRealmGoats() {
    }

    public static boolean isBigGoat(Object entity) {
        return entity instanceof Goat goat && goat.level() != null
                && goat.level().dimension() == ModDimensions.FROZEN_REALM;
    }

    @SubscribeEvent
    public static void onSize(EntityEvent.Size event) {
        if (isBigGoat(event.getEntity())) {
            event.setNewSize(event.getNewSize().scale(SCALE), true);
        }
    }
}
