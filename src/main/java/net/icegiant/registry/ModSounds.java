package net.icegiant.registry;

import net.icegiant.IceGiantMod;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ModSounds {

    public static final DeferredRegister<SoundEvent> SOUND_EVENTS =
            DeferredRegister.create(ForgeRegistries.SOUND_EVENTS, IceGiantMod.MOD_ID);

    // Ice Giant uses deep zombie sounds for now (IceGiantEntity) - own sound events
    // come back here together with real .ogg files.
    public static final RegistryObject<SoundEvent> NORDIC_AXE_SWOOSH = registerSound("nordic_axe_swoosh");
    public static final RegistryObject<SoundEvent> NORDIC_AXE_SLAM_IMPACT = registerSound("nordic_axe_slam_impact");

    // Giant Rune Sword (2026-10-17, synthesised by tools/make_sword_sounds.py): wide sweep / ground cut swoosh, cut through the ground
    public static final RegistryObject<SoundEvent> RUNE_SWORD_SLASH = registerSound("giant_rune_sword.slash");
    public static final RegistryObject<SoundEvent> RUNE_SWORD_GROUND_CUT = registerSound("giant_rune_sword.ground_cut");

    // Frozen Realm snowstorm (SnowstormClient): two looping wind beds + random gusts.
    // Recordings by Dragon Studio (Pixabay), see CREDITS.md. Distinct IDs/folder (frozen_realm_storm/) so no
    // other mod or resource pack can define the same sound name (user heard a smithing sound in the storm).
    public static final RegistryObject<SoundEvent> SNOWSTORM_WIND = registerSound("frozen_realm.storm_wind");
    public static final RegistryObject<SoundEvent> SNOWSTORM_WIND_LOW = registerSound("frozen_realm.storm_wind_low");
    public static final RegistryObject<SoundEvent> SNOWSTORM_GUST = registerSound("frozen_realm.storm_gust");

    // Heartbeat the victim hears when knocked down (thrown by a giant, trampled by a dash) -
    // Dragon Studio (Pixabay), see CREDITS.md; played by effect/HeartbeatEvents
    public static final RegistryObject<SoundEvent> HEARTBEAT = registerSound("player.heartbeat");

    // Ice Giant running stomps (charging / dashing through walls) - freesound_community (Pixabay),
    // "Giant Walking Fast", cut into 6 single steps; played by IceGiantEntity.playStepSound
    public static final RegistryObject<SoundEvent> ICE_GIANT_RUN_STEP = registerSound("ice_giant.run_step");
    // Ice Giant walking steps (no target, not dashing) - "Giant footsteps Sound Effect" (Sound Effect Database),
    // cut into 6 single steps; played by IceGiantEntity.playStepSound
    public static final RegistryObject<SoundEvent> ICE_GIANT_WALK_STEP = registerSound("ice_giant.walk_step");
    // Ice Giant pain sounds - "Pain sounds for giant" (video from the user), 8 single sounds
    public static final RegistryObject<SoundEvent> ICE_GIANT_HURT = registerSound("ice_giant.hurt");
    // Ice Giant war cry (first sight of an enemy / when attacked, 20 s cooldown) - "Epic Dragon Roar"
    // (#364481) by Dragon Studio (Pixabay), mono; played by IceGiantEntity.tickRoar
    public static final RegistryObject<SoundEvent> ICE_GIANT_ROAR = registerSound("ice_giant.roar");
    // death: painful cry (the giant's own pain voice, deeper and slower) and the thump of hitting the ground
    public static final RegistryObject<SoundEvent> ICE_GIANT_DEATH = registerSound("ice_giant.death");
    public static final RegistryObject<SoundEvent> ICE_GIANT_FALL = registerSound("ice_giant.fall");
    // Schöpfungskristalle (2026-10-25/26, Rubenbuben4's picks from Pixabay, mono so they fade with distance)
    public static final RegistryObject<SoundEvent> CREATION_CRYSTAL_AWAKEN = registerSound("creation_crystal.awaken");
    public static final RegistryObject<SoundEvent> CREATION_CRYSTAL_ASLEEP = registerSound("creation_crystal.asleep");
    public static final RegistryObject<SoundEvent> CREATION_CRYSTAL_AMBIENT = registerSound("creation_crystal.ambient");
    // Slimes crawling over the ground (2026-10-27, Rubenbuben4's "Slime moving" recording)
    public static final RegistryObject<SoundEvent> SLIME_CRAWL = registerSound("slime.crawl");

    // Gnolls - sounds from the "Gnolls" entity pack by Hibiscus Studios x AnonymousDevs (used with permission)
    public static final RegistryObject<SoundEvent> GNOLL_IDLE = registerSound("gnoll.idle");
    // war horn a Gnoll troop WITHOUT a giant blows when it first spots a player ("Dark War Horn", see CREDITS.md)
    public static final RegistryObject<SoundEvent> GNOLL_WAR_HORN = registerSound("gnoll.war_horn");
    public static final RegistryObject<SoundEvent> GNOLL_HURT = registerSound("gnoll.hurt");
    public static final RegistryObject<SoundEvent> GNOLL_DEATH = registerSound("gnoll.death");
    public static final RegistryObject<SoundEvent> GNOLL_DEATH_BIG = registerSound("gnoll.death_big");
    public static final RegistryObject<SoundEvent> GNOLL_ATTACK = registerSound("gnoll.attack");
    public static final RegistryObject<SoundEvent> GNOLL_PLAYER_HIT = registerSound("gnoll.player_hit");
    public static final RegistryObject<SoundEvent> GNOLL_BITE = registerSound("gnoll.bite");
    public static final RegistryObject<SoundEvent> GNOLL_MASSACRE = registerSound("gnoll.massacre");
    public static final RegistryObject<SoundEvent> GNOLL_ARCHER_SHOOT = registerSound("gnoll.archer.shoot");
    public static final RegistryObject<SoundEvent> GNOLL_ARCHER_JUMP = registerSound("gnoll.archer.jump");
    public static final RegistryObject<SoundEvent> GNOLL_SHAMAN_SPIRITS = registerSound("gnoll.shaman.spirits");
    public static final RegistryObject<SoundEvent> GNOLL_SHAMAN_SPIRITS_HEAL = registerSound("gnoll.shaman.spirits_heal");
    public static final RegistryObject<SoundEvent> GNOLL_SHAMAN_SPIRITS_HIT = registerSound("gnoll.shaman.spirits_hit");
    public static final RegistryObject<SoundEvent> GNOLL_SHAMAN_CIRCLE = registerSound("gnoll.shaman.circle");
    public static final RegistryObject<SoundEvent> GNOLL_SHAMAN_CIRCLE_APPLY = registerSound("gnoll.shaman.circle_apply");

    private static RegistryObject<SoundEvent> registerSound(String name) {
        ResourceLocation id = new ResourceLocation(IceGiantMod.MOD_ID, name);
        return SOUND_EVENTS.register(name, () -> SoundEvent.createVariableRangeEvent(id));
    }
}
