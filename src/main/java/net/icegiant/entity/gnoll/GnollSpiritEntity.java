package net.icegiant.entity.gnoll;

import net.icegiant.registry.ModEntityTypes;
import net.icegiant.registry.ModSounds;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

import javax.annotation.Nullable;

/**
 * A shaman's spirit: a small homing wisp drawn only with particles. Green spirits heal the ally
 * they reach, red ones hurt the enemy. Spirits pass through blocks and fade after 6 s.
 * Lives only on the server side for its logic; it is never saved with the chunk.
 */
public class GnollSpiritEntity extends Entity {

    private static final DustParticleOptions HEAL_DUST = new DustParticleOptions(new Vector3f(0.56F, 0.85F, 0.26F), 1.0F);
    private static final DustParticleOptions HEAL_TRAIL = new DustParticleOptions(new Vector3f(0.36F, 0.40F, 0.22F), 0.8F);
    private static final DustParticleOptions HARM_DUST = new DustParticleOptions(new Vector3f(0.44F, 0.08F, 0.08F), 1.0F);
    private static final DustParticleOptions HARM_TRAIL = new DustParticleOptions(new Vector3f(0.34F, 0.18F, 0.18F), 0.8F);
    private static final int LIFETIME = 120;

    @Nullable private LivingEntity owner;
    @Nullable private LivingEntity target;
    private boolean heal;
    private float power;

    public GnollSpiritEntity(EntityType<?> type, Level level) {
        super(type, level);
        this.noPhysics = true;
    }

    public static void launch(ServerLevel level, LivingEntity owner, LivingEntity target, Vec3 from, boolean heal,
                              float power) {
        GnollSpiritEntity spirit = ModEntityTypes.GNOLL_SPIRIT.get().create(level);
        if (spirit == null) return;
        spirit.owner = owner;
        spirit.target = target;
        spirit.heal = heal;
        spirit.power = power;
        spirit.moveTo(from.x, from.y, from.z, 0.0F, 0.0F);
        // burst out upwards/sideways first, then curve to the target
        spirit.setDeltaMovement((level.random.nextDouble() - 0.5D) * 0.5D, 0.25D + level.random.nextDouble() * 0.2D,
                (level.random.nextDouble() - 0.5D) * 0.5D);
        level.addFreshEntity(spirit);
    }

    @Override
    public void tick() {
        super.tick();
        if (!(this.level() instanceof ServerLevel server)) return;
        if (this.target == null || !this.target.isAlive() || this.tickCount > LIFETIME) {
            this.discard();
            return;
        }
        Vec3 aim = this.target.position().add(0.0D, this.target.getBbHeight() * 0.6D, 0.0D);
        Vec3 to = aim.subtract(this.position());
        if (to.lengthSqr() < 0.6D * 0.6D) {
            this.arrive(server);
            return;
        }
        Vec3 vel = this.getDeltaMovement().scale(0.85D).add(to.normalize().scale(0.13D));
        if (vel.length() > 0.55D) vel = vel.normalize().scale(0.55D);
        this.setDeltaMovement(vel);
        this.setPos(this.getX() + vel.x, this.getY() + vel.y, this.getZ() + vel.z);

        server.sendParticles(this.heal ? HEAL_DUST : HARM_DUST, this.getX(), this.getY(), this.getZ(),
                4, 0.08D, 0.08D, 0.08D, 0.0D);
        server.sendParticles(this.heal ? ParticleTypes.END_ROD : ParticleTypes.FLAME, this.getX(), this.getY(),
                this.getZ(), 1, 0.0D, 0.0D, 0.0D, 0.0D);
        if (this.tickCount % 2 == 0) {
            server.sendParticles(this.heal ? HEAL_TRAIL : HARM_TRAIL, this.getX(), this.getY(), this.getZ(),
                    2, 0.16D, 0.16D, 0.16D, 0.0D);
        }
    }

    private void arrive(ServerLevel server) {
        LivingEntity victim = this.target;
        if (victim == null) return;
        if (this.heal) {
            victim.heal(this.power);
            this.playSound(ModSounds.GNOLL_SHAMAN_SPIRITS_HEAL.get(), 0.9F, 1.0F);
            server.sendParticles(ParticleTypes.HAPPY_VILLAGER, victim.getX(), victim.getY() + 0.6D, victim.getZ(),
                    6, 0.4D, 0.6D, 0.4D, 0.0D);
        } else {
            victim.hurt(this.damageSources().indirectMagic(this, this.owner), this.power);
            this.playSound(ModSounds.GNOLL_SHAMAN_SPIRITS_HIT.get(), 0.9F, 1.0F);
            server.sendParticles(ParticleTypes.SMOKE, victim.getX(), victim.getY() + 0.6D, victim.getZ(),
                    6, 0.4D, 0.6D, 0.4D, 0.0D);
        }
        this.discard();
    }

    @Override
    public boolean shouldBeSaved() {
        return false;
    }

    @Override
    public boolean isPickable() {
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
}
