package net.icegiant.slime;

import net.icegiant.IceGiantMod;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.MagmaCube;
import net.minecraft.world.entity.monster.Slime;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;

import javax.annotation.Nullable;
import java.util.UUID;

/**
 * What a slime has eaten and what it does to him (Rubenbuben4, 2026-10-27: "die Slimes cooler machen").
 * <ul>
 *   <li><b>Fullness</b>: food points up to {@value #POINTS_PER_SIZE} x size; below half he is hungry (then he also
 *       grazes plants and, from size 2, hunts monsters and villagers). One point fades every
 *       {@value #HUNGER_TICKS} ticks.</li>
 *   <li><b>Growing</b>: full -> next size (1 -> 2 -> 4). At size 4, his natural size, he splits into two size-2 slimes
 *       instead - unless there are already {@value #CROWD} slimes around (then he just stays full).</li>
 *   <li><b>Diet</b> (item tags {@code icegiant:slime_food/meat|ore|plant|fire}): meat makes him hit harder, raw ore
 *       gives armour, plants more health; the outer jelly tints towards what he eats. A normal slime that eats
 *       {@value #FIRE_TO_MAGMA} fiery things (magma cream, blaze rods, magma blocks) turns into a magma cube.
 *       Magma cubes eat meat, ore and fire, no plants.</li>
 *   <li><b>Size</b>: health and damage grow more with size than in vanilla (+50 % health, +0.25 damage per size).</li>
 * </ul>
 * Stored in the slime's persistent data ({@code icegiant_slime}).
 */
public final class SlimeFood {

    public enum Kind { MEAT, ORE, PLANT, FIRE }

    public static final TagKey<Item> MEAT = item("slime_food/meat");
    public static final TagKey<Item> ORE = item("slime_food/ore");
    public static final TagKey<Item> PLANT = item("slime_food/plant");
    public static final TagKey<Item> FIRE = item("slime_food/fire");
    /** Plants a hungry slime grazes off the ground (grass blocks under them turn to dirt, like with sheep). */
    public static final TagKey<Block> GRAZE = TagKey.create(Registries.BLOCK, new ResourceLocation(IceGiantMod.MOD_ID, "slime_grazeable"));

    static final String TAG = "icegiant_slime";
    public static final int POINTS_PER_SIZE = 8, MAX_NATURAL_SIZE = 4, FIRE_TO_MAGMA = 3;
    static final int HUNGER_TICKS = 1800;                     // one food point fades every 90 s
    static final int CROWD = 8;                               // no splitting with this many slimes within CROWD_RANGE
    static final double CROWD_RANGE = 24.0D;

    private static final UUID HEALTH_ID = UUID.fromString("6f1c8a2e-3b5d-4e7a-9c11-2d4f6a8b0c01");
    private static final UUID ATTACK_ID = UUID.fromString("6f1c8a2e-3b5d-4e7a-9c11-2d4f6a8b0c02");
    private static final UUID ARMOR_ID = UUID.fromString("6f1c8a2e-3b5d-4e7a-9c11-2d4f6a8b0c03");

    private SlimeFood() {
    }

    private static TagKey<Item> item(String path) {
        return TagKey.create(Registries.ITEM, new ResourceLocation(IceGiantMod.MOD_ID, path));
    }

    // ------------------------------------------------------------------ data

    /** The slime's food data, created (a bit hungry) when missing. */
    static CompoundTag data(Slime s) {
        CompoundTag p = s.getPersistentData();
        if (!p.contains(TAG)) {
            CompoundTag t = new CompoundTag();
            t.putFloat("Food", capacity(s) * (0.3F + 0.3F * s.getRandom().nextFloat()));
            p.put(TAG, t);
        }
        return p.getCompound(TAG);
    }

    @Nullable
    private static CompoundTag peek(Slime s) {
        CompoundTag p = s.getPersistentData();
        return p.contains(TAG) ? p.getCompound(TAG) : null;
    }

    public static int capacity(Slime s) {
        return POINTS_PER_SIZE * Math.max(1, s.getSize());
    }

    public static float food(Slime s) {
        return data(s).getFloat("Food");
    }

    public static boolean isHungry(Slime s) {
        return food(s) < capacity(s) * 0.5F;
    }

    public static boolean isFull(Slime s) {
        return food(s) >= capacity(s);
    }

    static int count(CompoundTag d, Kind k) {
        return d.getInt(k.name());
    }

    /** What kind of food this is for this slime, or null if he doesn't eat it. */
    @Nullable
    public static Kind kindOf(Slime s, ItemStack stack) {
        if (stack.isEmpty()) return null;
        if (stack.is(FIRE)) return Kind.FIRE;
        if (stack.is(MEAT)) return Kind.MEAT;
        if (stack.is(ORE)) return Kind.ORE;
        if (stack.is(PLANT) && !(s instanceof MagmaCube)) return Kind.PLANT;
        return null;
    }

    static float points(Kind k) {
        return switch (k) {
            case MEAT, FIRE -> 3.0F;
            case ORE -> 2.0F;
            case PLANT -> 1.0F;
        };
    }

    /** Hunger: called once a second (SlimeLife). */
    static void tickHunger(Slime s) {
        CompoundTag d = data(s);
        d.putFloat("Food", Math.max(0.0F, d.getFloat("Food") - 20.0F / HUNGER_TICKS));
    }

    // ------------------------------------------------------------------ eating

    /** He finished one item / one bite of this kind. */
    static void eat(Slime s, Kind k, @Nullable ItemStack shown) {
        if (!(s.level() instanceof ServerLevel level)) return;
        CompoundTag d = data(s);
        d.putFloat("Food", d.getFloat("Food") + points(k));
        d.putInt(k.name(), count(d, k) + 1);
        if (shown != null && !shown.isEmpty()) {
            level.sendParticles(new ItemParticleOption(ParticleTypes.ITEM, shown), s.getX(), s.getY() + s.getBbHeight() * 0.4D,
                    s.getZ(), 6, s.getBbWidth() * 0.25D, 0.1D, s.getBbWidth() * 0.25D, 0.05D);
        }
        s.playSound(SoundEvents.GENERIC_EAT, 0.5F, 0.5F + s.getRandom().nextFloat() * 0.2F);
        if (k == Kind.FIRE && !(s instanceof MagmaCube) && count(d, Kind.FIRE) >= FIRE_TO_MAGMA) {
            toMagma(s, level);
            return;
        }
        afterMeal(s, level);
    }

    /** He ate a whole body: completely full, sure to grow (or split). */
    static void eatCorpse(Slime s) {
        if (!(s.level() instanceof ServerLevel level)) return;
        CompoundTag d = data(s);
        d.putFloat("Food", capacity(s));
        d.putInt(Kind.MEAT.name(), count(d, Kind.MEAT) + 2);
        afterMeal(s, level);
    }

    private static void afterMeal(Slime s, ServerLevel level) {
        CompoundTag d = data(s);
        if (d.getFloat("Food") >= capacity(s)) {
            int size = s.getSize();
            if (size < MAX_NATURAL_SIZE) {
                grow(s, level, size <= 1 ? 2 : MAX_NATURAL_SIZE);
                return;
            }
            if (level.getEntitiesOfClass(Slime.class, s.getBoundingBox().inflate(CROWD_RANGE)).size() < CROWD) {
                split(s, level);
                return;
            }
            d.putFloat("Food", capacity(s));                       // too crowded: stays full and stops eating
        }
        applyStats(s, false);
        SlimeLife.syncTint(s);
    }

    private static void grow(Slime s, ServerLevel level, int newSize) {
        s.setSize(newSize, true);                                  // (applyStats runs from the setSize mixin)
        data(s).putFloat("Food", capacity(s) * 0.35F);
        level.sendParticles(ParticleTypes.ITEM_SLIME, s.getX(), s.getY() + s.getBbHeight() * 0.5D, s.getZ(),
                12 * newSize, s.getBbWidth() * 0.4D, s.getBbHeight() * 0.3D, s.getBbWidth() * 0.4D, 0.1D);
        s.playSound(s instanceof MagmaCube ? SoundEvents.MAGMA_CUBE_SQUISH : SoundEvents.SLIME_SQUISH, 1.0F, 0.6F);
        SlimeLife.syncTint(s);
    }

    /** At his natural size and full: splits into two half-size slimes that keep half of what he ate. */
    private static void split(Slime s, ServerLevel level) {
        CompoundTag d = data(s);
        int childSize = Math.max(1, s.getSize() / 2);
        for (int i = 0; i < 2; i++) {
            Slime child = (Slime) s.getType().create(level);
            if (child == null) continue;
            double a = s.getRandom().nextDouble() * Math.PI * 2.0D, r = s.getBbWidth() * 0.3D;
            child.moveTo(s.getX() + Math.cos(a) * r, s.getY() + 0.3D, s.getZ() + Math.sin(a) * r,
                    s.getRandom().nextFloat() * 360.0F, 0.0F);
            if (s.isPersistenceRequired()) child.setPersistenceRequired();
            child.setCustomName(s.getCustomName());
            child.setSize(childSize, true);
            CompoundTag cd = data(child);
            cd.putFloat("Food", capacity(child) * 0.5F);
            for (Kind k : Kind.values()) cd.putInt(k.name(), count(d, k) / 2);
            applyStats(child, true);
            level.addFreshEntity(child);
        }
        level.sendParticles(ParticleTypes.ITEM_SLIME, s.getX(), s.getY() + s.getBbHeight() * 0.5D, s.getZ(),
                40, s.getBbWidth() * 0.4D, s.getBbHeight() * 0.3D, s.getBbWidth() * 0.4D, 0.15D);
        s.playSound(s instanceof MagmaCube ? SoundEvents.MAGMA_CUBE_SQUISH : SoundEvents.SLIME_SQUISH, 1.2F, 0.8F);
        s.discard();
    }

    /** Ate enough fire: the slime becomes a magma cube of his size, keeping his name and what he ate. */
    private static void toMagma(Slime s, ServerLevel level) {
        CompoundTag d = data(s).copy();
        int size = s.getSize();
        double x = s.getX(), y = s.getY(), z = s.getZ();
        MagmaCube cube = s.convertTo(EntityType.MAGMA_CUBE, true);
        if (cube == null) return;
        cube.setSize(size, true);
        d.putInt(Kind.FIRE.name(), 0);
        d.putInt(Kind.PLANT.name(), 0);
        cube.getPersistentData().put(TAG, d);
        applyStats(cube, true);
        level.sendParticles(ParticleTypes.FLAME, x, y + cube.getBbHeight() * 0.5D, z, 30,
                cube.getBbWidth() * 0.4D, cube.getBbHeight() * 0.3D, cube.getBbWidth() * 0.4D, 0.02D);
        level.sendParticles(ParticleTypes.LAVA, x, y + cube.getBbHeight() * 0.5D, z, 6, 0.2D, 0.2D, 0.2D, 0.0D);
        cube.playSound(SoundEvents.FIRECHARGE_USE, 1.0F, 0.8F);
        cube.playSound(SoundEvents.MAGMA_CUBE_SQUISH, 1.0F, 0.7F);
        SlimeLife.syncTint(cube);
    }

    // ------------------------------------------------------------------ attributes

    /**
     * Size and diet on top of vanilla's values (vanilla: health size^2, damage size, speed 0.2 + 0.1 size). Called
     * after every setSize (SlimeSizeMixin) and when the slime appears in the world.
     */
    public static void applyStats(Slime s, boolean resetHealth) {
        if (s.level().isClientSide()) return;
        int size = Math.max(1, s.getSize());
        CompoundTag d = peek(s);                                   // (while loading, the data may not be read yet)
        int meat = d == null ? 0 : count(d, Kind.MEAT), ore = d == null ? 0 : count(d, Kind.ORE);
        int plant = d == null ? 0 : count(d, Kind.PLANT);
        set(s, Attributes.MAX_HEALTH, HEALTH_ID, "Slime size and diet",
                0.5D * size * size + Math.min(10.0D, 2.0D * (plant / 4)));
        set(s, Attributes.ATTACK_DAMAGE, ATTACK_ID, "Slime size and diet",
                0.25D * size + Math.min(4.0D, 0.5D * (meat / 4)));
        set(s, Attributes.ARMOR, ARMOR_ID, "Slime diet", Math.min(10.0D, ore / 3));
        if (resetHealth) s.setHealth(s.getMaxHealth());
        else if (s.getHealth() > s.getMaxHealth()) s.setHealth(s.getMaxHealth());
    }

    private static void set(Slime s, Attribute attribute, UUID id, String name, double amount) {
        AttributeInstance inst = s.getAttribute(attribute);
        if (inst == null) return;
        inst.removeModifier(id);
        if (amount != 0.0D) inst.addPermanentModifier(new AttributeModifier(id, name, amount, AttributeModifier.Operation.ADDITION));
    }

    // ------------------------------------------------------------------ colour

    /** Tint of the outer jelly (0xRRGGBB, white = none): towards red (meat), grey (ore) or light green (plants). */
    static int tint(Slime s) {
        CompoundTag d = peek(s);
        if (d == null) return 0xFFFFFF;
        float meat = count(d, Kind.MEAT), ore = count(d, Kind.ORE), plant = count(d, Kind.PLANT);
        float total = meat + ore + plant;
        if (total <= 0.0F) return 0xFFFFFF;
        float r = (meat * 1.0F + ore * 0.62F + plant * 0.72F) / total;
        float g = (meat * 0.48F + ore * 0.64F + plant * 1.0F) / total;
        float b = (meat * 0.45F + ore * 0.72F + plant * 0.45F) / total;
        float k = Math.min(1.0F, total / 16.0F) * 0.7F;            // only lightly, more the more he ate
        int ri = (int) (255 * (1.0F + (r - 1.0F) * k)), gi = (int) (255 * (1.0F + (g - 1.0F) * k));
        int bi = (int) (255 * (1.0F + (b - 1.0F) * k));
        return (ri << 16) | (gi << 8) | bi;
    }

    /** For the corpse particles. */
    static ItemStack corpseCrumb(boolean bony) {
        return new ItemStack(bony ? Items.BONE : Items.ROTTEN_FLESH);
    }
}
