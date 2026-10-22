package net.icegiant.entity;

import net.icegiant.item.IceDashAbility;
import net.icegiant.item.IceWeaponItem;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.ForgeEventFactory;

import java.util.EnumSet;

/**
 * Lets the Ice Giant use the wall-breaker dash (IceDashAbility) - but only on purpose:
 * when its target is hidden behind a wall, not too far away, and it holds one of the axes.
 * Never in the open and never with mobGriefing turned off.
 */
public class IceDashAttackGoal extends Goal {

    private static final int COOLDOWN_TICKS = 200;     // 10 s between dashes
    private static final int FIRST_DASH_DELAY = 60;
    private static final int CHANCE_ONE_IN = 10;       // per tick once the situation fits -> about 0.5 s
    private static final double MIN_DISTANCE = 4.0D;
    private static final double MAX_DISTANCE_PER_SIZE = 7.0D; // x SCALE -> 21 blocks for the default giant

    private final IceGiantEntity giant;
    private int cooldown = FIRST_DASH_DELAY;

    public IceDashAttackGoal(IceGiantEntity giant) {
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
        if (target == null || !target.isAlive()) return false;
        if (!this.giant.canDash()) return false;                                  // "no dash" spawn egg
        boolean golem = target instanceof net.minecraft.world.entity.animal.IronGolem;  // charges straight at golems
        if (!(target instanceof net.minecraft.world.entity.player.Player) && !golem) return false; // else only players
        if (!(this.giant.getMainHandItem().getItem() instanceof IceWeaponItem)) return false;
        if (!this.giant.onGround()) return false;
        if (net.icegiant.item.SwordGroundCutAbility.isBusy(this.giant)) return false;   // another ability is running
        if (!ForgeEventFactory.getMobGriefingEvent(this.giant.level(), this.giant)) return false;
        double distance = this.giant.distanceTo(target);
        if (distance < MIN_DISTANCE || distance > MAX_DISTANCE_PER_SIZE * IceGiantEntity.SCALE) return false;
        if (!golem && this.giant.hasLineOfSight(target)) return false; // players: only when a wall is in the way
        return this.giant.getRandom().nextInt(CHANCE_ONE_IN) == 0;
    }

    @Override
    public void start() {
        LivingEntity target = this.giant.getTarget();
        if (target == null) return;
        Vec3 toTarget = target.position().subtract(this.giant.position());
        if (IceDashAbility.startDash(this.giant, toTarget)) {
            this.cooldown = COOLDOWN_TICKS;
        } else {
            this.cooldown = 20;
        }
    }

    @Override
    public boolean canContinueToUse() {
        return IceDashAbility.isDashing(this.giant);
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }
}
