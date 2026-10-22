package net.icegiant.mixin;

import net.icegiant.block.RiverWaterBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FlowingFluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Gives river water (RiverWaterBlock) the direction of its river as its flow. Minecraft uses this flow to
 * draw the moving water texture and to push entities and boats - so the river visibly runs downstream and
 * carries things, although its blocks are still source blocks. All other water is untouched.
 */
@Mixin(FlowingFluid.class)
public abstract class RiverFlowMixin {

    @Inject(method = "getFlow", at = @At("HEAD"), cancellable = true)
    private void icegiant$riverFlow(BlockGetter level, BlockPos pos, FluidState fluid, CallbackInfoReturnable<Vec3> cir) {
        BlockState state = level.getBlockState(pos);
        if (state.getBlock() instanceof RiverWaterBlock) {
            int flow = state.getValue(RiverWaterBlock.FLOW);
            if (flow > 0) cir.setReturnValue(RiverWaterBlock.vector(flow));
        }
    }
}
