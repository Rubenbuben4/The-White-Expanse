package net.icegiant.mixin;

import net.icegiant.entity.GiantDeath;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Big players in Ice Giant armor die the giant's death (GiantDeath) instead of vanilla's 1 s tip-over. */
@Mixin(LivingEntity.class)
public abstract class LivingEntityDeathMixin {

    @Inject(method = "tickDeath", at = @At("HEAD"), cancellable = true)
    private void icegiant$giantDeath(CallbackInfo ci) {
        if (GiantDeath.tickDeath((LivingEntity) (Object) this)) ci.cancel();
    }
}
