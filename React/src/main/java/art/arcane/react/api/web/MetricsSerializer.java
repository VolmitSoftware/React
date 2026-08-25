package art.arcane.react.api.web;

import art.arcane.react.api.sampler.Sampler;
import art.arcane.react.api.web.dto.SamplerDto;
import art.arcane.react.core.history.MetricSnapshotValue;

public class MetricsSerializer {

    public SamplerDto toDto(Sampler s) {
        double value = s.sample();
        boolean available = s.isSampleAvailable() && Double.isFinite(value);
        return toDto(new MetricSnapshotValue(
            s.getId(),
            s.getName(),
            s.formattedSuffix(value),
            Double.isFinite(value) ? value : 0D,
            s.formattedValue(value),
            available
        ));
    }

    public SamplerDto toDto(MetricSnapshotValue value) {
        SamplerDto dto = new SamplerDto();
        dto.id = value.id();
        dto.name = value.name();
        dto.value = value.value();
        dto.suffix = value.suffix();
        dto.display = value.display();
        dto.available = value.available();
        return dto;
    }
}
