package net.icegiant.block;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.ModelBlockRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Siehe Lizenz-/Herkunftshinweis in FractureBlock.java.
 *
 * Hebt den Block nach OBEN an (statt einzusinken) und kippt ihn dabei
 * deterministisch nach außen weg vom Einschlagszentrum - rein optisch,
 * die tatsächliche Kollision bleibt am Original-Platz (siehe FractureBlock),
 * damit der Boden begehbar bleibt.
 */
public class FractureBlockEntityRenderer implements BlockEntityRenderer<FractureBlockEntity> {

    private static final int TRANSITION_TICKS = 4;
    private static final float MAX_TILT_DEGREES = 28f; // stärker als vorher (war 20)
    private static final float MAX_RAISE = 0.25f;

    public FractureBlockEntityRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(FractureBlockEntity entity, float partialTick, PoseStack poseStack,
                        MultiBufferSource buffer, int packedLight, int packedOverlay) {
        BlockState originalState = entity.getOriginalState();
        if (originalState.isAir()) return;

        float t = entity.getTicksAlive() + partialTick;
        int lifetime = entity.getLifetimeTicks();

        float phase;
        if (t < TRANSITION_TICKS) {
            phase = t / TRANSITION_TICKS;
        } else if (t > lifetime - TRANSITION_TICKS) {
            phase = Math.max(0f, (lifetime - t) / TRANSITION_TICKS);
        } else {
            phase = 1.0f;
        }

        float depthFactor = entity.getDepthFactor();
        // Rotation jetzt gleich stark für alle Blöcke (nicht mehr vom
        // Zentrum abhängig) - nur die Höhe (unten) bleibt vom Abstand abhängig.
        float tiltAngle = phase * MAX_TILT_DEGREES;

        // Äußere Blöcke (kleiner depthFactor) werden angehoben, bis zu 0.5
        // Blöcke am äußersten Rand - umgekehrt zum Kipp-Effekt, der im
        // Zentrum am stärksten ist.
        float outerRaise = phase * 1.0f * (1f - depthFactor);

        // Zurückgedreht auf "weg vom Zentrum" (wie ursprünglich)
        float axisX = -entity.getDirZ();
        float axisZ = entity.getDirX();

        // Zusätzliche, zufällige Kipp-Komponente - ADDITIV obendrauf, ersetzt
        // NICHT die normale richtungsbasierte Kippung oben.
        float maxExtraTiltDegrees = 10f;
        float extraTiltX = phase * maxExtraTiltDegrees * entity.getExtraTiltA();
        float extraTiltZ = phase * maxExtraTiltDegrees * entity.getExtraTiltB();

        poseStack.pushPose();
        poseStack.translate(0.5, outerRaise, 0.5);
        poseStack.mulPose(Axis.XP.rotationDegrees(tiltAngle * axisX));
        poseStack.mulPose(Axis.ZP.rotationDegrees(tiltAngle * axisZ));
        poseStack.mulPose(Axis.XP.rotationDegrees(extraTiltX));
        poseStack.mulPose(Axis.ZP.rotationDegrees(extraTiltZ));
        poseStack.translate(-0.5, -0.5, -0.5);

        Minecraft mc = Minecraft.getInstance();

        // renderSingleBlock() macht die komplette Block-Darstellung korrekt -
        // inklusive pro-Fläche-Einfärbung (z.B. Gras oben grün, Seiten/Unterseite
        // normale Erdfarbe) - viel korrekter als ein pauschaler Farbwert fürs
        // ganze Modell (das hatte vorher alles einheitlich grün eingefärbt).
        mc.getBlockRenderer().renderSingleBlock(originalState, poseStack, buffer, packedLight, packedOverlay);

        poseStack.popPose();
    }
}
