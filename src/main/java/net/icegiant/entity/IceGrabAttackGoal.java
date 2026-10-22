package net.icegiant.entity;

import net.icegiant.item.IceDashAbility;
import net.icegiant.item.NordicAxeSlamAbility;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;

/**
 * Ice Giant grab & throw: grabs a normal-sized creature (zombie, player, ...) that is
 * right in front of it, holds it in the right fist (where the axe handle is) for a moment,
 * swings the arm up over its head and hurls it away.
 * <p>
 * Holding works by letting the victim ride the giant: IceGiantEntity#positionRider puts it
 * into the fist every tick on both sides, so it moves smoothly with the arm (also for the
 * held player's own camera). Players can't shift-dismount while held (GrabEvents).
 */
public class IceGrabAttackGoal extends Goal {

    private static final int COOLDOWN_TICKS = 300;      // 15 s between grabs
    private static final int FIRST_GRAB_DELAY = 100;
    private static final int CHANCE_ONE_IN = 15;
    private static final int HOLD_TICKS = 30;           // 1.5 s in the fist
    private static final double REACH_PER_SIZE = 1.5D;  // x SCALE -> 4.5 blocks (centre to centre)
    /** "Normal sized": at most this tall / wide (players, zombies, skeletons, villagers ...). */
    private static final double MAX_VICTIM_HEIGHT = 2.2D;
    private static final double MAX_VICTIM_WIDTH = 1.0D;
    private static final double THROW_FORWARD = 2.2D;  // was 1.4 (2026-10-02: throw further)
    private static final double THROW_UP = 0.75D;

    private final IceGiantEntity giant;
    private int cooldown = FIRST_GRAB_DELAY;
    private LivingEntity victim;
    private int phaseTicks;

    public IceGrabAttackGoal(IceGiantEntity giant) {
        this.giant = giant;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK, Flag.JUMP));
    }

    public static boolean canBeGrabbed(LivingEntity grabber, LivingEntity target) {
        // Ice Giants also grab and throw iron golems (bigger than "normal sized", but the giant can lift them)
        if (grabber instanceof IceGiantEntity && target instanceof net.minecraft.world.entity.animal.IronGolem) {
            return canBeGrabbed(grabber, target, GOLEM_MAX_HEIGHT, GOLEM_MAX_WIDTH);
        }
        return canBeGrabbed(grabber, target, Math.min(MAX_VICTIM_HEIGHT, grabber.getBbHeight() * 0.5D), MAX_VICTIM_WIDTH);
    }

    /** Iron golem: 1.4 wide, 2.7 tall. */
    private static final double GOLEM_MAX_HEIGHT = 3.0D, GOLEM_MAX_WIDTH = 1.5D;

    /** Shared rules (also for players in Ice Giant armor, PlayerGrabAbility) with a size limit. */
    public static boolean canBeGrabbed(LivingEntity grabber, LivingEntity target, double maxHeight, double maxWidth) {
        if (target == null || target == grabber || !target.isAlive() || target.isPassenger() || target.isVehicle()) {
            return false;
        }
        if (target instanceof Player p && (p.isCreative() || p.isSpectator())) return false;
        if (target instanceof IceGiantEntity || target instanceof WitherBoss || target instanceof EnderDragon) return false;
        return target.getBbHeight() <= maxHeight && target.getBbWidth() <= maxWidth;
    }

    @Override
    public boolean canUse() {
        if (this.cooldown > 0) {
            this.cooldown--;
            return false;
        }
        LivingEntity target = this.giant.getTarget();
        if (!canBeGrabbed(this.giant, target)) return false;
        if (this.giant.isVehicle() || !this.giant.onGround()) return false;
        if (IceDashAbility.isDashing(this.giant) || NordicAxeSlamAbility.isSlamming(this.giant)) return false;
        if (net.icegiant.item.SwordGroundCutAbility.isBusy(this.giant)) return false;   // also the sword's cut / sweep
        double reach = REACH_PER_SIZE * IceGiantEntity.SCALE;
        if (this.giant.distanceToSqr(target) > reach * reach) return false;
        if (!this.giant.hasLineOfSight(target)) return false;
        return this.giant.getRandom().nextInt(CHANCE_ONE_IN) == 0;
    }

    @Override
    public void start() {
        this.victim = this.giant.getTarget();
        this.cooldown = COOLDOWN_TICKS;
        this.phaseTicks = 0;
        this.giant.getNavigation().stop();
        if (this.victim == null) return;
        // grab first, then start riding: the dismount guard (GrabEvents) only holds while grabbing
        this.giant.setGrabPhase(IceGiantEntity.GRAB_HOLD);
        if (!this.victim.startRiding(this.giant, true)) {
            this.giant.setGrabPhase(IceGiantEntity.GRAB_NONE);
            this.victim = null;
            return;
        }
        this.giant.level().playSound(null, this.giant.getX(), this.giant.getY(), this.giant.getZ(),
                SoundEvents.RAVAGER_ATTACK, SoundSource.HOSTILE, 1.2F, 0.6F);
    }

    @Override
    public boolean canContinueToUse() {
        return this.victim != null && this.victim.isAlive() && this.victim.getVehicle() == this.giant
                && this.giant.isGrabbing() && this.giant.isAlive();
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void tick() {
        this.phaseTicks++;
        if (this.giant.getTarget() != null) {
            this.giant.getLookControl().setLookAt(this.giant.getTarget(), 30.0F, 30.0F);
        }
        if (this.giant.getGrabPhase() == IceGiantEntity.GRAB_HOLD && this.phaseTicks >= HOLD_TICKS) {
            this.giant.setGrabPhase(IceGiantEntity.GRAB_WINDUP);
            this.phaseTicks = 0;
        } else if (this.giant.getGrabPhase() == IceGiantEntity.GRAB_WINDUP
                && this.phaseTicks >= IceGiantEntity.GRAB_WINDUP_TICKS) {
            throwVictim();
        }
    }

    /** Let go at the top of the swing and hurl the victim forward and up. */
    private void throwVictim() {
        LivingEntity thrown = this.victim;
        Vec3 hand = this.giant.getGrabHandPosition(1.0F);
        this.giant.setGrabPhase(IceGiantEntity.GRAB_NONE);
        thrown.stopRiding();
        double x = hand.x;
        double y = hand.y - thrown.getBbHeight() * 0.7D;
        double z = hand.z;
        if (thrown instanceof ServerPlayer player) {
            player.teleportTo(x, y, z);
        } else {
            thrown.setPos(x, y, z);
        }
        double yaw = Math.toRadians(this.giant.yBodyRot);
        Vec3 forward = new Vec3(-Math.sin(yaw), 0.0D, Math.cos(yaw));
        thrown.setDeltaMovement(forward.x * THROW_FORWARD, THROW_UP, forward.z * THROW_FORWARD);
        thrown.hurtMarked = true;
        thrown.fallDistance = 0.0F;
        GrabEvents.trackThrown(thrown);
        this.giant.swing(net.minecraft.world.InteractionHand.MAIN_HAND);
        this.giant.level().playSound(null, this.giant.getX(), this.giant.getY(), this.giant.getZ(),
                SoundEvents.PLAYER_ATTACK_KNOCKBACK, SoundSource.HOSTILE, 1.5F, 0.5F);
        this.victim = null;
    }

    @Override
    public void stop() {
        // interrupted (victim died, giant hurt badly, ...): just let go
        if (this.giant.isGrabbing()) {
            this.giant.setGrabPhase(IceGiantEntity.GRAB_NONE);
            this.giant.ejectPassengers();
        }
        this.victim = null;
    }
}
