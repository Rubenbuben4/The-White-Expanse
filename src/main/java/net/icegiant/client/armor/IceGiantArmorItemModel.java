package net.icegiant.client.armor;

import net.icegiant.IceGiantMod;
import net.icegiant.item.IceGiantArmorItem;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.GeoModel;

/**
 * 3D item model for an Ice Giant armor piece (inventory, hand, ground, item frame).
 * Each piece has its own small geo file, centred on the origin, generated from the
 * same Blockbench project as the worn model: geo/ice_giant_{helmet|chestplate|leggings|boots}_item.geo.json
 */
public class IceGiantArmorItemModel extends GeoModel<IceGiantArmorItem> {

    @Override
    public ResourceLocation getModelResource(IceGiantArmorItem animatable) {
        return new ResourceLocation(IceGiantMod.MOD_ID,
                "geo/ice_giant_" + animatable.getType().getName() + "_item.geo.json");
    }

    @Override
    public ResourceLocation getTextureResource(IceGiantArmorItem animatable) {
        return IceGiantArmorModel.TEXTURE;
    }

    @Override
    public ResourceLocation getAnimationResource(IceGiantArmorItem animatable) {
        return null; // static model
    }
}
