package net.icegiant.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * A loud sound that follows its entity and fades away clearly with distance (Rubenbuben4, 2026-10-26: "die Riesen,
 * wenn sie brüllen, kann man es weiter weg genau so laut hören als wenn man direkt bei ihnen steht").
 * <p>
 * Minecraft fades a sound linearly over its range - over 64 blocks the roar was still at half strength 32 blocks
 * away, and the recording is mixed very loud. Here the volume is turned down a second time with distance
 * (1 - d / falloff), on top of Minecraft's own fading over the sound's attenuation_distance (sounds.json), so it is
 * full next to the giant and only faint far away. Needs a mono sound (stereo sounds are not placed in the world).
 */
public class FalloffSound extends AbstractTickableSoundInstance {

    @javax.annotation.Nullable private final Entity source;     // null: stays where it was played
    private final float baseVolume;
    private final float falloff;

    private FalloffSound(SoundEvent event, SoundSource category, @javax.annotation.Nullable Entity source,
                         double x, double y, double z, float volume, float pitch, float falloff) {
        super(event, category, SoundInstance.createUnseededRandom());
        this.source = source;
        this.baseVolume = volume;
        this.falloff = falloff;
        this.pitch = pitch;
        this.looping = false;
        this.delay = 0;
        this.attenuation = SoundInstance.Attenuation.LINEAR;
        this.x = x;
        this.y = y;
        this.z = z;
        follow();
    }

    /** Plays {@code event} at the entity; it fades out completely {@code falloff} blocks away. */
    public static void play(SoundEvent event, SoundSource category, Entity source, float volume, float pitch, float falloff) {
        Minecraft.getInstance().getSoundManager().play(new FalloffSound(event, category, source,
                source.getX(), source.getEyeY(), source.getZ(), volume, pitch, falloff));
    }

    /** Plays {@code event} at a fixed spot (e.g. a footstep); not at all if the listener is further than falloff. */
    public static void playAt(SoundEvent event, SoundSource category, double x, double y, double z, float volume,
                              float pitch, float falloff) {
        Player listener = Minecraft.getInstance().player;
        if (listener != null && listener.getEyePosition().distanceToSqr(x, y, z) > (double) falloff * falloff) return;
        Minecraft.getInstance().getSoundManager().play(new FalloffSound(event, category, null, x, y, z, volume, pitch, falloff));
    }

    private void follow() {
        if (this.source != null) {
            this.x = this.source.getX();
            this.y = this.source.getEyeY();
            this.z = this.source.getZ();
        }
        Player listener = Minecraft.getInstance().player;
        double d = listener == null ? 0.0D : listener.getEyePosition().distanceTo(new Vec3(this.x, this.y, this.z));
        this.volume = this.baseVolume * (float) Math.max(0.0D, 1.0D - d / this.falloff);
    }

    @Override
    public boolean canStartSilent() {
        return true;   // (far away it starts at 0 and gets louder if you come closer)
    }

    @Override
    public void tick() {
        follow();
    }
}
