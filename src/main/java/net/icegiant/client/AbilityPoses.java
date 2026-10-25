package net.icegiant.client;

import net.minecraft.client.model.geom.ModelPart;

/**
 * Body poses of the mod weapons' abilities, hand-animated by Rubenbuben4 in Blockbench (linear between keyframes):
 * the right-click swing (1.2917 s) and the slam 2.0 (6.3333 s, also used by the ground cut).
 * <p>
 * Used by PlayerModelArmMixin (third person, the whole body) and by FirstPersonAbilityPose (first person: the
 * held weapon follows the right arm). Moved out of the mixin unchanged on 2026-10-25, because other classes
 * cannot read a mixin's fields.
 */
public final class AbilityPoses {

    private AbilityPoses() {
    }

    static final float REST_BODY_X = 0f, REST_BODY_Y = 0f, REST_BODY_Z = 0f;
    static final float REST_HEAD_X = 0f, REST_HEAD_Y = 0f, REST_HEAD_Z = 0f;
    static final float REST_RARM_X = -5f, REST_RARM_Y = 2f, REST_RARM_Z = 0f;
    static final float REST_LARM_X = 5f, REST_LARM_Y = 2f, REST_LARM_Z = 0f;
    static final float REST_RLEG_X = -1.9f, REST_RLEG_Y = 12f, REST_RLEG_Z = 0f;
    static final float REST_LLEG_X = 1.9f, REST_LLEG_Y = 12f, REST_LLEG_Z = 0f;

    // ================= SWING-Daten (1.2917s) =================

    static final float SWING_LENGTH = 1.2917f;

    static final float[] T_HEAD = {0f, 0.2917f, 0.5833f, 0.875f, 1.2917f};
    static final float[][] R_HEAD = {
            {0, 0, 0}, {2.5414f, -2.4879f, -0.1137f}, {7.5936f, -8.7221f, -0.3942f},
            {12.9912f, -22.3869f, -1.7508f}, {0, 0, 0}
    };

    static final float[] T_BODY = {0f, 0.2917f, 0.5f, 0.6667f, 1.2917f};
    static final float[][] R_BODY = {
            {0, -5, 0}, {2.5f, -10, 0}, {2.5218f, -12.4976f, -0.1117f},
            {2.5786f, -17.272f, -0.3317f}, {0, -5, 0}
    };

    static final float[] T_RARM = {0f, 0.0417f, 0.125f, 0.25f, 0.3333f, 0.5f, 0.625f, 0.7917f, 1.2917f};
    static final float[][] R_RARM = {
            {-40, 0, 0}, {-44.0863f, 29.7533f, -0.2593f}, {-61.5556f, 67.335f, -22.4321f},
            {-138.79f, 57.5465f, -101.1394f}, {-113.7245f, 42.1186f, -88.1094f}, {-82.1006f, 37.5122f, -85.5757f},
            {-55.8967f, 37.5218f, -97.8478f}, {-51.8659f, 33.6394f, -89.4662f}, {-40, 0, 0}
    };
    static final float[] T_RARM_POS = {0f, 0.0833f, 0.2083f, 1.2917f};
    static final float[][] P_RARM = {
            {0, 0, 0}, {0.64911f, 0.13024f, -0.74947f}, {0.18985f, -0.07277f, -2.0718f}, {0, 0, 0}
    };

    static final float[] T_LARM = {0f, 0.5f, 0.75f, 1.2917f};
    static final float[][] R_LARM = {
            {0, -2.5f, 0}, {-16.4365f, -14.4006f, -15.6551f}, {-15.5077f, -30.1804f, -16.3603f}, {0, -2.5f, 0}
    };
    static final float[] T_LARM_POS = {0f, 0.5f, 0.75f, 1.2917f};
    static final float[][] P_LARM = {
            {0, 0, 0}, {-0.03271f, 0, 0.74929f}, {-0.31496f, 0.20949f, 1.61598f}, {0, 0, 0}
    };

    static final float[] T_RLEG = {0f, 0.1667f, 1.2917f};
    static final float[][] R_RLEG = {{-2.5f, 0, 0}, {-3.4494f, 11.279f, 4.1092f}, {-2.5f, 0, 0}};

    static final float[] T_LLEG = {0f, 0.1667f, 1.2917f};
    static final float[][] R_LLEG = {{5, -5, 0}, {6.0973f, -11.262f, -5.5967f}, {5, -5, 0}};
    static final float[] T_LLEG_POS = {0f, 0.1667f, 1.2917f};
    static final float[][] P_LLEG = {{0, 0, 0}, {-0.06512f, 0.06537f, 0.7443f}, {0, 0, 0}};

    // ================= SLAM 2.0-Daten (6.3333s) =================

    static final float SLAM_LENGTH = 6.3333f;

    static final float[] T_S2_HEAD = {0.0f, 0.2083f, 0.75f, 1.5f, 1.7083f, 1.9167f, 2.3333f, 2.8333f, 3.375f, 4.5f, 5.7083f, 6.3333f};
    static final float[][] R_S2_HEAD = {{0f, 0f, 0f}, {7.5f, 0f, 0f}, {0f, 0f, 0f}, {-20f, 0f, 0f}, {22.5f, 0f, 0f}, {45f, 0f, 0f}, {67.5f, 0f, 0f}, {67.5f, 0f, 0f}, {7.5f, 0f, 0f}, {7.5f, 0f, 0f}, {7.5f, 0f, 0f}, {0f, 0f, 0f}};
    static final float[] T_S2_HEAD_POS = {0.0f, 0.75f, 1.2917f, 1.5f, 1.7083f, 1.8333f, 1.9167f, 2.0417f, 2.3333f, 2.4583f, 2.8333f, 3.375f, 3.8333f, 4.5f, 5.125f, 5.7083f, 6.3333f};
    static final float[][] P_S2_HEAD = {{0f, 0f, 0f}, {0f, 0f, -0.5f}, {0f, 0f, -1.5f}, {0f, 0f, -2.5f}, {0f, 0f, -5.5f}, {0f, -1f, -8.5f}, {0f, -2f, -9.5f}, {0f, -3f, -10.5f}, {0f, -4f, -11.5f}, {0f, -6f, -10.5f}, {0f, -6f, -10.5f}, {0f, 1f, -2.5f}, {0f, 0.25f, -2.5f}, {0f, 1f, -2.5f}, {0f, 0.25f, -2.5f}, {0f, 1f, -2.5f}, {0f, 0f, 0f}};
    static final float[] T_S2_HEAD_SCALE = {0.0f, 2.8333f, 6.3333f};
    static final float[][] SC_S2_HEAD = {{1f, 1f, 1f}, {1f, 1f, 1f}, {1f, 1f, 1f}};

    static final float[] T_S2_BODY = {0.0f, 0.2917f, 0.75f, 1.2917f, 1.7083f, 1.8333f, 1.9167f, 2.0417f, 2.0833f, 2.3333f, 2.4583f, 2.8333f, 3.375f, 4.5f, 5.7083f, 6.3333f};
    static final float[][] R_S2_BODY = {{0f, 0f, 0f}, {0f, 10f, 0f}, {5f, 10f, 0f}, {12.5f, 10f, 0f}, {25f, 10f, 0f}, {37.5f, 10f, 0f}, {38.3772f, 10f, 0f}, {47.5f, 10f, 0f}, {47.5f, 10f, 0f}, {62.5f, 10f, 0f}, {72.5f, 10f, 0f}, {72.5f, 10f, 0f}, {0f, 10f, 0f}, {0f, 10f, 0f}, {0f, 10f, 0f}, {0f, 0f, 0f}};
    static final float[] T_S2_BODY_POS = {0.0f, 0.75f, 1.2917f, 1.5f, 1.7083f, 1.8333f, 1.9167f, 2.0417f, 2.3333f, 2.4583f, 2.8333f, 3.375f, 3.8333f, 4.5f, 5.125f, 5.7083f, 6.3333f};
    static final float[][] P_S2_BODY = {{0f, 0f, 0f}, {0f, 0f, -1f}, {0f, 0f, -2f}, {0f, 0f, -4f}, {0f, -1f, -6f}, {0f, -2f, -8f}, {0f, -2f, -9f}, {0f, -3f, -11f}, {0f, -5f, -11f}, {-1f, -7f, -11f}, {-1f, -7f, -11f}, {0f, 1f, -3f}, {0f, 0f, -3f}, {0f, 1f, -3f}, {0f, 0f, -3f}, {0f, 1f, -3f}, {0f, 0f, 0f}};
    static final float[] T_S2_BODY_SCALE = {0.0f, 0.75f, 1.2917f, 2.8333f, 3.375f, 3.8333f, 4.5f, 5.125f, 5.7083f, 6.3333f};
    static final float[][] SC_S2_BODY = {{1f, 1f, 1f}, {1f, 1f, 1.1f}, {1.1f, 1f, 1.1f}, {1.1f, 1f, 1.1f}, {1.2f, 1.1f, 1.2f}, {1.1f, 1f, 1.1f}, {1.2f, 1.1f, 1.2f}, {1.1f, 1f, 1.1f}, {1.2f, 1.1f, 1.2f}, {1.1f, 1f, 1.1f}};

    static final float[] T_S2_RARM = {0.0f, 0.2083f, 0.5417f, 0.75f, 0.9583f, 1.2917f, 1.5f, 1.6667f, 1.8333f, 1.9167f, 2.0417f, 2.2083f, 2.3333f, 2.8333f, 3.0f, 3.1667f, 3.375f, 3.8333f, 4.5f, 5.125f, 5.7083f, 6.3333f};
    static final float[][] R_S2_RARM = {{0f, 0f, 0f}, {0f, 0f, 0f}, {-34.8423f, -2.5587f, -7.0524f}, {-62.4603f, -5.8226f, -6.1817f}, {-73.8667f, -11.4427f, 5.3456f}, {-106.3667f, -11.4427f, 5.3456f}, {-156.3667f, -11.4427f, 5.3456f}, {-119.7579f, -11.4427f, 5.3456f}, {-84.7579f, -11.4427f, 5.3456f}, {-57.2579f, -11.4427f, 5.3456f}, {-25.7317f, -15.3653f, -1.2276f}, {-11.9341f, -16.629f, -9.182f}, {3.0659f, -16.629f, -9.182f}, {3.07f, -16.63f, -9.18f}, {3.07f, -16.63f, -9.18f}, {2.1835f, -4.596f, 1.505f}, {1.5638f, -4.8415f, 9.0266f}, {1.9806f, -4.6868f, 4.0116f}, {1.5638f, -4.8415f, 9.0266f}, {1.9806f, -4.6868f, 4.0116f}, {1.5638f, -4.8415f, 9.0266f}, {0f, 0f, 0f}};
    static final float[] T_S2_RARM_POS = {0.0f, 0.2917f, 0.75f, 1.2917f, 1.5f, 1.7083f, 1.8333f, 1.9167f, 2.0417f, 2.3333f, 2.4583f, 2.8333f, 3.0f, 3.1667f, 3.375f, 3.8333f, 4.5f, 5.125f, 5.7083f, 6.3333f};
    static final float[][] P_S2_RARM = {{0f, 0f, 0f}, {0f, 0f, 1f}, {0f, 0f, 0.25f}, {0f, 0f, -0.75f}, {0f, 2f, -1.75f}, {0f, 1f, -5.75f}, {0f, 1f, -7.75f}, {0f, -2f, -7.75f}, {0f, -3f, -8.75f}, {0f, -4f, -8.75f}, {0f, -7f, -8.75f}, {0f, -7f, -8.75f}, {0f, -5f, -8.75f}, {0f, -3f, -4.75f}, {-1f, 0f, -1.75f}, {-0.5f, -0.5f, -1.75f}, {-1f, 0f, -1.75f}, {-0.5f, -0.5f, -1.75f}, {-1f, 0f, -1.75f}, {0f, 0f, 0.25f}};
    static final float[] T_S2_RARM_SCALE = {0.0f, 1.5f, 2.3333f, 2.4583f, 2.625f, 2.8333f, 3.0f};
    static final float[][] SC_S2_RARM = {{1f, 1f, 1f}, {0.9f, 1.2f, 0.9f}, {0.9f, 1.3f, 0.9f}, {1.1f, 1f, 1.1f}, {1f, 1f, 1f}, {1f, 1f, 1f}, {1f, 1f, 1f}};

    static final float[] T_S2_LARM = {0.0f, 0.2083f, 0.5417f, 0.75f, 0.9583f, 1.2917f, 1.5f, 1.6667f, 1.8333f, 1.9167f, 2.0417f, 2.2083f, 2.4583f, 2.8333f, 3.0f, 3.1667f, 3.375f, 3.8333f, 4.5f, 5.125f, 5.7083f, 6.3333f};
    static final float[][] R_S2_LARM = {{0f, 0f, 0f}, {0f, 0f, 0f}, {-49.3949f, 14.3887f, -0.109f}, {-69.3949f, 14.3887f, -0.109f}, {-76.0374f, 32.9299f, 8.1359f}, {-102.6339f, 42.7194f, 5.5423f}, {-165.205f, 55.1765f, -26.2773f}, {-131.0058f, 52.5221f, -21.825f}, {-80.2719f, 44.3674f, 9.7695f}, {-60.8476f, 43.6099f, 12.7519f}, {-21.6316f, 24.9452f, 33.1331f}, {-4.2219f, 24.9452f, 33.1331f}, {8.2781f, 24.9452f, 33.1331f}, {8.28f, 24.95f, 33.13f}, {8.28f, 24.95f, 33.13f}, {-10.7981f, 24.0207f, -11.0086f}, {-10.7981f, 24.0207f, -11.0086f}, {-8.5565f, 24.8619f, -5.5943f}, {-10.7981f, 24.0207f, -11.0086f}, {-8.5565f, 24.8619f, -5.5943f}, {-10.7981f, 24.0207f, -11.0086f}, {0f, 0f, 0f}};
    static final float[] T_S2_LARM_POS = {0.0f, 0.2917f, 0.75f, 1.2917f, 1.5f, 1.7083f, 1.8333f, 1.9167f, 2.0417f, 2.4583f, 2.8333f, 3.0f, 3.1667f, 3.375f, 3.8333f, 4.5f, 5.125f, 5.7083f, 6.3333f};
    static final float[][] P_S2_LARM = {{0f, 0f, 0f}, {0f, 0f, -1f}, {0f, 0f, -2.25f}, {0f, 0f, -3.25f}, {0f, 3f, -4.25f}, {0f, 1f, -8.25f}, {1f, 1f, -11.25f}, {0f, -3f, -9.25f}, {0f, -3f, -9.25f}, {-2f, -8f, -10.25f}, {-2f, -8f, -10.25f}, {-2f, -6f, -10.25f}, {-1f, -3f, -5.25f}, {1f, 0f, -5.25f}, {0.75f, -0.5f, -5.25f}, {1f, 0f, -5.25f}, {0.75f, -0.5f, -5.25f}, {1f, 0f, -5.25f}, {0.5f, 0f, -0.25f}};
    static final float[] T_S2_LARM_SCALE = {0.0f, 1.5f, 2.3333f, 2.4583f, 2.625f, 2.8333f, 3.0f};
    static final float[][] SC_S2_LARM = {{1f, 1f, 1f}, {0.9f, 1.2f, 0.9f}, {0.9f, 1.3f, 0.9f}, {1.1f, 1f, 1.1f}, {1f, 1f, 1f}, {1f, 1f, 1f}, {1f, 1f, 1f}};

    static final float[] T_S2_RLEG = {0.0f, 0.2917f, 0.6667f, 1.5f, 1.7083f, 2.8333f, 4.75f, 6.3333f};
    static final float[][] R_S2_RLEG = {{0f, 0f, 0f}, {12.6044f, 7.3212f, 1.6322f}, {17.6044f, 7.3212f, 1.6322f}, {17.6044f, 7.3212f, 1.6322f}, {30.1044f, 7.3212f, 1.6322f}, {30.1f, 7.32f, 1.63f}, {7.6f, 7.32f, 1.63f}, {0.0991f, -0.18f, 1.617f}};
    static final float[] T_S2_RLEG_POS = {0.0f, 0.2917f, 0.6667f, 1.5f, 1.7083f, 1.9167f, 2.8333f, 6.3333f};
    static final float[][] P_S2_RLEG = {{0f, 0f, 0f}, {-0.25f, 0.25f, 1f}, {-0.25f, 0f, 1f}, {-0.25f, 1f, 1f}, {-0.25f, 1f, 0f}, {-0.25f, 1f, -1f}, {-0.25f, 1f, -1f}, {0f, 0f, 0f}};
    static final float[] T_S2_RLEG_SCALE = {0.0f, 1.5f, 2.8333f, 6.3333f};
    static final float[][] SC_S2_RLEG = {{1f, 1f, 1f}, {1f, 1.1f, 1f}, {1f, 1.1f, 1f}, {1f, 1f, 1f}};

    static final float[] T_S2_LLEG = {0.0f, 0.2917f, 0.4583f, 0.6667f, 1.125f, 1.7083f, 1.9167f, 2.8333f};
    static final float[][] R_S2_LLEG = {{0f, 0f, 0f}, {-0.0001f, -0.0002f, -0.0005f}, {-0.0001f, -0.0002f, -0.0005f}, {0f, 0f, 0f}, {-15f, 0f, 0f}, {0f, 0f, 0f}, {0f, 0f, 0f}, {0f, 0f, 0f}};
    static final float[] T_S2_LLEG_POS = {0.0f, 0.2917f, 0.4583f, 0.6667f, 1.125f, 1.7083f, 1.9167f, 2.2083f, 2.8333f, 4.75f, 6.3333f};
    static final float[][] P_S2_LLEG = {{0f, 0f, 0f}, {0.75f, 0.25f, -1f}, {0.75f, 0.25f, -1f}, {0.75f, 0.25f, -2f}, {0.75f, 0.25f, -1f}, {0.75f, 0.25f, -4f}, {0.75f, 0.25f, -4f}, {0.75f, 0.25f, -5f}, {0.75f, 0.25f, -5f}, {0.75f, 0.25f, -3f}, {-0.25f, 0.25f, 0f}};
    static final float[] T_S2_LLEG_SCALE = {0.0f, 1.9167f, 2.8333f, 6.3333f};
    static final float[][] SC_S2_LLEG = {{1f, 1f, 1f}, {1.2f, 1f, 1.1f}, {1.2f, 1f, 1.1f}, {1f, 1f, 1f}};

    public static void swing(float elapsed, ModelPart head, ModelPart body, ModelPart rightArm, ModelPart leftArm,
                            ModelPart rightLeg, ModelPart leftLeg) {
        float[] headRot = interp(T_HEAD, R_HEAD, elapsed);
        head.xRot = (float) Math.toRadians(headRot[0]);
        head.yRot = (float) Math.toRadians(headRot[1]);
        head.zRot = (float) Math.toRadians(headRot[2]);

        float[] bodyRot = interp(T_BODY, R_BODY, elapsed);
        body.xRot = (float) Math.toRadians(bodyRot[0]);
        body.yRot = (float) Math.toRadians(bodyRot[1]);
        body.zRot = (float) Math.toRadians(bodyRot[2]);

        float[] rArm = interp(T_RARM, R_RARM, elapsed);
        rightArm.xRot = (float) Math.toRadians(rArm[0]);
        rightArm.yRot = (float) Math.toRadians(rArm[1]);
        rightArm.zRot = (float) Math.toRadians(rArm[2]);
        float[] rArmPos = interp(T_RARM_POS, P_RARM, elapsed);
        rightArm.x = REST_RARM_X + rArmPos[0];
        rightArm.y = REST_RARM_Y - rArmPos[1];
        rightArm.z = REST_RARM_Z + rArmPos[2];

        float[] lArm = interp(T_LARM, R_LARM, elapsed);
        leftArm.xRot = (float) Math.toRadians(lArm[0]);
        leftArm.yRot = (float) Math.toRadians(lArm[1]);
        leftArm.zRot = (float) Math.toRadians(lArm[2]);
        float[] lArmPos = interp(T_LARM_POS, P_LARM, elapsed);
        leftArm.x = REST_LARM_X + lArmPos[0];
        leftArm.y = REST_LARM_Y - lArmPos[1];
        leftArm.z = REST_LARM_Z + lArmPos[2];

        float[] rLeg = interp(T_RLEG, R_RLEG, elapsed);
        rightLeg.xRot = (float) Math.toRadians(rLeg[0]);
        rightLeg.yRot = (float) Math.toRadians(rLeg[1]);
        rightLeg.zRot = (float) Math.toRadians(rLeg[2]);

        float[] lLeg = interp(T_LLEG, R_LLEG, elapsed);
        leftLeg.xRot = (float) Math.toRadians(lLeg[0]);
        leftLeg.yRot = (float) Math.toRadians(lLeg[1]);
        leftLeg.zRot = (float) Math.toRadians(lLeg[2]);
        float[] lLegPos = interp(T_LLEG_POS, P_LLEG, elapsed);
        leftLeg.x = REST_LLEG_X + lLegPos[0];
        leftLeg.y = REST_LLEG_Y - lLegPos[1];
        leftLeg.z = REST_LLEG_Z + lLegPos[2];

    }

    public static void slam(float elapsed, ModelPart head, ModelPart body, ModelPart rightArm, ModelPart leftArm,
                            ModelPart rightLeg, ModelPart leftLeg) {
        float[] headRot = interp(T_S2_HEAD, R_S2_HEAD, elapsed);
        head.xRot = (float) Math.toRadians(headRot[0]);
        head.yRot = (float) Math.toRadians(headRot[1]);
        head.zRot = (float) Math.toRadians(headRot[2]);
        float[] headPos = interp(T_S2_HEAD_POS, P_S2_HEAD, elapsed);
        head.x = REST_HEAD_X + headPos[0];
        head.y = REST_HEAD_Y - headPos[1];
        head.z = REST_HEAD_Z + headPos[2];
        float[] headScale = interp(T_S2_HEAD_SCALE, SC_S2_HEAD, elapsed);
        head.xScale = headScale[0]; head.yScale = headScale[1]; head.zScale = headScale[2];

        float[] bodyRot = interp(T_S2_BODY, R_S2_BODY, elapsed);
        body.xRot = (float) Math.toRadians(bodyRot[0]);
        body.yRot = (float) Math.toRadians(bodyRot[1]);
        body.zRot = (float) Math.toRadians(bodyRot[2]);
        float[] bodyPos = interp(T_S2_BODY_POS, P_S2_BODY, elapsed);
        body.x = REST_BODY_X + bodyPos[0];
        body.y = REST_BODY_Y - bodyPos[1];
        body.z = REST_BODY_Z + bodyPos[2];
        float[] bodyScale = interp(T_S2_BODY_SCALE, SC_S2_BODY, elapsed);
        body.xScale = bodyScale[0]; body.yScale = bodyScale[1]; body.zScale = bodyScale[2];

        float[] rArm = interp(T_S2_RARM, R_S2_RARM, elapsed);
        rightArm.xRot = (float) Math.toRadians(rArm[0]);
        rightArm.yRot = (float) Math.toRadians(rArm[1]);
        rightArm.zRot = (float) Math.toRadians(rArm[2]);
        float[] rArmPos = interp(T_S2_RARM_POS, P_S2_RARM, elapsed);
        rightArm.x = REST_RARM_X + rArmPos[0];
        rightArm.y = REST_RARM_Y - rArmPos[1];
        rightArm.z = REST_RARM_Z + rArmPos[2];
        float[] rArmScale = interp(T_S2_RARM_SCALE, SC_S2_RARM, elapsed);
        rightArm.xScale = rArmScale[0]; rightArm.yScale = rArmScale[1]; rightArm.zScale = rArmScale[2];

        float[] lArm = interp(T_S2_LARM, R_S2_LARM, elapsed);
        leftArm.xRot = (float) Math.toRadians(lArm[0]);
        leftArm.yRot = (float) Math.toRadians(lArm[1]);
        leftArm.zRot = (float) Math.toRadians(lArm[2]);
        float[] lArmPos = interp(T_S2_LARM_POS, P_S2_LARM, elapsed);
        leftArm.x = REST_LARM_X + lArmPos[0];
        leftArm.y = REST_LARM_Y - lArmPos[1];
        leftArm.z = REST_LARM_Z + lArmPos[2];
        float[] lArmScale = interp(T_S2_LARM_SCALE, SC_S2_LARM, elapsed);
        leftArm.xScale = lArmScale[0]; leftArm.yScale = lArmScale[1]; leftArm.zScale = lArmScale[2];

        float[] rLeg = interp(T_S2_RLEG, R_S2_RLEG, elapsed);
        rightLeg.xRot = (float) Math.toRadians(rLeg[0]);
        rightLeg.yRot = (float) Math.toRadians(rLeg[1]);
        rightLeg.zRot = (float) Math.toRadians(rLeg[2]);
        float[] rLegPos = interp(T_S2_RLEG_POS, P_S2_RLEG, elapsed);
        rightLeg.x = REST_RLEG_X + rLegPos[0];
        rightLeg.y = REST_RLEG_Y - rLegPos[1];
        rightLeg.z = REST_RLEG_Z + rLegPos[2];
        float[] rLegScale = interp(T_S2_RLEG_SCALE, SC_S2_RLEG, elapsed);
        rightLeg.xScale = rLegScale[0]; rightLeg.yScale = rLegScale[1]; rightLeg.zScale = rLegScale[2];

        float[] lLeg = interp(T_S2_LLEG, R_S2_LLEG, elapsed);
        leftLeg.xRot = (float) Math.toRadians(lLeg[0]);
        leftLeg.yRot = (float) Math.toRadians(lLeg[1]);
        leftLeg.zRot = (float) Math.toRadians(lLeg[2]);
        float[] lLegPos = interp(T_S2_LLEG_POS, P_S2_LLEG, elapsed);
        leftLeg.x = REST_LLEG_X + lLegPos[0];
        leftLeg.y = REST_LLEG_Y - lLegPos[1];
        leftLeg.z = REST_LLEG_Z + lLegPos[2];
        float[] lLegScale = interp(T_S2_LLEG_SCALE, SC_S2_LLEG, elapsed);
        leftLeg.xScale = lLegScale[0]; leftLeg.yScale = lLegScale[1]; leftLeg.zScale = lLegScale[2];

    }

    static float[] interp(float[] times, float[][] values, float t) {
        if (t <= times[0]) return values[0];
        if (t >= times[times.length - 1]) return values[values.length - 1];
        for (int i = 0; i < times.length - 1; i++) {
            if (t >= times[i] && t <= times[i + 1]) {
                float f = (t - times[i]) / (times[i + 1] - times[i]);
                float[] a = values[i], b = values[i + 1];
                return new float[]{
                        a[0] + (b[0] - a[0]) * f,
                        a[1] + (b[1] - a[1]) * f,
                        a[2] + (b[2] - a[2]) * f
                };
            }
        }
        return values[values.length - 1];
    }
}
