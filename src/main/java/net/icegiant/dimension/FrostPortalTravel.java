package net.icegiant.dimension;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/**
 * Server-side "how long has this entity been standing in a Frost Portal"
 * counter - gives the Frost Portal the same charge-up feel as a Nether
 * portal instead of teleporting on first contact.
 * <p>
 * Players have to stay inside for {@link #PLAYER_WAIT_TICKS} (5 seconds);
 * creative/invulnerable players and non-player mobs travel right away,
 * matching what players know from the Nether portal.
 * <p>
 * Own implementation: a per-entity counter keyed on UUID that only
 * advances once per game tick (an entity can overlap several portal
 * blocks at once) and resets as soon as a tick is skipped, i.e. the
 * entity stepped out of the portal.
 */
public final class FrostPortalTravel {

    /** 5 seconds at 20 ticks per second. Also drives the client overlay fill speed. */
    public static final int PLAYER_WAIT_TICKS = 100;

    private static final int CLEANUP_THRESHOLD = 64;
    private static final long STALE_AFTER_TICKS = 40;

    private static final Map<UUID, Progress> PROGRESS = new HashMap<>();

    private static final class Progress {
        int ticksInside;
        long lastCountedTick;

        Progress(long lastCountedTick) {
            this.lastCountedTick = lastCountedTick;
        }
    }

    private FrostPortalTravel() {
    }

    /**
     * Call every time the entity touches a portal block (server side only).
     *
     * @return true exactly once, on the tick the entity has waited long enough and should be teleported now
     */
    public static boolean tickInside(Entity entity, long gameTime) {
        cleanupIfNeeded(gameTime);

        Progress progress = PROGRESS.computeIfAbsent(entity.getUUID(), id -> new Progress(gameTime - 1));
        if (progress.lastCountedTick == gameTime) {
            return false; // already counted for this tick (touching several portal blocks)
        }
        if (progress.lastCountedTick != gameTime - 1) {
            progress.ticksInside = 0; // stepped out in between -> start over
        }
        progress.lastCountedTick = gameTime;
        progress.ticksInside++;

        if (progress.ticksInside >= requiredTicks(entity)) {
            PROGRESS.remove(entity.getUUID());
            return true;
        }
        return false;
    }

    /** Forget any charge-up (e.g. while the entity is on portal cooldown). */
    public static void reset(Entity entity) {
        PROGRESS.remove(entity.getUUID());
    }

    public static int requiredTicks(Entity entity) {
        if (entity instanceof Player player) {
            return player.getAbilities().invulnerable ? 1 : PLAYER_WAIT_TICKS;
        }
        return 1;
    }

    private static void cleanupIfNeeded(long gameTime) {
        if (PROGRESS.size() < CLEANUP_THRESHOLD) {
            return;
        }
        Iterator<Map.Entry<UUID, Progress>> it = PROGRESS.entrySet().iterator();
        while (it.hasNext()) {
            if (gameTime - it.next().getValue().lastCountedTick > STALE_AFTER_TICKS) {
                it.remove();
            }
        }
    }
}
