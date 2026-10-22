package net.icegiant.effect;

import net.icegiant.IceGiantMod;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * "Betäubt" - applied by the axe/Ice Giant slam once a knocked-back entity lands
 * (see NordicAxeSlamAbility). While active the entity can't walk (movement speed
 * x0, synced to the client for players) and can't jump.
 */
@Mod.EventBusSubscriber(modid = IceGiantMod.MOD_ID)
public class StunEffect extends MobEffect {

    private static final String SPEED_MODIFIER_UUID = "5f6b1c2e-8a3d-4c7e-9b1f-2d4e6a8c0b13";

    public StunEffect() {
        super(MobEffectCategory.HARMFUL, 0x9FD8FF);
        this.addAttributeModifier(Attributes.MOVEMENT_SPEED, SPEED_MODIFIER_UUID, -1.0D,
                AttributeModifier.Operation.MULTIPLY_TOTAL);
    }

    /** Cancels the upward push of a jump (runs on both sides, so it also works for the local player). */
    @SubscribeEvent
    public static void onJump(LivingEvent.LivingJumpEvent event) {
        if (event.getEntity().hasEffect(net.icegiant.registry.ModEffects.STUN.get())) {
            var motion = event.getEntity().getDeltaMovement();
            event.getEntity().setDeltaMovement(motion.x, Math.min(0.0D, motion.y), motion.z);
        }
    }
}
