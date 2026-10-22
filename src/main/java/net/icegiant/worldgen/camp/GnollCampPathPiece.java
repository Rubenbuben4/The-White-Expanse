package net.icegiant.worldgen.camp;

import net.icegiant.registry.ModStructures;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.TerrainAdjustment;
import net.minecraftforge.common.world.PieceBeardifierModifier;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;

/**
 * A trodden path between two huts of a Gnoll camp: coarse dirt and dirt path (grass at the frayed edges),
 * laid onto the real ground. The camp's terrain adaptation (beard) leaves the paths alone (getTerrainAdjustment:
 * NONE) - it only levels the ground under the huts and the square. (Until 2026-10-17 the paths' boxes lay 80
 * blocks up in the air to keep the beard away - but Minecraft adapts the terrain round every piece, also up there:
 * it heaped a slab of ground into the air and the paths were laid on top of it.)
 */
public class GnollCampPathPiece extends StructurePiece implements PieceBeardifierModifier {

    private final BlockPos from, to;
    private final int width;
    private final boolean square;            // the open square in the middle (radius = width, with a campfire)
    private final int campRadius;            // square only: how far the whole camp reaches (trees keep out)

    public GnollCampPathPiece(BlockPos from, BlockPos to, int width, int maxBuildHeight, boolean square) {
        super(ModStructures.GNOLL_CAMP_PATH.get(), 0, box(from, to, width, maxBuildHeight));
        this.from = from;
        this.to = to;
        this.width = width;
        this.square = square;
        this.campRadius = width * 4;
    }

    /**
     * The camp's square. Unlike the paths its box lies right on the ground (one block over the square's height), so
     * the camp's terrain adaptation (beard) levels the ground there.
     */
    public static GnollCampPathPiece square(BlockPos center, int radius, int campRadius) {
        return new GnollCampPathPiece(center, radius, campRadius);
    }

    private GnollCampPathPiece(BlockPos center, int radius, int campRadius) {
        super(ModStructures.GNOLL_CAMP_PATH.get(), 0, new BoundingBox(center.getX() - radius - 1, center.getY() + 1,
                center.getZ() - radius - 1, center.getX() + radius + 1, center.getY() + 1, center.getZ() + radius + 1));
        this.from = center;
        this.to = center;
        this.width = radius;
        this.square = true;
        this.campRadius = campRadius;
    }

    public boolean isSquare() {
        return this.square;
    }

    public int centerX() {
        return this.from.getX();
    }

    public int centerZ() {
        return this.from.getZ();
    }

    public int campRadius() {
        return this.campRadius;
    }

    public GnollCampPathPiece(CompoundTag tag) {
        super(ModStructures.GNOLL_CAMP_PATH.get(), tag);
        this.from = NbtUtils.readBlockPos(tag.getCompound("From"));
        this.to = NbtUtils.readBlockPos(tag.getCompound("To"));
        this.width = tag.getInt("Width");
        this.square = tag.getBoolean("Square");
        this.campRadius = tag.contains("Radius") ? tag.getInt("Radius") : this.width * 6;   // (older camps)
    }

    private static BoundingBox box(BlockPos a, BlockPos b, int w, int maxBuildHeight) {
        int lo = Math.min(a.getY(), b.getY()) - 8, hi = Math.min(maxBuildHeight - 1, Math.max(a.getY(), b.getY()) + 8);
        return new BoundingBox(Math.min(a.getX(), b.getX()) - w, lo, Math.min(a.getZ(), b.getZ()) - w,
                Math.max(a.getX(), b.getX()) + w, hi, Math.max(a.getZ(), b.getZ()) + w);
    }

    // ---- terrain adaptation (Forge): the square levels the ground, a path does not touch the terrain ----

    @Override
    public BoundingBox getBeardifierBox() {
        return this.boundingBox;
    }

    @Override
    public TerrainAdjustment getTerrainAdjustment() {
        return this.square ? TerrainAdjustment.BEARD_THIN : TerrainAdjustment.NONE;
    }

    @Override
    public int getGroundLevelDelta() {
        return 0;
    }

    @Override
    protected void addAdditionalSaveData(StructurePieceSerializationContext ctx, CompoundTag tag) {
        tag.put("From", NbtUtils.writeBlockPos(this.from));
        tag.put("To", NbtUtils.writeBlockPos(this.to));
        tag.putInt("Width", this.width);
        tag.putBoolean("Square", this.square);
        tag.putInt("Radius", this.campRadius);
    }

    @Override
    public void postProcess(WorldGenLevel level, StructureManager structures, ChunkGenerator generator, RandomSource random,
                            BoundingBox chunkBox, ChunkPos chunkPos, BlockPos pivot) {
        if (this.square) {
            this.square(level, chunkBox);
            return;
        }
        double dx = this.to.getX() - this.from.getX(), dz = this.to.getZ() - this.from.getZ();
        double len = Math.hypot(dx, dz);
        if (len < 1.0D) return;
        double nx = -dz / len, nz = dx / len;
        int steps = (int) Math.ceil(len * 2.0D);
        double half = (this.width - 1) * 0.5D + 0.5D;
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        for (int s = 0; s <= steps; s++) {
            double t = s / (double) steps;
            // a gentle wobble so the path isn't ruler-straight
            double wob = Math.sin(t * Math.PI * 3.0D + this.from.getX() * 0.1D) * Math.min(2.0D, len * 0.05D) * Math.sin(t * Math.PI);
            double cx = this.from.getX() + dx * t + nx * wob, cz = this.from.getZ() + dz * t + nz * wob;
            for (double o = -half; o <= half + 1.0E-6D; o += 0.5D) {
                int x = (int) Math.floor(cx + nx * o), z = (int) Math.floor(cz + nz * o);
                paint(level, chunkBox, p, x, z, Math.abs(o) / Math.max(0.5D, half));
            }
        }
    }

    /** The open square: a round patch of trodden ground (the campfire and benches: GnollCampPropsPiece). */
    private void square(WorldGenLevel level, BoundingBox chunkBox) {
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        int r = this.width;
        for (int x = this.from.getX() - r - 1; x <= this.from.getX() + r + 1; x++) {
            for (int z = this.from.getZ() - r - 1; z <= this.from.getZ() + r + 1; z++) {
                double d = Math.hypot(x - this.from.getX(), z - this.from.getZ()) + (hash(x, 7, z) - 0.5D) * 1.2D;
                if (d > r + 0.5D) continue;
                paint(level, chunkBox, p, x, z, d / (r + 0.5D));
            }
        }
    }

    /** Trodden ground on one column (edge: 0 = middle of the path, 1 = its frayed edge). */
    private static void paint(WorldGenLevel level, BoundingBox chunkBox, BlockPos.MutableBlockPos p, int x, int z, double edge) {
        if (x < chunkBox.minX() || x > chunkBox.maxX() || z < chunkBox.minZ() || z > chunkBox.maxZ()) return;
        int y = level.getHeight(Heightmap.Types.WORLD_SURFACE_WG, x, z) - 1;
        p.set(x, y, z);
        BlockState st = level.getBlockState(p);
        if (!st.getFluidState().isEmpty()) return;
        if (!ground(st)) {
            if (!st.canBeReplaced()) return;                                     // a hut, a stone: leave it
            y--;                                                                 // a plant or snow layer on top
            p.setY(y);
            st = level.getBlockState(p);
            if (!ground(st)) return;
        }
        double v = hash(x, y, z);
        BlockState path = edge > 0.8D ? (v < 0.5D ? Blocks.COARSE_DIRT.defaultBlockState() : null)
                : v < 0.75D ? Blocks.DIRT_PATH.defaultBlockState() : Blocks.COARSE_DIRT.defaultBlockState();
        if (path == null) return;
        for (int up = 1; up <= 2; up++) {                                          // grass and flowers on it go
            BlockState above = level.getBlockState(p.setY(y + up));
            if (above.canBeReplaced() && above.getFluidState().isEmpty() && !above.isAir()) level.setBlock(p, Blocks.AIR.defaultBlockState(), 2);
        }
        level.setBlock(p.setY(y), path, 2);
    }

    private static boolean ground(BlockState st) {
        return st.is(BlockTags.DIRT) || st.is(Blocks.SAND) || st.is(Blocks.RED_SAND) || st.is(Blocks.SNOW_BLOCK)
                || st.is(Blocks.GRAVEL) || st.is(Blocks.DIRT_PATH);
    }

    private static double hash(int x, int y, int z) {
        long h = x * 73856093L ^ y * 19349663L ^ z * 83492791L ^ 0x6B07DL;
        h = (h ^ (h >>> 13)) * 1274126177L;
        h ^= h >>> 16;
        return (h & 0xffff) / 65535.0D;
    }
}
