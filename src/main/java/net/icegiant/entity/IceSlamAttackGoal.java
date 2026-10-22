package net.icegiant.entity;

import net.icegiant.item.IceWeaponItem;
import net.icegiant.item.NordicAxeSlamAbility;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;

import java.util.EnumSet;

/**
 * Lets an Ice Giant use the same ground slam as the axes (NordicAxeSlamAbility):
 * when its target is close enough it stops, plays the slam (charge particles,
 * impact sound, knockback, crater, smoke) and then goes back to normal melee
 * (MeleeAttackGoal, lower priority). Only works while holding one of the axes.
 */
public class IceSlamAttackGoal extends Goal {

    private static final int COOLDOWN_TICKS = 240;   // 12 s between slams
    private static final int FIRST_SLAM_DELAY = 60;  // don't slam the very moment a fight starts
    private static final int CHANCE_ONE_IN = 20;     // per tick once in range -> about once per second

    private final IceGiantEntity giant;
    private int cooldown = FIRST_SLAM_DELAY;

    public IceSlamAttackGoal(IceGiantEntity giant) {
        this.giant = giant;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK, Flag.JUMP));
    }

    /** The slam knocks back everything within ~6 blocks per player-size; start a bit inside that. */
    private double slamRange() {
        return 2.5D * IceGiantEntity.SCALE;
    }

    @Override
    public boolean canUse() {
        if (this.cooldown > 0) {
            this.cooldown--;
            return false;
        }
        LivingEntity target = this.giant.getTarget();
        if (target == null || !target.isAlive()) return false;
        // villagers are grabbed and thrown or just hit - never slammed (Frost Raids)
        if (target instanceof net.minecraft.world.entity.npc.AbstractVillager) return false;
        if (!(this.giant.getMainHandItem().getItem() instanceof IceWeaponItem)) return false;
        if (!this.giant.onGround()) return false;
        if (net.icegiant.item.SwordGroundCutAbility.isBusy(this.giant)) return false;   // another ability is running
        if (this.giant.distanceTo(target) > slamRange()) return false;
        return this.giant.getRandom().nextInt(CHANCE_ONE_IN) == 0;
    }

    @Override
    public void start() {
        this.giant.getNavigation().stop();
        NordicAxeSlamAbility.startSlam(this.giant);
        this.cooldown = COOLDOWN_TICKS;
    }

    @Override
    public boolean canContinueToUse() {
        return NordicAxeSlamAbility.isSlamming(this.giant);
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void tick() {
        LivingEntity target = this.giant.getTarget();
        if (target != null) {
            this.giant.getLookControl().setLookAt(target, 30.0F, 30.0F);
        }
    }
}
