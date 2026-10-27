package net.icegiant.slime;

import net.icegiant.mixin.SlimeInvoker;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.monster.AbstractSkeleton;
import net.minecraft.world.entity.monster.Slime;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.ForgeEventFactory;

import javax.annotation.Nullable;
import java.util.EnumSet;
import java.util.List;

/**
 * The slimes' behaviour (SlimeLife puts these in place of vanilla's goals, 2026-10-27). Highest priority first:
 * swim, hunt the target (hopping), eat a body, eat food lying on the ground, graze plants when hungry, crawl around.
 */
public final class SlimeGoals {

    static final double FOOD_RANGE = 10.0D, CORPSE_RANGE = 14.0D, GRAZE_RANGE = 6;
    static final int ITEM_EAT_TICKS = 40;                      // one item every 2 s ("langsam essen")
    static final int CORPSE_EAT_TICKS = 200;                   // a whole body in 10 s
    static final int GRAZE_TICKS = 60;
    static final int GIVE_UP_TICKS = 600;

    private SlimeGoals() {
    }

    static SlimeControl control(Slime s) {
        return (SlimeControl) s.getMoveControl();
    }

    /** Horizontal distance from the slime's middle to a point. */
    static double flatDist(Slime s, double x, double z) {
        return Math.hypot(x - s.getX(), z - s.getZ());
    }

    /** Crawl to a spot, then sit exactly on it (nudged into place). True while sitting on it. */
    static boolean crawlOnto(Slime s, Vec3 at, int tick) {
        double d = flatDist(s, at.x, at.z);
        SlimeControl c = control(s);
        c.crawl();
        if (d > 0.45D) {
            if (tick % 10 == 0 || s.getNavigation().isDone()) s.getNavigation().moveTo(at.x, at.y, at.z, 1.0D);
            return false;
        }
        s.getNavigation().stop();
        c.halt();
        Vec3 pull = new Vec3(at.x - s.getX(), 0.0D, at.z - s.getZ()).scale(0.15D);
        s.setDeltaMovement(s.getDeltaMovement().multiply(0.5D, 1.0D, 0.5D).add(pull));
        return true;
    }

    // ------------------------------------------------------------------ swim

    static final class Swim extends Goal {
        private final Slime s;

        Swim(Slime s) {
            this.s = s;
            this.setFlags(EnumSet.of(Flag.JUMP));
        }

        @Override
        public boolean canUse() {
            return this.s.isInWater() || this.s.isInLava();
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }

        @Override
        public void tick() {
            if (this.s.getRandom().nextFloat() < 0.8F) this.s.getJumpControl().jump();
        }
    }

    // ------------------------------------------------------------------ hunt

    /** Chases the target hopping (like vanilla); touching damages it - now also monsters and villagers. */
    static final class Hunt extends Goal {
        private final Slime s;
        private int ticks;

        Hunt(Slime s) {
            this.s = s;
            this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            LivingEntity t = this.s.getTarget();
            return t != null && t.isAlive() && this.s.canAttack(t);
        }

        @Override
        public void start() {
            this.ticks = 300;
            this.s.getNavigation().stop();
        }

        @Override
        public boolean canContinueToUse() {
            LivingEntity t = this.s.getTarget();
            if (t == null || !t.isAlive() || !this.s.canAttack(t)) return false;
            return !(t instanceof Player p && p.getAbilities().invulnerable) && --this.ticks > 0;
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }

        @Override
        public void tick() {
            LivingEntity t = this.s.getTarget();
            if (t == null) return;
            this.s.lookAt(t, 10.0F, 10.0F);
            control(this.s).hop(this.s.getYRot(), true, 1.0D);
            // vanilla only hurts players (playerTouch) and iron golems (push) on touch
            if (!(t instanceof Player) && !(t instanceof IronGolem) && this.s.getBoundingBox().inflate(0.1D).intersects(t.getBoundingBox())) {
                SlimeInvoker inv = (SlimeInvoker) this.s;
                if (inv.icegiant$isDealsDamage()) inv.icegiant$dealDamage(t);
            }
        }
    }

    // ------------------------------------------------------------------ eat a body

    static final class EatCorpse extends Goal {
        private final Slime s;
        @Nullable private LivingEntity body;
        private int ticks, eaten;

        EatCorpse(Slime s) {
            this.s = s;
            this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK, Flag.JUMP));
        }

        @Override
        public boolean canUse() {
            if (this.s.getTarget() != null || this.s.tickCount % 20 != 0) return false;
            List<LivingEntity> bodies = this.s.level().getEntitiesOfClass(LivingEntity.class,
                    this.s.getBoundingBox().inflate(CORPSE_RANGE), e -> e.isDeadOrDying() && SlimeCorpses.isCorpse(e)
                            && !SlimeCorpses.isTaken(e, this.s));
            LivingEntity best = null;
            for (LivingEntity e : bodies) if (best == null || this.s.distanceToSqr(e) < this.s.distanceToSqr(best)) best = e;
            this.body = best;
            return best != null;
        }

        @Override
        public void start() {
            this.ticks = 0;
            this.eaten = 0;
            if (this.body != null) SlimeCorpses.take(this.body, this.s);
        }

        @Override
        public boolean canContinueToUse() {
            return this.body != null && !this.body.isRemoved() && this.s.getTarget() == null && this.ticks < GIVE_UP_TICKS + CORPSE_EAT_TICKS;
        }

        @Override
        public void stop() {
            if (this.body != null) SlimeCorpses.release(this.body, this.s);
            this.body = null;
            this.s.getNavigation().stop();
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }

        @Override
        public void tick() {
            if (this.body == null) return;
            this.ticks++;
            if (!crawlOnto(this.s, this.body.position(), this.ticks)) return;
            this.eaten++;
            if (this.eaten % 10 == 0 && this.s.level() instanceof ServerLevel level) {
                ItemStack crumb = SlimeFood.corpseCrumb(this.body instanceof AbstractSkeleton);
                level.sendParticles(new ItemParticleOption(ParticleTypes.ITEM, crumb), this.body.getX(), this.body.getY() + 0.3D,
                        this.body.getZ(), 4, 0.3D, 0.1D, 0.3D, 0.04D);
                this.s.playSound(this.eaten % 20 == 0 ? SoundEvents.GENERIC_EAT : SoundEvents.SLIME_SQUISH_SMALL,
                        0.6F, 0.5F + this.s.getRandom().nextFloat() * 0.2F);
            }
            if (this.eaten >= CORPSE_EAT_TICKS) {
                this.body.discard();
                SlimeFood.eatCorpse(this.s);
                this.body = null;
            }
        }
    }

    // ------------------------------------------------------------------ eat food lying on the ground

    static final class EatItem extends Goal {
        private final Slime s;
        @Nullable private ItemEntity item;
        private int ticks, chew;

        EatItem(Slime s) {
            this.s = s;
            this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK, Flag.JUMP));
        }

        @Override
        public boolean canUse() {
            if (this.s.getTarget() != null || this.s.tickCount % 10 != 0 || SlimeFood.isFull(this.s)) return false;
            ItemEntity best = null;
            for (ItemEntity e : this.s.level().getEntitiesOfClass(ItemEntity.class, this.s.getBoundingBox().inflate(FOOD_RANGE),
                    e -> e.isAlive() && SlimeFood.kindOf(this.s, e.getItem()) != null)) {
                if (best == null || this.s.distanceToSqr(e) < this.s.distanceToSqr(best)) best = e;
            }
            this.item = best;
            return best != null;
        }

        @Override
        public void start() {
            this.ticks = 0;
            this.chew = 0;
        }

        @Override
        public boolean canContinueToUse() {
            return this.item != null && this.item.isAlive() && this.s.getTarget() == null && !SlimeFood.isFull(this.s)
                    && this.ticks < GIVE_UP_TICKS && SlimeFood.kindOf(this.s, this.item.getItem()) != null;
        }

        @Override
        public void stop() {
            this.item = null;
            this.s.getNavigation().stop();
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }

        @Override
        public void tick() {
            if (this.item == null) return;
            this.ticks++;
            if (!crawlOnto(this.s, this.item.position(), this.ticks)) {
                this.chew = 0;
                return;
            }
            this.item.setDeltaMovement(Vec3.ZERO);                   // (lies still inside him while he eats it)
            if (++this.chew % 10 == 0) this.s.playSound(SoundEvents.SLIME_SQUISH_SMALL, 0.4F, 0.7F + this.s.getRandom().nextFloat() * 0.3F);
            if (this.chew < ITEM_EAT_TICKS) return;
            this.chew = 0;
            ItemStack stack = this.item.getItem().copy();
            SlimeFood.Kind kind = SlimeFood.kindOf(this.s, stack);
            if (kind == null) return;
            ItemStack rest = stack.copy();
            rest.shrink(1);
            if (rest.isEmpty()) this.item.discard();
            else this.item.setItem(rest);
            stack.setCount(1);
            SlimeFood.eat(this.s, kind, stack);
        }
    }

    // ------------------------------------------------------------------ graze plants

    static final class Graze extends Goal {
        private final Slime s;
        @Nullable private BlockPos spot;
        private int ticks, chew;

        Graze(Slime s) {
            this.s = s;
            this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK, Flag.JUMP));
        }

        @Override
        public boolean canUse() {
            if (this.s.getTarget() != null || this.s.getRandom().nextInt(60) != 0 || !SlimeFood.isHungry(this.s)
                    || this.s instanceof net.minecraft.world.entity.monster.MagmaCube
                    || !ForgeEventFactory.getMobGriefingEvent(this.s.level(), this.s)) return false;
            this.spot = this.findPlant();
            return this.spot != null;
        }

        /** A plant (or a bare grass block) near him on his level. */
        @Nullable
        private BlockPos findPlant() {
            BlockPos at = this.s.blockPosition();
            BlockPos grass = null;
            for (int i = 0; i < 24; i++) {
                BlockPos p = at.offset(this.s.getRandom().nextInt(13) - 6, this.s.getRandom().nextInt(3) - 1, this.s.getRandom().nextInt(13) - 6);
                BlockState st = this.s.level().getBlockState(p);
                if (st.is(SlimeFood.GRAZE)) return p;
                if (grass == null && this.s.level().getBlockState(p.below()).is(Blocks.GRASS_BLOCK) && st.isAir()) grass = p;
            }
            return grass;
        }

        @Override
        public void start() {
            this.ticks = 0;
            this.chew = 0;
        }

        @Override
        public boolean canContinueToUse() {
            return this.spot != null && this.s.getTarget() == null && this.ticks < GIVE_UP_TICKS && !SlimeFood.isFull(this.s);
        }

        @Override
        public void stop() {
            this.spot = null;
            this.s.getNavigation().stop();
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }

        @Override
        public void tick() {
            if (this.spot == null) return;
            this.ticks++;
            if (!crawlOnto(this.s, Vec3.atBottomCenterOf(this.spot), this.ticks)) return;
            BlockState plant = this.s.level().getBlockState(this.spot);
            BlockState below = this.s.level().getBlockState(this.spot.below());
            boolean isPlant = plant.is(SlimeFood.GRAZE);
            if (!isPlant && !below.is(Blocks.GRASS_BLOCK)) {
                this.spot = null;
                return;
            }
            this.chew++;
            if (this.chew % 20 == 0 && this.s.level() instanceof ServerLevel level) {
                BlockState shown = isPlant ? plant : below;
                level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, shown), this.spot.getX() + 0.5D,
                        this.spot.getY() + 0.1D, this.spot.getZ() + 0.5D, 8, 0.25D, 0.05D, 0.25D, 0.05D);
                this.s.playSound(shown.getSoundType().getBreakSound(), 0.5F, 0.8F);
            }
            if (this.chew < GRAZE_TICKS) return;
            if (isPlant) {
                this.s.level().destroyBlock(this.spot, false, this.s);
            } else {
                this.s.level().levelEvent(2001, this.spot.below(), Block.getId(below));
                this.s.level().setBlock(this.spot.below(), Blocks.DIRT.defaultBlockState(), 2);
            }
            SlimeFood.eat(this.s, SlimeFood.Kind.PLANT, null);
            this.spot = null;
        }
    }

    // ------------------------------------------------------------------ crawl around

    /** Crawls to a spot nearby now and then; sometimes hops a few times instead (the old slime way). */
    static final class Wander extends Goal {
        private final Slime s;
        private int hops, hopTicks;
        private float hopYaw;

        Wander(Slime s) {
            this.s = s;
            this.setFlags(EnumSet.of(Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            return this.s.getTarget() == null && this.s.getNavigation().isDone() && this.s.getRandom().nextInt(80) == 0;
        }

        @Override
        public void start() {
            if (this.s.getRandom().nextFloat() < 0.2F) {                // a few hops in some direction
                this.hops = 1;
                this.hopTicks = 60 + this.s.getRandom().nextInt(60);
                this.hopYaw = this.s.getRandom().nextFloat() * 360.0F;
                return;
            }
            this.hops = 0;
            BlockPos at = this.s.blockPosition();
            for (int i = 0; i < 8; i++) {
                BlockPos p = at.offset(this.s.getRandom().nextInt(17) - 8, this.s.getRandom().nextInt(5) - 2, this.s.getRandom().nextInt(17) - 8);
                if (this.s.level().getBlockState(p).isAir() && this.s.level().getBlockState(p.below()).isSolid()) {
                    control(this.s).crawl();
                    this.s.getNavigation().moveTo(p.getX() + 0.5D, p.getY(), p.getZ() + 0.5D, 0.8D);
                    return;
                }
            }
        }

        @Override
        public boolean canContinueToUse() {
            return this.s.getTarget() == null && (this.hops > 0 ? --this.hopTicks > 0 : !this.s.getNavigation().isDone());
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return this.hops > 0;
        }

        @Override
        public void tick() {
            if (this.hops > 0) control(this.s).hop(this.hopYaw, false, 1.0D);
        }

        @Override
        public void stop() {
            this.hops = 0;
            control(this.s).crawl();
        }
    }

    static float yawTo(Slime s, double x, double z) {
        return (float) (Mth.atan2(z - s.getZ(), x - s.getX()) * Mth.RAD_TO_DEG) - 90.0F;
    }
}
