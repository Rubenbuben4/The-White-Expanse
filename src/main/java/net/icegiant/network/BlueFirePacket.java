package net.icegiant.network;

import net.icegiant.client.BlueFireClient;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Server -> clients that see the entity: it burns in blue flames for this many more ticks (0: out). BlueFire. */
public class BlueFirePacket {

    private final int entityId;
    private final int ticks;

    public BlueFirePacket(int entityId, int ticks) {
        this.entityId = entityId;
        this.ticks = ticks;
    }

    public static void encode(BlueFirePacket msg, FriendlyByteBuf buf) {
        buf.writeInt(msg.entityId);
        buf.writeVarInt(msg.ticks);
    }

    public static BlueFirePacket decode(FriendlyByteBuf buf) {
        return new BlueFirePacket(buf.readInt(), buf.readVarInt());
    }

    public static void handle(BlueFirePacket msg, Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> BlueFireClient.set(msg.entityId, msg.ticks)));
        ctx.setPacketHandled(true);
    }
}
