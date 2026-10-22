package net.icegiant.mixin;

import net.icegiant.block.RiverWaterBlock;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.animal.AbstractFish;
import net.minecraftforge.fluids.FluidType;
import org.spongepowered.asm.mixin.Mixin;

/**
 * Fish are not carried along by a river's current (Rubenbuben4, 2026-10-22: "make them unaffected of the current").
 * Forge asks every entity {@code isPushedByFluid(FluidType)} before the water's flow (RiverFlowMixin) pushes it; fish
 * get their own answer here: no while they swim in river water (RiverWaterBlock), otherwise Forge's usual one (so
 * vanilla's flowing water still moves them as before). Players, mobs, items and boats keep drifting downstream.
 * <p>
 * The method is added to AbstractFish (it overrides Forge's default method in IForgeEntity; Forge's names are not
 * obfuscated, so it needs no mapping).
 */
@Mixin(AbstractFish.class)
public abstract class FishCurrentMixin {

    public boolean isPushedByFluid(FluidType type) {
        Entity self = (Entity) (Object) this;
        if (self.level().getBlockState(self.blockPosition()).getBlock() instanceof RiverWaterBlock) return false;
        return self.isPushedByFluid() && type.canPushEntity(self);
    }
}
