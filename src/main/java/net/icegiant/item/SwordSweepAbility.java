package net.icegiant.item;

import net.icegiant.IceGiantMod;
import net.icegiant.compat.KnockbackCompat;
import net.icegiant.network.NetworkHandler;
import net.icegiant.network.SweepStartBroadcastPacket;
import net.icegiant.registry.ModSounds;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/**
 * Giant Rune Sword, key N (Rubenbuben4, 2026-10-17; first L, which is vanilla's advancements key): a wide slash in front of the body.
 * Since 2026-10-21 with his animation "Swing with the Sword" (SweepAnimation, 1.4583 s, times in SweepTiming): he
 * winds up to his left, at 0.35 s the slash begins - only then the sword sound and the hit: everything in a 120
 * degree cone in front (reach 6 x size, an Ice Giant: 15) takes the swinger's attack damage and is thrown away from
 * it. The clients draw his big slash picture from 0.35 to 0.95 s (GiantSlashParticle). Works for players
 * (tryActivate) and Ice Giants (startSweep, SwordSweepGoal). Maths: SwordGeometry.inCone.
 */
@Mod.EventBusSubscriber(modid = IceGiantMod.MOD_ID)
public final class SwordSweepAbility {

    // ---- TUNE ----
    static final int HIT_TICK = SweepTiming.HIT_TICK;      // 0.35 s: wound up, the slash begins (hit + sound)
    static final int POSE_TICKS = SweepTiming.POSE_TICKS;  // the whole animation (1.4583 s -> 30 ticks)
    static final int PLAYER_COOLDOWN_TICKS = 60;           // 3 s
    static final double PUSH = 2.2D, LIFT = 0.5D;          // thrown away from the swinger

    private static final Map<UUID, Long> PLAYER_COOLDOWN_UNTIL = new HashMap<>();
    private static final Map<UUID, Sweep> ACTIVE = new HashMap<>();

    private static final class Sweep {
        final LivingEntity user;
        final double size;
        final ResourceKey<Level> dimension;
        int age;

        Sweep(LivingEntity user) {
            this.user = user;
            this.size = SwordGeometry.size(user.getBbWidth());
            this.dimension = user.level().dimension();
        }
    }

    private SwordSweepAbility() {
    }

    /** Key N (SweepActivatePacket): sword in hand, nothing else going on, cooldown over. */
    public static void tryActivate(Player player) {
        if (player.level().isClientSide() || !GiantRuneSwordItem.isHeldBy(player)) return;
        if (player.isSpectator() || player.isPassenger() || player.isSleeping()) return;
        if (SwordGroundCutAbility.isBusy(player)) return;
        long now = player.level().getGameTime();
        long left = SwordGeometry.cooldownLeft(PLAYER_COOLDOWN_UNTIL.get(player.getUUID()), now, PLAYER_COOLDOWN_TICKS);
        if (left > 0) {
            int seconds = (int) Math.ceil(left / 20.0D);
            player.displayClientMessage(Component.translatable("message.icegiant.sweep_cooldown", seconds), true);
            return;
        }
        PLAYER_COOLDOWN_UNTIL.put(player.getUUID(), now + PLAYER_COOLDOWN_TICKS);
        startSweep(player);
    }

    /** Starts the wide sweep for any creature (no weapon / cooldown checks - callers do those). */
    public static void startSweep(LivingEntity user) {
        if (!(user.level() instanceof ServerLevel level) || SwordGroundCutAbility.isBusy(user)) return;
        Sweep sweep = new Sweep(user);
        ACTIVE.put(user.getUUID(), sweep);
        NetworkHandler.INSTANCE.send(PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> user),
                new SweepStartBroadcastPacket(user.getId()));
    }

    public static boolean isSweeping(LivingEntity entity) {
        return ACTIVE.containsKey(entity.getUUID());
    }

    /** A world change or a long break must not leave a cooldown behind (SwordGeometry.cooldownLeft). */
    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        PLAYER_COOLDOWN_UNTIL.remove(event.getEntity().getUUID());
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || ACTIVE.isEmpty()) return;
        Iterator<Sweep> it = ACTIVE.values().iterator();
        while (it.hasNext()) {
            Sweep sweep = it.next();
            LivingEntity user = sweep.user;
            if (!user.isAlive() || user.isRemoved() || user.level().dimension() != sweep.dimension
                    || !(user.level() instanceof ServerLevel level)) {
                it.remove();
                continue;
            }
            if (sweep.age == HIT_TICK) hit(level, sweep);
            if (++sweep.age >= POSE_TICKS) it.remove();
        }
    }

    private static boolean hitting;

    /**
     * True while the sweep is hitting (server thread). Its hits use the normal attack damage sources, so the
     * blue flames of the sword 2.0 (BlueFire, only for normal hits) ask this to leave them out.
     */
    public static boolean isHitting() {
        return hitting;
    }

    /** 0.35 s: the slash begins - the sword sound, and everything in the cone is hit and thrown away. */
    private static void hit(ServerLevel level, Sweep sweep) {
        LivingEntity user = sweep.user;
        level.playSound(null, user.getX(), user.getY(), user.getZ(), ModSounds.RUNE_SWORD_SLASH.get(),
                user.getSoundSource(), (float) (1.2D * sweep.size), (float) (1.0D / Math.sqrt(sweep.size)));
        Vec3 look = Vec3.directionFromRotation(0.0F, user.getYRot());
        double reach = SwordGeometry.SWEEP_REACH * sweep.size;
        DamageSource source = user instanceof Player p ? level.damageSources().playerAttack(p) : level.damageSources().mobAttack(user);
        float damage = (float) user.getAttributeValue(Attributes.ATTACK_DAMAGE);
        hitting = true;                                                 // (BlueFire: not a normal hit)
        try {
            for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, user.getBoundingBox().inflate(reach), LivingEntity::isAlive)) {
                double dx = target.getX() - user.getX(), dz = target.getZ() - user.getZ(), dy = target.getY() - user.getY();
                if (!SwordGeometry.inCone(dx, dz, dy, look.x, look.z, sweep.size)) continue;
                if (!SwordGroundCutAbility.mayHit(user, target)) continue;
                boolean hurt = target.hurt(source, damage);
                if (!hurt && target instanceof Player) continue;        // (no PvP / protected: no throw either)
                KnockbackCompat.applyKnockback(user, target, PUSH, LIFT);   // after hurt(): adds to the vanilla knockback (like the slam)
            }
        } finally {
            hitting = false;
        }
        // (the slash itself: GiantSlashParticle on the clients - until 2026-10-21 here 7 small vanilla sweep particles)
    }
}
