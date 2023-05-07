package com.volmit.react.api.monitor;

import com.volmit.react.api.graph.RollingGraph;
import com.volmit.react.model.ReactPlayer;
import com.volmit.react.api.sampler.Sampler;

import java.util.Map;

public class GraphMonitor extends PlayerMonitor {
    private final Map<Sampler, RollingGraph> graphs;
    private final int graphHeight;
    private final int graphWidth;
    private final int history;

    public GraphMonitor(String type, ReactPlayer player, long interval, int history, int graphWidth, int graphHeight) {
        super(type, player, interval);
        this.graphs = new java.util.HashMap<>();
        this.history = history;
        this.graphWidth = graphWidth;
        this.graphHeight = graphHeight;
    }

    @Override
    public void start()
    {
        super.start();
    }

    @Override
    public void stop()
    {
        super.stop();
    }

    @Override
    public void flush() {
        for(Sampler i : getSamplers().keySet()) {
            if(!graphs.containsKey(i)) {
                graphs.put(i, new RollingGraph(history));
            }
        }
    }
}
