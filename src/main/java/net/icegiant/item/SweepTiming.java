package net.icegiant.item;

/**
 * Times of the Giant Rune Sword's wide sweep (key N, SwordSweepAbility; the Ice Giant's SwordSweepGoal), from
 * Rubenbuben4's Blockbench animation "Swing with the Sword" (2026-10-21, animations/ice_giant_sword_swing.json):
 * 1.4583 s long; at 0.35 s he has wound up and the slash begins - only then the hit and the sword sound; his
 * slash picture (9 frames, GiantSlashParticle) plays from 0.35 to 0.95 s, as long as the blade sweeps across.
 * <p>
 * No Minecraft classes (server and client use it, test/SweepTimingTest checks it). A new export with another
 * length: change LENGTH_SECONDS (the server holds the swinger that long).
 */
public final class SweepTiming {

    /** "animation_length" of his file. */
    public static final float LENGTH_SECONDS = 1.4583f;
    /** Wound up, the slash begins: hit, sound and the slash picture start here ... */
    public static final float SLASH_START = 0.35f;
    /** ... and the picture ends here. */
    public static final float SLASH_END = 0.95f;
    /** Pictures in his sprite sheet (3 x 3, read row by row). */
    public static final int FRAMES = 9;

    /** Server ticks after the start: the hit and the sound (0.35 s = 7 ticks). */
    public static final int HIT_TICK = Math.round(SLASH_START * 20.0f);
    /** Server ticks the swinger is busy with the sweep (the whole animation, rounded up: 30). */
    public static final int POSE_TICKS = (int) Math.ceil(LENGTH_SECONDS * 20.0f - 1.0e-4f);

    private SweepTiming() {
    }

    /** Which slash picture (0-8) shows t seconds into the sweep, or -1 before 0.35 s and from 0.95 s on. */
    public static int slashFrame(float t) {
        if (t < SLASH_START || t >= SLASH_END) return -1;
        int frame = (int) ((t - SLASH_START) / (SLASH_END - SLASH_START) * FRAMES);
        return Math.min(FRAMES - 1, Math.max(0, frame));
    }
}
