package net.icegiant.dimension;

import net.icegiant.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.portal.PortalInfo;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.ITeleporter;

import javax.annotation.Nullable;
import java.util.function.Function;

/**
 * Places the traveling entity next to a Frost Portal block on the other
 * side, creating one if none already exists near the matching X/Z
 * coordinates (1:1 coordinate mapping - no scaling, like the End, not the
 * 8:1 Nether scaling). If nothing is nearby, a fresh, minimal Blue Ice
 * frame is generated on arrival - same idea as a vanilla Nether portal
 * building its own return frame instead of leaving a bare floating block.
 * <p>
 * The arrival point is handed to Forge through {@link #getPortalInfo}: Forge moves players (and
 * sends the position to their client) from that, before placeEntity runs. Without it a player kept
 * the Y of the world they came from - in the much higher Frozen Realm that meant arriving underground
 * next to a portal standing on the surface.
 */
public class FrostPortalTeleporter implements ITeleporter {

    /** Horizontal search radius for an existing portal; vertically the whole world is searched. */
    private static final int SEARCH_RADIUS = 6;

    private final BlockPos sourcePos;
    private final Direction.Axis sourceAxis;
    /** Found or built once per trip, then reused by getPortalInfo and placeEntity. */
    @Nullable private BlockPos destination;

    public FrostPortalTeleporter(BlockPos sourcePos, Direction.Axis sourceAxis) {
        this.sourcePos = sourcePos;
        this.sourceAxis = sourceAxis;
    }

    private BlockPos destination(ServerLevel destWorld) {
        if (this.destination == null) {
            this.destination = findOrCreatePortal(destWorld, this.sourcePos, this.sourceAxis);
        }
        return this.destination;
    }

    @Override
    public PortalInfo getPortalInfo(Entity entity, ServerLevel destWorld,
                                    Function<ServerLevel, PortalInfo> defaultPortalInfo) {
        BlockPos d = destination(destWorld);
        return new PortalInfo(new Vec3(d.getX() + 0.5D, d.getY() + 0.1D, d.getZ() + 0.5D), Vec3.ZERO,
                entity.getYRot(), entity.getXRot());
    }

    @Override
    public Entity placeEntity(Entity entity, ServerLevel currentWorld, ServerLevel destWorld, float yaw,
                               Function<Boolean, Entity> repositionEntity) {
        // repositionEntity moves the entity to the position from getPortalInfo (players: incl. the client)
        Entity repositioned = repositionEntity.apply(false);
        BlockPos d = destination(destWorld);
        repositioned.setPos(d.getX() + 0.5D, d.getY() + 0.1D, d.getZ() + 0.5D);
        repositioned.setDeltaMovement(0, 0, 0);
        return repositioned;
    }

    private static BlockPos findOrCreatePortal(ServerLevel destWorld, BlockPos sourcePos, Direction.Axis axis) {
        BlockPos existing = findNearbyPortal(destWorld, sourcePos, SEARCH_RADIUS);
        if (existing != null) {
            return existing;
        }

        // ground height without leaves, so a new portal never ends up on a tree top
        int surfaceY = destWorld.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, sourcePos.getX(), sourcePos.getZ());
        int y = Math.max(destWorld.getMinBuildHeight() + 1, Math.min(destWorld.getMaxBuildHeight() - 4, surfaceY));
        BlockPos basePos = new BlockPos(sourcePos.getX(), y, sourcePos.getZ());

        FrostPortalShape.createNewPortal(destWorld, basePos, axis);
        return basePos;
    }

    /**
     * Looks for a Frost Portal near the same X/Z at ANY height (the Frozen Realm has mountains up to
     * Y 639, so the portal on the other side can be hundreds of blocks above or below). Picks the one
     * closest to the source height.
     */
    private static BlockPos findNearbyPortal(ServerLevel level, BlockPos center, int radius) {
        BlockPos best = null;
        double bestDist = Double.MAX_VALUE;
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                int x = center.getX() + dx, z = center.getZ() + dz;
                for (int y = level.getMinBuildHeight(); y < level.getMaxBuildHeight(); y++) {
                    p.set(x, y, z);
                    if (level.getBlockState(p).is(ModBlocks.FROST_PORTAL.get())) {
                        double d = p.distSqr(center);
                        if (d < bestDist) {
                            bestDist = d;
                            best = p.immutable();
                        }
                    }
                }
            }
        }
        return best;
    }
}
