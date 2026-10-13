package art.arcane.react.api.web.dto;

import art.arcane.react.api.sampler.Sampler;

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
    public Sampler.Quality quality;
    public String unit;
    public String unavailableReason;
    public Sampler.Diagnostics diagnostics;
}
