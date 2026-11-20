package art.arcane.react.mod;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.util.Objects;

/** The raw frame of the optional {@code react:maps} channel; vanilla clients never receive it. */
public record ReactMapsPayload(byte[] data) implements CustomPacketPayload {
    public static final int MAX_BYTES = 1 << 20;
    public static final Type<ReactMapsPayload> TYPE = new Type<>(VanillaApi.id(ReactModRuntime.ID, "maps"));
    public static final StreamCodec<FriendlyByteBuf, ReactMapsPayload> CODEC = CustomPacketPayload.codec(
            (ReactMapsPayload payload, FriendlyByteBuf buffer) -> buffer.writeBytes(payload.data()), ReactMapsPayload::read);

    public ReactMapsPayload {
        Objects.requireNonNull(data, "data");
    }

    private static ReactMapsPayload read(FriendlyByteBuf buffer) {
        int length = buffer.readableBytes();
        if (length > MAX_BYTES) {
            throw new IllegalArgumentException("React map payload of " + length + " bytes exceeds " + MAX_BYTES);
        }
        byte[] bytes = new byte[length];
        buffer.readBytes(bytes);
        return new ReactMapsPayload(bytes);
    }

    @Override
    public Type<ReactMapsPayload> type() {
        return TYPE;
    }
}
