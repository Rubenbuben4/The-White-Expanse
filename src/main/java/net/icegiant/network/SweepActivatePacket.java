package net.icegiant.network;

import net.icegiant.item.SwordSweepAbility;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Key N: the Giant Rune Sword's wide sweep (SwordSweepAbility). Empty - just the trigger. */
public class SweepActivatePacket {

    public SweepActivatePacket() {
    }

    public static void encode(SweepActivatePacket msg, FriendlyByteBuf buf) {
    }

    public static SweepActivatePacket decode(FriendlyByteBuf buf) {
        return new SweepActivatePacket();
    }

    public static void handle(SweepActivatePacket msg, Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> {
            ServerPlayer player = ctx.getSender();
            if (player != null) SwordSweepAbility.tryActivate(player);
        });
        ctx.setPacketHandled(true);
    }
}
