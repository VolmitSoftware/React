package art.arcane.react.api.web.heatmap;

public class HeatmapCellDto {
    public int x;
    public int z;
    public double score;

    public HeatmapCellDto() {}

    public HeatmapCellDto(int x, int z, double score) {
        this.x = x;
        this.z = z;
        this.score = score;
    }
}
