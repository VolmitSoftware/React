package art.arcane.react.mod.mixin;

import art.arcane.react.mod.cost.NetworkTraffic;
import art.arcane.react.mod.cost.PacketChannels;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import net.minecraft.network.PacketEncoder;
import net.minecraft.network.protocol.Packet;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Counts each encoded packet's size, already known from the output buffer, under its channel namespace. */
@Mixin(PacketEncoder.class)
public abstract class PacketEncoderHooks {
    @Inject(method = "encode(Lio/netty/channel/ChannelHandlerContext;Lnet/minecraft/network/protocol/Packet;Lio/netty/buffer/ByteBuf;)V",
            at = @At("TAIL"), require = 0)
    private void react$encoded(ChannelHandlerContext context, Packet<?> packet, ByteBuf output, CallbackInfo callback) {
        if (NetworkTraffic.active()) {
            NetworkTraffic.sent(PacketChannels.namespace(packet), output.readableBytes());
        }
    }
}
