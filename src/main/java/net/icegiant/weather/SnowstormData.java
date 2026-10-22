package net.icegiant.weather;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * Saved state of the Frozen Realm's snowstorm cycle: is a storm raging, how long until the next
 * change, and where the wind blows from. Stored with the dimension (data/icegiant_snowstorm.dat).
 */
public class SnowstormData extends SavedData {

    public static final String NAME = "icegiant_snowstorm";

    // clear weather 5-15 min, storm 2-5 min
    static final int CLEAR_MIN = 5 * 60 * 20;
    static final int CLEAR_MAX = 15 * 60 * 20;
    static final int STORM_MIN = 2 * 60 * 20;
    static final int STORM_MAX = 5 * 60 * 20;

    boolean active;
    int ticksLeft = -1;   // -1 = not rolled yet
    float windYaw;        // degrees, direction the wind blows TO

    public static SnowstormData load(CompoundTag tag) {
        SnowstormData data = new SnowstormData();
        data.active = tag.getBoolean("Active");
        data.ticksLeft = tag.getInt("TicksLeft");
        data.windYaw = tag.getFloat("WindYaw");
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        tag.putBoolean("Active", active);
        tag.putInt("TicksLeft", ticksLeft);
        tag.putFloat("WindYaw", windYaw);
        return tag;
    }

    void startStorm(RandomSource random, int durationTicks) {
        active = true;
        ticksLeft = durationTicks > 0 ? durationTicks : STORM_MIN + random.nextInt(STORM_MAX - STORM_MIN + 1);
        windYaw = random.nextFloat() * 360.0F;
        setDirty();
    }

    void stopStorm(RandomSource random) {
        active = false;
        ticksLeft = CLEAR_MIN + random.nextInt(CLEAR_MAX - CLEAR_MIN + 1);
        setDirty();
    }
}
