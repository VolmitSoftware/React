package com.volmit.react.api.graph;

import java.util.ArrayList;
import java.util.List;

public class RollingGraph {
    private final List<Double> values;
    private final int maxLength;

    public RollingGraph(int length) {
        values = new ArrayList<>(length);
        this.maxLength = length;
    }

    public void push(double value) {
        values.add(0, value);

        while (values.size() > maxLength) {
            values.remove(values.size() - 1);
        }
    }

    public List<Double> getValues() {
        return values;
    }

    public int getLength() {
        return values.size();
    }
}
