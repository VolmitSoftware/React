package art.arcane.react.api.web.heatmap;

public class HeatmapWorldBorderDto {
    public double centerBlockX;
    public double centerBlockZ;
    public double sizeBlocks;

    public HeatmapWorldBorderDto() {}

    public HeatmapWorldBorderDto(double centerBlockX, double centerBlockZ, double sizeBlocks) {
        this.centerBlockX = centerBlockX;
        this.centerBlockZ = centerBlockZ;
        this.sizeBlocks = sizeBlocks;
    }
}
