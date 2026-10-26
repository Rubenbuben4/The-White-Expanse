package net.icegiant.entity;

import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.EnumSet;

/**
 * What an Ice Giant does when he noticed something but has no target yet (GiantSensePlayerGoal, 2026-10-26):
 * <ul>
 *   <li><b>suspicious</b> (awareness 25 - 60): stops and turns his head to where he saw or heard it;</li>
 *   <li><b>searching</b> (60 - 100): walks there slowly, and once there looks around.</li>
 * </ul>
 * Calms down again when the awareness sinks (you stayed out of sight and quiet).
 */
public class GiantInvestigateGoal extends Goal {

    static final double SEARCH_SPEED = 0.7D, ARRIVED = 4.0D;
    static final int REPATH_TICKS = 20;

    private final PathfinderMob giant;
    private final GiantSensePlayerGoal senses;
    private int repath;
    private int lookAroundTicks;
    @Nullable private Vec3 lookAround;

    public GiantInvestigateGoal(PathfinderMob giant, GiantSensePlayerGoal senses) {
        this.giant = giant;
        this.senses = senses;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        return this.giant.getTarget() == null && this.senses.focusPos() != null
                && this.senses.focusAwareness() >= GiantSensePlayerGoal.SUSPICIOUS;
    }

    @Override
    public boolean canContinueToUse() {
        return this.canUse();
    }

    @Override
    public void start() {
        this.repath = 0;
        this.lookAround = null;
    }

    @Override
    public void stop() {
        this.giant.getNavigation().stop();
    }

    @Override
    public void tick() {
        Vec3 pos = this.senses.focusPos();
        if (pos == null) return;
        boolean searching = this.senses.focusAwareness() >= GiantSensePlayerGoal.SEARCHING;
        double dist = this.giant.position().distanceTo(pos);
        if (!searching) {                                          // suspicious: stand and listen
            this.giant.getNavigation().stop();
            this.giant.getLookControl().setLookAt(pos.x, pos.y + 1.0D, pos.z, 10.0F, 30.0F);
            return;
        }
        if (dist > ARRIVED) {                                      // searching: go there
            this.lookAround = null;
            if (--this.repath <= 0) {
                this.repath = REPATH_TICKS;
                this.giant.getNavigation().moveTo(pos.x, pos.y, pos.z, SEARCH_SPEED);
            }
            this.giant.getLookControl().setLookAt(pos.x, pos.y + 1.0D, pos.z, 10.0F, 30.0F);
            return;
        }
        // arrived: look around now and then
        this.giant.getNavigation().stop();
        if (this.lookAround == null || --this.lookAroundTicks <= 0) {
            double a = this.giant.getRandom().nextDouble() * Math.PI * 2.0D;
            this.lookAround = this.giant.position().add(Math.cos(a) * 8.0D, 1.5D, Math.sin(a) * 8.0D);
            this.lookAroundTicks = 30 + this.giant.getRandom().nextInt(30);
        }
        this.giant.getLookControl().setLookAt(this.lookAround.x, this.lookAround.y, this.lookAround.z, 10.0F, 30.0F);
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }
}
