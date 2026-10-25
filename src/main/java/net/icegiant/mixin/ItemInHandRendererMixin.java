package net.icegiant.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import net.icegiant.client.FirstPersonAbilityPose;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * First person: the held mod weapon follows the abilities' arm animation (see FirstPersonAbilityPose).
 * Injected right after renderArmWithItem's first pushPose, so the method's own popPose removes it again.
 */
@Mixin(ItemInHandRenderer.class)
public abstract class ItemInHandRendererMixin {

    @Inject(method = "renderArmWithItem",
            at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/PoseStack;pushPose()V",
                    ordinal = 0, shift = At.Shift.AFTER))
    private void icegiant$abilityPose(AbstractClientPlayer player, float partialTicks, float pitch,
                                      InteractionHand hand, float swingProgress, ItemStack stack,
                                      float equipProgress, PoseStack poseStack, MultiBufferSource buffer,
                                      int light, CallbackInfo ci) {
        if (hand != InteractionHand.MAIN_HAND) return;
        HumanoidArm arm = player.getMainArm();
        FirstPersonAbilityPose.apply(poseStack, player, arm);
    }
}
