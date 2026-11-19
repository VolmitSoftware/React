package art.arcane.react.platform.cost;

public interface OwnerCostProbe {
    boolean available();

    String unavailableReason();

    void setActive(boolean active);

    boolean active();

    void drain(OwnerCostConsumer consumer);

    @FunctionalInterface
    interface OwnerCostConsumer {
        void accept(String ownerId, String displayName, long exclusiveNanos, long syncCalls, long asyncCalls);
    }
}
