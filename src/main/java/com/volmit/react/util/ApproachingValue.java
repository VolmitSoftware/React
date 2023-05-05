package com.volmit.react.util;

import java.util.concurrent.atomic.AtomicReference;

public class ApproachingValue
{
    private double value;
    private final double percent;

    public ApproachingValue(double percent) {
        this.value = 0;
        this.percent = percent;
    }

    public double get(Double realValue) {
        if(realValue == null) {
            return value;
        }

        value = M.lerp(this.value, realValue, percent);
        return value;
    }
}
