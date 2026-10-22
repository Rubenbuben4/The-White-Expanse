package net.icegiant.weather;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * Does something above a spot keep the snowstorm off? Used for freezing (Snowstorms) and for how
 * strong the fog/sound is (SnowstormClient) - both sides use the same rule.
 * <p>
 * A block counts as a roof only if it is a solid, closed layer: its collision shape covers the whole
 * top or bottom face (full blocks, glass, slabs, stairs, closed trapdoors ...). Leaves and snow /
 * powder snow never count, and neither do thin or gappy blocks like fences, fence gates, walls,
 * glass panes, iron bars, chains, flowers or torches - the wind blows straight through them.
 */
public final class StormShelter {

    private StormShelter() {
    }

    public static boolean isSheltered(LevelReader level, BlockPos eye) {
        int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING, eye.getX(), eye.getZ());
        BlockPos.MutableBlockPos pos = eye.mutable();
        for (int y = eye.getY() + 1; y < top; y++) {
            pos.setY(y);
            if (isRoof(level, pos)) return true;
        }
        return false;
    }

    private static boolean isRoof(LevelReader level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (state.isAir()) return false;
        if (state.is(BlockTags.LEAVES) || state.is(Blocks.SNOW) || state.is(Blocks.POWDER_SNOW)) return false;
        if (state.is(BlockTags.FENCES) || state.is(BlockTags.FENCE_GATES) || state.is(BlockTags.WALLS)) return false;
        return state.isFaceSturdy(level, pos, Direction.DOWN) || state.isFaceSturdy(level, pos, Direction.UP);
    }
}
