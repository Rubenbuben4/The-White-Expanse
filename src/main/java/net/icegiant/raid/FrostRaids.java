package net.icegiant.raid;

import net.icegiant.IceGiantMod;
import net.icegiant.dimension.ModDimensions;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;

/**
 * Frost Raids: villagers brought into the Frozen Realm are attacked by Gnolls with Ice Giants as their generals.
 * <ul>
 *   <li>A village is at least {@value #MIN_VILLAGERS} villagers with a bed (their home) close together near a
 *       player.</li>
 *   <li>Once a village is known, it is raided every {@value #RAID_INTERVAL_MINUTES} minutes of play (the Frozen
 *       Realm has no nights, so the clock is the game time) - only while a player is there.</li>
 *   <li>Not on Peaceful.</li>
 * </ul>
 * <b>Snowy villages in the Overworld</b> (a village whose middle lies in a biome cold enough for snow) are raided,
 * too - at night, at most every {@value #RAID_INTERVAL_MINUTES} minutes, the first time on the first night after the
 * village was found. There the attackers come through a frost rift (see FrostRaid).
 * The raid itself: FrostRaid; what the attackers do: RaidGoals.
 */
@Mod.EventBusSubscriber(modid = IceGiantMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class FrostRaids {

    // ---- TUNE ----
    static final int MIN_VILLAGERS = 3;
    static final int RAID_INTERVAL_MINUTES = 40;
    static final long RAID_INTERVAL = RAID_INTERVAL_MINUTES * 60L * 20L;
    static final double DETECT_RADIUS = 64.0D;        // villagers around a player that are looked at
    static final double SAME_VILLAGE = 64.0D;         // village centres closer than this are the same village
    static final double PLAYER_NEAR = 64.0D;          // a raid starts only with a player this close
    static final long OVERWORLD_FIRST_DELAY = 20L * 60L * 2L;   // a snowy village found: raided from the next night on

    private static final int STEP = 20;               // the raids are updated once a second
    private static final int DETECT_EVERY = 100;      // villages are looked for every 5 s

    private FrostRaids() {
    }

    @SubscribeEvent
    public static void onLevelTick(TickEvent.LevelTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.level instanceof ServerLevel level)) return;
        boolean realm = level.dimension() == ModDimensions.FROZEN_REALM;
        if (!realm && level.dimension() != net.minecraft.world.level.Level.OVERWORLD) return;
        long time = level.getGameTime();
        if (time % STEP != 0) return;
        FrostRaidData data = FrostRaidData.get(level);
        // running raids
        for (Iterator<FrostRaid> it = data.raids.iterator(); it.hasNext(); ) {
            FrostRaid raid = it.next();
            raid.tick(level, STEP);
            if (raid.done) it.remove();
            data.setDirty();
        }
        if (time % DETECT_EVERY != 0) return;
        if (level.getDifficulty() == Difficulty.PEACEFUL) return;
        for (ServerPlayer player : level.players()) {
            if (player.isSpectator()) continue;
            BlockPos center = villageNear(level, player);
            if (center == null) continue;
            if (!realm && !snowy(level, center)) continue;                     // Overworld: snowy villages only
            FrostRaidData.Village village = null;
            for (FrostRaidData.Village v : data.villages) {
                if (v.center.closerThan(center, SAME_VILLAGE)) village = v;
            }
            if (village == null) {                                             // a new village: first raid later
                data.villages.add(new FrostRaidData.Village(center, time + (realm ? RAID_INTERVAL : OVERWORLD_FIRST_DELAY)));
                data.setDirty();
                continue;
            }
            village.center = center;
            if (time < village.nextRaid || raidAt(data, center)) continue;
            if (!center.closerToCenterThan(player.position(), PLAYER_NEAR)) continue;
            if (!realm && !level.isNight()) continue;                           // Overworld: they come at night
            village.nextRaid = time + RAID_INTERVAL;
            data.raids.add(new FrostRaid(center, !realm));
            data.setDirty();
        }
    }

    private static boolean snowy(ServerLevel level, BlockPos pos) {
        return level.getBiome(pos).value().coldEnoughToSnow(pos);
    }

    private static boolean raidAt(FrostRaidData data, BlockPos center) {
        for (FrostRaid r : data.raids) if (!r.done && r.center.closerThan(center, SAME_VILLAGE)) return true;
        return false;
    }

    /** The middle of the homes of the villagers near this player, if there are enough of them. */
    private static BlockPos villageNear(ServerLevel level, ServerPlayer player) {
        List<BlockPos> homes = new ArrayList<>();
        for (Villager v : level.getEntitiesOfClass(Villager.class, player.getBoundingBox().inflate(DETECT_RADIUS), Villager::isAlive)) {
            Optional<GlobalPos> home = v.getBrain().getMemory(MemoryModuleType.HOME);
            if (home.isPresent() && home.get().dimension() == level.dimension()) homes.add(home.get().pos());
        }
        if (homes.size() < MIN_VILLAGERS) return null;
        long x = 0, y = 0, z = 0;
        for (BlockPos p : homes) {
            x += p.getX();
            y += p.getY();
            z += p.getZ();
        }
        return new BlockPos((int) (x / homes.size()), (int) (y / homes.size()), (int) (z / homes.size()));
    }

    // ---- for testing: /frostraid start | stop (operators, in the Frozen Realm) ----

    @SubscribeEvent
    public static void onCommands(net.minecraftforge.event.RegisterCommandsEvent event) {
        event.getDispatcher().register(net.minecraft.commands.Commands.literal("frostraid")
                .requires(src -> src.hasPermission(2))
                .then(net.minecraft.commands.Commands.literal("start").executes(ctx -> {
                    ServerPlayer player = ctx.getSource().getPlayerOrException();
                    if (!(player.level() instanceof ServerLevel level)
                            || (level.dimension() != ModDimensions.FROZEN_REALM && level.dimension() != net.minecraft.world.level.Level.OVERWORLD)) {
                        ctx.getSource().sendFailure(net.minecraft.network.chat.Component.translatable("command.icegiant.frostraid.realm_only"));
                        return 0;
                    }
                    BlockPos center = villageNear(level, player);
                    if (center == null) center = player.blockPosition();
                    FrostRaidData data = FrostRaidData.get(level);
                    if (raidAt(data, center)) {
                        ctx.getSource().sendFailure(net.minecraft.network.chat.Component.translatable("command.icegiant.frostraid.running"));
                        return 0;
                    }
                    data.raids.add(new FrostRaid(center, level.dimension() != ModDimensions.FROZEN_REALM));
                    data.setDirty();
                    BlockPos c = center;
                    ctx.getSource().sendSuccess(() -> net.minecraft.network.chat.Component.translatable(
                            "command.icegiant.frostraid.started", c.getX(), c.getY(), c.getZ()), true);
                    return 1;
                }))
                .then(net.minecraft.commands.Commands.literal("stop").executes(ctx -> {
                    int n = 0;
                    for (ServerLevel level : new ServerLevel[]{ctx.getSource().getServer().getLevel(ModDimensions.FROZEN_REALM),
                            ctx.getSource().getServer().overworld()}) {
                        if (level == null) continue;
                        FrostRaidData data = FrostRaidData.get(level);
                        for (FrostRaid r : data.raids) {
                            if (!r.done) n++;
                            r.end(level, null);
                        }
                        data.raids.clear();
                        data.setDirty();
                    }
                    int stopped = n;
                    ctx.getSource().sendSuccess(() -> net.minecraft.network.chat.Component.translatable(
                            "command.icegiant.frostraid.stopped", stopped), true);
                    return n;
                })));
    }

    /** Raiders loaded from disk get their raid goals back. */
    @SubscribeEvent
    public static void onJoin(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide() || !(event.getEntity() instanceof Mob mob)) return;
        if (RaidGoals.isRaider(mob)) RaidGoals.mark(mob, RaidGoals.center(mob));
    }

    /** A raider died or was removed for good (not just unloaded): its raid stops counting it as alive. */
    @SubscribeEvent
    public static void onLeave(net.minecraftforge.event.entity.EntityLeaveLevelEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level) || !(event.getEntity() instanceof Mob mob)) return;
        if (!RaidGoals.isRaider(mob)) return;
        net.minecraft.world.entity.Entity.RemovalReason reason = mob.getRemovalReason();
        if (reason == null || !reason.shouldDestroy()) return;            // unloaded or changed dimension: still out there
        for (FrostRaid raid : FrostRaidData.get(level).raids) {
            if (raid.raiders.contains(mob.getUUID())) raid.fallen.add(mob.getUUID());
        }
    }

    @SubscribeEvent
    public static void onStopping(ServerStoppingEvent event) {
        for (ServerLevel level : new ServerLevel[]{event.getServer().getLevel(ModDimensions.FROZEN_REALM), event.getServer().overworld()}) {
            if (level == null) continue;
            for (FrostRaid r : FrostRaidData.get(level).raids) r.removeBar();
        }
    }
}
