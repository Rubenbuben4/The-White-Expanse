package net.icegiant.item;

import net.icegiant.client.armor.IceGiantArmorItemRenderer;
import net.icegiant.client.armor.IceGiantArmorRenderer;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;
import software.bernie.geckolib.animatable.GeoItem;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.function.Consumer;

/**
 * One piece of the Ice Giant armor set (helmet / chestplate / leggings / boots).
 * Rendered fully through GeckoLib:
 * <ul>
 *   <li>worn: {@link IceGiantArmorRenderer} with the shared model
 *       {@code geo/ice_giant_armor.geo.json} (GeckoLib shows only the bones of this slot)</li>
 *   <li>inventory / hand / ground / item frame: {@link IceGiantArmorItemRenderer} with the
 *       per-piece model {@code geo/ice_giant_<type>_item.geo.json} - so no 2D icon is needed</li>
 * </ul>
 * All pieces share one texture: {@code textures/armor/ice_giant_armor.png}.
 */
public class IceGiantArmorItem extends ArmorItem implements GeoItem {

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    public IceGiantArmorItem(ArmorMaterial material, Type type, Properties properties) {
        super(material, type, properties);
    }

    /** The full set lets the player grab and throw (PlayerGrabAbility, G). */
    @Override
    public void appendHoverText(ItemStack stack, @javax.annotation.Nullable net.minecraft.world.level.Level level,
                                java.util.List<net.minecraft.network.chat.Component> tooltip, net.minecraft.world.item.TooltipFlag flag) {
        super.appendHoverText(stack, level, tooltip, flag);
        ItemTooltips.key(tooltip, "tooltip.icegiant.grab", "key.icegiant.grab");
    }

    @Override
    public void initializeClient(Consumer<IClientItemExtensions> consumer) {
        consumer.accept(new IClientItemExtensions() {
            private IceGiantArmorRenderer armorRenderer;
            private IceGiantArmorItemRenderer itemRenderer;

            @Override
            public HumanoidModel<?> getHumanoidArmorModel(LivingEntity livingEntity, ItemStack itemStack,
                                                            EquipmentSlot equipmentSlot, HumanoidModel<?> original) {
                if (armorRenderer == null) {
                    armorRenderer = new IceGiantArmorRenderer();
                }
                armorRenderer.prepForRender(livingEntity, itemStack, equipmentSlot, original);
                return armorRenderer;
            }

            @Override
            public BlockEntityWithoutLevelRenderer getCustomRenderer() {
                if (itemRenderer == null) {
                    itemRenderer = new IceGiantArmorItemRenderer();
                }
                return itemRenderer;
            }
        });
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        // static model, no animations
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return this.cache;
    }
}
