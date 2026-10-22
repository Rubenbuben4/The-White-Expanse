package net.icegiant.client;

import net.icegiant.IceGiantMod;
import net.icegiant.item.IceGeoWeaponItem;
import net.icegiant.registry.ModParticles;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.Map;

/**
 * Löst beim Schwung-Start (Rechtsklick, siehe SwingAnimationState) EINEN
 * Slash-Partikel aus - fest vor dem Spieler auf Brusthöhe, liegend parallel
 * zum Boden. KEINE Abhängigkeit mehr von der Axt-Rotation oder der vanilla
 * Angriffs-Animation (Linksklick löst absichtlich KEINEN Slash mehr aus,
 * das ist jetzt der ganz normale vanilla Angriff) - nur noch Spielerposition
 * und Blickrichtung (horizontal) zählen.
 */
@Mod.EventBusSubscriber(modid = IceGiantMod.MOD_ID, value = Dist.CLIENT)
public class AxeParticleTracker {

    private static final double FORWARD_DISTANCE = 1.0; // wie weit vor dem Spieler
    private static final double CHEST_HEIGHT = 1.3;      // ungefähre Brusthöhe über den Füßen

    private static final Map<Integer, Boolean> PREV_SWING_ACTIVE = new HashMap<>();

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;

        for (Player player : mc.level.players()) {
            // Any GeckoLib weapon (Nordic Axe, Gravebite, ...) gets the same
            // slash effect - no model-name check, so a new weapon added via
            // IceGeoWeaponItem automatically gets this too.
            if (!(player.getMainHandItem().getItem() instanceof IceGeoWeaponItem weapon)) continue;

            int id = player.getId();
            boolean currentlyActive = SwingAnimationState.getElapsedSeconds(id) >= 0f;
            boolean wasActive = Boolean.TRUE.equals(PREV_SWING_ACTIVE.get(id));
            boolean justStarted = currentlyActive && !wasActive;
            PREV_SWING_ACTIVE.put(id, currentlyActive);

            if (!justStarted) continue; // nur EIN Partikel pro Schwung

            // Nur horizontale Blickrichtung (Yaw) - Hoch-/Runterschauen soll
            // die Partikel-Position nicht beeinflussen.
            float yawRad = (float) Math.toRadians(player.getYRot());
            double forwardX = -Math.sin(yawRad);
            double forwardZ = Math.cos(yawRad);

            Vec3 spawnPos = player.position()
                    .add(0, CHEST_HEIGHT, 0)
                    .add(forwardX * FORWARD_DISTANCE, 0, forwardZ * FORWARD_DISTANCE);

            // Fest um 90° gekippt (liegt parallel zum Boden statt aufrecht zu
            // stehen), zusätzlich um Yaw gedreht, damit die "Vorderseite" des
            // Bildes zum Spieler zeigt. dx/dy/dz sind hier KEINE Geschwindigkeit,
            // sondern die feste Rotation in Radiant (siehe NordicAxeSlashParticle).
            double rotX = Math.toRadians(90);
            double rotY = yawRad;
            double rotZ = 0;

            mc.level.addParticle(ModParticles.NORDIC_AXE_SLASH.get(),
                    spawnPos.x, spawnPos.y, spawnPos.z, rotX, rotY, rotZ);
        }
    }
}
