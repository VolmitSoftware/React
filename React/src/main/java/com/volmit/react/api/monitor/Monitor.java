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

package com.volmit.react.api.monitor;

import com.volmit.react.React;
import com.volmit.react.api.sampler.Sampler;
import com.volmit.react.util.scheduling.Ticked;
import org.bukkit.event.Listener;

import java.util.Map;

public interface Monitor extends Ticked, Listener {
    Map<Sampler, Double> getSamplers();

    /**
     * Called when a sampler has changed
     */
    void flush();

    default Monitor sample(String samplerId) {
        return sample((Sampler) React.sampler(samplerId));
    }

    default boolean isAlwaysFlushing() {
        return false;
    }

    default Monitor sample(Sampler sampler) {
        getSamplers().put(sampler, sampler.sample());
        return this;
    }

    default void start() {
        React.instance.getTicker().register(this);
        React.instance.registerListener(this);
    }

    default void stop() {
        unregister();
        React.instance.unregisterListener(this);
    }
}
