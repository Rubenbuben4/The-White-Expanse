package net.icegiant.item;

import net.icegiant.client.weapon.IceWeaponRenderer;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;
import software.bernie.geckolib.animatable.GeoItem;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.core.object.PlayState;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.function.Consumer;

/**
 * Waffe mit GeckoLib-Animation. "modelName" bestimmt automatisch, wo Modell/
 * Textur/Animation gesucht werden (siehe IceWeaponModel):
 *   geo/{modelName}.geo.json, animations/{modelName}.animation.json,
 *   textures/item/{modelName}.png
 *
 * Die Schwing-Animation muss in der .animation.json exakt
 * "animation.{modelName}.swing" heißen, sonst greift RawAnimation nicht.
 */
public class IceGeoWeaponItem extends IceWeaponItem implements GeoItem {

    public static final String WEAPON_CONTROLLER = "weapon_controller";
    public static final String SWING_TRIGGER = "swing";
    public static final String SLAM_TRIGGER = "slam";
    // Alte Konstante bleibt als Alias erhalten, falls woanders noch referenziert
    public static final String SWING_CONTROLLER = WEAPON_CONTROLLER;
    public static final String SLAM_CONTROLLER = WEAPON_CONTROLLER;

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    private final String modelName;
    private IceWeaponRenderer renderer;

    public IceGeoWeaponItem(Properties properties, double totalAttackDamage, double attackSpeedModifier, String modelName) {
        super(properties, totalAttackDamage, attackSpeedModifier);
        this.modelName = modelName;
        // WICHTIG (laut GeckoLib-Doku für Items/Singleton-Animatables): ohne diese
        // Registrierung wird triggerAnim() nie zum Client synchronisiert.
        software.bernie.geckolib.animatable.SingletonGeoAnimatable.registerSyncedAnimatable(this);
    }

    public String getModelName() {
        return modelName;
    }

    @Override
    public void initializeClient(Consumer<IClientItemExtensions> consumer) {
        consumer.accept(new IClientItemExtensions() {
            @Override
            public BlockEntityWithoutLevelRenderer getCustomRenderer() {
                if (renderer == null) {
                    renderer = new IceWeaponRenderer();
                }
                return renderer;
            }
        });
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        // WICHTIG: beide Trigger (swing + slam) laufen über EINEN Controller,
        // weil sie dasselbe Bone ("bone") beeinflussen. Zwei getrennte
        // Controller auf demselben Bone überschreiben sich sonst gegenseitig
        // jeden Frame - das war der Grund, warum die Axt "eingefroren" wirkte.
        AnimationController<IceGeoWeaponItem> controller =
                new AnimationController<>(this, WEAPON_CONTROLLER, 5, state -> PlayState.STOP);
        controller.triggerableAnim(SWING_TRIGGER,
                RawAnimation.begin().thenPlay("animation." + modelName + ".swing"));
        controller.triggerableAnim(SLAM_TRIGGER,
                RawAnimation.begin().thenPlay("animation." + modelName + ".slam"));
        controllers.add(controller);
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return this.cache;
    }
}
