package art.arcane.react.api.sampler;

import art.arcane.react.platform.PlayerStatsHost;
import art.arcane.react.platform.ReactPlatform;
import art.arcane.react.platform.ReactPlatforms;
import art.arcane.react.platform.RegistryHost;
import art.arcane.react.platform.RuntimeStatsHost;
import art.arcane.react.platform.SamplerHost;
import art.arcane.react.platform.TickStatsHost;
import art.arcane.react.platform.WorldStatsHost;
import art.arcane.react.core.controller.SampleController;

import java.util.Optional;

public final class SamplerHosts {
  private SamplerHosts() {
  }

  public static Optional<SamplerHost> sampler() {
    ReactPlatform platform = ReactPlatforms.getOrNull();
    return platform == null ? Optional.empty() : platform.samplerHost();
  }

  public static Optional<RegistryHost> registry() {
    ReactPlatform platform = ReactPlatforms.getOrNull();
    return platform == null ? Optional.empty() : platform.registryHost();
  }

  public static Optional<TickStatsHost> ticks() {
    ReactPlatform platform = ReactPlatforms.getOrNull();
    return platform == null ? Optional.empty() : platform.tickStatsHost();
  }

  public static Optional<WorldStatsHost> worlds() {
    ReactPlatform platform = ReactPlatforms.getOrNull();
    return platform == null ? Optional.empty() : platform.worldStatsHost();
  }

  public static Optional<PlayerStatsHost> players() {
    ReactPlatform platform = ReactPlatforms.getOrNull();
    return platform == null ? Optional.empty() : platform.playerStatsHost();
  }

  public static Optional<RuntimeStatsHost> runtime() {
    ReactPlatform platform = ReactPlatforms.getOrNull();
    return platform == null ? Optional.empty() : platform.runtimeStatsHost();
  }

  public static SampleController sampleController() {
    return sampler().flatMap(SamplerHost::sampleController).orElse(null);
  }

  public static Sampler sampler(String id) {
    SampleController controller = sampleController();
    return controller == null ? null : controller.getSampler(id);
  }

  public static void verbose(String message) {
    ReactPlatform platform = ReactPlatforms.getOrNull();
    if (platform != null) {
      platform.verbose(message);
    }
  }

  public static void warn(String message) {
    ReactPlatform platform = ReactPlatforms.getOrNull();
    if (platform != null) {
      platform.warn(message);
    }
  }

  public static void warn(String message, Throwable failure) {
    ReactPlatform platform = ReactPlatforms.getOrNull();
    if (platform != null) {
      platform.warn(message, failure);
    }
  }

  public static void info(String message) {
    ReactPlatform platform = ReactPlatforms.getOrNull();
    if (platform != null) {
      platform.info(message);
    }
  }

  public static void reportError(String context, Throwable failure) {
    ReactPlatform platform = ReactPlatforms.getOrNull();
    if (platform != null) {
      platform.reportError(context, failure);
    }
  }
}
