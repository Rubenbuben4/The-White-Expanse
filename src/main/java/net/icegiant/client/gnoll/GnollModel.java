package net.icegiant.client.gnoll;

import net.icegiant.IceGiantMod;
import net.icegiant.entity.gnoll.GnollArcherEntity;
import net.icegiant.entity.gnoll.GnollEntity;
import net.icegiant.entity.gnoll.GnollMeleeEntity;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.core.animation.AnimationState;
import software.bernie.geckolib.model.GeoModel;

/**
 * GeckoLib model of a Gnoll (converted from the Gnolls pack by Hibiscus Studios x AnonymousDevs).
 * Each variant has its own files: {name}{suffix} with suffix "" (snow), "_normal" or "_warm":
 *   geo/....geo.json, animations/....animation.json, textures/entity/....png
 * <p>
 * Per frame it shows only the optional armour pieces this Gnoll wears, shows the spectral jaws
 * only during the bite and hides the archer's arrow right after the shot. The head turn is done
 * at render time (GnollRenderer), so it never adds up between frames.
 */
public class GnollModel<T extends GnollEntity> extends GeoModel<T> {

    private static final String[] SUFFIX = {"", "_normal", "_warm"};

    private final ResourceLocation[] models = new ResourceLocation[3];
    private final ResourceLocation[] textures = new ResourceLocation[3];
    private final ResourceLocation[] animations = new ResourceLocation[3];

    public GnollModel(String name) {
        for (int v = 0; v < 3; v++) {
            String n = name + SUFFIX[v];
            this.models[v] = new ResourceLocation(IceGiantMod.MOD_ID, "geo/" + n + ".geo.json");
            this.textures[v] = new ResourceLocation(IceGiantMod.MOD_ID, "textures/entity/" + n + ".png");
            this.animations[v] = new ResourceLocation(IceGiantMod.MOD_ID, "animations/" + n + ".animation.json");
        }
    }

    @Override
    public ResourceLocation getModelResource(T animatable) {
        return this.models[animatable.getVariant()];
    }

    @Override
    public ResourceLocation getTextureResource(T animatable) {
        return this.textures[animatable.getVariant()];
    }

    @Override
    public ResourceLocation getAnimationResource(T animatable) {
        return this.animations[animatable.getVariant()];
    }

    @Override
    public void setCustomAnimations(T gnoll, long instanceId, AnimationState<T> state) {
        super.setCustomAnimations(gnoll, instanceId, state);

        // the bones are shared by every Gnoll of this model, so set all visibilities every frame
        for (int i = 1; i <= 3; i++) {
            int index = i;
            this.getBone("addarmor" + i).ifPresent(b -> b.setHidden(!gnoll.showsArmor(index)));
        }

        int action = gnoll.getClientAction();
        float t = gnoll.getClientActionTime(state.getPartialTick());
        boolean biting = gnoll instanceof GnollMeleeEntity && action == GnollMeleeEntity.ACT_BITE;
        boolean jaws = biting && t >= 0.6F && t < 1.0F;
        boolean jawsFinal = biting && t >= 1.0F && t < 1.25F;
        this.getBone("up_jaw").ifPresent(b -> b.setHidden(!jaws));
        this.getBone("down_jaw").ifPresent(b -> b.setHidden(!jaws));
        this.getBone("jaws_final").ifPresent(b -> b.setHidden(!jawsFinal));

        boolean shot = gnoll instanceof GnollArcherEntity && action == GnollArcherEntity.ACT_SHOOT
                && t >= GnollArcherEntity.RELEASE_TICK / 20.0F && t < 1.4F;
        this.getBone("arrow").ifPresent(b -> b.setHidden(shot));
    }
}
