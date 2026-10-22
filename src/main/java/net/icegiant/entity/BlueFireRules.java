package net.icegiant.entity;

/**
 * Timing of the blue flames the Giant Rune Sword 2.0 sets (BlueFire), without Minecraft classes, so it can be
 * checked outside the game (test/BlueFireRulesTest). Rubenbuben4's choice (2026-10-21): like soul fire, 2 damage
 * a second, burning as long as Fire Aspect IV did (16 s); water, rain and powder snow do not put it out.
 */
public final class BlueFireRules {

    private BlueFireRules() {
    }

    /** How long one hit sets the flames burning (Fire Aspect IV: 4 x 4 s). */
    public static final int DURATION_TICKS = 16 * 20;
    /** Damage once a second, like standing in soul fire. */
    public static final float DAMAGE = 2.0F;
    /** Ticks between two damages. */
    public static final int INTERVAL = 20;

    /** Burning time after a new hit: topped up, never shortened. */
    public static int ignite(int remaining, int added) {
        return Math.max(remaining, added);
    }

    /** Burning time one tick later. */
    public static int next(int remaining) {
        return Math.max(0, remaining - 1);
    }

    /**
     * Damage in this tick? burned: ticks it has burned so far without going out (a new hit does not start it
     * again, so it stays once a second); the first damage comes right with the hit.
     */
    public static boolean damageNow(int burned) {
        return burned % INTERVAL == 0;
    }
}
