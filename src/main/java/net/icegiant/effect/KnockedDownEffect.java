package net.icegiant.effect;

import net.icegiant.IceGiantMod;
import net.icegiant.registry.ModEffects;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * "Niedergeworfen" - a player run over by the Ice Giant's wall-breaker dash
 * (IceDashAbility) lies prone on the floor: forced into the crawl/swim pose
 * until the effect ends. They can still crawl slowly.
 * <p>
 * The pose is forced with Forge's {@code Player#setForcedPose} on both sides
 * (effects are synced to the affected player, so the local client knows too);
 * other players see the pose through the normal entity data sync.
 */
@Mod.EventBusSubscriber(modid = IceGiantMod.MOD_ID)
public class KnockedDownEffect extends MobEffect {

    /** Players whose pose we forced (per side - client and server have their own Player objects). */
    private static final Map<Player, Boolean> FORCED = new WeakHashMap<>();

    public KnockedDownEffect() {
        super(MobEffectCategory.HARMFUL, 0x6E8FA8);
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.START) return;
        Player player = event.player;
        if (player.hasEffect(ModEffects.KNOCKED_DOWN.get()) && !player.isSpectator()) {
            player.setForcedPose(Pose.SWIMMING);
            FORCED.put(player, Boolean.TRUE);
        } else if (FORCED.remove(player) != null && player.getForcedPose() == Pose.SWIMMING) {
            player.setForcedPose(null); // vanilla keeps them crawling if there is no room to stand up
        }
    }
}
