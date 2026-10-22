package net.icegiant.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.icegiant.IceGiantMod;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Client side of the blue flames (entity/BlueFire): which entities burn, the flames drawn on them (vanilla's
 * soul fire pictures, block/soul_fire_0 and _1 - not copied, only used from the block atlas), soul fire
 * particles rising from them, and the flames at the bottom of the screen while you burn yourself.
 * <p>
 * The flames are drawn after all entities (RenderLevelStageEvent), not in a renderer event, so they show on
 * every creature - also the GeckoLib ones (gnolls), whose renderers fire no RenderLivingEvent.
 */
@Mod.EventBusSubscriber(modid = IceGiantMod.MOD_ID, value = Dist.CLIENT)
public final class BlueFireClient {

    public static final String OVERLAY_ID = "blue_fire";

    private static final ResourceLocation FIRE_0 = new ResourceLocation("minecraft", "block/soul_fire_0");
    private static final ResourceLocation FIRE_1 = new ResourceLocation("minecraft", "block/soul_fire_1");

    /** Block atlas, cut-out, both sides drawn (the sheets turn with the camera). */
    private static final RenderType FLAMES = RenderType.entityCutoutNoCull(TextureAtlas.LOCATION_BLOCKS);

    /** Entity id -> game time when its flames go out. */
    private static final Map<Integer, Long> UNTIL = new ConcurrentHashMap<>();

    // ---- TUNE ----
    static final float WIDTH = 1.4F;          // flames this much wider than the creature
    static final float SHEET_STEP = 0.45F;    // each sheet of flames this much (of the width) higher than the last
    static final float SHRINK = 0.92F;        // ... and this much narrower
    static final int PARTICLE_EVERY = 3;      // a soul fire particle every this many ticks
    static final float SCREEN_ALPHA = 0.85F;  // flames at the bottom of the screen

    private BlueFireClient() {
    }

    /** From BlueFirePacket: this entity burns for ticks more (0: put out). */
    public static void set(int entityId, int ticks) {
        Level level = Minecraft.getInstance().level;
        if (ticks <= 0 || level == null) {
            UNTIL.remove(entityId);
            return;
        }
        UNTIL.put(entityId, level.getGameTime() + ticks);
    }

    public static boolean isBurning(Entity entity) {
        Long until = UNTIL.get(entity.getId());
        return until != null && until > entity.level().getGameTime();
    }

    /** Old entries out, soul fire particles from the burning ones. */
    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        Level level = mc.level;
        if (level == null) {
            UNTIL.clear();
            return;
        }
        if (mc.isPaused()) return;
        long time = level.getGameTime();
        for (Iterator<Map.Entry<Integer, Long>> it = UNTIL.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<Integer, Long> e = it.next();
            Entity entity = level.getEntity(e.getKey());
            if (e.getValue() <= time || entity == null || entity.isRemoved()) {
                it.remove();
                continue;
            }
            if (time % PARTICLE_EVERY != 0 || entity.isInvisible()) continue;
            double x = entity.getX() + (level.random.nextDouble() - 0.5D) * entity.getBbWidth();
            double y = entity.getY() + level.random.nextDouble() * entity.getBbHeight();
            double z = entity.getZ() + (level.random.nextDouble() - 0.5D) * entity.getBbWidth();
            level.addParticle(ParticleTypes.SOUL_FIRE_FLAME, x, y, z, 0.0D, 0.03D, 0.0D);
        }
    }

    /** The flames on every burning creature in view. */
    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES || UNTIL.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        Level level = mc.level;
        if (level == null) return;
        Camera camera = event.getCamera();
        Vec3 cam = camera.getPosition();
        float partial = event.getPartialTick();
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        boolean drew = false;
        for (Map.Entry<Integer, Long> e : UNTIL.entrySet()) {
            Entity entity = level.getEntity(e.getKey());
            if (entity == null || e.getValue() <= level.getGameTime()) continue;
            if (entity.isInvisible()) continue;
            if (entity == camera.getEntity() && !camera.isDetached()) continue;      // yourself in first person: screen
            Vec3 pos = entity.getPosition(partial);
            if (!event.getFrustum().isVisible(entity.getBoundingBoxForCulling())) continue;
            PoseStack poseStack = event.getPoseStack();
            poseStack.pushPose();
            poseStack.translate(pos.x - cam.x, pos.y - cam.y, pos.z - cam.z);
            drawFlames(poseStack, buffers.getBuffer(FLAMES), entity, camera.getYRot());
            poseStack.popPose();
            drew = true;
        }
        if (drew) buffers.endBatch(FLAMES);
    }

    /**
     * Sheets of flames facing the camera, from the feet up past the head: each a square picture, a bit higher,
     * narrower and nearer the camera than the one below, the two soul fire pictures taking turns (and every other
     * pair mirrored) so it flickers like a real fire.
     */
    private static void drawFlames(PoseStack poseStack, VertexConsumer buffer, Entity entity, float cameraYaw) {
        java.util.function.Function<ResourceLocation, TextureAtlasSprite> atlas =
                Minecraft.getInstance().getTextureAtlas(TextureAtlas.LOCATION_BLOCKS);
        TextureAtlasSprite[] sprites = {atlas.apply(FIRE_0), atlas.apply(FIRE_1)};
        float width = entity.getBbWidth() * WIDTH;
        float height = entity.getBbHeight() / width;                       // in widths
        poseStack.pushPose();
        poseStack.scale(width, width, width);
        poseStack.mulPose(Axis.YP.rotationDegrees(-cameraYaw));
        Matrix4f pose = poseStack.last().pose();
        Matrix3f normal = poseStack.last().normal();
        float half = 0.5F, y = 0.0F, z = 0.0F;
        int sheet = 0;
        while (y < height + 0.2F && sheet < 24) {
            TextureAtlasSprite s = sprites[sheet % 2];
            boolean mirror = (sheet / 2) % 2 == 1;
            float u0 = mirror ? s.getU1() : s.getU0(), u1 = mirror ? s.getU0() : s.getU1();
            float top = y + 2.0F * half;
            vertex(buffer, pose, normal, -half, y, z, u0, s.getV1());
            vertex(buffer, pose, normal, half, y, z, u1, s.getV1());
            vertex(buffer, pose, normal, half, top, z, u1, s.getV0());
            vertex(buffer, pose, normal, -half, top, z, u0, s.getV0());
            y += SHEET_STEP;
            z -= 0.03F;                                                     // the next one a little in front
            half *= SHRINK;
            sheet++;
        }
        poseStack.popPose();
    }

    private static void vertex(VertexConsumer buffer, Matrix4f pose, Matrix3f normal, float x, float y, float z, float u, float v) {
        buffer.vertex(pose, x, y, z).color(255, 255, 255, 255).uv(u, v).overlayCoords(OverlayTexture.NO_OVERLAY)
                .uv2(LightTexture.FULL_BRIGHT).normal(normal, 0.0F, 1.0F, 0.0F).endVertex();
    }

    /** You burn yourself (first person): blue flames at the bottom of the screen, like vanilla's fire. */
    public static void renderOverlay(ForgeGui gui, GuiGraphics graphics, float partialTick, int width, int height) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui || !mc.options.getCameraType().isFirstPerson()) return;
        if (mc.player.isSpectator() || !isBurning(mc.player)) return;
        TextureAtlasSprite sprite = mc.getTextureAtlas(TextureAtlas.LOCATION_BLOCKS).apply(FIRE_1);
        int size = Math.max(width, height) / 2;
        RenderSystem.enableBlend();
        RenderSystem.disableDepthTest();
        graphics.blit(-size / 4, height - size * 3 / 4, 0, size, size, sprite, 1.0F, 1.0F, 1.0F, SCREEN_ALPHA);
        graphics.blit(width - size * 3 / 4, height - size * 3 / 4, 0, size, size, sprite, 1.0F, 1.0F, 1.0F, SCREEN_ALPHA);
        RenderSystem.enableDepthTest();
        RenderSystem.disableBlend();
    }
}
