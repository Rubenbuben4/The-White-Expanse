package net.icegiant.item;

import net.icegiant.IceGiantMod;
import net.icegiant.block.FractureBlock;
import net.icegiant.registry.ModEffects;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.icegiant.entity.FlungBlockEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.ForgeEventFactory;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;

/**
 * Ice Giant "wall breaker" dash: a short, straight charge that smashes through
 * walls up to {@link #MAX_WALL_THICKNESS} blocks thick. The smashed blocks are
 * flung to both sides as falling blocks and land on the ground next to the path.
 * <p>
 * Everything scales with the size of whoever dashes: the hole is as wide and as
 * tall as the entity's hitbox, and bigger entities run further and faster. So a
 * player that is scaled up (or morphed into an Ice Giant) for roleplay smashes a
 * giant-sized hole, a normal player a player-sized one.
 * <p>
 * Triggered on purpose only: players press the dash key (J by default) while
 * holding a Nordic Axe or Gravebite; Ice Giants use it when their target is
 * hidden behind a wall (IceDashAttackGoal). A wall that is too thick, too hard
 * (obsidian and harder) or protected stops the dash in front of it.
 * Own implementation - no code from other mods.
 */
@Mod.EventBusSubscriber(modid = IceGiantMod.MOD_ID)
public final class IceDashAbility {

    /** Thickest wall (in blocks, measured along the dash) that can be smashed through. */
    public static final int MAX_WALL_THICKNESS = 4;

    private static final int DASH_TICKS = 12;                // 0.6 s
    private static final double BASE_DISTANCE = 8.0D;        // blocks, normal-sized player
    private static final double DISTANCE_PER_SIZE = 4.0D;    // extra blocks per extra "player height"
    private static final int PLAYER_COOLDOWN_TICKS = 160;    // 8 s
    private static final float MAX_HARDNESS = 50.0F;         // obsidian (50) and harder stop the dash
    private static final double MAX_HOLE_SIZE = 16.0D;       // safety cap for absurdly scaled entities
    private static final int MAX_FALLING_BLOCKS_PER_TICK = 64; // more than that just break and drop
    private static final int MAX_BREAK_EFFECTS_PER_TICK = 8;
    private static final int QUAKE_INTERVAL_TICKS = 10;
    private static final double SAMPLE_STEP = 0.5D;
    /** Extra room cleared above the head where the path steps up, so dashing up slopes doesn't get stuck. */
    private static final double HEADROOM = 1.25D;
    /** Hole shape: upright oval, this much wider (each side) / taller (top) than the hitbox, x sqrt(size). */
    private static final double OVAL_EXTRA_WIDTH = 0.8D;
    private static final double OVAL_EXTRA_HEIGHT = 0.6D;

    /** Players in the way: slam damage (armor-piercing), slammed to the floor, prone for this long. */
    private static final int KNOCKED_DOWN_TICKS = 60;    // 3 s
    /** Crater trail: how far the cracked ground reaches past the hitbox on each side. */
    private static final double TRAIL_EXTRA_WIDTH = 0.5D;
    private static final int MAX_TRAIL_BLOCKS_PER_TICK = 40;

    private static final Map<LivingEntity, Dash> ACTIVE = new WeakHashMap<>();
    private static final Map<UUID, Long> PLAYER_COOLDOWN_UNTIL = new HashMap<>();

    private static final class Dash {
        final Vec3 dir;       // horizontal, normalised
        final double speed;   // blocks per tick
        final double scale;   // size factor (1 = normal player)
        int ticksLeft = DASH_TICKS;
        int ticksSinceQuake = QUAKE_INTERVAL_TICKS;
        final Set<UUID> knockedDown = new HashSet<>(); // players already hit by this dash

        Dash(Vec3 dir, double speed, double scale) {
            this.dir = dir;
            this.speed = speed;
            this.scale = scale;
        }
    }

    private IceDashAbility() {
    }

    // ------------------------------------------------------------------ start

    /** Key press from a player (DashActivatePacket). Weapon + cooldown checks happen here. */
    public static void tryActivate(ServerPlayer player) {
        if (!(player.getMainHandItem().getItem() instanceof IceGeoWeaponItem)) return;
        if (player.isSpectator() || player.isPassenger() || player.isFallFlying() || player.isSleeping()) return;
        if (player.isVehicle()) return; // holding someone (grab)

        long now = player.level().getGameTime();
        Long until = PLAYER_COOLDOWN_UNTIL.get(player.getUUID());
        if (until != null && now < until) {
            int seconds = (int) Math.ceil((until - now) / 20.0D);
            player.displayClientMessage(Component.translatable("message.icegiant.dash_cooldown", seconds), true);
            return;
        }
        Vec3 look = Vec3.directionFromRotation(0.0F, player.getYRot());
        if (startDash(player, look)) {
            PLAYER_COOLDOWN_UNTIL.put(player.getUUID(), now + PLAYER_COOLDOWN_TICKS);
        }
    }

    /**
     * Starts a dash for any living entity in the given (horizontal) direction.
     * No weapon/cooldown checks - callers do those. Returns false if it could not start.
     */
    public static boolean startDash(LivingEntity user, Vec3 direction) {
        if (!(user.level() instanceof ServerLevel level)) return false;
        if (isDashing(user) || NordicAxeSlamAbility.isSlamming(user) || SwordGroundCutAbility.isCutting(user)
                || SwordSweepAbility.isSweeping(user)) return false;
        Vec3 dir = new Vec3(direction.x, 0.0D, direction.z);
        if (dir.lengthSqr() < 1.0E-6D) return false;
        dir = dir.normalize();

        double scale = sizeScale(user);
        double distance = BASE_DISTANCE + DISTANCE_PER_SIZE * (scale - 1.0D);
        ACTIVE.put(user, new Dash(dir, distance / DASH_TICKS, scale));

        if (user instanceof Mob mob) {
            mob.getNavigation().stop();
        }
        faceDirection(user, dir);
        level.playSound(null, user.getX(), user.getY(), user.getZ(), SoundEvents.RAVAGER_ROAR, user.getSoundSource(),
                1.2F, 0.75F + 0.25F / (float) scale);
        NordicAxeSlamAbility.shakeNearbyPlayers(level, user, scale * 0.6D);
        return true;
    }

    public static boolean isDashing(LivingEntity entity) {
        return ACTIVE.containsKey(entity);
    }

    /** Size factor from the hitbox: 1 for a normal player, 3 for the Ice Giant, etc. */
    private static double sizeScale(LivingEntity entity) {
        return Math.max(1.0D, Math.max(entity.getBbHeight() / 1.8D, entity.getBbWidth() / 0.6D));
    }

    // ------------------------------------------------------------------- tick

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || ACTIVE.isEmpty()) return;
        for (Map.Entry<LivingEntity, Dash> entry : new ArrayList<>(ACTIVE.entrySet())) {
            LivingEntity user = entry.getKey();
            Dash dash = entry.getValue();
            if (user == null || !user.isAlive() || user.isRemoved() || !(user.level() instanceof ServerLevel level)) {
                ACTIVE.remove(user);
                continue;
            }
            if (!tickDash(level, user, dash) || --dash.ticksLeft <= 0) {
                stop(user, dash);
            }
        }
    }

    /** One dash step. Returns false when the dash has to end (blocked). */
    private static boolean tickDash(ServerLevel level, LivingEntity user, Dash dash) {
        dash.ticksSinceQuake++;
        Geometry g = new Geometry(user, dash.dir, dash.scale);

        // 1) everything solid in the strip the entity will move through this tick (plus a little lookahead
        //    so the client never bumps into a block the server is about to remove).
        //    A layer that is only blocked at foot level is a step, not a wall: hop over it.
        Scan ahead = g.scan(level, -0.3D, dash.speed + 0.6D);
        boolean hop = false;

        if (ahead.wall.isEmpty()) {
            hop = ahead.step;
            // going up a step inside a tunnel/hill: clear the block above the head first, otherwise
            // the hop bumps the head and the dash gets stuck on slopes
            Set<BlockPos> room = smashable(level, user, ahead.extra);
            if (!room.isEmpty()) smash(level, user, dash, g, room);
        } else {
            // the wall in front of the body (what is left of it once we are inside) must be thin enough,
            // soft enough and not protected - otherwise the dash stops right here
            if (!canSmash(level, user, ahead.wall)
                    || g.wallThicknessAhead(level, dash.speed + 0.6D) > MAX_WALL_THICKNESS) {
                return blocked(level, user, dash);
            }
            // the rest of the upright oval around the body goes too - except blocks that may not be broken
            ahead.wall.addAll(smashable(level, user, ahead.extra));
            smash(level, user, dash, g, ahead.wall);
        }

        hitEntitiesInPath(level, user, dash, g);
        if (user.onGround()) {
            carveTrail(level, user, dash, g);
        }

        // 2) move
        Vec3 motion = user.getDeltaMovement();
        double y = hop && user.onGround() ? 0.45D : motion.y;
        user.setDeltaMovement(dash.dir.x * dash.speed, y, dash.dir.z * dash.speed);
        user.hurtMarked = true; // players move client-side: send them the new motion every tick
        user.fallDistance = 0.0F;
        faceDirection(user, dash.dir);

        // 3) looks: snow dust at the feet, heavy steps
        level.sendParticles(ParticleTypes.SNOWFLAKE, user.getX(), user.getY() + 0.2D, user.getZ(),
                (int) (4 * dash.scale), user.getBbWidth() * 0.4D, 0.1D, user.getBbWidth() * 0.4D, 0.05D);
        if (dash.ticksLeft % 3 == 0 && !(user instanceof net.icegiant.entity.IceGiantEntity)) { // giants: own stomps
            level.playSound(null, user.getX(), user.getY(), user.getZ(), SoundEvents.RAVAGER_STEP, user.getSoundSource(),
                    1.0F, 0.6F);
        }
        return true;
    }

    private static boolean canSmash(ServerLevel level, LivingEntity user, Set<BlockPos> blocks) {
        if (user instanceof Player player) {
            if (!player.getAbilities().mayBuild) return false; // adventure mode
        } else if (!ForgeEventFactory.getMobGriefingEvent(level, user)) {
            return false; // gamerule mobGriefing (or another mod) says no
        }
        for (BlockPos pos : blocks) {
            BlockState state = level.getBlockState(pos);
            float hardness = state.getDestroySpeed(level, pos);
            if (hardness < 0.0F || hardness >= MAX_HARDNESS) return false; // bedrock, obsidian, ...
            if (user instanceof Player player
                    && MinecraftForge.EVENT_BUS.post(new BlockEvent.BreakEvent(level, pos, state, player))) {
                return false; // protected (claims, spawn protection mods, ...)
            }
        }
        return true;
    }

    /** The blocks of {@code blocks} that may be broken one by one (for the oval edge / headroom - never blocks). */
    private static Set<BlockPos> smashable(ServerLevel level, LivingEntity user, Set<BlockPos> blocks) {
        Set<BlockPos> result = new LinkedHashSet<>();
        if (blocks.isEmpty()) return result;
        for (BlockPos pos : blocks) {
            Set<BlockPos> single = Set.of(pos);
            if (canSmash(level, user, single)) result.add(pos);
        }
        return result;
    }

    private static void smash(ServerLevel level, LivingEntity user, Dash dash, Geometry g, Set<BlockPos> blocks) {
        int falling = 0;
        int effects = 0;
        for (BlockPos pos : blocks) {
            BlockState state = level.getBlockState(pos);
            if (effects++ < MAX_BREAK_EFFECTS_PER_TICK) {
                level.levelEvent(2001, pos, Block.getId(state)); // break particles + sound
            }
            if (falling < MAX_FALLING_BLOCKS_PER_TICK && canFall(state)) {
                FlungBlockEntity block = FlungBlockEntity.fling(level, pos, state);
                // hurl it out to the side it was on, upwards and a little forward - it tumbles through the
                // air (FlungBlockRenderer) and lands well beside the path (~5-8 blocks, more for giants)
                Vec3 center = Vec3.atCenterOf(pos);
                double side = center.subtract(user.position()).dot(g.side);
                if (Math.abs(side) < 0.25D) side = level.random.nextBoolean() ? 1.0D : -1.0D;
                double sizeBoost = 1.0D + 0.3D * (dash.scale - 1.0D);
                double sideSpeed = (0.35D + level.random.nextDouble() * 0.25D) * Math.signum(side) * sizeBoost;
                block.setDeltaMovement(
                        g.side.x * sideSpeed + dash.dir.x * dash.speed * 0.2D,
                        0.25D + level.random.nextDouble() * 0.15D,
                        g.side.z * sideSpeed + dash.dir.z * dash.speed * 0.2D);
                block.hurtMarked = true;
                falling++;
            } else {
                level.destroyBlock(pos, true, user); // chests drop their contents, doors/beds break cleanly
            }
        }
        level.playSound(null, user.getX(), user.getY(), user.getZ(), SoundEvents.GENERIC_EXPLODE,
                SoundSource.BLOCKS, 0.5F, 0.55F + level.random.nextFloat() * 0.1F); // heavy crash, no zombie door bash
        Vec3 front = g.point(0.6D, 0.0D, user.getBbHeight() * 0.5D);
        level.sendParticles(ParticleTypes.CLOUD, front.x, front.y, front.z, (int) (6 * dash.scale),
                user.getBbWidth() * 0.5D, user.getBbHeight() * 0.3D, user.getBbWidth() * 0.5D, 0.08D);
        if (dash.ticksSinceQuake >= QUAKE_INTERVAL_TICKS) {
            NordicAxeSlamAbility.shakeNearbyPlayers(level, user, dash.scale);
            dash.ticksSinceQuake = 0;
        }
    }

    /** Simple full blocks fly away; blocks with contents or two halves are broken normally instead. */
    private static boolean canFall(BlockState state) {
        return !state.hasBlockEntity()
                && !state.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF)
                && !state.hasProperty(BlockStateProperties.BED_PART)
                && !state.hasProperty(BlockStateProperties.CHEST_TYPE);
    }

    /**
     * Players in the way are slammed onto the floor: slam damage (armor-piercing, like the axe slam),
     * thrown straight down instead of to the side, and knocked down (prone) for a few seconds - once per dash.
     * Iron golems are rammed: slam damage and thrown back along the dash (once per dash).
     * Other creatures are shoved to the side (no damage).
     */
    static final double GOLEM_RAM_PUSH = 2.0D, GOLEM_RAM_LIFT = 0.6D;    // an iron golem rammed by the dash flies

    private static void hitEntitiesInPath(ServerLevel level, LivingEntity user, Dash dash, Geometry g) {
        AABB box = user.getBoundingBox().expandTowards(dash.dir.scale(dash.speed + 0.5D)).inflate(0.3D, 0.0D, 0.3D);
        for (Entity other : level.getEntities(user, box, e -> e instanceof LivingEntity && e.isAlive()
                && !e.isSpectator() && !(e instanceof Player p && p.isCreative()))) {
            if (other instanceof Player player) {
                if (!dash.knockedDown.add(player.getUUID())) continue; // already lying on the floor
                player.hurt(NordicAxeSlamAbility.slamDamageSource(level, user), NordicAxeSlamAbility.SLAM_DAMAGE);
                // after hurt(): replace its sideways knockback with a slam straight onto the floor
                player.setDeltaMovement(0.0D, -0.6D, 0.0D);
                player.hurtMarked = true;
                player.addEffect(new MobEffectInstance(ModEffects.KNOCKED_DOWN.get(), KNOCKED_DOWN_TICKS, 0,
                        false, false, true));
                level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.PLAYER_ATTACK_KNOCKBACK,
                        SoundSource.PLAYERS, 1.0F, 0.6F);
                level.sendParticles(ParticleTypes.CLOUD, player.getX(), player.getY() + 0.1D, player.getZ(), 10,
                        0.4D, 0.05D, 0.4D, 0.04D);
                continue;
            }
            if (other instanceof net.minecraft.world.entity.animal.IronGolem golem) {
                if (!dash.knockedDown.add(golem.getUUID())) continue;           // one ram per dash
                golem.hurt(NordicAxeSlamAbility.slamDamageSource(level, user), NordicAxeSlamAbility.SLAM_DAMAGE);
                golem.setDeltaMovement(dash.dir.x * GOLEM_RAM_PUSH, GOLEM_RAM_LIFT, dash.dir.z * GOLEM_RAM_PUSH);
                golem.hurtMarked = true;
                level.playSound(null, golem.getX(), golem.getY(), golem.getZ(), SoundEvents.IRON_GOLEM_DAMAGE,
                        SoundSource.HOSTILE, 1.5F, 0.8F);
                level.sendParticles(ParticleTypes.CLOUD, golem.getX(), golem.getY() + 0.5D, golem.getZ(), 14,
                        0.6D, 0.3D, 0.6D, 0.06D);
                continue;
            }
            double side = other.position().subtract(user.position()).dot(g.side);
            double s = side >= 0 ? 1.0D : -1.0D;
            other.setDeltaMovement(other.getDeltaMovement().add(g.side.x * s * 0.9D + dash.dir.x * 0.4D, 0.35D,
                    g.side.z * s * 0.9D + dash.dir.z * 0.4D));
            other.hurtMarked = true;
        }
    }

    /**
     * Crater trail: the ground under the dasher's path cracks and tilts like the slam crater
     * (temporary crater blocks that restore themselves after 12 s), as wide as the hitbox plus a bit.
     */
    private static void carveTrail(ServerLevel level, LivingEntity user, Dash dash, Geometry g) {
        int floorY = Mth.floor(user.getY() - 0.2D);
        double half = g.halfWidth + TRAIL_EXTRA_WIDTH * Math.sqrt(dash.scale);
        int across = Math.max(1, (int) Math.ceil(half * 2.0D / SAMPLE_STEP));
        Set<BlockPos> done = new HashSet<>();
        int placed = 0;
        // from the back of the hitbox to where the dasher will be after this tick, so the trail has no gaps
        for (double f = -g.front; f <= g.front + dash.speed + 1.0E-6D && placed < MAX_TRAIL_BLOCKS_PER_TICK; f += SAMPLE_STEP) {
            for (int i = 0; i <= across && placed < MAX_TRAIL_BLOCKS_PER_TICK; i++) {
                double r = -half + half * 2.0D * i / across;
                Vec3 p = g.origin.add(g.dir.scale(f)).add(g.side.scale(r));
                BlockPos pos = new BlockPos(Mth.floor(p.x), floorY, Mth.floor(p.z));
                if (!done.add(pos)) continue;
                // only real surface blocks: nothing solid on top
                BlockPos above = pos.above();
                if (!level.getBlockState(above).getCollisionShape(level, above).isEmpty()) continue;
                float depth = (float) (1.0D - 0.6D * Math.abs(r) / half);
                Vec3 tilt = Math.abs(r) < 0.3D ? dash.dir : g.side.scale(Math.signum(r)); // edges tip outwards
                if (NordicAxeSlamAbility.fractureGround(level, pos, depth, (float) tilt.x, (float) tilt.z)) {
                    placed++;
                }
            }
        }
        if (placed > 0 && dash.ticksLeft % 2 == 0) {
            level.playSound(null, user.getX(), user.getY(), user.getZ(), SoundEvents.ANVIL_LAND, SoundSource.BLOCKS,
                    0.4F, 0.5F + level.random.nextFloat() * 0.2F);
        }
    }

    private static boolean blocked(ServerLevel level, LivingEntity user, Dash dash) {
        level.playSound(null, user.getX(), user.getY(), user.getZ(), SoundEvents.ANVIL_LAND,
                SoundSource.BLOCKS, 0.8F, 0.4F); // dull iron thud, no zombie door bang
        NordicAxeSlamAbility.shakeNearbyPlayers(level, user, dash.scale * 0.5D);
        return false;
    }

    private static void stop(LivingEntity user, Dash dash) {
        ACTIVE.remove(user);
        Vec3 motion = user.getDeltaMovement();
        user.setDeltaMovement(motion.x * 0.2D, motion.y, motion.z * 0.2D);
        user.hurtMarked = true;
    }

    private static void faceDirection(LivingEntity user, Vec3 dir) {
        if (user instanceof Player) return; // players keep control of their camera
        float yaw = (float) (Mth.atan2(dir.z, dir.x) * (180.0D / Math.PI)) - 90.0F;
        user.setYRot(yaw);
        user.setYHeadRot(yaw);
        user.yBodyRot = yaw;
    }

    // --------------------------------------------------------------- geometry

    /**
     * The dashing entity's cross-section (hitbox width x height) and directions.
     * Blocks are found by sampling points every half block over that cross-section,
     * so it works for any size and any (diagonal) direction.
     */
    private static final class Geometry {
        final Vec3 origin;  // feet position
        final Vec3 dir;     // forward
        final Vec3 side;    // to the right
        final double front; // distance from the centre to the front of the hitbox, along dir
        final double halfWidth;
        final double height;

        final double ovalHalfWidth;  // the hole: an upright oval, a bit wider and taller than the hitbox
        final double ovalHalfHeight;
        final double ovalCentre;     // height of the oval's centre above the feet

        Geometry(LivingEntity user, Vec3 dir, double scale) {
            this.origin = user.position();
            this.dir = dir;
            this.side = new Vec3(-dir.z, 0.0D, dir.x);
            double width = Math.min(user.getBbWidth(), MAX_HOLE_SIZE);
            this.halfWidth = width / 2.0D;
            this.height = Math.min(user.getBbHeight(), MAX_HOLE_SIZE);
            this.front = halfWidth * (Math.abs(dir.x) + Math.abs(dir.z));
            double grow = Math.sqrt(scale);
            this.ovalHalfWidth = halfWidth + OVAL_EXTRA_WIDTH * grow;
            this.ovalHalfHeight = height / 2.0D + OVAL_EXTRA_HEIGHT * grow;
            this.ovalCentre = height / 2.0D + 0.1D;
        }

        Vec3 point(double forward, double right, double up) {
            return origin.add(dir.scale(front + forward)).add(side.scale(right)).add(0.0D, up, 0.0D);
        }

        /**
         * Solid blocks between {@code from} and {@code to} blocks in front of the hitbox. Sample layers
         * (every half block) that are blocked above foot level belong to a wall - all their blocks go into
         * {@code wall}. Layers blocked only at foot level are a step ({@code step = true}).
         */
        Scan scan(ServerLevel level, double from, double to) {
            Scan result = new Scan();
            Set<BlockPos> layer = new LinkedHashSet<>();
            Set<BlockPos> extra = new LinkedHashSet<>();
            for (double f = from; f <= to + 1.0E-6D; f += SAMPLE_STEP) {
                layer.clear();
                extra.clear();
                if (scanLayer(level, f, layer, extra)) {
                    result.wall.addAll(layer);
                } else if (!layer.isEmpty()) {
                    result.step = true;
                }
                result.extra.addAll(extra);
            }
            return result;
        }

        /**
         * Thickness of the first wall ahead, in 1-block layers along the dash (sampled in the middle of
         * each layer). Looks a little past {@link #MAX_WALL_THICKNESS} so "too thick" can be detected.
         */
        int wallThicknessAhead(ServerLevel level, double lookahead) {
            int maxLayers = (int) Math.ceil(lookahead) + MAX_WALL_THICKNESS + 1;
            int thickness = 0;
            Set<BlockPos> scratch = new LinkedHashSet<>();
            for (int k = 0; k < maxLayers; k++) {
                scratch.clear();
                if (scanLayer(level, k + 0.5D, scratch, null)) {
                    thickness++;
                    if (thickness > MAX_WALL_THICKNESS) return thickness;
                } else if (thickness > 0) {
                    break; // came out the other side
                }
            }
            return thickness;
        }

        /**
         * Adds the solid blocks of one cross-section (the hitbox); returns true if any of them is above foot
         * level (= wall). If {@code extraOut} is given, it also collects: for a wall layer the rest of the
         * upright oval around the hitbox (the bigger, rounder hole), for a step layer the row above the head
         * (room to step up a slope). Those extra blocks never count as wall.
         */
        private boolean scanLayer(ServerLevel level, double forward, Set<BlockPos> out, Set<BlockPos> extraOut) {
            double edge = Math.max(0.0D, halfWidth - 0.05D);
            int across = Math.max(1, (int) Math.ceil(edge * 2.0D / SAMPLE_STEP));
            int up = Math.max(1, (int) Math.ceil((height - 0.1D) / SAMPLE_STEP));
            int footRow = Mth.floor(origin.y + 0.05D);
            boolean high = false;
            for (int i = 0; i <= across; i++) {
                double r = -edge + edge * 2.0D * i / across;
                for (int j = 0; j <= up; j++) {
                    double u = 0.05D + (height - 0.1D) * j / up;
                    BlockPos pos = BlockPos.containing(point(forward, r, u));
                    if (out.contains(pos) || !isSolid(level, pos)) continue;
                    out.add(pos);
                    if (pos.getY() > footRow) high = true;
                }
            }
            if (extraOut != null && high) {
                // upright oval: x^2/a^2 + (y-c)^2/b^2 <= 1, cut off flat at the feet (never digs the floor)
                int ovalAcross = Math.max(1, (int) Math.ceil(ovalHalfWidth * 2.0D / SAMPLE_STEP));
                for (int i = 0; i <= ovalAcross; i++) {
                    double r = -ovalHalfWidth + ovalHalfWidth * 2.0D * i / ovalAcross;
                    double t = r / ovalHalfWidth;
                    double halfH = ovalHalfHeight * Math.sqrt(Math.max(0.0D, 1.0D - t * t));
                    double bottom = Math.max(0.05D, ovalCentre - halfH);
                    for (double u = bottom; u <= ovalCentre + halfH + 1.0E-6D; u += SAMPLE_STEP) {
                        BlockPos pos = BlockPos.containing(point(forward, r, u));
                        if (!out.contains(pos) && isSolid(level, pos)) extraOut.add(pos);
                    }
                }
            } else if (extraOut != null && !out.isEmpty()) {
                for (int i = 0; i <= across; i++) {
                    double r = -edge + edge * 2.0D * i / across;
                    for (double u = height + 0.25D; u <= height + HEADROOM + 1.0E-6D; u += SAMPLE_STEP) {
                        BlockPos pos = BlockPos.containing(point(forward, r, u));
                        if (!out.contains(pos) && isSolid(level, pos)) extraOut.add(pos);
                    }
                }
            }
            return high;
        }

        private boolean isSolid(ServerLevel level, BlockPos pos) {
            BlockState state = level.getBlockState(pos);
            if (state.isAir() || state.getBlock() instanceof FractureBlock) return false;
            VoxelShape shape = state.getCollisionShape(level, pos);
            if (shape.isEmpty()) return false; // plants, torches, fluids, portals
            // the floor we stand on (slabs, paths, snow layers reach up to the feet) is not in the way
            return pos.getY() + shape.max(Direction.Axis.Y) > origin.y + 0.05D;
        }
    }

    /** Result of {@link Geometry#scan}. */
    private static final class Scan {
        final Set<BlockPos> wall = new LinkedHashSet<>();
        final Set<BlockPos> extra = new LinkedHashSet<>(); // oval edge around a wall / headroom above a step
        boolean step;
    }
}
