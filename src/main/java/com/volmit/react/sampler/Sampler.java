package com.volmit.react.sampler;

import org.bukkit.ChatColor;

public interface Sampler
{
    String getName();

    String getDescription();

    String getPrefix();

    ChatColor getValueColor();

    String getSuffix();

    double sample();

    String format(double value);

    boolean shouldSample();

    double getLastValue();

    void setLastValue(double t);
}
