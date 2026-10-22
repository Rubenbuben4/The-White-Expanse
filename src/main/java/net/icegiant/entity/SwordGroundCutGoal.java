package net.icegiant.entity;

import net.icegiant.item.GiantRuneSwordItem;
import net.icegiant.item.SwordGroundCutAbility;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.npc.AbstractVillager;

import java.util.EnumSet;

/**
 * The Ice Giant with the Giant Rune Sword sends a ground cut (SwordGroundCutAbility) at a target that stands
 * 6 - 20 blocks in front of it in plain sight: it stops, turns to the target until the blade hits the ground, and the
 * cut runs straight at it. Not against villagers (they are grabbed or hit - Frost Raids).
 */
public class SwordGroundCutGoal extends Goal {

    private static final int COOLDOWN_TICKS = 240;     // 12 s between cuts
    private static final int FIRST_DELAY = 80;         // not the very moment a fight starts
    private static final double MIN_DISTANCE = 6.0D;
    private static final double MAX_DISTANCE = 20.0D;  // (the cut is 20 blocks long for a giant)
    private static final double MAX_ANGLE = 25.0D;     // the target must stand roughly in front of it

    private final IceGiantEntity giant;
    private int cooldown = FIRST_DELAY;

    public SwordGroundCutGoal(IceGiantEntity giant) {
        this.giant = giant;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK, Flag.JUMP));
    }

    @Override
    public boolean canUse() {
        if (this.cooldown > 0) {
            this.cooldown--;
            return false;
        }
        LivingEntity target = this.giant.getTarget();
        if (target == null || !target.isAlive() || target instanceof AbstractVillager) return false;
        if (!GiantRuneSwordItem.isHeldBy(this.giant) || !this.giant.onGround()) return false;
        if (SwordGroundCutAbility.isBusy(this.giant)) return false;
        double distance = this.giant.distanceTo(target);
        if (distance < MIN_DISTANCE || distance > MAX_DISTANCE) return false;
        if (!this.giant.hasLineOfSight(target)) return false;
        return angleTo(target) <= MAX_ANGLE;
    }

    /** Degrees between where the giant faces and the target (horizontal). */
    private double angleTo(LivingEntity target) {
        double dx = target.getX() - this.giant.getX(), dz = target.getZ() - this.giant.getZ();
        double yawToTarget = Math.toDegrees(Math.atan2(-dx, dz));                 // Minecraft yaw: 0 = +z, 90 = -x
        double diff = (yawToTarget - this.giant.getYRot()) % 360.0D;
        if (diff > 180.0D) diff -= 360.0D;
        if (diff < -180.0D) diff += 360.0D;
        return Math.abs(diff);
    }

    @Override
    public void start() {
        this.giant.getNavigation().stop();
        SwordGroundCutAbility.startCut(this.giant);
        this.cooldown = COOLDOWN_TICKS;
    }

    @Override
    public boolean canContinueToUse() {
        return SwordGroundCutAbility.isCutting(this.giant);
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void tick() {
        LivingEntity target = this.giant.getTarget();
        if (target != null && !SwordGroundCutAbility.impactDone(this.giant)) {
            this.giant.getLookControl().setLookAt(target, 30.0F, 30.0F);         // aim until the blade comes down
            this.giant.setYRot(this.giant.getYHeadRot());
            this.giant.yBodyRot = this.giant.getYHeadRot();
        }
    }
}
