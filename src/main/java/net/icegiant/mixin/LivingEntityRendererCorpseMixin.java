package net.icegiant.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.icegiant.slime.SlimeCorpses;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Bodies a slime left (SlimeCorpses, 2026-10-27): they fall backwards onto their back within the first second (vanilla
 * tips the dying sideways) and then lie there - without the red hurt tint vanilla keeps on dying mobs.
 */
@Mixin(LivingEntityRenderer.class)
public abstract class LivingEntityRendererCorpseMixin {

    @Inject(method = "setupRotations", at = @At("HEAD"), cancellable = true)
    private void icegiant$onTheBack(LivingEntity entity, PoseStack pose, float bob, float bodyYaw, float partialTick,
                                    CallbackInfo ci) {
        if (!SlimeCorpses.isCorpse(entity)) return;
        float f = Mth.sqrt(Mth.clamp((entity.deathTime + partialTick - 1.0F) / 20.0F * 1.6F, 0.0F, 1.0F));
        pose.mulPose(Axis.YP.rotationDegrees(180.0F - bodyYaw));
        pose.translate(0.0D, 0.12D * f * entity.getBbWidth() / 0.6D, 0.0D);   // (so the back does not sink into the ground)
        pose.mulPose(Axis.XP.rotationDegrees(90.0F * f));                       // face up, head where the back was
        ci.cancel();
    }

    @Inject(method = "getOverlayCoords", at = @At("HEAD"), cancellable = true)
    private static void icegiant$noRedBody(LivingEntity entity, float whiteOverlay, CallbackInfoReturnable<Integer> cir) {
        if (entity.deathTime > 20 && SlimeCorpses.isCorpse(entity)) cir.setReturnValue(OverlayTexture.NO_OVERLAY);
    }
}
