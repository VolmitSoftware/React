package com.volmit.react.api.monitor.configuration;

import com.volmit.react.React;
import com.volmit.react.api.sampler.Sampler;
import lombok.Builder;
import lombok.Data;
import lombok.Singular;

import java.util.List;
import java.util.stream.Collectors;

@Data
@Builder
public class MonitorConfiguration {
    @Singular
    private List<MonitorGroup> groups;

    public List<Sampler> getAllSamplers() {
        return groups.stream()
                .flatMap(group -> group.getSamplers().stream())
                .map(i -> (Sampler) React.sampler(i))
                .collect(Collectors.toList());
    }
}
