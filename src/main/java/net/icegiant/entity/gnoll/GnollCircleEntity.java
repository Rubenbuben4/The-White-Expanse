package net.icegiant.entity.gnoll;

import net.icegiant.registry.ModSounds;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import org.joml.Vector3f;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

import javax.annotation.Nullable;

/**
 * The shaman's cursed circle (model "gnoll_shaman_circle" from the Gnolls pack): it grows on
 * the ground for 2.5 s, then bursts - every enemy within 5 blocks gets Slowness II (10 s) and
 * Nausea (6 s). Gnolls and Ice Giants are not affected.
 */
public class GnollCircleEntity extends Entity implements GeoEntity {

    private static final RawAnimation EXPLODE = RawAnimation.begin().thenPlayAndHold("explode");
    private static final DustParticleOptions CURSE = new DustParticleOptions(new Vector3f(0.33F, 0.15F, 0.37F), 1.5F);
    private static final int BURST_TICK = 50;
    private static final double RADIUS = 5.0D;

    private final AnimatableInstanceCache geoCache = GeckoLibUtil.createInstanceCache(this);
    @Nullable private LivingEntity owner;

    public GnollCircleEntity(EntityType<?> type, Level level) {
        super(type, level);
    }

    public void setOwner(@Nullable LivingEntity owner) {
        this.owner = owner;
    }

    @Override
    public void tick() {
        super.tick();
        if (!(this.level() instanceof ServerLevel server)) return;
        if (this.tickCount == BURST_TICK) {
            this.playSound(ModSounds.GNOLL_SHAMAN_CIRCLE_APPLY.get(), 1.0F, 1.0F);
            for (LivingEntity e : server.getEntitiesOfClass(LivingEntity.class, this.getBoundingBox().inflate(RADIUS, 2.0D, RADIUS),
                    e -> e.isAlive() && !GnollEntity.isFriend(e) && e.distanceToSqr(this) <= RADIUS * RADIUS
                            && (e instanceof Player || (this.owner instanceof GnollEntity k && k.getTarget() == e)))) {
                if (e instanceof Player p && (p.isCreative() || p.isSpectator())) continue;
                e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 200, 1), this.owner);
                e.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 120, 0), this.owner);
            }
            server.sendParticles(CURSE, this.getX(), this.getY() + 0.3D, this.getZ(), 100, 3.0D, 0.4D, 3.0D, 0.0D);
            for (int i = 0; i < 60; i++) {
                double a = i * Math.PI * 2.0D / 60.0D;
                server.sendParticles(CURSE, this.getX() + Math.cos(a) * RADIUS, this.getY() + 0.4D,
                        this.getZ() + Math.sin(a) * RADIUS, 1, 0.0D, 0.1D, 0.0D, 0.0D);
            }
        }
        if (this.tickCount > BURST_TICK + 2) this.discard();
    }

    @Override
    public boolean shouldBeSaved() {
        return false;
    }

    @Override
    protected void defineSynchedData() {
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
    }

    @Override
    public Packet<ClientGamePacketListener> getAddEntityPacket() {
        return new ClientboundAddEntityPacket(this);
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "main", 0, state -> state.setAndContinue(EXPLODE)));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return this.geoCache;
    }
}
