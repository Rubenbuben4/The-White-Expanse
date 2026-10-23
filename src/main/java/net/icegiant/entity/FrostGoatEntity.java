package net.icegiant.entity;

import net.icegiant.item.HorseshoeItem;
import net.icegiant.registry.ModEntityTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Container;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.horse.AbstractChestedHorse;
import net.minecraft.world.entity.animal.horse.AbstractHorse;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Frost Goat (Rubenbuben4, 2026-10-23): the goats of the Frozen Realm, twice the size of a vanilla goat (its model,
 * drawn 2x - FrostGoatRenderer) and ridden like a horse. Everything a donkey has comes from vanilla's
 * AbstractChestedHorse: taming by riding it until it accepts you, the horse inventory screen, a saddle to steer it and
 * jump (hold space), a chest for 15 slots, breeding. On top:
 * <ul>
 *   <li>it climbs 2 blocks without jumping and jumps about 3.5 blocks high;</li>
 *   <li>the armour slot of the horse screen takes a horseshoe (HorseshoeItem): faster, and the horseshoe is worn on
 *       its feet, so Soul Speed and Frost Walker on it work like on boots (vanilla does that for the feet slot);</li>
 *   <li>the horseshoe also makes it jump higher;</li>
 *   <li>dash (the dash key, J by default, while riding it with a saddle): a short charge, faster than its gallop,
 *       that shoves mobs in its way aside and hurts them a little (tryDash);</li>
 *   <li>while ridden it walks through leaves (LeavesPassMixin).</li>
 * </ul>
 * Goats that would spawn in the Frozen Realm become Frost Goats (FrozenRealmGoats).
 */
public class FrostGoatEntity extends AbstractChestedHorse {

    public static final float SCALE = 2.0F;
    // ---- TUNE ----
    static final float STEP_UP = 2.0F;                 // blocks it walks up without jumping
    static final double JUMP = 0.8D;                   // horse jump strength: ~3.6 blocks (0.75 = 3.2, 0.9 = 4.4)
    static final double SPEED = 0.24D;                 // a little slower than an average horse (0.225 - 0.3)
    static final double HEALTH = 30.0D;
    private static final UUID HORSESHOE_SPEED = UUID.fromString("6b3f1a52-9c1e-4d8e-a5a0-2f6d3c8b91e7");
    private static final UUID HORSESHOE_JUMP = UUID.fromString("c4e27d90-3b5a-4f61-8d2e-7a9b0c1f5e43");
    // ---- TUNE: dash ----
    static final int DASH_TICKS = 14;                  // how long the charge lasts (0.7 s)
    static final int DASH_COOLDOWN = 60;               // ticks from one dash to the next (3 s, counted from the start)
    static final float DASH_SPEED = 1.6F;              // x its riding speed (horseshoe included) while dashing
    static final float DASH_DAMAGE = 3.0F;             // 1.5 hearts - "leichter Schaden"
    static final double DASH_PUSH = 0.9D;              // sideways shove (vanilla knockback strength)
    /** Game time at which the current dash ends - synced, so the riding client knows to speed up. */
    private static final EntityDataAccessor<Long> DATA_DASH_END =
            SynchedEntityData.defineId(FrostGoatEntity.class, EntityDataSerializers.LONG);
    private long nextDashAt;                            // server only
    private final Set<Integer> dashHit = new HashSet<>();  // mobs already hit by this dash (server)

    public FrostGoatEntity(EntityType<? extends FrostGoatEntity> type, Level level) {
        super(type, level);
        this.setMaxUpStep(STEP_UP);
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(DATA_DASH_END, 0L);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return createBaseChestedHorseAttributes()
                .add(Attributes.MAX_HEALTH, HEALTH)
                .add(Attributes.MOVEMENT_SPEED, SPEED)
                .add(Attributes.JUMP_STRENGTH, JUMP);
    }

    @Override
    protected void randomizeAttributes(RandomSource random) {
        this.getAttribute(Attributes.MAX_HEALTH).setBaseValue(HEALTH + random.nextInt(5));
        this.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(SPEED + random.nextDouble() * 0.03D);
        this.getAttribute(Attributes.JUMP_STRENGTH).setBaseValue(JUMP + random.nextDouble() * 0.08D);
    }

    // ---- horseshoe (the horse screen's armour slot, inventory slot 1) ----

    @Override
    public boolean canWearArmor() {
        return true;
    }

    @Override
    public boolean isArmor(ItemStack stack) {
        return stack.getItem() instanceof HorseshoeItem;
    }

    @Override
    public boolean isWearingArmor() {
        return !this.getItemBySlot(EquipmentSlot.FEET).isEmpty();
    }

    public ItemStack getHorseshoe() {
        return this.inventory.getItem(1);
    }

    @Override
    protected void updateContainerEquipment() {
        super.updateContainerEquipment();
        if (this.level().isClientSide) return;
        ItemStack shoe = this.getHorseshoe();
        this.setItemSlot(EquipmentSlot.FEET, shoe);           // worn on the feet: Soul Speed / Frost Walker work
        this.setDropChance(EquipmentSlot.FEET, 0.0F);         // (dropped with the inventory instead)
        AttributeInstance speed = this.getAttribute(Attributes.MOVEMENT_SPEED);
        AttributeInstance jump = this.getAttribute(Attributes.JUMP_STRENGTH);
        if (speed != null) speed.removeModifier(HORSESHOE_SPEED);
        if (jump != null) jump.removeModifier(HORSESHOE_JUMP);
        if (shoe.getItem() instanceof HorseshoeItem h) {    // both attributes are synced to the riding client
            if (speed != null) speed.addTransientModifier(new AttributeModifier(HORSESHOE_SPEED, "Horseshoe speed",
                    h.speedBonus(), AttributeModifier.Operation.MULTIPLY_BASE));
            if (jump != null) jump.addTransientModifier(new AttributeModifier(HORSESHOE_JUMP, "Horseshoe jump",
                    h.jumpBonus(), AttributeModifier.Operation.ADDITION));
        }
    }

    // ---- dash ----

    public boolean isDashing() {
        return this.level().getGameTime() < this.entityData.get(DATA_DASH_END);
    }

    /** Server: the rider pressed the dash key (DashActivatePacket). Needs a saddle and the rider steering it. */
    public void tryDash(Player rider) {
        if (this.level().isClientSide || !this.isSaddled() || this.getControllingPassenger() != rider) return;
        long now = this.level().getGameTime();
        if (now < this.nextDashAt) return;
        this.nextDashAt = now + DASH_COOLDOWN;
        this.entityData.set(DATA_DASH_END, now + DASH_TICKS);
        this.dashHit.clear();
        this.playSound(SoundEvents.GOAT_PREPARE_RAM, 1.0F, 0.8F);
    }

    /** While dashing it charges straight ahead, even without W (the riding client moves it). */
    @Override
    protected Vec3 getRiddenInput(Player rider, Vec3 input) {
        if (this.isDashing()) return new Vec3(0.0D, 0.0D, 1.0D);
        return super.getRiddenInput(rider, input);
    }

    @Override
    protected float getRiddenSpeed(Player rider) {
        float speed = super.getRiddenSpeed(rider);
        return this.isDashing() ? speed * DASH_SPEED : speed;
    }

    @Override
    public void tick() {
        super.tick();
        if (!this.level().isClientSide && this.isDashing()) this.dashTick();
    }

    /** Server, every dash tick: shove what is in front of it aside and hurt it a little - once per mob and dash. */
    private void dashTick() {
        float yaw = this.getYRot() * ((float) Math.PI / 180F);
        Vec3 fwd = new Vec3(-Math.sin(yaw), 0.0D, Math.cos(yaw));
        LivingEntity rider = this.getControllingPassenger();
        AABB box = this.getBoundingBox().inflate(0.4D, 0.0D, 0.4D).expandTowards(fwd.scale(1.2D));
        for (LivingEntity e : this.level().getEntitiesOfClass(LivingEntity.class, box, EntitySelector.NO_SPECTATORS)) {
            if (e == this || this.hasPassenger(e) || e instanceof ArmorStand || this.dashHit.contains(e.getId())) continue;
            if (rider != null && (e instanceof TamableAnimal pet && pet.isOwnedBy(rider)
                    || e instanceof AbstractHorse h && rider.getUUID().equals(h.getOwnerUUID()))) continue;
            this.dashHit.add(e.getId());
            // to the side it already stands on (left or right of the goat's path), a bit forward
            Vec3 rel = e.position().subtract(this.position());
            double side = fwd.x * rel.z - fwd.z * rel.x >= 0.0D ? 1.0D : -1.0D;
            Vec3 push = new Vec3(-fwd.z * side, 0.0D, fwd.x * side).add(fwd.scale(0.4D)).normalize();
            boolean hurt = true;
            if (!(e instanceof AbstractVillager)) {           // villagers are only shoved, not hurt
                hurt = e.hurt(rider instanceof Player p ? this.damageSources().playerAttack(p)
                        : this.damageSources().mobAttack(this), DASH_DAMAGE);
            }
            if (!hurt && e instanceof Player) continue;       // no PvP: leave players alone
            e.knockback(DASH_PUSH, -push.x, -push.z);         // knockback pushes away from (x, z)
            e.hurtMarked = true;
            this.playSound(SoundEvents.GOAT_RAM_IMPACT, 1.0F, 1.0F);
        }
        if (this.level() instanceof ServerLevel sl) {
            sl.sendParticles(ParticleTypes.SNOWFLAKE, this.getX(), this.getY() + 0.3D, this.getZ(),
                    4, this.getBbWidth() * 0.4D, 0.2D, this.getBbWidth() * 0.4D, 0.02D);
        }
    }

    @Override
    public void containerChanged(Container container) {
        ItemStack before = this.getItemBySlot(EquipmentSlot.FEET).copy();
        super.containerChanged(container);
        ItemStack after = this.getHorseshoe();
        if (this.tickCount > 20 && !after.isEmpty() && !ItemStack.isSameItem(before, after)) {
            this.playSound(SoundEvents.ARMOR_EQUIP_IRON, 0.6F, 1.2F);
        }
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        if (!this.getHorseshoe().isEmpty()) tag.put("HorseshoeItem", this.getHorseshoe().save(new CompoundTag()));
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.contains("HorseshoeItem", 10)) {
            ItemStack shoe = ItemStack.of(tag.getCompound("HorseshoeItem"));
            if (this.isArmor(shoe)) this.inventory.setItem(1, shoe);
        }
        this.updateContainerEquipment();
    }

    // ---- size, food, breeding ----

    @Override
    public double getPassengersRidingOffset() {
        return this.getBbHeight() * 0.68D;                    // on its back (the model is 2x a goat)
    }

    @Override
    public boolean isFood(ItemStack stack) {
        return stack.is(Items.WHEAT) || stack.is(Items.HAY_BLOCK) || stack.is(Items.GOLDEN_CARROT)
                || stack.is(Items.GOLDEN_APPLE) || stack.is(Items.ENCHANTED_GOLDEN_APPLE);
    }

    @Override
    public AgeableMob getBreedOffspring(ServerLevel level, AgeableMob partner) {
        FrostGoatEntity child = ModEntityTypes.FROST_GOAT.get().create(level);
        if (child != null) this.setOffspringAttributes(partner, child);
        return child;
    }

    @Override
    public boolean canMate(net.minecraft.world.entity.animal.Animal other) {
        return other != this && other instanceof FrostGoatEntity && this.canParent() && ((FrostGoatEntity) other).canParent();
    }

    // ---- goat sounds (no horse neighing and galloping) ----

    @Override
    protected SoundEvent getAmbientSound() {
        return SoundEvents.GOAT_AMBIENT;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.GOAT_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.GOAT_DEATH;
    }

    @Override
    protected SoundEvent getEatingSound() {
        return SoundEvents.GOAT_EAT;
    }

    @Override
    protected SoundEvent getAngrySound() {
        return SoundEvents.GOAT_SCREAMING_AMBIENT;
    }

    @Override
    protected void playStepSound(BlockPos pos, BlockState state) {
        this.playSound(SoundEvents.GOAT_STEP, 0.3F, 0.8F);
    }

    @Override
    protected void playJumpSound() {
        this.playSound(SoundEvents.GOAT_LONG_JUMP, 0.5F, 0.9F);
    }

    @Override
    protected void playChestEquipsSound() {
        this.playSound(SoundEvents.DONKEY_CHEST, 1.0F, (this.random.nextFloat() - this.random.nextFloat()) * 0.2F + 1.0F);
    }
}
