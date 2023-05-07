package com.volmit.react.util.math;

import com.volmit.react.legacyutil.M;

public class ApproachingValue {
    private final double percent;
    private double value;

    public ApproachingValue(double percent) {
        this.value = 0;
        this.percent = percent;
    }

    public double get(Double realValue) {
        if (realValue == null) {
            return value;
        }

        value = M.lerp(this.value, realValue, percent);
        return value;
    }
}
