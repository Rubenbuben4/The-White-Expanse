package net.icegiant.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Server -> clients: this dead entity is a body a slime left (it lies on its back for a minute, SlimeCorpses). */
public class CorpsePacket {

    private final int entityId;

    public CorpsePacket(int entityId) {
        this.entityId = entityId;
    }

    public static void encode(CorpsePacket msg, FriendlyByteBuf buf) {
        buf.writeInt(msg.entityId);
    }

    public static CorpsePacket decode(FriendlyByteBuf buf) {
        return new CorpsePacket(buf.readInt());
    }

    public static void handle(CorpsePacket msg, Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> net.icegiant.slime.SlimeCorpses.CLIENT.add(msg.entityId));
        ctx.setPacketHandled(true);
    }
}
