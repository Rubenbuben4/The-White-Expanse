package net.icegiant.raid;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.ArrayList;
import java.util.List;

/** Saved with the Frozen Realm: the villages known there (and when each is raided next) and the raids going on. */
public class FrostRaidData extends SavedData {

    private static final String NAME = "icegiant_frost_raids";

    /** A village: where it is and the game time of its next raid. */
    static final class Village {
        BlockPos center;
        long nextRaid;

        Village(BlockPos center, long nextRaid) {
            this.center = center;
            this.nextRaid = nextRaid;
        }
    }

    final List<Village> villages = new ArrayList<>();
    final List<FrostRaid> raids = new ArrayList<>();

    static FrostRaidData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(FrostRaidData::load, FrostRaidData::new, NAME);
    }

    static FrostRaidData load(CompoundTag tag) {
        FrostRaidData d = new FrostRaidData();
        for (Tag t : tag.getList("Villages", Tag.TAG_COMPOUND)) {
            CompoundTag c = (CompoundTag) t;
            d.villages.add(new Village(NbtUtils.readBlockPos(c.getCompound("Center")), c.getLong("NextRaid")));
        }
        for (Tag t : tag.getList("Raids", Tag.TAG_COMPOUND)) d.raids.add(FrostRaid.load((CompoundTag) t));
        return d;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag vs = new ListTag();
        for (Village v : this.villages) {
            CompoundTag c = new CompoundTag();
            c.put("Center", NbtUtils.writeBlockPos(v.center));
            c.putLong("NextRaid", v.nextRaid);
            vs.add(c);
        }
        tag.put("Villages", vs);
        ListTag rs = new ListTag();
        for (FrostRaid r : this.raids) if (!r.done) rs.add(r.save());
        tag.put("Raids", rs);
        return tag;
    }
}
