package net.icegiant.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import net.icegiant.client.SlimeShapeClient;
import net.minecraft.client.renderer.entity.MagmaCubeRenderer;
import net.minecraft.world.entity.monster.MagmaCube;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** A magma cube stretches over what it eats, like the slimes (SlimeShape / SlimeShapeClient). */
@Mixin(MagmaCubeRenderer.class)
public abstract class MagmaCubeRendererShapeMixin {

    @Inject(method = "scale(Lnet/minecraft/world/entity/monster/MagmaCube;Lcom/mojang/blaze3d/vertex/PoseStack;F)V", at = @At("HEAD"))
    private void icegiant$stretchOver(MagmaCube cube, PoseStack pose, float partialTick, CallbackInfo ci) {
        SlimeShapeClient.apply(cube, pose, partialTick);
    }
}
