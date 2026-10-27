package net.icegiant.mixin;

import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.control.MoveControl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Swaps a mob's move control (the slimes get SlimeControl, which can crawl). */
@Mixin(Mob.class)
public interface MobMoveControlAccessor {

    @Accessor("moveControl")
    void icegiant$setMoveControl(MoveControl control);
}
