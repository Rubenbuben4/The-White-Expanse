package net.icegiant.client.armor;

import net.icegiant.item.IceGiantArmorItem;
import software.bernie.geckolib.renderer.GeoItemRenderer;

/**
 * Item renderer for the Ice Giant armor pieces - replaces the flat inventory icon with
 * the real 3D piece. Positioning/scale per view (GUI, hand, ground, frame) lives in the
 * item model JSONs (models/item/ice_giant_*.json, "display" block).
 */
public class IceGiantArmorItemRenderer extends GeoItemRenderer<IceGiantArmorItem> {

    public IceGiantArmorItemRenderer() {
        super(new IceGiantArmorItemModel());
    }
}
