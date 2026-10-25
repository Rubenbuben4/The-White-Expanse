package net.icegiant.block;

import net.icegiant.crystal.CreationCrystals;
import net.icegiant.item.IceGiantCoreItem;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

import javax.annotation.Nullable;

/**
 * Schöpfungskristall (creation crystal, Rubenbuben4 2026-10-25), made by the noble Ice Giants: put in an engraved Ice
 * Giant core that carries a biome (IceGiantCoreItem) and the crystal turns a cylinder around itself into that biome -
 * radius 64, 120 blocks up and down (CreationCrystals). Sneak + right-click with an empty hand takes the core out
 * again; breaking the crystal drops it. Either way the natural biome comes back.
 * <p>
 * Drawn by CreationCrystalRenderer (Rubenbuben4's model with the inverted cubes, floating and turning); the block
 * itself renders nothing. LargeCreationCrystalBlock is the 3 x 3 x 3 version (radius 192).
 */
public class CreationCrystalBlock extends Block implements EntityBlock {

    public static final BooleanProperty ACTIVE = BooleanProperty.create("active");
    private static final VoxelShape SHAPE = Block.box(2.0D, 2.0D, 2.0D, 14.0D, 14.0D, 14.0D);

    public CreationCrystalBlock(Properties properties) {
        super(properties);
        registerDefaultState(this.stateDefinition.any().setValue(ACTIVE, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(ACTIVE);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.INVISIBLE;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new CreationCrystalBlockEntity(pos, state);
    }

    /** The block that holds the block entity (the crystal itself; the middle block for the large one). */
    protected BlockPos center(BlockPos pos, BlockState state) {
        return pos;
    }

    protected int radius() {
        return CreationCrystals.SMALL_RADIUS;
    }

    /** Lights the crystal up or down (all of its blocks). */
    protected void setActive(Level level, BlockPos center, boolean active) {
        BlockState s = level.getBlockState(center);
        if (s.is(this)) level.setBlock(center, s.setValue(ACTIVE, active), 3);
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (level.isClientSide()) return InteractionResult.SUCCESS;
        BlockPos center = center(pos, state);
        if (!(level.getBlockEntity(center) instanceof CreationCrystalBlockEntity crystal)) return InteractionResult.PASS;
        ServerLevel server = (ServerLevel) level;
        ItemStack held = player.getItemInHand(hand);

        if (held.getItem() instanceof IceGiantCoreItem) {
            ResourceLocation biome = IceGiantCoreItem.biome(held);
            if (biome == null) {
                player.displayClientMessage(Component.translatable("message.icegiant.crystal.core_empty").withStyle(ChatFormatting.AQUA), true);
                return InteractionResult.CONSUME;
            }
            if (!crystal.core().isEmpty()) giveBack(player, crystal.core());   // a new core replaces the old one
            ItemStack one = held.copy();
            one.setCount(1);
            crystal.setCore(one);
            if (!player.getAbilities().instabuild) held.shrink(1);
            CreationCrystals.activate(server, center, radius(), biome);
            setActive(level, center, true);
            level.sendBlockUpdated(center, level.getBlockState(center), level.getBlockState(center), 3);
            level.playSound(null, center, SoundEvents.BEACON_ACTIVATE, SoundSource.BLOCKS, 1.0F, 1.4F);
            player.displayClientMessage(Component.translatable("message.icegiant.crystal.awakened",
                    IceGiantCoreItem.biomeName(biome), radius()).withStyle(ChatFormatting.AQUA), true);
            return InteractionResult.CONSUME;
        }

        if (held.isEmpty() && player.isShiftKeyDown() && !crystal.core().isEmpty()) {     // take the core out
            giveBack(player, crystal.core());
            crystal.setCore(ItemStack.EMPTY);
            CreationCrystals.deactivate(server, center);
            setActive(level, center, false);
            level.sendBlockUpdated(center, level.getBlockState(center), level.getBlockState(center), 3);
            level.playSound(null, center, SoundEvents.BEACON_DEACTIVATE, SoundSource.BLOCKS, 1.0F, 1.4F);
            player.displayClientMessage(Component.translatable("message.icegiant.crystal.asleep").withStyle(ChatFormatting.GRAY), true);
            return InteractionResult.CONSUME;
        }

        if (held.isEmpty()) {                                                               // what is it doing?
            ResourceLocation biome = CreationCrystals.activeBiome(server, center);
            player.displayClientMessage(biome == null
                    ? Component.translatable("message.icegiant.crystal.idle").withStyle(ChatFormatting.GRAY)
                    : Component.translatable("message.icegiant.crystal.status", IceGiantCoreItem.biomeName(biome), radius())
                    .withStyle(ChatFormatting.AQUA), true);
            return InteractionResult.CONSUME;
        }
        return InteractionResult.PASS;
    }

    private static void giveBack(Player player, ItemStack core) {
        if (!player.getInventory().add(core.copy())) player.drop(core.copy(), false);
    }

    /** The crystal (its block entity) is going: drop the core, give the area back its natural biome. */
    protected static void crystalRemoved(Level level, BlockPos center) {
        if (!(level instanceof ServerLevel server)) return;
        if (level.getBlockEntity(center) instanceof CreationCrystalBlockEntity crystal && !crystal.core().isEmpty()) {
            Containers.dropItemStack(level, center.getX(), center.getY(), center.getZ(), crystal.core().copy());
            crystal.setCore(ItemStack.EMPTY);
        }
        CreationCrystals.deactivate(server, center);
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moved) {
        if (!state.is(newState.getBlock())) crystalRemoved(level, pos);
        super.onRemove(state, level, pos, newState, moved);
    }
}
