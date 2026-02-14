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

package art.arcane.react.core.controller;

import art.arcane.react.React;
import art.arcane.react.api.sampler.Sampler;
import art.arcane.react.util.plugin.IController;
import art.arcane.react.util.registry.Registry;
import art.arcane.react.util.scheduling.J;
import art.arcane.react.util.scheduling.TickedObject;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.bukkit.event.Listener;

@EqualsAndHashCode(callSuper = true)
@Data
public class SampleController extends TickedObject implements IController {
    private transient Registry<Sampler> samplers;

    public SampleController() {
        super("react", "sample", 3000);
    }

    @Override
    public void onTick() {

    }

    @Override
    public String getName() {
        return "Sample";
    }

    public Sampler getSampler(String id) {
        return samplers.get(id);
    }

    @Override
    public void start() {
        samplers = new Registry<>(Sampler.class, "art.arcane.react.content.sampler");
    }

    public void postStart() {
        samplers.all().forEach(Sampler::start);
        samplers.all().forEach(((a) -> {
            if (a instanceof Listener l) {
                React.instance.registerListener(l);
            }
        }));
        React.info("Registered " + samplers.size() + " Samplers");
        J.s(() -> React.controller(PlayerController.class).updateMonitors());
    }

    @Override
    public void stop() {
        samplers.all().forEach(((a) -> {
            if (a instanceof Listener l) {
                React.instance.unregisterListener(l);
            }
        }));
        samplers.all().forEach(Sampler::stop);
    }
}
