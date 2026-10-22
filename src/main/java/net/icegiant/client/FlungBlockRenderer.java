package net.icegiant.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.icegiant.entity.FlungBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Vector3f;

/**
 * Draws a block hurled by the wall-breaker dash, tumbling around its centre while it flies.
 * Every block gets its own spin axis and speed (from its entity id), so a smashed wall
 * breaks up into a spray of differently spinning blocks.
 */
public class FlungBlockRenderer extends EntityRenderer<FlungBlockEntity> {

    public FlungBlockRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.shadowRadius = 0.4F;
    }

    @Override
    public void render(FlungBlockEntity entity, float entityYaw, float partialTick, PoseStack poseStack,
                       MultiBufferSource buffer, int packedLight) {
        BlockState state = entity.getBlockState();
        if (state.getRenderShape() != RenderShape.MODEL) return;

        RandomSource spin = RandomSource.create(entity.getId() * 7919L);
        Vector3f axis = new Vector3f(spin.nextFloat() - 0.5F, spin.nextFloat() - 0.5F, spin.nextFloat() - 0.5F);
        if (axis.lengthSquared() < 1.0E-4F) axis.set(1.0F, 0.0F, 0.0F);
        axis.normalize();
        float degreesPerTick = 12.0F + spin.nextFloat() * 18.0F;
        // tumbling stops as soon as it touches the ground (it turns back into a block right after)
        float angle = entity.onGround() ? 0.0F : (entity.tickCount + partialTick) * degreesPerTick;

        poseStack.pushPose();
        poseStack.translate(0.0D, 0.5D, 0.0D);
        poseStack.mulPose(Axis.of(axis).rotationDegrees(angle));
        poseStack.translate(-0.5D, -0.5D, -0.5D);
        Minecraft.getInstance().getBlockRenderer().renderSingleBlock(state, poseStack, buffer, packedLight,
                OverlayTexture.NO_OVERLAY);
        poseStack.popPose();
        super.render(entity, entityYaw, partialTick, poseStack, buffer, packedLight);
    }

    @Override
    public ResourceLocation getTextureLocation(FlungBlockEntity entity) {
        return TextureAtlas.LOCATION_BLOCKS;
    }
}
