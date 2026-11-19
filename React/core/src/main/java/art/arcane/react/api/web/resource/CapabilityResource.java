package art.arcane.react.api.web.resource;

import art.arcane.react.api.web.dto.Envelope;
import io.javalin.http.Context;

import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

/** {@code GET /api/v1/ping}: open, answers whether this is the paired server and what it is called. */
public final class CapabilityResource {

    public static final int PROTOCOL_VERSION = 3;

    private final Supplier<String> fingerprintSupplier;
    private final Supplier<String> serverNameSupplier;
    private final BooleanSupplier relayAvailableSupplier;

    public CapabilityResource(Supplier<String> fingerprintSupplier, Supplier<String> serverNameSupplier, BooleanSupplier relayAvailableSupplier) {
        this.fingerprintSupplier = fingerprintSupplier;
        this.serverNameSupplier = serverNameSupplier;
        this.relayAvailableSupplier = relayAvailableSupplier;
    }

    public record CapabilityDto(int protocolVersion, String serverFingerprint, String serverName, boolean relayAvailable) {}

    public void get(Context ctx) {
        CapabilityDto capability = new CapabilityDto(
            PROTOCOL_VERSION,
            fingerprintSupplier.get(),
            serverNameSupplier.get(),
            relayAvailableSupplier.getAsBoolean()
        );
        ctx.json(new Envelope<>(capability));
    }
}
