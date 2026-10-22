package net.icegiant.mixin;

import net.icegiant.item.PlayerGrabAbility;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * A creature held by a player (grab with full Ice Giant armor, PlayerGrabAbility) sits in
 * that player's right fist instead of on top of their head. Players can't override
 * positionRider themselves, so this hooks it for every entity and only acts when the
 * vehicle is a player carrying a living creature (only possible through the grab).
 * Runs on both sides, so the held creature moves smoothly.
 */
@Mixin(Entity.class)
public abstract class EntityGrabRiderMixin {

    @Inject(method = "positionRider(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/entity/Entity$MoveFunction;)V",
            at = @At("HEAD"), cancellable = true)
    private void icegiant$holdInFist(Entity passenger, Entity.MoveFunction move, CallbackInfo ci) {
        if ((Object) this instanceof Player grabber && passenger instanceof LivingEntity) {
            Vec3 held = PlayerGrabAbility.heldPosition(grabber, passenger, 1.0F);
            move.accept(passenger, held.x, held.y, held.z);
            ci.cancel();
        }
    }
}
