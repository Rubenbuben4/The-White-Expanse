package net.icegiant.client;

import net.icegiant.IceGiantMod;
import net.icegiant.dimension.ModDimensions;
import net.icegiant.registry.ModParticles;
import net.icegiant.registry.ModSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.FogRenderer;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.FogType;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Client side of the Frozen Realm snowstorm (server: weather/Snowstorms):
 *  - white-out: fog closes in to ~20 blocks (a bit less during gusts), lighter under a roof
 *  - driven snow: lots of thick, fast BlizzardFlakeParticles racing sideways with the wind, on top of
 *    heavy vanilla snowfall (the client's rain level is raised; the biomes turn it into snow), doubled by
 *    a second layer of slowly falling snowflakes (icegiant:snowfall_flake)
 *  - sound: two looping wind beds (mid howl + low rumble) and random gusts that also speed up
 *    the snow for a few seconds (recordings by Dragon Studio / Pixabay)
 * Everything fades in and out over ~10 s. The wind sounds are in the "Ambient/Environment" volume slider.
 * Own implementation.
 */
@Mod.EventBusSubscriber(modid = IceGiantMod.MOD_ID, value = Dist.CLIENT)
public final class SnowstormClient {

    private static final float FADE_PER_TICK = 1.0F / 200.0F;   // 10 s fade in/out
    private static final float EXPOSURE_PER_TICK = 0.05F;       // 1 s to adjust when going in/out of shelter
    private static final float STORM_FOG_DISTANCE = 20.0F;     // you see about 20 blocks
    private static final float GUST_FOG_DISTANCE = 14.0F;       // ... less while a gust blows
    private static final float SHELTER_FOG_KEEP = 0.25F;       // under a roof the fog is mostly gone

    private static final double WIND_SPEED = 0.95D;             // blocks per tick (~19 m/s)
    private static final int MAX_FLAKES_PER_TICK = 180;          // fast blizzard flakes (was 110)
    private static final int MAX_SNOWFALL_PER_TICK = 60;         // extra falling snow = about twice the normal snowfall
    private static final double SNOWFALL_RADIUS = 18.0D;
    private static final double FLAKE_RADIUS = 16.0D;

    private static final int GUST_MIN_TICKS = 100, GUST_MAX_TICKS = 260;   // a gust every 5-13 s

    private static boolean stormActive;
    private static float windYaw;
    private static float intensity, prevIntensity;   // 0..1 storm strength
    private static float exposure, prevExposure;     // 0..1, 1 = under the open sky
    private static float gust, prevGust;             // 0..1, rises with each gust sound and dies away
    private static int gustCooldown = 60;
    private static boolean overridingRain;
    private static WindLoop windMain, windLow;

    private SnowstormClient() {
    }

    /** Called from SnowstormPacket. */
    public static void setStorm(boolean active, float wind) {
        stormActive = active;
        windYaw = wind;
    }

    /** Fog / sound strength: storm intensity, reduced (not removed) under a roof. */
    private static float strength(float partialTick) {
        float e = Mth.lerp(partialTick, prevExposure, exposure);
        return Mth.lerp(partialTick, prevIntensity, intensity) * (SHELTER_FOG_KEEP + (1.0F - SHELTER_FOG_KEEP) * e);
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        LocalPlayer player = mc.player;
        prevIntensity = intensity;
        prevExposure = exposure;
        prevGust = gust;
        if (level == null || player == null) {
            intensity = exposure = gust = 0.0F;
            stormActive = false;
            overridingRain = false;
            return;
        }
        if (mc.isPaused()) return;

        boolean inRealm = level.dimension() == ModDimensions.FROZEN_REALM;
        float target = stormActive && inRealm ? 1.0F : 0.0F;
        intensity = target > intensity ? Math.min(target, intensity + FADE_PER_TICK)
                : Math.max(target, intensity - FADE_PER_TICK);
        if (!inRealm) intensity = 0.0F;

        boolean outside = !net.icegiant.weather.StormShelter.isSheltered(level, BlockPos.containing(player.getEyePosition()));
        exposure = outside ? Math.min(1.0F, exposure + EXPOSURE_PER_TICK) : Math.max(0.0F, exposure - EXPOSURE_PER_TICK);
        gust = Math.max(0.0F, gust - 0.008F);

        // heavy vanilla snowfall: raise the client's rain level while the storm lasts, then hand it back
        if (intensity > 0.0F && inRealm) {
            float base = level.getLevelData().isRaining() ? 1.0F : 0.0F;
            level.setRainLevel(Math.max(base, intensity));
            overridingRain = true;
        } else if (overridingRain) {
            level.setRainLevel(level.getLevelData().isRaining() ? 1.0F : 0.0F);
            overridingRain = false;
        }

        if (intensity > 0.02F) {
            spawnFlakes(level, player);
        }
        updateSounds(mc, level.random);
    }

    private static void spawnFlakes(ClientLevel level, LocalPlayer player) {
        RandomSource random = level.random;
        double rad = Math.toRadians(windYaw);
        double dirX = -Math.sin(rad);
        double dirZ = Math.cos(rad);
        double speed = WIND_SPEED * (1.0D + 0.6D * gust);
        int count = (int) (MAX_FLAKES_PER_TICK * intensity);
        for (int i = 0; i < count; i++) {
            // spawn upwind so the flakes race past and around the player
            double x = player.getX() + (random.nextDouble() * 2.0D - 1.0D) * FLAKE_RADIUS - dirX * 12.0D;
            double z = player.getZ() + (random.nextDouble() * 2.0D - 1.0D) * FLAKE_RADIUS - dirZ * 12.0D;
            double y = player.getY() - 3.0D + random.nextDouble() * 14.0D;
            if (y < level.getHeight(Heightmap.Types.MOTION_BLOCKING, Mth.floor(x), Mth.floor(z))) continue;
            double s = speed * (0.75D + random.nextDouble() * 0.5D);
            double side = (random.nextDouble() - 0.5D) * 0.25D;   // a little spread across the wind
            level.addParticle(ModParticles.BLIZZARD_FLAKE.get(), x, y, z,
                    dirX * s - dirZ * side, -0.08D - random.nextDouble() * 0.14D, dirZ * s + dirX * side);
        }
        // ordinary snowfall on top of the vanilla snow: falls slowly, drifts a little with the wind
        int falling = (int) (MAX_SNOWFALL_PER_TICK * intensity);
        for (int i = 0; i < falling; i++) {
            double x = player.getX() + (random.nextDouble() * 2.0D - 1.0D) * SNOWFALL_RADIUS;
            double z = player.getZ() + (random.nextDouble() * 2.0D - 1.0D) * SNOWFALL_RADIUS;
            double y = player.getY() + 2.0D + random.nextDouble() * 14.0D;
            if (y < level.getHeight(Heightmap.Types.MOTION_BLOCKING, Mth.floor(x), Mth.floor(z))) continue;
            double drift = 0.18D + random.nextDouble() * 0.12D;
            level.addParticle(ModParticles.SNOWFALL_FLAKE.get(), x, y, z,
                    dirX * drift, -0.12D - random.nextDouble() * 0.08D, dirZ * drift);
        }
    }

    private static void updateSounds(Minecraft mc, RandomSource random) {
        if (intensity <= 0.01F) {
            gustCooldown = 60;
            return;
        }
        if (windMain == null || windMain.isStopped()) {
            windMain = new WindLoop(ModSounds.SNOWSTORM_WIND.get(), 0.85F, 1.0F);
            mc.getSoundManager().play(windMain);
        }
        if (windLow == null || windLow.isStopped()) {
            windLow = new WindLoop(ModSounds.SNOWSTORM_WIND_LOW.get(), 0.65F, 1.0F);
            mc.getSoundManager().play(windLow);
        }
        if (intensity > 0.3F && --gustCooldown <= 0) {
            gustCooldown = GUST_MIN_TICKS + random.nextInt(GUST_MAX_TICKS - GUST_MIN_TICKS + 1);
            float volume = (0.55F + random.nextFloat() * 0.45F) * intensity * (0.4F + 0.6F * exposure);
            float pitch = 0.88F + random.nextFloat() * 0.24F;
            mc.getSoundManager().play(new SimpleSoundInstance(ModSounds.SNOWSTORM_GUST.get().getLocation(),
                    SoundSource.AMBIENT, volume, pitch, random, false, 0, SoundInstance.Attenuation.NONE,
                    0.0D, 0.0D, 0.0D, true));
            gust = 1.0F;
        }
    }

    @SubscribeEvent
    public static void onRenderFog(ViewportEvent.RenderFog event) {
        if (event.getType() != FogType.NONE) return; // under water / in lava / powder snow: leave vanilla fog
        float partial = (float) event.getPartialTick();
        float s = strength(partial);
        if (s <= 0.0F) return;
        float g = Mth.lerp(partial, prevGust, gust);
        float stormFar = Mth.lerp(g, STORM_FOG_DISTANCE, GUST_FOG_DISTANCE);
        float far = Mth.lerp(s, event.getFarPlaneDistance(), Math.min(event.getFarPlaneDistance(), stormFar));
        float near = event.getMode() == FogRenderer.FogMode.FOG_SKY ? 0.0F
                : Mth.lerp(s, event.getNearPlaneDistance(), 0.0F);
        event.setFarPlaneDistance(far);
        event.setNearPlaneDistance(near);
        event.setCanceled(true); // Forge: changes only apply when the event is cancelled
    }

    @SubscribeEvent
    public static void onFogColor(ViewportEvent.ComputeFogColor event) {
        float s = strength((float) event.getPartialTick()) * 0.9F;
        if (s <= 0.0F) return;
        event.setRed(Mth.lerp(s, event.getRed(), 0.82F));
        event.setGreen(Mth.lerp(s, event.getGreen(), 0.87F));
        event.setBlue(Mth.lerp(s, event.getBlue(), 0.93F));
    }

    /** Looping wind bed; follows storm strength, quieter under a roof. */
    private static final class WindLoop extends AbstractTickableSoundInstance {

        private final float baseVolume;

        WindLoop(SoundEvent sound, float baseVolume, float pitch) {
            super(sound, SoundSource.AMBIENT, RandomSource.create());
            this.baseVolume = baseVolume;
            this.looping = true;
            this.delay = 0;
            this.relative = true;
            this.attenuation = SoundInstance.Attenuation.NONE;
            this.pitch = pitch;
            this.volume = 0.01F;
        }

        @Override
        public void tick() {
            if (intensity <= 0.01F) {
                this.stop();
                return;
            }
            this.volume = Math.max(0.01F, this.baseVolume * intensity * (0.3F + 0.7F * exposure));
        }

        @Override
        public boolean canStartSilent() {
            return true;
        }
    }
}
