package net.icegiant.client;

import net.minecraft.util.Mth;

/**
 * Rubenbuben4's Blockbench animation "Creator_Crystal_Animation" (Crystal_model.animation.json, 2026-10-25, 2 s loop)
 * as code - his keyframes and Molang formulas, evaluated the way Blockbench does (linear between keyframes; a formula
 * is worked out at the current time, Molang sin/cos take degrees).
 * <ul>
 *   <li>"Crystal" (all of it): from 0.25 s to 1.75 s sways (x = -sin(t*120)*6, z = cos(t*100)*4) and floats
 *       (y = sin(t*90) pixels); eases in from rest over 0 - 0.25 s and back over 1.75 - 2 s.</li>
 *   <li>"Enchantment middle" (the flat ring): one turn around the up axis per loop, tilting a little.</li>
 *   <li>"enchantment top" (the upright ring): one roll over (x -360) per loop, tilting a little.</li>
 *   <li>"Crystalcore", "Outershell", "core": stay still.</li>
 * </ul>
 */
public final class CreationCrystalAnimation {

    public static final int LENGTH_TICKS = 40;   // 2 s

    private static final float[] T = {0.0F, 0.25F, 0.5F, 0.75F, 1.0F, 1.25F, 1.5F, 1.75F, 2.0F};
    private static final float[][] MIDDLE = {
            {0, 0, 0}, {-5, 48, 0}, {-10, 87, 0}, {-5, 138, 0}, {0, 183, 0},
            {0, 228, -5}, {0, 270, -10}, {0, 313, -5}, {0, 360, 0}};
    private static final float[][] TOP = {
            {0, 0, 0}, {-45, 0, -5}, {-90, 0, -10}, {-135, 0, -5}, {-180, 0, 0},
            {-225, 0, 5}, {-270, 0, 10}, {-315, 0, 5}, {-360, 0, 0}};

    private CreationCrystalAnimation() {
    }

    private static float sinD(float deg) {
        return Mth.sin(deg * Mth.DEG_TO_RAD);
    }

    private static float cosD(float deg) {
        return Mth.cos(deg * Mth.DEG_TO_RAD);
    }

    /** The whole crystal at t seconds: {rotation x, rotation z, position y in pixels}. */
    public static float[] crystal(float t) {
        float k;                                                  // share of the formula: eases in and out
        if (t < 0.25F) k = t / 0.25F;
        else if (t <= 1.75F) k = 1.0F;
        else k = Math.max(0.0F, (2.0F - t) / 0.25F);
        return new float[]{
                k * -sinD(t * 120.0F) * 6.0F,
                k * cosD(t * 100.0F) * 4.0F,
                k * sinD(t * 90.0F)};
    }

    public static float[] middleRing(float t) {
        return sample(MIDDLE, t);
    }

    public static float[] topRing(float t) {
        return sample(TOP, t);
    }

    private static float[] sample(float[][] keys, float t) {
        if (t <= T[0]) return keys[0];
        for (int i = 0; i < T.length - 1; i++) {
            if (t <= T[i + 1]) {
                float f = (t - T[i]) / (T[i + 1] - T[i]);
                float[] a = keys[i], b = keys[i + 1];
                return new float[]{Mth.lerp(f, a[0], b[0]), Mth.lerp(f, a[1], b[1]), Mth.lerp(f, a[2], b[2])};
            }
        }
        return keys[keys.length - 1];
    }
}
