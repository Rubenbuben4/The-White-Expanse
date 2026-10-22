package net.icegiant.client;

import net.icegiant.IceGiantMod;
import net.icegiant.block.DungeonDoorBlockEntity;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.renderer.GeoBlockRenderer;

/**
 * Draws the dungeon door's GeckoLib model at its bottom middle cell. GeoBlockRenderer turns the
 * model by the block's FACING. Files (made in Blockbench, GeckoLib "Block/Item" model):
 *   geo/dungeon_door.geo.json, animations/dungeon_door.animation.json, textures/block/dungeon_door.png
 * The model's origin (0, 0, 0) is the bottom centre of the middle cell; the door is 48 x 96 pixels.
 */
public class DungeonDoorRenderer extends GeoBlockRenderer<DungeonDoorBlockEntity> {

    private static final ResourceLocation MODEL = new ResourceLocation(IceGiantMod.MOD_ID, "geo/dungeon_door.geo.json");
    private static final ResourceLocation TEXTURE = new ResourceLocation(IceGiantMod.MOD_ID, "textures/block/dungeon_door.png");
    private static final ResourceLocation ANIMATION = new ResourceLocation(IceGiantMod.MOD_ID, "animations/dungeon_door.animation.json");

    public DungeonDoorRenderer(BlockEntityRendererProvider.Context context) {
        super(new GeoModel<>() {
            @Override
            public ResourceLocation getModelResource(DungeonDoorBlockEntity door) {
                return MODEL;
            }

            @Override
            public ResourceLocation getTextureResource(DungeonDoorBlockEntity door) {
                return TEXTURE;
            }

            @Override
            public ResourceLocation getAnimationResource(DungeonDoorBlockEntity door) {
                return ANIMATION;
            }
        });
    }
}
