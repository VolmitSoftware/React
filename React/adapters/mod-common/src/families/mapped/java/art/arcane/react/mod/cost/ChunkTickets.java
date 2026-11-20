package art.arcane.react.mod.cost;

import art.arcane.react.mod.mixin.DistanceManagerAccess;
import art.arcane.react.mod.mixin.TicketAccess;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectMaps;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.Ticket;
import net.minecraft.server.level.TicketType;
import net.minecraft.util.SortedArraySet;

/** Reads a level's chunk tickets from its distance manager; each ticket carries a key such as a Forge ticket owner. */
public final class ChunkTickets {
    private ChunkTickets() {
    }

    static void visit(ServerLevel level, TicketVisitor visitor) {
        DistanceManagerAccess manager = (DistanceManagerAccess) level.getChunkSource().chunkMap.getDistanceManager();
        for (Long2ObjectMap.Entry<SortedArraySet<Ticket<?>>> entry : Long2ObjectMaps.fastIterable(manager.react$tickets())) {
            long chunk = entry.getLongKey();
            for (Ticket<?> ticket : entry.getValue()) {
                visitor.visit(chunk, ticket.getType(), ((TicketAccess) (Object) ticket).react$key());
            }
        }
    }

    static String typeName(Object type) {
        return type.toString();
    }

    static Object forcedType() {
        return TicketType.FORCED;
    }

    static Class<?> typeClass() {
        return TicketType.class;
    }
}
