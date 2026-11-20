package art.arcane.react.mod.cost;

import java.util.List;

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
