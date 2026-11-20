package art.arcane.react.mod.cost;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Forced and mod-held chunk tickets per owner, recounted on the server thread. Vanilla {@code /forceload} tickets
 * belong to {@code minecraft}, Forge and NeoForge controller tickets to the mod that holds them, and any other
 * non-vanilla ticket type to the mod that registered or created it. Vanilla's own player, portal and spawn tickets are
 * left out.
 */
public final class ChunkLoaderCensus {
    public static final String MINECRAFT = "minecraft";
    private static final Logger LOGGER = LoggerFactory.getLogger("React");
    private static final String SKIP = "";
    private final Map<Object, String> typeOwners = new IdentityHashMap<>();
    private final ChunkLoaderTally tally = new ChunkLoaderTally();
    private LoaderChunkTickets loader;
    private Set<Object> loaderTypes = Set.of();
    private volatile Map<String, Integer> counts = Map.of();
    private volatile long total;

    /** Server thread. */
    public void run(MinecraftServer server) {
        if (loader == null) {
            vanillaTypes();
            ModOwners owners = CostRegistry.owners();
            loader = owners == null ? LoaderChunkTickets.NONE : owners.loaderChunkTickets();
            try {
                loaderTypes = identitySet(loader.ticketTypes());
            } catch (RuntimeException | LinkageError failure) {
                disableLoader(failure);
            }
        }
        long sum = 0L;
        for (ServerLevel level : server.getAllLevels()) {
            collectLoader(level);
            ChunkTickets.visit(level, this::ticket);
            tally.endLevel();
        }
        Map<String, Integer> next = tally.finish();
        for (int count : next.values()) {
            sum += count;
        }
        counts = Collections.unmodifiableMap(next);
        total = sum;
    }

    public Map<String, Integer> counts() {
        return counts;
    }

    public double count(String owner) {
        return counts.getOrDefault(owner, 0);
    }

    public long total() {
        return total;
    }

    private void collectLoader(ServerLevel level) {
        if (loader == LoaderChunkTickets.NONE) {
            return;
        }
        try {
            loader.collect(level, (String namespace, long chunk) -> tally.add(CostRegistry.ownerOfNamespace(namespace), chunk));
        } catch (ReflectiveOperationException | RuntimeException | LinkageError failure) {
            disableLoader(failure);
        }
    }

    private void disableLoader(Throwable failure) {
        LOGGER.warn("Mod chunk loader attribution through the loader failed and is now off: {}", failure.toString());
        loader = LoaderChunkTickets.NONE;
        loaderTypes = Set.of();
        typeOwners.clear();
        vanillaTypes();
    }

    private void ticket(long chunk, Object type, Object key) {
        String typeOwner = typeOwners.get(type);
        if (typeOwner == null) {
            typeOwner = typeOwner(type);
            typeOwners.put(type, typeOwner);
        }
        if (typeOwner.isEmpty()) {
            return;
        }
        String keyed = TicketKeyOwners.namespaceOf(key);
        tally.add(keyed == null ? typeOwner : CostRegistry.ownerOfNamespace(keyed), chunk);
    }

    private String typeOwner(Object type) {
        if (loaderTypes.contains(type)) {
            return SKIP;
        }
        String namespace = TicketKeyOwners.namespace(ChunkTickets.typeName(type));
        if (namespace != null) {
            return CostRegistry.ownerOfNamespace(namespace);
        }
        Class<?> creator = TicketTypeCreators.creator(type);
        return creator == null ? ModOwners.UNKNOWN : CostRegistry.ownerOf(creator);
    }

    private void vanillaTypes() {
        Object forced = ChunkTickets.forcedType();
        for (Field field : ChunkTickets.typeClass().getFields()) {
            if (!Modifier.isStatic(field.getModifiers()) || !ChunkTickets.typeClass().isAssignableFrom(field.getType())) {
                continue;
            }
            try {
                Object type = field.get(null);
                if (type != null) {
                    typeOwners.put(type, type == forced ? MINECRAFT : SKIP);
                }
            } catch (IllegalAccessException ignored) {
                LOGGER.debug("Skipped ticket type field {}", field.getName());
            }
        }
    }

    private static Set<Object> identitySet(Set<Object> types) {
        Set<Object> set = Collections.newSetFromMap(new IdentityHashMap<>());
        set.addAll(types);
        return set;
    }
}
