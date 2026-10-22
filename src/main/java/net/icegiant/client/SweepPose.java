package net.icegiant.client;

/**
 * Body pose of the Giant Rune Sword's wide sweep (key N, SwordSweepAbility), 1 s: the sword arm swings out to the
 * right, then sweeps flat across the front of the body to the left, the torso turning with it, the other arm
 * countering a little; legs stay as they are. Angles in degrees like the Blockbench keyframes of the other poses
 * (PlayerModelArmMixin applies them; positive y turns a part to the creature's right). No Minecraft classes, so the
 * keyframes can be checked outside the game (test/SweepPoseTest). Made for the sword on 2026-10-17.
 * <p>
 * NO LONGER USED since 2026-10-21: Rubenbuben4's own Blockbench animation replaced it (SweepAnimation). Kept only
 * so the old pose can be looked up; nothing calls it.
 */
public final class SweepPose {

    public static final float LENGTH_SECONDS = 1.0f;

    private static final float[] T_RARM = {0f, 0.25f, 0.55f, 1.0f};
    private static final float[][] R_RARM = {{-40f, 0f, 0f}, {-80f, 70f, -10f}, {-85f, -60f, 10f}, {-40f, 0f, 0f}};

    private static final float[] T_BODY = {0f, 0.25f, 0.55f, 1.0f};
    private static final float[][] R_BODY = {{0f, 0f, 0f}, {0f, 25f, 0f}, {5f, -30f, 0f}, {0f, 0f, 0f}};

    private static final float[] T_LARM = {0f, 0.25f, 0.55f, 1.0f};
    private static final float[][] R_LARM = {{0f, 0f, 0f}, {-25f, -10f, -10f}, {-30f, 15f, -5f}, {0f, 0f, 0f}};

    private SweepPose() {
    }

    /** Rotation (x, y, z degrees) of "rightArm", "leftArm" or "body" at t seconds into the sweep. */
    public static float[] sample(String part, float t) {
        return switch (part) {
            case "rightArm" -> interp(T_RARM, R_RARM, t);
            case "leftArm" -> interp(T_LARM, R_LARM, t);
            case "body" -> interp(T_BODY, R_BODY, t);
            default -> throw new IllegalArgumentException(part);
        };
    }

    /** Between keyframes eased in and out (smoothstep): the sweep speeds up and slows down instead of jerking. */
    private static float[] interp(float[] times, float[][] values, float t) {
        if (t <= times[0]) return values[0].clone();
        if (t >= times[times.length - 1]) return values[values.length - 1].clone();
        for (int i = 0; i < times.length - 1; i++) {
            if (t <= times[i + 1]) {
                float f = (t - times[i]) / (times[i + 1] - times[i]);
                f = f * f * (3f - 2f * f);
                float[] a = values[i], b = values[i + 1];
                return new float[]{a[0] + (b[0] - a[0]) * f, a[1] + (b[1] - a[1]) * f, a[2] + (b[2] - a[2]) * f};
            }
        }
        return values[values.length - 1].clone();
    }
}
