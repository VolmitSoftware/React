package art.arcane.react.api.test.load;

public record LoadSummary(
    int samples,
    double avgMspt,
    double p95Mspt,
    double maxTickMs,
    double avgTps,
    double minTps,
    double heapStartMb,
    double heapEndMb,
    double heapMaxMb,
    boolean heapMonotonicGrowth,
    boolean oom,
    int reactPathExceptions
) {
}
