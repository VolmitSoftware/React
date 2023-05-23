package com.volmit.react.content.command.parser;

import art.arcane.edict.api.parser.SelectionParser;
import com.volmit.react.React;
import com.volmit.react.api.sampler.Sampler;
import com.volmit.react.core.controller.SampleController;

import java.util.List;

public class SamplerParser implements SelectionParser<Sampler> {
    @Override
    public List<Sampler> getSelectionOptions() {
        return React.controller(SampleController.class).getSamplers().all().stream().toList();
    }

    @Override
    public String getName(Sampler sampler) {
        return sampler.getId();
    }
}
