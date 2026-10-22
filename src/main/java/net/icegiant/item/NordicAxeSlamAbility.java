package net.icegiant.item;

import net.icegiant.IceGiantMod;
import net.icegiant.client.SlamAnimationState;
import net.icegiant.compat.KnockbackCompat;
import net.icegiant.network.NetworkHandler;
import net.icegiant.network.SlamStartBroadcastPacket;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
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
 * Rechtsklick (oder Taste "H") mit der Nordic Axe: Spieler wird kurz
 * bewegungsunfähig, spielt die Slam-2.0-Animation (6.3333s, Einschlag bei
 * 2.33s), und passend dazu Sound/Partikel/Knockback/Krater - alles auf
 * den Einschlag-Zeitpunkt abgestimmt, nicht mehr auf den Animationsstart.
 *
 * Die Axt selbst bewegt sich beim Slam nicht mehr eigenständig (bleibt
 * starr in der Hand) - deshalb kein GeckoLib-Trigger mehr für die
 * Axt-Animation beim Slam.
 */
@Mod.EventBusSubscriber(modid = IceGiantMod.MOD_ID)
public class NordicAxeSlamAbility {

    private static final int COOLDOWN_TICKS = 100;         // 5 Sekunden zwischen Nutzungen
    private static final int TOTAL_LOCK_TICKS = 127;        // 6.3333s - neue Slam-2.0-Animation
    private static final int IMPACT_TICKS = 47;             // 2.33s - der eigentliche Einschlag
    private static final int SOUND_START_TICKS = 36;        // 1.785s = 2.33s - 0.545s (Sound-Einschlagpunkt in der Audiodatei)
    private static final int CHARGE_PARTICLE_END_TICKS = 47; // Aufladen bis zum Einschlag durchgehend
    private static final int CRATER_LIFETIME_TICKS = 240;    // 12 Sekunden
    private static final int RING_DELAY_TICKS = 2;
    private static final int SMOKE_RING_COUNT = 8;       // wie viele Ausbreitungs-Schritte bis 4 Blöcke
    private static final int SMOKE_RING_DELAY_TICKS = 1; // schneller Schub nach außen (war 2)
    private static final int CHARGE_PARTICLES_PER_TICK = 4;

    private static final double KNOCKBACK_STRENGTH = 2.6D;
    private static final double KNOCKBACK_VERTICAL = 0.6D;
    private static final double AOE_RADIUS = 6.0D;
    private static final int GROUND_EFFECT_RADIUS = 6;
    private static final int MAX_GROUND_EFFECT_RADIUS = 10; // keeps giant slams from flattening the landscape

    /** Damage of the slam impact - ignores armor (damage type icegiant:slam, tag minecraft:bypasses_armor). */
    static final float SLAM_DAMAGE = 4.0F;
    /** Stun after being thrown: starts when the target lands again (or after STUN_MAX_WAIT_TICKS in the air/water). */
    private static final int STUN_TICKS = 40;           // 2 seconds
    private static final int STUN_MIN_AIR_TICKS = 3;    // ignore the first ticks while still on the ground
    private static final int STUN_MAX_WAIT_TICKS = 60;
    private static final net.minecraft.resources.ResourceKey<net.minecraft.world.damagesource.DamageType> SLAM_DAMAGE_TYPE =
            net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.DAMAGE_TYPE,
                    new net.minecraft.resources.ResourceLocation(IceGiantMod.MOD_ID, "slam"));

    /** Earthquake screen shake for players near the impact: radius (x size), duration, strength by distance. */
    private static final double EARTHQUAKE_RADIUS = 16.0D;
    private static final int EARTHQUAKE_TICKS = 40;     // 2 seconds, last second fades out

    /** Knocked-back entities waiting to land so the stun can start (entity -> ticks since the hit). */
    private static final Map<LivingEntity, Integer> AWAITING_STUN = new java.util.WeakHashMap<>();

    private static final List<PendingSlam> PENDING = new ArrayList<>();
    private static final Map<UUID, Vec3> LOCKED_POSITIONS = new HashMap<>();

    private record BlockInfo(BlockPos pos, BlockState state, float depthFactor, float dirX, float dirZ) {
    }

    private static class PendingSlam {
        final LivingEntity player; // the slamming entity (player or Ice Giant)
        final double scale;        // size factor: 1 for a player, more for bigger entities
        final int unlockTicks;     // when the position lock ends
        int ticksRemaining;
        boolean soundDone = false;
        boolean knockbackDone = false;
        boolean craterStarted = false;
        List<List<BlockInfo>> rings;
        int nextRingToActivate = 0;
        int ticksSinceCraterStart = 0;
        BlockPos impactCenter;
        int nextSmokeRing = 0;
        int ticksSinceSmokeStart = 0;

        PendingSlam(LivingEntity player, int ticksRemaining) {
            this.player = player;
            this.ticksRemaining = ticksRemaining;
            this.scale = Math.max(1.0D, player.getBbWidth() / 0.6D);
            // everyone stays locked for the whole slam animation (the client plays the 6.3 s
            // body pose) - for Ice Giants that doubles as a recovery window after each slam
            this.unlockTicks = TOTAL_LOCK_TICKS;
        }
    }

    // Rechtsklick löst NUR noch den Swing aus (siehe WeaponAnimationEvents) -
    // der Slam läuft jetzt ausschließlich über die "H"-Taste
    // (ClientKeyInputHandler -> SlamActivatePacket -> tryActivate).

    public static void tryActivate(Player player) {
        if (player.level().isClientSide()) return;
        // Any GeckoLib weapon (Nordic Axe, Gravebite, ...) gets the same slam
        // ability - no model-name check, so a new IceGeoWeaponItem
        // automatically gets it too.
        if (!(player.getMainHandItem().getItem() instanceof IceGeoWeaponItem weapon)) return;
        if (IceDashAbility.isDashing(player) || player.isVehicle()) return; // not while dashing or holding someone
        if (SwordGroundCutAbility.isCutting(player) || SwordSweepAbility.isSweeping(player)) return;   // nor during the sword's cut / sweep

        if (player.getCooldowns().isOnCooldown(weapon)) return;
        player.getCooldowns().addCooldown(weapon, COOLDOWN_TICKS);

        startSlam(player);
    }

    /**
     * Starts the slam for any living entity (player or mob such as the Ice Giant),
     * without weapon/cooldown checks - callers do those. Effects scale with the
     * entity's size (AOE radius, crater radius, impact distance).
     */
    public static void startSlam(LivingEntity user) {
        if (user.level().isClientSide()) return;
        if (isSlamming(user) || IceDashAbility.isDashing(user) || SwordGroundCutAbility.isCutting(user) || SwordSweepAbility.isSweeping(user)) return;

        LOCKED_POSITIONS.put(user.getUUID(), user.position());

        // Die Axt selbst bleibt starr - kein eigener GeckoLib-Trigger mehr nötig.
        // Body pose: clients that track the entity play the slam arm/leg pose (PlayerModelArmMixin).
        NetworkHandler.INSTANCE.send(PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> user),
                new SlamStartBroadcastPacket(user.getId()));

        PENDING.add(new PendingSlam(user, TOTAL_LOCK_TICKS));
    }

    /** True while the entity is still locked in place by its slam. */
    public static boolean isSlamming(LivingEntity entity) {
        return LOCKED_POSITIONS.containsKey(entity.getUUID());
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        tickPendingStuns();
        if (PENDING.isEmpty()) return;

        Iterator<PendingSlam> iterator = PENDING.iterator();
        while (iterator.hasNext()) {
            PendingSlam slam = iterator.next();
            int elapsedTicks = TOTAL_LOCK_TICKS - slam.ticksRemaining;

            if (!slam.player.isAlive() || slam.player.isRemoved()) {
                LOCKED_POSITIONS.remove(slam.player.getUUID());
                iterator.remove();
                continue;
            }
            if (elapsedTicks >= slam.unlockTicks) {
                LOCKED_POSITIONS.remove(slam.player.getUUID());
            }

            Vec3 lockPos = LOCKED_POSITIONS.get(slam.player.getUUID());
            if (lockPos != null) {
                slam.player.setPos(lockPos.x, lockPos.y, lockPos.z);
                slam.player.setDeltaMovement(Vec3.ZERO);
            }

            if (elapsedTicks < CHARGE_PARTICLE_END_TICKS && slam.player.level() instanceof ServerLevel level) {
                spawnChargeParticles(level, slam.player);
            }

            if (!slam.soundDone && elapsedTicks >= SOUND_START_TICKS) {
                slam.player.level().playSound(null, slam.player.blockPosition(),
                        net.icegiant.registry.ModSounds.NORDIC_AXE_SLAM_IMPACT.get(),
                        slam.player.getSoundSource(), (float) (1.5D * slam.scale), (float) (1.0D / Math.sqrt(slam.scale)));
                slam.soundDone = true;
            }

            if (!slam.knockbackDone && elapsedTicks >= IMPACT_TICKS) {
                doKnockback(slam.player, slam.scale);
                slam.knockbackDone = true;
            }

            if (!slam.craterStarted && elapsedTicks >= IMPACT_TICKS) {
                if (slam.player.level() instanceof ServerLevel level) {
                    slam.impactCenter = impactPositionInFront(slam.player, slam.scale);
                    int radius = (int) Math.min(MAX_GROUND_EFFECT_RADIUS, Math.round(GROUND_EFFECT_RADIUS * slam.scale));
                    slam.rings = collectRings(level, slam.impactCenter, radius);
                    spawnSmokeBurst(level, slam.impactCenter.getCenter());
                }
                slam.craterStarted = true;
            }

            if (slam.craterStarted && slam.rings != null && slam.nextRingToActivate < slam.rings.size()) {
                if (slam.ticksSinceCraterStart >= slam.nextRingToActivate * RING_DELAY_TICKS) {
                    if (slam.player.level() instanceof ServerLevel level) {
                        activateRing(level, slam.rings.get(slam.nextRingToActivate));
                    }
                    slam.nextRingToActivate++;
                }
                slam.ticksSinceCraterStart++;
            }

            slam.ticksRemaining--;
            if (slam.ticksRemaining <= 0) {
                LOCKED_POSITIONS.remove(slam.player.getUUID());
                iterator.remove();
            }
        }
    }

    /** Ein Block (mal Größe) vor dem Schlagenden (horizontale Blickrichtung), da die Axt dort auf den Boden trifft. */
    private static BlockPos impactPositionInFront(LivingEntity player, double scale) {
        BlockPos feet = player.blockPosition();
        net.minecraft.core.Direction facing = player.getDirection();
        return feet.relative(facing, (int) Math.max(1, Math.round(scale)));
    }

    /**
     * Impact: every living entity in range takes {@value #SLAM_DAMAGE} armor-piercing damage,
     * is thrown back, and gets stunned (no walking/jumping) for {@value #STUN_TICKS} ticks once it lands.
     * Mobs don't hit their own kind (an Ice Giant's slam spares other Ice Giants).
     */
    private static void doKnockback(LivingEntity player, double scale) {
        if (!(player.level() instanceof ServerLevel level)) return;
        AABB area = player.getBoundingBox().inflate(AOE_RADIUS * scale);
        net.minecraft.world.damagesource.DamageSource damage = slamDamageSource(level, player);
        for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, area, e -> e != player && e.isAlive())) {
            if (!(player instanceof Player) && target.getType() == player.getType()) continue;
            target.hurt(damage, SLAM_DAMAGE);
            // our knockback after hurt() so it overrides the small vanilla hit knockback
            KnockbackCompat.applyKnockback(player, target, KNOCKBACK_STRENGTH, KNOCKBACK_VERTICAL);
            AWAITING_STUN.put(target, 0);
        }
        shakeNearbyPlayers(level, player, scale);
    }

    /** Armor-piercing slam damage (icegiant:slam) caused by {@code attacker} - also used by IceDashAbility. */
    static net.minecraft.world.damagesource.DamageSource slamDamageSource(ServerLevel level, LivingEntity attacker) {
        return new net.minecraft.world.damagesource.DamageSource(
                level.registryAccess().registryOrThrow(net.minecraft.core.registries.Registries.DAMAGE_TYPE)
                        .getHolderOrThrow(SLAM_DAMAGE_TYPE), attacker);
    }

    /** Earthquake effect: every player near the impact (including the slammer) gets screen shake, stronger when closer. */
    public static void shakeNearbyPlayers(ServerLevel level, LivingEntity source, double scale) {
        double radius = EARTHQUAKE_RADIUS * scale;
        for (Player p : level.getEntitiesOfClass(Player.class, source.getBoundingBox().inflate(radius))) {
            double dist = p.distanceTo(source);
            if (dist > radius) continue;
            int strength = dist < radius * 0.35D ? 2 : dist < radius * 0.7D ? 1 : 0;
            p.addEffect(new net.minecraft.world.effect.MobEffectInstance(
                    net.icegiant.registry.ModEffects.EARTHQUAKE.get(), EARTHQUAKE_TICKS, strength, false, false, false));
        }
    }

    /** Stun a creature for {@value #STUN_TICKS} ticks once it lands again (slam; the sword's ground cut). */
    public static void stunWhenLanded(LivingEntity target) {
        AWAITING_STUN.put(target, 0);
    }

    private static void tickPendingStuns() {
        if (AWAITING_STUN.isEmpty()) return;
        Iterator<Map.Entry<LivingEntity, Integer>> it = AWAITING_STUN.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<LivingEntity, Integer> entry = it.next();
            LivingEntity target = entry.getKey();
            int ticks = entry.getValue() + 1;
            if (!target.isAlive() || target.isRemoved()) {
                it.remove();
                continue;
            }
            boolean landed = ticks >= STUN_MIN_AIR_TICKS && target.onGround();
            if (landed || ticks >= STUN_MAX_WAIT_TICKS) {
                target.addEffect(new net.minecraft.world.effect.MobEffectInstance(
                        net.icegiant.registry.ModEffects.STUN.get(), STUN_TICKS, 0, false, true, true));
                it.remove();
            } else {
                entry.setValue(ticks);
            }
        }
    }

    /**
     * Radialer Rauch-Stoß, angelehnt an das Funktionsprinzip aus Epic Fights
     * DustParticle-System (dort als eigene Technik analysiert, hier eigene
     * Umsetzung mit vanillas Lagerfeuer-Rauch statt eigenem Partikel):
     * viele einzelne Partikel fliegen GLEICHZEITIG in zufällige Richtungen
     * mit UNTERSCHIEDLICHER Geschwindigkeit los - schnellere fliegen weiter
     * (werden zur "Spur"), langsamere bleiben näher am Zentrum (werden zum
     * "Klumpen"). Kein künstliches Zeitstaffeln mehr nötig, die Schichtung
     * entsteht von selbst durch die Geschwindigkeits-Streuung.
     */
    public static void spawnSmokeBurst(ServerLevel level, Vec3 center) {
        int particleCount = 70;
        for (int i = 0; i < particleCount; i++) {
            double angle = level.random.nextDouble() * Math.PI * 2;
            // Geschwindigkeit stark gestreut: die meisten eher langsam (Klumpen
            // nah am Zentrum), einige wenige schnell (dünne, weiter reichende Spur)
            double speedRoll = level.random.nextDouble();
            double horizontalSpeed = 0.05 + Math.pow(speedRoll, 3) * 0.35;

            double dx = Math.cos(angle) * horizontalSpeed;
            double dz = Math.sin(angle) * horizontalSpeed;
            double dy = 0.01 + level.random.nextDouble() * 0.03; // bleibt erstmal flach, steigt kaum

            level.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE,
                    center.x, center.y + 0.15, center.z,
                    1, dx, dy, dz, 1.0);
        }
    }

    static void spawnChargeParticles(ServerLevel level, LivingEntity player) {   // (also the sword's ground cut)
        Vec3 target = player.position().add(0, player.getEyeHeight() * 0.7, 0)
                .add(player.getLookAngle().scale(0.3));

        for (int i = 0; i < CHARGE_PARTICLES_PER_TICK; i++) {
            double theta = level.random.nextDouble() * Math.PI * 2;
            double phi = Math.acos(2 * level.random.nextDouble() - 1);
            double spreadRadius = 1.2 + level.random.nextDouble() * 0.8;

            // Versatz vom Ziel weg (wo der Partikel sichtbar "herkommt")
            double offsetX = spreadRadius * Math.sin(phi) * Math.cos(theta);
            double offsetY = spreadRadius * Math.cos(phi);
            double offsetZ = spreadRadius * Math.sin(phi) * Math.sin(theta);

            // WICHTIG: Bei ENCHANT-Partikeln ist der erste Punkt das ZIEL
            // (wo sie ankommen), nicht der Startpunkt - der Versatz gibt an,
            // wie weit weg sie sichtbar herkommen. Umgekehrt zu vorher.
            level.sendParticles(ParticleTypes.ENCHANT, target.x, target.y, target.z,
                    1, offsetX, offsetY, offsetZ, 1.0);
        }
    }

    /**
     * Turns one ground block into a temporary crater block (tilted, restores itself after
     * {@value #CRATER_LIFETIME_TICKS} ticks). Same rules as the slam crater: no air, no
     * unbreakable blocks, no blocks with contents. Also used for the dash trail (IceDashAbility).
     */
    public static boolean fractureGround(ServerLevel level, BlockPos pos, float depthFactor, float dirX, float dirZ) {
        BlockState state = level.getBlockState(pos);
        if (state.isAir() || state.getBlock() instanceof net.icegiant.block.FractureBlock) return false;
        if (state.getDestroySpeed(level, pos) < 0 || level.getBlockEntity(pos) != null) return false;
        placeFracture(level, pos, state, depthFactor, dirX, dirZ);
        return true;
    }

    private static void placeFracture(ServerLevel level, BlockPos pos, BlockState original,
                                      float depthFactor, float dirX, float dirZ) {
        level.setBlock(pos, net.icegiant.registry.ModBlocks.FRACTURE_BLOCK.get().defaultBlockState(), 3);
        if (level.getBlockEntity(pos) instanceof net.icegiant.block.FractureBlockEntity fracture) {
            // Extra, zufällige Zusatz-Rotation obendrauf (additiv, siehe
            // FractureBlockEntity/-Renderer) - überschreibt NICHT die
            // normale, richtungsbasierte Kipp-Rotation.
            float extraA = (level.random.nextFloat() - 0.5f) * 2f; // -1..1
            float extraB = (level.random.nextFloat() - 0.5f) * 2f; // -1..1
            fracture.configure(original, depthFactor, dirX, dirZ, CRATER_LIFETIME_TICKS, extraA, extraB);
        }
        level.levelEvent(2001, pos, net.minecraft.world.level.block.Block.getId(original));
    }

    private static List<List<BlockInfo>> collectRings(ServerLevel level, BlockPos center, int radius) {
        Map<Integer, List<BlockInfo>> byRing = new HashMap<>();

        for (BlockPos pos : BlockPos.betweenClosed(
                center.offset(-radius, -1, -radius), center.offset(radius, 1, radius))) {

            double dx = pos.getX() - center.getX();
            double dz = pos.getZ() - center.getZ();
            double dist = Math.sqrt(dx * dx + dz * dz);
            if (dist > radius) continue;

            BlockPos immutablePos = pos.immutable();
            BlockState state = level.getBlockState(immutablePos);
            if (state.isAir()) continue;
            if (state.getBlock() instanceof net.icegiant.block.FractureBlock) continue;
            if (state.getDestroySpeed(level, immutablePos) < 0) continue;
            BlockEntity blockEntity = level.getBlockEntity(immutablePos);
            if (blockEntity != null) continue;

            float depthFactor = (float) (1.0 - dist / radius);
            float dirX = dist > 0.001 ? (float) (dx / dist) : 0f;
            float dirZ = dist > 0.001 ? (float) (dz / dist) : 0f;

            int ring = (int) Math.round(dist);
            byRing.computeIfAbsent(ring, k -> new ArrayList<>())
                    .add(new BlockInfo(immutablePos, state, depthFactor, dirX, dirZ));
        }

        List<List<BlockInfo>> ordered = new ArrayList<>();
        int maxRing = byRing.keySet().stream().mapToInt(Integer::intValue).max().orElse(-1);
        for (int i = 0; i <= maxRing; i++) {
            List<BlockInfo> ring = byRing.get(i);
            if (ring != null) ordered.add(ring);
        }
        return ordered;
    }

    private static void activateRing(ServerLevel level, List<BlockInfo> ring) {
        for (BlockInfo info : ring) {
            placeFracture(level, info.pos(), info.state(), info.depthFactor(), info.dirX(), info.dirZ());
        }
        if (!ring.isEmpty()) {
            level.playSound(null, ring.get(0).pos(), net.minecraft.sounds.SoundEvents.ANVIL_LAND,
                    net.minecraft.sounds.SoundSource.BLOCKS, // cracking ground -> "Blocks" slider
                    0.6f, 0.6f + level.random.nextFloat() * 0.2f);
        }
    }
}
