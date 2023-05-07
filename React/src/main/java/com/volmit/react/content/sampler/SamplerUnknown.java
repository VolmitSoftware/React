package com.volmit.react.content.sampler;

import com.volmit.react.api.sampler.ReactCachedSampler;

public class SamplerUnknown extends ReactCachedSampler {
    public static final String ID = "unknown";

    public SamplerUnknown() {
        super(ID, 30000);
    }

    public SamplerUnknown(String id) {
        super(id, 30000);
    }

    @Override
    public double onSample() {
        return 0;
    }

    @Override
    public String formattedValue(double t) {
        return "---";
    }

    @Override
    public String formattedSuffix(double t) {
        return "";
    }
}
