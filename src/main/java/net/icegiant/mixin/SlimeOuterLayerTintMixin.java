package net.icegiant.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.icegiant.slime.SlimeLife;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.layers.SlimeOuterLayer;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** A slime's outer jelly takes on a light tint of what it ate (SlimeFood.tint, sent by SlimeTintPacket; 2026-10-27). */
@Mixin(SlimeOuterLayer.class)
public abstract class SlimeOuterLayerTintMixin {

    private static int icegiant$tint = 0xFFFFFF;

    @Inject(method = "render(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;ILnet/minecraft/world/entity/LivingEntity;FFFFFF)V",
            at = @At("HEAD"))
    private void icegiant$pickTint(PoseStack pose, MultiBufferSource buffers, int light, LivingEntity entity, float a, float b,
                                   float c, float d, float e, float f, CallbackInfo ci) {
        icegiant$tint = SlimeLife.CLIENT_TINT.getOrDefault(entity.getId(), 0xFFFFFF);
    }

    @Redirect(method = "render(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;ILnet/minecraft/world/entity/LivingEntity;FFFFFF)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/model/EntityModel;renderToBuffer(Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;IIFFFF)V"))
    private void icegiant$tinted(EntityModel<?> model, PoseStack pose, VertexConsumer vc, int light, int overlay,
                                 float r, float g, float b, float alpha) {
        int t = icegiant$tint;
        model.renderToBuffer(pose, vc, light, overlay, r * ((t >> 16) & 255) / 255.0F, g * ((t >> 8) & 255) / 255.0F,
                b * (t & 255) / 255.0F, alpha);
    }
}
