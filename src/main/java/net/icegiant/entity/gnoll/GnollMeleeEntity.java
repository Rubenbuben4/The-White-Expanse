package net.icegiant.entity.gnoll;

import net.icegiant.registry.ModSounds;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.Comparator;
import java.util.List;

/**
 * Gnoll melee fighter with two blades: two-hit combo (attack1/attack2, each in two variants),
 * "massacre" (four quick cuts, 15 s cooldown) and a bite with big spectral jaws that slows the
 * victim and heals the Gnoll (10 s cooldown).
 */
public class GnollMeleeEntity extends GnollEntity {

    public static final int ACT_ATTACK1 = 1, ACT_ATTACK1_B = 2, ACT_ATTACK2 = 3, ACT_ATTACK2_B = 4,
            ACT_MASSACRE = 5, ACT_BITE = 6;
    private static final String[] ANIMS = {"", "attack1", "attack1_1", "attack2", "attack2_1", "massacre", "bite"};
    private static final int[] LENGTHS = {0, 15, 15, 15, 15, 59, 25};

    private static final int SWING_HIT_TICK = 9;
    private static final int[] MASSACRE_HIT_TICKS = {22, 30, 38, 48};
    private static final int BITE_LOCK_TICK = 15;
    private static final int BITE_HIT_TICK = 22;

    private int massacreCooldown = 100;
    private int biteCooldown = 60;
    /** Ticks left in which the next swing is the second hit of the combo. */
    private int comboWindow;
    private float lockedYaw;

    public GnollMeleeEntity(EntityType<? extends Monster> type, Level level) {
        super(type, level);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return baseAttributes()
                .add(Attributes.MAX_HEALTH, 30.0D)
                .add(Attributes.ATTACK_DAMAGE, 5.0D)
                .add(Attributes.ARMOR, 3.0D);
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
        this.goalSelector.addGoal(2, new GnollMeleeGoal(this));
    }

    /** Starts the next swing of the combo (called by the attack goal when in reach). */
    void swing() {
        boolean second = this.comboWindow > 0;
        boolean variant = this.random.nextBoolean();
        this.startAction(second ? (variant ? ACT_ATTACK2 : ACT_ATTACK2_B) : (variant ? ACT_ATTACK1 : ACT_ATTACK1_B));
        this.comboWindow = second ? 0 : 40;
        this.playSound(ModSounds.GNOLL_ATTACK.get(), 0.7F, this.getVoicePitch());
    }

    @Override
    protected void customServerAiStep() {
        if (this.massacreCooldown > 0) this.massacreCooldown--;
        if (this.biteCooldown > 0) this.biteCooldown--;
        if (this.comboWindow > 0) this.comboWindow--;
        LivingEntity target = this.getTarget();
        if (!this.isBusy() && target != null && this.hasLineOfSight(target)) {
            double dist = this.distanceTo(target);
            if (this.massacreCooldown <= 0 && dist < 4.5D && this.random.nextInt(8) == 0) {
                this.massacreCooldown = 300;
                this.startAction(ACT_MASSACRE);
                this.playSound(ModSounds.GNOLL_MASSACRE.get(), 0.9F, 1.0F);
            } else if (this.biteCooldown <= 0 && dist < 3.0D && this.random.nextInt(6) == 0) {
                this.biteCooldown = 200;
                this.startAction(ACT_BITE);
                this.playSound(ModSounds.GNOLL_BITE.get(), 0.9F, 0.8F);
            }
        }
        super.customServerAiStep();
    }

    @Override
    protected void tickAction(int id, int t) {
        float damage = (float) this.getAttributeValue(Attributes.ATTACK_DAMAGE);
        switch (id) {
            case ACT_ATTACK1, ACT_ATTACK1_B, ACT_ATTACK2, ACT_ATTACK2_B -> {
                if (t == SWING_HIT_TICK - 1) this.sweepParticle(1.4D);
                if (t == SWING_HIT_TICK) this.hitAll(this.enemiesInFront(3.2D, 120.0D), damage);
            }
            case ACT_MASSACRE -> {
                for (int hit : MASSACRE_HIT_TICKS) {
                    if (t == hit) {
                        this.sweepParticle(2.0D);
                        this.hitAll(this.enemiesInFront(3.5D, 160.0D), damage * 0.9F);
                    }
                }
            }
            case ACT_BITE -> {
                this.getNavigation().stop();
                if (t == BITE_LOCK_TICK) this.lockedYaw = this.getYRot();
                if (t == BITE_HIT_TICK) {
                    List<LivingEntity> victims = this.enemiesInFront(4.0D, 120.0D);
                    victims.stream().min(Comparator.comparingDouble(this::distanceToSqr)).ifPresent(victim -> {
                        if (victim.hurt(this.damageSources().mobAttack(this), damage * 1.8F)) {
                            victim.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 40, 1), this);
                            this.heal(this.getMaxHealth() * 0.1F);
                            this.playSound(ModSounds.GNOLL_PLAYER_HIT.get(), 0.7F, 0.9F);
                            if (this.level() instanceof ServerLevel server) {
                                server.sendParticles(ParticleTypes.CRIT, victim.getX(), victim.getY(0.6D), victim.getZ(),
                                        10, 0.2D, 0.2D, 0.2D, 0.1D);
                                server.sendParticles(ParticleTypes.HAPPY_VILLAGER, this.getX(), this.getY(0.5D),
                                        this.getZ(), 6, 0.3D, 0.5D, 0.3D, 0.0D);
                            }
                        }
                    });
                }
            }
            default -> {
            }
        }
    }

    @Override
    public void aiStep() {
        super.aiStep();
        // during the bite the Gnoll is rooted and keeps facing where it snapped
        if (!this.level().isClientSide && this.getActionId() == ACT_BITE && this.actionTicks >= BITE_LOCK_TICK) {
            this.setYRot(this.lockedYaw);
            this.yBodyRot = this.lockedYaw;
            this.yHeadRot = this.lockedYaw;
        }
    }

    private void hitAll(List<LivingEntity> victims, float damage) {
        boolean any = false;
        for (LivingEntity victim : victims) {
            any |= victim.hurt(this.damageSources().mobAttack(this), damage);
        }
        if (any) {
            this.playSound(ModSounds.GNOLL_PLAYER_HIT.get(), 0.6F, 0.8F + this.random.nextFloat() * 0.2F);
        }
    }

    private void sweepParticle(double forward) {
        if (this.level() instanceof ServerLevel server) {
            Vec3 look = Vec3.directionFromRotation(0.0F, this.getYRot());
            server.sendParticles(ParticleTypes.SWEEP_ATTACK, this.getX() + look.x * forward, this.getY() + 1.0D,
                    this.getZ() + look.z * forward, 1, 0.05D, 0.05D, 0.05D, 0.0D);
        }
    }

    /** Chases the target; in reach it starts a timed swing instead of the vanilla instant hit. */
    static class GnollMeleeGoal extends MeleeAttackGoal {
        private final GnollMeleeEntity gnoll;

        GnollMeleeGoal(GnollMeleeEntity gnoll) {
            super(gnoll, 1.25D, true);
            this.gnoll = gnoll;
        }

        @Override
        public void tick() {
            if (this.gnoll.getActionId() == ACT_BITE) {
                this.gnoll.getNavigation().stop();
                return;
            }
            super.tick();
        }

        @Override
        protected void checkAndPerformAttack(LivingEntity target, double distSqr) {
            if (this.gnoll.isBusy()) return;
            if (distSqr <= this.getAttackReachSqr(target) && this.isTimeToAttack()) {
                this.resetAttackCooldown();
                this.gnoll.swing();
            }
        }

        @Override
        protected double getAttackReachSqr(LivingEntity target) {
            return 2.6D * 2.6D + target.getBbWidth();
        }
    }
}
