package art.arcane.react.core.controller;

import art.arcane.react.api.action.Action;
import art.arcane.react.api.feature.Feature;
import art.arcane.react.api.tweak.Tweak;
import art.arcane.react.platform.RegistryHost;
import art.arcane.react.util.common.scheduling.J;
import art.arcane.react.util.project.registry.Registered;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * Changes one value of a feature, tweak or action config, writes the file and reloads the component, as React's
 * config editor does on Bukkit. Paths look like {@code feature.entity-trimmer.enabled}.
 */
public final class ComponentConfigEditor {
  @FunctionalInterface
  public interface ConfigWriter {
    void write(File file, Object value, String sourceTag) throws IOException;
  }

  private final RegistryHost registry;
  private final ConfigWriter writer;
  private final Object lock = new Object();

  public ComponentConfigEditor(RegistryHost registry, ConfigWriter writer) {
    this.registry = Objects.requireNonNull(registry, "registry");
    this.writer = Objects.requireNonNull(writer, "writer");
  }

  public boolean apply(String path, Object value) {
    String normalized = path == null ? "" : path.trim();
    String[] parts = normalized.split("\\.", 3);
    if (parts.length < 3 || parts[1].isBlank() || parts[2].isBlank()) {
      return false;
    }
    String category = parts[0];
    Registered target = switch (category) {
      case "feature" -> ReactComponents.feature(parts[1]);
      case "tweak" -> ReactComponents.tweak(parts[1]);
      case "action" -> ReactComponents.action(parts[1]);
      default -> null;
    };
    if (target == null) {
      return false;
    }
    synchronized (lock) {
      Object before;
      FieldRef ref = resolve(target, parts[2]);
      if (ref == null) {
        return false;
      }
      try {
        before = ref.field().get(ref.owner());
        Object typed = coerce(value, ref.field().getType());
        if (typed == null && ref.field().getType().isPrimitive()) {
          return false;
        }
        ref.field().set(ref.owner(), typed);
      } catch (ReflectiveOperationException | IllegalArgumentException failure) {
        return false;
      }
      File file = registry.configFile(category, target.getId() + ".toml");
      String sourceTag = category + ":" + target.getId();
      try {
        writer.write(file, target, sourceTag);
      } catch (IOException failure) {
        restore(ref, before);
        return false;
      }
      Boolean reloaded = J.sResult(() -> reload(target));
      return Boolean.TRUE.equals(reloaded);
    }
  }

  public static Object read(Registered target, String fieldPath) {
    FieldRef ref = resolve(target, fieldPath);
    if (ref == null) {
      return null;
    }
    try {
      return ref.field().get(ref.owner());
    } catch (IllegalAccessException failure) {
      return null;
    }
  }

  public static boolean reload(Registered target) {
    if (target instanceof Feature feature) {
      FeatureController controller = ReactComponents.features();
      if (controller == null || controller.getActiveFeatures() == null) {
        return false;
      }
      boolean wasActive = controller.getActiveFeatures().containsKey(feature.getId());
      feature.loadConfiguration();
      boolean nowEnabled = feature.isEnabled();
      if (wasActive && !nowEnabled) {
        controller.deactivateFeature(feature);
      } else if (!wasActive && nowEnabled) {
        controller.activateFeature(feature);
      } else if (wasActive) {
        controller.deactivateFeature(feature);
        controller.activateFeature(feature);
      }
      return true;
    }
    if (target instanceof Tweak tweak) {
      TweakController controller = ReactComponents.tweaks();
      if (controller == null || controller.getActiveTweaks() == null) {
        return false;
      }
      boolean wasActive = controller.getActiveTweaks().containsKey(tweak.getId());
      tweak.loadConfiguration();
      boolean nowEnabled = tweak.isEnabled();
      if (wasActive && !nowEnabled) {
        controller.deactivateTweak(tweak);
      } else if (!wasActive && nowEnabled) {
        controller.activateTweak(tweak);
      } else if (wasActive) {
        controller.deactivateTweak(tweak);
        controller.activateTweak(tweak);
      }
      return true;
    }
    if (target instanceof Action<?> action) {
      action.loadConfiguration();
      return true;
    }
    return false;
  }

  private static void restore(FieldRef ref, Object before) {
    try {
      ref.field().set(ref.owner(), before);
    } catch (IllegalAccessException ignored) {
    }
  }

  private record FieldRef(Object owner, Field field) {
  }

  private static FieldRef resolve(Object root, String fieldPath) {
    String[] segments = fieldPath.split("\\.");
    Object owner = root;
    for (int index = 0; index < segments.length; index++) {
      Field field = findField(owner.getClass(), segments[index]);
      if (field == null) {
        return null;
      }
      if (index == segments.length - 1) {
        return new FieldRef(owner, field);
      }
      try {
        owner = field.get(owner);
      } catch (IllegalAccessException failure) {
        return null;
      }
      if (owner == null) {
        return null;
      }
    }
    return null;
  }

  private static Field findField(Class<?> type, String name) {
    for (Class<?> current = type; current != null && current != Object.class; current = current.getSuperclass()) {
      try {
        Field field = current.getDeclaredField(name);
        int modifiers = field.getModifiers();
        if (Modifier.isStatic(modifiers) || Modifier.isTransient(modifiers) || Modifier.isFinal(modifiers)) {
          return null;
        }
        field.setAccessible(true);
        return field;
      } catch (NoSuchFieldException ignored) {
      }
    }
    return null;
  }

  @SuppressWarnings({"unchecked", "rawtypes"})
  static Object coerce(Object value, Class<?> type) {
    if (value == null) {
      return null;
    }
    if (type.isInstance(value) && !(value instanceof Collection<?>)) {
      return value;
    }
    String text = String.valueOf(value).trim();
    try {
      if (type == boolean.class || type == Boolean.class) {
        if (text.equalsIgnoreCase("true") || text.equalsIgnoreCase("false")) {
          return Boolean.parseBoolean(text);
        }
        return null;
      }
      if (type == int.class || type == Integer.class) {
        return value instanceof Number number ? number.intValue() : Integer.parseInt(text);
      }
      if (type == long.class || type == Long.class) {
        return value instanceof Number number ? number.longValue() : Long.parseLong(text);
      }
      if (type == double.class || type == Double.class) {
        return value instanceof Number number ? number.doubleValue() : Double.parseDouble(text);
      }
      if (type == float.class || type == Float.class) {
        return value instanceof Number number ? number.floatValue() : Float.parseFloat(text);
      }
      if (type == String.class) {
        return text;
      }
      if (type.isEnum()) {
        return Enum.valueOf((Class<? extends Enum>) type, text.toUpperCase(Locale.ROOT));
      }
      if (List.class.isAssignableFrom(type)) {
        List<String> values = new ArrayList<>();
        if (value instanceof Collection<?> collection) {
          for (Object item : collection) {
            values.add(String.valueOf(item).trim());
          }
        } else if (!text.isBlank()) {
          for (String item : text.split(",")) {
            if (!item.isBlank()) {
              values.add(item.trim());
            }
          }
        }
        return values;
      }
    } catch (IllegalArgumentException invalid) {
      return null;
    }
    return null;
  }
}
