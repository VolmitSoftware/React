package art.arcane.react.api.web;

import art.arcane.react.api.rendering.Graph;
import art.arcane.react.api.sampler.Sampler;
import art.arcane.react.api.web.dto.SamplerDto;

public class MetricsSerializer {

    public SamplerDto toDto(Sampler s) {
        double value = s.sample();
        String suffix = s.formattedSuffix(value);
        Graph g = Graph.of(s);
        int historySize = Math.min(g.getSize(), 128);
        double[] history = new double[historySize];
        for (int i = 0; i < historySize; i++) {
            history[i] = g.get(i);
        }
        SamplerDto dto = new SamplerDto();
        dto.id = s.getId();
        dto.name = s.getName();
        dto.value = value;
        dto.suffix = suffix;
        dto.display = s.formattedValue(value);
        dto.min = g.getMin();
        dto.max = g.getMax();
        dto.history = history;
        return dto;
    }
}
