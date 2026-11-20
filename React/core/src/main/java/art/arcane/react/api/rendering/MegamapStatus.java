package art.arcane.react.api.rendering;

public record MegamapStatus(int gridWidth, int gridHeight, int frames, MegamapGrid.DefectReason defect) {
  public MegamapStatus mergeWith(MegamapStatus other) {
    if (other == null) {
      return this;
    }

    boolean preferOther = (other.gridWidth * other.gridHeight) > (gridWidth * gridHeight);
    return new MegamapStatus(
        preferOther ? other.gridWidth : gridWidth,
        preferOther ? other.gridHeight : gridHeight,
        frames + other.frames,
        defect == null ? other.defect : defect
    );
  }
}
