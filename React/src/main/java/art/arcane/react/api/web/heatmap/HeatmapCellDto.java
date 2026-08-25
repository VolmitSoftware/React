package art.arcane.react.api.web.heatmap;

public class HeatmapCellDto {
    public int x;
    public int z;
    public int sizeChunks;
    public double score;
    public double averageScore;
    public int samples;

    public HeatmapCellDto() {}

    public HeatmapCellDto(
        int x,
        int z,
        int sizeChunks,
        double score,
        double averageScore,
        int samples
    ) {
        this.x = x;
        this.z = z;
        this.sizeChunks = sizeChunks;
        this.score = score;
        this.averageScore = averageScore;
        this.samples = samples;
    }
}
