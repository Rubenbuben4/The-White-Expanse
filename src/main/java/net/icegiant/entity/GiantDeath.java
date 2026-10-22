package net.icegiant.entity;

import net.icegiant.IceGiantMod;
import net.icegiant.item.IceGiantArmorItem;
import net.icegiant.network.GiantDeathPacket;
import net.icegiant.network.NetworkHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.EntityLeaveLevelEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The Ice Giant's death ("dying_1", by Rubenbuben4) for players, too: a player who is at least
 * {@value #MIN_SCALE} times as big as normal (Pehkui, Identity morph, ...) and wears Ice Giant armor
 * falls on his back like the giant - 5.5 s instead of vanilla's 1 s tip-over, with the thump, crater and
 * earthquake when he hits the ground. The inventory still drops at once, like always; the armor he wore
 * is still drawn on him while he falls (GiantDeathClient).
 * <p>
 * Server: decided when he dies (LivingDeathEvent - the armor is still on then), told to everyone who sees
 * him (GiantDeathPacket). LivingEntityDeathMixin hands LivingEntity.tickDeath over to {@link #tickDeath}.
 * The Ice Giant mob itself does the same in IceGiantEntity.tickDeath and uses {@link #impact} as well.
 */
@Mod.EventBusSubscriber(modid = IceGiantMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class GiantDeath {

    // ---- TUNE ----
    public static final float MIN_SCALE = 1.75F;               // "about twice as big" (hitbox width / 0.6)
    public static final int DEATH_TICKS = IceGiantEntity.DEATH_TICKS;           // 5.5 s, like the giant
    public static final int DEATH_IMPACT_TICK = IceGiantEntity.DEATH_IMPACT_TICK; // his back hits the ground
    static final int CRATER_LENGTH = 6, CRATER_HALF_WIDTH = 1; // at the giant's size (IceGiantEntity.SCALE)

    /** Server: players dying the giant's death (entity id -> size). */
    private static final Map<Integer, Float> SERVER = new ConcurrentHashMap<>();
    /** Client: entity ids dying the giant's death (filled by GiantDeathPacket). */
    public static final Set<Integer> CLIENT = ConcurrentHashMap.newKeySet();

    private GiantDeath() {
    }

    /** Size of an entity compared to a normal player (1 = normal), from its hitbox. */
    public static float sizeOf(LivingEntity entity) {
        return Math.max(1.0F, entity.getBbWidth() / 0.6F);
    }

    /** Big enough and wearing at least one piece of the Ice Giant armor. */
    public static boolean qualifies(Player player) {
        if (sizeOf(player) < MIN_SCALE) return false;
        for (EquipmentSlot slot : new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
            if (player.getItemBySlot(slot).getItem() instanceof IceGiantArmorItem) return true;
        }
        return false;
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onDeath(LivingDeathEvent event) {
        if (event.isCanceled() || !(event.getEntity() instanceof ServerPlayer player)) return;
        if (!qualifies(player)) return;
        SERVER.put(player.getId(), sizeOf(player));
        ItemStack[] armor = new ItemStack[4];                                 // feet, legs, chest, head
        for (int i = 0; i < 4; i++) armor[i] = player.getInventory().armor.get(i).copy();
        NetworkHandler.INSTANCE.send(PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> player),
                new GiantDeathPacket(player.getId(), armor));
    }

    @SubscribeEvent
    public static void onLeave(EntityLeaveLevelEvent event) {
        Entity e = event.getEntity();
        if (event.getLevel().isClientSide()) CLIENT.remove(e.getId());
        else SERVER.remove(e.getId());
    }

    /**
     * Called at the start of LivingEntity.tickDeath (LivingEntityDeathMixin). Returns true if this entity dies
     * the giant's death - then vanilla's own death tick is skipped.
     */
    public static boolean tickDeath(LivingEntity entity) {
        boolean client = entity.level().isClientSide();
        if (client ? !CLIENT.contains(entity.getId()) : !SERVER.containsKey(entity.getId())) return false;
        ++entity.deathTime;
        if (client) return true;
        if (entity.deathTime == DEATH_IMPACT_TICK && entity.level() instanceof ServerLevel level) {
            impact(level, entity, SERVER.getOrDefault(entity.getId(), MIN_SCALE));
        }
        if (entity.deathTime >= DEATH_TICKS && !entity.isRemoved()) {
            SERVER.remove(entity.getId());
            entity.level().broadcastEntityEvent(entity, (byte) 60);              // poof
            entity.remove(Entity.RemovalReason.KILLED);
        }
        return true;
    }

    /**
     * His back hits the ground: thump, a temporary crater where he lies (3 x 6 blocks at the giant's size,
     * shorter for smaller ones), dust and an earthquake around.
     */
    public static void impact(ServerLevel level, LivingEntity entity, float scale) {
        double yaw = Math.toRadians(entity.yBodyRot);
        double bx = Math.sin(yaw), bz = -Math.cos(yaw);                       // backwards (falls on his back)
        double sx = -bz, sz = bx;                                              // sideways
        double fx = entity.getX(), fz = entity.getZ();
        int length = Math.max(3, Math.round(CRATER_LENGTH * scale / IceGiantEntity.SCALE));
        int groundY = entity.blockPosition().getY() - 1;
        for (int a = 1; a <= length; a++) {
            for (int b = -CRATER_HALF_WIDTH; b <= CRATER_HALF_WIDTH; b++) {
                double x = fx + bx * (a - 0.5) + sx * b, z = fz + bz * (a - 0.5) + sz * b;
                BlockPos pos = null;
                for (int y = groundY + 1; y >= groundY - 2; y--) {               // the ground surface there
                    BlockPos p = BlockPos.containing(x, y, z);
                    if (!level.getBlockState(p).isAir() && level.getBlockState(p).getFluidState().isEmpty()) {
                        pos = p;
                        break;
                    }
                }
                if (pos == null) continue;
                float depth = b == 0 ? 1.0F : 0.6F;
                if (a == 1 || a == length) depth *= 0.7F;                       // shallower at head and feet
                float dirX = (float) (b == 0 ? bx : sx * b), dirZ = (float) (b == 0 ? bz : sz * b);
                net.icegiant.item.NordicAxeSlamAbility.fractureGround(level, pos, depth, dirX, dirZ);
            }
        }
        Vec3 middle = new Vec3(fx + bx * length * 0.5, groundY + 1, fz + bz * length * 0.5);
        level.playSound(null, middle.x, middle.y, middle.z, net.icegiant.registry.ModSounds.ICE_GIANT_FALL.get(),
                entity.getSoundSource(), 3.0F, 1.0F);
        net.icegiant.item.NordicAxeSlamAbility.spawnSmokeBurst(level, middle);
        net.icegiant.item.NordicAxeSlamAbility.shakeNearbyPlayers(level, entity, scale);
    }
}
