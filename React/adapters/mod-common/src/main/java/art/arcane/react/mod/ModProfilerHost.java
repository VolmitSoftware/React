package art.arcane.react.mod;

import art.arcane.react.mod.cost.CostRegistry;
import art.arcane.react.mod.cost.ModOwners;
import art.arcane.react.platform.ProfilerHost;
import net.minecraft.server.MinecraftServer;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Profiler frames on mod loaders belong to the mod whose jar holds the class, through the same owner lookup as the
 * per-mod cost. Mixin handlers that name their mod, as Fabric's do, count for that mod, except React's own timing
 * wrappers: the vanilla work they wrap stays with Minecraft. Minecraft, Java and the loader itself are platform owners.
 */
final class ModProfilerHost implements ProfilerHost {
    private static final Pattern MIXIN_HANDLER = Pattern.compile("^[a-zA-Z]+\\$[a-z0-9]+\\$([a-z0-9_.\\-]+)\\$.+");
    private static final Pattern MIXIN_UNIQUE = Pattern.compile("^md[0-9a-f]+\\$([a-z0-9_.\\-]+)\\$.+");
    private static final String REACT = "react";
    private static final Set<String> PLATFORM = Set.of("minecraft", "java", "forge", "neoforge", "fabricloader", "mixinextras", ModOwners.UNKNOWN);

    private final MinecraftServer server;
    private final List<ClassLoader> loaders;

    ModProfilerHost(MinecraftServer server) {
        this.server = server;
        this.loaders = List.of(MinecraftServer.class.getClassLoader(), ModProfilerHost.class.getClassLoader());
    }

    @Override
    public Optional<Thread> serverThread() {
        return Optional.ofNullable(server.getRunningThread());
    }

    @Override
    public Optional<FrameOwner> ownerOf(String className, String methodName) {
        Matcher handler = MIXIN_HANDLER.matcher(methodName);
        if (!handler.matches()) {
            handler = MIXIN_UNIQUE.matcher(methodName);
        }
        if (handler.matches()) {
            String mod = CostRegistry.ownerOfNamespace(handler.group(1));
            if (REACT.equals(mod)) {
                return Optional.of(new FrameOwner(mod, CostRegistry.displayName(mod), true));
            }
            if (!PLATFORM.contains(mod) && CostRegistry.costOwners().containsKey(mod)) {
                return Optional.of(owner(mod));
            }
        }
        Class<?> type = find(className);
        if (type == null) {
            return Optional.empty();
        }
        String id = CostRegistry.ownerOf(type);
        return ModOwners.UNKNOWN.equals(id) ? Optional.empty() : Optional.of(owner(id));
    }

    @Override
    public void loadNames() {
        ModFrameNames.get();
    }

    @Override
    public String readableClass(String className) {
        return ModFrameNames.get().readableClass(className);
    }

    @Override
    public String readableMethod(String methodName) {
        return ModFrameNames.get().readableMethod(methodName);
    }

    private static FrameOwner owner(String id) {
        return new FrameOwner(id, CostRegistry.displayName(id), PLATFORM.contains(id));
    }

    private Class<?> find(String className) {
        for (ClassLoader loader : loaders) {
            try {
                return Class.forName(className, false, loader);
            } catch (ClassNotFoundException | LinkageError missing) {
                continue;
            }
        }
        return null;
    }
}
