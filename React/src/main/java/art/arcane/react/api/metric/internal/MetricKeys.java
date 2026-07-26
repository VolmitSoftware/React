package art.arcane.react.api.metric.internal;

import java.util.Locale;
import java.util.Set;

public final class MetricKeys {
  public static final int MAX_SOURCE_ID_LENGTH = 32;
  public static final int MAX_KEY_LENGTH = 64;
  public static final Set<String> RESERVED_SOURCE_IDS = Set.of(
      "react", "iris", "adapt", "wormholes", "holoui", "hiddenore", "biletools");

  private MetricKeys() {
  }

  public static String normalizeSourceId(String sourceId) {
    return sourceId == null ? "" : sourceId.strip().toLowerCase(Locale.ROOT);
  }

  public static boolean isReserved(String normalizedSourceId) {
    return RESERVED_SOURCE_IDS.contains(normalizedSourceId);
  }

  public static boolean isValidSourceId(String normalizedSourceId) {
    if (normalizedSourceId == null
        || normalizedSourceId.length() < 2
        || normalizedSourceId.length() > MAX_SOURCE_ID_LENGTH) {
      return false;
    }

    char first = normalizedSourceId.charAt(0);

    if (!((first >= 'a' && first <= 'z') || (first >= '0' && first <= '9'))) {
      return false;
    }

    for (int i = 0; i < normalizedSourceId.length(); i++) {
      char c = normalizedSourceId.charAt(i);

      if ((c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || c == '_' || c == '-') {
        continue;
      }

      return false;
    }

    return true;
  }

  public static boolean isAcceptableSourceId(String normalizedSourceId) {
    return isValidSourceId(normalizedSourceId) && !isReserved(normalizedSourceId);
  }

  public static boolean isValidKey(String normalizedSourceId, String key) {
    if (key == null || key.length() > MAX_KEY_LENGTH || !isValidSourceId(normalizedSourceId)) {
      return false;
    }

    int prefixLength = normalizedSourceId.length();

    if (key.length() <= prefixLength + 1
        || !key.startsWith(normalizedSourceId)
        || key.charAt(prefixLength) != '.') {
      return false;
    }

    boolean previousWasSeparator = true;

    for (int i = prefixLength + 1; i < key.length(); i++) {
      char c = key.charAt(i);

      if ((c >= 'a' && c <= 'z') || (c >= '0' && c <= '9')) {
        previousWasSeparator = false;
        continue;
      }

      if ((c == '.' || c == '_' || c == '-') && !previousWasSeparator) {
        previousWasSeparator = true;
        continue;
      }

      return false;
    }

    return !previousWasSeparator;
  }

  public static String samplerIdFor(String key) {
    if (key == null || key.isEmpty()) {
      return "";
    }

    StringBuilder out = new StringBuilder(key.length());

    for (int i = 0; i < key.length(); i++) {
      char c = Character.toLowerCase(key.charAt(i));

      if ((c >= 'a' && c <= 'z') || (c >= '0' && c <= '9')) {
        out.append(c);
        continue;
      }

      out.append('-');
    }

    return out.toString();
  }
}
