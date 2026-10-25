package net.icegiant.client;

import net.icegiant.IceGiantMod;
import net.icegiant.dimension.ModDimensions;
import net.icegiant.worldgen.IceCavesFeature;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.FogRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.material.FogType;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * The Eishöhlen's air (Rubenbuben4, 2026-10-25): a light snowy haze and a few snowflakes drifting down.
 * <p>
 * Active while the camera is in the Eishöhlen biome or below it (the caverns reach further down than the biome itself,
 * see IceCavesFeature) and has no open sky above. Fades in and out over a few seconds. The fog only brings the far
 * plane closer - its colour stays the biome's fog colour. Shader packs usually draw their own fog and may ignore this.
 */
@Mod.EventBusSubscriber(modid = IceGiantMod.MOD_ID, value = Dist.CLIENT)
public final class IceCaveAtmosphereClient {

    static final float FOG_FAR = 72.0F, FOG_NEAR = 6.0F;       // blocks
    static final float FADE_PER_TICK = 0.02F;                   // 0 -> 1 in 2.5 s
    static final int FLAKES_PER_TICK = 3, FLAKE_RANGE = 14;
    static final int[] LOOK_UP = {0, 12, 24, 36, 48};           // the biome is also looked for this far above

    private static float strength, prevStrength;
    private static final RandomSource RANDOM = RandomSource.create();

    private IceCaveAtmosphereClient() {
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        LocalPlayer player = mc.player;
        prevStrength = strength;
        if (level == null || player == null) {
            strength = prevStrength = 0.0F;
            return;
        }
        if (mc.isPaused()) return;
        boolean inside = level.dimension() == ModDimensions.FROZEN_REALM && inCaves(level, player.blockPosition().above());
        strength = Mth.clamp(strength + (inside ? FADE_PER_TICK : -FADE_PER_TICK), 0.0F, 1.0F);
        if (strength <= 0.0F) return;
        for (int n = 0; n < FLAKES_PER_TICK; n++) {
            if (RANDOM.nextFloat() > strength) continue;
            double x = player.getX() + RANDOM.nextInt(2 * FLAKE_RANGE + 1) - FLAKE_RANGE + RANDOM.nextDouble();
            double y = player.getY() + RANDOM.nextInt(FLAKE_RANGE) - 3 + RANDOM.nextDouble();
            double z = player.getZ() + RANDOM.nextInt(2 * FLAKE_RANGE + 1) - FLAKE_RANGE + RANDOM.nextDouble();
            if (!level.getBlockState(BlockPos.containing(x, y, z)).isAir()) continue;
            level.addParticle(ParticleTypes.SNOWFLAKE, x, y, z, 0.0D, -0.02D - RANDOM.nextDouble() * 0.03D, 0.0D);
        }
    }

    private static boolean inCaves(ClientLevel level, BlockPos eye) {
        if (level.canSeeSky(eye)) return false;
        for (int up : LOOK_UP) {
            if (level.getBiome(eye.above(up)).is(IceCavesFeature.BIOME)) return true;
        }
        return false;
    }

    @SubscribeEvent
    public static void onRenderFog(ViewportEvent.RenderFog event) {
        if (event.getType() != FogType.NONE) return;     // under water / in lava / powder snow: leave vanilla fog
        float s = Mth.lerp((float) event.getPartialTick(), prevStrength, strength);
        if (s <= 0.0F) return;
        float far = Mth.lerp(s, event.getFarPlaneDistance(), Math.min(event.getFarPlaneDistance(), FOG_FAR));
        float near = event.getMode() == FogRenderer.FogMode.FOG_SKY ? 0.0F
                : Mth.lerp(s, event.getNearPlaneDistance(), Math.min(event.getNearPlaneDistance(), FOG_NEAR));
        event.setFarPlaneDistance(far);
        event.setNearPlaneDistance(near);
        event.setCanceled(true); // Forge: changes only apply when the event is cancelled
    }
}
