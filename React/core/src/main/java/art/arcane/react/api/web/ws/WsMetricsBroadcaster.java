package art.arcane.react.api.web.ws;

import art.arcane.react.api.web.WebRevisions;
import art.arcane.react.api.web.resource.MetricsResource;

import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Pushes the live metrics frame when the captured snapshot or a revision counter moved. Polled from
 * one scheduler thread; an unchanged poll allocates nothing.
 */
public final class WsMetricsBroadcaster {

    private final Supplier<MetricsResource.LiveFrame> frameSupplier;
    private final WebSocketSessions<CoalescingWsChannel> sessions;
    private final Function<MetricsResource.LiveFrame, String> serializer;
    private long lastSequence = Long.MIN_VALUE;
    private WebRevisions.Snapshot lastRevisions;

    public WsMetricsBroadcaster(
            Supplier<MetricsResource.LiveFrame> frameSupplier,
            WebSocketSessions<CoalescingWsChannel> sessions,
            Function<MetricsResource.LiveFrame, String> serializer) {
        this.frameSupplier = frameSupplier;
        this.sessions = sessions;
        this.serializer = serializer;
    }

    /** Sends the current frame to one newly authenticated socket, so it shows data now rather than at the next capture. */
    public void sendCurrent(CoalescingWsChannel channel) {
        channel.send(serializer.apply(frameSupplier.get()));
    }

    public synchronized int broadcastOnce() {
        if (sessions.isEmpty()) {
            return 0;
        }
        MetricsResource.LiveFrame frame = frameSupplier.get();
        if (frame.sequence() == lastSequence && frame.revisions() == lastRevisions) {
            return 0;
        }
        lastSequence = frame.sequence();
        lastRevisions = frame.revisions();
        String json = serializer.apply(frame);
        return sessions.forEachOpen(channel -> channel.send(json));
    }
}
