package art.arcane.react.nms.v26_2_R1;

import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundExplodePacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.phys.Vec3;
import org.bukkit.World;

import java.lang.reflect.Method;

public final class ExplosionPacketSubstitution {
    public static volatile Object HOOK;
    private static volatile Method shouldSuppressMethod;

    private ExplosionPacketSubstitution() {}

    public static void configure(Object hook) {
        HOOK = hook;
        shouldSuppressMethod = resolveMethod(hook, "shouldSuppress", World.class, double.class, double.class, double.class, float.class);
    }

    public static void maybeSend(ServerGamePacketListenerImpl connection, Packet<?> packet) {
        Object suppressor = HOOK;
        Method shouldSuppress = shouldSuppressMethod;
        if (suppressor != null && shouldSuppress != null && packet instanceof ClientboundExplodePacket explode) {
            World world = resolveWorld(connection);
            if (world != null) {
                Vec3 center = explode.center();
                try {
                    Object result = shouldSuppress.invoke(suppressor, world, center.x(), center.y(), center.z(), explode.radius());
                    if (Boolean.TRUE.equals(result)) {
                        return;
                    }
                } catch (Throwable ignored) {
                }
            }
        }
        connection.send(packet);
    }

    private static World resolveWorld(ServerGamePacketListenerImpl connection) {
        try {
            ServerPlayer player = connection.player;
            if (player == null) {
                return null;
            }
            return player.level().getWorld();
        } catch (Throwable ignored) {
            return null;
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
