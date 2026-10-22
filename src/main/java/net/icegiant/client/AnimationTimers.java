package net.icegiant.client;

import net.minecraft.world.entity.player.Player;

import java.util.HashMap;
import java.util.Map;

/**
 * Zentrale Zeitmessung, damit Körper-Mixin UND Axt-Modell exakt dieselbe
 * Zeitbasis nutzen (kein GeckoLib-eigenes Triggern mehr für die Axt-
 * Bewegung - alles läuft jetzt über dieselbe Java-Interpolation wie der
 * Körper).
 */
public class AnimationTimers {

    public static final float SWING_LENGTH = 1.2917f;
    public static final float SLAM_LENGTH = 3.2917f;
    public static final float LAZY_LENGTH = 26.0f;

    private static final Map<Integer, Float> PREV_ATTACK_ANIM = new HashMap<>();
    private static final Map<Integer, Long> SWING_START_NANOS = new HashMap<>();

    private AnimationTimers() {
    }

    /** -1 wenn gerade kein Swing läuft. */
    public static float getSwingElapsed(Player player) {
        int id = player.getId();
        float currentAttackAnim = player.getAttackAnim(1.0F);
        Float prev = PREV_ATTACK_ANIM.get(id);
        if ((prev == null || prev < 0.05f) && currentAttackAnim > 0.05f) {
            SWING_START_NANOS.put(id, System.nanoTime());
        }
        PREV_ATTACK_ANIM.put(id, currentAttackAnim);

        Long start = SWING_START_NANOS.get(id);
        if (start == null) return -1f;
        float elapsed = (System.nanoTime() - start) / 1_000_000_000f;
        if (elapsed > SWING_LENGTH) return -1f;
        return elapsed;
    }

    /** -1 wenn gerade kein Slam läuft. */
    public static float getSlamElapsed(int entityId) {
        return SlamAnimationState.getElapsedSeconds(entityId);
    }

    /** Freilaufender Loop für die Ruhepose - läuft immer, unabhängig von Triggern. */
    public static float getLazyElapsed() {
        double seconds = System.nanoTime() / 1_000_000_000.0;
        return (float) (seconds % LAZY_LENGTH);
    }
}
