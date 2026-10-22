package net.icegiant.network;

import net.icegiant.client.SwingAnimationState;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class SwingStartBroadcastPacket {

    private final int entityId;

    public SwingStartBroadcastPacket(int entityId) {
        this.entityId = entityId;
    }

    public static void encode(SwingStartBroadcastPacket msg, FriendlyByteBuf buf) {
        buf.writeInt(msg.entityId);
    }

    public static SwingStartBroadcastPacket decode(FriendlyByteBuf buf) {
        return new SwingStartBroadcastPacket(buf.readInt());
    }

    public static void handle(SwingStartBroadcastPacket msg, Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> SwingAnimationState.start(msg.entityId)));
        ctx.setPacketHandled(true);
    }
}
