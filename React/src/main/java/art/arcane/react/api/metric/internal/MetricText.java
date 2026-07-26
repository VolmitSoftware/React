package art.arcane.react.api.metric.internal;

public final class MetricText {
  public static final int MAX_LENGTH = 128;

  private MetricText() {
  }

  public static String sanitize(String value, int maxLength) {
    if (value == null || value.isEmpty()) {
      return "";
    }

    int limit = Math.max(1, maxLength);
    StringBuilder out = new StringBuilder(Math.min(value.length(), limit));

    for (int i = 0; i < value.length() && out.length() < limit; i++) {
      char c = value.charAt(i);

      if (c < ' ' || c == 0x7F || c == '\u00A7') {
        continue;
      }

      out.append(c);
    }

    return out.toString().strip();
  }
}
