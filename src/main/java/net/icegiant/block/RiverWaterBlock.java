package net.icegiant.block;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.material.FlowingFluid;
import net.minecraft.world.phys.Vec3;

import java.util.function.Supplier;

/**
 * River water of the Frozen Realm: ordinary water (same fluid - buckets, swimming, fishing and boats work as
 * usual) that also knows which way its river runs. The direction is written in when the river is generated
 * (property {@code flow}: 0 = still, 1-16 = 16 compass directions) and RiverFlowMixin hands it to Minecraft as
 * the water's flow. So the water texture moves downstream and pushes players, mobs, items and boats along -
 * while every block stays a still source block, so nothing spills out of the river bed.
 * <p>
 * Own code, inspired by the idea behind the "Streams Reflowing" mod (no code taken from it).
 */
public class RiverWaterBlock extends LiquidBlock {

    public static final IntegerProperty FLOW = IntegerProperty.create("flow", 0, 16);

    public RiverWaterBlock(Supplier<? extends FlowingFluid> fluid, Properties properties) {
        super(fluid, properties);
        this.registerDefaultState(this.defaultBlockState().setValue(FLOW, 0));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(FLOW);
    }

    /** Direction value for an angle in radians (0 = +X, pi/2 = +Z). */
    public static int flowFor(double angle) {
        int i = (int) Math.round(angle / (Math.PI * 2.0) * 16.0);
        return Math.floorMod(i, 16) + 1;
    }

    /** Unit vector downstream for a flow value (zero for still water). */
    public static Vec3 vector(int flow) {
        if (flow <= 0) return Vec3.ZERO;
        double a = (flow - 1) / 16.0 * Math.PI * 2.0;
        return new Vec3(Math.cos(a), 0.0, Math.sin(a));
    }
}
