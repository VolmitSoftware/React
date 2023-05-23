package com.volmit.react.content.command.parser;

import art.arcane.edict.api.parser.SelectionParser;
import com.volmit.react.React;
import com.volmit.react.api.feature.Feature;
import com.volmit.react.core.controller.FeatureController;

import java.util.List;

public class FeatureParser implements SelectionParser<Feature> {
    @Override
    public List<Feature> getSelectionOptions() {
        return React.controller(FeatureController.class).getFeatures().all().stream().toList();
    }

    @Override
    public String getName(Feature feature) {
        return feature.getId();
    }
}
