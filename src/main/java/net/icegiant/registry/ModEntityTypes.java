package net.icegiant.registry;

import net.icegiant.IceGiantMod;
import net.icegiant.entity.IceGiantEntity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ModEntityTypes {

    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES =
            DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, IceGiantMod.MOD_ID);

    // Eisriese: Spielermodell, um IceGiantEntity.SCALE vergrößert (wie der
    // vanilla Riesen-Zombie) - die Hitbox wächst mit (Spieler 0.6 x 1.8 * SCALE).
    public static final RegistryObject<EntityType<IceGiantEntity>> ICE_GIANT =
            ENTITY_TYPES.register("ice_giant", () -> EntityType.Builder.of(IceGiantEntity::new, MobCategory.MONSTER)
                    .sized(0.6f * IceGiantEntity.SCALE, 1.8f * IceGiantEntity.SCALE)
                    .clientTrackingRange(10)
                    .build("ice_giant"));

    // Frostziege (2026-10-23): Reittier des Frozen Realm, Ziegen-Hitbox 0.9 x 1.3 mal FrostGoatEntity.SCALE
    public static final RegistryObject<EntityType<net.icegiant.entity.FrostGoatEntity>> FROST_GOAT =
            ENTITY_TYPES.register("frost_goat", () -> EntityType.Builder
                    .<net.icegiant.entity.FrostGoatEntity>of(net.icegiant.entity.FrostGoatEntity::new, MobCategory.CREATURE)
                    .sized(0.9f * net.icegiant.entity.FrostGoatEntity.SCALE, 1.3f * net.icegiant.entity.FrostGoatEntity.SCALE)
                    .clientTrackingRange(10)
                    .build("frost_goat"));

    // Block, der beim Sturmlauf aus der Wand geschleudert wird (fliegt taumelnd, landet als Block)
    public static final RegistryObject<EntityType<net.icegiant.entity.FlungBlockEntity>> FLUNG_BLOCK =
            ENTITY_TYPES.register("flung_block", () -> EntityType.Builder
                    .<net.icegiant.entity.FlungBlockEntity>of(net.icegiant.entity.FlungBlockEntity::new, MobCategory.MISC)
                    .sized(0.98f, 0.98f)
                    .clientTrackingRange(10)
                    .updateInterval(20)
                    .build("flung_block"));

    // ---- Gnolls: the Ice Giants' minions (models/animations/sounds by Hibiscus Studios x
    // AnonymousDevs, "Gnolls" entity pack, used with permission - see CREDITS.md) ----
    public static final RegistryObject<EntityType<net.icegiant.entity.gnoll.GnollMeleeEntity>> GNOLL_MELEE =
            ENTITY_TYPES.register("gnoll_melee", () -> EntityType.Builder
                    .<net.icegiant.entity.gnoll.GnollMeleeEntity>of(net.icegiant.entity.gnoll.GnollMeleeEntity::new, MobCategory.MONSTER)
                    .sized(0.6f, 1.65f).clientTrackingRange(8).build("gnoll_melee"));
    public static final RegistryObject<EntityType<net.icegiant.entity.gnoll.GnollArcherEntity>> GNOLL_ARCHER =
            ENTITY_TYPES.register("gnoll_archer", () -> EntityType.Builder
                    .<net.icegiant.entity.gnoll.GnollArcherEntity>of(net.icegiant.entity.gnoll.GnollArcherEntity::new, MobCategory.MONSTER)
                    .sized(0.6f, 1.65f).clientTrackingRange(8).build("gnoll_archer"));
    public static final RegistryObject<EntityType<net.icegiant.entity.gnoll.GnollShamanEntity>> GNOLL_SHAMAN =
            ENTITY_TYPES.register("gnoll_shaman", () -> EntityType.Builder
                    .<net.icegiant.entity.gnoll.GnollShamanEntity>of(net.icegiant.entity.gnoll.GnollShamanEntity::new, MobCategory.MONSTER)
                    .sized(0.6f, 1.65f).clientTrackingRange(8).build("gnoll_shaman"));
    // shaman spells: homing spirit (particles only) and the cursed circle on the ground
    public static final RegistryObject<EntityType<net.icegiant.entity.gnoll.GnollSpiritEntity>> GNOLL_SPIRIT =
            ENTITY_TYPES.register("gnoll_spirit", () -> EntityType.Builder
                    .<net.icegiant.entity.gnoll.GnollSpiritEntity>of(net.icegiant.entity.gnoll.GnollSpiritEntity::new, MobCategory.MISC)
                    .sized(0.3f, 0.3f).clientTrackingRange(8).updateInterval(2).noSave().build("gnoll_spirit"));
    public static final RegistryObject<EntityType<net.icegiant.entity.gnoll.GnollCircleEntity>> GNOLL_CIRCLE =
            ENTITY_TYPES.register("gnoll_circle", () -> EntityType.Builder
                    .<net.icegiant.entity.gnoll.GnollCircleEntity>of(net.icegiant.entity.gnoll.GnollCircleEntity::new, MobCategory.MISC)
                    .sized(1.0f, 0.2f).clientTrackingRange(8).updateInterval(20).noSave().build("gnoll_circle"));
}
