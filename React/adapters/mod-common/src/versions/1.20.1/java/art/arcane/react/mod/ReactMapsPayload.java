package art.arcane.react.mod;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

import java.util.Objects;

/** The raw frame of the optional {@code react:maps} channel; 1.20.1 channels carry plain buffers, not payload types. */
public record ReactMapsPayload(byte[] data) {
    public static final int MAX_BYTES = 1 << 20;
    public static final ResourceLocation ID = VanillaApi.id(ReactModRuntime.ID, "maps");

    public ReactMapsPayload {
        Objects.requireNonNull(data, "data");
    }

    public static ReactMapsPayload read(FriendlyByteBuf buffer) {
        int length = buffer.readableBytes();
        if (length > MAX_BYTES) {
            throw new IllegalArgumentException("React map payload of " + length + " bytes exceeds " + MAX_BYTES);
        }
        byte[] bytes = new byte[length];
        buffer.readBytes(bytes);
        return new ReactMapsPayload(bytes);
    }

    public void write(FriendlyByteBuf buffer) {
        buffer.writeBytes(data);
    }
}
