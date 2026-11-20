package art.arcane.react.fabric.cost;

import art.arcane.react.mod.cost.ModOwners;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.fabricmc.loader.api.metadata.ModMetadata;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class FabricModOwners implements ModOwners {
    private static final String FABRIC_API = "fabric-api";
    private static final String JAVA_RUNTIME = "java";

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

    @Override
    public Map<String, String> costOwners() {
        Map<String, String> owners = ModOwners.super.costOwners();
        owners.remove(JAVA_RUNTIME);
        return owners;
    }

    @Override
    public List<LoadedMod> loadedMods() {
        List<LoadedMod> mods = new ArrayList<>();
        for (ModContainer container : FabricLoader.getInstance().getAllMods()) {
            ModMetadata metadata = container.getMetadata();
            mods.add(new LoadedMod(metadata.getId(), metadata.getName(), metadata.getVersion().getFriendlyString()));
        }
        mods.sort(Comparator.comparing(LoadedMod::id));
        return mods;
    }
}
