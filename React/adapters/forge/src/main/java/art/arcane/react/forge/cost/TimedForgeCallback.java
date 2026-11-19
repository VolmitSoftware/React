package art.arcane.react.forge.cost;

import art.arcane.react.mod.cost.CostBucket;
import art.arcane.react.mod.cost.CostFrame;
import net.minecraftforge.eventbus.api.listener.ObjBooleanBiConsumer;

import java.util.function.Consumer;
import java.util.function.Predicate;

/** Stands in for one listener callback while it is timed; implements every callback shape EventBus 7 records hold. */
@SuppressWarnings({"rawtypes", "unchecked"})
final class TimedForgeCallback implements Consumer<Object>, Predicate<Object>, ObjBooleanBiConsumer<Object> {
    private final Object delegate;
    private final CostBucket bucket;

    TimedForgeCallback(Object delegate, CostBucket bucket) {
        this.delegate = delegate;
        this.bucket = bucket;
    }

    @Override
    public void accept(Object event) {
        long start = CostFrame.enter();
        try {
            ((Consumer) delegate).accept(event);
        } finally {
            CostFrame.exit(start, bucket);
        }
    }

    @Override
    public boolean test(Object event) {
        long start = CostFrame.enter();
        try {
            return ((Predicate) delegate).test(event);
        } finally {
            CostFrame.exit(start, bucket);
        }
    }

    @Override
    public void accept(Object event, boolean cancelled) {
        long start = CostFrame.enter();
        try {
            ((ObjBooleanBiConsumer) delegate).accept(event, cancelled);
        } finally {
            CostFrame.exit(start, bucket);
        }
    }

    @Override
    public String toString() {
        return delegate.toString();
    }
}
