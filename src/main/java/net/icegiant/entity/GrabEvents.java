package net.icegiant.entity;

import net.icegiant.IceGiantMod;
import net.icegiant.registry.ModEffects;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityMountEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Server-side rules around the Ice Giant's grab & throw (IceGrabAttackGoal):
 * a held creature can't climb out of the fist, and a thrown player is knocked
 * down (prone) for a moment when hitting the ground.
 */
@Mod.EventBusSubscriber(modid = IceGiantMod.MOD_ID)
public final class GrabEvents {

    private static final int KNOCKED_DOWN_TICKS = 40;  // 2 s on the floor after the throw
    private static final int MIN_AIR_TICKS = 3;
    private static final int MAX_TRACK_TICKS = 100;

    /** Thrown creatures waiting to land (creature -> ticks since the throw). */
    private static final Map<LivingEntity, Integer> THROWN = new WeakHashMap<>();
    /** Throw velocity of thrown players, re-sent once the client knows it is no longer held. */
    private static final Map<LivingEntity, net.minecraft.world.phys.Vec3> THROW_VELOCITY = new WeakHashMap<>();
    private static final int RESEND_VELOCITY_TICK = 2;

    private GrabEvents() {
    }

    public static void trackThrown(LivingEntity entity) {
        THROWN.put(entity, 0);
        if (entity instanceof net.minecraft.server.level.ServerPlayer) {
            THROW_VELOCITY.put(entity, entity.getDeltaMovement());
        }
    }

    /** No shift-dismount while the giant holds you (server decides; the client is corrected). */
    @SubscribeEvent
    public static void onMount(EntityMountEvent event) {
        if (!event.isDismounting() || event.getLevel().isClientSide()) return;
        if (event.getEntityBeingMounted() instanceof IceGiantEntity giant && giant.isAlive() && giant.isGrabbing()) {
            event.setCanceled(true);
        } else if (event.getEntityBeingMounted() instanceof Player grabber
                && net.icegiant.item.PlayerGrabAbility.isHolding(grabber, event.getEntityMounting())) {
            event.setCanceled(true); // held by a player in Ice Giant armor
        }
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || THROWN.isEmpty()) return;
        for (Map.Entry<LivingEntity, Integer> entry : new ArrayList<>(THROWN.entrySet())) {
            LivingEntity entity = entry.getKey();
            int ticks = entry.getValue() + 1;
            if (entity == null || !entity.isAlive() || ticks > MAX_TRACK_TICKS) {
                THROWN.remove(entity);
                THROW_VELOCITY.remove(entity);
                continue;
            }
            // A player's own client moves the player. The "you're no longer riding" message
            // reaches it a tick after the throw; until then the client zeroes the velocity every
            // tick (riding), so the throw got lost and the player only dropped a few blocks.
            // Re-send the throw velocity once the client has let go.
            if (ticks == RESEND_VELOCITY_TICK && entity instanceof net.minecraft.server.level.ServerPlayer player) {
                net.minecraft.world.phys.Vec3 v = THROW_VELOCITY.remove(entity);
                if (v != null && !player.isPassenger()) {
                    player.setDeltaMovement(v);
                    player.connection.send(new net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket(player));
                }
            }
            if (ticks > MIN_AIR_TICKS && (entity.onGround() || entity.isInWater())) {
                THROWN.remove(entity);
                THROW_VELOCITY.remove(entity);
                if (entity instanceof Player) {
                    entity.addEffect(new MobEffectInstance(ModEffects.KNOCKED_DOWN.get(), KNOCKED_DOWN_TICKS, 0,
                            false, false, true));
                }
                continue;
            }
            THROWN.put(entity, ticks);
        }
    }
}
