package net.icegiant.worldgen.camp;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.icegiant.registry.ModStructures;
import net.icegiant.worldgen.FrozenTerrainMath;
import net.icegiant.worldgen.NorwayTerrain;
import net.icegiant.worldgen.RiverFeature;
import net.icegiant.worldgen.RiverNetwork;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.structure.BuiltinStructureSets;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement;
import net.minecraft.world.level.NoiseColumn;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePiecesBuilder;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

import java.util.Optional;
import java.util.Random;

/**
 * Gnoll camps (huts: Louisdepoui's builds, used with his permission - see CREDITS): laid out by CampPlanner - an
 * open square with a campfire and log benches, the huts close round it with their entrances turned to it, short
 * trodden paths, a way out marked by skull poles, barrels, hay, wood piles and lantern posts.
 * <ul>
 *   <li><b>large</b>: the chief's and the shaman's hut, one or two more big buildings, small huts round them - home
 *       of a chief, shamans and many warriors;</li>
 *   <li><b>small</b>: 3-4 small huts, sometimes the bar - no chief, no shaman hut, no shamans;</li>
 *   <li><b>huge</b> (rare, {@code "huge": true}, about twice a large camp): the main square with the chief's, the
 *       shaman's and two more big buildings, two or three districts with squares and small huts of their own, broad
 *       streets with lantern posts between them and out of the camp.</li>
 * </ul>
 * Kinds ({@code kind} in the structure file):
 * <ul>
 *   <li>{@code snow}: snowy biomes of the Overworld (snow and normal Gnolls) and the Frozen Realm (snow Gnolls only);</li>
 *   <li>{@code taiga}: taiga of the Overworld (normal - brown - and warm - desert - Gnolls).</li>
 * </ul>
 * Not on rivers or lakes (Frozen Realm: checked against the river plan), not on steep ground; trees and boulders
 * keep out of the camp (GnollCamps.insideCamp).
 */
public class GnollCampStructure extends Structure {

    public static final Codec<GnollCampStructure> CODEC = RecordCodecBuilder.create(i -> i.group(
            settingsCodec(i),
            Codec.BOOL.fieldOf("large").forGetter(s -> s.large),
            Codec.BOOL.optionalFieldOf("huge", false).forGetter(s -> s.huge),
            Codec.STRING.optionalFieldOf("kind", "snow").forGetter(s -> s.kind)
    ).apply(i, GnollCampStructure::new));

    private static final org.slf4j.Logger LOGGER = com.mojang.logging.LogUtils.getLogger();

    // ---- TUNE ----
    static final int MAX_SLOPE_HUGE = 16, MAX_SLOPE_LARGE = 10, MAX_SLOPE_SMALL = 8;   // camp ground may vary at most this much
    static final int RIVER_CLEAR_HUGE = 88, RIVER_CLEAR_LARGE = 56, RIVER_CLEAR_SMALL = 40;   // Frozen Realm: no river or lake this close
    static final int VILLAGE_CLEAR_HUGE = 12;   // huge camps reach far: no village may start within this many chunks
                                                // (the camps' structure set keeps all camps only 6 chunks off)
    private static final net.minecraft.resources.ResourceLocation FROZEN_REALM_SETTINGS =
            new net.minecraft.resources.ResourceLocation("icegiant", "frozen_realm");

    final boolean large, huge;
    final String kind;

    public GnollCampStructure(StructureSettings settings, boolean large, boolean huge, String kind) {
        super(settings);
        this.large = large || huge;
        this.huge = huge;
        this.kind = kind;
    }

    CampPlanner.Size size() {
        return this.huge ? CampPlanner.Size.HUGE : this.large ? CampPlanner.Size.LARGE : CampPlanner.Size.SMALL;
    }

    @Override
    public StructureType<?> type() {
        return ModStructures.GNOLL_CAMP.get();
    }

    @Override
    protected Optional<GenerationStub> findGenerationPoint(GenerationContext ctx) {
        int x = ctx.chunkPos().getMiddleBlockX(), z = ctx.chunkPos().getMiddleBlockZ();
        int y = top(ctx, x, z);
        if (wet(ctx, x, y, z)) return Optional.empty();
        int r = this.huge ? 80 : this.large ? 32 : 20, step = this.huge ? 40 : r, lo = y, hi = y;   // huge: 5 x 5 spots
        for (int dx = -r; dx <= r; dx += step) {
            for (int dz = -r; dz <= r; dz += step) {
                int h = top(ctx, x + dx, z + dz);
                lo = Math.min(lo, h);
                hi = Math.max(hi, h);
            }
        }
        if (hi - lo > (this.huge ? MAX_SLOPE_HUGE : this.large ? MAX_SLOPE_LARGE : MAX_SLOPE_SMALL)) return Optional.empty();
        if (nearRiver(ctx, x, z, this.huge ? RIVER_CLEAR_HUGE : this.large ? RIVER_CLEAR_LARGE : RIVER_CLEAR_SMALL)) return Optional.empty();
        if (this.huge && nearVillage(ctx, VILLAGE_CLEAR_HUGE)) return Optional.empty();
        BlockPos center = new BlockPos(x, y, z);
        return Optional.of(new GenerationStub(center, builder -> this.layout(builder, ctx, center)));
    }

    static int top(GenerationContext ctx, int x, int z) {
        return ctx.chunkGenerator().getFirstOccupiedHeight(x, z, Heightmap.Types.WORLD_SURFACE_WG, ctx.heightAccessor(), ctx.randomState());
    }

    static boolean wet(GenerationContext ctx, int x, int y, int z) {
        NoiseColumn col = ctx.chunkGenerator().getBaseColumn(x, z, ctx.heightAccessor(), ctx.randomState());
        return !col.getBlock(y).getFluidState().isEmpty() || !col.getBlock(y + 1).getFluidState().isEmpty();
    }

    /** Frozen Realm: a river, its banks or a lake within r blocks (the rivers are dug later, by a feature). */
    static boolean nearRiver(GenerationContext ctx, int x, int z, int r) {
        FrozenTerrainMath math = NorwayTerrain.find(ctx.randomState().router().continents());
        if (math == null) return false;                                     // not our terrain: no river plan
        RiverNetwork net = RiverFeature.networkFor(math, ctx.seed());
        int r2 = r * r;
        for (int cx = Math.floorDiv(x - r, 16); cx <= Math.floorDiv(x + r, 16); cx++) {
            for (int cz = Math.floorDiv(z - r, 16); cz <= Math.floorDiv(z + r, 16); cz++) {
                RiverNetwork.ChunkData d = net.chunk(cx, cz);
                if (!d.any) continue;
                for (int i = 0; i < 256; i++) {
                    byte k = d.kind[i];
                    if (k == RiverNetwork.ChunkData.NONE || (k == RiverNetwork.ChunkData.NEAR && d.edge[i] > 3.0F)) continue;
                    int dx = cx * 16 + i / 16 - x, dz = cz * 16 + i % 16 - z;
                    if (dx * dx + dz * dz <= r2) return true;
                }
            }
        }
        return false;
    }

    /**
     * True if a village may start within r chunks of this chunk: one of the villages' possible start chunks (their
     * random spread placement) is that close and its biome suits one of the villages (asked like Minecraft asks it:
     * at the chunk's corner, on the ground). Not in the Frozen Realm (no villages there, NoVillagesMixin).
     */
    static boolean nearVillage(GenerationContext ctx, int r) {
        if (ctx.chunkGenerator() instanceof NoiseBasedChunkGenerator noise && noise.generatorSettings().is(FROZEN_REALM_SETTINGS)) {
            return false;
        }
        Optional<Registry<StructureSet>> sets = ctx.registryAccess().registry(Registries.STRUCTURE_SET);
        if (sets.isEmpty()) return false;
        StructureSet villages = sets.get().get(BuiltinStructureSets.VILLAGES);
        if (villages == null || !(villages.placement() instanceof RandomSpreadStructurePlacement spread)) return false;
        ChunkPos c = ctx.chunkPos();
        int sp = spread.spacing();
        for (int gx = Math.floorDiv(c.x - r, sp); gx <= Math.floorDiv(c.x + r, sp); gx++) {
            for (int gz = Math.floorDiv(c.z - r, sp); gz <= Math.floorDiv(c.z + r, sp); gz++) {
                ChunkPos v = spread.getPotentialStructureChunk(ctx.seed(), gx * sp, gz * sp);
                if (Math.abs(v.x - c.x) > r || Math.abs(v.z - c.z) > r) continue;
                int bx = v.getMinBlockX(), bz = v.getMinBlockZ(), by = top(ctx, bx, bz) + 1;
                Holder<Biome> biome = ctx.chunkGenerator().getBiomeSource().getNoiseBiome(QuartPos.fromBlock(bx),
                        QuartPos.fromBlock(by), QuartPos.fromBlock(bz), ctx.randomState().sampler());
                for (StructureSet.StructureSelectionEntry e : villages.structures()) {
                    if (e.structure().value().biomes().contains(biome)) return true;
                }
            }
        }
        return false;
    }

    // ------------------------------------------------------------------ layout

    /**
     * Lays the camp out (CampPlanner) and adds its pieces. If there is no room for it, no pieces - Minecraft then
     * tries the other camps of the set at this spot (a huge camp that does not fit may become a smaller one).
     */
    private void layout(StructurePiecesBuilder builder, GenerationContext ctx, BlockPos center) {
        CampPlanner.Ground ground = new CampPlanner.Ground() {
            final java.util.HashMap<Long, Integer> tops = new java.util.HashMap<>();

            @Override
            public int height(int x, int z) {
                return this.tops.computeIfAbsent(((long) x << 32) ^ (z & 0xffffffffL), k -> top(ctx, x, z));
            }

            /** Water (or lava) on top: the top block does not hold you up - the ocean-floor height lies below it. */
            @Override
            public boolean wet(int x, int z) {
                return ctx.chunkGenerator().getFirstOccupiedHeight(x, z, Heightmap.Types.OCEAN_FLOOR_WG, ctx.heightAccessor(),
                        ctx.randomState()) < this.height(x, z);
            }
        };
        CampPlanner.Plan plan = CampPlanner.plan(center.getX(), center.getZ(), this.size(),
                new Random(ctx.random().nextLong()), ground, CampTemplates.HUTS);
        if (plan == null) return;                                            // not room for a camp here
        BlockPos camp = new BlockPos(plan.cx(), plan.plazaY(), plan.cz());
        int stay = this.huge ? Math.max(GnollCamps.STAY_LARGE, plan.radius() - 4)    // how far its Gnolls wander
                : this.large ? GnollCamps.STAY_LARGE : GnollCamps.STAY_SMALL;
        for (CampPlanner.Placed p : plan.huts()) {
            Rotation rot = Rotation.values()[p.rot() & 3];
            StructureTemplate template = ctx.structureTemplateManager().getOrCreate(GnollCampPiece.location(p.name()));
            CampPlanner.Hut h = CampTemplates.HUTS.get(p.name());
            if (template.getSize().getX() != h.w() || template.getSize().getZ() != h.l()) {      // a build was replaced
                LOGGER.warn("[icegiant] gnoll camp building {} is {}x{}, CampTemplates expects {}x{} - entrance may be off",
                        p.name(), template.getSize().getX(), template.getSize().getZ(), h.w(), h.l());
            }
            BoundingBox raw = template.getBoundingBox(new StructurePlaceSettings().setRotation(rot), BlockPos.ZERO);
            BlockPos pos = new BlockPos(p.minX() - raw.minX(), CampTemplates.placeY(p.name(), p.y()), p.minZ() - raw.minZ());
            builder.addPiece(new GnollCampPiece(ctx.structureTemplateManager(), p.name(), pos, rot, this.large, this.kind,
                    camp, stay));
        }
        int maxY = ctx.heightAccessor().getMaxBuildHeight();
        for (CampPlanner.Square s : plan.squares()) {                         // the square (a huge camp's districts too)
            builder.addPiece(GnollCampPathPiece.square(new BlockPos(s.x(), s.y(), s.z()), s.r(), s.reach()));
        }
        for (CampPlanner.Path path : plan.paths()) {
            builder.addPiece(new GnollCampPathPiece(new BlockPos(path.x0(), plan.plazaY(), path.z0()),
                    new BlockPos(path.x1(), plan.plazaY(), path.z1()), path.width(), maxY, false));
        }
        builder.addPiece(new GnollCampPropsPiece(plan.props(), plan.plazaY(), maxY));
    }
}
