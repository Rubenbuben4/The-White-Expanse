package net.icegiant.stealth;

import net.icegiant.IceGiantMod;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.entity.player.ArrowLooseEvent;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * How much noise each player makes right now, for the Ice Giants' hearing (Rubenbuben4, 2026-10-26: sneak past them).
 * 0 = silent ... 1 = as loud as sprinting. Moving: sprinting 1, walking 0.5, sneaking 0.1, standing still 0.
 * Actions add a short burst that fades over a second: breaking a block 1, a melee hit 0.9, jumping 0.7, letting an
 * arrow fly 0.5, placing a block 0.5. Server side only.
 */
@Mod.EventBusSubscriber(modid = IceGiantMod.MOD_ID)
public final class PlayerNoise {

    public static final float SPRINT = 1.0F, WALK = 0.5F, SNEAK = 0.1F;
    static final float BREAK = 1.0F, HIT = 0.9F, JUMP = 0.7F, SHOOT = 0.5F, PLACE = 0.5F;
    static final int BURST_TICKS = 20;                 // a burst fades to nothing over 1 s
    static final double MOVING = 0.03D;                // blocks per tick (sideways) that count as moving

    private static final class State {
        Vec3 last;
        float moving;
        float burst;
        long burstTick;
    }

    private static final Map<UUID, State> STATES = new HashMap<>();

    private PlayerNoise() {
    }

    /** The player's noise now (0..1). */
    public static float of(Player player) {
        State s = STATES.get(player.getUUID());
        if (s == null) return 0.0F;
        long age = player.level().getGameTime() - s.burstTick;
        float burst = age < BURST_TICKS ? s.burst * (1.0F - age / (float) BURST_TICKS) : 0.0F;
        return Math.max(s.moving, burst);
    }

    private static void burst(Player player, float loudness) {
        if (!(player instanceof ServerPlayer)) return;
        State s = STATES.computeIfAbsent(player.getUUID(), k -> new State());
        float now = of(player);
        s.burst = Math.max(now, loudness);
        s.burstTick = player.level().getGameTime();
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer player)) return;
        State s = STATES.computeIfAbsent(player.getUUID(), k -> new State());
        Vec3 pos = player.position();
        double moved = s.last == null ? 0.0D : Math.hypot(pos.x - s.last.x, pos.z - s.last.z);
        s.last = pos;
        if (moved < MOVING || player.isPassenger()) s.moving = 0.0F;
        else if (player.isCrouching()) s.moving = SNEAK;
        else if (player.isSprinting()) s.moving = SPRINT;
        else s.moving = WALK;
    }

    @SubscribeEvent
    public static void onJump(LivingEvent.LivingJumpEvent event) {
        if (event.getEntity() instanceof Player p) burst(p, p.isCrouching() ? SNEAK : JUMP);
    }

    @SubscribeEvent
    public static void onBreak(BlockEvent.BreakEvent event) {
        burst(event.getPlayer(), BREAK);
    }

    @SubscribeEvent
    public static void onPlace(BlockEvent.EntityPlaceEvent event) {
        if (event.getEntity() instanceof Player p) burst(p, PLACE);
    }

    @SubscribeEvent
    public static void onShoot(ArrowLooseEvent event) {
        burst(event.getEntity(), SHOOT);
    }

    @SubscribeEvent
    public static void onAttack(AttackEntityEvent event) {
        burst(event.getEntity(), HIT);
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        STATES.remove(event.getEntity().getUUID());
    }
}
