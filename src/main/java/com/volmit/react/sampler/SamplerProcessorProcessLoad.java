package com.volmit.react.sampler;

import art.arcane.amulet.util.Platform;
import com.volmit.react.api.sampler.ReactCachedSampler;
import com.volmit.react.util.Form;

public class SamplerProcessorProcessLoad extends ReactCachedSampler {
    public static final String ID = "processor-process-load";

    public SamplerProcessorProcessLoad() {
        super(ID, 100);
    }

    @Override
    public double onSample() {
        return Platform.CPU.getLiveProcessCPULoad();
    }

    @Override
    public String format(double t) {
        return Form.pc(t, 0) + " CPU";
    }
}
