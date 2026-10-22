package net.icegiant.registry;

import net.icegiant.IceGiantMod;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ModParticles {

    public static final DeferredRegister<ParticleType<?>> PARTICLE_TYPES =
            DeferredRegister.create(ForgeRegistries.PARTICLE_TYPES, IceGiantMod.MOD_ID);

    public static final RegistryObject<SimpleParticleType> NORDIC_AXE_SLASH =
            PARTICLE_TYPES.register("nordic_axe_slash", () -> new SimpleParticleType(false));

    // The big slash of the Giant Rune Sword's wide sweep (client/particle/GiantSlashParticle, Rubenbuben4's pictures)
    public static final RegistryObject<SimpleParticleType> GIANT_SLASH =
            PARTICLE_TYPES.register("giant_slash", () -> new SimpleParticleType(true));

    // Fast wind-blown snow of the Frozen Realm snowstorm (BlizzardFlakeParticle)
    public static final RegistryObject<SimpleParticleType> BLIZZARD_FLAKE =
            PARTICLE_TYPES.register("blizzard_flake", () -> new SimpleParticleType(false));
    // Extra ordinary falling snow during the snowstorm (doubles the snowfall)
    public static final RegistryObject<SimpleParticleType> SNOWFALL_FLAKE =
            PARTICLE_TYPES.register("snowfall_flake", () -> new SimpleParticleType(false));

    // Slam smoke now uses vanilla ParticleTypes.CAMPFIRE_COSY_SMOKE directly
    // (see NordicAxeSlamAbility.spawnSmokeBurst), so no custom particle type
    // is registered here anymore.
}
