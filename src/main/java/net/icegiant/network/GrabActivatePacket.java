package net.icegiant.network;

import net.icegiant.item.PlayerGrabAbility;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Empty packet - the grab key was pressed (ClientKeyInputHandler -> PlayerGrabAbility.toggle). */
public class GrabActivatePacket {

    public GrabActivatePacket() {
    }

    public static void encode(GrabActivatePacket msg, FriendlyByteBuf buf) {
        // nothing to encode
    }

    public static GrabActivatePacket decode(FriendlyByteBuf buf) {
        return new GrabActivatePacket();
    }

    public static void handle(GrabActivatePacket msg, Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> {
            ServerPlayer player = ctx.getSender();
            if (player != null) {
                PlayerGrabAbility.toggle(player);
            }
        });
        ctx.setPacketHandled(true);
    }
}
