package art.arcane.react.platform.bukkit;

import art.arcane.react.React;
import art.arcane.react.api.feature.Feature;
import art.arcane.react.api.feature.ReactTickedFeature;
import art.arcane.react.api.tweak.ReactTickedTweak;
import art.arcane.react.api.tweak.Tweak;
import art.arcane.react.core.controller.IntegrationController;
import art.arcane.react.core.controller.JobController;
import art.arcane.react.core.controller.WebController;
import art.arcane.react.core.integration.IntegrationCapabilitySupport;
import art.arcane.react.model.ReactConfiguration;
import art.arcane.react.platform.ComponentHost;
import art.arcane.react.platform.Subscription;
import art.arcane.react.util.common.scheduling.TickedObject;
import org.bukkit.event.Listener;

final class BukkitComponentHost implements ComponentHost {
  @Override
  public Subscription schedule(Object owner, String id, long intervalMs, Runnable tick) {
    TickedObject ticked;
    if (owner instanceof Feature feature && feature.getTickInterval() == intervalMs) {
      ticked = new ReactTickedFeature(feature);
    } else if (owner instanceof Tweak tweak && tweak.getTickInterval() == intervalMs) {
      ticked = new ReactTickedTweak(tweak);
    } else {
      ticked = new TickedObject("react", id, intervalMs) {
        @Override
        public void onTick() {
          tick.run();
        }
      };
    }
    return ticked::unregister;
  }

  @Override
  public void attach(Object component) {
    if (component instanceof Listener listener && React.instance != null) {
      React.instance.registerListener(listener);
    }
  }

  @Override
  public void detach(Object component) {
    if (component instanceof Listener listener && React.instance != null) {
      React.instance.unregisterListener(listener);
    }
  }

  @Override
  public boolean isEnabled() {
    return React.instance != null && React.instance.isEnabled();
  }

  @Override
  public boolean isRunning() {
    return React.instance != null && React.instance.isEnabled() && React.instance.isReady();
  }

  @Override
  public boolean isMonitoringOnly() {
    return React.instance != null && React.instance.isMonitoringOnly();
  }

  @Override
  public boolean integrationSecretsEnabled() {
    return ReactConfiguration.get().isIntegrationSecretsEnabled();
  }

  @Override
  public boolean hasCapability(String capability) {
    return IntegrationCapabilitySupport.hasCapability(React.controller(IntegrationController.class), capability);
  }

  @Override
  public boolean isIntegrationInstalled(String capability) {
    return IntegrationCapabilitySupport.isPluginInstalled(capability);
  }

  @Override
  public boolean applyConfigValue(String path, Object value) {
    return WebController.applyConfigValueOnServerThread(path, value);
  }

  @Override
  public double maxComputeTimeMs() {
    JobController controller = React.controller(JobController.class);
    return controller == null ? 1D : controller.getMaxComputeTime();
  }
}
