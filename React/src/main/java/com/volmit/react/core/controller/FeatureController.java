/*
 *  Copyright (c) 2016-2025 Arcane Arts (Volmit Software)
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program.  If not, see <https://www.gnu.org/licenses/>.
 *
 *
 */

package com.volmit.react.core.controller;

import com.volmit.react.React;
import com.volmit.react.api.feature.Feature;
import com.volmit.react.api.feature.ReactTickedFeature;
import com.volmit.react.content.feature.FeatureUnknown;
import com.volmit.react.util.plugin.IController;
import com.volmit.react.util.registry.Registry;
import com.volmit.react.util.scheduling.TickedObject;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.bukkit.event.Listener;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

@EqualsAndHashCode(callSuper = true)
@Data
public class FeatureController extends TickedObject implements IController {
    private transient Registry<Feature> features;
    private transient Map<String, Feature> activeFeatures;
    private transient Map<String, ReactTickedFeature> tickedFeatures;

    private Feature unknown;

    public FeatureController() {
        super("react", "feature", 50);
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
            if (feature instanceof Listener l) {
                React.instance.registerListener(l);
            }

            if (feature.getTickInterval() > 0) {
                tickedFeatures.put(feature.getId(), new ReactTickedFeature(feature));
            }

            React.verbose("Activated Feature: " + feature.getName());
        }
    }

    public void deactivateFeature(Feature feature) {
        if (feature instanceof Listener l) {
            React.instance.unregisterListener(l);
        }
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
        features = new Registry<>(Feature.class, "com.volmit.react.content.feature");
    }

    public void postStart() {
        React.info("Registered " + features.size() + " Features");

        for (String i : features.ids()) {
            Feature f = features.get(i);

            if (f.isEnabled()) {
                activateFeature(f);
            }
        }

        React.info("Activated " + activeFeatures.size() + " Features");
    }

    @Override
    public void stop() {
        new ArrayList<>(activeFeatures.values()).forEach(this::deactivateFeature);
    }
}
