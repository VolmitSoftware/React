package art.arcane.react.platform;

import java.util.Objects;

/**
 * World identities as config files and commands spell them: a fully qualified namespaced key such as
 * {@code minecraft:overworld}, validated the way Bukkit's {@code NamespacedKey} is.
 */
public final class WorldKeys {
  private WorldKeys() {
  }

  /**
   * The canonical key for a configured world identity.
   *
   * @throws IllegalArgumentException when the value is not a fully qualified, valid namespaced key
   */
  public static String parse(String serialized) {
    String value = Objects.requireNonNull(serialized, "serialized").trim();
    int separator = value.indexOf(':');
    if (separator <= 0 || separator == value.length() - 1) {
      throw new IllegalArgumentException("World identity must be a fully qualified namespaced key: " + value);
    }
    String namespace = value.substring(0, separator);
    String key = value.substring(separator + 1);
    if (!valid(namespace, false) || !valid(key, true)) {
      throw new IllegalArgumentException("Invalid world identity: " + value);
    }
    return namespace + ":" + key;
  }

  private static boolean valid(String part, boolean allowSlash) {
    if (part.isEmpty()) {
      return false;
    }
    for (int index = 0; index < part.length(); index++) {
      char c = part.charAt(index);
      boolean allowed = (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || c == '.' || c == '_' || c == '-'
          || (allowSlash && c == '/');
      if (!allowed) {
        return false;
      }
    }
    return true;
  }
}
