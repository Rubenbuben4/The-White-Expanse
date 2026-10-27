package net.icegiant.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Server -> clients: tint of a slime's outer jelly from what it ate (0xRRGGBB, white = none; SlimeFood.tint). */
public class SlimeTintPacket {

    private final int entityId;
    private final int rgb;

    public SlimeTintPacket(int entityId, int rgb) {
        this.entityId = entityId;
        this.rgb = rgb;
    }

    public static void encode(SlimeTintPacket msg, FriendlyByteBuf buf) {
        buf.writeInt(msg.entityId);
        buf.writeInt(msg.rgb);
    }

    public static SlimeTintPacket decode(FriendlyByteBuf buf) {
        return new SlimeTintPacket(buf.readInt(), buf.readInt());
    }

    public static void handle(SlimeTintPacket msg, Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> net.icegiant.slime.SlimeLife.CLIENT_TINT.put(msg.entityId, msg.rgb));
        ctx.setPacketHandled(true);
    }
}
