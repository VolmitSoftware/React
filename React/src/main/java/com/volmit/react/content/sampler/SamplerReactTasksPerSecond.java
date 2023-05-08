package com.volmit.react.content.sampler;

import com.volmit.react.React;
import com.volmit.react.api.sampler.ReactCachedSampler;
import com.volmit.react.util.format.Form;
import org.bukkit.Material;

public class SamplerReactTasksPerSecond extends ReactCachedSampler {
    public static final String ID = "react-tasks-per-second";

    public SamplerReactTasksPerSecond() {
        super(ID, 50);
    }

    @Override
    public Material getIcon() {
        return Material.GLOWSTONE_DUST;
    }

    @Override
    public double onSample() {
        return React.ticker.getTasksPerSecond();
    }

    @Override
    public String formattedValue(double t) {
        return Form.f(Math.ceil(t));
    }

    @Override
    public String formattedSuffix(double t) {
        return "TASK/s";
    }
}
