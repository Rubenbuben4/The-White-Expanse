package net.icegiant.client.armor;

import net.icegiant.IceGiantMod;
import net.icegiant.item.IceGiantArmorItem;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.GeoModel;

/**
 * Worn Ice Giant armor: one model for all four pieces. The bones follow
 * GeckoLib's armor naming (armorHead, armorBody, armorRightArm, armorLeftArm,
 * armorRightLeg, armorLeftLeg, armorRightBoot, armorLeftBoot); GeckoLib hides
 * every bone that doesn't belong to the slot being rendered.
 */
public class IceGiantArmorModel extends GeoModel<IceGiantArmorItem> {

    private static final ResourceLocation MODEL =
            new ResourceLocation(IceGiantMod.MOD_ID, "geo/ice_giant_armor.geo.json");
    static final ResourceLocation TEXTURE =
            new ResourceLocation(IceGiantMod.MOD_ID, "textures/armor/ice_giant_armor.png");

    @Override
    public ResourceLocation getModelResource(IceGiantArmorItem animatable) {
        return MODEL;
    }

    @Override
    public ResourceLocation getTextureResource(IceGiantArmorItem animatable) {
        return TEXTURE;
    }

    @Override
    public ResourceLocation getAnimationResource(IceGiantArmorItem animatable) {
        return null; // static model
    }
}
