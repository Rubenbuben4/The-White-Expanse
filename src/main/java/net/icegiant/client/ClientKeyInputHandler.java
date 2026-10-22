package net.icegiant.client;

import net.icegiant.IceGiantMod;
import net.icegiant.item.IceGeoWeaponItem;
import net.icegiant.network.NetworkHandler;
import net.icegiant.network.SlamActivatePacket;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = IceGiantMod.MOD_ID, value = Dist.CLIENT)
public class ClientKeyInputHandler {

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        while (ModKeyMappings.ACTIVATE_SLAM.consumeClick()) {
            // Any GeckoLib weapon (Nordic Axe, Gravebite, ...) can trigger the
            // slam via "H" - no model-name check needed.
            if (mc.player.getMainHandItem().getItem() instanceof IceGeoWeaponItem) {
                NetworkHandler.INSTANCE.sendToServer(new SlamActivatePacket());
            }
        }

        while (ModKeyMappings.ACTIVATE_DASH.consumeClick()) {
            // wall-breaker dash - same weapons as the slam (IceDashAbility)
            if (mc.player.getMainHandItem().getItem() instanceof IceGeoWeaponItem) {
                NetworkHandler.INSTANCE.sendToServer(new net.icegiant.network.DashActivatePacket());
            }
        }

        while (ModKeyMappings.ACTIVATE_GROUND_CUT.consumeClick()) {
            // Giant Rune Sword only: the ground cut (SwordGroundCutAbility)
            if (mc.player.getMainHandItem().getItem() instanceof net.icegiant.item.GiantRuneSwordItem) {
                NetworkHandler.INSTANCE.sendToServer(new net.icegiant.network.GroundCutActivatePacket());
            }
        }

        while (ModKeyMappings.ACTIVATE_SWEEP.consumeClick()) {
            // Giant Rune Sword only: the wide sweep (SwordSweepAbility)
            if (mc.player.getMainHandItem().getItem() instanceof net.icegiant.item.GiantRuneSwordItem) {
                NetworkHandler.INSTANCE.sendToServer(new net.icegiant.network.SweepActivatePacket());
            }
        }

        while (ModKeyMappings.GRAB.consumeClick()) {
            // grab / throw - the server checks the armor and tells the player if it is missing
            NetworkHandler.INSTANCE.sendToServer(new net.icegiant.network.GrabActivatePacket());
        }
    }
}
