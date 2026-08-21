package art.arcane.react.api.web.resource;

import art.arcane.react.api.web.dto.Envelope;
import io.javalin.http.Context;

import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

public final class CapabilityResource {

    public static final int PROTOCOL_VERSION = 2;

    private final Supplier<String> fingerprintSupplier;
    private final BooleanSupplier relayAvailableSupplier;

    public CapabilityResource(Supplier<String> fingerprintSupplier, BooleanSupplier relayAvailableSupplier) {
        this.fingerprintSupplier = fingerprintSupplier;
        this.relayAvailableSupplier = relayAvailableSupplier;
    }

    public record CapabilityDto(int protocolVersion, String serverFingerprint, boolean relayAvailable) {}

    public void get(Context ctx) {
        CapabilityDto capability = new CapabilityDto(
            PROTOCOL_VERSION,
            fingerprintSupplier.get(),
            relayAvailableSupplier.getAsBoolean()
        );
        ctx.json(new Envelope<>(capability));
    }
}
