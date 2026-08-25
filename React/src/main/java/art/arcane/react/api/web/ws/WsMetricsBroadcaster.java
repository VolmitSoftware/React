package art.arcane.react.api.web.ws;

import art.arcane.react.api.web.resource.MetricsResource;

import java.util.function.Function;
import java.util.function.Supplier;
import java.util.concurrent.atomic.AtomicLong;

public final class WsMetricsBroadcaster {

    private final Supplier<MetricsResource.SnapshotResponse> snapshotSupplier;
    private final WebSocketSessions sessions;
    private final Function<MetricsResource.SnapshotResponse, String> serializer;
    private final AtomicLong lastBroadcastSequence;

    public WsMetricsBroadcaster(
            Supplier<MetricsResource.SnapshotResponse> snapshotSupplier,
            WebSocketSessions sessions,
            Function<MetricsResource.SnapshotResponse, String> serializer) {
        this.snapshotSupplier = snapshotSupplier;
        this.sessions = sessions;
        this.serializer = serializer;
        this.lastBroadcastSequence = new AtomicLong(Long.MIN_VALUE);
    }

    public int broadcastOnce() {
        if (sessions.isEmpty()) {
            return 0;
        }
        MetricsResource.SnapshotResponse data = snapshotSupplier.get();
        long sequence = data.sequence();
        long previous = lastBroadcastSequence.get();
        if (sequence == previous || !lastBroadcastSequence.compareAndSet(previous, sequence)) {
            return 0;
        }
        String json = serializer.apply(data);
        return sessions.broadcast(json);
    }
}
