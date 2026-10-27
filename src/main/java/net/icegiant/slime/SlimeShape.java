package net.icegiant.slime;

import net.icegiant.network.NetworkHandler;
import net.icegiant.network.SlimeShapePacket;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.monster.Slime;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.PacketDistributor;

import java.util.HashMap;
import java.util.Map;

/**
 * Slimes stretch over what they eat when it is bigger than they are (Rubenbuben4, 2026-10-27): the jelly oozes over a
 * body or a plant until it covers it, and the wider it has to get the flatter it gets (it keeps its volume). While it
 * digests it wobbles a little, afterwards it pulls back together. Only the look changes, the hitbox stays.
 * <p>
 * Server: the eat goals (SlimeGoals) call cover and release, the clients get it as SlimeShapePacket.
 * Client: client.SlimeShapeClient bends the slime in its renderer (SlimeRendererShapeMixin, MagmaCubeRendererShapeMixin).
 */
public final class SlimeShape {

    /** Edge of the visible jelly per size step (the outer cube is 8 px). */
    public static final float WIDTH_PER_SIZE = 0.5F;
    /** What the jelly needs to cover a thing: its length * NEED_FACTOR + NEED_MARGIN. */
    public static final float NEED_FACTOR = 1.05F, NEED_MARGIN = 0.15F;
    /** Footprint of a plant he grazes (cross-shaped plants hardly fill their block). */
    static final float PLANT_FOOTPRINT = 0.75F;

    /** Server: shapes in use, by slime id (for players who start watching during a meal). */
    private static final Map<Integer, SlimeShapePacket> ACTIVE = new HashMap<>();
    /** Client: shape per slime id (filled by SlimeShapePacket, run by SlimeShapeClient). */
    public static final Map<Integer, Cover> CLIENT = new HashMap<>();

    private SlimeShape() {
    }

    /** Client state of one slime covering something. Times are the slime's tickCount + partial tick. */
    public static final class Cover {
        /** The body he covers, or -1 for a spot (cx, cz). */
        public final int bodyId;
        public final double cx, cz;
        /** Size of what he covers: along its axis (axisYaw, Minecraft yaw of the body) and across it. */
        public final float along, across, axisYaw;
        public float start = Float.NaN, end = Float.NaN, kAtEnd;
        public int size0 = -1;
        /** Server said he let go (meal over or interrupted). */
        public boolean letGo;

        public Cover(int bodyId, double cx, double cz, float along, float across, float axisYaw) {
            this.bodyId = bodyId;
            this.cx = cx;
            this.cz = cz;
            this.along = along;
            this.across = across;
            this.axisYaw = axisYaw;
        }
    }

    // ------------------------------------------------------------------ both sides

    /** Middle of a body lying on its back (LivingEntityRendererCorpseMixin: it falls backwards, head where the back was). */
    public static Vec3 bodyCenter(LivingEntity body) {
        float half = body.getDimensions(Pose.STANDING).height * 0.5F;
        float r = body.yBodyRot * Mth.DEG_TO_RAD;
        return new Vec3(body.getX() + Mth.sin(r) * half, body.getY(), body.getZ() - Mth.cos(r) * half);
    }

    /** True if a thing of this size is wider than the slime's jelly (then he has to stretch to cover it). */
    public static boolean isBigger(Slime s, float along, float across) {
        return Math.max(along, across) * NEED_FACTOR + NEED_MARGIN > WIDTH_PER_SIZE * s.getSize() * 1.1F;
    }

    // ------------------------------------------------------------------ server

    static void coverBody(Slime s, LivingEntity body) {
        EntityDimensions d = body.getDimensions(Pose.STANDING);
        Vec3 c = bodyCenter(body);
        cover(s, new SlimeShapePacket(s.getId(), body.getId(), c.x, c.z, d.height, d.width, body.yBodyRot, true));
    }

    static void coverSpot(Slime s, BlockPos spot) {
        cover(s, new SlimeShapePacket(s.getId(), -1, spot.getX() + 0.5D, spot.getZ() + 0.5D,
                PLANT_FOOTPRINT, PLANT_FOOTPRINT, 0.0F, true));
    }

    private static void cover(Slime s, SlimeShapePacket p) {
        if (s.level().isClientSide() || !isBigger(s, p.along, p.across)) return;
        ACTIVE.put(s.getId(), p);
        NetworkHandler.INSTANCE.send(PacketDistributor.TRACKING_ENTITY.with(() -> s), p);
    }

    /** Meal over (or interrupted): he pulls back together. Harmless if he was not stretched. */
    static void release(Slime s) {
        if (s.level().isClientSide() || ACTIVE.remove(s.getId()) == null || s.isRemoved()) return;
        NetworkHandler.INSTANCE.send(PacketDistributor.TRACKING_ENTITY.with(() -> s), SlimeShapePacket.off(s.getId()));
    }

    /** A player starts watching this slime: tell him if it is stretched over something right now. */
    static void sendTo(ServerPlayer player, Slime s) {
        SlimeShapePacket p = ACTIVE.get(s.getId());
        if (p != null) NetworkHandler.INSTANCE.send(PacketDistributor.PLAYER.with(() -> player), p);
    }

    /** Server: the slime left the level. */
    static void forget(Entity e) {
        ACTIVE.remove(e.getId());
    }

    // ------------------------------------------------------------------ client (from the packet)

    public static void onPacket(SlimeShapePacket p) {
        if (p.on) {
            CLIENT.put(p.slimeId, new Cover(p.bodyId, p.cx, p.cz, p.along, p.across, p.axisYaw));
        } else {
            Cover c = CLIENT.get(p.slimeId);
            if (c != null) c.letGo = true;
        }
    }
}
