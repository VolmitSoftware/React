package com.volmit.react.util.uniques.features;

import com.volmit.react.util.math.RNG;
import com.volmit.react.util.uniques.UFeature;
import com.volmit.react.util.uniques.UFeatureMeta;
import com.volmit.react.util.uniques.UImage;

import java.util.function.Consumer;

public class UFNOOP implements UFeature {
    @Override
    public void render(UImage image, RNG rng, double t, Consumer<Double> progressor, UFeatureMeta meta) {
    }
}
