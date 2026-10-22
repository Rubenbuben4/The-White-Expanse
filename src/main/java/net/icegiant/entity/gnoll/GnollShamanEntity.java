package net.icegiant.entity.gnoll;

import net.icegiant.registry.ModEntityTypes;
import net.icegiant.registry.ModSounds;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;

/**
 * Gnoll shaman: stays behind the fighters and casts
 * <ul>
 *   <li>"spirits" - up to three red spirits fly at enemies (damage) and up to three green
 *       spirits fly to hurt allies (Gnolls and Ice Giants) and heal them;</li>
 *   <li>"circle" - a cursed circle under the target which bursts after 2.5 s
 *       (slowness + nausea for enemies inside, see GnollCircleEntity; 35 s cooldown).</li>
 * </ul>
 */
public class GnollShamanEntity extends GnollEntity {

    public static final int ACT_SPIRITS = 1, ACT_CIRCLE = 2;
    private static final String[] ANIMS = {"", "spirits", "circle"};
    private static final int[] LENGTHS = {0, 50, 40};

    private static final int SPIRITS_RELEASE_TICK = 47;
    private static final int CIRCLE_SUMMON_TICK = 39;
    private static final float SPIRIT_POWER = 4.0F;

    private int spiritsCooldown = 40;
    private int circleCooldown = 200;

    public GnollShamanEntity(EntityType<? extends Monster> type, Level level) {
        super(type, level);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return baseAttributes()
                .add(Attributes.MAX_HEALTH, 20.0D)
                .add(Attributes.ATTACK_DAMAGE, 2.0D)
                .add(Attributes.ARMOR, 1.0D);
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
        this.goalSelector.addGoal(2, new CasterGoal(this));
    }

    @Override
    protected void customServerAiStep() {
        if (this.spiritsCooldown > 0) this.spiritsCooldown--;
        if (this.circleCooldown > 0) this.circleCooldown--;
        LivingEntity target = this.getTarget();
        if (target != null && !this.isBusy() && this.hasLineOfSight(target)) {
            double dist = this.distanceTo(target);
            if (this.circleCooldown <= 0 && dist < 16.0D && this.random.nextInt(20) == 0) {
                this.circleCooldown = 700;
                this.startAction(ACT_CIRCLE);
                this.playSound(ModSounds.GNOLL_SHAMAN_CIRCLE.get(), 0.8F, 1.0F);
            } else if (this.spiritsCooldown <= 0 && dist < 20.0D) {
                this.spiritsCooldown = 120;
                this.startAction(ACT_SPIRITS);
                this.playSound(ModSounds.GNOLL_SHAMAN_SPIRITS.get(), 0.8F, 1.0F);
            }
        }
        super.customServerAiStep();
    }

    /** Roughly where the staff tip is (front right, chest high). */
    private Vec3 staffTip() {
        Vec3 look = Vec3.directionFromRotation(0.0F, this.yBodyRot);
        Vec3 right = new Vec3(-look.z, 0.0D, look.x);
        return this.position().add(look.scale(0.7D)).add(right.scale(0.35D)).add(0.0D, 1.35D, 0.0D);
    }

    @Override
    protected void tickAction(int id, int t) {
        this.getNavigation().stop();
        LivingEntity target = this.getTarget();
        if (target != null) this.getLookControl().setLookAt(target, 30.0F, 30.0F);
        if (!(this.level() instanceof ServerLevel server)) return;

        if (id == ACT_SPIRITS) {
            if (t >= 25 && t < SPIRITS_RELEASE_TICK && t % 2 == 0) {
                Vec3 tip = this.staffTip();
                server.sendParticles(ParticleTypes.END_ROD, tip.x, tip.y, tip.z, 1, 0.02D, 0.02D, 0.02D, 0.0D);
            }
            if (t == SPIRITS_RELEASE_TICK) this.releaseSpirits(server);
        } else if (id == ACT_CIRCLE && t == CIRCLE_SUMMON_TICK && target != null && target.isAlive()) {
            GnollCircleEntity circle = ModEntityTypes.GNOLL_CIRCLE.get().create(server);
            if (circle != null) {
                circle.moveTo(target.getX(), target.getY(), target.getZ(), 0.0F, 0.0F);
                circle.setOwner(this);
                server.addFreshEntity(circle);
            }
        }
    }

    private void releaseSpirits(ServerLevel server) {
        Vec3 tip = this.staffTip();
        // red spirits: the current target first, then the nearest other players
        List<LivingEntity> enemies = new ArrayList<>();
        LivingEntity target = this.getTarget();
        if (target != null && target.isAlive()) enemies.add(target);
        server.getEntitiesOfClass(Player.class, this.getBoundingBox().inflate(20.0D),
                        p -> p.isAlive() && !p.isCreative() && !p.isSpectator() && p != target)
                .stream().sorted(Comparator.comparingDouble(this::distanceToSqr))
                .limit(3 - enemies.size()).forEach(enemies::add);
        for (LivingEntity enemy : enemies) {
            GnollSpiritEntity.launch(server, this, enemy, tip, false, SPIRIT_POWER);
        }
        // green spirits: the most hurt allies (Gnolls and Ice Giants) nearby
        server.getEntitiesOfClass(LivingEntity.class, this.getBoundingBox().inflate(17.0D),
                        e -> isFriend(e) && e.isAlive() && e.getHealth() < e.getMaxHealth())
                .stream().sorted(Comparator.comparingDouble(e -> e.getHealth() / e.getMaxHealth()))
                .limit(3).forEach(ally -> GnollSpiritEntity.launch(server, this, ally, tip, true, SPIRIT_POWER));
    }

    /** Keeps 8-14 blocks from the target and in sight of it; casting roots the shaman. */
    static class CasterGoal extends Goal {
        private final GnollShamanEntity gnoll;
        private int repath;

        CasterGoal(GnollShamanEntity gnoll) {
            this.gnoll = gnoll;
            this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            LivingEntity target = this.gnoll.getTarget();
            return target != null && target.isAlive();
        }

        @Override
        public void stop() {
            this.gnoll.getNavigation().stop();
        }

        @Override
        public void tick() {
            LivingEntity target = this.gnoll.getTarget();
            if (target == null || this.gnoll.isBusy()) return;
            this.gnoll.getLookControl().setLookAt(target, 30.0F, 30.0F);
            if (--this.repath > 0) return;
            this.repath = 10;
            double dist = this.gnoll.distanceTo(target);
            boolean sees = this.gnoll.getSensing().hasLineOfSight(target);
            if (!sees || dist > 14.0D) {
                this.gnoll.getNavigation().moveTo(target, 1.1D);
            } else if (dist < 8.0D) {
                Vec3 away = this.gnoll.position().subtract(target.position()).normalize();
                Vec3 dest = this.gnoll.position().add(away.scale(5.0D));
                this.gnoll.getNavigation().moveTo(dest.x, dest.y, dest.z, 1.2D);
            } else {
                this.gnoll.getNavigation().stop();
            }
        }
    }
}
