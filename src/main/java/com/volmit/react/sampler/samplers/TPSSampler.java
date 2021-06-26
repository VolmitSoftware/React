package com.volmit.react.sampler.samplers;

import art.arcane.quill.format.Form;
import com.volmit.react.sampler.DoubleSampler;
import lombok.Data;
import org.bukkit.ChatColor;

@Data
public class TPSSampler extends DoubleSampler {
    public TPSSampler()
    {
        setName("Ticks Per Second");
        setSuffix(" TPS");
        setValueColor(ChatColor.GREEN);
        setMaxSamplesPerSecond(20);
        setLastValue(20);
    }

    @Override
    public double onSample() {
        return 0;
    }

    @Override
    public String format(double value) {
        return getValueColor() + Form.f(value, 0) + getSuffix();
    }
}
