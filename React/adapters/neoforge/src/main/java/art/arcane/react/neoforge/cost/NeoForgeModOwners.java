package art.arcane.react.neoforge.cost;

import art.arcane.react.mod.cost.ModOwners;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.ModList;
import net.neoforged.fml.loading.FMLLoader;
import net.neoforged.neoforgespi.language.IModInfo;
import net.neoforged.neoforgespi.locating.IModFile;

import java.util.List;
import java.util.Optional;

public final class NeoForgeModOwners implements ModOwners {
    @Override
    public String ownerOf(Class<?> type) {
        IModFile file = FMLLoader.getCurrent().getModFileByClass(type);
        for (int depth = 0; file != null && depth < 8; depth++) {
            List<IModInfo> mods = file.getModInfos();
            if (!mods.isEmpty()) {
                return pick(mods, type.getPackageName());
            }
            file = file.getDiscoveryAttributes().parent();
        }
        return UNKNOWN;
    }

    private static String pick(List<IModInfo> mods, String packageName) {
        if (mods.size() > 1) {
            for (IModInfo mod : mods) {
                if (packageName.contains("." + mod.getModId())) {
                    return mod.getModId();
                }
            }
        }
        return mods.getFirst().getModId();
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
}
