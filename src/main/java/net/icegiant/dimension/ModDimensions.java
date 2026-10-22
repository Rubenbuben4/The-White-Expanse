package net.icegiant.dimension;

import net.icegiant.IceGiantMod;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

/**
 * Registry keys for the custom "Frozen Realm" dimension.
 * <p>
 * The dimension itself is data-driven (see
 * data/icegiant/dimension/frozen_realm.json and
 * data/icegiant/dimension_type/frozen_realm.json) — Forge loads it
 * automatically like any other datapack dimension, no code registration
 * needed for the level itself. This class just gives the rest of the mod
 * (the portal block, the teleporter) a typed handle to refer to it.
 */
public final class ModDimensions {

    public static final ResourceLocation FROZEN_REALM_ID =
            new ResourceLocation(IceGiantMod.MOD_ID, "frozen_realm");

    public static final ResourceKey<Level> FROZEN_REALM =
            ResourceKey.create(Registries.DIMENSION, FROZEN_REALM_ID);

    private ModDimensions() {
    }
}
