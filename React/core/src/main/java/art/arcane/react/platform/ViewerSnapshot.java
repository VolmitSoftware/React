package art.arcane.react.platform;

import java.util.UUID;

public record ViewerSnapshot(
    UUID viewerId,
    UUID worldId,
    double x,
    double y,
    double z,
    double eyeX,
    double eyeY,
    double eyeZ,
    double dirX,
    double dirY,
    double dirZ,
    boolean hasDirection,
    int mainHandMapId,
    int offHandMapId
) {
  public boolean isHolding(int mapId) {
    return mapId >= 0 && (mainHandMapId == mapId || offHandMapId == mapId);
  }
}
