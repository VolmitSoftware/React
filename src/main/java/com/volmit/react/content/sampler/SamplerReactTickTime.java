package com.volmit.react.content.sampler;

import com.volmit.react.React;
import com.volmit.react.api.sampler.ReactCachedSampler;
import com.volmit.react.legacyutil.Form;
import net.kyori.adventure.text.Component;

public class SamplerReactTickTime extends ReactCachedSampler {
    public static final String ID = "react-tick-time";

    public SamplerReactTickTime() {
        super(ID, 50);
    }

    @Override
    public double onSample() {
        return React.ticker.getTickTime();
    }

    @Override
    public String format(double t) {
        return formattedValue(t) + formattedSuffix(t);
    }

    @Override
    public Component format(Component value, Component suffix) {
        return Component.empty().append(value).append(suffix);
    }

    @Override
    public String formattedValue(double t) {
        return Form.durationSplit(t, 2)[0];
    }

    @Override
    public String formattedSuffix(double t) {
        return Form.durationSplit(t, 2)[1] + " RTT";
    }
}
