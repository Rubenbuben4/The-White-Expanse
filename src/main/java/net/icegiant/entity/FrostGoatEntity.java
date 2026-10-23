package net.icegiant.entity;

import net.icegiant.item.HorseshoeItem;
import net.icegiant.registry.ModEntityTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Container;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.horse.AbstractChestedHorse;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

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

    public FrostGoatEntity(EntityType<? extends FrostGoatEntity> type, Level level) {
        super(type, level);
        this.setMaxUpStep(STEP_UP);
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
        if (speed == null) return;
        speed.removeModifier(HORSESHOE_SPEED);
        if (shoe.getItem() instanceof HorseshoeItem h) {
            speed.addTransientModifier(new AttributeModifier(HORSESHOE_SPEED, "Horseshoe speed", h.speedBonus(),
                    AttributeModifier.Operation.MULTIPLY_BASE));
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
