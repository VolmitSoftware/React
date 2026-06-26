package art.arcane.react.nms.v26_2_R1;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BrewingStandBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.bukkit.World;

import java.lang.reflect.Method;

public final class BrewingTickRuntime {
    public static volatile Object HOOK;
    private static volatile Method decideMethod;
    private static volatile Method skipMethod;
    private static volatile Method advanceTicksMethod;

    private BrewingTickRuntime() {}

    public static void configure(Object hook) {
        HOOK = hook;
        decideMethod = resolveMethod(hook, "decide", World.class, int.class, int.class, int.class);
        skipMethod = null;
        advanceTicksMethod = null;
    }

    public static boolean enter(Level level, BlockPos pos, BlockState state, BrewingStandBlockEntity entity) {
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
            Object result = decide.invoke(hook, bukkit, pos.getX(), pos.getY(), pos.getZ());
            if (result == null) {
                return false;
            }
            if (resultSkip(result)) {
                return true;
            }
            int advance = resultAdvanceTicks(result);
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

    private static boolean resultSkip(Object result) throws Exception {
        Method method = skipMethod;
        if (method == null) {
            method = result.getClass().getMethod("skip");
            method.setAccessible(true);
            skipMethod = method;
        }
        return Boolean.TRUE.equals(method.invoke(result));
    }

    private static int resultAdvanceTicks(Object result) throws Exception {
        Method method = advanceTicksMethod;
        if (method == null) {
            method = result.getClass().getMethod("advanceTicks");
            method.setAccessible(true);
            advanceTicksMethod = method;
        }
        Object value = method.invoke(result);
        return value instanceof Number number ? number.intValue() : 0;
    }
}
