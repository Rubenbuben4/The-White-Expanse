package net.icegiant.client;

import java.util.HashMap;
import java.util.Map;

/**
 * Client-seitiger Zustand: wann hat der (rechtsklick-ausgelöste) Schwung
 * für welchen Spieler (Entity-ID) begonnen? Wird über
 * SwingStartBroadcastPacket vom Server gefüllt, vom PlayerModelArmMixin
 * ausgelesen, um die Körperhaltung zu zeigen - genau wie SlamAnimationState,
 * nur für den Swing statt den Slam.
 * <p>
 * Vorher wurde der Swing über die vanilla Angriffs-Animation (getAttackAnim,
 * ausgelöst durch Linksklick) erkannt. Jetzt löst NUR noch Rechtsklick den
 * Swing aus, Linksklick bleibt der ganz normale vanilla Angriff - deshalb
 * braucht es diesen eigenen, expliziten Zustand statt sich auf getAttackAnim
 * zu verlassen.
 */
public class SwingAnimationState {

    public static final float SWING_LENGTH_SECONDS = 1.2917f;

    private static final Map<Integer, Long> SWING_START_NANOS = new HashMap<>();

    private SwingAnimationState() {
    }

    public static void start(int entityId) {
        SWING_START_NANOS.put(entityId, System.nanoTime());
    }

    /** Gibt die vergangene Zeit in Sekunden zurück, oder -1 falls kein aktiver Swing. */
    public static float getElapsedSeconds(int entityId) {
        Long start = SWING_START_NANOS.get(entityId);
        if (start == null) return -1f;
        float elapsed = (System.nanoTime() - start) / 1_000_000_000f;
        if (elapsed > SWING_LENGTH_SECONDS) return -1f;
        return elapsed;
    }
}
