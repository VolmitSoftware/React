package art.arcane.react.core.controller;

import art.arcane.react.api.action.Action;
import art.arcane.react.api.feature.Feature;
import art.arcane.react.api.tweak.Tweak;
import art.arcane.react.platform.ComponentHost;
import art.arcane.react.platform.ReactPlatform;
import art.arcane.react.platform.ReactPlatforms;

import java.util.Optional;

/**
 * The running feature, tweak and action controllers on whichever platform started them.
 */
public final class ReactComponents {
  private static volatile FeatureController features;
  private static volatile TweakController tweaks;
  private static volatile ActionController actions;

  private ReactComponents() {
  }

  public static FeatureController features() {
    return features;
  }

  public static TweakController tweaks() {
    return tweaks;
  }

  public static ActionController actions() {
    return actions;
  }

  public static <T extends Feature> T feature(String id) {
    FeatureController controller = features;
    return controller == null || controller.getFeatures() == null ? null : controller.getFeatures().get(id);
  }

  public static <T extends Feature> T feature(Class<T> type) {
    FeatureController controller = features;
    return controller == null || controller.getFeatures() == null ? null : controller.getFeatures().get(type);
  }

  public static <T extends Tweak> T tweak(String id) {
    TweakController controller = tweaks;
    return controller == null || controller.getTweaks() == null ? null : controller.getTweaks().get(id);
  }

  public static <T extends Action<?>> T action(String id) {
    ActionController controller = actions;
    return controller == null || controller.getActions() == null ? null : controller.getActions().get(id);
  }

  public static <T extends Action<?>> T action(Class<T> type) {
    ActionController controller = actions;
    return controller == null || controller.getActions() == null ? null : controller.getActions().get(type);
  }

  public static Optional<ComponentHost> host() {
    ReactPlatform platform = ReactPlatforms.getOrNull();
    return platform == null ? Optional.empty() : platform.componentHost();
  }

  public static boolean isRunning() {
    return host().map(ComponentHost::isRunning).orElse(false);
  }

  public static boolean isMonitoringOnly() {
    return host().map(ComponentHost::isMonitoringOnly).orElse(false);
  }

  public static void verbose(String message) {
    ReactPlatform platform = ReactPlatforms.getOrNull();
    if (platform != null) {
      platform.verbose(message);
    }
  }

  public static void reportError(String context, Throwable failure) {
    ReactPlatform platform = ReactPlatforms.getOrNull();
    if (platform != null) {
      platform.reportError(context, failure);
    }
  }

  static void bind(FeatureController controller) {
    features = controller;
  }

  static void bind(TweakController controller) {
    tweaks = controller;
  }

  static void bind(ActionController controller) {
    actions = controller;
  }

  static void unbind(FeatureController controller) {
    if (features == controller) {
      features = null;
    }
  }

  static void unbind(TweakController controller) {
    if (tweaks == controller) {
      tweaks = null;
    }
  }

  static void unbind(ActionController controller) {
    if (actions == controller) {
      actions = null;
    }
  }
}
