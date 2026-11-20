package art.arcane.react.mod.mixin;

import art.arcane.react.mod.ModAwareness;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.GoalSelector;
import net.minecraft.world.entity.ai.goal.WrappedGoal;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Mob.class)
public abstract class MobHooks implements ModAwareness {
    @Shadow
    @Final
    protected GoalSelector goalSelector;

    @Unique
    private boolean react$aware = true;

    @Override
    public boolean react$isAware() {
        return react$aware;
    }

    @Override
    public void react$setAware(boolean aware) {
        react$aware = aware;
    }

    @Inject(method = "serverAiStep", at = @At("HEAD"), cancellable = true, require = 0)
    private void react$skipUnawareAi(CallbackInfo callback) {
        if (react$aware) {
            return;
        }
        Mob mob = (Mob) (Object) this;
        Goal floating = react$floatGoal();
        if (floating != null) {
            if (floating.canUse()) {
                floating.tick();
            }
            mob.getJumpControl().tick();
        }
        callback.cancel();
    }

    @Unique
    private Goal react$floatGoal() {
        for (WrappedGoal wrapped : goalSelector.getAvailableGoals()) {
            if (wrapped.getGoal() instanceof FloatGoal) {
                return wrapped.getGoal();
            }
        }
        return null;
    }
}
