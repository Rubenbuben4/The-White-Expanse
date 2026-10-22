package net.icegiant.client;

import java.util.HashMap;
import java.util.Map;

/**
 * Client side: when did the Giant Rune Sword's wide sweep (SwordSweepAbility) start for which entity? Filled by
 * SweepStartBroadcastPacket, read by PlayerModelArmMixin for the body pose (SweepAnimation) and by the big slash
 * (GiantSlashParticle) - one clock for both, so the slash runs exactly with the sword. Like SwingAnimationState.
 */
public class SweepAnimationState {

    private static final Map<Integer, Long> START_NANOS = new HashMap<>();

    private SweepAnimationState() {
    }

    public static void start(int entityId) {
        START_NANOS.put(entityId, System.nanoTime());
    }

    /** Seconds since the sweep started, or -1 when there is none (or it is over). */
    public static float getElapsedSeconds(int entityId) {
        Long start = START_NANOS.get(entityId);
        if (start == null) return -1f;
        float elapsed = (System.nanoTime() - start) / 1_000_000_000f;
        if (elapsed > net.icegiant.item.SweepTiming.LENGTH_SECONDS) {
            START_NANOS.remove(entityId);
            return -1f;
        }
        return elapsed;
    }
}
