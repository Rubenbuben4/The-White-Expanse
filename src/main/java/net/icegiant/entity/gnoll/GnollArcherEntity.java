package net.icegiant.entity.gnoll;

import net.icegiant.registry.ModSounds;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;

/**
 * Gnoll archer: keeps its distance and shoots poisoned arrows (poison + slowness, like the
 * green-trailed arrows of the original pack). When a player aims at it from close by it
 * leaps to the side (8 s cooldown).
 */
public class GnollArcherEntity extends GnollEntity {

    public static final int ACT_SHOOT = 1, ACT_JUMP_LEFT = 2, ACT_JUMP_RIGHT = 3;
    private static final String[] ANIMS = {"", "attack1", "jump_left", "jump_right"};
    private static final int[] LENGTHS = {0, 28, 12, 12};

    /** Tick of the draw sound and of the release (the arrow in the hand vanishes at 1.05 s). */
    private static final int DRAW_SOUND_TICK = 7;
    public static final int RELEASE_TICK = 21;
    private static final double SHOOT_RANGE = 15.0D;

    private int dodgeCooldown = 40;

    public GnollArcherEntity(EntityType<? extends Monster> type, Level level) {
        super(type, level);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return baseAttributes()
                .add(Attributes.MAX_HEALTH, 24.0D)
                .add(Attributes.ATTACK_DAMAGE, 2.0D)
                .add(Attributes.ARMOR, 2.0D);
    }

    @Override
    protected String actionAnimation(int id) {
        return ANIMS[id];
    }

    @Override
    protected int actionLength(int id) {
        return LENGTHS[id];
    }

    @Override
    protected void registerAttackGoals() {
        this.goalSelector.addGoal(2, new ArcherGoal(this));
    }

    @Override
    protected void customServerAiStep() {
        if (this.dodgeCooldown > 0) this.dodgeCooldown--;
        LivingEntity target = this.getTarget();
        if (target instanceof Player player && !this.isBusy() && this.dodgeCooldown <= 0 && this.onGround()
                && this.distanceToSqr(player) < 9.0D * 9.0D && isAimedAt(player)) {
            this.dodge(player);
        }
        super.customServerAiStep();
    }

    /** Is the player looking right at this Gnoll (within 20 degrees)? */
    private boolean isAimedAt(Player player) {
        Vec3 view = player.getViewVector(1.0F);
        Vec3 to = this.getEyePosition().subtract(player.getEyePosition()).normalize();
        return view.dot(to) > Math.cos(Math.toRadians(10.0D));
    }

    private void dodge(Player player) {
        this.dodgeCooldown = 160;
        boolean right = this.random.nextBoolean();
        Vec3 away = this.position().subtract(player.position());
        Vec3 side = new Vec3(-away.z, 0.0D, away.x).normalize().scale(right ? 1.0D : -1.0D);
        this.setDeltaMovement(side.x * 0.75D, 0.42D, side.z * 0.75D);
        this.hasImpulse = true;
        this.getNavigation().stop();
        this.startAction(right ? ACT_JUMP_LEFT : ACT_JUMP_RIGHT);
        this.playSound(ModSounds.GNOLL_ARCHER_JUMP.get(), 0.8F, 1.0F);
    }

    @Override
    protected void tickAction(int id, int t) {
        if (id != ACT_SHOOT) return;
        LivingEntity target = this.getTarget();
        if (target != null) this.getLookControl().setLookAt(target, 30.0F, 30.0F);
        if (t == DRAW_SOUND_TICK) this.playSound(ModSounds.GNOLL_ARCHER_SHOOT.get(), 0.8F, 1.0F);
        if (t == RELEASE_TICK && target != null && target.isAlive()) this.shootAt(target);
    }

    private void shootAt(LivingEntity target) {
        Arrow arrow = new Arrow(this.level(), this);
        arrow.setPos(this.getX(), this.getEyeY() - 0.1D, this.getZ());
        arrow.setBaseDamage(this.getAttributeValue(Attributes.ATTACK_DAMAGE));
        arrow.addEffect(new MobEffectInstance(MobEffects.POISON, 60));
        arrow.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 80));
        arrow.pickup = AbstractArrow.Pickup.DISALLOWED;
        double dx = target.getX() - this.getX();
        double dy = target.getY(0.33D) - arrow.getY();
        double dz = target.getZ() - this.getZ();
        double flat = Math.sqrt(dx * dx + dz * dz);
        arrow.shoot(dx, dy + flat * 0.2D, dz, 1.6F, 14 - this.level().getDifficulty().getId() * 4);
        this.level().addFreshEntity(arrow);
    }

    /** Keeps 6-12 blocks away, walks closer when out of range / sight, shoots every ~2.5 s. */
    static class ArcherGoal extends Goal {
        private final GnollArcherEntity gnoll;
        private int cooldown;
        private int seeTime;
        private int strafeTicks;
        private boolean strafeLeft;

        ArcherGoal(GnollArcherEntity gnoll) {
            this.gnoll = gnoll;
            this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            LivingEntity target = this.gnoll.getTarget();
            return target != null && target.isAlive();
        }

        @Override
        public void start() {
            this.cooldown = 20;
        }

        @Override
        public void stop() {
            this.gnoll.getNavigation().stop();
            this.seeTime = 0;
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }

        @Override
        public void tick() {
            LivingEntity target = this.gnoll.getTarget();
            if (target == null) return;
            if (this.cooldown > 0) this.cooldown--;
            int act = this.gnoll.getActionId();
            if (act == ACT_JUMP_LEFT || act == ACT_JUMP_RIGHT) return;

            boolean sees = this.gnoll.getSensing().hasLineOfSight(target);
            this.seeTime = sees ? this.seeTime + 1 : 0;
            double dist = this.gnoll.distanceTo(target);
            this.gnoll.getLookControl().setLookAt(target, 30.0F, 30.0F);

            if (!sees || dist > SHOOT_RANGE - 2.0D) {
                this.gnoll.getNavigation().moveTo(target, 1.15D);
            } else if (dist < 6.0D) {
                // too close: back off
                Vec3 away = this.gnoll.position().subtract(target.position()).normalize();
                Vec3 dest = this.gnoll.position().add(away.scale(4.0D));
                this.gnoll.getNavigation().moveTo(dest.x, dest.y, dest.z, 1.2D);
            } else {
                this.gnoll.getNavigation().stop();
                if (--this.strafeTicks <= 0) {
                    this.strafeTicks = 20 + this.gnoll.getRandom().nextInt(30);
                    this.strafeLeft = this.gnoll.getRandom().nextBoolean();
                }
                if (!this.gnoll.isBusy()) {
                    this.gnoll.getMoveControl().strafe(0.0F, this.strafeLeft ? 0.35F : -0.35F);
                }
                this.gnoll.setYRot(Mth.rotateIfNecessary(this.gnoll.getYRot(), this.gnoll.yHeadRot, 30.0F));
            }

            if (sees && this.seeTime > 5 && dist <= SHOOT_RANGE && this.cooldown <= 0 && !this.gnoll.isBusy()) {
                this.gnoll.startAction(ACT_SHOOT);
                this.cooldown = 50;
            }
        }
    }
}
