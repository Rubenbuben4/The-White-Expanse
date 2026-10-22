package net.icegiant.client;

import net.icegiant.IceGiantMod;
import net.icegiant.entity.FrozenRealmGoats;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLivingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Draws Frozen Realm goats bigger (see FrozenRealmGoats; the hitbox is scaled there). */
@Mod.EventBusSubscriber(modid = IceGiantMod.MOD_ID, value = Dist.CLIENT)
public final class FrozenRealmGoatRender {

    private FrozenRealmGoatRender() {
    }

    @SubscribeEvent
    public static void onRenderPre(RenderLivingEvent.Pre<?, ?> event) {
        if (FrozenRealmGoats.isBigGoat(event.getEntity())) {
            event.getPoseStack().pushPose();
            event.getPoseStack().scale(FrozenRealmGoats.SCALE, FrozenRealmGoats.SCALE, FrozenRealmGoats.SCALE);
        }
    }

    @SubscribeEvent
    public static void onRenderPost(RenderLivingEvent.Post<?, ?> event) {
        if (FrozenRealmGoats.isBigGoat(event.getEntity())) {
            event.getPoseStack().popPose();
        }
    }
}
