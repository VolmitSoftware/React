package art.arcane.react.nms.v26_2_R1;

import net.minecraft.world.entity.item.FallingBlockEntity;
import org.bukkit.entity.FallingBlock;

import java.lang.reflect.Method;

public final class FallingBlockTickRuntime {
    public static volatile Object HOOK;
    private static volatile Method decideMethod;

    private FallingBlockTickRuntime() {}

    public static void configure(Object hook) {
        HOOK = hook;
        decideMethod = resolveMethod(hook, "decide", FallingBlock.class);
    }

    public static boolean enter(FallingBlockEntity self) {
        Object hook = HOOK;
        Method decide = decideMethod;
        if (hook == null || decide == null) {
            return false;
        }
        try {
            FallingBlock bukkit = (FallingBlock) self.getBukkitEntity();
            if (bukkit == null) {
                return false;
            }
            Object decision = decide.invoke(hook, bukkit);
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
