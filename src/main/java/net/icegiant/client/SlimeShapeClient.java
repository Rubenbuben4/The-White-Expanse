package net.icegiant.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.icegiant.slime.SlimeShape;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.monster.Slime;

/**
 * Bends a slime over what it eats (SlimeShape, Rubenbuben4 2026-10-27). Called at the start of the slime and magma
 * cube renderers' scale(), so model and outer jelly bend together.
 * <p>
 * The shape: along the thing it is as long as needed, across as wide as needed (a little thinner when pulled long,
 * like stretched jelly), and the height is what is left of its volume - wider means flatter. It oozes out in 1.5 s,
 * jiggles once, wobbles softly while digesting and pulls back together in 0.7 s.
 */
public final class SlimeShapeClient {

    static final float SPREAD_TICKS = 30.0F, SETTLE_TICKS = 20.0F, CLOSE_TICKS = 14.0F;
    /** Flattest he gets (share of his height) and furthest he stretches (times his width). */
    static final float MIN_HEIGHT = 0.22F, MAX_HEIGHT = 1.2F, MAX_STRETCH = 6.0F;
    /** Furthest the jelly slides from his real position to the middle of the thing (blocks). */
    static final double MAX_SLIDE = 1.0D;

    private SlimeShapeClient() {
    }

    public static void apply(Slime s, PoseStack pose, float partialTick) {
        SlimeShape.Cover c = SlimeShape.CLIENT.get(s.getId());
        if (c == null) return;
        float now = s.tickCount + partialTick;
        if (Float.isNaN(c.start)) {
            c.start = now;
            c.size0 = s.getSize();
        }

        // how far out he is (0 = normal, 1 = covering it)
        float k, q = (float) c.size0 / Math.max(1, s.getSize());   // he grew/shrank meanwhile: keep the old look
        if (!c.letGo) {
            k = spread(now - c.start);
        } else {
            if (Float.isNaN(c.end)) {
                c.end = now;
                c.kAtEnd = spread(now - c.start);
            }
            float u = (now - c.end) / CLOSE_TICKS;
            if (u >= 1.0F) {
                SlimeShape.CLIENT.remove(s.getId());
                return;
            }
            float back = 1.0F - u * u * (3.0F - 2.0F * u);
            k = c.kAtEnd * back;
            q = 1.0F + (q - 1.0F) * back;
        }

        // what he covers: a body as the corpse renderer lays it down, else the spot from the packet
        double cx = c.cx, cz = c.cz;
        float along = c.along, across = c.across, yaw = c.axisYaw;
        if (c.bodyId >= 0 && s.level().getEntity(c.bodyId) instanceof LivingEntity body && !body.isRemoved()) {
            EntityDimensions d = body.getDimensions(Pose.STANDING);
            yaw = Mth.rotLerp(partialTick, body.yBodyRotO, body.yBodyRot);
            float fall = Mth.sqrt(Mth.clamp((body.deathTime + partialTick - 1.0F) / 20.0F * 1.6F, 0.0F, 1.0F));
            float half = d.height * 0.5F * fall;
            cx = Mth.lerp(partialTick, body.xo, body.getX()) + Mth.sin(yaw * Mth.DEG_TO_RAD) * half;
            cz = Mth.lerp(partialTick, body.zo, body.getZ()) - Mth.cos(yaw * Mth.DEG_TO_RAD) * half;
            along = d.height;
            across = d.width;
        }

        // target stretch, relative to the size he had when he began
        float w = SlimeShape.WIDTH_PER_SIZE * c.size0;
        float sz = Mth.clamp((along * SlimeShape.NEED_FACTOR + SlimeShape.NEED_MARGIN) / w, 1.0F, MAX_STRETCH);
        float sx = Mth.clamp(Math.max((across * SlimeShape.NEED_FACTOR + SlimeShape.NEED_MARGIN) / w, 1.0F / Mth.sqrt(sz)),
                0.6F, MAX_STRETCH);
        float bx = 1.0F + (sx - 1.0F) * k, bz = 1.0F + (sz - 1.0F) * k;
        float by = Mth.clamp(1.0F / (bx * bz), MIN_HEIGHT, MAX_HEIGHT);   // keeps his volume: wider = flatter
        if (!c.letGo) {
            float wob = 0.035F * Mth.sin(now * 0.3F) * Mth.clamp(k, 0.0F, 1.0F);   // digesting
            by *= 1.0F + wob;
            bx *= 1.0F - wob * 0.5F;
            bz *= 1.0F - wob * 0.5F;
        }

        // The pose here is: rotated to his body yaw (180 - yaw) and flipped (scale -1, -1, 1). A world direction
        // therefore turns by (thing's yaw - his yaw) and a world offset turns by -(180 - his yaw) with x mirrored.
        float bodyYaw = Mth.rotLerp(partialTick, s.yBodyRotO, s.yBodyRot);
        double ox = cx - Mth.lerp(partialTick, s.xo, s.getX()), oz = cz - Mth.lerp(partialTick, s.zo, s.getZ());
        double len = Math.hypot(ox, oz);
        if (len > MAX_SLIDE) {
            ox *= MAX_SLIDE / len;
            oz *= MAX_SLIDE / len;
        }
        double slide = Mth.clamp(k, 0.0F, 1.0F);
        float b = (180.0F - bodyYaw) * Mth.DEG_TO_RAD;
        double lx = oz * Mth.sin(b) - ox * Mth.cos(b), lz = ox * Mth.sin(b) + oz * Mth.cos(b);
        pose.translate(lx * slide, 0.0D, lz * slide);
        float turn = yaw - bodyYaw;
        pose.mulPose(Axis.YP.rotationDegrees(turn));
        pose.scale(bx * q, by * q, bz * q);
        pose.mulPose(Axis.YP.rotationDegrees(-turn));
    }

    /** 0 -> 1: oozes out (smooth), then jiggles once past it and settles. */
    static float spread(float t) {
        if (t <= 0.0F) return 0.0F;
        if (t < SPREAD_TICKS) {
            float u = t / SPREAD_TICKS;
            return u * u * (3.0F - 2.0F * u);
        }
        float v = (t - SPREAD_TICKS) / SETTLE_TICKS;
        if (v >= 1.0F) return 1.0F;
        return 1.0F + 0.08F * Mth.sin(v * Mth.TWO_PI) * (1.0F - v);
    }
}
