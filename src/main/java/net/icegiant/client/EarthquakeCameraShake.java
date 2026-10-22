package net.icegiant.client;

import net.icegiant.IceGiantMod;
import net.icegiant.registry.ModEffects;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Earthquake screen shake: while the local player has the Earthquake effect the
 * camera rumbles (yaw/pitch/roll offsets from a few overlapping sine waves of
 * different speeds), strongest right after the impact and fading out over the
 * last second of the effect. Own implementation - no code from other mods.
 */
@Mod.EventBusSubscriber(modid = IceGiantMod.MOD_ID, value = Dist.CLIENT)
public final class EarthquakeCameraShake {

    /** Degrees of shake per amplifier level (amplifier 0 = light, 2 = heavy). */
    private static final float DEGREES_PER_LEVEL = 0.9F;
    private static final float FADE_TICKS = 20.0F;

    private EarthquakeCameraShake() {
    }

    @SubscribeEvent
    public static void onCameraAngles(ViewportEvent.ComputeCameraAngles event) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) return;
        MobEffectInstance quake = player.getEffect(ModEffects.EARTHQUAKE.get());
        if (quake == null) return;

        float time = player.tickCount + (float) event.getPartialTick();
        float fade = Math.min(1.0F, quake.getDuration() / FADE_TICKS);
        float strength = (quake.getAmplifier() + 1) * DEGREES_PER_LEVEL * fade;

        // overlapping waves at unrelated frequencies feel like rumbling instead of a smooth wobble
        float yaw = (float) (Math.sin(time * 1.9F) + 0.5F * Math.sin(time * 4.3F + 1.3F)) * strength;
        float pitch = (float) (Math.sin(time * 2.6F + 0.7F) + 0.5F * Math.sin(time * 5.1F + 2.1F)) * strength;
        float roll = (float) Math.sin(time * 3.4F + 2.4F) * strength * 0.6F;

        event.setYaw(event.getYaw() + yaw);
        event.setPitch(event.getPitch() + pitch);
        event.setRoll(event.getRoll() + roll);
    }
}
