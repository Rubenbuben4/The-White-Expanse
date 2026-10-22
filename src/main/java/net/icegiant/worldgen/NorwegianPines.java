package net.icegiant.worldgen;

import net.icegiant.IceGiantMod;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.templatesystem.BlockIgnoreProcessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * The Frozen Realm's trees: "Norwegian Pines" by Voyde Studios (structure files in
 * data/icegiant/structures/norwegian_pine/, converted to 1.20.1 - see CREDITS.md).
 * <p>
 * Every vanilla tree that would grow in the Frozen Realm is swapped for one of these
 * (TreeFeatureMixin) - same spots, different trees. 15 shapes (4 small, 5 medium, 6 large),
 * each with 4 snow levels; the Frozen Realm uses snow 1-3.
 * <p>
 * During world generation the forest follows the height (like a real tree line, since 2026-10-03):
 * below {@value #FULL_FOREST_Y} the forest is full and extra thick (an extra pine next to many trees);
 * from there up to {@value #TREE_LINE_Y} trees get rarer, smaller and snowier; above that none.
 * The forest also has clearings: open patches, mostly 20-70 blocks across, with a few scattered
 * trees at their edges (clearing noise, since 2026-10-04).
 * Saplings planted by players are not affected.
 */
public final class NorwegianPines {

    private enum Size { SMALL, MEDIUM, LARGE }

    /** Shape name + trunk position in the bottom layer of the structure (the tree stands there). */
    private record Tree(String name, int trunkX, int trunkZ, Size size) {
    }

    private static final List<Tree> TREES = List.of(
            new Tree("pine_norway_small1", 3, 3, Size.SMALL),
            new Tree("pine_norway_small2", 2, 2, Size.SMALL),
            new Tree("pine_norway_small3", 2, 1, Size.SMALL),
            new Tree("pine_norway_small4", 2, 2, Size.SMALL),
            new Tree("pine_norway_med1", 3, 4, Size.MEDIUM),
            new Tree("pine_norway_med2", 3, 3, Size.MEDIUM),
            new Tree("pine_norway_med3", 3, 3, Size.MEDIUM),
            new Tree("pine_norway_med4", 5, 4, Size.MEDIUM),
            new Tree("pine_norway_med5", 4, 3, Size.MEDIUM),
            new Tree("pine_norway_large1", 5, 4, Size.LARGE),
            new Tree("pine_norway_large2", 6, 5, Size.LARGE),
            new Tree("pine_norway_large3", 5, 4, Size.LARGE),
            new Tree("pine_norway_large4", 5, 4, Size.LARGE),
            new Tree("pine_norway_large5", 5, 5, Size.LARGE),
            new Tree("pine_norway_large6", 5, 4, Size.LARGE));

    /** How often each size replaces a normal tree (big 2x2 spruces always become large pines). */
    private static final int SMALL_WEIGHT = 35;
    private static final int MEDIUM_WEIGHT = 40;
    private static final int LARGE_WEIGHT = 25;
    private static final int MIN_SNOW = 1;
    private static final int MAX_SNOW = 3;

    // ---- TUNE: forest density by height (Frozen Realm world generation) ----
    private static final int FULL_FOREST_Y = 110;      // below: full, thick forest
    private static final int TREE_LINE_Y = 250;        // above: no trees
    private static final float EXTRA_TREE_CHANCE = 0.7F; // lowland: chance of a second pine next to a tree
    private static final int EXTRA_TREE_MIN_DIST = 4, EXTRA_TREE_MAX_DIST = 8;
    // ---- TUNE: forest thins out towards the foot of the mountains ----
    private static final double FOOT_RADIUS = 72.0;      // look this far around for rising mountains
    private static final double FOOT_RISE_START = 18.0;  // terrain this much higher nearby -> forest starts to thin
    private static final double FOOT_RISE_FULL = 110.0;  // this much higher nearby -> only a few trees left
    private static final double FOOT_MAX_THIN = 0.9;
    // ---- TUNE: clearings (open patches in the forest, roughly 20-70 blocks across) ----
    private static final double CLEARING_SIZE = 110.0, CLEARING_DETAIL = 38.0;   // main shape + ragged edge
    private static final double CLEARING_FROM = 0.26, CLEARING_TO = 0.38;         // noise: forest -> fully open

    /** Clearing noise for one world seed (rebuilt when another world is generated). */
    private record ClearingNoise(long seed, FrozenTerrainMath.Grad2 main, FrozenTerrainMath.Grad2 detail) {
    }

    private static volatile ClearingNoise clearingNoise;

    private NorwegianPines() {
    }

    /**
     * Places a random Norwegian pine with its trunk at {@code origin} (where the vanilla tree's
     * trunk would have started). Returns false if the structure file could not be loaded.
     */
    public static boolean place(WorldGenLevel level, BlockPos origin, RandomSource random, boolean bigTree) {
        Size size = bigTree ? Size.LARGE : pickSize(random);
        List<Tree> candidates = new ArrayList<>();
        for (Tree tree : TREES) {
            if (tree.size() == size) candidates.add(tree);
        }
        Tree tree = candidates.get(random.nextInt(candidates.size()));
        int snow = MIN_SNOW + random.nextInt(MAX_SNOW - MIN_SNOW + 1);
        ResourceLocation id = new ResourceLocation(IceGiantMod.MOD_ID,
                "norwegian_pine/" + tree.name() + "_snow" + snow);

        Optional<StructureTemplate> template = level.getLevel().getStructureManager().get(id);
        if (template.isEmpty()) {
            return false;
        }
        BlockPos trunk = new BlockPos(tree.trunkX(), 0, tree.trunkZ());
        StructurePlaceSettings settings = new StructurePlaceSettings()
                .setRotation(Rotation.getRandom(random))
                .setRotationPivot(trunk)                      // turn around the trunk, not the corner
                .addProcessor(BlockIgnoreProcessor.STRUCTURE_AND_AIR) // don't carve air into terrain or other trees
                .setIgnoreEntities(true);
        BlockPos corner = origin.offset(-tree.trunkX(), 0, -tree.trunkZ());
        return template.get().placeInWorld(level, corner, corner, settings, random, Block.UPDATE_CLIENTS);
    }

    /**
     * World generation in the Frozen Realm: thins the forest with height (tree line), makes lowland
     * forests thicker, and uses smaller, snowier pines higher up.
     */
    public static boolean placeInForest(WorldGenLevel level, BlockPos origin, RandomSource random, boolean bigTree) {
        int y = origin.getY();
        // no trees in or over rivers and lakes (the trunk keeps away so the crown stays off the water too)
        if (RiverFeature.tooCloseForTree(level.getLevel(), origin.getX(), origin.getZ())) return false;
        double alpine = net.minecraft.util.Mth.clamp((y - FULL_FOREST_Y) / (double) (TREE_LINE_Y - FULL_FOREST_Y), 0.0, 1.0);
        // near the foot of a mountain (much higher ground close by) the forest gets thinner
        double foot = FOOT_MAX_THIN * footOfMountain(level, origin);
        double thin = Math.max(Math.max(alpine, foot), clearing(level.getSeed(), origin.getX(), origin.getZ()));
        if (random.nextDouble() < thin) return false;       // thinner towards the mountains, none above the tree line
        Size size = bigTree && alpine < 0.3 ? Size.LARGE : pickSize(random);
        if (alpine > 0.6) size = Size.SMALL;                // stunted trees near the tree line
        else if (alpine > 0.3 && size == Size.LARGE) size = Size.MEDIUM;
        int snow = Math.min(MAX_SNOW, MIN_SNOW + (int) (alpine * 2.0 + random.nextFloat()));
        boolean placed = placeTree(level, origin, random, size, snow);

        if (placed && alpine == 0.0 && random.nextFloat() < EXTRA_TREE_CHANCE * (1.0 - foot)) {
            // thick lowland forest: one more pine a few blocks away, on open ground
            double angle = random.nextDouble() * Math.PI * 2.0;
            int dist = EXTRA_TREE_MIN_DIST + random.nextInt(EXTRA_TREE_MAX_DIST - EXTRA_TREE_MIN_DIST + 1);
            int x = origin.getX() + (int) Math.round(Math.cos(angle) * dist);
            int z = origin.getZ() + (int) Math.round(Math.sin(angle) * dist);
            int top = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.WORLD_SURFACE_WG, x, z);
            BlockPos ground = new BlockPos(x, top - 1, z);
            if (level.getBlockState(ground).is(net.minecraft.tags.BlockTags.DIRT)
                    && Math.abs(top - y) <= 3
                    && !RiverFeature.tooCloseForTree(level.getLevel(), x, z)
                    && random.nextDouble() >= clearing(level.getSeed(), x, z)) {
                placeTree(level, ground.above(), random, pickSize(random), MIN_SNOW + random.nextInt(2));
            }
        }
        return placed;
    }

    /**
     * 0 = normal forest, 1 = inside a clearing (no trees); soft in between, so the edge of a clearing
     * has a few scattered trees instead of a hard wall.
     */
    static double clearing(long seed, int x, int z) {
        ClearingNoise n = clearingNoise;
        if (n == null || n.seed() != seed) {
            n = new ClearingNoise(seed, new FrozenTerrainMath.Grad2(seed * 17L + 901L),
                    new FrozenTerrainMath.Grad2(seed * 17L + 902L));
            clearingNoise = n;
        }
        double v = 0.75 * n.main().value(x / CLEARING_SIZE, z / CLEARING_SIZE)
                + 0.25 * n.detail().value(x / CLEARING_DETAIL, z / CLEARING_DETAIL);
        return FrozenTerrainMath.smooth(CLEARING_FROM, CLEARING_TO, v);
    }

    /**
     * 0 = open lowland, 1 = right at the foot of a big mountain: how much higher the terrain gets within
     * {@value #FOOT_RADIUS} blocks (smooth terrain height from the noise router - cheap, no chunk access).
     */
    private static double footOfMountain(WorldGenLevel level, BlockPos origin) {
        net.minecraft.world.level.levelgen.DensityFunction height =
                level.getLevel().getChunkSource().randomState().router().continents();
        double here = origin.getY(), highest = here;
        for (int i = 0; i < 8; i++) {
            double a = i * Math.PI / 4.0;
            int x = origin.getX() + (int) Math.round(Math.cos(a) * FOOT_RADIUS);
            int z = origin.getZ() + (int) Math.round(Math.sin(a) * FOOT_RADIUS);
            double h = height.compute(new net.minecraft.world.level.levelgen.DensityFunction.SinglePointContext(x, 0, z))
                    / 0.00294 + 300.0;                         // see noise_settings "continents"
            highest = Math.max(highest, h);
        }
        double t = net.minecraft.util.Mth.clamp((highest - here - FOOT_RISE_START) / (FOOT_RISE_FULL - FOOT_RISE_START), 0.0, 1.0);
        return t * t * (3.0 - 2.0 * t);
    }

    private static boolean placeTree(WorldGenLevel level, BlockPos origin, RandomSource random, Size size, int snow) {
        List<Tree> candidates = new ArrayList<>();
        for (Tree tree : TREES) {
            if (tree.size() == size) candidates.add(tree);
        }
        Tree tree = candidates.get(random.nextInt(candidates.size()));
        ResourceLocation id = new ResourceLocation(IceGiantMod.MOD_ID,
                "norwegian_pine/" + tree.name() + "_snow" + snow);
        Optional<StructureTemplate> template = level.getLevel().getStructureManager().get(id);
        if (template.isEmpty()) {
            return false;
        }
        BlockPos trunk = new BlockPos(tree.trunkX(), 0, tree.trunkZ());
        StructurePlaceSettings settings = new StructurePlaceSettings()
                .setRotation(Rotation.getRandom(random))
                .setRotationPivot(trunk)
                .addProcessor(BlockIgnoreProcessor.STRUCTURE_AND_AIR)
                .setIgnoreEntities(true);
        BlockPos corner = origin.offset(-tree.trunkX(), 0, -tree.trunkZ());
        return template.get().placeInWorld(level, corner, corner, settings, random, Block.UPDATE_CLIENTS);
    }

    private static Size pickSize(RandomSource random) {
        int roll = random.nextInt(SMALL_WEIGHT + MEDIUM_WEIGHT + LARGE_WEIGHT);
        if (roll < SMALL_WEIGHT) return Size.SMALL;
        if (roll < SMALL_WEIGHT + MEDIUM_WEIGHT) return Size.MEDIUM;
        return Size.LARGE;
    }
}
