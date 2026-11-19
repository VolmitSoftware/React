package art.arcane.react.mod.cost;

import java.util.Objects;
import java.util.concurrent.atomic.LongAdder;

/** Counters for one owner and one source of work. */
public final class CostBucket {
    private final String ownerId;
    private final CostKind kind;
    final LongAdder nanos = new LongAdder();
    final LongAdder syncCalls = new LongAdder();
    final LongAdder asyncCalls = new LongAdder();

    CostBucket(String ownerId, CostKind kind) {
        this.ownerId = Objects.requireNonNull(ownerId, "ownerId");
        this.kind = Objects.requireNonNull(kind, "kind");
    }

    public String ownerId() {
        return ownerId;
    }

    public CostKind kind() {
        return kind;
    }
}
