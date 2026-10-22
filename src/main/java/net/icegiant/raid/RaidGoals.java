package net.icegiant.raid;

import net.icegiant.entity.IceGiantEntity;
import net.icegiant.entity.gnoll.GnollEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.GoalSelector;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FarmBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraftforge.event.ForgeEventFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * What Ice Giants and Gnolls do while they raid a village (Frost Raid):
 * <ul>
 *   <li>they hunt villagers (and iron golems) - Gnolls as a pack, the giant as their general;</li>
 *   <li>without a fight at hand they march to the village;</li>
 *   <li>they wreck it: smash wooden doors (both), trample farmland and crops (both), set wooden buildings on
 *       fire (Gnolls - about half of them are "fire raisers" that do this even while villagers are near).</li>
 * </ul>
 * Raiders carry the NBT mark {@value #TAG} (the raid's centre) so they keep raiding after a reload.
 * Wrecking needs the mobGriefing game rule.
 */
public final class RaidGoals {

    public static final String TAG = "icegiant_raid";
    public static final String RETREAT = "icegiant_retreat";   // set when they withdraw: the rift to go back to
    public static final String RIFT = "icegiant_rift";         // raiders that came through a rift: where it is
    public static final String SNATCH = "icegiant_snatch";     // heavy losses: a giant grabs a villager and flees

    // ---- TUNE: abductions (giants of rift raids) ----
    static final double KIDNAPPER_SHARE = 0.35;               // share of giants that carry off a villager now and then
    static final int ABDUCT_CHANCE = 40;                      // ... 1 in this many ticks while one is within reach
    static final double SNATCH_SEARCH = 40.0D;                // fleeing giants look this far for a villager to take
    static final int SNATCH_SEARCH_TICKS = 600;               // ... and give up after 30 s
    private static final Set<Mob> EQUIPPED = Collections.newSetFromMap(new WeakHashMap<>());

    // ---- TUNE ----
    static final int SEARCH_RADIUS = 12, SEARCH_HEIGHT = 5;    // where a raider looks for something to wreck
    static final double MAX_FROM_CENTER = 56.0D;               // ... only inside the village
    static final int DOOR_HITS_TICKS = 40;                     // 2 s of hacking at a door before it breaks
    static final int WRECK_COOLDOWN_MIN = 60, WRECK_COOLDOWN_MAX = 160;
    static final int GIVE_UP_TICKS = 200;                      // can't reach it in 10 s: something else
    static final double FIRE_RAISER_SHARE = 0.5;               // share of Gnolls that set fires before fighting
    static final double MARCH_NEAR = 10.0D;                    // close enough to the village centre

    private RaidGoals() {
    }

    public static boolean isRaider(Entity e) {
        return e instanceof Mob m && m.getPersistentData().contains(TAG);
    }

    /** A raider that is still attacking (not withdrawing through the rift). */
    public static boolean isActiveRaider(Entity e) {
        return isRaider(e) && !((Mob) e).getPersistentData().contains(RETREAT) && !((Mob) e).getPersistentData().contains(SNATCH);
    }

    /** Withdraw: stop fighting and go back through the rift at {@code rift} (they vanish there). */
    public static void retreat(Mob m, BlockPos rift) {
        m.getPersistentData().putLong(RETREAT, rift.asLong());
        m.setTarget(null);
    }

    /** Gone through the rift: a swirl of snow and portal light, then it is no longer here. */
    public static void vanish(Mob m) {
        if (m.level() instanceof net.minecraft.server.level.ServerLevel level) {
            level.sendParticles(net.minecraft.core.particles.ParticleTypes.PORTAL, m.getX(), m.getY() + m.getBbHeight() * 0.5D, m.getZ(),
                    40, m.getBbWidth() * 0.5D, m.getBbHeight() * 0.4D, m.getBbWidth() * 0.5D, 0.3D);
            level.sendParticles(net.minecraft.core.particles.ParticleTypes.SNOWFLAKE, m.getX(), m.getY() + m.getBbHeight() * 0.5D, m.getZ(),
                    20, m.getBbWidth() * 0.5D, m.getBbHeight() * 0.4D, m.getBbWidth() * 0.5D, 0.05D);
            level.playSound(null, m.blockPosition(), SoundEvents.ENDERMAN_TELEPORT, SoundSource.HOSTILE, 1.0F, 0.6F);
        }
        m.discard();
    }

    public static BlockPos center(Mob m) {
        return BlockPos.of(m.getPersistentData().getLong(TAG));
    }

    /** Makes this mob a raider of the village at {@code center} (kept in its NBT). */
    public static void mark(Mob m, BlockPos center) {
        m.getPersistentData().putLong(TAG, center.asLong());
        m.setPersistenceRequired();
        if (m instanceof IceGiantEntity g) g.equipRaidGoals();
        if (m instanceof GnollEntity k) k.equipRaidGoals();
    }

    /** The raid is over: an ordinary mob again (its raid goals stay but do nothing). */
    public static void unmark(Mob m) {
        m.getPersistentData().remove(TAG);
        m.getPersistentData().remove(RETREAT);
        m.getPersistentData().remove(RIFT);
        m.getPersistentData().remove(SNATCH);
    }

    /**
     * Adds the raid goals to a giant or Gnoll (once; called from the entity itself, which owns its goal
     * selectors). Priorities fit their own goals: attacks 1-2, these 3-4, stroll 5+.
     */
    public static void addGoals(PathfinderMob mob, GoalSelector goals, GoalSelector targets) {
        if (!EQUIPPED.add(mob)) return;
        targets.addGoal(2, new NearestAttackableTargetGoal<>(mob, AbstractVillager.class, 10, false, false,
                t -> isActiveRaider(mob)));
        targets.addGoal(3, new NearestAttackableTargetGoal<>(mob, IronGolem.class, 10, false, false,
                t -> isActiveRaider(mob)));
        boolean fireRaiser = mob instanceof GnollEntity && (mob.getUUID().getLeastSignificantBits() & 1023) < FIRE_RAISER_SHARE * 1024;
        goals.addGoal(fireRaiser ? 1 : 3, new WreckGoal(mob, mob instanceof GnollEntity, fireRaiser));
        goals.addGoal(4, new MarchGoal(mob));
        goals.addGoal(0, new RetreatGoal(mob));
        if (mob instanceof IceGiantEntity giant) goals.addGoal(0, new AbductGoal(giant));
    }

    // ------------------------------------------------------------------ carry off a villager (rift raids)

    /**
     * A giant of a rift raid grabs a villager and carries it off through the rift - it is never seen again.
     * Some giants ("kidnappers") do this now and then during the fight with a villager in reach; when the raid
     * goes badly (SNATCH) every giant looks for a villager to take and flees with it. Killing the giant on its
     * way frees the villager. Uses the giant's grab pose (villager held in the fist); the grab-and-throw attack
     * itself is not changed.
     */
    static class AbductGoal extends Goal {
        private final IceGiantEntity giant;
        private AbstractVillager prey;
        private boolean carrying;
        private int ticks;

        AbductGoal(IceGiantEntity giant) {
            this.giant = giant;
            this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        private boolean kidnapper() {
            return ((this.giant.getUUID().getMostSignificantBits() >>> 3) & 1023) < KIDNAPPER_SHARE * 1024;
        }

        @Override
        public boolean canUse() {
            if (!isRaider(this.giant) || !this.giant.getPersistentData().contains(RIFT)) return false;
            if (this.giant.isVehicle() || this.giant.isGrabbing() || !this.giant.onGround()) return false;
            boolean snatch = this.giant.getPersistentData().contains(SNATCH);
            if (snatch) {
                if (this.giant.getPersistentData().contains(RETREAT)) return false;      // gave up: just flee
                this.prey = nearestVillager(this.giant, SNATCH_SEARCH);
                if (this.prey == null) {                                                 // nobody to take
                    this.giant.getPersistentData().putLong(RETREAT, this.giant.getPersistentData().getLong(RIFT));
                    return false;
                }
            } else {
                if (!this.kidnapper() || this.giant.getPersistentData().contains(RETREAT)) return false;
                if (this.giant.getRandom().nextInt(ABDUCT_CHANCE) != 0) return false;
                double reach = 1.5D * IceGiantEntity.SCALE;
                this.prey = nearestVillager(this.giant, reach);
                if (this.prey != null && !this.giant.hasLineOfSight(this.prey)) this.prey = null;
            }
            return this.prey != null;
        }

        @Override
        public boolean canContinueToUse() {
            if (!this.giant.isAlive()) return false;
            if (this.carrying) return this.prey.isAlive() && this.prey.getVehicle() == this.giant;
            return this.prey.isAlive() && !this.prey.isPassenger() && this.ticks < SNATCH_SEARCH_TICKS;
        }

        @Override
        public void start() {
            this.ticks = 0;
            this.carrying = false;
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }

        @Override
        public void tick() {
            this.ticks++;
            this.giant.setTarget(null);
            if (!this.carrying) {
                this.giant.getLookControl().setLookAt(this.prey, 30.0F, 30.0F);
                double reach = 1.5D * IceGiantEntity.SCALE;
                if (this.giant.distanceToSqr(this.prey) <= reach * reach && this.prey.startRiding(this.giant, true)) {
                    this.giant.setGrabPhase(IceGiantEntity.GRAB_HOLD);          // in the fist
                    this.giant.swing(InteractionHand.MAIN_HAND);
                    this.giant.level().playSound(null, this.giant.blockPosition(), SoundEvents.VILLAGER_HURT, SoundSource.NEUTRAL, 1.2F, 1.0F);
                    this.carrying = true;
                    this.ticks = 0;
                } else if (this.ticks % 10 == 1) {
                    this.giant.getNavigation().moveTo(this.prey, 1.15D);
                }
                return;
            }
            BlockPos rift = BlockPos.of(this.giant.getPersistentData().getLong(RIFT));
            double reach = 1.5D * IceGiantEntity.SCALE;
            double dx = this.giant.getX() - (rift.getX() + 0.5D), dz = this.giant.getZ() - (rift.getZ() + 0.5D);
            if (dx * dx + dz * dz <= reach * reach) {                            // through the rift with it
                AbstractVillager taken = this.prey;
                this.giant.setGrabPhase(IceGiantEntity.GRAB_NONE);              // (letting go is blocked while gripping)
                taken.stopRiding();
                if (this.giant.level() instanceof net.minecraft.server.level.ServerLevel level) {
                    for (net.minecraft.server.level.ServerPlayer p : level.getEntitiesOfClass(net.minecraft.server.level.ServerPlayer.class,
                            this.giant.getBoundingBox().inflate(96.0D))) {
                        p.displayClientMessage(net.minecraft.network.chat.Component.translatable("message.icegiant.frost_raid_abducted"), false);
                    }
                }
                taken.discard();
                vanish(this.giant);
                return;
            }
            if (this.ticks % 20 == 1) this.giant.getNavigation().moveTo(rift.getX() + 0.5D, rift.getY(), rift.getZ() + 0.5D, 1.15D);
        }

        @Override
        public void stop() {
            if (this.carrying && this.giant.isAlive()) {                          // interrupted: let go
                this.giant.setGrabPhase(IceGiantEntity.GRAB_NONE);
                this.giant.ejectPassengers();
            }
            if (!this.carrying && this.giant.getPersistentData().contains(SNATCH) && this.giant.getPersistentData().contains(RIFT)) {
                // no villager to take: flee empty-handed
                this.giant.getPersistentData().putLong(RETREAT, this.giant.getPersistentData().getLong(RIFT));
            }
            this.carrying = false;
            this.prey = null;
        }
    }

    static AbstractVillager nearestVillager(Mob mob, double range) {
        AbstractVillager best = null;
        double bestD = range * range;
        for (AbstractVillager v : mob.level().getEntitiesOfClass(AbstractVillager.class, mob.getBoundingBox().inflate(range),
                v -> v.isAlive() && !v.isPassenger() && !v.isVehicle())) {
            double d = mob.distanceToSqr(v);
            if (d <= bestD) {
                bestD = d;
                best = v;
            }
        }
        return best;
    }

    // ------------------------------------------------------------------ back through the rift

    static class RetreatGoal extends Goal {
        private final PathfinderMob mob;
        private int ticks;

        RetreatGoal(PathfinderMob mob) {
            this.mob = mob;
            this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK, Flag.TARGET));
        }

        @Override
        public boolean canUse() {
            return isRaider(this.mob) && this.mob.getPersistentData().contains(RETREAT);
        }

        @Override
        public void start() {
            this.ticks = 0;
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }

        @Override
        public void tick() {
            this.mob.setTarget(null);
            BlockPos rift = BlockPos.of(this.mob.getPersistentData().getLong(RETREAT));
            double reach = this.mob instanceof IceGiantEntity ? 1.5D * IceGiantEntity.SCALE : 2.0D;
            double dx = this.mob.getX() - (rift.getX() + 0.5D), dz = this.mob.getZ() - (rift.getZ() + 0.5D);
            if (dx * dx + dz * dz <= reach * reach) {
                vanish(this.mob);
                return;
            }
            if (this.ticks++ % 20 == 0) this.mob.getNavigation().moveTo(rift.getX() + 0.5D, rift.getY(), rift.getZ() + 0.5D, 1.2D);
        }
    }

    // ------------------------------------------------------------------ march to the village

    static class MarchGoal extends Goal {
        private final PathfinderMob mob;
        private int recalc;

        MarchGoal(PathfinderMob mob) {
            this.mob = mob;
            this.setFlags(EnumSet.of(Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            if (!isActiveRaider(this.mob) || this.mob.getTarget() != null) return false;
            return !center(this.mob).closerToCenterThan(this.mob.position(), MARCH_NEAR);
        }

        @Override
        public boolean canContinueToUse() {
            return this.canUse() && !this.mob.getNavigation().isDone();
        }

        @Override
        public void start() {
            this.recalc = 0;
            this.walk();
        }

        @Override
        public void tick() {
            if (++this.recalc % 40 == 0) this.walk();
        }

        private void walk() {
            BlockPos c = center(this.mob);
            RandomSource r = this.mob.getRandom();
            this.mob.getNavigation().moveTo(c.getX() + r.nextInt(9) - 4 + 0.5D, c.getY(), c.getZ() + r.nextInt(9) - 4 + 0.5D, 1.0D);
        }
    }

    // ------------------------------------------------------------------ wreck the village

    private enum Job { DOOR, FARMLAND, FIRE }

    static class WreckGoal extends Goal {
        private final PathfinderMob mob;
        private final boolean canBurn, fireRaiser;
        private int cooldown;
        private Job job;
        private BlockPos at, firePos;
        private int ticks, hits;

        WreckGoal(PathfinderMob mob, boolean canBurn, boolean fireRaiser) {
            this.mob = mob;
            this.canBurn = canBurn;
            this.fireRaiser = fireRaiser;
            this.cooldown = 40 + mob.getRandom().nextInt(80);
            this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            if (this.cooldown > 0) {
                this.cooldown--;
                return false;
            }
            if (!isActiveRaider(this.mob)) return false;
            if (!this.fireRaiser && this.mob.getTarget() != null) return false;     // fighters fight first
            if (!ForgeEventFactory.getMobGriefingEvent(this.mob.level(), this.mob)) return false;
            if (!center(this.mob).closerToCenterThan(this.mob.position(), MAX_FROM_CENTER)) return false;
            this.cooldown = 40;                                                       // (searching is not free)
            return this.findJob();
        }

        @Override
        public boolean canContinueToUse() {
            return this.job != null && this.ticks < GIVE_UP_TICKS && isActiveRaider(this.mob) && this.stillThere();
        }

        @Override
        public void start() {
            this.ticks = 0;
            this.hits = 0;
            this.mob.getNavigation().moveTo(this.at.getX() + 0.5D, this.at.getY(), this.at.getZ() + 0.5D, 1.1D);
        }

        @Override
        public void stop() {
            this.job = null;
            this.mob.getNavigation().stop();
            this.cooldown = WRECK_COOLDOWN_MIN + this.mob.getRandom().nextInt(WRECK_COOLDOWN_MAX - WRECK_COOLDOWN_MIN);
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }

        @Override
        public void tick() {
            // The job is cleared as soon as it is done, but the GoalSelector only checks canContinueToUse()
            // every second tick and still calls tick() in between (requiresUpdateEveryTick): nothing to do then.
            if (this.job == null || this.at == null) return;
            this.ticks++;
            this.mob.getLookControl().setLookAt(this.at.getX() + 0.5D, this.at.getY() + 0.5D, this.at.getZ() + 0.5D);
            double reach = this.mob instanceof IceGiantEntity ? 2.0D * IceGiantEntity.SCALE : 2.5D;
            if (this.mob.distanceToSqr(this.at.getX() + 0.5D, this.at.getY() + 0.5D, this.at.getZ() + 0.5D) > reach * reach) {
                if (this.ticks % 20 == 0) {
                    this.mob.getNavigation().moveTo(this.at.getX() + 0.5D, this.at.getY(), this.at.getZ() + 0.5D, 1.1D);
                }
                return;
            }
            this.mob.getNavigation().stop();
            Level level = this.mob.level();
            switch (this.job) {
                case DOOR -> {                                                      // hack at it, then it breaks
                    if (this.hits % 10 == 0) {
                        this.mob.swing(InteractionHand.MAIN_HAND);
                        level.playSound(null, this.at, SoundEvents.WOOD_HIT, SoundSource.HOSTILE, 1.5F, 0.6F + this.mob.getRandom().nextFloat() * 0.2F);
                    }
                    if (++this.hits >= DOOR_HITS_TICKS || this.mob instanceof IceGiantEntity) {
                        level.destroyBlock(this.at, false, this.mob);
                        level.playSound(null, this.at, SoundEvents.WOOD_BREAK, SoundSource.HOSTILE, 1.5F, 0.7F);
                        this.job = null;
                    }
                }
                case FARMLAND -> {                                                  // trampled: crops gone, dirt
                    this.mob.swing(InteractionHand.MAIN_HAND);
                    BlockPos above = this.at.above();
                    if (level.getBlockState(above).getBlock() instanceof CropBlock) level.destroyBlock(above, false, this.mob);
                    BlockState st = level.getBlockState(this.at);
                    if (st.getBlock() instanceof FarmBlock) FarmBlock.turnToDirt(this.mob, st, level, this.at);
                    // the farmland around it goes too (a Gnoll tramples a whole patch)
                    for (Direction d : Direction.Plane.HORIZONTAL) {
                        BlockPos n = this.at.relative(d);
                        if (this.mob.getRandom().nextBoolean() && level.getBlockState(n).getBlock() instanceof FarmBlock) {
                            if (level.getBlockState(n.above()).getBlock() instanceof CropBlock) level.destroyBlock(n.above(), false, this.mob);
                            FarmBlock.turnToDirt(this.mob, level.getBlockState(n), level, n);
                        }
                    }
                    this.job = null;
                }
                case FIRE -> {                                                      // set the wall alight
                    if (level.getBlockState(this.firePos).isAir() && BaseFireBlock.canBePlacedAt(level, this.firePos, Direction.UP)) {
                        this.mob.swing(InteractionHand.MAIN_HAND);
                        level.setBlock(this.firePos, BaseFireBlock.getState(level, this.firePos), 11);
                        level.playSound(null, this.firePos, SoundEvents.FLINTANDSTEEL_USE, SoundSource.HOSTILE, 1.0F, 0.8F + this.mob.getRandom().nextFloat() * 0.3F);
                    }
                    this.job = null;
                }
            }
        }

        private boolean stillThere() {
            if (this.job == null || this.at == null) return false;
            BlockState st = this.mob.level().getBlockState(this.at);
            return switch (this.job) {
                case DOOR -> st.getBlock() instanceof DoorBlock;
                case FARMLAND -> st.getBlock() instanceof FarmBlock;
                case FIRE -> this.mob.level().getBlockState(this.firePos).isAir();
            };
        }

        /** Looks around for a wooden door, farmland or a flammable wall it can set alight. */
        private boolean findJob() {
            Level level = this.mob.level();
            RandomSource random = this.mob.getRandom();
            BlockPos base = this.mob.blockPosition();
            BlockPos c = center(this.mob);
            List<BlockPos> doors = new ArrayList<>(), farms = new ArrayList<>(), fires = new ArrayList<>();
            List<BlockPos> fireAir = new ArrayList<>();
            BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
            for (int dx = -SEARCH_RADIUS; dx <= SEARCH_RADIUS; dx++) {
                for (int dz = -SEARCH_RADIUS; dz <= SEARCH_RADIUS; dz++) {
                    for (int dy = -SEARCH_HEIGHT; dy <= SEARCH_HEIGHT; dy++) {
                        p.set(base.getX() + dx, base.getY() + dy, base.getZ() + dz);
                        if (!c.closerThan(p, MAX_FROM_CENTER)) continue;
                        BlockState st = level.getBlockState(p);
                        if (st.isAir()) continue;
                        if (st.getBlock() instanceof DoorBlock && st.is(BlockTags.WOODEN_DOORS)
                                && st.getValue(DoorBlock.HALF) == DoubleBlockHalf.LOWER) {
                            doors.add(p.immutable());
                        } else if (st.getBlock() instanceof FarmBlock) {
                            farms.add(p.immutable());
                        } else if (this.canBurn && fires.size() < 64 && st.isFlammable(level, p, Direction.UP)
                                && (st.is(BlockTags.PLANKS) || st.is(BlockTags.LOGS) || st.is(BlockTags.WOOL)
                                || st.is(net.minecraft.world.level.block.Blocks.HAY_BLOCK))) {
                            for (Direction d : Direction.values()) {                    // an open side for the flame
                                BlockPos a = p.relative(d);
                                if (d != Direction.DOWN && level.getBlockState(a).isAir() && BaseFireBlock.canBePlacedAt(level, a, Direction.UP)) {
                                    fires.add(p.immutable());
                                    fireAir.add(a);
                                    break;
                                }
                            }
                        }
                    }
                }
            }
            // what to do: a mix, weighted; fire raisers prefer fire
            double roll = random.nextDouble();
            Job pick;
            if (this.fireRaiser && !fires.isEmpty() && roll < 0.7) pick = Job.FIRE;
            else if (!doors.isEmpty() && roll < 0.4) pick = Job.DOOR;
            else if (!farms.isEmpty() && roll < 0.7) pick = Job.FARMLAND;
            else if (!fires.isEmpty()) pick = Job.FIRE;
            else if (!doors.isEmpty()) pick = Job.DOOR;
            else if (!farms.isEmpty()) pick = Job.FARMLAND;
            else return false;
            this.job = pick;
            switch (pick) {
                case DOOR -> this.at = doors.get(random.nextInt(doors.size()));
                case FARMLAND -> this.at = farms.get(random.nextInt(farms.size()));
                case FIRE -> {
                    int i = random.nextInt(fires.size());
                    this.at = fires.get(i);
                    this.firePos = fireAir.get(i);
                }
            }
            return true;
        }
    }

    /** Keeps the Gnolls of a raid brave (they never lose their nerve while they raid a village). */
    static void encourage(GnollEntity gnoll, LivingEntity enemy) {
        if (enemy != null) gnoll.rally(enemy);
    }
}
