package art.arcane.react.platform;

public record DirtyRect(int x0, int y0, int x1, int y1) {
  public static final int SIZE = 128;
  public static final DirtyRect EMPTY = new DirtyRect(0, 0, 0, 0);
  public static final DirtyRect FULL = new DirtyRect(0, 0, SIZE, SIZE);

  public boolean isEmpty() {
    return x0 >= x1 || y0 >= y1;
  }

  public int width() {
    return Math.max(0, x1 - x0);
  }

  public int height() {
    return Math.max(0, y1 - y0);
  }

  public DirtyRect union(DirtyRect other) {
    if (other == null || other.isEmpty()) {
      return this;
    }
    if (isEmpty()) {
      return other;
    }
    return new DirtyRect(Math.min(x0, other.x0), Math.min(y0, other.y0), Math.max(x1, other.x1), Math.max(y1, other.y1));
  }
}
