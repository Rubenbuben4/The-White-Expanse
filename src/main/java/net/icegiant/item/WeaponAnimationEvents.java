package net.icegiant.item;

import net.icegiant.IceGiantMod;
import net.icegiant.network.NetworkHandler;
import net.icegiant.network.SwingStartBroadcastPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;
import software.bernie.geckolib.animatable.GeoItem;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Rechtsklick mit einer IceGeoWeaponItem-Waffe (Nordic Axe, Gravebite, ...)
 * löst den Schwung aus: GeckoLib-Item-Animation + Körperhaltung übers Mixin +
 * Swoosh-Sound + Flächenschaden vor dem Spieler.
 * <p>
 * Die Trefferzone ist eine "Scheibe" vor dem Spieler (Reichweite, Breite und
 * Höhe UNABHÄNGIG voneinander einstellbar) und skaliert automatisch mit der
 * tatsächlichen Spielergröße (player.getBbWidth()) - falls der Spieler durch
 * einen Skalierungs-Effekt größer ist (Ice Giant-Thema), wird die Trefferzone
 * proportional größer, statt fest auf Vanilla-Spielergröße zu bleiben.
 * <p>
 * Linksklick bleibt der ganz normale vanilla Nahkampf-Angriff (Einzelziel,
 * normale Arm-Animation) - keine eigene Logik hier.
 * <p>
 * "H" bleibt exklusiv für den Slam (siehe NordicAxeSlamAbility /
 * ClientKeyInputHandler).
 */
@Mod.EventBusSubscriber(modid = IceGiantMod.MOD_ID)
public class WeaponAnimationEvents {

    // Basis-Maße der Trefferzone bei Vanilla-Spielergröße (0.6 breit) -
    // werden mit dem tatsächlichen Größenverhältnis des Spielers skaliert.
    private static final double VANILLA_PLAYER_WIDTH = 0.6D;
    private static final double BASE_FORWARD_REACH = 5.0D;   // wie weit nach vorne
    private static final double BASE_HALF_WIDTH = 2.5D;      // wie breit (zu jeder Seite)
    private static final double BASE_HEIGHT_ABOVE = 4.0D;    // wie hoch über den Füßen
    private static final double BASE_HEIGHT_BELOW = 2.0D;    // wie weit unter den Füßen

    // Wie lange der Flächenschaden nach dem Schwung-Trigger verzögert wird,
    // damit er zeitlich zur Animation passt statt sofort beim Klick zu passieren.
    private static final int DAMAGE_DELAY_TICKS = 8; // 8 Ticks = 0.4 Sekunden

    private static final List<PendingHit> PENDING_HITS = new ArrayList<>();

    // Sperre gegen Mehrfach-Trigger, falls RightClickItem mehrfach pro Klick feuert.
    private static final int RETRIGGER_COOLDOWN_TICKS = 20; // ~1s, etwas kürzer als die Swing-Länge
    private static final Map<UUID, Integer> SWING_COOLDOWN = new HashMap<>();

    private static class PendingHit {
        final Player player;
        int ticksRemaining;

        PendingHit(Player player, int ticksRemaining) {
            this.player = player;
            this.ticksRemaining = ticksRemaining;
        }
    }

    @SubscribeEvent
    public static void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        if (event.getHand() != InteractionHand.MAIN_HAND) return;
        Player player = event.getEntity();
        triggerSwingAnimation(player);
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;

        if (!SWING_COOLDOWN.isEmpty()) {
            Iterator<Map.Entry<UUID, Integer>> cdIterator = SWING_COOLDOWN.entrySet().iterator();
            while (cdIterator.hasNext()) {
                Map.Entry<UUID, Integer> entry = cdIterator.next();
                int remaining = entry.getValue() - 1;
                if (remaining <= 0) {
                    cdIterator.remove();
                } else {
                    entry.setValue(remaining);
                }
            }
        }

        if (PENDING_HITS.isEmpty()) return;

        Iterator<PendingHit> iterator = PENDING_HITS.iterator();
        while (iterator.hasNext()) {
            PendingHit pending = iterator.next();
            pending.ticksRemaining--;
            if (pending.ticksRemaining <= 0) {
                hitEnemiesInFront(pending.player);
                iterator.remove();
            }
        }
    }

    private static void triggerSwingAnimation(Player player) {
        if (player.level().isClientSide()) return;

        ItemStack mainHand = player.getMainHandItem();
        if (!(mainHand.getItem() instanceof IceGeoWeaponItem weapon)) return;

        UUID uuid = player.getUUID();
        Integer cooldown = SWING_COOLDOWN.get(uuid);
        if (cooldown != null && cooldown > 0) return; // noch gesperrt
        SWING_COOLDOWN.put(uuid, RETRIGGER_COOLDOWN_TICKS);

        long id = GeoItem.getOrAssignId(mainHand, (ServerLevel) player.level());
        weapon.triggerAnim(player, id, IceGeoWeaponItem.SWING_CONTROLLER, IceGeoWeaponItem.SWING_TRIGGER);

        // Körperhaltung (Arme/Kopf/Beine) für ALLE Clients synchronisieren -
        // das PlayerModelArmMixin liest SwingAnimationState statt der
        // vanilla Angriffs-Animation, weil Rechtsklick die nicht setzt.
        if (player instanceof ServerPlayer serverPlayer) {
            NetworkHandler.INSTANCE.send(PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> serverPlayer),
                    new SwingStartBroadcastPacket(player.getId()));
        }

        // Partikel-Spawn läuft über AxeParticleTracker (client-seitig).
        player.level().playSound(null, player.blockPosition(),
                net.icegiant.registry.ModSounds.NORDIC_AXE_SWOOSH.get(),
                net.minecraft.sounds.SoundSource.PLAYERS, 1.0f, 1.0f);

        PENDING_HITS.add(new PendingHit(player, DAMAGE_DELAY_TICKS));
    }

    private static void hitEnemiesInFront(Player player) {
        if (player.level().isClientSide()) return;

        double damage = player.getAttribute(Attributes.ATTACK_DAMAGE) != null
                ? player.getAttribute(Attributes.ATTACK_DAMAGE).getValue()
                : 1.0D;

        // Skaliert die ganze Trefferzone mit der tatsächlichen Spielerbreite,
        // statt fest auf Vanilla-Maße zu bleiben - ein größer skalierter
        // Spieler (Ice Giant) trifft entsprechend proportional größer.
        double scaleFactor = Math.max(1.0D, player.getBbWidth() / VANILLA_PLAYER_WIDTH);
        double forwardReach = BASE_FORWARD_REACH * scaleFactor;
        double halfWidth = BASE_HALF_WIDTH * scaleFactor;
        double heightAbove = BASE_HEIGHT_ABOVE * scaleFactor;
        double heightBelow = BASE_HEIGHT_BELOW * scaleFactor;

        Vec3 feet = player.position();
        Vec3 look = player.getLookAngle();
        Vec3 forward = new Vec3(look.x, 0, look.z).normalize(); // Schwung ist horizontal - Blickneigung zählt nicht

        // Grobe (achsenausgerichtete) Vorauswahl, damit wir nicht die ganze
        // Welt durchsuchen müssen - die eigentliche Form wird unten geprüft.
        double searchRadius = forwardReach + halfWidth;
        AABB searchArea = new AABB(
                feet.x - searchRadius, feet.y - heightBelow, feet.z - searchRadius,
                feet.x + searchRadius, feet.y + heightAbove, feet.z + searchRadius);

        for (LivingEntity target : player.level().getEntitiesOfClass(LivingEntity.class, searchArea,
                e -> e != player && e.isAlive())) {

            Vec3 toTarget = target.position().subtract(feet);

            // Projektion auf die Blickrichtung (nach vorne) und die dazu
            // senkrechte Achse (zur Seite) - ergibt eine rechteckige
            // "Scheibe" vor dem Spieler statt eines Kegels/einer Kugel.
            double forwardDist = toTarget.x * forward.x + toTarget.z * forward.z;
            if (forwardDist < 0 || forwardDist > forwardReach) continue;

            double lateralDist = Math.abs(toTarget.x * forward.z - toTarget.z * forward.x);
            if (lateralDist > halfWidth) continue;

            double verticalDist = toTarget.y;
            if (verticalDist < -heightBelow || verticalDist > heightAbove) continue;

            target.hurt(player.damageSources().playerAttack(player), (float) damage);
        }
    }
}
