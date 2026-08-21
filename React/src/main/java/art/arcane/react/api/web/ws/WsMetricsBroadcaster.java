package art.arcane.react.api.web.ws;

import art.arcane.react.api.web.dto.SamplerDto;

import java.util.function.Function;
import java.util.function.Supplier;

public final class WsMetricsBroadcaster {

    private final Supplier<SamplerDto[]> snapshotSupplier;
    private final WebSocketSessions sessions;
    private final Function<SamplerDto[], String> serializer;

    public WsMetricsBroadcaster(
            Supplier<SamplerDto[]> snapshotSupplier,
            WebSocketSessions sessions,
            Function<SamplerDto[], String> serializer) {
        this.snapshotSupplier = snapshotSupplier;
        this.sessions = sessions;
        this.serializer = serializer;
    }

    public int broadcastOnce() {
        if (sessions.isEmpty()) {
            return 0;
        }
        SamplerDto[] data = snapshotSupplier.get();
        String json = serializer.apply(data);
        return sessions.broadcast(json);
    }
}
