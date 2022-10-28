package com.volmit.react.sampler;

import com.volmit.react.api.sampler.ReactTickedSampler;
import com.volmit.react.util.Form;

import java.util.concurrent.atomic.AtomicLong;

public class SamplerMemoryUsedAfterGC extends ReactTickedSampler {
    public static final String ID = "memory-used-after-gc";
    private final AtomicLong lastMemory;
    private final AtomicLong lastMemoryPostGC;
    private final Runtime runtime;

    public SamplerMemoryUsedAfterGC() {
        super(ID, 50, 20);
        this.runtime = Runtime.getRuntime();
        this.lastMemory = new AtomicLong(runtime.totalMemory() - runtime.freeMemory());
        this.lastMemoryPostGC = new AtomicLong(lastMemory.get());
    }

    @Override
    public double onSample() {
        long mem = runtime.totalMemory() - runtime.freeMemory();

        if(lastMemory.get() > mem) {
            lastMemoryPostGC.set(mem);
        }

        lastMemory.set(mem);
        return lastMemoryPostGC.get();
    }

    @Override
    public String formattedValue(double t) {
        return Form.memSizeSplit((long) t, 1)[0];
    }

    @Override
    public String formattedSuffix(double t) {
        return Form.memSizeSplit((long) t, 1)[1];
    }
}
