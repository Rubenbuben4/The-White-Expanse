package net.icegiant.registry;

import net.icegiant.IceGiantMod;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.decoration.PaintingVariant;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

/**
 * Paintings by Rubenbuben4. Every painting is 48 x 64 pixels = 3 blocks wide, 4 blocks tall
 * (16 pixels per block). Textures: assets/icegiant/textures/painting/<name>.png.
 * They are listed in the minecraft:placeable painting tag, so a normal painting item can pick them
 * when hung on a wall with a 3 x 4 free space, and they show up in the creative tab.
 */
public final class ModPaintings {
    public static final DeferredRegister<PaintingVariant> PAINTINGS =
            DeferredRegister.create(Registries.PAINTING_VARIANT, IceGiantMod.MOD_ID);

    public static final RegistryObject<PaintingVariant> WATERFALL = register("waterfall");
    public static final RegistryObject<PaintingVariant> GLOWING_TREE = register("glowing_tree");
    public static final RegistryObject<PaintingVariant> BARN_OWL = register("barn_owl");
    public static final RegistryObject<PaintingVariant> FOX = register("fox");
    public static final RegistryObject<PaintingVariant> RIVER_AT_DUSK = register("river_at_dusk");
    public static final RegistryObject<PaintingVariant> RUNE_GATE = register("rune_gate");
    public static final RegistryObject<PaintingVariant> WOLF = register("wolf");
    public static final RegistryObject<PaintingVariant> FOREST_PATH = register("forest_path");
    public static final RegistryObject<PaintingVariant> GLOWING_MUSHROOMS = register("glowing_mushrooms");
    public static final RegistryObject<PaintingVariant> STAG = register("stag");

    private static RegistryObject<PaintingVariant> register(String name) {
        return PAINTINGS.register(name, () -> new PaintingVariant(48, 64));
    }

    private ModPaintings() {}
}
