package net.icegiant.mixin;

import net.icegiant.item.IceGeoWeaponItem;
import net.icegiant.client.SlamAnimationState;
import net.icegiant.client.SwingAnimationState;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Voller Körper-Swing/Slam (Kopf/Körper/Arme/Beine), aus in Blockbench
 * handanimierten Keyframe-Sequenzen übernommen. Lineare Interpolation
 * zwischen den Keyframes.
 *
 * SLAM 2.0: neue, längere Animation (6.3333s statt vorher 3.2917s),
 * jetzt inklusive Skalierung (Arme/Beine/Körper "pumpen" sich während
 * des Einschlags kurz auf). Die Axt selbst bewegt sich beim Slam nicht
 * mehr eigenständig (bleibt starr in der Hand) - deshalb keine eigene
 * Axt-Animation mehr für Slam nötig.
 */
@Mixin(PlayerModel.class)
public abstract class PlayerModelArmMixin<T extends LivingEntity> extends HumanoidModel<T> {

    public PlayerModelArmMixin(net.minecraft.client.model.geom.ModelPart root) {
        super(root);
    }

    @Shadow @Final public ModelPart jacket;
    @Shadow @Final public ModelPart leftSleeve;
    @Shadow @Final public ModelPart rightSleeve;
    @Shadow @Final public ModelPart leftPants;
    @Shadow @Final public ModelPart rightPants;

    @Inject(method = "setupAnim", at = @At("TAIL"))
    private void icegiant$axeArmOverride(T entity, float limbSwing, float limbSwingAmount,
                                          float ageInTicks, float netHeadYaw, float headPitch, CallbackInfo ci) {
        // a big player in Ice Giant armor dying: the giant's death animation (GiantDeath / GiantDeathClient)
        float deathSeconds = net.icegiant.client.GiantDeathClient.seconds(entity.getId());
        if (deathSeconds >= 0f) {
            net.icegiant.client.GiantDeathClient.applyPose(this, deathSeconds);
            return;
        }

        // Players and humanoid mobs using the player model (Ice Giant) holding one of the mod axes
        if (!(entity.getMainHandItem().getItem() instanceof net.icegiant.item.IceWeaponItem)) return;

        int id = entity.getId();

        float slamElapsed = SlamAnimationState.getElapsedSeconds(id);
        if (slamElapsed >= 0f) {
            applySlamPose(slamElapsed);
            return;
        }

        // Giant Rune Sword's wide sweep (key N, SwordSweepAbility): Rubenbuben4's flat slash, left to right
        float sweepElapsed = net.icegiant.client.SweepAnimationState.getElapsedSeconds(id);
        if (sweepElapsed >= 0f) {
            applySweepPose(sweepElapsed);
            return;
        }

        // Swing is now only triggered by right-click (see WeaponAnimationEvents
        // + SwingStartBroadcastPacket) - NOT by the vanilla left-click attack
        // animation anymore, so left-click keeps the completely normal vanilla
        // arm swing (we simply don't touch the pose in that case, and whatever
        // the base setupAnim() already computed above stands as-is).
        float swingElapsed = SwingAnimationState.getElapsedSeconds(id);
        if (swingElapsed < 0f) return;

        applySwingPose(swingElapsed);
    }

    private void applySwingPose(float elapsed) {
        net.icegiant.client.AbilityPoses.swing(elapsed, this.head, this.body, this.rightArm, this.leftArm,
                this.rightLeg, this.leftLeg);
        copyOuterLayer();
    }

    /** Rubenbuben4's "Swing with the Sword" (SweepAnimation, since 2026-10-21; before: SweepPose). */
    private void applySweepPose(float elapsed) {
        net.icegiant.client.SweepAnimation.apply(elapsed, this.head, this.body, this.rightArm, this.leftArm,
                this.rightLeg, this.leftLeg);
        copyOuterLayer();
    }

    private void applySlamPose(float elapsed) {
        net.icegiant.client.AbilityPoses.slam(elapsed, this.head, this.body, this.rightArm, this.leftArm,
                this.rightLeg, this.leftLeg);
        copyOuterLayer();
    }

    private void copyOuterLayer() {
        this.hat.copyFrom(this.head);
        this.jacket.copyFrom(this.body);
        this.leftSleeve.copyFrom(this.leftArm);
        this.rightSleeve.copyFrom(this.rightArm);
        this.leftPants.copyFrom(this.leftLeg);
        this.rightPants.copyFrom(this.rightLeg);
    }

    /**
     * Grab & throw with the Ice Giant armor (PlayerGrabAbility): while a player holds a creature,
     * the right arm points straight forward - the creature sits in that fist (EntityGrabRiderMixin).
     * Declared after the axe override, so it runs after it and wins. The Ice Giant mob poses its
     * own arm in IceGiantModel.
     */
    @Inject(method = "setupAnim", at = @At("TAIL"))
    private void icegiant$grabArmPose(T entity, float limbSwing, float limbSwingAmount,
                                      float ageInTicks, float netHeadYaw, float headPitch, CallbackInfo ci) {
        if (!(entity instanceof Player player) || !(player.getFirstPassenger() instanceof LivingEntity)) return;
        if (net.icegiant.client.GiantDeathClient.seconds(entity.getId()) >= 0f) return;    // dying: no grab pose
        this.rightArm.xRot = (float) (-Math.PI / 2.0D);
        this.rightArm.yRot = 0.0F;
        this.rightArm.zRot = 0.0F;
        this.rightSleeve.copyFrom(this.rightArm);
    }
}
