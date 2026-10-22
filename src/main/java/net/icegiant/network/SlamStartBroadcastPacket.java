package net.icegiant.network;

import net.icegiant.client.SlamAnimationState;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class SlamStartBroadcastPacket {

    private final int entityId;

    public SlamStartBroadcastPacket(int entityId) {
        this.entityId = entityId;
    }

    public static void encode(SlamStartBroadcastPacket msg, FriendlyByteBuf buf) {
        buf.writeInt(msg.entityId);
    }

    public static SlamStartBroadcastPacket decode(FriendlyByteBuf buf) {
        return new SlamStartBroadcastPacket(buf.readInt());
    }

    public static void handle(SlamStartBroadcastPacket msg, Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> SlamAnimationState.start(msg.entityId)));
        ctx.setPacketHandled(true);
    }
}
