package net.icegiant.entity;

import net.icegiant.registry.ModEntityTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A block hurled out of a wall by the wall-breaker dash (IceDashAbility).
 * <p>
 * Behaves exactly like a falling block (flies, falls, lands and turns back into
 * the block - or drops as an item if it can't be placed); the only difference is
 * the look: {@code FlungBlockRenderer} lets it tumble through the air.
 */
public class FlungBlockEntity extends FallingBlockEntity {

    public FlungBlockEntity(EntityType<? extends FallingBlockEntity> type, Level level) {
        super(type, level);
    }

    /** Takes the block at {@code pos} out of the world and turns it into a flung block (velocity: caller). */
    public static FlungBlockEntity fling(ServerLevel level, BlockPos pos, BlockState state) {
        FlungBlockEntity entity = new FlungBlockEntity(ModEntityTypes.FLUNG_BLOCK.get(), level);
        entity.setFlungState(state);
        entity.setPos(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D);
        entity.xo = entity.getX();
        entity.yo = entity.getY();
        entity.zo = entity.getZ();
        entity.setStartPos(pos);
        entity.blocksBuilding = true;
        level.setBlock(pos, state.getFluidState().createLegacyBlock(), 3); // waterlogged blocks leave their water
        level.addFreshEntity(entity);
        return entity;
    }

    /** The block state field of the parent is private - set it through the parent's own save-data loader. */
    private void setFlungState(BlockState state) {
        CompoundTag tag = new CompoundTag();
        tag.put("BlockState", NbtUtils.writeBlockState(state));
        tag.putInt("Time", 1);
        tag.putBoolean("DropItem", true);
        this.readAdditionalSaveData(tag);
    }
}
