package com.volmit.react.content.sampler;

import com.volmit.react.React;
import com.volmit.react.api.sampler.ReactCachedSampler;
import com.volmit.react.util.format.Form;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;

public class SamplerReactJobBudget extends ReactCachedSampler {
    public static final String ID = "react-job-budget";

    public SamplerReactJobBudget() {
        super(ID, 50);
    }

    @Override
    public Material getIcon() {
        return Material.GLOWSTONE_DUST;
    }

    @Override
    public double onSample() {
        return React.instance.getJobController().getOverBudget();
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
        return Form.durationSplit(t, 2)[1] + " OVER";
    }
}
