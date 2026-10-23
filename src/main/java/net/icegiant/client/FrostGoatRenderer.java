package net.icegiant.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.icegiant.IceGiantMod;
import net.icegiant.entity.FrostGoatEntity;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;

/** Frost Goat: vanilla's goat model and texture, twice as big (FrostGoatEntity.SCALE). */
public class FrostGoatRenderer extends MobRenderer<FrostGoatEntity, FrostGoatModel> {

    public static final ModelLayerLocation LAYER = new ModelLayerLocation(new ResourceLocation(IceGiantMod.MOD_ID, "frost_goat"), "main");
    private static final ResourceLocation TEXTURE = new ResourceLocation("minecraft", "textures/entity/goat/goat.png");

    public FrostGoatRenderer(EntityRendererProvider.Context context) {
        super(context, new FrostGoatModel(context.bakeLayer(LAYER)), 0.7F * FrostGoatEntity.SCALE);
    }

    @Override
    protected void scale(FrostGoatEntity goat, PoseStack pose, float partialTick) {
        pose.scale(FrostGoatEntity.SCALE, FrostGoatEntity.SCALE, FrostGoatEntity.SCALE);
    }

    @Override
    public ResourceLocation getTextureLocation(FrostGoatEntity goat) {
        return TEXTURE;
    }
}
