package com.volmit.react.content.sampler;

import com.volmit.react.api.sampler.ReactTickedSampler;
import com.volmit.react.util.format.Form;

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
        if (allocated >= 0) {
            return allocated * (1000D / getInterval());
        }

        return 0;
    }

    @Override
    public String formattedValue(double t) {
        String[] s = Form.memSizeSplit((long) t, 1);
        if (s[1].equalsIgnoreCase("mb")) {
            return Form.memSizeSplit((long) t, 0)[0];
        } else {
            return s[0];
        }
    }

    @Override
    public String formattedSuffix(double t) {
        return Form.memSizeSplit((long) t, 1)[1] + "/s";
    }
}
