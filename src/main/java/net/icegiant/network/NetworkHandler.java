package net.icegiant.network;

import net.icegiant.IceGiantMod;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

public class NetworkHandler {

    private static final String PROTOCOL_VERSION = "7"; // 2: dash key packet, 3: grab key packet, 4: snowstorm, 5: giant death, 6: rune sword, 7: blue fire

    public static final SimpleChannel INSTANCE = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(IceGiantMod.MOD_ID, "main"),
            () -> PROTOCOL_VERSION,
            PROTOCOL_VERSION::equals,
            PROTOCOL_VERSION::equals
    );

    private static int packetId = 0;

    public static void register() {
        INSTANCE.messageBuilder(SlamActivatePacket.class, packetId++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(SlamActivatePacket::encode)
                .decoder(SlamActivatePacket::decode)
                .consumerMainThread(SlamActivatePacket::handle)
                .add();

        INSTANCE.messageBuilder(DashActivatePacket.class, packetId++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(DashActivatePacket::encode)
                .decoder(DashActivatePacket::decode)
                .consumerMainThread(DashActivatePacket::handle)
                .add();

        INSTANCE.messageBuilder(GrabActivatePacket.class, packetId++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(GrabActivatePacket::encode)
                .decoder(GrabActivatePacket::decode)
                .consumerMainThread(GrabActivatePacket::handle)
                .add();

        INSTANCE.messageBuilder(SlamStartBroadcastPacket.class, packetId++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(SlamStartBroadcastPacket::encode)
                .decoder(SlamStartBroadcastPacket::decode)
                .consumerMainThread(SlamStartBroadcastPacket::handle)
                .add();

        INSTANCE.messageBuilder(SnowstormPacket.class, packetId++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(SnowstormPacket::encode)
                .decoder(SnowstormPacket::decode)
                .consumerMainThread(SnowstormPacket::handle)
                .add();

        INSTANCE.messageBuilder(SwingStartBroadcastPacket.class, packetId++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(SwingStartBroadcastPacket::encode)
                .decoder(SwingStartBroadcastPacket::decode)
                .consumerMainThread(SwingStartBroadcastPacket::handle)
                .add();

        INSTANCE.messageBuilder(GiantDeathPacket.class, packetId++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(GiantDeathPacket::encode)
                .decoder(GiantDeathPacket::decode)
                .consumerMainThread(GiantDeathPacket::handle)
                .add();

        // Giant Rune Sword (2026-10-17)
        INSTANCE.messageBuilder(GroundCutActivatePacket.class, packetId++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(GroundCutActivatePacket::encode)
                .decoder(GroundCutActivatePacket::decode)
                .consumerMainThread(GroundCutActivatePacket::handle)
                .add();

        INSTANCE.messageBuilder(SweepActivatePacket.class, packetId++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(SweepActivatePacket::encode)
                .decoder(SweepActivatePacket::decode)
                .consumerMainThread(SweepActivatePacket::handle)
                .add();

        INSTANCE.messageBuilder(SweepStartBroadcastPacket.class, packetId++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(SweepStartBroadcastPacket::encode)
                .decoder(SweepStartBroadcastPacket::decode)
                .consumerMainThread(SweepStartBroadcastPacket::handle)
                .add();

        // Blue flames of the Giant Rune Sword 2.0 (2026-10-21)
        INSTANCE.messageBuilder(BlueFirePacket.class, packetId++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(BlueFirePacket::encode)
                .decoder(BlueFirePacket::decode)
                .consumerMainThread(BlueFirePacket::handle)
                .add();

        // How aware an Ice Giant is of you - the eye on the screen (2026-10-26)
        INSTANCE.messageBuilder(AwarenessPacket.class, packetId++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(AwarenessPacket::encode)
                .decoder(AwarenessPacket::decode)
                .consumerMainThread(AwarenessPacket::handle)
                .add();

        // Slimes (2026-10-27): bodies they leave, tint of their jelly
        INSTANCE.messageBuilder(CorpsePacket.class, packetId++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(CorpsePacket::encode)
                .decoder(CorpsePacket::decode)
                .consumerMainThread(CorpsePacket::handle)
                .add();
        INSTANCE.messageBuilder(SlimeTintPacket.class, packetId++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(SlimeTintPacket::encode)
                .decoder(SlimeTintPacket::decode)
                .consumerMainThread(SlimeTintPacket::handle)
                .add();
    }
}
