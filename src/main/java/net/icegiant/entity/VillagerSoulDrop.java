package net.icegiant.entity;

import net.icegiant.IceGiantMod;
import net.icegiant.registry.ModItems;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.living.LivingDropsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * A villager killed by a player drops Pure Soul Energy with a chance of {@value #CHANCE} (Rubenbuben4, 2026-10-22:
 * "villagers when they are killed have 10% chance to drop Pure Soul"). It is the last ingredient of the Frost Portal
 * Key that had no source - so the key, and the way into the Frozen Realm, can now be made in survival.
 * Like vanilla's rare drops it needs a player kill (also a mob that dies soon after a player hit it), so iron golems,
 * zombies and traps do not farm it. Zombie villagers and wandering traders are not villagers here.
 */
@Mod.EventBusSubscriber(modid = IceGiantMod.MOD_ID)
public final class VillagerSoulDrop {

    public static final float CHANCE = 0.10F;

    private VillagerSoulDrop() {
    }

    @SubscribeEvent
    public static void onDrops(LivingDropsEvent event) {
        if (!(event.getEntity() instanceof Villager villager) || villager.level().isClientSide()) return;
        if (!event.isRecentlyHit()) return;                                     // killed by a player
        if (villager.getRandom().nextFloat() >= CHANCE) return;
        ItemEntity drop = new ItemEntity(villager.level(), villager.getX(), villager.getY() + 0.5, villager.getZ(),
                new ItemStack(ModItems.PURE_SOUL_ENERGY.get()));
        drop.setDefaultPickUpDelay();
        event.getDrops().add(drop);
    }
}
