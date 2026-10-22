package net.icegiant.block;

import net.minecraft.world.level.block.AbstractGlassBlock;

/**
 * Cloud block: a solid, see-through block (about 60 % transparent, drawn translucent - the
 * transparency lives in the texture, the render type is set in the block model). Like glass it lets
 * light and sky light through and hides the faces between two clouds of the same colour, so a
 * big cloud looks like one soft mass instead of a grid of cubes.
 */
public class CloudBlock extends AbstractGlassBlock {

    public CloudBlock(Properties properties) {
        super(properties);
    }
}
