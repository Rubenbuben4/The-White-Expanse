package net.icegiant.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import net.icegiant.client.SlimeShapeClient;
import net.minecraft.client.renderer.entity.SlimeRenderer;
import net.minecraft.world.entity.monster.Slime;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** A slime stretches over what it eats (SlimeShape / SlimeShapeClient). */
@Mixin(SlimeRenderer.class)
public abstract class SlimeRendererShapeMixin {

    @Inject(method = "scale(Lnet/minecraft/world/entity/monster/Slime;Lcom/mojang/blaze3d/vertex/PoseStack;F)V", at = @At("HEAD"))
    private void icegiant$stretchOver(Slime slime, PoseStack pose, float partialTick, CallbackInfo ci) {
        SlimeShapeClient.apply(slime, pose, partialTick);
    }
}
