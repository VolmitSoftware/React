package com.volmit.react.content.sampler;

import com.volmit.react.React;
import com.volmit.react.api.sampler.ReactCachedSampler;
import com.volmit.react.core.controller.JobController;
import com.volmit.react.util.format.Form;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;

public class SamplerReactJobQueueTime extends ReactCachedSampler {
    public static final String ID = "react-job-queue-time";

    public SamplerReactJobQueueTime() {
        super(ID, 50);
    }

    @Override
    public Material getIcon() {
        return Material.MUSIC_DISC_5;
    }

    @Override
    public double onSample() {
        return React.controller(JobController.class).getQueuedComputeTime();
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
        return Form.durationSplit(t, 2)[1] + " JQT";
    }
}
