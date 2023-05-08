package com.volmit.react.content.sampler;

import com.volmit.react.api.sampler.ReactCachedSampler;
import com.volmit.react.api.sampler.Sampler;
import com.volmit.react.util.format.Form;
import org.bukkit.Material;

public class SamplerMemoryGarbage extends ReactCachedSampler {
    public static final String ID = "memory-garbage";
    private Sampler memoryUsedAfterGC;
    private Sampler memoryUsed;

    public SamplerMemoryGarbage() {
        super(ID, 50);
    }

    @Override
    public Material getIcon() {
        return Material.BROWN_DYE;
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
