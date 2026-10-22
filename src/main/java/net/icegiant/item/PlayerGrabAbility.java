package net.icegiant.item;

import net.icegiant.IceGiantMod;
import net.icegiant.entity.GrabEvents;
import net.icegiant.entity.IceGrabAttackGoal;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Grab & throw for players wearing the full Ice Giant armor (key G by default):
 * first press grabs the creature you look at and holds it in your right fist,
 * second press throws it where you look. Same idea as the Ice Giant's grab
 * (IceGrabAttackGoal): the victim rides the player and is put into the fist every
 * tick (EntityGrabRiderMixin), the right arm is held forward (PlayerModelArmMixin).
 * <p>
 * Scales with your size: you can grab creatures up to your own size, reach and
 * throw grow with your hitbox - so scaled-up RP giants can grab normal players.
 */
@Mod.EventBusSubscriber(modid = IceGiantMod.MOD_ID)
public final class PlayerGrabAbility {

    private static final double REACH = 3.0D;            // blocks for a normal-sized player (x size)
    private static final double THROW_SPEED = 1.5D;      // along the look direction (x sqrt(size), max 2x)
    private static final double THROW_LIFT = 0.45D;
    private static final int MAX_HOLD_TICKS = 300;       // 15 s, then you let go
    private static final int COOLDOWN_TICKS = 60;        // 3 s after a throw

    /** Grabber -> held creature (server side). */
    private static final Map<UUID, Entity> HELD = new HashMap<>();
    private static final Map<UUID, Integer> HOLD_TICKS = new HashMap<>();
    private static final Map<UUID, Long> COOLDOWN_UNTIL = new HashMap<>();

    private PlayerGrabAbility() {
    }

    // -------------------------------------------------------------- shared

    public static boolean wearsFullArmor(Player player) {
        for (EquipmentSlot slot : new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST,
                EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
            if (!(player.getItemBySlot(slot).getItem() instanceof IceGiantArmorItem)) return false;
        }
        return true;
    }

    private static double sizeScale(Player player) {
        return Math.max(1.0D, player.getBbHeight() / 1.8D);
    }

    /**
     * Where the held creature is (both sides, also used by EntityGrabRiderMixin): the right fist
     * with the arm held straight forward - shoulder 22 px above the feet, 5 px to the right,
     * fist 9 px down the arm - scaled with the player's size.
     */
    public static Vec3 handPosition(Player player, float partialTick) {
        double s = Math.max(0.1D, player.getBbHeight() / 1.8D);
        double yaw = Math.toRadians(Mth.lerp(partialTick, player.yBodyRotO, player.yBodyRot));
        Vec3 forward = new Vec3(-Math.sin(yaw), 0.0D, Math.cos(yaw));
        Vec3 right = new Vec3(-Math.cos(yaw), 0.0D, -Math.sin(yaw));
        return player.position().add(0.0D, 22.0D / 16.0D * s, 0.0D)
                .add(right.scale(5.0D / 16.0D * s))
                .add(forward.scale(9.0D / 16.0D * s + 0.2D));
    }

    /**
     * Where the held creature's feet go (both sides, used by EntityGrabRiderMixin): gripped around the
     * chest at the fist, pushed out in front of (and slightly right of) the fist - and always lifted off the
     * ground (feet at least 40 % of the holder's height up), so even a creature about as tall as you
     * dangles in the air instead of standing in front of you.
     */
    public static Vec3 heldPosition(Player player, Entity held, float partialTick) {
        Vec3 hand = handPosition(player, partialTick);
        double yaw = Math.toRadians(Mth.lerp(partialTick, player.yBodyRotO, player.yBodyRot));
        Vec3 forward = new Vec3(-Math.sin(yaw), 0.0D, Math.cos(yaw));
        double feetY = Math.max(hand.y - held.getBbHeight() * 0.7D, player.getY() + player.getBbHeight() * 0.4D);
        Vec3 right = new Vec3(-Math.cos(yaw), 0.0D, -Math.sin(yaw));
        // out in front of the fist and a bit to the right, so it doesn't hang right in your face
        Vec3 out = forward.scale(held.getBbWidth() * 0.5D).add(right.scale(held.getBbWidth() * 0.5D));
        return new Vec3(hand.x + out.x, feetY, hand.z + out.z);
    }

    /** True while {@code grabber} holds {@code held} (server side). */
    public static boolean isHolding(Player grabber, Entity held) {
        Entity current = HELD.get(grabber.getUUID());
        return current != null && current == held;
    }

    // --------------------------------------------------------------- key

    /** Key press (GrabActivatePacket): grab if the hand is empty, throw if something is held. */
    public static void toggle(ServerPlayer player) {
        if (HELD.containsKey(player.getUUID())) {
            throwHeld(player);
            return;
        }
        if (!wearsFullArmor(player)) {
            player.displayClientMessage(Component.translatable("message.icegiant.grab_needs_armor"), true);
            return;
        }
        if (player.isSpectator() || player.isPassenger() || player.isVehicle()) return;
        if (SwordGroundCutAbility.isBusy(player)) return;   // slam, dash, sword cut or sweep running (spec §4)
        long now = player.level().getGameTime();
        Long until = COOLDOWN_UNTIL.get(player.getUUID());
        if (until != null && now < until) return;

        LivingEntity target = findTarget(player);
        if (target == null) return;
        // the guard against climbing out is active as soon as the creature is in HELD
        HELD.put(player.getUUID(), target);
        HOLD_TICKS.put(player.getUUID(), 0);
        if (!target.startRiding(player, true)) {
            HELD.remove(player.getUUID());
            HOLD_TICKS.remove(player.getUUID());
            return;
        }
        player.swing(InteractionHand.MAIN_HAND, true);
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.RAVAGER_ATTACK,
                SoundSource.PLAYERS, 1.0F, 0.8F);
    }

    /** The creature you look at, within reach, that you may grab. */
    private static LivingEntity findTarget(ServerPlayer player) {
        double s = sizeScale(player);
        double reach = REACH * s;
        Vec3 eye = player.getEyePosition();
        Vec3 end = eye.add(player.getLookAngle().scale(reach));
        AABB area = player.getBoundingBox().expandTowards(player.getLookAngle().scale(reach)).inflate(1.0D);
        EntityHitResult hit = ProjectileUtil.getEntityHitResult(player.level(), player, eye, end, area,
                e -> e instanceof LivingEntity living && canGrab(player, living));
        return hit != null && hit.getEntity() instanceof LivingEntity living ? living : null;
    }

    private static boolean canGrab(ServerPlayer player, LivingEntity target) {
        // up to about your own size (zombies and players for a normal player, everything normal-sized
        // for a scaled-up giant)
        if (!IceGrabAttackGoal.canBeGrabbed(player, target, player.getBbHeight() * 1.2D, player.getBbWidth() * 1.7D)) {
            return false;
        }
        if (target instanceof Player other) {
            if (!player.server.isPvpAllowed() || !player.canHarmPlayer(other)) return false; // PvP off / same team
        }
        return true;
    }

    private static void throwHeld(ServerPlayer player) {
        Entity heldBefore = HELD.get(player.getUUID());
        Vec3 from = heldBefore != null ? heldPosition(player, heldBefore, 1.0F) : null;
        Entity held = release(player);
        if (!(held instanceof LivingEntity thrown) || !thrown.isAlive() || from == null) return;
        // start the flight from the fist (dismounting would otherwise drop it next to you)
        if (thrown instanceof ServerPlayer target) {
            target.teleportTo(from.x, from.y, from.z);
        } else {
            thrown.setPos(from.x, from.y, from.z);
        }
        double power = THROW_SPEED * Math.min(2.0D, Math.sqrt(sizeScale(player)));
        Vec3 look = player.getLookAngle();
        // always forward and a bit up - looking down at the ground must not "throw" it into the floor
        Vec3 flat = new Vec3(look.x, 0.0D, look.z);
        flat = flat.lengthSqr() < 1.0E-4D ? Vec3.directionFromRotation(0.0F, player.getYRot()) : flat.normalize();
        double up = Math.max(look.y, 0.0D) * power + THROW_LIFT;
        thrown.setDeltaMovement(flat.x * power, up, flat.z * power);
        thrown.hurtMarked = true;
        thrown.fallDistance = 0.0F;
        GrabEvents.trackThrown(thrown);
        player.swing(InteractionHand.MAIN_HAND, true);
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.PLAYER_ATTACK_KNOCKBACK, SoundSource.PLAYERS, 1.2F, 0.6F);
        COOLDOWN_UNTIL.put(player.getUUID(), player.level().getGameTime() + COOLDOWN_TICKS);
    }

    /** Lets go (no throw). Returns the creature that was held, or null. */
    private static Entity release(Player player) {
        Entity held = HELD.remove(player.getUUID()); // first: lifts the dismount guard
        HOLD_TICKS.remove(player.getUUID());
        if (held != null && held.getVehicle() == player) {
            held.stopRiding();
        }
        return held;
    }

    // -------------------------------------------------------------- tick

    /** Let go when the armor comes off, after 15 s, or when either side is gone. */
    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer player)) return;
        Entity held = HELD.get(player.getUUID());
        if (held == null) return;
        int ticks = HOLD_TICKS.merge(player.getUUID(), 1, Integer::sum);
        if (!player.isAlive() || player.isRemoved() || !held.isAlive() || held.getVehicle() != player
                || !wearsFullArmor(player) || ticks > MAX_HOLD_TICKS
                || !(player.level() instanceof ServerLevel)) {
            release(player);
        }
    }

    @SubscribeEvent
    public static void onLogout(net.minecraftforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent event) {
        release(event.getEntity());
        COOLDOWN_UNTIL.remove(event.getEntity().getUUID());
    }
}
