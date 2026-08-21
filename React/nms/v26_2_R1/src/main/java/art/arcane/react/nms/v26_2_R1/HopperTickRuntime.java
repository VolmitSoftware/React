package art.arcane.react.nms.v26_2_R1;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.bukkit.World;

import java.lang.reflect.Method;

public final class HopperTickRuntime {
    public static volatile Object HOOK;
    private static volatile Method decideMethod;

    private HopperTickRuntime() {}

    public static void configure(Object hook) {
        HOOK = hook;
        decideMethod = resolveMethod(hook, "decide", World.class, int.class, int.class, int.class);
    }

    public static boolean enter(Level level, BlockPos pos, BlockState state) {
        Object hook = HOOK;
        Method decide = decideMethod;
        if (hook == null || decide == null) {
            return false;
        }
        if (!(level instanceof ServerLevel server)) {
            return false;
        }
        try {
            World bukkit = server.getWorld();
            Object decision = decide.invoke(hook, bukkit, pos.getX(), pos.getY(), pos.getZ());
            return decision != null && "SKIP".equals(decision.toString());
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static Method resolveMethod(Object target, String name, Class<?>... parameterTypes) {
        if (target == null) {
            return null;
        }
        try {
            Method method = target.getClass().getMethod(name, parameterTypes);
            method.setAccessible(true);
            return method;
        } catch (Throwable ignored) {
            return null;
        }
    }
}
