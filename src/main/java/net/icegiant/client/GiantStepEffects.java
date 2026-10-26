package net.icegiant.client;

import net.icegiant.IceGiantMod;
import net.icegiant.entity.IceGiantEntity;
import net.icegiant.registry.ModSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * An Ice Giant's footstep, on each client (Rubenbuben4, 2026-10-26): called from IceGiantEntity.tickFootsteps each
 * time a foot comes down.
 * <ul>
 *   <li>the giant's step sound, heard far away but faint, loud close by (FalloffSound; walking 40, running 56 blocks);</li>
 *   <li>the ground's own step sound, deep and only close by - snow crunches, ice clinks, stone thuds;</li>
 *   <li>a cloud of the ground's dust at the foot (and snowflakes on snow), bigger when he runs;</li>
 *   <li>the ground shakes - the camera trembles briefly, the closer the stronger (up to 6 blocks).</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = IceGiantMod.MOD_ID, value = Dist.CLIENT)
public final class GiantStepEffects {

    static final float WALK_FALLOFF = 40.0F, RUN_FALLOFF = 56.0F;
    static final float GROUND_VOLUME = 0.9F, GROUND_PITCH = 0.55F;
    static final double FOOT_SIDE = 0.12D;                     // legs are 1.9 px off the middle of the player model
    static final int DUST_WALK = 10, DUST_RUN = 18;
    static final double SHAKE_RANGE = 6.0D;                  // (2026-10-26: was 24, then 8 - Rubenbuben4: 6)
    static final float SHAKE_DEGREES = 1.4F, SHAKE_TICKS = 8.0F, SHAKE_WALK = 0.6F, SHAKE_RUN = 1.0F;

    private static float shake;
    private static long shakeStart;

    private GiantStepEffects() {
    }

    public static void step(IceGiantEntity giant, BlockPos pos, BlockState ground, boolean running, boolean left) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        LocalPlayer player = mc.player;
        if (level == null || player == null) return;
        RandomSource r = giant.getRandom();

        // where the foot comes down: left or right of his middle
        float yaw = giant.yBodyRot * Mth.DEG_TO_RAD;
        double side = (left ? 1.0D : -1.0D) * FOOT_SIDE * IceGiantEntity.SCALE;
        double fx = giant.getX() + Math.cos(yaw) * side, fz = giant.getZ() + Math.sin(yaw) * side;
        double fy = pos.getY() + 1.0D;

        FalloffSound.playAt(running ? ModSounds.ICE_GIANT_RUN_STEP.get() : ModSounds.ICE_GIANT_WALK_STEP.get(),
                giant.getSoundSource(), fx, fy, fz, 1.0F, (running ? 0.92F : 0.9F) + r.nextFloat() * 0.15F,
                running ? RUN_FALLOFF : WALK_FALLOFF);
        SoundType type = ground.getSoundType(level, pos, giant);
        level.playLocalSound(fx, fy, fz, type.getStepSound(), giant.getSoundSource(),
                type.getVolume() * GROUND_VOLUME, type.getPitch() * GROUND_PITCH, false);

        double d = player.position().distanceTo(new net.minecraft.world.phys.Vec3(fx, fy, fz));
        if (d < 64.0D && ground.getRenderShape() != RenderShape.INVISIBLE) {
            BlockParticleOption dust = new BlockParticleOption(ParticleTypes.BLOCK, ground);
            boolean snow = ground.is(BlockTags.SNOW) || ground.is(Blocks.SNOW) || ground.is(Blocks.POWDER_SNOW);
            int n = running ? DUST_RUN : DUST_WALK;
            double spread = 0.35D * IceGiantEntity.SCALE;
            for (int i = 0; i < n; i++) {
                double a = r.nextDouble() * Math.PI * 2.0D, rr = (0.3D + 0.7D * r.nextDouble()) * spread;
                double cx = Math.cos(a), cz = Math.sin(a);
                level.addParticle(dust, fx + cx * rr, fy + 0.05D, fz + cz * rr, cx * 0.15D, 0.1D + r.nextDouble() * 0.15D, cz * 0.15D);
                if (snow && (i & 1) == 0) {
                    level.addParticle(ParticleTypes.SNOWFLAKE, fx + cx * rr, fy + 0.2D, fz + cz * rr,
                            cx * 0.05D, 0.05D + r.nextDouble() * 0.08D, cz * 0.05D);
                }
            }
        }
        if (d < SHAKE_RANGE && player.getVehicle() != giant) {
            float k = (float) (1.0D - d / SHAKE_RANGE);
            float strength = (running ? SHAKE_RUN : SHAKE_WALK) * k * k;
            long now = level.getGameTime();
            float left0 = remaining(now, 0.0F);
            if (strength > left0) {                                 // the stronger step wins
                shake = strength;
                shakeStart = now;
            }
        }
    }

    private static float remaining(long now, float partial) {
        float t = now - shakeStart + partial;
        return t >= SHAKE_TICKS ? 0.0F : shake * (1.0F - t / SHAKE_TICKS);
    }

    @SubscribeEvent
    public static void onCameraAngles(ViewportEvent.ComputeCameraAngles event) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null || shake <= 0.0F) return;
        float partial = (float) event.getPartialTick();
        float s = remaining(level.getGameTime(), partial);
        if (s <= 0.0F) {
            shake = 0.0F;
            return;
        }
        float t = level.getGameTime() - shakeStart + partial;
        float amp = s * SHAKE_DEGREES;
        event.setPitch(event.getPitch() + Mth.sin(t * 2.9F) * amp);
        event.setRoll(event.getRoll() + Mth.sin(t * 2.1F + 1.1F) * amp * 0.5F);
        event.setYaw(event.getYaw() + Mth.sin(t * 3.7F + 0.4F) * amp * 0.25F);
    }
}
