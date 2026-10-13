package art.arcane.react.api.web;

import art.arcane.react.api.sampler.Sampler;
import art.arcane.react.api.web.dto.SamplerDto;
import art.arcane.react.core.history.MetricSnapshotValue;

public class MetricsSerializer {

    public SamplerDto toDto(Sampler s) {
        s.markDemand();
        Sampler.Reading reading = s.captureReading();
        double value = reading.value();
        boolean available = reading.available();
        return toDto(new MetricSnapshotValue(
            s.getId(),
            s.getName(),
            s.formattedSuffix(value),
            Double.isFinite(value) ? value : 0D,
            s.formattedValue(value),
            available,
            reading.sampledAtMs(),
            reading.metadata(),
            reading.unavailableReason(),
            s.diagnostics()
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
        dto.sampledAtMs = value.sampledAtMs();
        dto.intervalMs = value.metadata().intervalMs();
        dto.windowMs = value.metadata().windowMs();
        dto.quality = value.metadata().quality();
        dto.unit = value.metadata().unit();
        dto.unavailableReason = value.unavailableReason();
        dto.diagnostics = value.diagnostics();
        return dto;
    }
}
