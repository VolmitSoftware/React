package art.arcane.react.neoforge.cost;

import art.arcane.react.mod.cost.ChunkTickets;
import art.arcane.react.mod.cost.LoaderChunkTickets;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.TicketStorage;
import net.neoforged.neoforge.common.NeoForgeMod;
import net.neoforged.neoforge.common.world.chunk.ForcedChunkManager;

import java.lang.reflect.Field;
import java.util.Collection;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

/**
 * NeoForge keeps its controllers' forced chunks under its own ticket types and records which controller holds each
 * chunk in the ticket storage's trackers. The controller id's namespace names the mod.
 */
public final class NeoForgeChunkTickets implements LoaderChunkTickets {
    private final Field[] sources;
    private final Field ownerId;

    public NeoForgeChunkTickets() throws ReflectiveOperationException {
        Class<?> tracker = ForcedChunkManager.TicketTracker.class;
        sources = new Field[]{accessible(tracker.getDeclaredField("sourcesLoading")),
                accessible(tracker.getDeclaredField("sourcesLoadingNaturalSpawning"))};
        ownerId = accessible(Class.forName(ForcedChunkManager.class.getName() + "$TicketOwner").getDeclaredField("id"));
    }

    @Override
    public Set<Object> ticketTypes() {
        Set<Object> types = Collections.newSetFromMap(new IdentityHashMap<>());
        types.add(NeoForgeMod.BLOCK_TICKET.value());
        types.add(NeoForgeMod.BLOCK_WITH_NATURAL_SPAWNING_TICKET.value());
        types.add(NeoForgeMod.ENTITY_TICKET.value());
        types.add(NeoForgeMod.ENTITY_WITH_NATURAL_SPAWNING_TICKET.value());
        return types;
    }

    @Override
    public void collect(ServerLevel level, ChunkOwnerSink sink) throws ReflectiveOperationException {
        TicketStorage storage = ChunkTickets.storage(level);
        if (storage == null) {
            return;
        }
        collect(storage.getBlockForcedChunks(), sink);
        collect(storage.getEntityForcedChunks(), sink);
    }

    private void collect(Object tracker, ChunkOwnerSink sink) throws ReflectiveOperationException {
        for (Field field : sources) {
            Long2ObjectMap<?> chunks = (Long2ObjectMap<?>) field.get(tracker);
            for (Long2ObjectMap.Entry<?> entry : chunks.long2ObjectEntrySet()) {
                for (Object owner : (Collection<?>) entry.getValue()) {
                    Object id = ownerId.get(owner);
                    String text = id == null ? null : id.toString();
                    int colon = text == null ? -1 : text.indexOf(':');
                    if (colon > 0) {
                        sink.accept(text.substring(0, colon), entry.getLongKey());
                    }
                }
            }
        }
    }

    private static Field accessible(Field field) {
        field.setAccessible(true);
        return field;
    }
}
