package art.arcane.react.mod.mixin;

import art.arcane.react.mod.cost.NetworkTraffic;
import art.arcane.react.mod.cost.PacketChannels;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import net.minecraft.network.PacketDecoder;
import net.minecraft.network.protocol.Packet;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

/**
 * Counts each decoded packet under its channel namespace. The frame's size is read before decoding; a decoder belongs to
 * one connection and runs on its event loop, so the fields need no synchronisation.
 */
@Mixin(PacketDecoder.class)
public abstract class PacketDecoderHooks {
    @Unique
    private int react$frameBytes;
    @Unique
    private int react$decodedBefore;

    @Inject(method = "decode(Lio/netty/channel/ChannelHandlerContext;Lio/netty/buffer/ByteBuf;Ljava/util/List;)V", at = @At("HEAD"), require = 0)
    private void react$frame(ChannelHandlerContext context, ByteBuf input, List<Object> output, CallbackInfo callback) {
        react$frameBytes = input.readableBytes();
        react$decodedBefore = output.size();
    }

    @Inject(method = "decode(Lio/netty/channel/ChannelHandlerContext;Lio/netty/buffer/ByteBuf;Ljava/util/List;)V", at = @At("RETURN"), require = 0)
    private void react$decoded(ChannelHandlerContext context, ByteBuf input, List<Object> output, CallbackInfo callback) {
        if (NetworkTraffic.active() && output.size() > react$decodedBefore && output.get(output.size() - 1) instanceof Packet<?> packet) {
            NetworkTraffic.received(PacketChannels.namespace(packet), react$frameBytes - input.readableBytes());
        }
    }
}
