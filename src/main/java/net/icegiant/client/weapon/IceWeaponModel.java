package net.icegiant.client.weapon;

import net.icegiant.IceGiantMod;
import net.icegiant.item.IceGeoWeaponItem;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.GeoModel;

/**
 * Resolves model/texture/animation by the weapon's modelName, so every
 * IceGeoWeaponItem (Nordic Axe, Gravebite, ...) shares this one model
 * class instead of needing its own:
 *   geo/{modelName}.geo.json
 *   animations/{modelName}.animation.json
 *   textures/item/{modelName}.png
 */
public class IceWeaponModel extends GeoModel<IceGeoWeaponItem> {

    @Override
    public ResourceLocation getModelResource(IceGeoWeaponItem animatable) {
        return new ResourceLocation(IceGiantMod.MOD_ID, "geo/" + animatable.getModelName() + ".geo.json");
    }

    @Override
    public ResourceLocation getTextureResource(IceGeoWeaponItem animatable) {
        return new ResourceLocation(IceGiantMod.MOD_ID, "textures/item/" + animatable.getModelName() + ".png");
    }

    @Override
    public ResourceLocation getAnimationResource(IceGeoWeaponItem animatable) {
        return new ResourceLocation(IceGiantMod.MOD_ID, "animations/" + animatable.getModelName() + ".animation.json");
    }
}
