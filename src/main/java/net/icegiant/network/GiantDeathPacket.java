package net.icegiant.network;

import net.icegiant.entity.GiantDeath;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Server -> clients: this player dies the Ice Giant's death; the armor he wore (feet, legs, chest, head). */
public class GiantDeathPacket {

    private final int entityId;
    private final ItemStack[] armor;

    public GiantDeathPacket(int entityId, ItemStack[] armor) {
        this.entityId = entityId;
        this.armor = armor;
    }

    public static void encode(GiantDeathPacket msg, FriendlyByteBuf buf) {
        buf.writeInt(msg.entityId);
        for (int i = 0; i < 4; i++) buf.writeItem(msg.armor[i]);
    }

    public static GiantDeathPacket decode(FriendlyByteBuf buf) {
        int id = buf.readInt();
        ItemStack[] armor = new ItemStack[4];
        for (int i = 0; i < 4; i++) armor[i] = buf.readItem();
        return new GiantDeathPacket(id, armor);
    }

    public static void handle(GiantDeathPacket msg, Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> {
            GiantDeath.CLIENT.add(msg.entityId);
            net.icegiant.client.GiantDeathClient.start(msg.entityId, msg.armor);
        }));
        ctx.setPacketHandled(true);
    }
}
