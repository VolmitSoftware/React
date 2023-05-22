package com.volmit.react.core.controller;

import art.arcane.chrono.PrecisionStopwatch;
import com.volmit.react.React;
import com.volmit.react.api.sampler.Sampler;
import com.volmit.react.content.sampler.SamplerUnknown;
import com.volmit.react.util.io.JarScanner;
import com.volmit.react.util.plugin.IController;
import com.volmit.react.util.registry.Registry;
import com.volmit.react.util.scheduling.J;
import com.volmit.react.util.scheduling.TickedObject;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

@EqualsAndHashCode(callSuper = true)
@Data
public class SampleController extends TickedObject implements IController {
    private transient Registry<Sampler> samplers;

    public SampleController() {
        super("react", "sample", 3000);
        start();
    }

    @Override
    public void onTick() {

    }

    @Override
    public String getName() {
        return "Sample";
    }

    public Sampler getSampler(String id) {
        return samplers.get(id);
    }

    @Override
    public void start() {
        samplers = new Registry<>(Sampler.class, "com.volmit.react.content.sampler");
    }

    public void postStart() {
        samplers.all().forEach(Sampler::start);
        React.info("Registered " + samplers.size() + " Samplers");
        J.s(() -> React.controller(PlayerController.class).updateMonitors());
    }

    @Override
    public void stop() {
        samplers.all().forEach(Sampler::stop);
    }
}
