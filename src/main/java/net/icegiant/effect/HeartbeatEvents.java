package net.icegiant.effect;

import net.icegiant.IceGiantMod;
import net.icegiant.registry.ModEffects;
import net.icegiant.registry.ModSounds;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraftforge.event.entity.living.MobEffectEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * A pounding heartbeat when a player gets knocked down - after landing from an Ice Giant's
 * throw (GrabEvents) or when trampled by a wall-breaker dash (IceDashAbility). Both give the
 * Knocked Down effect, so the sound is tied to that effect starting. Only the knocked-down
 * player hears it (it's their own heart).
 */
@Mod.EventBusSubscriber(modid = IceGiantMod.MOD_ID)
public final class HeartbeatEvents {

    private HeartbeatEvents() {
    }

    @SubscribeEvent
    public static void onEffectAdded(MobEffectEvent.Added event) {
        if (event.getOldEffectInstance() != null) return; // already down - don't restart the heartbeat
        if (event.getEffectInstance().getEffect() != ModEffects.KNOCKED_DOWN.get()) return;
        if (event.getEntity() instanceof ServerPlayer player) {
            player.playNotifySound(ModSounds.HEARTBEAT.get(), SoundSource.PLAYERS, 1.0F, 1.0F);
        }
    }
}
