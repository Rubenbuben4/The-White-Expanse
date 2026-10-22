package net.icegiant.block;

import net.icegiant.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Big double dungeon door, 3 blocks wide and 6 blocks tall (18 cells of this block).
 * <p>
 * Every cell knows its place in the door (PART_X 0..2 from left to right, PART_Y 0..5 from the
 * bottom). The bottom middle cell (1, 0) is the "controller": it holds the block entity that draws
 * the whole animated GeckoLib model (two wings of 1.5 x 6 blocks). All cells are invisible blocks
 * that only give the collision.
 * <p>
 * Right click on any cell opens or closes all 18 cells together. Closed: a 4 pixel thick wall in the
 * middle of the cells. Open: the middle column is free and the outer columns keep a thin strip where
 * the opened wings rest (the wings themselves reach further out than the door's cells).
 * Breaking one cell removes the whole door and drops one door item.
 */
public class DungeonDoorBlock extends Block implements EntityBlock {

    public static final int WIDTH = 3, HEIGHT = 6;

    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final IntegerProperty PART_X = IntegerProperty.create("part_x", 0, WIDTH - 1);
    public static final IntegerProperty PART_Y = IntegerProperty.create("part_y", 0, HEIGHT - 1);
    public static final BooleanProperty OPEN = BlockStateProperties.OPEN;

    private static final VoxelShape CLOSED_NS = Block.box(0, 0, 6, 16, 16, 10);
    private static final VoxelShape CLOSED_EW = Block.box(6, 0, 0, 10, 16, 16);
    private static final VoxelShape EDGE_WEST = Block.box(0, 0, 0, 3, 16, 16);
    private static final VoxelShape EDGE_EAST = Block.box(13, 0, 0, 16, 16, 16);
    private static final VoxelShape EDGE_NORTH = Block.box(0, 0, 0, 16, 16, 3);
    private static final VoxelShape EDGE_SOUTH = Block.box(0, 0, 13, 16, 16, 16);

    public DungeonDoorBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH)
                .setValue(PART_X, 1).setValue(PART_Y, 0).setValue(OPEN, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, PART_X, PART_Y, OPEN);
    }

    // ---- layout helpers ----

    /** The door's "right" side, seen by the player who placed it (looking along FACING). */
    private static Direction right(BlockState state) {
        return state.getValue(FACING).getClockWise();
    }

    /** Position of the controller (bottom middle cell) of the door this cell belongs to. */
    public static BlockPos controllerPos(BlockPos pos, BlockState state) {
        return pos.relative(right(state), 1 - state.getValue(PART_X)).below(state.getValue(PART_Y));
    }

    private static BlockPos cellPos(BlockPos controller, Direction right, int x, int y) {
        return controller.relative(right, x - 1).above(y);
    }

    public static boolean isController(BlockState state) {
        return state.getValue(PART_X) == 1 && state.getValue(PART_Y) == 0;
    }

    // ---- placing ----

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        Level level = ctx.getLevel();
        Direction facing = ctx.getHorizontalDirection();
        BlockPos base = ctx.getClickedPos();
        Direction right = facing.getClockWise();
        if (base.getY() + HEIGHT - 1 >= level.getMaxBuildHeight()) return null;
        for (int x = 0; x < WIDTH; x++) {
            for (int y = 0; y < HEIGHT; y++) {
                BlockPos p = cellPos(base, right, x, y);
                if (!level.getBlockState(p).canBeReplaced(ctx) || !level.getWorldBorder().isWithinBounds(p)) return null;
            }
        }
        return this.defaultBlockState().setValue(FACING, facing);
    }

    /** The item placed the controller; now fill in the other 17 cells. */
    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
        Direction right = right(state);
        for (int x = 0; x < WIDTH; x++) {
            for (int y = 0; y < HEIGHT; y++) {
                if (x == 1 && y == 0) continue;
                level.setBlock(cellPos(pos, right, x, y), state.setValue(PART_X, x).setValue(PART_Y, y), 3);
            }
        }
    }

    // ---- keeping the 18 cells together ----

    /** A cell turns into air when a neighbour that belongs to the same door is gone (chains through all cells). */
    @Override
    public BlockState updateShape(BlockState state, Direction dir, BlockState neighbor, LevelAccessor level,
                                  BlockPos pos, BlockPos neighborPos) {
        int x = state.getValue(PART_X), y = state.getValue(PART_Y);
        Direction right = right(state);
        int nx = x, ny = y;
        if (dir == Direction.UP) ny++;
        else if (dir == Direction.DOWN) ny--;
        else if (dir == right) nx++;
        else if (dir == right.getOpposite()) nx--;
        else return state;
        if (nx < 0 || nx >= WIDTH || ny < 0 || ny >= HEIGHT) return state;
        boolean partner = neighbor.is(this) && neighbor.getValue(FACING) == state.getValue(FACING)
                && neighbor.getValue(PART_X) == nx && neighbor.getValue(PART_Y) == ny;
        return partner ? state : Blocks.AIR.defaultBlockState();
    }

    /** Breaking any cell: one door item (from the controller's loot table), the rest vanishes without drops. */
    @Override
    public void playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (!level.isClientSide()) {
            BlockPos controller = controllerPos(pos, state);
            BlockState controllerState = level.getBlockState(controller);
            if (!player.isCreative() && !isController(state) && controllerState.is(this)) {
                Block.dropResources(controllerState, level, controller, level.getBlockEntity(controller),
                        player, player.getMainHandItem());
            }
            Direction right = right(state);
            for (int x = 0; x < WIDTH; x++) {
                for (int y = 0; y < HEIGHT; y++) {
                    BlockPos p = cellPos(controller, right, x, y);
                    if (p.equals(pos) || !level.getBlockState(p).is(this)) continue;
                    level.setBlock(p, Blocks.AIR.defaultBlockState(), 35);
                }
            }
        }
        super.playerWillDestroy(level, pos, state, player);
    }

    // ---- opening ----

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand,
                                 BlockHitResult hit) {
        if (level.isClientSide()) return InteractionResult.SUCCESS;
        boolean open = !state.getValue(OPEN);
        BlockPos controller = controllerPos(pos, state);
        Direction right = right(state);
        for (int x = 0; x < WIDTH; x++) {
            for (int y = 0; y < HEIGHT; y++) {
                BlockPos p = cellPos(controller, right, x, y);
                BlockState s = level.getBlockState(p);
                if (s.is(this)) level.setBlock(p, s.setValue(OPEN, open), 10);
            }
        }
        BlockPos middle = controller.above(HEIGHT / 2);
        level.playSound(null, middle, open ? SoundEvents.WOODEN_DOOR_OPEN : SoundEvents.WOODEN_DOOR_CLOSE,
                SoundSource.BLOCKS, 1.5F, 0.5F + level.random.nextFloat() * 0.05F);
        level.playSound(null, middle, SoundEvents.CHAIN_PLACE, SoundSource.BLOCKS, 0.8F, 0.6F);
        level.gameEvent(player, open ? GameEvent.BLOCK_OPEN : GameEvent.BLOCK_CLOSE, controller);
        return InteractionResult.CONSUME;
    }

    // ---- shapes ----

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        Direction.Axis axis = state.getValue(FACING).getAxis();
        if (!state.getValue(OPEN)) return axis == Direction.Axis.Z ? CLOSED_NS : CLOSED_EW;
        int x = state.getValue(PART_X);
        if (x == 1) return Shapes.empty();
        Direction edge = x == 0 ? right(state).getOpposite() : right(state);
        return switch (edge) {
            case WEST -> EDGE_WEST;
            case EAST -> EDGE_EAST;
            case NORTH -> EDGE_NORTH;
            default -> EDGE_SOUTH;
        };
    }

    @Override
    public boolean isPathfindable(BlockState state, BlockGetter level, BlockPos pos, PathComputationType type) {
        return state.getValue(OPEN) && type != PathComputationType.WATER;
    }

    // ---- rendering: the controller's block entity draws the model, the cells themselves are invisible ----

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.INVISIBLE;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return isController(state) ? new DungeonDoorBlockEntity(pos, state) : null;
    }

    @Override
    public BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    public BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }

    /** For the block entity type registration. */
    public static net.minecraft.world.level.block.entity.BlockEntityType<DungeonDoorBlockEntity> type() {
        return ModBlockEntities.DUNGEON_DOOR.get();
    }
}
