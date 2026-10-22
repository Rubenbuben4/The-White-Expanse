package net.icegiant.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.target.TargetGoal;
import net.minecraft.world.entity.ai.targeting.TargetingConditions;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.EnumSet;

/**
 * How an Ice Giant notices players: it hears and feels them, so it does not need to see them -
 * walls up to {@link #MAX_WALL_BLOCKS} blocks thick do not hide a walking player. Sneaking players
 * are only noticed when they come very close ({@link #SNEAK_NOTICE_DISTANCE} blocks), even in plain
 * sight. Invisibility still shortens the range like for vanilla mobs. Creative/spectator are ignored.
 */
public class GiantSensePlayerGoal extends TargetGoal {

    public static final int MAX_WALL_BLOCKS = 2;
    public static final double SNEAK_NOTICE_DISTANCE = 5.0D;
    private static final int CHECK_INTERVAL = 10;               // like vanilla: look around every ~0.5 s

    @Nullable private Player candidate;

    public GiantSensePlayerGoal(Mob mob) {
        super(mob, false);                                      // no line of sight needed
        this.setFlags(EnumSet.of(Flag.TARGET));
    }

    @Override
    public boolean canUse() {
        if (this.mob.getRandom().nextInt(CHECK_INTERVAL) != 0) return false;
        double range = this.mob.getAttributeValue(Attributes.FOLLOW_RANGE);
        Player best = null;
        double bestDist = Double.MAX_VALUE;
        for (Player p : this.mob.level().players()) {
            double d = this.mob.distanceTo(p);
            if (d < bestDist && d <= range && this.canSense(p, d, range)) {
                best = p;
                bestDist = d;
            }
        }
        this.candidate = best;
        return best != null;
    }

    private boolean canSense(Player p, double dist, double range) {
        if (!p.isAlive() || p.isCreative() || p.isSpectator()) return false;
        if (!this.canAttack(p, TargetingConditions.forCombat().ignoreLineOfSight().ignoreInvisibilityTesting())) return false;
        if (p.isCrouching()) return dist <= SNEAK_NOTICE_DISTANCE;          // sneaking: only very close
        if (dist > range * p.getVisibilityPercent(this.mob)) return false;  // invisibility, mob heads
        return this.mob.hasLineOfSight(p) || wallThickness(this.mob.getEyePosition(), p.getEyePosition()) <= MAX_WALL_BLOCKS;
    }

    /** Solid blocks between the two points (stops counting once it is too thick). */
    private int wallThickness(Vec3 from, Vec3 to) {
        Vec3 d = to.subtract(from);
        double len = d.length();
        int steps = (int) Math.ceil(len / 0.25D);
        BlockPos last = null;
        int count = 0;
        for (int i = 1; i < steps; i++) {
            Vec3 at = from.add(d.scale(i / (double) steps));
            BlockPos pos = BlockPos.containing(at);
            if (pos.equals(last)) continue;
            last = pos;
            BlockState state = this.mob.level().getBlockState(pos);
            if (!state.getCollisionShape(this.mob.level(), pos).isEmpty() && state.canOcclude()) {
                if (++count > MAX_WALL_BLOCKS) return count;
            }
        }
        return count;
    }

    @Override
    public void start() {
        this.mob.setTarget(this.candidate);
        super.start();
    }
}
