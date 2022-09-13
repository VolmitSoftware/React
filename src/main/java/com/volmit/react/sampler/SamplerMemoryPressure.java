package com.volmit.react.sampler;

import com.volmit.react.api.ReactTickedSampler;
import com.volmit.react.util.Form;

import java.util.concurrent.atomic.AtomicLong;

public class SamplerMemoryPressure extends ReactTickedSampler {
    public static final String ID = "memory-pressure";
    private final AtomicLong lastMemory;
    private final Runtime runtime;

    public SamplerMemoryPressure() {
        super(ID, 50, 20);
        this.runtime = Runtime.getRuntime();
        this.lastMemory = new AtomicLong(runtime.totalMemory() - runtime.freeMemory());
    }

    @Override
    public double onSample() {
        long mem = runtime.totalMemory() - runtime.freeMemory();
        long allocated = mem - lastMemory.get();
        lastMemory.set(mem);
        if(allocated >= 0) {
           return allocated * (1000D / getInterval());
        }

        return 0;
    }

    @Override
    public String format(double t) {
        return Form.memSize((long) t, 1) + "/s";
    }
}
