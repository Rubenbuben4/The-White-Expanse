package net.icegiant.registry;

import net.icegiant.IceGiantMod;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

/**
 * One creative tab holding everything the mod adds, so none of it needs
 * /give commands to test - just open the inventory.
 * <p>
 * The Fracture and Frost Portal blocks are deliberately left out: neither
 * has a BlockItem (they're only ever placed by the mod's own code - a
 * temporary ground-slam effect and a portal lit by the Frost Flint and
 * Steel), so there's nothing sensible to hand-place from creative for them.
 */
public class ModCreativeTabs {

    public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, IceGiantMod.MOD_ID);

    public static final RegistryObject<CreativeModeTab> ICE_GIANT_TAB = CREATIVE_MODE_TABS.register("ice_giant_tab",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.icegiant"))
                    .icon(() -> new ItemStack(ModItems.NORDIC_AXE.get()))
                    .displayItems((params, output) -> {
                        // Weapons
                        output.accept(ModItems.NORDIC_AXE.get());
                        output.accept(ModItems.NORDIC_AXE_2.get());
                        output.accept(ModItems.GRAVEBITE.get());
                        output.accept(ModItems.GIANT_RUNE_SWORD.get());
                        output.accept(ModItems.GIANT_RUNE_SWORD_2.get());

                        // Frost Portal item
                        output.accept(ModItems.FROST_FLINT_AND_STEEL.get());

                        // Armor
                        output.accept(ModItems.ICE_GIANT_HELMET.get());
                        output.accept(ModItems.ICE_GIANT_CHESTPLATE.get());
                        output.accept(ModItems.ICE_GIANT_LEGGINGS.get());
                        output.accept(ModItems.ICE_GIANT_BOOTS.get());

                        // Materials
                        output.accept(ModItems.RAW_ICE_GIANT_CORE.get());
                        output.accept(ModItems.ICE_GIANT_CORE.get());
                        output.accept(ModItems.MAGIC_ORB.get());
                        output.accept(ModItems.PURE_SOUL_ENERGY.get());

                        // Mobs
                        output.accept(ModItems.ICE_GIANT_SPAWN_EGG.get());
                        output.accept(ModItems.ICE_GIANT_NO_DASH_SPAWN_EGG.get().getDefaultInstance());
                        output.accept(ModItems.ICE_GIANT_ESCORT_SPAWN_EGG.get().getDefaultInstance());
                        output.accept(ModItems.GNOLL_MELEE_SPAWN_EGG.get().getDefaultInstance());
                        output.accept(ModItems.GNOLL_MELEE_NORMAL_SPAWN_EGG.get().getDefaultInstance());
                        output.accept(ModItems.GNOLL_MELEE_WARM_SPAWN_EGG.get().getDefaultInstance());
                        output.accept(ModItems.GNOLL_ARCHER_SPAWN_EGG.get().getDefaultInstance());
                        output.accept(ModItems.GNOLL_ARCHER_NORMAL_SPAWN_EGG.get().getDefaultInstance());
                        output.accept(ModItems.GNOLL_ARCHER_WARM_SPAWN_EGG.get().getDefaultInstance());
                        output.accept(ModItems.GNOLL_SHAMAN_SPAWN_EGG.get().getDefaultInstance());
                        output.accept(ModItems.GNOLL_SHAMAN_NORMAL_SPAWN_EGG.get().getDefaultInstance());
                        output.accept(ModItems.GNOLL_SHAMAN_WARM_SPAWN_EGG.get().getDefaultInstance());

                        output.accept(ModItems.FROST_GOAT_SPAWN_EGG.get());

                        // Tools
                        output.accept(ModItems.CHISEL.get());
                        output.accept(ModItems.IRON_HORSESHOE.get());
                        output.accept(ModItems.GOLDEN_HORSESHOE.get());
                        output.accept(ModItems.DIAMOND_HORSESHOE.get());
                        output.accept(ModItems.NETHERITE_HORSESHOE.get());

                        // Blocks (Frozen Realm trees)
                        output.accept(ModItems.PALE_OAK_WOOD.get());
                        output.accept(ModItems.PALE_OAK_PLANKS.get());
                        output.accept(ModItems.PALE_OAK_STAIRS.get());
                        output.accept(ModItems.PALE_OAK_SLAB.get());
                        output.accept(ModItems.PALE_OAK_FENCE.get());
                        output.accept(ModItems.PALE_OAK_FENCE_GATE.get());
                        output.accept(ModItems.PALE_OAK_TRAPDOOR.get());
                        output.accept(ModItems.TUFF_WALL.get());
                        output.accept(ModItems.TUFF_STAIRS.get());
                        output.accept(ModItems.TUFF_SLAB.get());
                        output.accept(ModItems.GLOW_ICE.get());
                        output.accept(ModItems.SNOWY_GRASS.get());
                        output.accept(ModItems.SNOWY_FERN.get());
                        output.accept(ModItems.SNOWY_TALL_GRASS.get());
                        output.accept(ModItems.SNOWY_LARGE_FERN.get());
                        output.accept(ModItems.SNOWY_SPRUCE_LEAVES.get());
                        output.accept(ModItems.SNOWY_BIRCH_LEAVES.get());
                        output.accept(ModItems.SNOWY_AZALEA_LEAVES.get());
                        output.accept(ModItems.ICICLE.get());
                        output.accept(ModItems.PACKED_ICE_STAIRS.get());
                        output.accept(ModItems.PACKED_ICE_SLAB.get());
                        output.accept(ModItems.PACKED_ICE_WALL.get());
                        output.accept(ModItems.PACKED_ICE_FENCE.get());
                        output.accept(ModItems.PACKED_ICE_PANE.get());
                        output.accept(ModItems.SNOW_STAIRS.get());
                        output.accept(ModItems.SNOW_SLAB.get());
                        output.accept(ModItems.SNOW_WALL.get());
                        output.accept(ModItems.SNOW_FENCE.get());
                        output.accept(ModItems.BONE_STAIRS.get());
                        output.accept(ModItems.BONE_SLAB.get());
                        output.accept(ModItems.BONE_WALL.get());
                        output.accept(ModItems.BONE_FENCE.get());
                        output.accept(ModItems.BONE_FENCE_GATE.get());
                        output.accept(ModItems.BONE_TRAPDOOR.get());
                        output.accept(ModItems.BONE_DOOR.get());
                        output.accept(ModItems.BONE_BUTTON.get());
                        output.accept(ModItems.BONE_PRESSURE_PLATE.get());
                        output.accept(ModItems.FROST_PORTAL.get());
                        output.accept(ModItems.DUNGEON_DOOR.get());
                        output.accept(ModItems.INVISIBLE_LIGHT.get());
                        output.accept(ModItems.CLOUD_WHITE.get());
                        output.accept(ModItems.CLOUD_VERY_LIGHT_GRAY.get());
                        output.accept(ModItems.CLOUD_LIGHT_GRAY.get());
                        output.accept(ModItems.CLOUD_GRAY.get());
                        output.accept(ModItems.CLOUD_DARK_GRAY.get());
                    })
                    .build());

    // vertical slabs (stone, sandstone, quartz, wood ...) get their own tab
    public static final RegistryObject<CreativeModeTab> VERTICAL_SLABS_TAB = CREATIVE_MODE_TABS.register("vertical_slabs",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.icegiant.vertical_slabs"))
                    .icon(() -> new ItemStack(ModVerticalSlabs.ALL.get(0).item().get()))
                    .withTabsBefore(ICE_GIANT_TAB.getKey())
                    .displayItems((params, output) -> {
                        for (ModVerticalSlabs.Entry e : ModVerticalSlabs.ALL) output.accept(e.item().get());
                    })
                    .build());

    private ModCreativeTabs() {
    }
}
