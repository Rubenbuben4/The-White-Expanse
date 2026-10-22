package net.icegiant.dimension;

import net.icegiant.block.FrostPortalBlock;
import net.icegiant.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Optional;

/**
 * Independent Frost-Portal equivalent of vanilla's Nether portal frame
 * detection: a rectangular Blue Ice frame (any size within the limits
 * below) around an empty pocket, lit from inside with the Frost Flint
 * and Steel. Written from scratch against the observed vanilla mechanic
 * (frame edges required, corners not required, variable width/height),
 * not copied from decompiled game code.
 */
public final class FrostPortalShape {

    public static final int MIN_WIDTH = 2;
    public static final int MAX_WIDTH = 21;
    public static final int MIN_HEIGHT = 3;
    public static final int MAX_HEIGHT = 21;

    private final Direction.Axis axis;
    private final BlockPos originCorner; // interior's bottom "left" corner (w=0, h=0)
    private final int width;
    private final int height;

    private FrostPortalShape(Direction.Axis axis, BlockPos originCorner, int width, int height) {
        this.axis = axis;
        this.originCorner = originCorner;
        this.width = width;
        this.height = height;
    }

    public Direction.Axis getAxis() {
        return axis;
    }

    /** Tries both horizontal axes and returns the first valid frame found around {@code fromPos}. */
    public static Optional<FrostPortalShape> findEmptyPortalShape(Level level, BlockPos fromPos) {
        Optional<FrostPortalShape> onX = trace(level, fromPos, Direction.Axis.X);
        if (onX.isPresent()) {
            return onX;
        }
        return trace(level, fromPos, Direction.Axis.Z);
    }

    private static boolean isFrame(Level level, BlockPos pos) {
        return level.getBlockState(pos).is(Blocks.BLUE_ICE);
    }

    private static boolean isOpen(Level level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        return state.isAir() || state.is(ModBlocks.FROST_PORTAL.get());
    }

    private static Optional<FrostPortalShape> trace(Level level, BlockPos fromPos, Direction.Axis axis) {
        if (!isOpen(level, fromPos)) {
            return Optional.empty();
        }

        Direction widthDir = axis == Direction.Axis.X ? Direction.WEST : Direction.NORTH;
        Direction widthDirOpp = widthDir.getOpposite();

        // Walk to the width-wise edge of the open pocket in one direction...
        BlockPos left = fromPos;
        int stepsLeft = 0;
        while (isOpen(level, left.relative(widthDir)) && stepsLeft < MAX_WIDTH) {
            left = left.relative(widthDir);
            stepsLeft++;
        }
        if (!isFrame(level, left.relative(widthDir))) {
            return Optional.empty();
        }

        // ...then walk back across to find the far edge and measure width.
        int width = 1;
        BlockPos cursor = left;
        while (width <= MAX_WIDTH) {
            BlockPos next = cursor.relative(widthDirOpp);
            if (isFrame(level, next)) break;
            if (!isOpen(level, next)) return Optional.empty();
            cursor = next;
            width++;
        }
        if (width < MIN_WIDTH || width > MAX_WIDTH) return Optional.empty();
        if (!isFrame(level, cursor.relative(widthDirOpp))) return Optional.empty();

        // Same idea vertically, from the "left" column.
        BlockPos bottom = left;
        int stepsDown = 0;
        while (isOpen(level, bottom.below()) && stepsDown < MAX_HEIGHT) {
            bottom = bottom.below();
            stepsDown++;
        }
        if (!isFrame(level, bottom.below())) return Optional.empty();

        int height = 1;
        BlockPos vCursor = bottom;
        while (height <= MAX_HEIGHT) {
            BlockPos next = vCursor.above();
            if (isFrame(level, next)) break;
            if (!isOpen(level, next)) return Optional.empty();
            vCursor = next;
            height++;
        }
        if (height < MIN_HEIGHT || height > MAX_HEIGHT) return Optional.empty();
        if (!isFrame(level, vCursor.above())) return Optional.empty();

        // Full perimeter check: the four straight edges must be frame,
        // corners are exempt (matches the vanilla nether portal leniency),
        // and every interior cell must be open.
        for (int h = -1; h <= height; h++) {
            for (int w = -1; w <= width; w++) {
                BlockPos p = offset(bottom, widthDir, w, h);
                boolean edgeW = (w == -1 || w == width);
                boolean edgeH = (h == -1 || h == height);
                if (edgeW || edgeH) {
                    boolean isCorner = edgeW && edgeH;
                    if (!isCorner && !isFrame(level, p)) {
                        return Optional.empty();
                    }
                } else if (!isOpen(level, p)) {
                    return Optional.empty();
                }
            }
        }

        return Optional.of(new FrostPortalShape(axis, bottom, width, height));
    }

    private static BlockPos offset(BlockPos origin, Direction widthDir, int widthSteps, int heightSteps) {
        return origin.relative(widthDir.getOpposite(), widthSteps).above(heightSteps);
    }

    /** Fills this shape's interior with Frost Portal blocks. */
    public void fill(Level level) {
        Direction widthDir = axis == Direction.Axis.X ? Direction.WEST : Direction.NORTH;
        BlockState portalState = ModBlocks.FROST_PORTAL.get().defaultBlockState().setValue(FrostPortalBlock.AXIS, axis);

        for (int h = 0; h < height; h++) {
            for (int w = 0; w < width; w++) {
                BlockPos p = offset(originCorner, widthDir, w, h);
                level.setBlock(p, portalState, 18);
            }
        }
    }

    /**
     * Builds a fresh, minimal (2 wide x 3 tall interior) Blue Ice frame at
     * the given base position and fills it with Frost Portal blocks - used
     * when teleporting somewhere with no existing portal to link back to,
     * mirroring how a vanilla Nether portal generates its own frame on
     * arrival instead of leaving a bare, frame-less portal block floating
     * in the air.
     */
    public static void createNewPortal(Level level, BlockPos basePos, Direction.Axis axis) {
        int interiorWidth = MIN_WIDTH;
        int interiorHeight = MIN_HEIGHT;
        Direction widthDir = axis == Direction.Axis.X ? Direction.WEST : Direction.NORTH;

        // basePos is the bottom-left INTERIOR corner.
        for (int h = -1; h <= interiorHeight; h++) {
            for (int w = -1; w <= interiorWidth; w++) {
                BlockPos p = offset(basePos, widthDir, w, h);
                boolean isFrameCell = (w == -1 || w == interiorWidth || h == -1 || h == interiorHeight);
                if (isFrameCell) {
                    level.setBlock(p, Blocks.BLUE_ICE.defaultBlockState(), 18);
                }
            }
        }

        new FrostPortalShape(axis, basePos, interiorWidth, interiorHeight).fill(level);
    }
}
