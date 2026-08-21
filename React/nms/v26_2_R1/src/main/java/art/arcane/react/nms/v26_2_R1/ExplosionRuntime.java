package art.arcane.react.nms.v26_2_R1;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ServerExplosion;
import net.minecraft.world.phys.Vec3;
import org.bukkit.Location;
import org.bukkit.World;

import java.lang.reflect.Method;

public final class ExplosionRuntime {
    public static volatile Object HOOK;
    private static volatile Method observeMethod;

    private ExplosionRuntime() {}

    public static void configure(Object hook) {
        HOOK = hook;
        observeMethod = resolveMethod(hook, "observe", Location.class);
    }

    public static void enter(ServerExplosion self) {
        Object hook = HOOK;
        Method observe = observeMethod;
        if (hook == null || observe == null) {
            return;
        }
        try {
            ServerLevel level = self.level();
            World bukkit = level.getWorld();
            Vec3 center = self.center();
            Location location = new Location(bukkit, center.x(), center.y(), center.z());
            observe.invoke(hook, location);
        } catch (Throwable ignored) {
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
