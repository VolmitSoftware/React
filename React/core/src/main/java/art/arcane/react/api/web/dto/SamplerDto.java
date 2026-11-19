package art.arcane.react.api.web.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

public class SamplerDto {
    public String id;
    public String name;
    public double value;
    public String suffix;
    public String display;
    public boolean available;
    public long sampledAtMs;
    public long intervalMs;
    public long windowMs;
    public String quality;
    public String unit;
    public String unavailableReason;
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public MetricDiagnosticsDto diagnostics;
}
