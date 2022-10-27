package com.volmit.react.sampler;

import art.arcane.amulet.util.Platform;
import com.volmit.react.api.sampler.ReactCachedSampler;
import com.volmit.react.util.AsyncRequest;
import com.volmit.react.util.Form;

public class SamplerProcessorSystemLoad extends ReactCachedSampler {
    public static final String ID = "processor-system-load";
    private final AsyncRequest<Double> poller;

    public SamplerProcessorSystemLoad() {
        super(ID, 100);
        poller = new AsyncRequest<>(Platform.CPU::getCPULoad, 0D);
    }

    @Override
    public double onSample() {
        return poller.request();
    }

    @Override
    public String format(double t) {
        return Form.pc(t, 0) + " CPU";
    }
}