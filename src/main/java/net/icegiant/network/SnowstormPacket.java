package net.icegiant.network;

import net.icegiant.client.SnowstormClient;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Server -> client: is a snowstorm raging in the player's dimension, and where does the wind blow. */
public class SnowstormPacket {

    private final boolean active;
    private final float windYaw;

    public SnowstormPacket(boolean active, float windYaw) {
        this.active = active;
        this.windYaw = windYaw;
    }

    public static void encode(SnowstormPacket msg, FriendlyByteBuf buf) {
        buf.writeBoolean(msg.active);
        buf.writeFloat(msg.windYaw);
    }

    public static SnowstormPacket decode(FriendlyByteBuf buf) {
        return new SnowstormPacket(buf.readBoolean(), buf.readFloat());
    }

    public static void handle(SnowstormPacket msg, Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> SnowstormClient.setStorm(msg.active, msg.windYaw)));
        ctx.setPacketHandled(true);
    }
}
