package net.icegiant.client;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.icegiant.IceGiantMod;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

import javax.annotation.Nullable;
import java.io.Reader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Plays a Blockbench / Bedrock animation file (format_version 1.8.0, as exported by Blockbench) on
 * the parts of a vanilla model - used for the Ice Giant, whose model is the plain player model.
 * <p>
 * The file is read once from the resource pack (assets/icegiant/animations/...json), so a new export
 * from Blockbench can simply replace it. Values are offsets on top of the normal pose, like vanilla's
 * own keyframe animations: rotation in degrees (same signs as Blockbench), position in pixels
 * (y flipped for Java models), scale as a factor. Linear and catmull-rom keyframes are supported.
 */
public final class BedrockPoseAnimation {

    private static final org.slf4j.Logger LOGGER = com.mojang.logging.LogUtils.getLogger();

    /** One animated value (rotation/position/scale) of one bone. */
    private record Track(float[] times, float[][] values, boolean[] smooth) {
        float[] sample(float t) {
            int n = this.times.length;
            if (n == 1 || t <= this.times[0]) return this.values[0];
            if (t >= this.times[n - 1]) return this.values[n - 1];
            int i = 0;
            while (i < n - 2 && t >= this.times[i + 1]) i++;
            float span = this.times[i + 1] - this.times[i];
            float f = span <= 0 ? 1 : (t - this.times[i]) / span;
            float[] out = new float[3];
            boolean cm = this.smooth[i] || this.smooth[i + 1];
            for (int k = 0; k < 3; k++) {
                if (cm) {
                    float p0 = this.values[Math.max(0, i - 1)][k], p1 = this.values[i][k];
                    float p2 = this.values[i + 1][k], p3 = this.values[Math.min(n - 1, i + 2)][k];
                    out[k] = Mth.catmullrom(f, p0, p1, p2, p3);
                } else {
                    out[k] = Mth.lerp(f, this.values[i][k], this.values[i + 1][k]);
                }
            }
            return out;
        }
    }

    private record Bone(@Nullable Track rotation, @Nullable Track position, @Nullable Track scale) {
    }

    private final ResourceLocation file;
    private final String animationName;
    @Nullable private Map<String, Bone> bones;
    private float length;
    private boolean loaded;

    public BedrockPoseAnimation(String path, String animationName) {
        this.file = new ResourceLocation(IceGiantMod.MOD_ID, path);
        this.animationName = animationName;
    }

    public float length() {
        this.load();
        return this.length;
    }

    private void load() {
        if (this.loaded) return;
        this.loaded = true;
        this.bones = new HashMap<>();
        try {
            Optional<net.minecraft.server.packs.resources.Resource> res =
                    Minecraft.getInstance().getResourceManager().getResource(this.file);
            if (res.isEmpty()) return;
            try (Reader reader = res.get().openAsReader()) {
                JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
                JsonObject anims = root.getAsJsonObject("animations");
                JsonObject anim = anims.has(this.animationName) ? anims.getAsJsonObject(this.animationName)
                        : anims.entrySet().iterator().next().getValue().getAsJsonObject();
                this.length = anim.has("animation_length") ? anim.get("animation_length").getAsFloat() : 0;
                JsonObject bonesJson = anim.getAsJsonObject("bones");
                for (Map.Entry<String, JsonElement> e : bonesJson.entrySet()) {
                    JsonObject b = e.getValue().getAsJsonObject();
                    this.bones.put(e.getKey().toLowerCase().replace(" ", "").replace("_", ""),
                            new Bone(track(b, "rotation"), track(b, "position"), track(b, "scale")));
                }
            }
        } catch (Exception ex) {
            LOGGER.warn("Could not read animation {}: {}", this.file, ex.toString());
        }
    }

    @Nullable
    private static Track track(JsonObject bone, String channel) {
        if (!bone.has(channel)) return null;
        JsonElement ch = bone.get(channel);
        List<Float> times = new ArrayList<>();
        List<float[]> values = new ArrayList<>();
        List<Boolean> smooth = new ArrayList<>();
        if (ch.isJsonObject() && ch.getAsJsonObject().has("vector")) {   // constant value written as {"vector": [...]}
            ch = ch.getAsJsonObject().get("vector");
        }
        if (ch.isJsonArray() || ch.isJsonPrimitive()) {          // constant value
            times.add(0f);
            values.add(vec(ch));
            smooth.add(false);
        } else {
            List<Map.Entry<String, JsonElement>> keys = new ArrayList<>(ch.getAsJsonObject().entrySet());
            keys.sort((a, b) -> Float.compare(Float.parseFloat(a.getKey()), Float.parseFloat(b.getKey())));
            for (Map.Entry<String, JsonElement> k : keys) {
                times.add(Float.parseFloat(k.getKey()));
                JsonElement v = k.getValue();
                boolean cm = false;
                if (v.isJsonObject()) {
                    JsonObject o = v.getAsJsonObject();
                    cm = o.has("lerp_mode") && "catmullrom".equals(o.get("lerp_mode").getAsString());
                    v = o.has("vector") ? o.get("vector") : o.has("post") ? o.get("post") : o.get("pre");
                }
                values.add(vec(v));
                smooth.add(cm);
            }
        }
        float[] t = new float[times.size()];
        boolean[] s = new boolean[times.size()];
        for (int i = 0; i < t.length; i++) {
            t[i] = times.get(i);
            s[i] = smooth.get(i);
        }
        return new Track(t, values.toArray(new float[0][]), s);
    }

    private static float[] vec(JsonElement e) {
        float[] out = new float[3];
        if (e.isJsonArray()) {
            for (int i = 0; i < 3 && i < e.getAsJsonArray().size(); i++) out[i] = num(e.getAsJsonArray().get(i));
        } else {
            float f = num(e);
            out[0] = out[1] = out[2] = f;
        }
        return out;
    }

    private static float num(JsonElement e) {
        try {
            return e.getAsFloat();
        } catch (Exception ex) {
            return 0f;                                           // molang expressions are not supported
        }
    }

    /** Adds the pose at time t (seconds) to the part, if the file has a bone with this name. */
    public void apply(String boneName, ModelPart part, float t) {
        this.load();
        if (this.bones == null) return;
        Bone bone = this.bones.get(boneName.toLowerCase().replace(" ", "").replace("_", ""));
        if (bone == null) return;
        if (bone.rotation() != null) {
            float[] r = bone.rotation().sample(t);
            part.xRot += r[0] * Mth.DEG_TO_RAD;
            part.yRot += r[1] * Mth.DEG_TO_RAD;
            part.zRot += r[2] * Mth.DEG_TO_RAD;
        }
        if (bone.position() != null) {
            float[] p = bone.position().sample(t);
            part.x += p[0];
            part.y -= p[1];                                      // Java models: y points down
            part.z += p[2];
        }
        if (bone.scale() != null) {
            float[] s = bone.scale().sample(t);
            part.xScale *= s[0];
            part.yScale *= s[1];
            part.zScale *= s[2];
        }
    }
}
