package net.icegiant.client.particle;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.icegiant.client.SweepAnimationState;
import net.icegiant.item.SweepTiming;
import net.icegiant.item.SwordGeometry;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;

/**
 * The big slash of the Giant Rune Sword's wide sweep (key N, the Ice Giant's sweep; Rubenbuben4, 2026-10-21):
 * ONE huge picture - his own slash sprite sheet (textures/particle/giant_slash_0..8, cut from his
 * pixel_art_sword_slash_sprites.png) - lying flat around the swinger at the height of the sword hand. It plays
 * his 9 pictures from 0.35 to 0.95 s of his animation, timed by the same clock as the body pose
 * (SweepAnimationState, SweepTiming.slashFrame), so it runs exactly as fast as the sword.
 * <p>
 * It is not turned towards the camera: the ring lies waagerecht around the swinger (GiantSlashShape: in front
 * the thick part, running from his left over the front to his right) and turns with his body only. Drawn from
 * both sides, so it shows from above and from below; never culled, even when its middle is off screen.
 * <p>
 * Spawned on every client that sees the sweep start (SweepStartBroadcastPacket -> spawnFor), not by the server
 * and not with /particle (its provider makes none: it needs a swinger).
 */
public class GiantSlashParticle extends TextureSheetParticle {

    // ---- TUNE ----
    static final double RADIUS = 0.6D;    // ring radius: this much of the sweep's reach (giant 9 blocks, player 3.6)
    static final double HEIGHT = 1.0D;    // above the feet, x size: about where the sword hand passes
    static final float ALPHA = 1.0F;

    @Nullable private static SpriteSet sprites;

    private final LivingEntity swinger;
    private final double radius;
    private final double height;

    private GiantSlashParticle(ClientLevel level, LivingEntity swinger, SpriteSet sprites) {
        super(level, swinger.getX(), swinger.getY(), swinger.getZ());
        this.swinger = swinger;
        double size = SwordGeometry.size(swinger.getBbWidth());
        this.radius = RADIUS * SwordGeometry.SWEEP_REACH * size;
        this.height = HEIGHT * size;
        this.hasPhysics = false;
        this.gravity = 0.0F;
        this.xd = this.yd = this.zd = 0.0D;
        this.lifetime = SweepTiming.POSE_TICKS + 20;           // only a safety net - it goes when the slash is over
        this.alpha = ALPHA;
        this.setSprite(sprites.get(0, SweepTiming.FRAMES - 1));
    }

    /** The sweep of this creature has just started on this client: its slash. */
    public static void spawnFor(int entityId) {
        Minecraft mc = Minecraft.getInstance();
        if (sprites == null || mc.level == null) return;
        if (!(mc.level.getEntity(entityId) instanceof LivingEntity swinger)) return;
        mc.particleEngine.add(new GiantSlashParticle(mc.level, swinger, sprites));
    }

    @Override
    public void tick() {
        this.xo = this.x;
        this.yo = this.y;
        this.zo = this.z;
        this.setPos(this.swinger.getX(), this.swinger.getY(), this.swinger.getZ());
        float t = SweepAnimationState.getElapsedSeconds(this.swinger.getId());
        if (this.age++ >= this.lifetime || this.swinger.isRemoved() || t < 0.0F || t >= SweepTiming.SLASH_END) {
            this.remove();
        }
    }

    @Override
    public void render(VertexConsumer buffer, Camera camera, float partialTicks) {
        int frame = SweepTiming.slashFrame(SweepAnimationState.getElapsedSeconds(this.swinger.getId()));
        if (frame < 0 || sprites == null) return;
        TextureAtlasSprite sprite = sprites.get(frame, SweepTiming.FRAMES - 1);
        Vec3 cam = camera.getPosition();
        Vec3 pos = this.swinger.getPosition(partialTicks);
        float yaw = Mth.rotLerp(partialTicks, this.swinger.yBodyRotO, this.swinger.yBodyRot);
        float y = (float) (pos.y + this.height - cam.y);
        float[] u = {sprite.getU0(), sprite.getU1(), sprite.getU1(), sprite.getU0()};
        float[] v = {sprite.getV0(), sprite.getV0(), sprite.getV1(), sprite.getV1()};
        float[][] corner = new float[4][];
        for (int i = 0; i < 4; i++) {
            double[] o = GiantSlashShape.offset(GiantSlashShape.CORNERS[i][0], GiantSlashShape.CORNERS[i][1], yaw, this.radius);
            corner[i] = new float[]{(float) (pos.x + o[0] - cam.x), y, (float) (pos.z + o[1] - cam.z)};
        }
        for (int i = 0; i < 4; i++) this.vertex(buffer, corner[i], u[i], v[i]);       // seen from above
        for (int i = 3; i >= 0; i--) this.vertex(buffer, corner[i], u[i], v[i]);      // ... and from below
    }

    private void vertex(VertexConsumer buffer, float[] p, float u, float v) {
        buffer.vertex(p[0], p[1], p[2]).uv(u, v).color(this.rCol, this.gCol, this.bCol, this.alpha)
                .uv2(LightTexture.FULL_BRIGHT).endVertex();
    }

    /** Huge and lying around the swinger: its middle may be off screen while the slash is not. */
    @Override
    public boolean shouldCull() {
        return false;
    }

    @Override
    public ParticleRenderType getRenderType() {
        return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
    }

    /** Registered for the pictures (particles/giant_slash.json); makes no particle by itself (needs a swinger). */
    public static class Provider implements ParticleProvider<SimpleParticleType> {

        public Provider(SpriteSet spriteSet) {
            sprites = spriteSet;
        }

        @Override
        @Nullable
        public Particle createParticle(SimpleParticleType type, ClientLevel level, double x, double y, double z,
                                       double dx, double dy, double dz) {
            return null;
        }
    }
}
