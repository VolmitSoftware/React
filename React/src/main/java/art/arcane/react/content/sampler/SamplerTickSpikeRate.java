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

package art.arcane.react.content.sampler;

import art.arcane.react.api.event.layer.ServerTickEvent;
import art.arcane.react.api.sampler.ReactCachedSampler;
import art.arcane.volmlib.util.format.Form;
import org.bukkit.Material;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

import java.util.ArrayDeque;

public class SamplerTickSpikeRate extends ReactCachedSampler implements Listener {
    public static final String ID = "tick-spike-rate";
    private transient final ArrayDeque<Long> spikes = new ArrayDeque<>();
    private int spikeThresholdMS = 50;
    private int windowMS = 60000;
    private transient long lastTickMS = 0;

    public SamplerTickSpikeRate() {
        super(ID, 250);
    }

    @Override
    public Material getIcon() {
        return Material.REPEATER;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void on(ServerTickEvent event) {
        long now = System.currentTimeMillis();
        if (lastTickMS > 0 && now - lastTickMS >= spikeThresholdMS) {
            synchronized (spikes) {
                spikes.addLast(now);
                cleanup(now);
            }
        }

        lastTickMS = now;
    }

    @Override
    public double onSample() {
        synchronized (spikes) {
            cleanup(System.currentTimeMillis());
            return spikes.size() * (60000D / Math.max(1000D, windowMS));
        }
    }

    private void cleanup(long now) {
        while (!spikes.isEmpty() && now - spikes.peekFirst() > windowMS) {
            spikes.removeFirst();
        }
    }

    @Override
    public String formattedValue(double t) {
        return Form.f(t, 1);
    }

    @Override
    public String formattedSuffix(double t) {
        return "SPIKE/m";
    }
}
