package net.icegiant.block;

import net.icegiant.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.HalfTransparentBlock;

/**
 * Glimmeis (glow ice, Rubenbuben4 2026-10-22): ice that glows like cave vines with glow berries (light 14). Grows in
 * the Kristallspitzen biome as veins and glowing tips of the ice spires (see CrystalSpires). Slippery and
 * see-through like ice, drawn without shading (it glows), hides the faces between two glow ice blocks, does not melt.
 * Only drops with Silk Touch (loot table), like ice.
 * <p>
 * Its light is cold: ice and snow layers next to it do not melt (GlowIceMeltMixin, since 2026-10-22 - before, the
 * light melted the clear ice of the spires and the snow round them, the whole biome dripped away).
 */
public class GlowIceBlock extends HalfTransparentBlock {

    public static final int LIGHT = 14;
    /**
     * Ice melts in block light above 11 - its own light blocking (1), so up to 3 blocks from glow ice (light 14 - 3),
     * snow layers above 11 (2 blocks): within this many steps (along the axes) glow ice keeps them frozen.
     */
    public static final int COLD_REACH = LIGHT - 11;

    public GlowIceBlock(Properties properties) {
        super(properties);
    }

    /** True if glow ice lies within {@value #COLD_REACH} steps of pos (its light is what would melt the ice there). */
    public static boolean coldLightNear(LevelReader level, BlockPos pos) {
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        int r = COLD_REACH;
        for (int dx = -r; dx <= r; dx++) {
            for (int dy = -(r - Math.abs(dx)); dy <= r - Math.abs(dx); dy++) {
                int rest = r - Math.abs(dx) - Math.abs(dy);
                for (int dz = -rest; dz <= rest; dz++) {
                    if (dx == 0 && dy == 0 && dz == 0) continue;
                    if (level.getBlockState(p.setWithOffset(pos, dx, dy, dz)).is(ModBlocks.GLOW_ICE.get())) return true;
                }
            }
        }
        return false;
    }
}
