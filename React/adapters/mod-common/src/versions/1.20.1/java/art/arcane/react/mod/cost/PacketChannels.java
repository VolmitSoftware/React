package art.arcane.react.mod.cost;

import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundCustomPayloadPacket;
import net.minecraft.network.protocol.game.ServerboundCustomPayloadPacket;

/** The channel namespace of a custom payload packet. */
public final class PacketChannels {
    private PacketChannels() {
    }

    /** The payload channel's namespace, or null for any other packet. */
    public static String namespace(Packet<?> packet) {
        if (packet instanceof ClientboundCustomPayloadPacket custom) {
            return custom.getIdentifier().getNamespace();
        }
        if (packet instanceof ServerboundCustomPayloadPacket custom) {
            return custom.getIdentifier().getNamespace();
        }
        return null;
    }
}
