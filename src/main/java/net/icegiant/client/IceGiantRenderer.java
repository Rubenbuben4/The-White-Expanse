package net.icegiant.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.icegiant.IceGiantMod;
import net.icegiant.entity.IceGiantEntity;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.resources.ResourceLocation;

/**
 * Renders the Ice Giant like vanilla renders its Giant: the normal humanoid
 * (player) model, scaled up. HumanoidMobRenderer already adds the held-item
 * layer (the axe, rendered through GeckoLib); the armor layer is added here -
 * the Ice Giant armor items hand it their GeckoLib armor model themselves.
 */
public class IceGiantRenderer extends HumanoidMobRenderer<IceGiantEntity, IceGiantModel> {

    private static final ResourceLocation TEXTURE =
            new ResourceLocation(IceGiantMod.MOD_ID, "textures/entity/ice_giant.png");

    public IceGiantRenderer(EntityRendererProvider.Context context) {
        super(context, new IceGiantModel(context.bakeLayer(ModelLayers.PLAYER)), 0.5F * IceGiantEntity.SCALE);
        this.addLayer(new HumanoidArmorLayer<>(this,
                new HumanoidModel<>(context.bakeLayer(ModelLayers.PLAYER_INNER_ARMOR)),
                new HumanoidModel<>(context.bakeLayer(ModelLayers.PLAYER_OUTER_ARMOR)),
                context.getModelManager()));
    }

    /**
     * Death: the model plays Rubenbuben4's fall animation instead of vanilla's sideways tip-over and red
     * flash - so while drawing, the giant is shown as "not dying" and the model gets the time since death.
     */
    @Override
    public void render(IceGiantEntity entity, float yaw, float partialTick, PoseStack poseStack,
                       net.minecraft.client.renderer.MultiBufferSource buffers, int light) {
        int deathTime = entity.deathTime;
        this.getModel().deathSeconds = deathTime > 0 ? (deathTime - 1 + partialTick) / 20.0F : -1.0F;
        entity.deathTime = 0;
        try {
            super.render(entity, yaw, partialTick, poseStack, buffers, light);
        } finally {
            entity.deathTime = deathTime;
        }
    }

    @Override
    protected void scale(IceGiantEntity entity, PoseStack poseStack, float partialTick) {
        poseStack.scale(IceGiantEntity.SCALE, IceGiantEntity.SCALE, IceGiantEntity.SCALE);
    }

    @Override
    public ResourceLocation getTextureLocation(IceGiantEntity entity) {
        return TEXTURE;
    }
}
