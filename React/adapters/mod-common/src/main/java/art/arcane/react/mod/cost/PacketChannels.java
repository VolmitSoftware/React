package art.arcane.react.mod.cost;

import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket;

/** The channel namespace of a custom payload packet. */
public final class PacketChannels {
    private PacketChannels() {
    }

    /** The payload channel's namespace, or null for any other packet. */
    public static String namespace(Packet<?> packet) {
        if (packet instanceof ClientboundCustomPayloadPacket custom) {
            return custom.payload().type().id().getNamespace();
        }
        if (packet instanceof ServerboundCustomPayloadPacket custom) {
            return custom.payload().type().id().getNamespace();
        }
        return null;
    }
}
