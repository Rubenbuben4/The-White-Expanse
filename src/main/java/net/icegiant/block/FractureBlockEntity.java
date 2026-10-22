package net.icegiant.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * Siehe Lizenz-/Herkunftshinweis in FractureBlock.java.
 *
 * WICHTIG: getUpdateTag()/handleUpdateTag()/getUpdatePacket() sind nötig,
 * damit der Original-Block (und Richtung/Tiefe/Zufalls-Kippung) überhaupt
 * beim CLIENT ankommt - ohne das bleibt der Client bei originalState=null
 * und zeigt den Stein-Fallback statt des echten Blocks (Gras etc.).
 */
public class FractureBlockEntity extends BlockEntity {

    private static final int DEFAULT_LIFETIME_TICKS = 90;

    private BlockState originalState;
    private int ticksAlive = 0;
    private int lifetimeTicks = DEFAULT_LIFETIME_TICKS;
    private float depthFactor = 1.0f;
    private float dirX = 0f;
    private float dirZ = 0f;

    // Zusätzliche, zufällige Kipp-Komponente - wird im Renderer ADDITIV zur
    // normalen, richtungsbasierten Kippung dazugerechnet, ersetzt sie nicht.
    private float extraTiltA = 0f;
    private float extraTiltB = 0f;

    public FractureBlockEntity(BlockPos pos, BlockState state) {
        super(net.icegiant.registry.ModBlockEntities.FRACTURE_BLOCK_ENTITY.get(), pos, state);
    }

    /** Setzt alle Werte auf einmal und stößt danach GENAU EINE Sync-Nachricht zum Client an. */
    public void configure(BlockState originalState, float depthFactor, float dirX, float dirZ, int lifetimeTicks) {
        configure(originalState, depthFactor, dirX, dirZ, lifetimeTicks, 0f, 0f);
    }

    public void configure(BlockState originalState, float depthFactor, float dirX, float dirZ, int lifetimeTicks,
                           float extraTiltA, float extraTiltB) {
        this.originalState = originalState;
        this.depthFactor = depthFactor;
        this.dirX = dirX;
        this.dirZ = dirZ;
        this.lifetimeTicks = lifetimeTicks;
        this.extraTiltA = extraTiltA;
        this.extraTiltB = extraTiltB;
        setChanged();
        if (level != null && !level.isClientSide()) {
            level.sendBlockUpdated(getBlockPos(), getBlockState(), getBlockState(), 3);
        }
    }

    public BlockState getOriginalState() {
        return originalState != null ? originalState : Blocks.STONE.defaultBlockState();
    }

    public boolean hasOriginalState() {
        return originalState != null;
    }

    public int getLifetimeTicks() {
        return lifetimeTicks;
    }

    public float getDepthFactor() {
        return depthFactor;
    }

    public float getDirX() {
        return dirX;
    }

    public float getDirZ() {
        return dirZ;
    }

    public float getExtraTiltA() {
        return extraTiltA;
    }

    public float getExtraTiltB() {
        return extraTiltB;
    }

    public int getTicksAlive() {
        return ticksAlive;
    }

    public void tick(Level level, BlockPos pos) {
        ticksAlive++;
        if (ticksAlive >= lifetimeTicks) {
            level.setBlock(pos, getOriginalState(), 3);
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        if (originalState != null) {
            tag.put("OriginalState", NbtUtils.writeBlockState(originalState));
        }
        tag.putFloat("DepthFactor", depthFactor);
        tag.putFloat("DirX", dirX);
        tag.putFloat("DirZ", dirZ);
        tag.putFloat("ExtraTiltA", extraTiltA);
        tag.putFloat("ExtraTiltB", extraTiltB);
        tag.putInt("LifetimeTicks", lifetimeTicks);
        tag.putInt("TicksAlive", ticksAlive);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        if (tag.contains("OriginalState")) {
            // the level is not set yet when a chunk loads from disk -> use the global block registry
            this.originalState = NbtUtils.readBlockState(
                    net.minecraft.core.registries.BuiltInRegistries.BLOCK.asLookup(), tag.getCompound("OriginalState"));
        }
        this.depthFactor = tag.getFloat("DepthFactor");
        this.dirX = tag.getFloat("DirX");
        this.dirZ = tag.getFloat("DirZ");
        this.extraTiltA = tag.getFloat("ExtraTiltA");
        this.extraTiltB = tag.getFloat("ExtraTiltB");
        if (tag.contains("LifetimeTicks")) this.lifetimeTicks = tag.getInt("LifetimeTicks");
        this.ticksAlive = tag.getInt("TicksAlive");
    }

    @Override
    public CompoundTag getUpdateTag() {
        CompoundTag tag = super.getUpdateTag();
        saveAdditional(tag);
        return tag;
    }

    @Override
    public void handleUpdateTag(CompoundTag tag) {
        load(tag);
    }

    @Nullable
    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
