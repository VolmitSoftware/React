package art.arcane.react.nms.v26_1_R1;

import art.arcane.react.nms.ExplosionPacketSuppressor;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundExplodePacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.phys.Vec3;
import org.bukkit.World;

public final class ExplosionPacketSubstitution {
    public static volatile ExplosionPacketSuppressor HOOK;

    private ExplosionPacketSubstitution() {}

    public static void maybeSend(ServerGamePacketListenerImpl connection, Packet<?> packet) {
        ExplosionPacketSuppressor suppressor = HOOK;
        if (suppressor != null && packet instanceof ClientboundExplodePacket explode) {
            World world = resolveWorld(connection);
            if (world != null) {
                Vec3 center = explode.center();
                try {
                    if (suppressor.shouldSuppress(world, center.x(), center.y(), center.z(), explode.radius())) {
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
}
