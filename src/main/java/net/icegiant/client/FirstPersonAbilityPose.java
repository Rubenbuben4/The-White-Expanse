package net.icegiant.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.icegiant.item.IceWeaponItem;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.world.entity.HumanoidArm;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.List;
import java.util.Map;

/**
 * First person: lets the held mod weapon follow the abilities' arm animation (right-click swing, slam H, ground
 * cut K, sweep N).
 * <p>
 * In third person these animations pose the player model (PlayerModelArmMixin). In first person Minecraft never draws
 * that model - it draws the held item on its own, in front of the camera - so the weapon stood still. Here the
 * same keyframes are sampled for the right arm, and the change of the arm's direction (and its shift) against the
 * animation's first frame is applied to the first-person item, turned around the shoulder. Called by ItemInHandRendererMixin right after the
 * renderer's own pushPose, so the vanilla popPose undoes it again.
 */
public final class FirstPersonAbilityPose {

    /**
     * How much of the arm's turn the first-person weapon follows (1 = all of it). The animations lift the arm high
     * over the head; fully followed, the weapon would leave the screen for a long time.
     */
    private static final float STRENGTH = 0.6F;

    /** The right shoulder, seen from the eyes (blocks; x right, y up, -z forward): 5 px to the side, 4 px below. */
    private static final float SHOULDER_X = 0.3125F;
    private static final float SHOULDER_Y = -0.245F;
    private static final float SHOULDER_Z = 0.0F;

    // scratch parts: only the right arm is read, the other parts just take the rest of the pose
    private static final ModelPart NOW = part();
    private static final ModelPart START = part();
    private static final ModelPart[] OTHER = {part(), part(), part(), part(), part()};

    private FirstPersonAbilityPose() {
    }

    private static ModelPart part() {
        return new ModelPart(List.of(), Map.of());
    }

    /** Applies the pose for the main hand of the local player; does nothing when no ability animation runs. */
    public static void apply(PoseStack poseStack, AbstractClientPlayer player, HumanoidArm arm) {
        if (!(player.getMainHandItem().getItem() instanceof IceWeaponItem)) return;
        int id = player.getId();
        // same order as PlayerModelArmMixin: slam (also ground cut), then sweep, then swing
        int kind;
        float t = SlamAnimationState.getElapsedSeconds(id);
        if (t >= 0F) {
            kind = 0;
        } else if ((t = SweepAnimationState.getElapsedSeconds(id)) >= 0F) {
            kind = 1;
        } else if ((t = SwingAnimationState.getElapsedSeconds(id)) >= 0F) {
            kind = 2;
        } else {
            return;
        }
        sample(kind, t, NOW);
        sample(kind, 0F, START);

        float side = arm == HumanoidArm.RIGHT ? 1F : -1F;   // a left main arm mirrors everything sideways

        // Where the arm points (shoulder to hand), seen from the eyes. Model space has y down and is mirrored on x
        // against the view: x and y flip, z stays. Only the pointing direction is followed, not the arm's twist
        // around itself - the first-person weapon is held at another angle than the model's arm, so the twist
        // would throw it out of the picture instead of across it.
        Vector3f now = new Quaternionf().rotationZYX(side * NOW.zRot, -side * NOW.yRot, -NOW.xRot)
                .transform(new Vector3f(0F, -1F, 0F));
        Vector3f start = new Quaternionf().rotationZYX(side * START.zRot, -side * START.yRot, -START.xRot)
                .transform(new Vector3f(0F, -1F, 0F));
        Quaternionf turn = new Quaternionf().slerp(new Quaternionf().rotationTo(start, now), STRENGTH);

        float dx = -side * (NOW.x - START.x) / 16F * STRENGTH;
        float dy = -(NOW.y - START.y) / 16F * STRENGTH;
        float dz = (NOW.z - START.z) / 16F * STRENGTH;

        float px = side * SHOULDER_X;
        poseStack.translate(px + dx, SHOULDER_Y + dy, SHOULDER_Z + dz);
        poseStack.mulPose(turn);
        poseStack.translate(-px, -SHOULDER_Y, -SHOULDER_Z);
    }

    private static void sample(int kind, float t, ModelPart rightArm) {
        rightArm.resetPose();
        for (ModelPart p : OTHER) p.resetPose();
        switch (kind) {
            case 0 -> AbilityPoses.slam(t, OTHER[0], OTHER[1], rightArm, OTHER[2], OTHER[3], OTHER[4]);
            case 1 -> SweepAnimation.apply(t, OTHER[0], OTHER[1], rightArm, OTHER[2], OTHER[3], OTHER[4]);
            default -> AbilityPoses.swing(t, OTHER[0], OTHER[1], rightArm, OTHER[2], OTHER[3], OTHER[4]);
        }
    }
}
