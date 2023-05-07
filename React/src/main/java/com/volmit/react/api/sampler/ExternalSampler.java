package com.volmit.react.api.sampler;

import art.arcane.curse.model.CursedComponent;
import art.arcane.curse.model.CursedMethod;
import com.volmit.react.util.format.Form;

public class ExternalSampler extends ReactCachedSampler {
    private final CursedComponent component;
    private final CursedMethod method;
    private final String id;
    private final int interval;
    private final String suffix;

    public ExternalSampler(CursedComponent component, CursedMethod method, String id, int interval, String suffix) {
        super(id, interval);
        this.component = component;
        this.method = method;
        this.id = id;
        this.interval = interval;
        this.suffix = suffix;
    }

    @Override
    public double onSample() {
        return method.invoke();
    }

    @Override
    public String formattedValue(double t) {
        return Form.f(Math.round(t));
    }

    @Override
    public String formattedSuffix(double t) {
        return suffix;
    }
}
