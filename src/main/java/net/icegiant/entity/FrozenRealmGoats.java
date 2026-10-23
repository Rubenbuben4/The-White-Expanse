package net.icegiant.entity;

import net.icegiant.IceGiantMod;
import net.icegiant.dimension.ModDimensions;
import net.minecraft.world.entity.animal.goat.Goat;
import net.minecraftforge.event.entity.EntityEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Mountain goats of the Frozen Realm are a bit bigger than normal goats. Server and client decide the
 * same way (the goat is in the Frozen Realm), so nothing needs to be synced: the hitbox grows here,
 * the model is scaled in client/FrozenRealmGoatRender. A goat that leaves the realm is normal again.
 * Goats spawn on our painted mountain rock thanks to the block tag minecraft:goats_spawnable_on.
 */
@Mod.EventBusSubscriber(modid = IceGiantMod.MOD_ID)
public final class FrozenRealmGoats {

    /** Size factor for goats in the Frozen Realm (hitbox and model). */
    public static final float SCALE = 1.4F;

    private FrozenRealmGoats() {
    }

    public static boolean isBigGoat(Object entity) {
        return entity instanceof Goat goat && goat.level() != null
                && goat.level().dimension() == ModDimensions.FROZEN_REALM;
    }

    /**
     * Every goat in the Frozen Realm is a Frost Goat (FrostGoatEntity, the rideable goats, 2026-10-23): a vanilla goat
     * that joins the realm - spawned with a new chunk, naturally, from an egg or loaded from an older save - is swapped
     * for one on the next tick (same place, look, age and name). Swapping right in the join event could disturb the
     * chunk loading the goat, so it waits a tick.
     */
    private static final java.util.List<Goat> PENDING = new java.util.ArrayList<>();

    @SubscribeEvent
    public static void onJoin(net.minecraftforge.event.entity.EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide() || event.getEntity().getType() != net.minecraft.world.entity.EntityType.GOAT) return;
        if (event.getLevel().dimension() != ModDimensions.FROZEN_REALM) return;
        synchronized (PENDING) {
            PENDING.add((Goat) event.getEntity());
        }
    }

    @SubscribeEvent
    public static void onLevelTick(net.minecraftforge.event.TickEvent.LevelTickEvent event) {
        if (event.phase != net.minecraftforge.event.TickEvent.Phase.END || !(event.level instanceof net.minecraft.server.level.ServerLevel level)) return;
        if (level.dimension() != ModDimensions.FROZEN_REALM) return;
        java.util.List<Goat> todo;
        synchronized (PENDING) {
            if (PENDING.isEmpty()) return;
            todo = new java.util.ArrayList<>(PENDING);
            PENDING.clear();
        }
        for (Goat old : todo) {
            if (old.isRemoved() || old.level() != level) continue;
            net.icegiant.entity.FrostGoatEntity goat = net.icegiant.registry.ModEntityTypes.FROST_GOAT.get().create(level);
            if (goat == null) continue;
            goat.moveTo(old.getX(), old.getY(), old.getZ(), old.getYRot(), old.getXRot());
            goat.setYHeadRot(old.getYHeadRot());
            goat.setAge(old.getAge());
            if (old.hasCustomName()) goat.setCustomName(old.getCustomName());
            if (old.isPersistenceRequired()) goat.setPersistenceRequired();
            goat.finalizeSpawn(level, level.getCurrentDifficultyAt(goat.blockPosition()), net.minecraft.world.entity.MobSpawnType.CONVERSION, null, null);
            old.discard();
            level.addFreshEntity(goat);
        }
    }

    @SubscribeEvent
    public static void onSize(EntityEvent.Size event) {
        if (isBigGoat(event.getEntity())) {
            event.setNewSize(event.getNewSize().scale(SCALE), true);
        }
    }
}
