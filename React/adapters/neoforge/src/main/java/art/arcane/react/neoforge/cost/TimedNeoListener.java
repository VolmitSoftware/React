package art.arcane.react.neoforge.cost;

import art.arcane.react.mod.cost.CostBucket;
import art.arcane.react.mod.cost.CostFrame;
import net.neoforged.bus.GeneratedEventListener;
import net.neoforged.bus.api.Event;
import net.neoforged.bus.api.EventListener;

final class TimedNeoListener extends GeneratedEventListener {
    final EventListener delegate;
    private final CostBucket bucket;

    TimedNeoListener(EventListener delegate, CostBucket bucket) {
        this.delegate = delegate;
        this.bucket = bucket;
    }

    @Override
    public void invoke(Event event) {
        long start = CostFrame.enter();
        try {
            delegate.invoke(event);
        } finally {
            CostFrame.exit(start, bucket);
        }
    }

    @Override
    public String toString() {
        return delegate.toString();
    }
}
