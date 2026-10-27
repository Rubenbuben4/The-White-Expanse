package net.icegiant.client;

import net.icegiant.registry.ModSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.monster.Slime;

import java.util.HashSet;
import java.util.Set;

/**
 * Client side of the crawling slimes (2026-10-27): each crawling slime nearby plays Rubenbuben4's glibber sound as a
 * loop that follows it and gets louder the faster it crawls, and fades out when it stops. At most
 * {@value #MAX_LOOPS} at once, only within {@value #RANGE} blocks.
 */
public final class SlimeClient {

    static final int MAX_LOOPS = 8;
    static final double RANGE = 20.0D;
    private static final Set<Integer> PLAYING = new HashSet<>();

    private SlimeClient() {
    }

    /** Called each tick a slime crawls (SlimeLife.afterTick). */
    public static void crawling(Slime s) {
        if (PLAYING.contains(s.getId()) || PLAYING.size() >= MAX_LOOPS) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.player.distanceToSqr(s) > RANGE * RANGE) return;
        PLAYING.add(s.getId());
        mc.getSoundManager().play(new CrawlLoop(s));
    }

    private static final class CrawlLoop extends AbstractTickableSoundInstance {
        private final Slime slime;
        private int idle;

        CrawlLoop(Slime slime) {
            super(ModSounds.SLIME_CRAWL.get(), SoundSource.HOSTILE, SoundInstance.createUnseededRandom());
            this.slime = slime;
            this.looping = true;
            this.delay = 0;
            this.attenuation = SoundInstance.Attenuation.LINEAR;
            this.volume = 0.01F;
            this.pitch = Mth.clamp(1.35F - 0.12F * slime.getSize(), 0.7F, 1.3F);
            this.x = slime.getX();
            this.y = slime.getY();
            this.z = slime.getZ();
        }

        @Override
        public boolean canStartSilent() {
            return true;
        }

        @Override
        public void tick() {
            if (this.slime.isRemoved() || !this.slime.isAlive()) {
                this.finish();
                return;
            }
            this.x = this.slime.getX();
            this.y = this.slime.getY();
            this.z = this.slime.getZ();
            double v = this.slime.onGround() ? Math.hypot(this.slime.getX() - this.slime.xo, this.slime.getZ() - this.slime.zo) : 0.0D;
            float want = (float) Mth.clamp(v / 0.12D, 0.0D, 1.0D) * (0.3F + 0.1F * Math.min(4, this.slime.getSize()));
            this.volume = Mth.lerp(0.2F, this.volume, want);
            this.idle = want < 0.02F ? this.idle + 1 : 0;
            if (this.idle > 40) this.finish();
        }

        private void finish() {
            PLAYING.remove(this.slime.getId());
            this.stop();
        }
    }
}
