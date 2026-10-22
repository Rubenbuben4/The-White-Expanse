package net.icegiant.entity.gnoll;

import net.icegiant.IceGiantMod;
import net.icegiant.entity.IceGiantEntity;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.AbstractIllager;
import net.minecraft.world.entity.monster.Ravager;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.monster.ZombifiedPiglin;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Villager enemies hunt the Ice Giants' side too: zombies (also husks, drowned, zombie villagers),
 * illagers (vindicators, pillagers, evokers, illusioners) and ravagers get the same "attack on
 * sight" goal for Gnolls and Ice Giants that they have for villagers. Giants and Gnolls already
 * fight hostile monsters back (IceGiantEntity.isHostileMonsterTarget).
 * <p>
 * No friendly fire between Ice Giants and Gnolls: a giant's slam, dash or thrown block does not
 * hurt its Gnolls, and a Gnoll's arrow, blade or spirit does not hurt a giant or another Gnoll.
 * Cancelling the attack also means nobody turns against an ally by accident.
 */
@Mod.EventBusSubscriber(modid = IceGiantMod.MOD_ID)
public final class GnollEvents {

    private GnollEvents() {
    }

    /** Same priority and "no line of sight needed" as their vanilla villager-targeting goal. */
    private static final int VILLAGER_TARGET_PRIORITY = 3;

    @SubscribeEvent
    public static void onJoinLevel(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide() || !(event.getEntity() instanceof Mob mob)) return;
        boolean zombie = mob instanceof Zombie && !(mob instanceof ZombifiedPiglin); // piglins stay neutral
        if (!zombie && !(mob instanceof AbstractIllager) && !(mob instanceof Ravager)) return;
        // goals are not saved, so every newly loaded mob instance gets them exactly once here
        mob.targetSelector.addGoal(VILLAGER_TARGET_PRIORITY,
                new NearestAttackableTargetGoal<>(mob, GnollEntity.class, false));
        mob.targetSelector.addGoal(VILLAGER_TARGET_PRIORITY,
                new NearestAttackableTargetGoal<>(mob, IceGiantEntity.class, false));
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onLivingAttack(LivingAttackEvent event) {
        if (!GnollEntity.isFriend(event.getEntity())) return;
        Entity attacker = event.getSource().getEntity();
        Entity direct = event.getSource().getDirectEntity();
        boolean fromAlly = GnollEntity.isFriend(attacker) || GnollEntity.isFriend(direct);
        // only Gnoll-related pairs - giants hurting giants stays as it was
        boolean involvesGnoll = event.getEntity() instanceof GnollEntity
                || attacker instanceof GnollEntity || direct instanceof GnollEntity;
        if (fromAlly && involvesGnoll && attacker != event.getEntity()) {
            event.setCanceled(true);
        }
    }
}
