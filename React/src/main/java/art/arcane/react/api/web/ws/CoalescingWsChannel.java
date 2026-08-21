package art.arcane.react.api.web.ws;

import java.util.concurrent.Executor;
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
            sender.execute(this::drain);
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
}
