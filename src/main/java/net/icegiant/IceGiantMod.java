package net.icegiant;

import net.icegiant.registry.ModEntityTypes;
import net.icegiant.registry.ModItems;
import net.icegiant.registry.ModParticles;
import net.icegiant.registry.ModSounds;
import net.icegiant.registry.ModBlocks;
import net.icegiant.registry.ModBlockEntities;
import net.icegiant.registry.ModCreativeTabs;
import net.minecraft.world.entity.SpawnPlacements;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.icegiant.entity.IceGiantEntity;

@Mod(IceGiantMod.MOD_ID)
public class IceGiantMod {

    public static final String MOD_ID = "icegiant";

    public IceGiantMod() {
        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();

        net.icegiant.registry.ModVerticalSlabs.init();     // vertical slabs join ModBlocks/ModItems first
        ModEntityTypes.ENTITY_TYPES.register(modBus);
        ModItems.ITEMS.register(modBus);
        ModSounds.SOUND_EVENTS.register(modBus);
        ModParticles.PARTICLE_TYPES.register(modBus);
        ModBlocks.BLOCKS.register(modBus);
        net.icegiant.registry.ModFeatures.FEATURES.register(modBus);
        net.icegiant.registry.ModFeatures.PLACEMENT_MODIFIERS.register(modBus);
        ModBlockEntities.BLOCK_ENTITIES.register(modBus);
        ModCreativeTabs.CREATIVE_MODE_TABS.register(modBus);
        net.icegiant.registry.ModEffects.EFFECTS.register(modBus);
        net.icegiant.registry.ModPaintings.PAINTINGS.register(modBus);
        net.icegiant.registry.ModStructures.STRUCTURE_TYPES.register(modBus);
        net.icegiant.registry.ModStructures.PIECES.register(modBus);

        modBus.addListener(this::commonSetup);
        modBus.addListener(this::registerAttributes);
        modBus.addListener(this::registerSpawnPlacements);
    }

    /**
     * Ice Giants spawn naturally only in the Frozen Realm: the biome modifier
     * (data/icegiant/forge/biome_modifier/ice_giant_spawns.json) adds them to the
     * snow biomes, and this spawn rule rejects every dimension except the Frozen Realm.
     */
    private void registerSpawnPlacements(net.minecraftforge.event.entity.SpawnPlacementRegisterEvent event) {
        event.register(ModEntityTypes.ICE_GIANT.get(),
                net.minecraft.world.entity.SpawnPlacements.Type.ON_GROUND,
                net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                IceGiantEntity::checkIceGiantSpawnRules,
                net.minecraftforge.event.entity.SpawnPlacementRegisterEvent.Operation.REPLACE);
        // Gnoll troops: Frozen Realm only as well (biome modifier gnoll_spawns.json)
        for (var type : java.util.List.of(ModEntityTypes.GNOLL_MELEE, ModEntityTypes.GNOLL_ARCHER, ModEntityTypes.GNOLL_SHAMAN)) {
            event.register(type.get(),
                    net.minecraft.world.entity.SpawnPlacements.Type.ON_GROUND,
                    net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                    net.icegiant.entity.gnoll.GnollEntity::checkGnollSpawnRules,
                    net.minecraftforge.event.entity.SpawnPlacementRegisterEvent.Operation.REPLACE);
        }
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        net.icegiant.network.NetworkHandler.register();
    }

    private void registerAttributes(EntityAttributeCreationEvent event) {
        event.put(ModEntityTypes.ICE_GIANT.get(), IceGiantEntity.createAttributes().build());
        event.put(ModEntityTypes.GNOLL_MELEE.get(), net.icegiant.entity.gnoll.GnollMeleeEntity.createAttributes().build());
        event.put(ModEntityTypes.GNOLL_ARCHER.get(), net.icegiant.entity.gnoll.GnollArcherEntity.createAttributes().build());
        event.put(ModEntityTypes.GNOLL_SHAMAN.get(), net.icegiant.entity.gnoll.GnollShamanEntity.createAttributes().build());
    }
}
