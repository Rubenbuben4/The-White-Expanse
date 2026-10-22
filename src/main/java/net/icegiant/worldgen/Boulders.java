package net.icegiant.worldgen;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SnowLayerBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * Small rounded boulders (glacial erratics) in the Frozen Realm's forests and on its beaches.
 * <p>
 * Forest: at most one little group per chunk - one stone, sometimes two or three that overlap and
 * grow into each other. Gravel beaches: a few tries per chunk, so single stones (now and then a pair)
 * lie scattered along the shore. Each stone is a slightly squashed, bumpy ball that sits partly sunk into the ground,
 * made of a grey rock mix, with a thin snow cap on top. Runs right after the snow cover (SnowCover),
 * so its snow is not painted over, and stays inside the chunk being decorated.
 */
public final class Boulders {

    // ---- TUNE ----
    static final float FOREST_CHANCE = 0.16F;    // chance per chunk in the lowland forest (snowy_taiga)
    static final float GROVE_CHANCE = 0.07F;     // higher, sparser forest (grove)
    static final float BEACH_CHANCE = 0.22F;     // per try on the gravel beaches
    static final int BEACH_TRIES = 4;            // beaches get several tries per chunk: stones scattered along it
    static final float TWO_STONES = 0.30F, THREE_STONES = 0.12F;   // how often a group has 2 / 3 stones
    static final double MIN_RADIUS = 1.2, MAX_RADIUS = 2.6;        // forest stones (blocks)
    static final double BEACH_MIN_RADIUS = 0.9, BEACH_MAX_RADIUS = 2.0;
    static final double SINK = 0.35;             // part of the height that sits below the ground
    static final double BUMPS = 0.18;            // how lumpy the surface is (0 = perfect ellipsoid)

    private Boulders() {
    }

    /** Within a Gnoll camp (or right at its edge): no boulders, no fallen trunks. */
    private static boolean inCamp(java.util.List<net.icegiant.worldgen.camp.GnollCamps.Camp> camps, int x, int z) {
        for (net.icegiant.worldgen.camp.GnollCamps.Camp c : camps) if (c.contains(x, z, 8)) return true;
        return false;
    }

    /** Called once per chunk in the Frozen Realm (after SnowCover); origin = the chunk's min corner. */
    public static void place(WorldGenRegion level, BlockPos origin) {
        int x0 = origin.getX(), z0 = origin.getZ();
        RandomSource random = RandomSource.create(level.getSeed()
                ^ (x0 * 341873128712L + z0 * 132897987541L) ^ 0x5B0D1E5L);
        boolean forestDone = false;
        java.util.List<net.icegiant.worldgen.camp.GnollCamps.Camp> camps =
                net.icegiant.worldgen.camp.GnollCamps.campsAt(level, new net.minecraft.world.level.ChunkPos(origin));
        for (int t = 0; t < BEACH_TRIES; t++) {
            int cx = x0 + 3 + random.nextInt(10), cz = z0 + 3 + random.nextInt(10);
            if (inCamp(camps, cx, cz)) continue;                                  // no boulders in a Gnoll camp
            int gy = groundY(level, cx, cz);
            if (gy == Integer.MIN_VALUE) continue;
            Holder<Biome> biome = level.getBiome(new BlockPos(cx, gy, cz));
            boolean beach = biome.is(Biomes.SNOWY_BEACH);
            if (!beach && forestDone) continue;                     // forests: only one try per chunk
            forestDone |= !beach;
            float chance = beach ? BEACH_CHANCE : biome.is(Biomes.SNOWY_TAIGA) ? FOREST_CHANCE
                    : biome.is(Biomes.GROVE) ? GROVE_CHANCE : 0.0F;
            if (random.nextFloat() >= chance) continue;
            if (beach) {
                // beach: mostly single stones scattered along the shore, sometimes a pair
                double r = BEACH_MIN_RADIUS + random.nextDouble() * (BEACH_MAX_RADIUS - BEACH_MIN_RADIUS);
                stone(level, random, x0, z0, cx + 0.5, cz + 0.5, r, true);
                if (random.nextFloat() < 0.25F) {
                    double a = random.nextDouble() * Math.PI * 2.0, r2 = r * 0.7;
                    stone(level, random, x0, z0, cx + 0.5 + Math.cos(a) * (r + r2) * 0.6,
                            cz + 0.5 + Math.sin(a) * (r + r2) * 0.6, r2, true);
                }
            } else {
                group(level, random, x0, z0, cx, cz);
            }
        }
    }

    /** Forest: one stone, sometimes two or three that overlap. */
    private static void group(WorldGenRegion level, RandomSource random, int x0, int z0, int cx, int cz) {
        float roll = random.nextFloat();
        int count = roll < THREE_STONES ? 3 : roll < THREE_STONES + TWO_STONES ? 2 : 1;
        double minR = MIN_RADIUS, maxR = MAX_RADIUS;
        double x = cx + 0.5, z = cz + 0.5, prevR = 0.0;
        for (int k = 0; k < count; k++) {
            double r = minR + random.nextDouble() * (maxR - minR);
            if (k > 0) {
                r *= 0.65 + 0.25 * random.nextDouble();                 // neighbours a bit smaller
                double angle = random.nextDouble() * Math.PI * 2.0;
                double dist = (prevR + r) * (0.55 + 0.2 * random.nextDouble());   // overlapping
                x += Math.cos(angle) * dist;
                z += Math.sin(angle) * dist;
            }
            prevR = r;
            stone(level, random, x0, z0, x, z, r, false);
        }
    }

    /** One bumpy, squashed ball of rock; skipped if it would leave the chunk or hit a tree. */
    private static void stone(WorldGenRegion level, RandomSource random, int x0, int z0,
                              double x, double z, double r, boolean beach) {
        double rx = r * (0.85 + 0.3 * random.nextDouble());
        double rz = r * (0.85 + 0.3 * random.nextDouble());
        double ry = r * (0.75 + 0.2 * random.nextDouble());
        int minX = (int) Math.floor(x - rx), maxX = (int) Math.floor(x + rx);
        int minZ = (int) Math.floor(z - rz), maxZ = (int) Math.floor(z + rz);
        if (minX < x0 || maxX > x0 + 15 || minZ < z0 || maxZ > z0 + 15) return;   // keep inside this chunk

        int gy = groundY(level, (int) Math.floor(x), (int) Math.floor(z));
        if (gy == Integer.MIN_VALUE) return;
        double y = gy + ry * (1.0 - 2.0 * SINK);                       // centre: partly sunk in
        int minY = (int) Math.floor(y - ry), maxY = (int) Math.floor(y + ry);

        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        RiverNetwork.ChunkData rivers = RiverFeature.chunkData(level.getLevel(), new net.minecraft.world.level.ChunkPos(x0 >> 4, z0 >> 4));
        for (int bx = minX - 1; bx <= maxX + 1; bx++) {                // never on or over a river, lake or its rim
            for (int bz = minZ - 1; bz <= maxZ + 1; bz++) {
                if (bx < x0 || bx > x0 + 15 || bz < z0 || bz > z0 + 15) continue;
                if (rivers != null) {
                    int i = (bx - x0) * 16 + (bz - z0);
                    byte k = rivers.kind[i];
                    if (k != RiverNetwork.ChunkData.NONE && !(k == RiverNetwork.ChunkData.NEAR && rivers.edge[i] > 2.0F)) return;
                }
                for (int by = minY - 1; by <= maxY + 1; by++) {
                    if (!level.getFluidState(pos.set(bx, by, bz)).isEmpty()) return;
                }
            }
        }
        for (int bx = minX; bx <= maxX; bx++) {                        // no stones through tree trunks
            for (int bz = minZ; bz <= maxZ; bz++) {
                for (int by = minY; by <= maxY + 1; by++) {
                    if (level.getBlockState(pos.set(bx, by, bz)).is(BlockTags.LOGS)) return;
                }
            }
        }
        long salt = random.nextLong();
        for (int bx = minX; bx <= maxX; bx++) {
            for (int bz = minZ; bz <= maxZ; bz++) {
                int top = Integer.MIN_VALUE;
                for (int by = minY; by <= maxY; by++) {
                    double dx = (bx + 0.5 - x) / rx, dy = (by + 0.5 - y) / ry, dz = (bz + 0.5 - z) / rz;
                    double d = dx * dx + dy * dy + dz * dz + BUMPS * (hash01(bx, by, bz, salt) - 0.5);
                    if (d > 1.0) continue;
                    pos.set(bx, by, bz);
                    BlockState here = level.getBlockState(pos);
                    if (!canReplace(here)) continue;
                    level.setBlock(pos, rock(bx, by, bz, salt, beach), 2);
                    top = by;
                }
                if (top != Integer.MIN_VALUE && top >= gy) {             // thin snow cap on the visible top
                    pos.set(bx, top + 1, bz);
                    BlockState above = level.getBlockState(pos);
                    if (above.isAir() || above.is(Blocks.SNOW)) {
                        double dx = (bx + 0.5 - x) / rx, dz = (bz + 0.5 - z) / rz;
                        int layers = dx * dx + dz * dz < 0.35 ? 2 : 1;
                        level.setBlock(pos, Blocks.SNOW.defaultBlockState().setValue(SnowLayerBlock.LAYERS, layers), 2);
                    }
                }
            }
        }
    }

    /** Y of the first free block above solid ground (through snow layers, plants and leaves), or MIN_VALUE. */
    private static int groundY(WorldGenRegion level, int x, int z) {
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z), z);
        for (int i = 0; i < 48; i++) {
            pos.move(0, -1, 0);
            BlockState st = level.getBlockState(pos);
            if (st.isAir() || st.is(Blocks.SNOW) || st.is(BlockTags.LEAVES) || (st.canBeReplaced() && st.getFluidState().isEmpty())) continue;
            boolean ground = st.is(BlockTags.DIRT) || st.is(Blocks.SNOW_BLOCK) || st.is(BlockTags.SAND)
                    || st.is(Blocks.GRAVEL) || st.is(Blocks.STONE) || st.is(Blocks.ANDESITE) || st.is(Blocks.POWDER_SNOW);
            return ground ? pos.getY() + 1 : Integer.MIN_VALUE;          // water, ice, logs: no stone
        }
        return Integer.MIN_VALUE;
    }

    private static boolean canReplace(BlockState st) {
        if (!st.getFluidState().isEmpty()) return false;                  // never into rivers or the sea
        return st.isAir() || st.canBeReplaced() || st.is(BlockTags.DIRT) || st.is(Blocks.SNOW_BLOCK)
                || st.is(BlockTags.SAND) || st.is(Blocks.GRAVEL) || st.is(Blocks.STONE) || st.is(Blocks.ANDESITE)
                || st.is(Blocks.POWDER_SNOW) || st.is(BlockTags.LEAVES);
    }

    /** Grey rock mix: mostly stone and andesite, a few cobbles (and a little moss in the forest). */
    private static BlockState rock(int x, int y, int z, long salt, boolean beach) {
        double v = hash01(x >> 1, y, z >> 1, salt ^ 0x77L) * 0.6 + hash01(x, y, z, salt ^ 0x99L) * 0.4;
        if (v < 0.50) return Blocks.STONE.defaultBlockState();
        if (v < 0.78) return Blocks.ANDESITE.defaultBlockState();
        if (v < 0.90 || beach) return Blocks.COBBLESTONE.defaultBlockState();
        return Blocks.MOSSY_COBBLESTONE.defaultBlockState();
    }

    private static double hash01(int x, int y, int z, long salt) {
        long h = x * 73856093L ^ y * 19349663L ^ z * 83492791L ^ salt;
        h = (h ^ (h >>> 13)) * 1274126177L;
        h ^= h >>> 16;
        return (h & 0xffff) / 65535.0;
    }

    // ---- TUNE: fallen trees along the rivers ----
    static final float LOG_CHANCE = 0.18F;           // chance per chunk next to a river
    static final int LOG_MIN = 3, LOG_MAX = 6;       // length in blocks

    /**
     * Now and then a fallen spruce trunk lies on a river bank (1-4 blocks from the gravel rim), roughly along
     * the river, with a thin layer of snow on it. Called after the snow cover, inside this chunk only.
     */
    public static void riverLogs(WorldGenRegion level, BlockPos origin) {
        RiverNetwork.ChunkData d = RiverFeature.chunkData(level.getLevel(), new net.minecraft.world.level.ChunkPos(origin));
        if (d == null) return;
        int x0 = origin.getX(), z0 = origin.getZ();
        RandomSource random = RandomSource.create(level.getSeed() ^ (x0 * 73428767L + z0 * 912931L) ^ 0x10C5L);
        if (random.nextFloat() >= LOG_CHANCE) return;
        java.util.List<Integer> spots = new java.util.ArrayList<>();
        for (int i = 0; i < 256; i++) {
            if (d.kind[i] == RiverNetwork.ChunkData.NEAR && d.edge[i] >= 1.0F && d.edge[i] <= 4.0F && d.gorge[i] <= 0.0F) spots.add(i);
        }
        if (spots.isEmpty()) return;
        int i0 = spots.get(random.nextInt(spots.size()));
        double a = d.angle[i0] + (random.nextDouble() - 0.5) * 0.8;     // roughly along the river
        boolean alongX = Math.abs(Math.cos(a)) >= Math.abs(Math.sin(a));
        int len = LOG_MIN + random.nextInt(LOG_MAX - LOG_MIN + 1);
        int sx = i0 / 16, sz = i0 % 16;
        int gy = groundY(level, x0 + sx, z0 + sz);
        if (gy == Integer.MIN_VALUE) return;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int k = 0; k < len; k++) {                                   // check the whole trunk first
            int lx = sx + (alongX ? k : 0), lz = sz + (alongX ? 0 : k);
            if (lx > 15 || lz > 15) return;
            byte kind = d.kind[lx * 16 + lz];
            if (kind == RiverNetwork.ChunkData.CHANNEL || kind == RiverNetwork.ChunkData.RIM
                    || kind == RiverNetwork.ChunkData.LAKE) return;
            if (Math.abs(groundY(level, x0 + lx, z0 + lz) - gy) > 1) return;   // fairly flat ground only
            BlockState here = level.getBlockState(pos.set(x0 + lx, gy, z0 + lz));
            if (!(here.isAir() || here.is(Blocks.SNOW)) || !here.getFluidState().isEmpty()) return;
        }
        BlockState log = Blocks.SPRUCE_LOG.defaultBlockState().setValue(net.minecraft.world.level.block.RotatedPillarBlock.AXIS,
                alongX ? net.minecraft.core.Direction.Axis.X : net.minecraft.core.Direction.Axis.Z);
        for (int k = 0; k < len; k++) {
            int x = x0 + sx + (alongX ? k : 0), z = z0 + sz + (alongX ? 0 : k);
            level.setBlock(pos.set(x, gy, z), log, 2);
            if (level.getBlockState(pos.set(x, gy + 1, z)).isAir()) {
                level.setBlock(pos, Blocks.SNOW.defaultBlockState().setValue(SnowLayerBlock.LAYERS, 1 + random.nextInt(2)), 2);
            }
        }
    }

    // ---- TUNE: fallen trees in the forest ----
    static final float FOREST_LOG_CHANCE = 0.12F;    // chance per chunk in the lowland forest
    static final float GROVE_LOG_CHANCE = 0.06F;     // higher, sparser forest
    static final float STUMP_CHANCE = 0.5F;          // a stump where the tree broke off

    /**
     * Now and then a fallen spruce lies on the forest floor: a trunk 3-6 blocks long on flat ground, sometimes
     * with the stump it broke off from, a thin layer of snow on it and a few mushrooms beside it. Not near rivers
     * (they have their own logs). Called after the snow cover, inside this chunk only.
     */
    public static void forestLogs(WorldGenRegion level, BlockPos origin) {
        int x0 = origin.getX(), z0 = origin.getZ();
        RandomSource random = RandomSource.create(level.getSeed() ^ (x0 * 51327911L + z0 * 7215661L) ^ 0xF0A1L);
        int sx = 2 + random.nextInt(12), sz = 2 + random.nextInt(12);
        if (inCamp(net.icegiant.worldgen.camp.GnollCamps.campsAt(level, new net.minecraft.world.level.ChunkPos(origin)), x0 + sx, z0 + sz)) return;
        int gy = groundY(level, x0 + sx, z0 + sz);
        if (gy == Integer.MIN_VALUE) return;
        Holder<Biome> biome = level.getBiome(new BlockPos(x0 + sx, gy, z0 + sz));
        float chance = biome.is(Biomes.SNOWY_TAIGA) ? FOREST_LOG_CHANCE : biome.is(Biomes.GROVE) ? GROVE_LOG_CHANCE : 0.0F;
        if (random.nextFloat() >= chance) return;
        RiverNetwork.ChunkData d = RiverFeature.chunkData(level.getLevel(), new net.minecraft.world.level.ChunkPos(origin));
        boolean alongX = random.nextBoolean();
        int dir = random.nextBoolean() ? 1 : -1;
        int len = LOG_MIN + random.nextInt(LOG_MAX - LOG_MIN + 1);
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        // the whole trunk (and the stump spot before it) must fit on flat, open ground in this chunk
        for (int k = -1; k < len; k++) {
            int lx = sx + (alongX ? k * dir : 0), lz = sz + (alongX ? 0 : k * dir);
            if (lx < 0 || lz < 0 || lx > 15 || lz > 15) return;
            if (d != null && d.kind[lx * 16 + lz] != RiverNetwork.ChunkData.NONE) return;
            if (groundY(level, x0 + lx, z0 + lz) != gy) return;
            BlockState here = level.getBlockState(pos.set(x0 + lx, gy, z0 + lz));
            if (!(here.isAir() || here.is(Blocks.SNOW) || (here.canBeReplaced() && here.getFluidState().isEmpty()))) return;
            if (level.getBlockState(pos.set(x0 + lx, gy + 1, z0 + lz)).is(BlockTags.LOGS)) return;
        }
        BlockState log = Blocks.SPRUCE_LOG.defaultBlockState().setValue(net.minecraft.world.level.block.RotatedPillarBlock.AXIS,
                alongX ? net.minecraft.core.Direction.Axis.X : net.minecraft.core.Direction.Axis.Z);
        for (int k = 0; k < len; k++) {
            int x = x0 + sx + (alongX ? k * dir : 0), z = z0 + sz + (alongX ? 0 : k * dir);
            level.setBlock(pos.set(x, gy, z), log, 2);
            pos.set(x, gy + 1, z);
            BlockState above = level.getBlockState(pos);
            if (above.isAir() || (above.canBeReplaced() && above.getFluidState().isEmpty())) {
                level.setBlock(pos, Blocks.SNOW.defaultBlockState().setValue(SnowLayerBlock.LAYERS, 1 + random.nextInt(2)), 2);
            }
        }
        if (random.nextFloat() < STUMP_CHANCE) {                               // the stump it broke off from
            int x = x0 + sx - (alongX ? dir : 0), z = z0 + sz - (alongX ? 0 : dir);
            level.setBlock(pos.set(x, gy, z), Blocks.SPRUCE_LOG.defaultBlockState(), 2);
            pos.set(x, gy + 1, z);
            BlockState above = level.getBlockState(pos);
            if (above.isAir() || (above.canBeReplaced() && above.getFluidState().isEmpty())) {
                level.setBlock(pos, Blocks.SNOW.defaultBlockState().setValue(SnowLayerBlock.LAYERS, 1), 2);
            }
        }
        for (int m = 0; m < 3; m++) {                                          // mushrooms beside the trunk
            if (random.nextFloat() >= 0.35F) continue;
            int k = random.nextInt(len), side = random.nextBoolean() ? 1 : -1;
            int lx = sx + (alongX ? k * dir : side), lz = sz + (alongX ? side : k * dir);
            if (lx < 0 || lz < 0 || lx > 15 || lz > 15) continue;
            int x = x0 + lx, z = z0 + lz;
            if (groundY(level, x, z) != gy) continue;
            BlockState here = level.getBlockState(pos.set(x, gy, z));
            if (!(here.isAir() || here.is(Blocks.SNOW))) continue;
            BlockState floor = level.getBlockState(pos.set(x, gy - 1, z));
            if (!(floor.is(BlockTags.DIRT) || floor.is(Blocks.SNOW_BLOCK))) continue;
            level.setBlock(pos.set(x, gy, z), Blocks.BROWN_MUSHROOM.defaultBlockState(), 2);
        }
    }

    /**
     * No floating plants: two-block plants (large ferns, tall grass, ...) whose other half was buried by snow,
     * cut by a river or replaced by a stone lose the leftover half, and plants (also whole tall ones) with no
     * ground to grow on any more - dug away by a river, turned to scree - are removed (not the mushrooms on
     * snow, they are placed there on purpose). Runs last, over the whole column, inside this chunk only.
     */
    public static void fixTallPlants(WorldGenRegion level, BlockPos origin) {
        int x0 = origin.getX(), z0 = origin.getZ();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos(), other = new BlockPos.MutableBlockPos();
        net.minecraft.world.level.block.state.properties.EnumProperty<net.minecraft.world.level.block.state.properties.DoubleBlockHalf> half =
                net.minecraft.world.level.block.state.properties.BlockStateProperties.DOUBLE_BLOCK_HALF;
        for (int lx = 0; lx < 16; lx++) {
            for (int lz = 0; lz < 16; lz++) {
                int x = x0 + lx, z = z0 + lz;
                int top = level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z);
                int bottom = level.getHeight(Heightmap.Types.OCEAN_FLOOR, x, z) - 2;
                for (int y = top; y >= bottom; y--) {
                    BlockState st = level.getBlockState(pos.set(x, y, z));
                    // a plant with nothing to grow on (the ground under it was dug away): gone, both halves
                    if (st.getBlock() instanceof net.minecraft.world.level.block.BushBlock
                            && !(st.getBlock() instanceof net.minecraft.world.level.block.MushroomBlock) && !st.canSurvive(level, pos)) {
                        BlockState upper = level.getBlockState(other.set(x, y + 1, z));
                        if (upper.is(st.getBlock())) {
                            level.setBlock(other, (upper.getFluidState().isEmpty() ? Blocks.AIR : Blocks.WATER).defaultBlockState(), 2);
                        }
                        level.setBlock(pos, (st.getFluidState().isEmpty() ? Blocks.AIR : Blocks.WATER).defaultBlockState(), 2);
                        continue;
                    }
                    if (!st.hasProperty(half) || st.getBlock() instanceof net.minecraft.world.level.block.DoorBlock) continue;
                    boolean upper = st.getValue(half) == net.minecraft.world.level.block.state.properties.DoubleBlockHalf.UPPER;
                    BlockState partner = level.getBlockState(other.set(x, upper ? y - 1 : y + 1, z));
                    if (partner.is(st.getBlock()) && partner.hasProperty(half) && partner.getValue(half) != st.getValue(half)) continue;
                    level.setBlock(pos, (st.getFluidState().isEmpty() ? Blocks.AIR : Blocks.WATER).defaultBlockState(), 2);
                }
            }
        }
    }

    // ---- TUNE: stones in the river ----
    static final float RIVER_STONE_CHANCE = 0.3F;    // chance per river chunk
    static final float STONE_OUT_OF_WATER = 0.6F;    // the middle block sticks out of the water

    /**
     * Now and then a stone lies in a wider river (never at its edge, so it can't dam a small stream): one or two
     * columns of rock from the bed to the surface, the middle one often sticking out with a little snow on it.
     */
    public static void riverStones(WorldGenRegion level, BlockPos origin) {
        RiverNetwork.ChunkData d = RiverFeature.chunkData(level.getLevel(), new net.minecraft.world.level.ChunkPos(origin));
        if (d == null) return;
        int x0 = origin.getX(), z0 = origin.getZ();
        RandomSource random = RandomSource.create(level.getSeed() ^ (x0 * 19349663L + z0 * 83492791L) ^ 0x57A1EL);
        if (random.nextFloat() >= RIVER_STONE_CHANCE) return;
        java.util.List<Integer> spots = new java.util.ArrayList<>();
        for (int i = 0; i < 256; i++) {
            if (d.kind[i] == RiverNetwork.ChunkData.CHANNEL && d.edge[i] < -2.5F && d.depth[i] >= 1) spots.add(i);
        }
        if (spots.isEmpty()) return;
        int i0 = spots.get(random.nextInt(spots.size()));
        int lx0 = i0 / 16, lz0 = i0 % 16;
        long salt = random.nextLong();
        int extra = random.nextInt(2);                                           // 0-1 more column next to it
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int c = 0; c <= extra; c++) {
            int lx = lx0 + (c == 1 ? 1 : 0), lz = lz0;
            if (lx > 15 || lz > 15) continue;
            int i = lx * 16 + lz;
            if (d.kind[i] != RiverNetwork.ChunkData.CHANNEL || d.edge[i] > -2.0F) continue;
            int x = x0 + lx, z = z0 + lz;
            boolean open = true;                                                 // water all around it (2 blocks)
            for (int a = -2; a <= 2 && open; a++) {
                for (int b = -2; b <= 2 && open; b++) {
                    if (Math.abs(a) + Math.abs(b) != 2 && Math.abs(a) + Math.abs(b) != 1) continue;
                    int nx = lx + a, nz = lz + b;
                    if (nx < 0 || nz < 0 || nx > 15 || nz > 15) { open = false; continue; }
                    if ((a == 1 && b == 0 && c == 0 && extra > 0) || (c == 1 && a == -1 && b == 0)) continue;   // its partner
                    open = d.kind[nx * 16 + nz] == RiverNetwork.ChunkData.CHANNEL;
                }
            }
            if (!open) continue;
            int surface = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z) - 1;
            if (level.getFluidState(pos.set(x, surface, z)).isEmpty()) continue;   // ice, gravel bar, ...
            int bed = surface;
            while (bed > surface - 8 && !level.getFluidState(pos.set(x, bed, z)).isEmpty()) bed--;
            boolean out = c == 0 && random.nextFloat() < STONE_OUT_OF_WATER;
            int top = out ? surface + 1 : surface - 1;                           // otherwise just under the surface
            for (int y = bed + 1; y <= top; y++) level.setBlock(pos.set(x, y, z), rock(x, y, z, salt, false), 2);
            if (out && level.getBlockState(pos.set(x, top + 1, z)).isAir()) {
                level.setBlock(pos, Blocks.SNOW.defaultBlockState().setValue(SnowLayerBlock.LAYERS, 1), 2);
            }
        }
    }

    // ---- TUNE: plants on the river banks ----
    static final double BANK_PLANT_CHANCE = 0.10;    // share of bank columns (1-4 blocks from the rim) with a plant
    static final double BANK_LARGE_FERN = 0.15;      // of those: large ferns ...
    static final double BANK_GRASS = 0.25;           // ... and grass (the rest: ferns)

    /**
     * Ferns and some grass on the land right next to rivers and lakes (the snow there is thin anyway), so the
     * banks look a little greener than the snowy forest around them. Only on soil, inside this chunk.
     */
    public static void bankPlants(WorldGenRegion level, BlockPos origin) {
        RiverNetwork.ChunkData d = RiverFeature.chunkData(level.getLevel(), new net.minecraft.world.level.ChunkPos(origin));
        if (d == null) return;
        int x0 = origin.getX(), z0 = origin.getZ();
        long salt = level.getSeed() ^ 0xBA4CL;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int i = 0; i < 256; i++) {
            if (d.kind[i] != RiverNetwork.ChunkData.NEAR || d.edge[i] < 0.5F || d.edge[i] > 4.0F) continue;
            int x = x0 + i / 16, z = z0 + i % 16;
            double v = hash01(x, 0, z, salt);
            if (v >= BANK_PLANT_CHANCE) continue;
            int gy = groundY(level, x, z);
            if (gy == Integer.MIN_VALUE) continue;
            BlockState floor = level.getBlockState(pos.set(x, gy - 1, z));
            if (!(floor.is(Blocks.GRASS_BLOCK) || floor.is(Blocks.DIRT) || floor.is(Blocks.PODZOL))) continue;
            BlockState here = level.getBlockState(pos.set(x, gy, z));
            if (!(here.isAir() || here.is(Blocks.SNOW))) continue;
            double kind = v / BANK_PLANT_CHANCE;
            boolean large = kind < BANK_LARGE_FERN && level.getBlockState(pos.set(x, gy + 1, z)).isAir();
            if (floor.is(Blocks.DIRT)) level.setBlock(pos.set(x, gy - 1, z), Blocks.GRASS_BLOCK.defaultBlockState(), 2);
            else if (floor.hasProperty(net.minecraft.world.level.block.SnowyDirtBlock.SNOWY)) {
                level.setBlock(pos.set(x, gy - 1, z), floor.setValue(net.minecraft.world.level.block.SnowyDirtBlock.SNOWY, false), 2);
            }
            if (large) {
                net.minecraft.world.level.block.DoublePlantBlock.placeAt(level, Blocks.LARGE_FERN.defaultBlockState(), new BlockPos(x, gy, z), 2);
            } else {
                level.setBlock(pos.set(x, gy, z), (kind < BANK_LARGE_FERN + BANK_GRASS ? Blocks.GRASS : Blocks.FERN).defaultBlockState(), 2);
            }
        }
    }

    // ---- TUNE: pebbles ----
    static final double PEBBLE_CHANCE = 0.06;        // share of open gravel tops with a pebble

    /**
     * Pebbles: small stone buttons lying on open, dry gravel of river rims and lake shores. Not on sea beaches
     * (Rubenbuben4: no buttons at the ocean) and not right next to water (water would wash them away).
     */
    public static void pebbles(WorldGenRegion level, BlockPos origin) {
        int x0 = origin.getX(), z0 = origin.getZ();
        RiverNetwork.ChunkData rivers = RiverFeature.chunkData(level.getLevel(), new net.minecraft.world.level.ChunkPos(origin));
        if (rivers == null) return;                                           // no river or lake here: no pebbles
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int lx = 0; lx < 16; lx++) {
            for (int lz = 0; lz < 16; lz++) {
                byte kind = rivers.kind[lx * 16 + lz];
                if (kind != RiverNetwork.ChunkData.RIM && kind != RiverNetwork.ChunkData.SHORE) continue;
                int x = x0 + lx, z = z0 + lz;
                int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z) - 1;
                if (!level.getBlockState(pos.set(x, top, z)).is(Blocks.GRAVEL)) continue;
                if (!level.getBlockState(pos.set(x, top + 1, z)).isAir()) continue;
                long salt = level.getSeed() ^ 0x9EBB1EL;
                if (hash01(x, top, z, salt) >= PEBBLE_CHANCE) continue;
                boolean wet = false;
                for (int dir = 0; dir < 4 && !wet; dir++) {
                    pos.set(x + (dir == 0 ? 1 : dir == 1 ? -1 : 0), top + 1, z + (dir == 2 ? 1 : dir == 3 ? -1 : 0));
                    wet = !level.getFluidState(pos).isEmpty();
                }
                if (wet) continue;
                double v = hash01(z, top, x, salt ^ 0x55L);
                BlockState button = Blocks.STONE_BUTTON.defaultBlockState()           // (no blackstone ones any more)
                        .setValue(net.minecraft.world.level.block.FaceAttachedHorizontalDirectionalBlock.FACE,
                                net.minecraft.world.level.block.state.properties.AttachFace.FLOOR)
                        .setValue(net.minecraft.world.level.block.HorizontalDirectionalBlock.FACING,
                                net.minecraft.core.Direction.from2DDataValue((int) (v * 1000) & 3));
                level.setBlock(pos.set(x, top + 1, z), button, 2);
            }
        }
    }
}
