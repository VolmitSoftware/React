package com.volmit.react.api.monitor.configuration;

import com.volmit.react.React;
import com.volmit.react.api.sampler.Sampler;
import com.volmit.react.util.C;
import lombok.Builder;
import lombok.Data;
import lombok.Singular;

import java.util.List;
import java.util.stream.Collectors;

@Builder
@Data
public class MonitorGroup {
    private C color;
    private String name;

    @Singular
    private List<String> samplers;

    public Sampler getHeadSampler() {
        return React.instance.getSampleController().getSampler(samplers.get(0));
    }

    public List<Sampler> getSubSamplers() {
        return samplers.stream().skip(1).map(i -> React.instance.getSampleController().getSampler(i)).collect(Collectors.toList());
    }
}
