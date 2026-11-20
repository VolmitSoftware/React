package art.arcane.react.platform.cost;

public interface OwnerCostProbe {
    String KIND_EVENT = "event";

    boolean available();

    String unavailableReason();

    void setActive(boolean active);

    boolean active();

    void drain(OwnerCostConsumer consumer);

    /**
     * One owner's work of one kind since the last drain. An owner may be reported once per kind; the model sums them.
     * {@code kind} is a lowercase id such as {@value #KIND_EVENT}, {@code block_entity} or {@code entity}.
     */
    @FunctionalInterface
    interface OwnerCostConsumer {
        void accept(String ownerId, String displayName, String kind, long exclusiveNanos, long syncCalls, long asyncCalls);
    }
}
