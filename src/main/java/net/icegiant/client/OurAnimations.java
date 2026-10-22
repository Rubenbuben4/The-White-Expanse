package net.icegiant.client;

import net.icegiant.entity.IceGiantEntity;
import net.icegiant.item.IceWeaponItem;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

/**
 * Is one of the mod's own body animations playing on this entity right now? Then animation mods that
 * re-pose the whole model (EMF / Fresh Animations) are asked to pause for it (EmfCompat), so ours is
 * not overwritten. Same conditions as in PlayerModelArmMixin and IceGiantModel.
 * <p>
 * The Ice Giant uses the player model, so Fresh Animations' player extension animates him too (walking,
 * standing) - Rubenbuben4 wants to keep that (2026-10-21). Until then only the weapon poses paused it; his
 * war cry, grab and throw and his fall when he dies were overwritten by it.
 */
public final class OurAnimations {

    private OurAnimations() {
    }

    public static boolean isPlaying(LivingEntity entity) {
        int id = entity.getId();
        if (GiantDeathClient.isDying(id)) return true;                              // a big player's giant death
        if (entity instanceof IceGiantEntity giant
                && (giant.isDeadOrDying()                                         // his fall (health, not deathTime:
                || giant.isRoaring()                                              //   IceGiantRenderer hides that)
                || giant.isGrabbing())) return true;                              // war cry; grab and throw
        if (entity.getMainHandItem().getItem() instanceof IceWeaponItem
                && (SlamAnimationState.getElapsedSeconds(id) >= 0.0F                // axe slam, sword ground cut
                || SweepAnimationState.getElapsedSeconds(id) >= 0.0F                // sword wide sweep
                || SwingAnimationState.getElapsedSeconds(id) >= 0.0F)) return true; // axe swing
        return entity instanceof Player player && player.getFirstPassenger() instanceof LivingEntity;   // grab
    }
}
