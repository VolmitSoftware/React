package com.volmit.react.content.sampler;

import com.volmit.react.api.sampler.ReactCachedSampler;
import com.volmit.react.util.format.Form;
import org.bukkit.Material;

public class SamplerMemoryFree extends ReactCachedSampler {
    public static final String ID = "memory-free";
    private transient final Runtime runtime;

    public SamplerMemoryFree() {
        super(ID, 50);
        this.runtime = Runtime.getRuntime();
    }

    @Override
    public Material getIcon() {
        return Material.WATER_BUCKET;
    }

    @Override
    public double onSample() {
        return runtime.maxMemory() - (runtime.totalMemory() - runtime.freeMemory());
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
        return Form.memSizeSplit((long) t, 1)[1];
    }
}
