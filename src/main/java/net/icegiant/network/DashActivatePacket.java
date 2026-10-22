package net.icegiant.network;

import net.icegiant.item.IceDashAbility;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Empty packet - the dash key was pressed (ClientKeyInputHandler -> IceDashAbility.tryActivate). */
public class DashActivatePacket {

    public DashActivatePacket() {
    }

    public static void encode(DashActivatePacket msg, FriendlyByteBuf buf) {
        // nothing to encode
    }

    public static DashActivatePacket decode(FriendlyByteBuf buf) {
        return new DashActivatePacket();
    }

    public static void handle(DashActivatePacket msg, Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> {
            ServerPlayer player = ctx.getSender();
            if (player != null) {
                IceDashAbility.tryActivate(player);
            }
        });
        ctx.setPacketHandled(true);
    }
}
