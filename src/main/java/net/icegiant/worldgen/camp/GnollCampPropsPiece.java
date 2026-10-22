package net.icegiant.worldgen.camp;

import net.icegiant.IceGiantMod;
import net.icegiant.registry.ModStructures;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.BarrelBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.SkullBlock;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.TerrainAdjustment;
import net.minecraftforge.common.world.PieceBeardifierModifier;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;

import java.util.ArrayList;
import java.util.List;

/**
 * The small things of camp life (CampPlanner.Prop), each set onto the real ground where it stands: the campfire and
 * the log benches round it, lantern posts where the paths meet the square, skull poles at the way out, the chief's
 * banner pole, barrels (with a bit of loot), hay bales, wood piles and a cauldron beside the huts. Nothing is put
 * where a hut stands or on water. The terrain is not adapted to it (getTerrainAdjustment: NONE - until 2026-10-17
 * its box lay 80 blocks up instead, and Minecraft heaped a slab of ground into the air there).
 */
public class GnollCampPropsPiece extends StructurePiece implements PieceBeardifierModifier {

    private static final ResourceLocation BARREL_LOOT = new ResourceLocation(IceGiantMod.MOD_ID, "chests/gnoll_camp_barrel");

    private final List<CampPlanner.Prop> props;

    public GnollCampPropsPiece(List<CampPlanner.Prop> props, int y, int maxBuildHeight) {
        super(ModStructures.GNOLL_CAMP_PROPS.get(), 0, box(props, y, maxBuildHeight));
        this.props = props;
    }

    public GnollCampPropsPiece(CompoundTag tag) {
        super(ModStructures.GNOLL_CAMP_PROPS.get(), tag);
        this.props = new ArrayList<>();
        for (Tag t : tag.getList("Props", Tag.TAG_COMPOUND)) {
            CompoundTag c = (CompoundTag) t;
            this.props.add(new CampPlanner.Prop(c.getString("T"), c.getInt("X"), c.getInt("Z"), c.getInt("D")));
        }
    }

    private static BoundingBox box(List<CampPlanner.Prop> props, int y, int maxBuildHeight) {
        int minX = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
        for (CampPlanner.Prop p : props) {
            minX = Math.min(minX, p.x() - 3);
            minZ = Math.min(minZ, p.z() - 3);
            maxX = Math.max(maxX, p.x() + 3);
            maxZ = Math.max(maxZ, p.z() + 3);
        }
        return new BoundingBox(minX, y - 8, minZ, maxX, Math.min(maxBuildHeight - 1, y + 8), maxZ);
    }

    @Override
    public BoundingBox getBeardifierBox() {
        return this.boundingBox;
    }

    @Override
    public TerrainAdjustment getTerrainAdjustment() {
        return TerrainAdjustment.NONE;                                          // the ground stays as it is
    }

    @Override
    public int getGroundLevelDelta() {
        return 0;
    }

    @Override
    protected void addAdditionalSaveData(StructurePieceSerializationContext ctx, CompoundTag tag) {
        ListTag list = new ListTag();
        for (CampPlanner.Prop p : this.props) {
            CompoundTag c = new CompoundTag();
            c.putString("T", p.type());
            c.putInt("X", p.x());
            c.putInt("Z", p.z());
            c.putInt("D", p.dir());
            list.add(c);
        }
        tag.put("Props", list);
    }

    @Override
    public void postProcess(WorldGenLevel level, StructureManager structures, ChunkGenerator generator, RandomSource random,
                            BoundingBox chunkBox, ChunkPos chunkPos, BlockPos pivot) {
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        for (CampPlanner.Prop prop : this.props) {
            Direction along = prop.dir() == 0 ? Direction.EAST : Direction.SOUTH;
            switch (prop.type()) {
                case "campfire" -> column(level, chunkBox, p, prop.x(), prop.z(), Blocks.CAMPFIRE.defaultBlockState()
                        .setValue(CampfireBlock.LIT, true));
                case "bench" -> {
                    BlockState log = Blocks.STRIPPED_SPRUCE_LOG.defaultBlockState().setValue(RotatedPillarBlock.AXIS, along.getAxis());
                    for (int k = -1; k <= 1; k++) column(level, chunkBox, p, prop.x() + along.getStepX() * k, prop.z() + along.getStepZ() * k, log);
                }
                case "lantern_post" -> column(level, chunkBox, p, prop.x(), prop.z(), Blocks.SPRUCE_FENCE.defaultBlockState(),
                        Blocks.SPRUCE_FENCE.defaultBlockState(), Blocks.LANTERN.defaultBlockState());
                case "skull_pole" -> column(level, chunkBox, p, prop.x(), prop.z(), Blocks.SPRUCE_FENCE.defaultBlockState(),
                        Blocks.SPRUCE_FENCE.defaultBlockState(), Blocks.SPRUCE_FENCE.defaultBlockState(),
                        Blocks.SKELETON_SKULL.defaultBlockState().setValue(SkullBlock.ROTATION, (prop.x() * 7 + prop.z() * 3) & 15));
                case "banner_pole" -> column(level, chunkBox, p, prop.x(), prop.z(), Blocks.DARK_OAK_FENCE.defaultBlockState(),
                        Blocks.DARK_OAK_FENCE.defaultBlockState(), Blocks.DARK_OAK_FENCE.defaultBlockState(),
                        Blocks.RED_BANNER.defaultBlockState());
                case "barrels" -> {
                    BlockState barrel = Blocks.BARREL.defaultBlockState().setValue(BarrelBlock.FACING, Direction.UP);
                    if (column(level, chunkBox, p, prop.x(), prop.z(), barrel)) loot(level, random, p);
                    column(level, chunkBox, p, prop.x() + along.getStepX(), prop.z() + along.getStepZ(), barrel, barrel);
                }
                case "hay" -> {
                    BlockState hay = Blocks.HAY_BLOCK.defaultBlockState();
                    column(level, chunkBox, p, prop.x(), prop.z(), hay, hay);
                    column(level, chunkBox, p, prop.x() + along.getStepX(), prop.z() + along.getStepZ(), hay);
                }
                case "woodpile" -> {
                    BlockState log = Blocks.SPRUCE_LOG.defaultBlockState().setValue(RotatedPillarBlock.AXIS, along.getAxis());
                    for (int k = 0; k < 3; k++) {
                        column(level, chunkBox, p, prop.x() + along.getStepX() * k, prop.z() + along.getStepZ() * k, log, k == 1 ? log : null);
                    }
                }
                case "cauldron" -> column(level, chunkBox, p, prop.x(), prop.z(), Blocks.CAULDRON.defaultBlockState());
                default -> {
                }
            }
        }
    }

    /**
     * Stacks the blocks onto the ground of this column (null = stop). Only on open ground: not on a hut, a path's
     * edge stone or water; plants and snow on the spot are replaced. Returns true if the bottom block was set (p = it).
     */
    private static boolean column(WorldGenLevel level, BoundingBox chunkBox, BlockPos.MutableBlockPos p, int x, int z, BlockState... blocks) {
        if (x < chunkBox.minX() || x > chunkBox.maxX() || z < chunkBox.minZ() || z > chunkBox.maxZ()) return false;
        int y = level.getHeight(Heightmap.Types.WORLD_SURFACE_WG, x, z) - 1;
        BlockState st = level.getBlockState(p.set(x, y, z));
        while (y > level.getMinBuildHeight() && (st.canBeReplaced() && st.getFluidState().isEmpty())) {     // plants, snow
            st = level.getBlockState(p.set(x, --y, z));
        }
        if (!st.getFluidState().isEmpty() || !ground(st)) return false;
        for (int i = 0; i < blocks.length && blocks[i] != null; i++) level.setBlock(p.set(x, y + 1 + i, z), blocks[i], 2);
        p.set(x, y + 1, z);
        return true;
    }

    private static boolean ground(BlockState st) {
        return st.is(BlockTags.DIRT) || st.is(Blocks.SNOW_BLOCK) || st.is(Blocks.GRAVEL) || st.is(Blocks.DIRT_PATH)
                || st.is(Blocks.SAND) || st.is(Blocks.STONE) || st.is(Blocks.PACKED_ICE);
    }

    private static void loot(WorldGenLevel level, RandomSource random, BlockPos pos) {
        RandomizableContainerBlockEntity.setLootTable(level, random, pos, BARREL_LOOT);
    }
}
