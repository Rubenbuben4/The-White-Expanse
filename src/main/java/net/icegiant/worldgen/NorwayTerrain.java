package net.icegiant.worldgen;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.icegiant.IceGiantMod;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.KeyDispatchDataCodec;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.RegisterEvent;

import java.util.concurrent.ConcurrentHashMap;

/**
 * Density-function wrapper for the Frozen Realm terrain. The maths lives in {@link FrozenTerrainMath}
 * (rules measured in real Lyngen Alps elevation data: real height distribution, slopes that get steeper
 * with altitude, U-troughs/fjords, crests high up, branching erosion gullies).
 *
 * <p>Outputs (field "output"): "height" (surface Y of a column - the noise settings turn it into
 * solid/air), "continent" (ocean &lt; 0 &lt; land), "shore" (biome erosion: beach only right at the water), "mountains" (-1 lowland ... 1 high mountains)
 * "sea_caves" (0..1: where the noise caves may break through the sea floor) and "spires" (the router's temperature
 * since 2026-10-22: 1 in a Kristallspitzen patch, 0.5 in the snowy ring around it, -1 elsewhere).
 * The biome layout uses all three.
 *
 * <p>The noise holders only provide the world seed (so every world looks different); the JSON files
 * stay the same as before.
 */
public record NorwayTerrain(DensityFunction.NoiseHolder warp, DensityFunction.NoiseHolder massif,
                            DensityFunction.NoiseHolder ridge, DensityFunction.NoiseHolder valleyMain,
                            DensityFunction.NoiseHolder valleySide, DensityFunction.NoiseHolder detail,
                            DensityFunction.NoiseHolder continent, DensityFunction.NoiseHolder continentDetail,
                            DensityFunction.NoiseHolder ranges, DensityFunction.NoiseHolder hills,
                            String output) implements DensityFunction {

    public static final MapCodec<NorwayTerrain> MAP_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            NoiseHolder.CODEC.fieldOf("warp").forGetter(NorwayTerrain::warp),
            NoiseHolder.CODEC.fieldOf("massif").forGetter(NorwayTerrain::massif),
            NoiseHolder.CODEC.fieldOf("ridge").forGetter(NorwayTerrain::ridge),
            NoiseHolder.CODEC.fieldOf("valley_main").forGetter(NorwayTerrain::valleyMain),
            NoiseHolder.CODEC.fieldOf("valley_side").forGetter(NorwayTerrain::valleySide),
            NoiseHolder.CODEC.fieldOf("detail").forGetter(NorwayTerrain::detail),
            NoiseHolder.CODEC.fieldOf("continent").forGetter(NorwayTerrain::continent),
            NoiseHolder.CODEC.fieldOf("continent_detail").forGetter(NorwayTerrain::continentDetail),
            NoiseHolder.CODEC.fieldOf("ranges").forGetter(NorwayTerrain::ranges),
            NoiseHolder.CODEC.fieldOf("hills").forGetter(NorwayTerrain::hills),
            Codec.STRING.optionalFieldOf("output", "height").forGetter(NorwayTerrain::output)
    ).apply(i, NorwayTerrain::new));
    public static final KeyDispatchDataCodec<NorwayTerrain> CODEC = KeyDispatchDataCodec.of(MAP_CODEC);

    static final double MIN_Y = -60.0, MAX_Y = 638.0;
    /** Mountains output: metres above sea that count as "full mountain" (1.0). */
    static final double MOUNTAIN_FULL_METRES = 900.0;

    /** One terrain instance per world seed (the record itself must stay immutable). */
    private static final ConcurrentHashMap<Long, FrozenTerrainMath> MATH = new ConcurrentHashMap<>();

    FrozenTerrainMath math() {
        // the seeded noise holders give a stable, world-specific number -> our seed
        long seed = Double.doubleToLongBits(this.warp.getValue(12.345, 0.0, 67.891)) * 31L
                + Double.doubleToLongBits(this.continent.getValue(-98.76, 0.0, 54.321));
        FrozenTerrainMath m = MATH.get(seed);
        if (m == null) {
            if (MATH.size() > 16) MATH.clear();                      // old worlds
            m = MATH.computeIfAbsent(seed, FrozenTerrainMath::new);
        }
        return m;
    }

    private record Last(net.minecraft.world.level.levelgen.RandomState state, FrozenTerrainMath math) {
    }

    private static volatile Last last;

    /** The terrain maths of a world's generator, or null if it is not our terrain (cached for the last one asked). */
    public static FrozenTerrainMath of(net.minecraft.world.level.levelgen.RandomState state) {
        Last l = last;
        if (l != null && l.state() == state) return l.math();
        FrozenTerrainMath m = find(state.router().continents());   // (null: not our terrain)
        last = new Last(state, m);
        return m;
    }

    /** Finds the terrain maths inside a (wrapped) density function, e.g. the noise router's "continents". */
    public static FrozenTerrainMath find(DensityFunction root) {
        FrozenTerrainMath[] found = new FrozenTerrainMath[1];
        root.mapAll(new DensityFunction.Visitor() {
            @Override
            public DensityFunction apply(DensityFunction f) {
                if (found[0] == null && f instanceof NorwayTerrain t) found[0] = t.math();
                return f;
            }
        });
        return found[0];
    }

    @Override
    public double compute(FunctionContext context) {
        double x = context.blockX(), z = context.blockZ();
        FrozenTerrainMath m = math();
        return switch (this.output) {
            case "continent" -> m.continent(x, z);
            case "shore" -> m.cachedShoreErosion(context.blockX(), context.blockZ());
            case "sea_caves" -> m.seaCaveOpening(context.blockX(), context.blockZ());
            case "spires" -> m.spireBiome(x, z);
            case "mountains" -> {
                double[] info = new double[2];
                m.baseY(x, z, info);
                yield Math.max(-1.0, Math.min(1.0, info[1] / MOUNTAIN_FULL_METRES * 2.0 - 1.0));
            }
            default -> Math.max(MIN_Y, Math.min(MAX_Y, m.cachedSurfaceY(context.blockX(), context.blockZ())));
        };
    }

    @Override
    public void fillArray(double[] values, ContextProvider provider) {
        provider.fillAllDirectly(values, this);
    }

    @Override
    public DensityFunction mapAll(Visitor visitor) {
        return visitor.apply(new NorwayTerrain(visitor.visitNoise(this.warp), visitor.visitNoise(this.massif),
                visitor.visitNoise(this.ridge), visitor.visitNoise(this.valleyMain),
                visitor.visitNoise(this.valleySide), visitor.visitNoise(this.detail),
                visitor.visitNoise(this.continent), visitor.visitNoise(this.continentDetail),
                visitor.visitNoise(this.ranges), visitor.visitNoise(this.hills), this.output));
    }

    @Override
    public double minValue() {
        return switch (this.output) {
            case "continent", "shore" -> -2.0;
            case "mountains", "spires" -> -1.0;
            case "sea_caves" -> 0.0;
            default -> MIN_Y;
        };
    }

    @Override
    public double maxValue() {
        return switch (this.output) {
            case "continent", "shore" -> 2.0;
            case "mountains", "sea_caves", "spires" -> 1.0;
            default -> MAX_Y;
        };
    }

    @Override
    public KeyDispatchDataCodec<? extends DensityFunction> codec() {
        return CODEC;
    }

    /** Registers the "icegiant:norway_terrain" density function type. */
    @Mod.EventBusSubscriber(modid = IceGiantMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static final class Registration {
        private Registration() {
        }

        @SubscribeEvent
        public static void register(RegisterEvent event) {
            event.register(Registries.DENSITY_FUNCTION_TYPE, helper -> helper.register(
                    new ResourceLocation(IceGiantMod.MOD_ID, "norway_terrain"), CODEC.codec()));
        }
    }
}
