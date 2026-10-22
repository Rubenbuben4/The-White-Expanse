package net.icegiant.client;

import com.mojang.blaze3d.systems.RenderSystem;
import net.icegiant.IceGiantMod;
import net.icegiant.dimension.FrostPortalTravel;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Client-side Frost Portal screen effect: while the local player stands in
 * a Frost Portal, the screen fills with the (icy) portal texture and the
 * view wobbles, over the same 5 seconds the server waits before teleporting
 * (see {@link FrostPortalTravel}).
 * <p>
 * The overlay is a lighter, semi-transparent blue version of the portal animation.
 * The wobble reuses the player's vanilla "spinning effect" value, which
 * the game renderer already turns into the nausea-style screen warp. The
 * vanilla GUI would then also draw the purple Nether overlay for that
 * value, so that overlay is suppressed while our effect is active and our
 * own frost overlay (registered in {@link ClientSetup}) is drawn instead.
 */
@Mod.EventBusSubscriber(modid = IceGiantMod.MOD_ID, value = Dist.CLIENT)
public final class FrostPortalOverlay {

    public static final String OVERLAY_ID = "frost_portal";

    /** Light-blue animated overlay (32 frames, 16x16 each), see textures/misc/frost_portal_overlay.png. */
    private static final ResourceLocation OVERLAY_TEXTURE =
            new ResourceLocation(IceGiantMod.MOD_ID, "textures/misc/frost_portal_overlay.png");
    private static final int FRAME_SIZE = 16;
    private static final int FRAMES = 32;
    private static final int FRAME_TICKS = 2;
    private static final float MAX_ALPHA = 0.6F;

    private static final float FILL_PER_TICK = 1.0F / FrostPortalTravel.PLAYER_WAIT_TICKS;
    private static final float FADE_PER_TICK = 0.05F;

    private static boolean insideThisTick;
    private static float intensity;
    private static float previousIntensity;

    private FrostPortalOverlay() {
    }

    /** Called from FrostPortalBlock#entityInside on the client. */
    public static void markInside(Entity entity) {
        if (entity == Minecraft.getInstance().player) {
            insideThisTick = true;
        }
    }

    public static boolean isActive() {
        return intensity > 0.0F || previousIntensity > 0.0F;
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null) {
            intensity = previousIntensity = 0.0F;
            insideThisTick = false;
            return;
        }
        if (mc.isPaused()) {
            return;
        }

        previousIntensity = intensity;
        if (insideThisTick) {
            if (intensity == 0.0F) {
                mc.getSoundManager().play(SimpleSoundInstance.forLocalAmbience(
                        SoundEvents.PORTAL_TRIGGER, 0.8F + player.getRandom().nextFloat() * 0.4F, 0.25F));
            }
            intensity = Math.min(1.0F, intensity + FILL_PER_TICK);
        } else {
            intensity = Math.max(0.0F, intensity - FADE_PER_TICK);
        }
        insideThisTick = false;

        if (isActive()) {
            // Feed the vanilla screen-warp; overwrites vanilla's own decay for this tick.
            player.oSpinningEffectIntensity = previousIntensity;
            player.spinningEffectIntensity = intensity;
        }
    }

    /** Hide the purple Nether overlay while our frost effect drives the spinning value. */
    @SubscribeEvent
    public static void onRenderOverlayPre(RenderGuiOverlayEvent.Pre event) {
        if (isActive() && event.getOverlay().id().equals(VanillaGuiOverlay.PORTAL.id())) {
            event.setCanceled(true);
        }
    }

    /** IGuiOverlay implementation, registered above the vanilla portal overlay. */
    public static void render(ForgeGui gui, GuiGraphics graphics, float partialTick, int width, int height) {
        float progress = Mth.lerp(partialTick, previousIntensity, intensity);
        if (progress <= 0.0F) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null && mc.player.hasEffect(MobEffects.CONFUSION)) {
            return;
        }

        // Light, see-through icy haze: fades in over the 5 s charge-up and never
        // fully covers the screen (max MAX_ALPHA).
        float alpha = MAX_ALPHA * (0.2F + 0.8F * progress * progress);

        // Animated strip (16 x 16*FRAMES), frame picked from game time like a block animation.
        long time = mc.level != null ? mc.level.getGameTime() : 0L;
        int frame = (int) ((time / FRAME_TICKS) % FRAMES);

        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.enableBlend();
        graphics.setColor(1.0F, 1.0F, 1.0F, alpha);
        graphics.blit(OVERLAY_TEXTURE, 0, 0, width, height,
                0.0F, frame * FRAME_SIZE, FRAME_SIZE, FRAME_SIZE, FRAME_SIZE, FRAME_SIZE * FRAMES);
        graphics.setColor(1.0F, 1.0F, 1.0F, 1.0F);
        RenderSystem.disableBlend();
        RenderSystem.depthMask(true);
        RenderSystem.enableDepthTest();
    }
}
