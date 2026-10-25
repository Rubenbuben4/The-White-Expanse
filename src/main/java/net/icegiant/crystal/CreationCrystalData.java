package net.icegiant.crystal;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.saveddata.SavedData;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * Per dimension: the areas the Schöpfungskristalle (creation crystals) have changed the biome of.
 * <p>
 * {@code active}: crystals that are switched on (centre, radius, biome). {@code reverts}: areas of crystals that were
 * switched off or broken - there the natural biome is put back. Both are kept, because only loaded chunks can be
 * changed at once; every chunk that loads later is brought in line with them (CreationCrystals).
 */
public class CreationCrystalData extends SavedData {

    public static final String NAME = "icegiant_creation_crystals";
    /** Oldest revert areas are forgotten beyond this many (they are only needed until those chunks were loaded). */
    static final int MAX_REVERTS = 256;

    /** A cylinder: centre, radius (x/z), half height (y), and the biome (null for a revert area). */
    public record Zone(BlockPos center, int radius, int halfHeight, @Nullable ResourceLocation biome) {
        boolean contains(int x, int y, int z) {
            int dx = x - this.center.getX(), dz = z - this.center.getZ();
            return Math.abs(y - this.center.getY()) <= this.halfHeight && (long) dx * dx + (long) dz * dz <= (long) this.radius * this.radius;
        }

        boolean touchesChunk(int cx, int cz) {
            int x0 = cx << 4, z0 = cz << 4;
            int nx = Math.max(x0, Math.min(this.center.getX(), x0 + 15)) - this.center.getX();
            int nz = Math.max(z0, Math.min(this.center.getZ(), z0 + 15)) - this.center.getZ();
            return (long) nx * nx + (long) nz * nz <= (long) this.radius * this.radius;
        }

        CompoundTag save() {
            CompoundTag t = new CompoundTag();
            t.putLong("Pos", this.center.asLong());
            t.putInt("Radius", this.radius);
            t.putInt("HalfHeight", this.halfHeight);
            if (this.biome != null) t.putString("Biome", this.biome.toString());
            return t;
        }

        static Zone load(CompoundTag t) {
            ResourceLocation b = t.contains("Biome") ? ResourceLocation.tryParse(t.getString("Biome")) : null;
            return new Zone(BlockPos.of(t.getLong("Pos")), t.getInt("Radius"), t.getInt("HalfHeight"), b);
        }
    }

    final List<Zone> active = new ArrayList<>();
    final List<Zone> reverts = new ArrayList<>();

    public static CreationCrystalData load(CompoundTag tag) {
        CreationCrystalData data = new CreationCrystalData();
        for (Tag t : tag.getList("Active", Tag.TAG_COMPOUND)) {
            Zone z = Zone.load((CompoundTag) t);
            if (z.biome() != null) data.active.add(z);
        }
        for (Tag t : tag.getList("Reverts", Tag.TAG_COMPOUND)) data.reverts.add(Zone.load((CompoundTag) t));
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag a = new ListTag();
        for (Zone z : this.active) a.add(z.save());
        ListTag r = new ListTag();
        for (Zone z : this.reverts) r.add(z.save());
        tag.put("Active", a);
        tag.put("Reverts", r);
        return tag;
    }

    /** Switches a crystal on (or changes its biome). */
    void activate(Zone zone) {
        this.active.removeIf(z -> z.center().equals(zone.center()));
        this.active.add(zone);
        setDirty();
    }

    /** Switches the crystal at this centre off; its area becomes a revert area. Returns the old zone, or null. */
    @Nullable
    Zone deactivate(BlockPos center) {
        Zone old = null;
        for (Zone z : this.active) {
            if (z.center().equals(center)) old = z;
        }
        if (old == null) return null;
        this.active.remove(old);
        this.reverts.add(new Zone(old.center(), old.radius(), old.halfHeight(), null));
        while (this.reverts.size() > MAX_REVERTS) this.reverts.remove(0);
        setDirty();
        return old;
    }

    @Nullable
    Zone activeAt(BlockPos center) {
        for (Zone z : this.active) {
            if (z.center().equals(center)) return z;
        }
        return null;
    }

    boolean touchesChunk(int cx, int cz) {
        for (Zone z : this.active) if (z.touchesChunk(cx, cz)) return true;
        for (Zone z : this.reverts) if (z.touchesChunk(cx, cz)) return true;
        return false;
    }
}
