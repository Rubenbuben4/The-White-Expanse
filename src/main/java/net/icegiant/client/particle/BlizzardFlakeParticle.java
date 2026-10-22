package net.icegiant.client.particle;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.Mth;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * One snowflake of the Frozen Realm snowstorm: a small white dot that races sideways with the
 * wind (the speed it is spawned with), sinks slowly and flutters a little. Unlike the vanilla
 * snowflake it keeps its speed, so the storm looks like driven snow. It vanishes when it
 * reaches the ground, a roof or a wall top (cheap height-map check instead of block collision,
 * so thousands of flakes stay fast). Uses the vanilla "generic" particle textures, tinted white.
 */
public class BlizzardFlakeParticle extends TextureSheetParticle {

    private final float flutterPhase;

    protected BlizzardFlakeParticle(ClientLevel level, double x, double y, double z,
                                    double xd, double yd, double zd, SpriteSet sprites) {
        this(level, x, y, z, xd, yd, zd, sprites, false);
    }

    /**
     * @param falling false = fast wind-driven blizzard flake (short life, thick);
     *                true = ordinary falling snowflake that drifts down slowly (long life)
     */
    protected BlizzardFlakeParticle(ClientLevel level, double x, double y, double z,
                                    double xd, double yd, double zd, SpriteSet sprites, boolean falling) {
        super(level, x, y, z);
        this.xd = xd;
        this.yd = yd;
        this.zd = zd;
        this.hasPhysics = false;
        this.gravity = 0.0F;
        this.friction = 1.0F;
        if (falling) {
            this.lifetime = 70 + this.random.nextInt(40);
            this.quadSize = 0.06F + this.random.nextFloat() * 0.06F;
        } else {
            this.lifetime = 22 + this.random.nextInt(18);
            this.quadSize = 0.09F + this.random.nextFloat() * 0.11F;   // twice as thick as before (2026-10-02)
        }
        float shade = 0.92F + this.random.nextFloat() * 0.08F;
        this.setColor(shade, shade, 1.0F);
        this.setAlpha(0.0F);
        this.flutterPhase = this.random.nextFloat() * (float) (Math.PI * 2.0D);
        this.pickSprite(sprites);
    }

    @Override
    public void tick() {
        this.xo = this.x;
        this.yo = this.y;
        this.zo = this.z;
        if (this.age++ >= this.lifetime) {
            this.remove();
            return;
        }
        // fade in/out over 3 ticks so flakes don't pop
        int left = this.lifetime - this.age;
        this.setAlpha(0.95F * Math.min(1.0F, Math.min(this.age, left) / 3.0F));
        double flutter = Math.sin(this.age * 0.6F + this.flutterPhase) * 0.02D;
        this.x += this.xd + flutter;
        this.y += this.yd + flutter * 0.5D;
        this.z += this.zd - flutter;
        this.setPos(this.x, this.y, this.z);
        if (this.y < this.level.getHeight(Heightmap.Types.MOTION_BLOCKING, Mth.floor(this.x), Mth.floor(this.z))) {
            this.remove(); // hit the ground / a roof / a wall
        }
    }

    @Override
    public ParticleRenderType getRenderType() {
        return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
    }

    public static class Provider implements ParticleProvider<SimpleParticleType> {
        private final SpriteSet sprites;

        public Provider(SpriteSet sprites) {
            this.sprites = sprites;
        }

        @Override
        public Particle createParticle(SimpleParticleType type, ClientLevel level, double x, double y, double z,
                                       double xd, double yd, double zd) {
            return new BlizzardFlakeParticle(level, x, y, z, xd, yd, zd, this.sprites);
        }
    }

    /** Ordinary falling snowflakes (icegiant:snowfall_flake). */
    public static class FallingProvider implements ParticleProvider<SimpleParticleType> {
        private final SpriteSet sprites;

        public FallingProvider(SpriteSet sprites) {
            this.sprites = sprites;
        }

        @Override
        public Particle createParticle(SimpleParticleType type, ClientLevel level, double x, double y, double z,
                                       double xd, double yd, double zd) {
            return new BlizzardFlakeParticle(level, x, y, z, xd, yd, zd, this.sprites, true);
        }
    }
}
