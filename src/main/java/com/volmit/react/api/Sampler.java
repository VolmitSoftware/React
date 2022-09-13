package com.volmit.react.api;

import com.volmit.react.util.tick.Ticked;

public interface Sampler extends Ticked {
    double sample();

    String format(double t);

    String getId();

    default String sampleFormatted(){
        return format(sample());
    }
}
