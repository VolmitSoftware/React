package com.volmit.react.api.rendering;

import com.volmit.react.api.sampler.Sampler;
import com.volmit.react.util.math.RollingSequence;
import it.unimi.dsi.fastutil.doubles.DoubleArrayList;
import it.unimi.dsi.fastutil.doubles.DoubleList;
import lombok.Getter;

import java.awt.GradientPaint;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Getter
public class Graph {
    private static Map<String, Graph> graphs = new HashMap<>();

    public static Graph of(Sampler sampler) {
        Graph g = graphs.computeIfAbsent(sampler.getName(), (k) -> new Graph());
        g.push(sampler.sample());
        return g;
    }

    private final DoubleList sequence;
    private final RollingSequence rs = new RollingSequence(3);

    public Graph() {
        sequence = new DoubleArrayList();
    }

    public double get(int index) {
        return sequence.size() > index ? sequence.getDouble(index) : 0;
    }

    public void push(double v) {
        rs.put(v);
        sequence.add(0, rs.getAverage());
        while(sequence.size() > 128) {
            sequence.removeDouble(sequence.size() - 1);
        }
    }

    public double getPaddedMax(double percent) {
        return getMax() + ((getMax() - getMin()) * percent);
    }

    public double getPaddedMin(double percent) {
        return getMin() - ((getMax() - getMin()) * percent);
    }

    public double getRange() {
        return getMax() - getMin();
    }

    public double getMax() {
        return sequence.doubleStream().max().orElse(0);
    }

    public double getMin() {
        return sequence.doubleStream().min().orElse(0);
    }
}
