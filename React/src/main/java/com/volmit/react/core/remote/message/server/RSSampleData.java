package com.volmit.react.core.remote.message.server;

import com.volmit.react.api.sampler.Sampler;
import com.volmit.react.core.remote.ReactWebsocket;
import com.volmit.react.core.remote.message.ReactMessage;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@EqualsAndHashCode(callSuper = true)
@Builder
@Data
public class RSSampleData extends ReactMessage {
    private Map<String, Double> values;
    private Map<String, String> formatted;

    public RSSampleData() {
        super("sample-data");
        values = new HashMap<>();
        formatted = new HashMap<>();
    }

    public void sample(Sampler s) {
        double v = s.sample();
        values.put(s.getId(), v);
        formatted.put(s.getId(), s.format(v));
    }

    @Override
    public void handle(ReactWebsocket socket) {

    }
}
