package art.arcane.react.nms.v26_1_R1;

import art.arcane.react.nms.FurnaceTickHook;
import art.arcane.react.nms.FurnaceTickResult;
import net.bytebuddy.asm.Advice;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.bukkit.World;

public final class FurnaceTickAdvice {
    public static volatile FurnaceTickHook HOOK;

    private FurnaceTickAdvice() {}

    @Advice.OnMethodEnter(skipOn = Advice.OnNonDefaultValue.class)
    public static boolean enter(@Advice.Argument(0) Level level,
                                @Advice.Argument(1) BlockPos pos,
                                @Advice.Argument(2) BlockState state,
                                @Advice.Argument(3) AbstractFurnaceBlockEntity entity) {
        FurnaceTickHook hook = HOOK;
        if (hook == null) {
            return false;
        }
        if (!(level instanceof ServerLevel server)) {
            return false;
        }
        try {
            World bukkit = server.getWorld();
            FurnaceTickResult result = hook.decide(bukkit, pos.getX(), pos.getY(), pos.getZ());
            if (result == null) {
                return false;
            }
            if (result.skip()) {
                return true;
            }
            int advance = result.advanceTicks();
            if (advance > 0) {
                int cookTotal = entity.cookingTotalTime;
                if (cookTotal > 0) {
                    int cookingCurrent = entity.cookingTimer;
                    int cookingTarget = cookingCurrent + advance;
                    int cookingCap = Math.max(0, cookTotal - 1);
                    entity.cookingTimer = Math.min(cookingCap, cookingTarget);
                }
                int litRemaining = entity.litTimeRemaining;
                if (litRemaining > 0) {
                    entity.litTimeRemaining = Math.max(1, litRemaining - advance);
                }
            }
            return false;
        } catch (Throwable ignored) {
            return false;
        }
    }
}
