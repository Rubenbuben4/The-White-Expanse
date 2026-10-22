package net.icegiant.client.armor;

import net.icegiant.item.IceGiantArmorItem;
import software.bernie.geckolib.renderer.GeoArmorRenderer;

/** Renders the Ice Giant armor while it is worn (players, the boss, armor stands). */
public class IceGiantArmorRenderer extends GeoArmorRenderer<IceGiantArmorItem> {

    public IceGiantArmorRenderer() {
        super(new IceGiantArmorModel());
    }
}
