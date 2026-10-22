package net.icegiant.block;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import software.bernie.geckolib.animatable.GeoBlockEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.core.object.PlayState;
import software.bernie.geckolib.util.GeckoLibUtil;

/**
 * Sits in the bottom middle cell of a dungeon door and plays the door's GeckoLib animations.
 * <p>
 * Animation names in animations/dungeon_door.animation.json:
 *   animation.dungeon_door.open    - the wings swing open (held on the last frame)
 *   animation.dungeon_door.close   - the wings swing shut  (held on the last frame)
 *   animation.dungeon_door.opened  - standing open   (used when the door comes into view already open)
 *   animation.dungeon_door.closed  - standing closed (used when the door comes into view already closed)
 * so a door does not swing every time its chunk is loaded.
 */
public class DungeonDoorBlockEntity extends BlockEntity implements GeoBlockEntity {

    private static final RawAnimation OPEN = RawAnimation.begin().thenPlayAndHold("animation.dungeon_door.open");
    private static final RawAnimation CLOSE = RawAnimation.begin().thenPlayAndHold("animation.dungeon_door.close");
    private static final RawAnimation OPENED = RawAnimation.begin().thenPlayAndHold("animation.dungeon_door.opened");
    private static final RawAnimation CLOSED = RawAnimation.begin().thenPlayAndHold("animation.dungeon_door.closed");

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    private Boolean shownOpen;          // client only: what the model shows right now
    private RawAnimation current = CLOSED;

    public DungeonDoorBlockEntity(BlockPos pos, BlockState state) {
        super(DungeonDoorBlock.type(), pos, state);
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "door", 0, state -> {
            boolean open = this.getBlockState().hasProperty(DungeonDoorBlock.OPEN)
                    && this.getBlockState().getValue(DungeonDoorBlock.OPEN);
            if (this.shownOpen == null) {
                this.current = open ? OPENED : CLOSED;
            } else if (this.shownOpen != open) {
                this.current = open ? OPEN : CLOSE;
            }
            this.shownOpen = open;
            return state.setAndContinue(this.current);
        }));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return this.cache;
    }

    /** The model is 3 x 6 blocks and the open wings reach 1.5 blocks out: don't cull it by the one cell. */
    @Override
    public AABB getRenderBoundingBox() {
        BlockPos p = this.getBlockPos();
        return new AABB(p.getX() - 2, p.getY(), p.getZ() - 2, p.getX() + 3, p.getY() + 7, p.getZ() + 3);
    }
}
