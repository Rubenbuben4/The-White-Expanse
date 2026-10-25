package net.icegiant.block;

import net.icegiant.crystal.CreationCrystals;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import javax.annotation.Nullable;

/**
 * Großer Schöpfungskristall: the 3 x 3 x 3 creation crystal (radius 192 = 3 x the small one). Really takes up 27 blocks
 * (Rubenbuben4: "Echte 3x3x3 Fläche"): PART says which one (0..26, 13 = the middle, which holds the block entity and
 * is drawn 3 times as big). Placed with its bottom middle on the clicked spot, only where all 27 blocks are free.
 * Breaking any part breaks the whole crystal; only the middle drops the item (loot table).
 */
public class LargeCreationCrystalBlock extends CreationCrystalBlock {

    public static final IntegerProperty PART = IntegerProperty.create("part", 0, 26);
    public static final int CENTER = 13, BOTTOM = 10;    // part(0, 0, 0) and part(0, -1, 0)

    public LargeCreationCrystalBlock(Properties properties) {
        super(properties);
        registerDefaultState(this.stateDefinition.any().setValue(ACTIVE, false).setValue(PART, CENTER));
    }

    public static int part(int dx, int dy, int dz) {
        return (dx + 1) * 9 + (dy + 1) * 3 + (dz + 1);
    }

    /** Offset of a part from the middle block. */
    public static BlockPos offset(int part) {
        return new BlockPos(part / 9 - 1, (part / 3) % 3 - 1, part % 3 - 1);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(ACTIVE, PART);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return Shapes.block();
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return state.getValue(PART) == CENTER ? new CreationCrystalBlockEntity(pos, state) : null;
    }

    @Override
    protected BlockPos center(BlockPos pos, BlockState state) {
        return pos.subtract(offset(state.getValue(PART)));
    }

    @Override
    protected int radius() {
        return CreationCrystals.LARGE_RADIUS;
    }

    @Override
    protected void setActive(Level level, BlockPos center, boolean active) {
        for (int p = 0; p < 27; p++) {
            BlockPos at = center.offset(offset(p));
            BlockState s = level.getBlockState(at);
            if (s.is(this) && s.getValue(ACTIVE) != active) level.setBlock(at, s.setValue(ACTIVE, active), 3);
        }
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockPos center = context.getClickedPos().above();
        Level level = context.getLevel();
        for (int p = 0; p < 27; p++) {
            BlockPos at = center.offset(offset(p));
            if (!level.isInWorldBounds(at) || !level.getBlockState(at).canBeReplaced(context)) return null;
        }
        return defaultBlockState().setValue(PART, BOTTOM);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (level.isClientSide()) return;
        BlockPos center = center(pos, state);
        for (int p = 0; p < 27; p++) {
            if (p == state.getValue(PART)) continue;
            level.setBlock(center.offset(offset(p)), state.setValue(PART, p), 3);
        }
    }

    @Override
    public void playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (!level.isClientSide() && state.getValue(PART) != CENTER) {
            BlockPos center = center(pos, state);
            if (level.getBlockState(center).is(this)) level.destroyBlock(center, !player.isCreative(), player);
        }
        super.playerWillDestroy(level, pos, state, player);
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moved) {
        if (!state.is(newState.getBlock())) {
            BlockPos center = center(pos, state);
            if (state.getValue(PART) == CENTER) crystalRemoved(level, pos);
            for (int p = 0; p < 27; p++) {                      // the rest of the crystal goes with it (no drops)
                BlockPos at = center.offset(offset(p));
                if (!at.equals(pos) && level.getBlockState(at).is(this)) level.setBlock(at, Blocks.AIR.defaultBlockState(), 35);
            }
        }
        // (not CreationCrystalBlock.onRemove: only the middle holds the core)
        if (state.hasBlockEntity() && !state.is(newState.getBlock())) level.removeBlockEntity(pos);
    }
}
