package com.volmit.react.sampler;

import com.volmit.react.api.sampler.ReactCachedSampler;
import com.volmit.react.util.Form;

public class SamplerProcessorOutsideLoad extends ReactCachedSampler {
    public static final String ID = "cpu-outside";

    public SamplerProcessorOutsideLoad() {
        super(ID, 250);
    }

    @Override
    public double onSample() {
        return Math.max(0, getSampler(SamplerProcessorSystemLoad.ID).sample() - getSampler(SamplerProcessorProcessLoad.ID).sample());
    }

    @Override
    public String formattedValue(double t) {
        return Form.pc(t, 0);
    }

    @Override
    public String formattedSuffix(double t) {
        return "xCPU";
    }
}
