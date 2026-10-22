package net.icegiant.item;

import net.icegiant.IceGiantMod;
import net.icegiant.block.FractureBlock;
import net.icegiant.block.FractureBlockEntity;
import net.icegiant.network.NetworkHandler;
import net.icegiant.network.SlamStartBroadcastPacket;
import net.icegiant.registry.ModEffects;
import net.icegiant.registry.ModSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Giant Rune Sword, key K (Rubenbuben4, 2026-10-17): the swinger strikes the ground like the axe slam (same body
 * pose, charge and lock), but instead of a crater a cut runs from the blade straight ahead along the ground - an aura
 * slash, 1 block per tick, 8 blocks x size (an Ice Giant: 20). The ground along it cracks open (fracture blocks that
 * heal like the crater, the edges tilt away from the line); everything in its path takes armor-piercing damage, is
 * thrown to the side, and players have to crawl for a while (knocked down), mobs are stunned. Works for players
 * (tryActivate) and Ice Giants (startCut, SwordGroundCutGoal). Maths: SwordGeometry.
 */
@Mod.EventBusSubscriber(modid = IceGiantMod.MOD_ID)
public final class SwordGroundCutAbility {

    // ---- TUNE ----
    static final int LOCK_TICKS = 127;                 // like the slam (its body pose lasts 6.3 s)
    static final int SWOOSH_TICK = 40;                 // the blade comes down
    static final int IMPACT_TICK = 47;                 // it hits the ground, the cut starts
    static final int PLAYER_COOLDOWN_TICKS = 160;      // 8 s
    static final float DAMAGE = 6.0F;                  // armor-piercing (icegiant:slam)
    static final double SIDE_PUSH = 1.8D, LIFT = 0.5D; // thrown to the side (x sqrt(size)) and up
    static final int KNOCKED_DOWN_TICKS = 60;          // players crawl for 3 s

    private static final Map<UUID, Long> PLAYER_COOLDOWN_UNTIL = new HashMap<>();
    private static final List<Cut> ACTIVE = new ArrayList<>();
    private static final Map<UUID, Cut> BY_USER = new HashMap<>();

    private static final class Cut {
        final LivingEntity user;
        final double size;
        final ResourceKey<Level> dimension;
        final Vec3 lockPos;
        final Set<UUID> hit = new HashSet<>();
        int age;
        boolean running, ended;
        Vec3 dir, side, start;
        int lastY, step, length, halfWidth;

        Cut(LivingEntity user) {
            this.user = user;
            this.size = SwordGeometry.size(user.getBbWidth());
            this.dimension = user.level().dimension();
            this.lockPos = user.position();
        }
    }

    private SwordGroundCutAbility() {
    }

    /** Key K (GroundCutActivatePacket): sword in hand, nothing else going on, cooldown over. */
    public static void tryActivate(Player player) {
        if (player.level().isClientSide() || !GiantRuneSwordItem.isHeldBy(player)) return;
        if (player.isSpectator() || player.isPassenger() || player.isFallFlying() || player.isSleeping()) return;
        if (isBusy(player)) return;
        long now = player.level().getGameTime();
        long left = SwordGeometry.cooldownLeft(PLAYER_COOLDOWN_UNTIL.get(player.getUUID()), now, PLAYER_COOLDOWN_TICKS);
        if (left > 0) {
            int seconds = (int) Math.ceil(left / 20.0D);
            player.displayClientMessage(Component.translatable("message.icegiant.ground_cut_cooldown", seconds), true);
            return;
        }
        PLAYER_COOLDOWN_UNTIL.put(player.getUUID(), now + PLAYER_COOLDOWN_TICKS);
        startCut(player);
    }

    /** Starts the ground cut for any creature (no weapon / cooldown checks - callers do those). */
    public static void startCut(LivingEntity user) {
        if (user.level().isClientSide() || isBusy(user)) return;
        Cut cut = new Cut(user);
        ACTIVE.add(cut);
        BY_USER.put(user.getUUID(), cut);
        // the slam's body pose (PlayerModelArmMixin) on every client that sees the swinger
        NetworkHandler.INSTANCE.send(PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> user),
                new SlamStartBroadcastPacket(user.getId()));
    }

    /** True from the start until the cut has run out and the swinger is free again. */
    public static boolean isCutting(LivingEntity entity) {
        return BY_USER.containsKey(entity.getUUID());
    }

    /** The blade has hit the ground (or there is no cut): the swinger no longer needs to aim. */
    public static boolean impactDone(LivingEntity entity) {
        Cut cut = BY_USER.get(entity.getUUID());
        return cut == null || cut.age >= IMPACT_TICK;
    }

    /** Busy with a sword or axe ability, or carrying someone: no other ability may start. */
    public static boolean isBusy(LivingEntity entity) {
        return NordicAxeSlamAbility.isSlamming(entity) || IceDashAbility.isDashing(entity) || isCutting(entity)
                || SwordSweepAbility.isSweeping(entity) || entity.isVehicle();
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || ACTIVE.isEmpty()) return;
        Iterator<Cut> it = ACTIVE.iterator();
        while (it.hasNext()) {
            Cut cut = it.next();
            LivingEntity user = cut.user;
            if (!user.isAlive() || user.isRemoved() || user.isPassenger() || user.level().dimension() != cut.dimension
                    || !(user.level() instanceof ServerLevel level)) {               // (grabbed: the carrier holds it now)
                end(it, cut);
                continue;
            }
            if (cut.age < LOCK_TICKS) {                                  // held in place like during the slam
                user.setPos(cut.lockPos.x, cut.lockPos.y, cut.lockPos.z);
                user.setDeltaMovement(Vec3.ZERO);
            }
            if (cut.age < IMPACT_TICK) NordicAxeSlamAbility.spawnChargeParticles(level, user);
            if (cut.age == SWOOSH_TICK) {
                level.playSound(null, user.getX(), user.getY(), user.getZ(), ModSounds.RUNE_SWORD_SLASH.get(),
                        user.getSoundSource(), (float) (1.2D * cut.size), (float) (1.0D / Math.sqrt(cut.size)));
            }
            if (cut.age == IMPACT_TICK) impact(level, cut);
            if (cut.running) advance(level, cut);
            cut.age++;
            if (cut.age >= LOCK_TICKS && !cut.running) end(it, cut);
        }
    }

    private static void end(Iterator<Cut> it, Cut cut) {
        it.remove();
        BY_USER.remove(cut.user.getUUID());
    }

    /** The blade hits the ground in front of the swinger: the cut starts there, straight ahead. */
    private static void impact(ServerLevel level, Cut cut) {
        LivingEntity user = cut.user;
        cut.dir = Vec3.directionFromRotation(0.0F, user.getYRot());
        cut.dir = new Vec3(cut.dir.x, 0.0D, cut.dir.z).normalize();
        cut.side = new Vec3(-cut.dir.z, 0.0D, cut.dir.x);
        cut.start = user.position().add(cut.dir.scale(Math.max(1.0D, cut.size)));
        int feetGround = user.blockPosition().getY() - 1;
        int g = SwordGeometry.nextGroundY(view(level), (int) Math.floor(cut.start.x), (int) Math.floor(cut.start.z), feetGround);
        cut.lastY = g == Integer.MIN_VALUE ? feetGround : g;
        cut.length = SwordGeometry.cutLength(cut.size);
        cut.halfWidth = SwordGeometry.cutHalfWidth(cut.size);
        cut.step = 0;
        cut.running = true;
        Vec3 at = new Vec3(cut.start.x, cut.lastY + 1.0D, cut.start.z);
        level.playSound(null, at.x, at.y, at.z, ModSounds.RUNE_SWORD_GROUND_CUT.get(), user.getSoundSource(),
                (float) (1.2D * cut.size), (float) (1.0D / Math.sqrt(cut.size)));
        NordicAxeSlamAbility.shakeNearbyPlayers(level, user, cut.size);
        NordicAxeSlamAbility.spawnSmokeBurst(level, at);
    }

    /** One tick: the tip runs one block further - the ground cracks, the aura blade flies, whatever it passes is hit. */
    private static void advance(ServerLevel level, Cut cut) {
        double prevTip = cut.step == 0 ? -1.0D : cut.step - 1;          // (the first step also covers the impact spot)
        double tip = cut.step;
        Vec3 tipPos = cut.start.add(cut.dir.scale(tip));
        BlockPos column = BlockPos.containing(tipPos.x, cut.lastY, tipPos.z);
        if (!level.isLoaded(column) || !level.isInWorldBounds(column)          // ran into unloaded land / out of the world
                || !level.getWorldBorder().isWithinBounds(column)) {           // ... or into the world border
            cut.running = false;
            return;
        }
        SwordGeometry.ColumnView view = view(level);
        int ground = SwordGeometry.nextGroundY(view, column.getX(), column.getZ(), cut.lastY);
        if (SwordGeometry.wallAhead(view, column.getX(), column.getZ(), cut.lastY)
                || SwordGeometry.blockedByWall(ground, cut.lastY)) {          // a wall: the cut ends here
            cut.running = false;
            return;
        }
        if (ground != Integer.MIN_VALUE) {                              // (no ground - water, a gap: it flies on)
            cut.lastY = ground;
            crack(level, cut, tipPos, view);
        }
        bladeParticles(level, cut, tipPos);
        hitAlongSlice(level, cut, prevTip, tip);
        cut.step++;
        if (cut.step > cut.length) cut.running = false;
    }

    /** The ground across the line cracks open: deepest in the middle, the edges tilt away from the line. */
    private static void crack(ServerLevel level, Cut cut, Vec3 tipPos, SwordGeometry.ColumnView view) {
        for (int i = -cut.halfWidth; i <= cut.halfWidth; i++) {
            Vec3 p = tipPos.add(cut.side.scale(i));
            int x = (int) Math.floor(p.x), z = (int) Math.floor(p.z);
            int y = i == 0 ? cut.lastY : SwordGeometry.groundY(view, x, z, cut.lastY, 1);
            if (y == Integer.MIN_VALUE) continue;
            float depth = (float) (1.0D - Math.abs(i) / (cut.halfWidth + 1.0D));
            float dirX = i == 0 ? 0.0F : (float) (cut.side.x * Math.signum(i));
            float dirZ = i == 0 ? 0.0F : (float) (cut.side.z * Math.signum(i));
            crackColumn(level, new BlockPos(x, y, z), depth, dirX, dirZ);
        }
    }

    /**
     * One column across the line: its ground block - and the thin cover on it (snow layer, flower) - becomes a crack
     * block that heals again. Top down, so the cover never stands on a crack block by itself (it would drop). The
     * column stays whole if the cover may not crack (tall grass, a sign) or something loose stands on the cover
     * (sugar cane): those would break off for good (SwordGeometry.crackLayers).
     */
    private static void crackColumn(ServerLevel level, BlockPos groundPos, float depth, float dirX, float dirZ) {
        BlockPos coverPos = groundPos.above();
        BlockState cover = level.getBlockState(coverPos);
        BlockState rider = level.getBlockState(coverPos.above());
        int layers = SwordGeometry.crackLayers(crackable(level, groundPos), !cover.isAir(), crackable(level, coverPos),
                !rider.isAir() && !rider.blocksMotion());
        if (layers == 2) NordicAxeSlamAbility.fractureGround(level, coverPos, depth, dirX, dirZ);
        if (layers >= 1) NordicAxeSlamAbility.fractureGround(level, groundPos, depth, dirX, dirZ);
    }

    /**
     * May the cut crack this block? The slam crater's rules (no air, nothing unbreakable, no block with contents) and
     * also: no fluid, no crack block, no half of a door or tall plant (the other half would vanish), and nothing next
     * to water (it would flow into the crack block and wash the block away for good).
     */
    private static boolean crackable(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (state.isAir() || state.getBlock() instanceof FractureBlock || !state.getFluidState().isEmpty()) return false;
        if (state.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF)) return false;
        if (state.getDestroySpeed(level, pos) < 0 || level.getBlockEntity(pos) != null) return false;
        for (Direction d : Direction.Plane.HORIZONTAL) {
            if (!level.getFluidState(pos.relative(d)).isEmpty()) return false;
        }
        return true;
    }

    /** The aura blade at the tip: sweep marks, snow sparks and dust of the ground it cuts. */
    private static void bladeParticles(ServerLevel level, Cut cut, Vec3 tipPos) {
        double y = cut.lastY + 1.0D;
        int n = (int) Math.max(1, Math.round(cut.size));
        level.sendParticles(ParticleTypes.SWEEP_ATTACK, tipPos.x, y + 0.5D * cut.size, tipPos.z, n,
                0.3D * cut.size, 0.2D * cut.size, 0.3D * cut.size, 0.0D);
        level.sendParticles(ParticleTypes.SNOWFLAKE, tipPos.x, y + 0.3D, tipPos.z, 4 * n,
                0.4D * cut.size, 0.3D * cut.size, 0.4D * cut.size, 0.05D);
        BlockState ground = dustState(level, BlockPos.containing(tipPos.x, cut.lastY, tipPos.z));
        if (!ground.isAir()) {
            level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, ground), tipPos.x, y + 0.1D, tipPos.z, 8 * n,
                    0.3D * cut.size, 0.1D, 0.3D * cut.size, 0.15D);
        }
    }

    /** The block whose dust flies here: for a crack block the block it stands in for (it has no texture itself). */
    private static BlockState dustState(ServerLevel level, BlockPos pos) {
        if (level.getBlockEntity(pos) instanceof FractureBlockEntity fracture) {
            return fracture.hasOriginalState() ? fracture.getOriginalState() : Blocks.AIR.defaultBlockState();
        }
        return level.getBlockState(pos);
    }

    /** Everything the tip has just passed: damage, thrown to the side, knocked down (players) or stunned (mobs). */
    private static void hitAlongSlice(ServerLevel level, Cut cut, double prevTip, double tip) {
        double half = SwordGeometry.CUT_HALF_WIDTH * cut.size;
        Vec3 a = cut.start.add(cut.dir.scale(prevTip)), b = cut.start.add(cut.dir.scale(tip));
        AABB box = new AABB(a.x, cut.lastY, a.z, b.x, cut.lastY + 1.0D + SwordGeometry.HIT_HEIGHT * cut.size, b.z)
                .inflate(half + 1.0D, SwordGeometry.HIT_BELOW + 1.0D, half + 1.0D);
        LivingEntity user = cut.user;
        for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, box, LivingEntity::isAlive)) {
            Vec3 rel = target.position().subtract(cut.start);
            double along = rel.x * cut.dir.x + rel.z * cut.dir.z;
            double alongBefore = (target.xo - cut.start.x) * cut.dir.x + (target.zo - cut.start.z) * cut.dir.z;
            double lateral = rel.x * cut.side.x + rel.z * cut.side.z;
            double dy = target.getY() - (cut.lastY + 1.0D);
            // (where it was at the start of this tick too: a target running at the cut must not slip through it)
            if (!SwordGeometry.inCutPath(alongBefore, along, lateral, dy, prevTip, tip, cut.size)) continue;
            if (!mayHit(user, target) || !cut.hit.add(target.getUUID())) continue;
            boolean hurt = target.hurt(NordicAxeSlamAbility.slamDamageSource(level, user), DAMAGE);
            if (!hurt && target instanceof Player) continue;            // (no PvP / protected: no throw either)
            // after hurt(): our throw replaces the small vanilla knockback
            double push = SwordGeometry.sideSign(lateral) * SIDE_PUSH * Math.sqrt(cut.size);
            target.setDeltaMovement(cut.side.x * push, LIFT, cut.side.z * push);
            target.hurtMarked = true;
            if (target instanceof Player player) {
                player.addEffect(new MobEffectInstance(ModEffects.KNOCKED_DOWN.get(), KNOCKED_DOWN_TICKS, 0, false, false, true));
            } else {
                NordicAxeSlamAbility.stunWhenLanded(target);
            }
        }
    }

    /** SwordGeometry.mayHit for real creatures (also used by SwordSweepAbility). */
    static boolean mayHit(LivingEntity user, LivingEntity target) {
        boolean creative = target instanceof Player p && (p.isCreative() || p.isSpectator());
        return SwordGeometry.mayHit(target == user, creative, user instanceof Player,
                target instanceof net.icegiant.entity.IceGiantEntity, target instanceof net.icegiant.entity.gnoll.GnollEntity,
                target instanceof AbstractVillager);
    }

    /**
     * The world as the cut sees it (SwordGeometry.Cell): solid blocks stop it and carry it, leaves do not (the cut
     * runs under trees), fluids are no ground; a crack block counts as the block it stands in for. Unloaded: open.
     */
    private static SwordGeometry.ColumnView view(ServerLevel level) {
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        return (x, y, z) -> {
            pos.set(x, y, z);
            if (!level.isLoaded(pos)) return SwordGeometry.Cell.OPEN;
            BlockState state = level.getBlockState(pos);
            if (state.getBlock() instanceof FractureBlock && level.getBlockEntity(pos) instanceof FractureBlockEntity fracture
                    && fracture.hasOriginalState()) {
                state = fracture.getOriginalState();
            }
            if (state.blocksMotion() && !state.is(BlockTags.LEAVES)) return SwordGeometry.Cell.SOLID;
            if (!state.getFluidState().isEmpty()) return SwordGeometry.Cell.FLUID;
            return SwordGeometry.Cell.OPEN;
        };
    }

    /** A world change or a long break must not leave a cooldown behind (SwordGeometry.cooldownLeft). */
    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        PLAYER_COOLDOWN_UNTIL.remove(event.getEntity().getUUID());
    }
}
