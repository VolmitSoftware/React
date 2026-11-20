package art.arcane.react.neoforge.cost;

import art.arcane.react.mod.cost.ModOwners;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.ModList;
import net.neoforged.neoforgespi.language.IModFileInfo;
import net.neoforged.neoforgespi.language.IModInfo;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class NeoForgeModOwners implements ModOwners {
    private volatile Map<String, List<IModInfo>> modulesToMods;

    @Override
    public String ownerOf(Class<?> type) {
        String module = type.getModule().getName();
        if (module == null) {
            return UNKNOWN;
        }
        List<IModInfo> mods = modules().get(module);
        if (mods == null || mods.isEmpty()) {
            return UNKNOWN;
        }
        if (mods.size() > 1) {
            for (IModInfo mod : mods) {
                if (type.getPackageName().contains("." + mod.getModId())) {
                    return mod.getModId();
                }
            }
        }
        return mods.getFirst().getModId();
    }

    private Map<String, List<IModInfo>> modules() {
        Map<String, List<IModInfo>> known = modulesToMods;
        if (known == null) {
            known = new HashMap<>();
            for (IModFileInfo file : ModList.get().getModFiles()) {
                known.put(file.moduleName(), file.getMods());
            }
            modulesToMods = known;
        }
        return known;
    }

    @Override
    public String ownerOfNamespace(String namespace) {
        return ModList.get().isLoaded(namespace) ? namespace : null;
    }

    @Override
    public String displayName(String ownerId) {
        Optional<? extends ModContainer> container = ModList.get().getModContainerById(ownerId);
        return container.isPresent() ? container.get().getModInfo().getDisplayName() : ownerId;
    }

    @Override
    public List<LoadedMod> loadedMods() {
        List<LoadedMod> mods = new ArrayList<>();
        for (IModFileInfo file : ModList.get().getModFiles()) {
            for (IModInfo mod : file.getMods()) {
                mods.add(new LoadedMod(mod.getModId(), mod.getDisplayName(), mod.getVersion().toString()));
            }
        }
        mods.sort(Comparator.comparing(LoadedMod::id));
        return mods;
    }
}
