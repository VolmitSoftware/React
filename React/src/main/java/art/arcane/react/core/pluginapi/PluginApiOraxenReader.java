package art.arcane.react.core.pluginapi;

import org.bukkit.plugin.Plugin;

import java.lang.reflect.Array;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Collection;
import java.util.Map;

public final class PluginApiOraxenReader {
  private PluginApiOraxenReader() {
  }

  public static double read(Plugin plugin, String key) throws ReflectiveOperationException {
    Target target = switch (key) {
      case "items" -> new Target("io.th0rgal.oraxen.api.OraxenItems", "getNames");
      case "blocks" -> new Target("io.th0rgal.oraxen.api.OraxenBlocks", "getBlockIDs");
      case "furniture" -> new Target("io.th0rgal.oraxen.api.OraxenFurniture", "getFurnitureIDs");
      default -> throw new IllegalArgumentException("Unsupported Oraxen metric: " + key);
    };
    ClassLoader classLoader = plugin.getClass().getClassLoader();
    Class<?> apiClass = Class.forName(target.className(), false, classLoader);
    Method method = apiClass.getMethod(target.methodName());
    if (!Modifier.isPublic(method.getModifiers()) || !Modifier.isStatic(method.getModifiers()) || method.getParameterCount() != 0) {
      throw new NoSuchMethodException(target.className() + "." + target.methodName());
    }
    return sizeOf(method.invoke(null));
  }

  private static int sizeOf(Object value) {
    if (value instanceof Collection<?> collection) {
      return collection.size();
    }
    if (value instanceof Map<?, ?> map) {
      return map.size();
    }
    if (value != null && value.getClass().isArray()) {
      return Array.getLength(value);
    }
    throw new IllegalArgumentException("Oraxen accessor did not return a collection, map, or array");
  }

  private record Target(String className, String methodName) {
  }
}
