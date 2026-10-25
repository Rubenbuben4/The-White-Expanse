package net.icegiant.block;

import net.icegiant.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

/**
 * Schöpfungskristall (small, or the middle block of a large one): holds the engraved core that was put in. The core
 * carries the biome (IceGiantCoreItem); the area itself is kept in CreationCrystalData. Rendered by
 * CreationCrystalRenderer.
 */
public class CreationCrystalBlockEntity extends BlockEntity {

    private ItemStack core = ItemStack.EMPTY;

    public CreationCrystalBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.CREATION_CRYSTAL.get(), pos, state);
    }

    public ItemStack core() {
        return this.core;
    }

    public void setCore(ItemStack stack) {
        this.core = stack;
        setChanged();
    }

    public boolean large() {
        return getBlockState().getBlock() instanceof LargeCreationCrystalBlock;
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        if (!this.core.isEmpty()) tag.put("Core", this.core.save(new CompoundTag()));
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        this.core = tag.contains("Core") ? ItemStack.of(tag.getCompound("Core")) : ItemStack.EMPTY;
    }

    @Override
    public CompoundTag getUpdateTag() {
        return saveWithoutMetadata();
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    /** The large crystal is drawn 3 blocks big around its middle block - keep it on screen at its edges too. */
    @Override
    public AABB getRenderBoundingBox() {
        return large() ? new AABB(this.worldPosition).inflate(1.5D) : new AABB(this.worldPosition);
    }
}
