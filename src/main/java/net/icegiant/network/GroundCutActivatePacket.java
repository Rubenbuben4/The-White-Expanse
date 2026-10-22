package net.icegiant.network;

import net.icegiant.item.SwordGroundCutAbility;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Key K: the Giant Rune Sword's ground cut (SwordGroundCutAbility). Empty - just the trigger. */
public class GroundCutActivatePacket {

    public GroundCutActivatePacket() {
    }

    public static void encode(GroundCutActivatePacket msg, FriendlyByteBuf buf) {
    }

    public static GroundCutActivatePacket decode(FriendlyByteBuf buf) {
        return new GroundCutActivatePacket();
    }

    public static void handle(GroundCutActivatePacket msg, Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> {
            ServerPlayer player = ctx.getSender();
            if (player != null) SwordGroundCutAbility.tryActivate(player);
        });
        ctx.setPacketHandled(true);
    }
}
