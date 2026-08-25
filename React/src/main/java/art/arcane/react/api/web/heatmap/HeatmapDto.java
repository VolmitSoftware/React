package art.arcane.react.api.web.heatmap;

public class HeatmapDto {
    public String id;
    public String label;
    public String world;
    public int centerChunkX;
    public int centerChunkZ;
    public int radius;
    public int originChunkX;
    public int originChunkZ;
    public int width;
    public int height;
    public int cellSizeChunks;
    public long capturedAtMs;
    public int spawnChunkX;
    public int spawnChunkZ;
    public HeatmapWorldBorderDto worldBorder;
    public double min;
    public double max;
    public HeatmapCellDto[] cells;
}
