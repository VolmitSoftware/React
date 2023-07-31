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

package com.volmit.react.api.rendering;

import com.volmit.react.api.sampler.Sampler;
import com.volmit.react.util.math.RollingSequence;
import it.unimi.dsi.fastutil.doubles.DoubleArrayList;
import it.unimi.dsi.fastutil.doubles.DoubleList;
import lombok.Getter;

import java.util.HashMap;
import java.util.Map;

@Getter
public class Graph {
    private static Map<String, Graph> graphs = new HashMap<>();
    private final DoubleList sequence;
    private final RollingSequence rs = new RollingSequence(3);

    public Graph() {
        sequence = new DoubleArrayList();
    }

    public static Graph of(Sampler sampler) {
        Graph g = graphs.computeIfAbsent(sampler.getName(), (k) -> new Graph());
        g.push(sampler.sample());
        return g;
    }

    public double get(int index) {
        return sequence.size() > index ? sequence.getDouble(index) : 0;
    }

    public void push(double v) {
        rs.put(v);
        sequence.add(0, rs.getAverage());
        while (sequence.size() > 128) {
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
