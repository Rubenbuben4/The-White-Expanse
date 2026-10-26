package net.icegiant.entity;

import net.icegiant.dimension.ModDimensions;
import net.icegiant.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.NeutralMob;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.monster.piglin.AbstractPiglin;
import net.minecraft.world.entity.monster.warden.Warden;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;

/**
 * Ice Giant - a giant humanoid that roams the Frozen Realm.
 * <p>
 * Like vanilla's Giant zombie it has no model of its own: it is rendered with
 * the normal player model (64x64 skin {@code textures/entity/ice_giant.png}),
 * scaled up by {@link #SCALE} (see IceGiantRenderer). It always spawns with one
 * of the three mod axes and the full Ice Giant armor set, fights with normal
 * melee hits and now and then performs the axe slam (see IceSlamAttackGoal /
 * NordicAxeSlamAbility).
 */
public class IceGiantEntity extends Monster {

    /** Render + hitbox scale relative to a player (vanilla Giant = 6). The hitbox is set in ModEntityTypes. */
    public static final float SCALE = 2.5F; // was 3.0 until 2026-10-02 (user: a bit too big)

    // ---- grab & throw: synced phase so the client can pose the arm and place the held creature ----
    public static final byte GRAB_NONE = 0;
    public static final byte GRAB_HOLD = 1;     // arm held forward, victim in the fist
    public static final byte GRAB_WINDUP = 2;   // arm swings up over the head before the throw
    public static final int GRAB_WINDUP_TICKS = 10;
    private static final net.minecraft.network.syncher.EntityDataAccessor<Byte> DATA_GRAB_PHASE =
            net.minecraft.network.syncher.SynchedEntityData.defineId(IceGiantEntity.class,
                    net.minecraft.network.syncher.EntityDataSerializers.BYTE);
    /** Ticks since the grab phase last changed (counted on both sides). */
    private int grabPhaseTicks;

    /** Step height (full block, like a horse) and jump height (clears 2-block obstacles). */
    private static final float STEP_HEIGHT = 1.0F;
    private static final float JUMP_POWER = 0.58F;   // vanilla 0.42 = 1.25 blocks, 0.58 = ~2.2 blocks
    private static final int MAX_JUMP_OBSTACLE = 2;  // blocks

    /** Natural spawning: at most this many giants near each other, and only 1 in N spawn attempts succeeds. */
    private static final int MAX_NEARBY_GIANTS = 1;
    private static final double NEARBY_CHECK_RADIUS = 48.0D;
    private static final int SPAWN_CHANCE_ONE_IN = 8;

    public IceGiantEntity(EntityType<? extends Monster> type, Level level) {
        super(type, level);
        // walks up full blocks without jumping, like a horse
        this.setMaxUpStep(STEP_HEIGHT);
        this.xpReward = 30;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 150.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.27D)
                // base 1.0 like a player -> the held axe adds the rest (12 total)
                .add(Attributes.ATTACK_DAMAGE, 1.0D)
                .add(Attributes.ATTACK_KNOCKBACK, 1.0D)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.8D)
                .add(Attributes.FOLLOW_RANGE, 40.0D)
                // no natural armor - the worn Ice Giant armor provides it
                .add(Attributes.ARMOR, 0.0D);
    }

    /** Set by the spawn egg data {SpawnEscort:1b}; the escort is spawned on the next server tick. */
    private boolean escortPending;

    // ---- war cry (roar): look at the enemy, scream, send every Gnoll nearby after it ----
    /** How long the giant stands and roars = the taunt animation (2.5 s; the sound itself is 3.45 s). */
    public static final int ROAR_TICKS = 50;
    private static final int ROAR_COOLDOWN = 400;          // 20 s between two war cries
    private static final int ROAR_ALERT_TICK = 12;         // the Gnolls react at the peak of the scream
    private static final double ROAR_RALLY_RADIUS = 40.0D;
    private static final int ROAR_PENDING_MAX = 60;         // a cry that had to wait is dropped after 3 s
    /** The roar fades out completely this far away (plus Minecraft's own fading over 64 blocks, sounds.json). */
    static final float ROAR_FALLOFF = 56.0F;
    private static final net.minecraft.network.syncher.EntityDataAccessor<Boolean> DATA_ROARING =
            net.minecraft.network.syncher.SynchedEntityData.defineId(IceGiantEntity.class,
                    net.minecraft.network.syncher.EntityDataSerializers.BOOLEAN);
    private int roarTicks, roarCooldown, roarPendingTicks;
    @Nullable private LivingEntity roarTarget, roarPending;
    /** Client: tick the roar started (for the pose). */
    private int clientRoarStart = -1000;

    public boolean isRoaring() {
        return this.entityData.get(DATA_ROARING);
    }

    /** Client: seconds since the roar started (time in the taunt animation, 2.5 s long). */
    public float getRoarTime(float partialTick) {
        return Math.max(0.0F, (this.tickCount - this.clientRoarStart + partialTick) / 20.0F);
    }

    /** Asks for a war cry against this enemy (first sight of a target, or being hit). */
    public void requestRoar(@Nullable LivingEntity enemy) {
        if (this.level().isClientSide() || enemy == null || !enemy.isAlive() || enemy == this) return;
        if (net.icegiant.entity.gnoll.GnollEntity.isFriend(enemy)) return;
        if (this.roarCooldown > 0 || this.roarTicks > 0) return;
        this.roarPending = enemy;
        this.roarPendingTicks = 0;
    }

    /** Busy with a special attack that must not be cut off (slam, dash, grab, sword cut or sweep)? */
    private boolean isBusyWithSpecialAttack() {
        if (this.isGrabbing() || net.icegiant.item.SwordGroundCutAbility.isBusy(this)) return true;
        return this.goalSelector.getRunningGoals().anyMatch(g -> g.getGoal() instanceof IceSlamAttackGoal
                || g.getGoal() instanceof IceDashAttackGoal || g.getGoal() instanceof IceGrabAttackGoal);
    }

    private void tickRoar() {
        if (this.roarCooldown > 0) this.roarCooldown--;
        if (this.roarPending != null && this.roarTicks == 0) {
            if (!this.roarPending.isAlive() || ++this.roarPendingTicks > ROAR_PENDING_MAX) {
                this.roarPending = null;
            } else if (!this.isBusyWithSpecialAttack()) {
                this.roarTarget = this.roarPending;
                this.roarPending = null;
                this.roarTicks = ROAR_TICKS;
                this.roarCooldown = ROAR_COOLDOWN;
                this.entityData.set(DATA_ROARING, true);
                // (the roar sound is played on the clients - onSyncedDataUpdated - so it can fade with distance)
            }
        }
        if (this.roarTicks > 0) {
            if (ROAR_TICKS - this.roarTicks == ROAR_ALERT_TICK) this.rallyGnolls();
            if (--this.roarTicks == 0) {
                this.entityData.set(DATA_ROARING, false);
                this.roarTarget = null;
            }
        }
    }

    /** Every Gnoll in earshot goes for the enemy - even a small group dares it after the cry. */
    private void rallyGnolls() {
        LivingEntity enemy = this.roarTarget;
        if (enemy == null || !enemy.isAlive()) return;
        for (net.icegiant.entity.gnoll.GnollEntity k : this.level().getEntitiesOfClass(
                net.icegiant.entity.gnoll.GnollEntity.class, this.getBoundingBox().inflate(ROAR_RALLY_RADIUS),
                LivingEntity::isAlive)) {
            k.rally(enemy);
        }
    }

    /** While roaring the giant stands still and stares at its enemy (blocks moving/looking goals). */
    static class RoarGoal extends net.minecraft.world.entity.ai.goal.Goal {
        private final IceGiantEntity giant;

        RoarGoal(IceGiantEntity giant) {
            this.giant = giant;
            this.setFlags(java.util.EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            return this.giant.roarTicks > 0;
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }

        @Override
        public void start() {
            this.giant.getNavigation().stop();
        }

        @Override
        public void tick() {
            this.giant.getNavigation().stop();
            LivingEntity t = this.giant.roarTarget;
            if (t != null) {
                this.giant.getLookControl().setLookAt(t, 30.0F, 30.0F);
                double dx = t.getX() - this.giant.getX(), dz = t.getZ() - this.giant.getZ();
                float yaw = (float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0D);
                this.giant.setYRot(yaw);
                this.giant.yBodyRot = yaw;
            }
        }
    }

    /** Seeing / hearing players (set in registerGoals, which runs before field initialisers - so no initialiser). */
    private GiantSensePlayerGoal senses;

    @Nullable
    public GiantSensePlayerGoal senses() {
        return this.senses;
    }

    @Override
    public void setTarget(@Nullable LivingEntity target) {
        LivingEntity old = this.getTarget();
        super.setTarget(target);
        if (target != null && old == null) this.requestRoar(target);        // first sight of an enemy
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        boolean hurt = super.hurt(source, amount);
        if (hurt && source.getEntity() instanceof LivingEntity attacker) this.requestRoar(attacker);
        if (hurt && source.getEntity() instanceof Player p && this.senses != null) this.senses.setAwareness(p, GiantSensePlayerGoal.ALERT);
        return hurt;
    }

    // ---- dash ability on/off (spawn egg "without dash" sets CanDash:false) ----
    private boolean canDash = true;

    public boolean canDash() {
        return this.canDash;
    }

    public void setCanDash(boolean canDash) {
        this.canDash = canDash;
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putBoolean("CanDash", this.canDash);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.contains("CanDash")) this.canDash = tag.getBoolean("CanDash");
        // one-shot request from the "Ice Giant with escort" spawn egg (never saved again)
        if (tag.getBoolean("SpawnEscort")) this.escortPending = true;
    }

    // ---- footsteps: one with every foot that comes down; heavy running steps while charging / dashing ----
    /**
     * Half a leg swing of the player model in walk-animation units (HumanoidModel swings the legs with
     * cos(position * 0.6662)): each time the walk animation passes another of these, a foot comes down.
     * Until 2026-10-21 there was one sound every 2.7 blocks - Rubenbuben4 saw the giant take about 3 steps
     * per sound. Now the sounds follow the legs, also when he walks slowly or runs.
     */
    private static final float LEG_HALF_SWING = (float) (Math.PI / 0.6662D);
    private static final float STEP_MIN_SPEED = 0.15F;          // slower than this: shuffling on the spot, no sound
    private int stepIndex = Integer.MIN_VALUE;

    /** Vanilla's distance-based footsteps are off (tickFootsteps plays them in time with the legs). */
    @Override
    protected float nextStep() {
        return Float.MAX_VALUE;
    }

    private void tickFootsteps() {
        int index = (int) Math.floor(this.walkAnimation.position() / LEG_HALF_SWING);
        if (index == this.stepIndex) return;
        boolean first = this.stepIndex == Integer.MIN_VALUE;
        this.stepIndex = index;
        if (first || !this.onGround() || this.isInWater() || this.walkAnimation.speed() < STEP_MIN_SPEED) return;
        BlockPos pos = this.getOnPos();
        net.minecraft.world.level.block.state.BlockState state = this.level().getBlockState(pos);
        if (state.isAir()) return;
        if (this.level().isClientSide()) {
            // 2026-10-26: shaking ground, dust at the foot, heard far away but faint, the ground's own sound
            boolean left = (index & 1) == 0, running = this.isAggressive();      // (aggressive = chasing, synced)
            net.minecraftforge.fml.DistExecutor.unsafeRunWhenOn(net.minecraftforge.api.distmarker.Dist.CLIENT,
                    () -> () -> net.icegiant.client.GiantStepEffects.step(this, pos, state, running, left));
        } else {
            this.gameEvent(net.minecraft.world.level.gameevent.GameEvent.STEP);
        }
    }

    /** Chasing a target or smashing through a wall: heavy running stomps (giant-walking-fast recording). */
    private boolean isCharging() {
        return this.getTarget() != null || net.icegiant.item.IceDashAbility.isDashing(this);
    }

    @Override
    protected void playStepSound(BlockPos pos, net.minecraft.world.level.block.state.BlockState state) {
        if (this.isCharging()) {
            this.playSound(net.icegiant.registry.ModSounds.ICE_GIANT_RUN_STEP.get(), 1.6F,
                    0.92F + this.random.nextFloat() * 0.16F);
        } else {
            // just walking around: slow, heavy giant footsteps ("Giant footsteps" recording)
            this.playSound(net.icegiant.registry.ModSounds.ICE_GIANT_WALK_STEP.get(), 1.3F,
                    0.9F + this.random.nextFloat() * 0.15F);
        }
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(0, new RoarGoal(this));
        this.goalSelector.addGoal(1, new IceSlamAttackGoal(this));
        this.goalSelector.addGoal(1, new IceDashAttackGoal(this));
        this.goalSelector.addGoal(1, new IceGrabAttackGoal(this));
        this.goalSelector.addGoal(1, new SwordGroundCutGoal(this));   // Giant Rune Sword only (2026-10-17)
        this.goalSelector.addGoal(1, new SwordSweepGoal(this));
        this.goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.1D, true) /* chase speed x1.1 (was 1.0, 2026-10-02) */);
        this.goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 0.8D));
        // (no LookAtPlayerGoal any more: he would turn to every player near him and see you sneak behind him)
        this.goalSelector.addGoal(7, new RandomLookAroundGoal(this));
        this.targetSelector.addGoal(1, new HurtByTargetGoal(this, net.icegiant.entity.gnoll.GnollEntity.class));
        // players are seen, heard and felt; awareness in stages - you can sneak past him (GiantSensePlayerGoal, 2026-10-26)
        this.senses = new GiantSensePlayerGoal(this);
        this.targetSelector.addGoal(2, this.senses);
        this.goalSelector.addGoal(3, new GiantInvestigateGoal(this, this.senses));
        // Also fights hostile monsters (zombies, skeletons, spiders, creepers ...), but not
        // neutral ones (Endermen, Zombified Piglins, Piglins), bosses or other Ice Giants.
        this.targetSelector.addGoal(3, new NearestAttackableTargetGoal<>(this, Monster.class, 10, true, false,
                IceGiantEntity::isHostileMonsterTarget));
    }

    /** Frost Raid: hunt villagers, march to the village, wreck it (see RaidGoals). Added once. */
    public void equipRaidGoals() {
        net.icegiant.raid.RaidGoals.addGoals(this, this.goalSelector, this.targetSelector);
    }

    /** Monsters the Ice Giant hunts on its own: real hostiles only. */
    public static boolean isHostileMonsterTarget(LivingEntity e) {
        if (!(e instanceof Enemy)) return false;
        if (e instanceof IceGiantEntity) return false;
        if (e instanceof net.icegiant.entity.gnoll.GnollEntity) return false; // its own minions
        if (e instanceof NeutralMob) return false;      // Enderman, Zombified Piglin ...
        if (e instanceof AbstractPiglin) return false;  // Piglin, Piglin Brute
        if (e instanceof WitherBoss || e instanceof Warden) return false;
        return true;
    }

    /** Ice Giants never despawn when the player walks away (only Peaceful still removes them). */
    @Override
    public boolean removeWhenFarAway(double distanceToClosestPlayer) {
        return false;
    }

    // ---- grab & throw (IceGrabAttackGoal) ----

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(DATA_GRAB_PHASE, GRAB_NONE);
        this.entityData.define(DATA_ROARING, false);
    }

    @Override
    public void onSyncedDataUpdated(net.minecraft.network.syncher.EntityDataAccessor<?> key) {
        super.onSyncedDataUpdated(key);
        if (DATA_ROARING.equals(key) && this.entityData.get(DATA_ROARING)) {
            this.clientRoarStart = this.tickCount;
            if (this.level().isClientSide()) {
                // loud close by, clearly quieter further away (Rubenbuben4, 2026-10-26) - FalloffSound
                float pitch = 0.97F + this.random.nextFloat() * 0.06F;
                net.minecraftforge.fml.DistExecutor.unsafeRunWhenOn(net.minecraftforge.api.distmarker.Dist.CLIENT,
                        () -> () -> net.icegiant.client.FalloffSound.play(net.icegiant.registry.ModSounds.ICE_GIANT_ROAR.get(),
                                this.getSoundSource(), this, 1.0F, pitch, ROAR_FALLOFF));
            }
        }
        if (DATA_GRAB_PHASE.equals(key)) this.grabPhaseTicks = 0;
    }

    public byte getGrabPhase() {
        return this.entityData.get(DATA_GRAB_PHASE);
    }

    public void setGrabPhase(byte phase) {
        this.entityData.set(DATA_GRAB_PHASE, phase);
        this.grabPhaseTicks = 0;
    }

    public boolean isGrabbing() {
        return getGrabPhase() != GRAB_NONE;
    }

    // ---- frosty breath: every few seconds a puff of white breath from the mouth (client only) ----
    private int breathCooldown = 40 + (int) (Math.random() * 60);
    private int exhaleTicks;

    private void tickBreath() {
        if (!this.isAlive() || this.isInWater()) return;
        if (this.exhaleTicks > 0) {
            this.exhaleTicks--;
            float yaw = this.yHeadRot;
            float pitch = this.getXRot();
            net.minecraft.world.phys.Vec3 look = net.minecraft.world.phys.Vec3.directionFromRotation(pitch, yaw);
            // mouth: a little below the eyes, at the front of the (scaled) head
            double x = this.getX() + look.x * 0.27D * SCALE;
            double y = this.getY() + this.getEyeHeight() - 0.12D * SCALE + look.y * 0.27D * SCALE;
            double z = this.getZ() + look.z * 0.27D * SCALE;
            for (int i = 0; i < 2; i++) {
                double speed = 0.06D + this.random.nextDouble() * 0.05D;
                this.level().addParticle(net.minecraft.core.particles.ParticleTypes.CLOUD,
                        x + (this.random.nextDouble() - 0.5D) * 0.2D, y + (this.random.nextDouble() - 0.5D) * 0.1D,
                        z + (this.random.nextDouble() - 0.5D) * 0.2D,
                        look.x * speed + (this.random.nextDouble() - 0.5D) * 0.02D,
                        look.y * speed - 0.01D + this.random.nextDouble() * 0.02D,
                        look.z * speed + (this.random.nextDouble() - 0.5D) * 0.02D);
            }
            return;
        }
        if (--this.breathCooldown <= 0) {
            this.exhaleTicks = 8;  // one breath = 0.4 s of puffs
            // calm: every 3.5-6 s; chasing/fighting: heavy breathing every 1.5-2.5 s
            this.breathCooldown = this.isAggressive() ? 30 + this.random.nextInt(20) : 70 + this.random.nextInt(50);
        }
    }

    @Override
    public void tick() {
        if (!this.level().isClientSide() && !this.isDeadOrDying()) this.tickRoar();
        if (!this.level().isClientSide() && !this.isDeadOrDying() && this.senses != null) this.senses.tickSenses();
        if (this.escortPending && this.level() instanceof net.minecraft.server.level.ServerLevel server) {
            this.escortPending = false;
            net.icegiant.entity.gnoll.GnollEntity.spawnEscort(server, this,
                    server.getCurrentDifficultyAt(this.blockPosition()));
        }
        super.tick();
        if (this.level().isClientSide()) this.tickBreath();
        if (!this.isDeadOrDying()) this.tickFootsteps();    // (both sides: the clients do sound, dust and shaking)
        this.grabPhaseTicks++;
        // a held creature without an active grab (e.g. after a world reload) is let go
        if (!this.level().isClientSide() && !isGrabbing() && this.isVehicle()) {
            this.ejectPassengers();
        }
    }

    /**
     * Pitch of the right arm in degrees (model xRot): 0 = hanging down, -90 = straight forward,
     * -170 = over the head. Used by IceGiantModel (pose) and positionRider (where the victim is) -
     * both sides compute it the same way, so the held creature sits exactly in the fist.
     */
    public float getGrabArmPitch(float partialTick) {
        return switch (getGrabPhase()) {
            case GRAB_HOLD -> -90.0F;
            case GRAB_WINDUP -> -90.0F - 80.0F * Math.min(1.0F, (this.grabPhaseTicks + partialTick) / GRAB_WINDUP_TICKS);
            default -> 0.0F;
        };
    }

    /**
     * World position of the right fist (where the axe handle is) for the current grab pose.
     * Same numbers as the player model: shoulder 22 px above the feet and 5 px to the right,
     * fist ~9 px down the arm - all times SCALE.
     */
    public net.minecraft.world.phys.Vec3 getGrabHandPosition(float partialTick) {
        double yaw = Math.toRadians(net.minecraft.util.Mth.lerp(partialTick, this.yBodyRotO, this.yBodyRot));
        net.minecraft.world.phys.Vec3 forward = new net.minecraft.world.phys.Vec3(-Math.sin(yaw), 0.0D, Math.cos(yaw));
        net.minecraft.world.phys.Vec3 right = new net.minecraft.world.phys.Vec3(-Math.cos(yaw), 0.0D, -Math.sin(yaw));
        double pitch = Math.toRadians(getGrabArmPitch(partialTick));
        double arm = 9.0D / 16.0D * SCALE;
        double up = 22.0D / 16.0D * SCALE - Math.cos(pitch) * arm;
        double ahead = -Math.sin(pitch) * arm;
        return this.position().add(0.0D, up, 0.0D).add(right.scale(5.0D / 16.0D * SCALE)).add(forward.scale(ahead));
    }

    /** The held creature hangs in the fist, gripped around the chest. */
    @Override
    protected void positionRider(net.minecraft.world.entity.Entity passenger, MoveFunction move) {
        if (!isGrabbing()) {
            super.positionRider(passenger, move);
            return;
        }
        net.minecraft.world.phys.Vec3 hand = getGrabHandPosition(1.0F);
        move.accept(passenger, hand.x, hand.y - passenger.getBbHeight() * 0.7D, hand.z);
    }

    /** Held creatures dangle (legs down) instead of sitting. */
    @Override
    public boolean shouldRiderSit() {
        return false;
    }

    /** Nobody steers the giant - not a held zombie, not a held player. */
    @Override
    @Nullable
    public LivingEntity getControllingPassenger() {
        return null;
    }

    // ---- movement: full-block steps, 2-block jumps ----

    @Override
    protected float getJumpPower() {
        return JUMP_POWER * this.getBlockJumpFactor();
    }

    /**
     * Vanilla pathfinding only plans climbs up to the step height, so a giant walking into a
     * 2-block obstacle would just push against it. When it bumps into something while it wants
     * to move, and the obstacle in front is at most {@value #MAX_JUMP_OBSTACLE} blocks high,
     * it jumps over it instead of getting stuck.
     */
    @Override
    public void aiStep() {
        super.aiStep();
        if (this.level().isClientSide() || !this.onGround() || !this.horizontalCollision) return;
        if (net.icegiant.item.IceDashAbility.isDashing(this)
                || net.icegiant.item.NordicAxeSlamAbility.isSlamming(this)
                || net.icegiant.item.SwordGroundCutAbility.isCutting(this)) return;
        boolean wantsToMove = this.getNavigation().isInProgress() || this.getMoveControl().hasWanted()
                || this.getTarget() != null;
        if (wantsToMove && obstacleAheadIsJumpable()) {
            this.getJumpControl().jump();
        }
    }

    /** True if the blocks right in front are 1..MAX_JUMP_OBSTACLE high with free space above them. */
    private boolean obstacleAheadIsJumpable() {
        net.minecraft.world.phys.Vec3 forward = net.minecraft.world.phys.Vec3.directionFromRotation(0.0F, this.getYRot());
        net.minecraft.world.phys.Vec3 side = new net.minecraft.world.phys.Vec3(-forward.z, 0.0D, forward.x);
        double half = this.getBbWidth() / 2.0D;
        int feet = net.minecraft.util.Mth.floor(this.getY() + 0.05D);
        int highest = 0;
        for (double r = -half; r <= half + 1.0E-6D; r += Math.max(0.25D, half)) {
            net.minecraft.world.phys.Vec3 p = this.position().add(forward.scale(half + 0.5D)).add(side.scale(r));
            int height = 0;
            for (int dy = 0; dy <= MAX_JUMP_OBSTACLE; dy++) {
                BlockPos pos = BlockPos.containing(p.x, feet + dy + 0.5D, p.z);
                if (this.level().getBlockState(pos).getCollisionShape(this.level(), pos).isEmpty()) break;
                height = dy + 1;
            }
            highest = Math.max(highest, height);
        }
        return highest >= 1 && highest <= MAX_JUMP_OBSTACLE;
    }

    // ---- equipment: one of the three axes + full Ice Giant armor ----

    @Override
    @Nullable
    public SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty, MobSpawnType reason,
                                        @Nullable SpawnGroupData spawnData, @Nullable CompoundTag dataTag) {
        SpawnGroupData data = super.finalizeSpawn(level, difficulty, reason, spawnData, dataTag);
        this.populateDefaultEquipmentSlots(level.getRandom(), difficulty);
        // giants spawning in the world bring 2-5 Gnolls that follow and defend them; from a spawn egg,
        // dispenser or /summon they come alone (the "with escort" egg sets SpawnEscort, see tick)
        if (reason != MobSpawnType.SPAWN_EGG && reason != MobSpawnType.DISPENSER && reason != MobSpawnType.COMMAND) {
            net.icegiant.entity.gnoll.GnollEntity.spawnEscort(level, this, difficulty);
        }
        return data;
    }

    @Override
    protected void populateDefaultEquipmentSlots(RandomSource random, DifficultyInstance difficulty) {
        // one of the four mod weapons, each 1 in 4 (the Giant Rune Sword since 2026-10-17)
        Item[] axes = {ModItems.NORDIC_AXE.get(), ModItems.GRAVEBITE.get(), ModItems.NORDIC_AXE_2.get(), ModItems.GIANT_RUNE_SWORD.get()};
        this.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(axes[random.nextInt(axes.length)]));
        this.setItemSlot(EquipmentSlot.HEAD, new ItemStack(ModItems.ICE_GIANT_HELMET.get()));
        this.setItemSlot(EquipmentSlot.CHEST, new ItemStack(ModItems.ICE_GIANT_CHESTPLATE.get()));
        this.setItemSlot(EquipmentSlot.LEGS, new ItemStack(ModItems.ICE_GIANT_LEGGINGS.get()));
        this.setItemSlot(EquipmentSlot.FEET, new ItemStack(ModItems.ICE_GIANT_BOOTS.get()));
        // the reward is the raw core (loot_tables/entities/ice_giant.json); the armour is never dropped (it is
        // crafted from engraved cores), the weapon sometimes is (2026-10-24) - vanilla Mob drop rules: only when
        // a player killed him, +1 % per Looting level
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            this.setDropChance(slot, 0.0F);
        }
        this.setDropChance(EquipmentSlot.MAINHAND, WEAPON_DROP_CHANCE);
    }

    /** Chance that a giant spawned in the world drops his weapon when a player kills him. */
    static final float WEAPON_DROP_CHANCE = 0.08F;

    /**
     * Creative mode: dress and arm the giant by hand. Right-click with an item: armor goes to its armor slot
     * (helmet, chestplate, leggings, boots), anything else into his weapon hand; sneaking puts the item into his
     * other hand. Right-click with an empty hand takes his weapon away; sneaking with an empty hand takes
     * everything off. The item in your hand is not used up (creative); the giant keeps what he got (saved with
     * him) and still never drops it. His slam and dash only work with one of the mod's ice weapons.
     */
    @Override
    protected net.minecraft.world.InteractionResult mobInteract(Player player, net.minecraft.world.InteractionHand hand) {
        if (!player.getAbilities().instabuild || hand != net.minecraft.world.InteractionHand.MAIN_HAND || !this.isAlive()) {
            return super.mobInteract(player, hand);
        }
        ItemStack held = player.getItemInHand(hand);
        if (held.getItem() instanceof net.minecraft.world.item.SpawnEggItem) return super.mobInteract(player, hand);
        if (!this.level().isClientSide) {
            net.minecraft.network.chat.Component msg;
            if (held.isEmpty()) {
                if (player.isShiftKeyDown()) {
                    for (EquipmentSlot slot : EquipmentSlot.values()) this.setItemSlot(slot, ItemStack.EMPTY);
                    msg = net.minecraft.network.chat.Component.translatable("message.icegiant.giant_unequip_all");
                } else {
                    this.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
                    msg = net.minecraft.network.chat.Component.translatable("message.icegiant.giant_unequip_weapon");
                }
            } else {
                EquipmentSlot slot = player.isShiftKeyDown() ? EquipmentSlot.OFFHAND : Mob.getEquipmentSlotForItem(held);
                this.setItemSlot(slot, held.copyWithCount(1));
                msg = net.minecraft.network.chat.Component.translatable("message.icegiant.giant_equip", held.getHoverName());
            }
            for (EquipmentSlot slot : EquipmentSlot.values()) this.setDropChance(slot, 0.0F);   // never dropped
            this.setPersistenceRequired();                                                      // keep the dressed giant
            this.playSound(SoundEvents.ARMOR_EQUIP_GENERIC, 1.0F, 0.8F);
            player.displayClientMessage(msg, true);
        }
        return net.minecraft.world.InteractionResult.sidedSuccess(this.level().isClientSide);
    }

    // ---- natural spawning (Frozen Realm only, see IceGiantMod#registerSpawnPlacements) ----

    public static boolean checkIceGiantSpawnRules(EntityType<IceGiantEntity> type, ServerLevelAccessor level,
                                                  MobSpawnType reason, BlockPos pos, RandomSource random) {
        if (level.getLevel().dimension() != ModDimensions.FROZEN_REALM) {
            return false; // the same snow biomes exist in the Overworld - giants only live in the Frozen Realm
        }
        if (level.getDifficulty() == Difficulty.PEACEFUL) {
            return false;
        }
        if (reason == MobSpawnType.NATURAL || reason == MobSpawnType.CHUNK_GENERATION) {
            if (random.nextInt(SPAWN_CHANCE_ONE_IN) != 0) {
                return false;
            }
            AABB area = new AABB(pos).inflate(NEARBY_CHECK_RADIUS);
            if (level.getEntitiesOfClass(IceGiantEntity.class, area).size() >= MAX_NEARBY_GIANTS) {
                return false;
            }
        }
        // The Frozen Realm is always daytime, so no darkness check (unlike normal monsters).
        return Mob.checkMobSpawnRules(type, level, reason, pos, random);
    }

    // ---- sounds: only the giant's own voice (hurt, roar, death) - no zombie groans ----

    /** No idle groaning: the giant is silent between his roars (the old zombie groan is gone). */
    @Override
    protected SoundEvent getAmbientSound() {
        return null;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return net.icegiant.registry.ModSounds.ICE_GIANT_HURT.get(); // real giant pain sounds (8 variants)
    }

    /** The pain sounds are already a giant's voice: play them at normal pitch, not the deep zombie pitch. */
    @Override
    protected void playHurtSound(DamageSource source) {
        SoundEvent sound = this.getHurtSound(source);
        if (sound != null) {
            this.playSound(sound, this.getSoundVolume(), 0.95F + this.random.nextFloat() * 0.1F);
        }
    }

    // ---- death: Rubenbuben4's "dying_1" animation - falls on his back, crater, thump, earthquake ----

    /** How long the dead giant stays (fall 1.6 s + lying there), and when his back hits the ground. */
    public static final int DEATH_TICKS = 110;
    public static final int DEATH_IMPACT_TICK = 30;           // 1.49 s in the animation
    // (crater: GiantDeath.CRATER_LENGTH / CRATER_HALF_WIDTH - 3 x 6 blocks behind his feet)

    /** The cry is played here (not as the normal death sound, which would get the deep zombie pitch). */
    @Override
    protected SoundEvent getDeathSound() {
        return null;
    }

    @Override
    public void die(DamageSource source) {
        boolean wasAlive = !this.isRemoved() && !this.dead;
        super.die(source);
        if (wasAlive && !this.level().isClientSide()) {
            this.ejectPassengers();                                          // let go of anyone in his fist
            this.playSound(net.icegiant.registry.ModSounds.ICE_GIANT_DEATH.get(), 3.0F, 1.0F);
        }
    }

    /** Instead of vanilla's 1 second tip-over: the long fall, the impact, then a few seconds lying there. */
    @Override
    protected void tickDeath() {
        ++this.deathTime;
        if (this.level().isClientSide()) return;
        if (this.deathTime == DEATH_IMPACT_TICK) this.deathImpact();
        if (this.deathTime >= DEATH_TICKS && !this.isRemoved()) {
            this.level().broadcastEntityEvent(this, (byte) 60);              // poof
            this.remove(RemovalReason.KILLED);
        }
    }

    /** His back hits the ground: thump, a 3 x 6 temporary crater where he lies, dust and an earthquake. */
    private void deathImpact() {
        if (this.level() instanceof net.minecraft.server.level.ServerLevel level) GiantDeath.impact(level, this, SCALE);
    }

    @Override
    public float getVoicePitch() {
        return 0.55F + (this.random.nextFloat() - 0.5F) * 0.1F;
    }

    @Override
    protected float getSoundVolume() {
        return 1.6F;
    }
}
