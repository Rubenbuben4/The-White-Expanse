package net.icegiant.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.SimpleWaterloggedBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.function.Supplier;

/**
 * A slab standing upright: half a block against one side (FACING = the side the half fills). Placed against
 * the clicked side of a block; on a top or bottom face it goes to the edge of the block nearest to where you
 * clicked. A second one of the same kind clicked into the open half makes a full block (DOUBLE). Hit box and
 * collision are exactly the half (or the full block when double); it can be waterlogged like a slab.
 * Its name is the base block's name + "Vertical Slab", so it follows the game language.
 */
public class VerticalSlabBlock extends Block implements SimpleWaterloggedBlock {

    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;
    public static final BooleanProperty DOUBLE = BooleanProperty.create("double");
    public static final BooleanProperty WATERLOGGED = BlockStateProperties.WATERLOGGED;

    private static final VoxelShape NORTH = Block.box(0, 0, 0, 16, 16, 8);
    private static final VoxelShape SOUTH = Block.box(0, 0, 8, 16, 16, 16);
    private static final VoxelShape WEST = Block.box(0, 0, 0, 8, 16, 16);
    private static final VoxelShape EAST = Block.box(8, 0, 0, 16, 16, 16);

    private final Supplier<Block> base;
    private final boolean wooden;

    public VerticalSlabBlock(Properties properties, Supplier<Block> base, boolean wooden) {
        super(properties);
        this.base = base;
        this.wooden = wooden;
        this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH)
                .setValue(DOUBLE, false).setValue(WATERLOGGED, false));
    }

    /** Hardness, blast resistance, sound and map colour of the base block (any tool drops it). */
    public static Properties propsOf(Block base) {
        return Properties.of().mapColor(base.defaultMapColor())
                .strength(base.defaultDestroyTime(), base.getExplosionResistance())
                .sound(base.defaultBlockState().getSoundType());
    }

    public boolean isWooden() {
        return this.wooden;
    }

    public Block base() {
        return this.base.get();
    }

    @Override
    public MutableComponent getName() {
        return Component.translatable("block.icegiant.vertical_slab_of", this.base.get().getName());
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, DOUBLE, WATERLOGGED);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        if (state.getValue(DOUBLE)) return Shapes.block();
        return switch (state.getValue(FACING)) {
            case SOUTH -> SOUTH;
            case WEST -> WEST;
            case EAST -> EAST;
            default -> NORTH;
        };
    }

    @Override
    public boolean useShapeForLightOcclusion(BlockState state) {
        return !state.getValue(DOUBLE);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockPos pos = context.getClickedPos();
        BlockState here = context.getLevel().getBlockState(pos);
        if (here.is(this)) return here.setValue(DOUBLE, true).setValue(WATERLOGGED, false);   // second half
        Direction face = context.getClickedFace(), facing;
        if (face.getAxis().isHorizontal()) {
            facing = face.getOpposite();                                    // against the clicked block
        } else {
            double dx = context.getClickLocation().x - pos.getX() - 0.5, dz = context.getClickLocation().z - pos.getZ() - 0.5;
            if (Math.abs(dx) < 0.1 && Math.abs(dz) < 0.1) facing = context.getHorizontalDirection();
            else if (Math.abs(dx) > Math.abs(dz)) facing = dx > 0 ? Direction.EAST : Direction.WEST;
            else facing = dz > 0 ? Direction.SOUTH : Direction.NORTH;
        }
        FluidState fluid = context.getLevel().getFluidState(pos);
        return this.defaultBlockState().setValue(FACING, facing).setValue(WATERLOGGED, fluid.getType() == Fluids.WATER);
    }

    @Override
    public boolean canBeReplaced(BlockState state, BlockPlaceContext context) {
        if (state.getValue(DOUBLE) || !context.getItemInHand().is(this.asItem())) return false;
        if (context.replacingClickedOnBlock()) {
            return context.getClickedFace() == state.getValue(FACING).getOpposite();   // clicked into the open half
        }
        return true;
    }

    @Override
    public FluidState getFluidState(BlockState state) {
        return state.getValue(WATERLOGGED) ? Fluids.WATER.getSource(false) : super.getFluidState(state);
    }

    @Override
    public boolean canPlaceLiquid(BlockGetter level, BlockPos pos, BlockState state, net.minecraft.world.level.material.Fluid fluid) {
        return !state.getValue(DOUBLE) && SimpleWaterloggedBlock.super.canPlaceLiquid(level, pos, state, fluid);
    }

    @Override
    public boolean placeLiquid(LevelAccessor level, BlockPos pos, BlockState state, FluidState fluid) {
        return !state.getValue(DOUBLE) && SimpleWaterloggedBlock.super.placeLiquid(level, pos, state, fluid);
    }

    @Override
    public BlockState updateShape(BlockState state, Direction direction, BlockState neighbor, LevelAccessor level,
                                  BlockPos pos, BlockPos neighborPos) {
        if (state.getValue(WATERLOGGED)) level.scheduleTick(pos, Fluids.WATER, Fluids.WATER.getTickDelay(level));
        return super.updateShape(state, direction, neighbor, level, pos, neighborPos);
    }

    @Override
    public boolean isPathfindable(BlockState state, BlockGetter level, BlockPos pos, PathComputationType type) {
        return type == PathComputationType.WATER && level.getFluidState(pos).is(net.minecraft.tags.FluidTags.WATER);
    }

    @Override
    public BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    public BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }

    // wooden ones burn like planks
    @Override
    public int getFlammability(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
        return this.wooden ? 5 : 0;
    }

    @Override
    public int getFireSpreadSpeed(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
        return this.wooden ? 20 : 0;
    }

    /** Item whose name follows the block (base name + "Vertical Slab"). */
    public static class Item extends net.minecraft.world.item.BlockItem {
        public Item(Block block, Properties properties) {
            super(block, properties);
        }

        @Override
        public Component getName(net.minecraft.world.item.ItemStack stack) {
            return this.getBlock().getName();
        }
    }
}
