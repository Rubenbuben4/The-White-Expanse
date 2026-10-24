package net.icegiant.registry;

import net.icegiant.IceGiantMod;
import net.icegiant.item.IceGiantArmorItem;
import net.icegiant.item.IceGiantArmorMaterial;
import net.icegiant.item.IceWeaponItem;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.Item;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ModItems {

    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, IceGiantMod.MOD_ID);

    // Ice-Giant-Rüstungsset - komplett GeckoLib (getragen + 3D im Inventar),
    // Modell aus Armor.gltf (Blockbench), siehe IceGiantArmorItem.
    public static final RegistryObject<Item> ICE_GIANT_HELMET = ITEMS.register("ice_giant_helmet",
            () -> new IceGiantArmorItem(IceGiantArmorMaterial.ICE_GIANT, ArmorItem.Type.HELMET, new Item.Properties()));
    public static final RegistryObject<Item> ICE_GIANT_CHESTPLATE = ITEMS.register("ice_giant_chestplate",
            () -> new IceGiantArmorItem(IceGiantArmorMaterial.ICE_GIANT, ArmorItem.Type.CHESTPLATE, new Item.Properties()));
    public static final RegistryObject<Item> ICE_GIANT_LEGGINGS = ITEMS.register("ice_giant_leggings",
            () -> new IceGiantArmorItem(IceGiantArmorMaterial.ICE_GIANT, ArmorItem.Type.LEGGINGS, new Item.Properties()));
    public static final RegistryObject<Item> ICE_GIANT_BOOTS = ITEMS.register("ice_giant_boots",
            () -> new IceGiantArmorItem(IceGiantArmorMaterial.ICE_GIANT, ArmorItem.Type.BOOTS, new Item.Properties()));

    // Roher (ungravierter) Eisgiganten-Kern - Drop vom Boss (loot_tables/entities/ice_giant.json)
    public static final RegistryObject<Item> RAW_ICE_GIANT_CORE = ITEMS.register("raw_ice_giant_core",
            () -> new Item(new Item.Properties()));

    // Gravierter Eisgiganten-Kern - Rezept: roher Kern + Meißel (recipes/ice_giant_core.json)
    public static final RegistryObject<Item> ICE_GIANT_CORE = ITEMS.register("ice_giant_core",
            () -> new Item(new Item.Properties()));

    // Magic Orb - Zutat für den Frost-Portal-Schlüssel (recipes/frost_flint_and_steel.json), droppt vom Gnoll-Schamanen
    public static final RegistryObject<Item> MAGIC_ORB = ITEMS.register("magic_orb",
            () -> new Item(new Item.Properties()));

    // Pure Soul Energy - Zutat für den Frost-Portal-Schlüssel (recipes/frost_flint_and_steel.json), 10 % von
    // Dorfbewohnern, die ein Spieler tötet (entity/VillagerSoulDrop)
    public static final RegistryObject<Item> PURE_SOUL_ENERGY = ITEMS.register("pure_soul_energy",
            () -> new Item(new Item.Properties()));

    // Frostziege (2026-10-23): Spawn-Ei und Hufeisen (schneller; Seelenläufer und Eisläufer möglich)
    public static final RegistryObject<Item> FROST_GOAT_SPAWN_EGG = ITEMS.register("frost_goat_spawn_egg",
            () -> new net.minecraftforge.common.ForgeSpawnEggItem(ModEntityTypes.FROST_GOAT, 0xE8EEF2, 0x7FA6C4,
                    new Item.Properties()));
    public static final RegistryObject<Item> IRON_HORSESHOE = ITEMS.register("iron_horseshoe",
            () -> new net.icegiant.item.HorseshoeItem(new Item.Properties(), 0.10D, 0.08D, 9));
    public static final RegistryObject<Item> GOLDEN_HORSESHOE = ITEMS.register("golden_horseshoe",
            () -> new net.icegiant.item.HorseshoeItem(new Item.Properties(), 0.15D, 0.12D, 25));
    public static final RegistryObject<Item> DIAMOND_HORSESHOE = ITEMS.register("diamond_horseshoe",
            () -> new net.icegiant.item.HorseshoeItem(new Item.Properties(), 0.20D, 0.15D, 10));
    public static final RegistryObject<Item> NETHERITE_HORSESHOE = ITEMS.register("netherite_horseshoe",
            () -> new net.icegiant.item.HorseshoeItem(new Item.Properties().fireResistant(), 0.30D, 0.20D, 15));

    // Spawn-Ei für den Eisriesen (Farben: Eisblau / dunkles Blau)
    public static final RegistryObject<Item> ICE_GIANT_SPAWN_EGG = ITEMS.register("ice_giant_spawn_egg",
            () -> new net.minecraftforge.common.ForgeSpawnEggItem(ModEntityTypes.ICE_GIANT, 0xA8DDF5, 0x2B4F7A,
                    new Item.Properties()));

    // Spawn-Ei für einen Eisriesen OHNE Sturmlauf (Farben: helleres Eis / Grau-Blau).
    // Gibt dem Riesen über das EntityTag CanDash:false mit (siehe IceGiantEntity.readAdditionalSaveData).
    public static final RegistryObject<Item> ICE_GIANT_NO_DASH_SPAWN_EGG = ITEMS.register("ice_giant_no_dash_spawn_egg",
            () -> new net.icegiant.item.NoDashSpawnEggItem(ModEntityTypes.ICE_GIANT, 0xE6F4FB, 0x6B7F96,
                    new Item.Properties()));

    // Spawn-Ei für einen Eisriesen MIT Gnoll-Gefolge (2-5 Gnolle); die anderen Riesen-Eier spawnen ihn allein.
    // Farben: Eisblau / Gnoll-Rot. Setzt das EntityTag SpawnEscort:true (siehe IceGiantEntity).
    public static final RegistryObject<Item> ICE_GIANT_ESCORT_SPAWN_EGG = ITEMS.register("ice_giant_escort_spawn_egg",
            () -> new net.icegiant.item.EscortSpawnEggItem(ModEntityTypes.ICE_GIANT, 0xA8DDF5, 0x7A2E2E,
                    new Item.Properties()));

    // Spawn-Eier der Gnolle (Diener der Eisriesen; Modelle von Hibiscus Studios x AnonymousDevs).
    // Je Typ drei Varianten: Schnee (ohne Zusatz), normal, warm - Grundfarbe = Fell, Punkte = Typ.
    public static final RegistryObject<Item> GNOLL_MELEE_SPAWN_EGG = ITEMS.register("gnoll_melee_spawn_egg",
            () -> new net.icegiant.item.GnollSpawnEggItem(ModEntityTypes.GNOLL_MELEE, 0, 0xD0E8EC, 0x7A2E2E, new Item.Properties()));
    public static final RegistryObject<Item> GNOLL_MELEE_NORMAL_SPAWN_EGG = ITEMS.register("gnoll_melee_normal_spawn_egg",
            () -> new net.icegiant.item.GnollSpawnEggItem(ModEntityTypes.GNOLL_MELEE, 1, 0x8A6444, 0x7A2E2E, new Item.Properties()));
    public static final RegistryObject<Item> GNOLL_MELEE_WARM_SPAWN_EGG = ITEMS.register("gnoll_melee_warm_spawn_egg",
            () -> new net.icegiant.item.GnollSpawnEggItem(ModEntityTypes.GNOLL_MELEE, 2, 0xD2AA82, 0x7A2E2E, new Item.Properties()));
    public static final RegistryObject<Item> GNOLL_ARCHER_SPAWN_EGG = ITEMS.register("gnoll_archer_spawn_egg",
            () -> new net.icegiant.item.GnollSpawnEggItem(ModEntityTypes.GNOLL_ARCHER, 0, 0xD0E8EC, 0x4F6B3A, new Item.Properties()));
    public static final RegistryObject<Item> GNOLL_ARCHER_NORMAL_SPAWN_EGG = ITEMS.register("gnoll_archer_normal_spawn_egg",
            () -> new net.icegiant.item.GnollSpawnEggItem(ModEntityTypes.GNOLL_ARCHER, 1, 0x8A6444, 0x4F6B3A, new Item.Properties()));
    public static final RegistryObject<Item> GNOLL_ARCHER_WARM_SPAWN_EGG = ITEMS.register("gnoll_archer_warm_spawn_egg",
            () -> new net.icegiant.item.GnollSpawnEggItem(ModEntityTypes.GNOLL_ARCHER, 2, 0xD2AA82, 0x4F6B3A, new Item.Properties()));
    public static final RegistryObject<Item> GNOLL_SHAMAN_SPAWN_EGG = ITEMS.register("gnoll_shaman_spawn_egg",
            () -> new net.icegiant.item.GnollSpawnEggItem(ModEntityTypes.GNOLL_SHAMAN, 0, 0xD0E8EC, 0x6E4A8E, new Item.Properties()));
    public static final RegistryObject<Item> GNOLL_SHAMAN_NORMAL_SPAWN_EGG = ITEMS.register("gnoll_shaman_normal_spawn_egg",
            () -> new net.icegiant.item.GnollSpawnEggItem(ModEntityTypes.GNOLL_SHAMAN, 1, 0x8A6444, 0x6E4A8E, new Item.Properties()));
    public static final RegistryObject<Item> GNOLL_SHAMAN_WARM_SPAWN_EGG = ITEMS.register("gnoll_shaman_warm_spawn_egg",
            () -> new net.icegiant.item.GnollSpawnEggItem(ModEntityTypes.GNOLL_SHAMAN, 2, 0xD2AA82, 0x6E4A8E, new Item.Properties()));

    // Meißel - Werkzeug zum Gravieren; bleibt beim Craften im Feld und verliert 1 Haltbarkeit
    public static final RegistryObject<Item> CHISEL = ITEMS.register("chisel",
            () -> new net.icegiant.item.ChiselItem(new Item.Properties().durability(64)));

    // Nordic Axe - jetzt mit GeckoLib-Schwing-Animation (deine "animation.nordic_axe.swing").
    // 12 Gesamt-Schaden, Angriffsgeschwindigkeit wie vanilla Äxte (-3.0).
    public static final RegistryObject<Item> NORDIC_AXE = ITEMS.register("nordic_axe",
            () -> new net.icegiant.item.IceGeoWeaponItem(new Item.Properties().stacksTo(1), 12.0D, -3.0D, "nordic_axe"));

    // Zweite Axt-Variante, gleiche Werte
    public static final RegistryObject<Item> NORDIC_AXE_2 = ITEMS.register("nordic_axe_2",
            () -> new IceWeaponItem(new Item.Properties().stacksTo(1), 12.0D, -3.0D));

    // "Frost-Portal-Schlüssel" (bis 2026-10-22 "Frost-Feuerstein"; die Item-ID bleibt, damit Welten ihn behalten)
    // - zündet einen Blue-Ice-Portalrahmen (wie ein Nether-Portal)
    // zur Frozen-Realm-Dimension. Einmal-Item: wird beim Anzünden verbraucht, stapelbar bis 16.
    public static final RegistryObject<Item> FROST_FLINT_AND_STEEL = ITEMS.register("frost_flint_and_steel",
            () -> new net.icegiant.item.FrostFlintAndSteelItem(new Item.Properties().stacksTo(16)));

    // Block items for the backported pale oak / tuff blocks (ModBlocks)
    // Frost portal as a placeable block (creative tab) - turned to face the player when placed
    public static final RegistryObject<Item> FROST_PORTAL = blockItem("frost_portal", ModBlocks.FROST_PORTAL);
    // dungeon door (3 x 6 blocks) - placed with its bottom middle on the clicked spot
    public static final RegistryObject<Item> DUNGEON_DOOR = blockItem("dungeon_door", ModBlocks.DUNGEON_DOOR);
    // invisible light - creative only (no recipe), like the barrier block
    public static final RegistryObject<Item> INVISIBLE_LIGHT = ITEMS.register("invisible_light",
            () -> new net.minecraft.world.item.BlockItem(ModBlocks.INVISIBLE_LIGHT.get(),
                    new Item.Properties().rarity(net.minecraft.world.item.Rarity.EPIC)));
    // bone building blocks (vanilla bone block texture)
    public static final RegistryObject<Item> BONE_STAIRS = blockItem("bone_stairs", ModBlocks.BONE_STAIRS);
    public static final RegistryObject<Item> BONE_SLAB = blockItem("bone_slab", ModBlocks.BONE_SLAB);
    public static final RegistryObject<Item> BONE_WALL = blockItem("bone_wall", ModBlocks.BONE_WALL);
    public static final RegistryObject<Item> BONE_FENCE = blockItem("bone_fence", ModBlocks.BONE_FENCE);
    public static final RegistryObject<Item> BONE_FENCE_GATE = blockItem("bone_fence_gate", ModBlocks.BONE_FENCE_GATE);
    public static final RegistryObject<Item> BONE_TRAPDOOR = blockItem("bone_trapdoor", ModBlocks.BONE_TRAPDOOR);
    public static final RegistryObject<Item> BONE_DOOR = ITEMS.register("bone_door",
            () -> new net.minecraft.world.item.DoubleHighBlockItem(ModBlocks.BONE_DOOR.get(), new Item.Properties()));
    public static final RegistryObject<Item> BONE_BUTTON = blockItem("bone_button", ModBlocks.BONE_BUTTON);
    public static final RegistryObject<Item> BONE_PRESSURE_PLATE = blockItem("bone_pressure_plate", ModBlocks.BONE_PRESSURE_PLATE);

    // snowy plants, leaves, icicles (ModBlocks)
    public static final RegistryObject<Item> SNOWY_GRASS = blockItem("snowy_grass", ModBlocks.SNOWY_GRASS);
    public static final RegistryObject<Item> SNOWY_FERN = blockItem("snowy_fern", ModBlocks.SNOWY_FERN);
    public static final RegistryObject<Item> SNOWY_TALL_GRASS = ITEMS.register("snowy_tall_grass",
            () -> new net.minecraft.world.item.DoubleHighBlockItem(ModBlocks.SNOWY_TALL_GRASS.get(), new Item.Properties()));
    public static final RegistryObject<Item> SNOWY_LARGE_FERN = ITEMS.register("snowy_large_fern",
            () -> new net.minecraft.world.item.DoubleHighBlockItem(ModBlocks.SNOWY_LARGE_FERN.get(), new Item.Properties()));
    public static final RegistryObject<Item> SNOWY_SPRUCE_LEAVES = blockItem("snowy_spruce_leaves", ModBlocks.SNOWY_SPRUCE_LEAVES);
    public static final RegistryObject<Item> SNOWY_BIRCH_LEAVES = blockItem("snowy_birch_leaves", ModBlocks.SNOWY_BIRCH_LEAVES);
    public static final RegistryObject<Item> SNOWY_AZALEA_LEAVES = blockItem("snowy_azalea_leaves", ModBlocks.SNOWY_AZALEA_LEAVES);
    public static final RegistryObject<Item> ICICLE = blockItem("icicle", ModBlocks.ICICLE);

    // Glimmeis (glow ice) from the Kristallspitzen biome
    public static final RegistryObject<Item> GLOW_ICE = blockItem("glow_ice", ModBlocks.GLOW_ICE);

    public static final RegistryObject<Item> PALE_OAK_WOOD = blockItem("pale_oak_wood", ModBlocks.PALE_OAK_WOOD);
    public static final RegistryObject<Item> PALE_OAK_FENCE_GATE = blockItem("pale_oak_fence_gate", ModBlocks.PALE_OAK_FENCE_GATE);
    public static final RegistryObject<Item> PALE_OAK_TRAPDOOR = blockItem("pale_oak_trapdoor", ModBlocks.PALE_OAK_TRAPDOOR);
    public static final RegistryObject<Item> PALE_OAK_PLANKS = blockItem("pale_oak_planks", ModBlocks.PALE_OAK_PLANKS);
    public static final RegistryObject<Item> PALE_OAK_STAIRS = blockItem("pale_oak_stairs", ModBlocks.PALE_OAK_STAIRS);
    public static final RegistryObject<Item> PALE_OAK_SLAB = blockItem("pale_oak_slab", ModBlocks.PALE_OAK_SLAB);
    public static final RegistryObject<Item> PALE_OAK_FENCE = blockItem("pale_oak_fence", ModBlocks.PALE_OAK_FENCE);
    public static final RegistryObject<Item> TUFF_WALL = blockItem("tuff_wall", ModBlocks.TUFF_WALL);
    public static final RegistryObject<Item> TUFF_SLAB = blockItem("tuff_slab", ModBlocks.TUFF_SLAB);
    public static final RegistryObject<Item> TUFF_STAIRS = blockItem("tuff_stairs", ModBlocks.TUFF_STAIRS);
    public static final RegistryObject<Item> PACKED_ICE_STAIRS = blockItem("packed_ice_stairs", ModBlocks.PACKED_ICE_STAIRS);
    public static final RegistryObject<Item> PACKED_ICE_SLAB = blockItem("packed_ice_slab", ModBlocks.PACKED_ICE_SLAB);
    public static final RegistryObject<Item> PACKED_ICE_WALL = blockItem("packed_ice_wall", ModBlocks.PACKED_ICE_WALL);
    public static final RegistryObject<Item> PACKED_ICE_FENCE = blockItem("packed_ice_fence", ModBlocks.PACKED_ICE_FENCE);
    public static final RegistryObject<Item> PACKED_ICE_PANE = blockItem("packed_ice_pane", ModBlocks.PACKED_ICE_PANE);
    // snow building blocks and cloud blocks (ModBlocks)
    public static final RegistryObject<Item> SNOW_STAIRS = blockItem("snow_stairs", ModBlocks.SNOW_STAIRS);
    public static final RegistryObject<Item> SNOW_SLAB = blockItem("snow_slab", ModBlocks.SNOW_SLAB);
    public static final RegistryObject<Item> SNOW_WALL = blockItem("snow_wall", ModBlocks.SNOW_WALL);
    public static final RegistryObject<Item> SNOW_FENCE = blockItem("snow_fence", ModBlocks.SNOW_FENCE);
    public static final RegistryObject<Item> CLOUD_WHITE = blockItem("cloud_white", ModBlocks.CLOUD_WHITE);
    public static final RegistryObject<Item> CLOUD_VERY_LIGHT_GRAY = blockItem("cloud_very_light_gray", ModBlocks.CLOUD_VERY_LIGHT_GRAY);
    public static final RegistryObject<Item> CLOUD_LIGHT_GRAY = blockItem("cloud_light_gray", ModBlocks.CLOUD_LIGHT_GRAY);
    public static final RegistryObject<Item> CLOUD_GRAY = blockItem("cloud_gray", ModBlocks.CLOUD_GRAY);
    public static final RegistryObject<Item> CLOUD_DARK_GRAY = blockItem("cloud_dark_gray", ModBlocks.CLOUD_DARK_GRAY);

    private static RegistryObject<Item> blockItem(String name, RegistryObject<net.minecraft.world.level.block.Block> block) {
        return ITEMS.register(name, () -> new net.minecraft.world.item.BlockItem(block.get(), new Item.Properties()));
    }

    // Gravebite - zweite GeckoLib-Axt mit exakt denselben Werten/Fähigkeiten
    // wie die Nordic Axe (Schwing-/Slam-Animation, Slam-Ability, Partikel,
    // Sounds - alles läuft generisch über IceGeoWeaponItem/"modelName").
    // Nur Modell, Textur und Animationsdatei unterscheiden sich
    // (geo/gravebite.geo.json, textures/item/gravebite.png,
    // animations/gravebite.animation.json).
    public static final RegistryObject<Item> GRAVEBITE = ITEMS.register("gravebite",
            () -> new net.icegiant.item.IceGeoWeaponItem(new Item.Properties().stacksTo(1), 12.0D, -3.0D, "gravebite"));

    /** Riesen-Runenschwert: all the axes' abilities plus ground cut (K) and wide sweep (N) - since 2026-10-17. */
    public static final RegistryObject<Item> GIANT_RUNE_SWORD = ITEMS.register("giant_rune_sword",
            () -> new net.icegiant.item.GiantRuneSwordItem(new Item.Properties().stacksTo(1), 12.0D, -3.0D));

    /** Riesen-Runenschwert 2.0: like the first, +4 hearts, hidden Fire Aspect IV + Smite VIII - since 2026-10-18. */
    public static final RegistryObject<Item> GIANT_RUNE_SWORD_2 = ITEMS.register("giant_rune_sword_2",
            () -> new net.icegiant.item.GiantRuneSword2Item(new Item.Properties().stacksTo(1), 20.0D, -3.0D));
}
