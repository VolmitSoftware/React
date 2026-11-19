package art.arcane.react.fabric.cost;

import art.arcane.react.mod.cost.ModOwners;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;

import java.util.Optional;

public final class FabricModOwners implements ModOwners {
    private static final String FABRIC_API = "fabric-api";

    @Override
    public String ownerOf(Class<?> type) {
        String resource = type.getName().replace('.', '/') + ".class";
        for (ModContainer container : FabricLoader.getInstance().getAllMods()) {
            if (container.findPath(resource).isPresent()) {
                return rollUp(container);
            }
        }
        return UNKNOWN;
    }

    private static String rollUp(ModContainer container) {
        String id = container.getMetadata().getId();
        if (id.startsWith("fabric-") && !id.equals("fabric-language-kotlin")) {
            return FABRIC_API;
        }
        ModContainer outer = container;
        Optional<ModContainer> parent = outer.getContainingMod();
        while (parent.isPresent()) {
            outer = parent.get();
            parent = outer.getContainingMod();
        }
        return outer.getMetadata().getId();
    }

    @Override
    public String ownerOfNamespace(String namespace) {
        Optional<ModContainer> container = FabricLoader.getInstance().getModContainer(namespace);
        return container.isPresent() ? rollUp(container.get()) : null;
    }

    @Override
    public String displayName(String ownerId) {
        if (ownerId.equals(FABRIC_API)) {
            return "Fabric API";
        }
        Optional<ModContainer> container = FabricLoader.getInstance().getModContainer(ownerId);
        return container.isPresent() ? container.get().getMetadata().getName() : ownerId;
    }
}
