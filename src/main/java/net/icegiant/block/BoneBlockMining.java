package net.icegiant.block;

import net.icegiant.IceGiantMod;
import net.icegiant.registry.ModBlocks;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TieredItem;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.common.ToolActions;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * The bone building blocks, the vertical slabs (axe for wooden ones) and the dungeon door (axe) drop themselves with any tool (or by hand), like the vanilla bone block they are
 * made of, but a pickaxe still mines them as fast as it mines a bone block (tier speed + Efficiency).
 */
@Mod.EventBusSubscriber(modid = IceGiantMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class BoneBlockMining {

    private BoneBlockMining() {
    }

    private static boolean isBone(Block b) {
        return b == ModBlocks.BONE_STAIRS.get() || b == ModBlocks.BONE_SLAB.get() || b == ModBlocks.BONE_WALL.get()
                || b == ModBlocks.BONE_FENCE.get() || b == ModBlocks.BONE_FENCE_GATE.get()
                || b == ModBlocks.BONE_TRAPDOOR.get() || b == ModBlocks.BONE_DOOR.get()
                || b == ModBlocks.BONE_BUTTON.get() || b == ModBlocks.BONE_PRESSURE_PLATE.get();
    }

    @SubscribeEvent
    public static void breakSpeed(PlayerEvent.BreakSpeed event) {
        Block block = event.getState().getBlock();
        net.minecraftforge.common.ToolAction action;
        if (isBone(block)) action = ToolActions.PICKAXE_DIG;
        else if (block instanceof VerticalSlabBlock slab) action = slab.isWooden() ? ToolActions.AXE_DIG : ToolActions.PICKAXE_DIG;
        else if (block instanceof DungeonDoorBlock) action = ToolActions.AXE_DIG;
        else return;
        ItemStack tool = event.getEntity().getMainHandItem();
        if (!tool.canPerformAction(action) || !(tool.getItem() instanceof TieredItem tiered)) return;
        float speed = tiered.getTier().getSpeed();
        int efficiency = EnchantmentHelper.getBlockEfficiency(event.getEntity());
        if (efficiency > 0) speed += efficiency * efficiency + 1;
        event.setNewSpeed(event.getNewSpeed() * speed);
    }
}
