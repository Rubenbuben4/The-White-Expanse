package net.icegiant.network;

import net.icegiant.item.NordicAxeSlamAbility;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Leeres Paket - reiner Auslöser, kein Inhalt nötig. */
public class SlamActivatePacket {

    public SlamActivatePacket() {
    }

    public static void encode(SlamActivatePacket msg, FriendlyByteBuf buf) {
        // nichts zu kodieren
    }

    public static SlamActivatePacket decode(FriendlyByteBuf buf) {
        return new SlamActivatePacket();
    }

    public static void handle(SlamActivatePacket msg, Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> {
            ServerPlayer player = ctx.getSender();
            if (player != null) {
                NordicAxeSlamAbility.tryActivate(player);
            }
        });
        ctx.setPacketHandled(true);
    }
}
