package net.icegiant.slime;

import net.icegiant.mixin.SlimeInvoker;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.MoveControl;
import net.minecraft.world.entity.monster.MagmaCube;
import net.minecraft.world.entity.monster.Slime;

/**
 * How our slimes move (Rubenbuben4, 2026-10-27: "Slimes müssen auch nicht nur springen, sie sollen sich jetzt auch
 * normal auf dem Boden bewegen"). Replaces vanilla's slime move control, which only moves while jumping.
 * <ul>
 *   <li><b>CRAWL</b>: slides along the ground like a normal mob, following the path navigation; climbs a block with a
 *       small hop when something is in the way. Slow: {@value #CRAWL_BASE} + {@value #CRAWL_PER_SIZE} x size.</li>
 *   <li><b>HOP</b>: vanilla-style hopping in a direction (chasing, now and then while wandering) - jumps after a short
 *       wait, moves only while in the air. Magma cubes wait longer, like in vanilla.</li>
 * </ul>
 */
public class SlimeControl extends MoveControl {

    public enum Mode { CRAWL, HOP }

    static final float CRAWL_BASE = 0.09F, CRAWL_PER_SIZE = 0.025F;

    private final Slime slime;
    private Mode mode = Mode.CRAWL;
    private float hopYaw;
    private boolean hopEager;
    private int hopDelay;

    public SlimeControl(Slime slime) {
        super(slime);
        this.slime = slime;
        this.hopYaw = slime.getYRot();
    }

    public Mode mode() {
        return this.mode;
    }

    /** Crawl (the navigation sets where to). */
    public void crawl() {
        this.mode = Mode.CRAWL;
    }

    /** Hop in this direction; eager hops come quicker (chasing). Must be called every tick while hopping. */
    public void hop(float yaw, boolean eager, double speed) {
        this.mode = Mode.HOP;
        this.hopYaw = yaw;
        this.hopEager = eager;
        this.speedModifier = speed;
        this.operation = Operation.MOVE_TO;
    }

    public void halt() {
        this.operation = Operation.WAIT;
        this.mob.setZza(0.0F);
        this.mob.setSpeed(0.0F);
    }

    private float crawlSpeed() {
        float s = CRAWL_BASE + CRAWL_PER_SIZE * Math.min(4, this.slime.getSize());
        return this.slime instanceof MagmaCube ? s * 0.8F : s;
    }

    @Override
    public void tick() {
        if (this.mode == Mode.HOP) {
            this.tickHop();
            return;
        }
        if (this.operation != Operation.MOVE_TO) {
            this.mob.setZza(0.0F);
            return;
        }
        this.operation = Operation.WAIT;
        double dx = this.wantedX - this.mob.getX(), dz = this.wantedZ - this.mob.getZ(), dy = this.wantedY - this.mob.getY();
        double d2 = dx * dx + dz * dz;
        if (d2 < 2.5E-5D) {
            this.mob.setZza(0.0F);
            return;
        }
        float yaw = (float) (Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90.0F;
        this.mob.setYRot(this.rotlerp(this.mob.getYRot(), yaw, 20.0F));
        this.mob.setYBodyRot(this.mob.getYRot());
        this.mob.setYHeadRot(this.mob.getYRot());
        this.mob.setSpeed((float) (this.speedModifier * this.crawlSpeed()));
        // a block in the way: a small hop up (like vanilla's walking control)
        boolean blocked = this.mob.horizontalCollision && this.mob.onGround();
        if (blocked || (dy > this.mob.maxUpStep() && d2 < Math.max(1.0D, this.mob.getBbWidth() * this.mob.getBbWidth()))) {
            this.mob.getJumpControl().jump();
        }
    }

    private void tickHop() {
        this.mob.setYRot(this.rotlerp(this.mob.getYRot(), this.hopYaw, 90.0F));
        this.mob.setYHeadRot(this.mob.getYRot());
        this.mob.setYBodyRot(this.mob.getYRot());
        if (this.operation != Operation.MOVE_TO) {
            this.mob.setZza(0.0F);
            return;
        }
        this.operation = Operation.WAIT;
        float speed = (float) (this.speedModifier * this.mob.getAttributeValue(Attributes.MOVEMENT_SPEED));
        if (this.mob.onGround()) {
            this.mob.setSpeed(0.0F);
            if (this.hopDelay-- <= 0) {
                SlimeInvoker inv = (SlimeInvoker) this.slime;
                this.hopDelay = inv.icegiant$getJumpDelay();
                if (this.hopEager) this.hopDelay /= 3;
                this.mob.getJumpControl().jump();
                this.mob.setSpeed(speed);
                this.slime.playSound(inv.icegiant$getJumpSound(), inv.icegiant$getSoundVolume(),
                        ((this.slime.getRandom().nextFloat() - this.slime.getRandom().nextFloat()) * 0.2F + 1.0F) * 0.8F);
            } else {
                this.mob.xxa = 0.0F;
                this.mob.zza = 0.0F;
            }
        } else {
            this.mob.setSpeed(speed);
        }
    }
}
