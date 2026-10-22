package net.icegiant.block;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/**
 * Nur ein PLATZHALTER-Block für den Boden-Einschlag-Effekt (siehe
 * NordicAxeSlamAbility). Er selbst hat KEIN eigenes Rendering
 * (RenderShape.INVISIBLE) - das eigentliche Bild (der ursprüngliche
 * Block, gekippt/verschoben) kommt komplett aus FractureBlockEntityRenderer.
 *
 * HINWEIS ZUR HERKUNFT DER TECHNIK: Die grundlegende Idee - einen Block
 * kurzzeitig durch eine "gekippte" Darstellung ersetzen, die nach einer
 * Weile wieder zum Originalblock wird - stammt aus dem Mod "Epic Fight"
 * (Antikythera Studios: https://github.com/Antikythera-Studios/epicfight,
 * Lizenz: GPL-3.0-or-later,
 * dortige Klassen FractureBlock/FractureBlockState). Der Code hier ist
 * eine eigenständige Neuimplementierung (andere Klassenstruktur, andere
 * Variablennamen, eigene Rotations-/Bounce-Berechnung), keine Kopie des
 * Originalcodes - da dieser Mod aber unter GPL-3.0-or-later steht und wir
 * diese Technik übernehmen, steht auch icegiantmod insgesamt unter
 * GPL-3.0-or-later (siehe LICENSE-Datei im Projekt-Root).
 */
public class FractureBlock extends Block implements EntityBlock {

    public FractureBlock(Properties properties) {
        super(properties);
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.INVISIBLE;
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        // Same hitbox as the block it stands in for: a snow layer stays a thin layer, grass and
        // flowers stay walk-through, a full block stays full (nobody falls through). Before
        // 2026-10-02 this was always a full block -> snow layers / plants became 1-block steps.
        if (level.getBlockEntity(pos) instanceof FractureBlockEntity fracture && fracture.hasOriginalState()) {
            return fracture.getOriginalState().getCollisionShape(level, pos, context);
        }
        return Shapes.block();
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        if (level.getBlockEntity(pos) instanceof FractureBlockEntity fracture && fracture.hasOriginalState()) {
            return fracture.getOriginalState().getShape(level, pos, context);
        }
        return Shapes.block();
    }

    /** The shape depends on the block entity, so Minecraft must not cache one shape for all fracture blocks. */
    @Override
    public boolean hasDynamicShape() {
        return true;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new FractureBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(net.minecraft.world.level.Level level,
                                                                   BlockState state, BlockEntityType<T> type) {
        return (level1, pos, state1, blockEntity) -> {
            if (blockEntity instanceof FractureBlockEntity fracture) {
                fracture.tick(level1, pos);
            }
        };
    }
}
