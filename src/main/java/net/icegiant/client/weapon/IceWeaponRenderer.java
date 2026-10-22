package net.icegiant.client.weapon;

import net.icegiant.item.IceGeoWeaponItem;
import software.bernie.geckolib.renderer.GeoItemRenderer;

/**
 * GeckoLib item renderer shared by every IceGeoWeaponItem. The actual
 * geometry/texture/animation lookup is delegated to IceWeaponModel, which
 * reads it off the item's modelName - so a new weapon (e.g. Gravebite)
 * doesn't need its own renderer class, just its own modelName.
 */
public class IceWeaponRenderer extends GeoItemRenderer<IceGeoWeaponItem> {

    public IceWeaponRenderer() {
        super(new IceWeaponModel());
    }
}
