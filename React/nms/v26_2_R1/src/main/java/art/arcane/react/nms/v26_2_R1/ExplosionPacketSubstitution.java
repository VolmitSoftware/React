package art.arcane.react.nms.v26_2_R1;

import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundExplodePacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.phys.Vec3;
import org.bukkit.World;

import java.lang.reflect.Method;
import java.util.ArrayDeque;
import java.util.ArrayList;

public final class ExplosionPacketSubstitution {
    public static volatile Object HOOK;
    private static final ThreadLocal<ArrayDeque<ArrayList<Object>>> EXPLOSION_SCOPES =
            new ThreadLocal<>();
    private static volatile Method shouldSuppressMethod;

    private ExplosionPacketSubstitution() {}

    public static void configure(Object hook) {
        HOOK = hook;
        shouldSuppressMethod = resolveMethod(
                hook,
                "shouldSuppress",
                World.class,
                double.class,
                double.class,
                double.class,
                float.class,
                int.class
        );
    }

    public static void enterExplosion() {
        ArrayDeque<ArrayList<Object>> scopes = EXPLOSION_SCOPES.get();
        if (scopes == null) {
            scopes = new ArrayDeque<>();
            EXPLOSION_SCOPES.set(scopes);
        }
        scopes.addLast(new ArrayList<>());
    }

    public static void exitExplosion() {
        ArrayDeque<ArrayList<Object>> scopes = EXPLOSION_SCOPES.get();
        if (scopes == null) {
            return;
        }
        ArrayList<Object> packets = scopes.isEmpty() ? null : scopes.removeLast();
        try {
            if (packets != null) {
                flushExplosionPackets(packets);
            }
        } finally {
            if (scopes.isEmpty()) {
                EXPLOSION_SCOPES.remove();
            }
        }
    }

    public static void maybeSend(ServerGamePacketListenerImpl connection, Packet<?> packet) {
        ArrayDeque<ArrayList<Object>> scopes = EXPLOSION_SCOPES.get();
        if (packet instanceof ClientboundExplodePacket && scopes != null && !scopes.isEmpty()) {
            ArrayList<Object> packets = scopes.peekLast();
            packets.add(connection);
            packets.add(packet);
            return;
        }
        connection.send(packet);
    }

    private static void flushExplosionPackets(ArrayList<Object> packets) {
        if (packets.isEmpty()) {
            return;
        }

        boolean hasPlayerKnockback = false;
        for (int index = 1; index < packets.size(); index += 2) {
            ClientboundExplodePacket explode = (ClientboundExplodePacket) packets.get(index);
            if (explode.playerKnockback().isPresent()) {
                hasPlayerKnockback = true;
                break;
            }
        }

        Object suppressor = HOOK;
        Method shouldSuppress = shouldSuppressMethod;
        boolean suppress = false;
        if (!hasPlayerKnockback && suppressor != null && shouldSuppress != null) {
            ServerGamePacketListenerImpl connection = (ServerGamePacketListenerImpl) packets.get(0);
            ClientboundExplodePacket explode = (ClientboundExplodePacket) packets.get(1);
            suppress = shouldSuppress(
                    suppressor,
                    shouldSuppress,
                    connection,
                    explode,
                    packets.size() / 2
            );
        }
        if (suppress) {
            return;
        }

        for (int index = 0; index < packets.size(); index += 2) {
            ServerGamePacketListenerImpl connection = (ServerGamePacketListenerImpl) packets.get(index);
            Packet<?> packet = (Packet<?>) packets.get(index + 1);
            connection.send(packet);
        }
    }

    private static boolean shouldSuppress(
            Object suppressor,
            Method shouldSuppress,
            ServerGamePacketListenerImpl connection,
            ClientboundExplodePacket explode,
            int packetCount
    ) {
        World world = resolveWorld(connection);
        if (world == null) {
            return false;
        }

        Vec3 center = explode.center();
        try {
            Object result = shouldSuppress.invoke(
                    suppressor,
                    world,
                    center.x(),
                    center.y(),
                    center.z(),
                    explode.radius(),
                    packetCount
            );
            return Boolean.TRUE.equals(result);
        } catch (Throwable ignored) {
            return false;
        }
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
