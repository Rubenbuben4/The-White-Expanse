package net.icegiant.entity;

import net.icegiant.item.GiantRuneSwordItem;
import net.icegiant.item.SwordGeometry;
import net.icegiant.item.SwordGroundCutAbility;
import net.icegiant.item.SwordSweepAbility;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;

/**
 * The Ice Giant with the Giant Rune Sword makes the wide sweep (SwordSweepAbility) when its target is close in front
 * of it, inside the sweep's cone. Not against villagers (Frost Raids).
 */
public class SwordSweepGoal extends Goal {

    private static final int COOLDOWN_TICKS = 100;     // 5 s between sweeps
    private static final int FIRST_DELAY = 40;
    private static final int CHANCE_ONE_IN = 8;        // per tick once the target is in the cone

    private final IceGiantEntity giant;
    private int cooldown = FIRST_DELAY;

    public SwordSweepGoal(IceGiantEntity giant) {
        this.giant = giant;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
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
        Vec3 look = Vec3.directionFromRotation(0.0F, this.giant.getYRot());
        double size = SwordGeometry.size(this.giant.getBbWidth());
        if (!SwordGeometry.inCone(target.getX() - this.giant.getX(), target.getZ() - this.giant.getZ(),
                target.getY() - this.giant.getY(), look.x, look.z, size)) return false;
        return this.giant.getRandom().nextInt(CHANCE_ONE_IN) == 0;
    }

    @Override
    public void start() {
        this.giant.getNavigation().stop();
        SwordSweepAbility.startSweep(this.giant);
        this.cooldown = COOLDOWN_TICKS;
    }

    @Override
    public boolean canContinueToUse() {
        return SwordSweepAbility.isSweeping(this.giant);
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void tick() {
        LivingEntity target = this.giant.getTarget();
        if (target != null) this.giant.getLookControl().setLookAt(target, 30.0F, 30.0F);
    }
}
