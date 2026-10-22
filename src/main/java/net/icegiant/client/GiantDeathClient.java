package net.icegiant.client;

import net.icegiant.IceGiantMod;
import net.icegiant.entity.GiantDeath;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.core.NonNullList;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderPlayerEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Client side of GiantDeath: a big player in Ice Giant armor plays the giant's "dying_1" animation.
 * While he is drawn, vanilla's sideways tip-over and red flash are switched off (deathTime 0 for that
 * moment, like IceGiantRenderer does it), the model gets the seconds since death (PlayerModelArmMixin
 * plays the animation), and the armor he wore is put back on him (it already dropped on the server).
 */
@Mod.EventBusSubscriber(modid = IceGiantMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class GiantDeathClient {

    private static final Map<Integer, ItemStack[]> ARMOR = new ConcurrentHashMap<>();
    /** Seconds since death of the player being drawn right now (only between Pre and Post). */
    private static final Map<Integer, Float> SECONDS = new ConcurrentHashMap<>();
    private static final Map<Integer, Integer> SAVED_DEATH_TIME = new ConcurrentHashMap<>();
    private static final Map<Integer, ItemStack[]> SAVED_ARMOR = new ConcurrentHashMap<>();

    private GiantDeathClient() {
    }

    public static void start(int entityId, ItemStack[] armor) {
        ARMOR.put(entityId, armor);
    }

    public static boolean isDying(int entityId) {
        return GiantDeath.CLIENT.contains(entityId);
    }

    /** Seconds since death while this player is drawn dying the giant's death, else -1. */
    public static float seconds(int entityId) {
        Float s = SECONDS.get(entityId);
        return s == null ? -1.0F : s;
    }

    /** Last (and only if nobody cancelled the render - then Post would never come to undo this). */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onRenderPre(RenderPlayerEvent.Pre event) {
        Player player = event.getEntity();
        int id = player.getId();
        if (!GiantDeath.CLIENT.contains(id)) {
            ARMOR.remove(id);
            return;
        }
        int deathTime = player.deathTime;
        SECONDS.put(id, deathTime > 0 ? (deathTime - 1 + event.getPartialTick()) / 20.0F : 0.0F);
        SAVED_DEATH_TIME.put(id, deathTime);
        player.deathTime = 0;                                                    // no tip-over, no red flash
        ItemStack[] wore = ARMOR.get(id);
        if (wore != null) {
            NonNullList<ItemStack> armor = player.getInventory().armor;
            ItemStack[] saved = new ItemStack[4];
            for (int i = 0; i < 4; i++) {
                saved[i] = armor.get(i);
                if (saved[i].isEmpty() && !wore[i].isEmpty()) armor.set(i, wore[i]);
            }
            SAVED_ARMOR.put(id, saved);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onRenderPost(RenderPlayerEvent.Post event) {
        Player player = event.getEntity();
        int id = player.getId();
        SECONDS.remove(id);
        Integer deathTime = SAVED_DEATH_TIME.remove(id);
        if (deathTime != null) player.deathTime = deathTime;
        ItemStack[] saved = SAVED_ARMOR.remove(id);
        if (saved != null) for (int i = 0; i < 4; i++) player.getInventory().armor.set(i, saved[i]);
    }

    /** The death pose on a player model (called from PlayerModelArmMixin after the normal pose). */
    public static void applyPose(HumanoidModel<?> model, float seconds) {
        float t = Math.min(seconds, IceGiantModel.DYING.length());               // hold the last frame
        ModelPart[] parts = {model.head, model.body, model.rightArm, model.leftArm, model.rightLeg, model.leftLeg};
        String[] bones = {"Head", "Body", "Right Arm", "Left Arm", "Right Leg", "Left Leg"};
        for (int i = 0; i < parts.length; i++) {
            parts[i].xScale = parts[i].yScale = parts[i].zScale = 1.0F;
            IceGiantModel.DYING.apply(bones[i], parts[i], t);
        }
        model.hat.copyFrom(model.head);
        if (model instanceof PlayerModel<?> pm) {
            pm.jacket.copyFrom(pm.body);
            pm.rightSleeve.copyFrom(pm.rightArm);
            pm.leftSleeve.copyFrom(pm.leftArm);
            pm.rightPants.copyFrom(pm.rightLeg);
            pm.leftPants.copyFrom(pm.leftLeg);
        }
    }
}
