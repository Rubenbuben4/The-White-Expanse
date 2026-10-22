package net.icegiant.entity;

import net.icegiant.IceGiantMod;
import net.icegiant.item.GiantRuneSword2Item;
import net.icegiant.item.SwordSweepAbility;
import net.icegiant.network.BlueFirePacket;
import net.icegiant.network.NetworkHandler;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;

/**
 * Blue flames of the Giant Rune Sword 2.0 (Rubenbuben4, 2026-10-21) - in place of its hidden Fire Aspect IV.
 * A normal hit with the sword (a player's left click, a giant's or other mob's melee hit; not the K and N
 * abilities) sets the target burning in blue for 16 s, 2 damage a second like soul fire (BlueFireRules).
 * <p>
 * It is not vanilla fire: water, rain and powder snow do not put it out, nothing does - it burns down.
 * The damage is fire damage (damage type {@code icegiant:blue_fire}, tags is_fire + bypasses_armor like
 * vanilla burning), so Fire Resistance protects and Fire Protection lessens it; fire-proof mobs (blaze,
 * strider ...) do not catch it at all. Stored in the entity's persistent data (so it survives saving), the
 * clients get the time left for the flames (BlueFirePacket, client/BlueFireClient draws them).
 */
@Mod.EventBusSubscriber(modid = IceGiantMod.MOD_ID)
public final class BlueFire {

    public static final ResourceKey<DamageType> DAMAGE_TYPE =
            ResourceKey.create(Registries.DAMAGE_TYPE, new ResourceLocation(IceGiantMod.MOD_ID, "blue_fire"));

    private static final String TICKS = "icegiant_blue_fire", BURNED = "icegiant_blue_fire_burned";

    private BlueFire() {
    }

    /** A normal melee hit with the sword 2.0 that really did damage: the target catches blue fire. */
    @SubscribeEvent
    public static void onDamage(LivingDamageEvent event) {
        LivingEntity target = event.getEntity();
        if (target.level().isClientSide()) return;
        DamageSource source = event.getSource();
        if (!(source.getEntity() instanceof LivingEntity attacker) || source.getDirectEntity() != attacker) return;
        if (!source.is(DamageTypes.PLAYER_ATTACK) && !source.is(DamageTypes.MOB_ATTACK)) return;
        if (SwordSweepAbility.isHitting()) return;                               // the wide sweep (N) uses these too
        if (!(attacker.getMainHandItem().getItem() instanceof GiantRuneSword2Item)) return;
        ignite(target, BlueFireRules.DURATION_TICKS);
    }

    /** Sets the target burning for at least this many ticks (never shortens it). */
    public static void ignite(LivingEntity target, int ticks) {
        if (target.fireImmune() || !target.isAlive()) return;
        CompoundTag data = target.getPersistentData();
        int before = data.getInt(TICKS);
        int now = BlueFireRules.ignite(before, ticks);
        if (before <= 0) data.putInt(BURNED, 0);
        data.putInt(TICKS, now);
        if (now != before) sync(target, now);
    }

    public static boolean isBurning(LivingEntity entity) {
        return entity.getPersistentData().getInt(TICKS) > 0;
    }

    @SubscribeEvent
    public static void onTick(LivingEvent.LivingTickEvent event) {
        LivingEntity entity = event.getEntity();
        if (entity.level().isClientSide()) return;
        CompoundTag data = entity.getPersistentData();
        if (!data.contains(TICKS)) return;
        int remaining = data.getInt(TICKS);
        if (remaining <= 0 || !entity.isAlive()) {
            clear(entity);
            return;
        }
        int burned = data.getInt(BURNED);
        if (BlueFireRules.damageNow(burned)) entity.hurt(damageSource(entity), BlueFireRules.DAMAGE);
        remaining = BlueFireRules.next(remaining);
        if (remaining <= 0 || !entity.isAlive()) {
            clear(entity);
            return;
        }
        data.putInt(TICKS, remaining);
        data.putInt(BURNED, burned + 1);
    }

    private static void clear(LivingEntity entity) {
        CompoundTag data = entity.getPersistentData();
        data.remove(TICKS);
        data.remove(BURNED);
        sync(entity, 0);
    }

    private static DamageSource damageSource(LivingEntity entity) {
        return new DamageSource(entity.level().registryAccess().registryOrThrow(Registries.DAMAGE_TYPE)
                .getHolderOrThrow(DAMAGE_TYPE));
    }

    /** The flames for everyone who sees the entity (and the entity itself, if it is a player). */
    private static void sync(LivingEntity entity, int ticks) {
        NetworkHandler.INSTANCE.send(PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> entity),
                new BlueFirePacket(entity.getId(), ticks));
    }

    /** A player starts seeing a burning entity (comes near, joins): he gets its flames too. */
    @SubscribeEvent
    public static void onStartTracking(PlayerEvent.StartTracking event) {
        if (!(event.getTarget() instanceof LivingEntity target) || !(event.getEntity() instanceof ServerPlayer player)) return;
        int ticks = target.getPersistentData().getInt(TICKS);
        if (ticks > 0) NetworkHandler.INSTANCE.send(PacketDistributor.PLAYER.with(() -> player), new BlueFirePacket(target.getId(), ticks));
    }

    /** A burning player logs in or changes dimension: his own flames (new entity id on his client). */
    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        resendOwn(event.getEntity());
    }

    @SubscribeEvent
    public static void onChangeDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        resendOwn(event.getEntity());
    }

    private static void resendOwn(net.minecraft.world.entity.player.Player p) {
        if (!(p instanceof ServerPlayer player)) return;
        int ticks = player.getPersistentData().getInt(TICKS);
        if (ticks > 0) NetworkHandler.INSTANCE.send(PacketDistributor.PLAYER.with(() -> player), new BlueFirePacket(player.getId(), ticks));
    }
}
