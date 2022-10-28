package com.volmit.react.sampler;

import com.volmit.react.api.sampler.ReactCachedSampler;
import com.volmit.react.util.Form;

public class SamplerUnknown extends ReactCachedSampler {
    public static final String ID = "unknown";

    public SamplerUnknown() {
        super(ID, 30000);
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
