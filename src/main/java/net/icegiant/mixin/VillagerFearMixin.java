package net.icegiant.mixin;

import net.icegiant.raid.RaidGoals;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.sensing.VillagerHostilesSensor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Villagers panic and flee from Frost Raid attackers (Ice Giants and Gnolls) like they do from zombies. */
@Mixin(VillagerHostilesSensor.class)
public abstract class VillagerFearMixin {

    private static final double FEAR_DISTANCE = 12.0D;

    @Inject(method = "isMatchingEntity", at = @At("HEAD"), cancellable = true)
    private void icegiant$fearRaiders(LivingEntity villager, LivingEntity other, CallbackInfoReturnable<Boolean> cir) {
        if (RaidGoals.isRaider(other)) cir.setReturnValue(villager.distanceToSqr(other) <= FEAR_DISTANCE * FEAR_DISTANCE);
    }
}
