package net.icegiant.worldgen;

import com.mojang.serialization.Codec;
import net.icegiant.IceGiantMod;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;
import net.minecraftforge.common.Tags;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The Eishöhlen (ice caves) of the Frozen Realm (IceCaves has the shapes): "icegiant:ice_caves", the first feature of
 * the biome (raw generation). For its own chunk it hollows out the big caverns where the biome is, turns part of the
 * rock along their walls into packed and blue ice, and puts in the ice spires standing on the floors and hanging from
 * the ceilings. Every chunk only touches its own 16 x 16 columns; the shapes are a pure function of the seed and the
 * position, so caverns and spires run on seamlessly across chunk borders.
 * <p>
 * Never opens water or lava (a block next to a fluid stays), never comes nearer than IceCaves.ROOF blocks to the
 * ground surface, and only cuts rock, dirt, gravel, ores and the ice of the ore veins.
 */
public class IceCavesFeature extends Feature<NoneFeatureConfiguration> {

    public static final ResourceKey<Biome> BIOME =
            ResourceKey.create(Registries.BIOME, new ResourceLocation(IceGiantMod.MOD_ID, "ice_caves"));

    /** The biome (depth 0.15 - 0.75 in the Frozen Realm's biome source) lies within these Y. */
    static final int Y_MIN = -24, Y_MAX = 100;
    /** Biome cells (4 x 4 x 4) averaged around a spot (+- this many), so the caverns fade out towards the edge. */
    static final int BLUR = 1;

    public IceCavesFeature(Codec<NoneFeatureConfiguration> codec) {
        super(codec);
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
        WorldGenLevel level = context.level();
        long seed = level.getSeed();
        ChunkPos chunk = new ChunkPos(context.origin());
        int x0 = chunk.getMinBlockX(), z0 = chunk.getMinBlockZ();
        Area area = new Area(level);
        if (!area.anyIn(x0, z0)) return false;

        // cavern or not, for the chunk and one block around it (for the walls)
        final int w = 18, h = Y_MAX - Y_MIN + 3;
        boolean[] open = new boolean[w * w * h];
        boolean any = false;
        for (int i = 0; i < w; i++) {
            for (int k = 0; k < w; k++) {
                int x = x0 - 1 + i, z = z0 - 1 + k, top = area.maxY(x, z);
                for (int j = 0; j < h; j++) {
                    int y = Y_MIN - 1 + j;
                    if (y > top) break;
                    boolean c = IceCaves.cavern(seed, x, y, z, area.weight(x, y, z));
                    open[(i * w + k) * h + j] = c;
                    any |= c;
                }
            }
        }
        if (!any) return false;

        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        BlockState air = Blocks.CAVE_AIR.defaultBlockState();
        for (int i = 1; i <= 16; i++) {
            for (int k = 1; k <= 16; k++) {
                int x = x0 - 1 + i, z = z0 - 1 + k;
                for (int j = 1; j < h - 1; j++) {
                    int y = Y_MIN - 1 + j, at = (i * w + k) * h + j;
                    pos.set(x, y, z);
                    if (open[at]) {
                        BlockState s = level.getBlockState(pos);
                        if (cuttable(s) && !nearFluid(level, pos)) level.setBlock(pos, air, 2);
                    } else if (open[at + 1] || open[at - 1] || open[at + h] || open[at - h] || open[at + w * h] || open[at - w * h]) {
                        BlockState s = level.getBlockState(pos);
                        if (!s.is(BlockTags.BASE_STONE_OVERWORLD)) continue;
                        byte m = IceCaves.wall(seed, x, y, z);
                        if (m == IceCaves.PACKED_ICE) level.setBlock(pos, Blocks.PACKED_ICE.defaultBlockState(), 2);
                        else if (m == IceCaves.BLUE_ICE) level.setBlock(pos, Blocks.BLUE_ICE.defaultBlockState(), 2);
                    }
                }
            }
        }

        // the spires (their blocks in this chunk's columns only)
        List<IceCaves.CaveCluster> spires = IceCaves.near(seed, x0, z0, x0 + 15, z0 + 15, new IceCaves.Column() {
            @Override
            public boolean cavern(int x, int y, int z) {
                return IceCaves.cavern(seed, x, y, z, area.weight(x, y, z));
            }

            @Override
            public int minY() {
                return Y_MIN;
            }

            @Override
            public int maxY(int x, int z) {
                return area.maxY(x, z);
            }
        });
        for (int x = x0; x < x0 + 16; x++) {
            for (int z = z0; z < z0 + 16; z++) {
                for (IceCaves.CaveCluster c : spires) {
                    double dx = x + 0.5 - c.cluster().x(), dz = z + 0.5 - c.cluster().z();
                    if (dx * dx + dz * dz > c.cluster().reach() * c.cluster().reach()) continue;
                    int lo = Math.max(c.bottomY(), Y_MIN), hi = Math.min(c.topY(), Y_MAX);
                    for (int y = lo; y <= hi; y++) {
                        byte m = c.blockAt(x, y, z);
                        if (m == CrystalSpires.NONE) continue;
                        pos.set(x, y, z);
                        BlockState s = level.getBlockState(pos);
                        if (!s.isAir() && !cuttable(s)) continue;         // (not into water, bedrock, ...)
                        if (s.isAir() && nearFluid(level, pos)) continue;
                        level.setBlock(pos, switch (m) {
                            case CrystalSpires.PACKED_ICE -> Blocks.PACKED_ICE.defaultBlockState();
                            case CrystalSpires.BLUE_ICE -> Blocks.BLUE_ICE.defaultBlockState();
                            case CrystalSpires.GLOW_ICE -> net.icegiant.registry.ModBlocks.GLOW_ICE.get().defaultBlockState();
                            case CrystalSpires.SNOW -> Blocks.SNOW_BLOCK.defaultBlockState();
                            default -> Blocks.ICE.defaultBlockState();
                        }, 2);
                    }
                }
            }
        }
        // icicles under the rock and ice of the ceilings (2026-10-24)
        BlockState icicle = net.icegiant.registry.ModBlocks.ICICLE.get().defaultBlockState();
        for (int i = 1; i <= 16; i++) {
            for (int k = 1; k <= 16; k++) {
                int x = x0 - 1 + i, z = z0 - 1 + k;
                for (int j = 2; j < h - 2; j++) {
                    int at = (i * w + k) * h + j;
                    if (!open[at] || open[at + 1] || !open[at - 1]) continue;        // the top cavern block, room below
                    int y = Y_MIN - 1 + j;
                    if (Icebergs.hash(seed ^ 0x1C1CA7EL, x, y, z) >= ICICLE_CHANCE) continue;
                    if (!level.getBlockState(pos.set(x, y, z)).isAir() || nearFluid(level, pos)) continue;
                    if (!level.getBiome(pos).is(SnowyDecor.SNOWY_BIOMES)) continue;   // (the caves are in the tag)
                    BlockPos up = pos.above();
                    if (!SnowyDecor.hangsFrom(level.getBlockState(up)) || !level.getBlockState(up).isFaceSturdy(level, up, Direction.DOWN)) continue;
                    level.setBlock(pos, icicle, 2);
                }
            }
        }
        return true;
    }

    /** Share of ceiling blocks with an icicle under them. */
    static final double ICICLE_CHANCE = 0.12;

    /** May a cavern or a spire take this block's place? Rock, dirt, gravel, clay, ores, the veins' packed / blue ice. */
    private static boolean cuttable(BlockState s) {
        return s.is(BlockTags.BASE_STONE_OVERWORLD) || s.is(Tags.Blocks.ORES) || s.is(BlockTags.DIRT)
                || s.is(Blocks.GRAVEL) || s.is(Blocks.CLAY) || s.is(Blocks.PACKED_ICE) || s.is(Blocks.BLUE_ICE);
    }

    private static boolean nearFluid(WorldGenLevel level, BlockPos pos) {
        if (!level.getFluidState(pos).isEmpty()) return true;
        for (Direction d : Direction.values()) {
            if (!level.getFluidState(pos.relative(d)).isEmpty()) return true;
        }
        return false;
    }

    /** Where the biome is (smoothed) and how high the caverns may go, cached for one chunk's work. */
    private static final class Area {
        private final WorldGenLevel level;
        private final Map<Long, Boolean> inBiome = new HashMap<>();
        private final Map<Long, Float> blurred = new HashMap<>();
        private final Map<Long, Integer> tops = new HashMap<>();

        Area(WorldGenLevel level) {
            this.level = level;
        }

        private static long key(int a, int b, int c) {
            return ((long) (a & 0x1FFFFF) << 42) | ((long) (b & 0x1FFFFF) << 21) | (c & 0x1FFFFF);
        }

        /** Is biome cell (quart coordinates) Eishöhlen? */
        private boolean in(int qx, int qy, int qz) {
            return this.inBiome.computeIfAbsent(key(qx, qy, qz), k -> this.level.getNoiseBiome(qx, qy, qz).is(BIOME));
        }

        /** Share of Eishöhlen in the cells around this one. */
        private float blur(int qx, int qy, int qz) {
            return this.blurred.computeIfAbsent(key(qx, qy, qz), k -> {
                int n = 0, t = 0;
                for (int a = -BLUR; a <= BLUR; a++) {
                    for (int b = -BLUR; b <= BLUR; b++) {
                        for (int c = -BLUR; c <= BLUR; c++) {
                            t++;
                            if (in(qx + a, qy + b, qz + c)) n++;
                        }
                    }
                }
                return (float) n / t;
            });
        }

        /** Any Eishöhlen in this chunk (checked every 4 blocks)? */
        boolean anyIn(int x0, int z0) {
            int qx0 = x0 >> 2, qz0 = z0 >> 2;
            for (int qx = qx0; qx < qx0 + 4; qx++) {
                for (int qz = qz0; qz < qz0 + 4; qz++) {
                    for (int qy = Y_MIN >> 2; qy <= Y_MAX >> 2; qy++) {
                        if (in(qx, qy, qz)) return true;
                    }
                }
            }
            return false;
        }

        /** How much Eishöhlen is at this block: 0 outside, 1 well inside, smooth in between. */
        double weight(int x, int y, int z) {
            double fx = (x + 0.5) / 4.0 - 0.5, fy = (y + 0.5) / 4.0 - 0.5, fz = (z + 0.5) / 4.0 - 0.5;
            int ix = (int) Math.floor(fx), iy = (int) Math.floor(fy), iz = (int) Math.floor(fz);
            double tx = fx - ix, ty = fy - iy, tz = fz - iz;
            double v = 0.0;
            for (int a = 0; a <= 1; a++) {
                for (int b = 0; b <= 1; b++) {
                    for (int c = 0; c <= 1; c++) {
                        double f = (a == 0 ? 1 - tx : tx) * (b == 0 ? 1 - ty : ty) * (c == 0 ? 1 - tz : tz);
                        if (f > 0.0) v += f * blur(ix + a, iy + b, iz + c);
                    }
                }
            }
            return Math.max(0.0, Math.min(1.0, (v - 0.25) / 0.6));
        }

        /** The highest Y a cavern may reach in this column (IceCaves.ROOF under the ground, at most Y_MAX). */
        int maxY(int x, int z) {
            return this.tops.computeIfAbsent(key(x, 0, z),
                    k -> Math.min(Y_MAX, this.level.getHeight(Heightmap.Types.OCEAN_FLOOR_WG, x, z) - IceCaves.ROOF));
        }
    }
}
