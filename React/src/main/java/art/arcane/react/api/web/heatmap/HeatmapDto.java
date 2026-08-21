package art.arcane.react.api.web.heatmap;

public class HeatmapDto {
    public String id;
    public String label;
    public String world;
    public int centerChunkX;
    public int centerChunkZ;
    public int radius;
    public double min;
    public double max;
    public HeatmapCellDto[] cells;
}
