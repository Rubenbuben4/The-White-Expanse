package net.icegiant.registry;

import net.icegiant.IceGiantMod;
import net.icegiant.block.FractureBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ModBlockEntities {

    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, IceGiantMod.MOD_ID);

    public static final RegistryObject<BlockEntityType<FractureBlockEntity>> FRACTURE_BLOCK_ENTITY =
            BLOCK_ENTITIES.register("fracture_block_entity", () -> BlockEntityType.Builder.of(
                    FractureBlockEntity::new, ModBlocks.FRACTURE_BLOCK.get()).build(null));

    public static final RegistryObject<BlockEntityType<net.icegiant.block.DungeonDoorBlockEntity>> DUNGEON_DOOR =
            BLOCK_ENTITIES.register("dungeon_door", () -> BlockEntityType.Builder.of(
                    net.icegiant.block.DungeonDoorBlockEntity::new, ModBlocks.DUNGEON_DOOR.get()).build(null));

    public static final RegistryObject<BlockEntityType<net.icegiant.block.CreationCrystalBlockEntity>> CREATION_CRYSTAL =
            BLOCK_ENTITIES.register("creation_crystal", () -> BlockEntityType.Builder.of(
                    net.icegiant.block.CreationCrystalBlockEntity::new,
                    ModBlocks.CREATION_CRYSTAL.get(), ModBlocks.LARGE_CREATION_CRYSTAL.get()).build(null));
}
