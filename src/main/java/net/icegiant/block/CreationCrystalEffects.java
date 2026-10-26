package net.icegiant.block;

import net.icegiant.registry.ModSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The Schöpfungskristall's air (Rubenbuben4, 2026-10-26), client side only: the enchanting table's glyphs fly in
 * towards the crystal from all around, its magic sound sometimes plays softly, and now and then the crystal hums
 * (his singing bowl / mystic whistle). All sounds are mono and positional, so they get quieter with distance.
 * Awake crystals do all of it more often than sleeping ones; the large one fills a wider space.
 */
public final class CreationCrystalEffects {

    // chance per tick: glyph, enchanting sound, hum (awake / asleep)
    static final float GLYPH_AWAKE = 0.6F, GLYPH_ASLEEP = 0.12F;
    static final float MAGIC_AWAKE = 1.0F / 160.0F, MAGIC_ASLEEP = 1.0F / 900.0F;   // ~8 s / ~45 s
    static final float HUM_AWAKE = 1.0F / 300.0F, HUM_ASLEEP = 0.0F;                // ~15 s
    static final float MAGIC_VOLUME = 0.25F, HUM_VOLUME = 0.55F;

    private CreationCrystalEffects() {
    }

    public static void clientTick(Level level, BlockPos pos, BlockState state, CreationCrystalBlockEntity crystal) {
        RandomSource r = level.getRandom();
        boolean awake = state.hasProperty(CreationCrystalBlock.ACTIVE) && state.getValue(CreationCrystalBlock.ACTIVE);
        boolean large = crystal.large();
        double cx = pos.getX() + 0.5D, cy = pos.getY() + 0.5D, cz = pos.getZ() + 0.5D;

        // glyphs: the enchant particle starts at (centre + delta) and flies to the centre
        int glyphs = large ? 3 : 1;
        float reach = large ? 4.5F : 2.0F;
        for (int i = 0; i < glyphs; i++) {
            if (r.nextFloat() >= (awake ? GLYPH_AWAKE : GLYPH_ASLEEP)) continue;
            double dx = (r.nextDouble() * 2.0D - 1.0D) * reach;
            double dy = (r.nextDouble() * 2.0D - 1.0D) * reach * 0.7D;
            double dz = (r.nextDouble() * 2.0D - 1.0D) * reach;
            level.addParticle(ParticleTypes.ENCHANT, cx, cy, cz, dx, dy, dz);
        }

        float pitch = large ? 0.8F : 1.0F;
        if (r.nextFloat() < (awake ? MAGIC_AWAKE : MAGIC_ASLEEP)) {
            level.playLocalSound(cx, cy, cz, SoundEvents.ENCHANTMENT_TABLE_USE, SoundSource.BLOCKS,
                    MAGIC_VOLUME * (0.7F + 0.3F * r.nextFloat()), pitch * (0.9F + 0.2F * r.nextFloat()), false);
        }
        if (r.nextFloat() < (awake ? HUM_AWAKE : HUM_ASLEEP)) {
            level.playLocalSound(cx, cy, cz, ModSounds.CREATION_CRYSTAL_AMBIENT.get(), SoundSource.BLOCKS,
                    HUM_VOLUME, pitch * (0.95F + 0.1F * r.nextFloat()), false);
        }
    }
}
