package art.arcane.react.nms.v26_1_R1;

import art.arcane.react.nms.ExplosionHook;
import net.bytebuddy.asm.Advice;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ServerExplosion;
import net.minecraft.world.phys.Vec3;
import org.bukkit.Location;
import org.bukkit.World;

public final class ExplosionAdvice {
    public static volatile ExplosionHook HOOK;

    private ExplosionAdvice() {}

    @Advice.OnMethodEnter
    public static void enter(@Advice.This ServerExplosion self) {
        ExplosionHook hook = HOOK;
        if (hook == null) {
            return;
        }
        try {
            ServerLevel level = self.level();
            World bukkit = level.getWorld();
            Vec3 center = self.center();
            Location location = new Location(bukkit, center.x(), center.y(), center.z());
            hook.observe(location);
        } catch (Throwable ignored) {
        }
    }
}
