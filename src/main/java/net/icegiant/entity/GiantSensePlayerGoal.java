package net.icegiant.entity;

import net.icegiant.network.AwarenessPacket;
import net.icegiant.network.NetworkHandler;
import net.icegiant.stealth.PlayerNoise;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.target.TargetGoal;
import net.minecraft.world.entity.ai.targeting.TargetingConditions;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.PacketDistributor;

import javax.annotation.Nullable;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/**
 * How an Ice Giant notices players - so you can sneak past him (Rubenbuben4, 2026-10-26; before: noticed at once,
 * sneaking only within 5 blocks).
 * <p>
 * Per player an awareness of 0..100 rises while he sees or hears you and slowly sinks when he doesn't:
 * <ul>
 *   <li><b>Seeing</b>: only in front of him (120 degree field of view) with a clear line of sight, up to
 *       {@value #SIGHT_RANGE} blocks, less the further away; in the dark down to 30 %, in a snowstorm 55 %,
 *       sneaking 35 %; invisibility counts like for vanilla mobs.</li>
 *   <li><b>Hearing</b>: your noise (PlayerNoise - sprinting, walking, jumping, breaking blocks, ...) carries
 *       {@value #HEAR_RANGE} blocks at full loudness, less in a storm; through a wall up to 2 blocks thick, muffled.</li>
 *   <li><b>Feeling</b>: right next to him (within {@value #TOUCH_RANGE} blocks) he notices you anyway, sneaking slower.</li>
 * </ul>
 * From {@value #SUSPICIOUS} he is suspicious (stops, turns to where it came from), from {@value #SEARCHING} he
 * searches (walks there, GiantInvestigateGoal), at 100 he attacks (target + war cry). Each player is sent his own
 * awareness for the eye on the screen (AwarenessPacket / AwarenessHud). Creative and spectators are ignored.
 */
public class GiantSensePlayerGoal extends TargetGoal {

    public static final int MAX_WALL_BLOCKS = 2;
    public static final float SUSPICIOUS = 25.0F, SEARCHING = 60.0F, ALERT = 100.0F;
    static final double SIGHT_RANGE = 32.0D, HEAR_RANGE = 30.0D, TOUCH_RANGE = 3.5D;
    static final double FOV_COS = Math.cos(Math.toRadians(60.0D));       // half of the 120 degree field of view
    static final float SIGHT_GAIN = 120.0F, HEAR_GAIN = 90.0F, TOUCH_GAIN = 150.0F, TOUCH_GAIN_SNEAK = 40.0F;  // per s
    static final float DECAY = 12.0F;                                    // per second when nothing is noticed
    static final float FORGET = 6.0F;                                    // per second, always (faint signals fade)
    static final float SNEAK_SIGHT = 0.35F, STORM = 0.55F, DARK = 0.3F, WALL_MUFFLE = 0.5F;
    static final int CHECK_INTERVAL = 5;                                 // ticks (0.25 s)

    private static final class Sense {
        float awareness;
        Vec3 lastPos;
        float sent = -1.0F;
    }

    private final Map<UUID, Sense> senses = new HashMap<>();
    @Nullable private Player candidate;
    @Nullable private UUID focus;
    @Nullable private UUID fought;
    static final float LOST_TARGET = 80.0F;     // after losing a target: searching for him

    public GiantSensePlayerGoal(Mob mob) {
        super(mob, false);                                      // no line of sight needed
        this.setFlags(EnumSet.of(Flag.TARGET));
    }

    // ------------------------------------------------------------------ for GiantInvestigateGoal

    /** Highest awareness of any player right now (0..100). */
    public float focusAwareness() {
        Sense s = this.focus == null ? null : this.senses.get(this.focus);
        return s == null ? 0.0F : s.awareness;
    }

    /** Where the most noticed player was last seen or heard. */
    @Nullable
    public Vec3 focusPos() {
        Sense s = this.focus == null ? null : this.senses.get(this.focus);
        return s == null ? null : s.lastPos;
    }

    /** He was hit by / lost this player: set how aware he is of him. */
    public void setAwareness(Player p, float value) {
        Sense s = this.senses.computeIfAbsent(p.getUUID(), k -> new Sense());
        s.awareness = Math.max(s.awareness, value);
        s.lastPos = p.position();
    }

    // ------------------------------------------------------------------ goal

    /** Called by the giant every tick (server), also while he fights - so the eye on the screen stays right. */
    public void tickSenses() {
        if (this.mob.tickCount % CHECK_INTERVAL != 0 || !(this.mob.level() instanceof ServerLevel level)) return;
        this.update(level);
    }

    @Override
    public boolean canUse() {
        return this.candidate != null && this.mob.getTarget() == null && this.candidate.isAlive();
    }

    private void update(ServerLevel level) {
        // a fight just ended (target lost, not dead): he goes looking where he last noticed him
        net.minecraft.world.entity.LivingEntity target = this.mob.getTarget();
        if (this.fought != null && (target == null || !target.getUUID().equals(this.fought))) {
            Sense f = this.senses.get(this.fought);
            if (f != null) f.awareness = Math.min(f.awareness, LOST_TARGET);
            this.fought = null;
        }
        if (target instanceof Player tp) this.fought = tp.getUUID();

        float dt = CHECK_INTERVAL / 20.0F;
        double range = Math.max(SIGHT_RANGE, this.mob.getAttributeValue(Attributes.FOLLOW_RANGE));
        boolean storm = net.icegiant.weather.Snowstorms.isStorming(level);
        this.candidate = null;
        this.focus = null;
        float best = 0.0F;
        for (Player p : level.players()) {
            Sense s = this.senses.get(p.getUUID());
            double d = this.mob.distanceTo(p);
            boolean valid = p.isAlive() && !p.isCreative() && !p.isSpectator() && d <= range + 8.0D
                    && this.canAttack(p, TargetingConditions.forCombat().ignoreLineOfSight().ignoreInvisibilityTesting());
            if (!valid) {
                if (s != null) s.awareness = 0.0F;
            } else {
                if (s == null) this.senses.put(p.getUUID(), s = new Sense());
                float gain = this.gain(level, p, d, storm);
                if (gain > 0.0F) {                           // weak signals (below FORGET per s) never build up
                    s.awareness = Math.max(0.0F, Math.min(ALERT, s.awareness + (gain - FORGET) * dt));
                    s.lastPos = p.position();
                } else {
                    s.awareness = Math.max(0.0F, s.awareness - DECAY * dt);
                }
                if (this.mob.getTarget() == p) s.awareness = ALERT;          // fighting him: fully aware
                if (s.awareness > best) {
                    best = s.awareness;
                    this.focus = p.getUUID();
                }
                if (s.awareness >= ALERT && this.mob.getTarget() == null) this.candidate = p;
            }
            if (s != null && p instanceof ServerPlayer sp) this.sync(sp, s);
        }
        // forget players who left
        Iterator<Map.Entry<UUID, Sense>> it = this.senses.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, Sense> e = it.next();
            if (e.getValue().awareness <= 0.0F && level.getPlayerByUUID(e.getKey()) == null) it.remove();
        }
    }

    /** Awareness per second this player causes right now. */
    private float gain(ServerLevel level, Player p, double d, boolean storm) {
        float weather = storm ? STORM : 1.0F;
        Vec3 eye = this.mob.getEyePosition();
        Vec3 to = p.getEyePosition().subtract(eye);
        double len = to.length();
        boolean lineOfSight = this.mob.hasLineOfSight(p);
        float total = 0.0F;

        // seeing: in front of him, nothing in the way
        if (lineOfSight && d <= SIGHT_RANGE && len > 1.0E-4D) {
            double cos = this.mob.getViewVector(1.0F).dot(to.scale(1.0D / len));
            if (cos >= FOV_COS) {
                float light = level.getMaxLocalRawBrightness(BlockPos.containing(p.getEyePosition())) / 15.0F;
                float sight = (float) (1.0D - d / SIGHT_RANGE) * (DARK + (1.0F - DARK) * light) * weather
                        * (p.isCrouching() ? SNEAK_SIGHT : 1.0F) * (float) p.getVisibilityPercent(this.mob);
                total += SIGHT_GAIN * sight;
            }
        }
        // hearing: louder noise carries further; walls muffle
        float noise = PlayerNoise.of(p);
        if (noise > 0.0F) {
            double reach = HEAR_RANGE * noise * weather;
            if (d < reach) {
                float hear = (float) (1.0D - d / reach);
                if (!lineOfSight) {
                    int walls = wallThickness(eye, p.getEyePosition());
                    hear = walls > MAX_WALL_BLOCKS ? 0.0F : hear * (float) Math.pow(WALL_MUFFLE, walls);
                }
                total += HEAR_GAIN * hear;
            }
        }
        // feeling: right next to him
        if (d <= TOUCH_RANGE) total += p.isCrouching() ? TOUCH_GAIN_SNEAK : TOUCH_GAIN;
        return total;
    }

    /** Tells the player how aware this giant is of him (only when it changed). */
    private void sync(ServerPlayer player, Sense s) {
        float v = s.awareness / ALERT;
        if (Math.abs(v - s.sent) < 0.02F && !(v == 0.0F && s.sent != 0.0F)) return;
        s.sent = v;
        NetworkHandler.INSTANCE.send(PacketDistributor.PLAYER.with(() -> player), new AwarenessPacket(this.mob.getId(), v));
    }

    /** Solid blocks between the two points (stops counting once it is too thick). */
    private int wallThickness(Vec3 from, Vec3 to) {
        Vec3 d = to.subtract(from);
        double len = d.length();
        int steps = (int) Math.ceil(len / 0.25D);
        BlockPos last = null;
        int count = 0;
        for (int i = 1; i < steps; i++) {
            Vec3 at = from.add(d.scale(i / (double) steps));
            BlockPos pos = BlockPos.containing(at);
            if (pos.equals(last)) continue;
            last = pos;
            BlockState state = this.mob.level().getBlockState(pos);
            if (!state.getCollisionShape(this.mob.level(), pos).isEmpty() && state.canOcclude()) {
                if (++count > MAX_WALL_BLOCKS) return count;
            }
        }
        return count;
    }

    @Override
    public void start() {
        this.mob.setTarget(this.candidate);
        super.start();
    }
}
