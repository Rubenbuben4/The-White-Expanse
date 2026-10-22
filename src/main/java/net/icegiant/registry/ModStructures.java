package net.icegiant.registry;

import net.icegiant.IceGiantMod;
import net.icegiant.worldgen.camp.GnollCampPathPiece;
import net.icegiant.worldgen.camp.GnollCampPiece;
import net.icegiant.worldgen.camp.GnollCampStructure;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

/** Gnoll camps (structure type and its piece types: buildings, paths and square, camp life). */
public final class ModStructures {

    public static final DeferredRegister<StructureType<?>> STRUCTURE_TYPES =
            DeferredRegister.create(Registries.STRUCTURE_TYPE, IceGiantMod.MOD_ID);
    public static final DeferredRegister<StructurePieceType> PIECES =
            DeferredRegister.create(Registries.STRUCTURE_PIECE, IceGiantMod.MOD_ID);

    public static final RegistryObject<StructureType<GnollCampStructure>> GNOLL_CAMP =
            STRUCTURE_TYPES.register("gnoll_camp", () -> () -> GnollCampStructure.CODEC);

    public static final RegistryObject<StructurePieceType> GNOLL_CAMP_BUILDING =
            PIECES.register("gnoll_camp_building", () -> (StructurePieceType.StructureTemplateType) GnollCampPiece::new);
    public static final RegistryObject<StructurePieceType> GNOLL_CAMP_PATH =
            PIECES.register("gnoll_camp_path", () -> (StructurePieceType.ContextlessType) GnollCampPathPiece::new);
    public static final RegistryObject<StructurePieceType> GNOLL_CAMP_PROPS =
            PIECES.register("gnoll_camp_props", () -> (StructurePieceType.ContextlessType) net.icegiant.worldgen.camp.GnollCampPropsPiece::new);

    private ModStructures() {
    }
}
