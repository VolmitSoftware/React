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

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@EqualsAndHashCode(callSuper = true)
@Data
public class SampleController extends TickedObject implements IController {
  private transient final Map<String, SamplerState> samplerStates = new ConcurrentHashMap<>();
  private transient final Map<String, Object> samplerLocks = new ConcurrentHashMap<>();
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
    samplerStates.clear();
    samplerLocks.clear();
  }

  public void postStart() {
    samplers.all().forEach(this::startSamplerRuntime);
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
    samplers.all().forEach(this::stopSamplerRuntime);
    samplerStates.clear();
    samplerLocks.clear();
  }

  public boolean canSample(Sampler sampler) {
    if (sampler == null) {
      return false;
    }

    SamplerState state = samplerStates.get(sampler.getId());
    return state == SamplerState.STARTED;
  }

  public boolean reloadSamplerConfig(String id) {
    if (id == null || id.isBlank() || samplers == null) {
      return false;
    }

    Sampler sampler = samplers.get(id);
    if (sampler == null) {
      return false;
    }

    Object lock = samplerLocks.computeIfAbsent(id, k -> new Object());
    synchronized (lock) {
      setSamplerState(id, SamplerState.RELOADING);
      try {
        sampler.loadConfiguration();
        stopSamplerRuntime(sampler);
        startSamplerRuntime(sampler);
        return canSample(sampler);
      } catch (Throwable e) {
        setSamplerState(id, SamplerState.STOPPED);
        React.warn(
            "Sampler reload failed: id=" + id
                + " class=" + sampler.getClass().getSimpleName()
                + " state=" + samplerStates.get(id)
                + " cause=" + summarizeThrowable(e)
                + " config=/plugins/React/sampler/" + id + ".toml"
        );
        return false;
      }
    }
  }

  private void startSamplerRuntime(Sampler sampler) {
    if (sampler == null) {
      return;
    }

    setSamplerState(sampler.getId(), SamplerState.STARTING);
    try {
      sampler.start();
      setSamplerState(sampler.getId(), SamplerState.STARTED);
    } catch (Throwable e) {
      setSamplerState(sampler.getId(), SamplerState.STOPPED);
      React.warn(
          "Sampler start failed: id=" + sampler.getId()
              + " class=" + sampler.getClass().getSimpleName()
              + " cause=" + summarizeThrowable(e)
              + " config=/plugins/React/sampler/" + sampler.getId() + ".toml"
      );
    }
  }

  private void stopSamplerRuntime(Sampler sampler) {
    if (sampler == null) {
      return;
    }

    setSamplerState(sampler.getId(), SamplerState.STOPPING);
    try {
      sampler.stop();
    } catch (Throwable e) {
      React.warn(
          "Sampler stop failed: id=" + sampler.getId()
              + " class=" + sampler.getClass().getSimpleName()
              + " cause=" + summarizeThrowable(e)
      );
    } finally {
      setSamplerState(sampler.getId(), SamplerState.STOPPED);
    }
  }

  private String summarizeThrowable(Throwable throwable) {
    if (throwable == null) {
      return "unknown";
    }

    Throwable root = throwable;
    while (root.getCause() != null && root.getCause() != root) {
      root = root.getCause();
    }

    String message = root.getMessage();
    if (message == null || message.isBlank()) {
      return root.getClass().getSimpleName();
    }

    return root.getClass().getSimpleName() + ": " + message;
  }

  private void setSamplerState(String id, SamplerState state) {
    if (id == null || id.isBlank() || state == null) {
      return;
    }
    samplerStates.put(id, state);
  }

  private enum SamplerState {
    STARTING,
    STARTED,
    RELOADING,
    STOPPING,
    STOPPED
  }
}
