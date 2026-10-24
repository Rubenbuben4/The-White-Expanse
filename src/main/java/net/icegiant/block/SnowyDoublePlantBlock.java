package net.icegiant.block;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DoublePlantBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;

/**
 * Snowy tall grass and large fern of the Frozen Realm (2026-10-24): a vanilla double plant with the SNOW look of
 * SnowyPlantBlock (both halves carry it, so their models match; textures from Better Snowy Features, MIT).
 */
public class SnowyDoublePlantBlock extends DoublePlantBlock {

    public SnowyDoublePlantBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.defaultBlockState().setValue(SnowyPlantBlock.SNOW, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(SnowyPlantBlock.SNOW);
    }
}
