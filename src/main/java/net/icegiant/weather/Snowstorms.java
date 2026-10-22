package net.icegiant.weather;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.icegiant.IceGiantMod;
import net.icegiant.dimension.ModDimensions;
import net.icegiant.network.NetworkHandler;
import net.icegiant.network.SnowstormPacket;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SnowLayerBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;

/**
 * Snowstorms in the Frozen Realm (server side). Storms come and go on their own timer
 * (clear 5-15 min, storm 2-5 min), independent of the overworld's weather.
 * During a storm:
 *  - every player in the dimension gets the storm visuals (SnowstormClient: heavy snowfall,
 *    white fog, wind-blown snow, wind sound)
 *  - players standing under the open sky freeze (frost overlay + slower walking, like powder
 *    snow) and, once fully frozen, take freezing damage (1 heart every 2 s). Warm armor protects:
 *    every worn piece in the item tag icegiant:warm_armor slows the freezing by a quarter,
 *    a full warm set (4 pieces) stops it completely. Shelter (a real roof over your head - not leaves,
 *    snow layers, fences, walls, panes ..., see StormShelter) thaws you.
 *  - snow piles up around players (up to the snowAccumulationHeight game rule + 1 layer)
 * Commands: /weather snowstorm [seconds] | /weather snowstorm stop; /weather clear also ends it.
 * Old form still works: /snowstorm start [seconds] | stop | (no argument = status).
 * Own implementation - no code from other mods.
 */
@Mod.EventBusSubscriber(modid = IceGiantMod.MOD_ID)
public final class Snowstorms {

    /** Item tag of armor that keeps you warm in a snowstorm (add new warm armor here). */
    public static final net.minecraft.tags.TagKey<net.minecraft.world.item.Item> WARM_ARMOR =
            net.minecraft.tags.ItemTags.create(new net.minecraft.resources.ResourceLocation(IceGiantMod.MOD_ID, "warm_armor"));
    /** Vanilla thaws 2 frost per tick outside powder snow; we add those 2 back plus the storm's cold. */
    private static final int VANILLA_THAW_PER_TICK = 2;
    /** A little over "fully frozen" (140) so the vanilla thaw can't drop it below the damage threshold. */
    private static final int FROST_OVERSHOOT = 4;
    private static final int DAMAGE_INTERVAL_TICKS = 40;  // 1 damage every 2 s once fully frozen (like powder snow)
    private static final float FREEZE_DAMAGE = 1.0F;
    private static final int SNOW_ATTEMPTS_PER_PLAYER = 3;
    private static final int SNOW_RADIUS = 32;

    private Snowstorms() {
    }

    public static SnowstormData data(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(SnowstormData::load, SnowstormData::new, SnowstormData.NAME);
    }

    public static boolean isStorming(ServerLevel level) {
        return level.dimension() == ModDimensions.FROZEN_REALM && data(level).active;
    }

    @SubscribeEvent
    public static void onLevelTick(TickEvent.LevelTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.level.isClientSide()) return;
        if (!(event.level instanceof ServerLevel level) || level.dimension() != ModDimensions.FROZEN_REALM) return;

        SnowstormData data = data(level);
        RandomSource random = level.random;
        if (data.ticksLeft < 0) data.stopStorm(random); // first time: start with clear weather
        if (--data.ticksLeft <= 0) {
            if (data.active) data.stopStorm(random);
            else data.startStorm(random, 0);
            broadcast(level, data, true);
        }
        if (data.ticksLeft % 200 == 0) data.setDirty();
        if (!data.active) return;

        for (ServerPlayer player : level.players()) {
            chill(level, player);
            for (int i = 0; i < SNOW_ATTEMPTS_PER_PLAYER; i++) pileSnow(level, player, random);
        }
    }

    /** Number of worn armor pieces that count as warm (0-4). */
    public static int warmPieces(ServerPlayer player) {
        int warm = 0;
        for (net.minecraft.world.item.ItemStack stack : player.getArmorSlots()) {
            if (stack.is(WARM_ARMOR)) warm++;
        }
        return warm;
    }

    private static void chill(ServerLevel level, ServerPlayer player) {
        if (player.isCreative() || player.isSpectator()) return;
        if (StormShelter.isSheltered(level, BlockPos.containing(player.getEyePosition()))) return; // real roof -> thaws
        int missing = 4 - warmPieces(player);
        if (missing <= 0) return; // full warm set
        // net freezing speed: missing/4 frost per tick -> no warm armor: fully frozen after ~7 s
        int frost = player.getTicksFrozen();
        int add = VANILLA_THAW_PER_TICK + (level.random.nextInt(4) < missing ? 1 : 0);
        int max = player.getTicksRequiredToFreeze() + FROST_OVERSHOOT;
        if (frost < max) player.setTicksFrozen(Math.min(max, frost + add));

        // damage once fully frozen. Vanilla already does this for players who "can freeze";
        // players wearing a freeze-immune piece (e.g. one leather boot) need it from us.
        if (player.isFullyFrozen() && !player.canFreeze() && level.getGameTime() % DAMAGE_INTERVAL_TICKS == 0) {
            player.hurt(level.damageSources().freeze(), FREEZE_DAMAGE);
        }
    }

    private static void pileSnow(ServerLevel level, ServerPlayer player, RandomSource random) {
        int limit = level.getGameRules().getInt(GameRules.RULE_SNOW_ACCUMULATION_HEIGHT);
        if (limit <= 0) return;
        limit = Math.min(8, limit + 1); // storms leave a little more snow than normal snowfall
        int x = player.getBlockX() + random.nextInt(SNOW_RADIUS * 2 + 1) - SNOW_RADIUS;
        int z = player.getBlockZ() + random.nextInt(SNOW_RADIUS * 2 + 1) - SNOW_RADIUS;
        if (!level.hasChunkAt(new BlockPos(x, 0, z))) return;
        BlockPos top = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING, new BlockPos(x, 0, z));
        Biome biome = level.getBiome(top).value();
        if (!biome.shouldSnow(level, top)) return; // too warm, too bright, not air/snow, or can't hold snow
        BlockState state = level.getBlockState(top);
        if (state.is(Blocks.SNOW)) {
            int layers = state.getValue(SnowLayerBlock.LAYERS);
            if (layers < limit) level.setBlockAndUpdate(top, state.setValue(SnowLayerBlock.LAYERS, layers + 1));
        } else {
            level.setBlockAndUpdate(top, Blocks.SNOW.defaultBlockState());
        }
    }

    // ---- sync with clients ----

    private static void broadcast(ServerLevel level, SnowstormData data, boolean announce) {
        SnowstormPacket packet = new SnowstormPacket(data.active, data.windYaw);
        for (ServerPlayer player : level.players()) {
            NetworkHandler.INSTANCE.send(PacketDistributor.PLAYER.with(() -> player), packet);
            if (announce) {
                player.displayClientMessage(Component.translatable(data.active
                        ? "message.icegiant.snowstorm_start" : "message.icegiant.snowstorm_end"), true);
            }
        }
    }

    private static void syncTo(ServerPlayer player) {
        boolean storm = false;
        float wind = 0.0F;
        if (player.level() instanceof ServerLevel level && level.dimension() == ModDimensions.FROZEN_REALM) {
            SnowstormData data = data(level);
            storm = data.active;
            wind = data.windYaw;
        }
        NetworkHandler.INSTANCE.send(PacketDistributor.PLAYER.with(() -> player), new SnowstormPacket(storm, wind));
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) syncTo(player);
    }

    @SubscribeEvent
    public static void onChangeDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) syncTo(player);
    }

    @SubscribeEvent
    public static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) syncTo(player);
    }

    // ---- /snowstorm command (operators) ----

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("snowstorm")
                .requires(source -> source.hasPermission(2))
                .executes(ctx -> status(ctx.getSource()))
                .then(Commands.literal("start")
                        .executes(ctx -> start(ctx.getSource(), 0))
                        .then(Commands.argument("seconds", IntegerArgumentType.integer(10, 3600))
                                .executes(ctx -> start(ctx.getSource(),
                                        IntegerArgumentType.getInteger(ctx, "seconds") * 20))))
                .then(Commands.literal("stop")
                        .executes(ctx -> stop(ctx.getSource()))));

        // "/weather snowstorm [seconds]" and "/weather snowstorm stop" - added to vanilla's /weather
        // (Brigadier merges this "weather" node into the existing one; vanilla's options stay as they are)
        event.getDispatcher().register(Commands.literal("weather")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("snowstorm")
                        .executes(ctx -> start(ctx.getSource(), 0))
                        .then(Commands.literal("stop")
                                .executes(ctx -> stop(ctx.getSource())))
                        .then(Commands.argument("seconds", IntegerArgumentType.integer(10, 3600))
                                .executes(ctx -> start(ctx.getSource(),
                                        IntegerArgumentType.getInteger(ctx, "seconds") * 20)))));
    }

    /** "/weather clear" also ends a snowstorm in the Frozen Realm (vanilla's clear runs as normal). */
    @SubscribeEvent
    public static void onCommand(net.minecraftforge.event.CommandEvent event) {
        var parse = event.getParseResults();
        if (parse.getContext().getCommand() == null || !parse.getExceptions().isEmpty()) return;
        String input = parse.getReader().getString().trim();
        if (input.startsWith("/")) input = input.substring(1);
        if (!input.equals("weather clear") && !input.startsWith("weather clear ")) return;
        ServerLevel level = parse.getContext().getSource().getServer().getLevel(ModDimensions.FROZEN_REALM);
        if (level == null) return;
        SnowstormData data = data(level);
        if (data.active) {
            data.stopStorm(level.random);
            broadcast(level, data, true);
        }
    }

    private static ServerLevel frozenRealm(CommandSourceStack source) {
        return source.getServer().getLevel(ModDimensions.FROZEN_REALM);
    }

    private static int status(CommandSourceStack source) {
        ServerLevel level = frozenRealm(source);
        if (level == null) return 0;
        SnowstormData data = data(level);
        int seconds = Math.max(0, data.ticksLeft / 20);
        source.sendSuccess(() -> Component.translatable(data.active
                ? "commands.icegiant.snowstorm.status_storm" : "commands.icegiant.snowstorm.status_clear", seconds), false);
        return 1;
    }

    private static int start(CommandSourceStack source, int ticks) {
        ServerLevel level = frozenRealm(source);
        if (level == null) return 0;
        SnowstormData data = data(level);
        data.startStorm(level.random, ticks);
        broadcast(level, data, true);
        source.sendSuccess(() -> Component.translatable("commands.icegiant.snowstorm.started"), true);
        return 1;
    }

    private static int stop(CommandSourceStack source) {
        ServerLevel level = frozenRealm(source);
        if (level == null) return 0;
        SnowstormData data = data(level);
        data.stopStorm(level.random);
        broadcast(level, data, true);
        source.sendSuccess(() -> Component.translatable("commands.icegiant.snowstorm.stopped"), true);
        return 1;
    }
}
