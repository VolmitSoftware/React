package art.arcane.react.api.web.ws;

import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

public final class CoalescingWsChannel implements WsChannel {

    private final WsChannel delegate;
    private final Executor sender;
    private final AtomicReference<String> pending = new AtomicReference<>();
    private final AtomicBoolean draining = new AtomicBoolean(false);

    public CoalescingWsChannel(WsChannel delegate, Executor sender) {
        this.delegate = delegate;
        this.sender = sender;
    }

    public static CoalescingWsChannel broadcastTo(
        String channelId,
        WebSocketSessions sessions,
        Executor sender
    ) {
        return new CoalescingWsChannel(new BroadcastChannel(channelId, sessions), sender);
    }

    @Override
    public String id() {
        return delegate.id();
    }

    @Override
    public boolean isOpen() {
        return delegate.isOpen();
    }

    @Override
    public void send(String text) {
        pending.set(text);
        scheduleDrain();
    }

    private void scheduleDrain() {
        if (draining.compareAndSet(false, true)) {
            try {
                sender.execute(this::drain);
            } catch (RejectedExecutionException failure) {
                draining.set(false);
            }
        }
    }

    private void drain() {
        while (true) {
            String frame = pending.getAndSet(null);
            if (frame == null) {
                draining.set(false);
                if (pending.get() != null && draining.compareAndSet(false, true)) {
                    continue;
                }
                return;
            }
            try {
                delegate.send(frame);
            } catch (Throwable t) {
                // swallow; broadcaster/WebSocketSessions already prune closed channels
            }
        }
    }

    private static final class BroadcastChannel implements WsChannel {
        private final String channelId;
        private final WebSocketSessions sessions;

        private BroadcastChannel(String channelId, WebSocketSessions sessions) {
            this.channelId = channelId;
            this.sessions = sessions;
        }

        @Override
        public String id() {
            return channelId;
        }

        @Override
        public boolean isOpen() {
            return true;
        }

        @Override
        public void send(String text) {
            sessions.broadcast(text);
        }
    }
}
