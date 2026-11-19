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

import art.arcane.react.api.sampler.Sampler;
import art.arcane.react.api.sampler.SamplerHosts;
import art.arcane.react.platform.SamplerHost;
import art.arcane.react.util.common.scheduling.J;
import art.arcane.react.util.plugin.IController;
import art.arcane.react.util.project.registry.Registry;
import lombok.Data;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Data
public class SampleController implements IController {
  private transient final Map<String, SamplerState> samplerStates = new ConcurrentHashMap<>();
  private transient final Map<String, Object> samplerLocks = new ConcurrentHashMap<>();
  private transient Registry<Sampler> samplers;

  @Override
  public String getId() {
    return "sample";
  }

  @Override
  public String getName() {
    return "Sample";
  }

  public Sampler getSampler(String id) {
    if (samplers == null) {
      return null;
    }
    return samplers.get(id);
  }

  @Override
  public void start() {
    samplers = new Registry<>(Sampler.class, "art.arcane.react.content.sampler");
    samplerStates.clear();
    samplerLocks.clear();
  }

  public void postStart() {
    if (samplers == null) {
      return;
    }

    samplers.all().forEach(this::startSamplerRuntime);
    SamplerHost host = SamplerHosts.sampler().orElse(null);
    if (host != null) {
      samplers.all().forEach(host::attachSampler);
    }
    SamplerHosts.verbose("Registered " + samplers.size() + " Samplers");
    if (host != null) {
      J.s(host::refreshMonitors);
    }
  }

  @Override
  public void stop() {
    Registry<Sampler> localSamplers = samplers;
    if (localSamplers == null) {
      samplerStates.clear();
      samplerLocks.clear();
      return;
    }

    SamplerHost host = SamplerHosts.sampler().orElse(null);
    if (host != null) {
      localSamplers.all().forEach(host::detachSampler);
    }
    localSamplers.all().forEach(this::stopSamplerRuntime);
    samplerStates.clear();
    samplerLocks.clear();
    samplers = null;
  }

  public boolean registerSampler(Sampler sampler) {
    if (sampler == null || samplers == null) {
      return false;
    }

    String id = sampler.getId();
    if (id == null || id.isBlank() || samplers.get(id) != null) {
      return false;
    }

    if (samplers.register(sampler) != sampler) {
      return false;
    }

    startSamplerRuntime(sampler);

    SamplerHost host = SamplerHosts.sampler().orElse(null);
    if (host != null) {
      host.attachSampler(sampler);
      host.samplerRegistered(sampler);
      J.s(host::refreshMonitors);
    }

    return true;
  }

  public boolean unregisterSampler(String id) {
    if (id == null || id.isBlank() || samplers == null) {
      return false;
    }

    Sampler sampler = samplers.get(id);
    if (sampler == null) {
      return false;
    }

    SamplerHost host = SamplerHosts.sampler().orElse(null);
    if (host != null) {
      host.detachSampler(sampler);
    }

    stopSamplerRuntime(sampler);
    samplers.unregister(id);
    samplerStates.remove(id);
    samplerLocks.remove(id);

    if (host != null) {
      host.samplerUnregistered(id);
      J.s(host::refreshMonitors);
    }

    return true;
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
        if (!sampler.reloadConfiguration()) {
          setSamplerState(id, SamplerState.STOPPED);
          return false;
        }
        stopSamplerRuntime(sampler);
        startSamplerRuntime(sampler);
        return canSample(sampler);
      } catch (Throwable e) {
        setSamplerState(id, SamplerState.STOPPED);
        SamplerHosts.warn(
            "Sampler reload failed: id=" + id
                + " class=" + sampler.getClass().getSimpleName()
                + " state=" + samplerStates.get(id)
                + " cause=" + summarizeThrowable(e)
                + " config=/plugins/React/sampler/" + id + ".toml",
            e
        );
        return false;
      }
    }
  }

  public boolean applySamplerConfig(String id, Object preparedConfiguration) {
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
        if (!sampler.applyConfigurationSnapshot(preparedConfiguration)) {
          setSamplerState(id, SamplerState.STOPPED);
          return false;
        }
        stopSamplerRuntime(sampler);
        startSamplerRuntime(sampler);
        return canSample(sampler);
      } catch (Throwable e) {
        setSamplerState(id, SamplerState.STOPPED);
        SamplerHosts.warn(
            "Sampler hotload apply failed: id=" + id
                + " class=" + sampler.getClass().getSimpleName()
                + " state=" + samplerStates.get(id)
                + " cause=" + summarizeThrowable(e)
                + " config=/plugins/React/sampler/" + id + ".toml",
            e
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
      SamplerHosts.warn(
          "Sampler start failed: id=" + sampler.getId()
              + " class=" + sampler.getClass().getSimpleName()
              + " cause=" + summarizeThrowable(e)
              + " config=/plugins/React/sampler/" + sampler.getId() + ".toml",
          e
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
      SamplerHosts.warn(
          "Sampler stop failed: id=" + sampler.getId()
              + " class=" + sampler.getClass().getSimpleName()
              + " cause=" + summarizeThrowable(e),
          e
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
