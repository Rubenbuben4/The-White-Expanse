package net.icegiant.client;

import net.icegiant.entity.IceGiantEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;

/**
 * The Ice Giant uses the plain player model (wide arms) - like vanilla's Giant
 * uses the zombie model - so a normal 64x64 Minecraft skin works as its texture.
 * Being a PlayerModel it also gets the axe swing/slam arm poses from
 * PlayerModelArmMixin.
 * <p>
 * While it holds a creature (grab & throw) the right arm is posed here, after the
 * mixin: straight forward while holding, swinging up over the head before the throw.
 * The held creature is placed at the same fist position (IceGiantEntity#positionRider).
 */
public class IceGiantModel extends PlayerModel<IceGiantEntity> {

    /** War cry animation by Rubenbuben4 (exported from Blockbench), applied on top of the normal pose. */
    private static final BedrockPoseAnimation TAUNT = new BedrockPoseAnimation("animations/ice_giant_taunt.json", "Taunt");
    /** Death animation by Rubenbuben4: falls on his back (holds the last frame while lying there). */
    static final BedrockPoseAnimation DYING = new BedrockPoseAnimation("animations/ice_giant_death.json", "dying_1");

    /** Seconds since death (set by IceGiantRenderer each frame), or -1 while alive. */
    float deathSeconds = -1.0F;

    public IceGiantModel(ModelPart root) {
        super(root, false);
    }

    @Override
    public void setupAnim(IceGiantEntity entity, float limbSwing, float limbSwingAmount, float ageInTicks,
                          float netHeadYaw, float headPitch) {
        for (ModelPart p : new ModelPart[]{this.head, this.hat, this.body, this.jacket, this.rightArm, this.leftArm,
                this.rightSleeve, this.leftSleeve, this.rightLeg, this.leftLeg, this.rightPants, this.leftPants}) {
            p.resetPose();
            p.xScale = p.yScale = p.zScale = 1.0F;
        }
        super.setupAnim(entity, limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch);
        float partial = Minecraft.getInstance().getFrameTime();
        if (this.deathSeconds >= 0.0F) {
            float t = Math.min(this.deathSeconds, DYING.length());           // hold the last frame
            for (String bone : new String[]{"Head", "Body", "Right Arm", "Left Arm", "Right Leg", "Left Leg"}) {
                DYING.apply(bone, part(bone), t);
            }
            copyLayers();
            return;
        }
        if (entity.isRoaring()) {
            // war cry: Rubenbuben4's "Taunt" animation (Blockbench, animations/ice_giant_taunt.json)
            float t = entity.getRoarTime(partial);
            TAUNT.apply("Head", this.head, t);
            TAUNT.apply("Body", this.body, t);
            TAUNT.apply("Right Arm", this.rightArm, t);
            TAUNT.apply("Left Arm", this.leftArm, t);
            TAUNT.apply("Right Leg", this.rightLeg, t);
            TAUNT.apply("Left Leg", this.leftLeg, t);
            copyLayers();
        }
        if (!entity.isGrabbing()) return;
        float partialTick = Minecraft.getInstance().getFrameTime();
        this.rightArm.xRot = (float) Math.toRadians(entity.getGrabArmPitch(partialTick));
        this.rightArm.yRot = 0.0F;
        this.rightArm.zRot = 0.0F;
        this.rightSleeve.copyFrom(this.rightArm);
    }

    private ModelPart part(String bone) {
        return switch (bone) {
            case "Head" -> this.head;
            case "Body" -> this.body;
            case "Right Arm" -> this.rightArm;
            case "Left Arm" -> this.leftArm;
            case "Right Leg" -> this.rightLeg;
            default -> this.leftLeg;
        };
    }

    /** The outer layers (hat, jacket, sleeves, pants) follow their parts. */
    private void copyLayers() {
        this.hat.copyFrom(this.head);
        this.jacket.copyFrom(this.body);
        this.rightSleeve.copyFrom(this.rightArm);
        this.leftSleeve.copyFrom(this.leftArm);
        this.rightPants.copyFrom(this.rightLeg);
        this.leftPants.copyFrom(this.leftLeg);
    }
}
