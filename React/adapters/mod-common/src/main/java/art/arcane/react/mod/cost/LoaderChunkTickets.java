package art.arcane.react.mod.cost;

import net.minecraft.server.level.ServerLevel;

import java.util.Set;

/**
 * Chunks a loader's forced-chunk manager keeps loaded for mods, reported by mod. The chunk loader census leaves the
 * tickets of {@link #ticketTypes()} to this and counts every other ticket itself.
 */
public interface LoaderChunkTickets {
    LoaderChunkTickets NONE = new LoaderChunkTickets() {
        @Override
        public Set<Object> ticketTypes() {
            return Set.of();
        }

        @Override
        public void collect(ServerLevel level, ChunkOwnerSink sink) {
        }
    };

    /** Ticket types whose owners this reports, compared by identity: ticket types are records and may be equal. */
    Set<Object> ticketTypes();

    void collect(ServerLevel level, ChunkOwnerSink sink) throws ReflectiveOperationException;

    @FunctionalInterface
    interface ChunkOwnerSink {
        /** {@code namespace} is the mod id or the namespace of the controller that holds {@code chunk}. */
        void accept(String namespace, long chunk);
    }
}
