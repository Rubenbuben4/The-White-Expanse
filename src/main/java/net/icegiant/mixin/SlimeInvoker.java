package net.icegiant.mixin;

import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Slime;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Lets our slime code use the slime's own (protected) attack and jump helpers (also the magma cube's overrides). */
@Mixin(Slime.class)
public interface SlimeInvoker {

    @Invoker("dealDamage")
    void icegiant$dealDamage(LivingEntity target);

    @Invoker("isDealsDamage")
    boolean icegiant$isDealsDamage();

    @Invoker("getJumpDelay")
    int icegiant$getJumpDelay();

    @Invoker("getJumpSound")
    SoundEvent icegiant$getJumpSound();

    @Invoker("getSoundVolume")
    float icegiant$getSoundVolume();
}
