package com.volmit.react.sampler;

import art.arcane.amulet.util.Platform;
import com.volmit.react.api.ReactCachedSampler;
import com.volmit.react.util.Form;

public class SamplerProcessorSystemLoad extends ReactCachedSampler {
    public static final String ID = "processor-system-load";

    public SamplerProcessorSystemLoad() {
        super(ID, 100);
    }

    @Override
    public double onSample() {
        return Platform.CPU.getCPULoad();
    }

    @Override
    public String format(double t) {
        return Form.pc(t, 0) + " CPU";
    }
}
