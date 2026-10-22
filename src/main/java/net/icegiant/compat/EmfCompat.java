package net.icegiant.compat;

import com.mojang.logging.LogUtils;
import net.icegiant.client.OurAnimations;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.client.event.RenderLivingEvent;
import net.minecraftforge.common.MinecraftForge;
import org.slf4j.Logger;

import java.lang.reflect.Method;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * Compatibility with Entity Model Features (EMF) - the mod behind Fresh Animations and its player
 * extension. EMF re-poses a model with its own animations after the normal pose is worked out, so it
 * would overwrite our slam, swing, grab and giant-death poses. EMF's public API (traben.entity_model_features
 * .EMFAnimationApi, read from EMF's own source on GitHub) lets other mods pause its animations for an entity:
 * <ul>
 *   <li>EMF 3.0.7+: {@code registerPauseCondition(Function<EMFEntity, Boolean>)} - asked for every entity;</li>
 *   <li>older EMF: {@code pauseAllCustomAnimationsForEntity / resumeAllCustomAnimationsForEntity(EMFEntity)}
 *       - switched each time the entity is drawn.</li>
 * </ul>
 * Every entity is an EMFEntity when EMF is installed (EMF adds the interface to Entity), so the entity itself
 * is handed over. Called by reflection: without EMF nothing happens, and EMF isn't needed to build the mod.
 * While our animation plays, EMF's animation pauses for that entity only; afterwards it goes on as before.
 */
public final class EmfCompat {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String API = "traben.entity_model_features.EMFAnimationApi";

    private static Method pause, resume;
    private static final Set<Integer> PAUSED = ConcurrentHashMap.newKeySet();

    private EmfCompat() {
    }

    /** Client setup (main thread). */
    public static void init() {
        Class<?> api;
        try {
            api = Class.forName(API);
        } catch (ClassNotFoundException | LinkageError e) {
            return;                                                               // EMF not installed
        }
        try {
            Method register = api.getMethod("registerPauseCondition", Function.class);
            Function<Object, Boolean> condition = e -> e instanceof LivingEntity le && OurAnimations.isPlaying(le);
            register.invoke(null, condition);
            LOGGER.info("[icegiant] EMF found: our animations pause EMF animations while they play (pause condition)");
            return;
        } catch (NoSuchMethodException ignored) {
            // older EMF: per entity below
        } catch (Throwable t) {
            LOGGER.warn("[icegiant] EMF pause condition could not be registered", t);
        }
        for (Method m : api.getMethods()) {
            if (m.getParameterCount() != 1) continue;
            if (m.getName().equals("pauseAllCustomAnimationsForEntity")) pause = m;
            if (m.getName().equals("resumeAllCustomAnimationsForEntity")) resume = m;
        }
        if (pause != null && resume != null) {
            MinecraftForge.EVENT_BUS.addListener(EmfCompat::onRenderLiving);
            LOGGER.info("[icegiant] EMF found: our animations pause EMF animations while they play (per entity)");
        } else {
            LOGGER.warn("[icegiant] EMF found, but its pause API is missing - our animations may be overwritten");
        }
    }

    private static void onRenderLiving(RenderLivingEvent.Pre<?, ?> event) {
        if (pause == null || resume == null) return;
        LivingEntity entity = event.getEntity();
        int id = entity.getId();
        try {
            if (OurAnimations.isPlaying(entity)) {
                if (PAUSED.add(id)) pause.invoke(null, entity);
            } else if (PAUSED.remove(id)) {
                resume.invoke(null, entity);
            }
        } catch (Throwable t) {
            pause = resume = null;
            LOGGER.warn("[icegiant] EMF pause failed, switched off", t);
        }
    }
}
