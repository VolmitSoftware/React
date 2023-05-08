package com.volmit.react.content.sampler;

import com.volmit.react.api.sampler.ReactCachedSampler;
import com.volmit.react.util.atomics.AsyncRequest;
import com.volmit.react.util.format.Form;
import com.volmit.react.util.reflect.Platform;
import org.bukkit.Material;

public class SamplerProcessorSystemLoad extends ReactCachedSampler {
    public static final String ID = "processor-system-load";
    private final AsyncRequest<Double> poller;

    public SamplerProcessorSystemLoad() {
        super(ID, 100);
        poller = new AsyncRequest<>(Platform.CPU::getCPULoad, 0D);
    }

    @Override
    public Material getIcon() {
        return Material.RED_CANDLE;
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
        return "CPU";
    }
}
