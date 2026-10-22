package net.icegiant.client;

import net.minecraft.client.model.geom.ModelPart;

/**
 * Body pose of the Giant Rune Sword's wide sweep (key N, SwordSweepAbility; the Ice Giant's sweep):
 * Rubenbuben4's Blockbench animation "Swing with the Sword" (2026-10-21, animations/ice_giant_sword_swing.json,
 * 1.4583 s, SweepTiming). He winds up to his left until 0.35 s and slashes flat from left to right.
 * <p>
 * Played on every player-model creature holding a mod weapon (PlayerModelArmMixin: players and the Ice Giant). Each
 * part is first put back to the model's rest pose, then his values are set on top - so the pose is exactly his
 * Blockbench pose (like the slam), not mixed with walking, head turning or the vanilla item-holding arm. Replaces
 * the first sweep pose (SweepPose, right to left, 1 s), which is no longer used.
 */
public final class SweepAnimation {

    static final BedrockPoseAnimation ANIMATION =
            new BedrockPoseAnimation("animations/ice_giant_sword_swing.json", "animation_Swing_with_the_Sword");

    private SweepAnimation() {
    }

    /** The pose t seconds into the sweep; the parts in this order: head, body, right arm, left arm, right leg, left leg. */
    public static void apply(float t, ModelPart head, ModelPart body, ModelPart rightArm, ModelPart leftArm,
                             ModelPart rightLeg, ModelPart leftLeg) {
        pose("Head", head, t);
        pose("Body", body, t);
        pose("Right Arm", rightArm, t);
        pose("Left Arm", leftArm, t);
        pose("Right Leg", rightLeg, t);
        pose("Left Leg", leftLeg, t);
    }

    private static void pose(String bone, ModelPart part, float t) {
        part.resetPose();
        part.xScale = part.yScale = part.zScale = 1.0F;
        ANIMATION.apply(bone, part, t);
    }
}
