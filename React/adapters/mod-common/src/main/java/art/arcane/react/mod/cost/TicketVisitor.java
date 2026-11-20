package art.arcane.react.mod.cost;

@FunctionalInterface
interface TicketVisitor {
    /** {@code key} is the ticket's key on versions whose tickets carry one, otherwise null. */
    void visit(long chunk, Object type, Object key);
}
