package net.icegiant.client.gnoll;

import net.icegiant.IceGiantMod;
import net.icegiant.entity.gnoll.GnollCircleEntity;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

import javax.annotation.Nullable;

/** The shaman's cursed circle (gnoll_shaman_circle model), drawn translucent and full-bright. */
public class GnollCircleRenderer extends GeoEntityRenderer<GnollCircleEntity> {

    private static final ResourceLocation MODEL = new ResourceLocation(IceGiantMod.MOD_ID, "geo/gnoll_shaman_circle.geo.json");
    private static final ResourceLocation TEXTURE = new ResourceLocation(IceGiantMod.MOD_ID, "textures/entity/gnoll_shaman_circle.png");
    private static final ResourceLocation ANIMATION = new ResourceLocation(IceGiantMod.MOD_ID, "animations/gnoll_shaman_circle.animation.json");

    public GnollCircleRenderer(EntityRendererProvider.Context context) {
        super(context, new GeoModel<>() {
            @Override
            public ResourceLocation getModelResource(GnollCircleEntity animatable) {
                return MODEL;
            }

            @Override
            public ResourceLocation getTextureResource(GnollCircleEntity animatable) {
                return TEXTURE;
            }

            @Override
            public ResourceLocation getAnimationResource(GnollCircleEntity animatable) {
                return ANIMATION;
            }
        });
        this.shadowRadius = 0.0F;
    }

    @Override
    public RenderType getRenderType(GnollCircleEntity animatable, ResourceLocation texture,
                                    @Nullable MultiBufferSource bufferSource, float partialTick) {
        return RenderType.entityTranslucentEmissive(texture);
    }
}
