package net.icegiant.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraftforge.client.settings.KeyConflictContext;

public class ModKeyMappings {

    public static final String CATEGORY = "key.categories.icegiant";

    public static final KeyMapping ACTIVATE_SLAM = new KeyMapping(
            "key.icegiant.activate_slam",
            KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM,
            InputConstants.KEY_H,
            CATEGORY
    );

    /** Wall-breaker dash (IceDashAbility) - J by default, changeable in the controls menu. */
    public static final KeyMapping ACTIVATE_DASH = new KeyMapping(
            "key.icegiant.activate_dash",
            KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM,
            InputConstants.KEY_J,
            CATEGORY
    );

    /** Giant Rune Sword: ground cut (SwordGroundCutAbility) - K by default. */
    public static final KeyMapping ACTIVATE_GROUND_CUT = new KeyMapping(
            "key.icegiant.activate_ground_cut",
            KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM,
            InputConstants.KEY_K,
            CATEGORY
    );

    /** Giant Rune Sword: wide sweep (SwordSweepAbility) - N by default (L is vanilla's advancements key). */
    public static final KeyMapping ACTIVATE_SWEEP = new KeyMapping(
            "key.icegiant.activate_sweep",
            KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM,
            InputConstants.KEY_N,
            CATEGORY
    );

    /** Grab / throw (PlayerGrabAbility) - needs the full Ice Giant armor. G by default. */
    public static final KeyMapping GRAB = new KeyMapping(
            "key.icegiant.grab",
            KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM,
            InputConstants.KEY_G,
            CATEGORY
    );
}
