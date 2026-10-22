package net.icegiant.client.gnoll;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.icegiant.entity.gnoll.GnollEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.util.Mth;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

/**
 * Renders a Gnoll with its GeckoLib model; the death animation replaces the vanilla tip-over.
 * <p>
 * Head look: while the head bone (h_head) is drawn, the look angles are added on top of the
 * animated rotation and removed again right after, so the turn is applied exactly once per frame
 * (adding it in the model's custom-animation step piled it up frame after frame).
 */
public class GnollRenderer<T extends GnollEntity> extends GeoEntityRenderer<T> {

    private static final String HEAD = "h_head";
    private static final float MAX_YAW = 70.0F;

    public GnollRenderer(EntityRendererProvider.Context context, String modelName) {
        super(context, new GnollModel<>(modelName));
        this.shadowRadius = 0.45F;
    }

    @Override
    protected float getDeathMaxRotation(T gnoll) {
        return 0.0F;
    }

    @Override
    public void renderRecursively(PoseStack poseStack, T gnoll, GeoBone bone, RenderType renderType,
                                  MultiBufferSource bufferSource, VertexConsumer buffer, boolean isReRender,
                                  float partialTick, int packedLight, int packedOverlay,
                                  float red, float green, float blue, float alpha) {
        if (!HEAD.equals(bone.getName()) || gnoll.isDeadOrDying()) {
            super.renderRecursively(poseStack, gnoll, bone, renderType, bufferSource, buffer, isReRender,
                    partialTick, packedLight, packedOverlay, red, green, blue, alpha);
            return;
        }
        float bodyYaw = Mth.rotLerp(partialTick, gnoll.yBodyRotO, gnoll.yBodyRot);
        float headYaw = Mth.rotLerp(partialTick, gnoll.yHeadRotO, gnoll.yHeadRot);
        float yaw = Mth.clamp(Mth.wrapDegrees(headYaw - bodyYaw), -MAX_YAW, MAX_YAW);
        float pitch = Mth.clamp(Mth.lerp(partialTick, gnoll.xRotO, gnoll.getXRot()), -40.0F, 40.0F);

        float rotX = bone.getRotX();
        float rotY = bone.getRotY();
        bone.setRotX(rotX - pitch * Mth.DEG_TO_RAD);
        bone.setRotY(rotY - yaw * Mth.DEG_TO_RAD);
        super.renderRecursively(poseStack, gnoll, bone, renderType, bufferSource, buffer, isReRender,
                partialTick, packedLight, packedOverlay, red, green, blue, alpha);
        bone.setRotX(rotX);
        bone.setRotY(rotY);
    }
}
