package com.volmit.react.sampler;

import com.volmit.react.api.ReactCachedSampler;
import com.volmit.react.util.Form;

public class SamplerMemoryFree extends ReactCachedSampler {
    public static final String ID = "memory-free";
    private final Runtime runtime;

    public SamplerMemoryFree() {
        super(ID, 50);
        this.runtime = Runtime.getRuntime();
    }

    @Override
    public double onSample() {
        return runtime.maxMemory() - (runtime.totalMemory() - runtime.freeMemory());
    }

    @Override
    public String format(double t) {
        return Form.memSize((long) t, 1);
    }
}
