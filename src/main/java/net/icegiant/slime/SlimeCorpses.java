package net.icegiant.slime;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Slime;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Bodies of what slimes killed (Rubenbuben4, 2026-10-27): a monster or villager killed by a slime falls on its back and
 * stays there for a minute instead of vanishing after a second - long enough for the slime to eat it whole
 * (SlimeGoals.EatCorpse). Drops and experience come as usual at the moment of death.
 * <p>
 * Server: the body is marked in its persistent data (kept over a reload). Clients learn about it from CorpsePacket
 * and draw it on its back (LivingEntityRendererCorpseMixin).
 */
public final class SlimeCorpses {

    public static final String TAG = "icegiant_corpse";
    /** Ticks a body lies there: the normal 1 s of dying plus a minute. */
    public static final int LIFE_TICKS = 20 + 1200;

    /** Client: entity ids of bodies (filled by CorpsePacket). */
    public static final Set<Integer> CLIENT = new HashSet<>();
    /** Server: which slime is eating which body. */
    private static final Map<UUID, UUID> TAKEN = new HashMap<>();

    private SlimeCorpses() {
    }

    public static boolean isCorpse(LivingEntity e) {
        return e.level().isClientSide() ? CLIENT.contains(e.getId()) : e.getPersistentData().getBoolean(TAG);
    }

    static void mark(LivingEntity e) {
        e.getPersistentData().putBoolean(TAG, true);
    }

    static boolean isTaken(LivingEntity body, Slime by) {
        UUID who = TAKEN.get(body.getUUID());
        return who != null && !who.equals(by.getUUID());
    }

    static void take(LivingEntity body, Slime by) {
        TAKEN.put(body.getUUID(), by.getUUID());
    }

    static void release(LivingEntity body, Slime by) {
        TAKEN.remove(body.getUUID(), by.getUUID());
    }

    /** From LivingEntityDeathMixin (tickDeath): a body keeps lying instead of being removed. True = handled. */
    public static boolean tickCorpse(LivingEntity e) {
        if (!isCorpse(e)) return false;
        e.deathTime++;
        if (!e.level().isClientSide() && e.deathTime >= LIFE_TICKS && !e.isRemoved()) {
            e.level().broadcastEntityEvent(e, (byte) 60);          // the usual poof
            e.remove(Entity.RemovalReason.KILLED);
            TAKEN.remove(e.getUUID());
        }
        return true;
    }
}
