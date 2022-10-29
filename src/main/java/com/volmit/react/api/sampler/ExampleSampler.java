package com.volmit.react.api.sampler;

@XReactSampler(id = "some-sampler", interval = 50, suffix = "/s")
public class ExampleSampler {
    public double sample() {
        return -3;
    }
}
