package art.arcane.react.nms.v26_1_R1;

import art.arcane.react.nms.BrewingTickHook;
import art.arcane.react.nms.BrewingTickResult;
import net.bytebuddy.asm.Advice;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BrewingStandBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.bukkit.World;

public final class BrewingTickAdvice {
    public static volatile BrewingTickHook HOOK;

    private BrewingTickAdvice() {}

    @Advice.OnMethodEnter(skipOn = Advice.OnNonDefaultValue.class)
    public static boolean enter(@Advice.Argument(0) Level level,
                                @Advice.Argument(1) BlockPos pos,
                                @Advice.Argument(2) BlockState state,
                                @Advice.Argument(3) BrewingStandBlockEntity entity) {
        BrewingTickHook hook = HOOK;
        if (hook == null) {
            return false;
        }
        if (!(level instanceof ServerLevel server)) {
            return false;
        }
        try {
            World bukkit = server.getWorld();
            BrewingTickResult result = hook.decide(bukkit, pos.getX(), pos.getY(), pos.getZ());
            if (result == null) {
                return false;
            }
            if (result.skip()) {
                return true;
            }
            int advance = result.advanceTicks();
            if (advance > 0) {
                int currentBrew = entity.brewTime;
                if (currentBrew > 0) {
                    entity.brewTime = Math.max(1, currentBrew - advance);
                }
            }
            return false;
        } catch (Throwable ignored) {
            return false;
        }
    }
}
