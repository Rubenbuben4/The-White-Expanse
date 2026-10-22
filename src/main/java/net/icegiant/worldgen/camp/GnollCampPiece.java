package net.icegiant.worldgen.camp;

import net.icegiant.IceGiantMod;
import net.icegiant.entity.gnoll.GnollEntity;
import net.icegiant.registry.ModEntityTypes;
import net.icegiant.registry.ModStructures;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.TemplateStructurePiece;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;
import net.minecraft.world.level.levelgen.structure.templatesystem.BlockIgnoreProcessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;

/**
 * One building of a Gnoll camp (a structure template from data/icegiant/structures/gnoll_camp/) and the
 * Gnolls that live in it - spawned once, when the chunk with the building's middle is generated:
 * <ul>
 *   <li>chief_hut: the chief (a warrior in full armour, more health, named) and his guards;</li>
 *   <li>shaman_hut: two shamans and a warrior;</li>
 *   <li>the others: a few warriors and archers.</li>
 * </ul>
 * Shamans live only in large camps (they come with the shaman's hut). Their skins follow the camp's kind
 * (GnollCamps.variantFor: Frozen Realm snow only, snowy camps snow and normal, taiga camps normal and warm); they
 * never despawn and stay within their camp (GnollCamps; huge camps: further out, over the districts).
 */
public class GnollCampPiece extends TemplateStructurePiece {

    private final String name;
    private final boolean large;
    private final String kind;
    private final BlockPos camp;               // middle of the camp's square
    private final int stay;                    // how far from it its Gnolls wander

    public static ResourceLocation location(String name) {
        return new ResourceLocation(IceGiantMod.MOD_ID, "gnoll_camp/" + name);
    }

    /** Height of the ground layer inside the template (it is laid onto the world's surface). */
    public static int groundLevel(String name) {
        return CampTemplates.groundLevel(name);
    }

    public GnollCampPiece(StructureTemplateManager templates, String name, BlockPos pos, Rotation rot, boolean large,
                           String kind, BlockPos camp, int stay) {
        super(ModStructures.GNOLL_CAMP_BUILDING.get(), 0, templates, location(name), location(name).toString(), settings(rot), pos);
        this.name = name;
        this.large = large;
        this.kind = kind;
        this.camp = camp;
        this.stay = stay;
    }

    public GnollCampPiece(StructureTemplateManager templates, CompoundTag tag) {
        super(ModStructures.GNOLL_CAMP_BUILDING.get(), tag, templates,
                id -> settings(Rotation.valueOf(tag.getString("Rot"))));
        this.name = tag.getString("Name");
        this.large = tag.getBoolean("Large");
        this.kind = tag.contains("Kind") ? tag.getString("Kind") : "snow";       // (camps from before 2026-10-17)
        this.camp = tag.contains("Camp") ? BlockPos.of(tag.getLong("Camp")) : this.boundingBox.getCenter();
        this.stay = tag.contains("Stay") ? tag.getInt("Stay") : this.large ? GnollCamps.STAY_LARGE : GnollCamps.STAY_SMALL;
    }

    private static StructurePlaceSettings settings(Rotation rot) {
        return new StructurePlaceSettings().setRotation(rot).addProcessor(BlockIgnoreProcessor.STRUCTURE_BLOCK);
    }

    @Override
    protected void addAdditionalSaveData(StructurePieceSerializationContext ctx, CompoundTag tag) {
        super.addAdditionalSaveData(ctx, tag);
        tag.putString("Rot", this.placeSettings.getRotation().name());
        tag.putString("Name", this.name);
        tag.putBoolean("Large", this.large);
        tag.putString("Kind", this.kind);
        tag.putLong("Camp", this.camp.asLong());
        tag.putInt("Stay", this.stay);
    }

    @Override
    protected void handleDataMarker(String marker, BlockPos pos, ServerLevelAccessor level, RandomSource random, BoundingBox box) {
    }

    @Override
    public void postProcess(WorldGenLevel level, StructureManager structures, ChunkGenerator generator, RandomSource random,
                            BoundingBox chunkBox, ChunkPos chunkPos, BlockPos pivot) {
        super.postProcess(level, structures, generator, random, chunkBox, chunkPos, pivot);
        BlockPos mid = this.boundingBox.getCenter();
        if (chunkBox.isInside(new BlockPos(mid.getX(), chunkBox.minY(), mid.getZ()))) this.spawnInhabitants(level, random);
    }

    // ------------------------------------------------------------------ inhabitants

    private void spawnInhabitants(WorldGenLevel level, RandomSource random) {
        int melee, archers, shamans = 0;
        boolean chief = false;
        switch (this.name) {
            case "chief_hut" -> { chief = true; melee = 2; archers = 1; }
            case "shaman_hut" -> { shamans = 2; melee = 1; archers = 0; }
            case "bar" -> { melee = this.large ? 2 : 1; archers = 1; }
            case "stacked_hut" -> { melee = 1; archers = 2; }
            case "fishhead_hut" -> { melee = 1; archers = 1; }
            default -> { melee = 1; archers = random.nextInt(2); }
        }
        if (!this.large) shamans = 0;                                            // small camps: no mages
        if (chief) this.spawn(level, random, ModEntityTypes.GNOLL_MELEE.get(), true);
        for (int i = 0; i < melee; i++) this.spawn(level, random, ModEntityTypes.GNOLL_MELEE.get(), false);
        for (int i = 0; i < archers; i++) this.spawn(level, random, ModEntityTypes.GNOLL_ARCHER.get(), false);
        for (int i = 0; i < shamans; i++) this.spawn(level, random, ModEntityTypes.GNOLL_SHAMAN.get(), false);
    }

    private void spawn(WorldGenLevel level, RandomSource random, EntityType<? extends GnollEntity> type, boolean chief) {
        BlockPos spot = this.findSpot(level, random);
        if (spot == null) return;
        GnollEntity k = type.create(level.getLevel());
        if (k == null) return;
        k.moveTo(spot.getX() + 0.5D, spot.getY(), spot.getZ() + 0.5D, random.nextFloat() * 360.0F, 0.0F);
        k.finalizeSpawn(level, level.getCurrentDifficultyAt(spot), MobSpawnType.STRUCTURE, null, null);
        k.setVariant(GnollCamps.variantFor(this.kind, level.getLevel().dimension() == net.icegiant.dimension.ModDimensions.FROZEN_REALM, random));
        k.setPersistenceRequired();
        GnollCamps.markCamp(k, this.campCenter(), this.stay);
        if (chief) k.makeChief();
        level.addFreshEntityWithPassengers(k);
    }

    /** The camp's middle (its square): the Gnolls keep around it. */
    private BlockPos campCenter() {
        return this.camp;
    }

    /** A free spot (2 blocks of air on solid ground) inside or right next to the building. */
    private BlockPos findSpot(WorldGenLevel level, RandomSource random) {
        BoundingBox b = this.boundingBox;
        int base = this.templatePosition.getY() + groundLevel(this.name);
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        for (int attempt = 0; attempt < 24; attempt++) {
            int x = b.minX() + 2 + random.nextInt(Math.max(1, b.getXSpan() - 4));
            int z = b.minZ() + 2 + random.nextInt(Math.max(1, b.getZSpan() - 4));
            for (int y = base; y < base + 8; y++) {
                p.set(x, y, z);
                if (!level.getBlockState(p).isFaceSturdy(level, p, net.minecraft.core.Direction.UP)) continue;
                if (!level.getBlockState(p.above()).getCollisionShape(level, p.above()).isEmpty()) continue;
                if (!level.getBlockState(p.above(2)).getCollisionShape(level, p.above(2)).isEmpty()) continue;
                if (!level.getFluidState(p.above()).isEmpty()) continue;
                return p.above().immutable();
            }
        }
        return null;
    }
}
