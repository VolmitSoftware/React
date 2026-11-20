package art.arcane.react.platform.bukkit;

import art.arcane.react.platform.ProfilerHost;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Profiler frames on Bukkit belong to the plugin whose class loader defined the class; the rest is the server. */
final class BukkitProfilerHost implements ProfilerHost {
  private final ClassLoader serverLoader = Bukkit.class.getClassLoader();

  @Override
  public Optional<Thread> serverThread() {
    return Optional.empty();
  }

  @Override
  public Optional<FrameOwner> ownerOf(String className, String methodName) {
    Map<ClassLoader, Plugin> loaders = new IdentityHashMap<>();
    List<ClassLoader> candidates = new ArrayList<>();
    candidates.add(serverLoader);
    for (Plugin plugin : Bukkit.getPluginManager().getPlugins()) {
      ClassLoader loader = plugin.getClass().getClassLoader();
      if (loaders.putIfAbsent(loader, plugin) == null) {
        candidates.add(loader);
      }
    }
    Class<?> type = find(className, candidates);
    if (type == null) {
      return Optional.empty();
    }
    Plugin plugin = loaders.get(type.getClassLoader());
    return plugin == null ? Optional.empty() : Optional.of(new FrameOwner(plugin.getName(), plugin.getName(), false));
  }

  private static Class<?> find(String className, List<ClassLoader> candidates) {
    for (ClassLoader loader : candidates) {
      try {
        return Class.forName(className, false, loader);
      } catch (ClassNotFoundException | LinkageError missing) {
        continue;
      }
    }
    return null;
  }
}
