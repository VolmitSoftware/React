package com.volmit.react.content.sampler;

import com.volmit.react.api.sampler.ReactCachedSampler;
import com.volmit.react.util.atomics.AsyncRequest;
import com.volmit.react.util.format.Form;
import com.volmit.react.util.reflect.Platform;

public class SamplerProcessorProcessLoad extends ReactCachedSampler {
    public static final String ID = "processor-process-load";
    private final AsyncRequest<Double> poller;

    public SamplerProcessorProcessLoad() {
        super(ID, 100);
        poller = new AsyncRequest<>(Platform.CPU::getLiveProcessCPULoad, 0D);
    }

    @Override
    public double onSample() {
        return poller.request();
    }

    @Override
    public String formattedValue(double t) {
        return Form.pc(t, 0);
    }

    @Override
    public String formattedSuffix(double t) {
        return "pCPU";
    }
}
