package art.arcane.react.nms.v26_1_R1;

import art.arcane.react.nms.HopperTickHook;
import art.arcane.react.nms.TickDecision;
import net.bytebuddy.asm.Advice;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.bukkit.World;

public final class HopperTickAdvice {
    public static volatile HopperTickHook HOOK;

    private HopperTickAdvice() {}

    @Advice.OnMethodEnter(skipOn = Advice.OnNonDefaultValue.class)
    public static boolean enter(@Advice.Argument(0) Level level,
                                @Advice.Argument(1) BlockPos pos,
                                @Advice.Argument(2) BlockState state) {
        HopperTickHook hook = HOOK;
        if (hook == null) {
            return false;
        }
        if (!(level instanceof ServerLevel server)) {
            return false;
        }
        try {
            World bukkit = server.getWorld();
            TickDecision decision = hook.decide(bukkit, pos.getX(), pos.getY(), pos.getZ());
            return decision == TickDecision.SKIP;
        } catch (Throwable ignored) {
            return false;
        }
    }
}
