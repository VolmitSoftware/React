package art.arcane.react.forge.cost;

import art.arcane.react.mod.cost.CostBucket;
import art.arcane.react.mod.cost.CostFrame;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.IEventListener;

/** Stands in for one EventBus 6 listener while it is timed. */
final class TimedForgeCallback implements IEventListener {
    private final IEventListener delegate;
    private final CostBucket bucket;

    TimedForgeCallback(IEventListener delegate, CostBucket bucket) {
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
    public String listenerName() {
        return delegate.listenerName();
    }

    @Override
    public String toString() {
        return delegate.toString();
    }
}
