package net.icegiant.effect;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;

/**
 * "Erdbeben" - pure client-side screen shake (see client/EarthquakeCameraShake).
 * Given to players near a slam impact (NordicAxeSlamAbility); the amplifier is
 * the shake strength (closer to the impact = stronger). No gameplay effect.
 */
public class EarthquakeEffect extends MobEffect {

    public EarthquakeEffect() {
        super(MobEffectCategory.NEUTRAL, 0x8A7A66);
    }
}
