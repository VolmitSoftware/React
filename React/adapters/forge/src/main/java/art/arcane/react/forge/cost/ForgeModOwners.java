package art.arcane.react.forge.cost;

import art.arcane.react.mod.cost.ModOwners;
import net.minecraftforge.fml.ModContainer;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.forgespi.language.IModFileInfo;
import net.minecraftforge.forgespi.language.IModInfo;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class ForgeModOwners implements ModOwners {
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
            for (IModFileInfo file : ModList.getModFiles()) {
                known.put(file.moduleName(), file.getMods());
            }
            modulesToMods = known;
        }
        return known;
    }

    @Override
    public String ownerOfNamespace(String namespace) {
        return ModList.isLoaded(namespace) ? namespace : null;
    }

    @Override
    public String displayName(String ownerId) {
        Optional<? extends ModContainer> container = ModList.getModContainerById(ownerId);
        return container.isPresent() ? container.get().getModInfo().getDisplayName() : ownerId;
    }
}
