package net.icegiant.worldgen;

import net.icegiant.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoublePlantBlock;
import net.minecraft.world.level.block.GrassBlock;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.VineBlock;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.icegiant.block.SnowyDoublePlantBlock;
import net.icegiant.block.SnowyPlantBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * The snowy look of the Frozen Realm (Rubenbuben4, 2026-10-24; textures from Better Snowy Features by zzzaitegui,
 * MIT - CREDITS.md, the code is our own): after the snow cover, vanilla grass, ferns, tall grass, large ferns and
 * all kinds of leaves, pumpkins and vines become their snowy versions (ModBlocks.SNOWY_*; plants stand in a snow layer
 * where the ground around them has snow), bare ground and rock gets snow on top, and icicles hang under overhangs.
 * <p>
 * Plants and leaves are swapped in this chunk and the 8 around it (trees and grass patches reach over chunk borders,
 * and a neighbour may be decorated after this chunk - its own pass then swaps what it put here). Icicles only in this
 * chunk. Called once per chunk from SnowAndFreezeFeatureMixin, Frozen Realm only - and there only in the cold biomes
 * listed in the biome tag {@code icegiant:snowy_decor} (2026-10-24: later biomes of the realm, e.g. on flying islands or
 * in dungeons, stay as they are unless they are added to the tag).
 */
public final class SnowyDecor {

    // ---- TUNE ----
    static final int SCAN_BELOW = 3, SCAN_ABOVE = 56;       // around the terrain height (trees are up to ~45 high)
    static final double ICICLE_CHANCE = 0.3;               // under an overhang (rock / ice / snow with air below)
    static final int ICICLE_SPAN = 24;                     // overhangs this far above / below the terrain height

    /** Biomes that get the snowy look (data/icegiant/tags/worldgen/biome/snowy_decor.json). */
    public static final net.minecraft.tags.TagKey<net.minecraft.world.level.biome.Biome> SNOWY_BIOMES =
            net.minecraft.tags.TagKey.create(net.minecraft.core.registries.Registries.BIOME,
                    new net.minecraft.resources.ResourceLocation(net.icegiant.IceGiantMod.MOD_ID, "snowy_decor"));

    private SnowyDecor() {
    }

    static boolean snowyBiome(WorldGenRegion level, BlockPos pos) {
        return level.getBiome(pos).is(SNOWY_BIOMES);
    }

    public static void apply(WorldGenRegion level, BlockPos origin) {
        int x0 = origin.getX() & ~15, z0 = origin.getZ() & ~15;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int x = x0 - 16; x < x0 + 32; x++) {
            for (int z = z0 - 16; z < z0 + 32; z++) {
                int ground = level.getHeight(Heightmap.Types.OCEAN_FLOOR_WG, x, z);
                if (!snowyBiome(level, pos.set(x, ground, z))) continue;                  // (the biome on the ground)
                for (int y = ground - SCAN_BELOW; y <= ground + SCAN_ABOVE; y++) {
                    BlockState s = level.getBlockState(pos.set(x, y, z));
                    if (s.isAir()) continue;
                    BlockState n = snowy(s);
                    if (n == null && (s.getBlock() instanceof SnowyPlantBlock || s.getBlock() instanceof SnowyDoublePlantBlock)) {
                        n = s;                                // ours already (a neighbour's pass): its snow may be new
                    }
                    if (n == null) continue;
                    if (n.hasProperty(SnowyPlantBlock.SNOW)) {
                        // plants: in a snow layer where the ground around them has snow (both halves the same)
                        if (n.hasProperty(DoublePlantBlock.HALF) && n.getValue(DoublePlantBlock.HALF) == DoubleBlockHalf.UPPER) continue;
                        n = n.setValue(SnowyPlantBlock.SNOW, n.getValue(SnowyPlantBlock.SNOW) || snowAround(level, x, y, z));
                        if (n.hasProperty(DoublePlantBlock.HALF)) {
                            BlockState up = level.getBlockState(pos.set(x, y + 1, z));
                            BlockState upN = snowy(up) != null ? snowy(up) : up;
                            if (upN.hasProperty(SnowyPlantBlock.SNOW) && upN.hasProperty(DoublePlantBlock.HALF)) {
                                upN = upN.setValue(SnowyPlantBlock.SNOW, n.getValue(SnowyPlantBlock.SNOW));
                                if (upN != up) level.setBlock(pos, upN, 2);
                            }
                            pos.set(x, y, z);
                        }
                    }
                    if (n != s) level.setBlock(pos, n, 2);
                }
            }
        }
        ground(level, pos, x0, z0);
        icicles(level, pos, x0, z0);
    }

    /** Snow layers / snow blocks next to (x, y, z)? */
    private static boolean snowAround(WorldGenRegion level, int x, int y, int z) {
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        for (Direction d : Direction.Plane.HORIZONTAL) {
            BlockState n = level.getBlockState(p.set(x + d.getStepX(), y, z + d.getStepZ()));
            if (n.is(Blocks.SNOW) || n.is(Blocks.SNOW_BLOCK) || n.is(Blocks.POWDER_SNOW)) return true;
        }
        return false;
    }

    /**
     * Bare ground and rock (no snow lying on it - steep places, tree wells, beaches) gets snow on top in this chunk:
     * grass blocks their snowy look, stone, gravel, sand and blue ice become their snowy versions (they drop what
     * they were).
     */
    private static void ground(WorldGenRegion level, BlockPos.MutableBlockPos pos, int x0, int z0) {
        for (int x = x0; x < x0 + 16; x++) {
            for (int z = z0; z < z0 + 16; z++) {
                int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 1;
                BlockState s = level.getBlockState(pos.set(x, top, z));
                if (!snowyBiome(level, pos)) continue;
                BlockState above = level.getBlockState(pos.set(x, top + 1, z));
                if (!(above.isAir() || above.getBlock() instanceof SnowyPlantBlock || above.getBlock() instanceof SnowyDoublePlantBlock)) continue;
                BlockState n = s.is(Blocks.GRASS_BLOCK) ? s.setValue(GrassBlock.SNOWY, true)
                        : s.is(Blocks.STONE) ? ModBlocks.SNOWY_STONE.get().defaultBlockState()
                        : s.is(Blocks.GRAVEL) ? ModBlocks.SNOWY_GRAVEL.get().defaultBlockState()
                        : s.is(Blocks.SAND) ? ModBlocks.SNOWY_SAND.get().defaultBlockState()
                        : s.is(Blocks.BLUE_ICE) ? ModBlocks.SNOWY_BLUE_ICE.get().defaultBlockState() : null;
                if (n != null && n != s) level.setBlock(pos.set(x, top, z), n, 2);
            }
        }
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
        if (s.is(Blocks.OAK_LEAVES)) return leaves(s, ModBlocks.SNOWY_OAK_LEAVES.get());
        if (s.is(Blocks.DARK_OAK_LEAVES)) return leaves(s, ModBlocks.SNOWY_DARK_OAK_LEAVES.get());
        if (s.is(Blocks.ACACIA_LEAVES)) return leaves(s, ModBlocks.SNOWY_ACACIA_LEAVES.get());
        if (s.is(Blocks.JUNGLE_LEAVES)) return leaves(s, ModBlocks.SNOWY_JUNGLE_LEAVES.get());
        if (s.is(Blocks.MANGROVE_LEAVES)) return leaves(s, ModBlocks.SNOWY_MANGROVE_LEAVES.get());
        if (s.is(Blocks.CHERRY_LEAVES)) return leaves(s, ModBlocks.SNOWY_CHERRY_LEAVES.get());
        if (s.is(Blocks.FLOWERING_AZALEA_LEAVES)) return leaves(s, ModBlocks.SNOWY_FLOWERING_AZALEA_LEAVES.get());
        if (s.is(Blocks.PUMPKIN)) return ModBlocks.SNOWY_PUMPKIN.get().defaultBlockState();
        if (s.is(Blocks.VINE)) {
            BlockState v = ModBlocks.SNOWY_VINE.get().defaultBlockState();
            for (net.minecraft.world.level.block.state.properties.BooleanProperty p : new net.minecraft.world.level.block.state.properties.BooleanProperty[]{
                    VineBlock.UP, VineBlock.NORTH, VineBlock.EAST, VineBlock.SOUTH, VineBlock.WEST}) v = v.setValue(p, s.getValue(p));
            return v;
        }
        return null;
    }

    private static BlockState half(BlockState s, Block to) {
        return to.defaultBlockState().setValue(DoublePlantBlock.HALF, s.getValue(DoublePlantBlock.HALF));   // (SNOW: see apply)
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
                    if (!snowyBiome(level, pos.set(x, y, z))) continue;
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
