package net.icegiant.worldgen;

import net.icegiant.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoublePlantBlock;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * The snowy look of the Frozen Realm (Rubenbuben4, 2026-10-24; textures from Better Snowy Features by zzzaitegui,
 * MIT - CREDITS.md, the code is our own): after the snow cover, vanilla grass, ferns, tall grass, large ferns and
 * spruce / birch / azalea leaves become their snowy versions (ModBlocks.SNOWY_*), and icicles hang under overhangs.
 * <p>
 * Plants and leaves are swapped in this chunk and the 8 around it (trees and grass patches reach over chunk borders,
 * and a neighbour may be decorated after this chunk - its own pass then swaps what it put here). Icicles only in this
 * chunk. Called once per chunk from SnowAndFreezeFeatureMixin, Frozen Realm only.
 */
public final class SnowyDecor {

    // ---- TUNE ----
    static final int SCAN_BELOW = 3, SCAN_ABOVE = 56;       // around the terrain height (trees are up to ~45 high)
    static final double ICICLE_CHANCE = 0.3;               // under an overhang (rock / ice / snow with air below)
    static final int ICICLE_SPAN = 24;                     // overhangs this far above / below the terrain height

    private SnowyDecor() {
    }

    public static void apply(WorldGenRegion level, BlockPos origin) {
        int x0 = origin.getX() & ~15, z0 = origin.getZ() & ~15;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int x = x0 - 16; x < x0 + 32; x++) {
            for (int z = z0 - 16; z < z0 + 32; z++) {
                int ground = level.getHeight(Heightmap.Types.OCEAN_FLOOR_WG, x, z);
                for (int y = ground - SCAN_BELOW; y <= ground + SCAN_ABOVE; y++) {
                    BlockState s = level.getBlockState(pos.set(x, y, z));
                    if (s.isAir()) continue;
                    BlockState n = snowy(s);
                    if (n != null) level.setBlock(pos, n, 2);
                }
            }
        }
        icicles(level, pos, x0, z0);
    }

    /** The snowy version of a vanilla plant / leaves block (same half, distance, persistence), or null. */
    static BlockState snowy(BlockState s) {
        if (s.is(Blocks.GRASS)) return ModBlocks.SNOWY_GRASS.get().defaultBlockState();
        if (s.is(Blocks.FERN)) return ModBlocks.SNOWY_FERN.get().defaultBlockState();
        if (s.is(Blocks.TALL_GRASS)) return half(s, ModBlocks.SNOWY_TALL_GRASS.get());
        if (s.is(Blocks.LARGE_FERN)) return half(s, ModBlocks.SNOWY_LARGE_FERN.get());
        if (s.is(Blocks.SPRUCE_LEAVES)) return leaves(s, ModBlocks.SNOWY_SPRUCE_LEAVES.get());
        if (s.is(Blocks.BIRCH_LEAVES)) return leaves(s, ModBlocks.SNOWY_BIRCH_LEAVES.get());
        if (s.is(Blocks.AZALEA_LEAVES)) return leaves(s, ModBlocks.SNOWY_AZALEA_LEAVES.get());
        return null;
    }

    private static BlockState half(BlockState s, Block to) {
        return to.defaultBlockState().setValue(DoublePlantBlock.HALF, s.getValue(DoublePlantBlock.HALF));
    }

    private static BlockState leaves(BlockState s, Block to) {
        return to.defaultBlockState()
                .setValue(LeavesBlock.DISTANCE, s.getValue(LeavesBlock.DISTANCE))
                .setValue(LeavesBlock.PERSISTENT, s.getValue(LeavesBlock.PERSISTENT))
                .setValue(LeavesBlock.WATERLOGGED, s.getValue(LeavesBlock.WATERLOGGED));
    }

    /** Icicles under overhangs of rock, ice and snow near the surface (above the sea), in this chunk. */
    private static void icicles(WorldGenRegion level, BlockPos.MutableBlockPos pos, int x0, int z0) {
        long seed = level.getSeed() ^ 0x1C1C1EL;
        BlockState icicle = ModBlocks.ICICLE.get().defaultBlockState();
        for (int x = x0; x < x0 + 16; x++) {
            for (int z = z0; z < z0 + 16; z++) {
                int ground = level.getHeight(Heightmap.Types.OCEAN_FLOOR_WG, x, z);
                int lo = Math.max(65, ground - ICICLE_SPAN), hi = ground + ICICLE_SPAN;
                for (int y = hi; y >= lo; y--) {
                    if (!level.getBlockState(pos.set(x, y, z)).isAir()) continue;
                    BlockState above = level.getBlockState(pos.set(x, y + 1, z));
                    if (!hangsFrom(above)) continue;
                    if (!level.getBlockState(pos.set(x, y - 1, z)).isAir()) continue;     // room for it
                    if (!above.isFaceSturdy(level, pos.set(x, y + 1, z), Direction.DOWN)) continue;
                    if (Icebergs.hash(seed, x, y, z) >= ICICLE_CHANCE) continue;
                    level.setBlock(pos.set(x, y, z), icicle, 2);
                    break;                                                            // one per column
                }
            }
        }
    }

    /** Icicles hang from rock, ice and snow (not from leaves, logs, plants). */
    static boolean hangsFrom(BlockState s) {
        return s.is(BlockTags.BASE_STONE_OVERWORLD) || s.is(Blocks.PACKED_ICE) || s.is(Blocks.BLUE_ICE) || s.is(Blocks.ICE)
                || s.is(Blocks.SNOW_BLOCK) || s.is(BlockTags.DIRT) || s.is(Blocks.COBBLESTONE) || s.is(Blocks.GRAVEL)
                || s.is(ModBlocks.GLOW_ICE.get());
    }
}
