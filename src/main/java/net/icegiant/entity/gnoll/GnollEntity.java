package net.icegiant.entity.gnoll;

import net.icegiant.dimension.ModDimensions;
import net.icegiant.entity.IceGiantEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.ai.goal.target.TargetGoal;
import net.minecraft.world.entity.ai.targeting.TargetingConditions;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.core.object.PlayState;
import software.bernie.geckolib.util.GeckoLibUtil;

import javax.annotation.Nullable;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;

/**
 * Gnoll - small snow creature serving the Ice Giants (melee, archer, shaman).
 * <p>
 * Models, textures, animations and sounds: "Gnolls" entity pack by Hibiscus Studios x AnonymousDevs,
 * used with permission (see CREDITS.md). This Java code is our own; the pack only ships a
 * MythicMobs/ModelEngine config, which served as the behaviour description.
 * <p>
 * Leader: every Gnoll may have a leader - an Ice Giant (escort, 2-5 per giant, spawned in
 * IceGiantEntity#finalizeSpawn) or the first Gnoll of a naturally spawned troop. Gnolls follow
 * the leader, defend it and attack whatever it attacks. Gnolls and Ice Giants never hurt each
 * other (GnollEvents).
 * <p>
 * Actions (attacks, spells, dodges) are timed on the server and sent to the client as one synced
 * int (sequence << 8 | action id), from which the client plays the matching animation and
 * shows/hides model parts (bite jaws, arrow in the hand).
 */
public abstract class GnollEntity extends Monster implements GeoEntity {

    public static final int ACT_NONE = 0;

    private static final EntityDataAccessor<Integer> DATA_ACTION =
            SynchedEntityData.defineId(GnollEntity.class, EntityDataSerializers.INT);
    /** Bits 0-2: which of the optional armour parts (addarmor1..3) are shown. Bit 3: big death. */
    /** Look of the pack: 0 = snow (cold, spawns in the Frozen Realm), 1 = normal, 2 = warm (desert). */
    public static final int VARIANT_SNOW = 0, VARIANT_NORMAL = 1, VARIANT_WARM = 2;
    private static final EntityDataAccessor<Byte> DATA_VARIANT =
            SynchedEntityData.defineId(GnollEntity.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Byte> DATA_LOOK =
            SynchedEntityData.defineId(GnollEntity.class, EntityDataSerializers.BYTE);
    private static final int BIG_DEATH_BIT = 8;

    protected static final RawAnimation IDLE = RawAnimation.begin().thenLoop("idle");
    protected static final RawAnimation IDLE_COMBAT = RawAnimation.begin().thenLoop("idle_b");
    protected static final RawAnimation WALK = RawAnimation.begin().thenLoop("walk");
    protected static final RawAnimation WALK_COMBAT = RawAnimation.begin().thenLoop("walk_b");
    protected static final RawAnimation DEATH = RawAnimation.begin().thenPlayAndHold("death");
    protected static final RawAnimation DEATH_BIG = RawAnimation.begin().thenPlayAndHold("death_bigdamage");
    private static final java.util.Map<String, RawAnimation> ACTION_ANIMS = new java.util.concurrent.ConcurrentHashMap<>();
    private static final RawAnimation[] HURT = {
            RawAnimation.begin().thenPlay("hurt1"), RawAnimation.begin().thenPlay("hurt2"),
            RawAnimation.begin().thenPlay("hurt3"), RawAnimation.begin().thenPlay("hurt4")};

    /** Natural troops: at most this many Gnolls near each other, 1 in N spawn attempts succeed. */
    private static final int MAX_NEARBY_GNOLLS = 10;
    private static final int SPAWN_CHANCE_ONE_IN = 3;

    private final AnimatableInstanceCache geoCache = GeckoLibUtil.createInstanceCache(this);

    // ---- leader ----
    @Nullable private UUID leaderId;
    @Nullable private LivingEntity leader;
    /** Escorts of a giant never despawn (like the giant). */
    private boolean escort;

    // ---- action state (server) ----
    private int actionId = ACT_NONE;
    protected int actionTicks;
    private int actionSeq;

    // ---- action state (client, for animations) ----
    private int clientActionId = ACT_NONE;
    private int clientActionStart;
    private int clientActionSeq;
    private int seenActionSeq = -1;
    private int prevHurtTime;
    private int hurtAnim;
    private int hurtSeq;
    private int seenHurtSeq = -1;

    protected GnollEntity(EntityType<? extends Monster> type, Level level) {
        super(type, level);
        this.xpReward = 6;
    }

    public static AttributeSupplier.Builder baseAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MOVEMENT_SPEED, 0.25D)
                .add(Attributes.FOLLOW_RANGE, 32.0D);
    }

    // =====================================================================
    // actions
    // =====================================================================

    /** Animation name of an action id (as in the model's animation file). */
    protected abstract String actionAnimation(int id);

    /** Length of an action in ticks (the animation length). */
    protected abstract int actionLength(int id);

    /** Called every server tick while an action runs; t = ticks since it started. */
    protected void tickAction(int id, int t) {
    }

    public void startAction(int id) {
        this.actionId = id;
        this.actionTicks = 0;
        this.actionSeq = (this.actionSeq + 1) & 0xFFFFFF;
        this.entityData.set(DATA_ACTION, (this.actionSeq << 8) | id);
    }

    public boolean isBusy() {
        return this.actionId != ACT_NONE;
    }

    public int getActionId() {
        return this.actionId;
    }

    /** Client: the running action and how long it has run (seconds), for model part visibility. */
    public int getClientAction() {
        return this.clientActionId;
    }

    public float getClientActionTime(float partialTick) {
        return (this.tickCount - this.clientActionStart + partialTick) / 20.0F;
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(DATA_ACTION, ACT_NONE);
        this.entityData.define(DATA_LOOK, (byte) 7);
        this.entityData.define(DATA_VARIANT, (byte) VARIANT_SNOW);
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        super.onSyncedDataUpdated(key);
        if (DATA_ACTION.equals(key) && this.level().isClientSide) {
            int v = this.entityData.get(DATA_ACTION);
            this.clientActionId = v & 0xFF;
            this.clientActionSeq = v >>> 8;
            this.clientActionStart = this.tickCount;
        }
    }

    @Override
    public void tick() {
        super.tick();
        if (this.level().isClientSide) {
            if (this.hurtTime > this.prevHurtTime) {        // just got hurt: pick one of the 4 flinches
                this.hurtAnim = this.random.nextInt(HURT.length);
                this.hurtSeq++;
            }
            this.prevHurtTime = this.hurtTime;
        }
    }

    @Override
    protected void customServerAiStep() {
        super.customServerAiStep();
        if (this.actionId != ACT_NONE) {
            int id = this.actionId;
            this.tickAction(id, this.actionTicks);
            if (this.actionId == id && ++this.actionTicks >= this.actionLength(id)) {
                this.actionId = ACT_NONE;
            }
        }
        LivingEntity target = this.getTarget();
        if (target != null && (!target.isAlive() || isFriend(target))) {
            this.setTarget(null);
        }
        if (this.hornCooldown > 0) this.hornCooldown--;
        if (this.tickCount % 10 == 0) this.updateCourage();
        this.setAggressive(this.getTarget() != null);
        if (this.tickCount % 20 == 0) this.refreshLeader();
    }

    // =====================================================================
    // courage: Gnolls only fight as a pack
    // =====================================================================

    /** Gnolls (this one included) that must be close by for the pack to dare a fight. */
    public static final int PACK_COURAGE = 3;
    private static final double PACK_RADIUS = 16.0D, GIANT_RADIUS = 24.0D;
    private boolean fleeing;

    /**
     * With fewer than 3 Gnolls left nearby (and no Ice Giant to protect them) they lose their nerve:
     * drop the target and run away. As soon as 3 or more are together again they fight again.
     */
    private void updateCourage() {
        boolean giantNear = !this.level().getEntitiesOfClass(IceGiantEntity.class,
                this.getBoundingBox().inflate(GIANT_RADIUS), LivingEntity::isAlive).isEmpty();
        int pack = this.level().getEntitiesOfClass(GnollEntity.class, this.getBoundingBox().inflate(PACK_RADIUS),
                k -> k.isAlive() && !k.isDeadOrDying()).size();
        if (this.rallyTicks > 0) this.rallyTicks -= 10;
        boolean flee = !giantNear && pack < PACK_COURAGE && this.rallyTicks <= 0;
        if (flee && !this.fleeing && this.getTarget() != null) this.fleeFrom = this.getTarget();
        this.fleeing = flee;
        if (flee) super.setTarget(null);
    }

    /** Ticks of courage left after an Ice Giant's war cry (they fight even as a small group). */
    private int rallyTicks;
    private static final int RALLY_TICKS = 300;

    /** Called by an Ice Giant's war cry: attack this enemy now. */
    public void rally(LivingEntity enemy) {
        this.rallyTicks = RALLY_TICKS;
        this.fleeing = false;
        if (enemy.isAlive() && !isFriend(enemy)) this.setTarget(enemy);
    }

    public boolean isFleeing() {
        return this.fleeing;
    }

    /** Whoever it was fighting when its nerve broke - it runs from them (and from players). */
    @Nullable private LivingEntity fleeFrom;

    @Override
    public void setTarget(@Nullable LivingEntity target) {
        if (this.fleeing && target != null) return;             // too few to fight: no new targets
        LivingEntity old = this.getTarget();
        super.setTarget(target);
        if (target instanceof Player player && old == null && !this.level().isClientSide()) this.blowWarHorn(player);
    }

    // ---- war horn: a troop without a giant announces a player it has just spotted ----
    private static final int HORN_COOLDOWN = 1200;              // one horn per troop per minute
    private static final double HORN_TROOP_RADIUS = 32.0D, HORN_GIANT_RADIUS = 32.0D;
    private int hornCooldown;

    private void blowWarHorn(Player player) {
        if (this.hornCooldown > 0) return;
        AABB area = this.getBoundingBox().inflate(HORN_TROOP_RADIUS);
        // with a giant around, the giant's war cry does the job
        if (!this.level().getEntitiesOfClass(IceGiantEntity.class, this.getBoundingBox().inflate(HORN_GIANT_RADIUS),
                LivingEntity::isAlive).isEmpty()) return;
        List<GnollEntity> troop = this.level().getEntitiesOfClass(GnollEntity.class, area, LivingEntity::isAlive);
        for (GnollEntity k : troop) k.hornCooldown = HORN_COOLDOWN;   // the whole troop shares the cooldown
        this.playSound(net.icegiant.registry.ModSounds.GNOLL_WAR_HORN.get(), 4.0F, 1.0F);
        for (GnollEntity k : troop) {                          // the troop turns on the player too
            if (k != this && k.getTarget() == null && !k.isFleeing()) k.setTarget(player);
        }
    }

    private boolean isThreat(LivingEntity e) {
        if (e == this.fleeFrom || e == this.getLastHurtByMob()) return true;
        return e instanceof Player p && !p.isCreative() && !p.isSpectator();
    }

    /** Runs from players / its last enemy while the pack is too small (vanilla avoid-goal behaviour). */
    static class FleeGoal extends net.minecraft.world.entity.ai.goal.AvoidEntityGoal<LivingEntity> {
        private final GnollEntity gnoll;

        FleeGoal(GnollEntity gnoll) {
            super(gnoll, LivingEntity.class, 16.0F, 1.15D, 1.4D, e -> gnoll.isThreat(e) && e.isAlive());
            this.gnoll = gnoll;
        }

        @Override
        public boolean canUse() {
            return this.gnoll.isFleeing() && super.canUse();
        }

        @Override
        public boolean canContinueToUse() {
            return this.gnoll.isFleeing() && super.canContinueToUse();
        }
    }

    // =====================================================================
    // leader & friends
    // =====================================================================

    /** Gnolls and Ice Giants are on one side - they never target or hurt each other. */
    public static boolean isFriend(@Nullable Entity e) {
        return e instanceof GnollEntity || e instanceof IceGiantEntity;
    }

    @Override
    public boolean isAlliedTo(Entity other) {
        return isFriend(other) || super.isAlliedTo(other);
    }

    public void setLeader(@Nullable LivingEntity leader) {
        this.leader = leader;
        this.leaderId = leader == null ? null : leader.getUUID();
        if (leader instanceof IceGiantEntity) this.escort = true;
    }

    @Nullable
    public LivingEntity getLeader() {
        if (this.leader != null && !this.leader.isAlive()) this.leader = null;
        return this.leader;
    }

    private void refreshLeader() {
        if (!(this.level() instanceof ServerLevel server)) return;
        if (this.leader == null && this.leaderId != null) {
            Entity e = server.getEntity(this.leaderId);
            if (e instanceof LivingEntity living && living.isAlive()) this.leader = living;
        }
        if (this.leader != null && !this.leader.isAlive()) {
            this.leader = null;
            this.leaderId = null;
        }
        // a troop whose chief is gone (or that never had one) joins a giant nearby that still needs guards
        if (this.leader == null || this.leader instanceof GnollEntity) {
            List<IceGiantEntity> giants = this.level().getEntitiesOfClass(IceGiantEntity.class,
                    this.getBoundingBox().inflate(24.0D), LivingEntity::isAlive);
            for (IceGiantEntity giant : giants) {
                if (countEscorts(giant) < 5) {
                    this.setLeader(giant);
                    break;
                }
            }
        }
    }

    private int countEscorts(LivingEntity giant) {
        return this.level().getEntitiesOfClass(GnollEntity.class, giant.getBoundingBox().inflate(32.0D),
                k -> giant.getUUID().equals(k.leaderId)).size();
    }

    @Override
    public boolean removeWhenFarAway(double distanceToClosestPlayer) {
        return !this.escort && super.removeWhenFarAway(distanceToClosestPlayer);
    }

    // =====================================================================
    // goals
    // =====================================================================

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(1, new FleeGoal(this));          // before any attack goal
        this.registerAttackGoals();
        this.goalSelector.addGoal(4, new FollowLeaderGoal(this));
        this.goalSelector.addGoal(6, new WaterAvoidingRandomStrollGoal(this, 0.8D));
        this.goalSelector.addGoal(7, new LookAtPlayerGoal(this, Player.class, 10.0F));
        this.goalSelector.addGoal(8, new RandomLookAroundGoal(this));

        this.targetSelector.addGoal(1, new HurtByTargetGoal(this, IceGiantEntity.class, GnollEntity.class)
                .setAlertOthers());
        this.targetSelector.addGoal(2, new LeaderTargetGoal(this));
        this.targetSelector.addGoal(3, new NearestAttackableTargetGoal<>(this, Player.class, true));
        // the same monsters an Ice Giant hunts (zombies, skeletons, ...), never giants or Gnolls
        this.targetSelector.addGoal(4, new NearestAttackableTargetGoal<>(this, Monster.class, 10, true, false,
                IceGiantEntity::isHostileMonsterTarget));
    }

    /**
     * Chief of a Gnoll camp: a warrior in full armour with more health and a name above his head.
     */
    public void makeChief() {
        this.entityData.set(DATA_LOOK, (byte) 7);
        net.minecraft.world.entity.ai.attributes.AttributeInstance hp = this.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH);
        if (hp != null) {
            hp.addPermanentModifier(new net.minecraft.world.entity.ai.attributes.AttributeModifier(
                    java.util.UUID.fromString("4f6e1c2a-9b1d-4c7e-8a35-0c2b6d9e7f11"), "Gnoll chief", 1.0D,
                    net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.MULTIPLY_BASE));
            this.setHealth(this.getMaxHealth());
        }
        this.setCustomName(net.minecraft.network.chat.Component.translatable("entity.icegiant.gnoll_chief"));
        this.getPersistentData().putBoolean("icegiant_chief", true);
    }

    /** Frost Raid: hunt villagers, march to the village, wreck it and set it on fire (see RaidGoals). Added once. */
    public void equipRaidGoals() {
        net.icegiant.raid.RaidGoals.addGoals(this, this.goalSelector, this.targetSelector);
    }

    /** Goal priorities 1-3 are free for the type's own attacks. */
    protected abstract void registerAttackGoals();

    /** Walks after the leader (giant or troop chief) when there is nothing to fight. */
    static class FollowLeaderGoal extends Goal {
        private final GnollEntity gnoll;
        private int recalc;

        FollowLeaderGoal(GnollEntity gnoll) {
            this.gnoll = gnoll;
            this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            LivingEntity leader = this.gnoll.getLeader();
            return leader != null && this.gnoll.getTarget() == null && this.gnoll.distanceToSqr(leader) > 8.0D * 8.0D;
        }

        @Override
        public boolean canContinueToUse() {
            LivingEntity leader = this.gnoll.getLeader();
            return leader != null && this.gnoll.getTarget() == null && this.gnoll.distanceToSqr(leader) > 4.0D * 4.0D;
        }

        @Override
        public void start() {
            this.recalc = 0;
        }

        @Override
        public void stop() {
            this.gnoll.getNavigation().stop();
        }

        @Override
        public void tick() {
            LivingEntity leader = this.gnoll.getLeader();
            if (leader == null) return;
            this.gnoll.getLookControl().setLookAt(leader, 10.0F, this.gnoll.getMaxHeadXRot());
            if (--this.recalc <= 0) {
                this.recalc = 10;
                double far = this.gnoll.distanceToSqr(leader);
                if (far > 48.0D * 48.0D) {
                    return; // too far to walk sensibly - wait until the leader comes back
                }
                this.gnoll.getNavigation().moveTo(leader, far > 16.0D * 16.0D ? 1.25D : 1.05D);
            }
        }
    }

    /** Defends the leader (attacks whoever hurt it) and joins its fights. */
    static class LeaderTargetGoal extends TargetGoal {
        private final GnollEntity gnoll;
        private int lastHurtStamp;
        @Nullable private LivingEntity candidate;

        LeaderTargetGoal(GnollEntity gnoll) {
            super(gnoll, false);
            this.gnoll = gnoll;
            this.setFlags(EnumSet.of(Flag.TARGET));
        }

        @Override
        public boolean canUse() {
            LivingEntity leader = this.gnoll.getLeader();
            if (leader == null) return false;
            this.candidate = null;
            LivingEntity attacker = leader.getLastHurtByMob();
            if (attacker != null && leader.getLastHurtByMobTimestamp() != this.lastHurtStamp) {
                this.candidate = attacker;
            } else if (leader instanceof Mob mob && mob.getTarget() != null) {
                this.candidate = mob.getTarget();
            }
            return this.candidate != null && this.candidate != this.gnoll.getTarget()
                    && !isFriend(this.candidate) && this.canAttack(this.candidate, TargetingConditions.DEFAULT);
        }

        @Override
        public void start() {
            LivingEntity leader = this.gnoll.getLeader();
            if (leader != null) this.lastHurtStamp = leader.getLastHurtByMobTimestamp();
            this.mob.setTarget(this.candidate);
            super.start();
        }
    }

    // =====================================================================
    // spawning
    // =====================================================================

    /** Shared by a naturally spawned group: the first Gnoll becomes the chief of the others. */
    public static class TroopData implements SpawnGroupData {
        final LivingEntity chief;

        public TroopData(LivingEntity chief) {
            this.chief = chief;
        }
    }

    @Override
    @Nullable
    public SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty, MobSpawnType reason,
                                        @Nullable SpawnGroupData spawnData, @Nullable CompoundTag dataTag) {
        SpawnGroupData data = super.finalizeSpawn(level, difficulty, reason, spawnData, dataTag);
        RandomSource random = level.getRandom();
        // optional armour pieces: 20 % fully equipped, otherwise each piece 40 %
        byte look = 0;
        boolean full = random.nextFloat() < 0.2F;
        for (int i = 0; i < 3; i++) {
            if (full || random.nextFloat() < 0.4F) look |= (byte) (1 << i);
        }
        this.entityData.set(DATA_LOOK, look);

        if (spawnData instanceof TroopData troop) {
            if (troop.chief != this && troop.chief.isAlive()) this.setLeader(troop.chief);
            return troop;
        }
        if (reason == MobSpawnType.NATURAL || reason == MobSpawnType.CHUNK_GENERATION) {
            // chief of a new troop: brings 1-2 Gnolls of mixed types along
            TroopData troop = new TroopData(this);
            spawnFollowers(level, this, 1 + random.nextInt(2), difficulty, troop);
            return troop;
        }
        return data;
    }

    /** Spawns 2-5 random Gnolls around a freshly spawned Ice Giant. */
    public static void spawnEscort(ServerLevelAccessor level, IceGiantEntity giant, DifficultyInstance difficulty) {
        spawnFollowers(level, giant, 2 + level.getRandom().nextInt(4), difficulty, new TroopData(giant));
    }

    private static void spawnFollowers(ServerLevelAccessor level, LivingEntity leader, int count,
                                       DifficultyInstance difficulty, TroopData group) {
        RandomSource random = level.getRandom();
        IceGiantEntity giant = leader instanceof IceGiantEntity g ? g : null;
        for (int i = 0; i < count; i++) {
            EntityType<? extends GnollEntity> type = randomType(random);
            GnollEntity gnoll = type.create(level.getLevel());
            if (gnoll == null) continue;
            double angle = random.nextDouble() * Math.PI * 2.0D;
            double dist = (giant != null ? 2.5D : 1.5D) + random.nextDouble() * 2.5D;
            int x = (int) Math.floor(leader.getX() + Math.cos(angle) * dist);
            int z = (int) Math.floor(leader.getZ() + Math.sin(angle) * dist);
            int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
            if (Math.abs(y - leader.getY()) > 6) y = (int) Math.floor(leader.getY());
            gnoll.moveTo(x + 0.5D, y, z + 0.5D, random.nextFloat() * 360.0F, 0.0F);
            gnoll.finalizeSpawn(level, difficulty, MobSpawnType.REINFORCEMENT, group, null);
            gnoll.setLeader(leader);
            if (leader instanceof Mob mob && mob.isPersistenceRequired()) gnoll.setPersistenceRequired();
            level.addFreshEntity(gnoll);
        }
    }

    private static EntityType<? extends GnollEntity> randomType(RandomSource random) {
        int r = random.nextInt(10);
        if (r < 5) return net.icegiant.registry.ModEntityTypes.GNOLL_MELEE.get();
        if (r < 8) return net.icegiant.registry.ModEntityTypes.GNOLL_ARCHER.get();
        return net.icegiant.registry.ModEntityTypes.GNOLL_SHAMAN.get();
    }

    /** Natural troops: Frozen Realm only, and not too many at once. */
    public static boolean checkGnollSpawnRules(EntityType<? extends GnollEntity> type, ServerLevelAccessor level,
                                                MobSpawnType reason, BlockPos pos, RandomSource random) {
        if (level.getLevel().dimension() != ModDimensions.FROZEN_REALM) return false;
        if (level.getDifficulty() == Difficulty.PEACEFUL) return false;
        if (reason == MobSpawnType.NATURAL || reason == MobSpawnType.CHUNK_GENERATION) {
            if (random.nextInt(SPAWN_CHANCE_ONE_IN) != 0) return false;
            AABB area = new AABB(pos).inflate(48.0D);
            if (level.getEntitiesOfClass(GnollEntity.class, area).size() >= MAX_NEARBY_GNOLLS) return false;
        }
        return Mob.checkMobSpawnRules(type, level, reason, pos, random);
    }

    // =====================================================================
    // save / look
    // =====================================================================

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        if (this.leaderId != null) tag.putUUID("Leader", this.leaderId);
        tag.putBoolean("Escort", this.escort);
        tag.putByte("Armor", (byte) (this.entityData.get(DATA_LOOK) & 7));
        tag.putByte("Variant", (byte) this.getVariant());
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.hasUUID("Leader")) this.leaderId = tag.getUUID("Leader");
        this.escort = tag.getBoolean("Escort");
        if (tag.contains("Armor")) this.entityData.set(DATA_LOOK, tag.getByte("Armor"));
        if (tag.contains("Variant")) this.setVariant(tag.getByte("Variant"));
    }

    /** Is optional armour piece addarmor{index} (1-3) shown? */
    public int getVariant() {
        return this.entityData.get(DATA_VARIANT);
    }

    /** Spawn eggs set this through their entity data ({Variant:1b}). */
    public void setVariant(int variant) {
        this.entityData.set(DATA_VARIANT, (byte) Math.max(VARIANT_SNOW, Math.min(VARIANT_WARM, variant)));
    }

    public boolean showsArmor(int index) {
        return (this.entityData.get(DATA_LOOK) & (1 << (index - 1))) != 0;
    }

    public boolean isBigDeath() {
        return (this.entityData.get(DATA_LOOK) & BIG_DEATH_BIT) != 0;
    }

    @Override
    public void die(DamageSource source) {
        // a heavy final blow plays the dramatic death animation (+ its own scream)
        if (this.lastHurt >= this.getMaxHealth() * 0.45F) {
            this.entityData.set(DATA_LOOK, (byte) (this.entityData.get(DATA_LOOK) | BIG_DEATH_BIT));
            this.playSound(net.icegiant.registry.ModSounds.GNOLL_DEATH_BIG.get(), 0.8F, 1.0F);
        }
        super.die(source);
    }

    // =====================================================================
    // sounds
    // =====================================================================

    @Override
    @Nullable
    protected SoundEvent getAmbientSound() {
        return this.isAggressive() ? null : net.icegiant.registry.ModSounds.GNOLL_IDLE.get();
    }

    @Override
    public int getAmbientSoundInterval() {
        return 240;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return net.icegiant.registry.ModSounds.GNOLL_HURT.get();
    }

    @Override
    protected SoundEvent getDeathSound() {
        return net.icegiant.registry.ModSounds.GNOLL_DEATH.get();
    }

    @Override
    protected float getSoundVolume() {
        return 0.7F;
    }

    @Override
    public float getVoicePitch() {
        return 0.85F + this.random.nextFloat() * 0.2F;
    }

    // =====================================================================
    // helpers for attacks
    // =====================================================================

    /** Living enemies within radius in front of the Gnoll (cone of fovDegrees). */
    protected List<LivingEntity> enemiesInFront(double radius, double fovDegrees) {
        Vec3 look = Vec3.directionFromRotation(0.0F, this.getYRot());
        double cos = Math.cos(Math.toRadians(fovDegrees / 2.0D));
        return this.level().getEntitiesOfClass(LivingEntity.class, this.getBoundingBox().inflate(radius), e -> {
            if (e == this || !e.isAlive() || isFriend(e)) return false;
            if (e instanceof Player p && (p.isCreative() || p.isSpectator())) return false;
            if (e != this.getTarget() && !(e instanceof Player) && !(e instanceof Monster)) return false;
            Vec3 to = e.position().subtract(this.position());
            double flat = Math.sqrt(to.x * to.x + to.z * to.z);
            if (flat > radius) return false;
            if (flat < 0.8D) return true;
            return (to.x * look.x + to.z * look.z) / flat >= cos;
        });
    }

    // =====================================================================
    // GeckoLib
    // =====================================================================

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "main", 4, state -> {
            if (this.isDeadOrDying()) {
                return state.setAndContinue(this.isBigDeath() ? DEATH_BIG : DEATH);
            }
            boolean combat = this.isAggressive();
            if (state.isMoving()) return state.setAndContinue(combat ? WALK_COMBAT : WALK);
            return state.setAndContinue(combat ? IDLE_COMBAT : IDLE);
        }));
        controllers.add(new AnimationController<>(this, "hurt", 1, state -> {
            if (this.isDeadOrDying() || this.hurtTime <= 0 || this.clientActionId != ACT_NONE
                    && this.getClientActionTime(state.getPartialTick()) * 20.0F < this.actionLength(this.clientActionId)) {
                return PlayState.STOP;
            }
            if (this.seenHurtSeq != this.hurtSeq) {
                this.seenHurtSeq = this.hurtSeq;
                state.getController().forceAnimationReset();
            }
            return state.setAndContinue(HURT[this.hurtAnim]);
        }));
        controllers.add(new AnimationController<>(this, "action", 3, state -> {
            int id = this.clientActionId;
            if (id == ACT_NONE || this.isDeadOrDying()
                    || this.getClientActionTime(state.getPartialTick()) * 20.0F >= this.actionLength(id)) {
                return PlayState.STOP;
            }
            if (this.seenActionSeq != this.clientActionSeq) {
                this.seenActionSeq = this.clientActionSeq;
                state.getController().forceAnimationReset();
            }
            return state.setAndContinue(ACTION_ANIMS.computeIfAbsent(this.actionAnimation(id),
                    name -> RawAnimation.begin().thenPlay(name)));
        }));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return this.geoCache;
    }
}
