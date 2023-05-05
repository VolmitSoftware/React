package com.volmit.react.sampler;

import com.volmit.react.api.sampler.ReactCachedSampler;
import com.volmit.react.util.AsyncRequest;
import com.volmit.react.util.Form;
import com.volmit.react.util.Platform;

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
