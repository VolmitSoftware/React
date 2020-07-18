package com.volmit.react.legacy.server;

import com.volmit.react.api.SampledType;
import primal.lang.collection.GMap;

public class ReactData {
    private final GMap<String, Double> samples;

    public ReactData() {
        samples = new GMap<>();
    }

    public void sample() {
        for (SampledType s : SampledType.values()) {
            samples.put(s.get().getID().toLowerCase(), s.get().getValue());
        }
    }

    public GMap<String, Double> getSamples() {
        return samples;
    }
}
