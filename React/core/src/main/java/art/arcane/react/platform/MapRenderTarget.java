package art.arcane.react.platform;

import java.util.UUID;

public record MapRenderTarget(int mapId, UUID worldId, UUID viewerId) {
  public static final MapRenderTarget DETACHED = new MapRenderTarget(-1, null, null);
}
