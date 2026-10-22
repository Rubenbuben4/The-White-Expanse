package net.icegiant.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.SimpleWaterloggedBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Invisible light source for building (creative only, like a barrier block).
 * <ul>
 *   <li>Gives light of level LEVEL (1-15, default 15). Never drawn, no collision - you walk through it.</li>
 *   <li>Only while you hold its item in creative it has an outline (so you can aim at it, break it
 *       or change it) and shows a marker particle, like barrier blocks do.</li>
 *   <li>Right click with the item in hand: brightness 15 -> 14 -> ... -> 1 -> 15 (sneaking: other way).</li>
 *   <li>Unbreakable in survival (like bedrock / barriers), drops nothing, no recipe.</li>
 *   <li>Other blocks can be placed into it (it is replaced), so it never gets in the way. Can be waterlogged.</li>
 * </ul>
 */
public class InvisibleLightBlock extends Block implements SimpleWaterloggedBlock {

    public static final IntegerProperty LEVEL = IntegerProperty.create("level", 1, 15);
    public static final BooleanProperty WATERLOGGED = BlockStateProperties.WATERLOGGED;

    public InvisibleLightBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(LEVEL, 15).setValue(WATERLOGGED, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(LEVEL, WATERLOGGED);
    }

    /** Use as Properties.lightLevel(...). */
    public static int lightOf(BlockState state) {
        return state.getValue(LEVEL);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        boolean water = ctx.getLevel().getFluidState(ctx.getClickedPos()).getType() == Fluids.WATER;
        return this.defaultBlockState().setValue(WATERLOGGED, water);
    }

    // ---- invisible and walk-through; an outline only while holding the item ----

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return ctx.isHoldingItem(this.asItem()) ? Shapes.block() : Shapes.empty();
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return Shapes.empty();
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.INVISIBLE;
    }

    @Override
    public boolean propagatesSkylightDown(BlockState state, BlockGetter level, BlockPos pos) {
        return !state.getValue(WATERLOGGED);
    }

    @Override
    public float getShadeBrightness(BlockState state, BlockGetter level, BlockPos pos) {
        return 1.0F;
    }

    /** Other blocks may be placed into it; with its own item you aim at it instead. */
    @Override
    public boolean canBeReplaced(BlockState state, BlockPlaceContext ctx) {
        return !ctx.getItemInHand().is(this.asItem());
    }

    // ---- change the brightness ----

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand,
                                 BlockHitResult hit) {
        if (!player.getAbilities().instabuild || !player.getItemInHand(hand).is(this.asItem())) return InteractionResult.PASS;
        if (!level.isClientSide()) {
            int light = state.getValue(LEVEL) + (player.isShiftKeyDown() ? 1 : -1);
            if (light < 1) light = 15;
            if (light > 15) light = 1;
            level.setBlock(pos, state.setValue(LEVEL, light), 2);
            level.playSound(null, pos, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, 0.6F, 0.5F + light / 15.0F);
            player.displayClientMessage(Component.translatable("message.icegiant.invisible_light_level", light), true);
        }
        return InteractionResult.sidedSuccess(level.isClientSide());
    }

    // ---- marker particle while a creative player holds the item ----

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        net.icegiant.client.InvisibleLightMarker.maybeShow(state, level, pos, this.asItem());
    }

    // ---- water ----

    @Override
    public FluidState getFluidState(BlockState state) {
        return state.getValue(WATERLOGGED) ? Fluids.WATER.getSource(false) : super.getFluidState(state);
    }

    @Override
    public BlockState updateShape(BlockState state, Direction dir, BlockState neighbor, LevelAccessor level,
                                  BlockPos pos, BlockPos neighborPos) {
        if (state.getValue(WATERLOGGED)) level.scheduleTick(pos, Fluids.WATER, Fluids.WATER.getTickDelay(level));
        return state;
    }
}
