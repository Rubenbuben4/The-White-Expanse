package net.icegiant.mixin;

import net.icegiant.entity.FrostGoatEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.EntityCollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * A ridden Frost Goat walks through leaves (Rubenbuben4, 2026-10-23): for it, leaves have no collision. Everything
 * else (and an unridden goat) still bumps into them. Cheap checks first - this runs for every collision test.
 */
@Mixin(BlockBehaviour.BlockStateBase.class)
public abstract class LeavesPassMixin {

    @Inject(method = "getCollisionShape(Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/phys/shapes/CollisionContext;)Lnet/minecraft/world/phys/shapes/VoxelShape;",
            at = @At("HEAD"), cancellable = true)
    private void icegiant$goatThroughLeaves(BlockGetter level, BlockPos pos, CollisionContext context, CallbackInfoReturnable<VoxelShape> cir) {
        if (context instanceof EntityCollisionContext ec && ec.getEntity() instanceof FrostGoatEntity goat && goat.isVehicle()
                && ((BlockBehaviour.BlockStateBase) (Object) this).is(BlockTags.LEAVES)) {
            cir.setReturnValue(Shapes.empty());
        }
    }
}
