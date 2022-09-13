package com.volmit.react.sampler;

import com.volmit.react.api.ReactCachedSampler;
import com.volmit.react.api.Sampler;
import com.volmit.react.util.Form;

public class SamplerMemoryGarbage extends ReactCachedSampler {
    public static final String ID = "memory-garbage";
    private Sampler memoryUsedAfterGC;
    private Sampler memoryUsed;

    public SamplerMemoryGarbage() {
        super(ID, 50);
    }

    @Override
    public void start() {
        memoryUsedAfterGC = getSampler(SamplerMemoryUsedAfterGC.ID);
        memoryUsed = getSampler(SamplerMemoryUsed.ID);
    }

    @Override
    public double onSample() {
        return Math.max(memoryUsed.sample() - memoryUsedAfterGC.sample(), 0);
    }

    @Override
    public String format(double t) {
        return Form.memSize((long) t, 1);
    }
}
