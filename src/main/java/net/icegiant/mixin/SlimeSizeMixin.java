package net.icegiant.mixin;

import net.icegiant.slime.SlimeFood;
import net.icegiant.slime.SlimeLife;
import net.minecraft.world.entity.monster.Slime;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Slimes (2026-10-27): after every size change the size and diet bonuses are put on again (SlimeFood.applyStats);
 * after every tick the crawl wobble and sound on the client (SlimeLife.afterTick).
 */
@Mixin(Slime.class)
public abstract class SlimeSizeMixin {

    @Inject(method = "setSize", at = @At("TAIL"))
    private void icegiant$stats(int size, boolean resetHealth, CallbackInfo ci) {
        SlimeFood.applyStats((Slime) (Object) this, resetHealth);
    }

    @Inject(method = "tick", at = @At("TAIL"))
    private void icegiant$afterTick(CallbackInfo ci) {
        SlimeLife.afterTick((Slime) (Object) this);
    }
}
