package com.volmit.react.core.controller;

import com.volmit.react.React;
import com.volmit.react.api.feature.Feature;
import com.volmit.react.api.feature.ReactTickedFeature;
import com.volmit.react.content.feature.FeatureUnknown;
import com.volmit.react.util.io.JarScanner;
import com.volmit.react.util.plugin.IController;
import com.volmit.react.util.scheduling.TickedObject;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

@EqualsAndHashCode(callSuper = true)
@Data
public class FeatureController extends TickedObject implements IController {
    private Map<String, Feature> features;
    private Map<String, Feature> activeFeatures;
    private Map<String, ReactTickedFeature> tickedFeatures;

    private Feature unknown;

    public FeatureController() {
        super("react", "feature", 50);
        start();
    }

    @Override
    public void onTick() {

    }

    @Override
    public String getName() {
        return "Feature";
    }

    public Feature getFeature(String id) {
        Feature s = features.get(id);

        s = s == null ? unknown : s;

        if (s == null) {
            s = new FeatureUnknown();
        }

        return s;
    }

    public void activateFeature(Feature feature) {
        if (!activeFeatures.containsKey(feature.getId())) {
            activeFeatures.put(feature.getId(), feature);
            feature.onActivate();

            if (feature.getTickInterval() > 0) {
                tickedFeatures.put(feature.getId(), new ReactTickedFeature(feature));
            }

            React.verbose("Activated Feature: " + feature.getName());
        }
    }

    public void deactivateFeature(Feature feature) {
        activeFeatures.remove(feature.getId());
        ReactTickedFeature t = tickedFeatures.remove(feature.getId());

        if (t != null) {
            t.unregister();
        }

        feature.onDeactivate()
        ;
        React.verbose("Deactivated Feature: " + feature.getName());
    }

    @Override
    public void start() {
        activeFeatures = new HashMap<>();
        tickedFeatures = new HashMap<>();
        features = new HashMap<>();
        features.put(FeatureUnknown.ID, new FeatureUnknown());
        String p = React.instance.jar().getAbsolutePath();
        p = p.replaceAll("\\Q.jar.jar\\E", ".jar");

        JarScanner j = new JarScanner(new File(p), "com.volmit.react.content.feature");
        try {
            j.scan();
            j.getClasses().stream()
                    .filter(i -> i.isAssignableFrom(Feature.class) || Feature.class.isAssignableFrom(i))
                    .map((i) -> {
                        try {
                            return (Feature) i.getConstructor().newInstance();
                        } catch (Throwable e) {
                            e.printStackTrace();
                        }

                        return null;
                    })
                    .forEach((i) -> {
                        if (i != null) {
                            features.put(i.getId(), i);
                        }
                    });
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public void postStart() {
        React.info("Registered " + features.size() + " Features");

        for (String i : features.keySet()) {
            Feature f = features.get(i);
            f.loadConfiguration();

            if (f.isEnabled()) {
                activateFeature(f);
            }
        }

        React.info("Activated " + features.size() + " Features");
    }

    @Override
    public void stop() {
        features.values().forEach(Feature::onDeactivate);
    }
}
