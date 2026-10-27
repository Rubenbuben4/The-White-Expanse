package net.icegiant.slime;

import net.icegiant.IceGiantMod;
import net.icegiant.mixin.MobMoveControlAccessor;
import net.icegiant.network.CorpsePacket;
import net.icegiant.network.NetworkHandler;
import net.icegiant.network.SlimeTintPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.monster.Slime;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.Tags;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.EntityLeaveLevelEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;

import java.util.HashMap;
import java.util.Map;

/**
 * Wires the new slime life into every slime and magma cube (Rubenbuben4, 2026-10-27): replaces vanilla's goals and
 * move control (SlimeControl, SlimeGoals), counts hunger down, marks the bodies of what they kill (SlimeCorpses) and
 * keeps the clients up to date (tint of the jelly, bodies).
 */
@Mod.EventBusSubscriber(modid = IceGiantMod.MOD_ID)
public final class SlimeLife {

    /** Client: tint of each slime's outer jelly (from SlimeTintPacket). */
    public static final Map<Integer, Integer> CLIENT_TINT = new HashMap<>();

    static final float PREY_MAX_HEALTH = 60.0F;

    private SlimeLife() {
    }

    /** What a hungry slime (size 2 and up) hunts, and whose bodies stay when a slime kills them. */
    public static boolean isPrey(LivingEntity e) {
        if (e instanceof Player || e instanceof Slime || e instanceof Creeper) return false;
        if (e.getType().is(Tags.EntityTypes.BOSSES) || e.getMaxHealth() > PREY_MAX_HEALTH) return false;
        if (e instanceof net.icegiant.entity.IceGiantEntity) return false;
        return e instanceof Enemy || e instanceof AbstractVillager;
    }

    @SubscribeEvent
    public static void onJoin(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide() || !(event.getEntity() instanceof Slime s)) return;
        setup(s);
    }

    private static void setup(Slime s) {
        s.goalSelector.removeAllGoals(g -> true);
        s.targetSelector.removeAllGoals(g -> true);
        ((MobMoveControlAccessor) s).icegiant$setMoveControl(new SlimeControl(s));
        s.goalSelector.addGoal(1, new SlimeGoals.Swim(s));
        s.goalSelector.addGoal(2, new SlimeGoals.Hunt(s));
        s.goalSelector.addGoal(3, new SlimeGoals.EatCorpse(s));
        s.goalSelector.addGoal(4, new SlimeGoals.EatItem(s));
        s.goalSelector.addGoal(5, new SlimeGoals.Graze(s));
        s.goalSelector.addGoal(7, new SlimeGoals.Wander(s));
        s.targetSelector.addGoal(1, new NearestAttackableTargetGoal<>(s, Player.class, 10, true, false,
                e -> Math.abs(e.getY() - s.getY()) <= 4.0D));
        s.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(s, IronGolem.class, true));
        s.targetSelector.addGoal(3, new NearestAttackableTargetGoal<>(s, LivingEntity.class, 10, true, false,
                e -> s.getSize() >= 2 && SlimeFood.isHungry(s) && isPrey(e)));
        SlimeFood.applyStats(s, false);
    }

    @SubscribeEvent
    public static void onTick(LivingEvent.LivingTickEvent event) {
        if (event.getEntity() instanceof Slime s && !s.level().isClientSide() && s.isAlive() && s.tickCount % 20 == 0) {
            SlimeFood.tickHunger(s);
        }
    }

    @SubscribeEvent
    public static void onDeath(LivingDeathEvent event) {
        LivingEntity victim = event.getEntity();
        if (victim.level().isClientSide() || !(event.getSource().getEntity() instanceof Slime) || !isPrey(victim)) return;
        SlimeCorpses.mark(victim);
        NetworkHandler.INSTANCE.send(PacketDistributor.TRACKING_ENTITY.with(() -> victim), new CorpsePacket(victim.getId()));
    }

    @SubscribeEvent
    public static void onStartTracking(PlayerEvent.StartTracking event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (event.getTarget() instanceof Slime s) {
            NetworkHandler.INSTANCE.send(PacketDistributor.PLAYER.with(() -> player), new SlimeTintPacket(s.getId(), SlimeFood.tint(s)));
        } else if (event.getTarget() instanceof LivingEntity e && e.isDeadOrDying() && SlimeCorpses.isCorpse(e)) {
            NetworkHandler.INSTANCE.send(PacketDistributor.PLAYER.with(() -> player), new CorpsePacket(e.getId()));
        }
    }

    @SubscribeEvent
    public static void onLeave(EntityLeaveLevelEvent event) {
        if (!event.getLevel().isClientSide()) return;
        SlimeCorpses.CLIENT.remove(event.getEntity().getId());
        CLIENT_TINT.remove(event.getEntity().getId());
    }

    static void syncTint(Slime s) {
        if (s.level().isClientSide() || s.isRemoved()) return;
        NetworkHandler.INSTANCE.send(PacketDistributor.TRACKING_ENTITY.with(() -> s), new SlimeTintPacket(s.getId(), SlimeFood.tint(s)));
    }

    /**
     * From SlimeSizeMixin at the end of each tick. Client: while a slime crawls it wobbles (its squish pulses - vanilla
     * only squishes on jumps and landings) and its glibber sound plays.
     */
    public static void afterTick(Slime s) {
        if (!s.level().isClientSide()) return;
        double v = Math.hypot(s.getX() - s.xo, s.getZ() - s.zo);
        if (s.onGround() && v > 0.008D) {
            s.targetSquish = 0.3F * Mth.sin(s.tickCount * 0.6F + s.getId());
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> net.icegiant.client.SlimeClient.crawling(s));
        }
    }
}
