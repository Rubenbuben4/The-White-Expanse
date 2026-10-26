package net.icegiant.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Server -> one player: how aware this Ice Giant is of him (0..1), for the eye on the screen (AwarenessHud). */
public class AwarenessPacket {

    private final int giantId;
    private final float awareness;

    public AwarenessPacket(int giantId, float awareness) {
        this.giantId = giantId;
        this.awareness = awareness;
    }

    public static void encode(AwarenessPacket msg, FriendlyByteBuf buf) {
        buf.writeInt(msg.giantId);
        buf.writeFloat(msg.awareness);
    }

    public static AwarenessPacket decode(FriendlyByteBuf buf) {
        return new AwarenessPacket(buf.readInt(), buf.readFloat());
    }

    public static void handle(AwarenessPacket msg, Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> net.icegiant.client.AwarenessHud.set(msg.giantId, msg.awareness)));
        ctx.setPacketHandled(true);
    }
}
