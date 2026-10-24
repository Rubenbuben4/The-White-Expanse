package net.icegiant.block;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.BushBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Snowy grass and fern of the Frozen Realm (2026-10-24): like vanilla grass, with snow on it (textures from Better
 * Snowy Features by zzzaitegui, MIT - see CREDITS.md). Not tinted by the biome. The world swaps vanilla grass / fern
 * for these (SnowyDecor). With SNOW it stands in a snow layer (one of 11 slightly different looks, so no random
 * offset - that would move the snow too), set by SnowyDecor where the ground around it has snow.
 */
public class SnowyPlantBlock extends BushBlock {

    private static final VoxelShape SHAPE = Block.box(2.0D, 0.0D, 2.0D, 14.0D, 13.0D, 14.0D);

    /** Stands in a snow layer (the model then has the snow at its foot). */
    public static final BooleanProperty SNOW = BooleanProperty.create("snow");

    public SnowyPlantBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(SNOW, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<net.minecraft.world.level.block.Block, BlockState> builder) {
        builder.add(SNOW);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        Vec3 offset = state.getOffset(level, pos);
        return SHAPE.move(offset.x, offset.y, offset.z);
    }
}
