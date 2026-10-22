package net.icegiant.raid;

import net.icegiant.entity.IceGiantEntity;
import net.icegiant.entity.gnoll.GnollEntity;
import net.icegiant.registry.ModEntityTypes;
import net.icegiant.registry.ModSounds;
import net.icegiant.block.RaidRiftBlock;
import net.icegiant.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.BossEvent;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * One Frost Raid on a village in the Frozen Realm: three waves, each announced by the war horn and shown in a
 * boss bar ("Frost Raid - wave n/3", filled by the attackers still alive).
 * <ol>
 *   <li>a pack of Gnolls;</li>
 *   <li>Gnolls led by an Ice Giant as their general (he brings his own escort);</li>
 *   <li>two Ice Giants with a big Gnoll army.</li>
 * </ol>
 * Won when the last wave is beaten (Hero of the Village for the players there); lost when no villager is left.
 * Saved with the world (FrostRaidData), so a raid goes on after a restart.
 * <p>
 * <b>Snowy villages in the Overworld</b> ({@code rift}): the attackers come out of a frost rift they tear open
 * beside the village - a frameless round 6 x 6 disc (RaidRiftBlock). When only {@value #RETREAT_VILLAGERS} or fewer
 * villagers are left they withdraw through it (they vanish there) and it closes; it also closes when the raid is
 * beaten or called off. This is how Ice Giants - and with them the items for the frost portal - reach the Overworld.
 * <p>
 * <b>Rout</b> (every wave since 2026-10-22; before only the last one, and only while a giant was left - Rubenbuben4
 * saw a lone giant of wave 2 fight on to the end): when a wave is down to {@value #FLEE_SHARE} of its attackers or
 * fewer, the survivors give up. Giants of a rift raid first grab a villager if one is near and carry it off, everyone
 * else runs back to where they came from (the rift, or the spot they arrived at) and vanishes there. Once they are
 * all gone (or after {@value #RETREAT_MAX_TICKS} ticks) the wave counts as beaten: the next one comes, after the
 * last one the village has won.
 */
public class FrostRaid {

    // ---- TUNE ----
    static final int WAVES = 3;
    static final int FIRST_WAVE_DELAY = 100;           // 5 s after the horn
    static final int WAVE_DELAY = 300;                 // 15 s between waves
    static final int MAX_TICKS = 20 * 60 * 25;         // a raid ends after 25 min whatever happens
    static final double BAR_RANGE = 96.0D;             // players this close see the boss bar
    static final double VILLAGE_RANGE = 64.0D;         // villagers this close to the centre belong to the village
    static final double ABANDON_RANGE = 160.0D;        // no player this close for ABANDON_TICKS: the raid is called off
    static final int ABANDON_TICKS = 20 * 60;
    static final int SPAWN_MIN = 32, SPAWN_MAX = 48;   // the attackers come from this far away
    static final int[] GNOLLS = {6, 5, 8};            // extra Gnolls per wave (giants also bring their own 2-5)
    static final int[] GIANTS = {0, 1, 2};
    static final int HERO_TICKS = 20 * 60 * 40;
    static final int RETREAT_VILLAGERS = 2;            // rift raids: this many villagers left (or fewer) -> withdraw
    static final int RETREAT_MAX_TICKS = 20 * 90;      // stragglers still out after 90 s vanish anyway
    static final int RIFT_SIZE = 6;                    // the rift: a round disc this many blocks across
    static final int RIFT_IN_FRONT = 3;                // the attackers step out this far in front of it
    static final double FLEE_SHARE = 0.4;              // a wave down to this share of its attackers: the survivors flee

    final BlockPos center;
    int wave;                 // 0-based wave being fought (or coming)
    int delay;                // ticks until the next wave arrives (> 0: waiting)
    int age;
    int idle;
    int waveSize;
    final List<UUID> raiders = new ArrayList<>();
    /** Raiders that died or were removed since the last update (filled by FrostRaids.onLeave, emptied each second). */
    final java.util.Set<UUID> fallen = new java.util.HashSet<>();
    boolean done;
    final boolean rift;                     // Overworld: they come through a frost rift
    BlockPos riftBase;                      // bottom middle of the rift (null until it is open)
    Direction.Axis riftAxis = Direction.Axis.X;
    boolean retreating;
    int retreatTicks;
    boolean routed;                         // this wave has given up: its survivors are fleeing (rout)
    int routTicks;
    BlockPos entry;                         // where this wave arrived (non-rift raids flee back there)
    private ServerBossEvent bar;

    FrostRaid(BlockPos center, boolean rift) {
        this.center = center;
        this.rift = rift;
        this.delay = FIRST_WAVE_DELAY;
    }

    // ---- every second ----

    void tick(ServerLevel level, int step) {
        if (this.done) return;
        this.age += step;
        // A dead raider is gone from the level just like an unloaded one (getEntity -> null); only the leave event
        // tells them apart. Drop the dead here so they no longer count as alive.
        if (!this.fallen.isEmpty()) {
            this.raiders.removeAll(this.fallen);
            this.fallen.clear();
        }
        this.updateBar(level);
        boolean playerNear = !level.getEntitiesOfClass(ServerPlayer.class, new AABB(this.center).inflate(ABANDON_RANGE),
                p -> !p.isSpectator()).isEmpty();
        this.idle = playerNear ? 0 : this.idle + step;
        if (this.idle > ABANDON_TICKS || this.age > MAX_TICKS) {
            if (this.rift && this.riftBase != null && !this.retreating) {
                this.startRetreat(level, "message.icegiant.frost_raid_retreat");
                this.retreatTicks = RETREAT_MAX_TICKS;                        // nobody watching: straight back
                this.tickRetreat(level, 0);
                if (!this.done) this.end(level, null);
            } else {
                this.end(level, null);
            }
            return;
        }
        if (this.retreating) {
            this.tickRetreat(level, step);
            return;
        }
        int villagers = this.villagers(level);
        if (this.rift && this.riftBase != null && villagers <= RETREAT_VILLAGERS) {   // their work is done
            this.startRetreat(level, "message.icegiant.frost_raid_retreat");
            return;
        }
        if (villagers == 0) {                                                 // nobody left to defend
            this.end(level, "message.icegiant.frost_raid_lost");
            return;
        }
        if (this.delay > 0) {
            int before = this.delay;
            this.delay -= step;
            if (before == FIRST_WAVE_DELAY && this.wave == 0 || before == WAVE_DELAY) this.horn(level);
            if (this.delay <= 0) this.spawnWave(level);
            return;
        }
        int alive = 0;
        for (UUID id : this.raiders) {
            Entity e = level.getEntity(id);
            if (e == null || (e.isAlive() && RaidGoals.isRaider(e))) alive++;   // (unloaded ones still count; dead ones were dropped above)
        }
        if (this.waveSize > 0) this.bar.setProgress(Math.max(0.0F, Math.min(1.0F, alive / (float) this.waveSize)));
        if (this.routed) {                                                     // the survivors are fleeing
            this.routTicks += step;
            if (this.routTicks >= RETREAT_MAX_TICKS) {                         // stragglers: gone through anyway
                for (UUID id : this.raiders) if (level.getEntity(id) instanceof Mob m && m.isAlive()) RaidGoals.vanish(m);
                this.raiders.clear();                                          // (unloaded ones still head back)
                alive = 0;
            }
        } else if (alive > 0 && alive <= this.waveSize * FLEE_SHARE && this.home() != null) {
            this.rout(level);                                                  // heavy losses: they give up
        }
        // keep the Gnolls brave: they go for the nearest villager
        for (UUID id : this.raiders) {
            if (level.getEntity(id) instanceof GnollEntity k && k.isAlive() && RaidGoals.isActiveRaider(k) && k.getTarget() == null
                    && level.random.nextInt(10) == 0) {
                AbstractVillager v = level.getNearestEntity(AbstractVillager.class,
                        net.minecraft.world.entity.ai.targeting.TargetingConditions.forCombat().ignoreLineOfSight(),
                        k, k.getX(), k.getY(), k.getZ(), k.getBoundingBox().inflate(32.0D));
                RaidGoals.encourage(k, v);
            }
        }
        if (alive == 0) {
            this.wave++;
            this.raiders.clear();
            this.fallen.clear();
            this.routed = false;
            if (this.wave >= WAVES) {
                this.victory(level);
            } else {
                this.delay = WAVE_DELAY;
                this.bar.setProgress(0.0F);
            }
        }
    }

    private void startRetreat(ServerLevel level, String message) {
        this.retreating = true;
        this.retreatTicks = 0;
        BlockPos home = this.riftBase.above(RIFT_SIZE / 2);
        for (UUID id : this.raiders) {
            if (level.getEntity(id) instanceof Mob m && m.isAlive()) RaidGoals.retreat(m, home);
        }
        // raiders of earlier waves still around go back, too
        for (Mob m : level.getEntitiesOfClass(Mob.class, new AABB(this.center).inflate(96.0D), RaidGoals::isActiveRaider)) {
            if (RaidGoals.center(m).closerThan(this.center, 8.0D)) {
                RaidGoals.retreat(m, home);
                if (!this.raiders.contains(m.getUUID())) this.raiders.add(m.getUUID());
            }
        }
        if (this.bar != null) {
            this.bar.setName(Component.translatable("event.icegiant.frost_raid_retreat"));
            for (ServerPlayer p : this.bar.getPlayers()) p.displayClientMessage(Component.translatable(message), false);
        }
    }

    private boolean giantsLeft(ServerLevel level) {
        for (UUID id : this.raiders) if (level.getEntity(id) instanceof IceGiantEntity g && g.isAlive()) return true;
        return false;
    }

    /** Where routed attackers run to: the rift (its middle), or else the spot their wave arrived at. */
    private BlockPos home() {
        if (this.rift && this.riftBase != null) return this.riftBase.above(RIFT_SIZE / 2);
        return this.entry;
    }

    /**
     * The wave gives up (heavy losses): giants of a rift raid snatch a villager and run, everyone else runs back.
     * The raid goes on - the wave counts as beaten once they are gone.
     */
    private void rout(ServerLevel level) {
        this.routed = true;
        this.routTicks = 0;
        BlockPos home = this.home();
        boolean snatch = this.rift && this.riftBase != null && this.giantsLeft(level);
        for (UUID id : this.raiders) {
            if (!(level.getEntity(id) instanceof Mob m) || !m.isAlive()) continue;
            if (snatch && m instanceof IceGiantEntity && m.getPersistentData().contains(RaidGoals.RIFT)) {
                m.getPersistentData().putBoolean(RaidGoals.SNATCH, true);
            } else {
                RaidGoals.retreat(m, home);
            }
            m.setTarget(null);
        }
        if (this.bar != null) {
            this.bar.setName(Component.translatable("event.icegiant.frost_raid_rout", this.wave + 1, WAVES));
            String msg = snatch ? "message.icegiant.frost_raid_flee" : "message.icegiant.frost_raid_rout";
            for (ServerPlayer p : this.bar.getPlayers()) p.displayClientMessage(Component.translatable(msg), false);
        }
    }

    private void tickRetreat(ServerLevel level, int step) {
        this.retreatTicks += step;
        int left = 0;
        for (UUID id : this.raiders) {
            Entity e = level.getEntity(id);
            if (e instanceof Mob m && m.isAlive()) {
                if (this.retreatTicks >= RETREAT_MAX_TICKS) RaidGoals.vanish(m);     // stragglers
                else left++;
            }
        }
        if (this.bar != null && this.waveSize > 0) this.bar.setProgress(Math.min(1.0F, left / (float) this.waveSize));
        if (left == 0) this.end(level, null);
    }

    private int villagers(ServerLevel level) {
        return level.getEntitiesOfClass(AbstractVillager.class, new AABB(this.center).inflate(VILLAGE_RANGE), Entity::isAlive).size();
    }

    private void horn(ServerLevel level) {
        level.playSound(null, this.center, ModSounds.GNOLL_WAR_HORN.get(), SoundSource.HOSTILE, 12.0F, 0.85F);
        for (ServerPlayer p : this.bar.getPlayers()) {
            p.displayClientMessage(Component.translatable(this.wave == 0
                    ? (this.rift ? "message.icegiant.frost_raid_start_rift" : "message.icegiant.frost_raid_start")
                    : "message.icegiant.frost_raid_wave", this.wave + 1, WAVES), true);
        }
    }

    // ---- the attackers ----

    private void spawnWave(ServerLevel level) {
        RandomSource random = level.random;
        BlockPos spot;
        if (this.rift) {
            if (this.riftBase == null) this.openRift(level, this.riftSpot(level, random));
            int dx = Integer.signum(this.center.getX() - this.riftBase.getX()), dz = Integer.signum(this.center.getZ() - this.riftBase.getZ());
            if (this.riftAxis == Direction.Axis.Z) dz = 0; else dx = 0;   // straight out of the disc, towards the village
            int x = this.riftBase.getX() + dx * RIFT_IN_FRONT, z = this.riftBase.getZ() + dz * RIFT_IN_FRONT;
            spot = new BlockPos(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z), z);
            level.sendParticles(net.minecraft.core.particles.ParticleTypes.SNOWFLAKE, this.riftBase.getX() + 0.5D,
                    this.riftBase.getY() + RIFT_SIZE / 2.0D, this.riftBase.getZ() + 0.5D, 120, 2.0D, 2.0D, 2.0D, 0.15D);
        } else {
            spot = this.spawnSpot(level, random);
        }
        List<Mob> spawned = new ArrayList<>();
        for (int i = 0; i < GIANTS[this.wave]; i++) {
            IceGiantEntity giant = ModEntityTypes.ICE_GIANT.get().create(level);
            if (giant != null && this.place(level, giant, spot, random, 3)) spawned.add(giant);
        }
        int gnolls = GNOLLS[this.wave] + random.nextInt(3);
        for (int i = 0; i < gnolls; i++) {
            int r = random.nextInt(10);
            EntityType<? extends GnollEntity> type = r < 5 ? ModEntityTypes.GNOLL_MELEE.get()
                    : r < 8 ? ModEntityTypes.GNOLL_ARCHER.get() : ModEntityTypes.GNOLL_SHAMAN.get();
            GnollEntity k = type.create(level);
            if (k != null && this.place(level, k, spot, random, 5)) spawned.add(k);
        }
        // the giants' own escorts join the raid too
        for (GnollEntity k : level.getEntitiesOfClass(GnollEntity.class, new AABB(spot).inflate(16.0D),
                k -> !RaidGoals.isRaider(k) && k.tickCount < 5)) {
            RaidGoals.mark(k, this.center);
            spawned.add(k);
        }
        for (Mob m : spawned) this.raiders.add(m.getUUID());
        this.waveSize = this.raiders.size();
        this.entry = spot;
        this.routed = false;
        this.routTicks = 0;
        this.bar.setName(Component.translatable("event.icegiant.frost_raid", this.wave + 1, WAVES));
        this.bar.setProgress(1.0F);
    }

    private boolean place(ServerLevel level, Mob mob, BlockPos spot, RandomSource random, int spread) {
        if (this.rift) spread = Math.min(spread, 2);                           // all out of the rift
        int x = spot.getX() + random.nextInt(spread * 2 + 1) - spread, z = spot.getZ() + random.nextInt(spread * 2 + 1) - spread;
        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
        mob.moveTo(x + 0.5D, y, z + 0.5D, random.nextFloat() * 360.0F, 0.0F);
        RaidGoals.mark(mob, this.center);                                    // before it joins: goals are ready
        if (this.rift && this.riftBase != null) mob.getPersistentData().putLong(RaidGoals.RIFT, this.riftBase.above(RIFT_SIZE / 2).asLong());
        mob.finalizeSpawn(level, level.getCurrentDifficultyAt(mob.blockPosition()), MobSpawnType.EVENT, null, null);
        return level.addFreshEntity(mob);
    }

    /** Somewhere 32-48 blocks from the village, on loaded dry ground (not in water). */
    private BlockPos spawnSpot(ServerLevel level, RandomSource random) {
        for (int attempt = 0; attempt < 20; attempt++) {
            double a = random.nextDouble() * Math.PI * 2.0D;
            int d = SPAWN_MIN + random.nextInt(SPAWN_MAX - SPAWN_MIN + 1);
            int x = this.center.getX() + (int) Math.round(Math.cos(a) * d);
            int z = this.center.getZ() + (int) Math.round(Math.sin(a) * d);
            if (!level.hasChunkAt(new BlockPos(x, 0, z))) continue;
            int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
            BlockPos p = new BlockPos(x, y, z);
            if (!level.getFluidState(p.below()).isEmpty()) continue;
            return p;
        }
        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, this.center.getX() + 24, this.center.getZ());
        return new BlockPos(this.center.getX() + 24, y, this.center.getZ());
    }

    // ---- the rift ----

    /** Every block of the rift: a round disc, RIFT_SIZE across, standing on the ground, facing the village. */
    private List<BlockPos> riftBlocks() {
        List<BlockPos> out = new ArrayList<>();
        double mid = (RIFT_SIZE - 1) / 2.0D, r = RIFT_SIZE / 2.0D;
        for (int i = 0; i < RIFT_SIZE; i++) {
            for (int j = 0; j < RIFT_SIZE; j++) {
                double a = i - mid, b = j - mid;
                if (a * a + b * b > r * r) continue;                         // the corners: round
                int off = i - RIFT_SIZE / 2;
                out.add(this.riftAxis == Direction.Axis.X ? this.riftBase.offset(off, j, 0) : this.riftBase.offset(0, j, off));
            }
        }
        return out;
    }

    /** Of a few places around the village, the one where the rift has the most open air (not in a tree). */
    private BlockPos riftSpot(ServerLevel level, RandomSource random) {
        BlockPos best = null;
        int bestFree = -1;
        for (int attempt = 0; attempt < 8; attempt++) {
            BlockPos spot = this.spawnSpot(level, random);
            int dx = this.center.getX() - spot.getX(), dz = this.center.getZ() - spot.getZ();
            this.riftAxis = Math.abs(dx) > Math.abs(dz) ? Direction.Axis.Z : Direction.Axis.X;
            this.riftBase = spot;
            int free = 0;
            for (BlockPos p : this.riftBlocks()) if (level.getBlockState(p).canBeReplaced()) free++;
            if (free > bestFree) {
                bestFree = free;
                best = spot;
            }
        }
        this.riftBase = null;
        return best;
    }

    private void openRift(ServerLevel level, BlockPos spot) {
        int dx = this.center.getX() - spot.getX(), dz = this.center.getZ() - spot.getZ();
        // the disc faces the village: it lies across the way to it
        this.riftAxis = Math.abs(dx) > Math.abs(dz) ? Direction.Axis.Z : Direction.Axis.X;
        this.riftBase = spot;
        BlockState state = ModBlocks.RAID_RIFT.get().defaultBlockState().setValue(RaidRiftBlock.AXIS, this.riftAxis);
        for (BlockPos p : this.riftBlocks()) {
            BlockState old = level.getBlockState(p);
            if (old.isAir() || old.canBeReplaced()) level.setBlock(p, state, 3);
        }
        level.playSound(null, this.riftBase.above(RIFT_SIZE / 2), SoundEvents.END_PORTAL_SPAWN, SoundSource.HOSTILE, 3.0F, 1.4F);
    }

    private void closeRift(ServerLevel level) {
        if (this.riftBase == null) return;
        for (BlockPos p : this.riftBlocks()) {
            if (level.getBlockState(p).getBlock() instanceof RaidRiftBlock) level.setBlock(p, Blocks.AIR.defaultBlockState(), 3);
        }
        level.sendParticles(net.minecraft.core.particles.ParticleTypes.SNOWFLAKE, this.riftBase.getX() + 0.5D,
                this.riftBase.getY() + RIFT_SIZE / 2.0D, this.riftBase.getZ() + 0.5D, 80, 1.5D, 2.0D, 1.5D, 0.05D);
        level.playSound(null, this.riftBase.above(RIFT_SIZE / 2), SoundEvents.BEACON_DEACTIVATE, SoundSource.HOSTILE, 3.0F, 0.6F);
        this.riftBase = null;
    }

    // ---- the end ----

    private void victory(ServerLevel level) {
        for (ServerPlayer p : this.bar.getPlayers()) {
            p.addEffect(new MobEffectInstance(MobEffects.HERO_OF_THE_VILLAGE, HERO_TICKS, 0, false, false, true));
        }
        this.end(level, "message.icegiant.frost_raid_won");
    }

    void end(ServerLevel level, String message) {
        if (this.done) return;
        this.done = true;
        if (this.bar != null) {
            if (message != null) for (ServerPlayer p : this.bar.getPlayers()) p.displayClientMessage(Component.translatable(message), false);
            this.bar.removeAllPlayers();
        }
        for (UUID id : this.raiders) {                                     // survivors stop raiding
            if (level.getEntity(id) instanceof Mob m) RaidGoals.unmark(m);
        }
        this.closeRift(level);
    }

    void removeBar() {
        if (this.bar != null) this.bar.removeAllPlayers();
    }

    private void updateBar(ServerLevel level) {
        if (this.bar == null) {
            this.bar = new ServerBossEvent(Component.translatable("event.icegiant.frost_raid", this.wave + 1, WAVES),
                    BossEvent.BossBarColor.BLUE, BossEvent.BossBarOverlay.NOTCHED_10);
            this.bar.setProgress(0.0F);
        }
        for (ServerPlayer p : level.players()) {
            boolean near = p.isAlive() && p.distanceToSqr(this.center.getX() + 0.5D, this.center.getY(), this.center.getZ() + 0.5D) < BAR_RANGE * BAR_RANGE;
            if (near) this.bar.addPlayer(p);
            else this.bar.removePlayer(p);
        }
    }

    // ---- saving ----

    CompoundTag save() {
        CompoundTag t = new CompoundTag();
        t.put("Center", NbtUtils.writeBlockPos(this.center));
        t.putInt("Wave", this.wave);
        t.putInt("Delay", this.delay);
        t.putInt("Age", this.age);
        t.putInt("WaveSize", this.waveSize);
        ListTag list = new ListTag();
        for (UUID id : this.raiders) list.add(NbtUtils.createUUID(id));
        t.put("Raiders", list);
        t.putBoolean("Rift", this.rift);
        if (this.riftBase != null) {
            t.put("RiftBase", NbtUtils.writeBlockPos(this.riftBase));
            t.putString("RiftAxis", this.riftAxis.getName());
        }
        t.putBoolean("Retreating", this.retreating);
        t.putInt("RetreatTicks", this.retreatTicks);
        t.putBoolean("Routed", this.routed);
        t.putInt("RoutTicks", this.routTicks);
        if (this.entry != null) t.put("Entry", NbtUtils.writeBlockPos(this.entry));
        return t;
    }

    static FrostRaid load(CompoundTag t) {
        FrostRaid r = new FrostRaid(NbtUtils.readBlockPos(t.getCompound("Center")), t.getBoolean("Rift"));
        if (t.contains("RiftBase")) {
            r.riftBase = NbtUtils.readBlockPos(t.getCompound("RiftBase"));
            Direction.Axis axis = Direction.Axis.byName(t.getString("RiftAxis"));
            r.riftAxis = axis == null ? Direction.Axis.X : axis;
        }
        r.retreating = t.getBoolean("Retreating");
        r.retreatTicks = t.getInt("RetreatTicks");
        r.routed = t.getBoolean("Routed");
        r.routTicks = t.getInt("RoutTicks");
        if (t.contains("Entry")) r.entry = NbtUtils.readBlockPos(t.getCompound("Entry"));
        r.wave = t.getInt("Wave");
        r.delay = t.getInt("Delay");
        r.age = t.getInt("Age");
        r.waveSize = t.getInt("WaveSize");
        for (Tag id : t.getList("Raiders", Tag.TAG_INT_ARRAY)) r.raiders.add(NbtUtils.loadUUID(id));
        return r;
    }
}
