package net.icegiant.registry;

import net.icegiant.IceGiantMod;
import net.icegiant.effect.StunEffect;
import net.minecraft.world.effect.MobEffect;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ModEffects {

    public static final DeferredRegister<MobEffect> EFFECTS =
            DeferredRegister.create(ForgeRegistries.MOB_EFFECTS, IceGiantMod.MOD_ID);

    /** Betäubung nach dem Slam: keine Bewegung, kein Springen. */
    public static final RegistryObject<MobEffect> STUN = EFFECTS.register("stun", StunEffect::new);

    /** Erdbeben: Bildschirm wackelt (nur Kamera, client-seitig), nach einem Slam in der Nähe. */
    public static final RegistryObject<MobEffect> EARTHQUAKE = EFFECTS.register("earthquake",
            net.icegiant.effect.EarthquakeEffect::new);

    /** Niedergeworfen: vom Sturmlauf umgerannt - Spieler liegt (Kriech-/Schwimm-Pose). */
    public static final RegistryObject<MobEffect> KNOCKED_DOWN = EFFECTS.register("knocked_down",
            net.icegiant.effect.KnockedDownEffect::new);

    private ModEffects() {
    }
}
