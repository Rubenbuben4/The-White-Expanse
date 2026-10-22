package net.icegiant.registry;

import net.icegiant.IceGiantMod;
import net.icegiant.worldgen.RiverFeature;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/** World generation features of the mod. */
public class ModFeatures {

    public static final DeferredRegister<Feature<?>> FEATURES =
            DeferredRegister.create(ForgeRegistries.FEATURES, IceGiantMod.MOD_ID);

    /** Frozen Realm rivers (RiverNetwork / RiverFeature); added to the realm's biomes by forge/biome_modifier/rivers.json. */
    public static final RegistryObject<Feature<NoneFeatureConfiguration>> RIVERS =
            FEATURES.register("rivers", () -> new RiverFeature(NoneFeatureConfiguration.CODEC));

    public static final DeferredRegister<net.minecraft.world.level.levelgen.placement.PlacementModifierType<?>> PLACEMENT_MODIFIERS =
            DeferredRegister.create(net.minecraft.core.registries.Registries.PLACEMENT_MODIFIER_TYPE, IceGiantMod.MOD_ID);

    /** "icegiant:frozen_realm_only" - a feature placed with it grows in the Frozen Realm only (ColdSea: kelp, seagrass). */
    public static final RegistryObject<net.minecraft.world.level.levelgen.placement.PlacementModifierType<net.icegiant.worldgen.ColdSea.OnlyHere>> FROZEN_REALM_ONLY =
            PLACEMENT_MODIFIERS.register(net.icegiant.worldgen.ColdSea.ONLY_HERE, () -> () -> net.icegiant.worldgen.ColdSea.OnlyHere.CODEC);
}
