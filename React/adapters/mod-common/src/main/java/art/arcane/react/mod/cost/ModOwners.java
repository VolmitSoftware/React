package art.arcane.react.mod.cost;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/** Maps classes and registry namespaces to the loaded mod that owns them. */
public interface ModOwners {
    String UNKNOWN = "unknown";

    /** The owning mod id, or {@link #UNKNOWN}. */
    String ownerOf(Class<?> type);

    /** The mod id for a registry namespace, or null when no loaded mod uses it. */
    String ownerOfNamespace(String namespace);

    String displayName(String ownerId);

    /** Every mod the loader lists, sorted by id. */
    List<LoadedMod> loadedMods();

    /** Each loaded mod's cost owner id and display name, with nested and bundled mods folded into their owner. */
    default Map<String, String> costOwners() {
        Map<String, String> owners = new TreeMap<>();
        for (LoadedMod mod : loadedMods()) {
            owners.computeIfAbsent(costOwnerOf(mod.id()), this::displayName);
        }
        return owners;
    }

    /** The owner whose cost counts this loaded mod's time: the mod itself, or the mod it is nested in or bundled with. */
    default String costOwnerOf(String modId) {
        return Objects.requireNonNullElse(ownerOfNamespace(modId), modId);
    }

    /** The loader's own forced-chunk manager, when it keeps the mod id outside the ticket. */
    default LoaderChunkTickets loaderChunkTickets() {
        return LoaderChunkTickets.NONE;
    }

    record LoadedMod(String id, String name, String version) {
    }

    static Class<?> declaringClass(Class<?> type) {
        Class<?> current = type.isHidden() ? type.getNestHost() : type;
        Class<?> enclosing = current.getEnclosingClass();
        while (enclosing != null) {
            current = enclosing;
            enclosing = current.getEnclosingClass();
        }
        return current;
    }
}
