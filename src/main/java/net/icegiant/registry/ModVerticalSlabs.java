package net.icegiant.registry;

import net.icegiant.block.VerticalSlabBlock;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.registries.RegistryObject;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * Vertical (upright) slabs for vanilla stone, sandstone, brick, quartz and wood blocks - one per block, named
 * "<block>_vertical_slab", with the vanilla textures (models in assets, made by a script). Wooden ones are
 * mined fastest with an axe, the others with a pickaxe (BoneBlockMining); any tool drops them.
 */
public final class ModVerticalSlabs {

    public record Entry(String base, RegistryObject<Block> block, RegistryObject<Item> item, boolean wooden) {
    }

    public static final List<Entry> ALL = new ArrayList<>();

    static {
        reg("stone", () -> Blocks.STONE, false);
        reg("cobblestone", () -> Blocks.COBBLESTONE, false);
        reg("mossy_cobblestone", () -> Blocks.MOSSY_COBBLESTONE, false);
        reg("smooth_stone", () -> Blocks.SMOOTH_STONE, false);
        reg("stone_bricks", () -> Blocks.STONE_BRICKS, false);
        reg("mossy_stone_bricks", () -> Blocks.MOSSY_STONE_BRICKS, false);
        reg("cracked_stone_bricks", () -> Blocks.CRACKED_STONE_BRICKS, false);
        reg("chiseled_stone_bricks", () -> Blocks.CHISELED_STONE_BRICKS, false);
        reg("granite", () -> Blocks.GRANITE, false);
        reg("polished_granite", () -> Blocks.POLISHED_GRANITE, false);
        reg("diorite", () -> Blocks.DIORITE, false);
        reg("polished_diorite", () -> Blocks.POLISHED_DIORITE, false);
        reg("andesite", () -> Blocks.ANDESITE, false);
        reg("polished_andesite", () -> Blocks.POLISHED_ANDESITE, false);
        reg("deepslate", () -> Blocks.DEEPSLATE, false);
        reg("cobbled_deepslate", () -> Blocks.COBBLED_DEEPSLATE, false);
        reg("polished_deepslate", () -> Blocks.POLISHED_DEEPSLATE, false);
        reg("deepslate_bricks", () -> Blocks.DEEPSLATE_BRICKS, false);
        reg("cracked_deepslate_bricks", () -> Blocks.CRACKED_DEEPSLATE_BRICKS, false);
        reg("deepslate_tiles", () -> Blocks.DEEPSLATE_TILES, false);
        reg("cracked_deepslate_tiles", () -> Blocks.CRACKED_DEEPSLATE_TILES, false);
        reg("chiseled_deepslate", () -> Blocks.CHISELED_DEEPSLATE, false);
        reg("tuff", () -> Blocks.TUFF, false);
        reg("calcite", () -> Blocks.CALCITE, false);
        reg("dripstone_block", () -> Blocks.DRIPSTONE_BLOCK, false);
        reg("blackstone", () -> Blocks.BLACKSTONE, false);
        reg("polished_blackstone", () -> Blocks.POLISHED_BLACKSTONE, false);
        reg("polished_blackstone_bricks", () -> Blocks.POLISHED_BLACKSTONE_BRICKS, false);
        reg("cracked_polished_blackstone_bricks", () -> Blocks.CRACKED_POLISHED_BLACKSTONE_BRICKS, false);
        reg("chiseled_polished_blackstone", () -> Blocks.CHISELED_POLISHED_BLACKSTONE, false);
        reg("smooth_basalt", () -> Blocks.SMOOTH_BASALT, false);
        reg("basalt", () -> Blocks.BASALT, false);
        reg("polished_basalt", () -> Blocks.POLISHED_BASALT, false);
        reg("sandstone", () -> Blocks.SANDSTONE, false);
        reg("cut_sandstone", () -> Blocks.CUT_SANDSTONE, false);
        reg("smooth_sandstone", () -> Blocks.SMOOTH_SANDSTONE, false);
        reg("chiseled_sandstone", () -> Blocks.CHISELED_SANDSTONE, false);
        reg("red_sandstone", () -> Blocks.RED_SANDSTONE, false);
        reg("cut_red_sandstone", () -> Blocks.CUT_RED_SANDSTONE, false);
        reg("smooth_red_sandstone", () -> Blocks.SMOOTH_RED_SANDSTONE, false);
        reg("chiseled_red_sandstone", () -> Blocks.CHISELED_RED_SANDSTONE, false);
        reg("bricks", () -> Blocks.BRICKS, false);
        reg("mud_bricks", () -> Blocks.MUD_BRICKS, false);
        reg("packed_mud", () -> Blocks.PACKED_MUD, false);
        reg("nether_bricks", () -> Blocks.NETHER_BRICKS, false);
        reg("cracked_nether_bricks", () -> Blocks.CRACKED_NETHER_BRICKS, false);
        reg("chiseled_nether_bricks", () -> Blocks.CHISELED_NETHER_BRICKS, false);
        reg("red_nether_bricks", () -> Blocks.RED_NETHER_BRICKS, false);
        reg("prismarine", () -> Blocks.PRISMARINE, false);
        reg("prismarine_bricks", () -> Blocks.PRISMARINE_BRICKS, false);
        reg("dark_prismarine", () -> Blocks.DARK_PRISMARINE, false);
        reg("end_stone", () -> Blocks.END_STONE, false);
        reg("end_stone_bricks", () -> Blocks.END_STONE_BRICKS, false);
        reg("purpur_block", () -> Blocks.PURPUR_BLOCK, false);
        reg("purpur_pillar", () -> Blocks.PURPUR_PILLAR, false);
        reg("bone_block", () -> Blocks.BONE_BLOCK, false);
        reg("quartz_block", () -> Blocks.QUARTZ_BLOCK, false);
        reg("smooth_quartz", () -> Blocks.SMOOTH_QUARTZ, false);
        reg("quartz_bricks", () -> Blocks.QUARTZ_BRICKS, false);
        reg("quartz_pillar", () -> Blocks.QUARTZ_PILLAR, false);
        reg("chiseled_quartz_block", () -> Blocks.CHISELED_QUARTZ_BLOCK, false);
        reg("oak_planks", () -> Blocks.OAK_PLANKS, true);
        reg("oak_log", () -> Blocks.OAK_LOG, true);
        reg("stripped_oak_log", () -> Blocks.STRIPPED_OAK_LOG, true);
        reg("oak_wood", () -> Blocks.OAK_WOOD, true);
        reg("stripped_oak_wood", () -> Blocks.STRIPPED_OAK_WOOD, true);
        reg("spruce_planks", () -> Blocks.SPRUCE_PLANKS, true);
        reg("spruce_log", () -> Blocks.SPRUCE_LOG, true);
        reg("stripped_spruce_log", () -> Blocks.STRIPPED_SPRUCE_LOG, true);
        reg("spruce_wood", () -> Blocks.SPRUCE_WOOD, true);
        reg("stripped_spruce_wood", () -> Blocks.STRIPPED_SPRUCE_WOOD, true);
        reg("birch_planks", () -> Blocks.BIRCH_PLANKS, true);
        reg("birch_log", () -> Blocks.BIRCH_LOG, true);
        reg("stripped_birch_log", () -> Blocks.STRIPPED_BIRCH_LOG, true);
        reg("birch_wood", () -> Blocks.BIRCH_WOOD, true);
        reg("stripped_birch_wood", () -> Blocks.STRIPPED_BIRCH_WOOD, true);
        reg("jungle_planks", () -> Blocks.JUNGLE_PLANKS, true);
        reg("jungle_log", () -> Blocks.JUNGLE_LOG, true);
        reg("stripped_jungle_log", () -> Blocks.STRIPPED_JUNGLE_LOG, true);
        reg("jungle_wood", () -> Blocks.JUNGLE_WOOD, true);
        reg("stripped_jungle_wood", () -> Blocks.STRIPPED_JUNGLE_WOOD, true);
        reg("acacia_planks", () -> Blocks.ACACIA_PLANKS, true);
        reg("acacia_log", () -> Blocks.ACACIA_LOG, true);
        reg("stripped_acacia_log", () -> Blocks.STRIPPED_ACACIA_LOG, true);
        reg("acacia_wood", () -> Blocks.ACACIA_WOOD, true);
        reg("stripped_acacia_wood", () -> Blocks.STRIPPED_ACACIA_WOOD, true);
        reg("dark_oak_planks", () -> Blocks.DARK_OAK_PLANKS, true);
        reg("dark_oak_log", () -> Blocks.DARK_OAK_LOG, true);
        reg("stripped_dark_oak_log", () -> Blocks.STRIPPED_DARK_OAK_LOG, true);
        reg("dark_oak_wood", () -> Blocks.DARK_OAK_WOOD, true);
        reg("stripped_dark_oak_wood", () -> Blocks.STRIPPED_DARK_OAK_WOOD, true);
        reg("mangrove_planks", () -> Blocks.MANGROVE_PLANKS, true);
        reg("mangrove_log", () -> Blocks.MANGROVE_LOG, true);
        reg("stripped_mangrove_log", () -> Blocks.STRIPPED_MANGROVE_LOG, true);
        reg("mangrove_wood", () -> Blocks.MANGROVE_WOOD, true);
        reg("stripped_mangrove_wood", () -> Blocks.STRIPPED_MANGROVE_WOOD, true);
        reg("cherry_planks", () -> Blocks.CHERRY_PLANKS, true);
        reg("cherry_log", () -> Blocks.CHERRY_LOG, true);
        reg("stripped_cherry_log", () -> Blocks.STRIPPED_CHERRY_LOG, true);
        reg("cherry_wood", () -> Blocks.CHERRY_WOOD, true);
        reg("stripped_cherry_wood", () -> Blocks.STRIPPED_CHERRY_WOOD, true);
        reg("crimson_planks", () -> Blocks.CRIMSON_PLANKS, true);
        reg("crimson_stem", () -> Blocks.CRIMSON_STEM, true);
        reg("stripped_crimson_stem", () -> Blocks.STRIPPED_CRIMSON_STEM, true);
        reg("crimson_hyphae", () -> Blocks.CRIMSON_HYPHAE, true);
        reg("stripped_crimson_hyphae", () -> Blocks.STRIPPED_CRIMSON_HYPHAE, true);
        reg("warped_planks", () -> Blocks.WARPED_PLANKS, true);
        reg("warped_stem", () -> Blocks.WARPED_STEM, true);
        reg("stripped_warped_stem", () -> Blocks.STRIPPED_WARPED_STEM, true);
        reg("warped_hyphae", () -> Blocks.WARPED_HYPHAE, true);
        reg("stripped_warped_hyphae", () -> Blocks.STRIPPED_WARPED_HYPHAE, true);
        reg("bamboo_planks", () -> Blocks.BAMBOO_PLANKS, true);
        reg("bamboo_mosaic", () -> Blocks.BAMBOO_MOSAIC, true);
        reg("bamboo_block", () -> Blocks.BAMBOO_BLOCK, true);
        reg("stripped_bamboo_block", () -> Blocks.STRIPPED_BAMBOO_BLOCK, true);
    }

    private ModVerticalSlabs() {
    }

    /** Called from the mod constructor before the registers are hooked up (loads this class). */
    public static void init() {
    }

    private static void reg(String base, Supplier<Block> block, boolean wooden) {
        String name = base + "_vertical_slab";
        RegistryObject<Block> b = ModBlocks.BLOCKS.register(name,
                () -> new VerticalSlabBlock(VerticalSlabBlock.propsOf(block.get()), block, wooden));
        RegistryObject<Item> i = ModItems.ITEMS.register(name,
                () -> new VerticalSlabBlock.Item(b.get(), new Item.Properties()));
        ALL.add(new Entry(base, b, i, wooden));
    }
}
