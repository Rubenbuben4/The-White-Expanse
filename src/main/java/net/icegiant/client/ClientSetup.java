package net.icegiant.client;

import net.icegiant.IceGiantMod;
import net.icegiant.client.particle.NordicAxeSlashParticle;
import net.icegiant.block.FractureBlockEntityRenderer;
import net.icegiant.registry.ModEntityTypes;
import net.icegiant.registry.ModParticles;
import net.icegiant.registry.ModBlockEntities;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.client.event.RegisterParticleProvidersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = IceGiantMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = net.minecraftforge.api.distmarker.Dist.CLIENT)
public class ClientSetup {

    @SubscribeEvent
    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ModEntityTypes.ICE_GIANT.get(), IceGiantRenderer::new);
        event.registerEntityRenderer(ModEntityTypes.FLUNG_BLOCK.get(), FlungBlockRenderer::new);
        event.registerEntityRenderer(ModEntityTypes.GNOLL_MELEE.get(),
                ctx -> new net.icegiant.client.gnoll.GnollRenderer<>(ctx, "gnoll_melee"));
        event.registerEntityRenderer(ModEntityTypes.GNOLL_ARCHER.get(),
                ctx -> new net.icegiant.client.gnoll.GnollRenderer<>(ctx, "gnoll_archer"));
        event.registerEntityRenderer(ModEntityTypes.GNOLL_SHAMAN.get(),
                ctx -> new net.icegiant.client.gnoll.GnollRenderer<>(ctx, "gnoll_shaman"));
        event.registerEntityRenderer(ModEntityTypes.GNOLL_CIRCLE.get(), net.icegiant.client.gnoll.GnollCircleRenderer::new);
        event.registerEntityRenderer(ModEntityTypes.GNOLL_SPIRIT.get(),
                net.minecraft.client.renderer.entity.NoopRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntities.FRACTURE_BLOCK_ENTITY.get(), FractureBlockEntityRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntities.DUNGEON_DOOR.get(), DungeonDoorRenderer::new);
    }

    /** Compatibility with EMF / Fresh Animations (our animations pause theirs while they play). */
    @SubscribeEvent
    public static void onClientSetup(net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent event) {
        event.enqueueWork(net.icegiant.compat.EmfCompat::init);
    }

    @SubscribeEvent
    public static void registerKeyMappings(RegisterKeyMappingsEvent event) {
        event.register(ModKeyMappings.ACTIVATE_SLAM);
        event.register(ModKeyMappings.ACTIVATE_DASH);
        event.register(ModKeyMappings.GRAB);
        event.register(ModKeyMappings.ACTIVATE_GROUND_CUT);
        event.register(ModKeyMappings.ACTIVATE_SWEEP);
    }

    @SubscribeEvent
    public static void registerParticleProviders(RegisterParticleProvidersEvent event) {
        event.registerSpriteSet(ModParticles.NORDIC_AXE_SLASH.get(), NordicAxeSlashParticle.Provider::new);
        event.registerSpriteSet(ModParticles.GIANT_SLASH.get(), net.icegiant.client.particle.GiantSlashParticle.Provider::new);
        event.registerSpriteSet(ModParticles.BLIZZARD_FLAKE.get(),
                net.icegiant.client.particle.BlizzardFlakeParticle.Provider::new);
        event.registerSpriteSet(ModParticles.SNOWFALL_FLAKE.get(),
                net.icegiant.client.particle.BlizzardFlakeParticle.FallingProvider::new);
    }

    /** Frost Portal screen overlay, drawn right above the (then hidden) vanilla Nether portal overlay. */
    @SubscribeEvent
    public static void registerGuiOverlays(RegisterGuiOverlaysEvent event) {
        event.registerAbove(VanillaGuiOverlay.PORTAL.id(), FrostPortalOverlay.OVERLAY_ID, FrostPortalOverlay::render);
        // blue flames at the bottom of the screen while you burn in them (Giant Rune Sword 2.0), under the rest of the GUI
        event.registerBelowAll(BlueFireClient.OVERLAY_ID, BlueFireClient::renderOverlay);
    }
}
