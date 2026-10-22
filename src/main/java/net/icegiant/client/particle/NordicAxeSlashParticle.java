package net.icegiant.client.particle;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * The Nordic Axe swing-slash effect. Unlike a normal particle it does NOT
 * bill-board to face the camera - it renders at a fixed rotation in world
 * space (parallel to the ground, facing the direction the player was
 * looking when it spawned), so it reads as a slash left hanging in the air
 * rather than a sprite that always faces you.
 * <p>
 * AxeParticleTracker repurposes the particle-spawn dx/dy/dz parameters as
 * this fixed rotation (radians) instead of a velocity.
 */
public class NordicAxeSlashParticle extends TextureSheetParticle {

    private final float fixedRotX;
    private final float fixedRotY;
    private final float fixedRotZ;
    private final SpriteSet sprites;

    protected NordicAxeSlashParticle(ClientLevel level, double x, double y, double z,
                                      double rotX, double rotY, double rotZ, SpriteSet sprites) {
        super(level, x, y, z);
        this.sprites = sprites;
        this.fixedRotX = (float) rotX;
        this.fixedRotY = (float) rotY;
        this.fixedRotZ = (float) rotZ;

        this.xd = 0.0D;
        this.yd = 0.0D;
        this.zd = 0.0D;
        this.hasPhysics = false;
        this.gravity = 0.0F;
        this.quadSize = 1.8F;
        this.lifetime = 6;
        this.setSpriteFromAge(sprites);
    }

    @Override
    public void tick() {
        this.xo = this.x;
        this.yo = this.y;
        this.zo = this.z;
        if (this.age++ >= this.lifetime) {
            this.remove();
        } else {
            this.setSpriteFromAge(this.sprites);
        }
    }

    @Override
    public float getQuadSize(float partialTicks) {
        // No shrink/grow over lifetime - the sprite frames themselves fade out.
        return this.quadSize;
    }

    @Override
    public void render(VertexConsumer buffer, Camera camera, float partialTicks) {
        Vec3 camPos = camera.getPosition();
        float x = (float) (Mth.lerp(partialTicks, this.xo, this.x) - camPos.x());
        float y = (float) (Mth.lerp(partialTicks, this.yo, this.y) - camPos.y());
        float z = (float) (Mth.lerp(partialTicks, this.zo, this.z) - camPos.z());

        Quaternionf rotation = new Quaternionf().rotateXYZ(fixedRotX, fixedRotY, fixedRotZ);
        float size = this.getQuadSize(partialTicks);

        Vector3f[] corners = new Vector3f[]{
                new Vector3f(-1.0F, -1.0F, 0.0F),
                new Vector3f(-1.0F, 1.0F, 0.0F),
                new Vector3f(1.0F, 1.0F, 0.0F),
                new Vector3f(1.0F, -1.0F, 0.0F)
        };
        for (Vector3f corner : corners) {
            corner.rotate(rotation);
            corner.mul(size);
            corner.add(x, y, z);
        }

        float u0 = this.getU0();
        float u1 = this.getU1();
        float v0 = this.getV0();
        float v1 = this.getV1();
        int light = this.getLightColor(partialTicks);

        buffer.vertex(corners[0].x(), corners[0].y(), corners[0].z()).uv(u1, v1)
                .color(this.rCol, this.gCol, this.bCol, this.alpha).uv2(light).endVertex();
        buffer.vertex(corners[1].x(), corners[1].y(), corners[1].z()).uv(u1, v0)
                .color(this.rCol, this.gCol, this.bCol, this.alpha).uv2(light).endVertex();
        buffer.vertex(corners[2].x(), corners[2].y(), corners[2].z()).uv(u0, v0)
                .color(this.rCol, this.gCol, this.bCol, this.alpha).uv2(light).endVertex();
        buffer.vertex(corners[3].x(), corners[3].y(), corners[3].z()).uv(u0, v1)
                .color(this.rCol, this.gCol, this.bCol, this.alpha).uv2(light).endVertex();
    }

    @Override
    public ParticleRenderType getRenderType() {
        return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
    }

    public static class Provider implements ParticleProvider<SimpleParticleType> {
        private final SpriteSet spriteSet;

        public Provider(SpriteSet spriteSet) {
            this.spriteSet = spriteSet;
        }

        @Override
        public Particle createParticle(SimpleParticleType type, ClientLevel level, double x, double y, double z,
                                        double rotX, double rotY, double rotZ) {
            return new NordicAxeSlashParticle(level, x, y, z, rotX, rotY, rotZ, spriteSet);
        }
    }
}
