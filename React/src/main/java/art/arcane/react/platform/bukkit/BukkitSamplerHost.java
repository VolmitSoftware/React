package art.arcane.react.platform.bukkit;

import art.arcane.react.React;
import art.arcane.react.api.sampler.Sampler;
import art.arcane.react.api.rendering.SamplerRenderer;
import art.arcane.react.core.controller.MapController;
import art.arcane.react.core.controller.ObserverController;
import art.arcane.react.core.controller.PlayerController;
import art.arcane.react.core.controller.SampleController;
import art.arcane.react.platform.SamplerHost;
import com.google.common.util.concurrent.AtomicDouble;
import org.bukkit.Chunk;
import org.bukkit.block.Block;
import org.bukkit.event.Listener;

import java.util.Optional;

final class BukkitSamplerHost implements SamplerHost {
  @Override
  public Optional<SampleController> sampleController() {
    try {
      return Optional.ofNullable(React.controller(SampleController.class));
    } catch (RuntimeException unavailable) {
      return Optional.empty();
    }
  }

  @Override
  public void attachSampler(Sampler sampler) {
    if (sampler instanceof Listener listener && React.instance != null) {
      React.instance.registerListener(listener);
    }
  }

  @Override
  public void detachSampler(Sampler sampler) {
    SamplerRenderer.forget(sampler);
    if (sampler instanceof Listener listener && React.instance != null) {
      React.instance.unregisterListener(listener);
    }
  }

  @Override
  public void samplerRegistered(Sampler sampler) {
    MapController mapController = React.controller(MapController.class);
    if (mapController != null) {
      mapController.registerRenderer(sampler);
    }
  }

  @Override
  public void samplerUnregistered(String samplerId) {
    MapController mapController = React.controller(MapController.class);
    if (mapController != null) {
      mapController.unregisterRenderer(samplerId);
    }
  }

  @Override
  public void refreshMonitors() {
    PlayerController playerController = React.controller(PlayerController.class);
    if (playerController != null) {
      playerController.updateMonitors();
    }
  }

  @Override
  public AtomicDouble chunkCounter(Object chunkOrBlock, Sampler sampler) {
    ObserverController observer = React.controller(ObserverController.class);
    if (chunkOrBlock instanceof Block block) {
      return observer.get(block, sampler);
    }
    return observer.get((Chunk) chunkOrBlock, sampler);
  }

  @Override
  public Optional<Double> sampleChunk(Object chunk, Sampler sampler) {
    return React.controller(ObserverController.class).sample((Chunk) chunk, sampler);
  }
}
