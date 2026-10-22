package net.icegiant.block;

import net.icegiant.client.FrostPortalOverlay;
import net.icegiant.dimension.FrostPortalTeleporter;
import net.icegiant.dimension.FrostPortalTravel;
import net.icegiant.dimension.ModDimensions;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import org.joml.Vector3f;

/**
 * A portal to the "Frozen Realm" dimension - built the same way as a
 * vanilla Nether portal: a rectangular Blue Ice frame of (almost) any size,
 * lit from inside with the Frost Flint and Steel
 * (see {@link net.icegiant.item.FrostFlintAndSteelItem} and
 * {@link net.icegiant.dimension.FrostPortalShape}).
 * Standing inside it (players: for 5 seconds, see FrostPortalTravel) teleports the entity to (or back from) the Frozen
 * Realm, creating a matching return portal (with its own small frame) on
 * the other side if one doesn't already exist nearby.
 */
public class FrostPortalBlock extends Block {

    // Same restricted-to-horizontal-axes property vanilla's NetherPortalBlock
    // uses (only ever X or Z, never Y).
    public static final EnumProperty<Direction.Axis> AXIS = BlockStateProperties.HORIZONTAL_AXIS;

    public FrostPortalBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(AXIS, Direction.Axis.X));
    }

    /** Placed by hand (creative): the portal faces the player. */
    @Override
    public BlockState getStateForPlacement(net.minecraft.world.item.context.BlockPlaceContext context) {
        Direction.Axis facing = context.getHorizontalDirection().getAxis();
        return this.defaultBlockState().setValue(AXIS, facing == Direction.Axis.Z ? Direction.Axis.X : Direction.Axis.Z);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(AXIS);
    }

    @Override
    public void entityInside(BlockState state, Level level, BlockPos pos, Entity entity) {
        if (level.isClientSide) {
            // Local player only: drives the frost screen overlay (client-only class,
            // so it must not be touched directly from common code).
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> FrostPortalOverlay.markInside(entity));
            return;
        }
        // Keep scope simple: only players and other living entities travel through it.
        if (!(entity instanceof LivingEntity)) {
            return;
        }
        if (!entity.canChangeDimensions() || entity.isPassenger() || entity.isVehicle()) {
            return;
        }
        if (entity.isOnPortalCooldown()) {
            // Just arrived / just came back: keep refreshing the cooldown while the
            // entity is still standing in the portal, and don't build up a charge.
            entity.setPortalCooldown();
            FrostPortalTravel.reset(entity);
            return;
        }
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }
        // Nether-portal-style charge-up: players must stand inside for 5 seconds.
        if (!FrostPortalTravel.tickInside(entity, serverLevel.getGameTime())) {
            return;
        }

        MinecraftServer server = serverLevel.getServer();
        net.minecraft.resources.ResourceKey<Level> targetKey =
                serverLevel.dimension() == ModDimensions.FROZEN_REALM
                        ? Level.OVERWORLD
                        : ModDimensions.FROZEN_REALM;
        ServerLevel targetLevel = server.getLevel(targetKey);
        if (targetLevel == null || targetLevel == serverLevel) {
            return;
        }

        // Set the cooldown before teleporting so the entity doesn't
        // immediately bounce back through the portal it lands next to.
        entity.setPortalCooldown();
        serverLevel.playSound(null, pos, SoundEvents.PORTAL_TRAVEL, SoundSource.BLOCKS, 1.0F, 1.0F);
        entity.changeDimension(targetLevel, new FrostPortalTeleporter(pos, state.getValue(AXIS)));
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (random.nextInt(3) != 0) {
            return;
        }
        double x = pos.getX() + random.nextDouble();
        double y = pos.getY() + random.nextDouble();
        double z = pos.getZ() + random.nextDouble();
        // Icy blue dust, drifting slowly upward - gives the portal an ambient shimmer.
        level.addParticle(
                new DustParticleOptions(new Vector3f(0.45f, 0.78f, 1.0f), 1.0f),
                x, y, z,
                0.0D, 0.02D, 0.0D);
    }

    @Override
    public boolean isRandomlyTicking(BlockState state) {
        return true;
    }

    @Override
    public void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (random.nextInt(200) == 0) {
            level.playSound(null, pos, SoundEvents.PORTAL_AMBIENT, SoundSource.BLOCKS,
                    0.5F, 0.9F + random.nextFloat() * 0.2F);
        }
    }
}
