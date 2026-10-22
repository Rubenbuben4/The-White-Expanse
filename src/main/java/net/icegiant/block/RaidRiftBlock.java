package net.icegiant.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import org.joml.Vector3f;

/**
 * The frost rift a Frost Raid tears open next to a snowy village: a round 6 x 6 disc of frost-portal light with
 * no frame. Only the raiders come out of it and flee back into it - it does not carry players anywhere (the
 * real way to the Frozen Realm still needs the portal items). It closes when the raid is over. Unbreakable,
 * no drops; same look as the frost portal.
 */
public class RaidRiftBlock extends Block {

    public static final EnumProperty<Direction.Axis> AXIS = BlockStateProperties.HORIZONTAL_AXIS;

    public RaidRiftBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(AXIS, Direction.Axis.X));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(AXIS);
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        double x = pos.getX() + random.nextDouble(), y = pos.getY() + random.nextDouble(), z = pos.getZ() + random.nextDouble();
        if (random.nextInt(2) == 0) {
            level.addParticle(new DustParticleOptions(new Vector3f(0.45f, 0.78f, 1.0f), 1.2f), x, y, z, 0.0D, 0.03D, 0.0D);
        }
        if (random.nextInt(6) == 0) {                                   // snow blowing out of the rift
            boolean alongX = state.getValue(AXIS) == Direction.Axis.X;
            double out = (random.nextBoolean() ? 1 : -1) * (0.05D + random.nextDouble() * 0.1D);
            level.addParticle(ParticleTypes.SNOWFLAKE, x, y, z, alongX ? 0.0D : out, 0.0D, alongX ? out : 0.0D);
        }
    }

    @Override
    public boolean isRandomlyTicking(BlockState state) {
        return true;
    }

    @Override
    public void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (random.nextInt(60) == 0) {
            level.playSound(null, pos, SoundEvents.PORTAL_AMBIENT, SoundSource.BLOCKS, 0.6F, 0.6F + random.nextFloat() * 0.2F);
        }
    }
}
