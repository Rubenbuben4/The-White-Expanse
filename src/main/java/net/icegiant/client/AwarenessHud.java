package net.icegiant.client;

import com.mojang.blaze3d.systems.RenderSystem;
import net.icegiant.IceGiantMod;
import net.icegiant.entity.GiantSensePlayerGoal;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraftforge.client.gui.overlay.ForgeGui;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * The eye above the crosshair (Rubenbuben4, 2026-10-26): shows how close an Ice Giant is to noticing you. Appears
 * when one becomes aware of you and fills from left to right - yellow while he is suspicious, orange while he
 * searches, red when he has spotted you. The most aware giant counts. Values come from AwarenessPacket.
 */
public final class AwarenessHud {

    public static final String OVERLAY_ID = "giant_awareness";
    private static final ResourceLocation EYE = new ResourceLocation(IceGiantMod.MOD_ID, "textures/gui/awareness_eye.png");
    private static final int W = 16, H = 10;

    private static final Map<Integer, Float> AWARENESS = new HashMap<>();
    private static float shown, alpha;

    private AwarenessHud() {
    }

    public static void set(int giantId, float value) {
        if (value <= 0.0F) AWARENESS.remove(giantId);
        else AWARENESS.put(giantId, value);
    }

    /** IGuiOverlay, registered above the crosshair. */
    public static void render(ForgeGui gui, GuiGraphics graphics, float partialTick, int width, int height) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) {
            AWARENESS.clear();
            return;
        }
        float target = 0.0F;
        Iterator<Map.Entry<Integer, Float>> it = AWARENESS.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Integer, Float> e = it.next();
            if (mc.level.getEntity(e.getKey()) == null) it.remove();        // giant gone (dead, out of range)
            else target = Math.max(target, e.getValue());
        }
        shown = Mth.lerp(0.15F, shown, target);
        alpha = Mth.clamp(alpha + (target > 0.0F ? 0.08F : -0.05F), 0.0F, 1.0F);
        if (alpha <= 0.0F || mc.options.hideGui) return;

        int x = width / 2 - W / 2, y = height / 2 - 20;
        float a = alpha * 0.9F;
        RenderSystem.enableBlend();
        graphics.setColor(1.0F, 1.0F, 1.0F, a * 0.8F);
        graphics.blit(EYE, x, y, 0.0F, 0.0F, W, H, 2 * W, H);              // the empty eye
        int fill = Math.round(W * Mth.clamp(shown, 0.0F, 1.0F));
        if (fill > 0) {
            float v = target * GiantSensePlayerGoal.ALERT;
            if (v >= GiantSensePlayerGoal.ALERT) graphics.setColor(1.0F, 0.25F, 0.2F, a);          // spotted
            else if (v >= GiantSensePlayerGoal.SEARCHING) graphics.setColor(1.0F, 0.6F, 0.2F, a);  // searching
            else graphics.setColor(1.0F, 0.92F, 0.4F, a);                                          // suspicious
            graphics.blit(EYE, x, y, W, 0.0F, fill, H, 2 * W, H);          // the full eye, left part of it
        }
        graphics.setColor(1.0F, 1.0F, 1.0F, 1.0F);
        RenderSystem.disableBlend();
    }
}
