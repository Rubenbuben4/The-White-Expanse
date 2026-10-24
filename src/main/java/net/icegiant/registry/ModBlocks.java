package net.icegiant.registry;

import net.icegiant.IceGiantMod;
import net.icegiant.block.FractureBlock;
import net.icegiant.block.FrostPortalBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ModBlocks {

    public static final DeferredRegister<Block> BLOCKS =
            DeferredRegister.create(ForgeRegistries.BLOCKS, IceGiantMod.MOD_ID);

    // Dungeon door: 3 x 6 blocks, two wings that swing open (GeckoLib model, see DungeonDoorBlock)
    public static final RegistryObject<Block> DUNGEON_DOOR = BLOCKS.register("dungeon_door",
            () -> new net.icegiant.block.DungeonDoorBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.WOOD)
                    .strength(4.0F, 6.0F)
                    .sound(SoundType.WOOD)
                    .noOcclusion()
                    .isValidSpawn((state, level, pos, type) -> false)
                    .pushReaction(PushReaction.BLOCK)));

    // Invisible light (creative only, like a barrier): walk-through, brightness 1-15, see InvisibleLightBlock
    public static final RegistryObject<Block> INVISIBLE_LIGHT = BLOCKS.register("invisible_light",
            () -> new net.icegiant.block.InvisibleLightBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.NONE)
                    .strength(-1.0F, 3600000.0F)
                    .noLootTable()
                    .noCollission()
                    .noOcclusion()
                    .isValidSpawn((state, level, pos, type) -> false)
                    .lightLevel(net.icegiant.block.InvisibleLightBlock::lightOf)
                    .pushReaction(PushReaction.BLOCK)));

    // the frameless round rift a Frost Raid opens beside a snowy village (raiders only, closes after the raid)
    public static final RegistryObject<Block> RAID_RIFT = BLOCKS.register("raid_rift",
            () -> new net.icegiant.block.RaidRiftBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_LIGHT_BLUE)
                    .noCollission()
                    .noOcclusion()
                    .noLootTable()
                    .lightLevel(state -> 12)
                    .sound(SoundType.GLASS)
                    .pushReaction(PushReaction.BLOCK)
                    .strength(-1.0F, 3600000.0F)));

    public static final RegistryObject<Block> FRACTURE_BLOCK = BLOCKS.register("fracture_block",
            () -> new FractureBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.NONE)
                    .noOcclusion()
                    .instabreak()
                    .noLootTable()));

    // Portal to the Frozen Realm dimension - fills a Nether-portal-style Blue Ice
    // frame when lit with the Frost Flint and Steel (see FrostFlintAndSteelItem,
    // FrostPortalShape). Players must stand inside for 5s (FrostPortalTravel).
    public static final RegistryObject<Block> FROST_PORTAL = BLOCKS.register("frost_portal",
            () -> new FrostPortalBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_LIGHT_BLUE)
                    .noCollission()
                    .noOcclusion()
                    .noLootTable()
                    .lightLevel(state -> 11)
                    .sound(SoundType.GLASS)
                    .pushReaction(PushReaction.BLOCK)
                    .strength(-1.0F, 3600000.0F)));

    // ---- Backported blocks for the Norwegian Pine trees (Frozen Realm) ----
    // Pale oak and tuff walls/stairs/slabs only exist in newer Minecraft versions; the tree
    // structures use them, so the mod brings its own copies with the original textures
    // (pale oak) and vanilla's tuff texture. Same shapes and behaviour as the vanilla blocks.
    // They burn like vanilla wood (2026-10-24): vanilla keeps its fire odds in a private FireBlock table, so each
    // block answers Forge's getFlammability / getFireSpreadSpeed itself (wood/bark 5/5, planks family 5/20 as vanilla).
    private static final int BURN_LOG = 5;
    private static final int BURN_PLANKS = 20;

    public static final RegistryObject<Block> PALE_OAK_WOOD = BLOCKS.register("pale_oak_wood",
            () -> new net.minecraft.world.level.block.RotatedPillarBlock(
                    BlockBehaviour.Properties.copy(net.minecraft.world.level.block.Blocks.DARK_OAK_WOOD)
                            .mapColor(MapColor.STONE)) {
                @Override public int getFlammability(BlockState s, BlockGetter l, BlockPos p, Direction d) { return BURN_LOG; }
                @Override public int getFireSpreadSpeed(BlockState s, BlockGetter l, BlockPos p, Direction d) { return 5; }
            });
    public static final RegistryObject<Block> PALE_OAK_FENCE_GATE = BLOCKS.register("pale_oak_fence_gate",
            () -> new net.minecraft.world.level.block.FenceGateBlock(
                    BlockBehaviour.Properties.copy(net.minecraft.world.level.block.Blocks.OAK_FENCE_GATE)
                            .mapColor(MapColor.QUARTZ),
                    net.minecraft.world.level.block.state.properties.WoodType.OAK) {
                @Override public int getFlammability(BlockState s, BlockGetter l, BlockPos p, Direction d) { return BURN_PLANKS; }
                @Override public int getFireSpreadSpeed(BlockState s, BlockGetter l, BlockPos p, Direction d) { return 5; }
            });
    public static final RegistryObject<Block> PALE_OAK_TRAPDOOR = BLOCKS.register("pale_oak_trapdoor",
            () -> new net.minecraft.world.level.block.TrapDoorBlock(
                    BlockBehaviour.Properties.copy(net.minecraft.world.level.block.Blocks.OAK_TRAPDOOR)
                            .mapColor(MapColor.QUARTZ),
                    net.minecraft.world.level.block.state.properties.BlockSetType.OAK));
    // Pale oak planks family (crafted from Pale Oak Wood) - behave like normal wooden planks
    public static final RegistryObject<Block> PALE_OAK_PLANKS = BLOCKS.register("pale_oak_planks",
            () -> new Block(BlockBehaviour.Properties.copy(net.minecraft.world.level.block.Blocks.OAK_PLANKS)
                    .mapColor(MapColor.QUARTZ)) {
                @Override public int getFlammability(BlockState s, BlockGetter l, BlockPos p, Direction d) { return BURN_PLANKS; }
                @Override public int getFireSpreadSpeed(BlockState s, BlockGetter l, BlockPos p, Direction d) { return 5; }
            });
    public static final RegistryObject<Block> PALE_OAK_STAIRS = BLOCKS.register("pale_oak_stairs",
            () -> new net.minecraft.world.level.block.StairBlock(() -> PALE_OAK_PLANKS.get().defaultBlockState(),
                    BlockBehaviour.Properties.copy(net.minecraft.world.level.block.Blocks.OAK_STAIRS)
                            .mapColor(MapColor.QUARTZ)) {
                @Override public int getFlammability(BlockState s, BlockGetter l, BlockPos p, Direction d) { return BURN_PLANKS; }
                @Override public int getFireSpreadSpeed(BlockState s, BlockGetter l, BlockPos p, Direction d) { return 5; }
            });
    public static final RegistryObject<Block> PALE_OAK_SLAB = BLOCKS.register("pale_oak_slab",
            () -> new net.minecraft.world.level.block.SlabBlock(
                    BlockBehaviour.Properties.copy(net.minecraft.world.level.block.Blocks.OAK_SLAB)
                            .mapColor(MapColor.QUARTZ)) {
                @Override public int getFlammability(BlockState s, BlockGetter l, BlockPos p, Direction d) { return BURN_PLANKS; }
                @Override public int getFireSpreadSpeed(BlockState s, BlockGetter l, BlockPos p, Direction d) { return 5; }
            });
    public static final RegistryObject<Block> PALE_OAK_FENCE = BLOCKS.register("pale_oak_fence",
            () -> new net.minecraft.world.level.block.FenceBlock(
                    BlockBehaviour.Properties.copy(net.minecraft.world.level.block.Blocks.OAK_FENCE)
                            .mapColor(MapColor.QUARTZ)) {
                @Override public int getFlammability(BlockState s, BlockGetter l, BlockPos p, Direction d) { return BURN_PLANKS; }
                @Override public int getFireSpreadSpeed(BlockState s, BlockGetter l, BlockPos p, Direction d) { return 5; }
            });

    // The trees' tuff walls/stairs/slabs are shown in pale oak bark (user's choice, 2026-10-02) - the
    // registry names stay tuff_* so the tree structures keep working; in game they are "Pale Oak ...".
    public static final RegistryObject<Block> TUFF_WALL = BLOCKS.register("tuff_wall",
            () -> new net.minecraft.world.level.block.WallBlock(
                    BlockBehaviour.Properties.copy(net.minecraft.world.level.block.Blocks.DARK_OAK_WOOD)
                            .mapColor(MapColor.STONE).forceSolidOn()) {
                @Override public int getFlammability(BlockState s, BlockGetter l, BlockPos p, Direction d) { return BURN_LOG; }
                @Override public int getFireSpreadSpeed(BlockState s, BlockGetter l, BlockPos p, Direction d) { return 5; }
            });
    public static final RegistryObject<Block> TUFF_SLAB = BLOCKS.register("tuff_slab",
            () -> new net.minecraft.world.level.block.SlabBlock(
                    BlockBehaviour.Properties.copy(net.minecraft.world.level.block.Blocks.DARK_OAK_WOOD)
                            .mapColor(MapColor.STONE)) {
                @Override public int getFlammability(BlockState s, BlockGetter l, BlockPos p, Direction d) { return BURN_LOG; }
                @Override public int getFireSpreadSpeed(BlockState s, BlockGetter l, BlockPos p, Direction d) { return 5; }
            });
    public static final RegistryObject<Block> TUFF_STAIRS = BLOCKS.register("tuff_stairs",
            () -> new net.minecraft.world.level.block.StairBlock(
                    () -> PALE_OAK_WOOD.get().defaultBlockState(),
                    BlockBehaviour.Properties.copy(net.minecraft.world.level.block.Blocks.DARK_OAK_WOOD)
                            .mapColor(MapColor.STONE)) {
                @Override public int getFlammability(BlockState s, BlockGetter l, BlockPos p, Direction d) { return BURN_LOG; }
                @Override public int getFireSpreadSpeed(BlockState s, BlockGetter l, BlockPos p, Direction d) { return 5; }
            });

    // ---- Packed ice building blocks (texture: vanilla packed ice) ----
    public static final RegistryObject<Block> PACKED_ICE_STAIRS = BLOCKS.register("packed_ice_stairs",
            () -> new net.minecraft.world.level.block.StairBlock(
                    () -> net.minecraft.world.level.block.Blocks.PACKED_ICE.defaultBlockState(),
                    BlockBehaviour.Properties.copy(net.minecraft.world.level.block.Blocks.PACKED_ICE)));
    public static final RegistryObject<Block> PACKED_ICE_SLAB = BLOCKS.register("packed_ice_slab",
            () -> new net.minecraft.world.level.block.SlabBlock(
                    BlockBehaviour.Properties.copy(net.minecraft.world.level.block.Blocks.PACKED_ICE)));
    public static final RegistryObject<Block> PACKED_ICE_WALL = BLOCKS.register("packed_ice_wall",
            () -> new net.minecraft.world.level.block.WallBlock(
                    BlockBehaviour.Properties.copy(net.minecraft.world.level.block.Blocks.PACKED_ICE).forceSolidOn()));
    public static final RegistryObject<Block> PACKED_ICE_FENCE = BLOCKS.register("packed_ice_fence",
            () -> new net.minecraft.world.level.block.FenceBlock(
                    BlockBehaviour.Properties.copy(net.minecraft.world.level.block.Blocks.PACKED_ICE)));
    // pane like a glass pane, but with the packed ice texture (anonymous subclass: works whether
    // the IronBarsBlock constructor is public or protected)
    public static final RegistryObject<Block> PACKED_ICE_PANE = BLOCKS.register("packed_ice_pane",
            () -> new net.minecraft.world.level.block.IronBarsBlock(
                    BlockBehaviour.Properties.copy(net.minecraft.world.level.block.Blocks.PACKED_ICE).noOcclusion()) {});

    // ---- snow building blocks: vanilla snow block texture (minecraft:block/snow). Unlike the vanilla
    // snow block they need no shovel to drop themselves. ----
    private static BlockBehaviour.Properties snow() {
        return BlockBehaviour.Properties.of().mapColor(MapColor.SNOW).strength(0.2F).sound(SoundType.SNOW);
    }

    public static final RegistryObject<Block> SNOW_STAIRS = BLOCKS.register("snow_stairs",
            () -> new net.minecraft.world.level.block.StairBlock(
                    () -> net.minecraft.world.level.block.Blocks.SNOW_BLOCK.defaultBlockState(), snow()));
    public static final RegistryObject<Block> SNOW_SLAB = BLOCKS.register("snow_slab",
            () -> new net.minecraft.world.level.block.SlabBlock(snow()));
    public static final RegistryObject<Block> SNOW_WALL = BLOCKS.register("snow_wall",
            () -> new net.minecraft.world.level.block.WallBlock(snow().forceSolidOn()));
    public static final RegistryObject<Block> SNOW_FENCE = BLOCKS.register("snow_fence",
            () -> new net.minecraft.world.level.block.FenceBlock(snow()));

    // ---- snowy plants, leaves and icicles of the Frozen Realm (2026-10-24; textures from Better Snowy Features by
    //      zzzaitegui, MIT - CREDITS.md). The world puts them in place of the vanilla ones (worldgen/SnowyDecor). ----
    public static final RegistryObject<Block> SNOWY_GRASS = BLOCKS.register("snowy_grass",
            () -> new net.icegiant.block.SnowyPlantBlock(BlockBehaviour.Properties.copy(net.minecraft.world.level.block.Blocks.GRASS)));
    public static final RegistryObject<Block> SNOWY_FERN = BLOCKS.register("snowy_fern",
            () -> new net.icegiant.block.SnowyPlantBlock(BlockBehaviour.Properties.copy(net.minecraft.world.level.block.Blocks.FERN)));
    public static final RegistryObject<Block> SNOWY_TALL_GRASS = BLOCKS.register("snowy_tall_grass",
            () -> new net.minecraft.world.level.block.DoublePlantBlock(BlockBehaviour.Properties.copy(net.minecraft.world.level.block.Blocks.TALL_GRASS)));
    public static final RegistryObject<Block> SNOWY_LARGE_FERN = BLOCKS.register("snowy_large_fern",
            () -> new net.minecraft.world.level.block.DoublePlantBlock(BlockBehaviour.Properties.copy(net.minecraft.world.level.block.Blocks.LARGE_FERN)));
    public static final RegistryObject<Block> SNOWY_SPRUCE_LEAVES = BLOCKS.register("snowy_spruce_leaves",
            () -> new net.minecraft.world.level.block.LeavesBlock(BlockBehaviour.Properties.copy(net.minecraft.world.level.block.Blocks.SPRUCE_LEAVES)));
    public static final RegistryObject<Block> SNOWY_BIRCH_LEAVES = BLOCKS.register("snowy_birch_leaves",
            () -> new net.minecraft.world.level.block.LeavesBlock(BlockBehaviour.Properties.copy(net.minecraft.world.level.block.Blocks.BIRCH_LEAVES)));
    public static final RegistryObject<Block> SNOWY_AZALEA_LEAVES = BLOCKS.register("snowy_azalea_leaves",
            () -> new net.minecraft.world.level.block.LeavesBlock(BlockBehaviour.Properties.copy(net.minecraft.world.level.block.Blocks.AZALEA_LEAVES)));
    public static final RegistryObject<Block> ICICLE = BLOCKS.register("icicle",
            () -> new net.icegiant.block.IcicleBlock(BlockBehaviour.Properties.of().mapColor(MapColor.ICE).noCollission()
                    .noOcclusion().strength(0.1F).sound(SoundType.GLASS).pushReaction(PushReaction.DESTROY)));

    // Glimmeis (glow ice) of the Kristallspitzen biome: glows like cave vines (light 14), slippery, see-through
    public static final RegistryObject<Block> GLOW_ICE = BLOCKS.register("glow_ice",
            () -> new net.icegiant.block.GlowIceBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.ICE).friction(0.98F).strength(0.5F).sound(SoundType.GLASS).noOcclusion()
                    .lightLevel(state -> net.icegiant.block.GlowIceBlock.LIGHT)
                    .emissiveRendering((s, l, p) -> true)
                    .isValidSpawn((s, l, p, e) -> false).isRedstoneConductor((s, l, p) -> false)
                    .isSuffocating((s, l, p) -> false).isViewBlocking((s, l, p) -> false)));

    // ---- cloud blocks: five shades from white to dark grey, ~60 % see-through (CloudBlock) ----
    private static RegistryObject<Block> cloud(String name, MapColor color) {
        return BLOCKS.register(name, () -> new net.icegiant.block.CloudBlock(BlockBehaviour.Properties.of()
                .mapColor(color).strength(0.3F).sound(SoundType.WOOL).noOcclusion()
                .isValidSpawn((s, l, p, e) -> false).isRedstoneConductor((s, l, p) -> false)
                .isSuffocating((s, l, p) -> false).isViewBlocking((s, l, p) -> false)));
    }

    // Frozen Realm river water: ordinary water that knows its river's direction (RiverWaterBlock,
    // RiverFlowMixin) - placed only by the river generator, so no item
    public static final RegistryObject<Block> RIVER_WATER = BLOCKS.register("river_water",
            () -> new net.icegiant.block.RiverWaterBlock(() -> net.minecraft.world.level.material.Fluids.WATER,
                    BlockBehaviour.Properties.copy(net.minecraft.world.level.block.Blocks.WATER)));

    // ---- bone building blocks: vanilla bone block texture (minecraft:block/bone_block_side/_top). Like the
    // snow blocks they drop themselves without the right tool; a pickaxe still mines them fast (BoneBlockMining).
    // Each one can be crafted back into bone meal. ----
    public static final net.minecraft.world.level.block.state.properties.BlockSetType BONE_SET =
            net.minecraft.world.level.block.state.properties.BlockSetType.register(
                    new net.minecraft.world.level.block.state.properties.BlockSetType("icegiant_bone", true, SoundType.BONE_BLOCK,
                            net.minecraft.sounds.SoundEvents.WOODEN_DOOR_CLOSE, net.minecraft.sounds.SoundEvents.WOODEN_DOOR_OPEN,
                            net.minecraft.sounds.SoundEvents.WOODEN_TRAPDOOR_CLOSE, net.minecraft.sounds.SoundEvents.WOODEN_TRAPDOOR_OPEN,
                            net.minecraft.sounds.SoundEvents.STONE_PRESSURE_PLATE_CLICK_OFF, net.minecraft.sounds.SoundEvents.STONE_PRESSURE_PLATE_CLICK_ON,
                            net.minecraft.sounds.SoundEvents.STONE_BUTTON_CLICK_OFF, net.minecraft.sounds.SoundEvents.STONE_BUTTON_CLICK_ON));

    private static BlockBehaviour.Properties bone() {
        return BlockBehaviour.Properties.of().mapColor(MapColor.SAND)
                .instrument(net.minecraft.world.level.block.state.properties.NoteBlockInstrument.XYLOPHONE)
                .strength(2.0F).sound(SoundType.BONE_BLOCK);
    }

    public static final RegistryObject<Block> BONE_STAIRS = BLOCKS.register("bone_stairs",
            () -> new net.minecraft.world.level.block.StairBlock(
                    () -> net.minecraft.world.level.block.Blocks.BONE_BLOCK.defaultBlockState(), bone()));
    public static final RegistryObject<Block> BONE_SLAB = BLOCKS.register("bone_slab",
            () -> new net.minecraft.world.level.block.SlabBlock(bone()));
    public static final RegistryObject<Block> BONE_WALL = BLOCKS.register("bone_wall",
            () -> new net.minecraft.world.level.block.WallBlock(bone().forceSolidOn()));
    public static final RegistryObject<Block> BONE_FENCE = BLOCKS.register("bone_fence",
            () -> new net.minecraft.world.level.block.FenceBlock(bone()));
    public static final RegistryObject<Block> BONE_FENCE_GATE = BLOCKS.register("bone_fence_gate",
            () -> new net.minecraft.world.level.block.FenceGateBlock(bone().forceSolidOn(),
                    net.minecraft.sounds.SoundEvents.FENCE_GATE_OPEN, net.minecraft.sounds.SoundEvents.FENCE_GATE_CLOSE));
    public static final RegistryObject<Block> BONE_TRAPDOOR = BLOCKS.register("bone_trapdoor",
            () -> new net.minecraft.world.level.block.TrapDoorBlock(bone().noOcclusion()
                    .isValidSpawn((s, l, p, e) -> false), BONE_SET));
    public static final RegistryObject<Block> BONE_DOOR = BLOCKS.register("bone_door",
            () -> new net.minecraft.world.level.block.DoorBlock(bone().noOcclusion()
                    .pushReaction(PushReaction.DESTROY), BONE_SET));
    public static final RegistryObject<Block> BONE_BUTTON = BLOCKS.register("bone_button",
            () -> new net.minecraft.world.level.block.ButtonBlock(BlockBehaviour.Properties.of().noCollission()
                    .strength(0.5F).sound(SoundType.BONE_BLOCK).pushReaction(PushReaction.DESTROY), BONE_SET, 20, false));
    public static final RegistryObject<Block> BONE_PRESSURE_PLATE = BLOCKS.register("bone_pressure_plate",
            () -> new net.minecraft.world.level.block.PressurePlateBlock(
                    net.minecraft.world.level.block.PressurePlateBlock.Sensitivity.MOBS,
                    BlockBehaviour.Properties.of().mapColor(MapColor.SAND).forceSolidOn().noCollission()
                            .strength(0.5F).sound(SoundType.BONE_BLOCK).pushReaction(PushReaction.DESTROY), BONE_SET));

    public static final RegistryObject<Block> CLOUD_WHITE = cloud("cloud_white", MapColor.SNOW);
    public static final RegistryObject<Block> CLOUD_VERY_LIGHT_GRAY = cloud("cloud_very_light_gray", MapColor.WOOL);
    public static final RegistryObject<Block> CLOUD_LIGHT_GRAY = cloud("cloud_light_gray", MapColor.COLOR_LIGHT_GRAY);
    public static final RegistryObject<Block> CLOUD_GRAY = cloud("cloud_gray", MapColor.COLOR_GRAY);
    public static final RegistryObject<Block> CLOUD_DARK_GRAY = cloud("cloud_dark_gray", MapColor.COLOR_BLACK);
}
