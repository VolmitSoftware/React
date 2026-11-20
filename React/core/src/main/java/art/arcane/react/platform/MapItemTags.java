package art.arcane.react.platform;

public record MapItemTags(boolean reactFlag, String rendererId, String token) {
  public static final MapItemTags NONE = new MapItemTags(false, null, null);
}
