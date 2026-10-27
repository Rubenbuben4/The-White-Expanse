package net.icegiant.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Server -> clients: a slime stretches over something it eats (on) or lets go again (off) - SlimeShape.
 * bodyId is the body it covers, or -1 for a spot (cx, cz); along/across/axisYaw is the size and direction of the thing.
 */
public class SlimeShapePacket {

    public final int slimeId, bodyId;
    public final double cx, cz;
    public final float along, across, axisYaw;
    public final boolean on;

    public SlimeShapePacket(int slimeId, int bodyId, double cx, double cz, float along, float across, float axisYaw, boolean on) {
        this.slimeId = slimeId;
        this.bodyId = bodyId;
        this.cx = cx;
        this.cz = cz;
        this.along = along;
        this.across = across;
        this.axisYaw = axisYaw;
        this.on = on;
    }

    public static SlimeShapePacket off(int slimeId) {
        return new SlimeShapePacket(slimeId, -1, 0.0D, 0.0D, 0.0F, 0.0F, 0.0F, false);
    }

    public static void encode(SlimeShapePacket msg, FriendlyByteBuf buf) {
        buf.writeInt(msg.slimeId);
        buf.writeBoolean(msg.on);
        if (!msg.on) return;
        buf.writeInt(msg.bodyId);
        buf.writeDouble(msg.cx);
        buf.writeDouble(msg.cz);
        buf.writeFloat(msg.along);
        buf.writeFloat(msg.across);
        buf.writeFloat(msg.axisYaw);
    }

    public static SlimeShapePacket decode(FriendlyByteBuf buf) {
        int id = buf.readInt();
        if (!buf.readBoolean()) return off(id);
        return new SlimeShapePacket(id, buf.readInt(), buf.readDouble(), buf.readDouble(), buf.readFloat(), buf.readFloat(),
                buf.readFloat(), true);
    }

    public static void handle(SlimeShapePacket msg, Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> net.icegiant.slime.SlimeShape.onPacket(msg));
        ctx.setPacketHandled(true);
    }
}
