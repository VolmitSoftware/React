package art.arcane.react.api.protect.internal;

public final class ProtectionText {
  public static final int MAX_LENGTH = 128;

  private ProtectionText() {
  }

  public static String sanitize(String value) {
    return sanitize(value, MAX_LENGTH);
  }

  public static String sanitize(String value, int maxLength) {
    if (value == null || value.isEmpty()) {
      return "";
    }

    int limit = Math.max(1, maxLength);
    StringBuilder out = new StringBuilder(Math.min(value.length(), limit));

    for (int i = 0; i < value.length() && out.length() < limit; i++) {
      char c = value.charAt(i);

      if (c < ' ' || c == 0x7F) {
        continue;
      }

      out.append(c);
    }

    return out.toString().strip();
  }

  public static String toOwnerToken(String pluginName) {
    if (pluginName == null || pluginName.isEmpty()) {
      return "";
    }

    StringBuilder out = new StringBuilder(Math.min(pluginName.length(), 48));

    for (int i = 0; i < pluginName.length() && out.length() < 48; i++) {
      char c = Character.toLowerCase(pluginName.charAt(i));

      if ((c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || c == '_' || c == '-' || c == '.') {
        out.append(c);
        continue;
      }

      out.append('_');
    }

    return out.toString();
  }

  public static boolean isSyntheticProviderId(String providerId) {
    return providerId != null && (providerId.contains("$$Lambda") || providerId.contains("$$lambda"));
  }
}
