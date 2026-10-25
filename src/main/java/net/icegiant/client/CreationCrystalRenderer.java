package net.icegiant.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.icegiant.IceGiantMod;
import net.icegiant.block.CreationCrystalBlock;
import net.icegiant.block.CreationCrystalBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.block.ModelBlockRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.client.model.data.ModelData;

/**
 * Draws the Schöpfungskristall: Rubenbuben4's block model (with its inverted cubes - you see their inside) in three
 * parts, moved by his Blockbench animation "Creator_Crystal_Animation" (CreationCrystalAnimation): the whole crystal
 * floats and sways, the middle ring turns around the up axis, the upright ring rolls over, shell and core stay.
 * <p>
 * The parts are plain block models (models/block/creation_crystal/*.json, registered in ClientSetup), so the inverted
 * cubes stay exactly as in Blockbench; they are drawn with back faces culled, which is what shows the inner faces.
 * The large crystal is the same, 3 times as big around its middle block. Glows at full brightness while active.
 */
public class CreationCrystalRenderer implements BlockEntityRenderer<CreationCrystalBlockEntity> {

    public static final ResourceLocation CORE = new ResourceLocation(IceGiantMod.MOD_ID, "block/creation_crystal/core");
    public static final ResourceLocation RING_MIDDLE = new ResourceLocation(IceGiantMod.MOD_ID, "block/creation_crystal/ring_middle");
    public static final ResourceLocation RING_TOP = new ResourceLocation(IceGiantMod.MOD_ID, "block/creation_crystal/ring_top");

    public CreationCrystalRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(CreationCrystalBlockEntity crystal, float partialTick, PoseStack pose, MultiBufferSource buffers,
                       int light, int overlay) {
        Level level = crystal.getLevel();
        if (level == null) return;
        BlockState state = crystal.getBlockState();
        boolean active = state.hasProperty(CreationCrystalBlock.ACTIVE) && state.getValue(CreationCrystalBlock.ACTIVE);
        int glow = active ? LightTexture.FULL_BRIGHT : light;
        // every crystal at its own point in the loop, so neighbours do not move in step
        long offset = crystal.getBlockPos().asLong() * 0x9E3779B97F4A7C15L >>> 58;     // 0..63 ticks
        float t = ((level.getGameTime() + offset) % CreationCrystalAnimation.LENGTH_TICKS + partialTick) / 20.0F;

        VertexConsumer vc = buffers.getBuffer(Sheets.translucentCullBlockSheet());
        pose.pushPose();
        pose.translate(0.5D, 0.5D, 0.5D);
        if (crystal.large()) pose.scale(3.0F, 3.0F, 3.0F);

        float[] root = CreationCrystalAnimation.crystal(t);                 // {rotX, rotZ, posY (pixels)}
        pose.translate(0.0D, root[2] / 16.0D, 0.0D);
        pose.mulPose(Axis.ZP.rotationDegrees(root[1]));
        pose.mulPose(Axis.XP.rotationDegrees(-root[0]));                    // (Bedrock x/y rotations point the other way)

        part(pose, vc, state, CORE, null, glow, overlay);
        part(pose, vc, state, RING_MIDDLE, CreationCrystalAnimation.middleRing(t), glow, overlay);
        part(pose, vc, state, RING_TOP, CreationCrystalAnimation.topRing(t), glow, overlay);
        pose.popPose();
    }

    /** One part, turned (degrees x, y, z, Bedrock style) around the crystal's middle. */
    private static void part(PoseStack pose, VertexConsumer vc, BlockState state, ResourceLocation name, float[] rot,
                             int light, int overlay) {
        BakedModel model = Minecraft.getInstance().getModelManager().getModel(name);
        pose.pushPose();
        if (rot != null) {
            pose.mulPose(Axis.ZP.rotationDegrees(rot[2]));
            pose.mulPose(Axis.YP.rotationDegrees(-rot[1]));
            pose.mulPose(Axis.XP.rotationDegrees(-rot[0]));
        }
        pose.translate(-0.5D, -0.5D, -0.5D);
        ModelBlockRenderer renderer = Minecraft.getInstance().getBlockRenderer().getModelRenderer();
        renderer.renderModel(pose.last(), vc, state, model, 1.0F, 1.0F, 1.0F, light, overlay, ModelData.EMPTY,
                RenderType.translucent());
        pose.popPose();
    }

    @Override
    public boolean shouldRenderOffScreen(CreationCrystalBlockEntity crystal) {
        return crystal.large();
    }

    @Override
    public int getViewDistance() {
        return 96;
    }
}
