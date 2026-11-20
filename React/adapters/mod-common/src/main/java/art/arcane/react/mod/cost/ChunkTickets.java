package art.arcane.react.mod.cost;

import art.arcane.react.mod.mixin.TicketStorageAccess;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectMaps;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.Ticket;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.TicketStorage;

import java.util.List;

/** Reads a level's chunk tickets: registered ticket types kept in the level's ticket storage. */
public final class ChunkTickets {
    private ChunkTickets() {
    }

    /** The level's ticket storage, or null before it exists. */
    public static TicketStorage storage(ServerLevel level) {
        return level.getDataStorage().get(TicketStorage.TYPE);
    }

    static void visit(ServerLevel level, TicketVisitor visitor) {
        TicketStorage storage = storage(level);
        if (storage == null) {
            return;
        }
        for (Long2ObjectMap.Entry<List<Ticket>> entry : Long2ObjectMaps.fastIterable(((TicketStorageAccess) storage).react$tickets())) {
            long chunk = entry.getLongKey();
            for (Ticket ticket : entry.getValue()) {
                visitor.visit(chunk, ticket.getType(), null);
            }
        }
    }

    static String typeName(Object type) {
        Object key = BuiltInRegistries.TICKET_TYPE.getKey((TicketType) type);
        return key == null ? null : key.toString();
    }

    static Object forcedType() {
        return TicketType.FORCED;
    }

    static Class<?> typeClass() {
        return TicketType.class;
    }
}
