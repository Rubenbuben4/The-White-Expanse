package net.icegiant.item;

import net.minecraft.world.entity.LivingEntity;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.RawAnimation;

/**
 * Riesen-Runenschwert (Rubenbuben4's model, 2026-10-17): a mod weapon like the axes - so it has their swing
 * (right click), slam (H) and dash (J) - plus its own ground cut (K, SwordGroundCutAbility) and wide sweep
 * (L, SwordSweepAbility). Model, texture and animations: giant_rune_sword.* (tools/convert_weapon.py --tree).
 * The ribbons on the guard wave all the time ("wind" from the glTF, own controller: other bones than the swing).
 */
public class GiantRuneSwordItem extends IceGeoWeaponItem {

    public static final String MODEL_NAME = "giant_rune_sword";
    public static final String WIND_CONTROLLER = "wind_controller";

    private final RawAnimation wind;

    public GiantRuneSwordItem(Properties properties, double totalAttackDamage, double attackSpeedModifier) {
        this(properties, totalAttackDamage, attackSpeedModifier, MODEL_NAME);
    }

    /** A variant with its own model, texture and animations ({@code modelName}, e.g. the 2.0); it needs a ".wind". */
    protected GiantRuneSwordItem(Properties properties, double totalAttackDamage, double attackSpeedModifier, String modelName) {
        super(properties, totalAttackDamage, attackSpeedModifier, modelName);
        this.wind = RawAnimation.begin().thenLoop("animation." + modelName + ".wind");
    }

    /** Does this creature hold the sword (or a variant like the 2.0) in its main hand? */
    public static boolean isHeldBy(LivingEntity entity) {
        return entity.getMainHandItem().getItem() instanceof GiantRuneSwordItem;
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        super.registerControllers(controllers);
        controllers.add(new AnimationController<>(this, WIND_CONTROLLER, 0, state -> state.setAndContinue(this.wind)));
    }
}
