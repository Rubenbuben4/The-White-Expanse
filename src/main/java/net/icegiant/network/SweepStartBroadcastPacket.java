package net.icegiant.network;

import net.icegiant.client.SweepAnimationState;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Server -> clients that see the swinger: the Giant Rune Sword's wide sweep started (body pose, SweepAnimationState; the big slash, GiantSlashParticle). */
public class SweepStartBroadcastPacket {

    private final int entityId;

    public SweepStartBroadcastPacket(int entityId) {
        this.entityId = entityId;
    }

    public static void encode(SweepStartBroadcastPacket msg, FriendlyByteBuf buf) {
        buf.writeInt(msg.entityId);
    }

    public static SweepStartBroadcastPacket decode(FriendlyByteBuf buf) {
        return new SweepStartBroadcastPacket(buf.readInt());
    }

    public static void handle(SweepStartBroadcastPacket msg, Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> {
            SweepAnimationState.start(msg.entityId);
            net.icegiant.client.particle.GiantSlashParticle.spawnFor(msg.entityId);
        }));
        ctx.setPacketHandled(true);
    }
}
