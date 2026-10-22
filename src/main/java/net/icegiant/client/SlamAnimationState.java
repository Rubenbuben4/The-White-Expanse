package net.icegiant.client;

import java.util.HashMap;
import java.util.Map;

/**
 * Client-seitiger Zustand: wann hat der Slam für welchen Spieler (Entity-ID)
 * begonnen? Wird über SlamStartBroadcastPacket vom Server gefüllt, vom
 * PlayerModelArmMixin ausgelesen, um die richtige Körperhaltung zu zeigen.
 */
public class SlamAnimationState {

    public static final float SLAM_LENGTH_SECONDS = 6.3333f;

    private static final Map<Integer, Long> SLAM_START_NANOS = new HashMap<>();

    private SlamAnimationState() {
    }

    public static void start(int entityId) {
        SLAM_START_NANOS.put(entityId, System.nanoTime());
    }

    /** Gibt die vergangene Zeit in Sekunden zurück, oder -1 falls kein aktiver Slam. */
    public static float getElapsedSeconds(int entityId) {
        Long start = SLAM_START_NANOS.get(entityId);
        if (start == null) return -1f;
        float elapsed = (System.nanoTime() - start) / 1_000_000_000f;
        if (elapsed > SLAM_LENGTH_SECONDS) return -1f;
        return elapsed;
    }
}
